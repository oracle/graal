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

import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;

import jdk.vm.ci.common.NativeImageReinitialize;

final class IgvOptions {

    @NativeImageReinitialize private static volatile OptionValuesImpl optionValues;

    private IgvOptions() {
        throw new IllegalStateException("No instance allowed.");
    }

    static <T> T getValue(final OptionKey<T> key) {
        OptionValues values = getOptions();
        if (values.hasBeenSet(key)) {
            return values.get(key);
        }
        return key.getDefaultValue();
    }

    static OptionValues getOptions() {
        OptionValuesImpl result = optionValues;
        if (result == null) {
            final EconomicMap<OptionKey<?>, Object> valuesMap = EconomicMap.create();
            final OptionDescriptors descriptors = new IgvOptionsOptionDescriptors();
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

    // Initialized by the options of the same name in org.graalvm.compiler.debug.DebugOptions

    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<Boolean> PrintGraphFile = new OptionKey<>(false);
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<Integer> PrintBinaryGraphPort = new OptionKey<>(4445);
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<String> PrintGraphHost = new OptionKey<>("127.0.0.1");
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<String> DumpPath = new OptionKey<>("dumps");
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<Boolean> ShowDumpFiles = new OptionKey<>(false);
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<Boolean> PrintGraph = new OptionKey<>(true);
    @Option(help = "", category = OptionCategory.DEBUG) public static final OptionKey<Boolean> PrintTruffleTrees = new OptionKey<>(true);
}
