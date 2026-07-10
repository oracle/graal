/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.guest.staging.option.RuntimeOptionKey.RuntimeOptionKeyFlag.RegisterForIsolateArgumentParser;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.genscavenge.compacting.ObjectMoveInfo;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidationSupport;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidationSupport.RuntimeOptionValidation;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

/** Options that are only valid for the serial GC (and not for the epsilon GC). */
public final class SerialGCOptions {
    @Option(help = "The garbage collection policy. Default: 'Adaptive2'. Former default: 'Adaptive' (deprecated). Serial GC only.", type = OptionType.User)//
    public static final RuntimeOptionKey<String> InitialCollectionPolicy = new RuntimeOptionKey<>(null, SerialGCOptions::validateInitialCollectionPolicy, RegisterForIsolateArgumentParser) {
        @Override
        public boolean shouldRegisterForIsolateArgumentParser() {
            return SubstrateOptions.useSerialGC() && super.shouldRegisterForIsolateArgumentParser();
        }
    };

    @Option(help = "Percentage of total collection time that should be spent on young generation collections. Serial GC with collection policy 'BySpaceAndTime' only.", type = OptionType.User)//
    public static final RuntimeOptionKey<Integer> PercentTimeInIncrementalCollection = new RuntimeOptionKey<>(50, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "The maximum free bytes reserved for allocations, in bytes (0 for automatic according to GC policy). Serial GC only.", type = OptionType.User)//
    public static final RuntimeOptionKey<Long> MaxHeapFree = new RuntimeOptionKey<>(0L, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "Determines if a full GC collects the young generation separately or together with the old generation. Serial GC only.", type = OptionType.Expert) //
    public static final RuntimeOptionKey<Boolean> CollectYoungGenerationSeparately = new RuntimeOptionKey<>(null, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "Enables card marking for image heap objects, which arranges them in chunks. Automatically enabled when supported. Serial GC only.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> ImageHeapCardMarking = new HostedOptionKey<>(null, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Print summary GC information after application main method returns. Serial GC only.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCSummary = new RuntimeOptionKey<>(false, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "Print the time for each of the phases of each collection, if +VerboseGC. Serial GC only.", type = OptionType.Debug)//
    public static final RuntimeOptionKey<Boolean> PrintGCTimes = new RuntimeOptionKey<>(false, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "Verify the remembered set if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyRememberedSet = new HostedOptionKey<>(true, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Verify all object references if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyReferences = new HostedOptionKey<>(true, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Verify that object references point into valid heap chunks if VerifyHeap is enabled. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyReferencesPointIntoValidChunk = new HostedOptionKey<>(false, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Verify write barriers. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> VerifyWriteBarriers = new HostedOptionKey<>(false, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Trace heap chunks during collections, if +VerboseGC. Serial GC only.", type = OptionType.Debug) //
    public static final RuntimeOptionKey<Boolean> TraceHeapChunks = new RuntimeOptionKey<>(false, SerialGCOptions::validateSerialRuntimeOption);

    @Option(help = "Develop demographics of the object references visited. Serial GC only.", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> GreyToBlackObjRefDemographics = new HostedOptionKey<>(false, SerialGCOptions::validateSerialHostedOption);

    @Option(help = "Ignore the maximum heap size while in VM-internal code. Serial GC only.", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> IgnoreMaxHeapSizeWhileInVMInternalCode = new HostedOptionKey<>(false, SerialGCOptions::validateSerialHostedOption);

    /** Query these options only through an appropriate method. */
    public static class ConcealedOptions {
        @Option(help = "Collect old generation by compacting in-place instead of copying. Serial GC only.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> CompactingOldGen = new HostedOptionKey<>(true, SerialGCOptions::validateCompactingOldGen);

        @Option(help = "Determines if a remembered set is used, which is necessary for collecting the young and old generation independently. Serial GC only.", type = OptionType.Expert) //
        public static final HostedOptionKey<Boolean> UseRememberedSet = new HostedOptionKey<>(true, SerialGCOptions::validateSerialHostedOption);

        /** Use {@link HeapParameters#getMaxSurvivorSpaces} instead. */
        @Option(help = "Maximum number of survivor spaces. Serial GC only.", type = OptionType.Expert) //
        public static final HostedOptionKey<Integer> MaxSurvivorSpaces = new HostedOptionKey<>(null, SerialGCOptions::validateSerialHostedOption);
    }

    public static class DeprecatedOptions {
        @Option(help = "Ignore the maximum heap size while in VM-internal code. Serial GC only.", type = OptionType.Expert, deprecated = true, deprecationMessage = "Please use the option 'IgnoreMaxHeapSizeWhileInVMInternalCode' instead.")//
        public static final HostedOptionKey<Boolean> IgnoreMaxHeapSizeWhileInVMOperation = new HostedOptionKey<>(false, SerialGCOptions::validateSerialHostedOption) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                IgnoreMaxHeapSizeWhileInVMInternalCode.update(values, newValue);
            }
        };

    }

    private SerialGCOptions() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerRuntimeOptionValidations() {
        RuntimeOptionValidationSupport.singleton().register(new RuntimeOptionValidation<>(SerialGCOptions::validateInitialCollectionPolicyValue, InitialCollectionPolicy));
    }

    private static void validateSerialHostedOption(HostedOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useSerialGC()) {
            throw UserError.abort("The option '" + optionKey.getName() + "' can only be used together with the serial garbage collector ('--gc=serial').");
        }
    }

    private static void validateInitialCollectionPolicy(RuntimeOptionKey<String> optionKey) {
        validateSerialRuntimeOption(optionKey);
        validateInitialCollectionPolicyValue(optionKey);
    }

    private static void validateSerialRuntimeOption(RuntimeOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useSerialGC()) {
            throw UserError.abort("The option '" + optionKey.getName() + "' can only be used together with the serial garbage collector ('--gc=serial').");
        }
    }

    private static void validateInitialCollectionPolicyValue(RuntimeOptionKey<String> optionKey) {
        CollectionPolicies.validatePolicyName(optionKey);

        if (optionKey.hasBeenSet() && !SerialGCOptions.useRememberedSet()) {
            throw invalidOptionValue("The collection policy cannot be set via '%s' if 'UseRememberedSet' is disabled.", SerialGCOptions.InitialCollectionPolicy.getName());
        }
    }

    private static RuntimeException invalidOptionValue(String message, Object... args) {
        if (SubstrateUtil.HOSTED) {
            throw UserError.abort(message, args);
        }
        throw new IllegalArgumentException(String.format(message, args));
    }

    private static void validateCompactingOldGen(HostedOptionKey<Boolean> compactingOldGen) {
        validateSerialHostedOption(compactingOldGen);
        if (!SubstrateOptions.useSerialGC() || !compactingOldGen.getValue()) {
            return;
        }
        if (!useRememberedSet()) {
            throw UserError.abort("%s requires %s.", SubstrateOptionsParser.commandArgument(ConcealedOptions.CompactingOldGen, "+"),
                            SubstrateOptionsParser.commandArgument(ConcealedOptions.UseRememberedSet, "+"));
        }
        if (SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue() > ObjectMoveInfo.MAX_CHUNK_SIZE) {
            throw UserError.abort("%s requires %s.", SubstrateOptionsParser.commandArgument(ConcealedOptions.CompactingOldGen, "+"),
                            SubstrateOptionsParser.commandArgument(SerialAndEpsilonGCOptions.AlignedHeapChunkSize, "<value below or equal to " + ObjectMoveInfo.MAX_CHUNK_SIZE + ">"));
        }
    }

    @Fold
    public static boolean useRememberedSet() {
        return !SubstrateOptions.useEpsilonGC() && ConcealedOptions.UseRememberedSet.getValue();
    }

    @Fold
    public static boolean useCompactingOldGen() {
        return !SubstrateOptions.useEpsilonGC() && ConcealedOptions.CompactingOldGen.getValue();
    }
}
