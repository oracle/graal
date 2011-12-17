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

import static com.sun.max.asm.gen.InstructionConstraint.Static.*;
import static com.sun.max.asm.gen.risc.ppc.PPCFields.*;

import java.lang.reflect.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.ppc.*;

/**
 * The definitions of the raw (i.e. non-synthetic) PowerPC instructions.
 */
public final class RawInstructions extends PPCInstructionDescriptionCreator {

    RawInstructions(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        setCurrentArchitectureManualSection("2.4.1");
        generateBranches();

        setCurrentArchitectureManualSection("2.4.3");
        generateConditionRegisterLogicals();

        setCurrentArchitectureManualSection("2.4.4");
        generateConditionRegisterFields();

        setCurrentArchitectureManualSection("3.3.2");
        generateLoads();

        setCurrentArchitectureManualSection("3.3.3");
        generateStores();

        setCurrentArchitectureManualSection("3.3.4");
        generateByteReversals();

        setCurrentArchitectureManualSection("3.3.5");
        generateLoadStoreMultiple();

        setCurrentArchitectureManualSection("3.3.6");
        generateMoveAssists();

        setCurrentArchitectureManualSection("3.3.8");
        generateFixedPointArithmetics();

        setCurrentArchitectureManualSection("3.3.9");
        generateFixedPointCompares();

        setCurrentArchitectureManualSection("3.3.10");
        generateFixedPointTraps();

        setCurrentArchitectureManualSection("3.3.11");
        generateFixedPointLogicals();

        setCurrentArchitectureManualSection("3.3.12");
        generateFixedPointRotates();

        setCurrentArchitectureManualSection("3.3.12.2");
        generateFixedPointShifts();

        setCurrentArchitectureManualSection("3.3.13");
        generateMoveToFromSystemRegisters();

        setCurrentArchitectureManualSection("4.6.2");
        generateFloatingPointLoads();

        setCurrentArchitectureManualSection("4.6.3");
        generateFloatingPointStores();

        setCurrentArchitectureManualSection("4.6.4");
        generateFloatingPointMoves();

        setCurrentArchitectureManualSection("4.6.5");
        generateFloatingPointAriths();

        setCurrentArchitectureManualSection("4.6.6");
        generateFloatingPointRoundsAndCvts();

        setCurrentArchitectureManualSection("4.6.7");
        generateFloatingPointCompares();

        setCurrentArchitectureManualSection("4.6.8");
        generateFloatingPointStatusAndCRs();

        setCurrentArchitectureManualSection("5.1.1");
        generateMoveToFromSystemRegistersOptional();

        setCurrentArchitectureManualSection("5.2.1");
        generateFloatingPointArithsOptional();

        setCurrentArchitectureManualSection("5.2.2");
        generateFloatingPointSelectOptional();

        setCurrentArchitectureManualSection("6.1");
        generateDeprecated();

        setCurrentArchitectureManualSection("3.2.1 [Book 2]");
        generateICacheManagement();

        setCurrentArchitectureManualSection("3.2.2 [Book 2]");
        generateDCacheManagement();

        setCurrentArchitectureManualSection("3.3.1 [Book 2]");
        generateInstructionSynchronization();

        setCurrentArchitectureManualSection("3.3.2 [Book 2]");
        generateAtomicUpdates();

        setCurrentArchitectureManualSection("3.3.3 [Book 2]");
        generateMemoryBarrier();
    }

    private void generateBranches() {
        define("b", opcd(18), li, lk, aa);
        define("bc", opcd(16), bo, bi, bd, lk, aa);
        define("bclr", opcd(19), bo, bi, res_16_18, bh, xo_21_30(16), lk);
        define("bcctr", opcd(19), bo, bi, res_16_18, bh, xo_21_30(528), lk);
    }

    private void generateConditionRegisterLogicals() {
        define("crand", opcd(19), bt, ba, bb, xo_21_30(257), res_31);
        define("crxor", opcd(19), bt, ba, bb, xo_21_30(193), res_31);
        define("cror", opcd(19), bt, ba, bb, xo_21_30(449), res_31);
        define("crnand", opcd(19), bt, ba, bb, xo_21_30(225), res_31);

        define("crnor", opcd(19), bt, ba, bb, xo_21_30(33), res_31);
        define("creqv", opcd(19), bt, ba, bb, xo_21_30(289), res_31);
        define("crandc", opcd(19), bt, ba, bb, xo_21_30(129), res_31);
        define("crorc", opcd(19), bt, ba, bb, xo_21_30(417), res_31);
    }

    private void generateConditionRegisterFields() {
        define("mcrf", opcd(19), bf, res_9_10, bfa, res_14_15, res_16_20, xo_21_30(0), res_31);
    }

    private void generateLoads() {

        define("lbz", opcd(34), rt, d, "(", ra0_notR0, ")");
        define("lbzu", opcd(35), rt, d, "(", ra_notR0_notRT, ")");
        define("lbzx", opcd(31), rt, ra0_notR0, rb, xo_21_30(87), res_31);
        define("lbzux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(119), res_31);

        define("lhz", opcd(40), rt, d, "(", ra0_notR0, ")");
        define("lhzu", opcd(41), rt, d, "(", ra_notR0_notRT, ")");
        define("lhzx", opcd(31), rt, ra0_notR0, rb, xo_21_30(279), res_31);
        define("lhzux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(311), res_31);

        define("lha", opcd(42), rt, d, "(", ra0_notR0, ")");
        define("lhau", opcd(43), rt, d, "(", ra_notR0_notRT, ")");
        define("lhax", opcd(31), rt, ra0_notR0, rb, xo_21_30(343), res_31);
        define("lhaux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(375), res_31);

        define("lwz", opcd(32), rt, d, "(", ra0_notR0, ")");
        define("lwzu", opcd(33), rt, d, "(", ra_notR0_notRT, ")");
        define("lwzx", opcd(31), rt, ra0_notR0, rb, xo_21_30(23), res_31);
        define("lwzux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(55), res_31);

        define64("lwa", opcd(58), rt, ds, "(", ra0_notR0, ")", xo_30_31(2));
        define64("lwax", opcd(31), rt, ra0_notR0, rb, xo_21_30(341), res_31);
        define64("lwaux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(373), res_31);

        define64("ld", opcd(58), rt, ds, "(", ra0_notR0, ")", xo_30_31(0));
        define64("ldu", opcd(58), rt, ds, "(", ra_notR0_notRT, ")", xo_30_31(1));
        define64("ldx", opcd(31), rt, ra0_notR0, rb, xo_21_30(21), res_31);
        define64("ldux", opcd(31), rt, ra_notR0_notRT, rb, xo_21_30(53), res_31);
    }

    private void generateStores() {

        define("stb", opcd(38), rs, d, "(", ra0_notR0, ")");
        define("stbu", opcd(39), rs, d, "(", ra_notR0, ")");
        define("stbx", opcd(31), rs, ra0_notR0, rb, xo_21_30(215), res_31);
        define("stbux", opcd(31), rs, ra_notR0, rb, xo_21_30(247), res_31);

        define("sth", opcd(44), rs, d, "(", ra0_notR0, ")");
        define("sthu", opcd(45), rs, d, "(", ra_notR0, ")");
        define("sthx", opcd(31), rs, ra0_notR0, rb, xo_21_30(407), res_31);
        define("sthux", opcd(31), rs, ra_notR0, rb, xo_21_30(439), res_31);

        define("stw", opcd(36), rs, d, "(", ra0_notR0, ")");
        define("stwu", opcd(37), rs, d, "(", ra_notR0, ")");
        define("stwx", opcd(31), rs, ra0_notR0, rb, xo_21_30(151), res_31);
        define("stwux", opcd(31), rs, ra_notR0, rb, xo_21_30(183), res_31);

        define64("std", opcd(62), rs, ds, "(", ra0_notR0, ")", xo_30_31(0));
        define64("stdu", opcd(62), rs, ds, "(", ra_notR0, ")", xo_30_31(1));
        define64("stdx", opcd(31), rs, ra0_notR0, rb, xo_21_30(149), res_31);
        define64("stdux", opcd(31), rs, ra_notR0, rb, xo_21_30(181), res_31);
    }

    private void generateByteReversals() {

        define("lhbrx", opcd(31), rt, ra0_notR0, rb, xo_21_30(790), res_31);
        define("lwbrx", opcd(31), rt, ra0_notR0, rb, xo_21_30(534), res_31);
        define("sthbrx", opcd(31), rt, ra0_notR0, rb, xo_21_30(918), res_31);
        define("stwbrx", opcd(31), rt, ra0_notR0, rb, xo_21_30(662), res_31);
    }

    private void generateLoadStoreMultiple() {

        define("lmw", opcd(46), rt, d, "(", ra0_notR0_ltRT, ")");
        define("stmw", opcd(47), rs, d, "(", ra0_notR0, ")");
    }

    private void generateMoveAssists() {

        final Method predicateMethod = InstructionConstraint.Static.getPredicateMethod(ZeroOrRegister.class, "isOutsideRegisterRange", GPR.class, Integer.TYPE);
        final InstructionConstraint lswiConstraint = InstructionConstraint.Static.makePredicate(predicateMethod, ra0, rt, nb);
        define("lswi", opcd(31), rt, ra0_notR0, nb, xo_21_30(597), res_31, lswiConstraint);
        define("lswx", opcd(31), rt, ra0_notR0, rb, xo_21_30(533), res_31, ne(rt, ra0), ne(rt, rb));
        define("stswi", opcd(31), rs, ra0_notR0, nb, xo_21_30(725), res_31);
        define("stswx", opcd(31), rs, ra0_notR0, rb, xo_21_30(661), res_31);
    }

    private void generateFixedPointArithmetics() {
        define("addi", opcd(14), rt, ra0_notR0, si);
        define("addis", opcd(15), rt, ra0_notR0, sis);
        define("add", opcd(31), rt, ra, rb, oe, xo_22_30(266), rc);
        define("subf", opcd(31), rt, ra, rb, oe, xo_22_30(40), rc);
        define("addic", opcd(12), rt, ra, si);
        define("addic_", opcd(13), rt, ra, si).setExternalName("addic.");
        define("subfic", opcd(8), rt, ra, si);
        define("addc", opcd(31), rt, ra, rb, oe, xo_22_30(10), rc);
        define("subfc", opcd(31), rt, ra, rb, oe, xo_22_30(8), rc);
        define("adde", opcd(31), rt, ra, rb, oe, xo_22_30(138), rc);
        define("subfe", opcd(31), rt, ra, rb, oe, xo_22_30(136), rc);
        define("addme", opcd(31), rt, ra, res_16_20, oe, xo_22_30(234), rc);
        define("subfme", opcd(31), rt, ra, res_16_20, oe, xo_22_30(232), rc);
        define("addze", opcd(31), rt, ra, res_16_20, oe, xo_22_30(202), rc);
        define("subfze", opcd(31), rt, ra, res_16_20, oe, xo_22_30(200), rc);
        define("neg", opcd(31), rt, ra, res_16_20, oe, xo_22_30(104), rc);

        define("mulli", opcd(7), rt, ra, si);
        define64("mulld", opcd(31), rt, ra, rb, oe, xo_22_30(233), rc);
        define("mullw", opcd(31), rt, ra, rb, oe, xo_22_30(235), rc);
        define64("mulhd", opcd(31), rt, ra, rb, res_21, xo_22_30(73), rc);
        define("mulhw", opcd(31), rt, ra, rb, res_21, xo_22_30(75), rc);
        define64("mulhdu", opcd(31), rt, ra, rb, res_21, xo_22_30(9), rc);
        define("mulhwu", opcd(31), rt, ra, rb, res_21, xo_22_30(11), rc);
        define64("divd", opcd(31), rt, ra, rb, oe, xo_22_30(489), rc);
        define("divw", opcd(31), rt, ra, rb, oe, xo_22_30(491), rc);
        define64("divdu", opcd(31), rt, ra, rb, oe, xo_22_30(457), rc);
        define("divwu", opcd(31), rt, ra, rb, oe, xo_22_30(459), rc);
    }

    private void generateFixedPointCompares() {
        define("cmpi", opcd(11), bf, res_9, l, ra, si);
        define("cmp", opcd(31), bf, res_9, l, ra, rb, xo_21_30(0), res_31);
        define("cmpli", opcd(10), bf, res_9, l, ra, ui);
        define("cmpl", opcd(31), bf, res_9, l, ra, rb, xo_21_30(32), res_31);
    }

    private void generateFixedPointTraps() {
        define64("tdi", opcd(2), to, ra, si);
        define("twi", opcd(3), to, ra, si);
        define64("td", opcd(31), to, ra, rb, xo_21_30(68), res_31);
        define("tw", opcd(31), to, ra, rb, xo_21_30(4), res_31);
    }

    private void generateFixedPointLogicals() {

        define("andi_", opcd(28), ra, rs, ui).setExternalName("andi.");
        define("andis_", opcd(29), ra, rs, ui).setExternalName("andis.");
        define("ori", opcd(24), ra, rs, ui);
        define("oris", opcd(25), ra, rs, ui);
        define("xori", opcd(26), ra, rs, ui);
        define("xoris", opcd(27), ra, rs, ui);

        define("and", opcd(31), ra, rs, rb, xo_21_30(28), rc);
        define("or", opcd(31), ra, rs, rb, xo_21_30(444), rc);
        define("xor", opcd(31), ra, rs, rb, xo_21_30(316), rc);
        define("nand", opcd(31), ra, rs, rb, xo_21_30(476), rc);
        define("nor", opcd(31), ra, rs, rb, xo_21_30(124), rc);
        define("eqv", opcd(31), ra, rs, rb, xo_21_30(284), rc);
        define("andc", opcd(31), ra, rs, rb, xo_21_30(60), rc);
        define("orc", opcd(31), ra, rs, rb, xo_21_30(412), rc);

        define("extsb", opcd(31), ra, rs, res_16_20, xo_21_30(954), rc);
        define("extsh", opcd(31), ra, rs, res_16_20, xo_21_30(922), rc);
        define64("extsw", opcd(31), ra, rs, res_16_20, xo_21_30(986), rc);
        define64("cntlzd", opcd(31), ra, rs, res_16_20, xo_21_30(58), rc);
        define("cntlzw", opcd(31), ra, rs, res_16_20, xo_21_30(26), rc);

        defineP5("popcntb", opcd(31), ra, rs, res_16_20, xo_21_30(122), res_31);
    }

    private void generateFixedPointRotates() {
        define64("rldicl", opcd(30), ra, rs, sh64, mb64, xo_27_29(0), rc);
        define64("rldicr", opcd(30), ra, rs, sh64, me64, xo_27_29(1), rc);
        define64("rldic", opcd(30), ra, rs, sh64, mb64, xo_27_29(2), rc);
        define("rlwinm", opcd(21), ra, rs, sh, mb, me, rc);
        define64("rldcl", opcd(30), ra, rs, rb, mb64, xo_27_30(8), rc);
        define64("rldcr", opcd(30), ra, rs, rb, me64, xo_27_30(9), rc);
        define("rlwnm", opcd(23), ra, rs, rb, mb, me, rc);
        define64("rldimi", opcd(30), ra, rs, sh64, mb64, xo_27_29(3), rc);
        define("rlwimi", opcd(20), ra, rs, sh, mb, me, rc);
    }

    private void generateFixedPointShifts() {
        define64("sld", opcd(31), ra, rs, rb, xo_21_30(27), rc);
        define("slw", opcd(31), ra, rs, rb, xo_21_30(24), rc);
        define64("srd", opcd(31), ra, rs, rb, xo_21_30(539), rc);
        define("srw", opcd(31), ra, rs, rb, xo_21_30(536), rc);
        define64("sradi", opcd(31), ra, rs, sh64, xo_21_29(413), rc);
        define("srawi", opcd(31), ra, rs, sh, xo_21_30(824), rc);
        define64("srad", opcd(31), ra, rs, rb, xo_21_30(794), rc);
        define("sraw", opcd(31), ra, rs, rb, xo_21_30(792), rc);
    }

    private void generateMoveToFromSystemRegisters() {
        define("mtspr", opcd(31), spr, rs, xo_21_30(467), res_31);
        define("mfspr", opcd(31), rt, spr, xo_21_30(339), res_31);
        define("mtcrf", opcd(31), fxm, rs, bit_11(0), res_20, xo_21_30(144), res_31);
        define("mfcr", opcd(31), rt, bit_11(0), res_12_20, xo_21_30(19), res_31);
    }

    private void generateFloatingPointLoads() {

        define("lfs", opcd(48), frt, d, "(", ra0_notR0, ")");
        define("lfsx", opcd(31), frt, ra0_notR0, rb, xo_21_30(535), res_31);
        define("lfsu", opcd(49), frt, d, "(", ra_notR0, ")");
        define("lfsux", opcd(31), frt, ra_notR0, rb, xo_21_30(567), res_31);

        define("lfd", opcd(50), frt, d, "(", ra0_notR0, ")");
        define("lfdx", opcd(31), frt, ra0_notR0, rb, xo_21_30(599), res_31);
        define("lfdu", opcd(51), frt, d, "(", ra_notR0, ")");
        define("lfdux", opcd(31), frt, ra_notR0, rb, xo_21_30(631), res_31);
    }

    private void generateFloatingPointStores() {

        define("stfs", opcd(52), frs, d, "(", ra0_notR0, ")");
        define("stfsx", opcd(31), frs, ra0_notR0, rb, xo_21_30(663), res_31);
        define("stfsu", opcd(53), frs, d, "(", ra_notR0, ")");
        define("stfsux", opcd(31), frs, ra_notR0, rb, xo_21_30(695), res_31);

        define("stfd", opcd(54), frs, d, "(", ra0_notR0, ")");
        define("stfdx", opcd(31), frs, ra0_notR0, rb, xo_21_30(727), res_31);
        define("stfdu", opcd(55), frs, d, "(", ra_notR0, ")");
        define("stfdux", opcd(31), frs, ra_notR0, rb, xo_21_30(759), res_31);
    }

    private void generateFloatingPointMoves() {
        define("fmr", opcd(63), frt, res_11_15, frb, xo_21_30(72), rc);
        define("fneg", opcd(63), frt, res_11_15, frb, xo_21_30(40), rc);
        define("fabs", opcd(63), frt, res_11_15, frb, xo_21_30(264), rc);
        define("fnabs", opcd(63), frt, res_11_15, frb, xo_21_30(136), rc);
    }

    private void generateFloatingPointAriths() {
        define("fadd", opcd(63), frt, fra, frb, res_21_25, xo_26_30(21), rc);
        define("fadds", opcd(59), frt, fra, frb, res_21_25, xo_26_30(21), rc);
        define("fsub", opcd(63), frt, fra, frb, res_21_25, xo_26_30(20), rc);
        define("fsubs", opcd(59), frt, fra, frb, res_21_25, xo_26_30(20), rc);
        define("fmul", opcd(63), frt, fra, res_16_20, frc, xo_26_30(25), rc);
        define("fmuls", opcd(59), frt, fra, res_16_20, frc, xo_26_30(25), rc);
        define("fdiv", opcd(63), frt, fra, frb, res_21_25, xo_26_30(18), rc);
        define("fdivs", opcd(59), frt, fra, frb, res_21_25, xo_26_30(18), rc);

        define("fmadd", opcd(63), frt, fra, frc, frb, xo_26_30(29), rc);
        define("fmadds", opcd(59), frt, fra, frc, frb, xo_26_30(29), rc);
        define("fmsub", opcd(63), frt, fra, frc, frb, xo_26_30(28), rc);
        define("fmsubs", opcd(59), frt, fra, frc, frb, xo_26_30(28), rc);
        define("fnmadd", opcd(63), frt, fra, frc, frb, xo_26_30(31), rc);
        define("fnmadds", opcd(59), frt, fra, frc, frb, xo_26_30(31), rc);
        define("fnmsub", opcd(63), frt, fra, frc, frb, xo_26_30(30), rc);
        define("fnmsubs", opcd(59), frt, fra, frc, frb, xo_26_30(30), rc);
    }

    private void generateFloatingPointRoundsAndCvts() {
        define("frsp", opcd(63), frt, res_11_15, frb, xo_21_30(12), rc);
        define64("fctid", opcd(63), frt, res_11_15, frb, xo_21_30(814), rc);
        define64("fctidz", opcd(63), frt, res_11_15, frb, xo_21_30(815), rc);
        define("fctiw", opcd(63), frt, res_11_15, frb, xo_21_30(14), rc);
        define("fctiwz", opcd(63), frt, res_11_15, frb, xo_21_30(15), rc);
        define64("fcfid", opcd(63), frt, res_11_15, frb, xo_21_30(846), rc);
    }

    private void generateFloatingPointCompares() {
        define("fcmpu", opcd(63), bf, res_9_10, fra, frb, xo_21_30(0), res_31);
        define("fcmpo", opcd(63), bf, res_9_10, fra, frb, xo_21_30(32), res_31);
    }

    private void generateFloatingPointStatusAndCRs() {
        define("mffs", opcd(63), frt, res_11_15, res_16_20, xo_21_30(583), rc);
        define("mcrfs", opcd(63), bf, res_9_10, bfa, res_14_15, res_16_20, xo_21_30(64), res_31);
        define("mtfsfi", opcd(63), bf, res_9_10, res_11_15, u, res_20, xo_21_30(134), rc);
        define("mtfsf", opcd(63), res_6, flm, res_15, frb, xo_21_30(711), rc);
        define("mtfsb0", opcd(63), bt, res_11_15, res_16_20, xo_21_30(70), rc);
        define("mtfsb1", opcd(63), bt, res_11_15, res_16_20, xo_21_30(38), rc);
    }

    private void generateMoveToFromSystemRegistersOptional() {
        define("mtocrf", opcd(31), fxm, rs, bit_11(1), res_20, xo_21_30(144), res_31);
        final Method predicateMethod = InstructionConstraint.Static.getPredicateMethod(CRF.class, "isExactlyOneCRFSelected", int.class);
        final InstructionConstraint ic = InstructionConstraint.Static.makePredicate(predicateMethod, fxm);
        define("mfocrf", opcd(31), rt, fxm, bit_11(1), res_20, xo_21_30(19), res_31, ic);
    }

    private void generateFloatingPointArithsOptional() {
        define64("fsqrt", opcd(63), frt, res_11_15, frb, res_21_25, xo_26_30(22), rc);
        define("fsqrts", opcd(59), frt, res_11_15, frb, res_21_25, xo_26_30(22), rc);
        define64("fre", opcd(63), frt, res_11_15, frb, res_21_25, xo_26_30(24), rc);
        define("fres", opcd(59), frt, res_11_15, frb, res_21_25, xo_26_30(24), rc);
        define64("frsqrte", opcd(63), frt, res_11_15, frb, res_21_25, xo_26_30(26), rc);
        define("frsqrtes", opcd(59), frt, res_11_15, frb, res_21_25, xo_26_30(26), rc);
    }

    private void generateFloatingPointSelectOptional() {
        define("fsel", opcd(63), frt, fra, frc, frb, xo_26_30(23), rc);
    }

    private void generateDeprecated() {
        define("mcrxr", opcd(31), bf, res_9_10, res_11_15, res_16_20, xo_21_30(512), res_31);
    }

    private void generateICacheManagement() {
        define("icbi", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(982), res_31);
    }

    private void generateDCacheManagement() {
        define("dcbt", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(278), res_31);
        define("dcbtst", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(246), res_31);
        define("dcbz", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(1014), res_31);
        define("dcbst", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(54), res_31);
        define("dcbf", opcd(31), res_6_10, ra0_notR0, rb, xo_21_30(86), res_31);
    }

    private void generateInstructionSynchronization() {
        define("isync", opcd(19), res_6_10, res_11_15, res_16_20, xo_21_30(150), res_31);
    }

    private void generateAtomicUpdates() {
        define("lwarx", opcd(31), rt, ra0_notR0, rb, xo_21_30(20), res_31);
        define64("ldarx", opcd(31), rt, ra0_notR0, rb, xo_21_30(84), res_31);
        define("stwcx", opcd(31), rs, ra0_notR0, rb, xo_21_30(150), bit_31(1)).setExternalName("stwcx.");
        define64("stdcx", opcd(31), rs, ra0_notR0, rb, xo_21_30(214), bit_31(1)).setExternalName("stdcx.");
    }

    private void generateMemoryBarrier() {
        define("sync", opcd(31), res_6_10, res_11_15, res_16_20, xo_21_30(598), res_31);
        define("eieio", opcd(31), res_6_10, res_11_15, res_16_20, xo_21_30(854), res_31);
    }
}
