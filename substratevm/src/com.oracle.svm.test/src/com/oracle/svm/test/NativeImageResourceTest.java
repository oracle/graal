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

import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_DIR;
import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_FILE_1;
import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_FILE_2;
import static com.oracle.svm.test.NativeImageResourceUtils.compareTwoURLs;
import static com.oracle.svm.test.NativeImageResourceUtils.resourceNameToURL;

import java.io.IOException;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

@AddExports("java.base/java.lang")
public class NativeImageResourceTest {

    /**
     * <p>
     * Combining URL and resource operations.
     * </p>
     *
     * <p>
     * <b>Description: </b> Test inspired by issues: </br>
     * <ol>
     * <li><a href="https://github.com/oracle/graal/issues/1349">1349</a></li>
     * <li><a href="https://github.com/oracle/graal/issues/2291">2291</a></li>
     * </ol>
     * </p>
     */
    @Test
    public void githubIssues() {
        try {
            URL url1 = resourceNameToURL(RESOURCE_FILE_1, true);
            URL url2 = new URL(url1, RESOURCE_FILE_2);
            Assert.assertNotNull("Second URL is null!", url2);
            Assert.assertFalse("Two URLs are same!", compareTwoURLs(url1, url2));
        } catch (IOException e) {
            Assert.fail("Exception occurs during URL operations!");
        }
    }

    /**
     * <p>
     * Access file as a resource with and without trailing slashes.
     * </p>
     *
     * <p>
     * <b>Description: </b> Test inspired by issues: </br>
     * <ol>
     * <li><a href="https://github.com/oracle/graal/issues/4326">1349</a></li>
     * </ol>
     * </p>
     */
    @Test
    public void classGetResource() {
        resourceNameToURL(RESOURCE_FILE_1, true);

        String resourceNameWTrailingSlash = RESOURCE_FILE_1 + "/";
        URL url = resourceNameToURL(resourceNameWTrailingSlash, false);
        Assert.assertNull("Resource " + resourceNameWTrailingSlash + " is found!", url);
    }

    /**
     * <p>
     * Access directory as a resource with and without canonicals paths. Note: The native image
     * should not make canonical paths if the resource comes from the JAR.
     * </p>
     *
     * <p>
     * <b>Description: </b> Test inspired by issues: </br>
     * <ol>
     * <li><a href="https://github.com/oracle/graal/issues/4326">1349</a></li>
     * </ol>
     * </p>
     */
    @Test
    public void classGetDirectoryResource() {
        URL url1 = resourceNameToURL(RESOURCE_DIR + "/", true);
        Assert.assertTrue("The URL should end with slash!", url1.toString().endsWith("/"));

        URL url2 = resourceNameToURL(RESOURCE_DIR, true);
        Assert.assertFalse("The URL should not end with slash!", url2.toString().endsWith("/"));
        Assert.assertTrue("Two URLs must be the same!", compareTwoURLs(url1, url2));

        String nonCanonicalResourceDirectoryName = RESOURCE_DIR + "/./";
        resourceNameToURL(nonCanonicalResourceDirectoryName, false);
    }
}
