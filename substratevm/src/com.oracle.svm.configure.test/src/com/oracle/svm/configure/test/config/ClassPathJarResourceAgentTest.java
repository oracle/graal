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
 * Looking up resources from a built-in class path JAR or a JDK system module makes the JDK create a
 * {@code jar:} or {@code jrt:} resource URL as part of its implementation. For ordinary
 * applications and benchmarks, that URL is only a resource lookup detail: Native Image embeds the
 * selected resources and does not need reflective access to the JDK URL handlers. In contrast, a
 * runtime {@link URLClassLoader} over a JAR still executes the JDK JAR URL path in a native image
 * and therefore remains metadata-relevant. Recording JVM-only handlers makes the image builder
 * treat reflective URL support as application-requested behavior and pulls related classes into
 * images. This test therefore runs against the real agent instead of only the trace processor,
 * because the regression is whether the agent emits the unwanted handler metadata in the first
 * place.
 */
public class ClassPathJarResourceAgentTest {
    private static final String GENERATOR_ENABLED_PROPERTY = ClassPathJarResourceAgentTest.class.getName() + ".generator.enabled";
    private static final String RESOURCE_NAME = "agent-url-protocol-resource.txt";
    private static final String RESOURCE_CONTENTS = "agent resource lookup";
    private static final String BUILT_IN_CLASSPATH_JAR_RESOURCE_NAME = "org/junit/Test.class";
    private static final String JDK_MODULE_RESOURCE_NAME = "java/lang/Object.class";

    @Test
    public void accessBuiltInClassPathJarResource() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));
        accessBuiltInClassPathJarResourceInternal(); // FS-001-native-image-semantics.3.2
    }

    @Test
    public void accessBuiltInClassPathJarResourceThenExplicitJarURL() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        accessBuiltInClassPathJarResourceInternal();
        accessExplicitJarURL(); // FS-001-native-image-semantics.3.2
    }

    @Test
    public void accessURLClassLoaderJarResource() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));
        accessURLClassLoaderJarResourceInternal(); // FS-001-native-image-semantics.3.2
    }

    @Test
    public void accessJDKModuleResource() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));
        accessJDKModuleResourceInternal();
    }

    @Test
    public void accessJDKModuleResourceThenExplicitJrtURL() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        accessJDKModuleResourceInternal();
        accessExplicitJrtURL();
    }

    private static void accessBuiltInClassPathJarResourceInternal() throws Exception {
        URL resource = ClassLoader.getSystemResource(BUILT_IN_CLASSPATH_JAR_RESOURCE_NAME);
        Assert.assertNotNull(resource);
        Assert.assertEquals("Built-in classpath resource URL: " + resource, "jar", resource.getProtocol());
        try (InputStream input = resource.openStream()) {
            Assert.assertTrue(input.read() >= 0);
        }
        Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
    }

    private static void accessURLClassLoaderJarResourceInternal() throws Exception {
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

    private static void accessJDKModuleResourceInternal() throws Exception {
        URL resource = ClassLoader.getSystemResource(JDK_MODULE_RESOURCE_NAME);
        Assert.assertNotNull(resource);
        Assert.assertEquals("jrt", resource.getProtocol());
        try (InputStream input = resource.openStream()) {
            Assert.assertTrue(input.read() >= 0);
        }
        Assert.assertSame(ReflectiveProbe.class, Class.forName(ReflectiveProbe.class.getName()));
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

    private static void accessExplicitJrtURL() throws Exception {
        URL resource = URI.create("jrt:/java.base/" + JDK_MODULE_RESOURCE_NAME).toURL();
        Assert.assertEquals("jrt", resource.getProtocol());
        try (InputStream input = resource.openStream()) {
            Assert.assertTrue(input.read() >= 0);
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
