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

import java.util.Iterator;
import java.util.Properties;

import javax.annotation.Nullable;

import org.apache.maven.plugin.MojoExecutionException;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

public class EnvSonarQubeScannerParams {
  private static final String SONARQUBE_SCANNER_PARAMS = "SONARQUBE_SCANNER_PARAMS";
  private final String scannerParams;

  public EnvSonarQubeScannerParams() {
    this(System.getenv(SONARQUBE_SCANNER_PARAMS));
  }

  public EnvSonarQubeScannerParams(@Nullable String scannerParams) {
    this.scannerParams = scannerParams;
  }

  public Properties loadEnvironmentProperties() throws MojoExecutionException {
    Properties props = new Properties();

    if (scannerParams != null) {
      try {

        JsonValue jsonValue = Json.parse(scannerParams);
        JsonObject jsonObject = jsonValue.asObject();
        Iterator<Member> it = jsonObject.iterator();

        while (it.hasNext()) {
          Member member = it.next();
          String key = member.getName();
          String value = member.getValue().asString();
          props.put(key, value);
        }
      } catch (Exception e) {
        throw new MojoExecutionException("Failed to parse JSON in SONARQUBE_SCANNER_PARAMS environment variable", e);
      }
    }
    return props;
  }
}
