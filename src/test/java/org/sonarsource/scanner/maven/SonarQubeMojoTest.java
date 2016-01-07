/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.scanner.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.MojoRule;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarQubeMojoTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public MojoRule mojoRule = new MojoRule();

  private Log mockedLogger;

  private SonarQubeMojo getMojo(File baseDir)
    throws Exception {
    return (SonarQubeMojo) mojoRule.lookupConfiguredMojo(baseDir, "sonar");
  }

  @Before
  public void setUpMocks() {
    mockedLogger = mock(Log.class);
  }

  @Test
  public void executeMojo()
    throws Exception {
    File baseDir = executeProject("sample-project");

    // passed in the properties of the profile and project
    assertGlobalPropsContains(entry("sonar.host.url1", "http://myserver:9000"));
    assertGlobalPropsContains(entry("sonar.host.url2", "http://myserver:9000"));
  }

  @Test
  public void shouldExportBinaries()
    throws Exception {
    File baseDir = executeProject("sample-project");

    assertPropsContains(entry("sonar.binaries", new File(baseDir, "target/classes").getAbsolutePath()));
  }

  @Test
  public void shouldExportDefaultWarWebSource()
    throws Exception {
    File baseDir = executeProject("sample-war-project");
    assertPropsContains(entry("sonar.sources",
      new File(baseDir, "src/main/webapp").getAbsolutePath() + ","
        + new File(baseDir, "pom.xml").getAbsolutePath() + ","
        + new File(baseDir, "src/main/java").getAbsolutePath()));
  }

  @Test
  public void shouldExportOverridenWarWebSource()
    throws Exception {
    File baseDir = executeProject("war-project-override-web-dir");
    assertPropsContains(entry("sonar.sources",
      new File(baseDir, "web").getAbsolutePath() + ","
        + new File(baseDir, "pom.xml").getAbsolutePath() + ","
        + new File(baseDir, "src/main/java").getAbsolutePath()));
  }

  @Test
  public void shouldExportDependencies()
    throws Exception {
    File baseDir = executeProject("export-dependencies");

    Properties outProps = readProps("target/dump.properties");
    String libJson = outProps.getProperty("sonar.maven.projectDependencies");

    JSONAssert.assertEquals("[{\"k\":\"commons-io:commons-io\",\"v\":\"2.4\",\"s\":\"compile\",\"d\":["
      + "{\"k\":\"commons-lang:commons-lang\",\"v\":\"2.6\",\"s\":\"compile\",\"d\":[]}" + "]},"
      + "{\"k\":\"junit:junit\",\"v\":\"3.8.1\",\"s\":\"test\",\"d\":[]}]", libJson, true);

    assertThat(outProps.getProperty("sonar.java.binaries")).isEqualTo(new File(baseDir,
      "target/classes").getAbsolutePath());
    assertThat(outProps.getProperty("sonar.java.test.binaries")).isEqualTo(new File(baseDir,
      "target/test-classes").getAbsolutePath());
  }

  // MSONAR-135
  @Test
  public void shouldExportDependenciesWithSystemScopeTransitive()
    throws Exception {
    executeProject("system-scope");

    Properties outProps = readProps("target/dump.properties");
    String libJson = outProps.getProperty("sonar.maven.projectDependencies");

    JSONAssert.assertEquals("[{\"k\":\"org.codehaus.xfire:xfire-core\",\"v\":\"1.2.6\",\"s\":\"compile\","
      + "\"d\":[{\"k\":\"javax.activation:activation\",\"v\":\"1.1.1\",\"s\":\"system\",\"d\":[]}]}]", libJson,
      true);
  }

  // MSONAR-113
  @Test
  public void shouldExportSurefireReportsPath()
    throws Exception {

    File baseDir = executeProject("sample-project-with-surefire");
    assertPropsContains(entry("sonar.junit.reportsPath",
      new File(baseDir, "target/surefire-reports").getAbsolutePath()));
  }

  // MSONAR-113
  @Test
  public void shouldExportSurefireCustomReportsPath()
    throws Exception {
    File baseDir = executeProject("sample-project-with-custom-surefire-path");
    assertPropsContains(entry("sonar.junit.reportsPath",
      new File(baseDir, "target/tests").getAbsolutePath()));
  }

  @Test
  public void findbugsExcludeFile()
    throws IOException, Exception {
    executeProject("project-with-findbugs");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", "findbugs-exclude.xml"));
    assertThat(readProps("target/dump.properties.global")).doesNotContain((entry("sonar.verbose",
      "true")));

  }

  @Test
  public void verbose()
    throws Exception {
    when(mockedLogger.isDebugEnabled()).thenReturn(true);
    executeProject("project-with-findbugs");
    verify(mockedLogger, atLeastOnce()).isDebugEnabled();
    assertThat(readProps("target/dump.properties.global")).contains((entry("sonar.verbose", "true")));
  }

  private File executeProject(String projectName)
    throws Exception {
    ArtifactRepository artifactRepo = new DefaultArtifactRepository("local",
      this.getClass().getResource("SonarQubeMojoTest/repository").toString(),
      new DefaultRepositoryLayout());

    File baseDir = new File("src/test/resources/org/sonarsource/scanner/maven/SonarQubeMojoTest/" + projectName);
    SonarQubeMojo mojo = getMojo(baseDir);
    mojo.getSession().getSortedProjects().get(0).setExecutionRoot(true);
    mojo.setLocalRepository(artifactRepo);
    mojo.setLog(mockedLogger);

    mojo.execute();

    return baseDir;
  }

  private void assertPropsContains(MapEntry... entries)
    throws FileNotFoundException, IOException {
    assertThat(readProps("target/dump.properties")).contains(entries);
  }

  private void assertGlobalPropsContains(MapEntry entries)
    throws FileNotFoundException, IOException {
    assertThat(readProps("target/dump.properties.global")).contains(entries);
  }

  private Properties readProps(String filePath)
    throws FileNotFoundException, IOException {
    FileInputStream fis = null;
    try {
      File dump = new File(filePath);
      Properties props = new Properties();
      fis = new FileInputStream(dump);
      props.load(fis);
      return props;
    } finally {
      IOUtils.closeQuietly(fis);
    }
  }

}
