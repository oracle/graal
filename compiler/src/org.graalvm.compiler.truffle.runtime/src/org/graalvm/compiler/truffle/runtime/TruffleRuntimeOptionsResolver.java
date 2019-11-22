/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.options.OptionsResolver;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.ArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.BackgroundCompilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.Compilation;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsArePrinted;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreThrown;
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
import org.graalvm.options.OptionKey;

public final class TruffleRuntimeOptionsResolver implements OptionsResolver {

    private static final EconomicMap<OptionKey<?>, RuntimeOptionValueSupplier<?>> POLYGLOT_TO_RUNTIME = initializePolyglotToGraalMapping();

    private static EconomicMap<OptionKey<?>, RuntimeOptionValueSupplier<?>> initializePolyglotToGraalMapping() {
        EconomicMap<OptionKey<?>, RuntimeOptionValueSupplier<?>> result = EconomicMap.create(Equivalence.IDENTITY);
        result.put(Compilation, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilation));
        result.put(CompileOnly, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompileOnly));
        result.put(CompileImmediately, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompileImmediately));
        result.put(BackgroundCompilation, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleBackgroundCompilation));
        result.put(CompilerThreads, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilerThreads));
        result.put(CompilationThreshold, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilationThreshold));
        result.put(MinInvokeThreshold, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold));
        result.put(InvalidationReprofileCount, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount));
        result.put(ReplaceReprofileCount, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount));
        result.put(ArgumentTypeSpeculation, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleArgumentTypeSpeculation));
        result.put(ReturnTypeSpeculation, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleReturnTypeSpeculation));

        result.put(MultiTier, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleMultiTier));
        result.put(FirstTierCompilationThreshold, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleFirstTierCompilationThreshold));
        result.put(FirstTierMinInvokeThreshold, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold));

        result.put(CompilationExceptionsArePrinted, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsArePrinted));
        result.put(CompilationExceptionsAreThrown, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreThrown));
        result.put(CompilationExceptionsAreFatal, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleCompilationExceptionsAreFatal));
        result.put(PerformanceWarningsAreFatal, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TrufflePerformanceWarningsAreFatal));

        result.put(TraceCompilation, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleCompilation));
        result.put(TraceCompilationDetails, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleCompilationDetails));
        result.put(TraceCompilationPolymorphism, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleCompilationPolymorphism));
        result.put(TraceCompilationAST, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleCompilationAST));
        result.put(TraceCompilationCallTree, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleCompilationCallTree));
        result.put(TraceAssumptions, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleAssumptions));
        result.put(TraceStackTraceLimit, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleStackTraceLimit));

        result.put(TraceInlining, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleInlining));
        result.put(TraceSplitting, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TraceTruffleSplitting));

        result.put(Inlining, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleFunctionInlining));
        result.put(InliningNodeBudget, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleInliningMaxCallerSize));
        result.put(InliningRecursionDepth, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleMaximumRecursiveInlining));
        result.put(LanguageAgnosticInlining, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleLanguageAgnosticInlining));

        result.put(Splitting, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplitting));
        result.put(SplittingMaxCalleeSize, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxCalleeSize));
        result.put(SplittingGrowthLimit, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingGrowthLimit));
        result.put(SplittingMaxNumberOfSplitNodes, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxNumberOfSplitNodes));
        result.put(SplittingMaxPropagationDepth, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingMaxPropagationDepth));
        result.put(TraceSplittingSummary, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleTraceSplittingSummary));
        result.put(SplittingTraceEvents, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingTraceEvents));
        result.put(SplittingDumpDecisions, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingDumpDecisions));
        result.put(SplittingAllowForcedSplits, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleSplittingAllowForcedSplits));

        result.put(OSR, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleOSR));
        result.put(OSRCompilationThreshold, new RuntimeOptionValueSupplier<>(SharedTruffleRuntimeOptions.TruffleOSRCompilationThreshold));
        return result;
    }

    public TruffleRuntimeOptionsResolver() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> resolve(OptionKey<T> key) {
        return (Supplier<T>) POLYGLOT_TO_RUNTIME.get(key);
    }

    @Override
    public boolean hasBeenSet(OptionKey<?> key) {
        RuntimeOptionValueSupplier<?> supplier = POLYGLOT_TO_RUNTIME.get(key);
        return supplier == null ? false : TruffleRuntimeOptions.getOptions().hasBeenSet(supplier.optionKey);
    }

    private static final class RuntimeOptionValueSupplier<T> implements Supplier<T> {
        private final OptionKey<T> optionKey;

        RuntimeOptionValueSupplier(OptionKey<T> optionKey) {
            this.optionKey = optionKey;
        }

        @Override
        public T get() {
            return TruffleRuntimeOptions.getValue(optionKey);
        }
    }

}
