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
package com.sun.max.asm.arm.example;

import com.sun.max.asm.arm.*;

/**
 * @see ExampleARMAssemblerSpecification
 * @see ExampleARMAssemblerSpecification.Generator
 */
public class ExampleARMAssembler extends AbstractARMAssembler {

    public ExampleARMAssembler(int startAddress) {
        super(startAddress);
    }

    public ExampleARMAssembler() {
    }

// START GENERATED RAW ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code adceq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 1, Serial#: 2
    public void adc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02A00000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 2, Serial#: 4
    public void adclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00A00000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code addeq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 3, Serial#: 14
    public void add(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02800000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 4, Serial#: 19
    public void addror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00800060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 5, Serial#: 45
    public void biclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01C00030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 6, Serial#: 58
    public void cmnasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01700050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 7, Serial#: 59
    public void cmnror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01700070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 8, Serial#: 66
    public void cmpasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01500040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 9, Serial#: 81
    public void eorlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00200030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code moveq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 10, Serial#: 91
    public void movror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01A00060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 11, Serial#: 107
    public void mvnror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01E00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 12, Serial#: 112
    public void orrlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01800000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 13, Serial#: 122
    public void rsb(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02600000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 14, Serial#: 124
    public void rsblsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00600000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 15, Serial#: 135
    public void rsc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00E00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 16, Serial#: 141
    public void rsclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00E00030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 17, Serial#: 155
    public void sbcror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00C00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code subeq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 18, Serial#: 158
    public void sub(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02400000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code tsteq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 19, Serial#: 181
    public void tst(final ConditionCode cond, final GPR Rn, final int immediate) {
        int instruction = 0x03100000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 20, Serial#: 185
    public void tstlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01100020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code smlal{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>RdLo</i>, <i>RdHi</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code smlaleq       r0, r0, r0, r0}
     * <p>
     * Constraint: {@code RdLo.value() != RdHi.value()}<br />
     * Constraint: {@code RdLo.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != 15}<br />
     * Constraint: {@code RdLo.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.39"
     */
    // Template#: 21, Serial#: 195
    public void smlal(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs) {
        int instruction = 0x00E00090;
        checkConstraint(RdLo.value() != RdHi.value(), "RdLo.value() != RdHi.value()");
        checkConstraint(RdLo.value() != Rm.value(), "RdLo.value() != Rm.value()");
        checkConstraint(RdHi.value() != Rm.value(), "RdHi.value() != Rm.value()");
        checkConstraint(RdHi.value() != 15, "RdHi.value() != 15");
        checkConstraint(RdLo.value() != 15, "RdLo.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rs.value() != 15, "Rs.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((RdLo.value() & 0xf) << 12);
        instruction |= ((RdHi.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code umull{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>RdLo</i>, <i>RdHi</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code umulleq       r0, r0, r0, r0}
     * <p>
     * Constraint: {@code RdLo.value() != RdHi.value()}<br />
     * Constraint: {@code RdLo.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != 15}<br />
     * Constraint: {@code RdLo.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.56"
     */
    // Template#: 22, Serial#: 198
    public void umull(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs) {
        int instruction = 0x00800090;
        checkConstraint(RdLo.value() != RdHi.value(), "RdLo.value() != RdHi.value()");
        checkConstraint(RdLo.value() != Rm.value(), "RdLo.value() != Rm.value()");
        checkConstraint(RdHi.value() != Rm.value(), "RdHi.value() != Rm.value()");
        checkConstraint(RdHi.value() != 15, "RdHi.value() != 15");
        checkConstraint(RdLo.value() != 15, "RdLo.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rs.value() != 15, "Rs.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((RdLo.value() & 0xf) << 12);
        instruction |= ((RdHi.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 23, Serial#: 204
    public void ldradd(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07900000;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, #-0x0]!}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 24, Serial#: 217
    public void ldrsubw(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05300000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 25, Serial#: 240
    public void ldraddrorpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06900060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, asr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 26, Serial#: 253
    public void strsubasr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07000040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, ror #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 27, Serial#: 269
    public void strsubrorw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07200060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0], #+0x0}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 28, Serial#: 272
    public void straddpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x04800000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code swi{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>immed_24</i>
     * Example disassembly syntax: {@code swieq         0x0}
     * <p>
     * Constraint: {@code 0 <= immed_24 && immed_24 <= 16777215}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.50"
     */
    // Template#: 29, Serial#: 289
    public void swi(final ConditionCode cond, final int immed_24) {
        int instruction = 0x0F000000;
        checkConstraint(0 <= immed_24 && immed_24 <= 16777215, "0 <= immed_24 && immed_24 <= 16777215");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= (immed_24 & 0xffffff);
        emitInt(instruction);
    }

// END GENERATED RAW ASSEMBLER METHODS

// START GENERATED LABEL ASSEMBLER METHODS
// END GENERATED LABEL ASSEMBLER METHODS

}
