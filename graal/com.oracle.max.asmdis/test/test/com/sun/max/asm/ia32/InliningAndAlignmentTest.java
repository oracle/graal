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
package test.com.sun.max.asm.ia32;

import static com.sun.max.asm.ia32.IA32GeneralRegister32.*;
import static com.sun.max.asm.ia32.IA32BaseRegister32.*;
import static com.sun.max.asm.ia32.IA32IndexRegister32.*;
import static com.sun.max.asm.x86.Scale.*;

import java.io.*;

import junit.framework.*;
import test.com.sun.max.asm.*;

import com.sun.max.asm.*;
import com.sun.max.asm.Assembler.*;
import com.sun.max.asm.dis.ia32.*;
import com.sun.max.asm.ia32.complete.*;
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

    private void disassemble(int startAddress, byte[] bytes, InlineDataDecoder inlineDataDecoder) throws IOException, AssemblyException {
        final IA32Disassembler disassembler = new IA32Disassembler(startAddress, inlineDataDecoder);
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    private byte[] assembleInlinedData(int startAddress, InlineDataRecorder recorder) throws IOException, AssemblyException {
        // tests inlining of various data types
        final IA32Assembler asm = new IA32Assembler(startAddress);
        final Directives dir = asm.directives();
        final Label skip = new Label();

        asm.mov(EAX, 0x12345678);
        asm.jmp(skip);

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

        final Label labelValue = skip;
        final Label inlinedLabel = new Label();
        asm.bindLabel(inlinedLabel);
        dir.inlineAddress(labelValue);

        final Label inlinedPaddingByte = new Label();
        asm.bindLabel(inlinedPaddingByte);
        dir.inlineByte((byte) 0);

        dir.align(8);
        asm.mov(EBX, 0xFFFFFFFF);
        asm.bindLabel(skip);
        asm.mov(ECX, 0xCAFEBABE);

        // retrieve the byte stream output of the assembler and confirm that the inlined data is in the expected format, and are aligned correctly
        final byte[] asmBytes = asm.toByteArray(recorder);

        assertTrue(ByteUtils.checkBytes(ByteUtils.toByteArray(byteValue), asmBytes, inlinedByte.position()));
        assertEquals(1, inlinedShort.position() - inlinedByte.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toLittleEndByteArray(shortValue), asmBytes, inlinedShort.position()));
        assertEquals(2, inlinedInt.position() - inlinedShort.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toLittleEndByteArray(intValue), asmBytes, inlinedInt.position()));
        assertEquals(4, inlinedLong.position() - inlinedInt.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toLittleEndByteArray(0x12345678CAFEBABEL), asmBytes, inlinedLong.position()));
        assertEquals(8, inlinedByteArray.position() - inlinedLong.position());

        assertTrue(ByteUtils.checkBytes(byteArrayValue, asmBytes, inlinedByteArray.position()));
        assertEquals(5, inlinedLabel.position() - inlinedByteArray.position());

        assertTrue(ByteUtils.checkBytes(ByteUtils.toLittleEndByteArray(asm.startAddress() + labelValue.position()), asmBytes, inlinedLabel.position()));
        assertEquals(asm.wordWidth().numberOfBytes, inlinedPaddingByte.position() - inlinedLabel.position());

        return asmBytes;
    }

    private final int intStartAddress = 0x12345678;

    public void testInlinedData() throws IOException, AssemblyException {
        System.out.println("--- testInlinedData: ---");
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleInlinedData(intStartAddress, recorder);
        disassemble(intStartAddress, bytes, InlineDataDecoder.createFrom(recorder));
    }

    private byte[] assembleAlignmentPadding(int startAddress, InlineDataRecorder recorder) throws IOException, AssemblyException {
        // test memory alignment directives from 1 byte to 16 bytes
        final IA32Assembler asm = new IA32Assembler(startAddress);
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

        asm.jmp(alignedLabel1);
        asm.bindLabel(unalignedLabel1);
        dir.align(1);
        asm.bindLabel(alignedLabel1);
        asm.nop();

        asm.jmp(alignedLabel2);
        asm.bindLabel(unalignedLabel2);
        dir.align(2);
        asm.bindLabel(alignedLabel2);
        asm.nop();

        asm.jmp(alignedLabel4By1);
        dir.inlineByteArray(new byte[]{}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel4By1);
        dir.align(4);
        asm.bindLabel(alignedLabel4By1);
        asm.nop();

        asm.jmp(alignedLabel4By2);
        dir.inlineByteArray(new byte[]{1, 2, 3}); // padding to make the following unaligned by 2 bytes
        asm.bindLabel(unalignedLabel4By2);
        dir.align(4);
        asm.bindLabel(alignedLabel4By2);
        asm.nop();

        asm.jmp(alignedLabel4By3);
        dir.inlineByteArray(new byte[]{}); // padding to make the following unaligned by 3 bytes
        asm.bindLabel(unalignedLabel4By3);
        dir.align(4);
        asm.bindLabel(alignedLabel4By3);
        asm.nop();

        asm.jmp(alignedLabel8);
        dir.inlineByteArray(new byte[]{1, 2, 3, 4, 5, 6}); // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel8);
        dir.align(8);
        asm.bindLabel(alignedLabel8);
        asm.nop();

        asm.jmp(alignedLabel16);
        dir.inlineByteArray(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14});  // padding to make the following unaligned by 1 byte
        asm.bindLabel(unalignedLabel16);
        dir.align(16);
        asm.bindLabel(alignedLabel16);
        asm.nop();

        // check the memory alignment (and that the memory locations were unaligned before the alignment directives)
        final byte[] asmCode = asm.toByteArray(recorder);

        assertEquals(1, (asm.startAddress() + unalignedLabel2.position()) % 2);
        assertEquals(0, (asm.startAddress() + alignedLabel2.position()) % 2);

        assertEquals(1, (asm.startAddress() + unalignedLabel4By1.position()) % 4);
        assertEquals(0, (asm.startAddress() + alignedLabel4By1.position()) % 4);

        assertEquals(2, (asm.startAddress() + unalignedLabel4By2.position()) % 4);
        assertEquals(0, (asm.startAddress() + alignedLabel4By2.position()) % 4);

        assertEquals(3, (asm.startAddress() + unalignedLabel4By3.position()) % 4);
        assertEquals(0, (asm.startAddress() + alignedLabel4By3.position()) % 4);

        assertEquals(1, (asm.startAddress() + unalignedLabel8.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLabel8.position()) % 8);

        assertEquals(1, (asm.startAddress() + unalignedLabel16.position()) % 16);
        assertEquals(0, (asm.startAddress() + alignedLabel16.position()) % 16);

        return asmCode;
    }

    public void testAlignmentPadding() throws IOException, AssemblyException {
        System.out.println("--- testAlignmentPadding: ---");
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleAlignmentPadding(intStartAddress, recorder);
        disassemble(intStartAddress, bytes, InlineDataDecoder.createFrom(recorder));
    }

    private byte[] assembleJumpAndAlignmentPadding(int startAddress, InlineDataRecorder recorder) throws IOException, AssemblyException {
        // tests span dependent instruction processing for label and padding instructions
        final IA32Assembler asm = new IA32Assembler(startAddress);
        final Directives dir = asm.directives();
        final Label longJump = new Label();
        final Label shortJump = new Label();

        asm.jmp(longJump); // this instruction is initially 2 bytes long, but will expand to 5 bytes
        final byte[] nopArray = new byte [297];
        java.util.Arrays.fill(nopArray, (byte) 0x90);
        dir.inlineByteArray(nopArray);

        final Label unalignedLocation1 = new Label();
        asm.bindLabel(unalignedLocation1);
        dir.align(8); // initially creates 5 bytes padding, but will reduce to 2 bytes
        final Label alignedLocation1 = new Label();
        asm.bindLabel(alignedLocation1);

        assertEquals(3, (asm.startAddress() + unalignedLocation1.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation1.position()) % 8);

        asm.bindLabel(longJump);
        asm.jmp(shortJump);

        final Label unalignedLocation2 = new Label();
        asm.bindLabel(unalignedLocation2);
        dir.align(8);
        final Label alignedLocation2 = new Label();
        asm.bindLabel(alignedLocation2);
        asm.nop();

        assertEquals(2, (asm.startAddress() + unalignedLocation2.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation2.position()) % 8);

        asm.bindLabel(shortJump);
        asm.nop();
        asm.nop();

        final Label unalignedLocation3 = new Label();
        asm.bindLabel(unalignedLocation3);
        dir.align(8);
        final Label alignedLocation3 = new Label();
        asm.bindLabel(alignedLocation3);

        assertEquals(3, (asm.startAddress() + unalignedLocation3.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation3.position()) % 8);

        final byte[] asmCode = asm.toByteArray(recorder);

        assertEquals(6, (asm.startAddress() + unalignedLocation1.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation1.position()) % 8);

        assertEquals(2, (asm.startAddress() + unalignedLocation2.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation2.position()) % 8);

        assertEquals(3, (asm.startAddress() + unalignedLocation3.position()) % 8);
        assertEquals(0, (asm.startAddress() + alignedLocation3.position()) % 8);

        return asmCode;
    }

    public void testJumpAndAlignmentPadding() throws IOException, AssemblyException {
        System.out.println("--- testJumpAndAlignmentPadding: ---");
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleJumpAndAlignmentPadding(intStartAddress, recorder);
        disassemble(intStartAddress, bytes, InlineDataDecoder.createFrom(recorder));
    }

    private byte[] assembleInvalidInstructionDisassembly(int startAddress, InlineDataRecorder recorder) throws IOException, AssemblyException {
        // tests span dependent instruction processing for label and padding instructions
        final IA32Assembler asm = new IA32Assembler(startAddress);
        final Directives dir = asm.directives();
        final Label jumpTarget1 = new Label();
        final Label jumpTarget2 = new Label();
        final Label jumpTarget3 = new Label();

        asm.jmp(jumpTarget3);

        dir.inlineAddress(jumpTarget1);
        dir.inlineAddress(jumpTarget2);
        dir.inlineAddress(jumpTarget3);
        for (int i = 0; i <= 0xFF; i++) {
            dir.inlineByte((byte) i);
        }

        asm.bindLabel(jumpTarget1);
        asm.mov(EAX, 0x12345678);

        asm.bindLabel(jumpTarget2);
        asm.nop();

        dir.inlineByte((byte) 0xEB); // this is the first byte of a two-byte instruction
        asm.bindLabel(jumpTarget3);
        asm.nop();

        return asm.toByteArray(recorder);
    }

    public void testInvalidInstructionDisassembly() throws IOException, AssemblyException {
        System.out.println("--- testInvalidInstructionDisassembly: ---");
        final InlineDataRecorder recorder = new InlineDataRecorder();
        final byte[] bytes = assembleInvalidInstructionDisassembly(intStartAddress, recorder);
        disassemble(intStartAddress, bytes, InlineDataDecoder.createFrom(recorder));
    }

    /**
     * Assembles a switch table:
     *
     *  switch (rsi) {
     *      case match0: rcx = match0; break;
     *      ...
     *      case matchN: rcx = matchN; break;
     *  }
     */
    private byte[] assembleSwitchTable(int startAddress, int[] matches, InlineDataRecorder inlineDataRecorder) throws IOException, AssemblyException {
        final IA32Assembler asm = new IA32Assembler(startAddress);
        final Directives directives = asm.directives();

        final Label end = new Label();
        final Label table = new Label();
        final Label[] matchTargets = new Label[matches.length];
        for (int i = 0; i < matches.length; i++) {
            matchTargets[i] = new Label();
        }

        final int min = Ints.min(matches);
        final int max = Ints.max(matches);

        if (min != 0) {
            asm.subl(ESI, min);
        }
        final int numElements = max - min + 1;
        asm.cmpl(ESI, numElements);
        asm.jnb(end);
        asm.m_lea(EBX, table);
        asm.movsxw(ESI, EBX_BASE, ESI_INDEX, SCALE_4);
        asm.add(EBX, ESI);
        asm.jmp(EBX);
        asm.nop();

        directives.align(4);
        asm.bindLabel(table);

        for (int i = 0; i < matches.length; i++) {
            directives.inlineOffset(matchTargets[i], table, WordWidth.BITS_32);
            if (i + 1 < matches.length) {
                // jump to 'end' for any "missing" entries
                final int currentMatch = matches[i];
                final int nextMatch = matches[i + 1];
                for (int j = currentMatch + 1; j < nextMatch; j++) {
                    directives.inlineOffset(end, table, WordWidth.BITS_32);
                }
            }
        }
        inlineDataRecorder.add(new InlineDataDescriptor.JumpTable32(table, min, max));
        for (int i = 0; i < matches.length; i++) {
            directives.align(4);
            asm.bindLabel(matchTargets[i]);
            asm.mov(ECX, matches[i]);
            asm.jmp(end);
        }
        asm.bindLabel(end);
        asm.nop();

        return asm.toByteArray(inlineDataRecorder);
    }

    public void test_switchTable() throws IOException, AssemblyException {
        final int[][] inputs = {
            new int[] {0, 1, 3, 4, 6, 7},
            new int[] {3, 4, 6, 7, 10},
            new int[] {-4, -2, 7, 10, 6}
        };
        for (int[] matches : inputs) {
            System.out.println("--- testSwitchTable:{" + Ints.toString(matches, ", ") + "} ---");
            final InlineDataRecorder inlineDataRecorder = new InlineDataRecorder();
            final byte[] bytes = assembleSwitchTable(intStartAddress, matches, inlineDataRecorder);
            disassemble(intStartAddress, bytes, InlineDataDecoder.createFrom(inlineDataRecorder));
        }
    }

}
