/*
 * SonarSource :: IT :: SonarQube Maven
 * Copyright (C) 2009-2024 SonarSource SA
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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.http.HttpMethod;
import com.sonar.orchestrator.http.HttpResponse;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.version.Version;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;

import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.Measures.Measure;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.components.ShowRequest;
import org.sonarqube.ws.client.components.TreeRequest;
import org.sonarqube.ws.client.measures.ComponentRequest;

import static java.util.Collections.singletonList;

public abstract class AbstractMavenTest {

  private static final Pattern VERSION_REGEX = Pattern.compile("Apache Maven\\s(\\d+\\.\\d+(?:\\.\\d+)?)(?:-\\S+)?\\s");

  private static Version mojoVersion;

  @RegisterExtension
  public static final OrchestratorExtension ORCHESTRATOR = OrchestratorExtension.builderEnv()
    .setSonarVersion(getSonarVersion())
    .useDefaultAdminCredentialsForBuilds(true)
    .addBundledPluginToKeep("sonar-java-plugin")
    .addBundledPluginToKeep("sonar-xml-plugin")
    .addBundledPluginToKeep("sonar-html-plugin")
    .build();

  protected HttpConnector wsConnector;
  protected WsClient wsClient;

  protected static String[] cleanInstallSonarGoal() {
    return new String[]{"clean install " + sonarGoal()};
  }

  protected static String sonarGoal() {
    return "org.sonarsource.scanner.maven:sonar-maven-plugin:" + mojoVersion().toString() + ":sonar -V";
  }

  protected static String[] cleanSonarGoal() {
    return new String[]{"clean " + sonarGoal()};
  }

  protected static String[] cleanPackageSonarGoal() {
    return new String[]{"clean package " + sonarGoal()};
  }

  protected static String[] cleanVerifySonarGoal() {
    return new String[]{"clean verify " + sonarGoal()};
  }

  @BeforeEach
  public void setUpWsClient() {
    wsConnector = HttpConnector.newBuilder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
      .build();
    wsClient = WsClientFactories.getDefault().newClient(wsConnector);
  }

  @AfterEach
  public void resetData() {
    Set<String> projectKeys = getProjectKeysToDelete();

    if (!projectKeys.isEmpty()) {
      ORCHESTRATOR.getServer()
        .newHttpCall("/api/projects/bulk_delete")
        .setAdminCredentials()
        .setMethod(HttpMethod.POST)
        .setParams("projects", String.join(",", projectKeys))
        .execute();
    }
  }

  private Set<String> getProjectKeysToDelete() {
    HttpResponse ps = ORCHESTRATOR.getServer()
      .newHttpCall("api/projects/search")
      .setAdminCredentials()
      .setMethod(HttpMethod.GET)
      .setParam("ps", "100")
      .execute();

    return Json.parse(ps.getBodyAsString())
      .asObject()
      .get("components")
      .asArray()
      .values()
      .stream()
      .map(JsonValue::asObject)
      .map(members -> members.getString("key", ""))
      .filter(s -> !s.isEmpty())
      .collect(Collectors.toSet());
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
    Measures.ComponentWsResponse response = newWsClient().measures().component(new ComponentRequest()
      .setComponent(componentKey)
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
      return newWsClient().components().show(new ShowRequest().setComponent(componentKey)).getComponent();
    } catch (HttpException e) {
      if (e.code() == 404) {
        return null;
      }
      throw new IllegalStateException(e);
    }
  }

  static List<Component> getModules(String projectKey) {
    return newWsClient().components().tree(new TreeRequest().setComponent(projectKey).setQualifiers(singletonList("BRC"))).getComponentsList();
  }

  static WsClient newWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .build());
  }

  Version mavenVersion = null;

  protected Version getMavenVersion() {

    if (mavenVersion != null) {
      return mavenVersion;
    }

    MavenBuild build = MavenBuild.create()
      .setGoals("-version");
    BuildResult result = ORCHESTRATOR.executeBuild(build);

    String logs = result.getLogs();
    Matcher matcher = VERSION_REGEX.matcher(logs);

    if (matcher.find()) {
      mavenVersion = Version.create(matcher.group(1));
      return mavenVersion;
    }
    throw new IllegalStateException("Could not find maven version: " + logs);
  }

  private static String getSonarVersion() {
    String versionProperty = System.getProperty("sonar.runtimeVersion");
    return versionProperty != null ? versionProperty : "DEV";
  }

}
