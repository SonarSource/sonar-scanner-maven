/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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

import java.util.Properties;
import org.apache.maven.plugin.logging.Log;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

public class PropertyDecryptor {

  private final Log log;

  private final SecDispatcher securityDispatcher;

  public PropertyDecryptor(Log log, SecDispatcher securityDispatcher) {
    this.log = log;
    this.securityDispatcher = securityDispatcher;
  }

  public Properties decryptProperties(Properties properties) {
    Properties newProperties = new Properties();
    try {
      for (String key : properties.stringPropertyNames()) {
        if (key.contains(".password") || key.contains(".login")) {
          decrypt(properties, newProperties, key);
        }
      }
    } catch (Exception e) {
      log.warn("Unable to decrypt properties", e);
    }
    return newProperties;
  }

  private void decrypt(Properties properties, Properties newProperties, String key) {
    try {
      String decrypted = securityDispatcher.decrypt(properties.getProperty(key));
      newProperties.setProperty(key, decrypted);
    } catch (SecDispatcherException e) {
      log.debug("Unable to decrypt property " + key, e);
    }
  }
}
