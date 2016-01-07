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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.PluginContainerException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

/**
 * {@link MavenPluginManager} helper to deal with API changes between Maven 3.0.x and 3.1.x, ie switch from Sonatype
 * Aether (in org.sonatype.aether package) to Eclipse Aether (in org.eclipse.aether package). Inspired from
 * maven-reporting-exec
 * 
 * @since 2.1
 */
@Component(role = MavenPluginManagerHelper.class)
public class DefaultMavenPluginManagerHelper
  implements MavenPluginManagerHelper {
  @Requirement
  private Logger logger;

  @Requirement
  protected MavenPluginManager mavenPluginManager;

  private Method setupPluginRealm;

  private Method getPluginDescriptor;

  private Method getRepositorySession;

  public DefaultMavenPluginManagerHelper() {
    try {
      for (Method m : MavenPluginManager.class.getMethods()) {
        if ("setupPluginRealm".equals(m.getName())) {
          setupPluginRealm = m;
        } else if ("getPluginDescriptor".equals(m.getName())) {
          getPluginDescriptor = m;
        }
      }
    } catch (SecurityException e) {
      logger.warn("unable to find MavenPluginManager.setupPluginRealm() method", e);
    }

    try {
      for (Method m : MavenSession.class.getMethods()) {
        if ("getRepositorySession".equals(m.getName())) {
          getRepositorySession = m;
          break;
        }
      }
    } catch (SecurityException e) {
      logger.warn("unable to find MavenSession.getRepositorySession() method", e);
    }
  }

  @Override
  public PluginDescriptor getPluginDescriptor(Plugin plugin, MavenSession session)
    throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException {
    try {
      Object repositorySession = getRepositorySession.invoke(session);
      List<?> remoteRepositories = session.getCurrentProject().getRemotePluginRepositories();

      return (PluginDescriptor) getPluginDescriptor.invoke(mavenPluginManager, plugin, remoteRepositories,
        repositorySession);
    } catch (IllegalArgumentException e) {
      logger.warn("IllegalArgumentException during MavenPluginManager.getPluginDescriptor() call", e);
    } catch (IllegalAccessException e) {
      logger.warn("IllegalAccessException during MavenPluginManager.getPluginDescriptor() call", e);
    } catch (InvocationTargetException e) {
      Throwable target = e.getTargetException();
      if (target instanceof PluginResolutionException) {
        throw (PluginResolutionException) target;
      }
      if (target instanceof PluginDescriptorParsingException) {
        throw (PluginDescriptorParsingException) target;
      }
      if (target instanceof InvalidPluginDescriptorException) {
        throw (InvalidPluginDescriptorException) target;
      }
      if (target instanceof RuntimeException) {
        throw (RuntimeException) target;
      }
      if (target instanceof Error) {
        throw (Error) target;
      }
      logger.warn("Exception during MavenPluginManager.getPluginDescriptor() call", e);
    }

    return null;
  }

  @Override
  public void setupPluginRealm(PluginDescriptor pluginDescriptor, MavenSession session, ClassLoader parent,
    List<String> imports)
      throws PluginResolutionException, PluginContainerException {
    try {
      setupPluginRealm.invoke(mavenPluginManager, pluginDescriptor, session, parent, imports, null);
    } catch (IllegalArgumentException e) {
      logger.warn("IllegalArgumentException during MavenPluginManager.setupPluginRealm() call", e);
    } catch (IllegalAccessException e) {
      logger.warn("IllegalAccessException during MavenPluginManager.setupPluginRealm() call", e);
    } catch (InvocationTargetException e) {
      Throwable target = e.getTargetException();
      if (target instanceof PluginResolutionException) {
        throw (PluginResolutionException) target;
      }
      if (target instanceof PluginContainerException) {
        throw (PluginContainerException) target;
      }
      if (target instanceof RuntimeException) {
        throw (RuntimeException) target;
      }
      if (target instanceof Error) {
        throw (Error) target;
      }
      logger.warn("Exception during MavenPluginManager.setupPluginRealm() call", e);
    }
  }
}
