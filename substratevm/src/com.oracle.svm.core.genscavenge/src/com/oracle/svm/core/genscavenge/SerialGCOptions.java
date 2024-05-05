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

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;

/** Options that are only valid for the serial GC (and not for the epsilon GC). */
public final class SerialGCOptions {
    @Option(help = "The garbage collection policy, either Adaptive (default) or BySpaceAndTime. Serial GC only.", type = OptionType.User)//
    public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>("Adaptive", SerialGCOptions::serialGCOnly);

    @Option(help = "Percentage of total collection time that should be spent on young generation collections. Serial GC with collection policy 'BySpaceAndTime' only.", type = OptionType.User)//
    public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50, SerialGCOptions::serialGCOnly);

    @Option(help = "The maximum free bytes reserved for allocations, in bytes (0 for automatic according to GC policy). Serial GC only.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapFree = new RuntimeOptionKey<>(0L, SerialGCOptions::serialGCOnly);

    @Option(help = "Maximum number of survivor spaces. Serial GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Integer> MaxSurvivorSpaces = new HostedOptionKey<>(null, SerialGCOptions::serialGCOnly) {
        @Override
        public Integer getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
            Integer value = (Integer) values.get(this);
            UserError.guarantee(value == null || value >= 0, "%s value must be greater than or equal to 0", getName());
            return CollectionPolicy.getMaxSurvivorSpaces(value);
        }

        @Override
        public Integer getValue(OptionValues values) {
            assert checkDescriptorExists();
            return getValueOrDefault(values.getMap());
        }
    };

    @Option(help = "Determines if a full GC collects the young generation separately or together with the old generation. Serial GC only.", type = OptionType.Expert) //
    public static final RuntimeOptionKey<Boolean> CollectYoungGenerationSeparately = new RuntimeOptionKey<>(null, SerialGCOptions::serialGCOnly);

    @Option(help = "Enables card marking for image heap objects, which arranges them in chunks. Automatically enabled when supported. Serial GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> ImageHeapCardMarking = new HostedOptionKey<>(null, SerialGCOptions::serialGCOnly);

    @Option(help = "This number of milliseconds multiplied by the free heap memory in MByte is the time span " +
                    "for which a soft reference will keep its referent alive after its last access. Serial GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Integer> SoftRefLRUPolicyMSPerMB = new HostedOptionKey<>(1000, SerialGCOptions::serialGCOnly);

    @Option(help = "Print summary GC information after application main method returns. Serial GC only.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCSummary = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    /* Will be removed as part of GR-48148. */
    @Option(help = "Deprecated. Print a time stamp at each collection, if +PrintGC or +VerboseGC. Serial GC only.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCTimeStamps = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Print the time for each of the phases of each collection, if +VerboseGC. Serial GC only.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCTimes = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Instrument write barriers with counters. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> CountWriteBarriers = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify the heap before doing a garbage collection if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyBeforeGC = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify the heap after doing a garbage collection if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyAfterGC = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify the remembered set if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyRememberedSet = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify all object references if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyReferences = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify that object references point into valid heap chunks if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyReferencesPointIntoValidChunk = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Verify write barriers. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyWriteBarriers = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Trace heap chunks during collections, if +VerboseGC. Serial GC only.", type = OptionType.Debug) //
    public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Develop demographics of the object references visited. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> GreyToBlackObjRefDemographics = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    /* Option should be renamed, see GR-53798. */
    @Option(help = "Ignore the maximum heap size while in VM-internal code.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> IgnoreMaxHeapSizeWhileInVMOperation = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    private SerialGCOptions() {
    }

    private static void serialGCOnly(OptionKey<?> optionKey) {
        if (!SubstrateOptions.UseSerialGC.getValue()) {
            throw new InterruptImageBuilding("The option '" + optionKey.getName() + "' can only be used together with the serial garbage collector ('--gc=serial').");
        }
    }
}
