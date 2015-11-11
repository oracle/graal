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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import static com.oracle.truffle.api.vm.PolyglotEngine.LOG;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

/**
 * Ahead-of-time initialization. If the JVM is started with {@link TruffleOptions#AOT}, it populates
 * cache with languages found in application classloader.
 */
final class LanguageCache {
    static final boolean PRELOAD;
    private static final Map<String, LanguageCache> CACHE;
    private TruffleLanguage<?> language;
    private final String className;
    private final Set<String> mimeTypes;
    private final String name;
    private final String version;

    static {
        Map<String, LanguageCache> map = null;
        if (TruffleOptions.AOT) {
            map = languages();
            for (LanguageCache info : map.values()) {
                info.getImpl(true);
            }
        }
        CACHE = map;
        PRELOAD = CACHE != null;
    }

    LanguageCache(String prefix, Properties info, TruffleLanguage<?> language) {
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.version = info.getProperty(prefix + "version");
        TreeSet<String> ts = new TreeSet<>();
        for (int i = 0;; i++) {
            String mt = info.getProperty(prefix + "mimeType." + i);
            if (mt == null) {
                break;
            }
            ts.add(mt);
        }
        this.mimeTypes = Collections.unmodifiableSet(ts);
        this.language = language;
    }

    private static ClassLoader loader() {
        ClassLoader l = PolyglotEngine.class.getClassLoader();
        if (l == null) {
            l = ClassLoader.getSystemClassLoader();
        }
        return l;
    }

    private static TruffleLanguage<?> find(String name, ClassLoader loader) throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        if (PRELOAD) {
            return CACHE.get(name).language;
        } else {
            Class<?> langClazz = Class.forName(name, true, loader);
            return (TruffleLanguage<?>) langClazz.getField("INSTANCE").get(null);
        }
    }

    static Map<String, LanguageCache> languages() {
        if (PRELOAD) {
            return CACHE;
        }
        ClassLoader loader = loader();
        Map<String, LanguageCache> map = new LinkedHashMap<>();
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
                if (p.getProperty(prefix + "name") == null) {
                    break;
                }
                LanguageCache l = new LanguageCache(prefix, p, null);
                for (String mimeType : l.getMimeTypes()) {
                    map.put(mimeType, l);
                }
            }
        }
        return map;
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

    TruffleLanguage<?> getImpl(boolean create) {
        if (PRELOAD) {
            return language;
        }
        if (create) {
            try {
                language = LanguageCache.find(className, loader());
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot initialize " + getName() + " language with implementation " + className, ex);
            }
        }
        return language;
    }

}
