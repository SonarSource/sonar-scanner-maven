/*
 * property-dump-plugin
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
package org.sonar.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.Plugin;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

public class PropertyDumpPlugin implements Plugin, Sensor {

  private static final Logger LOG = LoggerFactory.getLogger(PropertyDumpPlugin.class);

  @Override
  public void define(Context context) {
    context.addExtension(this);
  }

  @Override
  public void describe(SensorDescriptor sensorDescriptor) {
    sensorDescriptor.name("Property Dump Sensor");
  }

  @Override
  public void execute(SensorContext sensorContext) {
    var props = System.getProperties();
    try {
      Path filePath = sensorContext.fileSystem().workDir().toPath().resolve("dumpSensor.system.properties");
      LOG.info("Dumping system properties to {}", filePath);
      props.stringPropertyNames().stream()
        .filter(key -> key.startsWith("java."))
        .forEach(key -> LOG.info("{}={}", key, props.getProperty(key)));
      props.store(Files.newOutputStream(filePath), null);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
