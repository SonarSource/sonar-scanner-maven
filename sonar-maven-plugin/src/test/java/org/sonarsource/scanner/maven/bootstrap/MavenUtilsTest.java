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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


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
    Properties src = new Properties();
    src.put("abc", "123");
    src.put("encrypted1", "{AES}hello1");
    src.put("encrypted2", "{b64}hello2");
    src.put("encrypted3", "{aes-gcm}hello3");
    src.put("comments.around", "comment1{AES}comment2");
    src.put("comment.before", "comment{AES}");
    src.put("sonar.ours", "{aes}let-it-pass");
    src.put("env.SONAR_VAR", "{aes}this-too");

    Map<String, String> destMap = new HashMap<>();
    Properties destProps = new Properties();

    MavenUtils.putRelevant(src, destMap);
    MavenUtils.putRelevant(src, destProps);

    Map<String, String> expected = Map.of(
      "abc", "123",
      "sonar.ours", "{aes}let-it-pass",
      "env.SONAR_VAR", "{aes}this-too"
    );

    assertThat(destMap).containsExactlyInAnyOrderEntriesOf(expected);
    assertThat(destProps).containsExactlyInAnyOrderEntriesOf(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{QwHYDk6iuGUHznl0utkKxm7JT8O/GoH2GtdvjEr/z1FAwwh7Ezaje5EQVVcJFIGc3++l6trbNMNLON9raqev2A==}",
    "{[name=master,cipher=AES/GCM/NoPadding,version=4.1]uCO4dz5EbmShH1fFS7iZ1pVleaYjZBPYG2T+i6Vg6bwZ7eg0kRHBiS8dgjqU9zU+NJMZtsgLe8SWRc06ElnrfMrfbUuhqe3HGDqtJGTtuzu2hxlgZ2d14Q==}",
    "{[name=master,cipher=AES/GCM/NoPadding,version=4]Y0z68Gt6+bNZRnBRB2LTwpSn1S/pWE4AyX4mAVZV48V5kOJrNjPATUCvof76niWjiw==}",
    "{h79PWw5IoHYHmCAN9MfuaGSOcZ54HyOgD7PUCpOTvNo=}"
  })
  void testIrrelevantEncryptedValues(String encryptedString) {
    var propertyName = "my.org.password";

    // 1. Assert the raw encrypted string is considered irrelevant
    assertThat(MavenUtils.isIrrelevantEncryptedProperty(propertyName, encryptedString))
      .as("Should be true for exact encrypted format")
      .isTrue();

    // 2. Assert that prepending text makes it relevant (fails the check)
    assertThat(MavenUtils.isIrrelevantEncryptedProperty(propertyName, "some comment" + encryptedString))
      .as("Should be false when prefixed with text")
      .isTrue();

    // 3. Assert that appending text makes it relevant (fails the check)
    assertThat(MavenUtils.isIrrelevantEncryptedProperty(propertyName, encryptedString + "some comment"))
      .as("Should be false when suffixed with text")
      .isTrue();
  }
}
