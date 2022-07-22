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
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;

/** Options that are only valid for the serial GC (and not for the epsilon GC). */
public final class SerialGCOptions {
    @Option(help = "Serial GC only: the garbage collection policy, either Adaptive (default) or BySpaceAndTime.", type = OptionType.User)//
    public static final HostedOptionKey<String> InitialCollectionPolicy = new HostedOptionKey<>("Adaptive", SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: percentage of total collection time that should be spent on young generation collections.", type = OptionType.User)//
    public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: the maximum free bytes reserved for allocations, in bytes (0 for automatic according to GC policy).", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapFree = new RuntimeOptionKey<>(0L, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: maximum number of survivor spaces.", type = OptionType.Expert) //
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

    @Option(help = "Serial GC only: determines if a full GC collects the young generation separately or together with the old generation.", type = OptionType.Expert) //
    public static final RuntimeOptionKey<Boolean> CollectYoungGenerationSeparately = new RuntimeOptionKey<>(null, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: enables card marking for image heap objects, which arranges them in chunks. Automatically enabled when supported.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> ImageHeapCardMarking = new HostedOptionKey<>(null, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: this number of milliseconds multiplied by the free heap memory in MByte is the time span " +
                    "for which a soft reference will keep its referent alive after its last access.", type = OptionType.Expert) //
    public static final HostedOptionKey<Integer> SoftRefLRUPolicyMSPerMB = new HostedOptionKey<>(1000, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: print the shape of the heap before and after each collection, if +VerboseGC.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintHeapShape = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: print summary GC information after application main method returns.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCSummary = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: print a time stamp at each collection, if +PrintGC or +VerboseGC.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCTimeStamps = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: print the time for each of the phases of each collection, if +VerboseGC.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCTimes = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: instrument write barriers with counters", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> CountWriteBarriers = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: verify the remembered set if VerifyHeap is enabled.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyRememberedSet = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: verify all object references if VerifyHeap is enabled.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyReferences = new HostedOptionKey<>(true, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: verify write barriers", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyWriteBarriers = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: trace heap chunks during collections, if +VerboseGC and +PrintHeapShape.", type = OptionType.Debug) //
    public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false, SerialGCOptions::serialGCOnly);

    @Option(help = "Serial GC only: develop demographics of the object references visited.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> GreyToBlackObjRefDemographics = new HostedOptionKey<>(false, SerialGCOptions::serialGCOnly);

    private SerialGCOptions() {
    }

    private static void serialGCOnly(OptionKey<?> optionKey) {
        if (!SubstrateOptions.UseSerialGC.getValue() && !SubstrateOptions.UseEpsilonGC.getValue()) {
            throw new InterruptImageBuilding("The option " + optionKey.getName() + " is garbage collector specific and cannot be specified if the " +
                            Heap.getHeap().getGC().getName() + " is used at runtime.");
        }
    }
}
