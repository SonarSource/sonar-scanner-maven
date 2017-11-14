/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.scanner.maven;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.sonarsource.scanner.api.EmbeddedScanner;
import org.sonarsource.scanner.api.ScanProperties;
import org.sonarsource.scanner.api.Utils;
import org.sonarsource.scanner.maven.bootstrap.JavaVersionResolver;
import org.sonarsource.scanner.maven.bootstrap.LogHandler;
import org.sonarsource.scanner.maven.bootstrap.MavenProjectConverter;
import org.sonarsource.scanner.maven.bootstrap.PropertyDecryptor;
import org.sonarsource.scanner.maven.bootstrap.ScannerBootstrapper;
import org.sonarsource.scanner.maven.bootstrap.ScannerFactory;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;

/**
 * Analyze project. SonarQube server must be started.
 */
@Mojo(name = "sonar", requiresDependencyResolution = ResolutionScope.TEST, aggregator = true)

public class SonarQubeMojo extends AbstractMojo {

  @Parameter(defaultValue = "${session}", required = true, readonly = true)
  private MavenSession session;

  /**
   * Set this to 'true' to skip analysis.
   *
   * @since 2.3
   */
  @Parameter(alias = "sonar.skip", property = "sonar.skip", defaultValue = "false")
  private boolean skip;

  @Component
  private LifecycleExecutor lifecycleExecutor;

  @Component(hint = "mng-4384")
  private SecDispatcher securityDispatcher;

  @Component
  private RuntimeInformation runtimeInformation;

  @Parameter(defaultValue = "${mojoExecution}", required = true, readonly = true)
  private MojoExecution mojoExecution;

  /**
   * Wait until reaching the last project before executing sonar when attached to phase
   */
  static final AtomicInteger readyProjectsCounter = new AtomicInteger();

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (getLog().isDebugEnabled()) {
      setLog(new TimestampLogger(getLog()));
    }

    if (shouldDelayExecution()) {
      getLog().info("Delaying SonarQube Scanner to the end of multi-module project");
      return;
    }

    Properties envProps = Utils.loadEnvironmentProperties(System.getenv());

    JavaVersionResolver pluginParameterResolver = new JavaVersionResolver(session, lifecycleExecutor, getLog());
    MavenProjectConverter mavenProjectConverter = new MavenProjectConverter(getLog(), pluginParameterResolver, envProps);
    LogHandler logHandler = new LogHandler(getLog());

    PropertyDecryptor propertyDecryptor = new PropertyDecryptor(getLog(), securityDispatcher);

    ScannerFactory runnerFactory = new ScannerFactory(logHandler, getLog(), runtimeInformation, mojoExecution, session, envProps, propertyDecryptor);

    if (isSkip(runnerFactory.createGlobalProperties())) {
      return;
    }

    EmbeddedScanner runner = runnerFactory.create();
    new ScannerBootstrapper(getLog(), session, runner, mavenProjectConverter, propertyDecryptor).execute();
  }

  /**
   * Should scanner be delayed?
   * @return true if goal is attached to phase and not last in a multi-module project
   */
  private boolean shouldDelayExecution() {
    return !isDetachedGoal() && isLastProjectInReactor();
  }

  /**
   * Is this execution a 'detached' goal run from the cli.  e.g. mvn sonar:sonar
   *
   * See <a href="https://maven.apache.org/guides/mini/guide-default-execution-ids.html#Default_executionIds_for_Implied_Executions">
      Default executionIds for Implied Executions</a>
   * for explanation of command line execution id.
   *
   * @return true if this execution is from the command line
   */
  private boolean isDetachedGoal() {
    return "default-cli".equals(mojoExecution.getExecutionId());
  }

  /**
   * Is this project the last project in the reactor?
   *
   * See <a href="http://svn.apache.org/viewvc/maven/plugins/tags/maven-install-plugin-2.5.2/src/main/java/org/apache/maven/plugin/install/InstallMojo.java?view=markup">
      install plugin</a> for another example of using this technique.
   *
   * @return true if last project (including only project)
   */
  private boolean isLastProjectInReactor() {
    return readyProjectsCounter.incrementAndGet() != session.getProjects().size();
  }

  private boolean isSkip(Map<String, String> properties) {
    if (skip) {
      getLog().info("sonar.skip = true: Skipping analysis");
      return true;
    }

    if ("true".equalsIgnoreCase(properties.get(ScanProperties.SKIP))) {
      getLog().info("SonarQube Scanner analysis skipped");
      return true;
    }
    return false;
  }

  MavenSession getSession() {
    return session;
  }
}
