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
import com.sonar.orchestrator.build.MavenBuild;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One language per module
 *
 */
public class OldMultiLanguageTest extends AbstractMavenTest {

  @Before
  public void cleanDatabase() {
    orchestrator.resetData();
  }

  @Test
  public void test_maven_inspection() {
    MavenBuild build = MavenBuild
      .create(ItUtils.locateProjectPom("batch/multi-languages"))
      .setProperty("sonar.profile.java", "empty")
      .setProperty("sonar.profile.js", "empty")
      .setProperty("sonar.profile.py", "empty")
      .setGoals(cleanSonarGoal())
      .setDebugLogs(true);
    BuildResult result = orchestrator.executeBuild(build);

    // SONAR-4515
    assertThat(result.getLogs()).contains("Available languages:");
    assertThat(result.getLogs()).contains("JavaScript => \"js\"");
    assertThat(result.getLogs()).contains("Python => \"py\"");
    assertThat(result.getLogs()).contains("Java => \"java\"");

    // modules
    assertThat(getMeasureAsInteger("com.sonarsource.it.projects.batch.multi-languages:java-module", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.projects.batch.multi-languages:java-module").getLanguage()).isNullOrEmpty();

    assertThat(getMeasureAsInteger("com.sonarsource.it.projects.batch.multi-languages:javascript-module", "files")).isEqualTo(1);
    assertThat(getComponent("com.sonarsource.it.projects.batch.multi-languages:javascript-module").getLanguage()).isNullOrEmpty();

    assertThat(getMeasureAsInteger("com.sonarsource.it.projects.batch.multi-languages:python-module", "files")).isEqualTo(2);
    assertThat(getComponent("com.sonarsource.it.projects.batch.multi-languages:python-module").getLanguage()).isNullOrEmpty();

    // 1 + 1 + 2 for the languages, +1 for pom.xml at project root
    // The pom.xml of leaf projects are excluded due to forced language
    int expectedFileCount = orchestrator.getServer().version().isGreaterThanOrEquals("6.3") ? 5 : 4;
    assertThat(getMeasureAsInteger("com.sonarsource.it.projects.batch.multi-languages:multi-languages", "files")).isEqualTo(expectedFileCount);
    assertThat(getComponent("com.sonarsource.it.projects.batch.multi-languages:multi-languages").getLanguage()).isNullOrEmpty();
  }
}
