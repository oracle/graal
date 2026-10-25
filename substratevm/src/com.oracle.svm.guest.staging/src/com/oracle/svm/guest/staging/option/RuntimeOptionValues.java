/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.option;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;

import com.oracle.svm.guest.staging.GuestImageLayerBuildingSupport;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

/**
 * The singleton holder of runtime options.
 * <p>
 * Related core option package: {@code com.oracle.svm.core.option}.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = ApplicationLayerOnly.class)
public class RuntimeOptionValues {
    private final EconomicSet<String> allOptionNames;

    private final AtomicReference<Snapshot> v;

    public RuntimeOptionValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values, EconomicSet<String> allOptionNames) {
        this.allOptionNames = allOptionNames;

        updateCache(values);
        v = new AtomicReference<>(snapshot(null, values, values, Origin.BuildTime));
    }

    public static RuntimeOptionsSupport createRuntimeOptionsSupport() {
        return new RuntimeOptionsSupportImpl();
    }

    /**
     * In layered images we only expose the actual singleton within the final layer. In other layers
     * we expose a {@link SharedLayerRuntimeOptionsValues} singleton which does not allow values to
     * be modified.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static RuntimeOptionValues singleton() {
        if (!SubstrateUtil.HOSTED || GuestImageLayerBuildingSupport.lastImageBuild()) {
            return ImageSingletons.lookup(RuntimeOptionValues.class);
        } else {
            return ImageSingletons.lookup(SharedLayerRuntimeOptionsValues.class);
        }
    }

    UnmodifiableEconomicSet<String> getAllOptionNames() {
        return allOptionNames;
    }

    /// Applies an individual update with its explicit source.
    public void update(OptionKey<?> key, Object value, Origin origin) {
        if (key instanceof RuntimeOptionKey<?> r) {
            r.validateValueBeforeUpdate(value);
            /*
             * RuntimeOptionKey reads go through the per-key cache, so keep the old publication
             * order and update that cache before publishing the new snapshot.
             */
            r.setRawCachedValue(value);
        }

        UnmodifiableEconomicMap<OptionKey<?>, Object> updates = OptionValues.asMap(key, value);
        Snapshot expect;
        Snapshot newValues;
        EconomicMap<OptionKey<?>, Object> newMap;
        do {
            expect = v.get();
            newMap = EconomicMap.create(expect.values().getMap());
            if (key instanceof RuntimeOptionKey<?> runtimeOptionKey) {
                runtimeOptionKey.updateAfterValidation(newMap, value);
            } else {
                key.update(newMap, value);
            }
            newValues = snapshot(expect, newMap, updates, origin);
        } while (!v.compareAndSet(expect, newValues));

        if (key instanceof RuntimeOptionKey<?> r) {
            r.afterValueUpdateFromRuntimeValues();
        }
    }

    /// Applies an update while preserving its source through value publication.
    public void update(UnmodifiableEconomicMap<OptionKey<?>, Object> values, Origin origin) {
        if (values.isEmpty()) {
            return;
        }

        validateValues(values);
        updateCache(values);

        Snapshot expect;
        Snapshot newValues;
        EconomicMap<OptionKey<?>, Object> newMap;
        do {
            expect = v.get();
            newMap = EconomicMap.create(expect.values().getMap());
            var cursor = values.getEntries();
            while (cursor.advance()) {
                OptionKey<?> key = cursor.getKey();
                Object value = cursor.getValue();
                if (key instanceof RuntimeOptionKey<?> runtimeOptionKey) {
                    runtimeOptionKey.updateAfterValidation(newMap, value);
                } else {
                    key.update(newMap, value);
                }
            }
            newValues = snapshot(expect, newMap, values, origin);
        } while (!v.compareAndSet(expect, newValues));

        var cursor = values.getEntries();
        while (cursor.advance()) {
            if (cursor.getKey() instanceof RuntimeOptionKey<?> runtimeOptionKey) {
                runtimeOptionKey.afterValueUpdateFromRuntimeValues();
            }
        }
    }

    public OptionValues get() {
        return v.get().values();
    }

    /// Gets the values and origins from the same publication.
    Snapshot getSnapshot() {
        return v.get();
    }

    /// Retains unchanged origins and attributes explicit and dependent changes to this update.
    private static Snapshot snapshot(Snapshot previous, UnmodifiableEconomicMap<OptionKey<?>, Object> values,
                    UnmodifiableEconomicMap<OptionKey<?>, Object> updates, Origin origin) {
        EconomicMap<OptionKey<?>, Origin> origins = EconomicMap.create();
        var cursor = values.getEntries();
        while (cursor.advance()) {
            OptionKey<?> key = cursor.getKey();
            boolean updated = previous == null || updates.containsKey(key) || !previous.values().containsKey(key) ||
                            !Objects.equals(previous.values().getMap().get(key), cursor.getValue());
            origins.put(key, updated ? origin : previous.origin(key));
        }
        return new Snapshot(new OptionValues(values), origins);
    }

    public UnmodifiableEconomicMap<OptionKey<?>, Object> getMap() {
        return get().getMap();
    }

    public boolean containsKey(OptionKey<?> key) {
        return get().containsKey(key);
    }

    /**
     * In layered native images, {@link RuntimeOptionKey} objects can live in the base layer, while
     * the final option values are only known to the application layer. When a user supplies a value
     * for a runtime option at build-time, the cached value field inside the corresponding
     * {@link RuntimeOptionKey} cannot be reliably initialized at build-time due to this separation.
     * <p>
     * To address this, we copy all non-default runtime option values into the cache during early VM
     * startup, before any option parsing takes place. If an option is provided at run-time using
     * {@code -XX:...}, the cache is later updated again during option parsing (see calls to
     * {@link RuntimeOptionKey#setRawCachedValue}).
     */
    public void copyBuildTimeValuesToCache() {
        assert !SubstrateUtil.HOSTED;

        if (GuestImageLayerBuildingSupport.buildingImageLayer()) {
            updateCache(getMap());
        }
    }

    private static void updateCache(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        var cursor = values.getEntries();
        while (cursor.advance()) {
            if (cursor.getKey() instanceof RuntimeOptionKey<?> runtimeOptionKey) {
                runtimeOptionKey.setRawCachedValue(cursor.getValue());
            }
        }
    }

    private static void validateValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        var cursor = values.getEntries();
        while (cursor.advance()) {
            if (cursor.getKey() instanceof RuntimeOptionKey<?> runtimeOptionKey) {
                runtimeOptionKey.validateValueBeforeUpdate(cursor.getValue());
            }
        }
    }

    /// Identifies how an option received its current value.
    public enum Origin {
        /// Uses the option's declared default.
        Default("default"),

        /// Was supplied while building the image.
        BuildTime("build-time"),

        /// Was supplied through runtime arguments from any entry point, including JNI VM creation.
        /// Uses the label "command line" for compatibility with HotSpot.
        CommandLine("command line"),

        /// Was set programmatically at runtime.
        Runtime("runtime");

        /// Preserves the diagnostic label independently of the enum constant's spelling.
        private final String displayName;

        Origin(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /// Publishes values and their origins together so diagnostic output (e.g., `-XX:+PrintFlagsFinal`)
    /// observes a consistent pair.
    record Snapshot(OptionValues values, UnmodifiableEconomicMap<OptionKey<?>, Origin> origins) {
        /// Gets the origin of the current value, including an unset option's default.
        Origin origin(OptionKey<?> key) {
            return origins.get(key, Origin.Default);
        }
    }
}
