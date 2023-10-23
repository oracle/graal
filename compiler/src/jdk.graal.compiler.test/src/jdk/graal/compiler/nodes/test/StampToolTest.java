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
package jdk.graal.compiler.nodes.test;

import static jdk.graal.compiler.nodes.type.StampTool.stampForTrailingZeros;
import static jdk.graal.compiler.test.GraalTest.assertFalse;
import static jdk.graal.compiler.test.GraalTest.assertTrue;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.test.GraphTest;
import jdk.graal.compiler.nodes.type.StampTool;
import org.junit.Test;

public class StampToolTest extends GraphTest {

    @Test
    public void testStampForTrailingZeros() {
        assertIntegerStampEquals(stampForTrailingZeros(forInt(0)), 32);
        assertIntegerStampEquals(stampForTrailingZeros(forInt(1)), 0);
        assertIntegerStampEquals(stampForTrailingZeros(forInt(-1)), 0);
        assertIntegerStampEquals(stampForTrailingZeros(forInt(Integer.MIN_VALUE)), 31);
        assertIntegerStampEquals(stampForTrailingZeros(forInt(Integer.MAX_VALUE)), 0);
    }

    private static IntegerStamp forInt(int value) {
        return IntegerStamp.create(32, value, value);
    }

    private static void assertIntegerStampEquals(Stamp stamp, int value) {
        assertTrue(stamp instanceof IntegerStamp);
        IntegerStamp iStamp = (IntegerStamp) stamp;
        assertTrue(iStamp.lowerBound() == value);
        assertTrue(iStamp.upperBound() == value);
    }

    @Test
    public void testStampToolUnsignedCompare() {
        Stamp stamp1 = stampForTrailingZeros(forInt(0));
        Stamp stamp2 = stampForTrailingZeros(forInt(1));

        assertTrue(StampTool.unsignedCompare(stamp1, stamp2) != null);
        assertTrue(StampTool.unsignedCompare(stamp1, stamp1) == null);
        assertTrue(StampTool.unsignedCompare(stamp2, stamp1) == null);
    }

    @Test
    /**
     * Tests certain paths not reached by other testing.
     */
    public void testStampToolPaths() {
        Stamp stamp1 = stampForTrailingZeros(forInt(0));
        assertFalse(StampTool.isAlwaysArray(stamp1));
        assertFalse(StampTool.isExactType(stamp1));
    }

}
