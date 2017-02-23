/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.maven.ExtensionsFactory;

/**
 * Configure properties and bootstrap using SonarQube scanner API
 */
public class ScannerBootstrapper {

  private final Log log;
  private final MavenSession session;
  private final EmbeddedScanner scanner;
  private final MavenProjectConverter mavenProjectConverter;
  private final ExtensionsFactory extensionsFactory;
  private String serverVersion;
  private PropertyDecryptor propertyDecryptor;

  public ScannerBootstrapper(Log log, MavenSession session, EmbeddedScanner scanner, MavenProjectConverter mavenProjectConverter, ExtensionsFactory extensionsFactory,
    PropertyDecryptor propertyDecryptor) {
    this.log = log;
    this.session = session;
    this.scanner = scanner;
    this.mavenProjectConverter = mavenProjectConverter;
    this.extensionsFactory = extensionsFactory;
    this.propertyDecryptor = propertyDecryptor;
  }

  public void execute() throws IOException, MojoExecutionException {
    try {
      applyMasks();
      scanner.start();
      serverVersion = scanner.serverVersion();

      checkSQVersion();

      if (isVersionPriorTo("5.2")) {
        // for these versions, global properties and extensions are only applied when calling runAnalysis()
        if (supportsNewDependencyProperty()) {
          scanner.addExtensions(extensionsFactory.createExtensionsWithDependencyProperty().toArray());
        } else {
          scanner.addExtensions(extensionsFactory.createExtensions().toArray());
        }

      }
      if (log.isDebugEnabled()) {
        scanner.setGlobalProperty("sonar.verbose", "true");
      }

      scanner.runAnalysis(collectProperties());
      scanner.stop();
    } catch (Exception e) {
      throw ExceptionHandling.handle(e, log);
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

  private Properties collectProperties()
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
    Properties props = mavenProjectConverter.configure(sortedProjects, topLevelProject, session.getUserProperties(), analyzeResources());
    props.putAll(propertyDecryptor.decryptProperties(props));

    return props;
  }

  private void checkSQVersion() {
    if (serverVersion != null) {
      log.info("SonarQube version: " + serverVersion);
    }

    if (isVersionPriorTo("4.5")) {
      log.warn("With SonarQube prior to 4.5, it is recommended to use sonar-maven-plugin 2.6");
    }
  }

  boolean isVersionPriorTo(String version) {
    if (serverVersion == null) {
      return true;
    }
    return new ComparableVersion(serverVersion).compareTo(new ComparableVersion(version)) < 0;
  }

  private boolean supportsNewDependencyProperty() {
    return !isVersionPriorTo("5.0");
  }
  
  private boolean analyzeResources() {
    return !isVersionPriorTo("6.3");
  }

}
