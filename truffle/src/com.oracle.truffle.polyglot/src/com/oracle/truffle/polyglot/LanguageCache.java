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
import java.io.InputStream;
import java.io.PrintStream;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

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
    private static final Map<Collection<AbstractClassLoaderSupplier>, Map<String, LanguageCache>> runtimeCaches = new HashMap<>();
    private static volatile Map<String, LanguageCache> runtimeMimes;
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
    private final Set<String> services;
    private final LanguageReflection languageReflection;

    /*
     * When building a native image, this field is reset to null so that directories from the image
     * build do not leak into the image heap. The value is lazily recomputed at image run time.
     */
    private String languageHome;

    private LanguageCache(String id, String name, String implementationName, String version, String className,
                    String languageHome, Set<String> characterMimeTypes, Set<String> byteMimeTypes, String defaultMimeType,
                    Set<String> dependentLanguages, boolean interactive, boolean internal, Set<String> services,
                    LanguageReflection languageReflection) {
        this.languageReflection = languageReflection;
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
        this.languageHome = languageHome;
        this.services = services;

        if (TruffleOptions.AOT) {
            languageReflection.aotInitializeAtBuildTime();
        }
    }

    static LanguageCache createHostLanguageCache(String... services) {
        TruffleLanguage<?> languageInstance = new HostLanguage();
        Set<String> servicesClassNames;
        if (services.length == 0) {
            servicesClassNames = Collections.emptySet();
        } else {
            servicesClassNames = new TreeSet<>();
            Collections.addAll(servicesClassNames, services);
            servicesClassNames = Collections.unmodifiableSet(servicesClassNames);
        }
        return new LanguageCache(
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
                        false, false, servicesClassNames,
                        LanguageReflection.forLanguageInstance(new HostLanguage(), ContextPolicy.SHARED));
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

    static Map<String, LanguageCache> languages() {
        return loadLanguages(EngineAccessor.locatorOrDefaultLoaders());
    }

    private static Map<String, LanguageCache> loadLanguages(List<AbstractClassLoaderSupplier> classLoaders) {
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

    private static Map<String, LanguageCache> createLanguages(List<AbstractClassLoaderSupplier> suppliers) {
        List<LanguageCache> caches = new ArrayList<>();
        for (Supplier<ClassLoader> supplier : suppliers) {
            Loader.load(supplier.get(), caches);
        }
        Map<String, LanguageCache> cacheToId = new HashMap<>();
        for (LanguageCache languageCache : caches) {
            LanguageCache prev = cacheToId.put(languageCache.getId(), languageCache);
            if (prev != null && (!prev.getClassName().equals(languageCache.getClassName()) || !prev.languageReflection.hasSameCodeSource(languageCache.languageReflection))) {
                String message = String.format("Duplicate language id %s. First language [%s]. Second language [%s].",
                                languageCache.getId(),
                                formatLanguageLocation(prev),
                                formatLanguageLocation(languageCache));
                throw new IllegalStateException(message);
            }
        }
        return cacheToId;
    }

    private static String formatLanguageLocation(LanguageCache languageCache) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language class ").append(languageCache.getClassName());
        URL url = languageCache.languageReflection.getCodeSource();
        if (url != null) {
            sb.append(", Loaded from " + url);
        }
        return sb.toString();
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

    boolean isByteMimeType(String mimeType) {
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

    String getLanguageHome() {
        if (languageHome == null) {
            languageHome = getLanguageHomeImpl(id);
        }
        return languageHome;
    }

    private static String getLanguageHomeImpl(String languageId) {
        String home = System.getProperty("org.graalvm.language." + languageId + ".home");
        if (home == null) {
            // check legacy property
            home = System.getProperty(languageId + ".home");
        }
        return home;
    }

    TruffleLanguage<?> loadLanguage() {
        return languageReflection.newInstance();
    }

    Set<? extends Class<? extends Tag>> getProvidedTags() {
        return languageReflection.getProvidedTags();
    }

    ContextPolicy getPolicy() {
        return languageReflection.getContextPolicy();
    }

    Collection<String> getServices() {
        return services;
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    List<? extends FileTypeDetector> getFileTypeDetectors() {
        return languageReflection.getFileTypeDetectors();
    }

    @Override
    public String toString() {
        return "LanguageCache [id=" + id + ", name=" + name + ", implementationName=" + implementationName + ", version=" + version + ", className=" + className + ", services=" + services + "]";
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
     * Fetches all active language classes.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static Collection<Class<?>> getLanguageClasses() {
        assert TruffleOptions.AOT : "Only supported during image generation";
        ArrayList<Class<?>> list = new ArrayList<>();
        for (LanguageCache cache : nativeImageCache.values()) {
            Class<? extends TruffleLanguage<?>> clz = cache.languageReflection.aotInitializeAtBuildTime();
            if (clz != null) {
                list.add(clz);
            }
        }
        return list;
    }

    private abstract static class LanguageReflection {

        abstract TruffleLanguage<?> newInstance();

        abstract List<? extends FileTypeDetector> getFileTypeDetectors();

        abstract TruffleLanguage.ContextPolicy getContextPolicy();

        abstract Set<? extends Class<? extends Tag>> getProvidedTags();

        abstract Class<? extends TruffleLanguage<?>> aotInitializeAtBuildTime();

        abstract boolean hasSameCodeSource(LanguageReflection other);

        abstract URL getCodeSource();

        static LanguageReflection forLanguageInstance(TruffleLanguage<?> language, ContextPolicy contextPolycy, FileTypeDetector... fileTypeDetectors) {
            return new LanguageInstanceReflection(language, contextPolycy, fileTypeDetectors);
        }

        private static final class LanguageInstanceReflection extends LanguageReflection {

            private final TruffleLanguage<?> languageInstance;
            private final ContextPolicy contextPolycy;
            private final List<? extends FileTypeDetector> fileTypeDetectors;

            LanguageInstanceReflection(TruffleLanguage<?> languageInstance, ContextPolicy contextPolycy, FileTypeDetector... detectors) {
                assert languageInstance != null;
                assert contextPolycy != null;
                this.languageInstance = languageInstance;
                this.contextPolycy = contextPolycy;
                if (detectors.length == 0) {
                    fileTypeDetectors = Collections.emptyList();
                } else {
                    fileTypeDetectors = Arrays.asList(detectors);
                }
            }

            @Override
            TruffleLanguage<?> newInstance() {
                return languageInstance;
            }

            @Override
            List<? extends FileTypeDetector> getFileTypeDetectors() {
                return fileTypeDetectors;
            }

            @Override
            ContextPolicy getContextPolicy() {
                return contextPolycy;
            }

            @Override
            Set<? extends Class<? extends Tag>> getProvidedTags() {
                return Collections.emptySet();
            }

            @Override
            @SuppressWarnings("unchecked")
            Class<? extends TruffleLanguage<?>> aotInitializeAtBuildTime() {
                return null;
            }

            @Override
            boolean hasSameCodeSource(LanguageReflection other) {
                throw new UnsupportedOperationException("Should not reach here.");
            }

            @Override
            URL getCodeSource() {
                throw new UnsupportedOperationException("Should not reach here.");
            }
        }
    }

    private abstract static class Loader {

        static void load(ClassLoader loader, Collection<? super LanguageCache> into) {
            if (loader == null) {
                return;
            }
            try {
                Class<?> truffleLanguageClassAsSeenByLoader = Class.forName(TruffleLanguage.class.getName(), true, loader);
                if (truffleLanguageClassAsSeenByLoader != TruffleLanguage.class) {
                    return;
                }
            } catch (ClassNotFoundException ex) {
                return;
            }
            LegacyLoader.INSTANCE.loadImpl(loader, into);
            ServicesLoader.INSTANCE.loadImpl(loader, into);
        }

        static void exportTruffle(ClassLoader loader) {
            if (!TruffleOptions.AOT) {
                /*
                 * In JDK 9+, the Truffle API packages must be dynamically exported to a Truffle
                 * language since the Truffle API module descriptor only exports the packages to
                 * modules known at build time (such as the Graal module).
                 */
                EngineAccessor.JDKSERVICES.exportTo(loader, null);
            }
        }

        abstract void loadImpl(ClassLoader loader, Collection<? super LanguageCache> into);

        static String defaultId(final String name, final String className) {
            String resolvedId;
            if (name.isEmpty()) {
                int lastIndex = className.lastIndexOf('$');
                if (lastIndex == -1) {
                    lastIndex = className.lastIndexOf('.');
                }
                resolvedId = className.substring(lastIndex + 1, className.length());
            } else {
                // TODO remove this hack for single character languages
                if (name.length() == 1) {
                    resolvedId = name;
                } else {
                    resolvedId = name.toLowerCase();
                }
            }
            return resolvedId;
        }

        static String getLanguageHomeFromURLConnection(String languageId, URLConnection connection) {
            if (connection instanceof JarURLConnection) {
                /*
                 * The previous implementation used a `URL.getPath()`, but OS Windows is offended by
                 * leading slash and maybe other irrelevant characters. Therefore, for JDK 1.7+ a
                 * preferred way to go is URL -> URI -> Path.
                 *
                 * Also, Paths are more strict than Files and URLs, so we can't create an invalid
                 * Path from a random string like "/C:/". This leads us to the `URISyntaxException`
                 * for URL -> URI conversion and `java.nio.file.InvalidPathException` for URI ->
                 * Path conversion.
                 *
                 * For fixing further bugs at this point, please read
                 * http://tools.ietf.org/html/rfc1738 http://tools.ietf.org/html/rfc2396 (supersedes
                 * rfc1738) http://tools.ietf.org/html/rfc3986 (supersedes rfc2396)
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

        private static final class LegacyLoader extends Loader {

            static final Loader INSTANCE = new LegacyLoader();

            private LegacyLoader() {
            }

            @Override
            public void loadImpl(ClassLoader loader, Collection<? super LanguageCache> into) {
                Enumeration<URL> en;
                try {
                    en = loader.getResources("META-INF/truffle/language");
                } catch (IOException ex) {
                    throw new IllegalStateException("Cannot read list of Truffle languages", ex);
                }
                while (en.hasMoreElements()) {
                    URL u = en.nextElement();
                    Properties p;
                    URLConnection connection;
                    try {
                        p = new Properties();
                        connection = u.openConnection();
                        /* Ensure truffle language jar-files are not getting cached. See GR-12018 */
                        connection.setUseCaches(false);
                        try (InputStream is = connection.getInputStream()) {
                            p.load(is);
                        }
                    } catch (IOException ex) {
                        PrintStream out = System.err;
                        out.println("Cannot process " + u + " as language definition");
                        ex.printStackTrace();
                        continue;
                    }
                    for (int cnt = 1;; cnt++) {
                        String prefix = "language" + cnt + ".";
                        String name = p.getProperty(prefix + "name");
                        if (name == null) {
                            break;
                        }
                        into.add(createLanguageCache(name, prefix, p, loader, connection));
                    }
                }
            }

            private static LanguageCache createLanguageCache(String name, String prefix, Properties info, ClassLoader loader, URLConnection connection) {
                String id = info.getProperty(prefix + "id");
                if (id == null) {
                    id = defaultId(name, info.getProperty(prefix + "className"));
                }
                String languageHome = getLanguageHomeImpl(id);
                if (languageHome == null) {
                    languageHome = getLanguageHomeFromURLConnection(id, connection);
                }
                String className = info.getProperty(prefix + "className");
                String implementationName = info.getProperty(prefix + "implementationName");
                String version = info.getProperty(prefix + "version");
                TreeSet<String> characterMimes = parseList(info, prefix + "characterMimeType");
                if (characterMimes.isEmpty()) {
                    characterMimes = parseList(info, prefix + "mimeType");
                }
                TreeSet<String> byteMimeTypes = parseList(info, prefix + "byteMimeType");
                String defaultMime = info.getProperty(prefix + "defaultMimeType");
                TreeSet<String> dependentLanguages = parseList(info, prefix + "dependentLanguage");
                boolean interactive = Boolean.valueOf(info.getProperty(prefix + "interactive"));
                boolean internal = Boolean.valueOf(info.getProperty(prefix + "internal"));
                Set<String> servicesClassNames = new TreeSet<>();
                for (int servicesCounter = 0;; servicesCounter++) {
                    String nth = prefix + "service" + servicesCounter;
                    String serviceName = info.getProperty(nth);
                    if (serviceName == null) {
                        break;
                    }
                    servicesClassNames.add(serviceName);
                }
                List<String> detectorClassNames = new ArrayList<>();
                for (int fileTypeDetectorCounter = 0;; fileTypeDetectorCounter++) {
                    String nth = prefix + "fileTypeDetector" + fileTypeDetectorCounter;
                    String fileTypeDetectorClassName = info.getProperty(nth);
                    if (fileTypeDetectorClassName == null) {
                        break;
                    }
                    detectorClassNames.add(fileTypeDetectorClassName);
                }
                LegacyLanguageReflection reflection = new LegacyLanguageReflection(name, loader, className, detectorClassNames);
                return new LanguageCache(id, name, implementationName, version, className, languageHome,
                                characterMimes, byteMimeTypes, defaultMime, dependentLanguages,
                                interactive, internal, servicesClassNames, reflection);
            }

            private static TreeSet<String> parseList(Properties info, String prefix) {
                TreeSet<String> mimeTypesSet = new TreeSet<>();
                for (int i = 0;; i++) {
                    String mt = info.getProperty(prefix + "." + i);
                    if (mt == null) {
                        break;
                    }
                    mimeTypesSet.add(mt);
                }
                return mimeTypesSet;
            }

            private static final class LegacyLanguageReflection extends LanguageReflection {

                private final String name;
                private final ClassLoader loader;
                private final String className;
                private final List<String> fileTypeDetectorClassNames;
                private volatile Class<? extends TruffleLanguage<?>> languageClass;
                private volatile ContextPolicy policy;
                private volatile List<? extends FileTypeDetector> fileTypeDetectors;

                LegacyLanguageReflection(String name, ClassLoader loader, String className, List<String> fileTypeDetectorClassNames) {
                    Objects.requireNonNull(name, "Name must be non null.");
                    Objects.requireNonNull(loader, "Loader must be non null.");
                    Objects.requireNonNull(className, "ClassName must be non null.");
                    Objects.requireNonNull(fileTypeDetectorClassNames, "FileTypeDetectorClassNames must be non null.");
                    this.name = name;
                    this.loader = loader;
                    this.className = className;
                    this.fileTypeDetectorClassNames = fileTypeDetectorClassNames;
                }

                @Override
                TruffleLanguage<?> newInstance() {
                    try {
                        return getLanguageClass().getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot create instance of " + name + " language implementation. Public default constructor expected in " + className + ".", e);
                    }
                }

                @Override
                List<? extends FileTypeDetector> getFileTypeDetectors() {
                    initializeFileTypeDetectors();
                    return fileTypeDetectors;
                }

                @Override
                ContextPolicy getContextPolicy() {
                    initializeLanguageClass();
                    return policy;
                }

                @Override
                @SuppressWarnings("unchecked")
                Set<? extends Class<? extends Tag>> getProvidedTags() {
                    initializeLanguageClass();
                    ProvidedTags tags = languageClass.getAnnotation(ProvidedTags.class);
                    if (tags == null) {
                        return Collections.emptySet();
                    }
                    Set<Class<? extends Tag>> result = new HashSet<>();
                    Collections.addAll(result, (Class<? extends Tag>[]) tags.value());
                    return result;
                }

                @Override
                Class<? extends TruffleLanguage<?>> aotInitializeAtBuildTime() {
                    initializeLanguageClass();
                    initializeFileTypeDetectors();
                    assert languageClass != null;
                    assert policy != null;
                    return languageClass;
                }

                @Override
                boolean hasSameCodeSource(LanguageReflection other) {
                    if (other instanceof LegacyLanguageReflection) {
                        return loadUnitialized() == ((LegacyLanguageReflection) other).loadUnitialized();
                    }
                    return false;
                }

                @Override
                URL getCodeSource() {
                    CodeSource source = getLanguageClass().getProtectionDomain().getCodeSource();
                    return source != null ? source.getLocation() : null;
                }

                @SuppressWarnings("unchecked")
                private Class<? extends TruffleLanguage<?>> loadUnitialized() {
                    try {
                        return (Class<? extends TruffleLanguage<?>>) Class.forName(className, false, loader);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException("Cannot load language " + name + ". Language implementation class " + className + " failed to load.", e);
                    }
                }

                private Class<? extends TruffleLanguage<?>> getLanguageClass() {
                    if (!TruffleOptions.AOT) {
                        initializeLanguageClass();
                    }
                    return this.languageClass;
                }

                @SuppressWarnings("unchecked")
                private void initializeLanguageClass() {
                    if (languageClass == null) {
                        synchronized (this) {
                            if (languageClass == null) {
                                try {
                                    exportTruffle(loader);

                                    Class<?> loadedClass = Class.forName(className, true, loader);
                                    Registration reg = loadedClass.getAnnotation(Registration.class);
                                    if (reg == null) {
                                        policy = ContextPolicy.EXCLUSIVE;
                                    } else {
                                        policy = loadedClass.getAnnotation(Registration.class).contextPolicy();
                                    }
                                    languageClass = (Class<? extends TruffleLanguage<?>>) loadedClass;
                                } catch (ClassNotFoundException e) {
                                    throw new IllegalStateException("Cannot load language " + name + ". Language implementation class " + className + " failed to load.", e);
                                }
                            }
                        }
                    }
                }

                private void initializeFileTypeDetectors() {
                    if (fileTypeDetectors == null) {
                        synchronized (this) {
                            if (fileTypeDetectors == null) {
                                exportTruffle(loader);

                                List<FileTypeDetector> instances = new ArrayList<>(fileTypeDetectorClassNames.size());
                                for (String fileTypeDetectorClassName : fileTypeDetectorClassNames) {
                                    try {
                                        Class<? extends FileTypeDetector> detectorClass = Class.forName(fileTypeDetectorClassName, true, loader).asSubclass(FileTypeDetector.class);
                                        FileTypeDetector instance = detectorClass.getDeclaredConstructor().newInstance();
                                        instances.add(instance);
                                    } catch (ReflectiveOperationException e) {
                                        throw new IllegalStateException("Cannot instantiate FileTypeDetector, class  " + fileTypeDetectorClassName + ".", e);
                                    }
                                }
                                fileTypeDetectors = Collections.unmodifiableList(instances);
                            }
                        }
                    }
                }
            }
        }

        private static final class ServicesLoader extends Loader {

            static final Loader INSTANCE = new ServicesLoader();

            private ServicesLoader() {
            }

            @Override
            public void loadImpl(ClassLoader loader, Collection<? super LanguageCache> into) {
                exportTruffle(loader);
                for (TruffleLanguage.Provider provider : ServiceLoader.load(TruffleLanguage.Provider.class, loader)) {
                    Registration reg = provider.getClass().getAnnotation(Registration.class);
                    if (reg == null) {
                        PrintStream out = System.err;
                        out.println("Provider " + provider.getClass() + " is missing @Registration annotation.");
                        continue;
                    }
                    String className = provider.getLanguageClassName();
                    String name = reg.name();
                    String id = reg.id();
                    if (id == null || id.isEmpty()) {
                        id = defaultId(name, className);
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
                    if (characterMimes.isEmpty()) {
                        Collections.addAll(characterMimes, getMimeTypesDepecated(reg));
                    }
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
                    Set<String> servicesClassNames = new TreeSet<>();
                    for (String service : provider.getServicesClassNames()) {
                        servicesClassNames.add(service);
                    }
                    LanguageReflection reflection = new ServiceLoaderLanguageReflection(provider, reg.contextPolicy());
                    into.add(new LanguageCache(id, name, implementationName, version, className, languageHome,
                                    characterMimes, byteMimeTypes, defaultMime, dependentLanguages, interactive, internal,
                                    servicesClassNames, reflection));
                }
            }

            @SuppressWarnings("deprecation")
            private static String[] getMimeTypesDepecated(Registration reg) {
                return reg.mimeType();
            }

            private static final class ServiceLoaderLanguageReflection extends LanguageReflection {

                private final TruffleLanguage.Provider provider;
                private final ContextPolicy contextPolicy;
                private volatile Set<Class<? extends Tag>> providedTags;
                private volatile List<FileTypeDetector> fileTypeDetectors;

                ServiceLoaderLanguageReflection(TruffleLanguage.Provider provider, ContextPolicy contextPolicy) {
                    assert provider != null;
                    assert contextPolicy != null;
                    this.provider = provider;
                    this.contextPolicy = contextPolicy;
                }

                @Override
                TruffleLanguage<?> newInstance() {
                    return provider.create();
                }

                @Override
                List<? extends FileTypeDetector> getFileTypeDetectors() {
                    List<FileTypeDetector> result = fileTypeDetectors;
                    if (result == null) {
                        result = provider.createFileTypeDetectors();
                        fileTypeDetectors = result;
                    }
                    return result;
                }

                @Override
                ContextPolicy getContextPolicy() {
                    return contextPolicy;
                }

                @Override
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

                @Override
                Class<? extends TruffleLanguage<?>> aotInitializeAtBuildTime() {
                    return null;
                }

                @Override
                boolean hasSameCodeSource(LanguageReflection other) {
                    if (other instanceof ServiceLoaderLanguageReflection) {
                        return provider.getClass() == ((ServiceLoaderLanguageReflection) other).provider.getClass();
                    }
                    return false;
                }

                @Override
                URL getCodeSource() {
                    CodeSource source = provider.getClass().getProtectionDomain().getCodeSource();
                    return source != null ? source.getLocation() : null;
                }
            }
        }
    }

}
