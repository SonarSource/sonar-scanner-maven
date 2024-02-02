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
package org.sonarsource.scanner.maven;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampLoggerTest {

  @Test
  void all_messages_are_timestamped() {
    TestLog mavenLog = new TestLog(TestLog.LogLevel.DEBUG);
    TimestampLogger logger = new TimestampLogger(mavenLog);

    LocalTime expectedCurrentTime = LocalTime.of(10, 10, 30);

    try (MockedStatic<LocalTime> localTimeMockedStatic = Mockito.mockStatic(LocalTime.class)) {
      localTimeMockedStatic.when(LocalTime::now).thenReturn(expectedCurrentTime);

      logger.debug("My debug message");
      logger.debug("My debug message", new IllegalArgumentException());
      logger.debug(new IllegalArgumentException());
      logger.info("My info message");
      logger.info("My info message", new IllegalArgumentException());
      logger.info(new IllegalArgumentException());
      logger.warn("My warn message");
      logger.warn("My warn message", new IllegalArgumentException());
      logger.warn(new IllegalArgumentException());
      logger.error("My error message");
      logger.error("My error message", new IllegalArgumentException());
      logger.error(new IllegalArgumentException());
    }


    assertThat(mavenLog.logs).containsOnly(
      "[DEBUG] 10:10:30.000 My debug message",
      "[DEBUG] 10:10:30.000 My debug message",
      "[DEBUG] 10:10:30.000 ",
      "[INFO] 10:10:30.000 My info message",
      "[INFO] 10:10:30.000 My info message",
      "[INFO] 10:10:30.000 ",
      "[WARN] 10:10:30.000 My warn message",
      "[WARN] 10:10:30.000 My warn message",
      "[WARN] 10:10:30.000 ",
      "[ERROR] 10:10:30.000 My error message",
      "[ERROR] 10:10:30.000 My error message",
      "[ERROR] 10:10:30.000 "
    );
  }

  @Test
  void log_level_matches_underlying_maven_log_level() {
    TestLog debugLevelLog = new TestLog(TestLog.LogLevel.DEBUG);
    TestLog infoLevelLog = new TestLog(TestLog.LogLevel.INFO);
    TestLog warnLevelLog = new TestLog(TestLog.LogLevel.WARN);
    TestLog errorLevelLog = new TestLog(TestLog.LogLevel.ERROR);

    assertThat(new TimestampLogger(debugLevelLog).isDebugEnabled()).isTrue();
    assertThat(new TimestampLogger(debugLevelLog).isInfoEnabled()).isFalse();
    assertThat(new TimestampLogger(debugLevelLog).isWarnEnabled()).isFalse();
    assertThat(new TimestampLogger(debugLevelLog).isErrorEnabled()).isFalse();

    assertThat(new TimestampLogger(infoLevelLog).isDebugEnabled()).isFalse();
    assertThat(new TimestampLogger(infoLevelLog).isInfoEnabled()).isTrue();
    assertThat(new TimestampLogger(infoLevelLog).isWarnEnabled()).isFalse();
    assertThat(new TimestampLogger(infoLevelLog).isErrorEnabled()).isFalse();

    assertThat(new TimestampLogger(warnLevelLog).isDebugEnabled()).isFalse();
    assertThat(new TimestampLogger(warnLevelLog).isInfoEnabled()).isFalse();
    assertThat(new TimestampLogger(warnLevelLog).isWarnEnabled()).isTrue();
    assertThat(new TimestampLogger(warnLevelLog).isErrorEnabled()).isFalse();

    assertThat(new TimestampLogger(errorLevelLog).isDebugEnabled()).isFalse();
    assertThat(new TimestampLogger(errorLevelLog).isInfoEnabled()).isFalse();
    assertThat(new TimestampLogger(errorLevelLog).isWarnEnabled()).isFalse();
    assertThat(new TimestampLogger(errorLevelLog).isErrorEnabled()).isTrue();
  }

  private static class TestLog implements Log {
    private enum LogLevel {
      DEBUG,
      INFO,
      WARN,
      ERROR,
    }

    private final LogLevel logLevel;
    private final List<String> logs = new ArrayList<>();

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
}