/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature.FeatureAccess;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.XOptions;
import com.oracle.svm.core.util.AtomicUnsigned;
import com.oracle.svm.core.util.UserError;

/**
 * HeapPolicy contains different GC policies including size of memory chunk, large array threshold,
 * limit of unused chunk, maximum heap size and verbose for printing debugging information during
 * GC.
 */

public class HeapPolicy {

    static final long LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE = 0;
    private static final int ALIGNED_HEAP_CHUNK_FRACTION_FOR_LARGE_ARRAY_THRESHOLD = 8;

    /* Policy constants initialized from command line options during image build. */
    private final CollectOnAllocationPolicy collectOnAllocationPolicy;
    private final HeapPolicy.HintGCPolicy userRequestedGCPolicy;

    /* Constructor for subclasses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected HeapPolicy(FeatureAccess access) {
        if (!SubstrateUtil.isPowerOf2(getAlignedHeapChunkSize().rawValue())) {
            throw UserError.abort("AlignedHeapChunkSize (" + getAlignedHeapChunkSize().rawValue() + ")" + " should be a power of 2.");
        }
        if (!getLargeArrayThreshold().belowOrEqual(getAlignedHeapChunkSize())) {
            throw UserError.abort("LargeArrayThreshold (" + getLargeArrayThreshold().rawValue() + ")" +
                            " should be below or equal to AlignedHeapChunkSize (" + getAlignedHeapChunkSize().rawValue() + ").");
        }
        /* Policy variables. */
        userRequestedGCPolicy = instantiatePolicy(access, HeapPolicy.HintGCPolicy.class, HeapPolicyOptions.UserRequestedGCPolicy.getValue());
        collectOnAllocationPolicy = CollectOnAllocationPolicy.Sometimes.factory();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static <T> T instantiatePolicy(FeatureAccess access, Class<T> policyClass, String className) {
        Class<?> policy = access.findClassByName(className);
        if (policy == null) {
            throw UserError.abort("policy " + className + " does not exist. It must be a fully qualified class name.");
        }

        Object result;
        try {
            result = policy.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw UserError.abort("policy " + className + " cannot be instantiated.");
        }

        if (!policyClass.isInstance(result)) {
            throw UserError.abort("policy " + className + " does not extend " + policyClass.getTypeName() + ".");
        }
        return policyClass.cast(result);
    }

    /*
     * Instance field access methods.
     */
    CollectOnAllocationPolicy getCollectOnAllocationPolicy() {
        return collectOnAllocationPolicy;
    }

    public static Word getProducedHeapChunkZapWord() {
        return producedHeapChunkZapWord;
    }

    public static int getProducedHeapChunkZapInt() {
        return producedHeapChunkZapInt;
    }

    public static Word getConsumedHeapChunkZapWord() {
        return consumedHeapChunkZapWord;
    }

    public static int getConsumedHeapChunkZapInt() {
        return consumedHeapChunkZapInt;
    }

    /*
     * Access methods for policy things.
     */

    /* Useful constants. */
    /* TODO: These should be somewhere more public. */
    public static UnsignedWord k(int bytes) {
        assert 0 <= bytes;
        return k((long) bytes);
    }

    public static UnsignedWord k(long bytes) {
        assert 0 <= bytes;
        return k(WordFactory.unsigned(bytes));
    }

    public static UnsignedWord k(UnsignedWord bytes) {
        return bytes.multiply(1024);
    }

    public static UnsignedWord m(int bytes) {
        assert 0 <= bytes;
        return m((long) bytes);
    }

    public static UnsignedWord m(long bytes) {
        assert 0 <= bytes;
        return m(WordFactory.unsigned(bytes));
    }

    public static UnsignedWord m(UnsignedWord bytes) {
        return k(k(bytes));
    }

    public static UnsignedWord g(int bytes) {
        assert 0 <= bytes;
        return g((long) bytes);
    }

    public static UnsignedWord g(long bytes) {
        assert 0 <= bytes;
        return g(WordFactory.unsigned(bytes));
    }

    public static UnsignedWord g(UnsignedWord bytes) {
        return k(k(k(bytes)));
    }

    /* Memory configuration */

    private static UnsignedWord maximumYoungGenerationSize;
    private static UnsignedWord minimumHeapSize;
    private static UnsignedWord maximumHeapSize;

    /** The maximum size of the young generation as an UnsignedWord. */
    public static UnsignedWord getMaximumYoungGenerationSize() {
        final Log trace = Log.noopLog().string("[HeapPolicy.getMaximumYoungGenerationSize:");
        if (maximumYoungGenerationSize.aboveThan(WordFactory.zero())) {
            /* If someone has set the young generation size, use that value. */
            trace.string("  returns: ").unsigned(maximumYoungGenerationSize).string(" ]").newline();
            return maximumYoungGenerationSize;
        }
        final XOptions.XFlag xmn = XOptions.getXmn();
        final UnsignedWord result;
        if (xmn.getEpoch() > 0) {
            /* If `-Xmn` has been parsed from the command line, use that value. */
            result = WordFactory.unsigned(xmn.getValue());
        } else {
            /* The default value is just big enough. */
            result = m(256);
        }
        trace.string("  -Xmn.epoch: ").unsigned(xmn.getEpoch()).string("  -Xmn.value: ").unsigned(xmn.getValue())
                        .string("  returns: ").unsigned(result).string("]").newline();
        return result;
    }

    /** Set the maximum young generation size, returning the previous value. */
    public static UnsignedWord setMaximumYoungGenerationSize(UnsignedWord value) {
        final UnsignedWord result = maximumYoungGenerationSize;
        maximumYoungGenerationSize = value;
        return result;
    }

    /** The maximum size of the heap as an UnsignedWord. */
    public static UnsignedWord getMaximumHeapSize() {
        final Log trace = Log.noopLog().string("[HeapPolicy.getMaximumHeapSize:");
        if (maximumHeapSize.aboveThan(WordFactory.zero())) {
            /* If someone has set the maximum heap size, use that value. */
            trace.string("  returns: ").unsigned(maximumHeapSize).string(" ]").newline();
            return maximumHeapSize;
        }
        final UnsignedWord result;
        final XOptions.XFlag xmx = XOptions.getXmx();
        if (xmx.getEpoch() > 0) {
            /* If `-Xmx` has been parsed from the command line, use that value. */
            result = WordFactory.unsigned(xmx.getValue());
        } else {
            /*
             * Otherwise, the maximum size of the heap is a fraction of the size of the physical
             * memory. I choose not to worry about overflow on the multiply.
             */
            final int maximumHeapSizePercent = HeapPolicyOptions.MaximumHeapSizePercent.getValue();
            result = PhysicalMemory.size().multiply(maximumHeapSizePercent).unsignedDivide(100);
        }
        maximumHeapSize = result;
        if (trace.isEnabled()) {
            trace.string("  -Xmx.epoch: ").unsigned(xmx.getEpoch()).string("  -Xmx.value: ").unsigned(xmx.getValue())
                            .string("  MaximumHeapSizePercent: ").unsigned(HeapPolicyOptions.MaximumHeapSizePercent.getValue().intValue())
                            .string("  PhysicalMemory.size(): ").unsigned(PhysicalMemory.size())
                            .newline();
            trace.string("  returns: ").unsigned(result).string("]").newline();
        }
        return result;
    }

    /** Set the maximum heap size, returning the previous value. */
    public static UnsignedWord setMaximumHeapSize(UnsignedWord value) {
        final Log trace = Log.noopLog().string("[HeapPolicy.setMaximumHeapSize:");
        final UnsignedWord result = maximumHeapSize;
        maximumHeapSize = value;
        trace.string("  old: ").unsigned(result).string("  new: ").unsigned(maximumHeapSize).string(" ]").newline();
        return result;
    }

    /** The minimum size of the heap as an UnsignedWord. */
    public static UnsignedWord getMinimumHeapSize() {
        final Log trace = Log.noopLog().string("[HeapPolicy.getMinimumHeapSize:");
        if (minimumHeapSize.aboveThan(WordFactory.zero())) {
            /* If someone has set the minimum heap size, use that value. */
            trace.string("  returns: ").unsigned(minimumHeapSize).string(" ]").newline();
            return minimumHeapSize;
        }
        final XOptions.XFlag xms = XOptions.getXms();
        UnsignedWord result;
        if (xms.getEpoch() > 0) {
            /* If `-Xms` has been parsed from the command line, use that value. */
            result = WordFactory.unsigned(xms.getValue());
        } else {
            /* A default value chosen to delay the first full collection. */
            result = getMaximumYoungGenerationSize().multiply(2);
        }
        /* But not larger than -Xmx. */
        if (result.aboveThan(getMaximumHeapSize())) {
            result = getMaximumHeapSize();
        }
        minimumHeapSize = result;
        trace.string("  -Xms.epoch: ").unsigned(xms.getEpoch()).string("  -Xms.value: ").unsigned(xms.getValue())
                        .string("  returns: ").unsigned(result).string("]").newline();
        return result;
    }

    /** Set the minimum heap size, returning the previous value. */
    public static UnsignedWord setMinimumHeapSize(UnsignedWord value) {
        final UnsignedWord result = minimumHeapSize;
        minimumHeapSize = value;
        return result;
    }

    /** The size of an aligned chunk as an Unsigned. */
    @Fold
    public static UnsignedWord getAlignedHeapChunkSize() {
        return WordFactory.unsigned(HeapPolicyOptions.AlignedHeapChunkSize.getValue());
    }

    /** The alignment of an aligned chunk as an Unsigned. */
    @Fold
    static UnsignedWord getAlignedHeapChunkAlignment() {
        /* AlignedHeapChunks are an aligned size, and other value makes sense. */
        return getAlignedHeapChunkSize();
    }

    /** The LargeArrayThreshold as an Unsigned. */
    @Fold
    public static UnsignedWord getLargeArrayThreshold() {
        long largeArrayThreshold = HeapPolicyOptions.LargeArrayThreshold.getValue();
        if (LARGE_ARRAY_THRESHOLD_SENTINEL_VALUE == largeArrayThreshold) {
            return getAlignedHeapChunkSize().unsignedDivide(ALIGNED_HEAP_CHUNK_FRACTION_FOR_LARGE_ARRAY_THRESHOLD);
        } else {
            return WordFactory.unsigned(HeapPolicyOptions.LargeArrayThreshold.getValue());
        }
    }

    /* Zapping */

    public static boolean getZapProducedHeapChunks() {
        return HeapPolicyOptions.ZapChunks.getValue() || HeapPolicyOptions.ZapProducedHeapChunks.getValue();
    }

    public static boolean getZapConsumedHeapChunks() {
        return HeapPolicyOptions.ZapChunks.getValue() || HeapPolicyOptions.ZapConsumedHeapChunks.getValue();
    }

    static {
        /* WordFactory.boxFactory is initialized by the static initializer of Word. */
        Word.ensureInitialized();
    }

    /* - The value to use for zapping produced chunks. */
    private static final int producedHeapChunkZapInt = 0xbaadbeef;
    private static final Word producedHeapChunkZapWord = WordFactory.unsigned((long) producedHeapChunkZapInt << 32 | producedHeapChunkZapInt);

    /* - The value to use for zapping. */
    private static final int consumedHeapChunkZapInt = 0xdeadbeef;
    private static final Word consumedHeapChunkZapWord = WordFactory.unsigned((long) consumedHeapChunkZapInt << 32 | consumedHeapChunkZapInt);

    static final AtomicUnsigned bytesAllocatedSinceLastCollection = new AtomicUnsigned();

    static UnsignedWord getBytesAllocatedSinceLastCollection() {
        return bytesAllocatedSinceLastCollection.get();
    }

    public HeapPolicy.HintGCPolicy getUserRequestedGCPolicy() {
        return userRequestedGCPolicy;
    }

    /** Methods exposed for testing. */
    public static final class TestingBackDoor {

        private TestingBackDoor() {
            /* No instances. */
        }

        /** The size, in bytes, of what qualifies as a "large" array. */
        public static long getUnalignedObjectSize() {
            /* TODO: An Unsigned might not fit in a long. */
            return HeapPolicy.getLargeArrayThreshold().rawValue();
        }
    }

    /**
     * A policy for when to cause automatic collections on allocation.
     */
    protected abstract static class CollectOnAllocationPolicy {

        /** Constructor for subclasses. */
        CollectOnAllocationPolicy() {
            /* Nothing to do. */
        }

        /** Cause a collection if the policy says to. */
        public abstract void maybeCauseCollection();

        /** A policy that never causes collection on allocation. */
        protected static class Never extends CollectOnAllocationPolicy {

            public static Never factory() {
                return new Never();
            }

            @Override
            public void maybeCauseCollection() {
            }

            Never() {
                super();
                /* Nothing to do. */
            }
        }

        /** A policy that always causes collection on allocation. */
        protected static class Always extends CollectOnAllocationPolicy {

            public static Always factory() {
                return new Always();
            }

            @Override
            public void maybeCauseCollection() {
                HeapImpl.getHeapImpl().getGCImpl().collectWithoutAllocating("CollectOnAllocationPolicy.Always");
            }

            Always() {
                super();
                /* Nothing to do. */
            }
        }

        /** A policy that causes collections if enough young generation allocation has happened. */
        protected static class Sometimes extends CollectOnAllocationPolicy {

            public static Sometimes factory() {
                return new Sometimes();
            }

            /** Cause a collection if the fast-path allocation Space has allocated enough bytes. */
            @Override
            public void maybeCauseCollection() {
                final HeapImpl heap = HeapImpl.getHeapImpl();
                /* Has there been enough allocation to provoke a collection? */
                if (bytesAllocatedSinceLastCollection.get().aboveOrEqual(getMaximumYoungGenerationSize())) {
                    heap.getGCImpl().collectWithoutAllocating("CollectOnAllocation.Sometimes");
                }
            }

            Sometimes() {
                super();
            }
        }
    }

    public interface HintGCPolicy {
        @SuppressWarnings("SameParameterValue")
        void maybeCauseCollection(String message);
    }

    public static class AlwaysCollectCompletely implements HeapPolicy.HintGCPolicy {
        @Override
        public void maybeCauseCollection(String message) {
            HeapImpl.getHeapImpl().getGC().collectCompletely(message);
        }
    }

    /**
     * Collect if bytes allocated since last collection exceed the threshold defined by
     * {@link #collectScepticallyThreshold()}.
     */
    public static class ScepticallyCollect implements HeapPolicy.HintGCPolicy {
        @Override
        public void maybeCauseCollection(String message) {
            if (bytesAllocatedSinceLastCollection.get().aboveOrEqual(collectScepticallyThreshold())) {
                HeapImpl.getHeapImpl().getGCImpl().collect(message);
            }
        }

        public static UnsignedWord collectScepticallyThreshold() {
            return getMaximumYoungGenerationSize().subtract(WordFactory.unsigned(HeapPolicyOptions.UserRequestedGCThreshold.getValue()));
        }
    }
}
