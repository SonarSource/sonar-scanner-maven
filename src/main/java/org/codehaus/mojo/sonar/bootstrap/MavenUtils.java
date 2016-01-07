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
package org.codehaus.mojo.sonar.bootstrap;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * An utility class to manipulate Maven concepts
 *
 * @since 1.10
 */
public final class MavenUtils
{

    private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";

    public static final String GROUP_ID_APACHE_MAVEN = "org.apache.maven.plugins";

    public static final String GROUP_ID_CODEHAUS_MOJO = "org.codehaus.mojo";

    private MavenUtils()
    {
        // utility class with only static methods
    }

    /**
     * Returns the version of Java used by the maven compiler plugin
     *
     * @param pom the project pom
     * @return the java version
     */
    public static String getJavaVersion( MavenProject pom )
    {
        MavenPlugin compilerPlugin = MavenPlugin.getPlugin( pom, GROUP_ID_APACHE_MAVEN, MAVEN_COMPILER_PLUGIN );
        if ( compilerPlugin != null )
        {
            return compilerPlugin.getParameter( "target" );
        }
        return null;
    }

    public static String getJavaSourceVersion( MavenProject pom )
    {
        MavenPlugin compilerPlugin = MavenPlugin.getPlugin( pom, GROUP_ID_APACHE_MAVEN, MAVEN_COMPILER_PLUGIN );
        if ( compilerPlugin != null )
        {
            return compilerPlugin.getParameter( "source" );
        }
        return null;
    }

    /**
     * @return source encoding
     */
    public static String getSourceEncoding( MavenProject pom )
    {
        return pom.getProperties().getProperty( "project.build.sourceEncoding" );
    }

    /**
     * Search for a configuration setting of an other plugin for a configuration setting.
     *
     * @todo there should be a better way to do this
     * @param project the current maven project to get the configuration from.
     * @param pluginId the group id and artifact id of the plugin to search for
     * @param optionName the option to get from the configuration
     * @param defaultValue the default value if the configuration was not found
     * @return the value of the option configured in the plugin configuration
     */
    public static String getPluginSetting( MavenProject project, String pluginId, String optionName,
                                           String defaultValue )
    {
        Xpp3Dom dom = getPluginConfigurationDom( project, pluginId );
        if ( dom != null && dom.getChild( optionName ) != null )
        {
            return dom.getChild( optionName ).getValue();
        }
        return defaultValue;
    }

    /**
     * Search for the configuration Xpp3 dom of an other plugin.
     *
     * @todo there should be a better way to do this
     * @param project the current maven project to get the configuration from.
     * @param pluginId the group id and artifact id of the plugin to search for
     * @return the value of the option configured in the plugin configuration
     */
    private static Xpp3Dom getPluginConfigurationDom( MavenProject project, String pluginId )
    {

        Plugin plugin = project.getBuild().getPluginsAsMap().get( pluginId );
        if ( plugin != null )
        {
            // TODO: This may cause ClassCastExceptions eventually, if the dom impls differ.
            return (Xpp3Dom) plugin.getConfiguration();
        }
        return null;
    }
}
