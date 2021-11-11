/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.resources.NativeImageResourcePath;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

/**
 * Support for resources on Substrate VM. All resources that need to be available at run time need
 * to be added explicitly during native image generation using {@link #registerResource}.
 *
 * Registered resources are then available from DynamicHub#getResource classes and
 * {@link Target_java_lang_ClassLoader class loaders}.
 */
public final class Resources {

    public static final char RESOURCES_INTERNAL_PATH_SEPARATOR = '/';

    public static Resources singleton() {
        return ImageSingletons.lookup(Resources.class);
    }

    /** The hosted map used to collect registered resources. */
    private final EconomicMap<String, ResourceStorageEntry> resources = ImageHeapMap.create();

    Resources() {
    }

    public EconomicMap<String, ResourceStorageEntry> resources() {
        return resources;
    }

    public static byte[] inputStreamToByteArray(InputStream is) {
        // TODO: Replace this with is.readAllBytes() once Java 8 support is removed
        byte[] arr = new byte[4096];
        int pos = 0;
        try {
            for (;;) {
                if (pos == arr.length) {
                    byte[] tmp = new byte[arr.length * 2];
                    System.arraycopy(arr, 0, tmp, 0, arr.length);
                    arr = tmp;
                }
                int len = is.read(arr, pos, arr.length - pos);
                if (len == -1) {
                    break;
                }
                pos += len;
            }
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        byte[] data = new byte[pos];
        System.arraycopy(arr, 0, data, 0, pos);
        return data;
    }

    private static void addEntry(String resourceName, boolean isDirectory, byte[] data) {
        Resources support = singleton();
        ResourceStorageEntry entry = support.resources.get(resourceName);
        if (entry == null) {
            entry = new ResourceStorageEntry(isDirectory);
            support.resources.put(resourceName, entry);
        }
        entry.getData().add(data);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerResource(String resourceName, InputStream is) {
        addEntry(resourceName, false, inputStreamToByteArray(is));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerDirectoryResource(String resourceDirName, String content) {
        /*
         * A directory content represents the names of all files and subdirectories located in the
         * specified directory, separated with new line delimiter and joined into one string which
         * is later converted into a byte array and placed into the resources map.
         */
        addEntry(resourceDirName, true, content.getBytes());
    }

    /**
     * Avoid pulling native file system by using {@link NativeImageResourcePath} implementation to
     * convert <code>resourceName</code> to canonical variant.
     */
    public static String toCanonicalForm(String resourceName) {
        NativeImageResourcePath path = new NativeImageResourcePath(null, resourceName.getBytes(StandardCharsets.UTF_8), true);
        return new String(NativeImageResourcePath.getResolved(path));
    }

    public static ResourceStorageEntry get(String name) {
        return singleton().resources.get(name);
    }

    private static URL createURL(String resourceName, int index) {
        try {
            return new URL(JavaNetSubstitutions.RESOURCE_PROTOCOL, null, -1, resourceName,
                            new URLStreamHandler() {
                                @Override
                                protected URLConnection openConnection(URL url) {
                                    return new ResourceURLConnection(url, index);
                                }
                            });
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }

    }

    public static URL createURL(String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Enumeration<URL> urls = createURLs(toCanonicalForm(resourceName));
        return urls.hasMoreElements() ? urls.nextElement() : null;
    }

    /* Avoid pulling in the URL class when only an InputStream is needed. */
    public static InputStream createInputStream(String resourceName) {
        if (resourceName == null) {
            return null;
        }

        ResourceStorageEntry entry = Resources.get(toCanonicalForm(resourceName));
        if (entry == null) {
            return null;
        }
        List<byte[]> data = entry.getData();
        return data.isEmpty() ? null : new ByteArrayInputStream(data.get(0));
    }

    public static Enumeration<URL> createURLs(String resourceName) {
        if (resourceName == null) {
            return null;
        }

        String canonicalResourceName = toCanonicalForm(resourceName);
        ResourceStorageEntry entry = Resources.get(canonicalResourceName);
        if (entry == null) {
            return Collections.emptyEnumeration();
        }
        int numberOfResources = entry.getData().size();
        List<URL> resourcesURLs = new ArrayList<>(numberOfResources);
        for (int index = 0; index < numberOfResources; index++) {
            resourcesURLs.add(createURL(canonicalResourceName, index));
        }
        return Collections.enumeration(resourcesURLs);
    }
}

@AutomaticFeature
final class ResourcesFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(Resources.class, new Resources());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        /*
         * The resources embedded in the image heap are read-only at run time. Note that we do not
         * mark the collection data structures as read-only because Java collections have all sorts
         * of lazily initialized fields. Only the byte[] arrays themselves can be safely made
         * read-only.
         */
        for (ResourceStorageEntry resourceList : Resources.singleton().resources().getValues()) {
            for (byte[] resource : resourceList.getData()) {
                access.registerAsImmutable(resource);
            }
        }
    }
}
