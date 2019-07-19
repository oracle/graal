/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import java.util.WeakHashMap;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import java.security.CodeSource;

/**
 * Ahead-of-time initialization. If the JVM is started with {@link TruffleOptions#AOT}, it populates
 * runtimeCache with languages found in application classloader.
 */
final class LanguageCache implements Comparable<LanguageCache> {
    private static final Map<String, LanguageCache> nativeImageCache = TruffleOptions.AOT ? new HashMap<>() : null;
    private static final Map<String, LanguageCache> nativeImageMimes = TruffleOptions.AOT ? new HashMap<>() : null;
    private static final Map<ClassLoader, Map<String, LanguageCache>> runtimeCaches = new WeakHashMap<>();
    private static volatile Map<String, LanguageCache> runtimeMimes;
    private final String className;
    private final Set<String> mimeTypes;
    private final Set<String> characterMimeTypes;
    private final Set<String> byteMimeTypes;
    private final String defaultMimeType;
    private final Set<String> dependentLanguages;
    private final String id;
    private final String name;
    private final String implementationName;
    private final String version;
    private final boolean interactive;
    private final boolean internal;
    private final ClassLoader loader;
    private final TruffleLanguage<?> globalInstance;
    private final Set<String> services;
    private final List<String> fileTypeDetectorClassNames;
    private String languageHome;
    private volatile ContextPolicy policy;
    private volatile Class<? extends TruffleLanguage<?>> languageClass;
    private volatile List<? extends FileTypeDetector> fileTypeDetectors;

    private LanguageCache(String id, String prefix, Properties info, ClassLoader loader, String url) {
        this.loader = loader;
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.implementationName = info.getProperty(prefix + "implementationName");
        this.version = info.getProperty(prefix + "version");
        TreeSet<String> characterMimes = parseList(info, prefix + "characterMimeType");
        if (characterMimes.isEmpty()) {
            characterMimes = parseList(info, prefix + "mimeType");
        }
        this.characterMimeTypes = characterMimes;
        this.byteMimeTypes = parseList(info, prefix + "byteMimeType");
        this.mimeTypes = new TreeSet<>();
        this.mimeTypes.addAll(characterMimes);
        this.mimeTypes.addAll(byteMimeTypes);

        String defaultMime = info.getProperty(prefix + "defaultMimeType");
        if (mimeTypes.size() == 1 && defaultMime == null) {
            this.defaultMimeType = mimeTypes.iterator().next();
        } else {
            this.defaultMimeType = defaultMime;
        }

        this.dependentLanguages = parseList(info, prefix + "dependentLanguage");
        this.id = id;
        this.interactive = Boolean.valueOf(info.getProperty(prefix + "interactive"));
        this.internal = Boolean.valueOf(info.getProperty(prefix + "internal"));
        this.languageHome = url;

        Set<String> servicesClassNames = new TreeSet<>();
        for (int servicesCounter = 0;; servicesCounter++) {
            String nth = prefix + "service" + servicesCounter;
            String serviceName = info.getProperty(nth);
            if (serviceName == null) {
                break;
            }
            servicesClassNames.add(serviceName);
        }
        this.services = Collections.unmodifiableSet(servicesClassNames);

        List<String> detectorClassNames = new ArrayList<>();
        for (int fileTypeDetectorCounter = 0;; fileTypeDetectorCounter++) {
            String nth = prefix + "fileTypeDetector" + fileTypeDetectorCounter;
            String fileTypeDetectorClassName = info.getProperty(nth);
            if (fileTypeDetectorClassName == null) {
                break;
            }
            detectorClassNames.add(fileTypeDetectorClassName);
        }
        this.fileTypeDetectorClassNames = Collections.unmodifiableList(detectorClassNames);

        if (TruffleOptions.AOT) {
            initializeLanguageClass();
            initializeFileTypeDetectors();
            assert languageClass != null;
            assert policy != null;
        }
        this.globalInstance = null;
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

    @SuppressWarnings("unchecked")
    LanguageCache(String id, String name, String implementationName, String version, boolean interactive, boolean internal,
                    TruffleLanguage<?> instance, String... services) {
        this.id = id;
        this.className = instance.getClass().getName();
        this.mimeTypes = Collections.emptySet();
        this.characterMimeTypes = Collections.emptySet();
        this.byteMimeTypes = Collections.emptySet();
        this.defaultMimeType = null;
        this.implementationName = implementationName;
        this.name = name;
        this.version = version;
        this.interactive = interactive;
        this.internal = internal;
        this.dependentLanguages = Collections.emptySet();
        this.loader = instance.getClass().getClassLoader();
        this.languageClass = (Class<? extends TruffleLanguage<?>>) instance.getClass();
        this.languageHome = null;
        this.policy = ContextPolicy.SHARED;
        this.globalInstance = instance;
        if (services.length == 0) {
            this.services = Collections.emptySet();
        } else {
            Set<String> servicesClassNames = new TreeSet<>();
            Collections.addAll(servicesClassNames, services);
            this.services = Collections.unmodifiableSet(servicesClassNames);
        }
        this.fileTypeDetectorClassNames = Collections.emptyList();
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
        for (LanguageCache cache : languages(null).values()) {
            for (String mime : cache.getMimeTypes()) {
                mimes.put(mime, cache);
            }
        }
        return mimes;
    }

    static Map<String, LanguageCache> languages(ClassLoader additionalLoader) {
        if (TruffleOptions.AOT) {
            return nativeImageCache;
        }
        synchronized (LanguageCache.class) {
            Map<String, LanguageCache> cache = runtimeCaches.get(additionalLoader);
            if (cache == null) {
                cache = createLanguages(additionalLoader);
                runtimeCaches.put(additionalLoader, cache);
            }
            return cache;
        }
    }

    private static Map<String, LanguageCache> createLanguages(ClassLoader additionalLoader) {
        List<LanguageCache> caches = new ArrayList<>();
        for (ClassLoader loader : EngineAccessor.allLoaders()) {
            collectLanguages(loader, caches);
        }
        if (additionalLoader != null) {
            collectLanguages(additionalLoader, caches);
        }
        Map<String, LanguageCache> cacheToId = new HashMap<>();
        for (LanguageCache languageCache : caches) {
            LanguageCache prev = cacheToId.put(languageCache.getId(), languageCache);
            if (prev != null && (!prev.getClassName().equals(languageCache.getClassName()) || loadUninitialized(prev) != loadUninitialized(languageCache))) {
                String message = String.format("Duplicate language id %s. First language [%s]. Second language [%s].",
                                languageCache.getId(),
                                formatLanguageLocation(prev),
                                formatLanguageLocation(languageCache));
                throw new IllegalStateException(message);
            }
        }
        return cacheToId;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends TruffleLanguage<?>> loadUninitialized(LanguageCache cache) {
        try {
            return (Class<? extends TruffleLanguage<?>>) Class.forName(cache.getClassName(), false, cache.loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load language " + cache.getName() + ". Language implementation class " + cache.getClassName() + " failed to load.", e);
        }
    }

    private static String formatLanguageLocation(LanguageCache languageCache) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language class ").append(languageCache.getClassName());
        CodeSource source = languageCache.getLanguageClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            sb.append(", Loaded from " + source.getLocation());
        }
        return sb.toString();
    }

    private static void collectLanguages(ClassLoader loader, List<LanguageCache> list) {
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
                String id = p.getProperty(prefix + "id");
                if (id == null) {
                    id = defaultId(name, p.getProperty(prefix + "className"));
                }
                String languageHome = System.getProperty(id + ".home");
                if (languageHome == null && connection instanceof JarURLConnection) {

                    /*
                     * The previous implementation used a `URL.getPath()`, but OS Windows is
                     * offended by leading slash and maybe other irrelevant characters. Therefore,
                     * for JDK 1.7+ a preferred way to go is URL -> URI -> Path.
                     *
                     * Also, Paths are more strict than Files and URLs, so we can't create an
                     * invalid Path from a random string like "/C:/". This leads us to the
                     * `URISyntaxException` for URL -> URI conversion and
                     * `java.nio.file.InvalidPathException` for URI -> Path conversion.
                     *
                     * For fixing further bugs at this point, please read
                     * http://tools.ietf.org/html/rfc1738 http://tools.ietf.org/html/rfc2396
                     * (supersedes rfc1738) http://tools.ietf.org/html/rfc3986 (supersedes rfc2396)
                     *
                     * http://url.spec.whatwg.org/ does not contain URI interpretation. When you
                     * call `URI.toASCIIString()` all reserved and non-ASCII characters are
                     * percent-quoted.
                     */
                    try {
                        Path path;
                        path = Paths.get(((JarURLConnection) connection).getJarFileURL().toURI());
                        Path parent = path.getParent();
                        if (parent == null) {
                            languageHome = null;
                        } else {
                            languageHome = parent.toString();
                        }
                    } catch (URISyntaxException e) {
                        assert false : "Could not resolve path.";
                    }
                }
                list.add(new LanguageCache(id, prefix, p, loader, languageHome));
            }
        }
    }

    private static String defaultId(final String name, final String className) {
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
            languageHome = System.getProperty(id + ".home");
        }
        return languageHome;
    }

    TruffleLanguage<?> loadLanguage() {
        if (globalInstance != null) {
            return globalInstance;
        }
        TruffleLanguage<?> instance;
        try {
            instance = getLanguageClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create instance of " + name + " language implementation. Public default constructor expected in " + className + ".", e);
        }
        return instance;
    }

    Class<? extends TruffleLanguage<?>> getLanguageClass() {
        if (!TruffleOptions.AOT) {
            initializeLanguageClass();
        }
        return this.languageClass;
    }

    ContextPolicy getPolicy() {
        initializeLanguageClass();
        return policy;
    }

    Collection<String> getServices() {
        return services;
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    List<? extends FileTypeDetector> getFileTypeDetectors() {
        initializeFileTypeDetectors();
        return fileTypeDetectors;
    }

    @SuppressWarnings("unchecked")
    private void initializeLanguageClass() {
        if (languageClass == null) {
            synchronized (this) {
                if (languageClass == null) {
                    try {
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

    @Override
    public String toString() {
        return "LanguageCache [id=" + id + ", name=" + name + ", implementationName=" + implementationName + ", version=" + version + ", className=" + className + ", services=" + services + "]";
    }

    static void resetNativeImageCacheLanguageHomes() {
        for (LanguageCache languageCache : languages(null).values()) {
            languageCache.languageHome = null;
        }
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (LanguageCache languageCache : languages(loader).values()) {
            languageCache.languageHome = null;
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
        nativeImageCache.putAll(createLanguages(imageClassLoader));
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
            assert cache.languageClass != null;
            list.add(cache.languageClass);
        }
        return list;
    }

}
