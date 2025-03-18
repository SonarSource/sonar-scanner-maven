/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;

/**
 * An utility class to manipulate Maven concepts
 *
 * @since 1.10
 */
public final class MavenUtils {

  public static final String GROUP_ID_APACHE_MAVEN = "org.apache.maven.plugins";

  public static final String GROUP_ID_CODEHAUS_MOJO = "org.codehaus.mojo";

  private MavenUtils() {
    // utility class with only static methods
  }

  /**
   * @param pom the project pom
   * @return source encoding
   */
  @CheckForNull
  public static String getSourceEncoding(MavenProject pom) {
    return pom.getProperties().getProperty("project.build.sourceEncoding");
  }

  /**
   * Search for a configuration setting of an other plugin for a configuration setting.
   *
   * @param project      the current maven project to get the configuration from.
   * @param groupId      the group id of the plugin to search for
   * @param artifactId   the artifact id of the plugin to search for
   * @param optionName   the option to get from the configuration
   * @param defaultValue the default value if the configuration was not found
   * @return the value of the option configured in the plugin configuration
   */
  public static String getPluginSetting(MavenProject project, String groupId, String artifactId, String optionName, @Nullable String defaultValue) {
    MavenPlugin plugin = MavenPlugin.getPlugin(project, groupId, artifactId);
    if (plugin != null) {
      return StringUtils.defaultIfEmpty(plugin.getParameter(optionName), defaultValue);
    }
    return defaultValue;
  }

  static void putAll(Properties src, Map<String, String> dest) {
    for (final String name : src.stringPropertyNames()) {
      dest.put(name, src.getProperty(name));
    }
  }

  /**
   * Joins a list of strings that may contain commas by wrapping those strings in double quotes, like in CSV format.
   * <p>
   * For example:
   * values = { "/home/users/me/artifact-123,456.jar", "/opt/lib" }
   * return is the string: "\"/home/users/me/artifact-123,456.jar\",/opt/lib"
   *
   * @param values
   * @return a string having all the values separated by commas
   * and each single value that contains a comma wrapped in double quotes
   */
  public static String joinAsCsv(List<String> values) {
    return values.stream()
      .map(MavenUtils::escapeCommas)
      .collect(Collectors.joining(","));
  }

  private static String escapeCommas(String value) {
    // escape only when needed
    return value.contains(",") ? ("\"" + value + "\"") : value;
  }

  public static List<String> splitAsCsv(String joined) {
    List<String> collected = new ArrayList<>();
    if (joined.indexOf('"') == -1) {
      return Arrays.asList(joined.split(","));
    }
    int start = 0;
    int end = joined.length() - 1;
    while (start < end && end < joined.length()) {
      if (joined.charAt(start) == '"') {
        end = joined.indexOf('"', start + 1);
        String value = joined.substring(start + 1, end);
        collected.add(value);
        int nextComma = joined.indexOf(",", end);
        if (nextComma == -1) {
          break;
        }
        start = nextComma + 1;
      } else {
        int nextComma = joined.indexOf(",", start);
        if (nextComma == -1) {
          end = joined.length();
        } else {
          end = nextComma;
        }
        String value = joined.substring(start, end);
        collected.add(value);
        start = end + 1;
      }
      end = start + 1;
    }

    return collected;
  }

}
