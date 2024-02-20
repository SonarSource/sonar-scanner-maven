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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.ScanProperties;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.maven.bootstrap.ScannerBootstrapper.UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE;

public class ScannerBootstrapperTest {
  @Mock
  private Log log;

  @Mock
  private MavenSession session;

  @Mock
  private SecDispatcher securityDispatcher;

  @Mock
  private EmbeddedScanner scanner;

  @Mock
  private MavenProjectConverter mavenProjectConverter;

  @Rule
  public TemporaryFolder tmpFolder = new TemporaryFolder();

  private ScannerBootstrapper scannerBootstrapper;

  private Map<String, String> projectProperties;


  @Before
  public void setUp()
    throws MojoExecutionException, IOException {
    MockitoAnnotations.initMocks(this);

    MavenProject rootProject = mock(MavenProject.class);
    when(rootProject.isExecutionRoot()).thenReturn(true);
    when(session.getProjects()).thenReturn(Collections.singletonList(rootProject));
    when(session.getUserProperties()).thenReturn(new Properties());

    projectProperties = new HashMap<>();
    projectProperties.put(ScanProperties.PROJECT_BASEDIR, tmpFolder.getRoot().toString());
    // Create folders
    Path pom = tmpFolder.getRoot().toPath().resolve("pom.xml");
    pom.toFile().createNewFile();
    Path sourceMainDirs = tmpFolder.getRoot().toPath().resolve("src").resolve("main").resolve("java");
    sourceMainDirs.toFile().mkdirs();
    Path sourceResourceDirs = tmpFolder.getRoot().toPath().resolve("src").resolve("main").resolve("resources");
    sourceResourceDirs.toFile().mkdirs();
    Path javascriptResource = sourceResourceDirs.resolve("index.js");
    javascriptResource.toFile().createNewFile();
    projectProperties.put(ScanProperties.PROJECT_SOURCE_DIRS, sourceMainDirs.toFile().toString() + "," + pom.toFile().toString());


    when(mavenProjectConverter.configure(any(), any(), any())).thenReturn(projectProperties);

    when(scanner.mask(anyString())).thenReturn(scanner);
    when(scanner.unmask(anyString())).thenReturn(scanner);
    scannerBootstrapper = new ScannerBootstrapper(log, session, scanner, mavenProjectConverter, new PropertyDecryptor(log, securityDispatcher));
  }

  @Test
  public void testSQBefore56() {
    when(scanner.serverVersion()).thenReturn("5.1");
    try {
      scannerBootstrapper.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
        .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
    }
  }

  @Test
  public void testSQ56() throws MojoExecutionException {
    when(scanner.serverVersion()).thenReturn("5.6");
    ScannerBootstrapper mocked = Mockito.mock(ScannerBootstrapper.class);
    scannerBootstrapper.execute();

    verifyCommonCalls();

    // no extensions, mask or unmask
    verifyNoMoreInteractions(scanner);
  }

  @Test
  public void testVersionComparisonWithBuildNumber() throws MojoExecutionException {
    when(scanner.serverVersion()).thenReturn("6.3.0.12345");
    scannerBootstrapper.execute();

    assertThat(scannerBootstrapper.isVersionPriorTo("4.5")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.3")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.4")).isTrue();
  }

  @Test
  public void testNullServerVersion() {
    when(scanner.serverVersion()).thenReturn(null);

    try {
      scannerBootstrapper.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
        .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
    }
  }

  @Test
  public void scanAll_property_is_detected_and_applied() throws MojoExecutionException {
    // When sonar.scanner.scanAll is not set
    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(ScanProperties.PROJECT_SOURCE_DIRS);
    String[] sourceDirs = collectedProperties.get(ScanProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(2);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());

    // When sonar.scanner.scanAll is set explicitly to false
    Properties withScanAllSetToFalse = new Properties();
    withScanAllSetToFalse.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "false");
    when(session.getUserProperties()).thenReturn(withScanAllSetToFalse);
    collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(ScanProperties.PROJECT_SOURCE_DIRS);
    sourceDirs = collectedProperties.get(ScanProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(2);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());


    // When sonar.scanner.scanAll is set explicitly to true
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);
    collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(ScanProperties.PROJECT_SOURCE_DIRS);
    sourceDirs = collectedProperties.get(ScanProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(3);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
    assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    assertThat(sourceDirs[2]).endsWith(Paths.get("src", "main", "resources", "index.js").toString());
  }

  @Test
  public void should_not_collect_all_sources_when_sonar_sources_is_overridden() throws MojoExecutionException {
    // When sonar.scanner.scanAll is set explicitly to true
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);
    // Return the expected directory and notify of overriding
    projectProperties.put(ScanProperties.PROJECT_SOURCE_DIRS, Paths.get("src", "main", "resources").toFile().toString());
    when(mavenProjectConverter.isSourceDirsOverridden()).thenReturn(true);

    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(ScanProperties.PROJECT_SOURCE_DIRS);
    String[] sourceDirs = collectedProperties.get(ScanProperties.PROJECT_SOURCE_DIRS).split(",");
    assertThat(sourceDirs).hasSize(1);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "resources").toString());
  }

  @Test
  public void an_exception_is_logged_at_warning_level_when_failing_to_crawl_the_filesystem_to_scan_all_sources() throws MojoExecutionException, IOException {
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
  public void can_collect_sources_with_commas_in_paths() throws MojoExecutionException, IOException {
    Properties withScanAllSetToTrue = new Properties();
    withScanAllSetToTrue.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    when(session.getUserProperties()).thenReturn(withScanAllSetToTrue);

    // Create paths with commas in them
    Path root = tmpFolder.getRoot().toPath();
    Path directory = root.resolve(Paths.get("directory,with,commas"));
    directory.toFile().mkdirs();
    Path file = directory.resolve("file.properties");
    file.toFile().createNewFile();

    Map<String, String> collectedProperties = scannerBootstrapper.collectProperties();
    assertThat(collectedProperties).containsKey(ScanProperties.PROJECT_SOURCE_DIRS);
    List<String> values = MavenUtils.splitAsCsv(collectedProperties.get(ScanProperties.PROJECT_SOURCE_DIRS));
    assertThat(values).hasSize(4);
  }

  private void verifyCommonCalls() {
    verify(scanner).start();
    verify(scanner).serverVersion();
    verify(scanner).execute(projectProperties);
  }
}
