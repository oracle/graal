/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.WeakHashMap;

//TODO (chumer): maybe this class should share some code with LanguageCache?
final class InstrumentCache {

    private static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    private static final List<InstrumentCache> nativeImageCache = TruffleOptions.AOT ? new ArrayList<>() : null;
    private static Map<ClassLoader, List<InstrumentCache>> runtimeCaches = new WeakHashMap<>();

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
        nativeImageCache.addAll(doLoad(Collections.emptyList(), imageClassLoader));
    }

    /**
     * Initializes state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     */
    @SuppressWarnings("unused")
    private static void resetNativeImageState() {
        nativeImageCache.clear();
        runtimeCaches.clear();
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

    static List<InstrumentCache> load(Collection<ClassLoader> loaders, ClassLoader additionalLoader) {
        if (TruffleOptions.AOT) {
            return nativeImageCache;
        }
        synchronized (InstrumentCache.class) {
            List<InstrumentCache> cache = runtimeCaches.get(additionalLoader);
            if (cache == null) {
                cache = doLoad(loaders, additionalLoader);
                runtimeCaches.put(additionalLoader, cache);
            }
            return cache;
        }
    }

    private static List<InstrumentCache> doLoad(Collection<ClassLoader> loaders, ClassLoader additionalLoader) {
        List<InstrumentCache> list = new ArrayList<>();
        Set<String> classNamesUsed = new HashSet<>();
        for (ClassLoader loader : loaders) {
            loadForOne(loader, list, classNamesUsed);
        }
        if (additionalLoader != null) {
            loadForOne(additionalLoader, list, classNamesUsed);
        }
        if (!JDK8OrEarlier) {
            loadForOne(ModuleResourceLocator.createLoader(), list, classNamesUsed);
        }
        return list;
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
