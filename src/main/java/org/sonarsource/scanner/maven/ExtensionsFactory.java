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

import java.util.LinkedList;
import java.util.List;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;

public class ExtensionsFactory {
  private final MavenSession session;

  private final LifecycleExecutor lifecycleExecutor;

  private final ArtifactFactory artifactFactory;

  private final ArtifactRepository localRepository;

  private final ArtifactMetadataSource artifactMetadataSource;

  private final ArtifactCollector artifactCollector;

  private final Log log;

  private final DependencyTreeBuilder dependencyTreeBuilder;

  private final MavenProjectBuilder projectBuilder;

  public ExtensionsFactory(Log log, MavenSession session, LifecycleExecutor lifecycleExecutor, ArtifactFactory artifactFactory, ArtifactRepository localRepository,
    ArtifactMetadataSource artifactMetadataSource, ArtifactCollector artifactCollector, DependencyTreeBuilder dependencyTreeBuilder, MavenProjectBuilder projectBuilder) {
    this.log = log;
    this.session = session;
    this.lifecycleExecutor = lifecycleExecutor;
    this.artifactFactory = artifactFactory;
    this.localRepository = localRepository;
    this.artifactMetadataSource = artifactMetadataSource;
    this.artifactCollector = artifactCollector;
    this.dependencyTreeBuilder = dependencyTreeBuilder;
    this.projectBuilder = projectBuilder;
  }

  public List<Object> createExtensionsWithDependencyProperty() {
    List<Object> extensions = new LinkedList<>();

    extensions.add(log);
    extensions.add(session);
    extensions.add(lifecycleExecutor);
    extensions.add(projectBuilder);

    return extensions;
  }

  public List<Object> createExtensions() {
    List<Object> extensions = createExtensionsWithDependencyProperty();

    extensions.add(artifactFactory);
    extensions.add(localRepository);
    extensions.add(artifactMetadataSource);
    extensions.add(artifactCollector);
    extensions.add(dependencyTreeBuilder);

    return extensions;
  }
}
