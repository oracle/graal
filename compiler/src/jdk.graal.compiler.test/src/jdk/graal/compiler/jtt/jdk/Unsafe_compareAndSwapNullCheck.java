/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.jtt.jdk;

import org.junit.Test;

import jdk.graal.compiler.jtt.JTTTest;
import jdk.graal.compiler.phases.OptimisticOptimizations;

public class Unsafe_compareAndSwapNullCheck extends JTTTest {

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        // Disable profile based optimizations
        return OptimisticOptimizations.NONE;
    }

    static final long valueOffset;
    static {
        try {
            valueOffset = UNSAFE.objectFieldOffset(Unsafe_compareAndSwapNullCheck.class.getDeclaredField("value"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    long value;
    long lng;

    public static void test(Unsafe_compareAndSwapNullCheck u, long expected, long newValue) {
        @SuppressWarnings("unused")
        long l = u.lng;
        UNSAFE.compareAndSetLong(u, valueOffset, expected, newValue);
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", null, 1L, 2L);
    }
}
