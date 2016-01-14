/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.runner.api.EmbeddedRunner;
import org.sonar.runner.api.LogOutput;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RunnerFactoryTest {
  private LogOutput logOutput;
  private RuntimeInformation runtimeInformation;
  private MavenSession mavenSession;
  private MavenProject rootProject;
  private PropertyDecryptor propertyDecryptor;

  @Before
  public void setUp() {
    logOutput = mock(LogOutput.class);
    runtimeInformation = mock(RuntimeInformation.class, Mockito.RETURNS_DEEP_STUBS);
    mavenSession = mock(MavenSession.class);
    rootProject = mock(MavenProject.class);

    Properties system = new Properties();
    system.put("system", "value");
    system.put("user", "value");
    Properties root = new Properties();
    root.put("root", "value");

    when(runtimeInformation.getApplicationVersion().toString()).thenReturn("1.0");
    when(mavenSession.getExecutionProperties()).thenReturn(system);
    when(rootProject.getProperties()).thenReturn(root);
    when(mavenSession.getCurrentProject()).thenReturn(rootProject);
    propertyDecryptor = new PropertyDecryptor(mock(Log.class), mock(SecDispatcher.class));
  }

  @Test
  public void testProperties() {

    RunnerFactory factory = new RunnerFactory(logOutput, false, runtimeInformation, mavenSession, propertyDecryptor);
    EmbeddedRunner runner = factory.create();
    verify(mavenSession).getExecutionProperties();
    verify(rootProject).getProperties();

    assertThat(runner.globalProperties()).contains(entry("system", "value"), entry("user", "value"), entry("root", "value"));
    assertThat(runner.globalProperties()).contains(entry("sonar.mojoUseRunner", "true"));
  }

  @Test
  public void testDebug() {
    RunnerFactory factoryDebug = new RunnerFactory(logOutput, true, runtimeInformation, mavenSession, propertyDecryptor);
    RunnerFactory factory = new RunnerFactory(logOutput, false, runtimeInformation, mavenSession, propertyDecryptor);

    EmbeddedRunner runnerDebug = factoryDebug.create();
    EmbeddedRunner runner = factory.create();

    assertThat(runnerDebug.globalProperties()).contains(entry("sonar.verbose", "true"));
    assertThat(runner.globalProperties()).doesNotContain(entry("sonar.verbose", "true"));
  }
}
