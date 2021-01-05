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

import org.apache.maven.plugin.logging.Log;
import org.sonarsource.scanner.api.LogOutput;

public class LogHandler implements LogOutput {
  private Log mavenLog;

  public LogHandler(Log mavenLog) {
    this.mavenLog = mavenLog;
  }

  @Override
  public void log(String log, Level level) {
    switch (level) {
      case TRACE:
      case DEBUG:
        mavenLog.debug(log);
        break;
      case WARN:
        mavenLog.warn(log);
        break;
      case ERROR:
        mavenLog.error(log);
        break;
      case INFO:
      default:
        mavenLog.info(log);
        break;
    }
  }
}
