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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest extends AbstractMavenTest {

  @TempDir
  public Path temp;

  @Test
  void test_unsupported_platform() {
    String unsupportedOS = "unsupportedOS";
    String arch = "amd64";

    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/aggregator-inherit-parent"))
      .setProperty("sonar.scanner.os", unsupportedOS)
      .setProperty("sonar.scanner.arch", arch)
      .setProperty("sonar.login", ORCHESTRATOR.getDefaultAdminToken())
      .setProperty("sonar.host.url", ORCHESTRATOR.getServer().getUrl())
      .setGoals(cleanSonarGoal());

    BuildResult result = runner.runQuietly(null, build);
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 6)) {
      assertThat(result.isSuccess()).isFalse();

      String url = ORCHESTRATOR.getServer().getUrl() + String.format("/api/v2/analysis/jres?os=%s&arch=%s", unsupportedOS, arch);
      String expectedLog = String.format("Error status returned by url [%s]: 400", url);
      assertThat(result.getLogs()).contains(expectedLog);
    } else {
      assertThat(result.isSuccess()).isTrue();
    }

  }

  @Test
  void test_supported_arch_to_assert_jre_used() throws IOException {
    BuildRunner runner = new BuildRunner(ORCHESTRATOR.getConfiguration());
    String projectName = "maven/aggregator-inherit-parent";
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom(projectName))
      .setProperty("sonar.login", ORCHESTRATOR.getDefaultAdminToken())
      .setProperty("sonar.host.url", ORCHESTRATOR.getServer().getUrl())
      .setGoals(cleanSonarGoal());


    BuildResult result = runner.runQuietly(null, build);
    assertThat(result.isSuccess()).isTrue();
    Path propertiesFile = ItUtils.locateProjectDir(projectName).toPath().resolve("target/sonar/dumpSensor.system.properties");
    Properties props = new Properties();
    props.load(Files.newInputStream(propertiesFile));

    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(10, 6)) {
      //we test that we are actually using the JRE downloaded from SQ
      assertThat(props.getProperty("java.home"))
        .isNotEmpty()
        .isNotEqualTo(System.getProperty("java.home"))
        .contains(".sonar" + File.separator + "cache");
    } else {
      //we test that we are using the system JRE
      assertThat(props.getProperty("java.home"))
        .isNotEmpty()
        .isEqualTo(System.getProperty("java.home"))
        .doesNotContain(".sonar" + File.separator + "cache");
    }
  }

}
