/*
 * SonarSource :: IT :: SonarQube Maven
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
package com.sonar.maven.it.suite;

import com.sonar.maven.it.ItUtils;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.BuildRunner;
import com.sonar.orchestrator.build.MavenBuild;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Components;
import org.sonarqube.ws.client.components.ComponentsService;
import org.sonarqube.ws.client.components.ShowRequest;
import org.sonarqube.ws.client.settings.SetRequest;
import org.sonarqube.ws.client.users.CreateRequest;

import static org.assertj.core.api.Assertions.assertThat;

class MavenTest extends AbstractMavenTest {

  private static final String MODULE_START = "------------- Run sensors on module ";

  @TempDir
  public Path temp;

  @AfterEach
  public void cleanup() {
    wsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("false"));
  }

  /**
   * See MSONAR-129
   */
  @Test
  void useUserPropertiesGlobalConfig() throws Exception {
    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal());

    File settingsXml = temp.resolve("settings.xml").toFile();
    settingsXml.createNewFile();
    Map<String, String> props = ORCHESTRATOR.getDatabase().getSonarProperties();
    props.put("sonar.host.url", ORCHESTRATOR.getServer().getUrl());
    props.put("sonar.login", ORCHESTRATOR.getDefaultAdminToken());
    FileUtils.write(settingsXml, ItUtils.createSettingsXml(props));

    build.addArgument("--settings=" + settingsXml.getAbsolutePath());
    build.addArgument("-Psonar");
    // we build without sonarqube server settings, it will need to fetch it from the profile defined in the settings xml file
    BuildResult result = runner.run(null, build);

    assertThat(result.getLogs()).contains(ORCHESTRATOR.getServer().getUrl());
  }

  /**
   * See MSONAR-129
   */
  @Test
  void supportSonarHostURLParam() {
    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-global-properties"))
      // global property should take precedence
      .setEnvironmentVariable("SONAR_HOST_URL", "http://from-env.org:9000")
      .setGoals(cleanSonarGoal());

    BuildResult result = runner.runQuietly(null, build);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getLogs()).contains("http://dummy-url.org");
  }

  /**
   * See MSONAR-172
   */
  @Test
  void supportSonarHostURLParamFromEnvironmentVariable() {
    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setEnvironmentVariable("SONAR_HOST_URL", "http://from-env.org:9000")
      .setGoals(cleanSonarGoal());

    BuildResult result = runner.runQuietly(null, build);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getLogs()).contains("http://from-env.org:9000");
  }

  /**
   * See MSONAR-130
   */
  @Test
  void structureWithRelativePaths() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-structure-relative-paths"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);
  }

  /**
   * See MSONAR-164
   */
  @Test
  void flatStructure() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-flat-layout/parent"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);
  }

  @Test
  void aggregatorInheritParent() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/aggregator-inherit-parent"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);
    assertThat(getMeasureAsInteger("org.sonarsource.maven.its:aggregator", "files")).isEqualTo(4); // 4 x pom.xml
  }

  @Test
  void aggregatorInheritParentAndSonarAttachedToPhase() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/aggregator-inherit-parent-and-bind-to-verify"))
      .setGoals("clean verify")
      .setProperty("sonar.maven.it.mojoVersion", mojoVersion().toString());
    ORCHESTRATOR.executeBuild(build);
    assertThat(getMeasureAsInteger("org.sonarsource.maven.its:aggregator", "files")).isEqualTo(4); // 4 x pom.xml
  }

  @Test
  void shouldSupportJarWithoutSources() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/project-with-module-without-sources"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.project-with-module-without-sources:parent", "files")).isEqualTo(4);
    assertThat(getComponent("com.sonarsource.it.samples.project-with-module-without-sources:parent:without-sources")).isNotNull();
  }

  /**
   * See SONAR-594
   */
  @Test
  void shouldSupportJeeProjects() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/jee"))
      .setGoals(cleanInstallSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    // src/main/webapp is analyzed by web and xml plugin
    // scanning all files, so 2 more files (ejb-module/src/main/resources/META-INF/ejb-jar.xml and web-module/src/main/webapp/index.jsp)
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.jee:parent", "files")).isEqualTo(10);
  }

  /**
   * See SONAR-222
   */
  @Test
  void shouldSupportMavenExtensions() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-extensions"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-extensions", "files")).isEqualTo(2);
  }

  /**
   * This test should be splitted. It checks multiple use-cases at the same time : SONAR-518, SONAR-519 and SONAR-593
   */
  @Test
  void testBadMavenParameters() {
    // should not fail
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-parameters"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.maven-bad-parameters:parent", "files")).isPositive();
  }

  @Test
  void shouldAnalyzeMultiModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-order"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getComponent("org.sonar.tests.modules-order:root").getName()).isEqualTo("Sonar tests - modules order");

    assertThat(getComponent("org.sonar.tests.modules-order:root:parent").getName()).isEqualTo("parent");

    assertThat(getComponent("org.sonar.tests.modules-order:root:module_a").getName()).isEqualTo("module_a");
    assertThat(getComponent("org.sonar.tests.modules-order:root:module_b").getName()).isEqualTo("module_b");

    assertThat(getComponent("org.sonar.tests.modules-order:root:module_a/src/main/java/HelloA.java").getName()).isEqualTo("HelloA.java");
    assertThat(getComponent("org.sonar.tests.modules-order:root:module_b/src/main/java/HelloB.java").getName()).isEqualTo("HelloB.java");
  }

  @Test
  void shouldEvaluateSourceVersionOnEachModule() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-source-versions"))
      .setGoals(cleanSonarGoal());
    BuildResult buildResult = ORCHESTRATOR.executeBuild(build);

    assertThat(findScanSectionOfModule(buildResult.getLogs(), "higher-version")).contains("Configured Java source version (sonar.java.source): 8");
    assertThat(findScanSectionOfModule(buildResult.getLogs(), "same-version")).contains("Configured Java source version (sonar.java.source): 6");
  }

  private String findScanSectionOfModule(String logs, String moduleName) {
    String start = MODULE_START;
    int startSection = logs.indexOf(start + moduleName);
    assertThat(startSection).isNotEqualTo(-1);
    // This will match either a next section or the end of a maven plugin execution
    int endSection = logs.indexOf("-------------", startSection + start.length());
    assertThat(endSection).isNotEqualTo(-1);

    return logs.substring(startSection, endSection);
  }

  // MSONAR-158
  @Test
  void shouldAnalyzeMultiModulesAttachedToPhase() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/attach-sonar-to-verify"))
      .setGoals("clean verify")
      .setProperty("sonar.maven.it.mojoVersion", mojoVersion().toString());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getComponent("com.sonarsource.it.samples:attach-sonar-to-verify")).isNotNull();
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:attach-sonar-to-verify", "files")).isEqualTo(11);
  }

  /**
   * See SONAR-2735
   */
  @Test
  void shouldSupportDifferentDeclarationsForModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-declaration"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getComponent("org.sonar.tests.modules-declaration:root").getName()).isEqualTo("Root");

    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_a").getName()).isEqualTo("module_a");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_b").getName()).isEqualTo("module_b");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_c").getName()).isEqualTo("module_c");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_d").getName()).isEqualTo("module_d");
    // directories get collapsed
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_e/src/main/java").getName()).isEqualTo("module_e/src/main/java");

    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_a/src/main/java/HelloA.java").getName()).isEqualTo("HelloA.java");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_b/src/main/java/HelloB.java").getName()).isEqualTo("HelloB.java");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_c/src/main/java/HelloC.java").getName()).isEqualTo("HelloC.java");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_d/src/main/java/HelloD.java").getName()).isEqualTo("HelloD.java");
    assertThat(getComponent("org.sonar.tests.modules-declaration:root:module_e/src/main/java/HelloE.java").getName()).isEqualTo("HelloE.java");
  }

  /**
   * Original ticket, see SONAR-3843
   * For MSONAR-218, we ensure that the dependency-reduced-pom.xml file is generated and not committed.
   * This allows to test that the original pom is indexed along with the generated one.
   */
  @Test
  void should_support_shade_with_dependency_reduced_pom_with_clean_package_sonar_goals() {
    File projectLocation = ItUtils.locateProjectPom("maven/shade-with-dependency-reduced-pom");

    // Set up: make sure to delete left over dependency reduced pom from previous executions
    File dependencyReducedPom = projectLocation.getParentFile().toPath().resolve("child2").resolve("dependency-reduced-pom.xml").toFile();
    if (dependencyReducedPom.exists()) {
      dependencyReducedPom.delete();
    }
    assertThat(dependencyReducedPom).doesNotExist();

    MavenBuild build = MavenBuild.create(projectLocation)
      .setGoals(cleanPackageSonarGoal());
    BuildResult result = ORCHESTRATOR.executeBuildQuietly(build);

    // Test a reduced pom has peen produced as a result of clean package
    assertThat(dependencyReducedPom).exists();

    // Test that the structure of the project could be understood from the dependency-reduced-pom.xml
    assertThat(result.getLastStatus()).isZero();
    assertThat(result.getLogs()).doesNotContain(
      "Unable to determine structure of project. Probably you use Maven Advanced Reactor Options, which is not supported by Sonar and should not be used.");

    // Test that pom.xml was indexed
    ShowRequest requestForPom = new ShowRequest();
    requestForPom.setComponent("org.foo.bar:parent:child2/pom.xml");
    Components.ShowWsResponse show = wsClient.components().show(requestForPom);
    assertThat(show.getComponent()).isNotNull();

    // Test that dependency-reduced-pom.xml was indexed
    ShowRequest requestForReducedPom = new ShowRequest();
    requestForReducedPom.setComponent("org.foo.bar:parent:child2/dependency-reduced-pom.xml");
    ComponentsService components = wsClient.components();
    assertThat(components.show(requestForReducedPom)).isNotNull();

    // Clean up
    dependencyReducedPom.delete();
  }

  /**
   * src/main/java is missing
   */
  @Test
  void maven_project_with_only_test_dir() {
    // Need package to have test execution
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir")).setGoals(cleanPackageSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-only-test-dir", "tests")).isEqualTo(1);
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-only-test-dir", "files")).isEqualTo(1);
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  void override_sources() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-override-sources")).setGoals(sonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-override-sources", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.samples:maven-override-sources:src/main/java2/Hello2.java")).isNotNull();
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  void override_sources_in_multi_module() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-modules-override-sources")).setGoals(sonarGoal());
    ORCHESTRATOR.executeBuild(build);


    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:multi-modules-sample:module_a", "files")).isEqualTo(2);
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  void override_sources_in_multi_module_aggregator() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-module-aggregator"))
      .setGoals(sonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("edu.marcelo:multi-module-aggregator:module-web/src/main/webapp", "files")).isEqualTo(2);
  }

  /**
   * The property sonar.inclusions overrides the property sonar.sources
   */
  @Test
  void inclusions_apply_to_source_dirs() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/inclusions_apply_to_source_dirs")).setGoals(sonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:inclusions_apply_to_source_dirs", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.samples:inclusions_apply_to_source_dirs:src/main/java/Hello2.java")).isNotNull();
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  void fail_if_bad_value_of_sonar_sources_property() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-sources-property")).setGoals(sonarGoal());
    BuildResult result = ORCHESTRATOR.executeBuildQuietly(build);
    assertThat(result.getLastStatus()).isNotZero();
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-sources-property:jar:1.0-SNAPSHOT. Please check the property sonar.sources");
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  void fail_if_bad_value_of_sonar_tests_property() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-tests-property")).setGoals(sonarGoal());
    BuildResult result = ORCHESTRATOR.executeBuildQuietly(build);
    assertThat(result.getLastStatus()).isNotZero();
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-tests-property:jar:1.0-SNAPSHOT. Please check the property sonar.tests");
  }

  // MSONAR-91
  @Test
  void shouldSkipModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("exclusions/skip-one-module"))
      .setGoals(cleanSonarGoal());
    ORCHESTRATOR.executeBuild(build);

    assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_a/module_a1")).isNull();
    assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_a/module_a2").getName()).isEqualTo("module_a2");
    assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_b").getName()).isEqualTo("module_b");
  }

  // MSONAR-150
  @Test
  void shouldSkipWithEnvVar() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal())
      .setProperties("sonar.host.url", "invalid")
      .setEnvironmentVariable("SONARQUBE_SCANNER_PARAMS", "{ \"sonar.scanner.skip\" : \"true\" }");
    BuildResult result = ORCHESTRATOR.executeBuild(build);
    assertThat(result.getLogs()).contains("SonarQube Scanner analysis skipped");
  }

  /**
   * MSONAR-141
   */
  @Test
  void supportMavenEncryption() throws Exception {
    wsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
    wsClient.users().create(new CreateRequest().setLogin("julien").setName("Julien").setPassword("123abc"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal());

    File securityXml = new File(this.getClass().getResource("/security-settings.xml").toURI());
    File settingsXml = new File(this.getClass().getResource("/settings-with-encrypted-sonar-password.xml").toURI());

    build.addArgument("--settings=" + settingsXml.getAbsolutePath());
    // MNG-4853
    build.addArgument("-Dsettings.security=" + securityXml.getAbsolutePath());
    build.setProperty("sonar.login", "julien");
    build.addArgument("-Psonar-password");
    ORCHESTRATOR.executeBuild(build);
  }

}
