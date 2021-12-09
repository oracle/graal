/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
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
    static final String JDK_COMMIT = "https://raw.githubusercontent.com/openjdk/jdk/";

    static final String STUB_PORT_CLASS_NAME = "org.graalvm.compiler.lir.StubPort";
    static final String SYNC_CHECK_ENV_VAR = "HOTSPOT_PORT_SYNC_CHECK";
    static final String HTTPS_PROXY_ENV_VAR = "HTTPS_PROXY";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(STUB_PORT_CLASS_NAME);
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (Boolean.valueOf(System.getenv(SYNC_CHECK_ENV_VAR))) {
            if (!roundEnv.processingOver()) {
                try {
                    // Set https.protocols explicitly to avoid handshake failure
                    System.setProperty("https.protocols", "TLSv1.2");
                    MessageDigest md = MessageDigest.getInstance("SHA-1");
                    TypeElement tStubPort = getTypeElement(STUB_PORT_CLASS_NAME);

                    for (Element element : roundEnv.getElementsAnnotatedWith(tStubPort)) {
                        AnnotationMirror annotationMirror = getAnnotation(element, tStubPort.asType());

                        String path = getAnnotationValue(annotationMirror, "path", String.class);
                        int lineStart = getAnnotationValue(annotationMirror, "lineStart", Integer.class);
                        int lineEnd = getAnnotationValue(annotationMirror, "lineEnd", Integer.class);
                        String commit = getAnnotationValue(annotationMirror, "commit", String.class);
                        String sha1 = getAnnotationValue(annotationMirror, "sha1", String.class);

                        Proxy proxy = Proxy.NO_PROXY;
                        String proxyEnv = System.getenv(HTTPS_PROXY_ENV_VAR);

                        if (proxyEnv != null) {
                            URI proxyURI = new URI(System.getenv(HTTPS_PROXY_ENV_VAR));
                            proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyURI.getHost(), proxyURI.getPort()));
                        }

                        String url = JDK_LATEST + path;
                        URLConnection connection = new URL(url).openConnection(proxy);
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                            // Note that in.lines() discards the line separator and the digest will
                            // be different from hashing the whole file.
                            in.lines().skip(lineStart).limit(lineEnd - lineStart).map(String::getBytes).forEach(md::update);
                        }
                        String digest = String.format("%040x", new BigInteger(1, md.digest()));
                        if (!sha1.equals(digest)) {
                            String oldUrl = JDK_COMMIT + commit + '/' + path;
                            env().getMessager().printMessage(Diagnostic.Kind.ERROR,
                                            String.format("Sha1 digest of %s[%d:%d] does not match: expected %s but was %s. See diff of %s and %s.",
                                                            path, lineStart, lineEnd, sha1, digest, oldUrl, url));
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
