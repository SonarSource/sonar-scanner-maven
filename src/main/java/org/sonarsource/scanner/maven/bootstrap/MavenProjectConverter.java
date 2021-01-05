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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.sonarsource.scanner.api.ScanProperties;
import org.sonarsource.scanner.api.ScannerProperties;
import org.sonarsource.scanner.maven.bootstrap.MavenCompilerResolver.MavenCompilerConfiguration;

public class MavenProjectConverter {
  private final Log log;

  private static final char SEPARATOR = ',';

  private static final String UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE = "Unable to determine structure of project."
    + " Probably you use Maven Advanced Reactor Options with a broken tree of modules.";

  private static final String MODULE_KEY = "sonar.moduleKey";

  private static final String PROPERTY_PROJECT_BUILDDIR = "sonar.projectBuildDir";

  private static final String JAVA_SOURCE_PROPERTY = "sonar.java.source";

  private static final String JAVA_TARGET_PROPERTY = "sonar.java.target";

  private static final String LINKS_HOME_PAGE = "sonar.links.homepage";

  private static final String LINKS_CI = "sonar.links.ci";

  private static final String LINKS_ISSUE_TRACKER = "sonar.links.issue";

  private static final String LINKS_SOURCES = "sonar.links.scm";

  private static final String LINKS_SOURCES_DEV = "sonar.links.scm_dev";

  private static final String MAVEN_PACKAGING_POM = "pom";

  private static final String MAVEN_PACKAGING_WAR = "war";

  public static final String ARTIFACTID_MAVEN_WAR_PLUGIN = "maven-war-plugin";

  public static final String ARTIFACTID_MAVEN_SUREFIRE_PLUGIN = "maven-surefire-plugin";

  public static final String ARTIFACTID_FINDBUGS_MAVEN_PLUGIN = "findbugs-maven-plugin";

  public static final String FINDBUGS_EXCLUDE_FILTERS = "sonar.findbugs.excludeFilters";

  private static final String JAVA_PROJECT_MAIN_BINARY_DIRS = "sonar.java.binaries";

  private static final String JAVA_PROJECT_MAIN_LIBRARIES = "sonar.java.libraries";

  private static final String SONAR_JAVA_JDK_HOME_PROPERTY = "sonar.java.jdkHome";

  private static final String GROOVY_PROJECT_MAIN_BINARY_DIRS = "sonar.groovy.binaries";

  private static final String JAVA_PROJECT_TEST_BINARY_DIRS = "sonar.java.test.binaries";

  private static final String JAVA_PROJECT_TEST_LIBRARIES = "sonar.java.test.libraries";

  private static final String SUREFIRE_REPORTS_PATH_DEPRECATED_PROPERTY = "sonar.junit.reportsPath";
  // Since SonarJava 4.11
  private static final String SUREFIRE_REPORTS_PATH_PROPERTY = "sonar.junit.reportPaths";

  /**
   * Optional paths to binaries, for example to declare the directory of Java bytecode. Example : "binDir"
   */
  private static final String PROJECT_BINARY_DIRS = "sonar.binaries";

  /**
   * Optional comma-separated list of paths to libraries. Example :
   * <code>path/to/library/*.jar,path/to/specific/library/myLibrary.jar,parent/*.jar</code>
   */
  private static final String PROJECT_LIBRARIES = "sonar.libraries";

  private Properties userProperties;
  @Nullable
  private String specifiedProjectKey;

  private final Properties envProperties;

  private final MavenCompilerResolver mavenCompilerResolver;

  public MavenProjectConverter(Log log, MavenCompilerResolver mavenCompilerResolver, Properties envProperties) {
    this.log = log;
    this.mavenCompilerResolver = mavenCompilerResolver;
    this.envProperties = envProperties;
  }

  Map<String, String> configure(List<MavenProject> mavenProjects, MavenProject root, Properties userProperties) throws MojoExecutionException {
    this.userProperties = userProperties;
    this.specifiedProjectKey = specifiedProjectKey(userProperties, root);
    Map<MavenProject, Map<String, String>> propsByModule = new LinkedHashMap<>();

    try {
      configureModules(mavenProjects, propsByModule);
      Map<String, String> props = new HashMap<>();
      props.put(ScanProperties.PROJECT_KEY, getArtifactKey(root));
      Path topLevelDir = rebuildModuleHierarchy(props, propsByModule, root, "");
      props.put(ScanProperties.PROJECT_BASEDIR, topLevelDir.toString());
      if (!propsByModule.isEmpty()) {
        throw new IllegalStateException(UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE + " \""
          + propsByModule.keySet().iterator().next().getName() + "\" is orphan");
      }
      return props;
    } catch (IOException e) {
      throw new IllegalStateException("Cannot configure project", e);
    }
  }

  private static Path rebuildModuleHierarchy(Map<String, String> properties, Map<MavenProject, Map<String, String>> propsByModule,
    MavenProject current, String prefix)
    throws IOException {
    Map<String, String> currentProps = propsByModule.get(current);
    if (currentProps == null) {
      throw new IllegalStateException(UNABLE_TO_DETERMINE_PROJECT_STRUCTURE_EXCEPTION_MESSAGE);
    }
    for (Map.Entry<String, String> prop : currentProps.entrySet()) {
      properties.put(prefix + prop.getKey(), prop.getValue());
    }
    propsByModule.remove(current);
    Path topLevelDir = current.getBasedir().toPath().toAbsolutePath();
    List<String> moduleIds = new ArrayList<>();
    for (String modulePathStr : current.getModules()) {
      File modulePath = new File(current.getBasedir(), modulePathStr);
      MavenProject module = findMavenProject(modulePath, propsByModule.keySet());
      if (module != null) {
        String moduleId = module.getGroupId() + ":" + module.getArtifactId();
        Path topLevelModuleDir = rebuildModuleHierarchy(properties, propsByModule, module, prefix + moduleId + ".");
        moduleIds.add(moduleId);
        if (!topLevelModuleDir.startsWith(topLevelDir)) {
          // Find common prefix
          topLevelDir = findCommonParentDir(topLevelDir, topLevelModuleDir);
        }
      }
    }
    if (!moduleIds.isEmpty()) {
      properties.put(prefix + "sonar.modules", StringUtils.join(moduleIds, SEPARATOR));
    }
    return topLevelDir;
  }

  static Path findCommonParentDir(Path dir1, Path dir2) {
    if (dir1.startsWith(dir2)) {
      return dir2;
    }
    if (dir2.startsWith(dir1)) {
      return dir1;
    }
    Path candidate = dir1.getParent();
    while (candidate != null) {
      if (dir2.startsWith(candidate)) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException("Unable to find a common parent between two modules baseDir: '" + dir1 + "' and '" + dir2 + "'");
  }

  private void configureModules(List<MavenProject> mavenProjects, Map<MavenProject, Map<String, String>> propsByModule)
    throws MojoExecutionException {
    for (MavenProject pom : mavenProjects) {
      boolean skipped = "true".equals(pom.getModel().getProperties().getProperty("sonar.skip"));
      if (skipped) {
        log.info("Module " + pom + " skipped by property 'sonar.skip'");
        continue;
      }
      propsByModule.put(pom, computeSonarQubeProperties(pom));
    }
  }

  private static MavenProject findMavenProject(final File modulePath, Collection<MavenProject> modules)
    throws IOException {

    File canonical = modulePath.getCanonicalFile();
    if (canonical.isDirectory()) {
      File pom = new File(canonical, "pom.xml");
      for (MavenProject module : modules) {
        if (module.getFile().getCanonicalFile().equals(pom)) {
          return module;
        }
      }
      for (MavenProject module : modules) {
        if (module.getBasedir().equals(canonical)) {
          return module;
        }
      }
    } else {
      for (MavenProject module : modules) {
        if (module.getFile().getCanonicalFile().equals(canonical)) {
          return module;
        }
      }
    }

    return null;
  }

  private Map<String, String> computeSonarQubeProperties(MavenProject pom) throws MojoExecutionException {
    Map<String, String> props = new HashMap<>();
    defineModuleKey(pom, props, specifiedProjectKey);
    props.put(ScanProperties.PROJECT_VERSION, pom.getVersion());
    props.put(ScanProperties.PROJECT_NAME, pom.getName());
    String description = pom.getDescription();
    if (description != null) {
      props.put(ScanProperties.PROJECT_DESCRIPTION, description);
    }

    populateJavaAnalyzerProperties(pom, props);
    guessEncoding(pom, props);
    convertMavenLinksToProperties(props, pom);
    synchronizeFileSystemAndOtherProps(pom, props);
    findBugsExcludeFileMaven(pom, props);
    return props;
  }

  @CheckForNull
  private static String specifiedProjectKey(Properties userProperties, MavenProject root) {
    String projectKey = userProperties.getProperty(ScanProperties.PROJECT_KEY);
    if (projectKey == null) {
      projectKey = root.getModel().getProperties().getProperty(ScanProperties.PROJECT_KEY);
    }
    if (projectKey == null || projectKey.isEmpty()) {
      return null;
    }
    return projectKey;
  }

  private static void defineModuleKey(MavenProject pom, Map<String, String> props, @Nullable String specifiedProjectKey) {
    String key;
    if (pom.getModel().getProperties().containsKey(ScanProperties.PROJECT_KEY)) {
      key = pom.getModel().getProperties().getProperty(ScanProperties.PROJECT_KEY);
    } else if (specifiedProjectKey != null) {
      key = specifiedProjectKey + ":" + getArtifactKey(pom);
    } else {
      key = getArtifactKey(pom);
    }
    props.put(MODULE_KEY, key);
  }

  private static String getArtifactKey(MavenProject pom) {
    return pom.getGroupId() + ":" + pom.getArtifactId();
  }

  private static void guessEncoding(MavenProject pom, Map<String, String> props) {
    // See http://jira.codehaus.org/browse/SONAR-2151
    String encoding = MavenUtils.getSourceEncoding(pom);
    if (encoding != null) {
      props.put(ScanProperties.PROJECT_SOURCE_ENCODING, encoding);
    }
  }

  private void populateJavaAnalyzerProperties(MavenProject pom, Map<String, String> props) {
    Optional<MavenCompilerConfiguration> javaCompilerConfig = mavenCompilerResolver.extractConfiguration(pom);
    javaCompilerConfig.ifPresent(config -> {
      populateJavaAnalyzerSourceAndTarget(config, props);
      populateJavaAnalyzerJdkHome(config, props);
    });
  }

  private static void populateJavaAnalyzerJdkHome(MavenCompilerConfiguration config, Map<String, String> props) {
    config.getJdkHome().ifPresent(jdkHome -> props.put(SONAR_JAVA_JDK_HOME_PROPERTY, jdkHome));
  }

  private static void populateJavaAnalyzerSourceAndTarget(MavenCompilerConfiguration config, Map<String, String> props) {
    // Get Java release version from maven-compiler-plugin.
    Optional<String> release = config.getRelease();
    if (release.isPresent()) {
      props.put(JAVA_SOURCE_PROPERTY, release.get());
      props.put(JAVA_TARGET_PROPERTY, release.get());
    } else {
      // See http://jira.codehaus.org/browse/SONAR-2148
      // Get Java source and target versions from maven-compiler-plugin.
      config.getSource().ifPresent(s -> props.put(JAVA_SOURCE_PROPERTY, s));
      config.getTarget().ifPresent(t -> props.put(JAVA_TARGET_PROPERTY, t));
    }
  }

  private static void findBugsExcludeFileMaven(MavenProject pom, Map<String, String> props) {
    String excludeFilterFile = MavenUtils.getPluginSetting(pom, MavenUtils.GROUP_ID_CODEHAUS_MOJO, ARTIFACTID_FINDBUGS_MAVEN_PLUGIN, "excludeFilterFile", null);
    File path = resolvePath(excludeFilterFile, pom.getBasedir());
    if (path != null && path.exists()) {
      props.put(FINDBUGS_EXCLUDE_FILTERS, path.getAbsolutePath());
    }
  }

  /**
   * For SONAR-3676
   */
  private static void convertMavenLinksToProperties(Map<String, String> props, MavenProject pom) {
    setPropertyIfNotAlreadyExists(props, LINKS_HOME_PAGE, pom.getUrl());

    Scm scm = pom.getScm();
    if (scm == null) {
      scm = new Scm();
    }
    setPropertyIfNotAlreadyExists(props, LINKS_SOURCES, scm.getUrl());
    setPropertyIfNotAlreadyExists(props, LINKS_SOURCES_DEV, scm.getDeveloperConnection());

    CiManagement ci = pom.getCiManagement();
    if (ci == null) {
      ci = new CiManagement();
    }
    setPropertyIfNotAlreadyExists(props, LINKS_CI, ci.getUrl());

    IssueManagement issues = pom.getIssueManagement();
    if (issues == null) {
      issues = new IssueManagement();
    }
    setPropertyIfNotAlreadyExists(props, LINKS_ISSUE_TRACKER, issues.getUrl());
  }

  private static void setPropertyIfNotAlreadyExists(Map<String, String> props, String propertyKey, String propertyValue) {
    if (StringUtils.isBlank(props.get(propertyKey))) {
      props.put(propertyKey, StringUtils.defaultString(propertyValue));
    }
  }

  private void synchronizeFileSystemAndOtherProps(MavenProject pom, Map<String, String> props)
    throws MojoExecutionException {
    props.put(ScanProperties.PROJECT_BASEDIR, pom.getBasedir().getAbsolutePath());
    File buildDir = getBuildDir(pom);
    if (buildDir != null) {
      props.put(PROPERTY_PROJECT_BUILDDIR, buildDir.getAbsolutePath());
      props.put(ScannerProperties.WORK_DIR, getSonarWorkDir(pom).getAbsolutePath());
    }
    populateBinaries(pom, props);

    populateLibraries(pom, props, false);
    populateLibraries(pom, props, true);

    populateSurefireReportsPath(pom, props);

    // IMPORTANT NOTE : reference on properties from POM model must not be saved,
    // instead they should be copied explicitly - see SONAR-2896
    for (String k : pom.getModel().getProperties().stringPropertyNames()) {
      props.put(k, pom.getModel().getProperties().getProperty(k));
    }

    MavenUtils.putAll(envProperties, props);

    // Add user properties (ie command line arguments -Dsonar.xxx=yyyy) in last position to
    // override all other
    MavenUtils.putAll(userProperties, props);

    List<File> mainDirs = mainSources(pom);
    props.put(ScanProperties.PROJECT_SOURCE_DIRS, StringUtils.join(toPaths(mainDirs), SEPARATOR));
    List<File> testDirs = testSources(pom);
    if (!testDirs.isEmpty()) {
      props.put(ScanProperties.PROJECT_TEST_DIRS, StringUtils.join(toPaths(testDirs), SEPARATOR));
    } else {
      props.remove(ScanProperties.PROJECT_TEST_DIRS);
    }
  }

  private static void populateSurefireReportsPath(MavenProject pom, Map<String, String> props) {
    String surefireReportsPath = MavenUtils.getPluginSetting(pom, MavenUtils.GROUP_ID_APACHE_MAVEN, ARTIFACTID_MAVEN_SUREFIRE_PLUGIN, "reportsDirectory",
      pom.getBuild().getDirectory() + File.separator + "surefire-reports");
    File path = resolvePath(surefireReportsPath, pom.getBasedir());
    if (path != null && path.exists()) {
      props.put(SUREFIRE_REPORTS_PATH_DEPRECATED_PROPERTY, path.getAbsolutePath());
      props.put(SUREFIRE_REPORTS_PATH_PROPERTY, path.getAbsolutePath());
    }
  }

  private static void populateLibraries(MavenProject pom, Map<String, String> props, boolean test) throws MojoExecutionException {
    List<String> classpathElements;
    try {
      classpathElements = test ? pom.getTestClasspathElements() : pom.getCompileClasspathElements();
    } catch (DependencyResolutionRequiredException e) {
      throw new MojoExecutionException("Unable to populate" + (test ? " test" : "") + " libraries", e);
    }

    List<File> libraries = new ArrayList<>();
    if (classpathElements != null) {
      String outputDirectory = test ? pom.getBuild().getTestOutputDirectory() : pom.getBuild().getOutputDirectory();
      File basedir = pom.getBasedir();
      classpathElements.stream()
        .filter(cp -> !cp.equals(outputDirectory))
        .map(cp -> Optional.ofNullable(resolvePath(cp, basedir)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(File::exists)
        .forEach(libraries::add);
    }
    if (!libraries.isEmpty()) {
      String librariesValue = StringUtils.join(toPaths(libraries), SEPARATOR);
      if (test) {
        props.put(JAVA_PROJECT_TEST_LIBRARIES, librariesValue);
      } else {
        // Populate both deprecated and new property for backward compatibility
        props.put(PROJECT_LIBRARIES, librariesValue);
        props.put(JAVA_PROJECT_MAIN_LIBRARIES, librariesValue);
      }
    }
  }

  private static void populateBinaries(MavenProject pom, Map<String, String> props) {
    File mainBinaryDir = resolvePath(pom.getBuild().getOutputDirectory(), pom.getBasedir());
    if (mainBinaryDir != null && mainBinaryDir.exists()) {
      String binPath = mainBinaryDir.getAbsolutePath();
      // Populate both deprecated and new property for backward compatibility
      props.put(PROJECT_BINARY_DIRS, binPath);
      props.put(JAVA_PROJECT_MAIN_BINARY_DIRS, binPath);
      props.put(GROOVY_PROJECT_MAIN_BINARY_DIRS, binPath);
    }
    File testBinaryDir = resolvePath(pom.getBuild().getTestOutputDirectory(), pom.getBasedir());
    if (testBinaryDir != null && testBinaryDir.exists()) {
      String binPath = testBinaryDir.getAbsolutePath();
      props.put(JAVA_PROJECT_TEST_BINARY_DIRS, binPath);
    }
  }

  private static File getSonarWorkDir(MavenProject pom) {
    return new File(getBuildDir(pom), "sonar");
  }

  private static File getBuildDir(MavenProject pom) {
    return resolvePath(pom.getBuild().getDirectory(), pom.getBasedir());
  }

  private static File resolvePath(@Nullable String path, File basedir) {
    if (path != null) {
      File file = new File(StringUtils.trim(path));
      if (!file.isAbsolute()) {
        file = new File(basedir, path).getAbsoluteFile();
      }
      return file;
    }
    return null;
  }

  private static List<File> resolvePaths(Collection<String> paths, File basedir) {
    List<File> result = new ArrayList<>();
    for (String path : paths) {
      File fileOrDir = resolvePath(path, basedir);
      if (fileOrDir != null) {
        result.add(fileOrDir);
      }
    }
    return result;
  }

  private static void removeTarget(MavenProject pom, Collection<String> relativeOrAbsolutePaths) {
    final Path baseDir = pom.getBasedir().toPath().toAbsolutePath().normalize();
    final Path target = Paths.get(pom.getBuild().getDirectory()).toAbsolutePath().normalize();
    final Path targetRelativePath = baseDir.relativize(target);

    relativeOrAbsolutePaths.removeIf(pathStr -> {
      Path path = Paths.get(pathStr).toAbsolutePath().normalize();
      Path relativePath = baseDir.relativize(path);
      return relativePath.startsWith(targetRelativePath);
    });
  }

  private List<File> mainSources(MavenProject pom) throws MojoExecutionException {
    Set<String> sources = new LinkedHashSet<>();
    if (MAVEN_PACKAGING_WAR.equals(pom.getModel().getPackaging())) {
      sources.add(MavenUtils.getPluginSetting(
        pom,
        MavenUtils.GROUP_ID_APACHE_MAVEN,
        ARTIFACTID_MAVEN_WAR_PLUGIN,
        "warSourceDirectory",
        new File(pom.getBasedir().getAbsolutePath(), "src/main/webapp").getAbsolutePath()));
    }

    sources.add(pom.getFile().getAbsolutePath());
    if (!MAVEN_PACKAGING_POM.equals(pom.getModel().getPackaging())) {
      pom.getCompileSourceRoots().stream()
        .map(Paths::get)
        .map(path -> path.isAbsolute() ? path : pom.getBasedir().toPath().resolve(path))
        .map(Path::toString)
        .forEach(sources::add);
    }

    return sourcePaths(pom, ScanProperties.PROJECT_SOURCE_DIRS, sources);
  }

  private List<File> testSources(MavenProject pom) throws MojoExecutionException {
    return sourcePaths(pom, ScanProperties.PROJECT_TEST_DIRS, pom.getTestCompileSourceRoots());
  }

  private List<File> sourcePaths(MavenProject pom, String propertyKey, Collection<String> mavenPaths) throws MojoExecutionException {
    List<File> filesOrDirs;
    boolean userDefined = false;
    String prop = StringUtils.defaultIfEmpty(userProperties.getProperty(propertyKey), envProperties.getProperty(propertyKey));
    prop = StringUtils.defaultIfEmpty(prop, pom.getProperties().getProperty(propertyKey));

    if (prop != null) {
      List<String> paths = Arrays.asList(StringUtils.split(prop, ","));
      filesOrDirs = resolvePaths(paths, pom.getBasedir());
      userDefined = true;
    } else {
      removeTarget(pom, mavenPaths);
      filesOrDirs = resolvePaths(mavenPaths, pom.getBasedir());
    }

    if (userDefined && !MAVEN_PACKAGING_POM.equals(pom.getModel().getPackaging())) {
      return existingPathsOrFail(filesOrDirs, pom, propertyKey);
    } else {
      // Maven provides some directories that do not exist. They
      // should be removed. Same for pom module were sonar.sources and sonar.tests
      // can be defined only to be inherited by children
      return removeNested(keepExistingPaths(filesOrDirs));
    }
  }

  private static List<File> existingPathsOrFail(List<File> dirs, MavenProject pom, String propertyKey)
    throws MojoExecutionException {
    for (File dir : dirs) {
      if (!dir.exists()) {
        throw new MojoExecutionException(
          String.format("The directory '%s' does not exist for Maven module %s. Please check the property %s",
            dir.getAbsolutePath(), pom.getId(), propertyKey));
      }
    }
    return dirs;
  }

  private static List<File> keepExistingPaths(List<File> files) {
    return files.stream().filter(f -> f != null && f.exists()).collect(Collectors.toList());
  }

  private static List<File> removeNested(List<File> originalPaths) {
    List<File> result = new ArrayList<>();
    for (File maybeChild : originalPaths) {
      boolean hasParent = false;
      for (File possibleParent : originalPaths) {
        if (isStrictChild(maybeChild, possibleParent)) {
          hasParent = true;
        }
      }
      if (!hasParent) {
        result.add(maybeChild);
      }
    }
    return result;
  }

  private static boolean isStrictChild(File maybeChild, File possibleParent) {
    return !maybeChild.equals(possibleParent) && maybeChild.toPath().startsWith(possibleParent.toPath());
  }

  private static String[] toPaths(Collection<File> dirs) {
    return dirs.stream().map(File::getAbsolutePath).toArray(String[]::new);
  }
}
