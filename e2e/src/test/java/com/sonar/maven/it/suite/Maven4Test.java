/*
 * SonarSource :: E2E :: SonarQube Maven
 * Copyright (C) SonarSource Sàrl
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class Maven4Test extends AbstractMavenTest {
  private File outputProps;

  @BeforeEach
  void setUp(@TempDir Path temp) throws Exception {
    outputProps = temp.resolve("out.properties").toFile();
    outputProps.createNewFile();
  }

  @Test
  void modularJar() throws IOException {
    File projectPom = ItUtils.locateProjectPom("maven4/modular-jar");
    MavenBuild build = MavenBuild.create(projectPom)
      .setGoals(cleanPackageSonarGoal())
      .setProperty("sonar.scanner.internal.dumpToFile", outputProps.getAbsolutePath());
    executeBuildAndAssertWithoutCE(build);

    Properties generatedProps = new Properties();
    try (var inputStream = new FileInputStream(outputProps)) {
      generatedProps.load(inputStream);
    }

    assertThat(generatedProps)
      .extractingByKey("sonar.java.libraries")
      .asString()
      .contains("commons-text-1.13.0.jar")
      .contains("commons-lang3-3.17.0.jar");
  }
}
