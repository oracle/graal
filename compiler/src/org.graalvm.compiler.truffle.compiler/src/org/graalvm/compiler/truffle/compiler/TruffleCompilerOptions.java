/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler;

import static org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions.TruffleCompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions.TrufflePerformanceWarningsAreFatal;

import java.util.Map;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;

import jdk.vm.ci.common.NativeImageReinitialize;

/**
 * Options for the Truffle compiler. Options shared with the Truffle runtime are declared in
 * {@link SharedTruffleCompilerOptions}.
 */
@SharedTruffleOptions(name = "SharedTruffleCompilerOptions", runtime = false)
public final class TruffleCompilerOptions {

    // @formatter:off
    // configuration

    @Option(help = "Exclude assertion code from Truffle compilations", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleExcludeAssertions = new OptionKey<>(true);

    @Option(help = "Enable inlining across Truffle boundary", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleInlineAcrossTruffleBoundary = new OptionKey<>(false);

    @Option(help = "Print potential performance problems", type = OptionType.Debug)
    public static final OptionKey<Boolean> TraceTrufflePerformanceWarnings = new OptionKey<>(false);

    @Option(help = "Prints a histogram of all expanded Java methods.", type = OptionType.Debug)
    public static final OptionKey<Boolean> PrintTruffleExpansionHistogram = new OptionKey<>(false);

    @Option(help = "Enable support for simple infopoints in truffle partial evaluations.", type = OptionType.Expert)
    public static final OptionKey<Boolean> TruffleEnableInfopoints = new OptionKey<>(false);

    @Option(help = "Run the partial escape analysis iteratively in Truffle compilation.", type = OptionType.Debug)
    public static final OptionKey<Boolean> TruffleIterativePartialEscape = new OptionKey<>(false);

    @Option(help = "Instrument branches and output profiling information to the standard output.")
    public static final OptionKey<Boolean> TruffleInstrumentBranches = new OptionKey<>(false);

    @Option(help = "Instrument branches by considering different inlining sites as different branches.")
    public static final OptionKey<Boolean> TruffleInstrumentBranchesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries and output profiling information to the standard output.")
    public static final OptionKey<Boolean> TruffleInstrumentBoundaries = new OptionKey<>(false);

    @Option(help = "Instrument Truffle boundaries by considering different inlining sites as different branches.")
    public static final OptionKey<Boolean> TruffleInstrumentBoundariesPerInlineSite = new OptionKey<>(false);

    @Option(help = "Method filter for host methods in which to add instrumentation.")
    public static final OptionKey<String> TruffleInstrumentFilter = new OptionKey<>("*.*.*");

    @Option(help = "Maximum number of instrumentation counters available.")
    public static final OptionKey<Integer> TruffleInstrumentationTableSize = new OptionKey<>(10000);
    // @formatter:on

    private TruffleCompilerOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    static class Lazy {
        static final ThreadLocal<TruffleOptionsOverrideScope> overrideScope = new ThreadLocal<>();
    }

    @NativeImageReinitialize private static volatile OptionValues optionValues;

    private static OptionValues getInitialOptions() {
        OptionValues result = optionValues;
        if (result == null) {
            result = TruffleCompilerRuntime.getRuntime().getOptions(OptionValues.class);
            optionValues = result;
        }
        return result;
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
        boolean truffleCompilationExceptionsAreFatal = TruffleCompilerOptions.getValue(TruffleCompilationExceptionsAreFatal);
        assert TruffleCompilationExceptionsAreFatal.hasBeenSet(TruffleCompilerOptions.getOptions()) || (truffleCompilationExceptionsAreFatal = true) == true;
        return truffleCompilationExceptionsAreFatal || TruffleCompilerOptions.getValue(TrufflePerformanceWarningsAreFatal);
    }

    /**
     * Gets the object holding the values of Truffle options, taking into account any active
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) overrides}.
     */
    public static OptionValues getOptions() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : getInitialOptions();
    }

    /**
     * Gets the options defined in the current option
     * {@linkplain #overrideOptions(OptionKey, Object, Object...) override} scope or {@code null} if
     * there is no override scope active for the current thread.
     */
    public static OptionValues getCurrentOptionOverrides() {
        TruffleOptionsOverrideScope scope = Lazy.overrideScope.get();
        return scope != null ? scope.options : null;
    }

    public static final class TruffleOptionsOverrideScope implements AutoCloseable {
        private final TruffleOptionsOverrideScope outer;
        private final OptionValues options;

        private TruffleOptionsOverrideScope(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
            outer = Lazy.overrideScope.get();
            options = new OptionValues(outer == null ? getInitialOptions() : outer.options, overrides);
            Lazy.overrideScope.set(this);
        }

        @Override
        public void close() {
            Lazy.overrideScope.set(outer);
        }
    }

    /**
     * Forces specified values in the object returned by {@link #getOptions()} until
     * {@link TruffleOptionsOverrideScope#close()} is called on the object returned by this method.
     * The values forced while the override is active are taken from the key/value pairs in
     * {@code overrides}. The override is thread local.
     * <p>
     * The returned object should be used with the try-with-resource construct:
     *
     * <pre>
     * try (TruffleOptionsOverrideScope s = overrideOptions(option1, value1, option2, value2)) {
     *     ...
     * }
     * </pre>
     *
     * NOTE: This feature is only intended for testing. The caller must be aware whether or not the
     * options being overridden are accessed inside the new override scope.
     *
     * @param extraOverrides overrides in the form {@code [key1, value2, key3, value3, ...]}
     */
    public static TruffleOptionsOverrideScope overrideOptions(OptionKey<?> key1, Object value1, Object... extraOverrides) {
        return new TruffleOptionsOverrideScope(OptionValues.asMap(key1, value1, extraOverrides));
    }

    public static TruffleOptionsOverrideScope overrideOptions(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
        return new TruffleOptionsOverrideScope(overrides);
    }

    public static TruffleOptionsOverrideScope overrideOptions(Map<String, Object> overrides) {
        TruffleCompilerRuntime runtime = TruffleCompilerRuntime.getRuntime();
        UnmodifiableEconomicMap<OptionKey<?>, Object> values = runtime.convertOptions(OptionValues.class, overrides).getMap();
        return new TruffleOptionsOverrideScope(values);
    }

    /**
     * Gets the value of a given Truffle option key taking into account any active
     * {@linkplain #overrideOptions overrides}.
     */
    public static <T> T getValue(OptionKey<T> key) {
        return key.getValue(getOptions());
    }
}
