/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2017 SonarSource SA
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
import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.skyscreamer.jsonassert.JSONAssert;

import static org.junit.Assume.assumeTrue;

public class DependencyTest extends AbstractMavenTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void deleteData() {
    // Design features have been dropped in 5.2
    assumeTrue(!orchestrator.getServer().version().isGreaterThanOrEquals("5.2"));
    orchestrator.resetData();
  }

  @Test
  public void simple_maven_project() throws JSONException {
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("batch/dependencies/sample-with-deps"))
      .setGoals(cleanPackageSonarGoal());
    orchestrator.executeBuild(build);

    String jsonDeps = orchestrator.getServer().adminWsClient().post("/api/dependency_tree", "resource", "com.sonarsource.it.samples:sample-with-deps", "format", "json");
    JSONAssert
      .assertEquals(
        "[{\"w\":1,\"u\":\"compile\",\"s\":\"PRJ\",\"q\":\"LIB\",\"v\":\"2.4\",\"k\":\"commons-io:commons-io\",\"n\":\"commons-io:commons-io\"},"
          + "{\"w\":1,\"u\":\"test\",\"s\":\"PRJ\",\"q\":\"LIB\",\"v\":\"4.10\",\"k\":\"junit:junit\",\"n\":\"junit:junit\",\"to\":["
          + "{\"w\":1,\"u\":\"test\",\"s\":\"PRJ\",\"q\":\"LIB\",\"v\":\"1.1\",\"k\":\"org.hamcrest:hamcrest-core\",\"n\":\"org.hamcrest:hamcrest-core\"}]}]",
        jsonDeps, false);
  }

  @Test
  public void multi_module_deps() throws JSONException {
    // Module B depends on Module A
    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("batch/dependencies/multi-modules-with-deps"))
      .setGoals(cleanPackageSonarGoal());
    orchestrator.executeBuild(build);

    String jsonDeps = orchestrator.getServer().adminWsClient().post("/api/dependency_tree", "resource", "com.sonarsource.it.samples:module_b", "format", "json");
    JSONAssert
      .assertEquals(
        "[{\"w\":1,\"u\":\"compile\",\"s\":\"PRJ\",\"q\":\"BRC\",\"v\":\"1.0-SNAPSHOT\",\"k\":\"com.sonarsource.it.samples:module_a\",\"n\":\"Module A\"},"
          + "{\"w\":1,\"u\":\"test\",\"s\":\"PRJ\",\"q\":\"LIB\",\"v\":\"3.8.1\",\"k\":\"junit:junit\",\"n\":\"junit:junit\"}]",
        jsonDeps, false);
  }

}
