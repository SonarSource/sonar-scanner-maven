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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.IOException;

/**
 * Analyse project. WARNING, Sonar server must be started.
 *
 * @goal sonar
 * @aggregator
 * @requiresDependencyResolution test
 */
public class SonarMojo extends AbstractMojo
{

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession session;

    /**
     * Sonar host URL.
     *
     * @parameter expression="${sonar.host.url}" default-value="http://localhost:9000" alias="sonar.host.url"
     */
    private String sonarHostURL;

    /**
     * @component
     * @required
     */
    protected MavenPluginManager mavenPluginManager;

    /**
     * The local repository.
     *
     * @parameter expression="${localRepository}"
     */
    protected ArtifactRepository localRepository;

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            ServerMetadata server = new ServerMetadata( sonarHostURL );
            server.logSettings( getLog() );

            if (server.supportsMaven3() )
            {
                new Bootstraper( server, mavenPluginManager ).start( project, session );
            } else
            {
                throw new MojoExecutionException( "Sonar " + server.getVersion() + " does not support Maven 3" );
            }

        } catch ( IOException e )
        {
            throw new MojoExecutionException( "Failed to execute Sonar", e );
        }
    }

}