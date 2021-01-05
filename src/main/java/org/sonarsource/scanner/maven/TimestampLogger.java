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
package org.sonarsource.scanner.maven;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.maven.plugin.logging.Log;

public class TimestampLogger implements Log {
  private final DateTimeFormatter timeFormatter;
  private final Log log;

  public TimestampLogger(Log log) {
    this.log = log;
    this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS ");
  }

  @Override
  public void debug(CharSequence content) {
    log.debug(getCurrentTimeStamp() + content);
  }

  @Override
  public void debug(CharSequence content, Throwable error) {
    log.debug(getCurrentTimeStamp() + content, error);
  }

  @Override
  public void info(CharSequence content) {
    log.info(getCurrentTimeStamp() + content);
  }

  @Override
  public void info(CharSequence content, Throwable error) {
    log.info(getCurrentTimeStamp() + content, error);
  }

  @Override
  public void warn(CharSequence content) {
    log.warn(getCurrentTimeStamp() + content);
  }

  @Override
  public void warn(CharSequence content, Throwable error) {
    log.warn(getCurrentTimeStamp() + content, error);
  }

  @Override
  public void error(CharSequence content) {
    log.error(getCurrentTimeStamp() + content);
  }

  @Override
  public void error(CharSequence content, Throwable error) {
    log.error(getCurrentTimeStamp() + content, error);
  }

  private String getCurrentTimeStamp() {
    LocalTime currentTime = LocalTime.now();
    return currentTime.format(timeFormatter);
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  @Override
  public void debug(Throwable error) {
    log.debug(getCurrentTimeStamp(), error);
  }

  @Override
  public boolean isInfoEnabled() {
    return log.isInfoEnabled();
  }

  @Override
  public void info(Throwable error) {
    log.info(getCurrentTimeStamp(), error);
  }

  @Override
  public boolean isWarnEnabled() {
    return log.isWarnEnabled();
  }

  @Override
  public void warn(Throwable error) {
    log.warn(getCurrentTimeStamp(), error);
  }

  @Override
  public boolean isErrorEnabled() {
    return log.isErrorEnabled();
  }

  @Override
  public void error(Throwable error) {
    log.error(getCurrentTimeStamp(), error);
  }
}
