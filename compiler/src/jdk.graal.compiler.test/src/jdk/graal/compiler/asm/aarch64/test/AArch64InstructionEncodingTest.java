/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2021, Arm Limited. All rights reserved.
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
package jdk.graal.compiler.asm.aarch64.test;

import static org.junit.Assume.assumeTrue;

import java.nio.ByteBuffer;

import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ASIMDSize;
import jdk.graal.compiler.asm.aarch64.AArch64ASIMDAssembler.ElementSize;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.test.GraalTest;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.runtime.JVMCI;

public class AArch64InstructionEncodingTest extends GraalTest {
    @Before
    public void checkAArch64() {
        assumeTrue("skipping non AArch64 specific test", JVMCI.getRuntime().getHostJVMCIBackend().getTarget().arch instanceof AArch64);
    }

    private abstract class AArch64InstructionEncodingTestCase {
        private byte[] actual;
        private byte[] expected;
        TestProtectedAssembler assembler;

        AArch64InstructionEncodingTestCase(int expected) {
            this.expected = ByteBuffer.allocate(Integer.BYTES).putInt(expected).array();
            TargetDescription target = JVMCI.getRuntime().getHostJVMCIBackend().getTarget();
            assembler = new TestProtectedAssembler(target);
        }

        int getExpected() {
            return ByteBuffer.wrap(expected).getInt();
        }

        int getActual() {
            return ByteBuffer.wrap(actual).getInt();
        }

        void closeAssembler() {
            this.actual = assembler.close(true);
        }
    }

    private class CntEncodingTestCase extends AArch64InstructionEncodingTestCase {
        CntEncodingTestCase(int expected, ASIMDSize size, Register dst, Register src) {
            super(expected);
            assembler.neon.cntVV(size, dst, src);
            closeAssembler();
        }
    }

    private class AddvEncodingTestCase extends AArch64InstructionEncodingTestCase {
        AddvEncodingTestCase(int expected, ASIMDSize size, ElementSize laneWidth, Register dst, Register src) {
            super(expected);
            assembler.neon.addvSV(size, laneWidth, dst, src);
            closeAssembler();
        }
    }

    private class UmovEncodingTestCase extends AArch64InstructionEncodingTestCase {
        UmovEncodingTestCase(int expected, ElementSize eSize, Register dst, int srcIdx, Register src) {
            super(expected);
            assembler.neon.umovGX(eSize, dst, src, srcIdx);
            closeAssembler();
        }
    }

    private class Ld1MultipleVTestEncoding extends AArch64InstructionEncodingTestCase {
        Ld1MultipleVTestEncoding(int expected, ASIMDSize size, ElementSize eSize, Register dst, AArch64Address addr) {
            super(expected);
            assembler.neon.ld1MultipleV(size, eSize, dst, addr);
            closeAssembler();
        }
    }

    private class SqdmulhEncodingTestCase extends AArch64InstructionEncodingTestCase {
        SqdmulhEncodingTestCase(int expected, ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
            super(expected);
            assembler.neon.sqdmulhVVV(size, eSize, dst, src1, src2);
            closeAssembler();
        }
    }

    private class ShsubEncodingTestCase extends AArch64InstructionEncodingTestCase {
        ShsubEncodingTestCase(int expected, ASIMDSize size, ElementSize eSize, Register dst, Register src1, Register src2) {
            super(expected);
            assembler.neon.shsubVVV(size, eSize, dst, src1, src2);
            closeAssembler();
        }
    }

    private class SliEncodingTestCase extends AArch64InstructionEncodingTestCase {
        SliEncodingTestCase(int expected, ASIMDSize size, ElementSize eSize, Register dst, Register src, int shift) {
            super(expected);
            assembler.neon.sliVVI(size, eSize, dst, src, shift);
            closeAssembler();
        }
    }

    private class St4SingleEncodingTestCase extends AArch64InstructionEncodingTestCase {
        St4SingleEncodingTestCase(int expected, ElementSize eSize, Register src1, Register src2, Register src3, Register src4, int lane, AArch64Address addr) {
            super(expected);
            assembler.neon.st4SingleVVVV(eSize, src1, src2, src3, src4, lane, addr);
            closeAssembler();
        }
    }

    private class Ld4rEncodingTestCase extends AArch64InstructionEncodingTestCase {
        Ld4rEncodingTestCase(int expected, ASIMDSize size, ElementSize eSize, Register dst1, Register dst2, Register dst3, Register dst4, AArch64Address addr) {
            super(expected);
            assembler.neon.ld4rVVVV(size, eSize, dst1, dst2, dst3, dst4, addr);
            closeAssembler();
        }
    }

    private static final int invalidInstructionCode = 0x00000000;

    private void assertWrapper(AArch64InstructionEncodingTestCase testCase) {
        assertDeepEquals(testCase.getActual(), testCase.getExpected());
    }

    @Test
    public void testCnt() {
        assertWrapper(new CntEncodingTestCase(0x0058200e, ASIMDSize.HalfReg, AArch64.v0, AArch64.v0));
        assertWrapper(new CntEncodingTestCase(0x3f58204e, ASIMDSize.FullReg, AArch64.v31, AArch64.v1));
    }

    @Test
    public void testAddv() {
        assertWrapper(new AddvEncodingTestCase(0x20b8310e, ASIMDSize.HalfReg, ElementSize.Byte, AArch64.v0, AArch64.v1));
        assertWrapper(new AddvEncodingTestCase(0x42b8314e, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v2, AArch64.v2));
        assertWrapper(new AddvEncodingTestCase(0xd2ba710e, ASIMDSize.HalfReg, ElementSize.HalfWord, AArch64.v18, AArch64.v22));
        assertWrapper(new AddvEncodingTestCase(0x77ba714e, ASIMDSize.FullReg, ElementSize.HalfWord, AArch64.v23, AArch64.v19));
        assertWrapper(new AddvEncodingTestCase(0x18bbb14e, ASIMDSize.FullReg, ElementSize.Word, AArch64.v24, AArch64.v24));
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testAddvWithInvalidSizeLaneCombo() {
        new AddvEncodingTestCase(invalidInstructionCode, ASIMDSize.HalfReg, ElementSize.Word, AArch64.v0, AArch64.v1);
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testAddvWithInvalidLane() {
        new AddvEncodingTestCase(invalidInstructionCode, ASIMDSize.FullReg, ElementSize.DoubleWord, AArch64.v0, AArch64.v1);
    }

    @Test
    public void testUmov() {
        assertWrapper(new UmovEncodingTestCase(0x1f3c084e, ElementSize.DoubleWord, AArch64.r31, 0, AArch64.v0));
        assertWrapper(new UmovEncodingTestCase(0xe13f184e, ElementSize.DoubleWord, AArch64.r1, 1, AArch64.v31));

        assertWrapper(new UmovEncodingTestCase(0x5d3c040e, ElementSize.Word, AArch64.r29, 0, AArch64.v2));
        assertWrapper(new UmovEncodingTestCase(0x833f1c0e, ElementSize.Word, AArch64.r3, 3, AArch64.v28));

        assertWrapper(new UmovEncodingTestCase(0x4b3d020e, ElementSize.HalfWord, AArch64.r11, 0, AArch64.v10));
        assertWrapper(new UmovEncodingTestCase(0x893d1e0e, ElementSize.HalfWord, AArch64.r9, 7, AArch64.v12));

        assertWrapper(new UmovEncodingTestCase(0x0d3d010e, ElementSize.Byte, AArch64.r13, 0, AArch64.v8));
        assertWrapper(new UmovEncodingTestCase(0xc73d1f0e, ElementSize.Byte, AArch64.r7, 15, AArch64.v14));
    }

    @Test(expected = AssertionError.class)
    @SuppressWarnings("unused")
    public void testUmovInvalidSrcIdx() {
        new UmovEncodingTestCase(invalidInstructionCode, ElementSize.DoubleWord, AArch64.r0, 2, AArch64.v0);
    }

    @Test
    public void testLd1MultipleV() {
        AArch64Address addr = AArch64Address.createStructureNoOffsetAddress(AArch64.r0);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070404c, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v0, addr));
        addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_1R, ASIMDSize.FullReg, ElementSize.Byte, AArch64.r0, 16);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070df4c, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v0, addr));
        addr = AArch64Address.createStructureNoOffsetAddress(AArch64.r0);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070400c, ASIMDSize.HalfReg, ElementSize.Byte, AArch64.v0, addr));
        addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD1_MULTIPLE_1R, ASIMDSize.HalfReg, ElementSize.Byte, AArch64.r0, 8);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070df0c, ASIMDSize.HalfReg, ElementSize.Byte, AArch64.v0, addr));
        addr = AArch64Address.createStructureRegisterPostIndexAddress(AArch64.r0, AArch64.r1);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070c14c, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v0, addr));
        addr = AArch64Address.createStructureRegisterPostIndexAddress(AArch64.r0, AArch64.r1);
        assertWrapper(new Ld1MultipleVTestEncoding(0x0070c10c, ASIMDSize.HalfReg, ElementSize.Byte, AArch64.v0, addr));
    }

    @Test
    public void testSqdmulh() {
        assertWrapper(new SqdmulhEncodingTestCase(0x20b4a24e, ASIMDSize.FullReg, ElementSize.Word, AArch64.v0, AArch64.v1, AArch64.v2));
        assertWrapper(new SqdmulhEncodingTestCase(0xdfb77d4e, ASIMDSize.FullReg, ElementSize.HalfWord, AArch64.v31, AArch64.v30, AArch64.v29));
    }

    @Test
    public void testShsub() {
        assertWrapper(new ShsubEncodingTestCase(0x8324a54e, ASIMDSize.FullReg, ElementSize.Word, AArch64.v3, AArch64.v4, AArch64.v5));
        assertWrapper(new ShsubEncodingTestCase(0x0725294e, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v7, AArch64.v8, AArch64.v9));
    }

    @Test
    public void testSli() {
        assertWrapper(new SliEncodingTestCase(0x8354276f, ASIMDSize.FullReg, ElementSize.Word, AArch64.v3, AArch64.v4, 7));
        assertWrapper(new SliEncodingTestCase(0xdf57096f, ASIMDSize.FullReg, ElementSize.Byte, AArch64.v31, AArch64.v30, 1));
    }

    @Test
    public void testSt4Single() {
        AArch64Address addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.ST4, ASIMDSize.FullReg, ElementSize.Word, AArch64.r4, 16);
        assertWrapper(new St4SingleEncodingTestCase(0x80a0bf0d, ElementSize.Word, AArch64.v0, AArch64.v1, AArch64.v2, AArch64.v3, 0, addr));
        addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.ST4, ASIMDSize.FullReg, ElementSize.Word, AArch64.r5, 16);
        assertWrapper(new St4SingleEncodingTestCase(0xbcb0bf4d, ElementSize.Word, AArch64.v28, AArch64.v29, AArch64.v30, AArch64.v31, 3, addr));
    }

    @Test
    public void testLd4r() {
        AArch64Address addr = AArch64Address.createStructureImmediatePostIndexAddress(AArch64ASIMDAssembler.ASIMDInstruction.LD4R, ASIMDSize.FullReg, ElementSize.Word, AArch64.r6, 16);
        assertWrapper(new Ld4rEncodingTestCase(0xc8e8ff4d, ASIMDSize.FullReg, ElementSize.Word, AArch64.v8, AArch64.v9, AArch64.v10, AArch64.v11, addr));
    }
}
