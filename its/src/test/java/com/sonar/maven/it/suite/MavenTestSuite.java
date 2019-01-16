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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.locator.MavenLocation;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  LinksTest.class, MavenTest.class, DependencyTest.class, ProxyTest.class
})
public class MavenTestSuite {

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setSonarVersion(getSonarVersion())
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "LATEST_RELEASE"))
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", "LATEST_RELEASE"))
    .addPlugin(MavenLocation.of("org.sonarsource.python", "sonar-python-plugin", "LATEST_RELEASE"))
    .addPlugin(MavenLocation.of("org.sonarsource.html", "sonar-html-plugin", "LATEST_RELEASE"))
    .addPlugin(MavenLocation.of("org.sonarsource.xml", "sonar-xml-plugin", "LATEST_RELEASE"))
    .build();

  private static String getSonarVersion() {
    String versionProperty = System.getProperty("sonar.runtimeVersion");
    return versionProperty != null ? versionProperty : "LATEST_RELEASE";
  }
}
