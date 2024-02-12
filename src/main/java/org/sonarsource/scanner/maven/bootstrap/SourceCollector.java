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
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

public class SourceCollector implements FileVisitor<Path> {
  private static final Set<String> EXCLUDED_DIRECTORIES = new HashSet<>(
    Arrays.asList(
      "bin",
      "build",
      "dist",
      "nbbuild",
      "nbdist",
      "out",
      "target",
      "tmp"
    )
  );

  private static final Set<String> EXCLUDED_EXTENSIONS = new HashSet<>(
    Arrays.asList(
      "jar",
      "war",
      "class",
      "ear",
      "nar",
      // Archives
      "DS_Store",
      "zip",
      "7z",
      "rar",
      "gz",
      "tar",
      "xz",
      // log
      "log",
      // temp files
      "bak",
      "tmp",
      "swp",
      // ide files
      "iml",
      "ipr",
      "iws",
      "nib",
      "log",
      "java",
      "jav",
      "kt",
      "scala"
    )
  );
  private final Set<Path> existingSources;
  private final Set<Path> directoriesToIgnore;

  public Set<Path> getCollectedSources() {
    return collectedSources;
  }

  private final Set<Path> collectedSources = new HashSet<>();

  public SourceCollector(Set<Path> existingSources, Set<Path> directoriesToIgnore) {
    this.existingSources = existingSources;
    this.directoriesToIgnore = directoriesToIgnore;
  }
  @Override
  public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
    if (
      isHidden(path) ||
      isExcludedDirectory(path) ||
      isCoveredByExistingSources(path)
    ) {
      return FileVisitResult.SKIP_SUBTREE;
    }
    return FileVisitResult.CONTINUE;
  }

  private static boolean isHidden(Path path) {
    return StreamSupport.stream(path.spliterator(), true)
      .anyMatch(token -> token.toString().startsWith("."));
  }

  private boolean isExcludedDirectory(Path path) {
    String pathAsString = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return EXCLUDED_DIRECTORIES.contains(pathAsString) || directoriesToIgnore.contains(path);
  }

  private boolean isCoveredByExistingSources(Path path) {
    return existingSources.contains(path);
  }

  @Override
  public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) {
    if (
      EXCLUDED_EXTENSIONS.stream().noneMatch(ext -> path.toString().endsWith(ext)) &&
      existingSources.stream().noneMatch(path::equals)
    ) {
      collectedSources.add(path);
    }
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
    return null;
  }

  @Override
  public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
    return FileVisitResult.CONTINUE;
  }
}
