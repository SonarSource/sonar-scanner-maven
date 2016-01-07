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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

class ExceptionHandling {

  private ExceptionHandling() {
    // Hide public constructor
  }

  static RuntimeException handle(Exception e, Log log) throws MojoExecutionException {
    Throwable source = e;
    if ("org.sonar.runner.impl.RunnerException".equals(e.getClass().getName()) && e.getCause() != null) {
      source = e.getCause();
    }
    throw new MojoExecutionException(source.getMessage(), source);
  }

  static RuntimeException handle(String message, Log log)
    throws MojoExecutionException {
    return handle(new MojoExecutionException(message), log);
  }
}
