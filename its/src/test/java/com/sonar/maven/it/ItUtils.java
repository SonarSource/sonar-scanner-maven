/*
 * SonarSource :: IT :: SonarQube Maven
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
package com.sonar.maven.it;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class ItUtils {

  private ItUtils() {
  }

  private static final File home;

  static {
    File testResources = FileUtils.toFile(ItUtils.class.getResource("/ItUtilsLocator.txt"));
    home = testResources // home/tests/src/tests/resources
      .getParentFile() // home/tests/src/tests
      .getParentFile() // home/tests/src
      .getParentFile(); // home/tests
  }

  public static File locateHome() {
    return home;
  }

  public static File locateProjectDir(String projectName) {
    return new File(locateHome(), "projects/" + projectName);
  }

  public static File locateProjectPom(String projectName) {
    return new File(locateProjectDir(projectName), "pom.xml");
  }

  /**
   * Creates a settings xml with a sonar profile, containing all the given properties
   * Also adds repox to continue to use QAed artifacts 
   */
  public static String createSettingsXml(Map<String, String> props) throws Exception {
    DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    Document doc = docBuilder.newDocument();

    Element settings = doc.createElement("settings");
    Element profiles = doc.createElement("profiles");
    Element profile = doc.createElement("profile");

    Element id = doc.createElement("id");
    id.setTextContent("sonar");

    Element properties = doc.createElement("properties");

    for (Map.Entry<String, String> e : props.entrySet()) {
      Element el = doc.createElement(e.getKey());
      el.setTextContent(e.getValue());
      properties.appendChild(el);
    }

    profile.appendChild(id);
    profile.appendChild(properties);
    profile.appendChild(createRepositories(doc));
    profile.appendChild(createPluginRepositories(doc));

    profiles.appendChild(profile);
    settings.appendChild(profiles);
    doc.appendChild(settings);

    Writer writer = new StringWriter();
    Transformer tf = TransformerFactory.newInstance().newTransformer();
    tf.transform(new DOMSource(doc), new StreamResult(writer));
    return writer.toString();
  }

  private static Element createRepositories(Document doc) {
    Element repositories = doc.createElement("repositories");
    Element repository = doc.createElement("repository");
    Element id = doc.createElement("id");
    Element url = doc.createElement("url");

    id.setTextContent("sonarsource");
    url.setTextContent("https://repox.jfrog.io/repox/sonarsource");

    repositories.appendChild(repository);
    repository.appendChild(id);
    repository.appendChild(url);

    return repositories;
  }

  private static Element createPluginRepositories(Document doc) {
    Element repositories = doc.createElement("pluginRepositories");
    Element repository = doc.createElement("pluginRepository");
    Element id = doc.createElement("id");
    Element url = doc.createElement("url");

    id.setTextContent("sonarsource");
    url.setTextContent("https://repox.jfrog.io/repox/sonarsource");

    repositories.appendChild(repository);
    repository.appendChild(id);
    repository.appendChild(url);

    return repositories;
  }

}
