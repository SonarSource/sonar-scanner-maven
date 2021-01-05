/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2021 SonarSource SA
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

import java.util.Collection;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * A class to handle maven plugins
 *
 * @since 1.10
 */
public class MavenPlugin {

  private final Xpp3Dom configuration;

  /**
   * Creates a MavenPlugin based on a Plugin
   *
   * @param plugin the plugin
   */
  private MavenPlugin(Object configuration) {
    this.configuration = (Xpp3Dom) configuration;
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

  @CheckForNull
  private Xpp3Dom findNodeWith(String key) {
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

  /**
   * Returns a plugin from a pom based on its group id and artifact id
   * <p>
   * It searches in the build section, then the reporting section and finally the pluginManagement section
   * </p>
   *
   * @param pom the project pom
   * @param groupId the plugin group id
   * @param artifactId the plugin artifact id
   * @return the plugin if it exists, null otherwise
   */
  @CheckForNull
  public static MavenPlugin getPlugin(MavenProject pom, String groupId, String artifactId) {
    Object pluginConfiguration = null;

    // look for plugin in <build> section
    Plugin plugin = getPlugin(pom.getBuildPlugins(), groupId, artifactId);

    if (plugin != null) {
      pluginConfiguration = plugin.getConfiguration();
    } else {
      // look for plugin in reporting
      Reporting reporting = pom.getModel().getReporting();
      if (reporting != null) {
        ReportPlugin reportPlugin = getReportPlugin(reporting.getPlugins(), groupId, artifactId);
        if (reportPlugin != null) {
          pluginConfiguration = reportPlugin.getConfiguration();
        }
      }
    }

    // look for plugin in <pluginManagement> section
    PluginManagement pluginManagement = pom.getPluginManagement();
    if (pluginManagement != null) {
      Plugin pluginFromManagement = getPlugin(pluginManagement.getPlugins(), groupId, artifactId);
      if (pluginFromManagement != null) {
        Object pluginConfigFromManagement = pluginFromManagement.getConfiguration();
        if (pluginConfiguration == null) {
          pluginConfiguration = pluginConfigFromManagement;
        } else if (pluginConfigFromManagement != null) {
          Xpp3Dom.mergeXpp3Dom((Xpp3Dom) pluginConfiguration, (Xpp3Dom) pluginConfigFromManagement);
        }
      }
    }

    if (pluginConfiguration != null) {
      return new MavenPlugin(pluginConfiguration);
    }
    return null;

  }

  @CheckForNull
  private static Plugin getPlugin(Collection<Plugin> plugins, String groupId, String artifactId) {
    for (Plugin plugin : plugins) {
      if (isEqual(plugin, groupId, artifactId)) {
        return plugin;
      }
    }
    return null;
  }

  private static boolean isEqual(Plugin plugin, String groupId, String artifactId) {
    return plugin.getArtifactId().equals(artifactId) && plugin.getGroupId().equals(groupId);
  }

  @CheckForNull
  private static ReportPlugin getReportPlugin(Collection<ReportPlugin> plugins, String groupId, String artifactId) {
    for (ReportPlugin plugin : plugins) {
      if (isEqual(plugin, groupId, artifactId)) {
        return plugin;
      }
    }
    return null;
  }

  private static boolean isEqual(ReportPlugin plugin, String groupId, String artifactId) {
    return plugin.getArtifactId().equals(artifactId) && plugin.getGroupId().equals(groupId);
  }

}
