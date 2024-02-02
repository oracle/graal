/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;
import com.oracle.truffle.polyglot.EngineAccessor.StrongClassLoaderSupplier;
import org.graalvm.home.HomeFinder;
import org.graalvm.polyglot.SandboxPolicy;

/**
 * Ahead-of-time initialization. If the JVM is started with {@link TruffleOptions#AOT}, it populates
 * runtimeCache with languages found in application classloader.
 */
final class LanguageCache implements Comparable<LanguageCache> {
    private static final Map<String, LanguageCache> nativeImageCache = TruffleOptions.AOT ? new HashMap<>() : null;
    private static final Map<String, LanguageCache> nativeImageMimes = TruffleOptions.AOT ? new HashMap<>() : null;
    private static final Set<String> languagesOverridingPatchContext = TruffleOptions.AOT ? new HashSet<>() : null;
    private static final Map<Collection<AbstractClassLoaderSupplier>, Map<String, LanguageCache>> runtimeCaches = new HashMap<>();
    private static volatile Map<String, LanguageCache> runtimeMimes;
    @CompilationFinal private static volatile int maxStaticIndex;
    private final String className;
    private final Set<String> mimeTypes;
    private final Set<String> characterMimeTypes;
    private final String defaultMimeType;
    private final Set<String> dependentLanguages;
    private final String id;
    private final String name;
    private final String implementationName;
    private final String version;
    private final boolean interactive;
    private final boolean internal;
    private final boolean needsAllEncodings;
    private final Set<String> services;
    private final ContextPolicy contextPolicy;
    private final ProviderAdapter providerAdapter;
    private final String website;
    private final SandboxPolicy sandboxPolicy;
    private volatile List<FileTypeDetector> fileTypeDetectors;
    private volatile Set<Class<? extends Tag>> providedTags;
    private final Map<String, InternalResourceCache> internalResources;
    private int staticIndex;

    /*
     * When building a native image, this field is reset to null so that directories from the image
     * build do not leak into the image heap. The value is lazily recomputed at image run time.
     */
    private String languageHome;

    private LanguageCache(String id, String name, String implementationName, String version, String className,
                    String languageHome, Set<String> characterMimeTypes, Set<String> byteMimeTypes, String defaultMimeType,
                    Set<String> dependentLanguages, boolean interactive, boolean internal, boolean needsAllEncodings, Set<String> services,
                    ContextPolicy contextPolicy, ProviderAdapter providerAdapter, String website, SandboxPolicy sandboxPolicy,
                    Map<String, InternalResourceCache> internalResources) {
        assert providerAdapter != null : "Provider must be non null";
        this.className = className;
        this.name = name;
        this.implementationName = implementationName;
        this.version = version;
        this.characterMimeTypes = characterMimeTypes;
        this.mimeTypes = new TreeSet<>();
        this.mimeTypes.addAll(characterMimeTypes);
        this.mimeTypes.addAll(byteMimeTypes);
        this.defaultMimeType = mimeTypes.size() == 1 && defaultMimeType == null ? mimeTypes.iterator().next() : defaultMimeType;
        this.dependentLanguages = dependentLanguages;
        this.id = id;
        this.interactive = interactive;
        this.internal = internal;
        this.needsAllEncodings = needsAllEncodings;
        this.languageHome = languageHome;
        this.services = services;
        this.contextPolicy = contextPolicy;
        this.providerAdapter = providerAdapter;
        this.website = website;
        this.sandboxPolicy = sandboxPolicy;
        this.internalResources = internalResources;
    }

    /**
     * Returns the maximum index used by any loaded language. This index updates when new languages
     * are loaded. Make sure you only read this index when all languages are already loaded.
     */
    static int getMaxStaticIndex() {
        return maxStaticIndex;
    }

    static LanguageCache createHostLanguageCache(TruffleLanguage<?> languageInstance, String... services) {
        HostLanguageProvider hostLanguageProvider = new HostLanguageProvider(languageInstance, services);
        LanguageCache cache = new LanguageCache(
                        PolyglotEngineImpl.HOST_LANGUAGE_ID,
                        "Host",
                        "Host",
                        System.getProperty("java.version"),
                        languageInstance.getClass().getName(),
                        null,
                        Collections.emptySet(),
                        Collections.emptySet(),
                        null,
                        Collections.emptySet(),
                        false, false, false, hostLanguageProvider.getServicesClassNames(),
                        ContextPolicy.SHARED, new ModuleAwareProvider(hostLanguageProvider), "", SandboxPolicy.UNTRUSTED, Map.of());
        cache.staticIndex = PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
        return cache;
    }

    static Map<String, LanguageCache> languageMimes() {
        if (TruffleOptions.AOT) {
            return nativeImageMimes;
        }
        Map<String, LanguageCache> cache = runtimeMimes;
        if (cache == null) {
            synchronized (LanguageCache.class) {
                cache = runtimeMimes;
                if (cache == null) {
                    runtimeMimes = cache = createMimes();
                }
            }
        }
        return cache;
    }

    private static Map<String, LanguageCache> createMimes() {
        Map<String, LanguageCache> mimes = new LinkedHashMap<>();
        for (LanguageCache cache : languages().values()) {
            for (String mime : cache.getMimeTypes()) {
                mimes.put(mime, cache);
            }
        }
        return mimes;
    }

    /**
     * Returns {@code true} if any registered language has {@link Registration#needsAllEncodings()}
     * set.
     * <p>
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    public static boolean getNeedsAllEncodings() {
        for (LanguageCache cache : languages().values()) {
            if (cache.isNeedsAllEncodings()) {
                return true;
            }
        }
        return false;
    }

    static Map<String, LanguageCache> languages() {
        return loadLanguages(EngineAccessor.locatorOrDefaultLoaders());
    }

    static Collection<LanguageCache> internalLanguages() {
        Set<LanguageCache> result = new HashSet<>();
        for (Map.Entry<String, LanguageCache> e : languages().entrySet()) {
            if (e.getValue().isInternal()) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    static Collection<LanguageCache> computeTransitiveLanguageDependencies(String id) {
        Map<String, LanguageCache> languagesById = languages();
        LanguageCache root = languagesById.get(id);
        if (root == null) {
            throw new IllegalArgumentException(String.format("A language with id '%s' is not installed. Installed languages are: %s.",
                            id, String.join(", ", languagesById.keySet())));
        }
        Set<LanguageCache> result = new HashSet<>();
        Deque<LanguageCache> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            LanguageCache current = todo.removeFirst();
            if (result.add(current)) {
                current.getDependentLanguages().stream().map(languagesById::get).filter(Objects::nonNull).forEach(todo::add);
            }
        }
        return result;
    }

    static Map<String, LanguageCache> loadLanguages(List<AbstractClassLoaderSupplier> classLoaders) {
        if (TruffleOptions.AOT) {
            return nativeImageCache;
        }
        synchronized (LanguageCache.class) {
            Map<String, LanguageCache> cache = runtimeCaches.get(classLoaders);
            if (cache == null) {
                cache = createLanguages(classLoaders);
                runtimeCaches.put(classLoaders, cache);
            }
            return cache;
        }
    }

    private static synchronized Map<String, LanguageCache> createLanguages(List<AbstractClassLoaderSupplier> suppliers) {
        List<LanguageCache> caches = new ArrayList<>();
        Map<String, Map<String, Supplier<InternalResourceCache>>> optionalResources = InternalResourceCache.loadOptionalInternalResources(suppliers);
        for (AbstractClassLoaderSupplier supplier : suppliers) {
            ClassLoader loader = supplier.get();
            if (loader == null) {
                continue;
            }
            loadProviders(loader).filter((p) -> supplier.accepts(p.getProviderClass())).forEach((p) -> loadLanguageImpl(p, caches, optionalResources));
            if (supplier.supportsLegacyProviders()) {
                loadLegacyProviders(loader).filter((p) -> supplier.accepts(p.getProviderClass())).forEach((p) -> loadLanguageImpl(p, caches, optionalResources));
            }
        }

        Map<String, LanguageCache> idToCache = new LinkedHashMap<>();
        for (LanguageCache languageCache : caches) {
            LanguageCache prev = idToCache.put(languageCache.getId(), languageCache);
            if (prev != null && (!prev.getClassName().equals(languageCache.getClassName()) || !hasSameCodeSource(prev, languageCache))) {
                String message = String.format("Duplicate language id %s. First language [%s]. Second language [%s].",
                                languageCache.getId(),
                                formatLanguageLocation(prev),
                                formatLanguageLocation(languageCache));
                throw new IllegalStateException(message);
            }
        }
        int languageId = PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
        for (LanguageCache cache : idToCache.values()) {
            cache.staticIndex = ++languageId;
        }
        /*
         * maxLanguagesCount only needs to grow, otherwise we might access the fast thread local
         * array out of bounds.
         */
        maxStaticIndex = Math.max(maxStaticIndex, languageId);
        return idToCache;
    }

    @SuppressWarnings("deprecation")
    private static Stream<? extends ProviderAdapter> loadLegacyProviders(ClassLoader loader) {
        ModuleUtils.exportToUnnamedModuleOf(loader);
        return StreamSupport.stream(ServiceLoader.load(TruffleLanguage.Provider.class, loader).spliterator(), false).map(LegacyProvider::new);
    }

    private static Stream<? extends ProviderAdapter> loadProviders(ClassLoader loader) {
        return StreamSupport.stream(ServiceLoader.load(TruffleLanguageProvider.class, loader).spliterator(), false).map(ModuleAwareProvider::new);
    }

    private static boolean hasSameCodeSource(LanguageCache first, LanguageCache second) {
        return first.providerAdapter.getProviderClass() == second.providerAdapter.getProviderClass();
    }

    private static void loadLanguageImpl(ProviderAdapter providerAdapter, List<LanguageCache> into, Map<String, Map<String, Supplier<InternalResourceCache>>> optionalResources) {
        Class<?> providerClass = providerAdapter.getProviderClass();
        Module providerModule = providerClass.getModule();
        ModuleUtils.exportTransitivelyTo(providerModule);
        Registration reg = providerClass.getAnnotation(Registration.class);
        if (reg == null) {
            emitWarning("Warning Truffle language ignored: Provider %s is missing @Registration annotation.", providerClass);
            return;
        }
        String className = providerAdapter.getLanguageClassName();
        String name = reg.name();
        String id = reg.id();
        if (id == null || id.isEmpty()) {
            if (name.isEmpty()) {
                int lastIndex = className.lastIndexOf('$');
                if (lastIndex == -1) {
                    lastIndex = className.lastIndexOf('.');
                }
                id = className.substring(lastIndex + 1);
            } else {
                // TODO GR-38632 remove this hack for single character languages
                if (name.length() == 1) {
                    id = name;
                } else {
                    id = name.toLowerCase();
                }
            }
        }
        /*
         * We utilize the `HomeFinder#getLanguageHomes()` function because it works for legacy,
         * standalone, and unchained builds. It's important to note that this code is never
         * reachable during native image execution, and thus, using it doesn't introduce
         * `ProcessProperties#getExecutableName` function in the generated native image.
         */
        Path languageHomePath = HomeFinder.getInstance().getLanguageHomes().get(id);
        String languageHome = languageHomePath != null ? languageHomePath.toString() : null;
        String implementationName = reg.implementationName();
        String version = reg.version();
        TreeSet<String> characterMimes = new TreeSet<>();
        Collections.addAll(characterMimes, reg.characterMimeTypes());
        TreeSet<String> byteMimeTypes = new TreeSet<>();
        Collections.addAll(byteMimeTypes, reg.byteMimeTypes());
        String defaultMime = reg.defaultMimeType();
        if (defaultMime.isEmpty()) {
            defaultMime = null;
        }
        TreeSet<String> dependentLanguages = new TreeSet<>();
        Collections.addAll(dependentLanguages, reg.dependentLanguages());
        boolean interactive = reg.interactive();
        boolean internal = reg.internal();
        boolean needsAllEncodings = reg.needsAllEncodings();
        Set<String> servicesClassNames = new TreeSet<>(providerAdapter.getServicesClassNames());
        SandboxPolicy sandboxPolicy = reg.sandbox();
        Map<String, InternalResourceCache> resources = new HashMap<>();
        for (String resourceId : providerAdapter.getInternalResourceIds()) {
            resources.put(resourceId, new InternalResourceCache(id, resourceId, () -> providerAdapter.createInternalResource(resourceId)));
        }
        for (Map.Entry<String, Supplier<InternalResourceCache>> resourceSupplier : optionalResources.getOrDefault(id, Map.of()).entrySet()) {
            InternalResourceCache resource = resourceSupplier.getValue().get();
            InternalResourceCache old = resources.put(resourceSupplier.getKey(), resource);
            if (old != null) {
                throw InternalResourceCache.throwDuplicateOptionalResourceException(old, resource);
            }
        }
        into.add(new LanguageCache(id, name, implementationName, version, className, languageHome,
                        characterMimes, byteMimeTypes, defaultMime, dependentLanguages, interactive, internal, needsAllEncodings,
                        servicesClassNames, reg.contextPolicy(), providerAdapter, reg.website(), sandboxPolicy, Collections.unmodifiableMap(resources)));
    }

    private static String formatLanguageLocation(LanguageCache languageCache) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language class ").append(languageCache.getClassName());

        CodeSource source = languageCache.providerAdapter.getProviderClass().getProtectionDomain().getCodeSource();
        URL url = source != null ? source.getLocation() : null;
        if (url != null) {
            sb.append(", Loaded from " + url);
        }
        return sb.toString();
    }

    private static String getLanguageHomeFromSystemProperty(String languageId) {
        return toRealStringPath("org.graalvm.language." + languageId + ".home");
    }

    private static String toRealStringPath(String propertyName) {
        String path = System.getProperty(propertyName);
        if (path != null) {
            try {
                path = Path.of(path).toRealPath().toString();
            } catch (NoSuchFileException nsfe) {
                return path;
            } catch (IOException ioe) {
                throw CompilerDirectives.shouldNotReachHere(ioe);
            }
        }
        return path;
    }

    static boolean overridesPathContext(String languageId) {
        assert TruffleOptions.AOT : "Only supported in native image";
        return languagesOverridingPatchContext.contains(languageId);
    }

    static void resetNativeImageCacheLanguageHomes() {
        synchronized (LanguageCache.class) {
            if (nativeImageCache != null) {
                resetNativeImageCacheLanguageHomes(nativeImageCache);
            }
            for (Map<String, LanguageCache> caches : runtimeCaches.values()) {
                resetNativeImageCacheLanguageHomes(caches);
            }
        }
    }

    private static void resetNativeImageCacheLanguageHomes(Map<String, LanguageCache> caches) {
        for (LanguageCache cache : caches.values()) {
            cache.languageHome = null;
        }
    }

    /**
     * Initializes state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     * @param imageClassLoader class loader passed by the image builder.
     */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState(ClassLoader imageClassLoader) {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageCache.putAll(createLanguages(List.of(new StrongClassLoaderSupplier(imageClassLoader))));
        nativeImageMimes.putAll(createMimes());
        for (LanguageCache languageCache : nativeImageCache.values()) {
            try {
                Class<?> clz = Class.forName(languageCache.className, false, imageClassLoader);
                for (Method m : clz.getDeclaredMethods()) {
                    if (m.getName().equals("patchContext")) {
                        languagesOverridingPatchContext.add(languageCache.id);
                        break;
                    }
                }
            } catch (ReflectiveOperationException roe) {
                emitWarning("Failed to lookup patchContext method due to ", roe);
            }
        }
    }

    /*
     * Collect languages included in a native image.
     *
     * NOTE: this method is called reflectively by TruffleBaseFeature
     */
    @SuppressWarnings("unused")
    private static Set<String> collectLanguages() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        Set<String> toRet = new HashSet<>();
        for (LanguageCache languageCache : nativeImageCache.values()) {
            toRet.add(languageCache.id);
        }
        return toRet;
    }

    /**
     * Resets the state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        nativeImageCache.clear();
        nativeImageMimes.clear();
    }

    /**
     * Allows removal of loaded languages during native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void removeLanguageFromNativeImage(String languageId) {
        assert TruffleOptions.AOT : "Only supported during image generation";
        assert nativeImageCache.containsKey(languageId);
        LanguageCache cache = nativeImageCache.remove(languageId);
        if (cache != null) {
            for (String mime : cache.getMimeTypes()) {
                if (nativeImageCache.get(mime) == cache) {
                    nativeImageMimes.remove(mime);
                }
            }
        }
    }

    /**
     * Returns an index that allows to identify this language for the entire host process. This
     * index can be used and cached statically.
     */
    int getStaticIndex() {
        return staticIndex;
    }

    public int compareTo(LanguageCache o) {
        return id.compareTo(o.id);
    }

    String getId() {
        return id;
    }

    Set<String> getMimeTypes() {
        return mimeTypes;
    }

    String getDefaultMimeType() {
        return defaultMimeType;
    }

    boolean isCharacterMimeType(String mimeType) {
        return characterMimeTypes.contains(mimeType);
    }

    String getName() {
        return name;
    }

    String getImplementationName() {
        return implementationName;
    }

    Set<String> getDependentLanguages() {
        return dependentLanguages;
    }

    String getVersion() {
        return version;
    }

    String getClassName() {
        return className;
    }

    boolean isInternal() {
        return internal;
    }

    boolean isInteractive() {
        return interactive;
    }

    public boolean isNeedsAllEncodings() {
        return needsAllEncodings;
    }

    String getLanguageHome() {
        if (languageHome == null) {
            /*
             * In the legacy build, the language home property is set by the GraalVMLocator at
             * startup. We cannot use the HomeFinder#getLanguageHomes() function because it would
             * make the ProcessProperties#getExecutableName() reachable.
             */
            languageHome = getLanguageHomeFromSystemProperty(id);
        }
        return languageHome;
    }

    TruffleLanguage<?> loadLanguage() {
        return providerAdapter.create();
    }

    @SuppressWarnings("unchecked")
    Set<? extends Class<? extends Tag>> getProvidedTags() {
        Set<Class<? extends Tag>> res = providedTags;
        if (res == null) {
            ProvidedTags tags = providerAdapter.getProviderClass().getAnnotation(ProvidedTags.class);
            if (tags == null) {
                res = Collections.emptySet();
            } else {
                res = new HashSet<>();
                Collections.addAll(res, (Class<? extends Tag>[]) tags.value());
                res = Collections.unmodifiableSet(res);
            }
            providedTags = res;
        }
        return res;
    }

    ContextPolicy getPolicy() {
        return contextPolicy;
    }

    Collection<String> getServices() {
        return services;
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    List<? extends FileTypeDetector> getFileTypeDetectors() {
        List<FileTypeDetector> result = fileTypeDetectors;
        if (result == null) {
            result = providerAdapter.createFileTypeDetectors();
            fileTypeDetectors = result;
        }
        return result;
    }

    InternalResourceCache getResourceCache(String resourceId) {
        return internalResources.get(resourceId);
    }

    Collection<String> getResourceIds() {
        return internalResources.keySet();
    }

    Collection<InternalResourceCache> getResources() {
        return internalResources.values();
    }

    @Override
    public String toString() {
        return "LanguageCache [id=" + id + ", name=" + name + ", implementationName=" + implementationName + ", version=" + version + ", className=" + className + ", services=" + services + "]";
    }

    String getWebsite() {
        return website;
    }

    SandboxPolicy getSandboxPolicy() {
        return sandboxPolicy;
    }

    private static void emitWarning(String message, Object... args) {
        PrintStream out = System.err;
        out.printf(message + "%n", args);
    }

    private static final class HostLanguageProvider extends TruffleLanguageProvider {

        private final TruffleLanguage<?> languageInstance;
        private final Set<String> servicesClassNames;

        HostLanguageProvider(TruffleLanguage<?> languageInstance, String... services) {
            assert languageInstance != null : "LanguageInstance must be non null.";
            this.languageInstance = languageInstance;
            if (services.length == 0) {
                servicesClassNames = Collections.emptySet();
            } else {
                Set<String> treeSet = new TreeSet<>();
                Collections.addAll(treeSet, services);
                servicesClassNames = Collections.unmodifiableSet(treeSet);
            }
        }

        @Override
        public String getLanguageClassName() {
            return languageInstance.getClass().getName();
        }

        @Override
        public Object create() {
            return languageInstance;
        }

        @Override
        public Set<String> getServicesClassNames() {
            return servicesClassNames;
        }

        @Override
        protected List<TruffleLanguageProvider> createFileTypeDetectors() {
            return List.of();
        }
    }

    private interface ProviderAdapter {
        Class<?> getProviderClass();

        TruffleLanguage<?> create();

        String getLanguageClassName();

        Collection<String> getServicesClassNames();

        List<FileTypeDetector> createFileTypeDetectors();

        List<String> getInternalResourceIds();

        InternalResource createInternalResource(String resourceId);
    }

    /**
     * Provider adapter for deprecated {@code TruffleLanguage.Provider}. GR-46292 Remove the
     * deprecated {@code TruffleLanguage.Provider} and this adapter. When removed, the
     * {@link ModuleAwareProvider} should also be removed.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyProvider implements ProviderAdapter {

        private final TruffleLanguage.Provider provider;

        LegacyProvider(TruffleLanguage.Provider provider) {
            Objects.requireNonNull(provider, "Provider must be non null");
            this.provider = provider;
        }

        @Override
        public Class<?> getProviderClass() {
            return provider.getClass();
        }

        @Override
        public TruffleLanguage<?> create() {
            return provider.create();
        }

        @Override
        public String getLanguageClassName() {
            return provider.getLanguageClassName();
        }

        public Collection<String> getServicesClassNames() {
            return provider.getServicesClassNames();
        }

        @Override
        public List<FileTypeDetector> createFileTypeDetectors() {
            return provider.createFileTypeDetectors();
        }

        @Override
        public List<String> getInternalResourceIds() {
            return List.of();
        }

        @Override
        public InternalResource createInternalResource(String resourceId) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Provider adapter for {@link TruffleLanguageProvider}. When the {@link LegacyProvider} is
     * removed, this class should also be removed.
     */
    private static final class ModuleAwareProvider implements ProviderAdapter {

        private final TruffleLanguageProvider provider;

        ModuleAwareProvider(TruffleLanguageProvider provider) {
            Objects.requireNonNull(provider, "Provider must be non null");
            this.provider = provider;
        }

        @Override
        public Class<?> getProviderClass() {
            return provider.getClass();
        }

        @Override
        public TruffleLanguage<?> create() {
            return (TruffleLanguage<?>) EngineAccessor.LANGUAGE_PROVIDER.create(provider);
        }

        @Override
        public String getLanguageClassName() {
            return EngineAccessor.LANGUAGE_PROVIDER.getLanguageClassName(provider);
        }

        public Collection<String> getServicesClassNames() {
            return EngineAccessor.LANGUAGE_PROVIDER.getServicesClassNames(provider);
        }

        @Override
        public List<FileTypeDetector> createFileTypeDetectors() {
            return EngineAccessor.LANGUAGE_PROVIDER.createFileTypeDetectors(provider);
        }

        @Override
        public List<String> getInternalResourceIds() {
            return EngineAccessor.LANGUAGE_PROVIDER.getInternalResourceIds(provider);
        }

        @Override
        public InternalResource createInternalResource(String resourceId) {
            return EngineAccessor.LANGUAGE_PROVIDER.createInternalResource(provider, resourceId);
        }
    }
}
