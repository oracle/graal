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
package test.com.sun.max.asm.amd64;

import static com.sun.max.asm.amd64.AMD64GeneralRegister32.*;
import static com.sun.max.asm.amd64.AMD64GeneralRegister64.*;
import static com.sun.max.asm.amd64.AMD64GeneralRegister8.*;
import static com.sun.max.asm.amd64.AMD64IndexRegister64.*;
import static com.sun.max.asm.x86.Scale.*;

import java.io.*;

import junit.framework.*;

import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.asm.amd64.complete.*;
import com.sun.max.asm.dis.amd64.*;
import com.sun.max.ide.*;

/**
 */
public class InternalTest extends MaxTestCase {

    public InternalTest() {
        super();

    }

    public InternalTest(String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(InternalTest.class.getName());
        suite.addTestSuite(InternalTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(InternalTest.class);
    }

    private void disassemble(long startAddress, byte[] bytes) throws IOException, AssemblyException {
        final AMD64Disassembler disassembler = new AMD64Disassembler(startAddress, null);
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    public void testRedundantInstruction() throws IOException, AssemblyException {
        System.out.println("--- testRedundantInstruction: ---");
        final AMD64Assembler asm = new AMD64Assembler(0L);
        asm.mov(RBP, RSP);
        byte[] bytes = asm.toByteArray();
        assert bytes[0] == (byte) 0x48;
        assert bytes[1] == (byte) 0x89;
        assert bytes[2] == (byte) 0xE5;
        disassemble(0L, bytes);

        bytes = new byte[]{(byte) 0x48, (byte) 0x8B, (byte) 0xEC}; // redundant: same effect as the above, but different opcode
        disassemble(0L, bytes);
    }

    public void testXMMMove() throws IOException, AssemblyException {
        System.out.println("--- testXMMMove: ---");
        final AMD64Assembler asm = new AMD64Assembler(0L);
        asm.movq(8, RSP.indirect(), AMD64XMMRegister.XMM4);
        byte[] bytes = asm.toByteArray();
        System.out.println(bytes);
        disassemble(0L, bytes);

    }

    private byte[] assemble1(long startAddress) throws IOException, AssemblyException {
        final AMD64GeneralRegister64 myGPR = RAX;
        final AMD64Assembler asm = new AMD64Assembler(startAddress);
        final Label startLabel = new Label();
        final Label endLabel = new Label();
        final Label label1 = new Label();
        final Label label2 = new Label();
        final Label label3 = new Label();
        final Label fixLabel = new Label();

        asm.bindLabel(startLabel);
        asm.jmp(label1);
        asm.jmp(startLabel);
        asm.call(fixLabel);
        asm.add(RDX.indirect(), BL);
        asm.add(RAX.base(), RBX_INDEX, SCALE_2, CL);
        asm.m_add(0x12345678, RBP.index(), SCALE_1, AH);
        asm.rip_add(label1, BH);
        asm.rip_add(EBX, label1);
        asm.add(CL, DL);
        asm.fixLabel(fixLabel, startAddress + 4); // choose the right addend according to output to hit an instruction start address
        asm.jmp(fixLabel);
        asm.jmp(label3);
        asm.bindLabel(label3);
        asm.jmp(label3);
        asm.call(startLabel);
        asm.jmp(endLabel);
        asm.addl(ECX, 7);
        asm.bindLabel(label1);
        asm.cmpl(myGPR.indirect(), (byte) 7);
        asm.subl((byte) 4, RBX.base(), RCX.index(), SCALE_2, 5);
        asm.jmp(label1);
        asm.fixLabel(label2, startAddress + 0x12345678);
        asm.call(label2);
        asm.jmp(startLabel);
        asm.bindLabel(endLabel);
        asm.call(RAX);
        asm.call(RAX.indirect());

        asm.mov(RDI, 0x12345678);
        asm.mov(RDI, 0x123456789abcdefL);

        return asm.toByteArray();
    }

    public void test1() throws IOException, AssemblyException {
        System.out.println("--- test1: ---");
        final long startAddress = 0x12345678abcdef00L;
        final byte[] bytes = assemble1(startAddress);
        disassemble(startAddress, bytes);
    }

    private byte[] assemble2(long startAddress) throws IOException, AssemblyException {
        final AMD64Assembler asm = new AMD64Assembler(startAddress);

        final Label loop = new Label();
        final Label subroutine = new Label();
        asm.fixLabel(subroutine, 0x234L);

        asm.mov(RDX, 12, RSP.indirect());
        asm.bindLabel(loop);
        asm.call(subroutine);
        asm.sub(RDX, RAX);
        asm.cmpq(RDX, 0);
        asm.jnz(loop);

        asm.mov(20, RCX.base(), RDI.index(),
                SCALE_8, RDX);

        asm.mov(RAX, 0x0123456789abcdefL);

        return asm.toByteArray();
    }

    public void test2() throws IOException, AssemblyException {
        System.out.println("--- test2: ---");
        final long startAddress = 0x12345678L;
        final byte[] bytes = assemble2(startAddress);
        disassemble(startAddress, bytes);
    }

    public void test3() throws IOException, AssemblyException {
        System.out.println("--- test3: ---");
        final long startAddress = 0x12345678L;
        final AMD64Assembler asm = new AMD64Assembler(startAddress);
        for (AMD64GeneralRegister64 gpr : AMD64GeneralRegister64.ENUMERATOR) {
            asm.call(gpr);
        }
        final byte[] bytes = asm.toByteArray();
        disassemble(startAddress, bytes);
    }
}
