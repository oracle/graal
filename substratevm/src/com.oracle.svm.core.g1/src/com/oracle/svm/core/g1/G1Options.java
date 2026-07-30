/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import static com.oracle.svm.core.gc.shared.NativeGCOptions.K;
import static com.oracle.svm.core.gc.shared.NativeGCOptions.M;
import static com.oracle.svm.guest.staging.option.RuntimeOptionKey.RuntimeOptionKeyFlag.IsolateCreationOnly;
import static com.oracle.svm.shared.option.HostedOptionKey.HostedOptionKeyFlag.DoNotPassToNativeGC;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.graalvm.nativeimage.c.type.CCharPointer;

import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.core.gc.shared.NativeGCDebugLevel;
import com.oracle.svm.core.gc.shared.NativeGCOptions;
import com.oracle.svm.core.gc.shared.NativeGCOptions.HostedArgumentsSupplier;
import com.oracle.svm.core.gc.shared.NativeGCOptions.NativeGCHostedOptionKey;
import com.oracle.svm.core.gc.shared.NativeGCOptions.NativeGCRuntimeOptionKey;
import com.oracle.svm.core.gc.shared.NativeGCOptions.RuntimeArgumentsSupplier;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionKey;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

/** Contains options that are specific to G1. See {@link NativeGCOptions} for more details. */
public class G1Options {
    private static final String SUPPORTED_G1_HEAP_REGION_SIZES = "Supported values are 1m, 2m, 4m, 8m, 16m, 32m, 64m, 128m, 256m, or 512m";

    @Option(help = "Specifies the debug level of the linked G1 GC [product, fastdebug, or debug]", type = OptionType.Debug) //
    protected static final HostedOptionKey<String> G1DebugLevel = new G1HostedOptionKey<>("product", DoNotPassToNativeGC);

    @Fold
    public static NativeGCDebugLevel getDebugLevel() {
        NativeGCDebugLevel result = NativeGCDebugLevel.fromString(G1DebugLevel.getValue());
        UserError.guarantee(result != null, "'%s' is not a valid value for the option %s.", G1DebugLevel.getValue(), G1DebugLevel.getName());
        return result;
    }

    /* g1_globals.hpp */

    @Option(help = "Adaptively adjust the initiating heap occupancy from the initial value of InitiatingHeapOccupancyPercent. The policy attempts to start marking in time based on application behavior.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> G1UseAdaptiveIHOP = new G1RuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "Confidence level for MMU/pause predictions. A higher value means that G1 will use less safety margin for its predictions.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1ConfidencePercent = new G1RuntimeOptionKey<>(50, IsolateCreationOnly);

    @Option(help = "Target duration of individual concurrent marking steps in milliseconds.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Double> G1ConcMarkStepDurationMillis = new G1RuntimeOptionKey<>(10.0, IsolateCreationOnly);

    @Option(help = "The number of discovered reference objects to process before draining concurrent marking work queues.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1RefProcDrainInterval = new G1RuntimeOptionKey<>(1000, IsolateCreationOnly);

    @Option(help = "Number of entries in an SATB log buffer.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> G1SATBBufferSize = new G1RuntimeOptionKey<>(1L * K, IsolateCreationOnly);

    @Option(help = "Before enqueueing them, each mutator thread tries to do some filtering on the SATB buffers it generates. If post-filtering the percentage of retained entries is over this threshold " +
                    "the buffer will be enqueued for processing.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1SATBBufferEnqueueingThresholdPercent = new G1RuntimeOptionKey<>(60, IsolateCreationOnly);

    @Option(help = "Size of an update buffer.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> G1UpdateBufferSize = new G1RuntimeOptionKey<>(256L, IsolateCreationOnly);

    @Option(help = "A target percentage of time that is allowed to be spend on processing remembered set update buffers during the collection pause.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1RSetUpdatingPauseTimePercent = new G1RuntimeOptionKey<>(10, IsolateCreationOnly);

    @Option(help = "Control whether concurrent refinement is performed. Disabling effectively ignores G1RSetUpdatingPauseTimePercent.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> G1UseConcRefinement = new G1RuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "It determines the minimum reserve we should have in the heap to minimize the probability of promotion failure.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1ReservePercent = new G1RuntimeOptionKey<>(10, IsolateCreationOnly);

    @Option(help = "Size of the G1 regions in bytes. " + SUPPORTED_G1_HEAP_REGION_SIZES + ".", type = OptionType.User)//
    public static final HostedOptionKey<Integer> G1HeapRegionSize = new G1HostedOptionKey<>(1 * M, G1Options::validateHeapRegionSize);

    @Option(help = "The number of parallel remembered set update threads. Will be set ergonomically by default.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1ConcRefinementThreads = new G1RuntimeOptionKey<>(0, IsolateCreationOnly);

    @Option(help = "Amount of space, expressed as a percentage of the heap size, that G1 is willing not to collect to avoid expensive GCs.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> G1HeapWastePercent = new G1RuntimeOptionKey<>(5, IsolateCreationOnly);

    @Option(help = "The target number of mixed GCs after a marking cycle.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> G1MixedGCCountTarget = new G1RuntimeOptionKey<>(8L, IsolateCreationOnly);

    @Option(help = "Verify the code root lists attached to each heap region.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> G1VerifyHeapRegionCodeRoots = new G1RuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Number of milliseconds after a previous GC to wait before triggering a periodic gc. A value of zero disables periodically enforced gc cycles.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> G1PeriodicGCInterval = new G1RuntimeOptionKey<>(0L, IsolateCreationOnly);

    @Option(help = "Determines the kind of periodic GC. Set to true to have G1 perform a concurrent GC as periodic GC, otherwise use a STW Full GC.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> G1PeriodicGCInvokesConcurrent = new G1RuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "Maximum recent system wide load as returned by the 1m value of getloadavg() at which G1 triggers a periodic GC. A load above this value cancels a given periodic GC. " +
                    "A value of zero disables this check.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Double> G1PeriodicGCSystemLoadThreshold = new G1RuntimeOptionKey<>(0.0, IsolateCreationOnly);

    /* Encoded option values. */
    public static final CGlobalData<CCharPointer> HOSTED_ARGUMENTS = CGlobalDataFactory.createBytes(new HostedArgumentsSupplier(getOptionFields()));
    public static final CGlobalData<CCharPointer> RUNTIME_ARGUMENTS = CGlobalDataFactory.createBytes(new RuntimeArgumentsSupplier(getOptionFields()));

    public static ArrayList<Field> getOptionFields() {
        Class<?>[] optionClasses = {SubstrateGCOptions.class, SubstrateGCOptions.ConcealedOptions.class, NativeGCOptions.class, G1Options.class};
        return NativeGCOptions.getOptionFields(optionClasses);
    }

    private static void validateG1Option(SubstrateOptionKey<?> optionKey) {
        if (optionKey.hasBeenSet() && !SubstrateOptions.useG1GC()) {
            throw UserError.abort("The option '%s' can only be used together with the G1 garbage collector ('--gc=G1').", optionKey.getName());
        }
    }

    private static void validateHeapRegionSize(HostedOptionKey<Integer> optionKey) {
        int value = optionKey.getValue();
        if (value % M == 0 && SubstrateUtil.isPowerOf2(value / M) && value >= 1 * M && value <= 512 * M) {
            return;
        }
        throw UserError.invalidOptionValue(G1HeapRegionSize, value, SUPPORTED_G1_HEAP_REGION_SIZES);
    }

    private static class G1HostedOptionKey<T> extends NativeGCHostedOptionKey<T> {
        G1HostedOptionKey(T defaultValue, HostedOptionKeyFlag... flags) {
            this(defaultValue, null, flags);
        }

        G1HostedOptionKey(T defaultValue, Consumer<HostedOptionKey<T>> validation, HostedOptionKeyFlag... flags) {
            super(defaultValue, validation, flags);
        }

        @Override
        public void validate() {
            validateG1Option(this);
            super.validate();
        }
    }

    private static class G1RuntimeOptionKey<T> extends NativeGCRuntimeOptionKey<T> {
        G1RuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
            super(defaultValue, flags);
        }

        @Override
        public void validate() {
            validateG1Option(this);
            super.validate();
        }
    }
}
