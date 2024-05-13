/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2024 SonarSource SA
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.ScanProperties;
import org.sonarsource.scanner.api.ScannerProperties;

import static org.sonarsource.scanner.maven.bootstrap.MavenProjectConverter.getPropertyByKey;

/**
 * Configure properties and bootstrap using SonarQube scanner API
 */
public class ScannerBootstrapper {

  static final String UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE = "With SonarQube server prior to 5.6, use sonar-maven-plugin <= 3.3";
  private static final String SONARCLOUD_HOST_URL = "https://sonarcloud.io";

  private final Log log;
  private final MavenSession session;
  private final EmbeddedScanner scanner;
  private final MavenProjectConverter mavenProjectConverter;
  private String serverVersion;
  private PropertyDecryptor propertyDecryptor;

  public ScannerBootstrapper(Log log, MavenSession session, EmbeddedScanner scanner, MavenProjectConverter mavenProjectConverter, PropertyDecryptor propertyDecryptor) {
    this.log = log;
    this.session = session;
    this.scanner = scanner;
    this.mavenProjectConverter = mavenProjectConverter;
    this.propertyDecryptor = propertyDecryptor;
  }

  public void execute() throws MojoExecutionException {
    try {
      logEnvironmentInformation();
      scanner.start();
      serverVersion = scanner.serverVersion();

      if (isSonarCloudUsed()) {
        log.info("Communicating with SonarCloud");
      } else {
        if (serverVersion != null) {
          log.info("Communicating with SonarQube Server " + serverVersion);
        }
        checkSQVersion();
      }

      if (log.isDebugEnabled()) {
        scanner.setGlobalProperty("sonar.verbose", "true");
      }

      scanner.execute(collectProperties());
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  // TODO remove this workaround when discovering if the sevrer is SC or SQ is available through the API
  private boolean isSonarCloudUsed() {
    return session.getProjects().stream()
      // We can use EnvProperties from MavenProjectConverter as they are initialized at construction time,
      // but we can't use UserProperties from the MavenProjectConverter as they are only initialized
      // in the "collectProperties" method.
      .map(project -> getPropertyByKey(ScannerProperties.HOST_URL, project, session.getUserProperties(), mavenProjectConverter.getEnvProperties()))
      .filter(Objects::nonNull)
      .anyMatch(hostUrl -> hostUrl.startsWith(SONARCLOUD_HOST_URL));
  }

  @VisibleForTesting
  Map<String, String> collectProperties()
    throws MojoExecutionException {
    List<MavenProject> sortedProjects = session.getProjects();
    MavenProject topLevelProject = null;
    for (MavenProject project : sortedProjects) {
      if (project.isExecutionRoot()) {
        topLevelProject = project;
        break;
      }
    }

    if (topLevelProject == null) {
      throw new IllegalStateException("Maven session does not declare a top level project");
    }

    Properties userProperties = session.getUserProperties();
    Map<String, String> props = mavenProjectConverter.configure(sortedProjects, topLevelProject, userProperties);
    props.putAll(propertyDecryptor.decryptProperties(props));
    if (shouldCollectAllSources(userProperties)) {
      log.info("Parameter " + MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES + " is enabled. The scanner will attempt to collect additional sources.");
      if (mavenProjectConverter.isSourceDirsOverridden()) {
        log.warn(notCollectingAdditionalSourcesBecauseOf(ScanProperties.PROJECT_SOURCE_DIRS));
      } else if (mavenProjectConverter.isTestDirsOverridden()) {
        log.warn(notCollectingAdditionalSourcesBecauseOf(ScanProperties.PROJECT_TEST_DIRS));
      } else {
        collectAllSources(props);
      }
    }

    return props;
  }

  private static boolean shouldCollectAllSources(Properties userProperties) {
    return Boolean.TRUE.equals(Boolean.parseBoolean(userProperties.getProperty(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES)));
  }

  private static String notCollectingAdditionalSourcesBecauseOf(String overriddenProperty) {
    return "Parameter " + MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES + " is enabled but " +
      "the scanner will not collect additional sources because " + overriddenProperty + " has been overridden.";
  }

  @VisibleForTesting
  void collectAllSources(Map<String, String> props) {
    String projectBasedir = props.get(ScanProperties.PROJECT_BASEDIR);
    // Exclude the files and folders covered by sonar.sources and sonar.tests (and sonar.exclusions) as computed by the MavenConverter
    // Combine all the sonar.sources at the top-level and by module
    List<String> coveredSources = props.entrySet().stream()
      .filter(k -> k.getKey().endsWith(ScanProperties.PROJECT_SOURCE_DIRS) || k.getKey().endsWith(ScanProperties.PROJECT_TEST_DIRS))
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
      SourceCollector visitor = new SourceCollector(existingSources, mavenProjectConverter.getSkippedBasedDirs());
      Files.walkFileTree(Paths.get(projectBasedir), visitor);
      collectedSources = visitor.getCollectedSources().stream()
        .map(file -> file.toAbsolutePath().toString())
        .collect(Collectors.toList());
      List<String> mergedSources = new ArrayList<>();
      mergedSources.addAll(MavenUtils.splitAsCsv(props.get(ScanProperties.PROJECT_SOURCE_DIRS)));
      mergedSources.addAll(collectedSources);
      props.put(ScanProperties.PROJECT_SOURCE_DIRS, MavenUtils.joinAsCsv(mergedSources));
    } catch (IOException e) {
      log.warn(e);
    }
  }

  private void checkSQVersion() {
    if (isVersionPriorTo("5.6")) {
      throw new UnsupportedOperationException(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
    }
  }

  boolean isVersionPriorTo(String version) {
    if (serverVersion == null) {
      return true;
    }
    return new ComparableVersion(serverVersion).compareTo(new ComparableVersion(version)) < 0;
  }

  private void logEnvironmentInformation() {
    String vmInformation = String.format(
      "Java %s %s (%s-bit)",
      SystemWrapper.getProperty("java.version"),
      SystemWrapper.getProperty("java.vm.vendor"),
      SystemWrapper.getProperty("sun.arch.data.model"));
    log.info(vmInformation);
    String operatingSystem = String.format(
      "%s %s (%s)",
      SystemWrapper.getProperty("os.name"),
      SystemWrapper.getProperty("os.version"),
      SystemWrapper.getProperty("os.arch"));
    log.info(operatingSystem);
    String mavenOptions = SystemWrapper.getenv("MAVEN_OPTS");
    if (mavenOptions != null) {
      log.info(String.format("MAVEN_OPTS=%s", mavenOptions));
    }
  }

}
