/*
 * SonarSource :: IT :: SonarQube Maven
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
package com.sonar.maven.it.suite;

import com.sonar.maven.it.ItUtils;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import org.junit.Before;
import org.junit.Test;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;

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
    Resource javaModule = getResource("com.sonarsource.it.projects.batch.multi-languages:java-module", "files");
    Resource jsModule = getResource("com.sonarsource.it.projects.batch.multi-languages:javascript-module", "files");
    Resource pyModule = getResource("com.sonarsource.it.projects.batch.multi-languages:python-module", "files");
    verifyModule(javaModule, 1, "java");
    verifyModule(jsModule, 1, "js");
    verifyModule(pyModule, 2, "py");

    // project
    Resource project = getResource("com.sonarsource.it.projects.batch.multi-languages:multi-languages", "files");
    verifyProject(project);
  }

  private void verifyModule(Resource module, int files, String lang) {
    assertThat(module.getMeasureIntValue("files")).isEqualTo(files);
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(module.getLanguage()).isNull();
    } else {
      assertThat(module.getLanguage()).isEqualTo(lang);
    }
  }

  private void verifyProject(Resource project) {
    verifyModule(project, 4, "java");
  }

  private Resource getResource(String resourceKey, String metricKey) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(resourceKey, metricKey));
  }
}
