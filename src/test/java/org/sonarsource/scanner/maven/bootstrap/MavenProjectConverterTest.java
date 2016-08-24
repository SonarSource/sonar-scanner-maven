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
package org.sonarsource.scanner.maven.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.scanner.maven.DependencyCollector;

public class MavenProjectConverterTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private DependencyCollector dependencyCollector = mock(DependencyCollector.class);

  private Log log;

  private Properties env;

  private MavenProjectConverter projectConverter;
  
  private JavaVersionResolver javaVersionResolver;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void prepare() {
    log = mock(Log.class);
    javaVersionResolver = mock(JavaVersionResolver.class);
    env = new Properties();
    when(dependencyCollector.toJson(any(MavenProject.class))).thenReturn("");
    projectConverter = new MavenProjectConverter(log, dependencyCollector, javaVersionResolver, env);
  }

  @Test
  public void convertSingleModuleProject() throws Exception {
    File baseDir = temp.newFolder();
    MavenProject project = createProject(new File(baseDir, "pom.xml"), new Properties(), "jar");

    Properties props = projectConverter.configure(Arrays.asList(project),
      project, new Properties());
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");
  }

  // MSONAR-104
  @Test
  public void convertSingleModuleProjectAvoidNestedFolders() throws Exception {
    File baseDir = temp.newFolder();
    File webappDir = new File(baseDir, "src/main/webapp");
    webappDir.mkdirs();
    MavenProject project = createProject(new File(baseDir, "pom.xml"), new Properties(), "war");
    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.getProperty("sonar.sources")).isEqualTo(webappDir.getAbsolutePath());

    project.getModel().getProperties().setProperty("sonar.sources", "src");
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.getProperty("sonar.sources")).isEqualTo(srcDir.getAbsolutePath());
  }

  @Test
  public void shouldIncludePomIfRequested() throws Exception {
    File baseDir = temp.newFolder();
    File pom = new File(baseDir, "pom.xml");
    pom.createNewFile();
    MavenProject project = createProject(pom, new Properties(), "jar");

    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.getProperty("sonar.sources")).contains("pom.xml");
  }

  @Test
  public void shouldUseEnvironment() throws Exception {
    env.put("sonar.projectKey", "com.foo:anotherProject");
    File baseDir = temp.newFolder();
    File pom = new File(baseDir, "pom.xml");
    pom.createNewFile();
    MavenProject project = createProject(pom, new Properties(), "jar");

    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:anotherProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");
    assertThat(props.getProperty("sonar.sources")).contains("pom.xml");
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

    projectConverter.configure(Arrays.asList(module1, root), root, new Properties());
  }

  @Test
  public void convertMultiModuleProject() throws Exception {
    File baseDir = temp.newFolder();
    MavenProject root = createProject(new File(baseDir, "pom.xml"), new Properties(), "pom");
    root.setFile(new File(baseDir, "pom.xml"));

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

    Properties props = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.getProperty("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.getProperty("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.getProperty(module1Key + ".sonar.modules").split(",")).containsOnly(module11Key,
      module12Key);

    assertThat(props.getProperty(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    assertThat(props.getProperty(module1Key + "." + module11Key
      + ".sonar.projectBaseDir")).isEqualTo(module11BaseDir.getAbsolutePath());
    assertThat(props.getProperty(module1Key + "." + module12Key
      + ".sonar.projectBaseDir")).isEqualTo(module12BaseDir.getAbsolutePath());
    assertThat(props.getProperty(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());
  }

  // MSONAR-91
  @Test
  public void convertMultiModuleProjectSkipModule() throws Exception {
    File baseDir = temp.newFolder();
    MavenProject root = createProject(new File(baseDir, "pom.xml"), new Properties(), "pom");
    root.setFile(new File(baseDir, "pom.xml"));

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

    Properties props = projectConverter.configure(Arrays.asList(module12, module11, module1, module2, root),
      root, new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.getProperty("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.getProperty("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    String module11Key = "com.foo:module11";
    String module12Key = "com.foo:module12";
    assertThat(props.getProperty(module1Key + ".sonar.modules").split(",")).containsOnly(module12Key);

    assertThat(props.getProperty(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    // Module 11 is skipped
    assertThat(props.getProperty(module1Key + "." + module11Key + ".sonar.projectBaseDir")).isNull();
    assertThat(props.getProperty(module1Key + "." + module12Key
      + ".sonar.projectBaseDir")).isEqualTo(module12BaseDir.getAbsolutePath());
    assertThat(props.getProperty(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());

    verify(log).info("Module MavenProject: com.foo:module11:2.1 @ "
      + new File(module11BaseDir, "pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  // MSONAR-125
  @Test
  public void skipOrphanModule() throws Exception {
    File baseDir = temp.newFolder();
    MavenProject root = createProject(new File(baseDir, "pom.xml"), new Properties(), "pom");

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

    Properties props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root, new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.getProperty("sonar.projectBaseDir")).isEqualTo(baseDir.getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.getProperty("sonar.modules").split(",")).containsOnly(module1Key);

    assertThat(props.getProperty(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    // Module 2 is skipped
    assertThat(props.getProperty(module2Key + ".sonar.projectBaseDir")).isNull();

    verify(log).info("Module MavenProject: com.foo:module2:2.1 @ "
      + new File(module2BaseDir, "target/dependency-reduced-pom.xml").getAbsolutePath()
      + " skipped by property 'sonar.skip'");
  }

  @Test
  public void overrideSourcesSingleModuleProject() throws Exception {
    temp.newFolder("src");
    File srcMainDir = temp.newFolder("src", "main").getAbsoluteFile();
    File pom = temp.newFile("pom.xml");

    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");

    MavenProject project = createProject(pom, pomProps, "jar");

    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.getProperty("sonar.sources")).isEqualTo(srcMainDir.getAbsolutePath());
  }

  @Test
  public void overrideSourcesMultiModuleProject() throws Exception {
    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "src/main");
    pomProps.put("sonar.tests", "src/test");

    File pomRoot = temp.newFile("pom.xml");
    MavenProject root = createProject(pomRoot, pomProps, "pom");

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
    MavenProject module2 = createProject(new File(module2BaseDir, "pom.xml"), pomProps, "jar");
    module2.getModel().setArtifactId("module2");
    module2.getModel().setName("My Project - Module 2");
    module2.getModel().setDescription("My sample project - Module 2");
    module2.setParent(root);
    root.getModules().add("module2");

    Properties props = projectConverter.configure(Arrays.asList(module1, module2, root),
      root,
      new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("com.foo:myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");

    assertThat(props.getProperty("sonar.projectBaseDir")).isEqualTo(temp.getRoot().getAbsolutePath());

    String module1Key = "com.foo:module1";
    String module2Key = "com.foo:module2";
    assertThat(props.getProperty("sonar.modules").split(",")).containsOnly(module1Key, module2Key);

    assertThat(props.getProperty(module1Key
      + ".sonar.projectBaseDir")).isEqualTo(module1BaseDir.getAbsolutePath());
    assertThat(props.getProperty(module2Key
      + ".sonar.projectBaseDir")).isEqualTo(module2BaseDir.getAbsolutePath());

    assertThat(props.getProperty("sonar.sources")).isEqualTo("");
    assertThat(props.getProperty("sonar.tests")).isNull();
    assertThat(props.getProperty(module1Key + ".sonar.sources")).isEqualTo(module1SrcDir.getAbsolutePath());
    assertThat(props.getProperty(module1Key + ".sonar.tests")).isEqualTo(module1TestDir.getAbsolutePath());
    assertThat(props.getProperty(module2Key + ".sonar.sources")).isEqualTo(module2SrcDir.getAbsolutePath());
    assertThat(props.getProperty(module2Key + ".sonar.tests")).isEqualTo(module2TestDir.getAbsolutePath());
  }

  @Test(expected = MojoExecutionException.class)
  public void overrideSourcesNonexistentFolder() throws Exception {
    File pom = temp.newFile("pom.xml");

    Properties pomProps = new Properties();
    pomProps.put("sonar.sources", "nonexistent-folder");

    MavenProject project = createProject(pom, pomProps, "jar");

    projectConverter.configure(Arrays.asList(project), project, new Properties());
  }

  @Test
  public void overrideProjectKeySingleModuleProject() throws Exception {
    File pom = temp.newFile("pom.xml");

    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");

    MavenProject project = createProject(pom, pomProps, "jar");

    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.getProperty("sonar.projectKey")).isEqualTo("myProject");
    assertThat(props.getProperty("sonar.projectName")).isEqualTo("My Project");
    assertThat(props.getProperty("sonar.projectVersion")).isEqualTo("2.1");
  }

  // MSONAR-134
  @Test
  public void preserveFoldersHavingCommonPrefix() throws Exception {
    File baseDir = temp.newFolder();
    File srcDir = new File(baseDir, "src");
    srcDir.mkdirs();
    File srcGenDir = new File(baseDir, "src-gen");
    srcGenDir.mkdirs();
    MavenProject project = createProject(new File(baseDir, "pom.xml"), new Properties(), "jar");
    project.getCompileSourceRoots().add("src");
    project.getCompileSourceRoots().add("src-gen");

    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());

    assertThat(props.getProperty("sonar.sources")).contains(srcDir.getAbsolutePath(), srcGenDir.getAbsolutePath());
  }

  // MSONAR-145
  @Test
  public void ignoreNonStringModelProperties() throws Exception {
    File pom = temp.newFile("pom.xml");

    Properties pomProps = new Properties();
    pomProps.put("sonar.projectKey", "myProject");
    pomProps.put("sonar.integer", new Integer(10));
    pomProps.put("sonar.string", "myString");

    MavenProject project = createProject(pom, pomProps, "jar");
    Properties props = projectConverter.configure(Arrays.asList(project), project, new Properties());
    assertThat(props.getProperty("sonar.string")).isEqualTo("myString");
    assertThat(props).doesNotContainKey("sonar.integer");
  }

  private MavenProject createProject(File pom, Properties pomProps, String packaging)
    throws Exception {
    MavenProject project = new MavenProject();
    project.getModel().setGroupId("com.foo");
    project.getModel().setArtifactId("myProject");
    project.getModel().setName("My Project");
    project.getModel().setDescription("My sample project");
    project.getModel().setVersion("2.1");
    project.getModel().setPackaging(packaging);
    project.getBuild().setOutputDirectory(temp.newFolder().getPath());
    project.getBuild().setTestOutputDirectory(temp.newFolder().getPath());
    project.getModel().setProperties(pomProps);
    project.setFile(pom);
    return project;
  }
}
