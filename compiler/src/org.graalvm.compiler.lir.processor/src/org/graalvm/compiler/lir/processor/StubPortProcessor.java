/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.lir.processor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processor for the {@code org.graalvm.compiler.lir.StubPort} annotation. It verifies whether the
 * digest of the latest source code from the OpenJDK repository matches the one specified in the
 * {@code StubPort}.
 */
public class StubPortProcessor extends AbstractProcessor {

    static final String JDK_LATEST = "https://raw.githubusercontent.com/openjdk/jdk/master/";
    static final String JDK_LATEST_INFO = "https://api.github.com/repos/openjdk/jdk/git/matching-refs/heads/master";
    static final String JDK_COMMIT = "https://raw.githubusercontent.com/openjdk/jdk/";

    static final String STUB_PORT_CLASS_NAME = "org.graalvm.compiler.lir.StubPort";
    static final String STUB_PORTS_CLASS_NAME = "org.graalvm.compiler.lir.StubPorts";
    static final String SYNC_CHECK_ENV_VAR = "HOTSPOT_PORT_SYNC_CHECK";
    static final String HTTPS_PROXY_ENV_VAR = "HTTPS_PROXY";

    static final int SEARCH_RANGE = 100;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(STUB_PORT_CLASS_NAME, STUB_PORTS_CLASS_NAME);
    }

    private void compareDigest(MessageDigest md, AnnotationMirror annotationMirror, Element element, Proxy proxy) throws IOException {
        String path = getAnnotationValue(annotationMirror, "path", String.class);
        int lineStart = getAnnotationValue(annotationMirror, "lineStart", Integer.class);
        int lineEnd = getAnnotationValue(annotationMirror, "lineEnd", Integer.class);
        String commit = getAnnotationValue(annotationMirror, "commit", String.class);
        String sha1 = getAnnotationValue(annotationMirror, "sha1", String.class);

        String urlHumanSuffix = path + "#L" + lineStart + "-L" + lineEnd;
        String url = JDK_LATEST + path;
        String sha1Latest;
        try {
            sha1Latest = digest(proxy, md, url, lineStart, lineEnd);
        } catch (FileNotFoundException e) {
            env().getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("Sha1 digest of https://github.com/openjdk/jdk/blob/%s/%s (ported by %s) does not match : " +
                                            "File not found in the latest commit.",
                                            commit,
                                            urlHumanSuffix,
                                            element.toString()));
            return;
        }

        if (!sha1.equals(sha1Latest)) {
            String urlOld = JDK_COMMIT + commit + '/' + path;
            String sha1Old = digest(proxy, md, urlOld, lineStart, lineEnd);

            String extraMessage = "";

            if (sha1.equals(sha1Old)) {
                int idx = find(proxy, urlOld, url, lineStart, lineEnd);
                if (idx != -1) {
                    extraMessage = String.format("It may be simply shifted. Try:\n@StubPort(path      = \"%s\",\n" +
                                    "lineStart = %d,\n" +
                                    "lineEnd   = %d,\n" +
                                    "commit    = \"%s\",\n" +
                                    "sha1      = \"%s\")\n",
                                    path,
                                    idx,
                                    idx + (lineEnd - lineStart),
                                    fetchLatestCommit(proxy),
                                    sha1);
                }
            } else {
                extraMessage = String.format("New StubPort? Then:\n@StubPort(path      = \"%s\",\n" +
                                "lineStart = %d,\n" +
                                "lineEnd   = %d,\n" +
                                "commit    = \"%s\",\n" +
                                "sha1      = \"%s\")\n",
                                path,
                                lineStart,
                                lineEnd,
                                fetchLatestCommit(proxy),
                                sha1Latest);
            }

            env().getMessager().printMessage(Diagnostic.Kind.ERROR,
                            String.format("Sha1 digest of https://github.com/openjdk/jdk/blob/%s/%s (ported by %s) does not match " +
                                            "https://github.com/openjdk/jdk/blob/master/%s : expected %s but was %s. %s",
                                            commit,
                                            urlHumanSuffix,
                                            element.toString(),
                                            urlHumanSuffix,
                                            sha1,
                                            sha1Latest,
                                            extraMessage));
        }
    }

    @SuppressWarnings("deprecation")
    private static String digest(Proxy proxy, MessageDigest md, String url, int lineStart, int lineEnd) throws IOException {
        URLConnection connection = new URL(url).openConnection(proxy);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            // Note that in.lines() discards the line separator and the digest
            // will be different from hashing the whole file.
            in.lines().skip(lineStart).limit(lineEnd - lineStart).map(String::getBytes).forEach(md::update);
        }
        return String.format("%040x", new BigInteger(1, md.digest()));
    }

    @SuppressWarnings("deprecation")
    private static int find(Proxy proxy, String oldUrl, String newUrl, int lineStart, int lineEnd) throws IOException {
        URLConnection oldUrlConnection = new URL(oldUrl).openConnection(proxy);
        URLConnection newUrlConnection = new URL(newUrl).openConnection(proxy);

        try (BufferedReader oldUrlIn = new BufferedReader(new InputStreamReader(oldUrlConnection.getInputStream()));
                        BufferedReader newUrlIn = new BufferedReader(new InputStreamReader(newUrlConnection.getInputStream()))) {
            String oldSnippet = oldUrlIn.lines().skip(lineStart).limit(lineEnd - lineStart).collect(Collectors.joining("\n"));
            int newLineStart = Math.max(0, lineStart - SEARCH_RANGE);
            int newLineEnd = lineEnd + SEARCH_RANGE;
            String newFullFile = newUrlIn.lines().skip(newLineStart).limit(newLineEnd - newLineStart).collect(Collectors.joining("\n"));
            int idx = newFullFile.indexOf(oldSnippet);
            if (idx != -1) {
                return newLineStart + newFullFile.substring(0, idx).split("\n").length;
            }
        }
        return -1;
    }

    @SuppressWarnings("deprecation")
    private static String fetchLatestCommit(Proxy proxy) throws IOException {
        URLConnection connection = new URL(JDK_LATEST_INFO).openConnection(proxy);

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String result = in.lines().collect(Collectors.joining());
            int idx = result.indexOf("commits/");
            if (idx != -1) {
                return result.substring(idx + 8, idx + 48);
            }
        }
        return "UNKNOWN";
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (Boolean.parseBoolean(System.getenv(SYNC_CHECK_ENV_VAR))) {
            if (!roundEnv.processingOver()) {
                try {
                    // Set https.protocols explicitly to avoid handshake failure
                    System.setProperty("https.protocols", "TLSv1.2");
                    TypeElement tStubPort = getTypeElement(STUB_PORT_CLASS_NAME);
                    MessageDigest md = MessageDigest.getInstance("SHA-1");

                    Proxy proxy = Proxy.NO_PROXY;
                    String proxyEnv = System.getenv(HTTPS_PROXY_ENV_VAR);

                    if (proxyEnv != null) {
                        URI proxyURI = new URI(System.getenv(HTTPS_PROXY_ENV_VAR));
                        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyURI.getHost(), proxyURI.getPort()));
                    }

                    for (Element element : roundEnv.getElementsAnnotatedWith(tStubPort)) {
                        compareDigest(md, getAnnotation(element, tStubPort.asType()), element, proxy);
                    }

                    TypeElement tStubPorts = getTypeElement(STUB_PORTS_CLASS_NAME);

                    for (Element element : roundEnv.getElementsAnnotatedWith(tStubPorts)) {
                        AnnotationMirror stubPorts = getAnnotation(element, tStubPorts.asType());
                        for (AnnotationMirror stubPort : getAnnotationValueList(stubPorts, "value", AnnotationMirror.class)) {
                            compareDigest(md, stubPort, element, proxy);
                        }
                    }
                } catch (NoSuchAlgorithmException | IOException | URISyntaxException e) {
                    env().getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
                }
            }
        }
        return false;
    }
}
