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
package org.graalvm.compiler.truffle.compiler;

import java.util.function.Supplier;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.options.OptionsResolver;
import org.graalvm.options.OptionKey;

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InlineAcrossTruffleBoundary;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TracePerformanceWarnings;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PrintExpansionHistogram;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.IterativePartialEscape;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentFilter;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InstrumentationTableSize;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumGraalNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.MaximumInlineNodeCount;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.TraceInliningDetails;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningPolicy;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningExpansionBudget;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.InliningInliningBudget;

public final class TruffleCompilerOptionsResolver implements OptionsResolver {

    private static final EconomicMap<OptionKey<?>, CompilerOptionValueSupplier<?>> POLYGLOT_TO_COMPILER = initializePolyglotToGraalMapping();

    private static EconomicMap<OptionKey<?>, CompilerOptionValueSupplier<?>> initializePolyglotToGraalMapping() {
        EconomicMap<OptionKey<?>, CompilerOptionValueSupplier<?>> result = EconomicMap.create(Equivalence.IDENTITY);
        result.put(InlineAcrossTruffleBoundary, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInlineAcrossTruffleBoundary));
        result.put(TracePerformanceWarnings, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TraceTrufflePerformanceWarnings));
        result.put(PrintExpansionHistogram, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.PrintTruffleExpansionHistogram));
        result.put(IterativePartialEscape, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleIterativePartialEscape));
        result.put(InstrumentFilter, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInstrumentFilter));
        result.put(InstrumentationTableSize, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInstrumentationTableSize));
        result.put(MaximumGraalNodeCount, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleMaximumGraalNodeCount));
        result.put(MaximumInlineNodeCount, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleMaximumInlineNodeCount));
        result.put(TraceInliningDetails, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TraceTruffleInliningDetails));
        result.put(InliningPolicy, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInliningPolicy));
        result.put(InliningExpansionBudget, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInliningExpansionBudget));
        result.put(InliningInliningBudget, new CompilerOptionValueSupplier<>(TruffleCompilerOptions.TruffleInliningInliningBudget));
        return result;
    }

    public TruffleCompilerOptionsResolver() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> resolve(OptionKey<T> key) {
        return (Supplier<T>) POLYGLOT_TO_COMPILER.get(key);
    }

    @Override
    public boolean hasBeenSet(OptionKey<?> key) {
        // Not used
        return false;
    }

    private static final class CompilerOptionValueSupplier<T> implements Supplier<T> {

        private final org.graalvm.compiler.options.OptionKey<T> optionKey;

        CompilerOptionValueSupplier(org.graalvm.compiler.options.OptionKey<T> optionKey) {
            this.optionKey = optionKey;
        }

        @Override
        public T get() {
            return TruffleCompilerOptions.getValue(optionKey);
        }
    }
}
