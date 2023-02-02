/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.visualizer.settings;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.openide.util.WeakListeners;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import org.graalvm.visualizer.settings.SettingsStore.SettingsBean;

/**
 *
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public abstract class SettingsStore<S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> implements Settings {

    protected final B bean;

    protected static <S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> S obtain(Class<S> type) {
        return SettingsHolder.obtain(type);
    }

    protected static <S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> S obtain(Class<S> type, Supplier<S> get) {
        return SettingsHolder.obtain(type, get);
    }

    protected SettingsStore() {
        SettingsHolder.register(this);
        Map<Class, Map<String, Object>> defs = new HashMap<>();
        fillDefaults((n, v) -> def(defs, n, v));
        this.defaults = finalize(defs);
        bean = makeBean();
        bean.load();
    }

    protected abstract void fillDefaults(BiConsumer<String, Object> filler);

    protected final Preferences PREFERENCES = Preferences.userNodeForPackage(this.getClass());

    private final static class PrefMapper<T> {

        private final Function<Preferences, Function<String, Consumer<T>>> setter;
        private final Function<Preferences, Function<String, Function<T, T>>> getter;

        public PrefMapper(Function<Preferences, Function<String, Consumer<T>>> setter, Function<Preferences, Function<String, Function<T, T>>> getter) {
            this.setter = setter;
            this.getter = getter;
        }

        public void save(Preferences p, String name, T val) {
            setter.apply(p).apply(name).accept(val);
        }

        public void save(Preferences p, Map.Entry<String, T> entry) {
            save(p, entry.getKey(), entry.getValue());
        }

        public T load(Preferences p, String name, T val) {
            return getter.apply(p).apply(name).apply(val);
        }

        public T load(Preferences p, Map.Entry<String, T> entry) {
            return load(p, entry.getKey(), entry.getValue());
        }
    }
    private static final Map<Class<?>, PrefMapper> prefMaps;

    static {
        Map<Class<?>, PrefMapper> prefMapps = new HashMap<>();
        prefMapps.put(Boolean.class, new PrefMapper<Boolean>(
                p -> n -> v -> p.putBoolean(n, v),
                p -> n -> v -> p.getBoolean(n, v)));
        prefMapps.put(Integer.class, new PrefMapper<Integer>(
                p -> n -> v -> p.putInt(n, v),
                p -> n -> v -> p.getInt(n, v)));
        prefMapps.put(Float.class, new PrefMapper<Float>(
                p -> n -> v -> p.putFloat(n, v),
                p -> n -> v -> p.getFloat(n, v)));
        prefMapps.put(Double.class, new PrefMapper<Double>(
                p -> n -> v -> p.putDouble(n, v),
                p -> n -> v -> p.getDouble(n, v)));
        prefMapps.put(String.class, new PrefMapper<String>(
                p -> n -> v -> p.put(n, v),
                p -> n -> v -> p.get(n, v)));
        prefMaps = Collections.unmodifiableMap(prefMapps);
    }

    protected abstract B makeBean();

    public B obtainBean() {
        return bean.copy();
    }

    public final Map<Class, Map<String, Object>> defaults;

    private static void def(Map<Class, Map<String, Object>> defs, String propName, Object def) {
        defs.computeIfAbsent(def.getClass(), (c) -> new HashMap<>()).put(propName, def);
    }

    private static Map<Class, Map<String, Object>> finalize(Map<Class, Map<String, Object>> defs) {
        defs.replaceAll((k, m) -> Collections.unmodifiableMap(m));
        return Collections.unmodifiableMap(defs);
    }

    @Override
    public <T> T set(String propertyName, T val) {
        T obj = bean.set(propertyName, val);
        if (obj != null && !obj.equals(val)) {
            Class type = obj.getClass();
            PrefMapper mapper = prefMaps.get(type);
            if (mapper != null) {
                mapper.save(PREFERENCES, propertyName, val == null ? getDefault(type, propertyName) : val);
            }
        }
        return obj;
    }

    @Override
    public <T> T get(Class<T> type, String propertyName) {
        return bean.get(type, propertyName);
    }

    @Override
    public <T> T get(String propertyName) {
        return bean.get(propertyName);
    }

    <T> T getDefault(Class<T> type, String propertyName) {
        return (T) defaults.getOrDefault(type, Collections.EMPTY_MAP).get(propertyName);
    }

    <T> T getDefault(String propertyName) {
        for (Map<String, Object> map : defaults.values()) {
            Object val = map.get(propertyName);
            if (val != null) {
                return (T) val;
            }
        }
        return null;
    }

    @Override
    public void store() {
        bean.store();
    }

    public static abstract class SettingsBean<S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> implements Settings {

        final Map<Class, Map<String, Object>> settings;
        protected final S store;

        private SettingsBean() {
            this(null, null);
        }

        private SettingsBean(S store, Map<Class, Map<String, Object>> settings) {
            this.settings = settings;
            this.store = store;
        }

        protected SettingsBean(S store) {
            this(store, repack(store.defaults));
        }

        protected SettingsBean(B bean) {
            this(bean.store, repack(bean.settings));
        }

        private static Map<Class, Map<String, Object>> repack(Map<Class, Map<String, Object>> defs) {
            return repack(defs, new HashMap<>());
        }

        private static Map<Class, Map<String, Object>> repack(Map<Class, Map<String, Object>> defs, Map<Class, Map<String, Object>> tmp) {
            defs.forEach((t, m) -> tmp.put(t, new HashMap<>(m)));
            return tmp;
        }

        public abstract B copy();

        public void load() {
            Preferences prefs = store.PREFERENCES;
            store.defaults.forEach((t, m) -> {
                Map<String, Object> map = settings.get(t);
                if (map != null) {
                    PrefMapper mapper = prefMaps.get(t);
                    if (mapper != null) {
                        for (Map.Entry<String, Object> entry : m.entrySet()) {
                            map.put(entry.getKey(), mapper.load(prefs, entry));
                        }
                    }
                }
            });
        }

        void pull(B bean) {
            if (!bean.settings.equals(settings)) {
                repack(bean.settings, settings);
            }
        }

        public void reset() {
            repack(store.defaults, settings);
        }

        @Override
        public void store() {
            if (store.bean != this) {
                store.bean.pull((B) this);
            }
            Preferences prefs = store.PREFERENCES;
            settings.forEach((t, m) -> {
                PrefMapper mapper = prefMaps.get(t);
                if (mapper != null) {
                    m.entrySet().forEach((e) -> mapper.save(prefs, e));
                }
            });
        }

        @Override
        public <T> T set(String propertyName, T val) {
            if (val == null) {
                val = store.getDefault(propertyName);
                if (val == null) {
                    return null;
                }
            }
            Map<String, Object> tmap = settings.get(val.getClass());
            if (tmap != null && tmap.containsKey(propertyName)) {
                return (T) tmap.put(propertyName, val);
            }
            return null;
        }

        @Override
        public <T> T get(Class<T> type, String propertyName) {
            return (T) settings.getOrDefault(type, Collections.EMPTY_MAP).get(propertyName);
        }

        @Override
        public <T> T get(String propertyName) {
            for (Map<String, Object> map : settings.values()) {
                Object val = map.get(propertyName);
                if (val != null) {
                    return (T) val;
                }
            }
            return null;
        }

        @Override
        public void fireChanged() {
            store.fireChanged((B) this);
        }

        @Override
        public int hashCode() {
            return 97 * settings.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final SettingsBean other = (SettingsBean) obj;
            return settings.equals(other.settings);
        }
    }

    private static class WeakeningChangeListenerList extends EventListenerList {

        private final WeakHashMap<ChangeListener, ChangeListener> weakListenersMapping = new WeakHashMap<>();

        public void removeChangeListener(ChangeListener l) {
            ChangeListener wL = weakListenersMapping.get(l);
            if (wL != null) {
                weakListenersMapping.remove(l);
                remove(ChangeListener.class, wL);
            } else {
                remove(ChangeListener.class, l);
            }
        }

        public void addWeakChangeListener(ChangeListener l) {
            if (!weakListenersMapping.containsKey(l)) {
                ChangeListener wL = WeakListeners.change(l, this);
                weakListenersMapping.put(l, wL);
                add(ChangeListener.class, wL);
            }
        }

        public void addChangeListener(ChangeListener l) {
            if (!weakListenersMapping.containsKey(l)) {
                add(ChangeListener.class, l);
                weakListenersMapping.put(l, l);
            }
        }

        public void fire(ChangeEvent event) {
            for (ChangeListener l : getListeners(ChangeListener.class)) {
                l.stateChanged(event);
            }
        }
    }
    private final WeakeningChangeListenerList LISTENERS = new WeakeningChangeListenerList();

    public void addWeakChangeListener(ChangeListener l) {
        LISTENERS.addWeakChangeListener(l);
    }

    public void addChangeListener(ChangeListener l) {
        LISTENERS.addChangeListener(l);
    }

    public void removeChangeListener(ChangeListener l) {
        LISTENERS.removeChangeListener(l);
    }

    public void addPreferenceChangeListener(PreferenceChangeListener l) {
        PREFERENCES.addPreferenceChangeListener(l);
    }

    public void removePreferenceChangeListener(PreferenceChangeListener l) {
        PREFERENCES.removePreferenceChangeListener(l);
    }

    @Override
    public void fireChanged() {
        fireChanged(bean);
    }

    void fireChanged(B bean) {
        LISTENERS.fire(new ChangeEvent(bean.copy()));
    }

    private static class SettingsHolder {

        private SettingsHolder() {
        }
        private static Map<Class<? extends SettingsStore>, WeakReference<? extends SettingsStore>> hold = new HashMap<>();

        private synchronized static void register(SettingsStore store) {
            Class<? extends SettingsStore> type = store.getClass();
            WeakReference<? extends SettingsStore> wk = hold.get(type);
            if (wk != null && wk.get() != null) {
                throw new IllegalStateException("Can't instanciate more than one " + store.getClass());
            }
            hold.put(type, new WeakReference<>(store));
        }

        private synchronized static <S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> S obtain(Class<S> type) {
            WeakReference<? extends SettingsStore> wk = hold.get(type);
            if (wk == null) {
                return null;
            }
            return (S) wk.get();
        }

        private synchronized static <S extends SettingsStore<S, B>, B extends SettingsBean<S, B>> S obtain(Class<S> type, Supplier<S> get) {
            S settings = obtain(type);
            if (settings == null) {
                settings = get.get();
            }
            return settings;
        }
    }
}
