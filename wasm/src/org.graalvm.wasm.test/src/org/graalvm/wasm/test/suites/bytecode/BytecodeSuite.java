/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.test.suites.bytecode;

import java.util.function.Consumer;

import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.constants.BytecodeBitEncoding;
import org.graalvm.wasm.constants.ExceptionHandlerType;
import org.graalvm.wasm.constants.SegmentMode;
import org.graalvm.wasm.parser.bytecode.RuntimeBytecodeGen;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.memory.ByteArraySupport;

/**
 * Tests the correctness of the bytecode produced by the {@link RuntimeBytecodeGen}.
 */
public class BytecodeSuite {
    private static void test(Consumer<RuntimeBytecodeGen> b, byte[] expected) {
        RuntimeBytecodeGen bytecodeGen = new RuntimeBytecodeGen();
        b.accept(bytecodeGen);
        Assert.assertArrayEquals(expected, bytecodeGen.toArray());
    }

    private static void testAssertion(Consumer<RuntimeBytecodeGen> b, String errorMessage) {
        RuntimeBytecodeGen bytecodeGen = new RuntimeBytecodeGen();
        try {
            b.accept(bytecodeGen);
            Assert.fail("Should have thrown assertion error");
        } catch (AssertionError e) {
            Assert.assertTrue("Invalid assertion message: " + e.getMessage(), e.getMessage().contains(errorMessage));
        }
    }

    @Test
    public void testEmptyLabel() {
        test(b -> b.addLabel(0, 0, 0), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x00});
    }

    // Test common result type encoding
    @Test
    public void testLabelU8ValueTypeNum() {
        test(b -> b.addLabel(1, 0, WasmType.NUM_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x40});
    }

    @Test
    public void testLabelU8ValueTypeObj() {
        test(b -> b.addLabel(1, 0, WasmType.OBJ_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, (byte) 0x80});
    }

    @Test
    public void testLabelU8ValueTypeMix() {
        testAssertion(b -> b.addLabel(1, 0, WasmType.MIX_COMMON_TYPE), "Single label value must either have number or reference type.");
    }

    @Test
    public void testLabelU8ValueTypeNone() {
        testAssertion(b -> b.addLabel(1, 0, WasmType.NONE_COMMON_TYPE), "Single label value must either have number or reference type.");
    }

    @Test
    public void testLabelU16ValueTypeNum() {
        test(b -> b.addLabel(2, 0, WasmType.NUM_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, 0x42, 0x00});
    }

    @Test
    public void testLabelU16ValueTypeObj() {
        test(b -> b.addLabel(2, 0, WasmType.OBJ_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, (byte) 0x82, 0x00});
    }

    @Test
    public void testLabelU16ValueTypeMix() {
        test(b -> b.addLabel(2, 0, WasmType.MIX_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, (byte) 0xC2, 0x00});
    }

    @Test
    public void testLabelI32ValueTypeNum() {
        test(b -> b.addLabel(64, 0, WasmType.NUM_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_I32, Bytecode.LABEL_I32, 0x01, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testLabelI32ValueTypeObj() {
        test(b -> b.addLabel(64, 0, WasmType.OBJ_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_I32, Bytecode.LABEL_I32, 0x02, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testLabelI32ValueTypeMix() {
        test(b -> b.addLabel(64, 0, WasmType.MIX_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_I32, Bytecode.LABEL_I32, 0x03, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testLegacyLabelU8Encoding() {
        test(b -> b.addLabel(1, 20, WasmType.NUM_COMMON_TYPE, 3, true), new byte[]{
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x54,
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_CATCH_UNWIND,
                        0x03, 0x00, 0x00, 0x00});
    }

    @Test
    public void testLegacyLabelU16Encoding() {
        test(b -> b.addLabel(2, 64, WasmType.NUM_COMMON_TYPE, 3, true), new byte[]{
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_SKIP_LABEL_U16, Bytecode.LABEL_U16, 0x42, 0x40,
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_CATCH_UNWIND,
                        0x03, 0x00, 0x00, 0x00});
    }

    @Test
    public void testLegacyLabelI32Encoding() {
        test(b -> b.addLabel(64, 256, WasmType.NUM_COMMON_TYPE, 3, true), new byte[]{
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_SKIP_LABEL_I32, Bytecode.LABEL_I32, 0x01, 0x40, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
                        (byte) Bytecode.MISC, (byte) Bytecode.LEGACY_CATCH_UNWIND,
                        0x03, 0x00, 0x00, 0x00});
    }

    @Test
    public void testNonzeroLegacyCatchDepthWithoutUnwind() {
        test(b -> b.addLabel(0, 0, WasmType.NONE_COMMON_TYPE, 3, false), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x00});
    }

    @Test
    public void testLabelU8MaxStackSize() {
        test(b -> b.addLabel(0, 63, 0), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x3F});
    }

    @Test
    public void testLabelU16MinStackSize() {
        test(b -> b.addLabel(0, 64, 0), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, 0x00, 0x40});
    }

    @Test
    public void testLabelU16MaxArity() {
        test(b -> b.addLabel(63, 0, WasmType.NUM_COMMON_TYPE), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, 0x7F, 0x00});
    }

    @Test
    public void testLabelU16MaxStackSize() {
        test(b -> b.addLabel(0, 255, 0), new byte[]{Bytecode.SKIP_LABEL_U16, Bytecode.LABEL_U16, 0x00, (byte) 0xFF});
    }

    @Test
    public void testLabelI32MinStackSize() {
        test(b -> b.addLabel(0, 256, 0), new byte[]{Bytecode.SKIP_LABEL_I32, Bytecode.LABEL_I32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    /**
     * Checks the compact loop-header encoding: an ordinary skip prefix, a label carrying one
     * numeric parameter and its stack height, then the LOOP instruction.
     */
    @Test
    public void testAddLoopLabel() {
        test(b -> b.addLoopLabel(1, 20, WasmType.NUM_COMMON_TYPE, 0, false), new byte[]{Bytecode.SKIP_LABEL_U8, Bytecode.LABEL_U8, 0x54, Bytecode.LOOP});
    }

    @Test
    public void testInvalidLabelValueType() {
        testAssertion(b -> b.addLabel(1, 1, 5), "invalid label value type");
    }

    @Test
    public void testBrU8Min() {
        test(b -> b.addBackwardBranch(1, RuntimeBytecodeGen.BranchOp.BR), new byte[]{Bytecode.BR_U8, 0x00});
    }

    @Test
    public void testBrU8Max() {
        final byte[] expected = new byte[256];
        expected[254] = Bytecode.BR_U8;
        expected[255] = (byte) 0xFF;
        test(b -> {
            for (int i = 0; i < 254; i++) {
                b.addOp(0);
            }
            b.addBackwardBranch(0, RuntimeBytecodeGen.BranchOp.BR);
        }, expected);
    }

    @Test
    public void testBrI32MinForward() {
        test(b -> b.addForwardBranch(RuntimeBytecodeGen.BranchOp.BR).patch(2), new byte[]{Bytecode.BR_I32, 0x01, 0x00, 0x00, 0x00});
    }

    @Test
    public void testBrI32MaxForward() {
        test(b -> b.addForwardBranch(RuntimeBytecodeGen.BranchOp.BR).patch(2147483647), new byte[]{Bytecode.BR_I32, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, 0x7F});
    }

    @Test
    public void testBrI32MinBackward() {
        final byte[] expected = new byte[260];
        expected[255] = Bytecode.BR_I32;
        expected[256] = 0x00;
        expected[257] = (byte) 0xFF;
        expected[258] = (byte) 0xFF;
        expected[259] = (byte) 0xFF;
        test(b -> {
            for (int i = 0; i < 255; i++) {
                b.addOp(0);
            }
            b.addBackwardBranch(0, RuntimeBytecodeGen.BranchOp.BR);
        }, expected);
    }

    @Test
    public void testBrIfU8Min() {
        test(b -> b.addBackwardBranch(1, RuntimeBytecodeGen.BranchOp.BR_IF), new byte[]{Bytecode.BR_IF_U8, 0x00, 0x00, 0x00});
    }

    @Test
    public void testBrIfU8Max() {
        final byte[] expected = new byte[258];
        expected[254] = Bytecode.BR_IF_U8;
        expected[255] = (byte) 0xFF;
        test(b -> {
            for (int i = 0; i < 254; i++) {
                b.addOp(0);
            }
            b.addBackwardBranch(0, RuntimeBytecodeGen.BranchOp.BR_IF);
        }, expected);
    }

    @Test
    public void testBrIfI32MinForward() {
        test(b -> b.addForwardBranch(RuntimeBytecodeGen.BranchOp.BR_IF).patch(2), new byte[]{Bytecode.BR_IF_I32, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testBrIfI32MaxForward() {
        test(b -> b.addForwardBranch(RuntimeBytecodeGen.BranchOp.BR_IF).patch(2147483647),
                        new byte[]{Bytecode.BR_IF_I32, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, 0x7F, 0x00, 0x00});
    }

    @Test
    public void testBrIfI32MinBackward() {
        final byte[] expected = new byte[262];
        expected[255] = Bytecode.BR_IF_I32;
        expected[256] = 0x00;
        expected[257] = (byte) 0xFF;
        expected[258] = (byte) 0xFF;
        expected[259] = (byte) 0xFF;
        test(b -> {
            for (int i = 0; i < 255; i++) {
                b.addOp(0);
            }
            b.addBackwardBranch(0, RuntimeBytecodeGen.BranchOp.BR_IF);
        }, expected);
    }

    /**
     * Checks that all six branch forms compute displacements relative to the displacement operand,
     * accounting for opcode prefixes and cast type operands. Covers forward and backward targets
     * and both sides of the U8 displacement limit.
     */
    @Test
    public void testBranchOperandOffsets() {
        RuntimeBytecodeGen.BranchOp[] branches = {
                        RuntimeBytecodeGen.BranchOp.BR,
                        RuntimeBytecodeGen.BranchOp.BR_IF,
                        RuntimeBytecodeGen.BranchOp.BR_ON_NULL,
                        RuntimeBytecodeGen.BranchOp.BR_ON_NON_NULL,
                        new RuntimeBytecodeGen.BranchOp.BrOnCast(false, WasmType.FUNCREF_TYPE),
                        new RuntimeBytecodeGen.BranchOp.BrOnCast(true, WasmType.FUNCREF_TYPE)};
        for (RuntimeBytecodeGen.BranchOp branch : branches) {
            boolean castBranch = branch instanceof RuntimeBytecodeGen.BranchOp.BrOnCast;
            // Derive the operand position from the encoding, independently of prefixSize().
            int prefixSize = castBranch ? 5 : branch == RuntimeBytecodeGen.BranchOp.BR || branch == RuntimeBytecodeGen.BranchOp.BR_IF ? 1 : 2;
            // Include both sides of the u8 displacement limit for every prefix size.
            for (int padding : new int[]{0, 249, 250, 253, 254, 255, 256}) {
                for (int target : new int[]{0, 1024}) {
                    RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
                    for (int i = 0; i < padding; i++) {
                        bytecode.addOp(Bytecode.NOP);
                    }
                    if (castBranch) {
                        bytecode.addOp(Bytecode.AGGREGATE);
                    }
                    int displacementOffset = bytecode.location() + prefixSize;
                    boolean backward = target <= displacementOffset;
                    if (backward) {
                        bytecode.addBackwardBranch(target, branch);
                    } else {
                        bytecode.addForwardBranch(branch).patch(target);
                    }
                    byte[] bytes = bytecode.toArray();
                    boolean compact = backward && displacementOffset - target <= 255;
                    int displacementSize = compact ? 1 : 4;
                    int profileSize = branch == RuntimeBytecodeGen.BranchOp.BR ? 0 : 2;
                    Assert.assertEquals(displacementOffset + displacementSize + profileSize, bytes.length);
                    int displacement = compact ? -Byte.toUnsignedInt(bytes[displacementOffset])
                                    : ByteArraySupport.littleEndian().getInt(bytes, displacementOffset);
                    Assert.assertEquals(target, displacementOffset + displacement);
                }
            }
        }
    }

    @Test
    public void testBrTableU8Min() {
        test(b -> b.addBranchTable(1), new byte[]{Bytecode.BR_TABLE_U8, 0x01, 0x00, 0x00});
    }

    @Test
    public void testBrTableU8Max() {
        test(b -> b.addBranchTable(255), new byte[]{Bytecode.BR_TABLE_U8, (byte) 0xFF, 0x00, 0x00});
    }

    @Test
    public void testBrTableI32Min() {
        test(b -> b.addBranchTable(256), new byte[]{Bytecode.BR_TABLE_I32, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00});
    }

    /**
     * Checks label-width selection and encoded operands across stack-height, arity, and catch-depth
     * boundaries, including matching ordinary and legacy skip prefixes.
     */
    @Test
    public void testLabelDataLengths() {
        for (int arity : new int[]{0, 1, 2, 63, 64, 256}) {
            for (int stackSize : new int[]{0, 63, 64, 255, 256}) {
                for (int depth : new int[]{0, 1, 256}) {
                    RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
                    bytecode.addOp(Bytecode.NOP);
                    int label = bytecode.addLabel(arity, stackSize, arity == 0 ? WasmType.NONE_COMMON_TYPE : WasmType.NUM_COMMON_TYPE, depth, depth != 0);
                    Assert.assertEquals(depth == 0 ? 2 : 3, label);
                    byte[] bytes = bytecode.toArray();
                    int labelSize = arity <= 1 && stackSize <= 63 ? 2 : arity <= 63 && stackSize <= 255 ? 3 : 10;
                    int labelOpcode = labelSize == 2 ? Bytecode.LABEL_U8 : labelSize == 3 ? Bytecode.LABEL_U16 : Bytecode.LABEL_I32;
                    int skipOpcode = labelSize == 2 ? Bytecode.SKIP_LABEL_U8 : labelSize == 3 ? Bytecode.SKIP_LABEL_U16 : Bytecode.SKIP_LABEL_I32;
                    if (depth != 0) {
                        skipOpcode = labelSize == 2 ? Bytecode.LEGACY_SKIP_LABEL_U8 : labelSize == 3 ? Bytecode.LEGACY_SKIP_LABEL_U16 : Bytecode.LEGACY_SKIP_LABEL_I32;
                        Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, label - 2));
                    }
                    Assert.assertEquals(skipOpcode, BinaryStreamParser.rawPeekU8(bytes, label - 1));
                    int ordinarySkipOpcode = labelSize == 2 ? Bytecode.SKIP_LABEL_U8 : labelSize == 3 ? Bytecode.SKIP_LABEL_U16 : Bytecode.SKIP_LABEL_I32;
                    Assert.assertEquals(labelSize, ordinarySkipOpcode);
                    Assert.assertEquals(labelOpcode, BinaryStreamParser.rawPeekU8(bytes, label));
                    if (labelSize == 2) {
                        Assert.assertEquals((arity == 0 ? 0 : 0x40) | stackSize, BinaryStreamParser.rawPeekU8(bytes, label + 1));
                    } else if (labelSize == 3) {
                        Assert.assertEquals((arity == 0 ? 0 : 0x40) | arity, BinaryStreamParser.rawPeekU8(bytes, label + 1));
                        Assert.assertEquals(stackSize, BinaryStreamParser.rawPeekU8(bytes, label + 2));
                    } else {
                        Assert.assertEquals(arity == 0 ? 0 : 1, BinaryStreamParser.rawPeekU8(bytes, label + 1));
                        Assert.assertEquals(arity, BinaryStreamParser.rawPeekI32(bytes, label + 2));
                        Assert.assertEquals(stackSize, BinaryStreamParser.rawPeekI32(bytes, label + 6));
                    }
                    if (depth != 0) {
                        Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, label + labelSize));
                        Assert.assertEquals(Bytecode.LEGACY_CATCH_UNWIND, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 1));
                        Assert.assertEquals(depth, BinaryStreamParser.rawPeekI32(bytes, label + labelSize + 2));
                    }
                    Assert.assertEquals(label + labelSize + (depth == 0 ? 0 : 6), bytes.length);
                }
            }
        }
    }

    /**
     * Checks that each label width can explicitly encode cleanup to catch depth zero,
     * distinguishable from absent metadata, immediately followed by LOOP.
     */
    @Test
    public void testExplicitZeroDepthLegacyUnwind() {
        for (int stackSize : new int[]{0, 64, 256}) {
            RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
            int label = bytecode.addLabel(0, stackSize, WasmType.NONE_COMMON_TYPE, 0, true);
            bytecode.addOp(Bytecode.LOOP);
            byte[] bytes = bytecode.toArray();
            int labelSize = stackSize == 0 ? 2 : stackSize == 64 ? 3 : 10;
            int skipOpcode = stackSize == 0 ? Bytecode.LEGACY_SKIP_LABEL_U8 : stackSize == 64 ? Bytecode.LEGACY_SKIP_LABEL_U16 : Bytecode.LEGACY_SKIP_LABEL_I32;
            Assert.assertEquals(2, label);
            Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, 0));
            Assert.assertEquals(skipOpcode, BinaryStreamParser.rawPeekU8(bytes, 1));
            Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, label + labelSize));
            Assert.assertEquals(Bytecode.LEGACY_CATCH_UNWIND, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 1));
            Assert.assertEquals(0, BinaryStreamParser.rawPeekI32(bytes, label + labelSize + 2));
            Assert.assertEquals(Bytecode.LOOP, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 6));
            Assert.assertEquals(label + labelSize + 7, bytes.length);
        }
    }

    /**
     * Checks all loop-label widths with and without legacy catches: depth-zero unwind metadata and
     * the legacy skip prefix appear only when requested, and LOOP follows the complete label.
     */
    @Test
    public void testLoopLabelUnwindMetadata() {
        for (boolean hasLegacyCatches : new boolean[]{false, true}) {
            for (int stackSize : new int[]{0, 64, 256}) {
                RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
                int label = bytecode.addLoopLabel(1, stackSize, WasmType.OBJ_COMMON_TYPE, 0, hasLegacyCatches);
                byte[] bytes = bytecode.toArray();
                int labelSize = stackSize == 0 ? 2 : stackSize == 64 ? 3 : 10;
                int skipOpcode = stackSize == 0 ? Bytecode.SKIP_LABEL_U8 : stackSize == 64 ? Bytecode.SKIP_LABEL_U16 : Bytecode.SKIP_LABEL_I32;
                if (hasLegacyCatches) {
                    skipOpcode = stackSize == 0 ? Bytecode.LEGACY_SKIP_LABEL_U8 : stackSize == 64 ? Bytecode.LEGACY_SKIP_LABEL_U16 : Bytecode.LEGACY_SKIP_LABEL_I32;
                    Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, 0));
                }
                Assert.assertEquals(hasLegacyCatches ? 2 : 1, label);
                Assert.assertEquals(skipOpcode, BinaryStreamParser.rawPeekU8(bytes, label - 1));
                int loopOffset = label + labelSize + (hasLegacyCatches ? 6 : 0);
                Assert.assertEquals(Bytecode.LOOP, BinaryStreamParser.rawPeekU8(bytes, loopOffset));
                Assert.assertEquals(loopOffset + 1, bytes.length);
                if (hasLegacyCatches) {
                    Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, label + labelSize));
                    Assert.assertEquals(Bytecode.LEGACY_CATCH_UNWIND, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 1));
                    Assert.assertEquals(0, BinaryStreamParser.rawPeekI32(bytes, label + labelSize + 2));
                }
            }
        }
    }

    /**
     * Checks that a loop inside legacy catches encodes its nonzero unwind depth after the parameter
     * label and before LOOP, for every label width.
     */
    @Test
    public void testLegacyLoopLabel() {
        for (int stackSize : new int[]{0, 64, 256}) {
            RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
            bytecode.addOp(Bytecode.NOP);
            int label = bytecode.addLoopLabel(1, stackSize, WasmType.OBJ_COMMON_TYPE, 2, true);
            byte[] bytes = bytecode.toArray();
            Assert.assertEquals(3, label);
            int labelSize = stackSize <= 63 ? 2 : stackSize <= 255 ? 3 : 10;
            int labelOpcode = labelSize == 2 ? Bytecode.LABEL_U8 : labelSize == 3 ? Bytecode.LABEL_U16 : Bytecode.LABEL_I32;
            Assert.assertEquals(labelOpcode, BinaryStreamParser.rawPeekU8(bytes, label));
            Assert.assertEquals(Bytecode.MISC, BinaryStreamParser.rawPeekU8(bytes, label + labelSize));
            Assert.assertEquals(Bytecode.LEGACY_CATCH_UNWIND, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 1));
            Assert.assertEquals(2, BinaryStreamParser.rawPeekI32(bytes, label + labelSize + 2));
            Assert.assertEquals(Bytecode.LOOP, BinaryStreamParser.rawPeekU8(bytes, label + labelSize + 6));
            Assert.assertEquals(label + labelSize + 7, bytes.length);
        }
    }

    /**
     * Checks the six-byte placeholder for a branch-table entry: a four-byte displacement followed
     * by a two-byte profile, and verifies that the returned fixup patches the displacement.
     */
    @Test
    public void testBranchTableItems() {
        test(b -> b.addBranchTableItem().patch(1), new byte[]{1, 0, 0, 0, 0, 0});
    }

    @Test
    public void testIfLocation() {
        test(b -> b.addIf(RuntimeBytecodeGen.BranchHint.NONE).patch(6), new byte[]{Bytecode.IF, 5, 0, 0, 0, 0, 0});
    }

    /**
     * Checks that U8 and I32 branch-table headers followed by fixed-size entries end exactly at the
     * next instruction.
     */
    @Test
    public void testBranchTableLayout() {
        for (int size : new int[]{2, 256}) {
            RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
            bytecode.addBranchTable(size);
            for (int i = 0; i < size; i++) {
                bytecode.addBranchTableItem();
            }
            int callOffset = bytecode.location();
            bytecode.addCall(0, 42);
            byte[] bytes = bytecode.toArray();
            int headerSize = size <= 255 ? BytecodeBitEncoding.BR_TABLE_U8_HEADER_SIZE : BytecodeBitEncoding.BR_TABLE_I32_HEADER_SIZE;
            Assert.assertEquals(callOffset, headerSize + size * BytecodeBitEncoding.BR_TABLE_ITEM_SIZE);
            Assert.assertEquals(Bytecode.CALL_U8, bytes[callOffset]);
        }
    }

    @Test
    public void testCallU8Min() {
        test(b -> b.addCall(0, 0), new byte[]{Bytecode.CALL_U8, 0x00, 0x00});
    }

    @Test
    public void testCallU8MaxNodeIndex() {
        test(b -> b.addCall(255, 0), new byte[]{Bytecode.CALL_U8, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCallU8MaxFunctionIndex() {
        test(b -> b.addCall(0, 255), new byte[]{Bytecode.CALL_U8, 0x00, (byte) 0xFF});
    }

    @Test
    public void testCallI32MinNodeIndex() {
        test(b -> b.addCall(256, 0), new byte[]{Bytecode.CALL_I32, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testCallI32MinFunctionIndex() {
        test(b -> b.addCall(0, 256), new byte[]{Bytecode.CALL_I32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testCallIndirectU8Min() {
        test(b -> b.addIndirectCall(0, 0, 0), new byte[]{Bytecode.CALL_INDIRECT_U8, 0x00, 0x00, 0x00});
    }

    @Test
    public void testCallIndirectU8MaxNodeIndex() {
        test(b -> b.addIndirectCall(255, 0, 0), new byte[]{Bytecode.CALL_INDIRECT_U8, (byte) 0xFF, 0x00, 0x00});
    }

    @Test
    public void testCallIndirectU8MaxTypeIndex() {
        test(b -> b.addIndirectCall(0, 255, 0), new byte[]{Bytecode.CALL_INDIRECT_U8, 0x00, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCallIndirectU8MaxTableIndex() {
        test(b -> b.addIndirectCall(0, 0, 255), new byte[]{Bytecode.CALL_INDIRECT_U8, 0x00, 0x00, (byte) 0xFF});
    }

    @Test
    public void testCallIndirectI32MinNodeIndex() {
        test(b -> b.addIndirectCall(256, 0, 0), new byte[]{Bytecode.CALL_INDIRECT_I32, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testCallIndirectI32MinTypeIndex() {
        test(b -> b.addIndirectCall(0, 256, 0), new byte[]{Bytecode.CALL_INDIRECT_I32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testCallIndirectI32MinTableIndex() {
        test(b -> b.addIndirectCall(0, 0, 256), new byte[]{Bytecode.CALL_INDIRECT_I32, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testI32SignedI8Min() {
        test(b -> b.addSigned(Bytecode.I32_CONST_I8, Bytecode.I32_CONST_I32, -128), new byte[]{Bytecode.I32_CONST_I8, (byte) 0x80});
    }

    @Test
    public void testI32SignedI8Max() {
        test(b -> b.addSigned(Bytecode.I32_CONST_I8, Bytecode.I32_CONST_I32, 127), new byte[]{Bytecode.I32_CONST_I8, 0x7F});
    }

    @Test
    public void testI32SignedI32MinNegative() {
        test(b -> b.addSigned(Bytecode.I32_CONST_I8, Bytecode.I32_CONST_I32, -129), new byte[]{Bytecode.I32_CONST_I32, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testI32SignedI32MinPositive() {
        test(b -> b.addSigned(Bytecode.I32_CONST_I8, Bytecode.I32_CONST_I32, 128), new byte[]{Bytecode.I32_CONST_I32, (byte) 0x80, 0x00, 0x00, 0x00});
    }

    @Test
    public void testI64SignedU8Min() {
        test(b -> b.addSigned(Bytecode.I64_CONST_I8, Bytecode.I64_CONST_I64, -128L), new byte[]{Bytecode.I64_CONST_I8, (byte) 0x80});
    }

    @Test
    public void testI64SignedU8Max() {
        test(b -> b.addSigned(Bytecode.I64_CONST_I8, Bytecode.I64_CONST_I64, 127L), new byte[]{Bytecode.I64_CONST_I8, 0x7F});
    }

    @Test
    public void testI64SignedI64MinNegative() {
        test(b -> b.addSigned(Bytecode.I64_CONST_I8, Bytecode.I64_CONST_I64, -129L),
                        new byte[]{Bytecode.I64_CONST_I64, (byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testI64SignedI64MinPositive() {
        test(b -> b.addSigned(Bytecode.I64_CONST_I8, Bytecode.I64_CONST_I64, 128L), new byte[]{Bytecode.I64_CONST_I64, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testUnsignedU8Min() {
        test(b -> b.addUnsigned(Bytecode.LOCAL_GET_U8, Bytecode.LOCAL_GET_I32, 0), new byte[]{Bytecode.LOCAL_GET_U8, 0x00});
    }

    @Test
    public void testUnsignedU8Max() {
        test(b -> b.addUnsigned(Bytecode.LOCAL_GET_U8, Bytecode.LOCAL_GET_I32, 255), new byte[]{Bytecode.LOCAL_GET_U8, (byte) 0xFF});
    }

    @Test
    public void testUnsignedI32Min() {
        test(b -> b.addUnsigned(Bytecode.LOCAL_GET_U8, Bytecode.LOCAL_GET_I32, 256), new byte[]{Bytecode.LOCAL_GET_I32, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionU8Min() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 0, false), new byte[]{Bytecode.I32_LOAD_U8, 0x00});
    }

    @Test
    public void testMemoryInstructionU8Max() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 255, false), new byte[]{Bytecode.I32_LOAD_U8, (byte) 0xFF});
    }

    @Test
    public void testMemoryInstructionI32Min() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 256, false), new byte[]{Bytecode.I32_LOAD_I32, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionI32Max() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 4294967295L, false),
                        new byte[]{Bytecode.I32_LOAD_I32, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testMemoryInstructionIndexType64() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 0, true), new byte[]{Bytecode.I32_LOAD, (byte) 0x81, 0x00, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionMinI64Offset() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 4294967296L, false),
                        new byte[]{Bytecode.I32_LOAD, 0x08, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionIndexType64MaxU8Offset() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 255, true),
                        new byte[]{Bytecode.I32_LOAD, (byte) 0x81, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF});
    }

    @Test
    public void testMemoryInstructionIndexType64MinU32Offset() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 256, true),
                        new byte[]{Bytecode.I32_LOAD, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionIndexType64MaxU32Offset() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 4294967295L, true),
                        new byte[]{Bytecode.I32_LOAD, (byte) 0x84, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testMemoryInstructionIndexType64MinI64Offset() {
        test(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 4294967296L, true),
                        new byte[]{Bytecode.I32_LOAD, (byte) 0x88, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00});
    }

    @Test
    public void testMemoryInstructionInvalidOpcode() {
        testAssertion(b -> b.addMemoryInstruction(256, Bytecode.I32_LOAD_U8, Bytecode.I32_LOAD_I32, 0, 1, false), "opcode does not fit into byte");
    }

    @Test
    public void testMemoryInstructionInvalidOpcodeU8() {
        testAssertion(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, 256, Bytecode.I32_LOAD_I32, 0, 1, false), "opcode does not fit into byte");
    }

    @Test
    public void testMemoryInstructionInvalidOpcodeI32() {
        testAssertion(b -> b.addMemoryInstruction(Bytecode.I32_LOAD, Bytecode.I32_LOAD_U8, 256, 0, 1, false), "opcode does not fit into byte");
    }

    @Test
    public void testAddMin() {
        test(b -> b.addOp(0x00), new byte[]{0x00});
    }

    @Test
    public void testAddMax() {
        test(b -> b.addOp(0xFF), new byte[]{(byte) 0xFF});
    }

    @Test
    public void testInvalidAdd() {
        testAssertion(b -> b.addOp(256), "opcode does not fit into byte");
    }

    @Test
    public void testAddImmediateMin() {
        test(b -> b.addOp(0x01, 0), new byte[]{0x01, 0x00, 0x00, 0x00, 0x00});
    }

    @Test
    public void testAddImmediateMax() {
        test(b -> b.addOp(0x01, 0xFFFFFFFF), new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testInvalidAddImmediate() {
        testAssertion(b -> b.addOp(256, 0), "opcode does not fit into byte");
    }

    @Test
    public void testAddImmediate64Max() {
        test(b -> b.addOp(0x01, 0xFFFFFFFFFFFFFFFFL), new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testInvalidAddImmediate64() {
        testAssertion(b -> b.addOp(256, 0xFFL), "opcode does not fit into byte");
    }

    @Test
    public void testAddImmediateMax2() {
        test(b -> b.addOp(0x01, 0, 0xFFFFFFFF), new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testInvalidAddImmediate2() {
        testAssertion(b -> b.addOp(256, 0, 0), "opcode does not fit into byte");
    }

    @Test
    public void testActiveDataHeaderMinU8Length() {
        test(b -> b.addDataHeader(0, null, -1, -1), new byte[]{0x40, 0x00});
    }

    @Test
    public void testActiveDataHeaderMaxU8Length() {
        test(b -> b.addDataHeader(255, null, -1, -1), new byte[]{0x40, (byte) 0xFF});
    }

    @Test
    public void testActiveDataHeaderMinU16Length() {
        test(b -> b.addDataHeader(256, null, -1, -1), new byte[]{(byte) 0x80, 0x00, 0x01});
    }

    @Test
    public void testActiveDataHeaderMaxU16Length() {
        test(b -> b.addDataHeader(65535, null, -1, -1), new byte[]{(byte) 0x80, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testActiveDataHeaderMinI32Length() {
        test(b -> b.addDataHeader(65536, null, -1, -1), new byte[]{(byte) 0xC0, 0x00, 0x00, 0x01, 0x00});
    }

    private static byte[] byteArrayConcat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Test
    public void testActiveDataHeaderMaxU8OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[255];
        test(b -> b.addDataHeader(1, offsetBytecode, -1, -1),
                        byteArrayConcat(new byte[]{0x42, 0x01, (byte) 0xFF}, offsetBytecode));
    }

    @Test
    public void testActiveDataHeaderMinU16OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[256];
        test(b -> b.addDataHeader(1, offsetBytecode, -1, -1),
                        byteArrayConcat(new byte[]{0x44, 0x01, 0x00, 0x01}, offsetBytecode));
    }

    @Test
    public void testActiveDataHeaderMaxU16OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[65535];
        test(b -> b.addDataHeader(1, offsetBytecode, -1, -1),
                        byteArrayConcat(new byte[]{0x44, 0x01, (byte) 0xFF, (byte) 0xFF}, offsetBytecode));
    }

    @Test
    public void testActiveDataHeaderMinI32OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[65536];
        test(b -> b.addDataHeader(1, offsetBytecode, -1, -1),
                        byteArrayConcat(new byte[]{0x46, 0x01, 0x00, 0x00, 0x01, 0x00}, offsetBytecode));
    }

    @Test
    public void testActiveDataHeaderMaxU8OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 255, -1), new byte[]{0x52, 0x01, (byte) 0xFF});
    }

    @Test
    public void testActiveDataHeaderMinU16OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 256, -1), new byte[]{0x54, 0x01, 0x00, 0x01});
    }

    @Test
    public void testActiveDataHeaderMaxU16OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 65535, -1), new byte[]{0x54, 0x01, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testActiveDataHeaderMinU32OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 65536, -1), new byte[]{0x56, 0x01, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testActiveDataHeaderMaxU32OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 4294967295L, -1), new byte[]{0x56, 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testActiveDataHeaderMinI64OffsetAddress() {
        test(b -> b.addDataHeader(1, null, 4294967296L, -1), new byte[]{0x58, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00});
    }

    @Test
    public void testActiveDataHeaderGlobalIndexAndOffsetAddress() {
        testAssertion(b -> b.addDataHeader(1, new byte[]{Bytecode.GLOBAL_GET_U8, 1}, 1, -1), "data header does not allow offset bytecode and offset address");
    }

    @Test
    public void testPassiveDataHeaderMin() {
        test(b -> b.addDataHeader(SegmentMode.PASSIVE, 1), new byte[]{0x41, 0x01});
    }

    @Test
    public void testInvalidDataHeaderSegmentMode() {
        testAssertion(b -> b.addDataHeader(3, 1), "invalid segment mode in data header");
    }

    @Test
    public void testActiveModeInPassiveDataHeader() {
        testAssertion(b -> b.addDataHeader(SegmentMode.ACTIVE, 1), "invalid active segment mode in passive data header");
    }

    @Test
    public void testElemHeaderMin() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{0x40, 0x10, WasmType.FUNCREF_TYPE, 0x00});
    }

    @Test
    public void testElemHeaderMinU8Count() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 1, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{0x40, 0x10, WasmType.FUNCREF_TYPE, 0x01});
    }

    @Test
    public void testElemHeaderMaxU8Count() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 255, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{0x40, 0x10, WasmType.FUNCREF_TYPE, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinU16Count() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 256, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{(byte) 0x80, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x01});
    }

    @Test
    public void testElemHeaderMaxU16Count() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 65535, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{(byte) 0x80, 0x10, WasmType.FUNCREF_TYPE, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinI32Count() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 65536, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{(byte) 0xC0, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testElemHeaderPassive() {
        test(b -> b.addElemHeader(SegmentMode.PASSIVE, 8, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{0x40, 0x11, WasmType.FUNCREF_TYPE, 0x08});
    }

    @Test
    public void testElemHeaderDeclarative() {
        test(b -> b.addElemHeader(SegmentMode.DECLARATIVE, 8, WasmType.FUNCREF_TYPE, 0, null, -1), new byte[]{0x40, 0x12, WasmType.FUNCREF_TYPE, 0x08});
    }

    @Test
    public void testElemHeaderExternref() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 8, WasmType.EXTERNREF_TYPE, 0, null, -1), new byte[]{0x40, 0x10, WasmType.EXTERNREF_TYPE, 0x08});
    }

    @Test
    public void testElemHeaderExnref() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 8, WasmType.EXNREF_TYPE, 0, null, -1), new byte[]{0x40, 0x10, WasmType.EXNREF_TYPE, 0x08});
    }

    @Test
    public void testElemHeaderMinU8TableIndex() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 1, null, -1), new byte[]{0x50, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x01});
    }

    @Test
    public void testElemHeaderMaxU8TableIndex() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 255, null, -1), new byte[]{0x50, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinU16TableIndex() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 256, null, -1), new byte[]{0x60, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x01});
    }

    @Test
    public void testElemHeaderMaxU16TableIndex() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 65535, null, -1), new byte[]{0x60, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinI32TableIndex() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 65536, null, -1), new byte[]{0x70, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testElemHeaderMinU8OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[0];
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, offsetBytecode, -1),
                        byteArrayConcat(new byte[]{0x41, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00}, offsetBytecode));
    }

    @Test
    public void testElemHeaderMaxU8OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[255];
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, offsetBytecode, -1),
                        byteArrayConcat(new byte[]{0x41, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF}, offsetBytecode));
    }

    @Test
    public void testElemHeaderMinU16OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[256];
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, offsetBytecode, -1),
                        byteArrayConcat(new byte[]{0x42, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x01}, offsetBytecode));
    }

    @Test
    public void testElemHeaderMaxU16OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[65535];
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, offsetBytecode, -1),
                        byteArrayConcat(new byte[]{0x42, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF, (byte) 0xFF}, offsetBytecode));
    }

    @Test
    public void testElemHeaderMinI32OffsetBytecodeLength() {
        byte[] offsetBytecode = new byte[65536];
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, offsetBytecode, -1),
                        byteArrayConcat(new byte[]{0x43, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x00, 0x01, 0x00}, offsetBytecode));
    }

    @Test
    public void testElemHeaderMinU8OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 0), new byte[]{0x49, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00});
    }

    @Test
    public void testElemHeaderMaxU8OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 255), new byte[]{0x49, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinU16OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 256), new byte[]{0x4A, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x01});
    }

    @Test
    public void testElemHeaderMaxU16OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 65535), new byte[]{0x4A, 0x10, WasmType.FUNCREF_TYPE, 0x00, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testElemHeaderMinI32OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 65536), new byte[]{0x4B, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testElemHeaderMinI64OffsetAddress() {
        test(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, null, 4294967296L),
                        new byte[]{0x4C, 0x10, WasmType.FUNCREF_TYPE, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00});
    }

    @Test
    public void testInvalidElemHeaderGlobalIndexAndOffsetAddress() {
        testAssertion(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.FUNCREF_TYPE, 0, new byte[]{Bytecode.GLOBAL_GET_U8, 1}, 1), "elem header does not allow offset bytecode and offset address");
    }

    @Test
    public void testInvalidElemHeaderSegmentMode() {
        testAssertion(b -> b.addElemHeader(4, 0, WasmType.FUNCREF_TYPE, 0, null, 1), "invalid segment mode in elem header");
    }

    @Test
    public void testInvalidElemHeaderElemType() {
        testAssertion(b -> b.addElemHeader(SegmentMode.ACTIVE, 0, WasmType.I32_TYPE, 0, null, 1), "invalid elem type in elem header");
    }

    @Test
    public void testElemMinFunctionIndex() {
        test(b -> b.addElemFunctionIndex(0), new byte[]{0x00});
    }

    @Test
    public void testElemMaxInlineFunctionIndex() {
        test(b -> b.addElemFunctionIndex(31), new byte[]{0x1F});
    }

    @Test
    public void testElemMinU8FunctionIndex() {
        test(b -> b.addElemFunctionIndex(32), new byte[]{0x20, 0x20});
    }

    @Test
    public void testElemMaxU8FunctionIndex() {
        test(b -> b.addElemFunctionIndex(255), new byte[]{0x20, (byte) 0xFF});
    }

    @Test
    public void testElemMinU16FunctionIndex() {
        test(b -> b.addElemFunctionIndex(256), new byte[]{0x40, 0x00, 0x01});
    }

    @Test
    public void testElemMaxU16FunctionIndex() {
        test(b -> b.addElemFunctionIndex(65535), new byte[]{0x40, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testElemMinI32FunctionIndex() {
        test(b -> b.addElemFunctionIndex(65536), new byte[]{0x60, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryMin() {
        test(b -> b.addCodeEntry(0, 0, 0, 0, 0), new byte[]{0x04, 0x00});
    }

    @Test
    public void testCodeEntryMinU8FunctionIndex() {
        test(b -> b.addCodeEntry(1, 0, 0, 0, 0), new byte[]{0x44, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryMaxU8FunctionIndex() {
        test(b -> b.addCodeEntry(255, 0, 0, 0, 0), new byte[]{0x44, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCodeEntryMinU16FunctionIndex() {
        test(b -> b.addCodeEntry(256, 0, 0, 0, 0), new byte[]{(byte) 0x84, 0x00, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryMaxU16FunctionIndex() {
        test(b -> b.addCodeEntry(65535, 0, 0, 0, 0), new byte[]{(byte) 0x84, (byte) 0xFF, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCodeEntryMinI32FunctionIndex() {
        test(b -> b.addCodeEntry(65536, 0, 0, 0, 0), new byte[]{(byte) 0xC4, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testCodeEntryMinU8StackSize() {
        test(b -> b.addCodeEntry(0, 1, 0, 0, 0), new byte[]{0x14, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryMaxU8StackSize() {
        test(b -> b.addCodeEntry(0, 255, 0, 0, 0), new byte[]{0x14, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCodeEntryMinU16StackSize() {
        test(b -> b.addCodeEntry(0, 256, 0, 0, 0), new byte[]{0x24, 0x00, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryMaxU16StackSize() {
        test(b -> b.addCodeEntry(0, 65535, 0, 0, 0), new byte[]{0x24, (byte) 0xFF, (byte) 0xFF, 0x00});
    }

    @Test
    public void testCodeEntryMinI32StackSize() {
        test(b -> b.addCodeEntry(0, 65536, 0, 0, 0), new byte[]{0x34, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    @Test
    public void testCodeEntryMaxU8Length() {
        test(b -> b.addCodeEntry(0, 0, 255, 0, 0), new byte[]{0x04, (byte) 0xFF});
    }

    @Test
    public void testCodeEntryMinU16Length() {
        test(b -> b.addCodeEntry(0, 0, 256, 0, 0), new byte[]{0x08, 0x00, 0x01});
    }

    @Test
    public void testCodeEntryMaxU16Length() {
        test(b -> b.addCodeEntry(0, 0, 65535, 0, 0), new byte[]{0x08, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    public void testCodeEntryMinI32Length() {
        test(b -> b.addCodeEntry(0, 0, 65536, 0, 0), new byte[]{0x0C, 0x00, 0x00, 0x01, 0x00});
    }

    @Test
    public void testCodeEntryLocals() {
        test(b -> b.addCodeEntry(0, 0, 0, 1, 0), new byte[]{0x06, 0x00});
    }

    @Test
    public void testCodeEntryResults() {
        test(b -> b.addCodeEntry(0, 0, 0, 0, 1), new byte[]{0x05, 0x00});
    }

    @Test
    public void testCatchExceptionHandler() {
        test(b -> b.addExceptionHandler(5, 10, ExceptionHandlerType.CATCH, 0, 10), new byte[]{0x05, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00});
    }

    @Test
    public void testCatchRefExceptionHandler() {
        test(b -> b.addExceptionHandler(0, 12, ExceptionHandlerType.CATCH_REF, 1, 256),
                        new byte[]{0x00, 0x00, 0x00, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00});
    }

    /**
     * Checks truncation at the current offset, an interior offset, and zero, preserving the
     * retained bytes and allowing subsequent appends and single-byte retreat.
     */
    @Test
    public void testRetreatTo() {
        RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
        bytecode.retreatTo(0);
        for (int i = 0; i < 6; i++) {
            bytecode.addOp(i);
        }
        bytecode.retreatTo(bytecode.location());
        Assert.assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5}, bytecode.toArray());
        bytecode.retreatTo(3);
        Assert.assertEquals(3, bytecode.location());
        Assert.assertArrayEquals(new byte[]{0, 1, 2}, bytecode.toArray());
        bytecode.addOp(99);
        Assert.assertArrayEquals(new byte[]{0, 1, 2, 99}, bytecode.toArray());
        bytecode.retreat();
        Assert.assertArrayEquals(new byte[]{0, 1, 2}, bytecode.toArray());
        bytecode.retreatTo(0);
        Assert.assertEquals(0, bytecode.location());
        Assert.assertArrayEquals(new byte[0], bytecode.toArray());
        bytecode.addOp(7);
        Assert.assertArrayEquals(new byte[]{7}, bytecode.toArray());
    }

    /**
     * Checks that negative offsets and offsets beyond the current end are rejected without changing
     * the bytecode length or contents.
     */
    @Test
    public void testRetreatToInvalidOffset() {
        RuntimeBytecodeGen bytecode = new RuntimeBytecodeGen();
        bytecode.addOp(42);
        for (int offset : new int[]{-1, Integer.MIN_VALUE, 2, Integer.MAX_VALUE}) {
            Assert.assertThrows(IndexOutOfBoundsException.class, () -> bytecode.retreatTo(offset));
            Assert.assertEquals(1, bytecode.location());
            Assert.assertArrayEquals(new byte[]{42}, bytecode.toArray());
        }
    }
}
