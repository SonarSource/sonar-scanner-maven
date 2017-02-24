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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.version.Version;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.junit.ClassRule;
import org.sonarqube.ws.WsComponents.Component;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.WsMeasures.Measure;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.component.ShowWsRequest;
import org.sonarqube.ws.client.component.TreeWsRequest;
import org.sonarqube.ws.client.measure.ComponentWsRequest;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public abstract class AbstractMavenTest {

  private static Version mojoVersion;

  @ClassRule
  public static Orchestrator orchestrator = MavenTestSuite.ORCHESTRATOR;

  protected static String[] cleanInstallSonarGoal() {
    return new String[] {"clean install " + sonarGoal()};
  }

  protected static String sonarGoal() {
    return "org.sonarsource.scanner.maven:sonar-maven-plugin:" + mojoVersion().toString() + ":sonar -V";
  }

  protected static String[] cleanSonarGoal() {
    return new String[] {"clean " + sonarGoal()};
  }

  protected static String[] cleanPackageSonarGoal() {
    return new String[] {"clean package " + sonarGoal()};
  }

  protected static String[] cleanVerifySonarGoal() {
    return new String[] {"clean verify " + sonarGoal()};
  }

  protected static Version mojoVersion() {
    if (mojoVersion == null) {
      try {
        for (String line : Files.readAllLines(Paths.get("../pom.xml"), StandardCharsets.UTF_8)) {
          if (line.startsWith("  <version>")) {
            String version = StringUtils.substringAfter(line, "<version>");
            version = StringUtils.substringBefore(version, "</version>");
            mojoVersion = Version.create(version);
            return mojoVersion;
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      throw new IllegalStateException("Unable to find version of the Maven plugin to be used by ITs");
    }
    return mojoVersion;
  }

  @CheckForNull
  static Measure getMeasure(String componentKey, String metricKey) {
    WsMeasures.ComponentWsResponse response = newWsClient().measures().component(new ComponentWsRequest()
      .setComponentKey(componentKey)
      .setMetricKeys(singletonList(metricKey)));
    List<Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  @CheckForNull
  static Integer getMeasureAsInteger(String componentKey, String metricKey) {
    Measure measure = getMeasure(componentKey, metricKey);
    return (measure == null) ? null : Integer.parseInt(measure.getValue());
  }

  @CheckForNull
  static Component getComponent(String componentKey) {
    try {
      return newWsClient().components().show(new ShowWsRequest().setKey((componentKey))).getComponent();
    } catch (HttpException e) {
      if (e.code() == 404) {
        return null;
      }
      throw new IllegalStateException(e);
    }
  }

  static List<Component> getModules(String projectKey) {
    return newWsClient().components().tree(new TreeWsRequest().setBaseComponentKey(projectKey).setQualifiers(asList("BRC"))).getComponentsList();
  }

  static WsClient newWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(orchestrator.getServer().getUrl())
      .build());
  }

}
