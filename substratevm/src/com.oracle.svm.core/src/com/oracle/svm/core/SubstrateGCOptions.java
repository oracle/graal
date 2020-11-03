/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.XOptions;

/**
 * Garbage collection-specific options that are supported by all garbage collectors.
 */
public class SubstrateGCOptions {
    @Option(help = "Print summary GC information after each collection", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> PrintGC = new RuntimeOptionKey<>(false);

    @Option(help = "Print more information about the heap before and after each collection", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Boolean> VerboseGC = new RuntimeOptionKey<>(false);

    @Option(help = "Determines if references from runtime-compiled code to Java heap objects should be treated as strong or weak.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> TreatRuntimeCodeInfoReferencesAsWeak = new HostedOptionKey<>(true);

    @Option(help = "Verify the heap before and after each collection.")//
    public static final HostedOptionKey<Boolean> VerifyHeap = new HostedOptionKey<>(false);

    @Option(help = "The minimum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MinHeapSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXms().setValue(newValue);
            }
        }
    };

    @Option(help = "The maximum heap size at run-time, in bytes.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXmx().setValue(newValue);
            }
        }
    };

    @Option(help = "The maximum size of the young generation at run-time, in bytes", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxNewSize = new RuntimeOptionKey<Long>(0L) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Long oldValue, Long newValue) {
            if (!SubstrateUtil.HOSTED) {
                XOptions.getXmn().setValue(newValue);
            }
        }
    };
}
