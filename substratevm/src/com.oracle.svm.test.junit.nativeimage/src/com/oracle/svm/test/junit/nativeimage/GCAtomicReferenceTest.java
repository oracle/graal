/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.junit.nativeimage;

import static com.oracle.svm.test.junit.nativeimage.GCTestUtils.TIMEOUT_MS;
import static com.oracle.svm.test.junit.nativeimage.GCTestUtils.allocateGarbage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;

/**
 * Tests atomic operations on reference fields and array elements across garbage collections.
 *
 * Ordinary reference stores and atomic ones (compare-and-set, get-and-set) are handled by different
 * code in the VM: an atomic operation has to both perform the hardware atomic instruction and do the
 * GC bookkeeping, without breaking either. That combination is easy to get subtly wrong, and when a
 * collector moves objects at the same time it gets harder still, because the value sitting in the
 * field may be the old location of an object that has just been moved. A buggy implementation can
 * then make a compare-and-set fail when it should have succeeded, or leave a stale reference behind.
 *
 * The tests use allocation and {@link System#gc()} to request collections without collector-specific
 * controls.
 *
 * The single-threaded tests that expect compare-and-set to succeed request collections between
 * reading and using the expected reference. This gives the collector a chance to move the referent.
 * These tests use {@link #escape} to keep the atomic operation targets from being optimized away.
 */
public class GCAtomicReferenceTest {

    /**
     * Repeatedly replaces the contents of an {@link AtomicReference} with
     * {@code compareAndSet}, allocating and collecting along the way.
     *
     * Every step must succeed, because the test is single-threaded and always passes the value it
     * just read as the expected value. A failed compare-and-set here would therefore mean the VM
     * reported a mismatch that cannot exist in the program itself, which is exactly the symptom of a
     * GC that moved the referenced object without the atomic operation accounting for it.
     */
    @Test
    public void testCompareAndSetAlwaysSucceedsWhileCollecting() {
        final int iterations = 20_000;
        AtomicReference<Value> ref = escape(new AtomicReference<>(new Value(0)));

        for (int i = 1; i <= iterations; i++) {
            Value expected = ref.get();

            if (i % 100 == 0) {
                /* Collect after reading the expected value and before using it. */
                allocateGarbage();
                System.gc();
            }

            Value updated = new Value(i);
            boolean ok = ref.compareAndSet(expected, updated);
            Assert.assertTrue("compareAndSet failed at iteration " + i + " although the value had not changed", ok);
        }

        Assert.assertEquals("the reference does not hold the last value written", iterations, ref.get().id);
    }

    /**
     * Checks {@code compareAndSet} in the case where it is supposed to fail: the expected value does
     * not match what the field holds. The operation must return false and must leave the field
     * untouched.
     *
     * This is the mirror image of the previous test. Together they show that the implementation
     * neither invents failures nor accepts mismatches.
     */
    @Test
    public void testCompareAndSetFailsOnMismatchAndLeavesValueIntact() {
        Value actual = new Value(1);
        Value wrongExpectation = new Value(2);
        Value replacement = new Value(3);
        AtomicReference<Value> ref = escape(new AtomicReference<>(actual));

        allocateGarbage();
        System.gc();

        boolean ok = ref.compareAndSet(wrongExpectation, replacement);
        Assert.assertFalse("compareAndSet must fail when the expected value does not match", ok);
        Assert.assertSame("a failed compareAndSet must not modify the reference", actual, ref.get());
    }

    /**
     * Exercises {@code getAndSet}, which unconditionally installs a new value and returns the old
     * one. Because the test knows the exact sequence of values, the returned value must always be the
     * one from the previous step; anything else means a reference was lost or duplicated.
     */
    @Test
    public void testGetAndSetReturnsPreviousValueWhileCollecting() {
        final int iterations = 20_000;
        AtomicReference<Value> ref = escape(new AtomicReference<>(new Value(0)));

        for (int i = 1; i <= iterations; i++) {
            Value previous = ref.getAndSet(new Value(i));
            Assert.assertEquals("getAndSet returned the wrong previous value at iteration " + i, i - 1, previous.id);

            if (i % 100 == 0) {
                allocateGarbage();
                System.gc();
            }
        }

        Assert.assertEquals("the reference does not hold the last value written", iterations, ref.get().id);
    }

    /**
     * Repeats the compare-and-set test through a {@link VarHandle} on a normal object field, rather
     * than through {@link AtomicReference}. This reaches the same VM machinery by a different route,
     * which is worth covering separately because the two are compiled differently.
     */
    @Test
    public void testVarHandleCompareAndSetOnFieldWhileCollecting() throws ReflectiveOperationException {
        VarHandle handle = MethodHandles.privateLookupIn(Holder.class, MethodHandles.lookup())
                        .findVarHandle(Holder.class, "field", Value.class);

        final int iterations = 10_000;
        Holder holder = escape(new Holder());
        handle.set(holder, new Value(0));

        for (int i = 1; i <= iterations; i++) {
            Value expected = (Value) handle.get(holder);

            if (i % 100 == 0) {
                /* Collect after reading the expected value and before using it. */
                allocateGarbage();
                System.gc();
            }

            boolean ok = handle.compareAndSet(holder, expected, new Value(i));
            Assert.assertTrue("VarHandle compareAndSet failed at iteration " + i, ok);
        }

        Value finalValue = (Value) handle.get(holder);
        Assert.assertEquals("the field does not hold the last value written", iterations, finalValue.id);
    }

    /**
     * Same operations again, but on the many slots of an {@link AtomicReferenceArray}. Array elements
     * are addressed differently from object fields, so this covers an additional code path, and using
     * many slots increases the chance of catching a collector that mishandles one of them.
     *
     * In each round, the expected values of all slots are read first, then a collection is requested,
     * and only then the slots are updated.
     */
    @Test
    public void testAtomicReferenceArraySlotsStayConsistent() {
        final int slots = 512;
        final int rounds = 40;
        AtomicReferenceArray<Value> array = escape(new AtomicReferenceArray<>(slots));
        for (int i = 0; i < slots; i++) {
            array.set(i, new Value(i));
        }

        Value[] expected = new Value[slots];
        for (int round = 1; round <= rounds; round++) {
            for (int i = 0; i < slots; i++) {
                expected[i] = array.get(i);
            }

            /* Collect after reading the expected values and before using them. */
            allocateGarbage();
            System.gc();

            for (int i = 0; i < slots; i++) {
                boolean ok = array.compareAndSet(i, expected[i], new Value(round * slots + i));
                Assert.assertTrue("compareAndSet failed for slot " + i + " in round " + round, ok);
            }
        }

        int base = rounds * slots;
        for (int i = 0; i < slots; i++) {
            Assert.assertEquals("slot " + i + " does not hold the last value written", base + i, array.get(i).id);
        }
    }

    /**
     * The multi-threaded version: several threads hammer the same {@link AtomicReference} with
     * compare-and-set while collections happen.
     *
     * With real concurrency an individual compare-and-set is allowed to fail, because another thread
     * may have won the race, so the test does not check individual attempts. Instead each thread
     * counts its own successful updates, and at the end the total must match the counter stored in
     * the reference, which every successful update increments by one. If the VM ever let two threads
     * believe they both won, or lost an update, the two numbers would disagree.
     *
     * The main thread waits for every worker to reach the start latch before requesting collections.
     * Workers can keep attempting updates until the main thread finishes requesting collections and
     * signals them to stop.
     */
    @Test
    public void testConcurrentCompareAndSetDoesNotLoseUpdates() throws Throwable {
        final int threads = 4;
        final int collections = 50;
        AtomicReference<Value> ref = new AtomicReference<>(new Value(0));
        AtomicBoolean stop = new AtomicBoolean();
        CountDownLatch running = new CountDownLatch(threads);
        long[] successes = new long[threads];
        Stressor.Worker[] workers = new Stressor.Worker[threads];

        for (int t = 0; t < threads; t++) {
            int slot = t;
            workers[t] = new Stressor.Worker(() -> {
                running.countDown();
                long count = 0;
                while (!stop.get()) {
                    Value expected = ref.get();
                    if (ref.compareAndSet(expected, new Value(expected.id + 1))) {
                        count++;
                    }
                }
                successes[slot] = count;
            });
            workers[t].setDaemon(true);
        }

        try {
            Stressor.startAll(workers);
            Assert.assertTrue("worker threads did not start in time", running.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            /* Keep the collector busy while the threads are competing. */
            for (int i = 0; i < collections; i++) {
                allocateGarbage();
                System.gc();
                Thread.sleep(5);
            }
        } finally {
            stop.set(true);
            Stressor.joinAll(workers, TIMEOUT_MS);
        }
        Stressor.checkWorkers(workers);

        long total = 0;
        for (long count : successes) {
            total += count;
        }

        Assert.assertTrue("the worker threads did not update the reference", total > 0);
        Assert.assertEquals("updates were lost or double-counted", total, ref.get().id);
    }

    /** A value object that remembers which iteration created it. */
    private static final class Value {
        private final int id;

        Value(int id) {
            this.id = id;
        }
    }

    /** Target of the {@link VarHandle} test above. */
    private static final class Holder {
        @SuppressWarnings("unused") private volatile Value field;
    }

    /**
     * Makes {@code target} escape from the test method. Without this, the compiler could replace an
     * object that never leaves the method by plain loads and stores, and the atomic operations that
     * the test is about would never be executed.
     */
    private static <T> T escape(T target) {
        GraalDirectives.blackhole(target);
        return target;
    }
}
