/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.polyglot.EngineAccessor.AbstractClassLoaderSupplier;
import com.oracle.truffle.polyglot.EngineAccessor.StrongClassLoaderSupplier;

final class InstrumentCache {

    private static final String DEBUGGER_CLASS = "com.oracle.truffle.api.debug.impl.DebuggerInstrument";
    private static final String DEBUGGER_PROVIDER = "com.oracle.truffle.api.debug.impl.DebuggerInstrumentProvider";
    private static final List<InstrumentCache> nativeImageCache = TruffleOptions.AOT ? new ArrayList<>() : null;
    private static Map<List<AbstractClassLoaderSupplier>, List<InstrumentCache>> runtimeCaches = new HashMap<>();

    private final String className;
    private final String id;
    private final String name;
    private final String version;
    private final String website;
    private final boolean internal;
    private final Set<String> services;
    private final TruffleInstrument.Provider provider;

    /**
     * Initializes state for native image generation.
     *
     * NOTE: this method is called reflectively by downstream projects.
     *
     * @param imageClassLoader class loader passed by the image builder.
     */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState(ClassLoader imageClassLoader) {
        nativeImageCache.addAll(doLoad(Arrays.asList(new StrongClassLoaderSupplier(imageClassLoader))));
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

    private InstrumentCache(String id, String name, String version, String className, boolean internal, Set<String> services,
                    TruffleInstrument.Provider provider, String website) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.website = website;
        this.className = className;
        this.internal = internal;
        this.services = services;
        this.provider = provider;
    }

    boolean isInternal() {
        return internal;
    }

    static List<InstrumentCache> load() {
        if (TruffleOptions.AOT) {
            return nativeImageCache;
        }
        synchronized (InstrumentCache.class) {
            List<AbstractClassLoaderSupplier> classLoaders = EngineAccessor.locatorOrDefaultLoaders();
            List<InstrumentCache> cache = runtimeCaches.get(classLoaders);
            if (cache == null) {
                cache = doLoad(classLoaders);
                runtimeCaches.put(classLoaders, cache);
            }
            return cache;
        }
    }

    static List<InstrumentCache> doLoad(List<AbstractClassLoaderSupplier> suppliers) {
        List<InstrumentCache> list = new ArrayList<>();
        Set<String> classNamesUsed = new HashSet<>();
        for (Supplier<ClassLoader> supplier : suppliers) {
            ClassLoader loader = supplier.get();
            if (loader == null || !isValidLoader(loader)) {
                continue;
            }
            if (!TruffleOptions.AOT) {
                // In JDK 9+, the Truffle API packages must be dynamically exported to
                // a Truffle instrument since the Truffle API module descriptor only
                // exports the packages to modules known at build time (such as the
                // Graal module).
                EngineAccessor.JDKSERVICES.exportTo(loader, null);
            }
            for (TruffleInstrument.Provider provider : ServiceLoader.load(TruffleInstrument.Provider.class, loader)) {
                loadInstrumentImpl(provider, list, classNamesUsed);
            }

            /*
             * Make sure the builtin debugger instrument is loaded if the service loader does not
             * pick them up. This may happen on JDK 11 if the loader delegates to the platform class
             * loader and does see the Truffle module only through the special named module behavior
             * for the platform class loader. However, while truffle classes are visible, Java
             * services are not enumerated from there. This is a workaround, that goes around this
             * problem by hardcoding instruments that are included in the Truffle module. The
             * behavior is actually beneficial as this also avoids languages to be picked up from
             * the application classpath.
             */
            if (!classNamesUsed.contains(DEBUGGER_CLASS)) {
                try {
                    loadInstrumentImpl((TruffleInstrument.Provider) loader.loadClass(DEBUGGER_PROVIDER).getConstructor().newInstance(), list,
                                    classNamesUsed);
                } catch (Exception e) {
                    throw shouldNotReachHere("Failed to discover debugger instrument.", e);
                }
            }
        }
        Collections.sort(list, new Comparator<InstrumentCache>() {
            @Override
            public int compare(InstrumentCache o1, InstrumentCache o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        return list;
    }

    private static void loadInstrumentImpl(TruffleInstrument.Provider provider, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
        Registration reg = provider.getClass().getAnnotation(Registration.class);
        if (reg == null) {
            PrintStream out = System.err;
            out.println("Provider " + provider.getClass() + " is missing @Registration annotation.");
            return;
        }
        String className = provider.getInstrumentClassName();
        String name = reg.name();
        String id = reg.id();
        if (id == null || id.isEmpty()) {
            /* use class name default id */
            int lastIndex = className.lastIndexOf('$');
            if (lastIndex == -1) {
                lastIndex = className.lastIndexOf('.');
            }
            id = className.substring(lastIndex + 1);
        }
        String version = reg.version();
        String website = reg.website();
        boolean internal = reg.internal();
        Set<String> servicesClassNames = new TreeSet<>();
        for (String service : provider.getServicesClassNames()) {
            servicesClassNames.add(service);
        }
        // we don't want multiple instruments with the same class name
        if (!classNamesUsed.contains(className)) {
            classNamesUsed.add(className);
            list.add(new InstrumentCache(id, name, version, className, internal, servicesClassNames, provider, website));
        }
    }

    private static boolean isValidLoader(ClassLoader loader) {
        try {
            Class<?> truffleInstrumentClassAsSeenByLoader = Class.forName(TruffleInstrument.class.getName(), true, loader);
            return truffleInstrumentClassAsSeenByLoader == TruffleInstrument.class;
        } catch (ClassNotFoundException ex) {
            return false;
        }
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

    TruffleInstrument loadInstrument() {
        return provider.create();
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    String[] services() {
        return services.toArray(new String[0]);
    }

    String getWebsite() {
        return website;
    }
}
