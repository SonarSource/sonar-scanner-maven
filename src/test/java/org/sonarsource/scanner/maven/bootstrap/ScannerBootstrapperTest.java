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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.scanner.maven.bootstrap.ScannerBootstrapper.UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE;

public class ScannerBootstrapperTest {
  @Mock
  private Log log;

  @Mock
  private MavenSession session;

  @Mock
  private SecDispatcher securityDispatcher;

  @Mock
  private EmbeddedScanner scanner;

  @Mock
  private MavenProjectConverter mavenProjectConverter;

  private ScannerBootstrapper scannerBootstrapper;

  private Map<String, String> projectProperties;

  @Before
  public void setUp()
    throws MojoExecutionException {
    MockitoAnnotations.initMocks(this);

    MavenProject rootProject = mock(MavenProject.class);
    when(rootProject.isExecutionRoot()).thenReturn(true);
    when(session.getProjects()).thenReturn(Collections.singletonList(rootProject));

    projectProperties = new HashMap<>();
    when(mavenProjectConverter.configure(anyListOf(MavenProject.class), any(MavenProject.class), any(Properties.class))).thenReturn(projectProperties);

    when(scanner.mask(anyString())).thenReturn(scanner);
    when(scanner.unmask(anyString())).thenReturn(scanner);
    scannerBootstrapper = new ScannerBootstrapper(log, session, scanner, mavenProjectConverter, new PropertyDecryptor(log, securityDispatcher));
  }

  @Test
  public void testSQBefore56() {
    when(scanner.serverVersion()).thenReturn("5.1");
    try {
      scannerBootstrapper.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
        .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
    }
  }

  @Test
  public void testSQ56() throws MojoExecutionException {
    when(scanner.serverVersion()).thenReturn("5.6");
    scannerBootstrapper.execute();

    verifyCommonCalls();

    // no extensions, mask or unmask
    verifyNoMoreInteractions(scanner);
  }

  @Test
  public void testVersionComparisonWithBuildNumber() throws MojoExecutionException {
    when(scanner.serverVersion()).thenReturn("6.3.0.12345");
    scannerBootstrapper.execute();

    assertThat(scannerBootstrapper.isVersionPriorTo("4.5")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.3")).isFalse();
    assertThat(scannerBootstrapper.isVersionPriorTo("6.4")).isTrue();
  }

  @Test
  public void testNullServerVersion() {
    when(scanner.serverVersion()).thenReturn(null);

    try {
      scannerBootstrapper.execute();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasCauseExactlyInstanceOf(UnsupportedOperationException.class)
        .hasMessage(UNSUPPORTED_BELOW_SONARQUBE_56_MESSAGE);
    }
  }

  private void verifyCommonCalls() {
    verify(scanner, atLeastOnce()).mask(anyString());
    verify(scanner, atLeastOnce()).unmask(anyString());

    verify(scanner).start();
    verify(scanner).serverVersion();
    verify(scanner).execute(projectProperties);
  }
}
