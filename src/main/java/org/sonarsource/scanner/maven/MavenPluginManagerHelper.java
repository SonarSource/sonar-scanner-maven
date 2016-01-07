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
package org.sonarsource.scanner.maven;

import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;

/**
 * {@link MavenPluginManager} helper to deal with API changes between Maven 3.0.x and 3.1.x. Inspired from
 * maven-reporting-exec
 * 
 * @since 2.1
 */
public interface MavenPluginManagerHelper {
  PluginDescriptor getPluginDescriptor(Plugin plugin, MavenSession session) throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException;

  void setupPluginRealm(PluginDescriptor pluginDescriptor, MavenSession session, ClassLoader parent, List<String> imports)
    throws PluginResolutionException, PluginContainerException;

}
