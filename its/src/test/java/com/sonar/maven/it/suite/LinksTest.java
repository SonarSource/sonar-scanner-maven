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

import com.google.common.collect.Lists;
import com.sonar.maven.it.ItUtils;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.db.Database;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

public class LinksTest extends AbstractMavenTest {

  private static Object[] expectedLinks = new String[] {
    "homepage=http://www.simplesample.org_OVERRIDDEN",
    "ci=http://bamboo.ci.codehaus.org/browse/SIMPLESAMPLE",
    "issue=http://jira.codehaus.org/browse/SIMPLESAMPLE",
    "scm=https://github.com/SonarSource/simplesample",
    "scm_dev=scm:git:git@github.com:SonarSource/simplesample.git"
  };

  @Before
  public void deleteData() {
    orchestrator.resetData();
  }

  @Before
  @After
  public void cleanProjectLinksTable() {
    orchestrator.getDatabase().truncate("project_links");
  }

  /**
   * SONAR-3676
   */
  @Test
  public void shouldUseLinkPropertiesOverPomLinksInMaven() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("batch/links-project"))
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.dynamicAnalysis", "false")
      .setProperty("sonar.scm.disabled", "true");
    orchestrator.executeBuild(build);

    checkLinks();
  }

  private void checkLinks() {
    Database db = orchestrator.getDatabase();
    List<Map<String, String>> links = db.executeSql("select * from project_links");

    assertThat(links.size()).isEqualTo(5);
    Collection<String> linksToCheck = Lists.newArrayList();
    for (Map<String, String> linkRow : links) {
      linksToCheck.add(linkRow.get("LINK_TYPE") + "=" + linkRow.get("HREF"));
    }
    assertThat(linksToCheck).contains(expectedLinks);
  }

}
