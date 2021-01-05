/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Collections;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.junit.After;
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
  private MojoExecution mojoExecution;
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
    mojoExecution = mock(MojoExecution.class);
    envProps = new Properties();

    Properties system = new Properties();
    system.put("system", "value");
    system.put("user", "value");
    Properties root = new Properties();
    root.put("root", "value");
    envProps.put("env", "value");

    when(mojoExecution.getVersion()).thenReturn("2.0");
    when(runtimeInformation.getMavenVersion()).thenReturn("1.0");
    when(mavenSession.getSystemProperties()).thenReturn(system);
    when(mavenSession.getUserProperties()).thenReturn(new Properties());
    when(mavenSession.getSettings()).thenReturn(new Settings());
    when(rootProject.getProperties()).thenReturn(root);
    when(mavenSession.getCurrentProject()).thenReturn(rootProject);
    propertyDecryptor = new PropertyDecryptor(mock(Log.class), mock(SecDispatcher.class));
  }

  @After
  @Before
  public void clearProxyProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  public void testProxy() {
    Proxy proxy = new Proxy();
    proxy.setActive(true);
    proxy.setProtocol("http");
    proxy.setHost("myhost");
    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(proxy));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = mock(Log.class);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();
    assertThat(System.getProperty("http.proxyHost")).isEqualTo("myhost");
  }

  @Test
  public void testProperties() {
    Log log = mock(Log.class);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scanner = factory.create();
    verify(mavenSession).getSystemProperties();
    verify(rootProject).getProperties();

    assertThat(scanner.appVersion()).isEqualTo("2.0/1.0");
    assertThat(scanner.app()).isEqualTo("ScannerMaven");
    assertThat(scanner.globalProperties()).contains(entry("system", "value"), entry("user", "value"), entry("root", "value"), entry("env", "value"));
  }

  @Test
  public void testDebug() {
    Log log = mock(Log.class);
    when(log.isDebugEnabled()).thenReturn(true);
    ScannerFactory factoryDebug = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scannerDebug = factoryDebug.create();

    when(log.isDebugEnabled()).thenReturn(false);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    EmbeddedScanner scanner = factory.create();

    assertThat(scannerDebug.globalProperties()).contains(entry("sonar.verbose", "true"));
    assertThat(scanner.globalProperties()).doesNotContain(entry("sonar.verbose", "true"));
  }
}
