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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.locator.MavenLocation;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  LinksTest.class, MavenTest.class, ProxyTest.class, JavaTest.class
})
public class MavenTestSuite {

  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setSonarVersion(getSonarVersion())

    // The scanner for maven should still be compatible with previous LTS 6.7, and not the 7.9
    // at the time of writing, so the installed plugins should be compatible with
    // both 6.7 and 8.x. The latest releases of analysers drop the compatibility with
    // 6.7, that's why versions are hardcoded here.
    .addBundledPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", "5.14.0.18788"))
    .addBundledPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", "5.2.1.7778"))
    .addBundledPlugin(MavenLocation.of("org.sonarsource.python", "sonar-python-plugin", "1.12.0.2726"))
    .addBundledPlugin(MavenLocation.of("org.sonarsource.html", "sonar-html-plugin", "3.2.0.2082"))
    .addBundledPlugin(MavenLocation.of("org.sonarsource.xml", "sonar-xml-plugin", "2.0.1.2020"))
    .build();

  private static String getSonarVersion() {
    String versionProperty = System.getProperty("sonar.runtimeVersion");
    return versionProperty != null ? versionProperty : "LATEST_RELEASE";
  }
}
