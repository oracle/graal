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

package com.oracle.svm.test;

import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_FILE_2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.internal.loader.BuiltinClassLoader;

@AddExports({"java.base/jdk.internal.loader"})
public class NativeImageRuntimeClassPathResourceTest {

    @Test
    public void runtimeClassPathResource() throws IOException {
        Assume.assumeTrue("runtime classpath resource lookup test requires an explicit runtime java.class.path", Boolean.getBoolean("svm.test.expectRuntimeClassPathResource"));

        String resourceName = "runtime-class-path-resource.txt";
        try (InputStream in = ClassLoader.getSystemResourceAsStream(resourceName)) {
            Assert.assertNotNull("Runtime class path resource " + resourceName + " is not found!", in);
            Assert.assertEquals("runtime class path resource", new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
        }
    }

    @Test
    public void builtinClassLoaderFindsClassPathResources() throws IOException {
        BuiltinClassLoader loader = (BuiltinClassLoader) ClassLoader.getSystemClassLoader();

        Enumeration<URL> resources = loader.findResources(RESOURCE_FILE_2.substring(1));
        URL firstResource = singleResource(RESOURCE_FILE_2, resources);
        try (InputStream in = firstResource.openStream()) {
            Assert.assertEquals("Native image is awesome!", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        Enumeration<URL> loaderResources = ClassLoader.getSystemClassLoader().getResources(RESOURCE_FILE_2.substring(1));
        URL firstLoaderResource = singleResource(RESOURCE_FILE_2, loaderResources);
        try (InputStream in = firstLoaderResource.openStream()) {
            Assert.assertEquals("Native image is awesome!", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        Enumeration<URL> systemResources = ClassLoader.getSystemResources(RESOURCE_FILE_2.substring(1));
        URL firstSystemResource = singleResource(RESOURCE_FILE_2, systemResources);
        try (InputStream in = firstSystemResource.openStream()) {
            Assert.assertEquals("Native image is awesome!", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static URL singleResource(String resourceName, Enumeration<URL> resources) {
        List<URL> urls = Collections.list(resources);
        Assert.assertEquals("Resource " + resourceName + " URLs: " + urls, 1, urls.size());
        return urls.get(0);
    }
}
