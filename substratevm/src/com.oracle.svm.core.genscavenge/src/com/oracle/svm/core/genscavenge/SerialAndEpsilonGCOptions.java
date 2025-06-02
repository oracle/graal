/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

/** Common options that can be specified for both the serial and the epsilon GC. */
public final class SerialAndEpsilonGCOptions {
    @Option(help = "The maximum heap size as percent of physical memory. Serial and epsilon GC only.", type = OptionType.User) //
    public static final RuntimeOptionKey<Integer> MaximumHeapSizePercent = new NotifyGCRuntimeOptionKey<>(80, SerialAndEpsilonGCOptions::validateSerialOrEpsilonRuntimeOption);

    @Option(help = "The maximum size of the young generation as a percentage of the maximum heap size. Serial and epsilon GC only.", type = OptionType.User) //
    public static final RuntimeOptionKey<Integer> MaximumYoungGenerationSizePercent = new NotifyGCRuntimeOptionKey<>(10, SerialAndEpsilonGCOptions::validateSerialOrEpsilonRuntimeOption);

    @Option(help = "The size of an aligned chunk. Serial and epsilon GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Long> AlignedHeapChunkSize = new HostedOptionKey<>(512 * 1024L, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    /*
     * This should be a fraction of the size of an aligned chunk, else large small arrays will not
     * fit in an aligned chunk.
     */
    @Option(help = "The size at or above which an array will be allocated in its own unaligned chunk. Serial and epsilon GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Long> LargeArrayThreshold = new HostedOptionKey<>(128 * 1024L, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    @Option(help = "Fill unused memory chunks with a sentinel value. Serial and epsilon GC only.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapChunks = new HostedOptionKey<>(false, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    @Option(help = "Before use, fill memory chunks with a sentinel value. Serial and epsilon GC only.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapProducedHeapChunks = new HostedOptionKey<>(false, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    @Option(help = "After use, Fill memory chunks with a sentinel value. Serial and epsilon GC only.", type = OptionType.Debug) //
    public static final HostedOptionKey<Boolean> ZapConsumedHeapChunks = new HostedOptionKey<>(false, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    @Option(help = "Number of bytes at the beginning of each heap chunk that are not used for payload data, i.e., can be freely used as metadata by the heap chunk provider. Serial and epsilon GC only.", type = OptionType.Debug) //
    public static final HostedOptionKey<Integer> HeapChunkHeaderPadding = new HostedOptionKey<>(0, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    @Option(help = "Starting TLAB size (in bytes); zero means set ergonomically.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Long> InitialTLABSize = new RuntimeOptionKey<>(8 * 1024L, SerialAndEpsilonGCOptions::validateSerialOrEpsilonRuntimeOption, RegisterForIsolateArgumentParser);

    @Option(help = "Print information about TLABs. Printed when The TLABs are retired before a GC, and during the resizing of the TLABs. Serial and epsilon GC only.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> PrintTLAB = new HostedOptionKey<>(false, SerialAndEpsilonGCOptions::validateSerialOrEpsilonHostedOption);

    private SerialAndEpsilonGCOptions() {
    }

    public static void validateSerialOrEpsilonHostedOption(HostedOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useSerialGC() && !SubstrateOptions.useEpsilonGC()) {
            throw UserError.abort("The option '" + optionKey.getName() + "' can only be used together with the serial ('--gc=serial') or the epsilon garbage collector ('--gc=epsilon').");
        }
    }

    public static void validateSerialOrEpsilonRuntimeOption(RuntimeOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useSerialGC() && !SubstrateOptions.useEpsilonGC()) {
            throw UserError.abort("The option '" + optionKey.getName() + "' can only be used together with the serial ('--gc=serial') or the epsilon garbage collector ('--gc=epsilon').");
        }
    }
}
