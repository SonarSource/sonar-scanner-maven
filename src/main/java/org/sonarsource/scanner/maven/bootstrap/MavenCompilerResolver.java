/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2021 SonarSource SA
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.apache.maven.toolchain.java.DefaultJavaToolChain;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.StringConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class MavenCompilerResolver {
  private static final String DEFAULT_COMPILE_EXECUTION_ID = "default-compile";
  private static final String TEST_COMPILE_PHASE = "test-compile";
  private static final String COMPILE_GOAL = "compile";
  private static final String TEST_COMPILE_GOAL = "testCompile";
  private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";

  private final MavenSession session;
  private final Log log;
  private final ToolchainManager toolchainManager;
  private final LifecycleExecutor lifecycleExecutor;

  public MavenCompilerResolver(MavenSession session, LifecycleExecutor lifecycleExecutor, Log log, ToolchainManager toolchainManager) {
    this.session = session;
    this.lifecycleExecutor = lifecycleExecutor;
    this.log = log;
    this.toolchainManager = toolchainManager;
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

  public static class MavenCompilerConfiguration {
    private Optional<String> release;
    private Optional<String> target;
    private Optional<String> source;
    private Optional<String> jdkHome;
    private final String executionId;

    private MavenCompilerConfiguration(String executionId) {
      this.executionId = executionId;
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

    public Optional<String> getJdkHome() {
      return jdkHome;
    }

    public String getExecutionId() {
      return executionId;
    }

    public static boolean same(MavenCompilerConfiguration one, MavenCompilerConfiguration two) {
      return Objects.equals(one.jdkHome, two.jdkHome)
        && Objects.equals(one.release, two.release)
        && Objects.equals(one.source, two.source)
        && Objects.equals(one.target, two.target);
    }

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

  private static boolean isMavenCompilerGoal(MojoExecution e) {
    return e.getArtifactId().equals(MAVEN_COMPILER_PLUGIN)
      && e.getGroupId().equals(MavenUtils.GROUP_ID_APACHE_MAVEN)
      && (e.getGoal().equals(COMPILE_GOAL) || e.getGoal().equals(TEST_COMPILE_GOAL));
  }

  private MavenCompilerConfiguration extractConfiguration(MojoExecution compilerExecution) {
    MavenCompilerConfiguration result = new MavenCompilerConfiguration(compilerExecution.getExecutionId());
    result.release = getStringConfiguration(compilerExecution, "release");
    result.target = getStringConfiguration(compilerExecution, "target");
    result.source = getStringConfiguration(compilerExecution, "source");
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
    return Optional.ofNullable(convertString(exec, config));
  }

  private String convertString(MojoExecution exec, PlexusConfiguration config) {
    ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, exec);
    BasicStringConverter converter = new BasicStringConverter();
    try {
      return converter.fromExpression(config, expressionEvaluator);
    } catch (ComponentConfigurationException e) {
      log.warn(e.getMessage(), e);
      return null;
    }
  }

  private Optional<Map<String, String>> getMapConfiguration(MojoExecution exec, String parameterName) {
    Xpp3Dom configuration = exec.getConfiguration();
    PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(configuration);
    PlexusConfiguration config = pomConfiguration.getChild(parameterName, false);
    if (config == null) {
      return Optional.empty();
    }
    return Optional.of(Stream.of(config.getChildren())
      .collect(Collectors.toMap(PlexusConfiguration::getName, c -> convertString(exec, config.getChild(c.getName(), false)))));
  }

  private Optional<String> getJdkHome(MojoExecution compilerExecution) {

    Optional<String> executable = getStringConfiguration(compilerExecution, "executable");
    if (executable.isPresent()) {
      return getJavaHomeFromJavac(executable.get());
    }

    // Inspired by
    // https://github.com/apache/maven-compiler-plugin/blob/dc4a5635ba4eb2ba5e461fa53b2c47c58d7fa397/src/main/java/org/apache/maven/plugin/compiler/AbstractCompilerMojo.java#L1418
    Toolchain tc = null;

    // Maven 3.3.1 has plugin execution scoped Toolchain Support
    Optional<Map<String, String>> jdkToolchain = getMapConfiguration(compilerExecution, "jdkToolchain");
    if (jdkToolchain.isPresent() && !jdkToolchain.get().isEmpty()) {
      List<Toolchain> tcs = collectMatchingToolchains(jdkToolchain.get());
      if (!tcs.isEmpty()) {
        tc = tcs.get(0);
      }
    }

    // Fallback on the global jdk toolchain
    if (tc == null) {
      tc = toolchainManager.getToolchainFromBuildContext("jdk", session);
    }

    if (tc instanceof DefaultJavaToolChain) {
      return Optional.of(((DefaultJavaToolChain) tc).getJavaHome());
    }

    // Like m-compiler-p, last fallback is to compile with the runtime JDK
    Optional<String> runtimeCompiler = getJavacExecutableFromRuntimeJdk();
    if (runtimeCompiler.isPresent()) {
      return getJavaHomeFromJavac(runtimeCompiler.get());
    }

    return Optional.empty();
  }

  /**
   *
   * @param javacPath Should be something like <jdk home>/bin/javac[.exe]
   * @return <jdk home>
   */
  private static Optional<String> getJavaHomeFromJavac(String javacPath) {
    return Optional.of(Paths.get(javacPath).getParent().getParent().toString());
  }

  @SuppressWarnings("unchecked")
  private List<Toolchain> collectMatchingToolchains(Map<String, String> jdkToolchain) {
    // getToolchains method only added in Maven 3.3.1, so use reflection
    try {
      Method getToolchainsMethod = toolchainManager.getClass().getMethod("getToolchains", MavenSession.class, String.class, Map.class);
      return (List<Toolchain>) getToolchainsMethod.invoke(toolchainManager, session, "jdk", jdkToolchain);
    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      // ignore
      return Collections.emptyList();
    }
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

  private static class BasicStringConverter extends StringConverter {
    @Override
    public String fromExpression(PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) throws ComponentConfigurationException {
      return (String) super.fromExpression(configuration, expressionEvaluator);
    }

  }
}
