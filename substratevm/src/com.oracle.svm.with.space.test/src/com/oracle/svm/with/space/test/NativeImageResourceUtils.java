/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.with.space.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.junit.Assert;

public class NativeImageResourceUtils {

    public static final String ROOT_DIRECTORY = "/";
    public static final String RESOURCE_DIR = "/resources";
    public static final String RESOURCE_FILE_IN_JAR_WITH_SPACE = RESOURCE_DIR + "/resource-in-jar-with-space.txt";

    // Register resources.
    public static final class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            /* Verify the input JAR before its resources are embedded in the native executable. */
            URL resource = access.getApplicationClassLoader().getResource(RESOURCE_FILE_IN_JAR_WITH_SPACE.substring(1));
            assertNotNull("Test resource is missing from the image-build class path", resource);
            assertEquals("Test resource must come from a JAR: " + resource, "jar", resource.getProtocol());
            try {
                JarURLConnection connection = (JarURLConnection) resource.openConnection();
                Path jarPath = Path.of(connection.getJarFileURL().toURI());
                assertTrue("Test resource JAR filename must contain a space: " + jarPath, jarPath.getFileName().toString().contains(" "));
            } catch (IOException | URISyntaxException e) {
                throw new AssertionError("Cannot determine the test resource JAR", e);
            }

            // Remove leading / for the resource patterns
            Module resourceModule = TestFeature.class.getModule();
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_DIR.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_FILE_IN_JAR_WITH_SPACE.substring(1));
        }
    }

    public static URL resourceNameToURL(String resourceName, boolean failIfNotExists) {
        URL resource = NativeImageResourceUtils.class.getResource(resourceName);
        Assert.assertFalse("Resource " + resourceName + " is not found!", resource == null && failIfNotExists);
        return resource;
    }

    public static URI resourceNameToURI(String resourceName, boolean failIfNotExists) {
        try {
            URL url = resourceNameToURL(resourceName, failIfNotExists);
            return url != null ? url.toURI() : null;
        } catch (URISyntaxException e) {
            Assert.fail("Bad URI syntax!");
        }
        return null;
    }

    public static Path resourceNameToPath(String resourceName, boolean failIfNotExists) {
        URI uri = resourceNameToURI(resourceName, failIfNotExists);
        return uri != null ? Paths.get(uri) : null;
    }
}
