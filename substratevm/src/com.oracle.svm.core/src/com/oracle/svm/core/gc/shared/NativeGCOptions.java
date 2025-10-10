/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shared;

import static com.oracle.svm.core.option.RuntimeOptionKey.RuntimeOptionKeyFlag.IsolateCreationOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.SubstrateOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.CodeUtil;

/**
 * Defines options that are specific to GCs such as G1. Hosted options are properly validated at
 * build-time. Runtime options, for which a default value is specified at build-time using
 * {@code -R:...}, undergo only basic build-time validation. Comprehensive validation of these
 * runtime options is performed by the C++ code during GC initialization.
 * <p>
 * Please note that the default values below don't necessarily match the default values that are
 * specified in files such as {@code gc_globals.hpp}. Some of these values are computed during
 * startup as they depend on the used GC and operating system.
 * <p>
 * All options that are relevant for the C++ code are serialized into byte arrays (see
 * {@link HostedArgumentsSupplier} and {@link RuntimeArgumentsSupplier}). During VM startup, SVM
 * passes those byte arrays to the C++ code.
 * <p>
 * <b>Important:</b> After VM startup, only the C++ code knows which value each runtime option has.
 * Option values are not synced back to SVM, so Java code must not access any of the runtime options
 * below, as doing so may return outdated or incorrect values.
 */
public class NativeGCOptions {
    public static final int K = 1024;
    public static final int M = 1024 * K;

    /* gc_globals.hpp */

    @Option(help = "Number of parallel threads that the GC will use.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> ParallelGCThreads = new NativeGCRuntimeOptionKey<>(0, IsolateCreationOnly);

    @Option(help = "Dynamically choose the number of threads up to a maximum of ParallelGCThreads that the GC will use for garbage collection work.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> UseDynamicNumberOfGCThreads = new NativeGCRuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "Size of heap (bytes) per GC thread used in calculating the number of GC threads.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> HeapSizePerGCThread = new NativeGCRuntimeOptionKey<>(42L * M, IsolateCreationOnly);

    @Option(help = "Number of concurrent threads that the GC will use.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> ConcGCThreads = new NativeGCRuntimeOptionKey<>(0, IsolateCreationOnly);

    @Option(help = "Determines if System.gc() invokes a concurrent collection.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> ExplicitGCInvokesConcurrent = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Wasted fraction of parallel allocation buffer.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> ParallelGCBufferWastePct = new NativeGCRuntimeOptionKey<>(10, IsolateCreationOnly);

    @Option(help = "Target wasted space in last buffer as percent of overall allocation.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> TargetPLABWastePct = new NativeGCRuntimeOptionKey<>(10, IsolateCreationOnly);

    @Option(help = "Percentage (0-100) used to weight the current sample when computing exponentially decaying average for ResizePLAB.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> PLABWeight = new NativeGCRuntimeOptionKey<>(75, IsolateCreationOnly);

    @Option(help = "Dynamically resize (survivor space) promotion LAB's.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> ResizePLAB = new NativeGCRuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "Scan a subset of object array and push remainder, if array is bigger than this.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> ParGCArrayScanChunk = new NativeGCRuntimeOptionKey<>(50, IsolateCreationOnly);

    @Option(help = "Force all freshly committed pages to be pre-touched.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> AlwaysPreTouch = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Per-thread chunk size for parallel memory pre-touch.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> PreTouchParallelChunkSize = new NativeGCRuntimeOptionKey<>(4L * M, IsolateCreationOnly);

    @Option(help = "Maximum size of marking stack in bytes.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> MarkStackSizeMax = new NativeGCRuntimeOptionKey<>(512L * M, IsolateCreationOnly);

    @Option(help = "Size of marking stack in bytes.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> MarkStackSize = new NativeGCRuntimeOptionKey<>(4L * M, IsolateCreationOnly);

    @Option(help = "Enable parallel reference processing whenever possible.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> ParallelRefProcEnabled = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Enable balancing of reference processing queues.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> ParallelRefProcBalancingEnabled = new NativeGCRuntimeOptionKey<>(true, IsolateCreationOnly);

    @Option(help = "The percent occupancy (IHOP) of the current old generation capacity above which a concurrent mark cycle will be initiated. Its value may change over time if adaptive IHOP is enabled, otherwise " +
                    "the value remains constant. In the latter case a value of 0 will result as frequent as possible concurrent marking cycles. A value of 100 disables concurrent marking. Fragmentation waste in the old generation " +
                    "is not considered free space in this calculation.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> InitiatingHeapOccupancyPercent = new NativeGCRuntimeOptionKey<>(45, IsolateCreationOnly);

    protected static final RuntimeOptionKey<Long> MaxRAM = SubstrateOptions.ConcealedOptions.MaxRAM;

    @Option(help = "Maximum ergonomically set heap size (in bytes); zero means use MaxRAM * MaxRAMPercentage / 100.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> ErgoHeapSizeLimit = new NativeGCRuntimeOptionKey<>(0L, IsolateCreationOnly);

    @Option(help = "Maximum percentage of real memory used for maximum heap size.", type = OptionType.Expert)//
    public static final RuntimeOptionKey<Double> MaxRAMPercentage = new NativeGCRuntimeOptionKey<>(25.0, IsolateCreationOnly);

    @Option(help = "Minimum percentage of real memory used for maximum heap size on systems with small physical memory size.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Double> MinRAMPercentage = new NativeGCRuntimeOptionKey<>(50.0, IsolateCreationOnly);

    @Option(help = "Percentage of real memory used for initial heap size.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Double> InitialRAMPercentage = new NativeGCRuntimeOptionKey<>(0.2, IsolateCreationOnly);

    protected static final RuntimeOptionKey<Integer> ActiveProcessorCount = SubstrateOptions.ActiveProcessorCount;

    @Option(help = "Adaptive size policy maximum GC pause time goal in millisecond, or the maximum GC time per MMU time slice.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> MaxGCPauseMillis = new NativeGCRuntimeOptionKey<>(200L, IsolateCreationOnly);

    @Option(help = "Time slice for MMU specification.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> GCPauseIntervalMillis = new NativeGCRuntimeOptionKey<>(201L, IsolateCreationOnly);

    @Option(help = "Adaptive size policy application time to GC time ratio.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> GCTimeRatio = new NativeGCRuntimeOptionKey<>(12, IsolateCreationOnly);

    @Option(help = "How far ahead to prefetch destination area (<= 0 means off).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> PrefetchCopyIntervalInBytes = new NativeGCRuntimeOptionKey<>(-1L, IsolateCreationOnly);

    @Option(help = "How far ahead to prefetch scan area (<= 0 means off).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> PrefetchScanIntervalInBytes = new NativeGCRuntimeOptionKey<>(-1L, IsolateCreationOnly);

    @Option(help = "Verify memory system before GC.", type = OptionType.Debug)//
    protected static final RuntimeOptionKey<Boolean> VerifyBeforeGC = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Verify memory system after GC.", type = OptionType.Debug)//
    protected static final RuntimeOptionKey<Boolean> VerifyAfterGC = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Verify memory system during GC (between phases).", type = OptionType.Debug)//
    protected static final RuntimeOptionKey<Boolean> VerifyDuringGC = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Initial heap size (in bytes); zero means use ergonomics.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> InitialHeapSize = new NativeGCRuntimeOptionKey<>(0L, IsolateCreationOnly);

    @Option(help = "Initial new generation size (in bytes).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> NewSize = new NativeGCRuntimeOptionKey<>(1L * M, IsolateCreationOnly);

    @Option(help = "Ratio of eden/survivor space size.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> SurvivorRatio = new NativeGCRuntimeOptionKey<>(8L, IsolateCreationOnly);

    @Option(help = "Ratio of old/new generation sizes.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> NewRatio = new NativeGCRuntimeOptionKey<>(2L, IsolateCreationOnly);

    @Option(help = "Number of times an allocation that queues behind a GC will retry before printing a warning.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> QueuedAllocationWarningCount = new NativeGCRuntimeOptionKey<>(0L, IsolateCreationOnly);

    @Option(help = "GC invoke count where +VerifyHeap kicks in.", type = OptionType.Debug)//
    protected static final RuntimeOptionKey<Long> VerifyGCStartAt = new NativeGCRuntimeOptionKey<>(0L, IsolateCreationOnly);

    @Option(help = "Maximum value for tenuring threshold.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> MaxTenuringThreshold = new NativeGCRuntimeOptionKey<>(15, IsolateCreationOnly);

    @Option(help = "Desired percentage of survivor space used after scavenge.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> TargetSurvivorRatio = new NativeGCRuntimeOptionKey<>(50, IsolateCreationOnly);

    @Option(help = "Number of entries we will try to leave on the stack during gc.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Integer> GCDrainStackTargetSize = new NativeGCRuntimeOptionKey<>(64, IsolateCreationOnly);

    @Option(help = "Card table entry size (in bytes).", type = OptionType.Expert)//
    public static final HostedOptionKey<Integer> GCCardSizeInBytes = new NativeGCHostedOptionKey<>(512, NativeGCOptions::validatePowerOfTwo);

    /* tlab_globals.hpp */
    @Option(help = "Zero out the newly created TLAB.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Boolean> ZeroTLAB = new NativeGCRuntimeOptionKey<>(false, IsolateCreationOnly);

    @Option(help = "Size of young gen promotion LAB's (in HeapWords).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> YoungPLABSize = new NativeGCRuntimeOptionKey<>(4096L, IsolateCreationOnly);

    @Option(help = "Size of old gen promotion LAB's (in HeapWords).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> OldPLABSize = new NativeGCRuntimeOptionKey<>(1024L, IsolateCreationOnly);

    @Option(help = "Allocation averaging weight.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> TLABAllocationWeight = new NativeGCRuntimeOptionKey<>(35L, IsolateCreationOnly);

    @Option(help = "Percentage of Eden that can be wasted (half-full TLABs at GC).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> TLABWasteTargetPercent = new NativeGCRuntimeOptionKey<>(1L, IsolateCreationOnly);

    @Option(help = "Maximum TLAB waste at a refill (internal fragmentation).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> TLABRefillWasteFraction = new NativeGCRuntimeOptionKey<>(64L, IsolateCreationOnly);

    @Option(help = "Increment allowed waste at slow allocation.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> TLABWasteIncrement = new NativeGCRuntimeOptionKey<>(4L, IsolateCreationOnly);

    /* globals.hpp */

    @Option(help = "The minimum percentage of heap free after GC to avoid expansion.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> MinHeapFreeRatio = new NativeGCRuntimeOptionKey<>(40L, IsolateCreationOnly);

    @Option(help = "Number of milliseconds per MB of free space in the heap.", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> SoftRefLRUPolicyMSPerMB = new NativeGCRuntimeOptionKey<>(1000L, IsolateCreationOnly);

    @Option(help = "The minimum change in heap space due to GC (in bytes).", type = OptionType.Expert)//
    protected static final RuntimeOptionKey<Long> MinHeapDeltaBytes = new NativeGCRuntimeOptionKey<>(168L * K, IsolateCreationOnly);

    /* This runtime option depends on a hosted option (see special handling in svmToGC.cpp). */
    protected static final RuntimeOptionKey<Boolean> UsePerfData = SubstrateOptions.ConcealedOptions.UsePerfData;

    /* globals_linux.hpp */
    protected static final HostedOptionKey<Boolean> UseContainerSupport = SubstrateOptions.UseContainerSupport;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ArrayList<Field> getOptionFields(Class<?>[] optionClasses) {
        ArrayList<Field> result = new ArrayList<>();
        for (Class<?> clazz : optionClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && OptionKey.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    result.add(field);
                }
            }
        }
        return result;
    }

    private static void validatePowerOfTwo(HostedOptionKey<Integer> optionKey) {
        int value = optionKey.getValue();
        if (!CodeUtil.isPowerOf2(value)) {
            throw UserError.invalidOptionValue(optionKey, value, "The value must be a power of two");
        }
    }

    private static void validatePlatformAndGC(SubstrateOptionKey<?> optionKey) {
        if (!optionKey.hasBeenSet()) {
            return;
        }

        if (!Platform.includedIn(Platform.LINUX_AMD64.class) && !Platform.includedIn(Platform.LINUX_AARCH64.class)) {
            throw UserError.abort("The option '%s' can only be used on linux/amd64 or linux/aarch64.", optionKey.getName());
        } else if (!SubstrateOptions.useG1GC() && !SubstrateOptions.useShenandoahGC()) {
            throw UserError.abort("The option '%s' can only be used with the G1 ('--gc=G1') or the Shenandoah ('--gc=shenandoah') garbage collector.", optionKey.getName());
        }
    }

    public static class NativeGCHostedOptionKey<T> extends HostedOptionKey<T> {
        private final boolean passToCpp;

        public NativeGCHostedOptionKey(T defaultValue, Consumer<HostedOptionKey<T>> validation) {
            this(defaultValue, true, validation);
        }

        public NativeGCHostedOptionKey(T defaultValue, boolean passToCpp, Consumer<HostedOptionKey<T>> validation) {
            super(defaultValue, validation);
            this.passToCpp = passToCpp;
        }

        public boolean shouldPassToCpp() {
            return passToCpp;
        }

        @Override
        public void validate() {
            validatePlatformAndGC(this);
            super.validate();
        }
    }

    public static class NativeGCRuntimeOptionKey<T> extends RuntimeOptionKey<T> {
        public NativeGCRuntimeOptionKey(T defaultValue, RuntimeOptionKeyFlag... flags) {
            super(defaultValue, flags);
        }

        @Override
        public void validate() {
            validatePlatformAndGC(this);
            super.validate();
        }
    }

    /** Serializes GC-relevant hosted options and their build-time values into a byte array. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class HostedArgumentsSupplier implements Supplier<byte[]> {
        private final ArrayList<Field> optionFields;

        public HostedArgumentsSupplier(ArrayList<Field> optionFields) {
            this.optionFields = optionFields;
        }

        @Override
        public byte[] get() {
            NativeGCArgumentsBuffer buffer = new NativeGCArgumentsBuffer();
            UnmodifiableEconomicMap<OptionKey<?>, Object> map = HostedOptionValues.singleton().getMap();
            for (Field field : optionFields) {
                try {
                    Class<?> type = field.getType();
                    if (HostedOptionKey.class.isAssignableFrom(type)) {
                        HostedOptionKey<?> key = (HostedOptionKey<?>) field.get(null);
                        if (shouldPassToCpp(key)) {
                            buffer.putString(key.getName());
                            buffer.putPrimitive(key.getValueOrDefault(map));
                        }
                    }
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
            buffer.putEnd();
            return buffer.toArray();
        }

        private static boolean shouldPassToCpp(HostedOptionKey<?> key) {
            if (key instanceof NativeGCHostedOptionKey<?>) {
                return ((NativeGCHostedOptionKey<?>) key).shouldPassToCpp();
            }
            return true;
        }
    }

    /** Serializes GC-relevant runtime options and their build-time values into a byte array. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class RuntimeArgumentsSupplier implements Supplier<byte[]> {
        private final ArrayList<Field> optionFields;

        public RuntimeArgumentsSupplier(ArrayList<Field> optionFields) {
            this.optionFields = optionFields;
        }

        @Override
        public byte[] get() {
            NativeGCArgumentsBuffer buffer = new NativeGCArgumentsBuffer();
            OptionValues optionValues = RuntimeOptionValues.singleton();
            for (Field field : optionFields) {
                try {
                    Class<?> type = field.getType();
                    if (RuntimeOptionKey.class.isAssignableFrom(type)) {
                        RuntimeOptionKey<?> key = (RuntimeOptionKey<?>) field.get(null);
                        if (key.hasBeenSet(optionValues)) {
                            buffer.putString(key.getName());
                            buffer.putPrimitive(key.getValue(optionValues));
                        }
                    }
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }
            buffer.putEnd();
            return buffer.toArray();
        }
    }

    /**
     * Writes Strings as ISO_8859_1 with zero termination. Primitive values are encoded as 64-bit
     * values (regardless of their actual size and type).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    private static class NativeGCArgumentsBuffer {
        private ByteBuffer buffer;

        NativeGCArgumentsBuffer() {
            buffer = allocateBuffer(128);
        }

        public void putString(String value) {
            assert value != null && !value.isEmpty() : value;
            byte[] latin1String = value.getBytes(StandardCharsets.ISO_8859_1);
            ensureCapacity(latin1String.length + 1);

            buffer.put(latin1String);
            buffer.put((byte) 0);
        }

        public void putPrimitive(Object value) {
            ensureCapacity(8);

            long rawLong = switch (value) {
                case Boolean _ -> ((boolean) value) ? 1L : 0L;
                case Byte _ -> ((byte) value) & 0xFFL;
                case Character _ -> ((char) value) & 0xFFFFL;
                case Short _ -> ((short) value) & 0xFFFFL;
                case Integer _ -> ((int) value) & 0xFFFFFFFFL;
                case Long _ -> (long) value;
                case Double _ -> Double.doubleToLongBits((double) value);
                default -> throw VMError.shouldNotReachHere("Unexpected type: " + value.getClass());
            };

            buffer.putLong(rawLong);
        }

        public void putEnd() {
            ensureCapacity(1);
            buffer.put((byte) 0);
        }

        public void ensureCapacity(int requested) {
            int needed = buffer.position() + requested;
            if (buffer.capacity() < needed) {
                int newCapacity = buffer.capacity();
                do {
                    newCapacity *= 2;
                } while (newCapacity < needed);

                ByteBuffer newBuffer = allocateBuffer(newCapacity);
                newBuffer.put(buffer.array(), 0, buffer.position());
                buffer = newBuffer;
            }
        }

        public byte[] toArray() {
            return Arrays.copyOf(buffer.array(), buffer.position());
        }

        private static ByteBuffer allocateBuffer(int size) {
            return ByteBuffer.allocate(size).order(ByteOrder.nativeOrder());
        }
    }
}
