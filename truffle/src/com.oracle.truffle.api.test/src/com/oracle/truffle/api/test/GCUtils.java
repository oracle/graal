/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.junit.Assert;

/**
 * Test collectible objects.
 */
public final class GCUtils {

    private GCUtils() {
        throw new IllegalStateException("No instance allowed.");
    }

    /**
     * Number of iterations of {@link System#gc()} calls after which the GC is eventually run.
     */
    public static final int GC_TEST_ITERATIONS = 15;

    /**
     * Calls the objectFactory {@link #GC_TEST_ITERATIONS} times and asserts that at least one
     * object provided by the factory is collected.
     *
     * @param objectFactory producer of collectible object per an iteration
     */
    public static void assertObjectsCollectible(Function<Integer, Object> objectFactory) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        List<WeakReference<Object>> collectibleObjects = new ArrayList<>();
        for (int i = 0; i < GC_TEST_ITERATIONS; i++) {
            collectibleObjects.add(new WeakReference<>(objectFactory.apply(i), queue));
            System.gc();
        }
        gc(IsFreed.anyOf(collectibleObjects), true);
        int refsCleared = 0;
        while (queue.poll() != null) {
            refsCleared++;
        }
        // we need to have any refs cleared for this test to have any value
        Assert.assertTrue(refsCleared > 0);
    }

    /**
     * Asserts that given reference is cleaned, the referent is freed by garbage collector.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by GC
     * @param ref the reference
     */
    public static void assertGc(final String message, final Reference<?> ref) {
        if (!gc(IsFreed.allOf(Collections.singleton(ref)), true)) {
            Assert.fail(message);
        }
    }

    /**
     * Asserts that given reference is not cleaned, the referent is freed by garbage collector.
     *
     * @param message the message for an {@link AssertionError} when referent is not freed by GC
     * @param ref the reference
     */
    public static void assertNotGc(final String message, final Reference<?> ref) {
        if (gc(IsFreed.allOf(Collections.singleton(ref)), false)) {
            Assert.fail(message);
        }
    }

    private static boolean gc(BooleanSupplier isFreed, boolean performAllocations) {
        int blockSize = 100_000;
        final List<byte[]> blocks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            if (isFreed.getAsBoolean()) {
                return true;
            }
            try {
                System.gc();
            } catch (OutOfMemoryError oom) {
            }
            try {
                System.runFinalization();
            } catch (OutOfMemoryError oom) {
            }
            if (performAllocations) {
                try {
                    blocks.add(new byte[blockSize]);
                    blockSize = (int) (blockSize * 1.3);
                } catch (OutOfMemoryError oom) {
                    blockSize >>>= 1;
                }
            }
            if (i % 10 == 0) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        return false;
    }

    private static final class IsFreed implements BooleanSupplier {

        private enum Operator {
            AND,
            OR
        }

        private final Collection<? extends Reference<?>> refs;
        private final Operator operator;

        private IsFreed(Collection<? extends Reference<?>> refs, Operator operator) {
            this.refs = refs;
            this.operator = operator;
        }

        @Override
        public boolean getAsBoolean() {
            for (Reference<?> ref : refs) {
                boolean freed = ref.get() == null;
                if (freed) {
                    if (operator == Operator.OR) {
                        return true;
                    }
                } else {
                    if (operator == Operator.AND) {
                        return false;
                    }
                }
            }
            return operator == Operator.AND;
        }

        static IsFreed anyOf(Collection<? extends Reference<?>> refs) {
            return new IsFreed(refs, Operator.OR);
        }

        static IsFreed allOf(Collection<? extends Reference<?>> refs) {
            return new IsFreed(refs, Operator.AND);
        }
    }
}
