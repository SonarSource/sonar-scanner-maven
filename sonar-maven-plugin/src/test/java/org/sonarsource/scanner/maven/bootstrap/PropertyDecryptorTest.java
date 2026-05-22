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

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PropertyDecryptorTest {

  @Test
  void should_use_security_dispatcher_for_embedded_encrypted_values() {
    SettingsDecrypter settingsDecrypter = request -> mock(SettingsDecryptionResult.class);
    PropertyDecryptor underTest = new PropertyDecryptor(settingsDecrypter, new TestSecurityDispatcher());

    Map<String, String> properties = new LinkedHashMap<>();
    properties.put("sonar.password", "1_2_3_a_b_c{encrypted}");
    properties.put("sonar.relevant.secret.from.cli", "{ABCDEFGHIJKLMNOPQRSTUVWXYZ=}cannot-decrypt");
    properties.put("plain.property", "plain-value");

    Map<String, String> decrypted = underTest.decryptProperties(properties);

    assertThat(decrypted)
      .containsEntry("sonar.password", "123abc")
      .containsEntry("sonar.relevant.secret.from.cli", "{ABCDEFGHIJKLMNOPQRSTUVWXYZ=}cannot-decrypt")
      .containsEntry("plain.property", "plain-value");
  }

  static class TestSecurityDispatcher {
    public String decrypt(String value) {
      if ("1_2_3_a_b_c{encrypted}".equals(value)) {
        return "123abc";
      }
      throw new IllegalArgumentException("Cannot decrypt");
    }
  }
}
