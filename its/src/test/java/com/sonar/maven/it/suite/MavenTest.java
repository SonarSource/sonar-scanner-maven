/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009 ${owner}
 * sonarqube@googlegroups.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.sonar.maven.it.suite;

import org.apache.commons.io.FileUtils;
import com.sonar.orchestrator.build.BuildRunner;
import com.sonar.maven.it.ItUtils;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.Sonar;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

public class MavenTest extends AbstractMavenTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void deleteData() {
    orchestrator.resetData();
  }

  @Test
  /**
   * See MSONAR-129
   */
  public void useUserPropertiesGlobalConfig() throws Exception {
    BuildRunner runner = new BuildRunner(orchestrator.getConfiguration(), orchestrator.getDatabase());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal());

    File settingsXml = temp.newFile();
    Map<String, String> props = orchestrator.getDatabase().getSonarProperties();
    props.put("sonar.host.url", orchestrator.getServer().getUrl());
    FileUtils.write(settingsXml, ItUtils.createSettingsXml(props));

    build.addArgument("--settings=" + settingsXml.getAbsolutePath());
    build.addArgument("-Psonar");
    // we build without sonarqube server settings, it will need to fetch it from the profile defined in the settings xml file
    BuildResult result = runner.run(null, build);

    assertThat(result.getLogs()).contains(orchestrator.getServer().getUrl());
  }

  @Test
  /**
   * See MSONAR-129
   */
  public void supportSonarHostURLParam() {
    BuildRunner runner = new BuildRunner(orchestrator.getConfiguration(), orchestrator.getDatabase());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-global-properties"))
      .setGoals(cleanSonarGoal());

    BuildResult result = runner.runQuietly(null, build);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getLogs()).contains("http://dummy-url.org");
  }

  @Test
  public void shouldSupportJarWithoutSources() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/project-with-module-without-sources"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient()
      .find(ResourceQuery.createForMetrics("com.sonarsource.it.samples.project-with-module-without-sources:parent", "files"));
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.5") && mojoVersion().isGreaterThanOrEquals("2.4")) {
      assertThat(project.getMeasureIntValue("files")).isEqualTo(3);
    } else {
      assertThat(project.getMeasureIntValue("files")).isEqualTo(1);
    }

    Resource subProject = orchestrator.getServer().getWsClient().find(ResourceQuery.create("com.sonarsource.it.samples.project-with-module-without-sources:without-sources"));
    assertThat(subProject).isNotNull();
  }
  
  /**
   * See SONAR-594
   */
  @Test
  public void shouldSupportJeeProjects() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/jee"))
      .setGoals(cleanInstallSonarGoal())
      .setProperty("sonar.lang.patterns.web", "**/*.jsp");
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples.jee:parent", "files"));

    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.5") && mojoVersion().isGreaterThanOrEquals("2.4")) {
      // src/main/webapp is analyzed by web and xml plugin
      assertThat(project.getMeasureIntValue("files")).isEqualTo(8);
    } else {
      assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
    }

    List<Resource> modules = orchestrator.getServer().getWsClient().findAll(ResourceQuery.create("com.sonarsource.it.samples.jee:parent").setDepth(-1).setQualifiers("BRC"));
    assertThat(modules).hasSize(4);
  }

  /**
   * See SONAR-222
   */
  @Test
  public void shouldSupportMavenExtensions() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-extensions"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples:maven-extensions", "files"));
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.5") && mojoVersion().isGreaterThanOrEquals("2.4")) {
      assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
    } else {
      assertThat(project.getMeasureIntValue("files")).isEqualTo(1);
    }
  }

  /**
   * This test should be splitted. It checks multiple use-cases at the same time : SONAR-518, SONAR-519 and SONAR-593
   */
  @Test
  public void testBadMavenParameters() {
    // should not fail
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-parameters"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples.maven-bad-parameters:parent", "files"));
    assertThat(project.getMeasureIntValue("files")).isGreaterThan(0);
  }

  @Test
  public void shouldAnalyzeMultiModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-order"))
      .setGoals(cleanSonarGoal())
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    Sonar sonar = orchestrator.getServer().getWsClient();
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:root")).getName()).isEqualTo("Sonar tests - modules order");

    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:parent")).getName()).isEqualTo("Parent");

    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:module_a")).getName()).isEqualTo("Module A");
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:module_b")).getName()).isEqualTo("Module B");

    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:module_a:src/main/java/HelloA.java")).getName()).isEqualTo("HelloA.java");
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-order:module_b:src/main/java/HelloB.java")).getName()).isEqualTo("HelloB.java");
    }
  }

  /**
   * See SONAR-2735
   */
  @Test
  public void shouldSupportDifferentDeclarationsForModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-declaration"))
      .setGoals(cleanSonarGoal())
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);
    Sonar sonar = orchestrator.getServer().getWsClient();

    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:root")).getName()).isEqualTo("Root");

    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_a")).getName()).isEqualTo("Module A");
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_b")).getName()).isEqualTo("Module B");
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_c")).getName()).isEqualTo("Module C");
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_d")).getName()).isEqualTo("Module D");
    assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_e")).getName()).isEqualTo("Module E");

    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_a:src/main/java/HelloA.java")).getName()).isEqualTo("HelloA.java");
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_b:src/main/java/HelloB.java")).getName()).isEqualTo("HelloB.java");
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_c:src/main/java/HelloC.java")).getName()).isEqualTo("HelloC.java");
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_d:src/main/java/HelloD.java")).getName()).isEqualTo("HelloD.java");
      assertThat(sonar.find(new ResourceQuery("org.sonar.tests.modules-declaration:module_e:src/main/java/HelloE.java")).getName()).isEqualTo("HelloE.java");
    }
  }

  @Test
  public void build_helper_plugin_should_add_dirs_when_dynamic_analysis() {
    // sonar.phase is no more supported in SQ 4.3
    assumeFalse(orchestrator.getServer().version().isGreaterThanOrEquals("4.3"));
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/many-source-dirs"))
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.dynamicAnalysis", "true");
    orchestrator.executeBuild(build);

    checkBuildHelperFiles();
    checkBuildHelperTestFiles();
  }

  /**
   * There was a regression in 2.9 and 2.10, which was fixed in 2.10.1 and 2.11 - SONAR-2744
   */
  @Test
  public void build_helper_plugin_should_add_dirs_when_static_analysis() {
    // sonar.phase is no more supported in SQ 4.3
    assumeFalse(orchestrator.getServer().version().isGreaterThanOrEquals("4.3"));
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/many-source-dirs"))
      .setGoals(cleanSonarGoal())
      .setProperty("sonar.dynamicAnalysis", "false");
    orchestrator.executeBuild(build);

    checkBuildHelperFiles();
  }

  /**
   * See SONAR-3843
   */
  @Test
  public void should_support_shade_with_dependency_reduced_pom_with_clean_install_sonar_goals() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/shade-with-dependency-reduced-pom"))
      .setProperty("sonar.dynamicAnalysis", "false")
      .setGoals(cleanInstallSonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getStatus()).isEqualTo(0);
    assertThat(result.getLogs()).doesNotContain(
      "Unable to determine structure of project. Probably you use Maven Advanced Reactor Options, which is not supported by Sonar and should not be used.");
  }

  /**
   * src/main/java is missing
   */
  @Test
  public void maven_project_with_only_test_dir() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir")).setGoals(cleanPackageSonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples:maven-only-test-dir", "tests", "files"));
    assertThat(project.getMeasureIntValue("tests")).isEqualTo(1);
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.5") && mojoVersion().isGreaterThanOrEquals("2.4")) {
      assertThat(project.getMeasure("files").getIntValue()).isEqualTo(1);
    } else {
      assertThat(project.getMeasure("files")).isNull();
    }
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources() {
    assumeTrue(orchestrator.getServer().version().isGreaterThanOrEquals("4.2"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-override-sources")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples:maven-override-sources", "files"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(1);

    Resource file = orchestrator.getServer().getWsClient().find(ResourceQuery.create("com.sonarsource.it.samples:maven-override-sources:src/main/java2/Hello2.java"));
    assertThat(file).isNotNull();
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources_in_multi_module() {
    assumeTrue(mojoVersion().isGreaterThanOrEquals("2.4"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-modules-override-sources")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples:module_a1", "files"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(1);
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources_in_multi_module_aggregator() {
    assumeTrue(mojoVersion().isGreaterThanOrEquals("2.4") && orchestrator.getServer().version().isGreaterThanOrEquals("4.3"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-module-aggregator")).setGoals(sonarGoal())
      .setProperty("sonar.lang.patterns.web", "**/*.jsp");
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("edu.marcelo:module-web", "files"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
  }

  /**
   * The property sonar.inclusions overrides the property sonar.sources
   */
  @Test
  public void inclusions_apply_to_source_dirs() {
    assumeTrue(orchestrator.getServer().version().isGreaterThanOrEquals("4.2"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/inclusions_apply_to_source_dirs")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics("com.sonarsource.it.samples:inclusions_apply_to_source_dirs", "files"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(1);

    Resource file = orchestrator.getServer().getWsClient().find(ResourceQuery.create("com.sonarsource.it.samples:inclusions_apply_to_source_dirs:src/main/java/Hello2.java"));
    assertThat(file).isNotNull();
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  public void fail_if_bad_value_of_sonar_sources_property() {
    assumeTrue(orchestrator.getServer().version().isGreaterThanOrEquals("4.2"));
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-sources-property")).setGoals(sonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getStatus()).isNotEqualTo(0);
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-sources-property:jar:1.0-SNAPSHOT. Please check the property sonar.sources");
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  public void fail_if_bad_value_of_sonar_tests_property() {
    assumeTrue(orchestrator.getServer().version().isGreaterThanOrEquals("4.2"));
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-tests-property")).setGoals(sonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getStatus()).isNotEqualTo(0);
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-tests-property:jar:1.0-SNAPSHOT. Please check the property sonar.tests");
  }

  // MSONAR-83
  @Test
  public void shouldPopulateLibraries() throws IOException {
    assumeTrue(mojoVersion().isGreaterThanOrEquals("2.4") && orchestrator.getServer().version().isGreaterThanOrEquals("4.3"));

    File outputProps = temp.newFile();
    File projectPom = ItUtils.locateProjectPom("shared/struts-1.3.9-diet");
    MavenBuild build = MavenBuild.create(projectPom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonarRunner.dumpToFile", outputProps.getAbsolutePath());
    orchestrator.executeBuild(build);

    String[] moduleIds = getProps(outputProps).getProperty("sonar.modules").split(",");
    String strutsCoreModuleId = null;
    for (String moduleId : moduleIds) {
      if (getProps(outputProps).getProperty(moduleId + ".sonar.moduleKey").equals("org.apache.struts:struts-core")) {
        strutsCoreModuleId = moduleId;
        break;
      }
    }
    assertThat(strutsCoreModuleId).isNotNull();
    if (mojoVersion().isGreaterThanOrEquals("2.5")) {
      assertThat(getProps(outputProps).getProperty(strutsCoreModuleId + ".sonar.java.libraries")).contains("antlr-2.7.2.jar");
    }
    assertThat(getProps(outputProps).getProperty(strutsCoreModuleId + ".sonar.libraries")).contains("antlr-2.7.2.jar");
  }

  // MSONAR-91
  @Test
  public void shouldSkipModules() {
    assumeTrue(mojoVersion().isGreaterThanOrEquals("2.5"));
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("exclusions/skip-one-module"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    Sonar sonar = orchestrator.getServer().getWsClient();

    assertThat(sonar.find(new ResourceQuery("com.sonarsource.it.samples:module_a1"))).isNull();
    assertThat(sonar.find(new ResourceQuery("com.sonarsource.it.samples:module_a2")).getName()).isEqualTo("Sub-module A2");
    assertThat(sonar.find(new ResourceQuery("com.sonarsource.it.samples:module_b")).getName()).isEqualTo("Module B");

  }

  private Properties getProps(File outputProps)
    throws FileNotFoundException, IOException {
    FileInputStream fis = null;
    try {
      Properties props = new Properties();
      fis = new FileInputStream(outputProps);
      props.load(fis);
      return props;
    } finally {
      IOUtils.closeQuietly(fis);
    }
  }

  private void checkBuildHelperFiles() {
    Resource project = getResource("com.sonarsource.it.samples:many-source-dirs");
    assertThat(project).isNotNull();
    assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(getResource("com.sonarsource.it.samples:many-source-dirs:src/main/java/FirstClass.java")).isNotNull();
      assertThat(getResource("com.sonarsource.it.samples:many-source-dirs:src/main/java2/SecondClass.java")).isNotNull();
    } else {
      assertThat(getResource("com.sonarsource.it.samples:many-source-dirs:[default].FirstClass")).isNotNull();
      assertThat(getResource("com.sonarsource.it.samples:many-source-dirs:[default].SecondClass")).isNotNull();
    }
  }

  private void checkBuildHelperTestFiles() {
    Resource project = getResource("com.sonarsource.it.samples:many-source-dirs");
    assertThat(project).isNotNull();
    assertThat(project.getMeasureIntValue("tests")).isEqualTo(2);
  }

  private Resource getResource(String key) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(key, "files", "tests"));
  }

}
