/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package test.com.sun.max.asm.sparc;

import static com.sun.max.asm.sparc.GPR.*;

import java.io.*;

import junit.framework.*;
import test.com.sun.max.asm.*;

import com.sun.max.asm.*;
import com.sun.max.asm.Assembler.*;
import com.sun.max.asm.dis.sparc.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.asm.sparc.complete.*;
import com.sun.max.ide.*;
import com.sun.max.lang.*;

/**
 */
public class InliningAndAlignmentTest extends MaxTestCase {

    public InliningAndAlignmentTest() {
        super();

    }

    public InliningAndAlignmentTest(String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(InliningAndAlignmentTest.class.getName());
        suite.addTestSuite(InliningAndAlignmentTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(InliningAndAlignmentTest.class);
    }

    private void disassemble(SPARCDisassembler disassembler, byte[] bytes, InlineDataDecoder inlineDataDecoder) throws IOException, AssemblyException {
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    private byte[] assembleInlineData(SPARCAssembler asm, long startAddress, int pointerSize, InlineDataRecorder recorder) throws IOException, AssemblyException {
        final Directives dir = asm.directives();
        final Label label1 = new Label();

        asm.ba(label1);
        asm.nop();

        final byte byteValue = (byte) 0x77;
        final Label inlinedByte = new Label();
        asm.bindLabel(inlinedByte);
        dir.inlineByte(byteValue);

        final short shortValue = (short) 0xABCD;
        final Label inlinedShort = new Label();
        asm.bindLabel(inlinedShort);
        dir.inlineShort(shortValue);

        final int intValue = 0x12345678;
        final Label inlinedInt = new Label();
        asm.bindLabel(inlinedInt);
        dir.inlineInt(intValue);

        final long longValue = 0x12345678CAFEBABEL;
        final Label inlinedLong = new Label();
        asm.bindLabel(inlinedLong);
        dir.inlineLong(longValue);

        final byte[] byteArrayValue = {1, 2, 3, 4, 5};
        final Label inlinedByteArray = new Label();
        asm.bindLabel(inlinedByteArray);
        dir.inlineByteArray(byteArrayValue);

        final Label labelValue = label1;
        final Label inlinedLabel = new Label();
        asm.bindLabel(inlinedLabel);
        dir.inlineAddress(labelValue);

        final Label inlinedPaddingByte = new Label();
        asm.bindLabel(inlinedPaddingByte);
        dir.inlineByte((byte) 0);

        final Label unalignedLabel = new Label();
        asm.bindLabel(unalignedLabel);

        dir.align(4);
        asm.bindLabel(label1);
        asm.nop();

        // retrieve the byte stream output of the assembler and confirm that the inlined data is in the expected format, and are aligned correctly
        final byte[] asmBytes = asm.toByteArray(recorder);

        assertTrue(ByteUtils.checkBytes(ByteUtils.toByteArray(byteValue), asmBytes, inlinedByte.position()));
        assertEquals(1, inlinedShort.position() - inlinedByte.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toBigEndByteArray(shortValue), asmBytes, inlinedShort.position()));
        assertEquals(2, inlinedInt.position() - inlinedShort.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toBigEndByteArray(intValue), asmBytes, inlinedInt.position()));
        assertEquals(4, inlinedLong.position() - inlinedInt.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toBigEndByteArray(0x12345678CAFEBABEL), asmBytes, inlinedLong.position()));
        assertEquals(8, inlinedByteArray.position() - inlinedLong.position());

        assertTrue(ByteUtils.checkBytes(byteArrayValue, asmBytes, inlinedByteArray.position()));
        assertEquals(5, inlinedLabel.position() - inlinedByteArray.position());

        if (pointerSize == 4) {
            assertTrue(ByteUtils.checkBytes(ByteUtils.toBigEndByteArray((int) (startAddress + labelValue.position())), asmBytes, inlinedLabel.position()));
        } else if (pointerSize == 8) {
            assertTrue(ByteUtils.checkBytes(ByteUtils.toBigEndByteArray(startAddress + labelValue.position()), asmBytes, inlinedLabel.position()));
        }
        assertEquals(pointerSize, inlinedPaddingByte.position() - inlinedLabel.position());

        return asmBytes;
    }

    public void testInlineData32() throws IOException, AssemblyException {
        System.out.println("--- testInlineData32: ---");
        final int startAddress = 0x12345678;
        final SPARC32Assembler assembler = new SPARC32Assembler(startAddress);
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleInlineData(assembler, startAddress, 4, recorder);
        final SPARC32Disassembler disassembler = new SPARC32Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
        disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
        System.out.println();
    }

    public void testInlineData64() throws IOException, AssemblyException {
        System.out.println("--- testInlineData64: ---");
        final long startAddress = 0x1234567812340000L;
        final SPARC64Assembler assembler = new SPARC64Assembler(startAddress);
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleInlineData(assembler, startAddress, 8, recorder);
        final SPARC64Disassembler disassembler = new SPARC64Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
        disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
        System.out.println();
    }

    private byte[] assembleAlignmentPadding(SPARCAssembler asm, long startAddress, InlineDataRecorder recorder) throws IOException, AssemblyException {
        // test memory alignment directives from 1 byte to 16 bytes
        final Directives dir = asm.directives();

        final Label unalignedLabel1 = new Label();
        final Label alignedLabel1 = new Label();

        final Label unalignedLabel2 = new Label();
        final Label alignedLabel2 = new Label();

        final Label unalignedLabel4By1 = new Label();
        final Label alignedLabel4By1 = new Label();

        final Label unalignedLabel4By2 = new Label();
        final Label alignedLabel4By2 = new Label();

        final Label unalignedLabel4By3 = new Label();
        final Label alignedLabel4By3 = new Label();

        final Label unalignedLabel8 = new Label();
        final Label alignedLabel8 = new Label();

        final Label unalignedLabel16 = new Label();
        final Label alignedLabel16 = new Label();

        final Label done = new Label();

        asm.ba(done);
        dir.inlineByteArray(new byte[]{1}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel1);
        dir.align(1);
        asm.bindLabel(alignedLabel1);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel2);
        dir.align(2);
        asm.bindLabel(alignedLabel2);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel4By1);
        dir.align(4);
        asm.bindLabel(alignedLabel4By1);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1, 2}); // padding to make the following unaligned by 2 bytes
        asm.bindLabel(unalignedLabel4By2);
        dir.align(4);
        asm.bindLabel(alignedLabel4By2);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1, 2, 3}); // padding to make the following unaligned by 3 bytes
        asm.bindLabel(unalignedLabel4By3);
        dir.align(4);
        asm.bindLabel(alignedLabel4By3);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel8);
        dir.align(8);
        asm.bindLabel(alignedLabel8);
        asm.nop();

        dir.align(4);
        asm.ba(done);
        dir.inlineByteArray(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9});  // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel16);
        dir.align(16);
        asm.bindLabel(alignedLabel16);
        asm.nop();

        dir.align(4);
        asm.bindLabel(done);
        asm.nop();

        // check the memory alignment (and that the memory locations were unaligned before the alignment directives)
        final byte[] asmCode = asm.toByteArray(recorder);

        assertEquals(1, (startAddress + unalignedLabel2.position()) % 2);
        assertEquals(0, (startAddress + alignedLabel2.position()) % 2);

        assertEquals(1, (startAddress + unalignedLabel4By1.position()) % 4);
        assertEquals(0, (startAddress + alignedLabel4By1.position()) % 4);

        assertEquals(2, (startAddress + unalignedLabel4By2.position()) % 4);
        assertEquals(0, (startAddress + alignedLabel4By2.position()) % 4);

        assertEquals(3, (startAddress + unalignedLabel4By3.position()) % 4);
        assertEquals(0, (startAddress + alignedLabel4By3.position()) % 4);

        assertEquals(1, (startAddress + unalignedLabel8.position()) % 8);
        assertEquals(0, (startAddress + alignedLabel8.position()) % 8);

        assertEquals(1, (startAddress + unalignedLabel16.position()) % 16);
        assertEquals(0, (startAddress + alignedLabel16.position()) % 16);

        assertEquals(0, (startAddress + done.position()) % 4);

        return asmCode;
    }

    public void testAlignmentPadding32() throws IOException, AssemblyException {
        System.out.println("--- testAlignmentPadding32: ---");
        final int startAddress = 0x12345678;
        final SPARC32Assembler assembler = new SPARC32Assembler(startAddress);
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleAlignmentPadding(assembler, startAddress, recorder);
        final SPARC32Disassembler disassembler = new SPARC32Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
        disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
        System.out.println();
    }

    public void testAlignmentPadding64() throws IOException, AssemblyException {
        System.out.println("--- testAlignmentPadding64: ---");
        final long startAddress = 0x1234567812345678L;
        final SPARC64Assembler assembler = new SPARC64Assembler(startAddress);
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleAlignmentPadding(assembler, startAddress, recorder);
        final SPARC64Disassembler disassembler = new SPARC64Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
        disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
        System.out.println();
    }

    private byte[] assembleSwitchTable(SPARCAssembler asm, int[] matches, InlineDataRecorder inlineDataRecorder) throws IOException, AssemblyException {
        final Directives directives = asm.directives();

        final Label[] matchTargets = new Label[matches.length];
        final Label end = new Label();
        final Label table = new Label();
        for (int i = 0; i < matches.length; i++) {
            matchTargets[i] = new Label();
        }
        final int min = Ints.min(matches);
        final int max = Ints.max(matches);
        final int numElements = max - min + 1;

        final GPR tagRegister = L0;
        final GPR scratchRegister = L1;
        final GPR indexRegister = L3;
        final GPR tableRegister = L1;

        final int imm = min;
        if (imm != 0) {
            if (isSimm13(imm)) {
                asm.sub(tagRegister, imm, tagRegister);
            } else {
                asm.setsw(imm, scratchRegister);
                asm.sub(tagRegister, scratchRegister, tagRegister);
            }
        }
        if (isSimm13(numElements)) {
            asm.cmp(tagRegister, numElements);
        } else {
            asm.setuw(numElements, scratchRegister);
            asm.cmp(tagRegister, scratchRegister);
        }
        final Label here = new Label();
        asm.bindLabel(here);
        asm.rd(StateRegister.PC, tableRegister);
        // Branch on unsigned greater than or equal.
        asm.bcc(AnnulBit.NO_A, end);
        // complete loading of the jump table in the delay slot, regardless of whether we're taking the branch.
        asm.addcc(tableRegister, here, table, tableRegister);
        asm.sll(tagRegister, 2, indexRegister);
        asm.ldsw(tableRegister, indexRegister, O7);
        asm.jmp(tableRegister, O7);
        asm.nop(); // delay slot
        asm.bindLabel(table);
        for (int i = 0; i < matches.length; i++) {
            directives.inlineOffset(matchTargets[i], table, WordWidth.BITS_32);
            if (i + 1 < matches.length) {
                // jump to the default target for any "missing" entries
                final int currentMatch = matches[i];
                final int nextMatch = matches[i + 1];
                for (int j = currentMatch + 1; j < nextMatch; j++) {
                    directives.inlineOffset(end, table, WordWidth.BITS_32);
                }
            }
        }
        inlineDataRecorder.add(new InlineDataDescriptor.JumpTable32(table, min, max));

        for (int i = 0; i < matches.length; ++i) {
            asm.bindLabel(matchTargets[i]);
            final int match = matches[i];
            if (isSimm13(match)) {
                asm.sub(tagRegister, match, tagRegister);
            } else {
                asm.setsw(match, scratchRegister);
                asm.sub(tagRegister, scratchRegister, tagRegister);
            }
            asm.ba(end);
        }
        asm.bindLabel(end);
        asm.nop();

        return asm.toByteArray(inlineDataRecorder);
    }

    private boolean isSimm13(final int imm) {
        return Ints.numberOfEffectiveSignedBits(imm) <= 13;
    }

    private static int[][] switchTableInputs = {
        new int[] {0, 1, 3, 4, 6, 7},
        new int[] {3, 4, 6, 7, 10},
        new int[] {-4, -2, 7, 10, 6}
    };

    public void test_switchTable32() throws IOException, AssemblyException {
        for (int[] matches : switchTableInputs) {
            System.out.println("--- testSwitchTable32:{" + Ints.toString(matches, ", ") + "} ---");
            final int startAddress = 0x12345678;
            final SPARC32Assembler assembler = new SPARC32Assembler(startAddress);
            final InlineDataRecorder recorder = new InlineDataRecorder();

            final byte[] bytes = assembleSwitchTable(assembler, matches, recorder);
            final SPARC32Disassembler disassembler = new SPARC32Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
            disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
            System.out.println();
        }
    }

    public void test_switchTable64() throws IOException, AssemblyException {
        for (int[] matches : switchTableInputs) {
            System.out.println("--- testSwitchTable64:{" + Ints.toString(matches, ", ") + "} ---");
            final long startAddress = 0x1234567812340000L;
            final SPARCAssembler assembler = new SPARC64Assembler(startAddress);
            final InlineDataRecorder recorder = new InlineDataRecorder();

            final byte[] bytes = assembleSwitchTable(assembler, matches, recorder);
            final SPARCDisassembler disassembler = new SPARC64Disassembler(startAddress, InlineDataDecoder.createFrom(recorder));
            disassemble(disassembler, bytes, InlineDataDecoder.createFrom(recorder));
            System.out.println();
        }
    }
}
