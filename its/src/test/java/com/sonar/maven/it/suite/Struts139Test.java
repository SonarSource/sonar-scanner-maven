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
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.wsclient.services.Dependency;
import org.sonar.wsclient.services.DependencyQuery;
import org.sonar.wsclient.services.DependencyTree;
import org.sonar.wsclient.services.DependencyTreeQuery;
import org.sonar.wsclient.services.Event;
import org.sonar.wsclient.services.EventQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class Struts139Test extends AbstractMavenTest {

  private static final String PROJECT_STRUTS = "org.apache.struts:struts-parent";
  private static final String MODULE_CORE = "org.apache.struts:struts-core";

  @BeforeClass
  public static void analyzeProject() {
    orchestrator.resetData();
    orchestrator.getServer().restoreProfile(FileLocation.ofClasspath("/sonar-way-2.7.xml"));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("shared/struts-1.3.9-diet"))
      .setGoals("clean verify")
      .setProperty("skipTests", "true");

    MavenBuild analysis = MavenBuild.create(ItUtils.locateProjectPom("shared/struts-1.3.9-diet"))
      .setGoals(sonarGoal())
      .setProperty("sonar.dynamicAnalysis", "true")
      .setProperty("sonar.scm.disabled", "true")
      .setProperty("sonar.exclusions", "**/package.html")
      .setProperty("sonar.profile.java", "sonar-way-2.7");

    orchestrator.executeBuilds(build, analysis);
  }

  @Test
  public void dependencyTree() {
    // Design features have been dropped in 5.2
    assumeTrue(!orchestrator.getServer().version().isGreaterThanOrEquals("5.2"));
    List<DependencyTree> trees = orchestrator.getServer().getWsClient().findAll(DependencyTreeQuery.createForProject(PROJECT_STRUTS));
    assertThat(trees).isEmpty();

    trees = orchestrator.getServer().getWsClient().findAll(DependencyTreeQuery.createForProject(MODULE_CORE));
    assertThat(trees).isNotEmpty();

    assertThat(trees).extracting("resourceKey").contains("antlr:antlr");
  }

  /**
   * Dependencies query will fail with ClassCastException in sonar-ws-client 2.7 - see http://jira.codehaus.org/browse/SONAR-2379
   */
  @Test
  public void dependencies() {
    // Design features have been dropped in 5.2
    assumeTrue(!orchestrator.getServer().version().isGreaterThanOrEquals("5.2"));
    List<Dependency> dependencies = orchestrator.getServer().getWsClient().findAll(DependencyQuery.createForResource(PROJECT_STRUTS));
    assertThat(dependencies).isEmpty();

    dependencies = orchestrator.getServer().getWsClient().findAll(DependencyQuery.createForResource(MODULE_CORE));
    assertThat(dependencies).isNotEmpty();
  }

  @Test
  public void versionEvent() {
    EventQuery query = new EventQuery(PROJECT_STRUTS);
    query.setCategories(new String[] {"Version"});
    List<Event> events = orchestrator.getServer().getWsClient().findAll(query);
    assertThat(events).hasSize(1);

    Event version = events.get(0);
    assertThat(version.getName()).isEqualTo("1.3.9");
    assertThat(version.getCategory()).isEqualTo("Version");
  }

}
