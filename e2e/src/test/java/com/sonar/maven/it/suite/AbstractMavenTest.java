/*
 * SonarSource :: E2E :: SonarQube Maven
 * Copyright (C) 2009-2025 SonarSource Sàrl
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

import com.sonar.orchestrator.build.Build;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.container.Server;
import com.sonar.orchestrator.junit5.OrchestratorExtension;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.version.Version;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonarqube.ws.Ce.TaskStatus;
import org.sonarqube.ws.Components.Component;
import org.sonarqube.ws.Measures;
import org.sonarqube.ws.Measures.Measure;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.HttpException;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.ce.TaskRequest;
import org.sonarqube.ws.client.components.ShowRequest;
import org.sonarqube.ws.client.measures.ComponentRequest;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;


public abstract class AbstractMavenTest {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractMavenTest.class);

  public static final int EXEC_SUCCESS = 0;
  public static final int EXEC_FAILED = 1;

  private static final Pattern VERSION_REGEX = Pattern.compile("Apache Maven\\s(\\d+\\.\\d+(?:\\.\\d+)?)(?:-\\S+)?\\s");

  private static Version mojoVersion;

  private static int orchestratorAccessingClassCount = 0;

  public static final String LATEST_RELEASE = "LATEST_RELEASE";
  public static final String DEV = "DEV";

  // @RegisterExtension was removed because it did not properly support parallel execution
  public static final OrchestratorExtension ORCHESTRATOR = OrchestratorExtension.builderEnv()
    .setSonarVersion(getSonarVersion())
    .setEdition(Set.of(LATEST_RELEASE, DEV).contains(getSonarVersion()) ? Edition.COMMUNITY : Edition.DEVELOPER)
    .useDefaultAdminCredentialsForBuilds(true)
    .addBundledPluginToKeep("sonar-java-plugin")
    .addBundledPluginToKeep("sonar-xml-plugin")
    .addBundledPluginToKeep("sonar-html-plugin")
    // This plugin should have been built locally from the property-dump-plugin module
    .addPlugin(FileLocation.of("../property-dump-plugin/target/property-dump-plugin-1.0-SNAPSHOT.jar"))
    .build();

  protected WsClient wsClient;

  @BeforeAll
  public static void setUp() {
    synchronized (AbstractMavenTest.class) {
      orchestratorAccessingClassCount++;
      if (orchestratorAccessingClassCount == 1) {
        ORCHESTRATOR.start();
        if (ORCHESTRATOR.getServer().getEdition() != Edition.COMMUNITY) {
          ORCHESTRATOR.activateLicense();
        }
      }
    }
  }

  @AfterAll
  public static void tearDown() {
    synchronized (AbstractMavenTest.class) {
      orchestratorAccessingClassCount--;
      if (orchestratorAccessingClassCount < 0) {
        throw new IllegalStateException("tearDown called too many times");
      }
      if (orchestratorAccessingClassCount == 0) {
        ORCHESTRATOR.stop();
      }
    }
  }

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

  @BeforeEach
  public void setUpWsClient() {
    wsClient = WsClientFactories.getDefault()
      .newClient(HttpConnector.newBuilder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .credentials(Server.ADMIN_LOGIN, Server.ADMIN_PASSWORD)
      .build());
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
  Measure getMeasure(String componentKey, String metricKey) {
    Measures.ComponentWsResponse response = wsClient.measures().component(new ComponentRequest()
      .setComponent(componentKey)
      .setMetricKeys(singletonList(metricKey)));
    List<Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  @CheckForNull
  Integer getMeasureAsInteger(String componentKey, String metricKey) {
    Measure measure = getMeasure(componentKey, metricKey);
    return (measure == null) ? null : Integer.parseInt(measure.getValue());
  }

  @CheckForNull
  Component getComponent(String componentKey) {
    try {
      return wsClient.components().show(new ShowRequest().setComponent(componentKey)).getComponent();
    } catch (HttpException e) {
      if (e.code() == 404) {
        return null;
      }
      throw new IllegalStateException(e);
    }
  }

  Version mavenVersion = null;

  protected Version getMavenVersion() {

    if (mavenVersion != null) {
      return mavenVersion;
    }

    MavenBuild build = MavenBuild.create()
      .setGoals("-version");
    BuildResult result = assertBuildResultStatuses( ORCHESTRATOR.executeBuild(build), 0);

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
    return versionProperty != null ? versionProperty : LATEST_RELEASE;
  }

  public BuildResult executeBuildAndAssertWithCE(Build<?> build) {
    return assertBuildWithCE(ORCHESTRATOR.executeBuild(build));
  }

  public BuildResult assertBuildWithCE(BuildResult result) {
    assertBuildResultStatuses(result, 0);
    List<String> ceTaskIds = extractCETaskIds(result);
    if (ceTaskIds.isEmpty()) {
      throw new AssertionError("No CE task id found in logs, can't wait for the CE task to be finished");
    }
    for (String ceTaskId : ceTaskIds) {
      waitForCeTaskToBeFinished(ceTaskId);
    }
    return result;
  }

  public BuildResult executeBuildAndAssertWithoutCE(Build<?> build) {
    return assertBuildWithoutCE(ORCHESTRATOR.executeBuild(build));
  }

  public static BuildResult assertBuildWithoutCE(BuildResult result) {
    return assertBuildWithoutCE(result, EXEC_SUCCESS);
  }

  public static BuildResult assertBuildWithoutCE(BuildResult result, int expectedStatus) {
    assertBuildResultStatuses(result, expectedStatus);
    assertThat(extractCETaskIds(result))
      .withFailMessage("The build result contains unexpected CE task ids")
      .isEmpty();
    return result;
  }

  public static BuildResult assertBuildResultStatuses(BuildResult result, int expectedStatus) {
    for (Integer status : result.getStatuses()) {
      assertThat(status).isEqualTo(expectedStatus);
    }
    return result;
  }

  // [INFO] More about the report processing at http://127.0.0.1:63532/api/ce/task?id=bedf3100-4d72-497b-8103-68402821e49c
  private static final Pattern CE_TASK_ID_PATTERN = Pattern.compile("More about the report processing at[^?]++\\?id=([\\w\\-]++)");

  public static List<String> extractCETaskIds(BuildResult result) {
    Matcher matcher = CE_TASK_ID_PATTERN.matcher(result.getLogs());
    List<String> ids = new ArrayList<>();
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    return ids;
  }

  private static final long POLLING_TIME = 500; // 0.5 second
  private static final long MAX_WAIT_TIME = 20_000; // 20 seconds

  private void waitForCeTaskToBeFinished(String ceTaskId) {
    LOG.info("Waiting for CE task {} to be finished", ceTaskId);
    try {
      long start = System.currentTimeMillis();
      while (true) {
        TaskStatus status = wsClient.ce().task(new TaskRequest().setId(ceTaskId)).getTask().getStatus();
        if (status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS) {
          long duration = System.currentTimeMillis() - start;
          if (duration > MAX_WAIT_TIME) {
            throw new AssertionError("CE task " + ceTaskId + " did not finish after " + (MAX_WAIT_TIME / 1000) + " seconds");
          }
          LOG.info("CE task {} has status {}, wait duration {} ms", ceTaskId, status.name(), duration);
          Thread.sleep(POLLING_TIME);
        } else if (status == TaskStatus.SUCCESS) {
          LOG.info("CE task {} succeeded", ceTaskId);
          return;
        } else {
          // FAILED or CANCELED
          throw new AssertionError("CE task " + ceTaskId + " failed: " + status.name());
        }

      }
    } catch (InterruptedException e) {
      throw new AssertionError("Interrupted while waiting for CE task to be finished", e);
    }
  }

}
