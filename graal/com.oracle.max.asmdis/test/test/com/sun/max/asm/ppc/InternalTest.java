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
package test.com.sun.max.asm.ppc;

import static com.sun.max.asm.ppc.BranchPredictionBits.*;
import static com.sun.max.asm.ppc.CRF.*;
import static com.sun.max.asm.ppc.GPR.*;
import static com.sun.max.asm.ppc.Zero.*;

import java.io.*;

import junit.framework.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.ppc.*;
import com.sun.max.asm.ppc.complete.*;
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

    private byte[] assemble(PPCAssembler asm) throws IOException, AssemblyException {
        final Label loop1 = new Label();
        final Label loop2 = new Label();

        // Example code from B.3 [Book 2] for list insertion
        asm.lwz(RTOC, 0, R3);      // get next pointer
        asm.bindLabel(loop1);
        asm.mr(R5, RTOC);          // keep a copy
        asm.stw(RTOC, 0, R4);      // store in new element
        asm.sync();                // order stw before stwcx. and before lwarx
        asm.bindLabel(loop2);
        asm.lwarx(RTOC, ZERO, R3); // get it again
        asm.cmpw(RTOC, R5);        // loop if changed (someone
        asm.bne(CR0, loop1, PN);   //    else progressed)
        asm.stwcx(R4, ZERO, R3);   // add new element to list
        asm.bne(CR0, loop2, PN);   // loop if failed

        return asm.toByteArray();
    }

    private void disassemble(PPCDisassembler disassembler, byte[] bytes) throws IOException, AssemblyException {
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    public void test32() throws IOException, AssemblyException {
        final int startAddress = 0x12340000;
        final PPC32Assembler assembler = new PPC32Assembler(startAddress);
        final PPC32Disassembler disassembler = new PPC32Disassembler(startAddress, null);
        final byte[] bytes = assemble(assembler);
        disassemble(disassembler, bytes);
        System.out.println();
    }

    public void test64() throws IOException, AssemblyException {
        final long startAddress = 0x1234567812340000L;
        final PPC64Assembler assembler = new PPC64Assembler(startAddress);
        final PPC64Disassembler disassembler = new PPC64Disassembler(startAddress, null);
        final byte[] bytes = assemble(assembler);
        disassemble(disassembler, bytes);
        System.out.println();
    }
}
