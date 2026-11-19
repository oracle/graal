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

import java.util.function.BooleanSupplier;

import org.junit.Assert;

import com.oracle.svm.shared.NeverInline;

/**
 * Helpers shared by the garbage collector tests ({@link GCReferenceTest}, {@link GCObjectGraphTest}
 * and {@link GCAtomicReferenceTest}). They only use standard Java to provoke collections, so they
 * work with any garbage collector.
 */
final class GCTestUtils {

    /** How long a test is willing to wait for the GC to do its work before failing. */
    static final long TIMEOUT_MS = 30_000;

    /** How much garbage {@link #allocateGarbage()} allocates, to create memory pressure. */
    private static final int GARBAGE_CHUNK = 32 * 1024;

    private GCTestUtils() {
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
    static void awaitGC(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        int attempts = 0;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            attempts++;
            System.gc();
            allocateGarbage();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for GC", e);
            }
        }
        Assert.fail(message + " (still not satisfied after " + attempts + " GC attempts in " + TIMEOUT_MS + "ms)");
    }

    /**
     * Allocates a short-lived array to create memory pressure. The caller does not need the array.
     * The method is never inlined and returns the array, so the compiler cannot remove the
     * allocation.
     */
    @NeverInline("Prevent escape analysis from removing the allocation.")
    static byte[] allocateGarbage() {
        return new byte[GARBAGE_CHUNK];
    }

    /** Allocates {@code chunks} short-lived arrays, see {@link #allocateGarbage()}. */
    static void allocateGarbage(int chunks) {
        for (int i = 0; i < chunks; i++) {
            allocateGarbage();
        }
    }
}
