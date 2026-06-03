/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.processor;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * Processor for the {@code jdk.graal.compiler.lir.SyncPort} annotation. It verifies whether the
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
    // Allows generating a file of bash commands to update changed line numbers. E.g.:
    // HOTSPOT_PORT_SYNC_CHECK=true HOTSPOT_PORT_SYNC_SHIFT_UPDATE_CMD_FILE="update.sh" mx build &&
    // bash update.sh
    static final String SYNC_SHIFT_UPDATE_COMMAND_DUMP_FILE_ENV_VAR = "HOTSPOT_PORT_SYNC_SHIFT_UPDATE_CMD_FILE";
    // Allows searching code in the range [BEGIN-$HOTSPOT_PORT_SYNC_SEARCH_RANGE,
    // END+HOTSPOT_PORT_SYNC_SEARCH_RANGE].
    static final String SYNC_SEARCH_RANGE_VAR = "HOTSPOT_PORT_SYNC_SEARCH_RANGE";

    static final String JDK_LATEST_BRANCH = "master";
    static final String JDK_LATEST = "https://raw.githubusercontent.com/openjdk/%s/" + JDK_LATEST_BRANCH + "/";
    static final String JDK_LATEST_INFO = "https://api.github.com/repos/openjdk/%s/git/matching-refs/heads/" + JDK_LATEST_BRANCH;

    static final String SYNC_PORT_CLASS_NAME = "jdk.graal.compiler.lir.SyncPort";
    static final String SYNC_PORTS_CLASS_NAME = "jdk.graal.compiler.lir.SyncPorts";

    static final Pattern URL_PATTERN = Pattern.compile(
                    "^https://github.com/(?<user>[^/]+)/(?<repo>[^/]+)/blob/(?<commit>[0-9a-fA-F]{40})/(?<path>[-_./A-Za-z0-9]+)#L(?<lineStart>[0-9]+)-L(?<lineEnd>[0-9]+)$");
    static final Pattern URL_RAW_PATTERN = Pattern.compile("^https://raw.githubusercontent.com/(?<user>[^/]+)/(?<repo>[^/]+)/(?<commit>[0-9a-fA-F]{40})/$");
    static final Pattern LATEST_COMMIT_PATTERN = Pattern.compile("\"sha\"\\s*:\\s*\"(?<commit>[0-9a-fA-F]{40})\"");

    static final int DEFAULT_SEARCH_RANGE = 200;
    static final int URL_OPEN_ATTEMPTS = 3;
    static final long URL_OPEN_RETRY_DELAY_MILLIS = 2_000;
    static final String UNKNOWN_COMMIT = "UNKNOWN";

    private static final Map<String, String> latestCommitCache = new LinkedHashMap<>();

    private final boolean isEnabled;
    private final boolean shouldDump;
    private final String dumpUpdateCommandsEnvVar;
    private final String overwriteURL;
    private final int searchRange;

    public SyncPortProcessor() {
        this.isEnabled = Boolean.parseBoolean(System.getenv(SYNC_CHECK_ENV_VAR));
        this.shouldDump = Boolean.parseBoolean(System.getenv(SYNC_DUMP_ENV_VAR));
        this.dumpUpdateCommandsEnvVar = System.getenv(SYNC_SHIFT_UPDATE_COMMAND_DUMP_FILE_ENV_VAR);
        this.overwriteURL = System.getenv(SYNC_OVERWRITE_ENV_VAR);

        int tempSearchRange = DEFAULT_SEARCH_RANGE;
        try {
            tempSearchRange = Integer.parseInt(System.getenv(SYNC_SEARCH_RANGE_VAR));
        } catch (NumberFormatException e) {
            // SYNC_SEARCH_RANGE_VAR not set or illegal
        }
        this.searchRange = tempSearchRange;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(SYNC_PORT_CLASS_NAME, SYNC_PORTS_CLASS_NAME)));
    }

    private void compareDigest(MessageDigest md, AnnotationMirror annotationMirror, Element element, Proxy proxy) throws IOException, URISyntaxException {
        String from = getAnnotationValue(annotationMirror, "from", String.class);
        String sha1 = getAnnotationValue(annotationMirror, "sha1", String.class);
        String ignore = getAnnotationValue(annotationMirror, "ignore", String.class);
        Diagnostic.Kind kind = "".equals(ignore) ? ERROR : NOTE;

        Matcher matcher = URL_PATTERN.matcher(from);
        if (!matcher.matches()) {
            printMessage(ERROR, String.format("Invalid URL: %s", from), element, annotationMirror);
            return;
        }

        String user = matcher.group("user");
        String repo = matcher.group("repo");
        String commit = matcher.group("commit");
        String path = matcher.group("path");
        int lineStart = Integer.parseInt(matcher.group("lineStart"));
        int lineEnd = Integer.parseInt(matcher.group("lineEnd"));

        PrintWriter dumpUpdateCommands;
        if (dumpUpdateCommandsEnvVar != null) {
            dumpUpdateCommands = new PrintWriter(new FileOutputStream(dumpUpdateCommandsEnvVar, true));
        } else {
            dumpUpdateCommands = null;
        }
        try (dumpUpdateCommands) {
            boolean isURLOverwritten = overwriteURL != null && !"".equals(overwriteURL);

            String urlPrefix = isURLOverwritten ? overwriteURL : String.format(JDK_LATEST, repo);
            String url = urlPrefix + path;
            String latestCommit = isURLOverwritten ? latestCommitFromOverwrite(urlPrefix) : null;
            String sha1Latest = digest(proxy, md, url, lineStart - 1, lineEnd);

            if (sha1Latest.equals(sha1)) {
                return;
            }

            String extraMessage = "";

            String urlOld = String.format("https://raw.githubusercontent.com/%s/%s/%s/%s", user, repo, commit, path);
            String sha1Old = digest(proxy, md, urlOld, lineStart - 1, lineEnd);

            if (sha1.equals(sha1Old)) {
                int idx = find(proxy, urlOld, url, lineStart - 1, lineEnd, searchRange);
                if (idx != -1) {
                    int idxInclusive = idx + 1;
                    kind = NOTE;
                    latestCommit = latestCommit == null ? getLatestCommit(proxy, repo) : latestCommit;
                    if (!isKnown(latestCommit)) {
                        extraMessage = " The original code snippet is shifted." + latestCommitResolutionMessage();
                    } else {
                        String urlFormat = "https://github.com/%s/%s/blob/%s/%s#L%d-L%d";
                        String newUrl = String.format(urlFormat, user, repo, latestCommit, path, idxInclusive, idxInclusive + (lineEnd - lineStart));
                        extraMessage = String.format("""
                                         The original code snippet is shifted. Update with:
                                        @SyncPort(from = "%s",
                                                  sha1 = "%s")
                                        """,
                                        newUrl,
                                        sha1);
                        if (dumpUpdateCommands != null) {
                            String oldUrl = String.format(urlFormat, user, repo, commit, path, lineStart, lineEnd);
                            assert !oldUrl.contains("+");
                            assert !newUrl.contains("+");
                            // Parfait_ALLOW xss-injection
                            dumpUpdateCommands.printf("sed -i s+%s+%s+g $(git grep --files-with-matches %s)%n", oldUrl, newUrl, sha1);
                        }
                    }
                } else {
                    String latestReference = isURLOverwritten ? latestReference(latestCommit) : JDK_LATEST_BRANCH;
                    extraMessage = String.format("""
                                     See also:
                                    https://github.com/%s/%s/compare/%s...%s
                                    https://github.com/%s/%s/commits/%s/%s
                                    """,
                                    user,
                                    repo,
                                    commit,
                                    latestReference,
                                    user,
                                    repo,
                                    latestReference,
                                    path);
                    if (shouldDump) {
                        dump(proxy, urlOld, lineStart - 1, lineEnd, "old", element.getSimpleName().toString());
                        dump(proxy, url, lineStart - 1, lineEnd, "new", element.getSimpleName().toString());
                    }
                }
            } else {
                latestCommit = latestCommit == null ? getLatestCommit(proxy, repo) : latestCommit;
                if (isKnown(latestCommit)) {
                    extraMessage = String.format("""
                                     New SyncPort? Then:
                                    @SyncPort(from = "https://github.com/%s/%s/blob/%s/%s#L%d-L%d",
                                              sha1 = "%s")
                                    """,
                                    user,
                                    repo,
                                    latestCommit,
                                    path,
                                    lineStart,
                                    lineEnd,
                                    sha1Latest);
                } else {
                    extraMessage = String.format("""
                                     New SyncPort? The latest source has sha1 "%s".
                                    Resolve the latest openjdk/%s branch head commit before updating the @SyncPort URL.%s
                                    """,
                                    sha1Latest,
                                    repo,
                                    latestCommitResolutionMessage());
                }
                if (dumpUpdateCommands != null) {
                    dumpUpdateCommands.printf("sed -i s+%s+%s+g $(git grep --files-with-matches %s)%n", sha1, sha1Latest, sha1);
                }
            }
            printMessage(kind,
                            String.format("Sha1 digest of %s (ported by %s) does not match https://github.com/%s/%s/blob%s/%s#L%d-L%d : expected %s but was %s.%s",
                                            from,
                                            toString(element),
                                            user,
                                            repo,
                                            isURLOverwritten ? "/" + latestReference(latestCommit) : "/master",
                                            path,
                                            lineStart,
                                            lineEnd,
                                            sha1,
                                            sha1Latest,
                                            extraMessage),
                            element,
                            annotationMirror);
        } catch (FileNotFoundException e) {
            printMessage(kind,
                            String.format("Sha1 digest of %s (ported by %s) does not match : File not found in the latest commit.",
                                            from, toString(element)),
                            element,
                            annotationMirror);
        }
    }

    private void printMessage(Diagnostic.Kind kind, String message, Element element, AnnotationMirror annotationMirror) {
        if (element == null) {
            env().getMessager().printMessage(kind, message);
        } else if (annotationMirror == null) {
            env().getMessager().printMessage(kind, message, element);
        } else {
            env().getMessager().printMessage(kind, message, element, annotationMirror);
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

        IOException lastException = null;
        for (int attempt = 1; attempt <= URL_OPEN_ATTEMPTS; attempt++) {
            try {
                URLConnection connection = uri.toURL().openConnection(proxy);
                return connection.getInputStream();
            } catch (FileNotFoundException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                if (attempt == URL_OPEN_ATTEMPTS) {
                    throw e;
                }
                waitBeforeRetry(e);
            }
        }
        throw lastException;
    }

    private static void waitBeforeRetry(IOException original) throws IOException {
        try {
            Thread.sleep(URL_OPEN_RETRY_DELAY_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while retrying URL connection", e);
            interrupted.addSuppressed(original);
            throw interrupted;
        }
    }

    private static String digest(Proxy proxy, MessageDigest md, String url, int lineStartExclusive, int lineEnd) throws IOException, URISyntaxException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(toInputStream(proxy, url)))) {
            // Note that in.lines() discards the line separator and the digest
            // will be different from hashing the whole file.
            in.lines().skip(lineStartExclusive).limit(lineEnd - lineStartExclusive).map(String::getBytes).forEach(md::update);
        }
        // Parfait_ALLOW missing-crypto-step
        return String.format("%040x", new BigInteger(1, md.digest()));
    }

    private static int find(Proxy proxy, String oldUrl, String newUrl, int lineStartExclusive, int lineEnd, int searchRange) throws IOException, URISyntaxException {
        try (BufferedReader oldUrlIn = new BufferedReader(new InputStreamReader(toInputStream(proxy, oldUrl)));
                        BufferedReader newUrlIn = new BufferedReader(new InputStreamReader(toInputStream(proxy, newUrl)))) {
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

    private static void dump(Proxy proxy, String url, int lineStartExclusive, int lineEnd, String dirName, String fileName) throws IOException, URISyntaxException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(toInputStream(proxy, url)))) {
            String content = in.lines().skip(lineStartExclusive).limit(lineEnd - lineStartExclusive).collect(Collectors.joining("\n"));
            File directory = new File(dirName);
            if (!directory.exists()) {
                directory.mkdir();
            }

            try (PrintWriter out = new PrintWriter(dirName + "/" + fileName + ".tmp")) {
                // Parfait_ALLOW xss-injection
                out.print(content);
                out.print('\n');
            }
        }
    }

    private static String latestCommitFromOverwrite(String urlPrefix) {
        Matcher rawMatcher = URL_RAW_PATTERN.matcher(urlPrefix);
        if (rawMatcher.matches()) {
            return rawMatcher.group("commit");
        }
        return UNKNOWN_COMMIT;
    }

    private static String latestCommitResolutionMessage() {
        return " Could not resolve the latest OpenJDK branch head commit.";
    }

    private static boolean isKnown(String latestCommit) {
        return latestCommit != null && !UNKNOWN_COMMIT.equals(latestCommit);
    }

    private static String latestReference(String latestCommit) {
        return isKnown(latestCommit) ? latestCommit : JDK_LATEST_BRANCH;
    }

    private static String getLatestCommit(Proxy proxy, String repo) {
        synchronized (latestCommitCache) {
            String latestCommit = latestCommitCache.get(repo);
            if (latestCommit != null) {
                return latestCommit;
            }
            latestCommit = readLatestCommit(proxy, repo);
            latestCommitCache.put(repo, latestCommit);
            return latestCommit;
        }
    }

    private static String readLatestCommit(Proxy proxy, String repo) {
        String latestInfoURL = String.format(JDK_LATEST_INFO, repo);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(toInputStream(proxy, latestInfoURL)))) {
            String response = in.lines().collect(Collectors.joining());
            Matcher matcher = LATEST_COMMIT_PATTERN.matcher(response);
            if (matcher.find()) {
                return matcher.group("commit");
            }
            return UNKNOWN_COMMIT;
        } catch (IOException | URISyntaxException e) {
            return UNKNOWN_COMMIT;
        }
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Element currentElement = null;
        AnnotationMirror currentAnnotationMirror = null;
        if (isEnabled) {
            if (!roundEnv.processingOver()) {
                try {
                    // Set https.protocols explicitly to avoid handshake failure
                    System.setProperty("https.protocols", "TLSv1.2");
                    TypeElement tSyncPort = getTypeElement(SYNC_PORT_CLASS_NAME);
                    // Parfait_ALLOW weak-hash
                    MessageDigest md = MessageDigest.getInstance("SHA-1");

                    Proxy proxy = Proxy.NO_PROXY;
                    String proxyEnv = System.getenv("HTTPS_PROXY");

                    if (proxyEnv != null && !"".equals(proxyEnv)) {
                        URI proxyURI = new URI(proxyEnv);
                        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyURI.getHost(), proxyURI.getPort()));
                    }

                    for (Element element : roundEnv.getElementsAnnotatedWith(tSyncPort)) {
                        currentElement = element;
                        currentAnnotationMirror = getAnnotation(element, tSyncPort.asType());
                        compareDigest(md, currentAnnotationMirror, element, proxy);
                    }

                    TypeElement tSyncPorts = getTypeElement(SYNC_PORTS_CLASS_NAME);

                    for (Element element : roundEnv.getElementsAnnotatedWith(tSyncPorts)) {
                        AnnotationMirror syncPorts = getAnnotation(element, tSyncPorts.asType());
                        for (AnnotationMirror syncPort : getAnnotationValueList(syncPorts, "value", AnnotationMirror.class)) {
                            currentElement = element;
                            currentAnnotationMirror = syncPort;
                            compareDigest(md, syncPort, element, proxy);
                        }
                    }
                } catch (NoSuchAlgorithmException | IOException | URISyntaxException e) {
                    printMessage(ERROR, "Error caught in %s: %s".formatted(getClass().getName(), e), currentElement, currentAnnotationMirror);
                }
            }
        }
        return true;
    }
}
