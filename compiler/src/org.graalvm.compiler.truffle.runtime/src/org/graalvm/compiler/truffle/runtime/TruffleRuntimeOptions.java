/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.SharedTruffleOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import jdk.vm.ci.common.NativeImageReinitialize;
import org.graalvm.collections.Pair;

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

    @NativeImageReinitialize private static volatile OptionValuesImpl optionValues;

    private TruffleRuntimeOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    /**
     * Gets the object holding the values of Truffle options.
     */
    private static OptionValues getOptions() {
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
                    value = GraalRuntimeAccessor.ENGINE.getUnparsedOptionValue(values, key);
                }
                if (value != null) {
                    map.put(desc.getName(), value);
                }
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
        Pair<? extends OptionKey<?>, Function<Object, ?>> runtimeOptionKeyPair = POLYGLOT_TO_RUNTIME.get(optionKey);
        if (runtimeOptionKeyPair != null) {
            return (T) runtimeOptionKeyPair.getRight().apply(runtimeOptionKeyPair.getLeft().getValue(getOptions()));
        }
        return optionKey.getDefaultValue();
    }

    private static boolean hasBeenSet(OptionValues options, OptionKey<?> optionKey) {
        if (options != null && options.hasBeenSet(optionKey)) {
            return true;
        }
        Pair<? extends OptionKey<?>, Function<Object, ?>> runtimeOptionKeyPair = POLYGLOT_TO_RUNTIME.get(optionKey);
        return runtimeOptionKeyPair == null ? false : getOptions().hasBeenSet(runtimeOptionKeyPair.getLeft());
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

    // Support for mapping PolyglotCompilerOptions to legacy TruffleCompilerOptions.
    private static final EconomicMap<OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> POLYGLOT_TO_RUNTIME = initializePolyglotToGraalMapping();

    private static EconomicMap<OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> initializePolyglotToGraalMapping() {
        EconomicMap<OptionKey<?>, Pair<? extends OptionKey<?>, Function<Object, ?>>> result = EconomicMap.create(Equivalence.IDENTITY);
        result.put(Compilation, identity(SharedTruffleRuntimeOptions.TruffleCompilation));
        result.put(CompileOnly, identity(SharedTruffleRuntimeOptions.TruffleCompileOnly));
        result.put(CompileImmediately, identity(SharedTruffleRuntimeOptions.TruffleCompileImmediately));
        result.put(BackgroundCompilation, identity(SharedTruffleRuntimeOptions.TruffleBackgroundCompilation));
        result.put(CompilerThreads, identity(SharedTruffleRuntimeOptions.TruffleCompilerThreads));
        result.put(CompilationThreshold, identity(SharedTruffleRuntimeOptions.TruffleCompilationThreshold));
        result.put(MinInvokeThreshold, identity(SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold));
        result.put(InvalidationReprofileCount, identity(SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount));
        result.put(ReplaceReprofileCount, identity(SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount));
        result.put(ArgumentTypeSpeculation, identity(SharedTruffleRuntimeOptions.TruffleArgumentTypeSpeculation));
        result.put(ReturnTypeSpeculation, identity(SharedTruffleRuntimeOptions.TruffleReturnTypeSpeculation));
        result.put(Profiling, identity(SharedTruffleRuntimeOptions.TruffleProfilingEnabled));

        result.put(MultiTier, identity(SharedTruffleRuntimeOptions.TruffleMultiTier));
        result.put(FirstTierCompilationThreshold, identity(SharedTruffleRuntimeOptions.TruffleFirstTierCompilationThreshold));
        result.put(FirstTierMinInvokeThreshold, identity(SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold));

        result.put(CompilationExceptionsArePrinted, identity(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsArePrinted));
        result.put(CompilationExceptionsAreThrown, identity(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown));
        result.put(CompilationExceptionsAreFatal, identity(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal));
        result.put(PerformanceWarningsAreFatal, booleanToPerformanceWarningKind(SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal));
        result.put(TraceCompilation, identity(SharedTruffleRuntimeOptions.TraceTruffleCompilation));
        result.put(TraceCompilationDetails, identity(SharedTruffleRuntimeOptions.TraceTruffleCompilationDetails));
        result.put(TraceCompilationPolymorphism, identity(SharedTruffleRuntimeOptions.TraceTruffleCompilationPolymorphism));
        result.put(TraceCompilationAST, identity(SharedTruffleRuntimeOptions.TraceTruffleCompilationAST));
        result.put(TraceCompilationCallTree, identity(SharedTruffleRuntimeOptions.TraceTruffleCompilationCallTree));
        result.put(TraceAssumptions, identity(SharedTruffleRuntimeOptions.TraceTruffleAssumptions));
        result.put(TraceStackTraceLimit, identity(SharedTruffleRuntimeOptions.TraceTruffleStackTraceLimit));
        result.put(CompilationStatistics, identity(SharedTruffleRuntimeOptions.TruffleCompilationStatistics));
        result.put(CompilationStatisticDetails, identity(SharedTruffleRuntimeOptions.TruffleCompilationStatisticDetails));
        result.put(TraceTransferToInterpreter, identity(SharedTruffleRuntimeOptions.TraceTruffleTransferToInterpreter));

        result.put(TraceInlining, identity(SharedTruffleRuntimeOptions.TraceTruffleInlining));
        result.put(TraceSplitting, identity(SharedTruffleRuntimeOptions.TraceTruffleSplitting));

        result.put(Inlining, identity(SharedTruffleRuntimeOptions.TruffleFunctionInlining));
        result.put(InliningNodeBudget, identity(SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize));
        result.put(InliningRecursionDepth, identity(SharedTruffleRuntimeOptions.TruffleMaximumRecursiveInlining));
        result.put(LanguageAgnosticInlining, identity(SharedTruffleRuntimeOptions.TruffleLanguageAgnosticInlining));

        result.put(Splitting, identity(SharedTruffleRuntimeOptions.TruffleSplitting));
        result.put(SplittingMaxCalleeSize, identity(SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize));
        result.put(SplittingGrowthLimit, identity(SharedTruffleRuntimeOptions.TruffleSplittingGrowthLimit));
        result.put(SplittingMaxNumberOfSplitNodes, identity(SharedTruffleRuntimeOptions.TruffleSplittingMaxNumberOfSplitNodes));
        result.put(SplittingMaxPropagationDepth, identity(SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth));
        result.put(TraceSplittingSummary, identity(SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary));
        result.put(SplittingTraceEvents, identity(SharedTruffleRuntimeOptions.TruffleSplittingTraceEvents));
        result.put(SplittingDumpDecisions, identity(SharedTruffleRuntimeOptions.TruffleSplittingDumpDecisions));
        result.put(SplittingAllowForcedSplits, identity(SharedTruffleRuntimeOptions.TruffleSplittingAllowForcedSplits));

        result.put(OSR, identity(SharedTruffleRuntimeOptions.TruffleOSR));
        result.put(OSRCompilationThreshold, identity(SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold));
        return result;
    }

    private static Pair<OptionKey<?>, Function<Object, ?>> identity(OptionKey<?> key) {
        return Pair.create(key, Function.identity());
    }

    private static Pair<OptionKey<Boolean>, Function<Object, ?>> booleanToPerformanceWarningKind(OptionKey<Boolean> key) {
        return Pair.create(key, new Function<Object, Set<PolyglotCompilerOptions.PerformanceWarningKind>>() {
            @Override
            public Set<PolyglotCompilerOptions.PerformanceWarningKind> apply(Object t) {
                return ((Boolean) t) ? EnumSet.allOf(PolyglotCompilerOptions.PerformanceWarningKind.class) : EnumSet.noneOf(PolyglotCompilerOptions.PerformanceWarningKind.class);
            }
        });
    }
}
