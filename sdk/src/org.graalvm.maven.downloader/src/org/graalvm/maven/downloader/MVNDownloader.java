/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.maven.downloader;

import static org.graalvm.maven.downloader.Main.LOGGER;
import static org.graalvm.maven.downloader.OptionProperties.DEFAULT_MAVEN_REPO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class MVNDownloader {

    private final Set<String> downloadedJars = new HashSet<>();
    private final Set<String> existingModules;
    private final Path outputDir;
    private final Path downloadDir;

    private final class DeleteDownloadDir implements Runnable {
        @Override
        public void run() {
            try {
                Files.deleteIfExists(downloadDir);
            } catch (IOException e) {
                // Nothing we can do now
            }
        }
    }

    public MVNDownloader(String outputDir) throws IOException {
        this.outputDir = Paths.get(outputDir);
        this.existingModules = getModuleNamesInDirectory(this.outputDir);
        this.downloadDir = Files.createTempDirectory(MVNDownloader.class.getSimpleName());
        Runtime.getRuntime().addShutdownHook(new Thread(new DeleteDownloadDir()));
    }

    private static Set<String> getModuleNamesInDirectory(Path dir) {
        return ModuleFinder.of(dir).findAll().stream().map(mr -> mr.descriptor().name()).collect(Collectors.toUnmodifiableSet());
    }

    public void downloadDependencies(String repoUrl, String groupId, String artifactId, String version)
                    throws IOException, URISyntaxException, ParserConfigurationException, SAXException, ClassCastException, NoSuchAlgorithmException {
        String artifactName = toMavenPath(groupId, artifactId, version, "pom");
        boolean mvnCentralFallback = !repoUrl.startsWith(DEFAULT_MAVEN_REPO);
        byte[] bytes = downloadMavenFile(repoUrl, artifactName, mvnCentralFallback);

        var builder = createSecurePOMParser();
        var document = builder.parse(new ByteArrayInputStream(bytes));

        // We care only about a very small subset of the POM, and accept even malformed POMs if the
        // required tags are there. Since this is supposed to access an actual repository, real
        // Maven tools wouldn't work if the POMs are really malformed, so we do minimal error
        // checking.

        var projectNode = (Element) document.getElementsByTagName("project").item(0);
        if (projectNode == null) {
            LOGGER.severe(String.format("Malformed pom %s does not have <project> tag", artifactName));
            System.exit(1);
        }
        LOGGER.fine(String.format("loaded model for %s", artifactName));

        var packagingNode = projectNode.getElementsByTagName("packaging").item(0);
        var packaging = packagingNode == null ? "jar" : packagingNode.getTextContent();

        if (!packaging.equals("pom")) {
            artifactName = toMavenPath(groupId, artifactId, version, packaging);
            if (downloadedJars.contains(artifactName)) {
                LOGGER.finer(String.format("skipped already downloaded artifact %s", artifactName));
                return;
            }
            bytes = downloadMavenFile(repoUrl, artifactName, mvnCentralFallback);
            var tmpfile = store(downloadDir, groupId, artifactId, version, bytes, packaging);
            var definedModules = getModuleNamesInDirectory(downloadDir);
            if (definedModules.size() > 1) {
                LOGGER.severe(String.format("Internal error: more than one module in temporary directory %s", downloadDir));
                System.exit(1);
            } else if (definedModules.size() == 1 && existingModules.containsAll(definedModules)) {
                LOGGER.finer(String.format("skipped artifact %s, which defines module %s, because it matches an existing module in the output dir %s",
                                artifactName, definedModules.toArray()[0], outputDir));
                Files.delete(tmpfile);
                // if the module is already there, we assume its dependencies must be too.
                return;
            } else {
                Files.move(tmpfile, outputDir.resolve(tmpfile.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            downloadedJars.add(artifactName);
        }

        var dependenciesNodes = document.getDocumentElement().getElementsByTagName("dependencies");
        for (int i = 0; i < dependenciesNodes.getLength(); i++) {
            var dependenciesNode = (Element) dependenciesNodes.item(i);
            var dependencyNodes = dependenciesNode.getElementsByTagName("dependency");
            for (int j = 0; j < dependencyNodes.getLength(); j++) {
                var dependencyNode = (Element) dependencyNodes.item(j);
                var gidNode = dependencyNode.getElementsByTagName("groupId").item(0);
                if (gidNode == null) {
                    LOGGER.severe(String.format("Malformed pom %s, dependency does not have <groupId> tag", artifactName));
                    System.exit(1);
                }
                var gid = gidNode.getTextContent();
                var aidNode = dependencyNode.getElementsByTagName("artifactId").item(0);
                if (aidNode == null) {
                    LOGGER.severe(String.format("Malformed pom %s, dependency does not have <artifactId> tag", artifactName));
                    System.exit(1);
                }
                var aid = aidNode.getTextContent();
                var versionNode = dependencyNode.getElementsByTagName("version").item(0);
                if (versionNode == null) {
                    LOGGER.severe(String.format("missing version for dependency %s:%s in %s", gid, aid, artifactName));
                    System.exit(1);
                }
                var ver = versionNode.getTextContent();
                var scopeNode = dependencyNode.getElementsByTagName("scope").item(0);
                if (scopeNode != null) {
                    var scope = scopeNode.getTextContent();
                    if ("test".equals(scope) || "provided".equals(scope)) {
                        continue;
                    }
                }
                downloadDependencies(repoUrl, gid, aid, ver);
            }
        }
    }

    /**
     * Creates a {@link DocumentBuilder} that is crafted to be safe while still not disallowing
     * valid Maven POM files.
     */
    private static DocumentBuilder createSecurePOMParser() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        /*
         * Disallow potentially dangerous external entity resolutions. Compatible with Maven because
         * POM files do not need external entities.
         */
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        /*
         * Injecting external XMLs is not allowed by the Maven POM schema, but we can still include
         * the rule to err on the side of safety.
         */
        factory.setXIncludeAware(false);

        /*
         * We don't expect legitimate POMs to be huge, so use recommended defaults for max
         * processing limits.
         */
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

        return factory.newDocumentBuilder();
    }

    private static byte[] downloadMavenFile(String repoUrl, String artefactName, boolean fallback) throws IOException, URISyntaxException {
        String url = repoUrl + artefactName;
        try {
            if (url.startsWith("file:")) {
                File file = new File(new URI(url));
                if (!file.exists()) {
                    throw new IOException(String.format("does not exist %s", url));
                }
            }
            URL u = new URI(url).toURL();
            byte[] bytes = downloadFromServer(u);
            LOGGER.info(String.format("downloaded file %s", url));
            return bytes;
        } catch (IOException ioe) {
            if (fallback) {
                LOGGER.log(Level.WARNING, String.format("could not download maven file from %s, because of: %s. Falling back on %s", url, ioe, DEFAULT_MAVEN_REPO));
                return downloadMavenFile(DEFAULT_MAVEN_REPO, artefactName, false);
            }
            LOGGER.log(Level.SEVERE, String.format("exception while downloading maven file from %s", url), ioe);
            throw ioe;
        } catch (URISyntaxException ex) {
            LOGGER.log(Level.SEVERE, String.format("wrong url", url), ex);
            throw ex;
        }
    }

    private static byte[] downloadFromServer(URL url) throws IOException {
        URLConnection conn = url.openConnection(getProxy());
        int code = HttpURLConnection.HTTP_OK;
        if (conn instanceof HttpURLConnection) {
            code = ((HttpURLConnection) conn).getResponseCode();
        }
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("Skipping download from " + url + " due to response code " + code);
        }
        try {
            try (InputStream is = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int read;
                while ((read = is.read(buf)) != -1) {
                    baos.write(buf, 0, read);
                }
                return baos.toByteArray();
            }
        } catch (IOException ex) {
            throw new IOException("Cannot download: " + url + " due to: " + ex, ex);
        }
    }

    private static Proxy getProxy() {
        String httpProxy = getProxyVar("https_proxy");
        if (httpProxy == null || "".equals(httpProxy.trim())) {
            httpProxy = getProxyVar("http_proxy");
        }
        if (httpProxy == null || "".equals(httpProxy.trim())) {
            LOGGER.info(String.format("using no proxy"));
            return Proxy.NO_PROXY;
        }
        httpProxy = httpProxy.trim();
        int idx = httpProxy.lastIndexOf(":");
        if (idx < 0 || idx > httpProxy.length() - 1) {
            LOGGER.warning(String.format("http_proxy env variable has to be in format host:url, but was '%s'", httpProxy));
            return Proxy.NO_PROXY;
        }
        String host = httpProxy.substring(0, idx);
        int port;
        try {
            port = Integer.parseInt(httpProxy.substring(idx + 1));
        } catch (NumberFormatException e) {
            LOGGER.severe(String.format("can't parse port number in '%s'", httpProxy));
            throw e;
        }
        LOGGER.info(String.format("using proxy '%s:%s'", host, port));
        if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        } else if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        }
        InetSocketAddress address = InetSocketAddress.createUnresolved(host, port);
        return new Proxy(Proxy.Type.HTTP, address);
    }

    private static String getProxyVar(String key) {
        String var = System.getenv(key);
        if (var == null) {
            var = System.getenv(key.toUpperCase(Locale.ROOT));
        }
        return var;
    }

    private static String toMavenPath(String groupId, String artifactId, String version, String extension) {
        return String.format("%s/%s/%s/%s", groupId.replace(".", "/"), artifactId, version, toArtifactFilename(artifactId, version, extension));
    }

    private static String toArtifactFilename(String artifactId, String version, String extension) {
        return String.format("%s-%s.%s", artifactId, version, extension);
    }

    private static Path store(Path dir, String groupId, String artifactId, String version, byte[] bytes, String extension) throws IOException {
        String fileName = String.format("%s-%s", groupId, toArtifactFilename(artifactId, version, extension));
        Path path = dir.resolve(fileName);
        Files.write(path, bytes);
        return path;
    }
}
