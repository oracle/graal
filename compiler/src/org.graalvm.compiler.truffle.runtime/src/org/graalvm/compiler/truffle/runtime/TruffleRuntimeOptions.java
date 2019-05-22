/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.common.NativeImageReinitialize;

/**
 * Helpers to read and overwrite values of Truffle runtime options. The options themselves are
 * declared in {@link SharedTruffleRuntimeOptions}.
 */
@SharedTruffleOptions(name = "SharedTruffleRuntimeOptions", runtime = true)
public final class TruffleRuntimeOptions {

    private TruffleRuntimeOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    static class Lazy {
        static final ThreadLocal<TruffleRuntimeOptionsOverrideScope> overrideScope = new ThreadLocal<>();
    }

    @NativeImageReinitialize private static volatile OptionValuesImpl optionValues;

    private static OptionValuesImpl getInitialOptions() {
        OptionValuesImpl result = optionValues;
        if (result == null) {
            final EconomicMap<OptionKey<?>, Object> valuesMap = EconomicMap.create();
            final OptionDescriptors descriptors = new SharedTruffleRuntimeOptionsOptionDescriptors();
            for (Map.Entry<String, Object> e : TruffleCompilerRuntime.getRuntime().getOptions().entrySet()) {
                final OptionDescriptor descriptor = descriptors.get(e.getKey());
                final OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
                if (k != null) {
                    valuesMap.put(k, e.getValue());
                }
            }
            result = new OptionValuesImpl(descriptors, valuesMap);
            optionValues = result;
        }
        return result;
    }

    /**
     * Gets the object holding the values of Truffle options, taking into account any active
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) overrides}.
     */
    public static OptionValues getOptions() {
        TruffleRuntimeOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : getInitialOptions();
    }

    /**
     * Gets the options defined in the current option
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) override} scope or {@code null} if
     * there is no override scope active for the current thread.
     */
    public static OptionValues getCurrentOptionOverrides() {
        TruffleRuntimeOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : null;
    }

    public static Map<String, Object> asMap(OptionValues values) {
        if (values == null) {
            return Collections.emptyMap();
        }
        final Map<String, Object> m = new HashMap<>();
        for (OptionDescriptor desc : values.getDescriptors()) {
            final OptionKey<?> key = desc.getKey();
            if (values.hasBeenSet(key)) {
                m.put(desc.getName(), values.get(key));
            }
        }
        return m;
    }

    public static class TruffleRuntimeOptionsOverrideScope implements AutoCloseable {
        private final TruffleRuntimeOptionsOverrideScope outer;
        private final OptionValuesImpl options;

        TruffleRuntimeOptionsOverrideScope(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
            outer = Lazy.overrideScope.get();
            options = new OptionValuesImpl(outer == null ? getInitialOptions() : outer.options, overrides);
            Lazy.overrideScope.set(this);
        }

        @Override
        public void close() {
            Lazy.overrideScope.set(outer);
        }
    }

    /**
     * Gets the value of a given Truffle option key taking into account any active
     * {@linkplain #overrideOptions overrides}.
     */
    public static <T> T getValue(OptionKey<T> key) {
        return key.getValue(getOptions());
    }

    public static TruffleRuntimeOptionsOverrideScope overrideOptions(final OptionValues values) {
        return new TruffleRuntimeOptionsOverrideScope(asEconomicMap(values));
    }

    public static TruffleRuntimeOptionsOverrideScope overrideOptions(OptionKey<?> key1, Object value1, Object... extraOverrides) {
        final EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(key1, value1);
        if ((extraOverrides.length & 1) == 1) {
            throw new IllegalArgumentException("extraOverrides.length must be even: " + extraOverrides.length);
        }
        for (int i = 0; i < extraOverrides.length; i += 2) {
            map.put((OptionKey<?>) extraOverrides[i], extraOverrides[i + 1]);
        }
        return new TruffleRuntimeOptionsOverrideScope(map);
    }

    private static EconomicMap<OptionKey<?>, Object> asEconomicMap(final OptionValues values) {
        final EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        for (OptionDescriptor desc : values.getDescriptors()) {
            final OptionKey<?> key = desc.getKey();
            if (values.hasBeenSet(key)) {
                map.put(key, values.get(key));
            }
        }
        return map;
    }

    /**
     * Determines whether an exception during a Truffle compilation should result in calling
     * {@link System#exit(int)}.
     */
    public static boolean areTruffleCompilationExceptionsFatal() {
        /*
         * Automatically enable TruffleCompilationExceptionsAreFatal when asserts are enabled but
         * respect TruffleCompilationExceptionsAreFatal if it's been explicitly set.
         */
        boolean truffleCompilationExceptionsAreFatal = getValue(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal);
        assert SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal.hasBeenSet(TruffleRuntimeOptions.getOptions()) || (truffleCompilationExceptionsAreFatal = true) == true;
        return truffleCompilationExceptionsAreFatal || TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal);
    }
}
