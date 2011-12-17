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

package com.sun.max.asm.gen.risc.arm;

import static com.sun.max.asm.gen.Expression.Static.*;
import static com.sun.max.asm.gen.InstructionConstraint.Static.*;
import static com.sun.max.asm.gen.risc.arm.ARMFields.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.arm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;

/**
 * Definitions of ARM instructions.
 */

public final class RawInstructions extends ARMInstructionDescriptionCreator {
    final Immediate32Argument zero = new Immediate32Argument(0);

    RawInstructions(RiscTemplateCreator templateCreator) {
        super(templateCreator);

        generateBranch();
        generateDataProcessing();
        generateMultiply();
        generateMiscellaneousArithmetic();
        generateStatusRegisterAccess();
        generateLoadAndStore();
        generateLoadAndStoreMultiple();
        generateSemaphore();
        generateExceptionGenerating();
        generateCoprocessor();
    }

    private void generateCoprocessor() {
    }

    private void generateExceptionGenerating() {
        setCurrentArchitectureManualSection("4.1.7");
        define("bkpt", bits_31_28(14), bits_27_20(18), bits_7_4(7), immediate2, immed_19_8(and(rightShift(immediate2, 4), 0xfff)), immed_3_0(and(immediate2, 0xf)));
        setCurrentArchitectureManualSection("4.1.50");
        define("swi", cond, bits_27_24(15), immed_24);
    }

    private void generateSemaphore() {
        setCurrentArchitectureManualSection("4.1.51");
        define("swp", cond, Rd, Rm, ", [", Rn, "]", bits_27_20(16), sbz_11_8(0), bits_7_4(9), ne(Rd, 15), ne(Rm, 15), ne(Rn, 15), ne(Rn, Rd), ne(Rn, Rm));
        setCurrentArchitectureManualSection("4.1.52");
        define("swpb", cond, Rd, Rm, ", [", Rn, "]", bits_27_20(20), sbz_11_8(0), bits_7_4(9), ne(Rd, 15), ne(Rm, 15), ne(Rn, 15), ne(Rn, Rd), ne(Rn, Rm));
    }

    private void generateLoadAndStoreMultiple() {
        // TODO Auto-generated method stub

    }

    private void generateLoadAndStore() {
        // TODO Auto-generated method stub
        defineLoadAndStoreForAddressingModesExceptPostIndexed("4.1.20", "ldr", b(0), l(1));
        defineLoadAndStoreForPostIndexedAddressingModes("4.1.20", "ldr", b(0), l(1), w(0));

        defineLoadAndStoreForAddressingModesExceptPostIndexed("4.1.44", "str", b(0), l(0));
        defineLoadAndStoreForPostIndexedAddressingModes("4.1.44", "str", b(0), l(0), w(0));

//        defineLoadAndStoreForAddressingModesExceptPostIndexed("4.1.21", "ldrb", b(1), l(1));
//        defineLoadAndStoreForPostIndexedAddressingModes("4.1.21", "ldrb", b(1), l(1), w(0));

//        defineLoadAndStoreForAddressingModesExceptPostIndexed("4.1.45", "strb", b(1), l(0));
//        defineLoadAndStoreForPostIndexedAddressingModes("4.1.45", "strb", b(1), l(0), w(0));

//        defineLoadAndStoreForPostIndexedAddressingModes("4.1.22", "ldrbt", b(1), l(1), w(1));

    }

    private void generateStatusRegisterAccess() {
     // TODO Auto-generated method stub
        setCurrentArchitectureManualSection("4.1.31");
        define("mrscpsr", cond, bit_27(0), bit_26(0), bit_25(0), bit_24(1), bit_23(0), r(0), bit_21(0), bit_20(0), sbo_19_16(15), Rd, sbz_11_0(0), ", cpsr").setExternalName("mrs");
        define("mrsspsr", cond, bit_27(0), bit_26(0), bit_25(0), bit_24(1), bit_23(0), r(1), bit_21(0), bit_20(0), sbo_19_16(15), Rd, sbz_11_0(0), ", spsr").setExternalName("mrs");
        //setCurrentArchitectureManualSection("4.1.32");
        //define("msr");
    }

    private void generateMiscellaneousArithmetic() {
        setCurrentArchitectureManualSection("4.1.12");
        define("clz", cond, bits_27_20(22), sbo_19_16(15), Rd, sbo_11_8(15), bits_7_4(1), Rm, ne(Rd, 15), ne(Rm, 15));
    }

    private void generateMultiply() {
        setCurrentArchitectureManualSection("4.1.28");
        define("mla", cond, bits_27_21(1), s, Rd2, Rm, Rs, Rn2, bits_7_4(9), ne(Rd2, Rm), ne(Rd2, 15), ne(Rm, 15), ne(Rs, 15), ne(Rn2, 15));
        setCurrentArchitectureManualSection("4.1.33");
        define("mul", cond, bits_27_21(0), s, Rd2, Rm, Rs, sbz_15_12(0), bits_7_4(9), ne(Rd2, Rm), ne(Rd2, 15), ne(Rm, 15), ne(Rs, 15));
        setCurrentArchitectureManualSection("4.1.39");
        define("smlal", cond, bits_27_21(7), s, RdLo, RdHi, Rm, Rs, bits_7_4(9), ne(RdLo, RdHi), ne(RdLo, Rm), ne(RdHi, Rm), ne(RdHi, 15), ne(RdLo, 15), ne(Rm, 15), ne(Rs, 15));
        setCurrentArchitectureManualSection("4.1.40");
        define("smull", cond, bits_27_21(6), s, RdLo, RdHi, Rm, Rs, bits_7_4(9), ne(RdLo, RdHi), ne(RdLo, Rm), ne(RdHi, Rm), ne(RdHi, 15), ne(RdLo, 15), ne(Rm, 15), ne(Rs, 15));
        setCurrentArchitectureManualSection("4.1.55");
        define("umlal", cond, bits_27_21(5), s, RdLo, RdHi, Rm, Rs, bits_7_4(9), ne(RdLo, RdHi), ne(RdLo, Rm), ne(RdHi, Rm), ne(RdHi, 15), ne(RdLo, 15), ne(Rm, 15), ne(Rs, 15));
        setCurrentArchitectureManualSection("4.1.56");
        define("umull", cond, bits_27_21(4), s, RdLo, RdHi, Rm, Rs, bits_7_4(9), ne(RdLo, RdHi), ne(RdLo, Rm), ne(RdHi, Rm), ne(RdHi, 15), ne(RdLo, 15), ne(Rm, 15), ne(Rs, 15));
    }

    private void generateDataProcessing() {
        final Method validImmedMethod = getPredicateMethod(ARMImmediates.class, "isValidImmediate", int.class);
        final InstructionConstraint immedConstraint = new TestOnlyInstructionConstraint(makePredicate(validImmedMethod, immediate));

        defineDataProcessingForAllAddressingModes("4.1.2", "adc", s, Rn, Rd, 5, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.3", "add", s, Rn, Rd, 4, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.4", "and", s, Rn, Rd, 0, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.6", "bic", s, Rn, Rd, 14, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.13", "cmn", s(1), Rn, sbz_15_12(0), 11, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.14", "cmp", s(1), Rn, sbz_15_12(0), 10, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.15", "eor", s, Rn, Rd, 1, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.29", "mov", s, sbz_19_16(0), Rd, 13, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.34", "mvn", s, sbz_19_16(0), Rd, 15, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.35", "orr", s, Rn, Rd, 12, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.36", "rsb", s, Rn, Rd, 3, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.37", "rsc", s, Rn, Rd, 7, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.38", "sbc", s, Rn, Rd, 6, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.49", "sub", s, Rn, Rd, 2, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.53", "teq", s(1), Rn, sbz_15_12(0), 9, immedConstraint);
        defineDataProcessingForAllAddressingModes("4.1.54", "tst", s(1), Rn, sbz_15_12(0), 8, immedConstraint);
    }

    private void generateBranch() {
        // TODO Auto-generated method stub

    }

    /**
     * Generates instruction descriptions for all 11 addressing modes of data processing instructions.
     *
     * @param mnemonic assembly instruction mnemonic
     * @param sFld option field which decides whether status registers are updated or not
     * @param source first source operand
     * @param dest destination operand
     * @param opc numeric opcode of the assembly instruction
     * @param constraints constraints on the instruction parameters
     */
    public void defineDataProcessingForAllAddressingModes(String section, String mnemonic, Object sFld, Object source, Object dest, int opc, InstructionConstraint... constraints) {

        setCurrentArchitectureManualSection(section);

        define(mnemonic, cond, bits_27_26(0), i(1), opcode(opc), sFld, dest, source, ", #", immediate, constraints, shifter_operand(encodeShifterOperand(immediate)));
        define(mnemonic, cond, bits_27_26(0), i(1), opcode(opc), sFld, dest, source, ", #", immed_8, ", ", rotate_amount, even(rotate_amount), rotate_imm(div(rotate_amount, 2)));
        define(mnemonic, cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, bits_11_7(0), bits_6_4(0));

        define(mnemonic + "lsl", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", lsl #", shift_imm, bits_6_4(0)).setExternalName(mnemonic);
        define(mnemonic + "lsr", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", lsr #", shift_imm2, shift_imm(mod(shift_imm2, 32)), bits_6_4(2)).setExternalName(mnemonic);
        define(mnemonic + "asr", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", asr #", shift_imm2, shift_imm(mod(shift_imm2, 32)), bits_6_4(4)).setExternalName(mnemonic);
        define(mnemonic + "ror", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero), bits_6_4(6)).setExternalName(mnemonic);

        define(mnemonic + "lsl", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", lsl ", Rs, bits_7_4(1)).setExternalName(mnemonic);
        define(mnemonic + "lsr", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", lsr ", Rs, bits_7_4(3)).setExternalName(mnemonic);
        define(mnemonic + "asr", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", asr ", Rs, bits_7_4(5)).setExternalName(mnemonic);
        define(mnemonic + "ror", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", ror ", Rs, bits_7_4(7)).setExternalName(mnemonic);

        define(mnemonic + "rrx", cond, bits_27_26(0), i(0), opcode(opc), sFld, dest, source, Rm, ", rrx ", bits_11_4(6)).setExternalName(mnemonic);
    }

    /**
     * Generates instruction descriptions for first 6 out of 9 addressing modes for load and store instructions.
     * @param section
     * @param mnemonic
     * @param bFld
     * @param lFld
     */
    public void defineLoadAndStoreForAddressingModesExceptPostIndexed(String section, String mnemonic, Object bFld, Object lFld) {
        setCurrentArchitectureManualSection(section);

        //Addressing mode 1
        define(mnemonic + "add", cond, bits_27_26(1), Rd, ", [", Rn, i(0), p(1), u(1), bFld, w(0), lFld, ", #+", offset_12, "]").setExternalName(mnemonic);
        define(mnemonic + "sub", cond, bits_27_26(1), Rd, ", [", Rn, i(0), p(1), u(0), bFld, w(0), lFld, ", #-", offset_12.withExcludedExternalTestArguments(zero), "]").setExternalName(mnemonic);

        //Addressing mode 2
        define(mnemonic + "add", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, "]", bits_11_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sub", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, "]", bits_11_4(0), ne(Rm, 15)).setExternalName(mnemonic);

        //Addressing mode 3
        define(mnemonic + "addlsl", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, ", lsl #", shift_imm, "]", shift(0), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sublsl", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, ", lsl #", shift_imm.withExcludedExternalTestArguments(zero), "]", shift(0), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addlsr", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, ", lsr #", shift_imm2, "]", shift_imm(mod(shift_imm2, 32)), shift(1), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sublsr", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, ", lsr #", shift_imm2, "]", shift_imm(mod(shift_imm2, 32)), shift(1), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addasr", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, ", asr #", shift_imm2, "]", shift_imm(mod(shift_imm2, 32)), shift(2), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subasr", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, ", asr #", shift_imm2, "]", shift_imm(mod(shift_imm2, 32)), shift(2), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addror", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero), "]",
                shift(3), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subror", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero), "]",
                shift(3), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addrrx", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(0), lFld, ", +", Rm, ", rrx", "]", shift_imm(0), shift(3), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subrrx", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(0), lFld, ", -", Rm, ", rrx", "]", shift_imm(0), shift(3), bit_4(0), ne(Rm, 15)).setExternalName(mnemonic);

        //Addressing mode 4
        define(mnemonic + "addw", cond, bits_27_26(1), Rd, ", [", Rn, i(0), p(1), u(1), bFld, w(1), lFld, ", #+", offset_12, "]", "!", ne(Rd, Rn), ne(Rn, 15)).setExternalName(mnemonic);
        define(mnemonic + "subw", cond, bits_27_26(1), Rd, ", [", Rn, i(0), p(1), u(0), bFld, w(1), lFld, ", #-", offset_12.withExcludedExternalTestArguments(zero), "]", "!", ne(Rd, Rn), ne(Rn, 15)).setExternalName(mnemonic);

        //Addressing mode 5
        define(mnemonic + "addw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, "]", bits_11_4(0), "!", ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, "]", bits_11_4(0), "!", ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);

        //Addressing mode 6
        define(mnemonic + "addlslw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, ", lsl #", shift_imm, "]", "!", shift(0), bit_4(0), ne(Rd, Rn),
                ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sublslw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, ", lsl #", shift_imm.withExcludedExternalTestArguments(zero), "]", "!", shift(0), bit_4(0), ne(Rd, Rn),
                ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addlsrw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, ", lsr #", shift_imm2, "]", "!", shift_imm(mod(shift_imm2, 32)),
                shift(1), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sublsrw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, ", lsr #", shift_imm2, "]", "!", shift_imm(mod(shift_imm2, 32)),
                shift(1), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addasrw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, ", asr #", shift_imm2, "]", "!", shift_imm(mod(shift_imm2, 32)),
                shift(2), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subasrw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, ", asr #", shift_imm2, "]", "!", shift_imm(mod(shift_imm2, 32)),
                shift(2), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addrorw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero), "]", "!",
                shift(3), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subrorw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero), "]", "!",
                shift(3), bit_4(0), ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "addrrxw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(1), bFld, w(1), lFld, ", +", Rm, ", rrx", "]", "!", shift_imm(0), shift(3), bit_4(0),
                ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "subrrxw", cond, bits_27_26(1), Rd, ", [", Rn, i(1), p(1), u(0), bFld, w(1), lFld, ", -", Rm, ", rrx", "]", "!", shift_imm(0), shift(3), bit_4(0),
                ne(Rd, Rn), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);

    }

    /**
     * Generates instruction descriptions for last 3 post indexed addressing modes for load and store instructions.
     * @param section
     * @param mnemonic
     * @param bFld
     * @param lFld
     */
    public void defineLoadAndStoreForPostIndexedAddressingModes(String section, String mnemonic, Object bFld, Object lFld, Object wFld) {
        //Addressing mode 7
        define(mnemonic + "add" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(0), p(0), u(1), bFld, wFld, lFld, ", #+", offset_12, ne(Rn, 15)).setExternalName(mnemonic);
        define(mnemonic + "sub" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(0), p(0), u(0), bFld, wFld, lFld, ", #-", offset_12.withExcludedExternalTestArguments(zero), ne(Rn, 15)).setExternalName(mnemonic);

        //Addressing mode 8
        define(mnemonic + "add" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, bits_11_4(0), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);
        define(mnemonic + "sub" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, bits_11_4(0), ne(Rm, Rn), ne(Rn, 15), ne(Rm, 15)).setExternalName(mnemonic);

        //Addressing mode 9
        define(mnemonic + "addlsl" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, ", lsl #", shift_imm, shift(0), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "sublsl" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, ", lsl #", shift_imm.withExcludedExternalTestArguments(zero),
                shift(0), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "addlsr" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, ", lsr #", shift_imm2, shift_imm(mod(shift_imm2, 32)),
                shift(1), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "sublsr" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, ", lsr #", shift_imm2, shift_imm(mod(shift_imm2, 32)),
                shift(1), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "addasr" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, ", asr #", shift_imm2, shift_imm(mod(shift_imm2, 32)),
            shift(2), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "subasr" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, ", asr #", shift_imm2, shift_imm(mod(shift_imm2, 32)),
            shift(2), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "addror" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero),
                shift(3), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "subror" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, ", ror #", shift_imm.withExcludedExternalTestArguments(zero),
                shift(3), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "addrrx" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(1), bFld, wFld, lFld, ", +", Rm, ", rrx", shift_imm(0), shift(3), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);
        define(mnemonic + "subrrx" + "post", cond, bits_27_26(1), Rd, ", [", Rn, "]", i(1), p(0), u(0), bFld, wFld, lFld, ", -", Rm, ", rrx", shift_imm(0), shift(3), bit_4(0), ne(Rm, 15), ne(Rn, 15), ne(Rm, Rn)).setExternalName(mnemonic);

    }

    /**
     * Encodes the given 32 bit immediate value as an 8 bit immediate value and a 4 bit rotate value.
     *
     * @param term
     *            the 32 bit immediate value
     */
    public static Expression encodeShifterOperand(final Object term) {
        return new Expression() {
            public long evaluate(Template template, List<Argument> arguments) {
                return ARMImmediates.calculateShifter((int) Static.evaluateTerm(term, template, arguments));
            }

            public String valueString() {
                return ARMImmediates.class.getSimpleName() + "." + "calculateShifter(" + Static.termValueString(term) + ")";
            }
        };
    }

}
