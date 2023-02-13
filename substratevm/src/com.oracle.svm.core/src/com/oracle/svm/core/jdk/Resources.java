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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.resources.MissingResourceMetadataException;
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

    private static final int INVALID_TIMESTAMP = -1;
    public static final char RESOURCES_INTERNAL_PATH_SEPARATOR = '/';

    public static Resources singleton() {
        return ImageSingletons.lookup(Resources.class);
    }

    /**
     * The hosted map used to collect registered resources. Using a {@link Pair} of (moduleName,
     * resourceName) provides implementations for {@code hashCode()} and {@code equals()} needed for
     * the map keys.
     */
    private final EconomicMap<Pair<String, String>, Object> resources = ImageHeapMap.create();
    private final List<Pair<String, Pattern>> includePatterns = new ArrayList<>();
    private final List<Pair<String, Pattern>> excludePatterns = new ArrayList<>();

    /**
     * The object used to mark a resource as reachable according to the metadata. It can be obtained
     * when accessing the {@link Resources#resources} map, and it means that even though the
     * resource was correctly specified in the configuration, accessing it will return null.
     */
    public static final Object NEGATIVE_QUERY = new Object();

    /**
     * The object used to detect that the resource is not reachable according to the metadata. It
     * can be returned by the {@link Resources#get} method if the resource was not correctly
     * specified in the configuration, but we do not want to throw directly (for example when we try
     * to check all the modules for a resource).
     */
    private static final Object MISSING_METADATA = new Object();

    /**
     * Embedding a resource into an image is counted as a modification. Since all resources are
     * baked into the image during image generation, we save this value so that it can be fetched
     * later by calling {@link ResourceURLConnection#getLastModified()}.
     */
    private long lastModifiedTime = INVALID_TIMESTAMP;

    Resources() {
    }

    public EconomicMap<Pair<String, String>, Object> resources() {
        return resources;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public static byte[] inputStreamToByteArray(InputStream is) {
        try {
            return is.readAllBytes();
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private void updateTimeStamp() {
        if (lastModifiedTime == INVALID_TIMESTAMP) {
            lastModifiedTime = new Date().getTime();
        }
    }

    private Object addEntry(String moduleName, String resourceName, Object newEntry, boolean isDirectory, boolean fromJar) {
        synchronized (resources) {
            Pair<String, String> key = Pair.create(moduleName, resourceName);
            Object entry = resources.get(key);
            if (entry == null || entry == NEGATIVE_QUERY) {
                entry = newEntry == null ? new ResourceStorageEntry(isDirectory, fromJar) : newEntry;
                updateTimeStamp();
                resources.put(key, entry);
            }
            return entry;
        }
    }

    private void addEntry(String moduleName, String resourceName, boolean isDirectory, byte[] data, boolean fromJar) {
        Object entry = addEntry(moduleName, resourceName, null, isDirectory, fromJar);
        ((ResourceStorageEntry) entry).getData().add(data);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerResource(String resourceName, InputStream is) {
        singleton().registerResource(null, resourceName, is, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(String resourceName, InputStream is, boolean fromJar) {
        registerResource(null, resourceName, is, fromJar);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(String moduleName, String resourceName, InputStream is) {
        registerResource(moduleName, resourceName, is, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(String moduleName, String resourceName, byte[] resourceContent) {
        addEntry(moduleName, resourceName, false, resourceContent, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(String moduleName, String resourceName, InputStream is, boolean fromJar) {
        addEntry(moduleName, resourceName, false, inputStreamToByteArray(is), fromJar);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(String resourceDirName, String content) {
        registerDirectoryResource(null, resourceDirName, content, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(String resourceDirName, String content, boolean fromJar) {
        registerDirectoryResource(null, resourceDirName, content, fromJar);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(String moduleName, String resourceDirName, String content) {
        registerDirectoryResource(moduleName, resourceDirName, content, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(String moduleName, String resourceDirName, String content, boolean fromJar) {
        /*
         * A directory content represents the names of all files and subdirectories located in the
         * specified directory, separated with new line delimiter and joined into one string which
         * is later converted into a byte array and placed into the resources map.
         */
        addEntry(moduleName, resourceDirName, true, content.getBytes(), fromJar);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIOException(String resourceName, IOException e) {
        registerIOException(null, resourceName, e);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIOException(String moduleName, String resourceName, IOException e) {
        Pair<String, String> key = Pair.create(moduleName, resourceName);
        synchronized (resources) {
            updateTimeStamp();
            resources.put(key, e);
        }
    }

    public void registerNegativeQueryRuntime(String resourceName) {
        addEntry(null, resourceName, NEGATIVE_QUERY, false, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(String resourceName) {
        registerNegativeQuery(null, resourceName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(String moduleName, String resourceName) {
        addEntry(moduleName, resourceName, NEGATIVE_QUERY, false, false);
    }

    private void registerPattern(List<Pair<String, Pattern>> patterns, String module, Pattern pattern) {
        synchronized (patterns) {
            updateTimeStamp();
            patterns.add(Pair.create(module, pattern));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIncludePattern(Pattern pattern) {
        registerIncludePattern(null, pattern);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIncludePattern(String module, Pattern pattern) {
        registerPattern(includePatterns, module, pattern);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerExcludePattern(Pattern pattern) {
        registerExcludePattern(null, pattern);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerExcludePattern(String module, Pattern pattern) {
        registerPattern(excludePatterns, module, pattern);
    }

    /**
     * Avoid pulling native file system by using {@link NativeImageResourcePath} implementation to
     * convert <code>resourceName</code> to canonical variant.
     */
    public static String toCanonicalForm(String resourceName) {
        NativeImageResourcePath path = new NativeImageResourcePath(null, removeTrailingSlash(resourceName).getBytes(StandardCharsets.UTF_8), true);
        return new String(NativeImageResourcePath.getResolved(path));
    }

    private static boolean hasTrailingSlash(String resourceName) {
        return resourceName.endsWith("/");
    }

    private static String removeTrailingSlash(String resourceName) {
        return hasTrailingSlash(resourceName) ? resourceName.substring(0, resourceName.length() - 1) : resourceName;
    }

    private static boolean wasAlreadyInCanonicalForm(String resourceName, String canonicalResourceName) {
        return resourceName.equals(canonicalResourceName) || removeTrailingSlash(resourceName).equals(canonicalResourceName);
    }

    public Object get(String name, boolean throwOnMissing) {
        return get(null, name, throwOnMissing);
    }

    /**
     * If {@code throwOnMissing} is false, we have to distinguish an entry that was in the metadata
     * from one that was not, so the caller can correctly throw the
     * {@link MissingResourceMetadataException}. This is needed because different modules can be
     * tried on the same resource name, causing an unexpected exception if we throw directly.
     */
    public Object get(String moduleName, String resourceName, boolean throwOnMissing) {
        String canonicalResourceName = toCanonicalForm(resourceName);
        Object entry = resources.get(Pair.create(moduleName, canonicalResourceName));
        if (entry == null) {
            if (MissingResourceMetadataException.Options.ThrowMissingMetadataExceptions.getValue()) {
                for (Pair<String, Pattern> pattern : excludePatterns) {
                    if (Objects.equals(moduleName, pattern.getLeft()) && (pattern.getRight().matcher(resourceName).matches() || pattern.getRight().matcher(canonicalResourceName).matches())) {
                        return missingMetadata(resourceName, throwOnMissing);
                    }
                }
                for (Pair<String, Pattern> pattern : includePatterns) {
                    if (Objects.equals(moduleName, pattern.getLeft()) && (pattern.getRight().matcher(resourceName).matches() || pattern.getRight().matcher(canonicalResourceName).matches())) {
                        return null;
                    }
                }
                return missingMetadata(resourceName, throwOnMissing);
            } else {
                return null;
            }
        }
        if (entry instanceof IOException) {
            throw new RuntimeException((IOException) entry);
        }
        if (entry == NEGATIVE_QUERY) {
            return null;
        }
        ResourceStorageEntry resourceStorageEntry = (ResourceStorageEntry) entry;
        if (resourceStorageEntry.isFromJar() && !wasAlreadyInCanonicalForm(resourceName, canonicalResourceName)) {
            /*
             * The resource originally came from a jar file, thus behave like ZipFileSystem behaves
             * for non-canonical paths.
             */
            return null;
        }
        if (!resourceStorageEntry.isDirectory() && hasTrailingSlash(resourceName)) {
            /*
             * If this is an actual resource file (not a directory) we do not tolerate a trailing
             * slash.
             */
            return null;
        }
        return resourceStorageEntry;
    }

    private static Object missingMetadata(String resourceName, boolean throwOnMissing) {
        if (throwOnMissing) {
            throw MissingResourceMetadataException.missingResource(resourceName);
        } else {
            return MISSING_METADATA;
        }
    }

    @SuppressWarnings("deprecation")
    private static URL createURL(String moduleName, String resourceName, int index) {
        try {
            String refPart = index != 0 ? '#' + Integer.toString(index) : "";
            return new URL(JavaNetSubstitutions.RESOURCE_PROTOCOL, moduleName, -1, '/' + resourceName + refPart);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public URL createURL(String resourceName) {
        return createURL(null, resourceName);
    }

    public URL createURL(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Enumeration<URL> urls = createURLs(moduleName, resourceName);
        return urls.hasMoreElements() ? urls.nextElement() : null;
    }

    public InputStream createInputStream(String resourceName) {
        return createInputStream(null, resourceName);
    }

    /* Avoid pulling in the URL class when only an InputStream is needed. */
    public InputStream createInputStream(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Object entry = get(moduleName, resourceName, false);
        boolean isInMetadata = entry != MISSING_METADATA;
        if (moduleName == null && (entry == MISSING_METADATA || entry == null)) {
            /*
             * If no moduleName is specified and entry was not found as classpath-resource we have
             * to search for the resource in all modules in the image.
             */
            for (Module module : BootModuleLayerSupport.instance().getBootLayer().modules()) {
                entry = get(module.getName(), resourceName, false);
                if (entry != MISSING_METADATA) {
                    isInMetadata = true;
                }
                if (entry != null && entry != MISSING_METADATA) {
                    break;
                }
            }
        }

        if (!isInMetadata) {
            throw MissingResourceMetadataException.missingResource(resourceName);
        }
        if (entry == null || entry == MISSING_METADATA) {
            return null;
        }
        List<byte[]> data = ((ResourceStorageEntry) entry).getData();
        return data.isEmpty() ? null : new ByteArrayInputStream(data.get(0));
    }

    public Enumeration<URL> createURLs(String resourceName) {
        return createURLs(null, resourceName);
    }

    public Enumeration<URL> createURLs(String moduleName, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        boolean missingMetadata = true;

        List<URL> resourcesURLs = new ArrayList<>();
        String canonicalResourceName = toCanonicalForm(resourceName);
        boolean shouldAppendTrailingSlash = hasTrailingSlash(resourceName);
        /* If moduleName was unspecified we have to consider all modules in the image */
        if (moduleName == null) {
            for (Module module : BootModuleLayerSupport.instance().getBootLayer().modules()) {
                Object entry = get(module.getName(), resourceName, false);
                if (entry == MISSING_METADATA) {
                    continue;
                }
                missingMetadata = false;
                addURLEntries(resourcesURLs, (ResourceStorageEntry) entry, module.getName(), shouldAppendTrailingSlash ? canonicalResourceName + '/' : canonicalResourceName);
            }
        }
        Object explicitEntry = get(moduleName, resourceName, false);
        if (explicitEntry != MISSING_METADATA) {
            missingMetadata = false;
            addURLEntries(resourcesURLs, (ResourceStorageEntry) explicitEntry, moduleName, shouldAppendTrailingSlash ? canonicalResourceName + '/' : canonicalResourceName);
        }

        if (missingMetadata) {
            throw MissingResourceMetadataException.missingResource(resourceName);
        }

        if (resourcesURLs.isEmpty()) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(resourcesURLs);
    }

    private static void addURLEntries(List<URL> resourcesURLs, ResourceStorageEntry entry, String moduleName, String canonicalResourceName) {
        if (entry == null) {
            return;
        }
        int numberOfResources = entry.getData().size();
        for (int index = 0; index < numberOfResources; index++) {
            resourcesURLs.add(createURL(moduleName, canonicalResourceName, index));
        }
    }
}

@AutomaticallyRegisteredFeature
final class ResourcesFeature implements InternalFeature {
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
        for (Object entry : Resources.singleton().resources().getValues()) {
            if (entry != Resources.NEGATIVE_QUERY) {
                ResourceStorageEntry resourceList = (ResourceStorageEntry) entry;
                for (byte[] resource : resourceList.getData()) {
                    access.registerAsImmutable(resource);
                }
            }
        }
    }
}
