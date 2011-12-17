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

import static com.sun.max.asm.sparc.AnnulBit.*;
import static com.sun.max.asm.sparc.BranchPredictionBit.*;
import static com.sun.max.asm.sparc.GPR.*;
import static com.sun.max.asm.sparc.ICCOperand.*;
import static com.sun.max.asm.sparc.MembarOperand.*;

import java.io.*;

import junit.framework.*;

import com.sun.max.asm.*;
import com.sun.max.asm.dis.sparc.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.asm.sparc.complete.*;
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

    private byte[] assemble(SPARCAssembler asm) throws IOException, AssemblyException {
        asm.rd(StateRegister.PC, G1);

        asm.add(G0, I1, O2);
        asm.sub(G5, 12, G7);
        asm.movvc(ICC, -12, G7);
        asm.sethi(0x1234, G7);

        // Example code from J.6:
        final Label retry = new Label();
        final Label loop = new Label();
        final Label out = new Label();

        asm.bindLabel(retry);
        asm.ldstub(G1, G4, I0);
        asm.tst(I0);
        asm.be(out);
        asm.nop();
        asm.bindLabel(loop);
        asm.ldub(G1, G4, I0);
        asm.tst(I0);
        asm.bne(loop);
        asm.nop();
        asm.ba(retry);
        asm.ba(A, PT, ICC, retry);
        asm.bindLabel(out);
        asm.rd(StateRegister.PC, L0);

        try {
            asm.sethi(0x0fffffff, G1);
            fail();
        } catch (IllegalArgumentException illegalArgumentException) {
        }

        asm.membar(LOAD_LOAD.or(LOAD_STORE));
        asm.membar(NO_MEMBAR.or(LOAD_STORE));

        return asm.toByteArray();
    }

    private void disassemble(SPARCDisassembler disassembler, byte[] bytes) throws IOException, AssemblyException {
        final BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        disassembler.scanAndPrint(stream, System.out);
    }

    public void test32() throws IOException, AssemblyException {
        final int startAddress = 0x12340000;
        final SPARC32Assembler assembler = new SPARC32Assembler(startAddress);
        final SPARC32Disassembler disassembler = new SPARC32Disassembler(startAddress, null);
        final byte[] bytes = assemble(assembler);
        disassemble(disassembler, bytes);
        System.out.println();
    }

    public void test64() throws IOException, AssemblyException {
        final long startAddress = 0x1234567812340000L;
        final SPARC64Assembler assembler = new SPARC64Assembler(startAddress);
        final SPARC64Disassembler disassembler = new SPARC64Disassembler(startAddress, null);
        final byte[] bytes = assemble(assembler);
        disassemble(disassembler, bytes);
        System.out.println();
    }
}
