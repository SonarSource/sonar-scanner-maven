/*
 * SonarQube Scanner for Maven :: Reactor Converter
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.scanner.maven.converter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;

/**
 * Turns the reactor of a {@link MavenSession} into the flat {@code sonar.*} properties describing it: one set of
 * properties per module, the submodule ones prefixed by their position in the module hierarchy.
 *
 * <p>This is the single entry point for everything that needs to read a Maven reactor the way the {@code sonar} goal
 * does. The session it is given has to be resolved to the same degree that goal's own declaration
 * ({@code requiresDependencyResolution = TEST}, {@code aggregator = true}) guarantees, so that every module's
 * classpath is available.
 */
public class MavenReactorConverter {

  private static final Pattern REPORT_PROPERTY_PATTERN = Pattern.compile("^sonar\\..*[rR]eportPaths?$");

  private final Log log;
  private final MavenSession session;
  private final MavenProjectConverter mavenProjectConverter;

  public MavenReactorConverter(Log log, MavenSession session, MavenProjectConverter mavenProjectConverter) {
    this.log = log;
    this.session = session;
    this.mavenProjectConverter = mavenProjectConverter;
  }

  /**
   * Wires the standard chain of converters around the Maven components a {@code sonar} goal execution has at hand.
   *
   * @param envProperties the {@code sonar.*} properties read from the environment, merged into every module's
   *                      properties; empty for a caller that applies its own environment handling.
   */
  public static MavenReactorConverter create(Log log, MavenSession session, LifecycleExecutor lifecycleExecutor,
    ToolchainManager toolchainManager, Map<String, String> envProperties) {
    ToolchainResolver toolchainResolver = new Maven3ToolchainResolver(session, log, toolchainManager);
    MavenCompilerResolver compilerResolver = new MavenCompilerResolver(session, lifecycleExecutor, log, toolchainResolver);
    return new MavenReactorConverter(log, session, new MavenProjectConverter(log, compilerResolver, envProperties));
  }

  public Map<String, String> collectProperties() throws MojoExecutionException {
    List<MavenProject> sortedProjects = session.getProjects();
    MavenProject topLevelProject = sortedProjects.stream()
      .filter(MavenProject::isExecutionRoot)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Maven session does not declare a top level project"));

    Properties userProperties = new Properties();
    MavenUtils.putRelevant(session.getUserProperties(), userProperties);
    Map<String, String> props = mavenProjectConverter.configure(sortedProjects, topLevelProject, userProperties);
    if (shouldCollectAllSources(userProperties)) {
      log.info("Parameter " + MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES + " is enabled. The scanner will attempt to collect additional sources.");
      if (mavenProjectConverter.isSourceDirsOverridden()) {
        log.warn(notCollectingAdditionalSourcesBecauseOf(SonarProperties.PROJECT_SOURCE_DIRS));
      } else if (mavenProjectConverter.isTestDirsOverridden()) {
        log.warn(notCollectingAdditionalSourcesBecauseOf(SonarProperties.PROJECT_TEST_DIRS));
      } else {
        collectAllSources(props, isUserDefinedJavaBinaries(userProperties));
      }
    }
    return props;
  }

  private static boolean shouldCollectAllSources(Properties userProperties) {
    return Boolean.parseBoolean(userProperties.getProperty(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES));
  }

  private static String notCollectingAdditionalSourcesBecauseOf(String overriddenProperty) {
    return "Parameter " + MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES + " is enabled but " +
      "the scanner will not collect additional sources because " + overriddenProperty + " has been overridden.";
  }

  void collectAllSources(Map<String, String> props, boolean shouldCollectJavaAndKotlinSources) {
    String projectBasedir = props.get(SonarProperties.PROJECT_BASEDIR);
    // Exclude the files and folders covered by sonar.sources and sonar.tests (and sonar.exclusions) as computed by the MavenConverter
    // Combine all the sonar.sources at the top-level and by module
    List<String> coveredSources = props.entrySet().stream()
      .filter(k -> k.getKey().endsWith(SonarProperties.PROJECT_SOURCE_DIRS) || k.getKey().endsWith(SonarProperties.PROJECT_TEST_DIRS))
      .map(Map.Entry::getValue)
      .filter(value -> !value.isEmpty())
      .flatMap(value -> MavenUtils.splitAsCsv(value).stream())
      .collect(Collectors.toList());
    // Crawl the FS for files we want
    List<String> collectedSources;
    try {
      Set<Path> existingSources = coveredSources.stream()
        .map(Paths::get)
        .collect(Collectors.toSet());
      SourceCollector visitor = new SourceCollector(existingSources, mavenProjectConverter.getSkippedBasedDirs(), excludedReportFiles(props), shouldCollectJavaAndKotlinSources);
      Files.walkFileTree(Paths.get(projectBasedir), visitor);
      collectedSources = visitor.getCollectedSources().stream()
        .map(file -> file.toAbsolutePath().toString())
        .collect(Collectors.toList());
      List<String> mergedSources = new ArrayList<>();
      mergedSources.addAll(MavenUtils.splitAsCsv(props.get(SonarProperties.PROJECT_SOURCE_DIRS)));
      mergedSources.addAll(collectedSources);
      props.put(SonarProperties.PROJECT_SOURCE_DIRS, MavenUtils.joinAsCsv(mergedSources));
    } catch (IOException e) {
      log.warn(e);
    }
  }

  private static Set<Path> excludedReportFiles(Map<String, String> props) {
    return props.keySet().stream()
      .filter(key -> REPORT_PROPERTY_PATTERN.matcher(key).matches())
      .map(props::get)
      .map(MavenUtils::splitAsCsv)
      .flatMap(List::stream)
      .map(Paths::get)
      .map(Path::toAbsolutePath)
      .map(Path::normalize)
      .collect(Collectors.toSet());
  }

  private static boolean isUserDefinedJavaBinaries(Properties userProperties) {
    return userProperties.containsKey(MavenProjectConverter.JAVA_PROJECT_MAIN_LIBRARIES) &&
      userProperties.containsKey(MavenProjectConverter.JAVA_PROJECT_MAIN_BINARY_DIRS);
  }
}
