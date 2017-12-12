/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
            result = policy.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
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

    public static Word getProducedHeapChunkZapValue() {
        return producedHeapChunkZapValue;
    }

    public static Word getConsumedHeapChunkZapValue() {
        return consumedHeapChunkZapValue;
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

    /** The YoungGenerationSize option as an Unsigned. */
    public static UnsignedWord getYoungGenerationSize() {
        return WordFactory.unsigned(HeapPolicyOptions.YoungGenerationSize.getValue());
    }

    /* Computed and remembered at the first use of getOldGenerationSize */
    private long rememberedOldGenerationSize = -1L;

    public static UnsignedWord getOldGenerationSize() {
        final HeapPolicy heapPolicy = HeapImpl.getHeapImpl().getHeapPolicy();
        long oldGenerationSize = heapPolicy.rememberedOldGenerationSize;
        if (oldGenerationSize < 0) {
            oldGenerationSize = HeapPolicyOptions.OldGenerationSizePercent.getValue();
            if (oldGenerationSize < 0) {
                oldGenerationSize = HeapPolicyOptions.OldGenerationSize.getValue();
            } else {
                oldGenerationSize *= PhysicalMemory.size().rawValue();
                oldGenerationSize /= 100L;
            }
            heapPolicy.rememberedOldGenerationSize = oldGenerationSize;
        }
        return WordFactory.unsigned(oldGenerationSize);
    }

    /** The FreeSpaceSize option as an Unsigned. */
    static UnsignedWord getFreeSpaceSize() {
        UnsignedWord result = WordFactory.unsigned(HeapPolicyOptions.FreeSpaceSize.getValue());
        if (result.equal(0)) {
            result = getYoungGenerationSize().add(getOldGenerationSize());
        }
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
    private static final Word producedHeapChunkZapValue = WordFactory.unsigned(0xbaadbeefbaadbeefL);

    /* - The value to use for zapping. */
    private static final Word consumedHeapChunkZapValue = WordFactory.unsigned(0xdeadbeefdeadbeefL);

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
                if (bytesAllocatedSinceLastCollection.get().aboveOrEqual(getYoungGenerationSize())) {
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
            return getYoungGenerationSize().subtract(WordFactory.unsigned(HeapPolicyOptions.UserRequestedGCThreshold.getValue()));
        }
    }
}
