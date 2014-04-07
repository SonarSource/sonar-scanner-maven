/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.codehaus.mojo.sonar.bootstrap;

import org.apache.maven.project.MavenProject;

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
}
