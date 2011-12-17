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
package com.sun.max.asm.gen.risc.sparc;

import static com.sun.max.asm.gen.InstructionConstraint.Static.*;
import static com.sun.max.asm.gen.risc.sparc.SPARCFields.*;
import static com.sun.max.asm.sparc.AnnulBit.*;
import static com.sun.max.asm.sparc.BranchPredictionBit.*;
import static com.sun.max.asm.sparc.GPR.*;
import static com.sun.max.asm.sparc.ICCOperand.*;

import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.asm.sparc.complete.*;

/**
 * Simple synthetic SPARC instructions.
 *
 * @see SPARCAssembler
 */
class SyntheticInstructions extends SPARCInstructionDescriptionCreator {

    private void create_A39() {
        synthesize("nop", "sethi", imm22(0), rd(G0));
    }

    private static final int ASI_P = 0x80;
    private static final int ASI_P_L = 0x88;

    private void create_G3() {
        synthesize("cmp", "subcc", rd(G0));
        synthesize("jmp", "jmpl", rd(G0));
        synthesize("call", "jmpl", rd(O7));
        synthesize("iprefetch", "bn", a(A), p(PT), cc(XCC));
        synthesize("tst", "orcc", rs1(G0), rd(G0), i(0));
        synthesize("ret", "jmpl", rs1(I7), simm13(8), rd(G0)).replace(" + ", "");
        synthesize("retl", "jmpl", rs1(O7), simm13(8), rd(G0)).replace(" + ", "");
        synthesize("restore", "restore", rs1(G0), rs2(G0), rd(G0));
        synthesize("save", "save", rs1(G0), rs2(G0), rd(G0));
        synthesize("signx", "sra", rs2(G0), i(0));
        synthesize("signx", "sra", rs1(rd), rs2(G0), i(0));
        synthesize("not", "xnor", rs2(G0));
        synthesize("not", "xnor", rs1(rd), rs2(G0));
        synthesize("neg", "sub", rs1(G0), i(0));
        synthesize("neg", "sub", rs1(G0), rs2(rd), i(0));
        synthesize("cas", "casa", immAsi(ASI_P));
        synthesize("casl", "casa", immAsi(ASI_P_L));
        synthesize("casx", "casxa", immAsi(ASI_P));
        synthesize("casxl", "casxa", immAsi(ASI_P_L));
        synthesize("inc", "add", rs1(rd), simm13(1), i(1));
        synthesize("inc", "add", rs1(rd), i(1));
        synthesize("inccc", "addcc", rs1(rd), simm13(1), i(1));
        synthesize("inccc", "addcc", rs1(rd), i(1));
        synthesize("dec", "sub", rs1(rd), simm13(1), i(1));
        synthesize("dec", "sub", rs1(rd), i(1));
        synthesize("deccc", "subcc", rs1(rd), simm13(1), i(1));
        synthesize("deccc", "subcc", rs1(rd), i(1));
        synthesize("btst", "andcc", rd(G0)).swap(rs2, rs1).swap(simm13, rs1);
        synthesize("bset", "or", rs1(rd));
        synthesize("bclr", "andn", rs1(rd));
        synthesize("btog", "xor", rs1(rd));
        synthesize("clr", "or", rs1(G0), rs2(G0));
        synthesize("clrb", "stb", rd(G0)).replace(", [", "[");
        synthesize("clrh", "sth", rd(G0)).replace(", [", "[");
        synthesize("clr", "stw", rd(G0)).replace(", [", "[");
        synthesize("clrx", "stx", rd(G0)).replace(", [", "[");
        synthesize("clruw", "srl", rs2(G0), i(0));
        synthesize("clruw", "srl", rs1(rd), rs2(G0), i(0));
        synthesize("mov", "or", rs1(G0));
        synthesize("mov", "rd", makePredicate(getPredicateMethod(StateRegister.class, "isYorASR"), rs1_state));
        synthesize("mov", "wr", rs1(G0), makePredicate(getPredicateMethod(StateRegister.class, "isYorASR"), rd_state));
    }

    SyntheticInstructions(RiscTemplateCreator creator) {
        super(creator);

        setCurrentArchitectureManualSection("A.39");
        create_A39();

        setCurrentArchitectureManualSection("G.3");
        create_G3();
    }

}
