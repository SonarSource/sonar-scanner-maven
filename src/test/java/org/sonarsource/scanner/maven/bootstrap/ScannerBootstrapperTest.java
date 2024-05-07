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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sonarsource.scanner.lib.AnalysisProperties;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonarsource.scanner.lib.ScannerEngineFacade;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.maven.bootstrap.ScannerBootstrapper.UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE;

class ScannerBootstrapperTest {
  @Mock
  private Log log;

  @Mock
  private MavenSession session;

  @Mock
  private SecDispatcher securityDispatcher;

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


  @BeforeEach
  public void setUp()
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
    Path sourceResourceDirs = tmpFolder.resolve(Paths.get("src", "main", "resources"));
    sourceResourceDirs.toFile().mkdirs();
    Path javascriptResource = sourceResourceDirs.resolve("index.js");
    javascriptResource.toFile().createNewFile();
    projectProperties.put(AnalysisProperties.PROJECT_SOURCE_DIRS, sourceMainDirs.toFile().toString() + "," + pom.toFile().toString());


    when(mavenProjectConverter.configure(any(), any(), any())).thenReturn(projectProperties);
    when(mavenProjectConverter.getEnvProperties()).thenReturn(new HashMap<>());
    when(rootProject.getProperties()).thenReturn(new Properties());


    when(scannerEngineBootstrapper.bootstrap()).thenReturn(scannerEngineFacade);

    scannerBootstrapper = new ScannerBootstrapper(log, session, scannerEngineBootstrapper, mavenProjectConverter, new PropertyDecryptor(log, securityDispatcher));
  }

  @Test
  void testSQBefore56() {
    when(scannerEngineFacade.isSonarCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.1");

    MojoExecutionException exception = assertThrows(MojoExecutionException.class,
      () -> scannerBootstrapper.execute());

    assertThat(exception)
      .hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
      .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
  }

  @Test
  void testSQ56() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("5.6");
    scannerBootstrapper.execute();

    verifyCommonCalls();
  }

  @Test
  void testVersionComparisonWithBuildNumber() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("6.3.0.12345");
    scannerBootstrapper.execute();

    assertThat(scannerBootstrapper.isVersionPriorTo("4.5")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.3")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.4")).isTrue();
  }

  @Test
  void scanAll_property_is_detected_and_applied() throws MojoExecutionException {
    // When sonar.scanner.scanAll is not set
    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(AnalysisProperties.PROJECT_SOURCE_DIRS);
    String[] sourceDirs = collectedProperties.get(AnalysisProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(2);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    verify(log, never()).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");

    // When sonar.scanner.scanAll is set explicitly to false
    Properties withScanAllSetToFalse = new Properties();
    withScanAllSetToFalse.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "false");
    when(session.getUserProperties()).thenReturn(withScanAllSetToFalse);
    collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(AnalysisProperties.PROJECT_SOURCE_DIRS);
    sourceDirs = collectedProperties.get(AnalysisProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(2);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    verify(log, never()).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");


    // When sonar.scanner.scanAll is set explicitly to true
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);
    collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(AnalysisProperties.PROJECT_SOURCE_DIRS);
    sourceDirs = collectedProperties.get(AnalysisProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(3);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    assertThat(sourceDirs[2]).endsWith(Paths.get("src", "main", "resources", "index.js").toString());
    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
  }

  @Test
  void should_not_collect_all_sources_when_sonar_sources_is_overridden() throws MojoExecutionException {
    // When sonar.scanner.scanAll is set explicitly to true
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);
    // Return the expected directory and notify of overriding
    projectProperties.put(AnalysisProperties.PROJECT_SOURCE_DIRS, Paths.get("src", "main", "resources").toFile().toString());
    when(mavenProjectConverter.isSourceDirsOverridden()).thenReturn(true);

    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(AnalysisProperties.PROJECT_SOURCE_DIRS);
    String[] sourceDirs = collectedProperties.get(AnalysisProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(1);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "resources").toString());

    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
    verify(log, times(1)).warn("Parameter sonar.maven.scanAll is enabled but the scanner will not collect additional sources because sonar.sources has been overridden.");
  }

  @Test
  void an_exception_is_logged_at_warning_level_when_failing_to_crawl_the_filesystem_to_scan_all_sources() throws MojoExecutionException, IOException {
    // Enabling the scanAll option explicitly as a scanner option
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);

    IOException expectedException = new IOException("This is what we expected");
    try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
      mockedFiles.when(() -> Files.walkFileTree(any(), any())).thenThrow(expectedException);
      scannerBootstrapper.collectProperties();
    }
    verify(log, times(1)).warn(expectedException);
  }

  @Test
  void can_collect_sources_with_commas_in_paths() throws MojoExecutionException, IOException {
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);

    // Create paths with commas in them
    Path root = tmpFolder.toAbsolutePath();
    Path directory = root.resolve(Paths.get("directory,with,commas"));
    directory.toFile().mkdirs();
    Path file = directory.resolve("file.properties");
    file.toFile().createNewFile();

    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(AnalysisProperties.PROJECT_SOURCE_DIRS);
    List<String> values = MavenUtils.splitAsCsv(collectedProperties.get(AnalysisProperties.PROJECT_SOURCE_DIRS));
    assertThat(values).hasSize(4);
  }

  @Test
  void test_logging_SQ_version() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarCloud()).thenReturn(false);
    when(scannerEngineFacade.getServerVersion()).thenReturn("10.5");
    scannerBootstrapper.execute();

    verify(log).info("Communicating with SonarQube Server 10.5");
  }

  @Test
  void test_not_logging_the_version_when_sonarcloud_is_used() throws MojoExecutionException {
    when(scannerEngineFacade.isSonarCloud()).thenReturn(true);

    scannerBootstrapper.execute();

    verify(log).info("Communicating with SonarCloud");
    verify(log, never()).info("Communicating with SonarQube Server 8.0");
  }

  @Nested
  class EnvironmentInformation {
    MockedStatic<SystemWrapper> mockedSystem;

    @BeforeEach
    void before() {
      when(scannerEngineFacade.isSonarCloud()).thenReturn(false);
      when(scannerEngineFacade.getServerVersion()).thenReturn("9.9");
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
    verify(scannerEngineFacade).isSonarCloud();
    verify(scannerEngineFacade).analyze(projectProperties);
  }
}
