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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sonar.maven.it.ItUtils;
import com.sonar.maven.it.Proxy;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;

public class ProxyTest extends AbstractMavenTest {
  private Proxy proxy;
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void prepare() throws Exception {
    proxy = new Proxy();
    proxy.startProxy();
  }

  @After
  public void after() throws Exception {
    if (proxy != null) {
      proxy.stopProxy();
    }
  }

  @Test
  public void useActiveProxyInSettings() throws IOException, URISyntaxException, InterruptedException {
    Thread.sleep(2000);
    Path proxyXml = Paths.get(this.getClass().getResource("/proxy-settings.xml").toURI());
    Path proxyXmlPatched = temp.newFile("settings.xml").toPath();
    assertThat(proxyXml).exists();
    replaceInFile(proxyXml, proxyXmlPatched, "8080", String.valueOf(proxy.port()));

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/many-source-dirs"))
      .setGoals(cleanPackageSonarGoal());
    build.addArgument("--settings=" + proxyXmlPatched.toAbsolutePath().toString());
    build.addArgument("-X");
    build.addArgument("-U");
    BuildResult result = orchestrator.executeBuildQuietly(build);

    assertThat(result.getLogs()).contains("Setting proxy properties");
    assertThat(proxy.seen()).isNotEmpty();
  }

  private void replaceInFile(Path srcFilePath, Path dstFilePath, String str, String replacement) throws IOException {
    List<String> lines = Files.readAllLines(srcFilePath, StandardCharsets.UTF_8);
    lines = lines.stream().map(s -> s.replaceAll(str, replacement)).collect(Collectors.toList());
    Files.write(dstFilePath, lines);
  }
}
