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

import java.util.Collection;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * A class to handle maven plugins
 *
 * @since 1.10
 */
public class MavenPlugin {

  private static final String CONFIGURATION_ELEMENT = "configuration";

  private Plugin plugin;

  private Xpp3Dom configuration;

  /**
   * Creates a MavenPlugin based on a Plugin
   *
   * @param plugin the plugin
   */
  public MavenPlugin(Plugin plugin) {
    this.plugin = plugin;
    this.configuration = (Xpp3Dom) plugin.getConfiguration();
    if (this.configuration == null) {
      configuration = new Xpp3Dom(CONFIGURATION_ELEMENT);
      plugin.setConfiguration(this.configuration);
    }
  }

  /**
   * @return the underlying plugin
   */
  public Plugin getPlugin() {
    return plugin;
  }

  /**
   * Gets a parameter of the plugin based on its key
   *
   * @param key the param key
   * @return the parameter if exist, null otherwise
   */
  public String getParameter(String key) {
    Xpp3Dom node = findNodeWith(key);
    return node == null ? null : node.getValue();
  }

  private static int getIndex(String key) {
    // parsing index-syntax (e.g. item[1])
    if (key.matches(".*?\\[\\d+\\]")) {
      return Integer.parseInt(StringUtils.substringBetween(key, "[", "]"));
    }
    // for down-compatibility of api we fallback to default 0
    return 0;
  }

  private static String removeIndexSnippet(String key) {
    return StringUtils.substringBefore(key, "[");
  }

  private Xpp3Dom findNodeWith(String key) {
    checkKeyArgument(key);
    String[] keyParts = key.split("/");
    Xpp3Dom node = configuration;
    for (String keyPart : keyParts) {

      if (node.getChildren(removeIndexSnippet(keyPart)).length <= getIndex(keyPart)) {
        return null;
      }

      node = node.getChildren(removeIndexSnippet(keyPart))[getIndex(keyPart)];
      if (node == null) {
        return null;
      }
    }
    return node;
  }

  private static void checkKeyArgument(String key) {
    if (key == null) {
      throw new IllegalArgumentException("Parameter 'key' should not be null.");
    }
  }

  /**
   * Returns a plugin from a pom based on its group id and artifact id
   * <p/>
   * <p>
   * It searches in the build section, then the reporting section and finally the pluginManagement section
   * </p>
   *
   * @param pom the project pom
   * @param groupId the plugin group id
   * @param artifactId the plugin artifact id
   * @return the plugin if it exists, null otherwise
   */
  public static MavenPlugin getPlugin(MavenProject pom, String groupId, String artifactId) {
    if (pom == null) {
      return null;
    }
    // look for plugin in <build> section
    Plugin plugin = null;
    if (pom.getBuildPlugins() != null) {
      plugin = getPlugin(pom.getBuildPlugins(), groupId, artifactId);
    }

    // look for plugin in <pluginManagement> section
    if (pom.getPluginManagement() != null) {
      Plugin pluginManagement = getPlugin(pom.getPluginManagement().getPlugins(), groupId, artifactId);
      if (plugin == null) {
        plugin = pluginManagement;

      } else if (pluginManagement != null) {
        if (pluginManagement.getConfiguration() != null) {
          if (plugin.getConfiguration() == null) {
            plugin.setConfiguration(pluginManagement.getConfiguration());
          } else {
            Xpp3Dom.mergeXpp3Dom((Xpp3Dom) plugin.getConfiguration(),
              (Xpp3Dom) pluginManagement.getConfiguration());
          }
        }
        if (plugin.getDependencies() == null && pluginManagement.getDependencies() != null) {
          plugin.setDependencies(pluginManagement.getDependencies());
        }
        if (plugin.getVersion() == null) {
          plugin.setVersion(pluginManagement.getVersion());
        }
      }
    }

    if (plugin != null) {
      return new MavenPlugin(plugin);
    }
    return null;
  }

  private static Plugin getPlugin(Collection<Plugin> plugins, String groupId, String artifactId) {
    if (plugins == null) {
      return null;
    }

    for (Plugin plugin : plugins) {
      if (isEqual(plugin, groupId, artifactId)) {
        return plugin;
      }
    }
    return null;
  }

  /**
   * Tests whether a plugin has got a given artifact id and group id
   *
   * @param plugin the plugin to test
   * @param groupId the group id
   * @param artifactId the artifact id
   * @return whether the plugin has got group + artifact ids
   */
  private static boolean isEqual(Plugin plugin, String groupId, String artifactId) {
    if (plugin != null && plugin.getArtifactId().equals(artifactId)) {
      if (plugin.getGroupId() == null) {
        return groupId == null || groupId.equals(MavenUtils.GROUP_ID_APACHE_MAVEN)
          || groupId.equals(MavenUtils.GROUP_ID_CODEHAUS_MOJO);
      }
      return plugin.getGroupId().equals(groupId);
    }
    return false;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("groupId",
      plugin.getGroupId()).append("artifactId",
        plugin.getArtifactId())
      .append("version",
        plugin.getVersion())
      .toString();
  }
}
