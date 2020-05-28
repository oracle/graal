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

import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.PrintGraphTarget.File;

import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.options.OptionValuesImpl;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import java.io.OutputStream;

import jdk.vm.ci.common.NativeImageReinitialize;

final class TruffleDebugOptions {

    @NativeImageReinitialize private static volatile OptionValuesImpl optionValues;

    private TruffleDebugOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    static <T> T getValue(final OptionKey<T> key) {
        OptionValues values = getOptions();
        if (values.hasBeenSet(key)) {
            return values.get(key);
        }
        return key.getDefaultValue();
    }

    static OutputStream getConfiguredLogStream() {
        OptionValues values = getOptions();
        if (values.hasBeenSet(LogFile)) {
            return GraalTruffleRuntime.getRuntime().getDefaultLogStream();
        } else {
            return null;
        }
    }

    /**
     * Shadows {@code org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget}.
     */
    enum PrintGraphTarget {
        File,
        Network,
        Disable;

        static PrintGraphTarget translate(Object value) {
            return valueOf(String.valueOf(value));
        }

        static OptionType<PrintGraphTarget> getOptionType() {
            return new OptionType<>(PrintGraphTarget.class.getSimpleName(), PrintGraphTarget::valueOf);
        }
    }

    static OptionValues getOptions() {
        OptionValuesImpl result = optionValues;
        if (result == null) {
            final EconomicMap<OptionKey<?>, Object> valuesMap = EconomicMap.create();
            final OptionDescriptors descriptors = OptionDescriptors.createUnion(new TruffleDebugOptionsOptionDescriptors(),
                            new SharedTruffleRuntimeOptionsOptionDescriptors());
            for (Map.Entry<String, Object> e : TruffleCompilerRuntime.getRuntime().getOptions().entrySet()) {
                final OptionDescriptor descriptor = descriptors.get(e.getKey());
                final OptionKey<?> k = descriptor != null ? descriptor.getKey() : null;
                if (k != null) {
                    if (e.getKey().equals("PrintGraph")) {
                        valuesMap.put(k, PrintGraphTarget.translate(e.getValue()));
                    } else {
                        valuesMap.put(k, e.getValue());
                    }
                }
            }
            result = new OptionValuesImpl(descriptors, valuesMap);
            optionValues = result;
        }
        return result;
    }

    // Initialized by the options of the same name in org.graalvm.compiler.debug.DebugOptions
    @Option(help = "", category = OptionCategory.INTERNAL) //
    static final OptionKey<PrintGraphTarget> PrintGraph = new OptionKey<>(File, PrintGraphTarget.getOptionType());
    // Initialized by the options of the same name in compiler
    @Option(help = "", category = OptionCategory.EXPERT) //
    static final OptionKey<String> LogFile = new OptionKey<>(null, OptionType.defaultType(String.class));
}
