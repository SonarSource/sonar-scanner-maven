/*
 * property-dump-plugin
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class PropertyDumpPluginTest {

  @Test
  void test_plugin_execute(@TempDir Path workDir) throws IOException {
    SensorContextTester sensorContext = SensorContextTester.create(Path.of("src", "test", "java"));
    sensorContext.fileSystem().setWorkDir(workDir);
    PropertyDumpPlugin plugin = new PropertyDumpPlugin();

    // sensor properties
    sensorContext.settings().setProperty("sonar.my-settings-prop", "my-value");

    // environment variables
    plugin.environmentVariables.put("MY_ENV1", "42");
    plugin.environmentVariables.put("MY_ENV2", "666");

    // system properties
    plugin.systemProperties.setProperty("sonar.my-prop1", "Foo");
    plugin.systemProperties.setProperty("sonar.my-prop2", "Bar");

    // define which elements to dump
    plugin.environmentVariables.put("DUMP_SENSOR_PROPERTIES", "sonar.my-settings-prop,sonar.unknown-settings-prop");
    plugin.environmentVariables.put("DUMP_ENV_PROPERTIES", "MY_ENV1,MY_ENV2,UNKNOWN_ENV");
    plugin.environmentVariables.put("DUMP_SYSTEM_PROPERTIES", "sonar.my-prop1,sonar.my-prop2,sonar.unknown-prop");

    plugin.execute(sensorContext);

    List<String> lines = Files.readAllLines(workDir.resolve("dumpSensor.system.properties"), UTF_8);
    String result = lines.stream()
      .filter(line -> !line.startsWith("#"))
      .sorted()
      .collect(Collectors.joining("\n"));

    assertThat(result).isEqualTo("""
      MY_ENV1=42
      MY_ENV2=666
      UNKNOWN_ENV=
      sonar.my-prop1=Foo
      sonar.my-prop2=Bar
      sonar.my-settings-prop=my-value
      sonar.unknown-prop=
      sonar.unknown-settings-prop=""");
  }

}
