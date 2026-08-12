/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.api.WebAssembly;
import org.graalvm.wasm.constants.Bytecode;
import org.graalvm.wasm.parser.ir.CodeEntry;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.graalvm.wasm.test.WasmTestUtils;
import org.junit.Test;

public class BranchHintSuite extends AbstractBinarySuite {
    private static final String BRANCH_HINT_SECTION = "metadata.code.branch_hint";

    @Test
    public void testIfLikelyTrue() {
        assertBranchProfile(moduleWithIf(branchHint(3, 1)), Bytecode.IF, 5, 255, 0);
    }

    @Test
    public void testIfLikelyFalse() {
        assertBranchProfile(moduleWithIf(branchHint(3, 0)), Bytecode.IF, 5, 0, 255);
    }

    @Test
    public void testBrIfLikelyTrue() {
        assertBranchProfile(moduleWithBrIf(branchHint(7, 1)), Bytecode.BR_IF_I32, 5, 255, 0);
    }

    @Test
    public void testBrIfLikelyFalse() {
        assertBranchProfile(moduleWithBrIf(branchHint(7, 0)), Bytecode.BR_IF_I32, 5, 0, 255);
    }

    @Test
    public void testHintOffsetIncludesLocalsDeclaration() {
        assertBranchProfile(module(new int[]{WasmType.I32_TYPE}, "20 00 04 7f 41 01 05 41 00 0b 0b", branchHint(5, 1)), Bytecode.IF, 5, 255, 0);
    }

    @Test
    public void testInvalidHintTargetDoesNotSuppressLaterHint() {
        final byte[] hints = bytes(1, 0, 2, 1, 1, 1, 8, 1, 1);
        assertBranchProfile(module(EMPTY_INTS, "20 00 04 40 0b 20 00 04 40 0b 41 00 0b", new byte[][]{hints}), Bytecode.IF, 1, 5, 255, 0);
    }

    @Test
    public void testInvalidHintTargetIgnored() {
        assertBranchProfile(moduleWithIf(branchHint(1, 1)), Bytecode.IF, 5, 0, 0);
    }

    @Test
    public void testMalformedHintSectionIgnored() {
        assertBranchProfile(moduleWithIf(bytes(1, 0, 1, 3, 0, 1)), Bytecode.IF, 5, 0, 0);
    }

    @Test
    public void testMultiByteHintPayloadIgnored() {
        assertBranchProfile(moduleWithIf(bytes(1, 0, 1, 3, 1, 0x81, 0)), Bytecode.IF, 5, 0, 0);
    }

    @Test
    public void testDuplicateAfterMalformedHintSectionIgnored() {
        assertBranchProfile(moduleWithIf(bytes(1, 0, 1, 3, 0, 1), branchHint(3, 1)), Bytecode.IF, 5, 0, 0);
    }

    @Test
    public void testDuplicateHintSectionIgnored() {
        assertBranchProfile(moduleWithIf(branchHint(3, 1), branchHint(3, 0)), Bytecode.IF, 5, 255, 0);
    }

    private static byte[] branchHint(int instructionOffset, int hint) {
        return bytes(1, 0, 1, instructionOffset, 1, hint);
    }

    private static void assertBranchProfile(byte[] moduleBytes, int opcode, int profileOffset, int expectedTrue, int expectedFalse) {
        assertBranchProfile(moduleBytes, opcode, 0, profileOffset, expectedTrue, expectedFalse);
    }

    private static void assertBranchProfile(byte[] moduleBytes, int opcode, int opcodeOccurrence, int profileOffset, int expectedTrue, int expectedFalse) {
        try (Context context = Context.newBuilder(WasmLanguage.ID).build()) {
            WasmTestUtils.runInWasmContext(context, c -> {
                WebAssembly wasm = new WebAssembly(c);
                WasmModule module = wasm.moduleDecode(moduleBytes);
                byte[] bytecode = module.bytecode();
                CodeEntry codeEntry = module.codeEntries()[0];
                int offset = findOpcode(bytecode, codeEntry, opcode, opcodeOccurrence);
                assertEquals(expectedTrue, BinaryStreamParser.rawPeekU8(bytecode, offset + profileOffset));
                assertEquals(expectedFalse, BinaryStreamParser.rawPeekU8(bytecode, offset + profileOffset + 1));
            });
        }
    }

    private static int findOpcode(byte[] bytecode, CodeEntry codeEntry, int opcode, int occurrence) {
        int remainingOccurrences = occurrence;
        for (int i = codeEntry.bytecodeStartOffset(); i < codeEntry.bytecodeEndOffset(); i++) {
            if (BinaryStreamParser.rawPeekU8(bytecode, i) == opcode) {
                if (remainingOccurrences == 0) {
                    return i;
                }
                remainingOccurrences--;
            }
        }
        fail("opcode not found: " + opcode);
        return -1;
    }

    private static byte[] moduleWithIf(byte[]... branchHintPayloads) {
        return module(EMPTY_INTS, "20 00 04 7f 41 01 05 41 00 0b 0b", branchHintPayloads);
    }

    private static byte[] moduleWithBrIf(byte[] branchHintPayload) {
        return module(EMPTY_INTS, "02 7f 41 01 20 00 0d 00 1a 41 00 0b 0b", new byte[][]{branchHintPayload});
    }

    private static byte[] module(int[] locals, String code, byte[]... branchHintPayloads) {
        BinaryBuilder b = newBuilder();
        b.addType(new int[]{WasmType.I32_TYPE}, new int[]{WasmType.I32_TYPE});
        for (byte[] branchHintPayload : branchHintPayloads) {
            b.addCustomSectionBeforeCode(BRANCH_HINT_SECTION, branchHintPayload);
        }
        b.addFunction(0, locals, code).addFunctionExport(0, "f");
        return b.build();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }
}
