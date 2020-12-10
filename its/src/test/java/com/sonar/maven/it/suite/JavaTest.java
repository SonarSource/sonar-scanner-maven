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
import com.sonar.orchestrator.build.MavenBuild;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.settings.SetRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

public class JavaTest extends AbstractMavenTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @After
  public void cleanup() {
    wsClient.settings().set(new SetRequest().setKey("sonar.forceAuthentication").setValue("false"));
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

  @Test
  public void setJavaVersionCompilerConfiguration() throws FileNotFoundException, IOException {
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

  @Test
  public void setJavaVersionProperties() throws IOException {
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
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(outputProps)) {
      props.load(fis);
    }
    return props;
  }

}
