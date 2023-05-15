/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.jfr.oldobject.OldObjectEffects;
import com.oracle.svm.core.jfr.oldobject.OldObjectEventEmitter;
import com.oracle.svm.core.jfr.oldobject.OldObjectList;
import com.oracle.svm.core.jfr.oldobject.OldObjectSampler;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class TestOldObjectProfiler {
    @Test
    public void testScavenge() {
        final int size = 10;
        final MutableBoolean isDead = new MutableBoolean(false);
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public boolean isDead(WeakReference<?> ref) {
                final int value = (int) getWeakReferent(ref);
                if (value < 10) {
                    return isDead.get();
                }

                return false;
            }
        };
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(i), (i + 1) * 10_000, -1);
        }

        // Set is-alive check for samples in first round to be false.
        // Scavenging should kick in when lower-span objects are sampled.
        isDead.set(true);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(10 + i), (i + 1) * 100, -1);
        }

        // Make sure that lower-span objects are inserted as a result of scavenging higher-span
        // objects,
        // and not as a result of checking that high-span objects are not alive at emit time.
        isDead.set(false);

        eventEmitter.emit(0);
        assertStreamEquals(IntStream.rangeClosed(10, 19).boxed(), effects.objects());
    }

    @Test
    public void testEvictUponFull() {
        final int size = 10;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(i), (i + 1) * 100, -1);
        }

        eventEmitter.emit(0);
        assertStreamEquals(IntStream.rangeClosed(0, 9).boxed(), effects.objects());

        // Clear accumulated samples and see what gets emitted now.
        effects.clearSamples();

        // Queue is full and objects to sample have bigger span than initial ones.
        // So sampling these new objects should result in evicting previously sampled ones.
        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(10 + i), (i + 1) * 10_000, -1);
        }

        eventEmitter.emit(0);
        assertStreamEquals(IntStream.rangeClosed(10, 19).boxed(), effects.objects());
    }

    @Test
    public void testEmitSkippingMiddle() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public boolean isDead(WeakReference<?> ref) {
                final Object value = super.getWeakReferent(ref);
                return "4".equals(value);
            }
        };
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 23L), LongStream.rangeClosed(25L, 27L)), effects.allocationTimes());
    }

    @Test
    public void testEmitSkippingYoungest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public boolean isDead(WeakReference<?> ref) {
                final Object value = super.getWeakReferent(ref);
                return "7".equals(value);
            }
        };
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 26L), effects.allocationTimes());
    }

    @Test
    public void testEmitSkippingOldest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public boolean isDead(WeakReference<?> ref) {
                final Object value = super.getWeakReferent(ref);
                return "0".equals(value);
            }
        };
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.rangeClosed(21L, 27L), effects.allocationTimes());
    }

    @Test
    public void testEmitSkippingAll() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
            public boolean isDead(WeakReference<?> ref) {
                return true;
            }
        };
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        Assert.assertEquals(0, effects.sizeSamples());
    }

    @Test
    public void testSampleManyEmitQueueSize() {
        final int size = 256;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < 1_000_000; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i, -1);
        }

        eventEmitter.emit(0);
        Assert.assertEquals(256, effects.sizeSamples());
    }

    @Test
    public void testSampleOverflowEvictYoungest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict youngest because that's the one with the lowest span.
            final int allocatedSize = i == (size - 1) ? 100 : 200;
            sampler.sample(new WeakReference<>(String.valueOf(i)), allocatedSize, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 26L), LongStream.rangeClosed(28L, 28L)), effects.allocationTimes());
    }

    @Test
    public void testSampleOverflowEvictMiddle() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict middle because that's the one with the lowest span.
            final int allocatedSize = i == (size / 2) ? 100 : 200;
            sampler.sample(new WeakReference<>(String.valueOf(i)), allocatedSize, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 23L), LongStream.rangeClosed(25L, 28L)), effects.allocationTimes());
    }

    @Test
    public void testSampleOverflowEvictOldest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict oldest because that's the one with the lowest span.
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.rangeClosed(21L, 28L), effects.allocationTimes());
    }

    @Test
    public void testSampleFullEmit() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 27L), effects.allocationTimes());
    }

    @Test
    public void testSampleNotFullEmit() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        for (int i = 0; i < size - 1; i++) {
            sampler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        eventEmitter.emit(0);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 26L), effects.allocationTimes());
    }

    @Test
    public void testSingleSample() {
        final int size = 3;
        final TestEffects effects = new TestEffects(20L, 3);
        final OldObjectList list = new OldObjectList();
        final OldObjectSampler sampler = new OldObjectSampler(size, list, effects);
        final OldObjectEventEmitter eventEmitter = new OldObjectEventEmitter(list, effects);

        sampler.sample(new WeakReference<>("a-sample"), 10, -1);
        eventEmitter.emit(0);
        final TestSample testSample = effects.peekLastSample();
        Assert.assertNotNull(testSample);
        Assert.assertEquals("a-sample", testSample.obj);
        Assert.assertEquals(21, testSample.timestamp);
        Assert.assertEquals(10, testSample.objectSize);
        Assert.assertEquals(20, testSample.allocationTime);
        Assert.assertEquals(50, testSample.threadId);
        Assert.assertEquals(40, testSample.stackTraceId);
        Assert.assertEquals(60, testSample.heapUsedAtLastGC);
        Assert.assertEquals(-1, testSample.arrayLength);
        Assert.assertEquals(1, effects.sizeSamples());
    }

    static void assertLongStreamEquals(LongStream expected, Stream<?> actual) {
        assertStreamEquals(expected.boxed(), actual);
    }

    static void assertStreamEquals(Stream<?> expected, Stream<?> actual) {
        Assert.assertEquals(expected.toList(), actual.toList());
    }

    private static class TestEffects implements OldObjectEffects {
        private final TestSample[] testSamples;
        private int head = 0;
        private int tail = 0;
        private long ticks;

        TestEffects(long initialTicks, int size) {
            this.ticks = initialTicks;
            this.testSamples = new TestSample[size];
            for (int i = 0; i < size; i++) {
                this.testSamples[i] = new TestSample();
            }
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long elapsedTicks() {
            return ticks++;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public Object getWeakReferent(WeakReference<?> ref) {
            try {
                return ReferenceInternals.getReferent(ref);
            } catch (ClassCastException e) {
                // A class cast occurs when running this test as a plain Java unit test.
                // Fallback to a mechanism that works in that environment.
                return getWeakReferent0(ref);
            }
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean isDead(WeakReference<?> ref) {
            try {
                return ReferenceInternals.refersTo(ref, null);
            } catch (NullPointerException e) {
                // A NPE occurs when running this test as a plain Java unit test.
                // Fallback to a mechanism that works in that environment.
                return getWeakReferent0(ref) == null;
            }
        }

        @Uninterruptible(reason = "A fallback for when this test is run in JVM mode", calleeMustBe = false)
        private static Object getWeakReferent0(WeakReference<?> ref) {
            return ref.get();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void emit(Object aliveObject, long timestamp, long allocatedSize, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            testSamples[tail++].set(aliveObject, timestamp, allocatedSize, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getStackTraceId() {
            return 40;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getThreadId(Thread thread) {
            return 50;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public long getHeapUsedAtLastGC() {
            return 60;
        }

        TestSample peekLastSample() {
            return testSamples[head];
        }

        int sizeSamples() {
            return tail - head;
        }

        Stream<Long> allocationTimes() {
            return Arrays.stream(testSamples).filter(s -> s.allocationTime != 0).map(TestSample::getAllocationTime);
        }

        Stream<Object> objects() {
            return Arrays.stream(testSamples).filter(s -> s.allocationTime != 0).map(TestSample::getObject);
        }

        public void clearSamples() {
            head = 0;
            tail = 0;
            for (TestSample testSample : testSamples) {
                testSample.set(null, 0, 0, 0, 0, 0, 0, 0);
            }
        }
    }

    private static final class TestSample {
        Object obj;
        long timestamp;
        long objectSize;
        long allocationTime;
        long threadId;
        long stackTraceId;
        long heapUsedAtLastGC;
        int arrayLength;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void set(Object obj, long timestamp, long allocatedSize, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            this.obj = obj;
            this.timestamp = timestamp;
            this.objectSize = allocatedSize;
            this.allocationTime = allocationTime;
            this.threadId = threadId;
            this.stackTraceId = stackTraceId;
            this.heapUsedAtLastGC = heapUsedAtLastGC;
            this.arrayLength = arrayLength;
        }

        long getAllocationTime() {
            return allocationTime;
        }

        Object getObject() {
            return obj;
        }
    }

    private static final class MutableBoolean {
        private boolean value;

        MutableBoolean(boolean value) {
            this.value = value;
        }

        void set(boolean newValue) {
            value = newValue;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        boolean get() {
            return value;
        }
    }
}
