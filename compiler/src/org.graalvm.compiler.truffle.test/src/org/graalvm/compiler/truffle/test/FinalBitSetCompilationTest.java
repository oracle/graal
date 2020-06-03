/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.utilities.FinalBitSet;

public class FinalBitSetCompilationTest extends PartialEvaluationTest {

    @Test
    public void testEmpty() {
        FinalBitSet set = FinalBitSet.valueOf(new long[]{});
        assertConstant(false, (f) -> set.get(42));
        assertConstant(false, (f) -> set.get(0));
        assertConstant(0, (f) -> set.size());
        assertConstant(0, (f) -> set.length());
        assertConstant(0, (f) -> set.cardinality());
        assertConstant(true, (f) -> set.isEmpty());
    }

    @Test
    public void testSingle() {
        FinalBitSet set = FinalBitSet.valueOf(new long[]{0x8000_0000_0000_0001L});
        assertConstant(true, (f) -> set.get(0));
        assertConstant(false, (f) -> set.get(1));
        assertConstant(true, (f) -> set.get(63));
        assertConstant(false, (f) -> set.get(64));
        assertConstant(64, (f) -> set.size());
        assertConstant(64, (f) -> set.length());
        assertConstant(2, (f) -> set.cardinality());
        assertConstant(false, (f) -> set.isEmpty());
    }

    @Test
    public void testMultiple() {
        int[] testSizes = new int[]{8, 128};

        for (int size : testSizes) {
            long[] array = new long[size];
            Arrays.fill(array, 0x8000_0000_0000_0001L);
            final FinalBitSet set = FinalBitSet.valueOf(array);

            for (int i = 0; i < size; i++) {
                final int index = i;
                int base = index * 64;
                assertConstant(true, (f) -> set.get(base));
                assertConstant(true, (f) -> set.get(base + 63));
                assertConstant(false, (f) -> set.get(base + 1));
            }
        }
    }

    private void assertConstant(Object expectedConstant, TestFunction test) {
        assertPartialEvalEquals(new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return expectedConstant;
            }
        }, new RootNode(null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return test.execute(frame);
            }
        }, new Object[0]);
    }

    @FunctionalInterface
    interface TestFunction {
        Object execute(VirtualFrame frame);
    }

}
