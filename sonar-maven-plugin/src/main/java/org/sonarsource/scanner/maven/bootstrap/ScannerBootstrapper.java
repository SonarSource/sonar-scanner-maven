/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapResult;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonarsource.scanner.maven.converter.MavenReactorConverter;

/**
 * Configure properties and bootstrap using SonarQube scanner API
 */
public class ScannerBootstrapper {

  static final String UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE = "With SonarQube server prior to 5.6, use sonar-maven-plugin <= 3.3";

  private final Log log;
  private final ScannerEngineBootstrapper bootstrapper;
  private final MavenReactorConverter mavenReactorConverter;
  private String serverVersion;
  private final PropertyDecryptor propertyDecryptor;

  public ScannerBootstrapper(Log log, ScannerEngineBootstrapper bootstrapper, MavenReactorConverter mavenReactorConverter,
    PropertyDecryptor propertyDecryptor) {
    this.log = log;
    this.bootstrapper = bootstrapper;
    this.mavenReactorConverter = mavenReactorConverter;
    this.propertyDecryptor = propertyDecryptor;
  }

  public void execute() throws MojoExecutionException {
    logEnvironmentInformation();
    try (ScannerEngineBootstrapResult bootstrapResult = bootstrapper.bootstrap()) {
      if (!bootstrapResult.isSuccessful()) {
        throw new MojoFailureException("The scanner bootstrapping has failed! See the logs for more details.");
      }
      try (ScannerEngineFacade engineFacade = bootstrapResult.getEngineFacade()) {
        if (!engineFacade.isSonarQubeCloud()) {
          serverVersion = engineFacade.getServerVersion();
          checkSQVersion();
        }
        if (!engineFacade.analyze(collectProperties())) {
          throw new MojoFailureException("The scanner analysis has failed! See the logs for more details.");
        }
      }
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  /**
   * Decrypting is the one step the shared reactor conversion deliberately leaves out: it needs the Maven settings
   * security configuration, which only a real plugin execution has access to.
   */
  @VisibleForTesting
  Map<String, String> collectProperties() throws MojoExecutionException {
    Map<String, String> props = mavenReactorConverter.collectProperties();
    props.putAll(propertyDecryptor.decryptProperties(props));
    return props;
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
      SystemWrapper.getProperty("sun.arch.data.model")
    );
    log.info(vmInformation);
    String operatingSystem = String.format(
      "%s %s (%s)",
      SystemWrapper.getProperty("os.name"),
      SystemWrapper.getProperty("os.version"),
      SystemWrapper.getProperty("os.arch")
    );
    log.info(operatingSystem);
    String mavenOptions = SystemWrapper.getenv("MAVEN_OPTS");
    if (mavenOptions != null) {
      log.info(String.format("MAVEN_OPTS=%s", mavenOptions));
    }
  }

}
