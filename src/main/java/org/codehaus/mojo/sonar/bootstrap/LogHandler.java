package org.codehaus.mojo.sonar.bootstrap;

import org.apache.maven.plugin.logging.Log;

import org.sonar.runner.api.LogOutput;

public class LogHandler
    implements LogOutput
{
    private Log mavenLog;

    public LogHandler( Log mavenLog )
    {
        this.mavenLog = mavenLog;
    }

    @Override
    public void log( String log, Level level )
    {
        switch ( level )
        {
            case TRACE:
                mavenLog.debug( log );
                break;
            case DEBUG:
                mavenLog.debug( log );
                break;
            case INFO:
                mavenLog.info( log );
                break;
            case WARN:
                mavenLog.warn( log );
                break;
            case ERROR:
                mavenLog.error( log );
                break;
        }
    }
}
