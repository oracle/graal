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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError;
import com.oracle.svm.core.jdk.resources.MissingResourceRegistrationUtils;
import com.oracle.svm.core.jdk.resources.NativeImageResourcePath;
import com.oracle.svm.core.jdk.resources.ResourceExceptionEntry;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.LogUtils;

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
     * The hosted map used to collect registered resources. Using a {@link Pair} of (module,
     * resourceName) provides implementations for {@code hashCode()} and {@code equals()} needed for
     * the map keys. Hosted module instances differ to runtime instances, so the map that ends up in
     * the image heap is computed after the runtime module instances have been computed {see
     * com.oracle.svm.hosted.ModuleLayerFeature}.
     */
    private final EconomicMap<Pair<Module, String>, ResourceStorageEntryBase> resources = ImageHeapMap.create();
    private final EconomicSet<ModuleResourcePair> includePatterns = EconomicSet.create();

    public record ModuleResourcePair(String module, String resource) {
    }

    /**
     * The object used to mark a resource as reachable according to the metadata. It can be obtained
     * when accessing the {@link Resources#resources} map, and it means that even though the
     * resource was correctly specified in the configuration, accessing it will return null.
     */
    public static final ResourceStorageEntryBase NEGATIVE_QUERY_MARKER = new ResourceStorageEntryBase();

    /**
     * The object used to detect that the resource is not reachable according to the metadata. It
     * can be returned by the {@link Resources#get} method if the resource was not correctly
     * specified in the configuration, but we do not want to throw directly (for example when we try
     * to check all the modules for a resource).
     */
    private static final ResourceStorageEntryBase MISSING_METADATA_MARKER = new ResourceStorageEntryBase();

    /**
     * Embedding a resource into an image is counted as a modification. Since all resources are
     * baked into the image during image generation, we save this value so that it can be fetched
     * later by calling {@link ResourceURLConnection#getLastModified()}.
     */
    private long lastModifiedTime = INVALID_TIMESTAMP;

    Resources() {
    }

    public EconomicMap<Pair<Module, String>, ResourceStorageEntryBase> getResourceStorage() {
        return resources;
    }

    public Iterable<ResourceStorageEntryBase> resources() {
        return resources.getValues();
    }

    public int count() {
        return resources.size();
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public static String moduleName(Module module) {
        return module == null ? null : module.getName();
    }

    private static Pair<Module, String> createStorageKey(Module module, String resourceName) {
        Module m = module != null && module.isNamed() ? module : null;
        return Pair.create(m, resourceName);
    }

    public static Set<String> getIncludedResourcesModules() {
        return StreamSupport.stream(singleton().resources.getKeys().spliterator(), false)
                        .map(Pair::getLeft)
                        .filter(Objects::nonNull)
                        .map(Module::getName)
                        .collect(Collectors.toSet());
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

    @Platforms(Platform.HOSTED_ONLY.class)
    private ResourceStorageEntryBase addEntry(Module module, String resourceName, ResourceStorageEntryBase newEntry, boolean isDirectory, boolean fromJar) {
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Trying to add a resource entry after analysis.");
        Module m = module != null && module.isNamed() ? module : null;
        if (m != null) {
            m = RuntimeModuleSupport.instance().getRuntimeModuleForHostedModule(m);
        }
        synchronized (resources) {
            Pair<Module, String> key = createStorageKey(m, resourceName);
            ResourceStorageEntryBase entry = resources.get(key);
            if (entry == null || entry == NEGATIVE_QUERY_MARKER) {
                entry = newEntry == null ? new ResourceStorageEntry(isDirectory, fromJar) : newEntry;
                updateTimeStamp();
                resources.put(key, entry);
            }
            return entry;
        }
    }

    private void addEntry(Module module, String resourceName, boolean isDirectory, byte[] data, boolean fromJar) {
        ResourceStorageEntryBase entry = addEntry(module, resourceName, null, isDirectory, fromJar);
        entry.getData().add(data);
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
    public void registerResource(Module module, String resourceName, InputStream is) {
        registerResource(module, resourceName, is, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(Module module, String resourceName, byte[] resourceContent) {
        addEntry(module, resourceName, false, resourceContent, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(Module module, String resourceName, InputStream is, boolean fromJar) {
        addEntry(module, resourceName, false, inputStreamToByteArray(is), fromJar);
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
    public void registerDirectoryResource(Module module, String resourceDirName, String content) {
        registerDirectoryResource(module, resourceDirName, content, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(Module module, String resourceDirName, String content, boolean fromJar) {
        /*
         * A directory content represents the names of all files and subdirectories located in the
         * specified directory, separated with new line delimiter and joined into one string which
         * is later converted into a byte array and placed into the resources map.
         */
        addEntry(module, resourceDirName, true, content.getBytes(), fromJar);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIOException(String resourceName, IOException e, boolean linkAtBuildTime) {
        registerIOException(null, resourceName, e, linkAtBuildTime);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIOException(Module module, String resourceName, IOException e, boolean linkAtBuildTime) {
        if (linkAtBuildTime) {
            if (SubstrateOptions.ThrowLinkAtBuildTimeIOExceptions.getValue()) {
                throw new RuntimeException("Resource " + resourceName + " from module " + module.getName() + " produced an IOException.", e);
            } else {
                LogUtils.warning("Resource " + resourceName + " from module " + module.getName() + " produced the following IOException: " + e.getClass().getTypeName() + ": " + e.getMessage());
            }
        }
        Pair<Module, String> key = createStorageKey(module, resourceName);
        synchronized (resources) {
            updateTimeStamp();
            resources.put(key, new ResourceExceptionEntry(e));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(String resourceName) {
        registerNegativeQuery(null, resourceName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(Module module, String resourceName) {
        addEntry(module, resourceName, NEGATIVE_QUERY_MARKER, false, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIncludePattern(String pattern) {
        registerIncludePattern(null, pattern);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIncludePattern(String module, String pattern) {
        assert MissingRegistrationUtils.throwMissingRegistrationErrors();
        synchronized (includePatterns) {
            updateTimeStamp();
            includePatterns.add(new ModuleResourcePair(module, pattern));
        }
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

    public ResourceStorageEntryBase get(String name, boolean throwOnMissing) {
        return get(null, name, throwOnMissing);
    }

    /**
     * If {@code throwOnMissing} is false, we have to distinguish an entry that was in the metadata
     * from one that was not, so the caller can correctly throw the
     * {@link MissingResourceRegistrationError}. This is needed because different modules can be
     * tried on the same resource name, causing an unexpected exception if we throw directly.
     */
    public ResourceStorageEntryBase get(Module module, String resourceName, boolean throwOnMissing) {
        String canonicalResourceName = toCanonicalForm(resourceName);
        String moduleName = moduleName(module);
        ResourceStorageEntryBase entry = resources.get(createStorageKey(module, canonicalResourceName));
        if (entry == null) {
            if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                for (ModuleResourcePair moduleResourcePair : includePatterns) {
                    if (Objects.equals(moduleName, moduleResourcePair.module) &&
                                    (matchResource(moduleResourcePair.resource, resourceName) || matchResource(moduleResourcePair.resource, canonicalResourceName))) {
                        return null;
                    }
                }
                return missingMetadata(resourceName, throwOnMissing);
            } else {
                return null;
            }
        }
        if (entry.isException()) {
            throw new RuntimeException(entry.getException());
        }
        if (entry == NEGATIVE_QUERY_MARKER) {
            return null;
        }
        if (entry.isFromJar() && !wasAlreadyInCanonicalForm(resourceName, canonicalResourceName)) {
            /*
             * The resource originally came from a jar file, thus behave like ZipFileSystem behaves
             * for non-canonical paths.
             */
            return null;
        }
        if (!entry.isDirectory() && hasTrailingSlash(resourceName)) {
            /*
             * If this is an actual resource file (not a directory) we do not tolerate a trailing
             * slash.
             */
            return null;
        }
        return entry;
    }

    private static ResourceStorageEntryBase missingMetadata(String resourceName, boolean throwOnMissing) {
        if (throwOnMissing) {
            MissingResourceRegistrationUtils.missingResource(resourceName);
        }
        return MISSING_METADATA_MARKER;
    }

    @SuppressWarnings("deprecation")
    private static URL createURL(Module module, String resourceName, int index) {
        try {
            String refPart = index != 0 ? '#' + Integer.toString(index) : "";
            String moduleName = moduleName(module);
            return new URL(JavaNetSubstitutions.RESOURCE_PROTOCOL, moduleName, -1, '/' + resourceName + refPart);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public URL createURL(String resourceName) {
        return createURL(null, resourceName);
    }

    public URL createURL(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Enumeration<URL> urls = createURLs(module, resourceName);
        return urls.hasMoreElements() ? urls.nextElement() : null;
    }

    public InputStream createInputStream(String resourceName) {
        return createInputStream(null, resourceName);
    }

    /* Avoid pulling in the URL class when only an InputStream is needed. */
    public InputStream createInputStream(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        ResourceStorageEntryBase entry = get(module, resourceName, false);
        boolean isInMetadata = entry != MISSING_METADATA_MARKER;
        if (moduleName(module) == null && (entry == MISSING_METADATA_MARKER || entry == null)) {
            /*
             * If module is not specified or is an unnamed module and entry was not found as
             * classpath-resource we have to search for the resource in all modules in the image.
             */
            for (Module m : RuntimeModuleSupport.instance().getBootLayer().modules()) {
                entry = get(m, resourceName, false);
                if (entry != MISSING_METADATA_MARKER) {
                    isInMetadata = true;
                }
                if (entry != null && entry != MISSING_METADATA_MARKER) {
                    break;
                }
            }
        }

        if (!isInMetadata) {
            MissingResourceRegistrationUtils.missingResource(resourceName);
        }
        if (entry == null || entry == MISSING_METADATA_MARKER) {
            return null;
        }
        List<byte[]> data = entry.getData();
        return data.isEmpty() ? null : new ByteArrayInputStream(data.get(0));
    }

    public Enumeration<URL> createURLs(String resourceName) {
        return createURLs(null, resourceName);
    }

    public Enumeration<URL> createURLs(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        boolean missingMetadata = true;

        List<URL> resourcesURLs = new ArrayList<>();
        String canonicalResourceName = toCanonicalForm(resourceName);
        boolean shouldAppendTrailingSlash = hasTrailingSlash(resourceName);

        /* If moduleName was unspecified we have to consider all modules in the image */
        if (moduleName(module) == null) {
            for (Module m : RuntimeModuleSupport.instance().getBootLayer().modules()) {
                ResourceStorageEntryBase entry = get(m, resourceName, false);
                if (entry == MISSING_METADATA_MARKER) {
                    continue;
                }
                missingMetadata = false;
                addURLEntries(resourcesURLs, (ResourceStorageEntry) entry, m, shouldAppendTrailingSlash ? canonicalResourceName + '/' : canonicalResourceName);
            }
        }
        ResourceStorageEntryBase explicitEntry = get(module, resourceName, false);
        if (explicitEntry != MISSING_METADATA_MARKER) {
            missingMetadata = false;
            addURLEntries(resourcesURLs, (ResourceStorageEntry) explicitEntry, module, shouldAppendTrailingSlash ? canonicalResourceName + '/' : canonicalResourceName);
        }

        if (missingMetadata) {
            MissingResourceRegistrationUtils.missingResource(resourceName);
        }

        if (resourcesURLs.isEmpty()) {
            return Collections.emptyEnumeration();
        }
        return Collections.enumeration(resourcesURLs);
    }

    private static void addURLEntries(List<URL> resourcesURLs, ResourceStorageEntry entry, Module module, String canonicalResourceName) {
        if (entry == null) {
            return;
        }
        int numberOfResources = entry.getData().size();
        for (int index = 0; index < numberOfResources; index++) {
            resourcesURLs.add(createURL(module, canonicalResourceName, index));
        }
    }

    private static boolean matchResource(String pattern, String resource) {
        if (pattern.equals(resource)) {
            return true;
        }

        if (!pattern.contains("*")) {
            return false;
        }

        if (pattern.endsWith("*")) {
            return resource.startsWith(pattern.substring(0, pattern.length() - 1));
        }

        String[] parts = pattern.split("\\*");

        int i = parts.length - 1;
        boolean found = false;
        while (i > 0 && !found) {
            found = !parts[i - 1].endsWith("\\");
            i--;
        }

        if (!found) {
            return false;
        }

        String start = String.join("*", Arrays.copyOfRange(parts, 0, i + 1));
        String end = String.join("*", Arrays.copyOfRange(parts, i + 1, parts.length));

        return resource.startsWith(start) && resource.endsWith(end);
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
        for (ResourceStorageEntryBase entry : Resources.singleton().resources()) {
            if (entry.hasData()) {
                for (byte[] resource : entry.getData()) {
                    access.registerAsImmutable(resource);
                }
            }
        }
    }
}
