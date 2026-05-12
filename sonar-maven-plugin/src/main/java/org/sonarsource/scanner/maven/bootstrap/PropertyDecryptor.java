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

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionRequest;
import org.codehaus.plexus.components.secdispatcher.SecDispatcherException;

public class PropertyDecryptor {
  private final Log log;
  private final SettingsDecrypter settingsDecrypter;

  public PropertyDecryptor(Log log, SettingsDecrypter settingsDecrypter) {
    this.log = log;
    this.settingsDecrypter = settingsDecrypter;
  }

  public Map<String, String> decryptProperties(Map<String, String> properties) {
    return properties.entrySet()
      .stream()
      .filter(entry -> entry.getKey().startsWith("sonar."))
      .map(entry -> Map.entry(entry.getKey(), decrypt(entry.getKey(), entry.getValue())))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private String decrypt(String key, String value) {
    // SettingsDecrypter requires a Server or Proxy object to perform decryption
    org.apache.maven.settings.Server server = new org.apache.maven.settings.Server();
    server.setPassword(value);

    SettingsDecryptionRequest request = new org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest(server);
    org.apache.maven.settings.crypto.SettingsDecryptionResult result = settingsDecrypter.decrypt(request);

    // Check if decryption encountered problems (e.g., missing master password)
    if (!result.getProblems().isEmpty()) {
      log.debug("Unable to decrypt property " + key);
      return value;
    }

    return result.getServer().getPassword();
  }
}
