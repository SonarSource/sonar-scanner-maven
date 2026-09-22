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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.sonarsource.scanner.lib.AnalysisProperties;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapResult;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonarsource.scanner.maven.converter.MavenProjectConverter;
import org.sonarsource.scanner.maven.converter.MavenReactorConverter;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.maven.bootstrap.ScannerBootstrapper.UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE;

class ScannerBootstrapperTest {
  @Mock
  private Log log;

  @Mock
  private MavenSession session;

  private static final SecDispatcher securityDispatcher = s -> s;

  @Mock
  private ScannerEngineBootstrapper scannerEngineBootstrapper;

  @Mock
  private MavenProjectConverter mavenProjectConverter;

  @TempDir
  public Path tmpFolder;

  private ScannerBootstrapper scannerBootstrapper;

  private Map<String, String> projectProperties;

  @Mock
  ScannerEngineFacade scannerEngineFacade;

  @Mock
  ScannerEngineBootstrapResult scannerEngineBootstrapResult;

  @BeforeEach
  void setUp()
    throws MojoExecutionException, IOException {
    MockitoAnnotations.initMocks(this);

    MavenProject rootProject = mock(MavenProject.class);
    when(rootProject.isExecutionRoot()).thenReturn(true);
    when(session.getProjects()).thenReturn(Collections.singletonList(rootProject));
    when(session.getUserProperties()).thenReturn(new Properties());

    projectProperties = new HashMap<>();
    projectProperties.put(AnalysisProperties.PROJECT_BASEDIR, tmpFolder.toAbsolutePath().toString());
    // Create folders
    Path pom = tmpFolder.resolve("pom.xml");
    pom.toFile().createNewFile();
    Path sourceMainDirs = tmpFolder.resolve(Paths.get("src", "main", "java"));
    sourceMainDirs.toFile().mkdirs();
    Path sourceTestDirs = tmpFolder.resolve(Paths.get("src", "test", "java"));
    sourceTestDirs.toFile().mkdirs();
    Path sourceResourceDirs = tmpFolder.resolve(Paths.get("src", "main", "resources"));
    sourceResourceDirs.toFile().mkdirs();
    Path javascriptResource = sourceResourceDirs.resolve("index.js");
    javascriptResource.toFile().createNewFile();
    projectProperties.put(AnalysisProperties.PROJECT_SOURCE_DIRS, sourceMainDirs.toFile() + "," + pom.toFile());
    projectProperties.put(AnalysisProperties.PROJECT_TEST_DIRS, sourceTestDirs.toFile() + "," + pom.toFile());

    when(mavenProjectConverter.configure(any(), any(), any())).thenReturn(projectProperties);
    when(mavenProjectConverter.getEnvProperties()).thenReturn(new HashMap<>());
    when(rootProject.getProperties()).thenReturn(new Properties());

    when(scannerEngineBootstrapResult.getEngineFacade()).thenReturn(scannerEngineFacade);
    when(scannerEngineBootstrapper.bootstrap()).thenReturn(scannerEngineBootstrapResult);
    when(scannerEngineBootstrapResult.isSuccessful()).thenReturn(true);
    when(scannerEngineFacade.analyze(any())).thenReturn(true);
    MavenReactorConverter mavenReactorConverter = new MavenReactorConverter(log, session, mavenProjectConverter);
    scannerBootstrapper = new ScannerBootstrapper(log, scannerEngineBootstrapper, mavenReactorConverter, new PropertyDecryptor(log, securityDispatcher));
  }

  @Test
  void testSQBefore56() {
    when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.1");

    assertThatThrownBy(scannerBootstrapper::execute)
      .isInstanceOf(MojoExecutionException.class)
      .hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
      .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
  }

  @Test
  void testSQ56() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.6");
    scannerBootstrapper.execute();

    verifyCommonCalls();
  }

  @Test
  void when_ScannerEngineBootstrapper_is_not_successful_getEngineFacade_should_not_be_called() {
    when(scannerEngineBootstrapResult.isSuccessful()).thenReturn(false);
    when(scannerEngineBootstrapResult.getEngineFacade()).thenThrow(new IllegalAccessError("Should not be called"));
    when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.6");

    assertThatThrownBy(() -> scannerBootstrapper.execute())
      .isInstanceOf(MojoExecutionException.class)
      .hasMessage("The scanner bootstrapping has failed! See the logs for more details.");
  }

  @Test
  void throw_an_exception_when_analyze_fail() {
    when(scannerEngineFacade.analyze(any())).thenReturn(false);
    when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.6");

    assertThatThrownBy(() -> scannerBootstrapper.execute())
      .isInstanceOf(MojoExecutionException.class)
      .hasMessage("The scanner analysis has failed! See the logs for more details.");
  }

  @Test
  void testVersionComparisonWithBuildNumber() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("6.3.0.12345");
    scannerBootstrapper.execute();

    assertThat(scannerBootstrapper.isVersionPriorTo("4.5")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.3")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.4")).isTrue();
  }

  @Nested
  class EnvironmentInformation {
    MockedStatic<SystemWrapper> mockedSystem;

    @BeforeEach
    void before() {
      when(scannerEngineFacade.getServerVersion()).thenReturn("9.9");
      when(scannerEngineFacade.isSonarQubeCloud()).thenReturn(false);
      mockedSystem = mockStatic(SystemWrapper.class);
    }

    @AfterEach
    void after() {
      mockedSystem.close();
    }

    @Test
    void environment_information_is_logged_at_info_level() throws MojoExecutionException {
      mockedSystem.when(() -> SystemWrapper.getProperty("os.name")).thenReturn("Solaris");
      mockedSystem.when(() -> SystemWrapper.getProperty("os.version")).thenReturn("42.1");
      mockedSystem.when(() -> SystemWrapper.getProperty("os.arch")).thenReturn("x16");

      mockedSystem.when(() -> SystemWrapper.getProperty("java.vm.vendor")).thenReturn("Artisanal Distribution");
      mockedSystem.when(() -> SystemWrapper.getProperty("java.version")).thenReturn("4.2.0");
      mockedSystem.when(() -> SystemWrapper.getProperty("sun.arch.data.model")).thenReturn("16");

      mockedSystem.when(() -> SystemWrapper.getenv("MAVEN_OPTS")).thenReturn("-XX:NotAnActualOption=42");

      scannerBootstrapper.execute();
      InOrder inOrderVerifier = inOrder(log);

      inOrderVerifier.verify(log, times(1)).info("Java 4.2.0 Artisanal Distribution (16-bit)");
      inOrderVerifier.verify(log, times(1)).info("Solaris 42.1 (x16)");
      inOrderVerifier.verify(log, times(1)).info("MAVEN_OPTS=-XX:NotAnActualOption=42");
    }

    @Test
    void maven_opts_is_not_logged_at_info_level_when_not_absent_from_environment_variables() throws MojoExecutionException {
      mockedSystem.when(() -> SystemWrapper.getenv("MAVEN_OPTS")).thenReturn(null);
      scannerBootstrapper.execute();
      verify(log, never()).info(contains("MAVEN_OPTS="));
    }
  }

  private void verifyCommonCalls() {
    verify(scannerEngineFacade).isSonarQubeCloud();
    verify(scannerEngineFacade).analyze(projectProperties);
  }
}
