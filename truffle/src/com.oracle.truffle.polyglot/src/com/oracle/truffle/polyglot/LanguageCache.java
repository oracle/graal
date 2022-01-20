/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;
import com.oracle.truffle.polyglot.EngineAccessor.StrongClassLoaderSupplier;

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
    private final TruffleLanguage.Provider provider;
    private final String website;
    private volatile List<FileTypeDetector> fileTypeDetectors;
    private volatile Set<Class<? extends Tag>> providedTags;
    private int staticIndex;

    /*
     * When building a native image, this field is reset to null so that directories from the image
     * build do not leak into the image heap. The value is lazily recomputed at image run time.
     */
    private String languageHome;

    private LanguageCache(String id, String name, String implementationName, String version, String className,
                    String languageHome, Set<String> characterMimeTypes, Set<String> byteMimeTypes, String defaultMimeType,
                    Set<String> dependentLanguages, boolean interactive, boolean internal, boolean needsAllEncodings, Set<String> services,
                    ContextPolicy contextPolicy, TruffleLanguage.Provider provider, String website) {
        assert provider != null : "Provider must be non null";
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
        this.provider = provider;
        this.website = website;
    }

    /**
     * Returns the maximum index used by any loaded language. This index updates when new languages
     * are loaded. Make sure you only read this index when all languages are already loaded.
     */
    static int getMaxStaticIndex() {
        return maxStaticIndex;
    }

    static LanguageCache createHostLanguageCache(TruffleLanguage<Object> languageInstance, String... services) {
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
                        ContextPolicy.SHARED, hostLanguageProvider, "");
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
     *
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
        for (Supplier<ClassLoader> supplier : suppliers) {
            ClassLoader loader = supplier.get();
            if (loader == null || !isValidLoader(loader)) {
                continue;
            }
            if (!TruffleOptions.AOT) {
                /*
                 * In JDK 9+, the Truffle API packages must be dynamically exported to a Truffle
                 * language since the Truffle API module descriptor only exports the packages to
                 * modules known at build time (such as the Graal module).
                 */
                EngineAccessor.JDKSERVICES.exportTo(loader, null);
            }
            for (TruffleLanguage.Provider provider : ServiceLoader.load(TruffleLanguage.Provider.class, loader)) {
                loadLanguageImpl(provider, caches);
            }
        }
        Map<String, LanguageCache> cacheToId = new LinkedHashMap<>();
        for (LanguageCache languageCache : caches) {
            LanguageCache prev = cacheToId.put(languageCache.getId(), languageCache);
            if (prev != null && (!prev.getClassName().equals(languageCache.getClassName()) || !hasSameCodeSource(prev, languageCache))) {
                String message = String.format("Duplicate language id %s. First language [%s]. Second language [%s].",
                                languageCache.getId(),
                                formatLanguageLocation(prev),
                                formatLanguageLocation(languageCache));
                throw new IllegalStateException(message);
            }
        }
        int languageId = PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
        for (LanguageCache cache : cacheToId.values()) {
            cache.staticIndex = ++languageId;
        }
        /*
         * maxLanguagesCount only needs to grow, otherwise we might access the fast thread local
         * array out of bounds.
         */
        maxStaticIndex = Math.max(maxStaticIndex, languageId);
        return cacheToId;
    }

    private static boolean hasSameCodeSource(LanguageCache first, LanguageCache second) {
        assert first.provider != null && second.provider != null : "Must not be called for host language cache";
        return first.provider.getClass() == second.provider.getClass();
    }

    private static boolean isValidLoader(ClassLoader loader) {
        try {
            Class<?> truffleLanguageClassAsSeenByLoader = Class.forName(TruffleLanguage.class.getName(), true, loader);
            return truffleLanguageClassAsSeenByLoader == TruffleLanguage.class;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static void loadLanguageImpl(TruffleLanguage.Provider provider, List<LanguageCache> into) {
        Registration reg = provider.getClass().getAnnotation(Registration.class);
        if (reg == null) {
            PrintStream out = System.err;
            out.println("Provider " + provider.getClass() + " is missing @Registration annotation.");
            return;
        }
        String className = provider.getLanguageClassName();
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
                // TODO remove this hack for single character languages
                if (name.length() == 1) {
                    id = name;
                } else {
                    id = name.toLowerCase();
                }
            }
        }
        String languageHome = getLanguageHomeImpl(id);
        if (languageHome == null) {
            URL url = provider.getClass().getClassLoader().getResource(className.replace('.', '/') + ".class");
            if (url != null) {
                try {
                    languageHome = getLanguageHomeFromURLConnection(id, url.openConnection());
                } catch (IOException ioe) {
                }
            }
        }
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
        Set<String> servicesClassNames = new TreeSet<>();
        for (String service : provider.getServicesClassNames()) {
            servicesClassNames.add(service);
        }
        into.add(new LanguageCache(id, name, implementationName, version, className, languageHome,
                        characterMimes, byteMimeTypes, defaultMime, dependentLanguages, interactive, internal, needsAllEncodings,
                        servicesClassNames, reg.contextPolicy(), provider, reg.website()));
    }

    private static String getLanguageHomeFromURLConnection(String languageId, URLConnection connection) {
        if (connection instanceof JarURLConnection) {
            /*
             * The previous implementation used a `URL.getPath()`, but OS Windows is offended by
             * leading slash and maybe other irrelevant characters. Therefore, for JDK 1.7+ a
             * preferred way to go is URL -> URI -> Path.
             *
             * Also, Paths are more strict than Files and URLs, so we can't create an invalid Path
             * from a random string like "/C:/". This leads us to the `URISyntaxException` for URL
             * -> URI conversion and `java.nio.file.InvalidPathException` for URI -> Path
             * conversion.
             *
             * For fixing further bugs at this point, please read http://tools.ietf.org/html/rfc1738
             * http://tools.ietf.org/html/rfc2396 (supersedes rfc1738)
             * http://tools.ietf.org/html/rfc3986 (supersedes rfc2396)
             *
             * http://url.spec.whatwg.org/ does not contain URI interpretation. When you call
             * `URI.toASCIIString()` all reserved and non-ASCII characters are percent-quoted.
             */
            try {
                URL url = ((JarURLConnection) connection).getJarFileURL();
                if ("file".equals(url.getProtocol())) {
                    Path path = Paths.get(url.toURI());
                    Path parent = path.getParent();
                    return parent != null ? parent.toString() : null;
                }
            } catch (URISyntaxException | FileSystemNotFoundException | IllegalArgumentException | SecurityException e) {
                assert false : "Cannot locate " + languageId + " language home due to " + e.getMessage();
            }
        }
        return null;
    }

    private static String formatLanguageLocation(LanguageCache languageCache) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language class ").append(languageCache.getClassName());

        CodeSource source = languageCache.provider != null ? languageCache.provider.getClass().getProtectionDomain().getCodeSource() : null;
        URL url = source != null ? source.getLocation() : null;
        if (url != null) {
            sb.append(", Loaded from " + url);
        }
        return sb.toString();
    }

    private static String getLanguageHomeImpl(String languageId) {
        String home = System.getProperty("org.graalvm.language." + languageId + ".home");
        if (home == null) {
            // check legacy property
            home = System.getProperty(languageId + ".home");
        }
        return home;
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
        nativeImageCache.putAll(createLanguages(Arrays.asList(new StrongClassLoaderSupplier(imageClassLoader))));
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
                PrintStream out = System.err;
                out.println("Failed to lookup patchContext method. " + roe);
            }
        }
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
            languageHome = getLanguageHomeImpl(id);
        }
        return languageHome;
    }

    TruffleLanguage<?> loadLanguage() {
        return provider.create();
    }

    @SuppressWarnings("unchecked")
    Set<? extends Class<? extends Tag>> getProvidedTags() {
        Set<Class<? extends Tag>> res = providedTags;
        if (res == null) {
            ProvidedTags tags = provider.getClass().getAnnotation(ProvidedTags.class);
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
            result = provider.createFileTypeDetectors();
            fileTypeDetectors = result;
        }
        return result;
    }

    @Override
    public String toString() {
        return "LanguageCache [id=" + id + ", name=" + name + ", implementationName=" + implementationName + ", version=" + version + ", className=" + className + ", services=" + services + "]";
    }

    String getWebsite() {
        return website;
    }

    private static final class HostLanguageProvider implements TruffleLanguage.Provider {

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
        public TruffleLanguage<?> create() {
            return languageInstance;
        }

        @Override
        public List<FileTypeDetector> createFileTypeDetectors() {
            return Collections.emptyList();
        }

        @Override
        public Set<String> getServicesClassNames() {
            return servicesClassNames;
        }
    }
}
