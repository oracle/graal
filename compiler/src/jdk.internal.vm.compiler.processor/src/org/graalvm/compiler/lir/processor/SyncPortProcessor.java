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

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processor for the {@code org.graalvm.compiler.lir.SyncPort} annotation. It verifies whether the
 * digest of the latest source code from the OpenJDK repository matches the one specified in the
 * {@code SyncPort}.
 */
public class SyncPortProcessor extends AbstractProcessor {

    // Enables digest verification. E.g., HOTSPOT_PORT_SYNC_CHECK=true mx build
    static final String SYNC_CHECK_ENV_VAR = "HOTSPOT_PORT_SYNC_CHECK";
    // Dumps the code snippets to current directory.
    static final String SYNC_DUMP_ENV_VAR = "HOTSPOT_PORT_SYNC_DUMP";
    // Allows comparing against local files. E.g.,
    // HOTSPOT_PORT_SYNC_CHECK=true HOTSPOT_PORT_SYNC_OVERWRITE="file:///PATH/TO/JDK" mx build
    static final String SYNC_OVERWRITE_ENV_VAR = "HOTSPOT_PORT_SYNC_OVERWRITE";

    static final String JDK_LATEST = "https://raw.githubusercontent.com/openjdk/jdk/master/";
    static final String JDK_LATEST_INFO = "https://api.github.com/repos/openjdk/jdk/git/matching-refs/heads/master";

    static final String SYNC_PORT_CLASS_NAME = "org.graalvm.compiler.lir.SyncPort";
    static final String SYNC_PORTS_CLASS_NAME = "org.graalvm.compiler.lir.SyncPorts";

    static final Pattern URL_PATTERN = Pattern.compile("^https://github.com/openjdk/jdk/blob/(?<commit>[0-9a-fA-F]{40})/(?<path>[-_./A-Za-z0-9]+)#L(?<lineStart>[0-9]+)-L(?<lineEnd>[0-9]+)$");

    static final int SEARCH_RANGE = 200;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(SYNC_PORT_CLASS_NAME, SYNC_PORTS_CLASS_NAME);
    }

    private void compareDigest(MessageDigest md, AnnotationMirror annotationMirror, Element element, Proxy proxy) throws IOException, URISyntaxException {
        String from = getAnnotationValue(annotationMirror, "from", String.class);
        String sha1 = getAnnotationValue(annotationMirror, "sha1", String.class);
        String ignore = getAnnotationValue(annotationMirror, "ignore", String.class);
        Diagnostic.Kind kind = "".equals(ignore) ? ERROR : NOTE;

        Matcher matcher = URL_PATTERN.matcher(from);
        if (!matcher.matches()) {
            env().getMessager().printMessage(ERROR, String.format("Invalid URL: %s", from));
            return;
        }

        String commit = matcher.group("commit");
        String path = matcher.group("path");
        int lineStart = Integer.parseInt(matcher.group("lineStart"));
        int lineEnd = Integer.parseInt(matcher.group("lineEnd"));

        try {
            String overwriteURL = System.getenv(SYNC_OVERWRITE_ENV_VAR);
            boolean isURLOverwritten = overwriteURL != null && !"".equals(overwriteURL);

            String urlPrefix = isURLOverwritten ? overwriteURL : JDK_LATEST;
            String url = urlPrefix + path;
            String sha1Latest = digest(proxy, md, url, lineStart - 1, lineEnd);

            if (sha1Latest.equals(sha1)) {
                return;
            }

            String extraMessage = "";
            if (!isURLOverwritten) {
                String urlOld = String.format("https://raw.githubusercontent.com/openjdk/jdk/%s/%s", commit, path);
                String sha1Old = digest(proxy, md, urlOld, lineStart - 1, lineEnd);

                if (sha1.equals(sha1Old)) {
                    String latestCommit = getLatestCommit(proxy);
                    int idx = find(proxy, urlOld, url, lineStart - 1, lineEnd, SEARCH_RANGE);
                    if (idx != -1) {
                        int idxInclusive = idx + 1;
                        kind = NOTE;
                        extraMessage = String.format("""
                                         The original code snippet is shifted. Update with:
                                        @SyncPort(from = "https://github.com/openjdk/jdk/blob/%s/%s#L%d-L%d",
                                                  sha1 = "%s")
                                        """,
                                        latestCommit,
                                        path,
                                        idxInclusive,
                                        idxInclusive + (lineEnd - lineStart),
                                        sha1);
                    } else {
                        extraMessage = String.format("""
                                         See also:
                                        https://github.com/openjdk/jdk/compare/%s...%s
                                        https://github.com/openjdk/jdk/commits/%s/%s
                                        """,
                                        commit,
                                        latestCommit,
                                        latestCommit,
                                        path);
                        if (Boolean.parseBoolean(System.getenv(SYNC_DUMP_ENV_VAR))) {
                            dump(proxy, urlOld, lineStart - 1, lineEnd, element + ".old");
                            dump(proxy, url, lineStart - 1, lineEnd, element + ".new");
                        }
                    }
                } else {
                    extraMessage = String.format("""
                                     New SyncPort? Then:
                                    @SyncPort(from = "https://github.com/openjdk/jdk/blob/%s/%s#L%d-L%d",
                                              sha1 = "%s")
                                    """,
                                    getLatestCommit(proxy),
                                    path,
                                    lineStart,
                                    lineEnd,
                                    sha1Latest);
                }
            }
            env().getMessager().printMessage(kind,
                            String.format("Sha1 digest of %s (ported by %s) does not match %s%s#L%d-L%d : expected %s but was %s.%s",
                                            from,
                                            toString(element),
                                            urlPrefix,
                                            path,
                                            lineStart,
                                            lineEnd,
                                            sha1,
                                            sha1Latest,
                                            extraMessage));
        } catch (FileNotFoundException e) {
            env().getMessager().printMessage(kind,
                            String.format("Sha1 digest of %s (ported by %s) does not match : File not found in the latest commit.",
                                            from, toString(element)));
        }
    }

    private static String toString(Element element) {
        return switch (element.getKind()) {
            case CONSTRUCTOR, METHOD -> element.getEnclosingElement().toString();
            default -> element.toString();
        };
    }

    private static InputStream toInputStream(Proxy proxy, String url) throws IOException, URISyntaxException {
        URI uri = new URI(url);
        if (uri.getScheme().equalsIgnoreCase("file")) {
            return new FileInputStream(new File(uri));
        }

        URLConnection connection = new URI(url).toURL().openConnection(proxy);
        return connection.getInputStream();
    }

    private static String digest(Proxy proxy, MessageDigest md, String url, int lineStartExclusive, int lineEnd) throws IOException, URISyntaxException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(toInputStream(proxy, url)))) {
            // Note that in.lines() discards the line separator and the digest
            // will be different from hashing the whole file.
            in.lines().skip(lineStartExclusive).limit(lineEnd - lineStartExclusive).map(String::getBytes).forEach(md::update);
        }
        return String.format("%040x", new BigInteger(1, md.digest()));
    }

    private static int find(Proxy proxy, String oldUrl, String newUrl, int lineStartExclusive, int lineEnd, int searchRange) throws IOException, URISyntaxException {
        URLConnection oldUrlConnection = new URI(oldUrl).toURL().openConnection(proxy);
        URLConnection newUrlConnection = new URI(newUrl).toURL().openConnection(proxy);

        try (BufferedReader oldUrlIn = new BufferedReader(new InputStreamReader(oldUrlConnection.getInputStream()));
                        BufferedReader newUrlIn = new BufferedReader(new InputStreamReader(newUrlConnection.getInputStream()))) {
            String oldSnippet = oldUrlIn.lines().skip(lineStartExclusive).limit(lineEnd - lineStartExclusive).collect(Collectors.joining("\n"));
            int newLineStartExclusive = Math.max(0, lineStartExclusive - searchRange);
            int newLineEnd = lineEnd + searchRange;
            String newFullFile = newUrlIn.lines().skip(newLineStartExclusive).limit(newLineEnd - newLineStartExclusive).collect(Collectors.joining("\n"));
            int idx = newFullFile.indexOf(oldSnippet);
            if (idx != -1) {
                return newLineStartExclusive + (int) newFullFile.substring(0, idx).lines().count();
            }
        }
        return -1;
    }

    private static void dump(Proxy proxy, String url, int lineStartExclusive, int lineEnd, String fileName) throws IOException, URISyntaxException {
        URLConnection connection = new URI(url).toURL().openConnection(proxy);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String content = in.lines().skip(lineStartExclusive).limit(lineEnd - lineStartExclusive).collect(Collectors.joining("\n"));
            try (PrintWriter out = new PrintWriter(fileName + ".tmp")) {
                out.print(content);
                out.print('\n');
            }
        }
    }

    private String cachedLatestCommit = null;

    private String getLatestCommit(Proxy proxy) throws IOException, URISyntaxException {
        if (cachedLatestCommit == null) {
            String result = null;

            URLConnection connection = new URI(JDK_LATEST_INFO).toURL().openConnection(proxy);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String result1 = in.lines().collect(Collectors.joining());
                int idx = result1.indexOf("commits/");
                if (idx != -1) {
                    result = result1.substring(idx + 8, idx + 48);
                }
            }
            if (result == null) {
                result = "UNKNOWN";
            }
            synchronized (this) {
                if (cachedLatestCommit == null) {
                    cachedLatestCommit = result;
                }
            }
        }
        return cachedLatestCommit;
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (Boolean.parseBoolean(System.getenv(SYNC_CHECK_ENV_VAR))) {
            if (!roundEnv.processingOver()) {
                try {
                    // Set https.protocols explicitly to avoid handshake failure
                    System.setProperty("https.protocols", "TLSv1.2");
                    TypeElement tSyncPort = getTypeElement(SYNC_PORT_CLASS_NAME);
                    MessageDigest md = MessageDigest.getInstance("SHA-1");

                    Proxy proxy = Proxy.NO_PROXY;
                    String proxyEnv = System.getenv("HTTPS_PROXY");

                    if (proxyEnv != null && !"".equals(proxyEnv)) {
                        URI proxyURI = new URI(proxyEnv);
                        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyURI.getHost(), proxyURI.getPort()));
                    }

                    for (Element element : roundEnv.getElementsAnnotatedWith(tSyncPort)) {
                        compareDigest(md, getAnnotation(element, tSyncPort.asType()), element, proxy);
                    }

                    TypeElement tSyncPorts = getTypeElement(SYNC_PORTS_CLASS_NAME);

                    for (Element element : roundEnv.getElementsAnnotatedWith(tSyncPorts)) {
                        AnnotationMirror syncPorts = getAnnotation(element, tSyncPorts.asType());
                        for (AnnotationMirror syncPort : getAnnotationValueList(syncPorts, "value", AnnotationMirror.class)) {
                            compareDigest(md, syncPort, element, proxy);
                        }
                    }
                } catch (NoSuchAlgorithmException | IOException | URISyntaxException e) {
                    env().getMessager().printMessage(ERROR, e.toString());
                }
            }
        }
        return false;
    }
}
