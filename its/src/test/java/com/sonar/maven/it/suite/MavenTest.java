/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2019 SonarSource SA
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.user.UserParameters;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.client.settings.SetRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class MavenTest extends AbstractMavenTest {

  private static final String MODULE_START_7_6 = "------------- Run sensors on module ";
  private static final String MODULE_START = "-------------  Scan ";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void deleteData() {
    orchestrator.resetData();
  }

  @After
  public void cleanup() {
    ItUtils.newAdminWsClient(orchestrator).settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("false"));
  }

  /**
   * See MSONAR-129
   */
  @Test
  public void useUserPropertiesGlobalConfig() throws Exception {
    BuildRunner runner = new BuildRunner(orchestrator.getConfiguration());
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

  /**
   * See MSONAR-129
   */
  @Test
  public void supportSonarHostURLParam() {
    BuildRunner runner = new BuildRunner(orchestrator.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-global-properties"))
      //global property should take precedence
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
  public void supportSonarHostURLParamFromEnvironmentVariable() {
    BuildRunner runner = new BuildRunner(orchestrator.getConfiguration());
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
  public void structureWithRelativePaths() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-structure-relative-paths"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);
  }

  /**
   * See MSONAR-164
   */
  @Test
  public void flatStructure() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-flat-layout/parent"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);
  }

  @Test
  public void aggregatorInheritParent() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/aggregator-inherit-parent"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);
    assertThat(getMeasureAsInteger("org.sonarsource.maven.its:aggregator", "files")).isEqualTo(4); // 4 x pom.xml
  }

  @Test
  public void aggregatorInheritParentAndSonarAttachedToPhase() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/aggregator-inherit-parent-and-bind-to-verify"))
      .setGoals("clean verify")
      .setProperty("sonar.maven.it.mojoVersion", mojoVersion().toString());
    orchestrator.executeBuild(build);
    assertThat(getMeasureAsInteger("org.sonarsource.maven.its:aggregator", "files")).isEqualTo(4); // 4 x pom.xml
  }

  @Test
  public void shouldSupportJarWithoutSources() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/project-with-module-without-sources"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.project-with-module-without-sources:parent", "files")).isEqualTo(4);
    if (hasModules()) {
      assertThat(getComponent("com.sonarsource.it.samples.project-with-module-without-sources:without-sources")).isNotNull();
    } else {
      assertThat(getComponent("com.sonarsource.it.samples.project-with-module-without-sources:parent:without-sources")).isNotNull();
    }
  }

  /**
   * See SONAR-594
   */
  @Test
  public void shouldSupportJeeProjects() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/jee"))
      .setGoals(cleanInstallSonarGoal());
    orchestrator.executeBuild(build);

    // src/main/webapp is analyzed by web and xml plugin
    // including resources, so one more file (ejb-module/src/main/resources/META-INF/ejb-jar.xml)
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.jee:parent", "files")).isEqualTo(9);

    if (hasModules()) {
      List<Component> modules = getModules("com.sonarsource.it.samples.jee:parent");
      assertThat(modules).hasSize(4);
    }
  }

  /**
   * See SONAR-222
   */
  @Test
  public void shouldSupportMavenExtensions() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-extensions"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-extensions", "files")).isEqualTo(2);
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

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples.maven-bad-parameters:parent", "files")).isGreaterThan(0);
  }

  @Test
  public void shouldAnalyzeMultiModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-order"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getComponent("org.sonar.tests.modules-order:root")).isEqualTo("Sonar tests - modules order");

    if (hasModules()) {
      assertThat(getComponent("org.sonar.tests.modules-order:parent").getName()).isEqualTo("Parent");

      assertThat(getComponent("org.sonar.tests.modules-order:module_a").getName()).isEqualTo("Module A");
      assertThat(getComponent("org.sonar.tests.modules-order:module_b").getName()).isEqualTo("Module B");

      assertThat(getComponent("org.sonar.tests.modules-order:module_a:src/main/java/HelloA.java").getName()).isEqualTo("HelloA.java");
      assertThat(getComponent("org.sonar.tests.modules-order:module_b:src/main/java/HelloB.java").getName()).isEqualTo("HelloB.java");
    } else {

      assertThat(getComponent("org.sonar.tests.modules-order:root:parent").getName()).isEqualTo("parent");

      assertThat(getComponent("org.sonar.tests.modules-order:root:module_a").getName()).isEqualTo("module_a");
      assertThat(getComponent("org.sonar.tests.modules-order:root:module_b").getName()).isEqualTo("module_b");

      assertThat(getComponent("org.sonar.tests.modules-order:root:module_a/src/main/java/HelloA.java").getName()).isEqualTo("HelloA.java");
      assertThat(getComponent("org.sonar.tests.modules-order:root:module_b/src/main/java/HelloB.java").getName()).isEqualTo("HelloB.java");
    }
  }

  @Test
  public void shouldEvaluateSourceVersionOnEachModule() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-source-versions"))
      .setGoals(cleanSonarGoal());
    BuildResult buildResult = orchestrator.executeBuild(build);

    assertThat(findScanSectionOfModule(buildResult.getLogs(), "higher-version")).contains("Configured Java source version (sonar.java.source): 8");
    assertThat(findScanSectionOfModule(buildResult.getLogs(), "same-version")).contains("Configured Java source version (sonar.java.source): 6");
  }

  private String findScanSectionOfModule(String logs, String moduleName) {
    String start = hasModules() ? MODULE_START : MODULE_START_7_6;
    int startSection = logs.indexOf(start + moduleName);
    assertThat(startSection).isNotEqualTo(-1);
    // This will match either a next section or the end of a maven plugin execution
    int endSection = logs.indexOf("-------------", startSection + start.length());
    assertThat(endSection).isNotEqualTo(-1);

    return logs.substring(startSection, endSection);
  }

  // MSONAR-158
  @Test
  public void shouldAnalyzeMultiModulesAttachedToPhase() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/attach-sonar-to-verify"))
      .setGoals("clean verify")
      .setProperty("sonar.maven.it.mojoVersion", mojoVersion().toString());
    orchestrator.executeBuild(build);

    assertThat(getComponent("com.sonarsource.it.samples:attach-sonar-to-verify")).isNotNull();
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:attach-sonar-to-verify", "files")).isEqualTo(11);

    if (hasModules()) {
      List<Component> modules = getModules("com.sonarsource.it.samples:attach-sonar-to-verify");
      assertThat(modules).hasSize(6);
    }
  }

  /**
   * See SONAR-2735
   */
  @Test
  public void shouldSupportDifferentDeclarationsForModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/modules-declaration"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getComponent("org.sonar.tests.modules-declaration:root").getName()).isEqualTo("Root");

    if (hasModules()) {
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_a").getName()).isEqualTo("Module A");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_b").getName()).isEqualTo("Module B");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_c").getName()).isEqualTo("Module C");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_d").getName()).isEqualTo("Module D");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_e").getName()).isEqualTo("Module E");

      assertThat(getComponent("org.sonar.tests.modules-declaration:module_a:src/main/java/HelloA.java").getName()).isEqualTo("HelloA.java");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_b:src/main/java/HelloB.java").getName()).isEqualTo("HelloB.java");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_c:src/main/java/HelloC.java").getName()).isEqualTo("HelloC.java");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_d:src/main/java/HelloD.java").getName()).isEqualTo("HelloD.java");
      assertThat(getComponent("org.sonar.tests.modules-declaration:module_e:src/main/java/HelloE.java").getName()).isEqualTo("HelloE.java");
    } else {
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
  }

  /**
   * See SONAR-3843
   */
  @Test
  public void should_support_shade_with_dependency_reduced_pom_with_clean_install_sonar_goals() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/shade-with-dependency-reduced-pom"))
      .setGoals(cleanInstallSonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLastStatus()).isEqualTo(0);
    assertThat(result.getLogs()).doesNotContain(
      "Unable to determine structure of project. Probably you use Maven Advanced Reactor Options, which is not supported by Sonar and should not be used.");
  }

  /**
   * src/main/java is missing
   */
  @Test
  public void maven_project_with_only_test_dir() {
    // Need package to have test execution
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir")).setGoals(cleanPackageSonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-only-test-dir", "tests")).isEqualTo(1);
    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-only-test-dir", "files")).isEqualTo(1);
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-override-sources")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:maven-override-sources", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.samples:maven-override-sources:src/main/java2/Hello2.java")).isNotNull();
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources_in_multi_module() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-modules-override-sources")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    if (hasModules()) {
      assertThat(getMeasureAsInteger("com.sonarsource.it.samples:module_a1", "files")).isEqualTo(1);
    } else {
      assertThat(getMeasureAsInteger("com.sonarsource.it.samples:multi-modules-sample:module_a", "files")).isEqualTo(2);
    }
  }

  /**
   * The property sonar.sources overrides the source dirs as declared in Maven
   */
  @Test
  public void override_sources_in_multi_module_aggregator() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/multi-module-aggregator"))
      .setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    if (hasModules()) {
      assertThat(getMeasureAsInteger("edu.marcelo:module-web", "files")).isEqualTo(2);
    } else {
      assertThat(getMeasureAsInteger("edu.marcelo:multi-module-aggregator:module-web/src/main/webapp", "files")).isEqualTo(2);
    }
  }

  /**
   * The property sonar.inclusions overrides the property sonar.sources
   */
  @Test
  public void inclusions_apply_to_source_dirs() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/inclusions_apply_to_source_dirs")).setGoals(sonarGoal());
    orchestrator.executeBuild(build);

    assertThat(getMeasureAsInteger("com.sonarsource.it.samples:inclusions_apply_to_source_dirs", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.samples:inclusions_apply_to_source_dirs:src/main/java/Hello2.java")).isNotNull();
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  public void fail_if_bad_value_of_sonar_sources_property() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-sources-property")).setGoals(sonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLastStatus()).isNotEqualTo(0);
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-sources-property:jar:1.0-SNAPSHOT. Please check the property sonar.sources");
  }

  /**
   * The property sonar.sources has a typo -> fail, like in sonar-runner
   */
  @Test
  public void fail_if_bad_value_of_sonar_tests_property() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-bad-tests-property")).setGoals(sonarGoal());
    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getLastStatus()).isNotEqualTo(0);
    assertThat(result.getLogs()).contains(
      "java2' does not exist for Maven module com.sonarsource.it.samples:maven-bad-tests-property:jar:1.0-SNAPSHOT. Please check the property sonar.tests");
  }

  // MSONAR-83
  @Test
  public void shouldPopulateLibraries() throws IOException {
    File outputProps = temp.newFile();
    File projectPom = ItUtils.locateProjectPom("shared/struts-1.3.9-diet");
    MavenBuild build = MavenBuild.create(projectPom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scanner.dumpToFile", outputProps.getAbsolutePath());
    orchestrator.executeBuild(build);

    Properties generatedProps = getProps(outputProps);
    String[] moduleIds = generatedProps.getProperty("sonar.modules").split(",");
    String strutsCoreModuleId = null;
    for (String moduleId : moduleIds) {
      if (generatedProps.getProperty(moduleId + ".sonar.moduleKey").equals("org.apache.struts:struts-core")) {
        strutsCoreModuleId = moduleId;
        break;
      }
    }
    assertThat(strutsCoreModuleId).isNotNull();
    assertThat(generatedProps.getProperty(strutsCoreModuleId + ".sonar.java.libraries")).contains("antlr-2.7.2.jar");
    assertThat(generatedProps.getProperty(strutsCoreModuleId + ".sonar.libraries")).contains("antlr-2.7.2.jar");
  }

  // MSONAR-91
  @Test
  public void shouldSkipModules() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("exclusions/skip-one-module"))
      .setGoals(cleanSonarGoal());
    orchestrator.executeBuild(build);

    if (hasModules()) {
      assertThat(getComponent("com.sonarsource.it.samples:module_a1")).isNull();
      assertThat(getComponent("com.sonarsource.it.samples:module_a2").getName()).isEqualTo("Sub-module A2");
      assertThat(getComponent("com.sonarsource.it.samples:module_b").getName()).isEqualTo("Module B");
    } else {
      assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_a/module_a1")).isNull();
      assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_a/module_a2").getName()).isEqualTo("module_a2");
      assertThat(getComponent("com.sonarsource.it.samples:multi-modules-sample:module_b").getName()).isEqualTo("module_b");
    }
  }

  // MSONAR-150
  @Test
  public void shouldSkipWithEnvVar() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal())
      .setProperties("sonar.host.url", "invalid")
      .setEnvironmentVariable("SONARQUBE_SCANNER_PARAMS", "{ \"sonar.scanner.skip\" : \"true\" }");
    BuildResult result = orchestrator.executeBuild(build);
    assertThat(result.getLogs()).contains("SonarQube Scanner analysis skipped");
  }

  @Test
  public void read_default_from_plugins_config() throws Exception {
    File outputProps = temp.newFile();
    // Need package to have test execution
    // Surefire reports are not in standard directory
    File pom = ItUtils.locateProjectPom("project-default-config");
    MavenBuild build = MavenBuild.create(pom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scanner.dumpToFile", outputProps.getAbsolutePath());
    orchestrator.executeBuild(build);

    Properties props = getProps(outputProps);
    assertThat(props).contains(
      entry("sonar.findbugs.excludeFilters", new File(pom.getParentFile(), "findbugs-filter.xml").toString()),
      entry("sonar.junit.reportsPath", new File(pom.getParentFile(), "target/surefire-output").toString()),
      entry("sonar.junit.reportPaths", new File(pom.getParentFile(), "target/surefire-output").toString()),
      entry("sonar.java.source", "1.7"));
  }

  /**
   * MSONAR-141
   */
  @Test
  public void supportMavenEncryption() throws Exception {
    ItUtils.newAdminWsClient(orchestrator).settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("true"));
    orchestrator.getServer().adminWsClient().userClient().create(UserParameters.create().login("julien").name("Julien").password("123abc").passwordConfirmation("123abc"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/maven-only-test-dir"))
      .setGoals(cleanSonarGoal());

    File securityXml = new File(this.getClass().getResource("/security-settings.xml").toURI());
    File settingsXml = new File(this.getClass().getResource("/settings-with-encrypted-sonar-password.xml").toURI());

    build.addArgument("--settings=" + settingsXml.getAbsolutePath());
    // MNG-4853
    build.addArgument("-Dsettings.security=" + securityXml.getAbsolutePath());
    build.setProperty("sonar.login", "julien");
    build.addArgument("-Psonar-password");
    orchestrator.executeBuild(build);
  }

  @Test
  public void setJavaVersionCompilerConfiguration() throws FileNotFoundException, IOException {
    Assume.assumeTrue(3 == getMavenMajorVersion());

    File outputProps = temp.newFile();

    File pom = ItUtils.locateProjectPom("version/compilerPluginConfig");
    MavenBuild build = MavenBuild.create(pom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scanner.dumpToFile", outputProps.getAbsolutePath());
    orchestrator.executeBuild(build);

    Properties props = getProps(outputProps);
    assertThat(props).contains(
      entry("sonar.java.source", "1.7"),
      entry("sonar.java.target", "1.8"));
  }

  Integer mavenVersion = null;

  private int getMavenMajorVersion() {
    String versionRegex = "Apache Maven\\s(\\d+)\\.\\d+(?:\\.\\d+)?\\s";

    if (mavenVersion != null) {
      return mavenVersion;
    }

    MavenBuild build = MavenBuild.create()
      .setGoals("-version");
    BuildResult result = orchestrator.executeBuild(build);

    String logs = result.getLogs();
    Pattern p = Pattern.compile(versionRegex);
    Matcher matcher = p.matcher(logs);

    if (matcher.find()) {
      mavenVersion = Integer.parseInt(matcher.group(1));
      return mavenVersion;
    }
    throw new IllegalStateException("Could not find maven version: " + logs);
  }

  @Test
  public void setJavaVersionProperties() throws IOException {
    Assume.assumeTrue(3 == getMavenMajorVersion());

    File outputProps = temp.newFile();

    File pom = ItUtils.locateProjectPom("version/properties");
    MavenBuild build = MavenBuild.create(pom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scanner.dumpToFile", outputProps.getAbsolutePath());
    orchestrator.executeBuild(build);

    Properties props = getProps(outputProps);
    assertThat(props).contains(
      entry("sonar.java.source", "1.7"),
      entry("sonar.java.target", "1.8"));
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

  private boolean hasModules() {
    return !orchestrator.getServer().version().isGreaterThanOrEquals(7, 6);
  }

}
