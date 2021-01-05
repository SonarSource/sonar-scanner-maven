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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.settings.Proxy;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.LogOutput;

public class ScannerFactory {

  private final LogOutput logOutput;
  private final RuntimeInformation runtimeInformation;
  private final MavenSession session;
  private final boolean debugEnabled;
  private final PropertyDecryptor propertyDecryptor;
  private final Properties envProps;
  private final Log log;
  private final MojoExecution mojoExecution;

  public ScannerFactory(LogOutput logOutput, Log log, RuntimeInformation runtimeInformation, MojoExecution mojoExecution, MavenSession session,
    Properties envProps, PropertyDecryptor propertyDecryptor) {
    this.logOutput = logOutput;
    this.log = log;
    this.runtimeInformation = runtimeInformation;
    this.mojoExecution = mojoExecution;
    this.session = session;
    this.debugEnabled = log.isDebugEnabled();
    this.envProps = envProps;
    this.propertyDecryptor = propertyDecryptor;
  }

  public EmbeddedScanner create() {
    setProxySystemProperties();
    EmbeddedScanner scanner = EmbeddedScanner.create("ScannerMaven", mojoExecution.getVersion() + "/" + runtimeInformation.getMavenVersion(), logOutput);

    scanner.addGlobalProperties(createGlobalProperties());

    if (debugEnabled) {
      scanner.setGlobalProperty("sonar.verbose", "true");
    }

    return scanner;
  }

  public Map<String, String> createGlobalProperties() {
    Map<String, String> p = new HashMap<>();
    MavenUtils.putAll(session.getCurrentProject().getProperties(), p);
    MavenUtils.putAll(envProps, p);
    MavenUtils.putAll(session.getSystemProperties(), p);
    MavenUtils.putAll(session.getUserProperties(), p);
    p.putAll(propertyDecryptor.decryptProperties(p));
    return p;
  }

  public void setProxySystemProperties() {
    Proxy activeProxy = session.getSettings().getActiveProxy();

    if (activeProxy != null && activeProxy.getProtocol() != null && activeProxy.getProtocol().contains("http")) {
      log.debug("Setting proxy properties");
      System.setProperty("http.proxyHost", activeProxy.getHost());
      System.setProperty("http.proxyPort", String.valueOf(activeProxy.getPort()));
      System.setProperty("http.proxyUser", StringUtils.defaultString(activeProxy.getUsername(), ""));
      System.setProperty("http.proxyPassword", StringUtils.defaultString(activeProxy.getPassword(), ""));
      System.setProperty("http.nonProxyHosts", StringUtils.defaultString(activeProxy.getNonProxyHosts(), ""));
    }
  }
}
