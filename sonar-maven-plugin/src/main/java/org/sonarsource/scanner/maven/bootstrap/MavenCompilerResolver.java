/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.scanner.maven.bootstrap;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import static org.sonarsource.scanner.maven.bootstrap.MavenUtils.convertString;

public class MavenCompilerResolver {
  private static final String DEFAULT_COMPILE_EXECUTION_ID = "default-compile";
  private static final String TEST_COMPILE_PHASE = "test-compile";
  private static final String COMPILE_GOAL = "compile";
  private static final String TEST_COMPILE_GOAL = "testCompile";
  private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";

  private final MavenSession session;
  private final Log log;
  private final ToolchainResolver toolchainResolver;
  private final LifecycleExecutor lifecycleExecutor;

  public MavenCompilerResolver(MavenSession session, LifecycleExecutor lifecycleExecutor, Log log, ToolchainResolver toolchainResolver) {
    this.session = session;
    this.lifecycleExecutor = lifecycleExecutor;
    this.log = log;
    this.toolchainResolver = toolchainResolver;
  }

  private static int defaultCompileFirstThenCompileFirst(MojoExecution a, MojoExecution b) {
    // Favor default-compile, as this is the one likely to best represent the "main" compiler execution
    if (DEFAULT_COMPILE_EXECUTION_ID.equals(a.getExecutionId())) {
      return -1;
    } else if (DEFAULT_COMPILE_EXECUTION_ID.equals(b.getExecutionId())) {
      return 1;
    } else {
      // compile is before testCompile
      return a.getGoal().compareTo(b.getGoal());
    }
  }

  private static boolean isMavenCompilerGoal(MojoExecution e) {
    return e.getArtifactId().equals(MAVEN_COMPILER_PLUGIN)
      && e.getGroupId().equals(MavenUtils.GROUP_ID_APACHE_MAVEN)
      && (e.getGoal().equals(COMPILE_GOAL) || e.getGoal().equals(TEST_COMPILE_GOAL));
  }

  /**
   * If {@code javacExecutable} is <code>/jdk/bin/javac</code> then the absolute path to JDK home is returned <code>/jdk</code>.
   * <br>
   * Empty is returned if {@code javacExecutable} is incorrect.
   *
   * @param javacExecutable    /jdk/bin/java*
   * @return path to jdk directory; or <code>empty</code> if wrong path or directory layout of JDK installation.
   */
  static Optional<Path> toJdkHomeFromJavacExec(String javacExecutable) {
    Path bin = Path.of(javacExecutable).toAbsolutePath().getParent();
    if ("bin".equals(bin.getFileName().toString())) {
      return Optional.of(bin.getParent());
    }
    return Optional.empty();
  }

  /**
   * Inspired from https://github.com/codehaus-plexus/plexus-compiler/blob/3300ad47ea45a3c3b9f815acc0973020b21486cd/plexus-compilers/plexus-compiler-javac/src/main/java/org/codehaus/plexus/compiler/javac/JavacCompiler.java#L999
   */
  private static Optional<String> getJavacExecutableFromRuntimeJdk() {
    String javacCommand = "javac" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");

    String javaHome = System.getProperty("java.home");
    Path javacExe;
    if (SystemUtils.IS_OS_AIX) {
      javacExe = Paths.get(javaHome, "..", "sh", javacCommand).normalize();
    } else if (SystemUtils.IS_OS_MAC_OSX) {
      javacExe = Paths.get(javaHome, "bin", javacCommand);
    } else {
      javacExe = Paths.get(javaHome, "..", "bin", javacCommand).normalize();
    }

    // ----------------------------------------------------------------------
    // Try to find javacExe from JAVA_HOME environment variable
    // ----------------------------------------------------------------------
    if (!Files.isRegularFile(javacExe)) {
      Map<String, String> env = System.getenv();
      javaHome = env.get("JAVA_HOME");
      if (StringUtils.isEmpty(javaHome) || !new File(javaHome).isDirectory()) {
        return Optional.empty();
      }
      javacExe = Paths.get(javaHome, "bin", javacCommand);
    }

    if (!Files.isRegularFile(javacExe)) {
      return Optional.empty();
    }

    return Optional.of(javacExe.toAbsolutePath().toString());
  }

  public Optional<MavenCompilerConfiguration> extractConfiguration(MavenProject pom) {
    MavenProject oldProject = session.getCurrentProject();
    try {
      // Switch to the project for which we try to resolve the configuration.
      session.setCurrentProject(pom);
      List<MojoExecution> allCompilerExecutions = lifecycleExecutor.calculateExecutionPlan(session, true, TEST_COMPILE_PHASE)
        .getMojoExecutions()
        .stream()
        .filter(MavenCompilerResolver::isMavenCompilerGoal)
        .sorted(MavenCompilerResolver::defaultCompileFirstThenCompileFirst)
        .collect(Collectors.toList());
      if (allCompilerExecutions.isEmpty()) {
        return Optional.empty();
      }
      List<MavenCompilerConfiguration> allCompilerConfigurations = allCompilerExecutions.stream().map(this::extractConfiguration).collect(Collectors.toList());
      MavenCompilerConfiguration first = allCompilerConfigurations.get(0);

      if (!allCompilerConfigurations.stream().allMatch(config -> MavenCompilerConfiguration.same(config, first))) {
        log.warn("Heterogeneous compiler configuration has been detected. Using compiler configuration from execution: '" + first.getExecutionId() + "'");
      }

      return Optional.of(first);

    } catch (Exception e) {
      log.warn("Failed to collect configuration from the maven-compiler-plugin", e);
      return Optional.empty();
    } finally {
      session.setCurrentProject(oldProject);
    }

  }

  private MavenCompilerConfiguration extractConfiguration(MojoExecution compilerExecution) {
    MavenCompilerConfiguration result = new MavenCompilerConfiguration(compilerExecution.getExecutionId());
    result.release = getStringConfiguration(compilerExecution, "release");
    result.target = getStringConfiguration(compilerExecution, "target");
    result.source = getStringConfiguration(compilerExecution, "source");
    result.enablePreview = getStringConfiguration(compilerExecution, "enablePreview");
    result.jdkHome = getJdkHome(compilerExecution);
    return result;
  }

  private Optional<String> getStringConfiguration(MojoExecution exec, String parameterName) {
    Xpp3Dom configuration = exec.getConfiguration();
    PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(configuration);
    PlexusConfiguration config = pomConfiguration.getChild(parameterName, false);
    if (config == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(convertString(session, log, exec, config));
  }

  private Optional<Path> getJdkHome(MojoExecution compilerExecution) {

    Optional<String> executable = getStringConfiguration(compilerExecution, "executable");
    if (executable.isPresent()) {
      return toJdkHomeFromJavacExec(executable.get());
    }

    Optional<Path> jdkHomeFromToolchain = toolchainResolver.getJdkHomeFromToolchains(compilerExecution);
    if (jdkHomeFromToolchain.isPresent()) {
      return jdkHomeFromToolchain;
    }

    // Like m-compiler-p, last fallback is to compile with the runtime JDK
    Optional<String> runtimeCompiler = getJavacExecutableFromRuntimeJdk();
    if (runtimeCompiler.isPresent()) {
      return toJdkHomeFromJavacExec(runtimeCompiler.get());
    }

    return Optional.empty();
  }

  public static class MavenCompilerConfiguration {
    private final String executionId;
    private Optional<String> release;
    private Optional<String> target;
    private Optional<String> source;
    private Optional<Path> jdkHome;
    private Optional<String> enablePreview;

    private MavenCompilerConfiguration(String executionId) {
      this.executionId = executionId;
    }

    public static boolean same(MavenCompilerConfiguration one, MavenCompilerConfiguration two) {
      return Objects.equals(one.getJdkHome(), two.getJdkHome())
        && Objects.equals(one.getRelease(), two.getRelease())
        && Objects.equals(one.getSource(), two.getSource())
        && Objects.equals(one.getTarget(), two.getTarget())
        && Objects.equals(one.getEnablePreview(), two.getEnablePreview());
    }

    public Optional<String> getRelease() {
      return release;
    }

    public Optional<String> getTarget() {
      return target;
    }

    public Optional<String> getSource() {
      return source;
    }

    public Optional<Path> getJdkHome() {
      return jdkHome;
    }

    public Optional<String> getEnablePreview() {
      return enablePreview;
    }

    public String getExecutionId() {
      return executionId;
    }

  }


}
