/*
 * SonarSource :: E2E :: SonarQube Maven
 * Copyright (C) 2009-2025 SonarSource Sàrl
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
package com.sonar.maven.it.suite;

import com.sonar.maven.it.ItUtils;
import com.sonar.maven.it.Proxy;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyTest extends AbstractMavenTest {
  private Proxy proxy;

  @BeforeEach
  void prepare() throws Exception {
    proxy = new Proxy();
    proxy.startProxy();
  }

  @AfterEach
  void after() throws Exception {
    if (proxy != null) {
      proxy.stopProxy();
    }
  }

  @Test
  void useActiveProxyInSettings(@TempDir Path temp) throws Exception {
    waitForProxyToBeUpAndRunning(proxy.port());

    Path originalSettings = Path.of(System.getProperty("user.home")).resolve(".m2").resolve("settings.xml");
    assertThat(originalSettings).exists();

    Path mergedSettings = temp.resolve("settings.xml");
    mergeProxyIntoSettings(originalSettings, mergedSettings, proxy.port());

    MavenBuild build = MavenBuild.create(ItUtils.locateProjectPom("maven/many-source-dirs"))
      .setGoals(cleanPackageSonarGoal());
    build.addArgument("--settings=" + mergedSettings.toAbsolutePath().toString());
    // "-X" can not be replaced with "--debug" because it causes the test to freeze with Maven 4
    build.addArgument("-X");
    build.addArgument("--update-snapshots");
    BuildResult result = executeBuildAndAssertWithCE(build);

    assertThat(result.getLogs()).contains("Setting proxy properties");
    assertThat(proxy.seen()).isNotEmpty();
  }

  private void mergeProxyIntoSettings(Path originalSettings, Path outputSettings, int proxyPort) throws Exception {
    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
    Document doc = dBuilder.parse(originalSettings.toFile());
    doc.getDocumentElement().normalize();

    Element root = doc.getDocumentElement();

    // Remove existing <proxies> element if present
    NodeList existingProxies = root.getElementsByTagName("proxies");
    for (int i = 0; i < existingProxies.getLength(); i++) {
      Node node = existingProxies.item(i);
      root.removeChild(node);
    }

    // Create new <proxies> element
    Element proxiesElt = doc.createElement("proxies");
    Element proxyElt = doc.createElement("proxy");

    Element id = doc.createElement("id");
    id.setTextContent("example-proxy");
    proxyElt.appendChild(id);

    Element active = doc.createElement("active");
    active.setTextContent("true");
    proxyElt.appendChild(active);

    Element protocol = doc.createElement("protocol");
    protocol.setTextContent("http|https");
    proxyElt.appendChild(protocol);

    Element host = doc.createElement("host");
    host.setTextContent("localhost");
    proxyElt.appendChild(host);

    Element port = doc.createElement("port");
    port.setTextContent(String.valueOf(proxyPort));
    proxyElt.appendChild(port);

    Element username = doc.createElement("username");
    username.setTextContent("scott");
    proxyElt.appendChild(username);

    Element password = doc.createElement("password");
    password.setTextContent("tiger");
    proxyElt.appendChild(password);

    Element nonProxyHosts = doc.createElement("nonProxyHosts");
    nonProxyHosts.setTextContent("");
    proxyElt.appendChild(nonProxyHosts);

    proxiesElt.appendChild(proxyElt);
    root.appendChild(proxiesElt);

    // Write the modified XML to the output file
    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(outputSettings.toFile());
    transformer.transform(source, result);
  }

  private static void waitForProxyToBeUpAndRunning(int port) throws InterruptedException {
    for (int retryCount = 0; retryCount < 100; retryCount++) {
      try (Socket ignored = new Socket("localhost", port)) {
        // Proxy is up
        return;
      } catch (IOException e) {
        Thread.sleep(50);
      }
    }
    throw new RuntimeException("Proxy server did not start within the expected time.");
  }
}
