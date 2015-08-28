package org.codehaus.mojo.sonar.bootstrap;

import org.apache.maven.rtinfo.RuntimeInformation;

import org.sonar.runner.api.LogOutput;
import org.sonar.runner.api.EmbeddedRunner;
import org.apache.maven.execution.MavenSession;

public class RunnerFactory
{

    private final LogOutput logOutput;

    private final RuntimeInformation runtimeInformation;

    private final MavenSession session;

    private final boolean debugEnabled;

    public RunnerFactory( LogOutput logOutput, boolean debugEnabled, RuntimeInformation runtimeInformation,
                          MavenSession session )
    {
        this.logOutput = logOutput;
        this.runtimeInformation = runtimeInformation;
        this.session = session;
        this.debugEnabled = debugEnabled;
    }

    public EmbeddedRunner create()
    {
        EmbeddedRunner runner = EmbeddedRunner.create( logOutput );
        runner.setApp( "Maven", runtimeInformation.getMavenVersion() );
        runner.addGlobalProperties( session.getSystemProperties() );
        // Secret property to manage backward compatibility on SQ side (see ProjectScanContainer)
        runner.setGlobalProperty( "sonar.mojoUseRunner", "true" );
        if ( debugEnabled )
        {
            runner.setGlobalProperty( "sonar.verbose", "true" );
        }

        return runner;
    }
}
