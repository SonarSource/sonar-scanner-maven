/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.sonarsource.scanner.maven.bootstrap.MavenCompilerResolver.toJdkHomeFromJavacExec;
import static org.sonarsource.scanner.maven.bootstrap.MavenUtils.convertString;

public class Maven3ToolchainResolver implements ToolchainResolver {

  private final MavenSession session;
  private final Log log;
  private final ToolchainManager toolchainManager;

  public Maven3ToolchainResolver(MavenSession session, Log log, ToolchainManager toolchainManager) {
    this.session = session;
    this.log = log;
    this.toolchainManager = toolchainManager;
  }

  @Override
  public Optional<Path> getJdkHomeFromToolchains(MojoExecution compilerExecution) {

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

    if (tc != null) {
      String javacToUse = tc.findTool("javac");
      if (isNotEmpty(javacToUse)) {
        return toJdkHomeFromJavacExec(javacToUse);
      }
    }

    return Optional.empty();
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

  private Optional<Map<String, String>> getMapConfiguration(MojoExecution exec, String parameterName) {
    Xpp3Dom configuration = exec.getConfiguration();
    PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(configuration);
    PlexusConfiguration config = pomConfiguration.getChild(parameterName, false);
    if (config == null) {
      return Optional.empty();
    }
    return Optional.of(Stream.of(config.getChildren())
      .collect(Collectors.toMap(PlexusConfiguration::getName, c -> convertString(session, log, exec, config.getChild(c.getName(), false)))));
  }

}
