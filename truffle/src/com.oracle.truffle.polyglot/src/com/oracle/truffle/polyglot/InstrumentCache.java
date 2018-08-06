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
package com.oracle.truffle.polyglot;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

//TODO (chumer): maybe this class should share some code with LanguageCache?
final class InstrumentCache {

    private static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    private static final List<InstrumentCache> nativeImageCache = TruffleOptions.AOT ? new ArrayList<>() : null;
    private static List<InstrumentCache> runtimeCache;

    private Class<? extends TruffleInstrument> instrumentClass;
    private final String className;
    private final String id;
    private final String name;
    private final String version;
    private final boolean internal;
    private final ClassLoader loader;
    private final Set<String> services;

    /**
     * Initializes state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     * @param imageClassLoader class loader passed by the image builder.
     */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState(ClassLoader imageClassLoader) {
        nativeImageCache.addAll(doLoad(Collections.singletonList(imageClassLoader)));
    }

    /**
     * Initializes state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        nativeImageCache.clear();
        runtimeCache = null;
    }

    private InstrumentCache(String prefix, Properties info, ClassLoader loader) {
        this.loader = loader;
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.version = info.getProperty(prefix + "version");
        this.internal = Boolean.valueOf(info.getProperty(prefix + "internal"));
        String loadedId = info.getProperty(prefix + "id");
        if (loadedId.equals("")) {
            /* use class name default id */
            int lastIndex = className.lastIndexOf('$');
            if (lastIndex == -1) {
                lastIndex = className.lastIndexOf('.');
            }
            this.id = className.substring(lastIndex + 1, className.length());
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
        if (TruffleOptions.AOT) {
            loadClass();
        }
    }

    public boolean isInternal() {
        return internal;
    }

    static List<InstrumentCache> load(Collection<ClassLoader> loaders) {
        if (TruffleOptions.AOT) {
            return nativeImageCache;
        }
        if (runtimeCache != null) {
            return runtimeCache;
        }
        return doLoad(loaders);
    }

    private static List<InstrumentCache> doLoad(Collection<ClassLoader> loaders) {
        List<InstrumentCache> list = new ArrayList<>();
        Set<String> classNamesUsed = new HashSet<>();
        for (ClassLoader loader : loaders) {
            loadForOne(loader, list, classNamesUsed);
        }
        if (!JDK8OrEarlier) {
            loadForOne(ModuleResourceLocator.createLoader(), list, classNamesUsed);
        }
        return runtimeCache = list;
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
                PrintStream out = System.err;
                out.println("Cannot process " + u + " as language definition");
                ex.printStackTrace();
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

    String getClassName() {
        return className;
    }

    String getVersion() {
        return version;
    }

    Class<?> getInstrumentationClass() {
        if (!TruffleOptions.AOT && instrumentClass == null) {
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
            instrumentClass = Class.forName(className, true, loader).asSubclass(TruffleInstrument.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialize " + getName() + " instrument with implementation " + className, ex);
        }
    }

}
