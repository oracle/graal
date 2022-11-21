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

import static org.graalvm.wasm.WasmType.VOID_TYPE_ARRAY;

import java.util.Arrays;

import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.parser.validation.ParserState;
import org.junit.Assert;
import org.junit.Test;

public class ExtraDataSuite {
    private static int compactTargetInfo(int byteCodeTarget, int extraDataTarget) {
        int e = Short.toUnsignedInt((short) extraDataTarget);
        int b = Short.toUnsignedInt((short) byteCodeTarget);
        return ((e << 16) + b) & 0x7fff_ffff;
    }

    private static int compactStackChange(int typeIndicator, int returnLength, int stackSize) {
        int r = Short.toUnsignedInt((short) returnLength);
        int s = Short.toUnsignedInt((short) stackSize);
        return typeIndicator | (r << 24) | (s << 16);
    }

    private static int extendedTargetInfo(int extraDataTarget) {
        return 0x8000_0000 | extraDataTarget;
    }

    @Test
    public void testCompactByteCodeTargetMaxBackJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterLoop(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY, 10);
        state.addConditionalBranch(0, 32778);
        state.exit(32779, false);
        state.exit(32780, false);

        final int[] expected = {compactTargetInfo(-32768, 0), 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactByteCodeTargetMaxForwardJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 10);
        state.exit(32777, false);
        state.exit(32778, false);
        final int[] expected = {compactTargetInfo(32767, 2), 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedByteCodeTargetMinBackJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterLoop(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY, 10);
        state.addConditionalBranch(0, 32779);
        state.exit(32780, false);
        state.exit(32781, false);
        final int[] expected = {extendedTargetInfo(0), -32769, 0, 0, 0, 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedByteCodeTargetMinForwardJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 10);
        state.exit(32778, false);
        state.exit(32779, false);
        final int[] expected = {extendedTargetInfo(6), 32768, 0, 0, 0, 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactExtraDataTargetMaxBackJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterLoop(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY, 0);
        for (int i = 0; i < 16384; i++) {
            state.addCall(0);
        }
        state.addConditionalBranch(0, 16385);
        state.exit(16386, false);
        state.exit(16387, false);
        final int[] expected = new int[16386];
        expected[16384] = compactTargetInfo(-16385, -16384);
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactExtraDataTargetMaxForwardJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 0);
        for (int i = 0; i < 16381; i++) {
            state.addCall(0);
        }
        state.exit(16382, false);
        state.exit(16383, false);
        final int[] expected = new int[16383];
        expected[0] = compactTargetInfo(16382, 16383);
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedExtraDataTargetMinBackwardJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterLoop(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY, 0);
        for (int i = 0; i < 16385; i++) {
            state.addCall(0);
        }
        state.addConditionalBranch(0, 16386);
        state.exit(16387, false);
        state.exit(16388, false);
        final int[] expected = new int[16391];
        expected[16385] = extendedTargetInfo(-16385);
        expected[16386] = -16386;
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedExtraDataTargetMinForwardJump() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 0);
        for (int i = 0; i < 16382; i++) {
            state.addCall(0);
        }
        state.exit(16383, false);
        state.exit(16384, false);
        final int[] expected = new int[16388];
        expected[0] = extendedTargetInfo(16388);
        expected[1] = 16383;
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactReturnLength() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, new byte[]{WasmType.I32_TYPE});
        state.push(WasmType.I32_TYPE);
        state.addConditionalBranch(0, 10);
        state.exit(20, false);
        state.pop();
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x0080_0000, 1, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactReturnLengthMax() {
        ParserState state = new ParserState();
        final byte[] returnTypes = new byte[127];
        Arrays.fill(returnTypes, WasmType.I32_TYPE);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, returnTypes);
        state.pushAll(returnTypes);
        state.addConditionalBranch(0, 10);
        state.exit(20, true);
        state.popAll(returnTypes);
        state.exit(21, true);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x0080_0000, 127, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedReturnLengthMin() {
        ParserState state = new ParserState();
        final byte[] returnTypes = new byte[128];
        Arrays.fill(returnTypes, WasmType.I32_TYPE);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, returnTypes);
        state.pushAll(returnTypes);
        state.addConditionalBranch(0, 10);
        state.exit(20, true);
        state.popAll(returnTypes);
        state.exit(21, true);
        final int[] expected = {extendedTargetInfo(6), 10, 0x0080_0000, 128, 0, 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactStackSize() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.push(WasmType.I32_TYPE);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 10);
        state.exit(20, false);
        state.pop();
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0, 0, 1)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCompactStackSizeMax() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        for (int i = 0; i < 127; i++) {
            state.push(WasmType.I32_TYPE);
        }
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 10);
        state.exit(20, false);
        for (int i = 0; i < 127; i++) {
            state.pop();
        }
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0, 0, 127)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testExtendedStackSizeMin() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        for (int i = 0; i < 128; i++) {
            state.push(WasmType.I32_TYPE);
        }
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.addConditionalBranch(0, 10);
        state.exit(20, false);
        for (int i = 0; i < 128; i++) {
            state.pop();
        }
        state.exit(21, false);
        final int[] expected = {extendedTargetInfo(6), 10, 0, 0, 128, 0};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testReferenceReturnType() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, new byte[]{WasmType.FUNCREF_TYPE});
        state.push(WasmType.FUNCREF_TYPE);
        state.addConditionalBranch(0, 10);
        state.exit(20, false);
        state.pop();
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x8000_0000, 1, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testBothReturnTypes() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, new byte[]{WasmType.I32_TYPE, WasmType.FUNCREF_TYPE});
        state.push(WasmType.I32_TYPE);
        state.push(WasmType.FUNCREF_TYPE);
        state.addConditionalBranch(0, 10);
        state.exit(20, true);
        state.pop();
        state.pop();
        state.exit(21, true);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x8080_0000, 2, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testNumericStackChange() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.push(WasmType.I32_TYPE);
        state.addConditionalBranch(0, 10);
        state.pop();
        state.exit(20, false);
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x0080_0000, 0, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testReferenceStackChange() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.push(WasmType.FUNCREF_TYPE);
        state.addConditionalBranch(0, 10);
        state.pop();
        state.exit(20, false);
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x8000_0000, 0, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testBothTypesStackChange() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.push(WasmType.FUNCREF_TYPE);
        state.push(WasmType.I32_TYPE);
        state.addConditionalBranch(0, 10);
        state.pop();
        state.pop();
        state.exit(20, false);
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x8080_0000, 0, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }

    @Test
    public void testCombinedTypeStackChangeAndReturn() {
        ParserState state = new ParserState();
        state.enterBlock(VOID_TYPE_ARRAY, VOID_TYPE_ARRAY);
        state.enterBlock(VOID_TYPE_ARRAY, new byte[]{WasmType.I32_TYPE});
        state.push(WasmType.FUNCREF_TYPE);
        state.push(WasmType.I32_TYPE);
        state.addConditionalBranch(0, 10);
        state.pop();
        state.pop();
        state.push(WasmType.I32_TYPE);
        state.exit(20, false);
        state.pop();
        state.exit(21, false);
        final int[] expected = {compactTargetInfo(10, 2), compactStackChange(0x8080_0000, 1, 0)};
        Assert.assertArrayEquals(expected, state.extraData());
    }
}
