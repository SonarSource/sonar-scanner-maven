/*
 * SonarQube Scanner for Maven :: Reactor Converter
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.scanner.maven.converter;

/**
 * Names of the analysis properties this module produces.
 *
 * <p>These are the wire format shared with every scanner: the same keys are declared by the
 * sonar-scanner-java-library (as {@code AnalysisProperties}/{@code ScannerProperties}) and read back by the
 * SonarQube scanner engines. They are restated here so that converting a Maven reactor needs nothing but Maven's
 * own libraries: a consumer that embeds this module to inspect a reactor should not be forced to also drag in the
 * library whose job is to download and launch a scanner engine.
 */
public final class SonarProperties {

  public static final String PROJECT_KEY = "sonar.projectKey";
  public static final String PROJECT_NAME = "sonar.projectName";
  public static final String PROJECT_VERSION = "sonar.projectVersion";
  public static final String PROJECT_DESCRIPTION = "sonar.projectDescription";
  public static final String PROJECT_BASEDIR = "sonar.projectBaseDir";
  public static final String PROJECT_SOURCE_DIRS = "sonar.sources";
  public static final String PROJECT_TEST_DIRS = "sonar.tests";
  public static final String PROJECT_SOURCE_ENCODING = "sonar.sourceEncoding";
  public static final String WORK_DIR = "sonar.working.directory";
  private SonarProperties() {
    // only constants
  }
}
