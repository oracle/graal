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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    @SuppressWarnings("deprecation")
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

    @Test
    public void moduleResourceURLAccess() {
        URL url = Class.class.getResource("uniName.dat");
        Assert.assertNotNull("URL for resource java.base/java/lang/uniName.dat must not be null", url);
        try (InputStream in = url.openStream()) {
            try {
                Assert.assertNotEquals("uniName.dat does not seem to contain valid data", in.read(), 0);
            } catch (IOException e) {
                Assert.fail("IOException in in.read(): " + e.getMessage());
            }
        } catch (IOException e) {
            Assert.fail("IOException in url.openStream(): " + e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testURLExternalFormEquivalence() {
        Enumeration<URL> urlEnumeration = null;
        try {
            urlEnumeration = ClassLoader.getSystemResources("module-info.class");
        } catch (IOException e) {
            Assert.fail("IOException in ClassLoader.getSystemResources(\"module-info.class\"): " + e.getMessage());
        }

        Assert.assertNotNull(urlEnumeration);
        Enumeration<URL> finalVar = urlEnumeration;
        Iterable<URL> urlIterable = finalVar::asIterator;
        List<URL> urlList = StreamSupport.stream(urlIterable.spliterator(), false).collect(Collectors.toList());
        Assert.assertTrue("ClassLoader.getSystemResources(\"module-info.class\") must return many module-info.class URLs",
                        urlList.size() > 3);

        URL thirdEntry = urlList.get(2);
        String thirdEntryExternalForm = thirdEntry.toExternalForm();
        URL thirdEntryFromExternalForm = null;
        try {
            thirdEntryFromExternalForm = new URL(thirdEntryExternalForm);
        } catch (MalformedURLException e) {
            Assert.fail("Creating a new URL from the ExternalForm of another has to work: " + e.getMessage());
        }

        boolean compareResult = compareTwoURLs(thirdEntry, thirdEntryFromExternalForm);
        Assert.assertTrue("Contents of original URL and one created from originals ExternalForm must be the same", compareResult);
    }

    @Test
    public void moduleGetResourceAsStream() {
        Module module = Class.class.getModule();
        try (InputStream in = module.getResourceAsStream("java/lang/uniName.dat")) {
            Assert.assertNotNull("InputStream for resource java.base/java/lang/uniName.dat must not be null", in);
            try {
                Assert.assertNotEquals("uniName.dat does not seem to contain valid data", in.read(), 0);
            } catch (IOException e) {
                Assert.fail("IOException in in.read(): " + e.getMessage());
            }
        } catch (IOException e) {
            Assert.fail("IOException in module.getResourceAsStream(): " + e.getMessage());
        }

        /* The resource file with leading slash should be present as well. */
        try (InputStream in2 = module.getResourceAsStream("/java/lang/uniName.dat")) {
            Assert.assertNotNull("InputStream for resource java.base/java/lang/uniName.dat must not be null", in2);
        } catch (IOException e) {
            Assert.fail("IOException in module.getResourceAsStream(): " + e.getMessage());
        }
    }
}
