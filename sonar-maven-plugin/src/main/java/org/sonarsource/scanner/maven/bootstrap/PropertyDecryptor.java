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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;

import static org.sonarsource.scanner.maven.bootstrap.MavenUtils.isIrrelevantEncryptedProperty;

@SuppressWarnings("deprecation")
public class PropertyDecryptor {
  private final SettingsDecrypter settingsDecrypter;
  private final Object securityDispatcher;

  public PropertyDecryptor(SettingsDecrypter settingsDecrypter) {
    this(settingsDecrypter, null);
  }

  public PropertyDecryptor(SettingsDecrypter settingsDecrypter, @Nullable Object securityDispatcher) {
    this.settingsDecrypter = settingsDecrypter;
    this.securityDispatcher = securityDispatcher;
  }


  public Map<String, String> decryptProperties(Map<String, String> properties) {
    Map<String, String> decryptedWithDispatcher = properties.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> decryptWithSecurityDispatcher(entry.getValue())
      ));

    if (settingsDecrypter == null) {
      return decryptedWithDispatcher;
    }
    // 1. Identify and wrap encrypted sonar properties into Server objects
    List<Server> serversToDecrypt = decryptedWithDispatcher.entrySet().stream()
      .filter(entry -> !isIrrelevantEncryptedProperty(entry.getKey(), entry.getValue()))
      .map(entry -> {
        Server s = new Server();
        s.setId(entry.getKey());
        s.setPassword(entry.getValue());
        return s;
      })
      .collect(java.util.stream.Collectors.toList());

    // 2. Perform batch decryption in one call
    SettingsDecryptionRequest request = new DefaultSettingsDecryptionRequest();
    request.setServers(serversToDecrypt);
    SettingsDecryptionResult result = settingsDecrypter.decrypt(request);

    // 3. Map decrypted results back to a lookup map
    Map<String, String> decryptedMap = result.getServers().stream()
      .collect(Collectors.toMap(
        Server::getId,
        Server::getPassword
      ));

    // 4. Return the original map with decrypted values where applicable
    return decryptedWithDispatcher.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> decryptedMap.getOrDefault(entry.getKey(), entry.getValue())
      ));
  }

  private String decryptWithSecurityDispatcher(String value) {
    if (securityDispatcher == null || value == null) {
      return value;
    }
    try {
      Object decrypted = securityDispatcher.getClass().getMethod("decrypt", String.class).invoke(securityDispatcher, value);
      return decrypted instanceof String ? (String) decrypted : value;
    } catch (Exception e) {
      return value;
    }
  }
}
