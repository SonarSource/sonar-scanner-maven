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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MavenReactorConverterTest {

  @Mock
  private Log log;

  @Mock
  private MavenSession session;

  @Mock
  private MavenProjectConverter mavenProjectConverter;

  @TempDir
  public Path tmpFolder;

  private MavenReactorConverter mavenReactorConverter;

  private Map<String, String> projectProperties;

  @BeforeEach
  void setUp() throws MojoExecutionException, IOException {
    MockitoAnnotations.openMocks(this);

    MavenProject rootProject = mock(MavenProject.class);
    when(rootProject.isExecutionRoot()).thenReturn(true);
    when(session.getProjects()).thenReturn(Collections.singletonList(rootProject));
    when(session.getUserProperties()).thenReturn(new Properties());

    projectProperties = new HashMap<>();
    projectProperties.put(SonarProperties.PROJECT_BASEDIR, tmpFolder.toAbsolutePath().toString());
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
    projectProperties.put(SonarProperties.PROJECT_SOURCE_DIRS, sourceMainDirs.toFile() + "," + pom.toFile());
    projectProperties.put(SonarProperties.PROJECT_TEST_DIRS, sourceTestDirs.toFile() + "," + pom.toFile());

    when(mavenProjectConverter.configure(any(), any(), any())).thenReturn(projectProperties);
    when(mavenProjectConverter.getEnvProperties()).thenReturn(new HashMap<>());
    when(rootProject.getProperties()).thenReturn(new Properties());

    mavenReactorConverter = new MavenReactorConverter(log, session, mavenProjectConverter);
  }

  @Test
  void scanAll_property_is_not_applied_by_default() throws MojoExecutionException {
    // When sonar.scannerEngineFacade.scanAll is not set
    verifyCollectedSources(sourceDirs -> {
      assertThat(sourceDirs).hasSize(2);
      assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
      assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    });

    verify(log, never()).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
  }

  @Test
  void scanAll_property_is_not_applied_when_set_explicitly() throws MojoExecutionException {
    setSonarScannerScanAllTo("false");

    verifyCollectedSources(sourceDirs -> {
      assertThat(sourceDirs).hasSize(2);
      assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
      assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
    });

    verify(log, never()).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
  }

  @Test
  void scanAll_property_is_applied_when_set_explicitly() throws MojoExecutionException {
    setSonarScannerScanAllTo("true");

    verifyCollectedSources(sourceDirs -> {
      assertThat(sourceDirs).hasSize(3);
      assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
      assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
      assertThat(sourceDirs[2]).endsWith(Paths.get("src", "main", "resources", "index.js").toString());
    });

    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
  }

  @Test
  void scanAll_should_also_collect_java_and_kotlin_sources_when_binaries_and_libraries_are_explicitly_set() throws MojoExecutionException {
    setSonarScannerScanAllAndBinariesAndLibraries();

    verifyCollectedSources(sourceDirs -> {
      assertThat(sourceDirs).hasSize(3);
      assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "java").toString());
      assertThat(sourceDirs[1]).endsWith(Paths.get("pom.xml").toString());
      assertThat(sourceDirs[2]).endsWith(Paths.get("src", "main", "resources", "index.js").toString());
    });

    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
  }

  @Test
  void should_not_collect_all_sources_when_sonar_sources_is_overridden() throws MojoExecutionException {
    setSonarScannerScanAllTo("true");

    // Return the expected directory and notify of overriding
    projectProperties.put(SonarProperties.PROJECT_SOURCE_DIRS, Paths.get("src", "main", "resources").toFile().toString());
    when(mavenProjectConverter.isSourceDirsOverridden()).thenReturn(true);

    verifyCollectedSources(sourceDirs -> {
      assertThat(sourceDirs).hasSize(1);
      assertThat(sourceDirs[0]).endsWith(Paths.get("src", "main", "resources").toString());
    });

    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
    verify(log, times(1)).warn("Parameter sonar.maven.scanAll is enabled but the scanner will not collect additional sources because sonar.sources has been overridden.");
  }

  @Test
  void should_not_collect_all_sources_when_sonar_tests_is_overridden() throws MojoExecutionException {
    setSonarScannerScanAllTo("true");

    // Return the expected directory and notify of overriding
    projectProperties.put(SonarProperties.PROJECT_TEST_DIRS, Paths.get("src", "test", "resources").toFile().toString());
    when(mavenProjectConverter.isTestDirsOverridden()).thenReturn(true);

    Map<String, String> collectedProperties = mavenReactorConverter.collectProperties();
    assertThat(collectedProperties).containsKey(SonarProperties.PROJECT_TEST_DIRS);
    String[] sourceDirs = collectedProperties.get(SonarProperties.PROJECT_TEST_DIRS).split(",");
    assertThat(sourceDirs).hasSize(1);
    assertThat(sourceDirs[0]).endsWith(Paths.get("src", "test", "resources").toString());

    verify(log, times(1)).info("Parameter sonar.maven.scanAll is enabled. The scanner will attempt to collect additional sources.");
    verify(log, times(1)).warn("Parameter sonar.maven.scanAll is enabled but the scanner will not collect additional sources because sonar.tests has been overridden.");
  }

  @Test
  void an_exception_is_logged_at_warning_level_when_failing_to_crawl_the_filesystem_to_scan_all_sources() throws MojoExecutionException {
    setSonarScannerScanAllTo("true");

    IOException expectedException = new IOException("This is what we expected");
    try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
      mockedFiles.when(() -> Files.walkFileTree(any(), any())).thenThrow(expectedException);
      mavenReactorConverter.collectProperties();
    }
    verify(log, times(1)).warn(expectedException);
  }

  @Test
  void can_collect_sources_with_commas_in_paths() throws MojoExecutionException, IOException {
    setSonarScannerScanAllTo("true");

    // Create paths with commas in them
    Path root = tmpFolder.toAbsolutePath();
    Path directory = root.resolve(Paths.get("directory,with,commas"));
    directory.toFile().mkdirs();
    Path file = directory.resolve("file.properties");
    file.toFile().createNewFile();

    Map<String, String> collectedProperties = mavenReactorConverter.collectProperties();
    assertThat(collectedProperties).containsKey(SonarProperties.PROJECT_SOURCE_DIRS);
    List<String> values = MavenUtils.splitAsCsv(collectedProperties.get(SonarProperties.PROJECT_SOURCE_DIRS));
    assertThat(values).hasSize(4);
  }

  private void setSonarScannerScanAllTo(String value) {
    Properties withScanAllSet = new Properties();
    withScanAllSet.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, value);
    when(session.getUserProperties()).thenReturn(withScanAllSet);
  }

  private void setSonarScannerScanAllAndBinariesAndLibraries() {
    Properties withScanAllSet = new Properties();
    withScanAllSet.put(MavenScannerProperties.PROJECT_SCAN_ALL_SOURCES, "true");
    withScanAllSet.put(MavenProjectConverter.JAVA_PROJECT_MAIN_BINARY_DIRS, "target/classes");
    withScanAllSet.put(MavenProjectConverter.JAVA_PROJECT_MAIN_LIBRARIES, "target/lib/log.jar");
    when(session.getUserProperties()).thenReturn(withScanAllSet);
  }

  private void verifyCollectedSources(Consumer<String[]> sourceDirsAssertions) throws MojoExecutionException {
    Map<String, String> collectedProperties = mavenReactorConverter.collectProperties();
    assertThat(collectedProperties).containsKey(SonarProperties.PROJECT_SOURCE_DIRS);
    String[] sourceDirs = collectedProperties.get(SonarProperties.PROJECT_SOURCE_DIRS).split(",");
    sourceDirsAssertions.accept(sourceDirs);
  }
}
