/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm;

import org.graalvm.collections.UnmodifiableEconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.webimage.options.WebImageOptions.CommentVerbosity;

import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

public class WebImageWasmOptions {

    @Option(help = "Size of the WASM shadow stack (in 64KiB pages).") //
    public static final HostedOptionKey<Integer> StackSize = new HostedOptionKey<>(16);

    @Option(help = "Determine the level of verbosity for comments in the WASM text format." +
                    "Has no effect on code size, the binary format does not have comments.")//
    public static final EnumOptionKey<CommentVerbosity> WasmComments = new EnumOptionKey<>(CommentVerbosity.NORMAL);

    @Option(help = "Assemble the Wasm binary file with debug names.")//
    public static final HostedOptionKey<Boolean> DebugNames = new HostedOptionKey<>(false) {

        @Override
        public Boolean getValue(OptionValues values) {
            assert checkDescriptorExists();
            return getValueOrDefault(values.getMap());
        }

        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (values.containsKey(this)) {
                return (Boolean) values.get(this);
            }

            return SubstrateOptions.GenerateDebugInfo.getValueOrDefault(values) > 0;
        }
    };

    /*
     * TODO GR-47009 Remove option once all nodes are lowered (at which point unsupported nodes will
     * be fatal at build time).
     */
    @Option(help = "Unsupported nodes trap with error message at runtime. If false, they are no-ops with a stub value.") //
    public static final HostedOptionKey<Boolean> FatalUnsupportedNodes = new HostedOptionKey<>(false) {

        @Override
        public Boolean getValue(OptionValues values) {
            assert checkDescriptorExists();
            return getValueOrDefault(values.getMap());
        }

        @Override
        public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            if (values.containsKey(this)) {
                return (Boolean) values.get(this);
            }

            return SubstrateOptions.GenerateDebugInfo.getValueOrDefault(values) > 0;
        }
    };

    @Option(help = "At most this many image heap objects are initialized in a single function. Only applies to the WasmGC backend", type = OptionType.Expert) //
    public static final HostedOptionKey<Integer> ImageHeapObjectsPerFunction = new HostedOptionKey<>(100_000, optionKey -> {
        if (optionKey.hasBeenSet() && optionKey.getValue() <= 0) {
            throw UserError.abort("Option '%s' must be a positive number", SubstrateOptionsParser.commandArgument(optionKey, optionKey.getValue().toString()));
        }
    });

    public static boolean fatalUnsupportedNodes() {
        return FatalUnsupportedNodes.getValue();
    }

    public static int getNumberOfImageHeapObjectsPerFunction() {
        return ImageHeapObjectsPerFunction.getValue();
    }

    /**
     * Returns true if wasm comments should be emitted.
     */
    public static boolean genComments(CommentVerbosity verbosity) {
        return WasmComments.getValue(HostedOptionValues.singleton()).isEnabled(verbosity);
    }

    public static boolean genComments() {
        return genComments(CommentVerbosity.NORMAL);
    }
}
