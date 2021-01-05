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
package org.sonarsource.scanner.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.graph.GraphBuilder;
import org.apache.maven.model.building.Result;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.MojoRule;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarQubeMojoTest {

  @Rule
  public MojoRule mojoRule = new MojoRule();

  private Log mockedLogger;

  private SonarQubeMojo getMojo(File baseDir) throws Exception {
    return (SonarQubeMojo) mojoRule.lookupConfiguredMojo(baseDir, "sonar");
  }

  @Before
  public void setUpMocks() {
    mockedLogger = mock(Log.class);
  }

  @Test
  public void executeMojo() throws Exception {
    executeProject("sample-project");

    // passed in the properties of the profile and project
    assertPropsContains(entry("sonar.host.url1", "http://myserver:9000"));
    assertPropsContains(entry("sonar.host.url2", "http://myserver:9000"));
  }

  @Test
  public void should_skip() throws Exception {
    File propsFile = new File("target/dump.properties");
    propsFile.delete();
    executeProject("sample-project", "sonar.scanner.skip", "true");
    assertThat(propsFile).doesNotExist();
  }

  @Test
  public void shouldExportBinaries() throws Exception {
    File baseDir = executeProject("sample-project");

    assertPropsContains(entry("sonar.binaries", new File(baseDir, "target/classes").getAbsolutePath()));
  }

  @Test
  public void shouldExportDefaultWarWebSource() throws Exception {
    File baseDir = executeProject("sample-war-project");
    assertPropsContains(entry("sonar.sources",
      new File(baseDir, "src/main/webapp").getAbsolutePath() + ","
        + new File(baseDir, "pom.xml").getAbsolutePath() + ","
        + new File(baseDir, "src/main/java").getAbsolutePath()));
  }

  @Test
  public void shouldExportOverridenWarWebSource() throws Exception {
    File baseDir = executeProject("war-project-override-web-dir");
    assertPropsContains(entry("sonar.sources",
      new File(baseDir, "web").getAbsolutePath() + ","
        + new File(baseDir, "pom.xml").getAbsolutePath() + ","
        + new File(baseDir, "src/main/java").getAbsolutePath()));
  }

  // MSONAR-113
  @Test
  public void shouldExportSurefireReportsPath() throws Exception {

    File baseDir = executeProject("sample-project-with-surefire");
    assertPropsContains(entry("sonar.junit.reportsPath", new File(baseDir, "target/surefire-reports").getAbsolutePath()));
    assertPropsContains(entry("sonar.junit.reportPaths", new File(baseDir, "target/surefire-reports").getAbsolutePath()));
  }

  // MSONAR-113
  @Test
  public void shouldExportSurefireCustomReportsPath() throws Exception {
    File baseDir = executeProject("sample-project-with-custom-surefire-path");
    assertPropsContains(entry("sonar.junit.reportsPath", new File(baseDir, "target/tests").getAbsolutePath()));
    assertPropsContains(entry("sonar.junit.reportPaths", new File(baseDir, "target/tests").getAbsolutePath()));
  }

  @Test
  public void reuse_findbugs_exclusions_from_reporting() throws IOException, Exception {
    File baseDir = executeProject("project-with-findbugs-reporting");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void reuse_findbugs_exclusions_from_plugin() throws IOException, Exception {
    File baseDir = executeProject("project-with-findbugs-build");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void reuse_findbugs_exclusions_from_plugin_management() throws IOException, Exception {
    File baseDir = executeProject("project-with-findbugs-plugin-management");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void verbose() throws Exception {
    when(mockedLogger.isDebugEnabled()).thenReturn(true);
    executeProject("project-with-findbugs-reporting");
    verify(mockedLogger, atLeastOnce()).isDebugEnabled();
    assertThat(readProps("target/dump.properties")).contains((entry("sonar.verbose", "true")));
  }

  private File executeProject(String projectName, String... properties) throws Exception {

    File baseDir = new File("src/test/projects/" + projectName).getAbsoluteFile();
    SonarQubeMojo mojo = getMojo(baseDir);
    mojo.getSession().getProjects().get(0).setExecutionRoot(true);
    mojo.getSession().setAllProjects(mojo.getSession().getProjects());

    Result<? extends ProjectDependencyGraph> result = mojoRule.lookup(GraphBuilder.class).build(mojo.getSession());
    mojo.getSession().setProjectDependencyGraph(result.get()); // done by maven in a normal execution

    mojo.setLog(mockedLogger);

    Properties userProperties = mojo.getSession().getUserProperties();
    if ((properties.length % 2) != 0) {
      throw new IllegalArgumentException("invalid number properties");
    }

    for (int i = 0; i < properties.length; i += 2) {
      userProperties.put(properties[i], properties[i + 1]);
    }

    mojo.execute();

    return baseDir;
  }

  @SafeVarargs
  private final void assertPropsContains(MapEntry<String, String>... entries) throws FileNotFoundException, IOException {
    assertThat(readProps("target/dump.properties")).contains(entries);
  }

  private Map<String, String> readProps(String filePath) throws FileNotFoundException, IOException {
    FileInputStream fis = null;
    try {
      File dump = new File(filePath);
      Properties props = new Properties();
      fis = new FileInputStream(dump);
      props.load(fis);
      return (Map) props;
    } finally {
      IOUtils.closeQuietly(fis);
    }
  }

}
