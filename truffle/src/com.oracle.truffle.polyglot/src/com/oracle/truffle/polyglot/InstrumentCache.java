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
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.TruffleJDKServices;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

//TODO (chumer): maybe this class should share some code with LanguageCache?
final class InstrumentCache {

    private static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    private static final List<InstrumentCache> nativeImageCache = TruffleOptions.AOT ? new ArrayList<>() : null;
    private static Map<ClassLoader, List<InstrumentCache>> runtimeCaches = new WeakHashMap<>();

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
            Loader.load(loader, list, classNamesUsed);
        }
        if (additionalLoader != null) {
            Loader.load(additionalLoader, list, classNamesUsed);
        }
        if (!JDK8OrEarlier) {
            Loader.load(ModuleResourceLocator.createLoader(), list, classNamesUsed);
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

        static void exportTruffle(ClassLoader loader) {
            if (!TruffleOptions.AOT) {
                // In JDK 9+, the Truffle API packages must be dynamically exported to
                // a Truffle instrument since the Truffle API module descriptor only
                // exports the packages to modules known at build time (such as the
                // Graal module).
                TruffleJDKServices.exportTo(loader, null);
            }
        }
    }

    private abstract static class Loader {

        private static final Loader[] INSTANCES = new Loader[]{
                        new LegacyLoader(),
                        new ServicesLoader()
        };

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
            for (Loader instance : INSTANCES) {
                instance.loadImpl(loader, list, classNamesUsed);
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
                throw new UnsupportedOperationException("Not supported yet."); // To change body of
                                                                               // generated methods,
                                                                               // choose Tools |
                                                                               // Templates.
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

        ServicesLoader() {
        }

        @Override
        void loadImpl(ClassLoader loader, List<? super InstrumentCache> list, Set<? super String> classNamesUsed) {
            for (TruffleInstrument.Provider provider : ServiceLoader.load(TruffleInstrument.Provider.class, loader)) {
                Registration reg = provider.getClass().getAnnotation(Registration.class);
                if (reg == null) {
                    PrintStream out = System.err;
                    out.println("Provider " + provider.getClass() + " is missing @Registration annotation.");
                    continue;
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
                for (Class<?> service : reg.services()) {
                    servicesClassNames.add(service.getCanonicalName());
                }
                // we don't want multiple instruments with the same class name
                if (!classNamesUsed.contains(className)) {
                    classNamesUsed.add(className);
                    InstrumentReflection reflection = new ServiceLoaderInstrumentReflection(provider);
                    list.add(new InstrumentCache(id, name, version, className, internal, servicesClassNames, reflection));
                }
            }
        }

        private static final class ServiceLoaderInstrumentReflection extends InstrumentReflection {

            private final TruffleInstrument.Provider provider;
            private final AtomicBoolean exported = new AtomicBoolean();

            ServiceLoaderInstrumentReflection(TruffleInstrument.Provider provider) {
                assert provider != null;
                this.provider = provider;
            }

            @Override
            TruffleInstrument newInstance() {
                if (exported.compareAndSet(false, true)) {
                    exportTruffle(provider.getClass().getClassLoader());
                }
                return provider.create();
            }

            @Override
            Class<? extends TruffleInstrument> aotInitializeAtBuildTime() {
                return null;
            }
        }
    }

}
