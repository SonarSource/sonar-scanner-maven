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
package com.sonar.orchestrator.build;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.sonar.orchestrator.config.Configuration;
import com.sonar.orchestrator.util.Command;
import com.sonar.orchestrator.util.CommandExecutor;
import com.sonar.orchestrator.util.StreamConsumer;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.LoggerFactory;

/**
 * Temporary fix for https://jira.sonarsource.com/browse/ORCH-351
 *
 */
class MavenBuildExecutor extends AbstractBuildExecutor<MavenBuild> {

  private static final String MAVEN_OPTS = "MAVEN_OPTS";

  @Override
  @VisibleForTesting
  BuildResult execute(MavenBuild build, Configuration config, Map<String, String> adjustedProperties, CommandExecutor commandExecutor) {
    BuildResult result = new BuildResult();
    for (String goal : build.getGoals()) {
      appendCoverageArgumentToOpts(build.getEnvironmentVariables(), config, MAVEN_OPTS);
      executeGoal(build, config, adjustedProperties, goal, result, commandExecutor);
    }
    return result;
  }

  private void executeGoal(MavenBuild build, Configuration config, Map<String, String> adjustedProperties, String goal,
    final BuildResult result, CommandExecutor commandExecutor) {
    try {
      File mavenHome = config.fileSystem().mavenHome();
      if(mavenHome == null) {
        throw new IllegalStateException("No maven home defined for the IT");
      }
      Command command = Command.create(getMvnPath(mavenHome));
      if (build.getExecutionDir() != null) {
        command.setDirectory(build.getExecutionDir());
      }
      for (Map.Entry<String, String> env : build.getEffectiveEnvironmentVariables().entrySet()) {
        command.setEnvironmentVariable(env.getKey(), env.getValue());
      }
      // Force M2_HOME to override default from env
      command.setEnvironmentVariable("M2_HOME", mavenHome.getAbsolutePath());
      // allow to set "clean install" in the same process
      command.addArguments(StringUtils.split(goal, " "));
      command.addArgument("-B");
      command.addArgument("-e");

      if (build.getPom() != null) {
        File pomFile = config.fileSystem().locate(build.getPom());
        Preconditions.checkState(pomFile.exists(), "Maven pom does not exist: " + build.getPom());
        command.addArgument("-f").addArgument(pomFile.getAbsolutePath());
      }
      if (build.isDebugLogs()) {
        command.addArgument("-X");
      }
      command.addArguments(build.arguments());
      for (Map.Entry<String, String> entry : adjustedProperties.entrySet()) {
        command.addSystemArgument(entry.getKey(), entry.getValue());
      }
      StreamConsumer.Pipe writer = new StreamConsumer.Pipe(result.getLogsWriter());
      LoggerFactory.getLogger(getClass()).info("Execute: " + command);
      int status = commandExecutor.execute(command, writer, build.getTimeoutSeconds() * 1000);
      result.setStatus(status);

    } catch (Exception e) {
      throw new IllegalStateException("Fail to execute Maven", e);
    }
  }

  static String getMvnPath(File mvnHome) throws IOException {
    String program = "mvn";
    if (SystemUtils.IS_OS_WINDOWS) {
      program += ".bat";
    }
    if (mvnHome != null) {
      program = new File(mvnHome, "bin/" + program).getCanonicalPath();
    }
    return program;
  }

}
