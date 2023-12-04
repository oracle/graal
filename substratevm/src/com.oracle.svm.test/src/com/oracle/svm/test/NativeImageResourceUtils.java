/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.junit.Assert;

public class NativeImageResourceUtils {

    public static final String ROOT_DIRECTORY = "/";
    public static final String RESOURCE_DIR = "/resources";
    public static final String SIMPLE_RESOURCE_DIR = "/simpleDir";
    public static final String RESOURCE_EMPTY_DIR = RESOURCE_DIR + "/empty";
    public static final String RESOURCE_DIR_WITH_SPACE = RESOURCE_DIR + "/dir with space";
    public static final String RESOURCE_FILE_1 = RESOURCE_DIR + "/resource-test1.txt";
    public static final String RESOURCE_FILE_2 = RESOURCE_DIR + "/resource-test2.txt";
    public static final String RESOURCE_FILE_3 = RESOURCE_DIR + "/resource-test3.html";
    public static final String RESOURCE_FILE_4 = RESOURCE_DIR + "/resource-test4.output";

    // Register resources.
    public static final class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            // Remove leading / for the resource patterns
            Module resourceModule = TestFeature.class.getModule();
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_DIR.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, SIMPLE_RESOURCE_DIR.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_EMPTY_DIR.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_DIR_WITH_SPACE.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_FILE_1.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_FILE_2.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_FILE_3.substring(1));
            RuntimeResourceAccess.addResource(resourceModule, RESOURCE_FILE_4.substring(1));

            /** Needed for {@link #testURLExternalFormEquivalence()} */
            for (Module module : ModuleLayer.boot().modules()) {
                RuntimeResourceAccess.addResource(module, "module-info.class");
            }
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

    public static boolean compareTwoURLs(URL url1, URL url2) {
        try {
            URLConnection url1Connection = url1.openConnection();
            URLConnection url2Connection = url2.openConnection();

            try (InputStream is1 = url1Connection.getInputStream(); InputStream is2 = url2Connection.getInputStream()) {
                Assert.assertNotNull("First input stream is null!", is1);
                Assert.assertNotNull("Second input stream is null!", is2);
                int nextByte1 = is1.read();
                int nextByte2 = is2.read();
                while (nextByte1 != -1 && nextByte2 != -1) {
                    if (nextByte1 != nextByte2) {
                        return false;
                    }

                    nextByte1 = is1.read();
                    nextByte2 = is2.read();
                }
                return nextByte1 == -1 && nextByte2 == -1;
            }
        } catch (IOException e) {
            Assert.fail("Exception occurs during URL operations!");
        }
        return false;
    }
}
