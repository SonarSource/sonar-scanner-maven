package org.codehaus.mojo.sonar;

/*
 * The MIT License
 *
 * Copyright 2009 The Codehaus.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * {@link MavenPluginManager} helper to deal with API changes between Maven 3.0.x and 3.1.x, ie switch from Sonatype
 * Aether (in org.sonatype.aether package) to Eclipse Aether (in org.eclipse.aether package). Inspired from
 * maven-reporting-exec
 * 
 * @since 2.1
 */
@Component( role = MavenPluginManagerHelper.class )
public class DefaultMavenPluginManagerHelper
    implements MavenPluginManagerHelper
{
    @Requirement
    private Logger logger;

    @Requirement
    protected MavenPluginManager mavenPluginManager;

    private Method setupPluginRealm;

    private Method getPluginDescriptor;

    private Method getRepositorySession;

    public DefaultMavenPluginManagerHelper()
    {
        try
        {
            for ( Method m : MavenPluginManager.class.getMethods() )
            {
                if ( "setupPluginRealm".equals( m.getName() ) )
                {
                    setupPluginRealm = m;
                }
                else if ( "getPluginDescriptor".equals( m.getName() ) )
                {
                    getPluginDescriptor = m;
                }
            }
        }
        catch ( SecurityException e )
        {
            logger.warn( "unable to find MavenPluginManager.setupPluginRealm() method", e );
        }

        try
        {
            for ( Method m : MavenSession.class.getMethods() )
            {
                if ( "getRepositorySession".equals( m.getName() ) )
                {
                    getRepositorySession = m;
                    break;
                }
            }
        }
        catch ( SecurityException e )
        {
            logger.warn( "unable to find MavenSession.getRepositorySession() method", e );
        }
    }

    public PluginDescriptor getPluginDescriptor( Plugin plugin, MavenSession session )
        throws PluginResolutionException, PluginDescriptorParsingException, InvalidPluginDescriptorException
    {
        try
        {
            Object repositorySession = getRepositorySession.invoke( session );
            List<?> remoteRepositories = session.getCurrentProject().getRemotePluginRepositories();

            return (PluginDescriptor) getPluginDescriptor.invoke( mavenPluginManager, plugin, remoteRepositories,
                                                                  repositorySession );
        }
        catch ( IllegalArgumentException e )
        {
            logger.warn( "IllegalArgumentException during MavenPluginManager.getPluginDescriptor() call", e );
        }
        catch ( IllegalAccessException e )
        {
            logger.warn( "IllegalAccessException during MavenPluginManager.getPluginDescriptor() call", e );
        }
        catch ( InvocationTargetException e )
        {
            Throwable target = e.getTargetException();
            if ( target instanceof PluginResolutionException )
            {
                throw (PluginResolutionException) target;
            }
            if ( target instanceof PluginDescriptorParsingException )
            {
                throw (PluginDescriptorParsingException) target;
            }
            if ( target instanceof InvalidPluginDescriptorException )
            {
                throw (InvalidPluginDescriptorException) target;
            }
            if ( target instanceof RuntimeException )
            {
                throw (RuntimeException) target;
            }
            if ( target instanceof Error )
            {
                throw (Error) target;
            }
            logger.warn( "Exception during MavenPluginManager.getPluginDescriptor() call", e );
        }

        return null;
    }

    @Override
    public void setupPluginRealm( PluginDescriptor pluginDescriptor, MavenSession session, ClassLoader parent,
                                  List<String> imports )
        throws PluginResolutionException, PluginContainerException
    {
        try
        {
            setupPluginRealm.invoke( mavenPluginManager, pluginDescriptor, session, parent, imports, null );
        }
        catch ( IllegalArgumentException e )
        {
            logger.warn( "IllegalArgumentException during MavenPluginManager.setupPluginRealm() call", e );
        }
        catch ( IllegalAccessException e )
        {
            logger.warn( "IllegalAccessException during MavenPluginManager.setupPluginRealm() call", e );
        }
        catch ( InvocationTargetException e )
        {
            Throwable target = e.getTargetException();
            if ( target instanceof PluginResolutionException )
            {
                throw (PluginResolutionException) target;
            }
            if ( target instanceof PluginContainerException )
            {
                throw (PluginContainerException) target;
            }
            if ( target instanceof RuntimeException )
            {
                throw (RuntimeException) target;
            }
            if ( target instanceof Error )
            {
                throw (Error) target;
            }
            logger.warn( "Exception during MavenPluginManager.setupPluginRealm() call", e );
        }
    }
}
