/*
 * SonarQube Scanner for Maven
 * Copyright (C) 2009-2021 SonarSource SA
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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.api.ScanProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MavenProjectConverterTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Log log;

  private Properties env;

  private MavenProjectConverter projectConverter;

  private MavenCompilerResolver mavenCompilerResolver;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void prepare() {
    log = mock(Log.class);
    mavenCompilerResolver = mock(MavenCompilerResolver.class);
    when(mavenCompilerResolver.extractConfiguration(any())).thenReturn(Optional.empty());
    env = new Properties();
    projectConverter = new MavenProjectConverter(log, mavenCompilerResolver, env);
  }

  @Test
  public void convertSingleModuleProject() throws Exception {
    MavenProject project = createProject(new Properties(), "jar");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project),
      project, new Properties());
    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");
  }

  // MSONAR-104
  @Test
  public void convertSingleModuleProjectAvoidNestedFolders() throws Exception {
    File baseDir = temp.getRoot();
    File webappDir = new File(baseDir, "src/main/webapp");
    webappDir.mkdirs();
    MavenProject project = createProject(new Properties(), "war");
    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.get("sonar.sources").split(",")).containsOnly(webappDir.getAbsolutePath(), new File(baseDir, "pom.xml").getAbsolutePath());

    project.getModel().getProperties().setProperty("sonar.sources", "src");
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.get("sonar.sources")).isEqualTo(srcDir.getAbsolutePath());
  }

  @Test
  public void shouldIncludePomIfRequested() throws Exception {
    MavenProject project = createProject(new Properties(), "jar");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.get("sonar.sources")).contains("pom.xml");
  }

  @Test
  public void shouldUseEnvironment() throws Exception {
    env.put("sonar.projectKey", "com.foo:anotherProject");
    MavenProject project = createProject(new Properties(), "jar");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:anotherProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.get("sonar.sources")).contains("pom.xml");
  }

  @Test
  public void convertMultiModuleProjectRelativePaths() throws Exception {
    File rootBaseDir = new File(temp.getRoot(), "root");
    MavenProject root = createProject(new File(rootBaseDir, "pom.xml"), new Properties(), "pom");

    File module1BaseDir = new File(temp.getRoot(), "module1");
    module1BaseDir.mkdir();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), new Properties(), "jar");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);

    root.getModules().add("../module1");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, root), root, new Properties());
    // MSONAR-164
    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(temp.getRoot().getAbsolutePath());
  }

  @Test
  public void findCommonParentDir() throws Exception {
    assertThat(MavenProjectConverter.findCommonParentDir(temp.getRoot().toPath(), temp.getRoot().toPath().resolve("foo").resolve("bar"))).isEqualTo(temp.getRoot().toPath());
    assertThat(MavenProjectConverter.findCommonParentDir(temp.getRoot().toPath().resolve("foo").resolve("bar"), temp.getRoot().toPath())).isEqualTo(temp.getRoot().toPath());
    assertThat(MavenProjectConverter.findCommonParentDir(temp.getRoot().toPath().resolve("foo").resolve("bar"), temp.getRoot().toPath().resolve("foo2").resolve("bar2")))
      .isEqualTo(temp.getRoot().toPath());

    try {
      MavenProjectConverter.findCommonParentDir(Paths.get("foo", "bar"), Paths.get("foo2", "bar2"));
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalStateException.class).hasMessageMatching("Unable to find a common parent between two modules baseDir: 'foo.bar' and 'foo2.bar2'");
    }
  }

  @Test
  public void convertMultiModuleProject() throws Exception {
    File baseDir = temp.getRoot();
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

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.get(module1Key + ".sonar.modules").split(",")).containsOnly(module11Key, module12Key);
    assertThat(props.get(module1Key + ".sonar.moduleKey")).isEqualTo(module1Key);
    assertThat(props.get(module2Key + ".sonar.moduleKey")).isEqualTo(module2Key);

    assertThat(props.get(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    assertThat(props.get(module1Key + "." + module11Key
      + ".sonar.projectBaseDir")).isEqualTo(module11BaseDir.getAbsolutePath());
    assertThat(props.get(module1Key + "." + module12Key
      + ".sonar.projectBaseDir")).isEqualTo(module12BaseDir.getAbsolutePath());
    assertThat(props.get(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());

    Properties userProperties = new Properties();
    String userProjectKey = "user-project-key";
    userProperties.put(ScanProperties.PROJECT_KEY, userProjectKey);
    Map<String, String> propsWithUserProjectKey = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, userProperties);

    assertThat(propsWithUserProjectKey.get("sonar.projectKey")).isEqualTo(userProjectKey);

    String customProjectKey = "custom-project-key";
    root.getModel().getProperties().setProperty(ScanProperties.PROJECT_KEY, customProjectKey);
    Map<String, String> propsWithCustomProjectKey = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(propsWithCustomProjectKey.get("sonar.projectKey")).isEqualTo(customProjectKey);
  }

  // MSONAR-91
  @Test
  public void convertMultiModuleProjectSkipModule() throws Exception {
    File baseDir = temp.getRoot();
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

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.get(module1Key + ".sonar.modules").split(",")).containsOnly(module12Key);

    assertThat(props.get(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    // Module 11 is skipped
    assertThat(props.get(module1Key + "." + module11Key + ".sonar.projectBaseDir")).isNull();
    assertThat(props.get(module1Key + "." + module12Key
      + ".sonar.projectBaseDir")).isEqualTo(module12BaseDir.getAbsolutePath());
    assertThat(props.get(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());

    verify(log).info("Module MavenProject: com.foo:module11:2.1 @ "
      + new File(module11BaseDir, "pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  // MSONAR-125
  @Test
  public void skipOrphanModule() throws Exception {
    File baseDir = temp.getRoot();
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

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key);

    assertThat(props.get(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    // Module 2 is skipped
    assertThat(props.get(module2Key + ".sonar.projectBaseDir")).isNull();

    verify(log).info("Module MavenProject: com.foo:module2:2.1 @ "
      + new File(module2BaseDir, "target/dependency-reduced-pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  @Test
  public void overrideSourcesSingleModuleProject() throws Exception {
    temp.newFolder("src");
    File srcMainDir = temp.newFolder("src", "main").getAbsoluteFile();

    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");

    MavenProject project = createProject(pomProps, "jar");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.sources")).isEqualTo(srcMainDir.getAbsolutePath());
  }

  @Test
  public void excludeSourcesInTarget() throws Exception {
    File src2 = temp.newFolder("src2");
    File pom = temp.newFile("pom.xml");

    MavenProject project = createProject(pom, new Properties(), "jar");
    project.addCompileSourceRoot(new File(temp.getRoot(), "target").getAbsolutePath());
    project.addCompileSourceRoot(new File(temp.getRoot(), "src2").getAbsolutePath());

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.sources").split(",")).containsOnly(src2.getAbsolutePath(), pom.getAbsolutePath());
  }

  @Test
  public void overrideSourcesMultiModuleProject() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");
    pomProps.put("sonar.tests", "src/test");

    MavenProject root = createProject(pomProps, "pom");

    File module1BaseDir = temp.newFolder("module1").getAbsoluteFile();
    File module1SrcDir = temp.newFolder("module1", "src", "main").getAbsoluteFile();
    File module1TestDir = temp.newFolder("module1", "src", "test").getAbsoluteFile();
    MavenProject module1 = createProject(new File(module1BaseDir, "pom.xml"), pomProps, "jar");
    module1.getModel().setArtifactId("module1");
    module1.getModel().setName("My Project - Module 1");
    module1.getModel().setDescription("My sample project - Module 1");
    module1.setParent(root);
    root.getModules().add("module1");

    File module2BaseDir = temp.newFolder("module2").getAbsoluteFile();
    File module2SrcDir = temp.newFolder("module2", "src", "main").getAbsoluteFile();
    File module2TestDir = temp.newFolder("module2", "src", "test").getAbsoluteFile();
    File module2BinaryDir = temp.newFolder("module2", "target", "classes").getAbsoluteFile();

    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), pomProps, "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("module2");

    Map<String, String> props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root,
      new Properties());

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(temp.getRoot().getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    assertThat(props.get(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    assertThat(props.get(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());

    assertThat(props.get("sonar.sources")).isEqualTo("");
    assertThat(props.get("sonar.tests")).isNull();
    assertThat(props.get(module1Key + ".sonar.sources")).isEqualTo(module1SrcDir.getAbsolutePath());
    assertThat(props.get(module1Key + ".sonar.tests")).isEqualTo(module1TestDir.getAbsolutePath());
    assertThat(props.get(module2Key + ".sonar.sources")).isEqualTo(module2SrcDir.getAbsolutePath());
    assertThat(props.get(module2Key + ".sonar.tests")).isEqualTo(module2TestDir.getAbsolutePath());
    assertThat(props.get(module2Key + ".sonar.binaries")).isEqualTo(module2BinaryDir.getAbsolutePath());
    assertThat(props.get(module2Key + ".sonar.groovy.binaries")).isEqualTo(module2BinaryDir.getAbsolutePath());
    assertThat(props.get(module2Key + ".sonar.java.binaries")).isEqualTo(module2BinaryDir.getAbsolutePath());
  }

  @Test(expected = MojoExecutionException.class)
  public void overrideSourcesNonexistentFolder() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "nonexistent-folder");

    MavenProject project = createProject(pomProps, "jar");

    projectConverter.configure(Arrays.asList(project), project, new Properties());
  }

  @Test
  public void overrideProjectKeySingleModuleProject() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");

    MavenProject project = createProject(pomProps, "jar");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.get("sonar.projectKey")).isEqualTo("myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");
  }

  // MSONAR-134
  @Test
  public void preserveFoldersHavingCommonPrefix() throws Exception {
    File baseDir = temp.getRoot();
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    File srcGenDir = new File(baseDir, "src-gen");
    srcGenDir.mkdirs();
    MavenProject project = createProject(new Properties(), "jar");
    project.getCompileSourceRoots().add("src");
    project.getCompileSourceRoots().add("src-gen");

    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.get("sonar.sources")).contains(srcDir.getAbsolutePath(), srcGenDir.getAbsolutePath());
  }

  // MSONAR-145
  @Test
  public void ignoreNonStringModelProperties() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");
    pomProps.put("sonar.integer", new Integer(10));
    pomProps.put("sonar.string", "myString");

    MavenProject project = createProject(pomProps, "jar");
    Map<String, String> props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.get("sonar.string")).isEqualTo("myString");
    assertThat(props).doesNotContainKey("sonar.integer");
  }

  @Test
  public void includePomInNonLeaf() throws Exception {
    Path baseDir = temp.getRoot().toPath();
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

    assertThat(props.get("sonar.sources")).isEqualTo(basePom.toString());
    assertThat(props.get("com.foo:module1.sonar.sources")).isEqualTo(leafPom.toString());
  }

  @Test
  public void ignoreSourcesInNonLeaf() throws Exception {
    Path baseDir = temp.getRoot().toPath();
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

    assertThat(props.get("sonar.sources")).isEqualTo(basePom.toString());
    String allLeafSources = leafPom + "," + leafSources;
    assertThat(props.get("com.foo:module1.sonar.sources")).isEqualTo(allLeafSources);
  }

  @Test
  public void includeSourcesInNonLeafWhenExplicitlyRequested() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "pom.xml,src/main/java");

    Path baseDir = temp.getRoot().toPath();
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

    String allBaseSources = basePom + "," + baseSources;
    assertThat(props.get("sonar.sources")).isEqualTo(allBaseSources);
    String allLeafSources = leafPom + "," + leafSources;
    assertThat(props.get("com.foo:module1.sonar.sources")).isEqualTo(allLeafSources);
  }

  // MSONAR-155
  @Test
  public void two_modules_in_same_folder() throws Exception {
    File baseDir = temp.getRoot();
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

    assertThat(props.get("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.get("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.get("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.get("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.get("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    assertThat(props.get(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(modulesBaseDir.getAbsolutePath());
    assertThat(props.get(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(modulesBaseDir.getAbsolutePath());
  }

  private MavenProject createProject(Properties pomProps, String packaging) throws IOException {
    File pom = temp.newFile("pom.xml");
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
    return project;
  }
}
