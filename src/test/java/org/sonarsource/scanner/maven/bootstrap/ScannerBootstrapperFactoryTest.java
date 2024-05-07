/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.sonarsource.scanner.lib.ScannerEngineBootstrapper;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerBootstrapperFactoryTest {
  private final RuntimeInformation runtimeInformation = mock(RuntimeInformation.class, Mockito.RETURNS_DEEP_STUBS);
  private final MojoExecution mojoExecution = mock(MojoExecution.class);
  private final MavenSession mavenSession = mock(MavenSession.class);
  private final MavenProject rootProject = mock(MavenProject.class);
  private final PropertyDecryptor propertyDecryptor = new PropertyDecryptor(mock(Log.class), mock(SecDispatcher.class));
  private final Map<String, String> envProps = new HashMap<>();

  private final Log log = mock(Log.class);
  private final ScannerBootstrapperFactory underTest = spy(new ScannerBootstrapperFactory(log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor));
  private final ScannerEngineBootstrapper mockBootstrapper = mock(ScannerEngineBootstrapper.class);


  @BeforeEach
  public void setUp() {
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

    when(underTest.createScannerEngineBootstrapper(anyString(), anyString())).thenReturn(mockBootstrapper);
  }

  @AfterEach
  @BeforeEach
  public void clearProxyProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  void testProxy() {
    Proxy proxy = new Proxy();
    proxy.setActive(true);
    proxy.setProtocol("https");
    proxy.setHost("myhost");
    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(proxy));
    when(mavenSession.getSettings()).thenReturn(settings);

    underTest.create();

    assertThat(System.getProperty("http.proxyHost")).isEqualTo("myhost");
  }

  @Test
  void testProperties() {

    underTest.create();

    verify(mavenSession).getSystemProperties();
    verify(rootProject).getProperties();
    verify(underTest).createScannerEngineBootstrapper("ScannerMaven", "2.0/1.0");

    ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockBootstrapper).addBootstrapProperties(captor.capture());
    assertThat(captor.getValue()).contains(entry("system", "value"), entry("user", "value"), entry("root", "value"), entry("env", "value"));
  }

  @Test
  void testDebugEnabled() {
    when(log.isDebugEnabled()).thenReturn(true);

    underTest.create();

    verify(mockBootstrapper).setBootstrapProperty("sonar.verbose", "true");
  }

  @Test
  void testDebugDisabled() {
    when(log.isDebugEnabled()).thenReturn(false);

    underTest.create();

    verify(mockBootstrapper, never()).setBootstrapProperty(anyString(), anyString());
  }
}
