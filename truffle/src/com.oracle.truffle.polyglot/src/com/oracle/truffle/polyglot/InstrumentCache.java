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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
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

    private static final List<InstrumentCache> nativeImageCache = TruffleOptions.AOT ? new ArrayList<>() : null;
    private static Map<List<AbstractClassLoaderSupplier>, List<InstrumentCache>> runtimeCaches = new HashMap<>();

    private final String className;
    private final String id;
    private final String name;
    private final String version;
    private final boolean internal;
    private final Set<String> services;
    private final InstrumentReflection instrumentReflection;

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

    private InstrumentCache(String id, String name, String version, String className, boolean internal, Set<String> services, InstrumentReflection instrumentReflection) {
        this.instrumentReflection = instrumentReflection;
        this.id = id;
        this.name = name;
        this.version = version;
        this.className = className;
        this.internal = internal;
        this.services = services;

        if (TruffleOptions.AOT) {
            instrumentReflection.aotInitializeAtBuildTime();
        }
    }

    public boolean isInternal() {
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
            Loader.load(supplier.get(), list, classNamesUsed);
        }
        Collections.sort(list, new Comparator<InstrumentCache>() {
            @Override
            public int compare(InstrumentCache o1, InstrumentCache o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        return list;
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
        return instrumentReflection.newInstance();
    }

    boolean supportsService(Class<?> clazz) {
        return services.contains(clazz.getName()) || services.contains(clazz.getCanonicalName());
    }

    String[] services() {
        return services.toArray(new String[0]);
    }

    private abstract static class InstrumentReflection {

        abstract TruffleInstrument newInstance();

        abstract Class<? extends TruffleInstrument> aotInitializeAtBuildTime();
    }

    private abstract static class Loader {

        static void load(ClassLoader loader, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
            if (loader == null) {
                return;
            }
            try {
                Class<?> truffleInstrumentClassAsSeenByLoader = Class.forName(TruffleInstrument.class.getName(), true, loader);
                if (truffleInstrumentClassAsSeenByLoader != TruffleInstrument.class) {
                    return;
                }
            } catch (ClassNotFoundException ex) {
                return;
            }
            LegacyLoader.INSTANCE.loadImpl(loader, list, classNamesUsed);
            ServicesLoader.INSTANCE.loadImpl(loader, list, classNamesUsed);
        }

        static void exportTruffle(ClassLoader loader) {
            if (!TruffleOptions.AOT) {
                // In JDK 9+, the Truffle API packages must be dynamically exported to
                // a Truffle instrument since the Truffle API module descriptor only
                // exports the packages to modules known at build time (such as the
                // Graal module).
                EngineAccessor.JDKSERVICES.exportTo(loader, null);
            }
        }

        abstract void loadImpl(ClassLoader loader, List<? super InstrumentCache> list, Set<? super String> classNamesUsed);

        static String defaultId(String className) {
            /* use class name default id */
            int lastIndex = className.lastIndexOf('$');
            if (lastIndex == -1) {
                lastIndex = className.lastIndexOf('.');
            }
            return className.substring(lastIndex + 1, className.length());
        }
    }

    private static final class LegacyLoader extends Loader {

        static final Loader INSTANCE = new LegacyLoader();

        private LegacyLoader() {
        }

        @Override
        void loadImpl(ClassLoader loader, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
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
                        list.add(createInstrumentCache(prefix, p, loader));
                    }
                }
            }
        }

        private static InstrumentCache createInstrumentCache(String prefix, Properties info, ClassLoader loader) {
            String className = info.getProperty(prefix + "className");
            String name = info.getProperty(prefix + "name");
            String version = info.getProperty(prefix + "version");
            boolean internal = Boolean.valueOf(info.getProperty(prefix + "internal"));
            String id = info.getProperty(prefix + "id");
            if (id == null || id.isEmpty()) {
                id = defaultId(className);
            }
            int servicesCounter = 0;
            Set<String> services = new TreeSet<>();
            for (;;) {
                String nth = prefix + "service" + servicesCounter++;
                String serviceName = info.getProperty(nth);
                if (serviceName == null) {
                    break;
                }
                services.add(serviceName);
            }
            InstrumentReflection reflection = new LegacyInstrumentReflection(name, loader, className);
            return new InstrumentCache(id, name, version, className, internal, services, reflection);
        }

        private static final class LegacyInstrumentReflection extends InstrumentReflection {

            private final String name;
            private final ClassLoader loader;
            private final String className;
            private volatile Class<? extends TruffleInstrument> instrumentClass;

            LegacyInstrumentReflection(String name, ClassLoader loader, String className) {
                Objects.requireNonNull(name, "Name must be non null.");
                Objects.requireNonNull(loader, "Loader must be non null.");
                Objects.requireNonNull(className, "ClassName must be non null.");
                this.name = name;
                this.loader = loader;
                this.className = className;
            }

            @Override
            TruffleInstrument newInstance() {
                try {
                    return getInstrumentationClass().getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create instance of " + name + " language implementation. Public default constructor expected in " + className + ".", e);
                }
            }

            @Override
            Class<? extends TruffleInstrument> aotInitializeAtBuildTime() {
                initializeInstrumentClass();
                assert instrumentClass != null;
                return instrumentClass;
            }

            private Class<? extends TruffleInstrument> getInstrumentationClass() {
                if (!TruffleOptions.AOT) {
                    initializeInstrumentClass();
                }
                return instrumentClass;
            }

            private void initializeInstrumentClass() {
                if (instrumentClass == null) {
                    synchronized (this) {
                        if (instrumentClass == null) {
                            try {
                                exportTruffle(loader);
                                instrumentClass = Class.forName(className, true, loader).asSubclass(TruffleInstrument.class);
                            } catch (Exception ex) {
                                throw new IllegalStateException("Cannot initialize " + name + " instrument with implementation " + className, ex);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class ServicesLoader extends Loader {

        static final Loader INSTANCE = new ServicesLoader();
        private static final String DEBUGGER_CLASS = "com.oracle.truffle.api.debug.impl.DebuggerInstrument";
        private static final String DEBUGGER_PROVIDER = "com.oracle.truffle.api.debug.impl.DebuggerInstrumentProvider";

        private ServicesLoader() {
        }

        @Override
        void loadImpl(ClassLoader loader, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
            exportTruffle(loader);
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

        static void loadInstrumentImpl(TruffleInstrument.Provider provider, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
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
                id = defaultId(className);
            }
            String version = reg.version();
            boolean internal = reg.internal();
            Set<String> servicesClassNames = new TreeSet<>();
            for (String service : provider.getServicesClassNames()) {
                servicesClassNames.add(service);
            }
            // we don't want multiple instruments with the same class name
            if (!classNamesUsed.contains(className)) {
                classNamesUsed.add(className);
                InstrumentReflection reflection = new ServiceLoaderInstrumentReflection(provider);
                list.add(new InstrumentCache(id, name, version, className, internal, servicesClassNames, reflection));
            }
        }

        private static final class ServiceLoaderInstrumentReflection extends InstrumentReflection {

            private final TruffleInstrument.Provider provider;

            ServiceLoaderInstrumentReflection(TruffleInstrument.Provider provider) {
                assert provider != null;
                this.provider = provider;
            }

            @Override
            TruffleInstrument newInstance() {
                return provider.create();
            }

            @Override
            Class<? extends TruffleInstrument> aotInitializeAtBuildTime() {
                return null;
            }
        }
    }

}
