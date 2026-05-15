/*
 * SonarQube Scanner for Maven
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
package org.sonarsource.scanner.maven.bootstrap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class MavenUtilsTest {
  @Test
  void testJoinAsCsv() {
    List<String> values = Arrays.asList("/home/users/me/artifact-123,456.jar", "/opt/lib");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("\"/home/users/me/artifact-123,456.jar\",/opt/lib");

    values = Arrays.asList("/opt/lib", "/home/users/me/artifact-123,456.jar");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("/opt/lib,\"/home/users/me/artifact-123,456.jar\"");

    values = Arrays.asList("/opt/lib", "/home/users/me");
    assertThat(MavenUtils.joinAsCsv(values)).isEqualTo("/opt/lib,/home/users/me");
  }

  @Test
  void testSplitAsCsv() {
    String[] expectedValues = {"/home/users/me/artifact-123,456.jar", "/opt/lib", "src/main/java"};
    // Single escaped value
    assertThat(MavenUtils.splitAsCsv("\"/home/users/me/artifact-123,456.jar\",/opt/lib,src/main/java"))
      .containsOnly(expectedValues);
    assertThat(MavenUtils.splitAsCsv("/opt/lib,\"/home/users/me/artifact-123,456.jar\",src/main/java"))
      .containsOnly("/opt/lib", "/home/users/me/artifact-123,456.jar", "src/main/java");
    assertThat(MavenUtils.splitAsCsv("/opt/lib,src/main/java,\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);

    // Consecutive escaped values
    assertThat(MavenUtils.splitAsCsv("/opt/lib,\"src/main/java\",\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
    assertThat(MavenUtils.splitAsCsv("\"/opt/lib\",\"src/main/java\",\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
    assertThat(MavenUtils.splitAsCsv("\"/opt/lib\",\"/home/users/me/artifact-123,456.jar\",src/main/java"))
      .containsOnly("/opt/lib", "/home/users/me/artifact-123,456.jar", "src/main/java");

    // Interleaved escaped values
    assertThat(MavenUtils.splitAsCsv("\"/opt/lib\",src/main/java,\"/home/users/me/artifact-123,456.jar\""))
      .containsOnly(expectedValues);
  }

  @Test
  void testPutRelevant() {
    var b64 = "{base64}";
    Properties src = new Properties();

    // prefix "sonar." -> should be in
    src.put("sonar.login", b64);
    src.put("sonar.password", b64);
    src.put("sonar.token", b64);
    src.put("sonar.scanner.keystorePassword", b64);
    src.put("sonar.hello", b64);

    // not prefix "sonar." -> should not be in
    src.put("other.plugin.sonar.api.token", b64);
    src.put("other.plugin.sonar.token", b64);
    src.put("hello.sonar", b64);
    src.put("hello", b64);

    Map<String, String> destMap = new HashMap<>();
    Properties destProps = new Properties();

    MavenUtils.putRelevant(src, destMap);
    MavenUtils.putRelevant(src, destProps);

    Map<String, String> expected = Map.of(
      "sonar.login", b64,
      "sonar.password", b64,
      "sonar.token", b64,
      "sonar.scanner.keystorePassword", b64,
      "sonar.hello", b64
    );

    assertThat(destMap).containsExactlyInAnyOrderEntriesOf(expected);
    assertThat(destProps).containsExactlyInAnyOrderEntriesOf(expected);
  }
}
