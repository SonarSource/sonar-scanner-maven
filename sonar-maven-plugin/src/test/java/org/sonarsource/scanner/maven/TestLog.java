/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package org.sonarsource.scanner.maven;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;

public class TestLog implements Log {
  public void setLogLevel(LogLevel logLevel) {
    this.logLevel = logLevel;
  }

  public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
  }

  private LogLevel logLevel;
  public final List<String> logs = new ArrayList<>();

  public TestLog(LogLevel logLevel) {
    this.logLevel = logLevel;
  }

  @Override
  public boolean isDebugEnabled() {
    return logLevel == LogLevel.DEBUG;
  }

  @Override
  public void debug(CharSequence content) {
    logs.add("[DEBUG] " + content);
  }

  @Override
  public void debug(CharSequence content, Throwable error) {
    debug(content);
  }

  @Override
  public void debug(Throwable error) {
    debug(error.getMessage());
  }

  @Override
  public boolean isInfoEnabled() {
    return logLevel == LogLevel.INFO;
  }

  @Override
  public void info(CharSequence content) {
    logs.add("[INFO] " + content);
  }

  @Override
  public void info(CharSequence content, Throwable error) {
    info(content);
  }

  @Override
  public void info(Throwable error) {
    info(error.getMessage());
  }

  @Override
  public boolean isWarnEnabled() {
    return logLevel == LogLevel.WARN;
  }

  @Override
  public void warn(CharSequence content) {
    logs.add("[WARN] " + content);
  }

  @Override
  public void warn(CharSequence content, Throwable error) {
    warn(content);
  }

  @Override
  public void warn(Throwable error) {
    warn(error.getMessage());
  }

  @Override
  public boolean isErrorEnabled() {
    return logLevel == LogLevel.ERROR;
  }

  @Override
  public void error(CharSequence content) {
    logs.add("[ERROR] " + content);
  }

  @Override
  public void error(CharSequence content, Throwable error) {
    error(content);
  }

  @Override
  public void error(Throwable error) {
    error(error.getMessage());
  }
}
