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
import static com.sun.max.asm.ppc.BOOperand.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.asm.ppc.*;
import com.sun.max.lang.*;

/**
 * The fields used in defining the PowerPC instruction templates.
 */
final class PPCFields {

    private PPCFields() {
    }

    /**
     * RA field that can also accept the constant 0.
     */
    public static final SymbolicOperandField<ZeroOrRegister> ra0 = SymbolicOperandField.createAscending(ZeroOrRegister.symbolizer(), 11, 15).setVariableName("ra");

    public static RiscConstant ra0(ZeroOrRegister value) {
        return ra0.constant(value);
    }

    /**
     * RA field that can only accept GPR symbols.
     */
    public static final SymbolicOperandField<GPR> ra = SymbolicOperandField.createAscending(GPR.GPR_SYMBOLIZER, 11, 15);

    public static RiscConstant ra(GPR value) {
        return ra.constant(value);
    }

    /**
     * GPR symbol RA field with constraint: RA != GPR.R0.
     */
    public static final Object[] ra_notR0 = {ra, ne(ra, GPR.R0)};

    /**
     * GPR symbol or 0 RA field with constraint: RA != GPR.R0.
     */
    public static final Object[] ra0_notR0 = {ra0, ne(ra0, GPR.R0)};

    public static final SymbolicOperandField<GPR> rb = SymbolicOperandField.createAscending(GPR.GPR_SYMBOLIZER, 16, 20);

    public static RiscConstant rb(GPR value) {
        return rb.constant(value);
    }

    public static RiscConstant rs(GPR value) {
        return rs.constant(value);
    }

    public static SymbolicOperandField<GPR> rs(Expression expression) {
        return rs.bindTo(expression);
    }

    public static final SymbolicOperandField<GPR> rs = SymbolicOperandField.createAscending(GPR.GPR_SYMBOLIZER, 6, 10);
    public static final SymbolicOperandField<GPR> rt = SymbolicOperandField.createAscending(GPR.GPR_SYMBOLIZER, 6, 10);

    /**
     * GPR symbol RA field with constraint: RA != GPR.R0 && RA != RT.
     */
    public static final Object[] ra_notR0_notRT = {ra, ne(ra, GPR.R0), ne(ra, rt)};

    /**
     * GCP symbol or 0 RA field with constraint: RA != GPR.R0 && RA < RT.
     */
    public static final Object[] ra0_notR0_ltRT = {ra0, ne(ra0, GPR.R0), lt(ra0, rt)};

    public static final SymbolicOperandField<CRF> bf = SymbolicOperandField.createAscending(CRF.ENUMERATOR, 6, 8);

    public static RiscConstant bf(CRF value) {
        return bf.constant(value);
    }

    public static final SymbolicOperandField<CRF> bfa = SymbolicOperandField.createAscending(CRF.ENUMERATOR, 11, 13);
    public static final SymbolicOperandField<CRF> br_crf = SymbolicOperandField.createAscending(CRF.ENUMERATOR, 11, 13).setVariableName("crf");

    public static final ImmediateOperandField spr = ImmediateOperandField.createAscending(16, 20, 11, 15);

    public static final SymbolicOperandField<FPR> frt = SymbolicOperandField.createAscending(FPR.ENUMERATOR, 6, 10);
    public static final SymbolicOperandField<FPR> frs = SymbolicOperandField.createAscending(FPR.ENUMERATOR, 6, 10);
    public static final SymbolicOperandField<FPR> fra = SymbolicOperandField.createAscending(FPR.ENUMERATOR, 11, 15);
    public static final SymbolicOperandField<FPR> frb = SymbolicOperandField.createAscending(FPR.ENUMERATOR, 16, 20);
    public static final SymbolicOperandField<FPR> frc = SymbolicOperandField.createAscending(FPR.ENUMERATOR, 21, 25);

    public static final SymbolicOperandField<BOOperand> bo = SymbolicOperandField.createAscending(BOOperand.SYMBOLIZER, 6, 10);

    public static RiscConstant bo(BOOperand value) {
        return bo.constant(value);
    }

    public static final ImmediateOperandField d = ImmediateOperandField.createAscending(16, 31).beSigned();
    public static final ImmediateOperandField ds = new AlignedImmediateOperandField(new AscendingBitRange(16, 29), 2).beSigned();
    public static final ImmediateOperandField si = ImmediateOperandField.createAscending(16, 31).beSigned();
    public static final ImmediateOperandField sis = ImmediateOperandField.createAscending(16, 31).beSignedOrUnsigned();
    public static final ImmediateOperandField ui = ImmediateOperandField.createAscending(16, 31);
    public static final ImmediateOperandField to = ImmediateOperandField.createAscending(6, 10);
    public static final ImmediateOperandField sh64 = ImmediateOperandField.createAscending(30, 30, 16, 20).setVariableName("sh");
    public static final ImmediateOperandField mb64 = ImmediateOperandField.createAscending(26, 26, 21, 25).setVariableName("mb");
    public static final ImmediateOperandField me64 = ImmediateOperandField.createAscending(26, 26, 21, 25).setVariableName("me");
    public static final ImmediateOperandField sh = ImmediateOperandField.createAscending(16, 20);
    public static final ImmediateOperandField mb = ImmediateOperandField.createAscending(21, 25);
    public static final ImmediateOperandField me = ImmediateOperandField.createAscending(26, 30);
    public static final ImmediateOperandField fxm = ImmediateOperandField.createAscending(12, 19);
    public static final ImmediateOperandField bi = ImmediateOperandField.createAscending(11, 15);
    public static final ImmediateOperandField bt = ImmediateOperandField.createAscending(6, 10);
    public static final ImmediateOperandField ba = ImmediateOperandField.createAscending(11, 15);
    public static final ImmediateOperandField bb = ImmediateOperandField.createAscending(16, 20);
    public static final ImmediateOperandField u = ImmediateOperandField.createAscending(16, 19);
    public static final ImmediateOperandField flm = ImmediateOperandField.createAscending(7, 14);
    public static final ImmediateOperandField l = ImmediateOperandField.createAscending(10, 10);

    private static final ImmediateOperandField bh_raw = ImmediateOperandField.createAscending(19, 20).setVariableName("bh");
    public static final Object[] bh = {bh_raw, InstructionConstraint.Static.ne(bh_raw, 2)};

    public static final ImmediateOperandField nb = ImmediateOperandField.createAscending(16, 20);
    public static final ImmediateOperandField numBits64 = ImmediateOperandField.createAscending(0, 5);
    public static final ImmediateOperandField numBits32 = ImmediateOperandField.createAscending(0, 4);

    public static final ImmediateOperandField byte0 = ImmediateOperandField.createAscending(0, 7);
    public static final ImmediateOperandField byte1 = ImmediateOperandField.createAscending(8, 15);
    public static final ImmediateOperandField byte2 = ImmediateOperandField.createAscending(16, 23);
    public static final ImmediateOperandField byte3 = ImmediateOperandField.createAscending(24, 31);

    public static final InputOperandField val = InputOperandField.create(si);
    public static final InputOperandField n = InputOperandField.create(sh);
    public static final InputOperandField b = InputOperandField.create(sh);
    public static final InputOperandField n64 = InputOperandField.create(sh64).setVariableName("n");
    public static final InputOperandField b64 = InputOperandField.create(sh64).setVariableName("b");

    public static RiscConstant to(int value) {
        return to.constant(value);
    }

    public static RiscConstant sh(int value) {
        return sh.constant(value);
    }

    public static RiscConstant mb(int value) {
        return mb.constant(value);
    }

    public static RiscConstant me(int value) {
        return me.constant(value);
    }

    public static RiscConstant sh64(int value) {
        return sh64.constant(value);
    }

    public static RiscConstant mb64(int value) {
        return mb64.constant(value);
    }

    public static RiscConstant me64(int value) {
        return me64.constant(value);
    }

    public static RiscConstant fxm(int value) {
        return fxm.constant(value);
    }

    public static RiscConstant bi(int value) {
        return bi.constant(value);
    }

    public static RiscConstant ui(int value) {
        return ui.constant(value);
    }

    public static RiscConstant si(int value) {
        return si.constant(value);
    }

    public static RiscConstant sis(int value) {
        return sis.constant(value);
    }

    public static RiscConstant bh(int value) {
        return bh_raw.constant(value);
    }

    public static RiscConstant l(int value) {
        return l.constant(value);
    }

    public static ImmediateOperandField bb(Expression expression) {
        return bb.bindTo(expression);
    }

    public static ImmediateOperandField bt(Expression expression) {
        return bt.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> si(Expression expression) {
        return si.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> sh(Expression expression) {
        return sh.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> me(Expression expression) {
        return me.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> mb(Expression expression) {
        return mb.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> sh64(Expression expression) {
        return sh64.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> me64(Expression expression) {
        return me64.bindTo(expression);
    }

    public static OperandField<ImmediateArgument> mb64(Expression expression) {
        return mb64.bindTo(expression);
    }

    public static final BranchDisplacementOperandField li = BranchDisplacementOperandField.createAscendingBranchDisplacementOperandField(6, 29);
    public static final BranchDisplacementOperandField bd = BranchDisplacementOperandField.createAscendingBranchDisplacementOperandField(16, 29);

    private static final ConstantField bit_11 = ConstantField.createAscending(11, 11);

    public static RiscConstant bit_11(int value) {
        return bit_11.constant(value);
    }

    private static final ConstantField bit_30 = ConstantField.createAscending(30, 30);

    public static RiscConstant bit_30(int value) {
        return bit_30.constant(value);
    }

    private static final ConstantField bit_31 = ConstantField.createAscending(31, 31);

    public static RiscConstant bit_31(int value) {
        return bit_31.constant(value);
    }

    private static final ConstantField opcd = ConstantField.createAscending(0,  5);

    public static RiscConstant opcd(int value) {
        return opcd.constant(value);
    }

    private static final ConstantField xo_21_29 = ConstantField.createAscending(21, 29);

    public static RiscConstant xo_21_29(int value) {
        return xo_21_29.constant(value);
    }

    private static final ConstantField xo_21_30 = ConstantField.createAscending(21, 30);

    public static RiscConstant xo_21_30(int value) {
        return xo_21_30.constant(value);
    }

    private static final ConstantField xo_22_30 = ConstantField.createAscending(22, 30);

    public static RiscConstant xo_22_30(int value) {
        return xo_22_30.constant(value);
    }

    private static final ConstantField xo_27_29 = ConstantField.createAscending(27, 29);

    public static RiscConstant xo_27_29(int value) {
        return xo_27_29.constant(value);
    }

    private static final ConstantField xo_26_30 = ConstantField.createAscending(26, 30);

    public static RiscConstant xo_26_30(int value) {
        return xo_26_30.constant(value);
    }

    private static final ConstantField xo_27_30 = ConstantField.createAscending(27, 30);

    public static RiscConstant xo_27_30(int value) {
        return xo_27_30.constant(value);
    }

    private static final ConstantField xo_30_31 = ConstantField.createAscending(30, 31);

    public static RiscConstant xo_30_31(int value) {
        return xo_30_31.constant(value);
    }

    public static final ReservedField res_6 = ReservedField.createAscending(6,  6);
    public static final ReservedField res_6_10 = ReservedField.createAscending(6, 10);
    public static final ReservedField res_9 = ReservedField.createAscending(9,  9);
    public static final ReservedField res_9_10 = ReservedField.createAscending(9, 10);
    public static final ReservedField res_11 = ReservedField.createAscending(11, 11);
    public static final ReservedField res_11_15 = ReservedField.createAscending(11, 15);
    public static final ReservedField res_12_20 = ReservedField.createAscending(12, 20);
    public static final ReservedField res_14_15 = ReservedField.createAscending(14, 15);
    public static final ReservedField res_15 = ReservedField.createAscending(15, 15);
    public static final ReservedField res_16_20 = ReservedField.createAscending(16, 20);
    public static final ReservedField res_16_18 = ReservedField.createAscending(16, 18);
    public static final ReservedField res_16_29 = ReservedField.createAscending(16, 29);
    public static final ReservedField res_20 = ReservedField.createAscending(20, 20);
    public static final ReservedField res_21 = ReservedField.createAscending(21, 21);
    public static final ReservedField res_21_25 = ReservedField.createAscending(21, 25);
    public static final ReservedField res_31 = ReservedField.createAscending(31, 31);

    public static final OptionField oe = OptionField.createAscending(21, 21).withOption("", 0).withOption("o", 1);
    public static final OptionField rc = OptionField.createAscending(31, 31).withOption("", 0).withOption("_", 1, ".");
    public static final OptionField lk = OptionField.createAscending(31, 31).withOption("", 0).withOption("l", 1);
    public static final OptionField aa = OptionField.createAscending(30, 30).withOption("", 0).withOption("a", 1);

    public static RiscConstant lk(int value) {
        return lk.constant(value);
    }

    public static final OptionField to_option = OptionField.createAscending(6, 10).
        withOption("lt", 16).
        withOption("le", 20).
        withOption("eq", 4).
        withOption("ge", 12).
        withOption("gt", 8).
        withOption("nl", 12).
        withOption("ne", 24).
        withOption("ng", 20).
        withOption("llt", 2).
        withOption("lle", 6).
        withOption("lge", 5).
        withOption("lgt", 1).
        withOption("lnl", 5).
        withOption("lng", 6);

    public static final OptionField spr_option = OptionField.createAscending(16, 20, 11, 15).
        withOption("xer", SPR.XER).
        withOption("lr", SPR.LR).
        withOption("ctr", SPR.CTR);

    private static OptionField createSuffixField(String suffix) {
        // When using option fields, we sometimes need a suffix in the mnemonic AFTER the option field.
        // We can construct this using option field with one option and no bits in it.
        return OptionField.createAscending(-1).withOption(suffix, 0);
    }

    public static final OptionField put_i_in_name = createSuffixField("i");
    public static final OptionField put_lr_in_name = createSuffixField("lr");
    public static final OptionField put_ctr_in_name = createSuffixField("ctr");

    private static int boTrue(int crValue) {
        return CRTrue.value() | crValue;
    }

    private static int boFalse(int crValue) {
        return CRFalse.value() | crValue;
    }

    public static final OptionField branch_conds = OptionField.createAscending(6, 8, 14, 15).
        withOption("lt", boTrue(CRF.LT)).
        withOption("le", boFalse(CRF.GT)).
        withOption("eq", boTrue(CRF.EQ)).
        withOption("ge", boFalse(CRF.LT)).
        withOption("gt", boTrue(CRF.GT)).
        withOption("nl", boFalse(CRF.LT)).
        withOption("ne", boFalse(CRF.EQ)).
        withOption("ng", boFalse(CRF.GT)).
        withOption("so", boTrue(CRF.SO)).
        withOption("ns", boFalse(CRF.SO)).
        withOption("un", boTrue(CRF.UN)).
        withOption("nu", boFalse(CRF.UN));

    /**
     * An OptionField for the BO values that are in terms of the Count Register (CTR) and a bit in the Condition Register (CR).
     */
    public static final OptionField bo_CTR_and_CR = OptionField.createAscending(6, 10).
        withOption("dnzt", CTRNonZero_CRTrue).
        withOption("dnzf", CTRNonZero_CRFalse).
        withOption("dzt", CTRZero_CRTrue).
        withOption("dzf", CTRZero_CRFalse);

    /**
     * An OptionField for the BO values that are only in terms of the Count Register (CTR) and don't include the prediction bits.
     */
    public static final OptionField bo_CTR = OptionField.createAscending(6, 6, 8, 9).
        withOption("dnz", CTRNonZero.valueWithoutPredictionBits()).
        withOption("dz", CTRZero.valueWithoutPredictionBits());

    /**
     * An OperandField for the prediction bits in the BO values that are only in terms of a bit in the Condition Register (CR).
     */
    public static final SymbolicOperandField<BranchPredictionBits> bo_CR_prediction = SymbolicOperandField.createAscending(BranchPredictionBits.SYMBOLIZER, 9, 10).setVariableName("prediction");

    /**
     * An OperandField for the prediction bits in the BO values that are only in terms of a bit in the Count Register (CTR).
     */
    public static final SymbolicOperandField<BranchPredictionBits> bo_CTR_prediction = SymbolicOperandField.createAscending(BranchPredictionBits.SYMBOLIZER, 7, 7, 10, 10).setVariableName("prediction");

    /**
     * An OptionField for the prediction bits in the BO values that are only in terms of a bit in the Condition Register (CR).
     */
    public static final OptionField bo_CTR_prediction_option = OptionField.createAscending(9, 10).
        withOption("", BranchPredictionBits.NONE).
        withOption("_pt", BranchPredictionBits.PT).
        withOption("_pn", BranchPredictionBits.PN);

    /**
     * An OptionField for the BO values that are only in terms of a bit in the Condition Register (CR) and don't include the prediction bits.
     */
    public static final OptionField bo_CR = OptionField.createAscending(6, 8).
        withOption("t", CRTrue.valueWithoutPredictionBits()).
        withOption("f", CRFalse.valueWithoutPredictionBits());

    /**
     * An OptionField for the prediction bits in the BO values that are only in terms of a bit in the Condition Register (CR).
     */
    public static final OptionField bo_CR_prediction_option = OptionField.createAscending(7, 7, 10, 10).
        withOption("", BranchPredictionBits.NONE).
        withOption("_pt", BranchPredictionBits.PT).
        withOption("_pn", BranchPredictionBits.PN);

    static {
        StaticFieldName.Static.initialize(PPCFields.class, new StaticFieldName.StringFunction() {
            public String function(String name) {
                if (name.startsWith("_")) {
                    return name.substring(1);
                }
                return name;
            }
        });
    }
}
