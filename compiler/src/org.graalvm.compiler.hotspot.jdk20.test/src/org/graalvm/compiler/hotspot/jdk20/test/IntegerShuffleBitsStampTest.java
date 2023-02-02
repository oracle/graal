/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk20.test;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.calc.CompressBitsNode;
import org.graalvm.compiler.nodes.calc.ExpandBitsNode;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

public class IntegerShuffleBitsStampTest extends GraalTest {

    private int[] inputs = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0xCAFEBABE, 0xFF00FFF0, 0x0000CABAB};

    static final long INT_MASK = CodeUtil.mask(32);

    @Test
    public void testICompress() {
        for (int value0 : inputs) {
            for (int value1 : inputs) {
                IntegerStamp valueStamp = (IntegerStamp) StampFactory.forInteger(JavaKind.Int, value0, value0, value0 & INT_MASK, value0 & INT_MASK).meet(
                                StampFactory.forInteger(JavaKind.Int, value1, value1, value1 & INT_MASK, value1 & INT_MASK));
                for (int mask0 : inputs) {
                    for (int mask1 : inputs) {
                        IntegerStamp maskStamp = (IntegerStamp) StampFactory.forInteger(JavaKind.Int, mask0, mask0, mask0 & INT_MASK, mask0 & INT_MASK).meet(
                                        StampFactory.forInteger(JavaKind.Int, mask1, mask1, mask1 & INT_MASK, mask1 & INT_MASK));
                        IntegerStamp newStamp = (IntegerStamp) CompressBitsNode.computeStamp(valueStamp, maskStamp);
                        assertTrue(newStamp.contains(Integer.compress(value0, mask0)));
                        assertTrue(newStamp.contains(Integer.compress(value0, mask1)));
                        assertTrue(newStamp.contains(Integer.compress(value1, mask0)));
                        assertTrue(newStamp.contains(Integer.compress(value1, mask1)));
                    }
                }
            }
        }
    }

    @Test
    public void testIExpand() {
        for (int value0 : inputs) {
            for (int value1 : inputs) {
                for (int mask0 : inputs) {
                    for (int mask1 : inputs) {
                        IntegerStamp maskStamp = (IntegerStamp) StampFactory.forInteger(JavaKind.Int, mask0, mask0, mask0 & INT_MASK, mask0 & INT_MASK).meet(
                                        StampFactory.forInteger(JavaKind.Int, mask1, mask1, mask1 & INT_MASK, mask1 & INT_MASK));
                        IntegerStamp newStamp = (IntegerStamp) ExpandBitsNode.computeStamp(maskStamp);
                        assertTrue(newStamp.contains(Integer.expand(value0, mask0)));
                        assertTrue(newStamp.contains(Integer.expand(value0, mask1)));
                        assertTrue(newStamp.contains(Integer.expand(value1, mask0)));
                        assertTrue(newStamp.contains(Integer.expand(value1, mask1)));
                    }
                }
            }
        }
    }
}
