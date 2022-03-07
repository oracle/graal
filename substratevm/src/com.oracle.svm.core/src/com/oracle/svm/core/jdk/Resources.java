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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
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

    /**
     * The hosted map used to collect registered resources. Using a {@link Pair} of (moduleName,
     * resourceName) provides implementations for {@code hashCode()} and {@code equals()} needed for
     * the map keys.
     */
    private final EconomicMap<Pair<String, String>, ResourceStorageEntry> resources = ImageHeapMap.create();

    Resources() {
    }

    public EconomicMap<Pair<String, String>, ResourceStorageEntry> resources() {
        return resources;
    }

    public static byte[] inputStreamToByteArray(InputStream is) {
        try {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static String getResourceWithoutTrailingSlash(String name) {
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static void addEntry(String moduleName, String resourceName, boolean isDirectory, byte[] data) {
        Resources support = singleton();
        Pair<String, String> key = Pair.create(moduleName, getResourceWithoutTrailingSlash(resourceName));
        ResourceStorageEntry entry = support.resources.get(key);
        if (entry == null) {
            entry = new ResourceStorageEntry(isDirectory);
            support.resources.put(key, entry);
        }
        entry.getData().add(data);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerResource(String resourceName, InputStream is) {
        registerResource(null, resourceName, is);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerResource(String moduleName, String resourceName, InputStream is) {
        addEntry(moduleName, resourceName, false, inputStreamToByteArray(is));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerDirectoryResource(String resourceDirName, String content) {
        registerDirectoryResource(null, resourceDirName, content);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerDirectoryResource(String moduleName, String resourceDirName, String content) {
        /*
         * A directory content represents the names of all files and subdirectories located in the
         * specified directory, separated with new line delimiter and joined into one string which
         * is later converted into a byte array and placed into the resources map.
         */
        addEntry(moduleName, resourceDirName, true, content.getBytes());
    }

    public static ResourceStorageEntry get(String name) {
        return get(null, name);
    }

    public static ResourceStorageEntry get(String moduleName, String resourceName) {
        ResourceStorageEntry resourceStorageEntry = singleton().resources.get(Pair.create(moduleName, getResourceWithoutTrailingSlash(resourceName)));
        if (resourceStorageEntry != null && (resourceStorageEntry.isDirectory() || !resourceName.endsWith("/"))) {
            return resourceStorageEntry;
        } else {
            return null;
        }
    }

    private static URL createURL(String moduleName, String resourceName, int index) {
        try {
            String refPart = index != 0 ? '#' + Integer.toString(index) : "";
            return new URL(JavaNetSubstitutions.RESOURCE_PROTOCOL, moduleName, -1, '/' + resourceName + refPart);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static URL createURL(String resourceName) {
        return createURL(null, resourceName);
    }

    public static URL createURL(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Enumeration<URL> urls = createURLs(moduleName, resourceName);
        return urls.hasMoreElements() ? urls.nextElement() : null;
    }

    public static InputStream createInputStream(String resourceName) {
        return createInputStream(null, resourceName);
    }

    /* Avoid pulling in the URL class when only an InputStream is needed. */
    public static InputStream createInputStream(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        ResourceStorageEntry entry = Resources.get(moduleName, resourceName);
        if (entry == null) {
            return null;
        }
        List<byte[]> data = entry.getData();
        return data.isEmpty() ? null : new ByteArrayInputStream(data.get(0));
    }

    public static Enumeration<URL> createURLs(String resourceName) {
        return createURLs(null, resourceName);
    }

    public static Enumeration<URL> createURLs(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        List<URL> resourcesURLs = new ArrayList<>();

        /* If moduleName was unspecified we have to consider all modules in the image */
        if (moduleName == null) {
            for (Module module : BootModuleLayerSupport.instance().getBootLayer().modules()) {
                ResourceStorageEntry entry = Resources.get(module.getName(), resourceName);
                addURLEntries(resourcesURLs, entry, module.getName(), resourceName);
            }
        }
        ResourceStorageEntry explicitEntry = Resources.get(moduleName, resourceName);
        addURLEntries(resourcesURLs, explicitEntry, moduleName, resourceName);

        if (resourcesURLs.isEmpty()) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(resourcesURLs);
    }

    private static void addURLEntries(List<URL> resourcesURLs, ResourceStorageEntry entry, String moduleName, String resourceName) {
        if (entry == null) {
            return;
        }
        int numberOfResources = entry.getData().size();
        for (int index = 0; index < numberOfResources; index++) {
            resourcesURLs.add(createURL(moduleName, resourceName, index));
        }
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
