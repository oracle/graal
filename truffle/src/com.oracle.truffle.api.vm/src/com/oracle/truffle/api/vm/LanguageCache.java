/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotEngine.LOG;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.vm.PolyglotEngine.LegacyEngineImpl;

/**
 * Ahead-of-time initialization. If the JVM is started with {@link TruffleOptions#AOT}, it populates
 * cache with languages found in application classloader.
 */
final class LanguageCache implements Comparable<LanguageCache> {
    private static final boolean PRELOAD;
    private static final Map<String, LanguageCache> CACHE;

    private static volatile Map<String, LanguageCache> cache;
    static {
        if (VMAccessor.SPI == null) {
            VMAccessor.initialize(new LegacyEngineImpl());
        }
    }
    static final Collection<ClassLoader> AOT_LOADERS = TruffleOptions.AOT ? VMAccessor.SPI.allLoaders() : null;
    private final String className;
    private final Set<String> mimeTypes;
    private final String id;
    private final String name;
    private final String version;
    private final boolean interactive;
    private final boolean internal;
    private final ClassLoader loader;
    private final TruffleLanguage<?> singletonLanguage;
    private volatile Class<? extends TruffleLanguage<?>> languageClass;

    static {
        CACHE = TruffleOptions.AOT ? initializeLanguages(loader()) : null;
        PRELOAD = CACHE != null;
    }

    /**
     * This method initializes all languages under the provided classloader.
     *
     * NOTE: Method's signature should not be changed as it is reflectively invoked from AOT
     * compilation.
     *
     * @param loader The classloader to be used for finding languages.
     * @return A map of initialized languages.
     */
    private static Map<String, LanguageCache> initializeLanguages(final ClassLoader loader) {
        return createLanguages(loader);
    }

    private LanguageCache(String prefix, Properties info, ClassLoader loader) {
        this.loader = loader;
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.version = info.getProperty(prefix + "version");
        String resolvedId = info.getProperty(prefix + "id");

        TreeSet<String> mimeTypesSet = new TreeSet<>();
        for (int i = 0;; i++) {
            String mt = info.getProperty(prefix + "mimeType." + i);
            if (mt == null) {
                break;
            }
            mimeTypesSet.add(mt);
        }
        this.mimeTypes = Collections.unmodifiableSet(mimeTypesSet);
        this.id = resolvedId == null ? defaultId() : resolvedId;
        this.interactive = Boolean.valueOf(info.getProperty(prefix + "interactive"));
        this.internal = Boolean.valueOf(info.getProperty(prefix + "internal"));

        if (PRELOAD) {
            this.languageClass = loadLanguageClass();
            this.singletonLanguage = readSingleton(languageClass);
        } else {
            this.languageClass = null;
            this.singletonLanguage = null;
        }
    }

    @SuppressWarnings("unchecked")
    LanguageCache(String id, Set<String> mimeTypes, String name, String version, boolean interactive, boolean internal,
                    TruffleLanguage<?> instance) {
        this.id = id;
        this.className = instance.getClass().getName();
        this.mimeTypes = mimeTypes;
        this.name = name;
        this.version = version;
        this.interactive = interactive;
        this.internal = internal;
        this.loader = instance.getClass().getClassLoader();
        this.singletonLanguage = instance;
        this.languageClass = (Class<? extends TruffleLanguage<?>>) instance.getClass();
    }

    private String defaultId() {
        String resolvedId;
        if (name.isEmpty() && !mimeTypes.isEmpty()) {
            resolvedId = mimeTypes.iterator().next();
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

    private static ClassLoader loader() {
        ClassLoader l;
        if (PolyglotEngine.JDK8OrEarlier) {
            l = PolyglotEngine.class.getClassLoader();
            if (l == null) {
                l = ClassLoader.getSystemClassLoader();
            }
        } else {
            l = ModuleResourceLocator.createLoader();
        }
        return l;
    }

    static Map<String, LanguageCache> languages() {
        if (PRELOAD) {
            return CACHE;
        }
        if (cache == null) {
            synchronized (LanguageCache.class) {
                if (cache == null) {
                    cache = createLanguages(null);
                }
            }
        }
        return cache;
    }

    private static Map<String, LanguageCache> createLanguages(ClassLoader additionalLoader) {
        List<LanguageCache> caches = new ArrayList<>();
        for (ClassLoader loader : (AOT_LOADERS == null ? VMAccessor.SPI.allLoaders() : AOT_LOADERS)) {
            collectLanguages(loader, caches);
        }
        if (additionalLoader != null) {
            collectLanguages(additionalLoader, caches);
        }
        Map<String, LanguageCache> seenClasses = new HashMap<>();
        for (LanguageCache languageCache : caches) {
            seenClasses.put(languageCache.className, languageCache);
        }
        Map<String, LanguageCache> cacheToMimeType = new HashMap<>();
        for (LanguageCache languageCache : caches) {
            for (String mimeType : languageCache.getMimeTypes()) {
                cacheToMimeType.put(mimeType, languageCache);
            }
        }
        return cacheToMimeType;
    }

    private static void collectLanguages(ClassLoader loader, List<LanguageCache> list) {
        if (loader == null) {
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
            try {
                p = new Properties();
                try (InputStream is = u.openStream()) {
                    p.load(is);
                }
            } catch (IOException ex) {
                LOG.log(Level.CONFIG, "Cannot process " + u + " as language definition", ex);
                continue;
            }
            for (int cnt = 1;; cnt++) {
                String prefix = "language" + cnt + ".";
                String name = p.getProperty(prefix + "name");
                if (name == null) {
                    break;
                }
                list.add(new LanguageCache(prefix, p, loader));
            }
        }
    }

    public int compareTo(LanguageCache o) {
        return className.compareTo(o.className);
    }

    String getId() {
        return id;
    }

    Set<String> getMimeTypes() {
        return mimeTypes;
    }

    String getName() {
        return name;
    }

    String getVersion() {
        return version;
    }

    String getClassName() {
        return className;
    }

    public boolean isInternal() {
        return internal;
    }

    boolean isInteractive() {
        return interactive;
    }

    LoadedLanguage loadLanguage() {
        TruffleLanguage<?> instance;
        boolean singleton = true;
        try {
            if (PRELOAD) {
                instance = singletonLanguage;
                if (instance == null) {
                    instance = this.languageClass.newInstance();
                    singleton = false;
                }
            } else {
                Class<? extends TruffleLanguage<?>> clazz = loadLanguageClass();
                try {
                    instance = readSingleton(clazz);
                    if (instance == null) {
                        instance = clazz.newInstance();
                        singleton = false;
                    }
                } catch (Exception e) {
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create instance of " + name + " language implementation. Public default constructor expected in " + className + ".", e);
        }
        return new LoadedLanguage(instance, singleton);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends TruffleLanguage<?>> loadLanguageClass() {
        if (languageClass == null) {
            synchronized (this) {
                if (languageClass == null) {
                    try {
                        languageClass = (Class<? extends TruffleLanguage<?>>) Class.forName(className, true, loader);
                    } catch (Exception e) {
                        throw new IllegalStateException("Cannot load language " + name + ". Language implementation class " + className + " failed to load.", e);
                    }
                }
            }
        }
        return languageClass;
    }

    private static TruffleLanguage<?> readSingleton(Class<?> languageClass) {
        try {
            return (TruffleLanguage<?>) languageClass.getField("INSTANCE").get(null);
        } catch (Exception ex) {
            return null;
        }
    }

    static final class LoadedLanguage {

        private final TruffleLanguage<?> language;
        private final boolean singleton;

        LoadedLanguage(TruffleLanguage<?> language, boolean singleton) {
            this.singleton = singleton;
            this.language = language;
        }

        TruffleLanguage<?> getLanguage() {
            return language;
        }

        boolean isSingleton() {
            return singleton;
        }

    }

}
