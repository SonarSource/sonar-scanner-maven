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
import java.util.stream.Collectors;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;

public class PropertyDecryptor {
  private final Log log;
  private final SettingsDecrypter settingsDecrypter;

  public PropertyDecryptor(Log log, SettingsDecrypter settingsDecrypter) {
    this.log = log;
    this.settingsDecrypter = settingsDecrypter;
  }

  public Map<String, String> decryptProperties(Map<String, String> properties) {
    // 1. Identify and wrap encrypted sonar properties into Server objects
    List<Server> serversToDecrypt = properties.entrySet().stream()
      .filter(entry -> entry.getKey().startsWith("sonar.") && entry.getValue()!= null)
      .map(entry -> {
        org.apache.maven.settings.Server s = new org.apache.maven.settings.Server();
        s.setId(entry.getKey()); // Use the key as the ID to track it
        s.setPassword(entry.getValue());
        return s;
      })
      .collect(java.util.stream.Collectors.toList());

    // 2. Perform batch decryption in one call
    SettingsDecryptionRequest request = new org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest();
    request.setServers(serversToDecrypt);
    org.apache.maven.settings.crypto.SettingsDecryptionResult result = settingsDecrypter.decrypt(request);

    // 3. Map decrypted results back to a lookup map
    Map<String, String> decryptedMap = result.getServers().stream()
      .collect(java.util.stream.Collectors.toMap(
        org.apache.maven.settings.Server::getId,
        org.apache.maven.settings.Server::getPassword
      ));

    // 4. Return the original map with decrypted values where applicable
    return properties.entrySet().stream()
      .collect(java.util.stream.Collectors.toMap(
        Map.Entry::getKey,
        entry -> decryptedMap.getOrDefault(entry.getKey(), entry.getValue())
      ));
  }
}
