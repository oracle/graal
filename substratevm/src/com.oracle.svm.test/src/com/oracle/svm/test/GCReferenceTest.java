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
package com.oracle.svm.test;

import java.lang.ref.Cleaner;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests that the garbage collector handles {@link java.lang.ref} reference objects correctly.
 *
 * In short: an object that nobody uses any more must be collected, and the reference objects that
 * were watching it must be updated (cleared) and reported (enqueued). An object that is still in
 * use must never be collected.
 *
 * These tests work with any garbage collector. They only use standard Java to trigger collections
 * ({@link System#gc()} plus allocating garbage), never GC-specific flags or internal APIs. Because
 * no collector promises to collect an object at an exact moment, tests that need a collection call
 * {@link #awaitGC}, which keeps retrying until a deadline instead of assuming that one
 * {@link System#gc()} call is enough. That also keeps the tests correct for concurrent collectors,
 * which finish their work in the background.
 *
 * Object finalization is deliberately not tested here, because Native Image does not run finalizers.
 */
public class GCReferenceTest {

    /** How long a test is willing to wait for the GC to do its work before failing. */
    private static final long TIMEOUT_MS = 30_000;

    /** How much garbage to allocate between collection attempts, to create memory pressure. */
    private static final int GARBAGE_CHUNK = 64 * 1024;

    /** A simple test object. The id only exists to make failure messages readable. */
    private static final class Payload {
        private final int id;

        Payload(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Payload(" + id + ")";
        }
    }

    /**
     * Keeps asking for a garbage collection until {@code condition} becomes true, then returns. If
     * the condition is still false when the timeout expires, the test fails.
     *
     * Why the loop is necessary: a single {@link System#gc()} is only a hint. Some collectors need
     * allocation to happen before they start a cycle, and a concurrent collector does its work on
     * background threads, so the result may only be visible a little later. Allocating garbage and
     * sleeping briefly on each attempt covers both cases.
     */
    private static void awaitGC(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        int attempts = 0;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            attempts++;
            System.gc();
            consume(new byte[GARBAGE_CHUNK]);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for GC", e);
            }
        }
        Assert.fail(message + " (still not satisfied after " + attempts + " GC attempts in " + TIMEOUT_MS + "ms)");
    }

    /** Written to a volatile field so the compiler cannot delete the allocation above. */
    private static volatile Object sink;

    private static void consume(Object o) {
        sink = o;
        sink = null;
    }

    /*
     * The two helpers below create the object being watched inside their own method. That matters:
     * when the helper returns, the only strong reference to the object is gone, so the object really
     * is unreachable. If the object were created in the test method instead, the test method's own
     * local variable could keep it alive and the test would pass for the wrong reason.
     */

    private static WeakReference<Payload> newWeak(ReferenceQueue<Payload> queue) {
        Payload referent = new Payload(1);
        return new WeakReference<>(referent, queue);
    }

    private static PhantomReference<Payload> newPhantom(ReferenceQueue<Payload> queue) {
        Payload referent = new Payload(2);
        return new PhantomReference<>(referent, queue);
    }

    /**
     * Checks the most basic weak-reference promise: once the only way to reach an object is through a
     * {@link WeakReference}, the GC is allowed to collect it, and afterwards the reference must read
     * as empty ({@code get()} returns null).
     */
    @Test
    public void testWeakReferenceIsCleared() {
        WeakReference<Payload> ref = newWeak(null);
        awaitGC(() -> ref.refersTo(null), "weak reference was not cleared");
        Assert.assertNull("get() must return null once the reference is cleared", ref.get());
    }

    /**
     * Checks the notification half of weak references: if the reference was registered with a
     * {@link ReferenceQueue}, the GC must not only clear it but also put it on that queue, so the
     * program can find out that the object is gone.
     */
    @Test
    public void testWeakReferenceIsEnqueued() throws InterruptedException {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        WeakReference<Payload> ref = newWeak(queue);
        awaitGC(() -> ref.refersTo(null), "weak reference was not cleared");

        Reference<? extends Payload> dequeued = queue.remove(TIMEOUT_MS);
        Assert.assertSame("the cleared reference must be enqueued", ref, dequeued);
    }

    /**
     * Same idea as the weak-reference queue test, but for {@link PhantomReference}, which the GC
     * handles in a later phase. Note that {@code get()} on a phantom reference always returns null
     * by design, so whether the object is still reachable is checked with
     * {@link Reference#refersTo} instead.
     */
    @Test
    public void testPhantomReferenceIsEnqueued() throws InterruptedException {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        PhantomReference<Payload> ref = newPhantom(queue);
        Assert.assertNull("PhantomReference.get() must always return null", ref.get());

        awaitGC(() -> ref.refersTo(null), "phantom reference was not cleared");
        Reference<? extends Payload> dequeued = queue.remove(TIMEOUT_MS);
        Assert.assertSame("the phantom reference must be enqueued", ref, dequeued);
    }

    /**
     * The opposite direction, and the more dangerous one to get wrong: an object that the program is
     * still holding on to must survive, no matter how many collections happen. If a GC wrongly
     * decided this object was garbage, the program would later read a dangling reference.
     *
     * The test runs many collections with allocation in between and then checks that neither the
     * weak nor the soft reference to the still-used object was cleared.
     */
    @Test
    public void testStronglyReachableReferentIsNotCleared() {
        Payload strong = new Payload(3);
        WeakReference<Payload> weak = new WeakReference<>(strong);
        SoftReference<Payload> soft = new SoftReference<>(strong);

        for (int i = 0; i < 20; i++) {
            System.gc();
            consume(new byte[GARBAGE_CHUNK]);
        }

        Assert.assertFalse("a strongly reachable weak referent must not be cleared", weak.refersTo(null));
        Assert.assertSame("weak.get() must still return the referent", strong, weak.get());
        Assert.assertFalse("a strongly reachable soft referent must not be cleared", soft.refersTo(null));
        Assert.assertSame("soft.get() must still return the referent", strong, soft.get());

        /* Using 'strong' here guarantees it stayed reachable for the whole test. */
        Assert.assertEquals("Payload(3)", strong.toString());
    }

    /**
     * A sanity check on the queue itself: a queue that the GC has not put anything into must stay
     * empty. {@code poll()} returns null immediately and {@code remove(timeout)} times out.
     */
    @Test
    public void testEmptyReferenceQueuePollsNull() throws InterruptedException {
        ReferenceQueue<Payload> queue = new ReferenceQueue<>();
        Assert.assertNull("poll() on an empty queue must return null", queue.poll());
        Assert.assertNull("remove(timeout) on an empty queue must time out", queue.remove(50));
    }

    /**
     * Checks {@link Cleaner}, the modern replacement for finalizers: a cleanup action registered for
     * an object must actually run once that object becomes unreachable. This goes through the same
     * GC reference-processing machinery as the tests above, but via the API that real code uses to
     * release resources.
     */
    @Test
    public void testCleanerRunsAfterObjectBecomesUnreachable() throws InterruptedException {
        Cleaner cleaner = Cleaner.create();
        CountDownLatch cleaned = new CountDownLatch(1);
        registerCleanable(cleaner, cleaned);

        awaitGC(() -> cleaned.getCount() == 0, "cleaner action did not run");
        Assert.assertTrue("cleaner action must have run", cleaned.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    /**
     * Registers the cleanup action. The action counts down a latch and deliberately does not
     * mention the watched object: a cleanup action that referred to its own object would keep that
     * object alive forever and the cleanup would never run.
     */
    private static void registerCleanable(Cleaner cleaner, CountDownLatch cleaned) {
        Payload referent = new Payload(4);
        cleaner.register(referent, cleaned::countDown);
    }

    /**
     * Scales the first test up from one reference to a thousand. A GC that only processed some of the
     * references it found would pass the single-reference test but fail here, so this checks that
     * every single one is cleared.
     */
    @Test
    public void testManyWeakReferencesAreAllCleared() {
        final int count = 1000;
        List<WeakReference<Payload>> refs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            refs.add(newWeak(null));
        }

        awaitGC(() -> refs.stream().allMatch(r -> r.refersTo(null)), "not all weak references were cleared");

        long remaining = refs.stream().filter(r -> !r.refersTo(null)).count();
        Assert.assertEquals("all weak references must be cleared", 0, remaining);
    }
}
