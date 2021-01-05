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

import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonarsource.scanner.api.EmbeddedScanner;

/**
 * Configure properties and bootstrap using SonarQube scanner API
 */
public class ScannerBootstrapper {

  static final String UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE = "With SonarQube server prior to 5.6, use sonar-maven-plugin <= 3.3";

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
      applyMasks();
      scanner.start();
      serverVersion = scanner.serverVersion();

      checkSQVersion();

      if (log.isDebugEnabled()) {
        scanner.setGlobalProperty("sonar.verbose", "true");
      }

      scanner.execute(collectProperties());
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void applyMasks() {
    // Exclude log implementation to not conflict with Maven 3.1 logging impl
    scanner.mask("org.slf4j.LoggerFactory");
    // Include slf4j Logger that is exposed by some Sonar components
    scanner.unmask("org.slf4j.Logger");
    scanner.unmask("org.slf4j.ILoggerFactory");
    // MSONAR-122
    scanner.unmask("org.slf4j.Marker");
    // Exclude other slf4j classes
    // .unmask("org.slf4j.impl.")
    scanner.mask("org.slf4j.");
    // Exclude logback
    scanner.mask("ch.qos.logback.");
    scanner.mask("org.sonar.");
    // Guava is not the same version in SonarQube classloader
    scanner.mask("com.google.common");
    // Include everything else (we need to unmask all extensions that might be passed to the batch)
    scanner.unmask("");
  }

  private Map<String, String> collectProperties()
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
    Map<String, String> props = mavenProjectConverter.configure(sortedProjects, topLevelProject, session.getUserProperties());
    props.putAll(propertyDecryptor.decryptProperties(props));

    return props;
  }

  private void checkSQVersion() {
    if (serverVersion != null) {
      log.info("SonarQube version: " + serverVersion);
    }

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

}
