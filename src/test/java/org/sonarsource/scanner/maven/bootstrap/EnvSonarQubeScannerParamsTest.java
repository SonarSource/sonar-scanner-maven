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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class EnvSonarQubeScannerParamsTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();
  
  @Test
  public void shouldHandleNull() throws MojoExecutionException {
    assertThat(new EnvSonarQubeScannerParams(null).loadEnvironmentProperties()).isEmpty();
  }

  @Test
  public void shouldHandleProps() throws MojoExecutionException {
    String props = "{\"sonar.login\" : \"admin\"}";
    assertThat(new EnvSonarQubeScannerParams(props).loadEnvironmentProperties()).containsExactly(entry("sonar.login", "admin"));
  }
  
  @Test
  public void shouldHandleEmptyJson() throws MojoExecutionException {
    String props = "{}";
    assertThat(new EnvSonarQubeScannerParams(props).loadEnvironmentProperties()).isEmpty();
  }
  
  @Test
  public void shouldHandleJsonErrors() throws MojoExecutionException {
    String props = "";
    exception.expect(MojoExecutionException.class);
    exception.expectMessage("Failed to parse JSON");
    assertThat(new EnvSonarQubeScannerParams(props).loadEnvironmentProperties()).isEmpty();
  }
}
