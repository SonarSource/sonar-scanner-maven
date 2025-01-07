/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource SA
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
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.LogOutput;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScannerFactoryTest {
  private LogOutput logOutput;
  private RuntimeInformation runtimeInformation;
  private MojoExecution mojoExecution;
  private MavenSession mavenSession;
  private MavenProject rootProject;
  private PropertyDecryptor propertyDecryptor;
  private Properties envProps;

  private Proxy httpsProxy;

  @BeforeEach
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

    // Set up default proxy
    httpsProxy = new Proxy();
    httpsProxy.setActive(true);
    httpsProxy.setProtocol("https");
    httpsProxy.setPort(443);
    httpsProxy.setHost("myhost");
    httpsProxy.setUsername("toto");
    httpsProxy.setPassword("some-secret");
    httpsProxy.setNonProxyHosts("sonarcloud.io|*.sonarsource.com");
  }

  @AfterEach
  @BeforeEach
  public void clearProxyProperties() {
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    System.clearProperty("http.proxyUser");
    System.clearProperty("http.proxyPassword");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  void http_proxy_properties_are_derived_from_maven_settings() {
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
    assertThat(System.getProperty("https.proxyHost")).isNull();
  }

  @Test
  void https_proxy_properties_are_derived_from_maven_settings() {
    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(httpsProxy));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = mock(Log.class);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();
    assertThat(System.getProperty("https.proxyHost")).isEqualTo("myhost");
    assertThat(System.getProperty("https.proxyPort")).isEqualTo("443");
    assertThat(System.getProperty("http.proxyUser")).isEqualTo("toto");
    assertThat(System.getProperty("http.proxyPassword")).isEqualTo("some-secret");
    assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("sonarcloud.io|*.sonarsource.com");

    assertThat(System.getProperty("http.proxyHost")).isNull();
    assertThat(System.getProperty("http.proxyPort")).isNull();
  }


  @Test
  void http_and_https_proxy_properties_are_derived_from_maven_settings() {
    Proxy proxyWithBothProtocols = httpsProxy.clone();
    proxyWithBothProtocols.setProtocol("http|https");
    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(proxyWithBothProtocols));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = mock(Log.class);
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();
    assertThat(System.getProperty("https.proxyHost")).isEqualTo("myhost");
    assertThat(System.getProperty("https.proxyPort")).isEqualTo("443");
    assertThat(System.getProperty("http.proxyHost")).isEqualTo("myhost");
    assertThat(System.getProperty("http.proxyPort")).isEqualTo("443");
    assertThat(System.getProperty("http.proxyUser")).isEqualTo("toto");
    assertThat(System.getProperty("http.proxyPassword")).isEqualTo("some-secret");
    assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("sonarcloud.io|*.sonarsource.com");
  }

  @Test
  void proxy_properties_are_not_set_when_no_active_proxy_is_provided() {
    Settings settings = new Settings();
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = spy(new DefaultLog(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "no-proxy")));
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();

    assertProxySettingsAreNotSet();

    verify(log, times(1)).debug("Skipping proxy settings: No active proxy detected.");
  }

  @Test
  void proxy_properties_are_not_set_when_protocol_is_null() {
    Proxy proxyWithNullProtocol = httpsProxy.clone();
    proxyWithNullProtocol.setProtocol(null);
    proxyWithNullProtocol.setId("null-protocol-proxy");

    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(proxyWithNullProtocol));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = spy(new DefaultLog(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "null-protocol-proxy")));
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();

    assertProxySettingsAreNotSet();

    verify(log, times(1)).warn("Skipping proxy settings: an active proxy was detected (id: null-protocol-proxy) but the protocol is null.");
  }

  @Test
  void proxy_properties_are_not_set_when_protocol_is_blank() {
    Proxy proxyWithNullProtocol = httpsProxy.clone();
    proxyWithNullProtocol.setProtocol("   | |");
    proxyWithNullProtocol.setId("null-protocol-proxy");

    Settings settings = new Settings();
    settings.setProxies(Collections.singletonList(proxyWithNullProtocol));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = spy(new DefaultLog(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "null-protocol-proxy")));
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();

    assertProxySettingsAreNotSet();

    verify(log, times(1)).warn("Skipping proxy settings: an active proxy was detected (id: null-protocol-proxy) but the protocol was not recognized.");
  }

  @Test
  void a_warning_is_logged_when_a_proxy_protocol_is_not_supported() {
    Settings settings = new Settings();
    Proxy proxyWithUnrecognizableProtocol = httpsProxy.clone();
    proxyWithUnrecognizableProtocol.setId("unknown-protocol-proxy");
    proxyWithUnrecognizableProtocol.setProtocol("unknown-proto|https");
    settings.setProxies(Collections.singletonList(proxyWithUnrecognizableProtocol));
    when(mavenSession.getSettings()).thenReturn(settings);

    Log log = spy(new DefaultLog(new ConsoleLogger(ConsoleLogger.LEVEL_DEBUG, "unrecognizable-protocol")));
    ScannerFactory factory = new ScannerFactory(logOutput, log, runtimeInformation, mojoExecution, mavenSession, envProps, propertyDecryptor);
    factory.create();

    assertThat(System.getProperty("https.proxyHost")).isEqualTo("myhost");
    assertThat(System.getProperty("https.proxyPort")).isEqualTo("443");
    assertThat(System.getProperty("http.proxyUser")).isEqualTo("toto");
    assertThat(System.getProperty("http.proxyPassword")).isEqualTo("some-secret");
    assertThat(System.getProperty("http.nonProxyHosts")).isEqualTo("sonarcloud.io|*.sonarsource.com");

    verify(log, times(1)).isDebugEnabled();
    verify(log, times(1)).debug("Setting proxy properties");
    verify(log, times(1)).warn("Setting proxy properties: one or multiple protocols of the active proxy (id: unknown-protocol-proxy) are not supported (protocols: unknown-proto).");
  }

  @Test
  void testProperties() {
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
  void testDebug() {
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

  static void assertProxySettingsAreNotSet() {
    assertThat(System.getProperty("https.proxyHost")).isNull();
    assertThat(System.getProperty("https.proxyPort")).isNull();
    assertThat(System.getProperty("http.proxyUser")).isNull();
    assertThat(System.getProperty("http.proxyPassword")).isNull();
    assertThat(System.getProperty("http.nonProxyHosts")).isNull();
    assertThat(System.getProperty("http.proxyHost")).isNull();
    assertThat(System.getProperty("http.proxyPort")).isNull();
  }
}
