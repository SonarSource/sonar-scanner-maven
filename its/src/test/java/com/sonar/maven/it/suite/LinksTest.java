/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2021 SonarSource SA
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
import com.sonar.orchestrator.container.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sonarqube.ws.ProjectLinks;
import org.sonarqube.ws.ProjectLinks.SearchWsResponse;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.projectlinks.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

public class LinksTest extends AbstractMavenTest {

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
      .setProperty("sonar.scm.disabled", "true");
    orchestrator.executeBuild(build);

    checkLinks();
  }

  private void checkLinks() {
    Server server = orchestrator.getServer();
    WsClient client = WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
      .build());
    SearchWsResponse response = client.projectLinks().search(new SearchRequest().setProjectKey("com.sonarsource.it.samples:simple-sample"));
    if (server.version().isGreaterThanOrEquals(7, 1)) {
      // SONAR-10299
      assertThat(response.getLinksList())
        .extracting(ProjectLinks.Link::getType, ProjectLinks.Link::getUrl)
        .containsExactlyInAnyOrder(tuple("homepage", "http://www.simplesample.org_OVERRIDDEN"),
          tuple("ci", "http://bamboo.ci.codehaus.org/browse/SIMPLESAMPLE"),
          tuple("issue", "http://jira.codehaus.org/browse/SIMPLESAMPLE"),
          tuple("scm", "https://github.com/SonarSource/simplesample"));
    } else {
      assertThat(response.getLinksList())
        .extracting(ProjectLinks.Link::getType, ProjectLinks.Link::getUrl)
        .containsExactlyInAnyOrder(tuple("homepage", "http://www.simplesample.org_OVERRIDDEN"),
          tuple("ci", "http://bamboo.ci.codehaus.org/browse/SIMPLESAMPLE"),
          tuple("issue", "http://jira.codehaus.org/browse/SIMPLESAMPLE"),
          tuple("scm", "https://github.com/SonarSource/simplesample"),
          tuple("scm_dev", "scm:git:git@github.com:SonarSource/simplesample.git"));
    }
  }

}
