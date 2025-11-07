/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.graph.GraphBuilder;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.building.Result;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.assertj.core.data.MapEntry;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class SonarQubeMojoTest {

  private static final String UNSPECIFIED_VERSION_WARNING_SUFFIX = "instead of an explicit plugin version may introduce breaking analysis changes at an unwanted time. " +
    "It is highly recommended to use an explicit version, e.g. 'org.sonarsource.scanner.maven:sonar-maven-plugin:";

  private static final String DEFAULT_GOAL = "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar";

  @Rule
  public MojoRule mojoRule = new MojoRule();

  private TestLog logger = new TestLog(TestLog.LogLevel.WARN);

  private SonarQubeMojo getMojo(File baseDir) throws Exception {
    return (SonarQubeMojo) mojoRule.lookupConfiguredMojo(baseDir, "sonar");
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
    executeProject("sample-project", DEFAULT_GOAL, "sonar.scanner.skip", "true");
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

  @Test
  public void project_with_java_files_not_in_src_should_not_be_collected() throws Exception {
    File baseDir = executeProject(
      "project-with-java-files-not-in-src",
      DEFAULT_GOAL,
      "sonar.maven.scanAll", "true");
    Set<String> actualListOfSources = extractSonarSources("target/dump.properties", baseDir.toPath());
    assertThat(actualListOfSources).containsExactlyInAnyOrder(
      "/pom.xml", "/src/main/java");
  }

  @Test
  public void project_with_java_files_not_in_src_should_be_collected_when_user_define_binaries_and_libraries() throws Exception {
    File baseDir = executeProject(
      "project-with-java-files-not-in-src",
      DEFAULT_GOAL,
      "sonar.maven.scanAll", "true",
      "sonar.java.binaries", "target/classes",
      "sonar.java.libraries", "target/lib/logger.jar");
    Set<String> actualListOfSources = extractSonarSources("target/dump.properties", baseDir.toPath());
    assertThat(actualListOfSources).containsExactlyInAnyOrder(
      "/pom.xml", "/src/main/java", "/Hello.java", "/Hello.kt");
  }

  @Test
  public void project_with_java_files_not_in_src_should_not_be_collected_when_user_define_only_binaries() throws Exception {
    File baseDir = executeProject(
      "project-with-java-files-not-in-src",
      DEFAULT_GOAL,
      "sonar.maven.scanAll", "true",
      "sonar.java.binaries", "target/classes");
    Set<String> actualListOfSources = extractSonarSources("target/dump.properties", baseDir.toPath());
    assertThat(actualListOfSources).containsExactlyInAnyOrder(
      "/pom.xml", "/src/main/java");
  }

  @Test
  public void project_with_java_files_not_in_src_should_not_be_collected_when_user_define_only_libraries() throws Exception {
    File baseDir = executeProject(
      "project-with-java-files-not-in-src",
      DEFAULT_GOAL,
      "sonar.maven.scanAll", "true",
      "sonar.java.libraries", "target/lib/logger.jar");
    Set<String> actualListOfSources = extractSonarSources("target/dump.properties", baseDir.toPath());
    assertThat(actualListOfSources).containsExactlyInAnyOrder(
      "/pom.xml", "/src/main/java");
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
  public void reuse_findbugs_exclusions_from_reporting() throws Exception {
    File baseDir = executeProject("project-with-findbugs-reporting");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void exclude_report_paths_from_scanAll() throws Exception {
    File projectBarDir = executeProject("project-with-external-reports", DEFAULT_GOAL, "sonar.maven.scanAll", "true");
    Set<String> actualListOfSources = extractSonarSources("target/dump.properties", projectBarDir.toPath());
    assertThat(actualListOfSources).containsExactlyInAnyOrder("/other.xml", "/pom.xml");
  }

  @Test
  public void reuse_findbugs_exclusions_from_plugin() throws Exception {
    File baseDir = executeProject("project-with-findbugs-build");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void reuse_findbugs_exclusions_from_plugin_management() throws Exception {
    File baseDir = executeProject("project-with-findbugs-plugin-management");
    assertPropsContains(entry("sonar.findbugs.excludeFilters", new File(baseDir, "findbugs-exclude.xml").getAbsolutePath()));
  }

  @Test
  public void sonar_maven_plugin_version_not_set_should_display_a_warning() throws Exception {
    executeProject("sample-project", "sonar:sonar");
    assertThat(logger.logs).anyMatch(log -> log.contains("Using an unspecified version " + UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_LATEST_in_goal_should_display_a_warning() throws Exception {
    executeProject("sample-project", "sonar:LATEST:sonar");
    assertThat(logger.logs).anyMatch(log -> log.contains("Using LATEST " + UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_RELEASE_in_goal_should_display_a_warning() throws Exception {
    executeProject("sample-project", "org.sonarsource.scanner.maven:sonar-maven-plugin:RELEASE:sonar");
    assertThat(logger.logs).anyMatch(log -> log.contains("Using RELEASE " + UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_set_in_goal_should_not_display_a_warning() throws Exception {
    executeProject("sample-project", "org.sonarsource.scanner.maven:sonar-maven-plugin:1.2.3.4:sonar");
    assertThat(logger.logs).noneMatch(log -> log.contains(UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_set_in_sonar_sonar_goal_should_not_display_a_warning() throws Exception {
    executeProject("sample-project", "sonar:1.2.3.4:sonar");
    assertThat(logger.logs).noneMatch(log -> log.contains(UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_set_in_plugin_management_should_not_display_a_warning() throws Exception {
    executeProject("project-with-sonar-plugin-management-configuration", "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar");
    assertThat(logger.logs).noneMatch(log -> log.contains(UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void sonar_maven_plugin_version_set_in_build_plugins_should_not_display_a_warning() throws Exception {
    executeProject("project-with-sonar-plugin-configuration", "org.sonarsource.scanner.maven:sonar-maven-plugin:sonar");
    assertThat(logger.logs).noneMatch(log -> log.contains(UNSPECIFIED_VERSION_WARNING_SUFFIX));
  }

  @Test
  public void cover_corner_cases_for_isPluginVersionDefinedInTheProject() {
    MavenProject project = new MavenProject();
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();

    Plugin pluginDefinition = new Plugin();
    project.getBuild().getPlugins().add(pluginDefinition);

    pluginDefinition.setGroupId(null);
    pluginDefinition.setArtifactId("sonar-maven-plugin");
    pluginDefinition.setVersion(null);
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();

    pluginDefinition.setGroupId("org.sonarsource.scanner.maven");
    pluginDefinition.setArtifactId("sonar-maven-plugin");
    pluginDefinition.setVersion(null);
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();

    pluginDefinition.setGroupId("org.sonarsource.scanner.maven");
    pluginDefinition.setArtifactId("sonar-maven-plugin");
    pluginDefinition.setVersion("  ");
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();

    pluginDefinition.setGroupId("org.sonarsource.scanner.maven");
    pluginDefinition.setArtifactId("sonar-maven-plugin");
    pluginDefinition.setVersion("1.2.3.4");
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isTrue();

    pluginDefinition.setGroupId("org.other");
    pluginDefinition.setArtifactId("sonar-maven-plugin");
    pluginDefinition.setVersion("1.2.3.4");
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();

    pluginDefinition.setGroupId("org.sonarsource.scanner.maven");
    pluginDefinition.setArtifactId("other-plugin");
    pluginDefinition.setVersion("1.2.3.4");
    assertThat(SonarQubeMojo.isPluginVersionDefinedInTheProject(project, "org.sonarsource.scanner.maven", "sonar-maven-plugin")).isFalse();
  }

  @Test
  public void verbose() throws Exception {
    logger.setLogLevel(TestLog.LogLevel.DEBUG);
    executeProject("project-with-findbugs-reporting");
    assertThat(readProps("target/dump.properties")).contains((entry("sonar.verbose", "true")));
  }

  @Test
  public void test_without_sonar_region() throws Exception {
    executeProject("sample-project", DEFAULT_GOAL);
    assertThat(readProps("target/dump.properties"))
      .contains((entry("sonar.host.url", "https://sonarcloud.io")))
      .contains((entry("sonar.scanner.apiBaseUrl", "https://api.sonarcloud.io")));
  }

  @Test
  public void test_without_sonar_region_but_sonar_host_url() throws Exception {
    executeProject("sample-project", DEFAULT_GOAL, "sonar.host.url", "https://my.sonarqube.com/sonarqube");
    assertThat(readProps("target/dump.properties"))
      .contains((entry("sonar.host.url", "https://my.sonarqube.com/sonarqube")))
      .contains((entry("sonar.scanner.apiBaseUrl", "https://my.sonarqube.com/sonarqube/api/v2")));
  }

  @Test
  public void test_without_sonar_region_but_sonar_host_url_env() throws Exception {
    var env = Map.of("SONAR_HOST_URL", "https://my.sonarqube.com/sonarqube");
    executeProject("sample-project", DEFAULT_GOAL, env);
    assertThat(readProps("target/dump.properties"))
      .contains((entry("sonar.host.url", "https://my.sonarqube.com/sonarqube")))
      .contains((entry("sonar.scanner.apiBaseUrl", "https://my.sonarqube.com/sonarqube/api/v2")));
  }

  @Test
  public void test_sonar_region_us() throws Exception {
    executeProject("sample-project", DEFAULT_GOAL, "sonar.region", "us");
    assertThat(readProps("target/dump.properties"))
      .contains((entry("sonar.host.url", "https://sonarqube.us")))
      .contains((entry("sonar.scanner.apiBaseUrl", "https://api.sonarqube.us")));
  }

  @Test
  public void test_sonar_region_us_using_env() throws Exception {
    var env = Map.of("SONAR_REGION", "us");
    executeProject("sample-project", DEFAULT_GOAL, env);
    assertThat(readProps("target/dump.properties"))
      .contains((entry("sonar.host.url", "https://sonarqube.us")))
      .contains((entry("sonar.scanner.apiBaseUrl", "https://api.sonarqube.us")));
  }

  @Test
  public void test_sonar_region_invalid() {
    assertThatThrownBy(() -> executeProject("sample-project", DEFAULT_GOAL, "sonar.region", "invalid"))
      .hasMessageContaining("Invalid region 'invalid'.");
  }

  private File executeProject(String projectName) throws Exception {
    return executeProject(projectName, DEFAULT_GOAL);
  }

  private File executeProject(String projectName, String goal, String... properties) throws Exception {
    return executeProject(projectName, goal, Collections.emptyMap(), properties);
  }

  private File executeProject(String projectName, String goal, Map<String, String> env, String... properties) throws Exception {
    File baseDir = new File("src/test/projects/" + projectName).getAbsoluteFile();
    SonarQubeMojo mojo = getMojo(baseDir);
    mojo.getSession().getRequest().setGoals(Collections.singletonList(goal));
    mojo.getSession().getProjects().get(0).setExecutionRoot(true);
    mojo.getSession().setAllProjects(mojo.getSession().getProjects());
    PluginDescriptor pluginDescriptor = mojo.getMojoExecution().getMojoDescriptor().getPluginDescriptor();
    pluginDescriptor.setPlugin(createSonarPluginFrom(pluginDescriptor, goal, mojo.getSession().getTopLevelProject()));

    Result<? extends ProjectDependencyGraph> result = mojoRule.lookup(GraphBuilder.class).build(mojo.getSession());
    mojo.getSession().setProjectDependencyGraph(result.get()); // done by maven in a normal execution

    mojo.setLog(logger);

    Properties userProperties = mojo.getSession().getUserProperties();
    if ((properties.length % 2) != 0) {
      throw new IllegalArgumentException("invalid number properties");
    }

    for (int i = 0; i < properties.length; i += 2) {
      userProperties.put(properties[i], properties[i + 1]);
    }

    // Clean environment variables. We don't want the context of the CI to interfere with the tests.
    // For example, we don't want the SONAR_HOST_URL to be set to https://next.sonarqube.com/sonarqube/
    mojo.environmentVariables.entrySet()
      .removeIf(entry -> entry.getKey().startsWith("SONAR_") || entry.getKey().startsWith("SONARQUBE_"));

    mojo.environmentVariables.putAll(env);

    mojo.execute();

    return baseDir;
  }

  private Plugin createSonarPluginFrom(PluginDescriptor pluginDescriptor, String goal, MavenProject project) {
    String version = null;
    Pattern versionPattern = Pattern.compile("(?:" +
      "sonar|" +
      "org\\.codehaus\\.mojo:sonar-maven-plugin|" +
      "org\\.sonarsource\\.scanner\\.maven:sonar-maven-plugin" +
      "):([^:]+):sonar");
    Matcher matcher = versionPattern.matcher(goal);
    if (matcher.matches()) {
      version = matcher.group(1);
    }
    if (version == null) {
      List<Plugin> plugins = new ArrayList<>(project.getBuildPlugins());
      PluginManagement pluginManagement = project.getPluginManagement();
      if (pluginManagement != null) {
        plugins.addAll(pluginManagement.getPlugins());
      }
      for (Plugin plugin : plugins) {
        String pluginGroupId = plugin.getGroupId();
        if ((pluginGroupId == null || pluginGroupId.equals(pluginDescriptor.getGroupId())) &&
          pluginDescriptor.getArtifactId().equals(plugin.getArtifactId()) &&
          plugin.getVersion() != null) {
          version = plugin.getVersion();
          break;
        }
      }
    }
    Plugin plugin = new Plugin();
    plugin.setGroupId(pluginDescriptor.getGroupId());
    plugin.setArtifactId(pluginDescriptor.getArtifactId());
    plugin.setVersion(version);
    return plugin;
  }

  @SafeVarargs
  private void assertPropsContains(MapEntry<String, String>... entries) throws IOException {
    assertThat(readProps("target/dump.properties")).contains(entries);
  }

  private static Map<String, String> readProps(String filePath) throws IOException {
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

  private static Set<String> extractSonarSources(String propertiesPath, Path projectBarDir) throws IOException {
    String sources = readProps(propertiesPath)
      .entrySet()
      .stream()
      .filter(e -> e.getKey().endsWith("sonar.sources"))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);

    return Arrays.stream(sources.split(","))
      .map(file -> file.replace(projectBarDir.toString(), "").replace("\\", "/"))
      .collect(Collectors.toSet());
  }
}
