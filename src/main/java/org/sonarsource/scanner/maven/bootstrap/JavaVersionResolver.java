/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2019 SonarSource SA
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

import java.util.LinkedList;
import java.util.List;

import javax.annotation.CheckForNull;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.converters.basic.StringConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class JavaVersionResolver {
  private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";
  private static final String COMPILE_GOAL = MavenUtils.GROUP_ID_APACHE_MAVEN + ":" + MAVEN_COMPILER_PLUGIN + ":compile";

  private final MavenSession session;
  private final Log log;
  private List<MojoExecution> mojoExecutions;

  public JavaVersionResolver(MavenSession session, LifecycleExecutor lifecycleExecutor, Log log) {
    this.session = session;
    this.log = log;
    this.mojoExecutions = new LinkedList<>();
    try {
      this.mojoExecutions = lifecycleExecutor.calculateExecutionPlan(session, true, COMPILE_GOAL).getMojoExecutions();
    } catch (Exception e) {
      log.warn(String.format("Failed to get mojo executions for goal '%s': %s", COMPILE_GOAL, e.getMessage()));
    }
  }

  /**
   * Returns the version of Java used by the maven compiler plugin
   *
   * @param pom the project pom
   * @return the java version
   */
  @CheckForNull
  public String getTarget(MavenProject pom) {
    return MavenUtils.coalesce(getString(pom, "target"), MavenUtils.getPluginSetting(pom, MavenUtils.GROUP_ID_APACHE_MAVEN, MAVEN_COMPILER_PLUGIN, "target", null));
  }

  @CheckForNull
  public String getSource(MavenProject pom) {
    return MavenUtils.coalesce(getString(pom, "source"), MavenUtils.getPluginSetting(pom, MavenUtils.GROUP_ID_APACHE_MAVEN, MAVEN_COMPILER_PLUGIN, "source", null));

  }

  @CheckForNull
  private String getString(MavenProject pom, String parameter) {
    MavenProject oldProject = session.getCurrentProject();
    try {
      // Switch to the project for which we try to resolve the property.
      session.setCurrentProject(pom);
      for (MojoExecution exec : mojoExecutions) {
        Xpp3Dom configuration = exec.getConfiguration();
        PlexusConfiguration pomConfiguration = new XmlPlexusConfiguration(configuration);
        PlexusConfiguration config = pomConfiguration.getChild(parameter);

        ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(session, exec);
        BasicStringConverter converter = new BasicStringConverter();

        String value = converter.fromExpression(config, expressionEvaluator);
        if (value != null) {
          return value;
        }
      }
    } catch (Exception e) {
      log.warn(String.format("Failed to get parameter '%s' for goal '%s': %s", parameter, COMPILE_GOAL, e.getMessage()));
    } finally {
      session.setCurrentProject(oldProject);
    }

    return null;
  }

  static class BasicStringConverter extends StringConverter {
    @Override
    public String fromExpression(PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator) throws ComponentConfigurationException {
      return (String) super.fromExpression(configuration, expressionEvaluator);
    }

  }
}
