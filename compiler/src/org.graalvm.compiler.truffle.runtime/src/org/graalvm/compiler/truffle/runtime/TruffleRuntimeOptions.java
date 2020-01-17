/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.common.NativeImageReinitialize;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Compilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatistics;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationStatisticDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileImmediately;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompileOnly;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilerThreads;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.FirstTierMinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Inlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningNodeBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningRecursionDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InvalidationReprofileCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.LanguageAgnosticInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MinInvokeThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MultiTier;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.OSR;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.OSRCompilationThreshold;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Profiling;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ReplaceReprofileCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ReturnTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Splitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingAllowForcedSplits;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingDumpDecisions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingGrowthLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxCalleeSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxNumberOfSplitNodes;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingMaxPropagationDepth;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.SplittingTraceEvents;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceAssumptions;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationAST;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationCallTree;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceCompilationPolymorphism;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInlining;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplitting;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceSplittingSummary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceStackTraceLimit;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceTransferToInterpreter;

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

    /**
     * Get Truffle-related compilation options as a Map to be passed to the compiler. Some
     * Truffle-like options are converted into Graal compiler options.
     */
    public static Map<String, Object> getOptionsForCompiler(OptimizedCallTarget callTarget) {
        Map<String, Object> map = new HashMap<>();
        OptionValues values = callTarget == null ? null : callTarget.getOptionValues();

        for (OptionDescriptor desc : PolyglotCompilerOptions.getDescriptors()) {
            final OptionKey<?> key = desc.getKey();
            if (hasBeenSet(values, key)) {
                Object value = getPolyglotOptionValue(values, key);
                if (!isPrimitiveType(value)) {
                    value = CompilerRuntimeAccessor.engineAccessor().getUnparsedOptionValue(values, key);
                }
                map.put(desc.getName(), value);
            }
        }

        return map;
    }

    /**
     * Uses the --engine option if set, otherwise falls back on the -Dgraal option.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPolyglotOptionValue(OptionValues options, OptionKey<T> optionKey) {
        if (options != null && options.hasBeenSet(optionKey)) {
            return options.get(optionKey);
        }
        OptionKey<T> runtimeOptionKey = (OptionKey<T>) POLYGLOT_TO_RUNTIME.get(optionKey);
        if (runtimeOptionKey != null) {
            return getValue(runtimeOptionKey);
        }
        return optionKey.getDefaultValue();
    }

    private static boolean hasBeenSet(OptionValues options, OptionKey<?> optionKey) {
        if (options != null && options.hasBeenSet(optionKey)) {
            return true;
        }
        OptionKey<?> runtimeOptionKey = POLYGLOT_TO_RUNTIME.get(optionKey);
        return runtimeOptionKey == null ? false : getOptions().hasBeenSet(runtimeOptionKey);
    }

    private static boolean isPrimitiveType(Object value) {
        Class<?> valueClass = value.getClass();
        return valueClass == Boolean.class ||
                        valueClass == Byte.class ||
                        valueClass == Short.class ||
                        valueClass == Character.class ||
                        valueClass == Integer.class ||
                        valueClass == Long.class ||
                        valueClass == Float.class ||
                        valueClass == Double.class ||
                        valueClass == String.class;
    }

    public static class TruffleRuntimeOptionsOverrideScope implements AutoCloseable {
        private final TruffleRuntimeOptionsOverrideScope outer;
        private final OptionValuesImpl options;

        TruffleRuntimeOptionsOverrideScope(UnmodifiableEconomicMap<OptionKey<?>, Object> overrides) {
            outer = Lazy.overrideScope.get();
            options = new OptionValuesImpl(outer == null ? getInitialOptions() : outer.options, overrides);
            GraalTVMCI.resetEngineData();
            Lazy.overrideScope.set(this);
        }

        @Override
        public void close() {
            Lazy.overrideScope.set(outer);
            GraalTVMCI.resetEngineData();
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
     *
     * @param target
     */
    public static boolean areTruffleCompilationExceptionsFatal(OptimizedCallTarget target) {
        /*
         * This is duplicated in TruffleCompilerOptions#areTruffleCompilationExceptionsFatal.
         */
        boolean compilationExceptionsAreFatal = target.getOptionValue(PolyglotCompilerOptions.CompilationExceptionsAreFatal);
        boolean performanceWarningsAreFatal = target.getOptionValue(PolyglotCompilerOptions.PerformanceWarningsAreFatal);
        return compilationExceptionsAreFatal || performanceWarningsAreFatal;
    }

    // Support for mapping PolyglotCompilerOptions to legacy TruffleCompilerOptions.
    private static final EconomicMap<OptionKey<?>, OptionKey<?>> POLYGLOT_TO_RUNTIME = initializePolyglotToGraalMapping();

    private static EconomicMap<OptionKey<?>, OptionKey<?>> initializePolyglotToGraalMapping() {
        EconomicMap<OptionKey<?>, OptionKey<?>> result = EconomicMap.create(Equivalence.IDENTITY);
        result.put(Compilation, SharedTruffleRuntimeOptions.TruffleCompilation);
        result.put(CompileOnly, SharedTruffleRuntimeOptions.TruffleCompileOnly);
        result.put(CompileImmediately, SharedTruffleRuntimeOptions.TruffleCompileImmediately);
        result.put(BackgroundCompilation, SharedTruffleRuntimeOptions.TruffleBackgroundCompilation);
        result.put(CompilerThreads, SharedTruffleRuntimeOptions.TruffleCompilerThreads);
        result.put(CompilationThreshold, SharedTruffleRuntimeOptions.TruffleCompilationThreshold);
        result.put(MinInvokeThreshold, SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold);
        result.put(InvalidationReprofileCount, SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount);
        result.put(ReplaceReprofileCount, SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount);
        result.put(ArgumentTypeSpeculation, SharedTruffleRuntimeOptions.TruffleArgumentTypeSpeculation);
        result.put(ReturnTypeSpeculation, SharedTruffleRuntimeOptions.TruffleReturnTypeSpeculation);
        result.put(Profiling, SharedTruffleRuntimeOptions.TruffleProfilingEnabled);

        result.put(MultiTier, SharedTruffleRuntimeOptions.TruffleMultiTier);
        result.put(FirstTierCompilationThreshold, SharedTruffleRuntimeOptions.TruffleFirstTierCompilationThreshold);
        result.put(FirstTierMinInvokeThreshold, SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold);

        result.put(CompilationExceptionsArePrinted, SharedTruffleRuntimeOptions.TruffleCompilationExceptionsArePrinted);
        result.put(CompilationExceptionsAreThrown, SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown);
        result.put(CompilationExceptionsAreFatal, SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal);
        result.put(PerformanceWarningsAreFatal, SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal);

        result.put(TraceCompilation, SharedTruffleRuntimeOptions.TraceTruffleCompilation);
        result.put(TraceCompilationDetails, SharedTruffleRuntimeOptions.TraceTruffleCompilationDetails);
        result.put(TraceCompilationPolymorphism, SharedTruffleRuntimeOptions.TraceTruffleCompilationPolymorphism);
        result.put(TraceCompilationAST, SharedTruffleRuntimeOptions.TraceTruffleCompilationAST);
        result.put(TraceCompilationCallTree, SharedTruffleRuntimeOptions.TraceTruffleCompilationCallTree);
        result.put(TraceAssumptions, SharedTruffleRuntimeOptions.TraceTruffleAssumptions);
        result.put(TraceStackTraceLimit, SharedTruffleRuntimeOptions.TraceTruffleStackTraceLimit);
        result.put(CompilationStatistics, SharedTruffleRuntimeOptions.TruffleCompilationStatistics);
        result.put(CompilationStatisticDetails, SharedTruffleRuntimeOptions.TruffleCompilationStatisticDetails);
        result.put(TraceTransferToInterpreter, SharedTruffleRuntimeOptions.TraceTruffleTransferToInterpreter);

        result.put(TraceInlining, SharedTruffleRuntimeOptions.TraceTruffleInlining);
        result.put(TraceSplitting, SharedTruffleRuntimeOptions.TraceTruffleSplitting);

        result.put(Inlining, SharedTruffleRuntimeOptions.TruffleFunctionInlining);
        result.put(InliningNodeBudget, SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize);
        result.put(InliningRecursionDepth, SharedTruffleRuntimeOptions.TruffleMaximumRecursiveInlining);
        result.put(LanguageAgnosticInlining, SharedTruffleRuntimeOptions.TruffleLanguageAgnosticInlining);

        result.put(Splitting, SharedTruffleRuntimeOptions.TruffleSplitting);
        result.put(SplittingMaxCalleeSize, SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize);
        result.put(SplittingGrowthLimit, SharedTruffleRuntimeOptions.TruffleSplittingGrowthLimit);
        result.put(SplittingMaxNumberOfSplitNodes, SharedTruffleRuntimeOptions.TruffleSplittingMaxNumberOfSplitNodes);
        result.put(SplittingMaxPropagationDepth, SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth);
        result.put(TraceSplittingSummary, SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary);
        result.put(SplittingTraceEvents, SharedTruffleRuntimeOptions.TruffleSplittingTraceEvents);
        result.put(SplittingDumpDecisions, SharedTruffleRuntimeOptions.TruffleSplittingDumpDecisions);
        result.put(SplittingAllowForcedSplits, SharedTruffleRuntimeOptions.TruffleSplittingAllowForcedSplits);

        result.put(OSR, SharedTruffleRuntimeOptions.TruffleOSR);
        result.put(OSRCompilationThreshold, SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold);
        return result;
    }
}
