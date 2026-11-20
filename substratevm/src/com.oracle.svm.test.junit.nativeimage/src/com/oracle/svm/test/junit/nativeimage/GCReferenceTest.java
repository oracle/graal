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
import static com.oracle.svm.test.junit.nativeimage.GCTestUtils.awaitGC;

import java.lang.ref.Cleaner;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.shared.NeverInline;

/**
 * Tests that the garbage collector handles {@link java.lang.ref} reference objects correctly.
 *
 * When the collector determines that an object is weakly or phantom reachable, it clears the
 * corresponding references. It also enqueues references registered with a queue, possibly later.
 * Weak and soft references to a strongly reachable object must stay intact.
 *
 * These tests work with any garbage collector that reclaims memory. They only use standard Java to
 * trigger collections ({@link System#gc()} plus allocating garbage), never GC-specific flags or
 * internal APIs. Because no collector promises to collect an object at an exact moment, tests that
 * need a collection call {@link GCTestUtils#awaitGC}, which keeps retrying until a deadline instead
 * of assuming that one {@link System#gc()} call is enough. That also keeps the tests correct for
 * concurrent collectors, which finish their work in the background. With the Epsilon GC, which
 * never collects, {@link GCTestUtils#awaitGC} skips the test.
 *
 * Object finalization is deliberately not tested here, because Native Image does not run finalizers.
 */
public class GCReferenceTest {

    /**
     * Checks the most basic weak-reference promise: once the only way to reach an object is through a
     * {@link WeakReference}, the GC is allowed to collect it. After the collector clears the
     * reference, {@code get()} must return {@code null}.
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
     * Checks clearing and enqueueing for a {@link PhantomReference}. The test uses
     * {@code refersTo(null)} to detect clearing because {@link PhantomReference#get} always
     * returns {@code null}.
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
            allocateGarbage();
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
    public void testCleanerRunsAfterObjectBecomesUnreachable() {
        Cleaner cleaner = Cleaner.create();
        CountDownLatch cleaned = new CountDownLatch(1);
        registerCleanable(cleaner, cleaned);

        awaitGC(() -> cleaned.getCount() == 0, "cleaner action did not run");
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
    }

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

    /*
     * The helpers below create the object being watched inside their own method. That matters:
     * when the helper returns, the only strong reference to the object is gone. If the object were
     * created in the test method instead, the test method's own local variable could keep it alive
     * and cause the test to time out.
     */
    @NeverInline("Keep the referent out of the caller's frame.")
    private static WeakReference<Payload> newWeak(ReferenceQueue<Payload> queue) {
        Payload referent = new Payload(1);
        return new WeakReference<>(referent, queue);
    }

    @NeverInline("Keep the referent out of the caller's frame.")
    private static PhantomReference<Payload> newPhantom(ReferenceQueue<Payload> queue) {
        Payload referent = new Payload(2);
        return new PhantomReference<>(referent, queue);
    }

    /**
     * Registers the cleanup action. The action counts down a latch and deliberately does not
     * mention the watched object: a cleanup action that referred to its own object would keep that
     * object alive forever and the cleanup would never run.
     */
    @NeverInline("Keep the referent out of the caller's frame.")
    private static void registerCleanable(Cleaner cleaner, CountDownLatch cleaned) {
        Payload referent = new Payload(4);
        cleaner.register(referent, cleaned::countDown);
    }
}
