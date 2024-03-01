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

/**
 * A wrapper around some java.lang.System methods that allows us to mock and work around the default access limitations
 * enforced in Java 16+.
 */
class SystemWrapper {
  private SystemWrapper() {
    /* Nothing done here */
  }
  static String getenv(String name) {
    return System.getenv(name);
  }

  static String getProperty(String name) {
    return System.getProperty(name);
  }
}
