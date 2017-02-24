/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.Settings;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.LogOutput;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScannerFactoryTest {
  private LogOutput logOutput;
  private RuntimeInformation runtimeInformation;
  private MavenSession mavenSession;
  private MavenProject rootProject;
  private PropertyDecryptor propertyDecryptor;
  private Properties envProps;

  @Before
  public void setUp() {
    logOutput = mock(LogOutput.class);
    runtimeInformation = mock(RuntimeInformation.class, Mockito.RETURNS_DEEP_STUBS);
    mavenSession = mock(MavenSession.class);
    rootProject = mock(MavenProject.class);
    envProps = new Properties();

    Properties system = new Properties();
    system.put("system", "value");
    system.put("user", "value");
    Properties root = new Properties();
    root.put("root", "value");
    envProps.put("env", "value");

    when(runtimeInformation.getMavenVersion()).thenReturn("1.0");
    when(mavenSession.getSystemProperties()).thenReturn(system);
    when(mavenSession.getUserProperties()).thenReturn(new Properties());
    when(mavenSession.getSettings()).thenReturn(new Settings());
    when(rootProject.getProperties()).thenReturn(root);
    when(mavenSession.getCurrentProject()).thenReturn(rootProject);
    propertyDecryptor = new PropertyDecryptor(mock(Log.class), mock(SecDispatcher.class));
  }

  @Test
  public void testProperties() {
    Log log = mock(Log.class);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scanner = factory.create();
    verify(mavenSession).getSystemProperties();
    verify(rootProject).getProperties();

    assertThat(scanner.globalProperties()).contains(entry("system", "value"), entry("user", "value"), entry("root", "value"), entry("env", "value"));
    assertThat(scanner.globalProperties()).contains(entry("sonar.mojoUseRunner", "true"));
  }

  @Test
  public void testDebug() {
    Log log = mock(Log.class);
    when(log.isDebugEnabled()).thenReturn(true);
    ScannerFactory factoryDebug = new ScannerFactory(logOutput, log, runtimeInformation, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scannerDebug = factoryDebug.create();

    when(log.isDebugEnabled()).thenReturn(false);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scanner = factory.create();

    assertThat(scannerDebug.globalProperties()).contains(entry("sonar.verbose", "true"));
    assertThat(scanner.globalProperties()).doesNotContain(entry("sonar.verbose", "true"));
  }
}
