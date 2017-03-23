/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import com.oracle.truffle.api.vm.PolyglotEngine.Access;
import java.util.TreeSet;

//TODO (chumer): maybe this class should share some code with LanguageCache?
final class InstrumentCache {

    static final boolean PRELOAD;
    private static final List<InstrumentCache> CACHE;
    private static List<InstrumentCache> cache;

    private Class<?> instrumentClass;
    private final String className;
    private final String id;
    private final String name;
    private final String version;
    private final ClassLoader loader;
    private final Set<String> services;

    static {
        List<InstrumentCache> instruments = null;
        if (Boolean.getBoolean("com.oracle.truffle.aot")) { // NOI18N
            instruments = load();
            for (InstrumentCache info : instruments) {
                info.loadClass();
            }
        }
        CACHE = instruments;
        PRELOAD = CACHE != null;
    }

    InstrumentCache(String prefix, Properties info, ClassLoader loader) {
        this.loader = loader;
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.version = info.getProperty(prefix + "version");
        String loadedId = info.getProperty(prefix + "id");
        if (loadedId.equals("")) {
            /* use class name default id */
            this.id = className;
        } else {
            this.id = loadedId;
        }
        int servicesCounter = 0;
        this.services = new TreeSet<>();
        for (;;) {
            String nth = prefix + "service" + servicesCounter++;
            String serviceName = info.getProperty(nth);
            if (serviceName == null) {
                break;
            }
            this.services.add(serviceName);
        }
    }

    static List<InstrumentCache> load() {
        if (PRELOAD) {
            return CACHE;
        }
        if (cache != null) {
            return cache;
        }
        List<InstrumentCache> list = new ArrayList<>();
        Set<String> classNamesUsed = new HashSet<>();
        for (ClassLoader loader : (LanguageCache.AOT_LOADERS == null ? Access.loaders() : LanguageCache.AOT_LOADERS)) {
            loadForOne(loader, list, classNamesUsed);
        }
        if (!PolyglotEngine.JDK8OrEarlier) {
            loadForOne(ModuleResourceLocator.createLoader(), list, classNamesUsed);
        }
        return cache = list;
    }

    private static void loadForOne(ClassLoader loader, List<InstrumentCache> list, Set<String> classNamesUsed) {
        if (loader == null) {
            return;
        }
        Enumeration<URL> en;
        try {
            en = loader.getResources("META-INF/truffle/instrument");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read list of Truffle instruments", ex);
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
                String prefix = "instrument" + cnt + ".";
                String className = p.getProperty(prefix + "className");
                if (className == null) {
                    break;
                }
                // we don't want multiple instruments with the same class name
                if (!classNamesUsed.contains(className)) {
                    classNamesUsed.add(className);
                    list.add(new InstrumentCache(prefix, p, loader));
                }
            }
        }
        Collections.sort(list, new Comparator<InstrumentCache>() {
            @Override
            public int compare(InstrumentCache o1, InstrumentCache o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getVersion() {
        return version;
    }

    Class<?> getInstrumentationClass() {
        if (!PRELOAD && instrumentClass == null) {
            loadClass();
        }
        return instrumentClass;
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    String[] services() {
        return services.toArray(new String[0]);
    }

    private void loadClass() {
        try {
            instrumentClass = Class.forName(className, true, loader);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialize " + getName() + " instrument with implementation " + className, ex);
        }
    }

}
