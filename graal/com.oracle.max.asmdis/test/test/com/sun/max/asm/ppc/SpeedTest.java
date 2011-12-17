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

import java.io.*;

import junit.framework.*;

import com.sun.max.asm.*;
import com.sun.max.asm.ppc.complete.*;
import com.sun.max.ide.*;

/**
 */
public class SpeedTest extends MaxTestCase {

    public SpeedTest() {
        super();

    }

    public SpeedTest(String name) {
        super(name);
    }

    public static Test suite() {
        final TestSuite suite = new TestSuite(SpeedTest.class.getName());
        suite.addTestSuite(SpeedTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(SpeedTest.class);
    }

    public byte[] produce()  throws IOException, AssemblyException {
        final int startAddress = 0x0000ecf0;
        final PPC32Assembler asm = new PPC32Assembler(startAddress);
        final Label label1 = new Label();

        asm.mflr(R0);
        asm.stwu(SP, -96, SP);
        asm.stmw(R23, 60, SP);
        asm.stw(R0, 100, SP);
        asm.mr(R23, R3);
        asm.mr(R31, R23);
        asm.cmplwi(R31, 2);
        asm.blt(CR0, label1, NONE);
        asm.addic(R30, R31, -1);
        asm.addic(R29, R31, -2);
        asm.mr(R3, R30);
        asm.mr(R3, R30);
        asm.lis(R24, 0);
        asm.ori(R24, R24, 60656);
        asm.mtctr(R24);
        asm.bctrl();
        asm.mr(R30, R3);
        asm.mr(R3, R29);
        asm.lis(R24, 0);
        asm.ori(R24, R24, 60656);
        asm.mtctr(R24);
        asm.bctrl();
        asm.mr(R29, R3);
        asm.addic(R30, R30, 1);
        asm.add(R3, R30, R29);
        asm.lwz(R0, 100, SP);
        asm.mtlr(R0);
        asm.lmw(R23, 60, SP);
        asm.addi(SP, SP, 96);
        asm.blr();
        asm.bindLabel(label1);
        asm.li(R3, 1);
        asm.lwz(R0, 100, SP);
        asm.mtlr(R0);
        asm.lmw(R23, 60, SP);
        asm.addi(SP, SP, 96);
        asm.blr();
        return asm.toByteArray();
    }

    public void test_speed() throws IOException, AssemblyException {
        System.out.println("start");
        for (int i = 0; i < 10000000; i++) {
            produce();
        }
        System.out.println("done.");
        //final PPC32Disassembler disassembler = new PPC32Disassembler(startAddress);
        //disassemble(disassembler, bytes);
    }
}
