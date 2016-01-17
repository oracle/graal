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

//TODO (chumer): maybe this class should share some code with LanguageCache?
final class InstrumentCache {

    static final boolean PRELOAD;
    private static final List<InstrumentCache> CACHE;

    private Class<?> instrumentationClass;
    private final String className;
    private final String id;
    private final String name;
    private final String version;
    private final boolean autostart;

    static {
        List<InstrumentCache> instruments = null;
        if (Boolean.getBoolean("com.oracle.truffle.aot")) { // NOI18N
            instruments = load(null);
            for (InstrumentCache info : instruments) {
                info.loadClass();
            }
        }
        CACHE = instruments;
        PRELOAD = CACHE != null;
    }

    private static ClassLoader loader() {
        ClassLoader l = PolyglotEngine.class.getClassLoader();
        if (l == null) {
            l = ClassLoader.getSystemClassLoader();
        }
        return l;
    }

    InstrumentCache(String prefix, Properties info) {
        this.className = info.getProperty(prefix + "className");
        this.name = info.getProperty(prefix + "name");
        this.version = info.getProperty(prefix + "version");
        this.autostart = Boolean.parseBoolean(info.getProperty(prefix + "autostart"));
        String loadedId = info.getProperty(prefix + "id");
        if (loadedId.equals("")) {
            /* use class name default id */
            this.id = className;
        } else {
            this.id = loadedId;
        }
    }

    static List<InstrumentCache> load(ClassLoader customLoader) {
        if (PRELOAD) {
            return CACHE;
        }
        ClassLoader loader = customLoader == null ? loader() : customLoader;
        List<InstrumentCache> list = new ArrayList<>();
        Set<String> classNamesUsed = new HashSet<>();
        Enumeration<URL> en;
        try {
            en = loader.getResources("META-INF/truffle/instrumentation");
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read list of Truffle instrumentations", ex);
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
                String prefix = "instrumentation" + cnt + ".";
                String className = p.getProperty(prefix + "className");
                if (className == null) {
                    break;
                }
                // we don't want multiple instrumentations with the same class name
                if (!classNamesUsed.contains(className)) {
                    classNamesUsed.add(className);
                    list.add(new InstrumentCache(prefix, p));
                }
            }
        }
        Collections.sort(list, new Comparator<InstrumentCache>() {
            public int compare(InstrumentCache o1, InstrumentCache o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        return list;
    }

    String getId() {
        return id;
    }

    boolean isAutostart() {
        return autostart;
    }

    String getName() {
        return name;
    }

    String getVersion() {
        return version;
    }

    Class<?> getInstrumentationClass() {
        if (!PRELOAD && instrumentationClass == null) {
            loadClass();
        }
        return instrumentationClass;
    }

    private void loadClass() {
        try {
            instrumentationClass = Class.forName(className, true, loader());
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialize " + getName() + " instrumentation with implementation " + className, ex);
        }
    }

}