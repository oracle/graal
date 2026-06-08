/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.config;

import static org.junit.Assume.assumeTrue;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * Regression fixture for the native-image-agent URL protocol breakpoint.
 *
 * Looking up a resource from a class path JAR makes the JDK create a {@code jar:} resource URL as
 * part of its class path implementation. For ordinary applications and benchmarks, that URL is only
 * a resource lookup detail: Native Image embeds the selected resources and does not need reflective
 * access to the JDK's {@code sun.net.www.protocol.jar.Handler}. Recording that handler makes the
 * image builder treat reflective JAR URL support as application-requested behavior and pulls
 * {@code JarURLConnection}, {@code URLJarFile}, and related classes into images. This test therefore
 * runs against the real agent instead of only the trace processor, because the regression is whether
 * the agent emits the unwanted handler metadata in the first place.
 */
public class ClassPathJarResourceAgentTest {
    private static final String GENERATOR_ENABLED_PROPERTY = ClassPathJarResourceAgentTest.class.getName() + ".generator.enabled";
    private static final String RESOURCE_NAME = "agent-url-protocol-resource.txt";
    private static final String RESOURCE_CONTENTS = "agent resource lookup";

    @Test
    public void accessClassPathJarResource() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));
        accessClassPathJarResourceInternal();
    }

    @Test
    public void accessClassPathJarResourceThenExplicitJarURL() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        accessClassPathJarResourceInternal();
        accessExplicitJarURL();
    }

    private static void accessClassPathJarResourceInternal() throws Exception {
        Path jarFile = Files.createTempFile("native-image-agent-classpath-resource", ".jar");
        try {
            writeProbeJar(jarFile);
            try (URLClassLoader classLoader = new URLClassLoader(new URL[]{jarFile.toUri().toURL()}, null)) {
                URL resource = classLoader.getResource(RESOURCE_NAME);
                Assert.assertNotNull(resource);
                Assert.assertEquals("jar", resource.getProtocol());
                URLConnection connection = resource.openConnection();
                connection.setUseCaches(false);
                try (InputStream input = connection.getInputStream()) {
                    Assert.assertEquals(RESOURCE_CONTENTS, new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    private static void accessExplicitJarURL() throws Exception {
        Path jarFile = Files.createTempFile("native-image-agent-explicit-jar-url", ".jar");
        try {
            writeProbeJar(jarFile);
            URL resource = URI.create("jar:" + jarFile.toUri() + "!/" + RESOURCE_NAME).toURL();
            Assert.assertEquals("jar", resource.getProtocol());
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                Assert.assertEquals(RESOURCE_CONTENTS, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    private static void writeProbeJar(Path jarFile) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarFile))) {
            output.putNextEntry(new JarEntry(RESOURCE_NAME));
            output.write(RESOURCE_CONTENTS.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }

    static final class ReflectiveProbe {
    }
}
