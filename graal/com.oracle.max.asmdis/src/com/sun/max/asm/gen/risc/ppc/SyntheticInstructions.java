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
package com.sun.max.asm.gen.risc.ppc;

import static com.sun.max.asm.gen.Expression.Static.*;
import static com.sun.max.asm.gen.InstructionConstraint.Static.*;
import static com.sun.max.asm.gen.risc.ppc.PPCFields.*;
import static com.sun.max.asm.ppc.BOOperand.*;

import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.asm.ppc.*;

/**
 * The definitions of the synthetic PowerPC instructions.
 * <p>
 * The instructions assembled for the extended Rotate and Shift Mnemonics can not be disassembled
 * into their extended mnemonic form as some of their parameters are not directly correlated with
 * a field in the instruction. Instead, these {@link InputOperandField input} parameters are
 * part of an expression that gives a field its value and often, the same parameter is
 * part of an expression for more than one field. Recovering these parameters during
 * disassembly would require support for solving simultaneous equations. Given that the
 * external Mac OS X 'otool' disassembler does not disassemble these instructions into their
 * extended mnemonic form either, no one should be too upset with this limited functionality.
 */
class SyntheticInstructions extends PPCInstructionDescriptionCreator {

    @Override
    protected RiscInstructionDescription define(Object... specifications) {
        return (RiscInstructionDescription) super.define(specifications).beSynthetic();
    }

    SyntheticInstructions(RiscTemplateCreator creator) {
        super(creator);

        setCurrentArchitectureManualSection("B.2.2");

        // Derived from "bc", "bca", "bcl" and "bcla" raw instructions
        define("b", opcd(16), bo_CR, bi,  bd, lk, aa, bo_CR_prediction);
        define("b", opcd(16), bo_CTR, bi(0), bd, lk, aa, bo_CTR_prediction);
        define("b", opcd(16), bo_CTR_and_CR, bi,  bd, lk, aa);

        // Derived from "bclr" and "bclrl" raw instructions
        define("blr", opcd(19), bo(Always), bi(0), res_16_18, bh(0), xo_21_30(16), lk);
        define("b", opcd(19), bo_CR, bi, res_16_18, bh(0), xo_21_30(16), put_lr_in_name, lk, bo_CR_prediction);
        define("b", opcd(19), bo_CTR, bi(0), res_16_18, bh(0), xo_21_30(16), put_lr_in_name, lk, bo_CTR_prediction);
        define("b", opcd(19), bo_CTR_and_CR, bi, res_16_18, bh(0), xo_21_30(16), put_lr_in_name, lk);

        // Derived from "bcctr" and "bcctrl" raw instructions
        define("bctr", opcd(19), bo(Always), bi(0), res_16_18, bh(0), xo_21_30(528), lk);
        define("b", opcd(19), bo_CR, bi, res_16_18, bh(0), xo_21_30(528), put_ctr_in_name, lk, bo_CR_prediction);

        setCurrentArchitectureManualSection("B.2.3");

        // Derived from "bc", "bca", "bcl" and "bcla" raw instructions
        define("b", opcd(16), "cr", br_crf, branch_conds, bd, lk, aa, bo_CR_prediction);
        define("b", opcd(19), "cr", br_crf, branch_conds, res_16_18, bh(0), xo_21_30(16), put_lr_in_name, lk, bo_CR_prediction);
        define("b", opcd(19), "cr", br_crf, branch_conds, res_16_18, bh(0), xo_21_30(528), put_ctr_in_name, lk, bo_CR_prediction);

        setCurrentArchitectureManualSection("B.3");

        synthesize("crset", "creqv", bt(ba), bb(ba));
        synthesize("crclr", "crxor", bt(ba), bb(ba));
        synthesize("crmove", "cror", bb(ba));
        synthesize("crnot", "crnor", bb(ba));

        setCurrentArchitectureManualSection("B.4.1");

        synthesize("subi", "addi", si(neg(val)), val);
        synthesize("subis", "addis", si(neg(val)), val);
        synthesize("subic", "addic", si(neg(val)), val);
        synthesize("subic_", "addic_", si(neg(val)), val).setExternalName("subic.");

        setCurrentArchitectureManualSection("B.4.2");

        synthesize("sub", "subf", rt).swap(ra, rb);
        synthesize("subc", "subfc", rt).swap(ra, rb);

        setCurrentArchitectureManualSection("B.5.1");

        synthesize("cmpdi", "cmpi", l(1));
        synthesize("cmpdi", "cmpi", bf(CRF.CR0), l(1));
        synthesize("cmpd", "cmp", l(1));
        synthesize("cmpd", "cmp", bf(CRF.CR0), l(1));
        synthesize("cmpldi", "cmpli", l(1));
        synthesize("cmpldi", "cmpli", bf(CRF.CR0), l(1));
        synthesize("cmpld", "cmpl", l(1));
        synthesize("cmpld", "cmpl", bf(CRF.CR0), l(1));

        setCurrentArchitectureManualSection("B.5.2");

        synthesize("cmpwi", "cmpi", l(0));
        synthesize("cmpwi", "cmpi", bf(CRF.CR0), l(0));
        synthesize("cmpw", "cmp", l(0));
        synthesize("cmpw", "cmp", bf(CRF.CR0), l(0));
        synthesize("cmplwi", "cmpli", l(0));
        synthesize("cmplwi", "cmpli", bf(CRF.CR0), l(0));
        synthesize("cmplw", "cmpl", l(0));
        synthesize("cmplw", "cmpl", bf(CRF.CR0), l(0));

        setCurrentArchitectureManualSection("B.6");

        synthesize("tw", "twi", to_option, put_i_in_name);
        synthesize("tw", "tw", to_option);
        synthesize("trap", "tw", to(31), ra(GPR.R0), rb(GPR.R0));
        synthesize64("td", "tdi", to_option, put_i_in_name);
        synthesize64("td", "td", to_option);

        setCurrentArchitectureManualSection("B.7.1");

        synthesize("extldi", "rldicr", sh64(b64), me64(sub(n64, 1)), n64, b64);
        synthesize("extrdi", "rldicl", sh64(add(b64, n64)), mb64(sub(64, n64)), n64, b64);
        synthesize("insrdi", "rldimi", sh64(sub(64, add(b64, n64))), mb64(b64), n64, b64);
        synthesize("rotldi", "rldicl", sh64(n64), mb64(0), n64);
        synthesize("rotrdi", "rldicl", sh64(sub(64, n64)), mb64(0), n64);
        synthesize("rotld", "rldcl", mb64(0));
        synthesize("sldi", "rldicr", sh64(n64), mb64(sub(63, n64)), n64);
        synthesize("srdi", "rldicl", sh64(sub(64, n64)), mb64(n64), n64);
        synthesize("clrldi", "rldicl", sh64(0), mb64(n64), n64);
        synthesize("clrrdi", "rldicr", sh64(0), me64(sub(63, n64)), n64);
        synthesize("clrlsldi", "rldic", sh64(n64), mb64(sub(b64, n64)), b64, n64);

        setCurrentArchitectureManualSection("B.7.2");

        synthesize("extlwi", "rlwinm", sh(b), mb(0), me(sub(n, 1)), n, b, gt(n, 0));
        synthesize("extrwi", "rlwinm", sh(add(b, n)), mb(sub(32, n)), me(31), n, b, gt(n, 0));
        synthesize("inslwi", "rlwimi", sh(sub(32, b)), mb(b), me(sub(add(b, n), 1)), n, b, gt(n, 0));
        synthesize("insrwi", "rlwimi", sh(sub(32, add(b, n))), mb(b), me(sub(add(b, n), 1)), n, b, gt(n, 0));
        synthesize("rotlwi", "rlwinm", sh(n), mb(0), me(31), n);
        synthesize("rotrwi", "rlwinm", sh(sub(32, n)), mb(0), me(31), n);
        synthesize("rotlw", "rlwnm", mb(0), me(31));
        synthesize("slwi", "rlwinm", sh(n), mb(0), me(sub(31, n)), n, lt(n, 32));
        synthesize("srwi", "rlwinm", sh(sub(32, n)), mb(n), me(31), n, lt(n, 32));
        synthesize("clrlwi", "rlwinm", sh(0), mb(n), me(31), n, lt(n, 32));
        synthesize("clrrwi", "rlwinm", sh(0), mb(0), me(sub(31, n)), n, lt(n, 32));
        synthesize("clrlslwi", "rlwinm", sh(n), mb(sub(b, n)), me(sub(31, n)), b, n, le(n, b), lt(b, 32));

        setCurrentArchitectureManualSection("B.8");

        synthesize("mt", "mtspr", spr_option);
        synthesize("mf", "mfspr", spr_option);

        setCurrentArchitectureManualSection("B.9");

        synthesize("nop", "ori", ra0(Zero.ZERO), rs(GPR.R0), ui(0));
        synthesize("li", "addi", ra0(Zero.ZERO));
        synthesize("lis", "addis", ra0(Zero.ZERO));
        define("la", opcd(14), rt, si, "(", ra0_notR0, ")");
        synthesize("mr", "or", rs(rb));
        synthesize("not", "nor", rs(rb));
        synthesize("mtcr", "mtcrf", fxm(0xFF));
    }
}
