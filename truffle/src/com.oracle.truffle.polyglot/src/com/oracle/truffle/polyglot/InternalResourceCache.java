/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.provider.InternalResourceProvider;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class InternalResourceCache {

    private static final char[] FILE_SYSTEM_SPECIAL_CHARACTERS = {'/', '\\', ':'};
    private static final Map<Collection<AbstractClassLoaderSupplier>, Map<String, Map<String, Supplier<InternalResourceCache>>>> optionalInternalResourcesCaches = new HashMap<>();
    private static final Map<String, Map<String, Supplier<InternalResourceCache>>> nativeImageCache = TruffleOptions.AOT ? new HashMap<>() : null;

    /**
     * Recomputed before the analyses by a substitution in the {@code TruffleBaseFeature} based on
     * the {@code CopyLanguageResources} option value. The field must not be declared as
     * {@code final} to make the substitution function correctly.
     */
    private static boolean useInternalResources = true;

    private final String id;
    private final String resourceId;
    private final Supplier<InternalResource> resourceFactory;

    /**
     * This field is reset to {@code null} by the {@code TruffleBaseFeature} before writing the
     * native image heap.
     */
    private InternalResourceRoots.Root owningRoot;
    /**
     * This field is reset to {@code null} by the {@code TruffleBaseFeature} before writing the
     * native image heap.
     */
    private volatile Path path;

    InternalResourceCache(String languageId, String resourceId, Supplier<InternalResource> resourceFactory) {
        this.id = Objects.requireNonNull(languageId);
        this.resourceId = Objects.requireNonNull(resourceId);
        this.resourceFactory = Objects.requireNonNull(resourceFactory);
    }

    String getResourceId() {
        return resourceId;
    }

    Path getPathOrNull() {
        return path;
    }

    Path getPath(PolyglotEngineImpl polyglotEngine) throws IOException {
        if (usesInternalResources()) {
            Path result = path;
            if (result == null) {
                synchronized (this) {
                    result = path;
                    if (result == null) {
                        result = installResource((resource) -> EngineAccessor.LANGUAGE.createInternalResourceEnv(resource, () -> polyglotEngine.inEnginePreInitialization));
                        path = result;
                    }
                }
            }
            return result;
        } else {
            throw new IllegalArgumentException("Internal resources are restricted. To enable them, use '-H:+CopyLanguageResources' during the native image build.");
        }
    }

    void initializeOwningRoot(InternalResourceRoots.Root root) {
        assert owningRoot == null;
        assert path == null;
        owningRoot = root;
        path = switch (root.kind()) {
            case RESOURCE -> root.path();
            case COMPONENT -> root.path().resolve(sanitize(resourceId));
            case UNVERSIONED -> findStandaloneResourceRoot(root.path());
            case VERSIONED -> null;
        };
        if (path != null && InternalResourceRoots.isTraceInternalResourceEvents()) {
            /*
             * The path for the VERSIONED resource is logged when the resource is requested.
             * Computation of this path is expensive and involves a call to
             * InternalResource#versionHash(). Additionally, we log whether the resource was
             * unpacked or reused.
             */
            String hint = switch (root.kind()) {
                case RESOURCE -> InternalResourceRoots.overriddenResourceRootProperty(id, resourceId) + " system property";
                case COMPONENT -> InternalResourceRoots.overriddenComponentRootProperty(id) + " system property";
                case UNVERSIONED -> "internal resource cache root directory";
                default -> throw CompilerDirectives.shouldNotReachHere(root.kind().name());
            };
            InternalResourceRoots.logInternalResourceEvent("Resolved a pre-created directory for the internal resource %s::%s to: %s, determined by the %s with the value %s.",
                            id, resourceId, path, hint, root.path());
        }
    }

    /**
     * Resets state for unit test execution. This method is intended only for testing.
     */
    void clearCache() {
        owningRoot = null;
        path = null;
    }

    /**
     * Installs truffleattach library. Used reflectively by
     * {@code com.oracle.truffle.runtime.ModulesSupport}. The {@code ModulesSupport} is initialized
     * before the Truffle runtime is created and accessor classes are initialized. For this reason,
     * it cannot use {@code EngineSupport} to call this method, nor can this method use any
     * accessor.
     */
    static Path installRuntimeResource(InternalResource resource) throws IOException {
        InternalResourceCache cache = createRuntimeResourceCache(resource);
        synchronized (cache) {
            Path result = cache.path;
            if (result == null) {
                result = cache.installResource(InternalResourceCache::createInternalResourceEnvReflectively);
                cache.path = result;
            }
            return result;
        }
    }

    private static InternalResourceCache createRuntimeResourceCache(InternalResource resource) {
        InternalResource.Id id = resource.getClass().getAnnotation(InternalResource.Id.class);
        assert id != null : resource.getClass() + " must be annotated by @InternalResource.Id";
        InternalResourceCache cache = new InternalResourceCache(PolyglotEngineImpl.ENGINE_ID, id.value(), () -> resource);
        InternalResourceRoots.initializeRuntimeResource(cache);
        return cache;
    }

    private static InternalResource.Env createInternalResourceEnvReflectively(InternalResource resource) {
        try {
            Constructor<InternalResource.Env> newEnv = InternalResource.Env.class.getDeclaredConstructor(InternalResource.class, BooleanSupplier.class);
            newEnv.setAccessible(true);
            return newEnv.newInstance(resource, (BooleanSupplier) () -> TruffleOptions.AOT);
        } catch (ReflectiveOperationException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private Path installResource(Function<InternalResource, InternalResource.Env> resourceEnvProvider) throws IOException {
        Objects.requireNonNull(resourceEnvProvider, "ResourceEnvProvider must be non-null.");
        assert Thread.holdsLock(this) : "Unpacking must be called under lock";
        assert owningRoot.kind() == InternalResourceRoots.Root.Kind.VERSIONED;
        assert !ImageInfo.inImageRuntimeCode() : "Must not be called in the image execution time.";
        InternalResource resource = resourceFactory.get();
        InternalResource.Env env = resourceEnvProvider.apply(resource);
        String versionHash = resource.versionHash(env);
        if (versionHash.getBytes().length > 128) {
            throw new IOException("The version hash length is restricted to a maximum of 128 bytes.");
        }
        Path target = owningRoot.path().resolve(Path.of(sanitize(id), sanitize(resourceId), sanitize(versionHash)));
        if (!Files.exists(target)) {
            InternalResourceRoots.logInternalResourceEvent("Resolved a directory for the internal resource %s::%s to: %s, unpacking resource files.", id, resourceId, target);
            Path parent = target.getParent();
            if (parent == null) {
                throw CompilerDirectives.shouldNotReachHere("Target must have a parent directory but was " + target);
            }
            Path owner = Files.createDirectories(Objects.requireNonNull(parent));
            Path tmpDir = Files.createTempDirectory(owner, null);
            resource.unpackFiles(env, tmpDir);
            try {
                Files.move(tmpDir, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException existsException) {
                // race with other process that already moved the folder just unlink the tmp
                // directory
                unlink(tmpDir);
            } catch (FileSystemException fsException) {
                // On some filesystem implementations, the generic FileSystemException is thrown
                // instead of FileAlreadyExistsException. We need to check if this is the case.
                if (Files.isDirectory(target)) {
                    unlink(tmpDir);
                }
            }
        } else {
            InternalResourceRoots.logInternalResourceEvent("Resolved a directory for the internal resource %s::%s to: %s, using existing resource files.",
                            id, resourceId, target);
            verifyResourceRoot(target);
        }
        return target;
    }

    private static void verifyResourceRoot(Path resourceRoot) throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be a directory.");
        }
        if (!Files.isReadable(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be readable.");
        }
    }

    private Path findStandaloneResourceRoot(Path root) {
        return root.resolve(Path.of(sanitize(id), sanitize(resourceId)));
    }

    private static String sanitize(String pathElement) {
        String result = pathElement;
        for (char fileSystemsSpecialChar : FILE_SYSTEM_SPECIAL_CHARACTERS) {
            result = result.replace(fileSystemsSpecialChar, '_');
        }
        return result;
    }

    /**
     * Returns true if internal resources are enabled. Internal resources can be disabled in the
     * native image using {-H:-CopyLanguageResources} option.
     */
    public static boolean usesInternalResources() {
        return useInternalResources;
    }

    /**
     * Collects optional internal resources for native-image build. This method is called
     * reflectively by the {@code TruffleBaseFeature#initializeTruffleReflectively}.
     */
    static void initializeNativeImageState(ClassLoader nativeImageClassLoader) {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageCache.putAll(collectOptionalResources(List.of(new EngineAccessor.StrongClassLoaderSupplier(nativeImageClassLoader))));
    }

    /**
     * Resets cache roots after closed word analyses. This method is called reflectively by the
     * {@code TruffleBaseFeature#afterAnalysis}.
     */
    static void resetNativeImageState() {
        nativeImageCache.clear();
    }

    /**
     * Unpacks internal resources after native-image write. This method is called reflectively by
     * the {@code TruffleBaseFeature#afterAnalysis}.
     */
    static boolean copyResourcesForNativeImage(Path target, String... components) throws IOException {
        boolean result = false;
        Collection<LanguageCache> languages;
        Collection<InstrumentCache> instruments;
        if (components.length == 0) {
            languages = LanguageCache.languages().values();
            instruments = InstrumentCache.load();
        } else {
            Set<String> requiredComponentIds = new HashSet<>();
            Collections.addAll(requiredComponentIds, components);
            Set<String> requiredLanguageIds = new HashSet<>(LanguageCache.languages().keySet());
            requiredLanguageIds.retainAll(requiredComponentIds);
            Set<String> requiredInstrumentIds = InstrumentCache.load().stream().map(InstrumentCache::getId).collect(Collectors.toSet());
            requiredInstrumentIds.retainAll(requiredComponentIds);
            requiredComponentIds.removeAll(requiredLanguageIds);
            requiredComponentIds.removeAll(requiredInstrumentIds);
            if (!requiredComponentIds.isEmpty()) {
                Set<String> installedComponents = new TreeSet<>(LanguageCache.languages().keySet());
                InstrumentCache.load().stream().map(InstrumentCache::getId).forEach(installedComponents::add);
                throw new IllegalArgumentException(String.format("Components with ids %s are not installed. Installed components are: %s.",
                                String.join(", ", requiredComponentIds),
                                String.join(", ", installedComponents)));
            }
            Set<LanguageCache> requiredLanguages = new HashSet<>(LanguageCache.internalLanguages());
            for (String requiredLanguageId : requiredLanguageIds) {
                requiredLanguages.addAll(LanguageCache.computeTransitiveLanguageDependencies(requiredLanguageId));
            }
            languages = requiredLanguages;
            Set<InstrumentCache> requiredInstruments = new HashSet<>(InstrumentCache.internalInstruments());
            InstrumentCache.load().stream().filter((ic) -> requiredInstrumentIds.contains(ic.getId())).forEach(requiredInstruments::add);
            instruments = requiredInstruments;
        }
        for (LanguageCache language : languages) {
            for (InternalResourceCache cache : language.getResources()) {
                result |= cache.copyResourcesForNativeImage(target);
            }
        }
        for (InstrumentCache instrument : instruments) {
            for (InternalResourceCache cache : instrument.getResources()) {
                result |= cache.copyResourcesForNativeImage(target);
            }
        }
        // Always install engine resources
        for (InternalResourceCache cache : getEngineResources()) {
            result |= cache.copyResourcesForNativeImage(target);
        }
        return result;
    }

    private boolean copyResourcesForNativeImage(Path target) throws IOException {
        Path root = findStandaloneResourceRoot(target);
        unlink(root);
        Files.createDirectories(root);
        InternalResource resource = resourceFactory.get();
        InternalResource.Env env = EngineAccessor.LANGUAGE.createInternalResourceEnv(resource, () -> false);
        resource.unpackFiles(env, root);
        if (isEmpty(root)) {
            Files.deleteIfExists(root);
            return false;
        } else {
            return true;
        }
    }

    static Collection<String> getEngineResourceIds() {
        Map<String, Supplier<InternalResourceCache>> engineResources = loadOptionalInternalResources(EngineAccessor.locatorOrDefaultLoaders()).get(PolyglotEngineImpl.ENGINE_ID);
        return engineResources != null ? engineResources.keySet() : List.of();
    }

    static Collection<InternalResourceCache> getEngineResources() {
        Map<String, Supplier<InternalResourceCache>> engineResources = loadOptionalInternalResources(EngineAccessor.locatorOrDefaultLoaders()).get(PolyglotEngineImpl.ENGINE_ID);
        if (engineResources != null) {
            return engineResources.values().stream().map(Supplier::get).collect(Collectors.toList());
        } else {
            return List.of();
        }
    }

    static InternalResourceCache getEngineResource(String resourceId) {
        Map<String, Supplier<InternalResourceCache>> engineResources = loadOptionalInternalResources(EngineAccessor.locatorOrDefaultLoaders()).get(PolyglotEngineImpl.ENGINE_ID);
        Supplier<InternalResourceCache> resourceSupplier = engineResources != null ? engineResources.get(resourceId) : null;
        return resourceSupplier != null ? resourceSupplier.get() : null;
    }

    static Map<String, Map<String, Supplier<InternalResourceCache>>> loadOptionalInternalResources(List<AbstractClassLoaderSupplier> suppliers) {
        if (TruffleOptions.AOT) {
            assert nativeImageCache != null;
            return nativeImageCache;
        }
        synchronized (InternalResourceCache.class) {
            Map<String, Map<String, Supplier<InternalResourceCache>>> cache = optionalInternalResourcesCaches.get(suppliers);
            if (cache == null) {
                cache = collectOptionalResources(suppliers);
                optionalInternalResourcesCaches.put(suppliers, cache);
            }
            return cache;
        }
    }

    private static Map<String, Map<String, Supplier<InternalResourceCache>>> collectOptionalResources(List<AbstractClassLoaderSupplier> suppliers) {
        Map<String, Map<String, Supplier<InternalResourceCache>>> cache = new HashMap<>();
        for (EngineAccessor.AbstractClassLoaderSupplier supplier : suppliers) {
            ClassLoader loader = supplier.get();
            if (loader == null) {
                continue;
            }
            StreamSupport.stream(ServiceLoader.load(InternalResourceProvider.class, loader).spliterator(), false).filter((p) -> supplier.accepts(p.getClass())).forEach((p) -> {
                ModuleUtils.exportTransitivelyTo(p.getClass().getModule());
                String componentId = EngineAccessor.LANGUAGE_PROVIDER.getInternalResourceComponentId(p);
                String resourceId = EngineAccessor.LANGUAGE_PROVIDER.getInternalResourceId(p);
                var componentOptionalResources = cache.computeIfAbsent(componentId, (k) -> new HashMap<>());
                var resourceSupplier = new OptionalResourceSupplier(p);
                var existing = (OptionalResourceSupplier) componentOptionalResources.put(resourceId, resourceSupplier);
                if (existing != null && !hasSameCodeSource(resourceSupplier, existing)) {
                    throw throwDuplicateOptionalResourceException(existing.get(), resourceSupplier.get());
                }
            });
        }
        return cache;
    }

    private static boolean hasSameCodeSource(OptionalResourceSupplier first, OptionalResourceSupplier second) {
        return first.optionalResourceProvider.getClass() == second.optionalResourceProvider.getClass();
    }

    static RuntimeException throwDuplicateOptionalResourceException(InternalResourceCache existing, InternalResourceCache duplicate) {
        String message = String.format("Duplicate optional resource id %s for component %s. First optional resource [%s]. Second optional resource [%s].",
                        existing.resourceId,
                        existing.id,
                        formatResourceLocation(existing.resourceFactory.get()),
                        formatResourceLocation(duplicate.resourceFactory.get()));
        throw new IllegalStateException(message);
    }

    private static String formatResourceLocation(InternalResource internalResource) {
        StringBuilder sb = new StringBuilder();
        sb.append("Internal resource class ").append(internalResource.getClass().getName());
        CodeSource source = internalResource.getClass().getProtectionDomain().getCodeSource();
        URL url = source != null ? source.getLocation() : null;
        if (url != null) {
            sb.append(", Loaded from ").append(url);
        }
        return sb.toString();
    }

    private static boolean isEmpty(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.findAny().isEmpty();
        }
    }

    private static void unlink(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    unlink(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Override
    public String toString() {
        return "InternalResourceCache[" +
                        "componentId='" + id + '\'' +
                        ", resourceId='" + resourceId + '\'' +
                        ", resourceRoot=" + path +
                        '}';
    }

    private static final class OptionalResourceSupplier implements Supplier<InternalResourceCache> {
        private final InternalResourceProvider optionalResourceProvider;
        private volatile InternalResourceCache cachedResource;

        private OptionalResourceSupplier(InternalResourceProvider optionalResourceProvider) {
            Objects.requireNonNull(optionalResourceProvider, "OptionalResourceProvider must be non null");
            this.optionalResourceProvider = optionalResourceProvider;
        }

        @Override
        public InternalResourceCache get() {
            InternalResourceCache res = cachedResource;
            if (res == null) {
                synchronized (this) {
                    res = cachedResource;
                    if (res == null) {
                        res = new InternalResourceCache(
                                        EngineAccessor.LANGUAGE_PROVIDER.getInternalResourceComponentId(optionalResourceProvider),
                                        EngineAccessor.LANGUAGE_PROVIDER.getInternalResourceId(optionalResourceProvider),
                                        () -> EngineAccessor.LANGUAGE_PROVIDER.createInternalResource(optionalResourceProvider));
                        cachedResource = res;
                    }
                }
            }
            return res;
        }
    }
}

/**
 * A C entry point utilized for determining the shared library's location. This entry point is
 * explicitly activated by the {@code TruffleBaseFeature} through reflective invocation of the
 * {@link InternalResourceCacheSymbol#initialize()} method.
 */
final class InternalResourceCacheSymbol implements BooleanSupplier {

    static final CEntryPointLiteral<CFunctionPointer> SYMBOL = CEntryPointLiteral.create(InternalResourceCacheSymbol.class,
                    "internalResourceCacheSymbol", IsolateThread.class);

    private InternalResourceCacheSymbol() {
    }

    @Override
    public boolean getAsBoolean() {
        return ImageSingletons.contains(InternalResourceCacheSymbol.class);
    }

    /**
     * Enables {@link #internalResourceCacheSymbol(IsolateThread)} entrypoint. Called reflectively
     * by the {@code TruffleBaseFeature#afterRegistration()}.
     */
    static void initialize() {
        ImageSingletons.add(InternalResourceCacheSymbol.class, new InternalResourceCacheSymbol());
    }

    @CEntryPoint(name = "graal_resource_cache_symbol", publishAs = CEntryPoint.Publish.SymbolOnly, include = InternalResourceCacheSymbol.class)
    @SuppressWarnings("unused")
    private static void internalResourceCacheSymbol(IsolateThread thread) {
    }
}
