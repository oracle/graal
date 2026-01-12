/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.junit.Test;

public class AtomicFieldUpdaterPETest extends PartialEvaluationTest {

    private static final AtomicIntegerFieldUpdater<ReceiverClass> INT_UPDATER = AtomicIntegerFieldUpdater.newUpdater(ReceiverClass.class, "intTest");
    private static final AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> REFERENCE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ReceiverClass.class, ValueClass.class, "referenceTest");
    private static final AtomicLongFieldUpdater<ReceiverClass> LONG_UPDATER = AtomicLongFieldUpdater.newUpdater(ReceiverClass.class, "longTest");

    static class ValueClass {
    }

    static class ReceiverClass {
        volatile int intTest;
        volatile long longTest;
        volatile ValueClass referenceTest;
    }

    @Test
    public void testConstantReceiver() {
        ReceiverClass c = new ReceiverClass();
        ValueClass v = new ValueClass();

        // int
        assertPartialEvalNoInvokes(() -> INT_UPDATER.get(c));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.getAndSet(c, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.getAndAdd(c, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.getAndDecrement(c));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.getAndUpdate(c, a -> a));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.incrementAndGet(c));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.decrementAndGet(c));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.addAndGet(c, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.updateAndGet(c, a -> a));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.accumulateAndGet(c, 0, (a, b) -> a + b));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.set(c, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.compareAndSet(c, 0, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.lazySet(c, 0));
        assertPartialEvalNoInvokes(() -> INT_UPDATER.weakCompareAndSet(c, 0, 0));

        // long
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.get(c));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.getAndSet(c, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.getAndAdd(c, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.getAndDecrement(c));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.getAndUpdate(c, a -> a));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.incrementAndGet(c));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.decrementAndGet(c));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.addAndGet(c, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.updateAndGet(c, a -> a));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.accumulateAndGet(c, 0L, (a, b) -> a + b));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.set(c, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.compareAndSet(c, 0L, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.lazySet(c, 0L));
        assertPartialEvalNoInvokes(() -> LONG_UPDATER.weakCompareAndSet(c, 0L, 0L));

        // reference
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.get(c));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.getAndSet(c, v));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.getAndUpdate(c, a -> a));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.updateAndGet(c, a -> a));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.set(c, v));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.compareAndSet(c, v, v));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.lazySet(c, v));
        assertPartialEvalNoInvokes(() -> REFERENCE_UPDATER.weakCompareAndSet(c, v, v));
    }

    @Test
    public void testDynamicReceiver() {
        ReceiverClass c = new ReceiverClass();
        ValueClass v = new ValueClass();

        // int
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.get(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.getAndSet(r, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.getAndAdd(r, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.getAndDecrement(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.getAndUpdate(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.incrementAndGet(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.decrementAndGet(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.addAndGet(r, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.updateAndGet(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.accumulateAndGet(r, 0, (a, b) -> a + b), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.set(r, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.compareAndSet(r, 0, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.lazySet(r, 0), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> INT_UPDATER.weakCompareAndSet(r, 0, 0), c);

        // long
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.get(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.getAndSet(r, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.getAndAdd(r, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.getAndDecrement(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.getAndUpdate(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.incrementAndGet(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.decrementAndGet(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.addAndGet(r, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.updateAndGet(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.accumulateAndGet(r, 0L, (a, b) -> a + b), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.set(r, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.compareAndSet(r, 0L, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.lazySet(r, 0L), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> LONG_UPDATER.weakCompareAndSet(r, 0L, 0L), c);

        // reference
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.get(r), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.getAndSet(r, v), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.getAndUpdate(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.updateAndGet(r, a -> a), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.set(r, v), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.compareAndSet(r, v, v), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.lazySet(r, v), c);
        assertPartialEvalNoInvokes((ReceiverClass r) -> REFERENCE_UPDATER.weakCompareAndSet(r, v, v), c);
    }

    @Test
    public void testDynamicUpdater() {
        ReceiverClass c = new ReceiverClass();
        ValueClass v = new ValueClass();

        // int
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.get(c)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.getAndSet(c, 0)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.getAndAdd(c, 0)), INT_UPDATER);

        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.getAndDecrement(c)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.getAndUpdate(c, a -> a)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.incrementAndGet(c)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.decrementAndGet(c)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.addAndGet(c, 0)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.updateAndGet(c, a -> a)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.accumulateAndGet(c, 0, (a, b) -> a + b)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.set(c, 0)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.compareAndSet(c, 0, 0)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.lazySet(c, 0)), INT_UPDATER);
        assertCompileBailout(((AtomicIntegerFieldUpdater<ReceiverClass> u) -> u.weakCompareAndSet(c, 0, 0)), INT_UPDATER);

        // long
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.get(c)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.getAndSet(c, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.getAndAdd(c, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.getAndDecrement(c)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.getAndUpdate(c, a -> a)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.incrementAndGet(c)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.decrementAndGet(c)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.addAndGet(c, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.updateAndGet(c, a -> a)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.accumulateAndGet(c, 0L, (a, b) -> a + b)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.set(c, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.compareAndSet(c, 0L, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.lazySet(c, 0L)), LONG_UPDATER);
        assertCompileBailout(((AtomicLongFieldUpdater<ReceiverClass> u) -> u.weakCompareAndSet(c, 0L, 0L)), LONG_UPDATER);

        // reference
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.get(c)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.getAndSet(c, v)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.getAndUpdate(c, a -> a)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.updateAndGet(c, a -> a)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.set(c, v)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.compareAndSet(c, v, v)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.lazySet(c, v)), REFERENCE_UPDATER);
        assertCompileBailout(((AtomicReferenceFieldUpdater<ReceiverClass, ValueClass> u) -> u.weakCompareAndSet(c, v, v)), REFERENCE_UPDATER);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testInvalidReceiver() {
        Object invalidReceiver = new Object();
        ValueClass v = new ValueClass();

        // int
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).get(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).getAndSet(invalidReceiver, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).getAndAdd(invalidReceiver, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).getAndDecrement(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).getAndUpdate(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).incrementAndGet(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).decrementAndGet(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).addAndGet(invalidReceiver, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).updateAndGet(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).accumulateAndGet(invalidReceiver, 0, (a, b) -> a + b)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).set(invalidReceiver, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).compareAndSet(invalidReceiver, 0, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).lazySet(invalidReceiver, 0)));
        assertCompileBailout((() -> ((AtomicIntegerFieldUpdater) INT_UPDATER).weakCompareAndSet(invalidReceiver, 0, 0)));

        // long
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).get(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).getAndSet(invalidReceiver, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).getAndAdd(invalidReceiver, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).getAndDecrement(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).getAndUpdate(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).incrementAndGet(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).decrementAndGet(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).addAndGet(invalidReceiver, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).updateAndGet(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).accumulateAndGet(invalidReceiver, 0L, (a, b) -> a + b)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).set(invalidReceiver, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).compareAndSet(invalidReceiver, 0L, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).lazySet(invalidReceiver, 0L)));
        assertCompileBailout((() -> ((AtomicLongFieldUpdater) LONG_UPDATER).weakCompareAndSet(invalidReceiver, 0L, 0L)));

        // reference
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).get(invalidReceiver)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).getAndSet(invalidReceiver, v)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).getAndUpdate(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).updateAndGet(invalidReceiver, a -> a)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).set(invalidReceiver, v)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).compareAndSet(invalidReceiver, v, v)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).lazySet(invalidReceiver, v)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).weakCompareAndSet(invalidReceiver, v, v)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void testInvalidValue() {
        ReceiverClass c = new ReceiverClass();
        Object invalidValue = new Object();

        // Note: invalid value is not possible for primitives

        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).getAndSet(c, invalidValue)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).set(c, invalidValue)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).lazySet(c, invalidValue)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).compareAndSet(c, invalidValue, invalidValue)));
        assertCompileBailout((() -> ((AtomicReferenceFieldUpdater) REFERENCE_UPDATER).weakCompareAndSet(c, invalidValue, invalidValue)));
    }

}
