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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.StringAssert;
import org.junit.jupiter.api.Test;

import static com.sonar.maven.it.ItUtils.locateProjectDir;
import static org.assertj.core.api.Assertions.assertThat;

class BootstrapTest extends AbstractMavenTest {

  @Test
  void test_unsupported_platform() {
    String unsupportedOS = "unsupportedOS";
    String arch = "amd64";

    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/bootstrap-small-project"))
      .setProperty("sonar.scanner.os", unsupportedOS)
      .setProperty("sonar.scanner.arch", arch)
      .setProperty("sonar.login", ORCHESTRATOR.getDefaultAdminToken())
      .setProperty("sonar.host.url", ORCHESTRATOR.getServer().getUrl())
      .setGoals(cleanSonarGoal());

    boolean sonarQubeThatSupportJREProvisioning = ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 6);
    if (sonarQubeThatSupportJREProvisioning) {
      BuildResult result = validateBuildWithoutCE(runner.runQuietly(null, build), EXEC_FAILED);
      String url = ORCHESTRATOR.getServer().getUrl() + String.format("/api/v2/analysis/jres?os=%s&arch=%s", unsupportedOS, arch);
      String expectedLog = String.format("Error status returned by url [%s]: 400", url);
      assertThat(result.getLogs()).contains(expectedLog);
    } else {
      validateBuildWithCE(runner.runQuietly(null, build));
    }
  }

  @Test
  void test_supported_arch_to_assert_jre_used() throws IOException {
    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    String projectName = "maven/bootstrap-small-project";
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom(projectName))
      .setProperty("sonar.login", ORCHESTRATOR.getDefaultAdminToken())
      .setProperty("sonar.host.url", ORCHESTRATOR.getServer().getUrl())
      .setEnvironmentVariable("DUMP_SENSOR_PROPERTIES", "" +
        "any.property.name," +
        "sonar.java.fileByFile," +
        "sonar.java.file.suffixes," +
        "sonar.projectKey," +
        "sonar.projectBaseDir," +
        "sonar.sources," +
        "sonar.working.directory," +
        "sonar.java.libraries," +
        "sonar.java.source," +
        "sonar.java.target," +
        "sonar.java.test.libraries," +
        "sonar.java.jdkHome")
      .setEnvironmentVariable("DUMP_ENV_PROPERTIES", "" +
        "ANY_ENV_VAR")
      .setEnvironmentVariable("DUMP_SYSTEM_PROPERTIES", "" +
        "http.proxyUser,"+
        "http.nonProxyHosts," +
        "java.home")
      .setEnvironmentVariable("ANY_ENV_VAR", "42")
      // http.nonProxyHosts will only be present in the maven context and not in the provisioned JRE
      .setProperty("http.nonProxyHosts", "localhost|my-custom-non-proxy.server.com")
      // Any property will be passed to the sensorContext.config()
      .setProperty("any.property.name", "foo42")
      // This property will be ignored because of the bellow "sonar.scanner.javaOpts" property that has priority
      .setEnvironmentVariable("SONAR_SCANNER_JAVA_OPTS", "-Dhttp.proxyUser=my-custom-user-from-env")
      // Set system property on the provisioned JRE
      .setProperty("sonar.scanner.javaOpts", "-Dhttp.proxyUser=my-custom-user-from-system-properties")
      .setGoals(cleanSonarGoal());

    BuildResult result = validateBuildWithCE(runner.runQuietly(null, build));
    assertThat(result.isSuccess()).isTrue();
    Path propertiesFile = ItUtils.locateProjectDir(projectName).toPath().resolve("target/sonar/dumpSensor.system.properties");
    Properties props = new Properties();
    props.load(Files.newInputStream(propertiesFile));

    Path smallProjectDir = locateProjectDir("maven").getAbsoluteFile().toPath().resolve("bootstrap-small-project");

    SoftAssertions softly = new SoftAssertions();

    // Environment variables
    softly.assertThat(props.getProperty("ANY_ENV_VAR")).isEqualTo( "42");

    // User defined in its/projects/maven/bootstrap-small-project/pom.xml properties
    softly.assertThat(props.getProperty("sonar.java.fileByFile")).isEqualTo( "true");

    // SonarQube properties
    softly.assertThat(props.getProperty("sonar.java.file.suffixes")).isEqualTo( ".java,.jav");

    // Project properties
    softly.assertThat(props.getProperty("sonar.projectKey")).isEqualTo( "org.sonarsource.maven.its:bootstrap-small-project");
    softly.assertThat(props.getProperty("sonar.projectBaseDir")).isEqualTo( smallProjectDir.toString());
    softly.assertThat(props.getProperty("sonar.sources")).isEqualTo( smallProjectDir.resolve("pom.xml") + "," + smallProjectDir.resolve("src").resolve("main").resolve("java"));
    softly.assertThat(props.getProperty("sonar.working.directory")).isEqualTo( smallProjectDir.resolve("target").resolve("sonar").toString());

    // Any properties are present in the sensor context
    softly.assertThat(props.getProperty("any.property.name")).contains("foo42");

    // Java analyzers properties
    softly.assertThat(props.getProperty("sonar.java.libraries")).contains("jsr305-3.0.2.jar");
    softly.assertThat(props.getProperty("sonar.java.source")).isEqualTo( "11");
    softly.assertThat(props.getProperty("sonar.java.target")).isEqualTo( "11");
    softly.assertThat(props.getProperty("sonar.java.test.libraries")).contains("jsr305-3.0.2.jar");
    // sonar.java.jdkHome should be the one used by "mvn sonar:sonar", by default maven uses JAVA_HOME
    String javaHome = System.getenv("JAVA_HOME");
    if (javaHome == null) {
      javaHome = System.getProperty("java.home");
    }
    softly.assertThat(props.getProperty("sonar.java.jdkHome")).isEqualTo( new File(javaHome).getCanonicalPath());

    StringAssert javaHomeAssertion = softly.assertThat(props.getProperty("java.home")).isNotEmpty();
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 6)) {
      //we test that we are actually using the JRE downloaded from SQ
      javaHomeAssertion
        .isNotEqualTo(System.getProperty("java.home"))
        .contains(".sonar" + File.separator + "cache");

      // System properties of the initial JRE are intentionally not set on the provisioned JRE
      softly.assertThat(props.getProperty("http.nonProxyHosts"))
        .isEmpty();

      // System properties defined in "sonar.scanner.javaOpts" are set on the provisioned JRE
      softly.assertThat(props.getProperty("http.proxyUser")).isEqualTo("my-custom-user-from-system-properties");
    } else {
      //we test that we are using the system JRE
      javaHomeAssertion
        .isEqualTo(System.getProperty("java.home"))
        .doesNotContain(".sonar" + File.separator + "cache");

      softly.assertThat(props.getProperty("http.nonProxyHosts"))
        .isEqualTo("localhost|my-custom-non-proxy.server.com");

      // System properties defined in "sonar.scanner.javaOpts" are ignored outside the provisioned JRE
      softly.assertThat(props.getProperty("http.proxyUser")).isEmpty();
    }
    softly.assertAll();
  }

}
