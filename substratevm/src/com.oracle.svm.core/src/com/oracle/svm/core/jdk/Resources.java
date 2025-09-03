/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ClassLoaderSupport.ConditionWithOrigin;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.encoder.SymbolEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.resources.MissingResourceRegistrationUtils;
import com.oracle.svm.core.jdk.resources.ResourceExceptionEntry;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntryBase;
import com.oracle.svm.core.jdk.resources.ResourceURLConnection;
import com.oracle.svm.core.jdk.resources.CompressedGlobTrie.CompressedGlobTrie;
import com.oracle.svm.core.jdk.resources.CompressedGlobTrie.GlobTrieNode;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.GlobUtils;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.NativeImageResourcePathRepresentation;

/**
 * Support for resources on Substrate VM. All resources that need to be available at run time need
 * to be added explicitly during native image generation using {@link #registerResource}.
 *
 * Registered resources are then available from DynamicHub#getResource classes and
 * {@link Target_java_lang_ClassLoader class loaders}.
 */
public final class Resources implements MultiLayeredImageSingleton {

    private static final int INVALID_TIMESTAMP = -1;
    public static final char RESOURCES_INTERNAL_PATH_SEPARATOR = '/';
    private static final String RESOURCE_KEYS = "resourceKeys";
    private static final String RESOURCE_REGISTRATION_STATES = "resourceRegistrationStates";
    private static final String PATTERNS = "patterns";

    @Platforms(Platform.HOSTED_ONLY.class) //
    private SymbolEncoder encoder;

    /**
     * @return the singleton corresponding to this layer's resources in a layered build, the unique
     *         singleton otherwise
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static Resources currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(Resources.class, false, true);
    }

    /**
     * @return an array of singletons corresponding to all layers in a layered build, or an array
     *         with a single element otherwise
     */
    public static Resources[] layeredSingletons() {
        assert !SubstrateUtil.HOSTED : "Accessing all layers resources at build time";
        return MultiLayeredImageSingleton.getAllLayers(Resources.class);
    }

    /**
     * The hosted map used to collect registered resources. Using a {@link ModuleResourceKey} of
     * (module, resourceName) provides implementations for {@code hashCode()} and {@code equals()}
     * needed for the map keys. Hosted module instances differ to runtime instances, so the map that
     * ends up in the image heap is computed after the runtime module instances have been computed
     * {see com.oracle.svm.hosted.ModuleLayerFeature}.
     */
    private final EconomicMap<ModuleResourceKey, ConditionalRuntimeValue<ResourceStorageEntryBase>> resources = ImageHeapMap.createNonLayeredMap();
    /** Regexp patterns used to match names of resources to be included in the image. */
    private final EconomicMap<RequestedPattern, RuntimeConditionSet> requestedPatterns = ImageHeapMap.createNonLayeredMap();

    /**
     * The string representation of {@link ModuleNameResourceKey} that are already registered in
     * previous layers. Since the {@link ModuleInstanceResourceKey} contains a reference to a
     * {@link Module}, the {@link Module} name is used instead of the object itself in the string
     * representation. This works under the assumption (enforced by
     * LayeredModuleSingleton.setPackages) that all modules have a different unique name in Layered
     * Images.
     *
     * The boolean associated to each {@link ModuleNameResourceKey} is true if the registered value
     * is complete and false in the case of a negative query.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Map<String, Boolean> previousLayerResources;

    /**
     * The string representation of {@link RequestedPattern} that are already registered in previous
     * layers.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final Set<String> previousLayerPatterns;

    public record RequestedPattern(String module, String pattern) {
    }

    public interface ModuleResourceKey {
        Module getModule();

        String getModuleName();

        Object module();

        String resource();
    }

    /**
     * In standalone images, the module object is the {@link Module} reference itself.
     */
    public record ModuleInstanceResourceKey(Module module, String resource) implements ModuleResourceKey {
        public ModuleInstanceResourceKey {
            assert !ImageLayerBuildingSupport.buildingImageLayer() : "The ModuleInstanceResourceKey should only be used in standalone images.";
        }

        @Override
        public Module getModule() {
            return module;
        }

        @Override
        public String getModuleName() {
            if (module == null) {
                return null;
            }
            return module.getName();
        }
    }

    /**
     * In Layered Image, only the module name is stored in the record.
     */
    public record ModuleNameResourceKey(Object module, String resource) implements ModuleResourceKey {
        public ModuleNameResourceKey {
            /*
             * A null module in the ModuleResourceKey represents any unnamed module, meaning that
             * only one marker (null) is needed for all of them and that if the module is not null,
             * it is named (see Resources.createStorageKey). This string representation relies on
             * the assumption (enforced by LayeredModuleSingleton.setPackages) that a layered image
             * build cannot contain two modules with the same name, so Module#getName() is
             * guaranteed to be unique for layered images.
             */
            assert module == null || module instanceof Module : "The ModuleNameResourceKey constructor should only be called with a Module as first argument";
            assert ImageLayerBuildingSupport.buildingImageLayer() : "The ModuleNameResourceKey should only be used in layered images.";
            module = (module != null) ? ((Module) module).getName() : module;
        }

        @Override
        public Module getModule() {
            throw VMError.shouldNotReachHere("Accessing the module instance of the ModuleResourceKey is not supported in layered images.");
        }

        @Override
        public String getModuleName() {
            return (String) module;
        }
    }

    /**
     * A resource marked with the NEGATIVE_QUERY_MARKER is a resource included in the image
     * according to the resource configuration, but it does not actually exist. Trying to access it
     * at runtime will return {@code null} and not throw a
     * {@link com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError}.
     */
    public static final ResourceStorageEntryBase NEGATIVE_QUERY_MARKER = new ResourceStorageEntryBase();

    /**
     * The object used to detect that the resource is not reachable according to the metadata. It
     * can be returned by the {@link Resources#getAtRuntime} method if the resource was not
     * correctly specified in the configuration, but we do not want to throw directly (for example
     * when we try to check all the modules for a resource).
     */
    public static final ResourceStorageEntryBase MISSING_METADATA_MARKER = new ResourceStorageEntryBase();

    /**
     * Embedding a resource into an image is counted as a modification. Since all resources are
     * baked into the image during image generation, we save this value so that it can be fetched
     * later by calling {@link ResourceURLConnection#getLastModified()}.
     */
    private long lastModifiedTime = INVALID_TIMESTAMP;

    private GlobTrieNode<ConditionWithOrigin> resourcesTrieRoot;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private Function<Module, Module> hostedToRuntimeModuleMapper;

    Resources() {
        this(Map.of(), Set.of());
    }

    Resources(Map<String, Boolean> previousLayerResources, Set<String> previousLayerPatterns) {
        this.previousLayerResources = previousLayerResources;
        this.previousLayerPatterns = previousLayerPatterns;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setEncoder(SymbolEncoder encoder) {
        this.encoder = encoder;
    }

    public GlobTrieNode<ConditionWithOrigin> getResourcesTrieRoot() {
        return resourcesTrieRoot;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setResourcesTrieRoot(GlobTrieNode<ConditionWithOrigin> resourcesTrieRoot) {
        this.resourcesTrieRoot = resourcesTrieRoot;
    }

    public void forEachResource(BiConsumer<ModuleResourceKey, ConditionalRuntimeValue<ResourceStorageEntryBase>> action) {
        MapCursor<ModuleResourceKey, ConditionalRuntimeValue<ResourceStorageEntryBase>> entries = resources.getEntries();
        while (entries.advance()) {
            action.accept(entries.getKey(), entries.getValue());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public UnmodifiableEconomicMap<ModuleResourceKey, ConditionalRuntimeValue<ResourceStorageEntryBase>> resources() {
        return resources;
    }

    public static long getLastModifiedTime() {
        var singletons = layeredSingletons();
        return singletons[singletons.length - 1].lastModifiedTime;
    }

    public static String moduleName(Module module) {
        return module == null ? null : module.getName();
    }

    public static ModuleResourceKey createStorageKey(Module module, String resourceName) {
        Module m = module != null && module.isNamed() ? module : null;
        if (ImageInfo.inImageBuildtimeCode()) {
            if (m != null) {
                m = currentLayer().hostedToRuntimeModuleMapper.apply(m);
            }
        }
        return ImageLayerBuildingSupport.buildingImageLayer() ? new ModuleNameResourceKey(m, resourceName) : new ModuleInstanceResourceKey(m, resourceName);
    }

    @Platforms(Platform.HOSTED_ONLY.class) //
    public void setHostedToRuntimeModuleMapper(Function<Module, Module> hostedToRuntimeModuleMapper) {
        this.hostedToRuntimeModuleMapper = hostedToRuntimeModuleMapper;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Set<String> getIncludedResourcesModules() {
        return StreamSupport.stream(currentLayer().resources.getKeys().spliterator(), false)
                        .map(ModuleResourceKey::getModuleName)
                        .filter(Objects::nonNull)
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

    private void addResource(ModuleResourceKey key, ConditionalRuntimeValue<ResourceStorageEntryBase> entry) {
        Boolean previousLayerData = ImageLayerBuildingSupport.buildingImageLayer() ? previousLayerResources.get(key.toString()) : null;
        /* GR-66387: The runtime condition should be combined across layers. */
        if (previousLayerData == null || (!previousLayerData && entry.getValueUnconditionally() != NEGATIVE_QUERY_MARKER)) {
            resources.put(key, entry);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void addEntry(Module module, String resourceName, boolean isDirectory, byte[] data, boolean fromJar, boolean isNegativeQuery) {
        VMError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Trying to add a resource entry after analysis.");
        Module m = module != null && module.isNamed() ? module : null;
        synchronized (resources) {
            ModuleResourceKey key = createStorageKey(m, resourceName);
            RuntimeConditionSet conditionSet = RuntimeConditionSet.emptySet();
            ConditionalRuntimeValue<ResourceStorageEntryBase> entry = resources.get(key);
            if (isNegativeQuery) {
                if (entry == null) {
                    addResource(key, new ConditionalRuntimeValue<>(conditionSet, NEGATIVE_QUERY_MARKER));
                }
                return;
            }

            if (entry == null || entry.getValueUnconditionally() == NEGATIVE_QUERY_MARKER) {
                updateTimeStamp();
                entry = new ConditionalRuntimeValue<>(conditionSet, new ResourceStorageEntry(isDirectory, fromJar));
                addResource(key, entry);
            } else {
                if (key.module() != null) {
                    // if the entry already exists, and it comes from a module, it is the same entry
                    // that we registered at some point before
                    return;
                }
            }
            entry.getValueUnconditionally().addData(data);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerResource(String resourceName, InputStream is) {
        currentLayer().registerResource(null, resourceName, is, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(Module module, String resourceName, byte[] resourceContent) {
        addEntry(module, resourceName, false, resourceContent, true, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerResource(Module module, String resourceName, InputStream is, boolean fromJar) {
        addEntry(module, resourceName, false, inputStreamToByteArray(is), fromJar, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerDirectoryResource(Module module, String resourceDirName, String content, boolean fromJar) {
        /*
         * A directory content represents the names of all files and subdirectories located in the
         * specified directory, separated with new line delimiter and joined into one string which
         * is later converted into a byte array and placed into the resources map.
         */
        addEntry(module, resourceDirName, true, content.getBytes(), fromJar, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIOException(Module module, String resourceName, IOException e, boolean linkAtBuildTime) {
        if (linkAtBuildTime) {
            if (SubstrateOptions.ThrowLinkAtBuildTimeIOExceptions.getValue()) {
                throw new RuntimeException("Resource " + resourceName + " from module " + moduleName(module) + " produced an IOException.", e);
            } else {
                LogUtils.warning("Resource " + resourceName + " from module " + moduleName(module) + " produced the following IOException: " + e.getClass().getTypeName() + ": " + e.getMessage());
            }
        }
        ModuleResourceKey key = createStorageKey(module, resourceName);
        synchronized (resources) {
            updateTimeStamp();
            addResource(key, new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(), new ResourceExceptionEntry(e)));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(String resourceName) {
        registerNegativeQuery(null, resourceName);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerNegativeQuery(Module module, String resourceName) {
        addEntry(module, resourceName, false, null, false, true);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerIncludePattern(ConfigurationCondition condition, String module, String pattern) {
        assert MissingRegistrationUtils.throwMissingRegistrationErrors();
        synchronized (requestedPatterns) {
            updateTimeStamp();
            addPattern(new RequestedPattern(encoder.encodeModule(module), handleEscapedCharacters(pattern)), RuntimeConditionSet.createHosted(condition));
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void addPattern(RequestedPattern pattern, RuntimeConditionSet condition) {
        if (!previousLayerPatterns.contains(pattern.toString())) {
            requestedPatterns.put(pattern, condition);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final String BEGIN_ESCAPED_SEQUENCE = "\\Q";

    @Platforms(Platform.HOSTED_ONLY.class)//
    private static final String END_ESCAPED_SEQUENCE = "\\E";

    /*
     * This handles generated include patterns which start and end with \Q and \E. The actual
     * resource name is located in between those tags.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static String handleEscapedCharacters(String pattern) {
        if (pattern.startsWith(BEGIN_ESCAPED_SEQUENCE) && pattern.endsWith(END_ESCAPED_SEQUENCE)) {
            return pattern.substring(BEGIN_ESCAPED_SEQUENCE.length(), pattern.length() - END_ESCAPED_SEQUENCE.length());
        }
        return pattern;
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

    public static ResourceStorageEntryBase getAtRuntime(String name) {
        return getAtRuntime(null, name, false);
    }

    /**
     * Looks up a resource from {@code module} with name {@code resourceName}.
     * <p>
     * The {@code probe} parameter indicates whether the caller is probing for the existence of a
     * resource. If {@code probe} is true, failed resource lookups return will not throw missing
     * registration errors and may instead return {@link #MISSING_METADATA_MARKER}.
     * <p>
     * Tracing note: When this method is used for probing, only successful metadata matches will be
     * traced. If a probing result is {@link #MISSING_METADATA_MARKER}, the caller must explicitly
     * trace the missing metadata.
     */
    public static ResourceStorageEntryBase getAtRuntime(Module module, String resourceName, boolean probe) {
        VMError.guarantee(ImageInfo.inImageRuntimeCode(), "This function should be used only at runtime.");
        String canonicalResourceName = NativeImageResourcePathRepresentation.toCanonicalForm(resourceName);
        String moduleName = moduleName(module);
        ConditionalRuntimeValue<ResourceStorageEntryBase> entry = getEntry(module, canonicalResourceName);
        if (entry == null) {
            if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                if (missingResourceMatchesIncludePattern(resourceName, moduleName) || missingResourceMatchesIncludePattern(canonicalResourceName, moduleName)) {
                    // This resource name matches a pattern/glob from the provided metadata, but no
                    // resource with the name actually exists. Do not report missing metadata.
                    traceResource(resourceName, moduleName);
                    return null;
                }
                traceResourceMissingMetadata(resourceName, moduleName, probe);
                return missingMetadata(module, resourceName, probe);
            } else {
                // NB: Without exact reachability metadata, resource include patterns are not
                // stored in the image heap, so we cannot reliably identify if the resource was
                // included at build time. Assume it is missing.
                traceResourceMissingMetadata(resourceName, moduleName, probe);
                return null;
            }
        }
        traceResource(resourceName, moduleName);
        if (!entry.getConditions().satisfied()) {
            return missingMetadata(module, resourceName, probe);
        }

        ResourceStorageEntryBase unconditionalEntry = entry.getValue();
        assert unconditionalEntry != null : "Already checked above that the condition is satisfied";
        if (unconditionalEntry.isException()) {
            throw new RuntimeException(unconditionalEntry.getException());
        }
        if (unconditionalEntry == NEGATIVE_QUERY_MARKER) {
            return null;
        }
        if (unconditionalEntry.isFromJar() && !wasAlreadyInCanonicalForm(resourceName, canonicalResourceName)) {
            /*
             * The resource originally came from a jar file, thus behave like ZipFileSystem behaves
             * for non-canonical paths.
             */
            return null;
        }
        if (!unconditionalEntry.isDirectory() && hasTrailingSlash(resourceName)) {
            /*
             * If this is an actual resource file (not a directory) we do not tolerate a trailing
             * slash.
             */
            return null;
        }
        return unconditionalEntry;
    }

    @AlwaysInline("tracing should fold away when disabled")
    private static void traceResource(String resourceName, String moduleName) {
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceResource(resourceName, moduleName);
        }
    }

    @AlwaysInline("tracing should fold away when disabled")
    private static void traceResourceMissingMetadata(String resourceName, String moduleName) {
        traceResourceMissingMetadata(resourceName, moduleName, false);
    }

    @AlwaysInline("tracing should fold away when disabled")
    private static void traceResourceMissingMetadata(String resourceName, String moduleName, boolean probe) {
        if (MetadataTracer.enabled() && !probe) {
            // Do not trace missing metadata for probing queries, otherwise we'll trace an entry for
            // every module. The caller is responsible for tracing missing entries if it uses
            // probing.
            MetadataTracer.singleton().traceResource(resourceName, moduleName);
        }
    }

    /**
     * Checks whether the given missing resource is matched by a pattern/glob registered at build
     * time. In such a case, we should not report missing metadata.
     */
    private static boolean missingResourceMatchesIncludePattern(String resourceName, String moduleName) {
        VMError.guarantee(MissingRegistrationUtils.throwMissingRegistrationErrors(), "include patterns are only stored in the image with exact reachability metadata");
        String glob = GlobUtils.transformToTriePath(resourceName, moduleName);
        for (var r : layeredSingletons()) {
            MapCursor<RequestedPattern, RuntimeConditionSet> cursor = r.requestedPatterns.getEntries();
            while (cursor.advance()) {
                RequestedPattern moduleResourcePair = cursor.getKey();
                if (Objects.equals(moduleName, moduleResourcePair.module) && matchResource(moduleResourcePair.pattern, resourceName) && cursor.getValue().satisfied()) {
                    return true;
                }
            }

            if (CompressedGlobTrie.match(r.getResourcesTrieRoot(), glob)) {
                return true;
            }
        }
        return false;
    }

    private static ConditionalRuntimeValue<ResourceStorageEntryBase> getEntry(Module module, String canonicalResourceName) {
        for (var r : layeredSingletons()) {
            ConditionalRuntimeValue<ResourceStorageEntryBase> entry = r.resources.get(createStorageKey(module, canonicalResourceName));
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private static ResourceStorageEntryBase missingMetadata(Module module, String resourceName, boolean probe) {
        if (!probe) {
            MissingResourceRegistrationUtils.reportResourceAccess(module, resourceName);
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

    public static URL createURL(String resourceName) {
        return createURL(null, resourceName);
    }

    public static URL createURL(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        Enumeration<URL> urls = createURLs(module, resourceName);
        return urls.hasMoreElements() ? urls.nextElement() : null;
    }

    /* Avoid pulling in the URL class when only an InputStream is needed. */
    public static InputStream createInputStream(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }
        ResourceStorageEntryBase entry = findResourceForInputStream(module, resourceName);
        if (entry == MISSING_METADATA_MARKER) {
            traceResourceMissingMetadata(resourceName, moduleName(module));
            MissingResourceRegistrationUtils.reportResourceAccess(module, resourceName);
            return null;
        } else if (entry == null) {
            return null;
        }
        byte[][] data = entry.getData();
        return data.length == 0 ? null : new ByteArrayInputStream(data[0]);
    }

    private static ResourceStorageEntryBase findResourceForInputStream(Module module, String resourceName) {
        ResourceStorageEntryBase result = getAtRuntime(module, resourceName, true);
        if (moduleName(module) == null && (result == MISSING_METADATA_MARKER || result == null)) {
            /*
             * If module is not specified or is an unnamed module and entry was not found as
             * classpath-resource we have to search for the resource in all modules in the image.
             */
            for (Module m : RuntimeModuleSupport.singleton().getBootLayer().modules()) {
                ResourceStorageEntryBase entry = getAtRuntime(m, resourceName, true);
                if (entry != MISSING_METADATA_MARKER) {
                    if (entry != null) {
                        // resource found
                        return entry;
                    } else {
                        // found a negative query. remember this result but keep trying in case some
                        // other module supplies an actual resource.
                        result = null;
                    }
                }
            }
        }
        return result;
    }

    public static Enumeration<URL> createURLs(String resourceName) {
        return createURLs(null, resourceName);
    }

    public static Enumeration<URL> createURLs(Module module, String resourceName) {
        if (resourceName == null) {
            return null;
        }

        boolean missingMetadata = true;

        List<URL> resourcesURLs = new ArrayList<>();
        String canonicalResourceName = NativeImageResourcePathRepresentation.toCanonicalForm(resourceName);
        if (hasTrailingSlash(resourceName)) {
            canonicalResourceName += "/";
        }

        /* If moduleName was unspecified we have to consider all modules in the image */
        if (moduleName(module) == null) {
            for (Module m : RuntimeModuleSupport.singleton().getBootLayer().modules()) {
                ResourceStorageEntryBase entry = getAtRuntime(m, resourceName, true);
                if (entry != MISSING_METADATA_MARKER) {
                    missingMetadata = false;
                    addURLEntries(resourcesURLs, (ResourceStorageEntry) entry, m, canonicalResourceName);
                }
            }
        }
        ResourceStorageEntryBase explicitEntry = getAtRuntime(module, resourceName, true);
        if (explicitEntry != MISSING_METADATA_MARKER) {
            missingMetadata = false;
            addURLEntries(resourcesURLs, (ResourceStorageEntry) explicitEntry, module, canonicalResourceName);
        }

        if (missingMetadata) {
            MissingResourceRegistrationUtils.reportResourceAccess(module, resourceName);
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
        for (int index = 0; index < entry.getData().length; index++) {
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

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }

    @Override
    public PersistFlags preparePersist(ImageSingletonWriter writer) {
        List<String> resourceKeys = new ArrayList<>();
        List<Boolean> resourceRegistrationStates = new ArrayList<>();
        Set<String> patterns = new HashSet<>(previousLayerPatterns);

        var cursor = resources.getEntries();
        while (cursor.advance()) {
            resourceKeys.add(cursor.getKey().toString());
            boolean isNegativeQuery = cursor.getValue().getValueUnconditionally() == NEGATIVE_QUERY_MARKER;
            resourceRegistrationStates.add(!isNegativeQuery);
        }

        for (var entry : previousLayerResources.entrySet()) {
            /*
             * If a complete entry overwrites a negative query from a previous layer, the
             * previousLayerResources map entry needs to be skipped to register the new entry for
             * extension layers.
             */
            if (!resourceKeys.contains(entry.getKey())) {
                resourceKeys.add(entry.getKey());
                resourceRegistrationStates.add(entry.getValue());
            }
        }

        requestedPatterns.getKeys().forEach(p -> patterns.add(p.toString()));

        writer.writeStringList(RESOURCE_KEYS, resourceKeys);
        writer.writeBoolList(RESOURCE_REGISTRATION_STATES, resourceRegistrationStates);
        writer.writeStringList(PATTERNS, patterns.stream().toList());

        return PersistFlags.CREATE;
    }

    @SuppressWarnings("unused")
    public static Object createFromLoader(ImageSingletonLoader loader) {
        List<String> previousLayerResourceKeys = loader.readStringList(RESOURCE_KEYS);
        List<Boolean> previousLayerRegistrationStates = loader.readBoolList(RESOURCE_REGISTRATION_STATES);
        Map<String, Boolean> previousLayerResources = new HashMap<>();

        for (int i = 0; i < previousLayerResourceKeys.size(); ++i) {
            previousLayerResources.put(previousLayerResourceKeys.get(i), previousLayerRegistrationStates.get(i));
        }

        Set<String> previousLayerPatterns = Set.copyOf(loader.readStringList(PATTERNS));

        return new Resources(Collections.unmodifiableMap(previousLayerResources), previousLayerPatterns);
    }
}

@AutomaticallyRegisteredFeature
final class ResourcesFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            ImageSingletons.add(Resources.class, new Resources());
        }
        Resources.currentLayer().setEncoder(SymbolEncoder.singleton());
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        /*
         * The resources embedded in the image heap are read-only at run time. Note that we do not
         * mark the collection data structures as read-only because Java collections have all sorts
         * of lazily initialized fields. Only the byte[] arrays themselves can be safely made
         * read-only.
         */
        for (ConditionalRuntimeValue<ResourceStorageEntryBase> entry : Resources.currentLayer().resources().getValues()) {
            var unconditionalEntry = entry.getValueUnconditionally();
            if (unconditionalEntry.hasData()) {
                for (byte[] resource : unconditionalEntry.getData()) {
                    access.registerAsImmutable(resource);
                }
            }
        }
    }
}
