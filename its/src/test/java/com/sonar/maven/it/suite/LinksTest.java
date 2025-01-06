/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package com.sonar.maven.it.suite;

import com.sonar.maven.it.ItUtils;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Server;
import org.junit.jupiter.api.Test;
import org.sonarqube.ws.ProjectLinks;
import org.sonarqube.ws.ProjectLinks.SearchWsResponse;
import org.sonarqube.ws.client.projectlinks.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class LinksTest extends AbstractMavenTest {

  /**
   * SONAR-3676
   */
  @Test
  void shouldUseLinkPropertiesOverPomLinksInMaven() {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("batch/links-project"))
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scm.disabled", "true");
    executeBuildAndValidateWithCE(build);

    checkLinks();
  }

  private void checkLinks() {
    Server server = ORCHESTRATOR.getServer();
    SearchWsResponse response = wsClient.projectLinks().search(new SearchRequest().setProjectKey("com.sonarsource.it.samples:simple-sample"));
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
