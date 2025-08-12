/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2025 SonarSource SA
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.scanner.lib.AnalysisProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MavenProjectConverterTest {

  @TempDir
  public Path temp;

  private Log log;

  private final Map<String, String> env = new HashMap<>();

  private MavenProjectConverter projectConverter;

  @BeforeEach
  void prepare() {
    log = mock(Log.class);
    MavenCompilerResolver mavenCompilerResolver = mock(MavenCompilerResolver.class);
    when(mavenCompilerResolver.extractConfiguration(any())).thenReturn(Optional.empty());
    projectConverter = new MavenProjectConverter(log, mavenCompilerResolver, env);
  }

  @Test
  void convertSingleModuleProject() throws Exception {
    MavenProject project = createProject(new Properties(), "jar");
    assertThat(projectConverter.getEnvProperties()).isEmpty();
    Map<String, String> props = projectConverter.configure(Collections.singletonList(project),
      project, new Properties());
    assertThat(props)
      .containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1");
  }

  // MSONAR-104
  @Test
  void convertSingleModuleProjectAvoidNestedFolders() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    File webappDir = new File(baseDir, "src/main/webapp");
    webappDir.mkdirs();
    MavenProject project = createProject(new Properties(), "war");
    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1");
    assertThat(props.get("sonar.sources").split(",")).containsOnly(webappDir.getAbsolutePath(), new File(baseDir, "pom.xml").getAbsolutePath());

    project.getModel().getProperties().setProperty("sonar.sources", "src");
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.sources", srcDir.getAbsolutePath());
  }

  @Test
  void shouldIncludePomIfRequested() throws Exception {
    MavenProject project = createProject(new Properties(), "jar");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.sources", temp.toAbsolutePath().resolve("pom.xml").toAbsolutePath().toString());
  }

  @Test
  void shouldUseEnvironment() throws Exception {
    env.put("sonar.projectKey", "com.foo:anotherProject");
    var projectProps = new Properties();
    projectProps.put("project.build.sourceEncoding", "UTF-8");
    MavenProject project = createProject(projectProps, "jar");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.projectKey", "com.foo:anotherProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.sources", temp.toAbsolutePath().resolve("pom.xml").toAbsolutePath().toString());
  }

  @Test
  void convertMultiModuleProjectRelativePaths() throws Exception {
    File rootBaseDir = new File(temp.toAbsolutePath().toFile(), "root");
    MavenProject root = createProject(new File(rootBaseDir, "pom.xml"), new Properties(), "pom");

    File module1BaseDir = new File(temp.toAbsolutePath().toFile(), "module1");
    module1BaseDir.mkdir();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), new Properties(), "jar");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);

    root.getModules().add("../module1");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, root), root, new Properties());
    // MSONAR-164
    assertThat(props).containsEntry("sonar.projectBaseDir", temp.toAbsolutePath().toFile().toString());
  }

  @Test
  void findCommonParentDir() {
    Path fooBarPath = Paths.get("foo", "bar");
    assertThat(MavenProjectConverter.findCommonParentDir(temp, temp.resolve(fooBarPath))).isEqualTo(temp);
    assertThat(MavenProjectConverter.findCommonParentDir(temp.resolve(fooBarPath), temp)).isEqualTo(temp);
    Path dir2 = Paths.get("foo2", "bar2");
    assertThat(MavenProjectConverter.findCommonParentDir(temp.resolve(fooBarPath), temp.resolve(dir2)))
      .isEqualTo(temp);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
      () -> MavenProjectConverter.findCommonParentDir(fooBarPath, dir2)
    );

    assertThat(exception).hasMessageMatching("Unable to find a common parent between two modules baseDir: 'foo.bar' and 'foo2.bar2'");
  }

  @Test
  void convertMultiModuleProject() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    MavenProject root = createProject(new Properties(), "pom");

    File module1BaseDir = new File(baseDir, "module1");
    module1BaseDir.mkdir();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), new Properties(), "pom");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("module1");

    File module11BaseDir = new File(module1BaseDir, "module1");
    module11BaseDir.mkdir();
    MavenProject module11 = createProject(new File(module11BaseDir, "pom.xml"), new Properties(), "jar");
    module11.getModel().setArtifactId("module11");
    module11.getModel().setName("My Project - Module 1 - Module 1");
    module11.getModel().setDescription("My sample project - Module 1 - Module 1");
    module11.setParent(module1);
    module1.getModules().add("module1");

    File module12BaseDir = new File(module1BaseDir, "module2");
    module12BaseDir.mkdir();
    MavenProject module12 = createProject(new File(module12BaseDir, "pom.xml"), new Properties(), "jar");
    module12.getModel().setArtifactId("module12");
    module12.getModel().setName("My Project - Module 1 - Module 2");
    module12.getModel().setDescription("My sample project - Module 1 - Module 2");
    module12.setParent(module1);
    module1.getModules().add("module2");

    File module2BaseDir = new File(baseDir, "module2");
    module2BaseDir.mkdir();
    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), new Properties(), "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("module2");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.projectBaseDir", baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.get(module1Key + ".sonar.modules").split(",")).containsOnly(module11Key, module12Key);
    assertThat(props)
      .containsEntry(module1Key + ".sonar.moduleKey", module1Key)
      .containsEntry(module2Key + ".sonar.moduleKey", module2Key)
      .containsEntry(module1Key + "." + module11Key + ".sonar.projectBaseDir", module11BaseDir.getAbsolutePath())
      .containsEntry(module1Key + ".sonar.projectBaseDir", module1BaseDir.getAbsolutePath())
      .containsEntry(module1Key + "." + module12Key + ".sonar.projectBaseDir", module12BaseDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.projectBaseDir", module2BaseDir.getAbsolutePath());

    Properties userProperties = new Properties();
    String userProjectKey = "user-project-key";
    userProperties.put(AnalysisProperties.PROJECT_KEY, userProjectKey);
    Map<String, String> propsWithUserProjectKey = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, userProperties);

    assertThat(propsWithUserProjectKey).containsEntry("sonar.projectKey", userProjectKey);

    String customProjectKey = "custom-project-key";
    root.getModel().getProperties().setProperty(AnalysisProperties.PROJECT_KEY, customProjectKey);
    Map<String, String> propsWithCustomProjectKey = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(propsWithCustomProjectKey).containsEntry("sonar.projectKey", customProjectKey);
  }

  // MSONAR-91
  @Test
  void convertMultiModuleProjectSkipModule() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    MavenProject root = createProject(new Properties(), "pom");

    File module1BaseDir = new File(baseDir, "module1");
    module1BaseDir.mkdir();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), new Properties(), "pom");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("module1");

    File module11BaseDir = new File(module1BaseDir, "module1");
    module11BaseDir.mkdir();
    Properties module11Props = new Properties();
    module11Props.setProperty("sonar.skip", "true");
    MavenProject module11 = createProject(new File(module11BaseDir, "pom.xml"), module11Props, "jar");
    module11.getModel().setArtifactId("module11");
    module11.getModel().setName("My Project - Module 1 - Module 1");
    module11.getModel().setDescription("My sample project - Module 1 - Module 1");
    module11.setParent(module1);
    module1.getModules().add("module1");

    File module12BaseDir = new File(module1BaseDir, "module2");
    module12BaseDir.mkdir();
    MavenProject module12 = createProject(new File(module12BaseDir, "pom.xml"), new Properties(), "jar");
    module12.getModel().setArtifactId("module12");
    module12.getModel().setName("My Project - Module 1 - Module 2");
    module12.getModel().setDescription("My sample project - Module 1 - Module 2");
    module12.setParent(module1);
    module1.getModules().add("module2");

    File module2BaseDir = new File(baseDir, "module2");
    module2BaseDir.mkdir();
    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), new Properties(), "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("module2");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.projectBaseDir", baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.get(module1Key + ".sonar.modules").split(",")).containsOnly(module12Key);

    assertThat(props).containsEntry(module1Key + ".sonar.projectBaseDir", module1BaseDir.getAbsolutePath());
    // Module 11 is skipped
    assertThat(props.get(module1Key + "." + module11Key + ".sonar.projectBaseDir")).isNull();
    assertThat(props).containsEntry(module1Key + "." + module12Key + ".sonar.projectBaseDir", module12BaseDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.projectBaseDir", module2BaseDir.getAbsolutePath());

    assertThat(projectConverter.getSkippedBasedDirs()).containsOnly(module11BaseDir.toPath());

    verify(log).info("Module MavenProject: com.foo:module11:2.1 @ "
      + new File(module11BaseDir, "pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  // MSONAR-125
  @Test
  void skipOrphanModule() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    MavenProject root = createProject(new Properties(), "pom");

    File module1BaseDir = new File(baseDir, "module1");
    module1BaseDir.mkdir();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), new Properties(), "pom");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("module1");

    File module2BaseDir = new File(baseDir, "module2");
    module2BaseDir.mkdir();
    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), new Properties(), "pom");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.getModel().getProperties().setProperty("sonar.skip", "true");
    // MSHADE-124 it is possible to change location of pom.xml
    module2.setFile(new File(module2BaseDir, "target/dependency-reduced-pom.xml"));
    module2.setParent(root);
    root.getModules().add("module2");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.projectBaseDir", baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key);

    assertThat(props).containsEntry(module1Key + ".sonar.projectBaseDir", module1BaseDir.getAbsolutePath());
    // Module 2 is skipped
    assertThat(props.get(module2Key + ".sonar.projectBaseDir")).isNull();

    verify(log).info("Module MavenProject: com.foo:module2:2.1 @ "
      + new File(module2BaseDir, "target/dependency-reduced-pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  @Test
  void overrideSourcesSingleModuleProject() throws Exception {
    File srcMainDir = temp.resolve(Paths.get("src", "main")).toAbsolutePath().toFile();
    srcMainDir.mkdirs();

    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");

    MavenProject project = createProject(pomProps, "jar");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.sources", srcMainDir.getAbsolutePath());

    assertThat(projectConverter.isSourceDirsOverridden()).isTrue();
  }

  @Test
  void excludeSourcesInTarget() throws Exception {
    File src2 = temp.resolve("src2").toFile();
    src2.mkdir();
    File pom = temp.resolve("pom.xml").toFile();
    pom.createNewFile();

    MavenProject project = createProject(pom, new Properties(), "jar");
    project.addCompileSourceRoot(new File(temp.toAbsolutePath().toFile(), "target").getAbsolutePath());
    project.addCompileSourceRoot(new File(temp.toAbsolutePath().toFile(), "src2").getAbsolutePath());

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1");

    assertThat(props.get("sonar.sources").split(",")).containsOnly(src2.getAbsolutePath(), pom.getAbsolutePath());
  }

  @Test
  void overrideSourcesMultiModuleProject() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");
    pomProps.put("sonar.tests", "src/test");

    MavenProject root = createProject(pomProps, "pom");

    File module1BaseDir = temp.resolve("module1").toAbsolutePath().toFile();
    File module1SrcDir = temp.resolve(Paths.get("module1", "src", "main")).toAbsolutePath().toFile();
    module1SrcDir.mkdirs();
    File module1TestDir = temp.resolve(Paths.get("module1", "src", "test")).toAbsolutePath().toFile();
    module1TestDir.mkdirs();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), pomProps, "jar");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("module1");

    File module2BaseDir = temp.resolve("module2").toAbsolutePath().toFile();
    File module2SrcDir = temp.resolve(Paths.get("module2", "src", "main")).toAbsolutePath().toFile();
    module2SrcDir.mkdirs();
    File module2TestDir = temp.resolve(Paths.get("module2", "src", "test")).toAbsolutePath().toFile();
    module2TestDir.mkdirs();
    File module2BinaryDir = temp.resolve(Paths.get("module2", "target", "classes")).toAbsolutePath().toFile();
    module2BinaryDir.mkdirs();

    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), pomProps, "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("module2");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root,
      new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.projectBaseDir", temp.toAbsolutePath().toFile().getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    assertThat(props).containsEntry(module1Key + ".sonar.projectBaseDir", module1BaseDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.projectBaseDir", module2BaseDir.getAbsolutePath())
      .containsEntry(module1Key + ".sonar.sources", module1SrcDir.getAbsolutePath())
      .containsEntry(module1Key + ".sonar.tests", module1TestDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.sources", module2SrcDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.tests", module2TestDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.binaries", module2BinaryDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.groovy.binaries", module2BinaryDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.java.binaries", module2BinaryDir.getAbsolutePath())
      .containsEntry("sonar.sources", "");
    assertThat(props.get("sonar.tests")).isNull();

    assertThat(projectConverter.isSourceDirsOverridden()).isTrue();
  }

  @Test
  void overrideSourcesNonexistentFolder() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "nonexistent-folder");

    MavenProject project = createProject(pomProps, "jar");

    assertThrows(MojoExecutionException.class, () ->
      projectConverter.configure(Collections.singletonList(project), project, new Properties()));
  }

  @Test
  void overrideProjectKeySingleModuleProject() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");

    MavenProject project = createProject(pomProps, "jar");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1");
  }

  // MSONAR-134
  @Test
  void preserveFoldersHavingCommonPrefix() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    File srcGenDir = new File(baseDir, "src-gen");
    srcGenDir.mkdirs();
    MavenProject project = createProject(new Properties(), "jar");
    project.getCompileSourceRoots().add("src");
    project.getCompileSourceRoots().add("src-gen");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());

    assertThat(props.get("sonar.sources")).contains(srcDir.getAbsolutePath(), srcGenDir.getAbsolutePath());
  }

  // MSONAR-145
  @Test
  void ignoreNonStringModelProperties() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");
    pomProps.put("sonar.integer", 10);
    pomProps.put("sonar.string", "myString");

    MavenProject project = createProject(pomProps, "jar");
    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.string", "myString")
      .doesNotContainKey("sonar.integer");
  }

  @Test
  void includePomInNonLeaf() throws Exception {
    Path baseDir = temp.toAbsolutePath();
    Path basePom = baseDir.resolve("pom.xml");
    MavenProject baseProject = createProject(new Properties(), "pom");

    Path leafDir = baseDir.resolve("module1");
    Files.createDirectories(leafDir);
    Path leafPom = leafDir.resolve("pom.xml");
    Files.createFile(leafPom);

    MavenProject leafProject = createProject(leafPom.toFile(), new Properties(), "jar");
    leafProject.getModel().setArtifactId("module1");
    baseProject.getModules().add(leafDir.getFileName().toString());

    Map<String, String> props = projectConverter.configure(Arrays.asList(leafProject, baseProject), baseProject, new Properties());

    assertThat(props).containsEntry("sonar.sources", basePom.toString())
      .containsEntry("com.foo:module1.sonar.sources", leafPom.toString());
  }

  @Test
  void ignoreSourcesInNonLeaf() throws Exception {
    Path baseDir = temp;
    Path basePom = baseDir.resolve("pom.xml");
    MavenProject baseProject = createProject(new Properties(), "pom");

    Path sources = Paths.get("src", "main", "java");
    baseProject.addCompileSourceRoot(sources.toString());
    Path baseSources = baseDir.resolve(sources);
    Files.createDirectories(baseSources);

    Path leafDir = baseDir.resolve("module1");
    Files.createDirectories(leafDir);
    Path leafPom = leafDir.resolve("pom.xml");
    Files.createFile(leafPom);

    MavenProject leafProject = createProject(leafPom.toFile(), new Properties(), "jar");
    leafProject.getModel().setArtifactId("module1");
    baseProject.getModules().add(leafDir.getFileName().toString());

    leafProject.addCompileSourceRoot(sources.toString());
    Path leafSources = leafDir.resolve(sources);
    Files.createDirectories(leafSources);

    Map<String, String> props = projectConverter.configure(Arrays.asList(leafProject, baseProject), baseProject, new Properties());

    assertThat(props).containsEntry("sonar.sources", basePom.toString())
      .containsEntry("com.foo:module1.sonar.sources", leafPom + "," + leafSources);
  }

  @Test
  void includeSourcesInNonLeafWhenExplicitlyRequested() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "pom.xml,src/main/java");

    Path baseDir = temp;
    Path basePom = baseDir.resolve("pom.xml");
    MavenProject baseProject = createProject(pomProps, "pom");

    Path sources = Paths.get("src", "main", "java");
    baseProject.addCompileSourceRoot(sources.toString());
    Path baseSources = baseDir.resolve(sources);
    Files.createDirectories(baseSources);

    Path leafDir = baseDir.resolve("module1");
    Files.createDirectories(leafDir);
    Path leafPom = leafDir.resolve("pom.xml");
    Files.createFile(leafPom);

    MavenProject leafProject = createProject(leafPom.toFile(), new Properties(), "jar");
    leafProject.getModel().setArtifactId("module1");
    baseProject.getModules().add(leafDir.getFileName().toString());

    leafProject.addCompileSourceRoot(sources.toString());
    Path leafSources = leafDir.resolve(sources);
    Files.createDirectories(leafSources);

    Map<String, String> props = projectConverter.configure(Arrays.asList(leafProject, baseProject), baseProject, new Properties());

    assertThat(props).containsEntry("sonar.sources", basePom + "," + baseSources)
      .containsEntry("com.foo:module1.sonar.sources", leafPom + "," + leafSources);
  }

  // MSONAR-155
  @Test
  void two_modules_in_same_folder() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    MavenProject root = createProject(new Properties(), "pom");

    File modulesBaseDir = new File(baseDir, "modules");
    modulesBaseDir.mkdir();
    MavenProject module1 = createProject(new File(modulesBaseDir, "pom.xml"), new Properties(), "jar");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("modules");

    MavenProject module2 = createProject(new File(modulesBaseDir, "pom-2.xml"), new Properties(), "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("modules/pom-2.xml");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root, new Properties());

    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1")
      .containsEntry("sonar.projectBaseDir", baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    assertThat(props).containsEntry(module1Key + ".sonar.projectBaseDir", modulesBaseDir.getAbsolutePath())
      .containsEntry(module2Key + ".sonar.projectBaseDir", modulesBaseDir.getAbsolutePath());
  }

  @Test
  void submodules_are_not_assigned_user_provided_project_key_from_parent() throws MojoExecutionException, IOException {
    Properties rootPomProperties = new Properties();
    rootPomProperties.put(AnalysisProperties.PROJECT_KEY, "the_greatest_project_key_there_ever_was");
    File baseDir = temp.toFile();
    baseDir.mkdirs();
    MavenProject root = createProject(rootPomProperties, "pom");
    root.setGroupId("org.example");
    root.setArtifactId("root");

    File module1BaseDir = temp.resolve("module1").toFile();
    module1BaseDir.mkdirs();
    File module1Pom = new File(module1BaseDir, "pom.xml");
    MavenProject module1 = createProject(module1Pom, new Properties(), "jar");

    // Link the 2 modules
    module1.setParent(root);
    root.getModules().add("module1");

    Map<String, String> properties = projectConverter.configure(
      Arrays.asList(module1, root),
      root,
      new Properties()
    );

    assertThat(properties.get(AnalysisProperties.PROJECT_KEY))
      .isNotNull()
      .isEqualTo("the_greatest_project_key_there_ever_was");
    String keyPrefixForModule1 = module1.getGroupId() + ":" + module1.getArtifactId() + ".";
    assertThat(properties).doesNotContainKey(keyPrefixForModule1 + AnalysisProperties.PROJECT_KEY);
  }

  @Test
  void getAbsolutePathToOriginalPom_returns_the_original_pom_when_available() throws IOException {
    Path baseDirectory = temp;
    File originalPom = baseDirectory.resolve("pom.xml").toFile();
    originalPom.createNewFile();
    File dependencyReducedPom = baseDirectory.resolve("dependency-reduced-pom.xml").toFile();
    dependencyReducedPom.createNewFile();
    MavenProject project = createProject(dependencyReducedPom, new Properties(), "jar");

    // The entrypoint pom and original pom are returned by default when available
    assertThat(MavenProjectConverter.getPathsToPoms(project))
      .hasSize(2)
      .containsOnly(dependencyReducedPom.getAbsolutePath(), originalPom.getAbsolutePath());

    // The entrypoint pom is returned alone when the original pom file is not available
    project.getModel().setPomFile(null);
    assertThat(MavenProjectConverter.getPathsToPoms(project))
      .hasSize(1)
      .containsOnly(dependencyReducedPom.getAbsolutePath());

    // The original pom is returned only once when it is provided as the entrypoint and the original pom file
    MavenProject originalPomProject = createProject(originalPom, new Properties(), "jar");
    assertThat(MavenProjectConverter.getPathsToPoms(originalPomProject))
      .hasSize(1)
      .containsOnly(originalPom.getAbsolutePath());
  }

  @Test
  void shouldIncludeGithubActionFolder() throws Exception {
    File baseDir = temp.toAbsolutePath().toFile();
    File githubDir = new File(baseDir, ".github");
    githubDir.mkdirs();
    MavenProject project = createProject(new Properties(), "jar");

    Map<String, String> props = projectConverter.configure(Collections.singletonList(project), project, new Properties());
    assertThat(props).containsEntry("sonar.projectKey", "com.foo:myProject")
      .containsEntry("sonar.projectName", "My Project")
      .containsEntry("sonar.projectVersion", "2.1");
    assertThat(props.get("sonar.sources").split(",")).containsExactly(
      new File(baseDir, "pom.xml").getAbsolutePath(),
      githubDir.getAbsolutePath()
    );
  }

  private MavenProject createProject(Properties pomProps, String packaging) throws IOException {
    File pom = temp.resolve("pom.xml").toFile();
    pom.createNewFile();
    return createProject(pom, pomProps, packaging);
  }

  private MavenProject createProject(File pom, Properties pomProps, String packaging) {
    MavenProject project = new MavenProject();
    File target = new File(pom.getParentFile(), "target");
    File classes = new File(target, "classes");
    File testClasses = new File(target, "test-classes");
    classes.mkdirs();
    testClasses.mkdirs();

    project.getModel().setGroupId("com.foo");
    project.getModel().setArtifactId("myProject");
    project.getModel().setName("My Project");
    project.getModel().setDescription("My sample project");
    project.getModel().setVersion("2.1");
    project.getModel().setPackaging(packaging);
    project.getBuild().setOutputDirectory(classes.getAbsolutePath());
    project.getBuild().setTestOutputDirectory(testClasses.getAbsolutePath());
    project.getModel().setProperties(pomProps);
    project.getBuild().setDirectory(new File(pom.getParentFile(), "target").getAbsolutePath());
    project.setFile(pom);
    File originalPom = pom.getParentFile().toPath().resolve("pom.xml").toFile();
    if (!pom.equals(originalPom) && originalPom.exists()) {
      project.getModel().setPomFile(originalPom);
    } else {
      project.getModel().setPomFile(pom);
    }
    return project;
  }
}
