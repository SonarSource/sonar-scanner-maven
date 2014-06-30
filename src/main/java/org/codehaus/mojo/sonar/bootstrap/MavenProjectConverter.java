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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.sonar.runner.api.RunnerProperties;
import org.sonar.runner.api.ScanProperties;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MavenProjectConverter
{

    private static final char SEPARATOR = ',';

    private static final String UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE =
        "Unable to determine structure of project."
            + " Probably you use Maven Advanced Reactor Options with a broken tree of modules.";

    private static final String MODULE_KEY = "sonar.moduleKey";

    private static final String PROPERTY_PROJECT_BUILDDIR = "sonar.projectBuildDir";

    private static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";

    private static final String JAVA_TARGET_PROPERTY = "sonar.java.target";

    private static final String LINKS_HOME_PAGE = "sonar.links.homepage";

    private static final String LINKS_CI = "sonar.links.ci";

    private static final String LINKS_ISSUE_TRACKER = "sonar.links.issue";

    private static final String LINKS_SOURCES = "sonar.links.scm";

    private static final String LINKS_SOURCES_DEV = "sonar.links.scm_dev";

    private static final String MAVEN_PACKAGING_POM = "pom";

    private static final String MAVEN_PACKAGING_WAR = "war";

    public static final String ARTIFACT_MAVEN_WAR_PLUGIN = "org.apache.maven.plugins:maven-war-plugin";

    public Properties configure( List<MavenProject> mavenProjects, MavenProject root )
        throws MojoExecutionException
    {
        // projects by canonical path to pom.xml
        Map<String, MavenProject> paths = new HashMap<String, MavenProject>();
        Map<MavenProject, Properties> propsByModule = new HashMap<MavenProject, Properties>();

        try
        {
            configureModules( mavenProjects, paths, propsByModule );
            Properties props = new Properties();
            props.setProperty( ScanProperties.PROJECT_KEY, getSonarKey( root ) );
            rebuildModuleHierarchy( props, paths, propsByModule, root, "" );
            if ( !propsByModule.isEmpty() )
            {
                throw new IllegalStateException( UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE + " \""
                    + propsByModule.keySet().iterator().next().getName() + "\" is orphan" );
            }
            return props;
        }
        catch ( IOException e )
        {
            throw new IllegalStateException( "Cannot configure project", e );
        }

    }

    private void rebuildModuleHierarchy( Properties properties, Map<String, MavenProject> paths,
                                         Map<MavenProject, Properties> propsByModule, MavenProject current,
                                         String prefix )
        throws IOException
    {
        Properties currentProps = propsByModule.get( current );
        if ( currentProps == null )
        {
            throw new IllegalStateException( UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE );
        }
        for ( Map.Entry<Object, Object> prop : currentProps.entrySet() )
        {
            properties.put( prefix + prop.getKey(), prop.getValue() );
        }
        propsByModule.remove( current );
        List<String> moduleIds = new ArrayList<String>();
        for ( String modulePathStr : current.getModules() )
        {
            File modulePath = new File( current.getBasedir(), modulePathStr );
            MavenProject module = findMavenProject( modulePath, paths );
            if ( module != null )
            {
                String moduleId = module.getGroupId() + ":" + module.getArtifactId();
                rebuildModuleHierarchy( properties, paths, propsByModule, module, prefix + moduleId + "." );
                moduleIds.add( moduleId );
            }
        }
        if ( !moduleIds.isEmpty() )
        {
            properties.put( prefix + "sonar.modules", StringUtils.join( moduleIds, SEPARATOR ) );
        }
    }

    private void configureModules( List<MavenProject> mavenProjects, Map<String, MavenProject> paths,
                                   Map<MavenProject, Properties> propsByModule )
        throws IOException, MojoExecutionException
    {
        for ( MavenProject pom : mavenProjects )
        {
            paths.put( pom.getFile().getCanonicalPath(), pom );
            Properties props = new Properties();
            merge( pom, props );
            propsByModule.put( pom, props );
        }
    }

    private static MavenProject findMavenProject( final File modulePath, Map<String, MavenProject> paths )
        throws IOException
    {
        if ( modulePath.exists() && modulePath.isDirectory() )
        {
            for ( Map.Entry<String, MavenProject> entry : paths.entrySet() )
            {
                String pomFileParentDir = new File( entry.getKey() ).getParent();
                if ( pomFileParentDir.equals( modulePath.getCanonicalPath() ) )
                {
                    return entry.getValue();
                }
            }
            return null;
        }
        return paths.get( modulePath.getCanonicalPath() );
    }

    @VisibleForTesting
    void merge( MavenProject pom, Properties props )
        throws MojoExecutionException
    {
        defineProjectKey( pom, props );
        props.setProperty( ScanProperties.PROJECT_VERSION, pom.getVersion() );
        props.setProperty( ScanProperties.PROJECT_NAME, pom.getName() );
        String description = pom.getDescription();
        if ( description != null )
        {
            props.setProperty( ScanProperties.PROJECT_DESCRIPTION, description );
        }

        guessJavaVersion( pom, props );
        guessEncoding( pom, props );
        convertMavenLinksToProperties( props, pom );
        synchronizeFileSystemAndOtherProps( pom, props );
    }

    private void defineProjectKey( MavenProject pom, Properties props )
    {
        String key;
        if ( pom.getModel().getProperties().containsKey( ScanProperties.PROJECT_KEY ) )
        {
            key = pom.getModel().getProperties().getProperty( ScanProperties.PROJECT_KEY );
        }
        else
        {
            key = getSonarKey( pom );
        }
        props.setProperty( MODULE_KEY, key );
    }

    private static String getSonarKey( MavenProject pom )
    {
        return new StringBuilder().append( pom.getGroupId() ).append( ":" ).append( pom.getArtifactId() ).toString();
    }

    private static void guessEncoding( MavenProject pom, Properties props )
    {
        // See http://jira.codehaus.org/browse/SONAR-2151
        String encoding = MavenUtils.getSourceEncoding( pom );
        if ( encoding != null )
        {
            props.setProperty( ScanProperties.PROJECT_SOURCE_ENCODING, encoding );
        }
    }

    private static void guessJavaVersion( MavenProject pom, Properties props )
    {
        // See http://jira.codehaus.org/browse/SONAR-2148
        // Get Java source and target versions from maven-compiler-plugin.
        String version = MavenUtils.getJavaSourceVersion( pom );
        if ( version != null )
        {
            props.setProperty( JAVA_SOURCE_PROPERTY, version );
        }
        version = MavenUtils.getJavaVersion( pom );
        if ( version != null )
        {
            props.setProperty( JAVA_TARGET_PROPERTY, version );
        }
    }

    /**
     * For SONAR-3676
     */
    private static void convertMavenLinksToProperties( Properties props, MavenProject pom )
    {
        setPropertyIfNotAlreadyExists( props, LINKS_HOME_PAGE, pom.getUrl() );

        Scm scm = pom.getScm();
        if ( scm == null )
        {
            scm = new Scm();
        }
        setPropertyIfNotAlreadyExists( props, LINKS_SOURCES, scm.getUrl() );
        setPropertyIfNotAlreadyExists( props, LINKS_SOURCES_DEV, scm.getDeveloperConnection() );

        CiManagement ci = pom.getCiManagement();
        if ( ci == null )
        {
            ci = new CiManagement();
        }
        setPropertyIfNotAlreadyExists( props, LINKS_CI, ci.getUrl() );

        IssueManagement issues = pom.getIssueManagement();
        if ( issues == null )
        {
            issues = new IssueManagement();
        }
        setPropertyIfNotAlreadyExists( props, LINKS_ISSUE_TRACKER, issues.getUrl() );
    }

    private static void setPropertyIfNotAlreadyExists( Properties props, String propertyKey,
                                                       String propertyValue )
    {
        if ( StringUtils.isBlank( props.getProperty( propertyKey ) ) )
        {
            props.setProperty( propertyKey, StringUtils.defaultString( propertyValue ) );
        }
    }

    private void synchronizeFileSystemAndOtherProps( MavenProject pom, Properties props )
        throws MojoExecutionException
    {
        props.setProperty( ScanProperties.PROJECT_BASEDIR, pom.getBasedir().getAbsolutePath() );
        File buildDir = getBuildDir( pom );
        if ( buildDir != null )
        {
            props.setProperty( PROPERTY_PROJECT_BUILDDIR, buildDir.getAbsolutePath() );
            props.setProperty( RunnerProperties.WORK_DIR, getSonarWorkDir( pom ).getAbsolutePath() );
        }
        populateBinaries( pom, props );

        populateLibraries( pom, props );

        // IMPORTANT NOTE : reference on properties from POM model must not be saved,
        // instead they should be copied explicitly - see SONAR-2896
        props.putAll( pom.getModel().getProperties() );

        List<File> mainDirs = mainDirs( pom );
        props.setProperty( ScanProperties.PROJECT_SOURCE_DIRS,
                           StringUtils.join( toPaths( mainDirs ), SEPARATOR ) );
        List<File> testDirs = testDirs( pom );
        if ( !testDirs.isEmpty() )
        {
            props.setProperty( ScanProperties.PROJECT_TEST_DIRS,
                               StringUtils.join( toPaths( testDirs ), SEPARATOR ) );
        }
        else
        {
            props.remove( ScanProperties.PROJECT_TEST_DIRS );
        }
    }

    private void populateLibraries( MavenProject pom, Properties props )
        throws MojoExecutionException
    {
        List<File> libraries = Lists.newArrayList();
        try
        {
            if ( pom.getCompileClasspathElements() != null )
            {
                for ( String classPathString : (List<String>) pom.getCompileClasspathElements() )
                {
                    if ( !classPathString.equals( pom.getBuild().getOutputDirectory() ) )
                    {
                        File libPath = resolvePath( classPathString, pom.getBasedir() );
                        if ( libPath != null && libPath.exists() )
                        {
                            libraries.add( libPath );
                        }
                    }
                }
            }
        }
        catch ( DependencyResolutionRequiredException e )
        {
            throw new MojoExecutionException( "Unable to populate libraries", e );
        }
        if ( !libraries.isEmpty() )
        {
            props.setProperty( ScanProperties.PROJECT_LIBRARIES,
                               StringUtils.join( toPaths( libraries ), SEPARATOR ) );
        }
    }

    private void populateBinaries( MavenProject pom, Properties props )
    {
        File binaryDir = resolvePath( pom.getBuild().getOutputDirectory(), pom.getBasedir() );
        if ( binaryDir != null && binaryDir.exists() )
        {
            props.setProperty( ScanProperties.PROJECT_BINARY_DIRS,
                               binaryDir.getAbsolutePath() );
        }
    }

    public static File getSonarWorkDir( MavenProject pom )
    {
        return new File( getBuildDir( pom ), "sonar" );
    }

    private static File getBuildDir( MavenProject pom )
    {
        return resolvePath( pom.getBuild().getDirectory(), pom.getBasedir() );
    }

    static File resolvePath( @Nullable String path, File basedir )
    {
        if ( path != null )
        {
            File file = new File( StringUtils.trim( path ) );
            if ( !file.isAbsolute() )
            {
                try
                {
                    file = new File( basedir, path ).getCanonicalFile();
                }
                catch ( IOException e )
                {
                    throw new IllegalStateException( "Unable to resolve path '" + path + "'", e );
                }
            }
            return file;
        }
        return null;
    }

    static List<File> resolvePaths( List<String> paths, File basedir )
    {
        List<File> result = Lists.newArrayList();
        for ( String path : paths )
        {
            File dir = resolvePath( path, basedir );
            if ( dir != null )
            {
                result.add( dir );
            }
        }
        return result;
    }

    private List<File> mainDirs( MavenProject pom )
        throws MojoExecutionException
    {
        List<String> srcDirs = new ArrayList<String>();
        if ( MAVEN_PACKAGING_WAR.equals( pom.getModel().getPackaging() ) )
        {
            srcDirs.add( MavenUtils.getPluginSetting( pom, ARTIFACT_MAVEN_WAR_PLUGIN, "warSourceDirectory",
                                                      "src/main/webapp" ) );
        }
        srcDirs.addAll( pom.getCompileSourceRoots() );
        return sourceDirs( pom, ScanProperties.PROJECT_SOURCE_DIRS, srcDirs );
    }

    private List<File> testDirs( MavenProject pom )
        throws MojoExecutionException
    {
        return sourceDirs( pom, ScanProperties.PROJECT_TEST_DIRS, pom.getTestCompileSourceRoots() );
    }

    private List<File> sourceDirs( MavenProject pom, String propertyKey, List<String> mavenDirs )
        throws MojoExecutionException
    {
        List<String> paths;
        List<File> dirs;
        boolean userDefined = false;
        String prop = pom.getProperties().getProperty( propertyKey );
        if ( prop != null )
        {
            paths = Arrays.asList( StringUtils.split( prop, "," ) );
            dirs = resolvePaths( paths, pom.getBasedir() );
            userDefined = true;
        }
        else
        {
            dirs = resolvePaths( mavenDirs, pom.getBasedir() );
        }

        if ( userDefined && !MAVEN_PACKAGING_POM.equals( pom.getModel().getPackaging() ) )
        {
            return existingDirsOrFail( dirs, pom, propertyKey );
        }
        else
        {
            // Maven provides some directories that do not exist. They
            // should be removed. Same for pom module were sonar.sources and sonar.tests
            // can be defined only to be inherited by children
            return keepExistingDirs( dirs );
        }
    }

    private List<File> existingDirsOrFail( List<File> dirs, MavenProject pom, String propertyKey )
        throws MojoExecutionException
    {
        for ( File dir : dirs )
        {
            if ( !dir.isDirectory() || !dir.exists() )
            {
                throw new MojoExecutionException(
                                                  String.format(
                                                                 "The directory '%s' does not exist for Maven module %s. Please check the property %s",
                                                                 dir.getAbsolutePath(), pom.getId(), propertyKey ) );
            }
        }
        return dirs;
    }

    private static List<File> keepExistingDirs( List<File> files )
    {
        return Lists.newArrayList( Collections2.filter( files,
                                                        new Predicate<File>()
                                                        {
                                                            @Override
                                                            public boolean apply( File dir )
                                                            {
                                                                return dir != null
                                                                    && dir.exists()
                                                                    && dir.isDirectory();
                                                            }
                                                        } ) );
    }

    private static String[] toPaths( Collection<File> dirs )
    {
        Collection<String> paths =
            Collections2.transform( dirs, new Function<File, String>()
            {
                @Override
                public String apply( File dir )
                {
                    if ( dir == null )
                    {
                        throw new NullPointerException( "Directory is null" );
                    }
                    return dir.getAbsolutePath();
                }
            } );
        return paths.toArray( new String[paths.size()] );
    }
}
