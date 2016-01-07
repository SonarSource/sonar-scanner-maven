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
import org.apache.commons.lang.StringUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.wsclient.services.Dependency;
import org.sonar.wsclient.services.DependencyQuery;
import org.sonar.wsclient.services.DependencyTree;
import org.sonar.wsclient.services.DependencyTreeQuery;
import org.sonar.wsclient.services.Event;
import org.sonar.wsclient.services.EventQuery;
import org.sonar.wsclient.services.Measure;
import org.sonar.wsclient.services.ResourceQuery;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparisons.greaterThan;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

public class Struts139Test extends AbstractMavenTest {

  private static final String PROJECT_STRUTS = "org.apache.struts:struts-parent";
  private static final String MODULE_CORE = "org.apache.struts:struts-core";
  private static final String DEPRECATED_PACKAGE_ACTION = "org.apache.struts:struts-core:org.apache.struts.action";
  private static final String PACKAGE_ACTION = "org.apache.struts:struts-core:src/main/java/org/apache/struts/action";
  private static final String DEPRECATED_FILE_ACTION = "org.apache.struts:struts-core:org.apache.struts.action.Action";
  private static final String FILE_ACTION = "org.apache.struts:struts-core:src/main/java/org/apache/struts/action/Action.java";

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
    assertThat(trees.size(), is(0));

    trees = orchestrator.getServer().getWsClient().findAll(DependencyTreeQuery.createForProject(MODULE_CORE));
    assertThat(trees.size(), greaterThan(0));

    assertThat(trees, hasItem(new BaseMatcher<DependencyTree>() {
      public boolean matches(Object o) {
        return StringUtils.equals("antlr:antlr", ((DependencyTree) o).getResourceKey());
      }

      public void describeTo(Description description) {
      }
    }));
  }

  /**
   * Dependencies query will fail with ClassCastException in sonar-ws-client 2.7 - see http://jira.codehaus.org/browse/SONAR-2379
   */
  @Test
  public void dependencies() {
    // Design features have been dropped in 5.2
    assumeTrue(!orchestrator.getServer().version().isGreaterThanOrEquals("5.2"));
    List<Dependency> dependencies = orchestrator.getServer().getWsClient().findAll(DependencyQuery.createForResource(PROJECT_STRUTS));
    assertThat(dependencies.size(), is(0));

    dependencies = orchestrator.getServer().getWsClient().findAll(DependencyQuery.createForResource(MODULE_CORE));
    assertThat(dependencies.size(), greaterThan(0));
  }

  @Test
  public void versionEvent() {
    EventQuery query = new EventQuery(PROJECT_STRUTS);
    query.setCategories(new String[] {"Version"});
    List<Event> events = orchestrator.getServer().getWsClient().findAll(query);
    assertThat(events.size(), is(1));

    Event version = events.get(0);
    assertThat(version.getName(), is("1.3.9"));
    assertThat(version.getCategory(), is("Version"));
  }

  /**
   * SONAR-2041
   */
  @Test
  public void unknownMetric() {
    assertThat(getProjectMeasure("notfound"), nullValue());
    assertThat(getCoreModuleMeasure("notfound"), nullValue());
    assertThat(getPackageMeasure("notfound"), nullValue());
    assertThat(getFileMeasure("notfound"), nullValue());
  }

  private Measure getFileMeasure(String metricKey) {
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(FILE_ACTION, metricKey)).getMeasure(metricKey);
    } else {
      return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(DEPRECATED_FILE_ACTION, metricKey)).getMeasure(metricKey);
    }
  }

  private Measure getCoreModuleMeasure(String metricKey) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(MODULE_CORE, metricKey)).getMeasure(metricKey);
  }

  private Measure getProjectMeasure(String metricKey) {
    return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(PROJECT_STRUTS, metricKey)).getMeasure(metricKey);
  }

  private Measure getPackageMeasure(String metricKey) {
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(PACKAGE_ACTION, metricKey)).getMeasure(metricKey);
    } else {
      return orchestrator.getServer().getWsClient().find(ResourceQuery.createForMetrics(DEPRECATED_PACKAGE_ACTION, metricKey)).getMeasure(metricKey);
    }
  }
}
