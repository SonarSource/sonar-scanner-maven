/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2024 SonarSource SA
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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class SourceCollectorTest {

  @TempDir
  Path emptyProjectBasedir;

  @TempDir
  static Path singleFileProjectBaseDir;

  @TempDir
  static Path simpleProjectBasedDir;

  @BeforeAll
  static void setup() throws IOException {
    Path singlePomXml = singleFileProjectBaseDir.resolve("pom.xml");
    singlePomXml.toFile().createNewFile();
  }


  @Test
  void testPrevisitDirectories() throws IOException {
    Path srcMainJava = Paths.get("src", "main", "java");
    Set<Path> existingSources = Collections.singleton(srcMainJava);
    FileVisitor<Path> visitor = new SourceCollector(existingSources, Collections.emptySet(), Collections.emptySet());


    Path gitFolder = Paths.get(".git");
    Path gitHooksFolder = Paths.get(".git", "hooks");
    Path sources = Paths.get("scripts");
    //BasicFileAttributes bfa = Mockito.mock(BasicFileAttributes.class);
    assertThat(visitor.preVisitDirectory(gitFolder, null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(gitHooksFolder, null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);

    assertThat(visitor.preVisitDirectory(Paths.get("src", "main", "java"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("bin"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("build"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("target"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("out"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("tmp"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("dist"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("nbdist"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);
    assertThat(visitor.preVisitDirectory(Paths.get("nbbuild"), null)).isEqualTo(FileVisitResult.SKIP_SUBTREE);

    assertThat(visitor.preVisitDirectory(sources, null)).isEqualTo(FileVisitResult.CONTINUE);
    assertThat(visitor.preVisitDirectory(Paths.get("src", "main", "js"), null)).isEqualTo(FileVisitResult.CONTINUE);
  }

  @Test
  void visitorCollectsConsistently() throws IOException {
    // File in the existing source is not repeated in the collected files
    SourceCollector visitor = new SourceCollector(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    Files.walkFileTree(emptyProjectBasedir, visitor);
    assertThat(visitor.getCollectedSources()).isEmpty();

    SourceCollector otherVisitor = new SourceCollector(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
    Files.walkFileTree(singleFileProjectBaseDir, otherVisitor);
    assertThat(otherVisitor.getCollectedSources()).containsOnly(singleFileProjectBaseDir.resolve("pom.xml"));

    SourceCollector visitorAvoidingPomXml = new SourceCollector(Collections.singleton(singleFileProjectBaseDir.resolve("pom.xml")), Collections.emptySet(), Collections.emptySet());
    Files.walkFileTree(singleFileProjectBaseDir, visitorAvoidingPomXml);
    assertThat(visitorAvoidingPomXml.getCollectedSources()).isEmpty();
  }

  @Test
  void visitorIgnoresFilesInDirectoriesToIgnore() throws IOException {
    Path simpleProjectPom = simpleProjectBasedDir.resolve("pom.xml");
    simpleProjectPom.toFile().createNewFile();
    Path subModule = simpleProjectBasedDir.resolve("submodule");
    subModule.toFile().mkdirs();
    Path fileInSubModule = subModule.resolve("ignore-me.php");
    fileInSubModule.toFile().createNewFile();

    SourceCollector visitor = new SourceCollector(Collections.emptySet(), Collections.singleton(subModule), Collections.emptySet());
    Files.walkFileTree(simpleProjectBasedDir, visitor);
    assertThat(visitor.getCollectedSources()).doesNotContain(fileInSubModule);
  }
}
