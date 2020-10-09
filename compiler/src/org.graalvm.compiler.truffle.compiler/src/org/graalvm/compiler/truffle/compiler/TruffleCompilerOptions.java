/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationExceptionsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.PerformanceWarningsAreFatal;
import static org.graalvm.compiler.truffle.options.PolyglotCompilerOptions.CompilationFailureAction;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import jdk.vm.ci.common.NativeImageReinitialize;

public final class TruffleCompilerOptions {

    private TruffleCompilerOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    @NativeImageReinitialize private static volatile OptionValues graalOptionValues;

    /**
     * Uses the --engine option if set, otherwise falls back on the -Dgraal option.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getPolyglotOptionValue(org.graalvm.options.OptionValues options, org.graalvm.options.OptionKey<T> optionKey) {
        if (options != null) {
            return options.get(optionKey);
        }
        return optionKey.getDefaultValue();
    }

    /**
     * Determines whether an exception during a Truffle compilation should result in calling
     * {@link System#exit(int)}.
     */
    public static boolean areTruffleCompilationExceptionsFatal(org.graalvm.options.OptionValues options) {
        /*
         * This is duplicated in TruffleRuntimeOptions#areTruffleCompilationExceptionsFatal.
         */
        boolean compilationExceptionsAreFatal = getPolyglotOptionValue(options, CompilationExceptionsAreFatal);
        boolean performanceWarningsAreFatal = !getPolyglotOptionValue(options, PerformanceWarningsAreFatal).isEmpty();
        boolean exitVM = getPolyglotOptionValue(options, CompilationFailureAction) == PolyglotCompilerOptions.ExceptionAction.ExitVM;
        return compilationExceptionsAreFatal || performanceWarningsAreFatal || exitVM;
    }

    static OptionValues enableNodeSourcePositions(OptionValues values) {
        if (GraalOptions.TrackNodeSourcePosition.getValue(values)) {
            // already enabled nothing to do
            return values;
        } else {
            return new OptionValues(values, GraalOptions.TrackNodeSourcePosition, Boolean.TRUE);
        }
    }

    /**
     * Gets the object holding the values of Graal options.
     */
    public static OptionValues getGraalOptions() {
        OptionValues result = graalOptionValues;
        if (result == null) {
            result = TruffleCompilerRuntime.getRuntime().getGraalOptions(OptionValues.class);
            graalOptionValues = result;
        }
        return result;
    }

    /**
     * Converts the values of {@link PolyglotCompilerOptions} passed to the
     * {@link TruffleCompiler#doCompile} as a {@link Map} into
     * {@link org.graalvm.options.OptionValues}.
     */
    public static org.graalvm.options.OptionValues getOptionsForCompiler(Map<String, Object> options) {
        EconomicMap<org.graalvm.options.OptionKey<?>, Object> parsedOptions = EconomicMap.create(Equivalence.IDENTITY);
        OptionDescriptors descriptors = PolyglotCompilerOptions.getDescriptors();
        for (Map.Entry<String, Object> e : options.entrySet()) {
            final OptionDescriptor descriptor = descriptors.get(e.getKey());
            final org.graalvm.options.OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
            if (k != null) {
                Object value = e.getValue();
                if (value.getClass() == String.class) {
                    value = descriptor.getKey().getType().convert((String) e.getValue());
                }
                parsedOptions.put(k, value);
            }
        }
        return new OptionValuesImpl(descriptors, parsedOptions);
    }
}
