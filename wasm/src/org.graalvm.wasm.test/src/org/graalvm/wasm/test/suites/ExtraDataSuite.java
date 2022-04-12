/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.test.suites;

import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.validation.collections.ExtraDataList;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTableEntry;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTarget;
import org.graalvm.wasm.parser.validation.collections.entries.BranchTargetWithStackChange;
import org.junit.Assert;
import org.junit.Test;

public class ExtraDataSuite {
    @Test
    public void testCompactByteCodeDisplacementSimpleForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        t.setTargetInfo(1, 0, 0);
        final int[] expected = {1, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactByteCodeDisplacementSimpleBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(1);
        t.setTargetInfo(0, 0, 0);
        final int[] expected = {65535, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactByteCodeDisplacementMaxForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        t.setTargetInfo(32767, 0, 0);
        final int[] expected = {32767, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactBytecodeDisplacementMaxBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(32768);
        t.setTargetInfo(0, 0, 0);
        final int[] expected = {32768, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedByteCodeDisplacementMinForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        t.setTargetInfo(32768, 0, 0);
        final int[] expected = {-2147483648, 32768, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedByteCodeDisplacementMinBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(32769);
        t.setTargetInfo(0, 0, 0);
        final int[] expected = {-2147483648, -32769, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedByteCodeDisplacementMaxForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        t.setTargetInfo(2147483647, 0, 0);
        final int[] expected = {-2147483648, 2147483647, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedByteCodeDisplacementMaxBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(2147483647);
        t.setTargetInfo(0, 0, 0);
        final int[] expected = {-2147483648, -2147483647, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactExtraDataDisplacementSimpleForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        l.addCall(0);
        t.setTargetInfo(0, 2, 1);
        final int[] expected = {131072, 0, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactExtraDataDisplacementSimpleBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        l.addCall(0);
        BranchTarget t = l.addIf(0);
        t.setTargetInfo(0, 0, 0);
        final int[] expected = {0, 2147418112, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactExtraDataDisplacementMaxForwardJump() {
        ExtraDataList l = new ExtraDataList();
        int displacement = 16383;
        BranchTarget t = l.addIf(0);
        for (int i = 0; i < displacement - 1; i++) {
            l.addCall(0);
        }
        t.setTargetInfo(0, displacement, displacement - 2);
        final int[] expected = new int[displacement + 1];
        expected[0] = 1073676288;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactExtraDataDisplacementMaxBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        int displacement = 16384;
        for (int i = 0; i < displacement; i++) {
            l.addCall(0);
        }
        BranchTarget t = l.addIf(displacement);
        t.setTargetInfo(displacement, 0, 0);
        final int[] expected = new int[displacement + 2];
        expected[displacement] = 1073741824;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedExtraDataDisplacementMinForwardJump() {
        ExtraDataList l = new ExtraDataList();
        int displacement = 16384;
        BranchTarget t = l.addIf(0);
        for (int i = 0; i < displacement - 1; i++) {
            l.addCall(0);
        }
        t.setTargetInfo(0, displacement, displacement - 2);
        final int[] expected = new int[displacement + 2];
        expected[0] = -2147467264;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedExtraDataDisplacementMinBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        int displacement = 16385;
        for (int i = 0; i < displacement; i++) {
            l.addCall(0);
        }
        BranchTarget t = l.addIf(displacement);
        t.setTargetInfo(displacement, 0, 0);
        final int[] expected = new int[displacement + 3];
        expected[displacement] = -2147467263;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedExtraDataDisplacementOutOfBoundsForwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        try {
            t.setTargetInfo(0, 1073741824, 0);
            Assert.fail("Should have thrown exception");
        } catch (WasmException e) {
            Assert.assertTrue(e.getMessage().contains("value cannot be represented in extra data"));
        }
    }

    @Test
    public void testExtendedExtraDataDisplacementOutOfBoundsBackwardJump() {
        ExtraDataList l = new ExtraDataList();
        BranchTarget t = l.addIf(0);
        try {
            t.setTargetInfo(0, -1073741825, 0);
            Assert.fail("Should have thrown exception");
        } catch (WasmException e) {
            Assert.assertTrue(e.getMessage().contains("value cannot be represented in extra data"));
        }
    }

    @Test
    public void testCompactReturnLength() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(1, 0);
        final int[] expected = {0, 16777216};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactReturnLengthMaxValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(255, 0);
        final int[] expected = {0, -16777216};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedReturnLengthMinValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(256, 0);
        final int[] expected = {-2147483648, 0, 256, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedReturnLengthMaxValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(2147483647, 0);
        final int[] expected = {-2147483648, 0, 2147483647, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedReturnLengthOutOfBounds() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        try {
            t.setStackInfo(-1, 0);
            Assert.fail("Should have thrown exception");
        } catch (WasmException e) {
            Assert.assertTrue(e.getMessage().contains("value cannot be represented in extra data"));
        }
    }

    @Test
    public void testCompactStackSize() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(0, 1);
        final int[] expected = {0, 65536};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactStackSizeMaxValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(0, 255);
        final int[] expected = {0, 16711680};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedStackSizeMinValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(0, 256);
        final int[] expected = {-2147483648, 0, 0, 256};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedStackSizeMaxValue() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        t.setStackInfo(0, 2147483647);
        final int[] expected = {-2147483648, 0, 0, 2147483647};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedStackSizeOutOfBounds() {
        ExtraDataList l = new ExtraDataList();
        BranchTargetWithStackChange t = l.addUnconditionalBranch(0);
        try {
            t.setStackInfo(0, -1);
            Assert.fail("Should have thrown exception");
        } catch (WasmException e) {
            Assert.assertTrue(e.getMessage().contains("value cannot be represented in extra data"));
        }
    }

    @Test
    public void testCompactTableHeader() {
        ExtraDataList l = new ExtraDataList();
        l.addBranchTable(0, 1);
        final int[] expected = {65536, 0, 0};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactTableHeaderMaxValue() {
        ExtraDataList l = new ExtraDataList();
        l.addBranchTable(0, 32767);
        final int[] expected = new int[1 + 2 * 32767];
        expected[0] = 2147418112;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedTableHeaderMinValue() {
        ExtraDataList l = new ExtraDataList();
        l.addBranchTable(0, 32768);
        final int[] expected = new int[2 + 5 * 32768];
        expected[0] = -2147450880;
        for (int i = 2; i < expected.length; i += 5) {
            expected[i] = -2147483648;
        }
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedTableHeaderOutOfBounds() {
        ExtraDataList l = new ExtraDataList();
        try {
            l.addBranchTable(0, -1);
            Assert.fail("Should have thrown exception");
        } catch (WasmException e) {
            Assert.assertTrue(e.getMessage().contains("value cannot be represented in extra data"));
        }
    }

    @Test
    public void testTableHeaderWithExtendedItem() {
        ExtraDataList l = new ExtraDataList();
        BranchTableEntry b = l.addBranchTable(0, 2);
        b.item(0).setTargetInfo(32768, 0, 0);
        for (int i = 0; i < 32764; i++) {
            l.addCall(0);
        }
        final int[] expected = new int[12 + 32764];
        expected[0] = -2147483646;
        expected[2] = -2147483648;
        expected[3] = 32768;
        expected[7] = -2147483648;
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactCallIndex() {
        ExtraDataList l = new ExtraDataList();
        l.addCall(1);
        final int[] expected = {65536};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testCompactCallIndexMaxValue() {
        ExtraDataList l = new ExtraDataList();
        l.addCall(32767);
        final int[] expected = {2147418112};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedCallIndexMinValue() {
        ExtraDataList l = new ExtraDataList();
        l.addCall(32768);
        final int[] expected = {-2147450880};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }

    @Test
    public void testExtendedCallIndexMaxValue() {
        ExtraDataList l = new ExtraDataList();
        l.addCall(2147483647);
        final int[] expected = {-1};
        Assert.assertArrayEquals(expected, l.extraDataArray());
    }
}
