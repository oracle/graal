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

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.lang.*;

/**
 * The fields used in defining the SPARC instruction templates.
 */
public final class SPARCFields {

    private SPARCFields() {
    }

    // Checkstyle: stop constant name checks

    private static final ConstantField bits_29_29 = ConstantField.createDescending(29, 29);

    public static RiscConstant bits_29_29(int value) {
        return bits_29_29.constant(value);
    }

    private static final ConstantField bits_29_27 = ConstantField.createDescending(29, 27);

    public static RiscConstant bits_29_27(int value) {
        return bits_29_27.constant(value);
    }

    private static final ConstantField bits_28_28 = ConstantField.createDescending(28, 28);

    public static RiscConstant bits_28_28(int value) {
        return bits_28_28.constant(value);
    }

    private static final ConstantField bits_24_22 = ConstantField.createDescending(24, 22);

    public static RiscConstant bits_24_22(int value) {
        return bits_24_22.constant(value);
    }

    private static final ConstantField bits_18_18 = ConstantField.createDescending(18, 18);

    public static RiscConstant bits_18_18(int value) {
        return bits_18_18.constant(value);
    }

    private static final ConstantField bits_18_14 = ConstantField.createDescending(18, 14);

    public static RiscConstant bits_18_14(int value) {
        return bits_18_14.constant(value);
    }

    private static final ConstantField bits_13_13 = ConstantField.createDescending(13, 13);

    public static RiscConstant bits_13_13(int value) {
        return bits_13_13.constant(value);
    }

    private static final ConstantField cond_17_14 = ConstantField.createDescending(17, 14);

    public static RiscConstant cond_17_14(int value) {
        return cond_17_14.constant(value);
    }

    private static final ConstantField fcnc = ConstantField.createDescending(29, 25);

    public static RiscConstant fcnc(int value) {
        return fcnc.constant(value);
    }

    private static final ConstantField i = ConstantField.createDescending(13, 13);

    public static RiscConstant i(int value) {
        return i.constant(value);
    }

    private static final ConstantField movTypeBit = ConstantField.createDescending(18, 18);

    public static RiscConstant movTypeBit(int value) {
        return movTypeBit.constant(value);
    }

    private static final ConstantField fmovTypeBit = ConstantField.createDescending(13, 13);

    public static RiscConstant fmovTypeBit(int value) {
        return fmovTypeBit.constant(value);
    }

    private static final ConstantField op = ConstantField.createDescending(31, 30);

    public static RiscConstant op(int value) {
        return op.constant(value);
    }

    private static final ConstantField op2 = ConstantField.createDescending(24, 22);

    public static RiscConstant op2(int value) {
        return op2.constant(value);
    }

    private static final ConstantField op3 = ConstantField.createDescending(24, 19);

    public static RiscConstant op3(int value) {
        return op3.constant(value);
    }

    private static final ConstantField opf = ConstantField.createDescending(13, 5);

    public static RiscConstant opf(int value) {
        return opf.constant(value);
    }

    private static final ConstantField opfLow_10_5 = ConstantField.createDescending(10, 5);

    public static RiscConstant opfLow_10_5(int value) {
        return opfLow_10_5.constant(value);
    }

    private static final ConstantField opfLow_9_5 = ConstantField.createDescending(9, 5);

    public static RiscConstant opfLow_9_5(int value) {
        return opfLow_9_5.constant(value);
    }

    private static final ConstantField rcond_12_10 = ConstantField.createDescending(12, 10);

    public static RiscConstant rcond_12_10(int value) {
        return rcond_12_10.constant(value);
    }

    private static final ConstantField x = ConstantField.createDescending(12, 12);

    public static RiscConstant x(int value) {
        return x.constant(value);
    }

    public static final IgnoredOperandField const22 = IgnoredOperandField.createDescendingIgnored(21, 0);

    public static final ImmediateOperandField fcn = ImmediateOperandField.createDescending(29, 25);

    public static final ImmediateOperandField imm22 = ImmediateOperandField.createDescending(21, 0).beSignedOrUnsigned();

    public static RiscConstant imm22(int value) {
        return imm22.constant(value);
    }

    public static final ImmediateOperandField immAsi = ImmediateOperandField.createDescending(12, 5);

    public static RiscConstant immAsi(int value) {
        return immAsi.constant(value);
    }

    public static final ImmediateOperandField shcnt32 = ImmediateOperandField.createDescending(4, 0);
    public static final ImmediateOperandField shcnt64 = ImmediateOperandField.createDescending(5, 0);
    public static final ImmediateOperandField simm10 = ImmediateOperandField.createDescending(9, 0).beSigned();
    public static final ImmediateOperandField simm11 = ImmediateOperandField.createDescending(10, 0).beSigned();
    public static final ImmediateOperandField swTrapNumber = ImmediateOperandField.createDescending(6, 0);
    static {
        swTrapNumber.setVariableName("software_trap_number");
    }

    public static final ImmediateOperandField simm13 = ImmediateOperandField.createDescending(12, 0).beSigned();

    public static RiscConstant simm13(int value) {
        return simm13.constant(value);
    }

    public static final BranchDisplacementOperandField disp30 = BranchDisplacementOperandField.createDescendingBranchDisplacementOperandField(29, 0);
    public static final BranchDisplacementOperandField disp22 = BranchDisplacementOperandField.createDescendingBranchDisplacementOperandField(21, 0);
    public static final BranchDisplacementOperandField disp19 = BranchDisplacementOperandField.createDescendingBranchDisplacementOperandField(18, 0);
    public static final BranchDisplacementOperandField d16 = BranchDisplacementOperandField.createDescendingBranchDisplacementOperandField(21, 20, 13, 0);

    public static final SymbolicOperandField<MembarOperand> membarMask = SymbolicOperandField.createDescending(MembarOperand.SYMBOLIZER, 6, 0);

    private static SymbolicOperandField<ICCOperand> createICCOperandField(int... bits) {
        return SymbolicOperandField.createDescending("i_or_x_cc", ICCOperand.SYMBOLIZER, bits);
    }

    private static SymbolicOperandField<FCCOperand> createFCCOperandField(int... bits) {
        return SymbolicOperandField.createDescending("n", FCCOperand.SYMBOLIZER, bits);
    }

    public static final SymbolicOperandField<ICCOperand> cc = createICCOperandField(21, 20);

    public static RiscConstant cc(ICCOperand icc) {
        return cc.constant(icc);
    }

    public static final SymbolicOperandField<FCCOperand> fcc_26_25 = createFCCOperandField(26, 25);
    public static final SymbolicOperandField<FCCOperand> fcc_21_20 = createFCCOperandField(21, 20);
    public static final SymbolicOperandField<ICCOperand> fmovicc = createICCOperandField(12, 11);
    public static final SymbolicOperandField<FCCOperand> fmovfcc = createFCCOperandField(12, 11);
    public static final SymbolicOperandField<ICCOperand> movicc = createICCOperandField(12, 11);
    public static final SymbolicOperandField<FCCOperand> movfcc = createFCCOperandField(12, 11);

    public static final SymbolicOperandField<GPR> rs1 = SymbolicOperandField.createDescending(GPR.SYMBOLIZER, 18, 14);

    public static RiscConstant rs1(GPR gpr) {
        return rs1.constant(gpr);
    }

    public static SymbolicOperandField<GPR> rs1(Expression expression) {
        return rs1.bindTo(expression);
    }

    public static RiscConstant rs1(int value) {
        return rs1.constant(value);
    }

    public static final SymbolicOperandField<StateRegister> rs1_state = SymbolicOperandField.createDescending("rs1", StateRegister.SYMBOLIZER, 18, 14);

    public static RiscConstant rs1_state(int value) {
        return rs1_state.constant(value);
    }

    public static final SymbolicOperandField<GPR> rs2 = SymbolicOperandField.createDescending(GPR.SYMBOLIZER, 4, 0);

    public static RiscConstant rs2(GPR gpr) {
        return rs2.constant(gpr);
    }

    public static SymbolicOperandField<GPR> rs2(Expression expression) {
        return rs2.bindTo(expression);
    }

    public static final SymbolicOperandField<GPR> rd = SymbolicOperandField.createDescending(GPR.SYMBOLIZER, 29, 25);

    public static RiscConstant rd(GPR gpr) {
        return rd.constant(gpr);
    }

    public static RiscConstant rd(int value) {
        return rd.constant(value);
    }

    public static final SymbolicOperandField<GPR.Even> rd_even = SymbolicOperandField.createDescending("rd", GPR.EVEN_SYMBOLIZER, 29, 25);

    public static final SymbolicOperandField<StateRegister.Writable> rd_state = SymbolicOperandField.createDescending("rd", StateRegister.WRITE_ONLY_SYMBOLIZER, 29, 25);

    public static final SymbolicOperandField<ICCOperand> tcc = createICCOperandField(12, 11);
    public static final SymbolicOperandField<SFPR> sfrs1 = SymbolicOperandField.createDescending("rs1", SFPR.SYMBOLIZER, 18, 14);
    public static final SymbolicOperandField<SFPR> sfrs2 = SymbolicOperandField.createDescending("rs2", SFPR.SYMBOLIZER, 4, 0);
    public static final SymbolicOperandField<SFPR> sfrd = SymbolicOperandField.createDescending("rd", SFPR.SYMBOLIZER, 29, 25);
    public static final SymbolicOperandField<DFPR> dfrs1 = SymbolicOperandField.createDescending("rs1", DFPR.SYMBOLIZER, 14, 14, 18, 15, -1);
    public static final SymbolicOperandField<DFPR> dfrs2 = SymbolicOperandField.createDescending("rs2", DFPR.SYMBOLIZER, 0, 0, 4, 1, -1);
    public static final SymbolicOperandField<DFPR> dfrd = SymbolicOperandField.createDescending("rd", DFPR.SYMBOLIZER, 25, 25, 29, 26, -1);
    public static final SymbolicOperandField<PrivilegedRegister> rs1PrivReg = SymbolicOperandField.createDescending("rs1", PrivilegedRegister.SYMBOLIZER, 18, 14);
    public static final SymbolicOperandField<PrivilegedRegister.Writable> rdPrivReg = SymbolicOperandField.createDescending("rd", PrivilegedRegister.WRITE_ONLY_SYMBOLIZER, 29, 25);

    private static final SymbolicOperandField<QFPR> qfrs1_raw = SymbolicOperandField.createDescending("rs1", QFPR.SYMBOLIZER, 14, 14, 18, 16, -2);
    private static final SymbolicOperandField<QFPR> qfrs2_raw = SymbolicOperandField.createDescending("rs2", QFPR.SYMBOLIZER, 0, 0, 4, 2, -2);
    private static final SymbolicOperandField<QFPR> qfrd_raw = SymbolicOperandField.createDescending("rd", QFPR.SYMBOLIZER, 25, 25, 29, 27, -2);

    public static final Object[] qfrs1 = {qfrs1_raw, ReservedField.createDescending(15, 15)};
    public static final Object[] qfrs2 = {qfrs2_raw, ReservedField.createDescending(1, 1)};
    public static final Object[] qfrd =  {qfrd_raw, ReservedField.createDescending(26, 26)};

    public static final SymbolicOperandField<BPr> rcond_27_25 = SymbolicOperandField.createDescending("cond", BPr.SYMBOLIZER, 27, 25);

    public static RiscConstant rcond_27_25(BPr value) {
        return rcond_27_25.constant(value);
    }

    public static final SymbolicOperandField<FBfcc> fcond_28_25 = SymbolicOperandField.createDescending("cond", FBfcc.SYMBOLIZER, 28, 25);

    public static RiscConstant fcond_28_25(FBfcc value) {
        return fcond_28_25.constant(value);
    }

    public static final SymbolicOperandField<Bicc> icond_28_25 = SymbolicOperandField.createDescending("cond", Bicc.SYMBOLIZER, 28, 25);

    public static RiscConstant icond_28_25(Bicc value) {
        return icond_28_25.constant(value);
    }

    public static final SymbolicOperandField<AnnulBit> a = SymbolicOperandField.createDescending(AnnulBit.SYMBOLIZER, 29, 29);
    public static RiscConstant a(AnnulBit value) {
        return a.constant(value);
    }

    public static final SymbolicOperandField<BranchPredictionBit> p = SymbolicOperandField.createDescending(BranchPredictionBit.SYMBOLIZER, 19, 19);
    public static RiscConstant p(BranchPredictionBit value) {
        return p.constant(value);
    }

    public static final ReservedField res_29_29 = ReservedField.createDescending(29, 29);
    public static final ReservedField res_29_25 = ReservedField.createDescending(29, 25);
    public static final ReservedField res_18_14 = ReservedField.createDescending(18, 14);
    public static final ReservedField res_18_0 = ReservedField.createDescending(18, 0);
    public static final ReservedField res_13_0 = ReservedField.createDescending(13, 0);
    public static final ReservedField res_12_7 = ReservedField.createDescending(12, 7);
    public static final ReservedField res_12_5 = ReservedField.createDescending(12, 5);
    public static final ReservedField res_12_0 = ReservedField.createDescending(12, 0);
    public static final ReservedField res_11_6 = ReservedField.createDescending(11, 6);
    public static final ReservedField res_11_5 = ReservedField.createDescending(11, 5);
    public static final ReservedField res_10_7 = ReservedField.createDescending(10, 7);
    public static final ReservedField res_10_5 = ReservedField.createDescending(10, 5);
    public static final ReservedField res_9_5 = ReservedField.createDescending(9, 5);
    public static final ReservedField impl_dep = ReservedField.createDescending(29, 25, 18, 0);

    // Checkstyle: resume constant name checks

    static {
        StaticFieldName.Static.initialize(SPARCFields.class, new StaticFieldName.StringFunction() {
            public String function(String name) {
                if (name.startsWith("_")) {
                    return name.substring(1);
                }
                return name;
            }
        });
        StaticFieldLiteral.Static.initialize(SPARCFields.class);
    }
}
