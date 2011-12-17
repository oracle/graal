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

package com.sun.max.asm.arm.complete;

import com.sun.max.asm.arm.*;

public abstract class ARMRawAssembler extends AbstractARMAssembler {

    protected ARMRawAssembler(int startAddress) {
        super(startAddress);
    }

    protected ARMRawAssembler() {
        super();
    }

// START GENERATED RAW ASSEMBLER METHODS
    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code adceq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 1, Serial#: 1
    public void adc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02A00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

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
    // Template#: 2, Serial#: 2
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
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 3, Serial#: 3
    public void adc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00A00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
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
    // Template#: 4, Serial#: 4
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
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 5, Serial#: 5
    public void adclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00A00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 6, Serial#: 6
    public void adcasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00A00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 7, Serial#: 7
    public void adcror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00A00060;
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
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 8, Serial#: 8
    public void adclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00A00010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 9, Serial#: 9
    public void adclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00A00030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 10, Serial#: 10
    public void adcasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00A00050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 11, Serial#: 11
    public void adcror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00A00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code adc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code adceq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.2"
     */
    // Template#: 12, Serial#: 12
    public void adcrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00A00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code addeq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 13, Serial#: 13
    public void add(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02800000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
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
    // Template#: 14, Serial#: 14
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
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 15, Serial#: 15
    public void add(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00800000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 16, Serial#: 16
    public void addlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00800000;
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
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 17, Serial#: 17
    public void addlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00800020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 18, Serial#: 18
    public void addasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00800040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
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
    // Template#: 19, Serial#: 19
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
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 20, Serial#: 20
    public void addlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00800010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 21, Serial#: 21
    public void addlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00800030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 22, Serial#: 22
    public void addasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00800050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 23, Serial#: 23
    public void addror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00800070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code add{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code addeq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.3"
     */
    // Template#: 24, Serial#: 24
    public void addrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00800060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code andeq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 25, Serial#: 25
    public void and(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02000000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code andeq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 26, Serial#: 26
    public void and(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02000000;
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
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 27, Serial#: 27
    public void and(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00000000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 28, Serial#: 28
    public void andlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00000000;
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
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 29, Serial#: 29
    public void andlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00000020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 30, Serial#: 30
    public void andasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00000040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 31, Serial#: 31
    public void andror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00000060;
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
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 32, Serial#: 32
    public void andlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00000010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 33, Serial#: 33
    public void andlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00000030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 34, Serial#: 34
    public void andasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00000050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 35, Serial#: 35
    public void andror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00000070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code and{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code andeq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.4"
     */
    // Template#: 36, Serial#: 36
    public void andrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00000060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code biceq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 37, Serial#: 37
    public void bic(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x03C00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code biceq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 38, Serial#: 38
    public void bic(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03C00000;
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
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 39, Serial#: 39
    public void bic(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x01C00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 40, Serial#: 40
    public void biclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01C00000;
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
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 41, Serial#: 41
    public void biclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01C00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 42, Serial#: 42
    public void bicasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01C00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 43, Serial#: 43
    public void bicror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01C00060;
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
     * Example disassembly syntax: {@code biceq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 44, Serial#: 44
    public void biclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01C00010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 45, Serial#: 45
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
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 46, Serial#: 46
    public void bicasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01C00050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 47, Serial#: 47
    public void bicror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01C00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bic{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code biceq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.6"
     */
    // Template#: 48, Serial#: 48
    public void bicrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x01C00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code cmneq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 49, Serial#: 49
    public void cmn(final ConditionCode cond, final GPR Rn, final int immediate) {
        int instruction = 0x03700000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code cmneq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 50, Serial#: 50
    public void cmn(final ConditionCode cond, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03700000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 51, Serial#: 51
    public void cmn(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01700000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 52, Serial#: 52
    public void cmnlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01700000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 53, Serial#: 53
    public void cmnlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01700020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 54, Serial#: 54
    public void cmnasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01700040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 55, Serial#: 55
    public void cmnror(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01700060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 56, Serial#: 56
    public void cmnlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01700010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 57, Serial#: 57
    public void cmnlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01700030;
        instruction |= ((cond.value() & 0xf) << 28);
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
    // Template#: 58, Serial#: 58
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
    // Template#: 59, Serial#: 59
    public void cmnror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01700070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code cmneq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.13"
     */
    // Template#: 60, Serial#: 60
    public void cmnrrx(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01700060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code cmpeq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 61, Serial#: 61
    public void cmp(final ConditionCode cond, final GPR Rn, final int immediate) {
        int instruction = 0x03500000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code cmpeq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 62, Serial#: 62
    public void cmp(final ConditionCode cond, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03500000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 63, Serial#: 63
    public void cmp(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01500000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 64, Serial#: 64
    public void cmplsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01500000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 65, Serial#: 65
    public void cmplsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01500020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
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
    // Template#: 66, Serial#: 66
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
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 67, Serial#: 67
    public void cmpror(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01500060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 68, Serial#: 68
    public void cmplsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01500010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 69, Serial#: 69
    public void cmplsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01500030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 70, Serial#: 70
    public void cmpasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01500050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 71, Serial#: 71
    public void cmpror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01500070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code cmp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code cmpeq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.14"
     */
    // Template#: 72, Serial#: 72
    public void cmprrx(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01500060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 73, Serial#: 73
    public void eor(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02200000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 74, Serial#: 74
    public void eor(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02200000;
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
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 75, Serial#: 75
    public void eor(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00200000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 76, Serial#: 76
    public void eorlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00200000;
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
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 77, Serial#: 77
    public void eorlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00200020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 78, Serial#: 78
    public void eorasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00200040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 79, Serial#: 79
    public void eorror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00200060;
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
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 80, Serial#: 80
    public void eorlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00200010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 81, Serial#: 81
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
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 82, Serial#: 82
    public void eorasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00200050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 83, Serial#: 83
    public void eorror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00200070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code eor{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code eoreq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.15"
     */
    // Template#: 84, Serial#: 84
    public void eorrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00200060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>immediate</i>
     * Example disassembly syntax: {@code moveq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 85, Serial#: 85
    public void mov(final ConditionCode cond, final SBit s, final GPR Rd, final int immediate) {
        int instruction = 0x03A00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code moveq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 86, Serial#: 86
    public void mov(final ConditionCode cond, final SBit s, final GPR Rd, final int immed_8, final int rotate_amount) {
        int instruction = 0x03A00000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>
     * Example disassembly syntax: {@code moveq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 87, Serial#: 87
    public void mov(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm) {
        int instruction = 0x01A00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code moveq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 88, Serial#: 88
    public void movlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01A00000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code moveq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 89, Serial#: 89
    public void movlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01A00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code moveq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 90, Serial#: 90
    public void movasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01A00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
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
    // Template#: 91, Serial#: 91
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
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code moveq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 92, Serial#: 92
    public void movlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01A00010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code moveq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 93, Serial#: 93
    public void movlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01A00030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code moveq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 94, Serial#: 94
    public void movasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01A00050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code moveq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 95, Serial#: 95
    public void movror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01A00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mov{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>
     * Example disassembly syntax: {@code moveq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.29"
     */
    // Template#: 96, Serial#: 96
    public void movrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm) {
        int instruction = 0x01A00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>immediate</i>
     * Example disassembly syntax: {@code mvneq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 97, Serial#: 97
    public void mvn(final ConditionCode cond, final SBit s, final GPR Rd, final int immediate) {
        int instruction = 0x03E00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code mvneq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 98, Serial#: 98
    public void mvn(final ConditionCode cond, final SBit s, final GPR Rd, final int immed_8, final int rotate_amount) {
        int instruction = 0x03E00000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 99, Serial#: 99
    public void mvn(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm) {
        int instruction = 0x01E00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 100, Serial#: 100
    public void mvnlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01E00000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 101, Serial#: 101
    public void mvnlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01E00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 102, Serial#: 102
    public void mvnasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01E00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 103, Serial#: 103
    public void mvnror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final int shift_imm) {
        int instruction = 0x01E00060;
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
     * Example disassembly syntax: {@code mvneq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 104, Serial#: 104
    public void mvnlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01E00010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 105, Serial#: 105
    public void mvnlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01E00030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 106, Serial#: 106
    public void mvnasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x01E00050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 107, Serial#: 107
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
     * Pseudo-external assembler syntax: {@code mvn{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>
     * Example disassembly syntax: {@code mvneq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.34"
     */
    // Template#: 108, Serial#: 108
    public void mvnrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm) {
        int instruction = 0x01E00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code orreq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 109, Serial#: 109
    public void orr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x03800000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code orreq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 110, Serial#: 110
    public void orr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03800000;
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
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 111, Serial#: 111
    public void orr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x01800000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
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
    // Template#: 112, Serial#: 112
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
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 113, Serial#: 113
    public void orrlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01800020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 114, Serial#: 114
    public void orrasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01800040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 115, Serial#: 115
    public void orrror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01800060;
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
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 116, Serial#: 116
    public void orrlsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01800010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 117, Serial#: 117
    public void orrlsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01800030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 118, Serial#: 118
    public void orrasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01800050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 119, Serial#: 119
    public void orrror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01800070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code orr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code orreq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.35"
     */
    // Template#: 120, Serial#: 120
    public void orrrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x01800060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 121, Serial#: 121
    public void rsb(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02600000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
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
    // Template#: 122, Serial#: 122
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
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 123, Serial#: 123
    public void rsb(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00600000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
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
    // Template#: 124, Serial#: 124
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
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 125, Serial#: 125
    public void rsblsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00600020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 126, Serial#: 126
    public void rsbasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00600040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 127, Serial#: 127
    public void rsbror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00600060;
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
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 128, Serial#: 128
    public void rsblsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00600010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 129, Serial#: 129
    public void rsblsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00600030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 130, Serial#: 130
    public void rsbasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00600050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 131, Serial#: 131
    public void rsbror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00600070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code rsbeq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.36"
     */
    // Template#: 132, Serial#: 132
    public void rsbrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00600060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 133, Serial#: 133
    public void rsc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02E00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 134, Serial#: 134
    public void rsc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02E00000;
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
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 135, Serial#: 135
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
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 136, Serial#: 136
    public void rsclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00E00000;
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
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 137, Serial#: 137
    public void rsclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00E00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 138, Serial#: 138
    public void rscasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00E00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 139, Serial#: 139
    public void rscror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00E00060;
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
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 140, Serial#: 140
    public void rsclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00E00010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 141, Serial#: 141
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
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 142, Serial#: 142
    public void rscasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00E00050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 143, Serial#: 143
    public void rscror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00E00070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code rsc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code rsceq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.37"
     */
    // Template#: 144, Serial#: 144
    public void rscrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00E00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 145, Serial#: 145
    public void sbc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02C00000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 146, Serial#: 146
    public void sbc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x02C00000;
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
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 147, Serial#: 147
    public void sbc(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00C00000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 148, Serial#: 148
    public void sbclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00C00000;
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
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 149, Serial#: 149
    public void sbclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00C00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 150, Serial#: 150
    public void sbcasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00C00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 151, Serial#: 151
    public void sbcror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00C00060;
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
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 152, Serial#: 152
    public void sbclsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00C00010;
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
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 153, Serial#: 153
    public void sbclsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00C00030;
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
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 154, Serial#: 154
    public void sbcasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00C00050;
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
    // Template#: 155, Serial#: 155
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
     * Pseudo-external assembler syntax: {@code sbc{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code sbceq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.38"
     */
    // Template#: 156, Serial#: 156
    public void sbcrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00C00060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code subeq         r0, r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 157, Serial#: 157
    public void sub(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final int immediate) {
        int instruction = 0x02400000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
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
    // Template#: 158, Serial#: 158
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
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 159, Serial#: 159
    public void sub(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00400000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 160, Serial#: 160
    public void sublsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00400000;
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
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 161, Serial#: 161
    public void sublsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00400020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 162, Serial#: 162
    public void subasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00400040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 163, Serial#: 163
    public void subror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x00400060;
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
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 164, Serial#: 164
    public void sublsl(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00400010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 165, Serial#: 165
    public void sublsr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00400030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 166, Serial#: 166
    public void subasr(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00400050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 167, Serial#: 167
    public void subror(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x00400070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code sub{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code subeq         r0, r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.49"
     */
    // Template#: 168, Serial#: 168
    public void subrrx(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x00400060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immediate</i>
     * Example disassembly syntax: {@code teqeq         r0, #0x0}
     * <p>
     * Constraint: {@code ARMImmediates.isValidImmediate(immediate)}<br />
     * Constraint: {@code 0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095}<br />
     *
     * @see com.sun.max.asm.arm.ARMImmediates#isValidImmediate
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 169, Serial#: 169
    public void teq(final ConditionCode cond, final GPR Rn, final int immediate) {
        int instruction = 0x03300000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code teqeq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 170, Serial#: 170
    public void teq(final ConditionCode cond, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03300000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 171, Serial#: 171
    public void teq(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01300000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 172, Serial#: 172
    public void teqlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01300000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 173, Serial#: 173
    public void teqlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01300020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 174, Serial#: 174
    public void teqasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01300040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 175, Serial#: 175
    public void teqror(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01300060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 176, Serial#: 176
    public void teqlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01300010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 177, Serial#: 177
    public void teqlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01300030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 178, Serial#: 178
    public void teqasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01300050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 179, Serial#: 179
    public void teqror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01300070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code teq{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code teqeq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.53"
     */
    // Template#: 180, Serial#: 180
    public void teqrrx(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01300060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
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
    // Template#: 181, Serial#: 181
    public void tst(final ConditionCode cond, final GPR Rn, final int immediate) {
        int instruction = 0x03100000;
        checkConstraint(0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095, "0 <= ARMImmediates.calculateShifter(immediate) && ARMImmediates.calculateShifter(immediate) <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (ARMImmediates.calculateShifter(immediate) & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>immed_8</i>, <i>rotate_amount</i>
     * Example disassembly syntax: {@code tsteq         r0, #0x0, 0x0}
     * <p>
     * Constraint: {@code 0 <= immed_8 && immed_8 <= 255}<br />
     * Constraint: {@code (rotate_amount % 2) == 0}<br />
     * Constraint: {@code 0 <= rotate_amount / 2 && rotate_amount / 2 <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 182, Serial#: 182
    public void tst(final ConditionCode cond, final GPR Rn, final int immed_8, final int rotate_amount) {
        int instruction = 0x03100000;
        checkConstraint(0 <= immed_8 && immed_8 <= 255, "0 <= immed_8 && immed_8 <= 255");
        checkConstraint((rotate_amount % 2) == 0, "(rotate_amount % 2) == 0");
        checkConstraint(0 <= rotate_amount / 2 && rotate_amount / 2 <= 15, "0 <= rotate_amount / 2 && rotate_amount / 2 <= 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (immed_8 & 0xff);
        instruction |= ((rotate_amount / 2 & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 183, Serial#: 183
    public void tst(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01100000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 184, Serial#: 184
    public void tstlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01100000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
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
    // Template#: 185, Serial#: 185
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
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 186, Serial#: 186
    public void tstasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01100040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 187, Serial#: 187
    public void tstror(final ConditionCode cond, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x01100060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, lsl r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 188, Serial#: 188
    public void tstlsl(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01100010;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, lsr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 189, Serial#: 189
    public void tstlsr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01100030;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, asr r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 190, Serial#: 190
    public void tstasr(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01100050;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, ror r0}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 191, Serial#: 191
    public void tstror(final ConditionCode cond, final GPR Rn, final GPR Rm, final GPR Rs) {
        int instruction = 0x01100070;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code tst{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code tsteq         r0, r0, rrx }
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.54"
     */
    // Template#: 192, Serial#: 192
    public void tstrrx(final ConditionCode cond, final GPR Rn, final GPR Rm) {
        int instruction = 0x01100060;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mla{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>, <i>Rn</i>
     * Example disassembly syntax: {@code mlaeq         r0, r0, r0, r0}
     * <p>
     * Constraint: {@code Rd.value() != Rm.value()}<br />
     * Constraint: {@code Rd.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.28"
     */
    // Template#: 193, Serial#: 193
    public void mla(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs, final GPR Rn) {
        int instruction = 0x00200090;
        checkConstraint(Rd.value() != Rm.value(), "Rd.value() != Rm.value()");
        checkConstraint(Rd.value() != 15, "Rd.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rs.value() != 15, "Rs.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
        instruction |= ((Rn.value() & 0xf) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mul{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>Rd</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code muleq         r0, r0, r0}
     * <p>
     * Constraint: {@code Rd.value() != Rm.value()}<br />
     * Constraint: {@code Rd.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.33"
     */
    // Template#: 194, Serial#: 194
    public void mul(final ConditionCode cond, final SBit s, final GPR Rd, final GPR Rm, final GPR Rs) {
        int instruction = 0x00000090;
        checkConstraint(Rd.value() != Rm.value(), "Rd.value() != Rm.value()");
        checkConstraint(Rd.value() != 15, "Rd.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rs.value() != 15, "Rs.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((s.value() & 0x1) << 20);
        instruction |= ((Rd.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rs.value() & 0xf) << 8);
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
    // Template#: 195, Serial#: 195
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
     * Pseudo-external assembler syntax: {@code smull{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>RdLo</i>, <i>RdHi</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code smulleq       r0, r0, r0, r0}
     * <p>
     * Constraint: {@code RdLo.value() != RdHi.value()}<br />
     * Constraint: {@code RdLo.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != 15}<br />
     * Constraint: {@code RdLo.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.40"
     */
    // Template#: 196, Serial#: 196
    public void smull(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs) {
        int instruction = 0x00C00090;
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
     * Pseudo-external assembler syntax: {@code umlal{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}{s}  }<i>RdLo</i>, <i>RdHi</i>, <i>Rm</i>, <i>Rs</i>
     * Example disassembly syntax: {@code umlaleq       r0, r0, r0, r0}
     * <p>
     * Constraint: {@code RdLo.value() != RdHi.value()}<br />
     * Constraint: {@code RdLo.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != Rm.value()}<br />
     * Constraint: {@code RdHi.value() != 15}<br />
     * Constraint: {@code RdLo.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rs.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.55"
     */
    // Template#: 197, Serial#: 197
    public void umlal(final ConditionCode cond, final SBit s, final GPR RdLo, final GPR RdHi, final GPR Rm, final GPR Rs) {
        int instruction = 0x00A00090;
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
    // Template#: 198, Serial#: 198
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
     * Pseudo-external assembler syntax: {@code clz{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rm</i>
     * Example disassembly syntax: {@code clzeq         r0, r0}
     * <p>
     * Constraint: {@code Rd.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.12"
     */
    // Template#: 199, Serial#: 199
    public void clz(final ConditionCode cond, final GPR Rd, final GPR Rm) {
        int instruction = 0x016F0F10;
        checkConstraint(Rd.value() != 15, "Rd.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mrs{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>
     * Example disassembly syntax: {@code mrseq         r0, cpsr}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.31"
     */
    // Template#: 200, Serial#: 200
    public void mrscpsr(final ConditionCode cond, final GPR Rd) {
        int instruction = 0x010F0000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code mrs{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>
     * Example disassembly syntax: {@code mrseq         r0, spsr}
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.31"
     */
    // Template#: 201, Serial#: 201
    public void mrsspsr(final ConditionCode cond, final GPR Rd) {
        int instruction = 0x014F0000;
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, #+0x0]}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 202, Serial#: 202
    public void ldradd(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05900000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, #-0x0]}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 203, Serial#: 203
    public void ldrsub(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05100000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
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
    // Template#: 204, Serial#: 204
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 205, Serial#: 205
    public void ldrsub(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07100000;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, lsl #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 206, Serial#: 206
    public void ldraddlsl(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07900000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, lsl #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 207, Serial#: 207
    public void ldrsublsl(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07100000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, lsr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 208, Serial#: 208
    public void ldraddlsr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07900020;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, lsr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 209, Serial#: 209
    public void ldrsublsr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07100020;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, asr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 210, Serial#: 210
    public void ldraddasr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07900040;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, asr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 211, Serial#: 211
    public void ldrsubasr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07100040;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, ror #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 212, Serial#: 212
    public void ldraddror(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07900060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, ror #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 213, Serial#: 213
    public void ldrsubror(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07100060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, rrx]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 214, Serial#: 214
    public void ldraddrrx(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07900060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, rrx]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 215, Serial#: 215
    public void ldrsubrrx(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07100060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, #+0x0]!}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 216, Serial#: 216
    public void ldraddw(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05B00000;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, #-0x0]!}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 217, Serial#: 217
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 218, Serial#: 218
    public void ldraddw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07B00000;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 219, Serial#: 219
    public void ldrsubw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07300000;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, lsl #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 220, Serial#: 220
    public void ldraddlslw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07B00000;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, lsl #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 221, Serial#: 221
    public void ldrsublslw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07300000;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, lsr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 222, Serial#: 222
    public void ldraddlsrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07B00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, lsr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 223, Serial#: 223
    public void ldrsublsrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07300020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, asr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 224, Serial#: 224
    public void ldraddasrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07B00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, asr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 225, Serial#: 225
    public void ldrsubasrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07300040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, ror #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 226, Serial#: 226
    public void ldraddrorw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07B00060;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, ror #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 227, Serial#: 227
    public void ldrsubrorw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07300060;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, +r0, rrx]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 228, Serial#: 228
    public void ldraddrrxw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07B00060;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0, -r0, rrx]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 229, Serial#: 229
    public void ldrsubrrxw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07300060;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], #+0x0}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 230, Serial#: 230
    public void ldraddpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x04900000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], #-0x0}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 231, Serial#: 231
    public void ldrsubpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x04100000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0}
     * <p>
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 232, Serial#: 232
    public void ldraddpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06900000;
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0}
     * <p>
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 233, Serial#: 233
    public void ldrsubpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06100000;
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 234, Serial#: 234
    public void ldraddlslpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06900000;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 235, Serial#: 235
    public void ldrsublslpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06100000;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 236, Serial#: 236
    public void ldraddlsrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06900020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 237, Serial#: 237
    public void ldrsublsrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06100020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 238, Serial#: 238
    public void ldraddasrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06900040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 239, Serial#: 239
    public void ldrsubasrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06100040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
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
    // Template#: 240, Serial#: 240
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 241, Serial#: 241
    public void ldrsubrorpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06100060;
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
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], +r0, rrx}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 242, Serial#: 242
    public void ldraddrrxpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06900060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code ldr{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code ldreq         r0, [r0], -r0, rrx}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.20"
     */
    // Template#: 243, Serial#: 243
    public void ldrsubrrxpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06100060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0, #+0x0]}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 244, Serial#: 244
    public void stradd(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05800000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0, #-0x0]}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 245, Serial#: 245
    public void strsub(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05000000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 246, Serial#: 246
    public void stradd(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07800000;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 247, Serial#: 247
    public void strsub(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07000000;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, lsl #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 248, Serial#: 248
    public void straddlsl(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07800000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, lsl #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 249, Serial#: 249
    public void strsublsl(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07000000;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, lsr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 250, Serial#: 250
    public void straddlsr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07800020;
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
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, lsr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 251, Serial#: 251
    public void strsublsr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07000020;
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
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, asr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 252, Serial#: 252
    public void straddasr(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07800040;
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
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, asr #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 253, Serial#: 253
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
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, ror #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 254, Serial#: 254
    public void straddror(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07800060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, ror #0x0]}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 255, Serial#: 255
    public void strsubror(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07000060;
        checkConstraint(0 <= shift_imm && shift_imm <= 31, "0 <= shift_imm && shift_imm <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, rrx]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 256, Serial#: 256
    public void straddrrx(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07800060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, rrx]}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 257, Serial#: 257
    public void strsubrrx(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07000060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0, #+0x0]!}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 258, Serial#: 258
    public void straddw(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05A00000;
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0, #-0x0]!}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 259, Serial#: 259
    public void strsubw(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x05200000;
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 260, Serial#: 260
    public void straddw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07A00000;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 261, Serial#: 261
    public void strsubw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07200000;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, lsl #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 262, Serial#: 262
    public void straddlslw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07A00000;
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, lsl #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 263, Serial#: 263
    public void strsublslw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07200000;
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, lsr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 264, Serial#: 264
    public void straddlsrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07A00020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
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
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, lsr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 265, Serial#: 265
    public void strsublsrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07200020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
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
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, asr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 266, Serial#: 266
    public void straddasrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07A00040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
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
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, asr #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 267, Serial#: 267
    public void strsubasrw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07200040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
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
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, ror #0x0]!}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 268, Serial#: 268
    public void straddrorw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x07A00060;
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
    // Template#: 269, Serial#: 269
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, +r0, rrx]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 270, Serial#: 270
    public void straddrrxw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07A00060;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0, -r0, rrx]!}
     * <p>
     * Constraint: {@code Rd.value() != Rn.value()}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 271, Serial#: 271
    public void strsubrrxw(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x07200060;
        checkConstraint(Rd.value() != Rn.value(), "Rd.value() != Rn.value()");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
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
    // Template#: 272, Serial#: 272
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>offset_12</i>
     * Example disassembly syntax: {@code streq         r0, [r0], #-0x0}
     * <p>
     * Constraint: {@code 0 <= offset_12 && offset_12 <= 4095}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 273, Serial#: 273
    public void strsubpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final int offset_12) {
        int instruction = 0x04000000;
        checkConstraint(0 <= offset_12 && offset_12 <= 4095, "0 <= offset_12 && offset_12 <= 4095");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (offset_12 & 0xfff);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], +r0}
     * <p>
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 274, Serial#: 274
    public void straddpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06800000;
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], -r0}
     * <p>
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 275, Serial#: 275
    public void strsubpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06000000;
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], +r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 276, Serial#: 276
    public void straddlslpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06800000;
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
     * Example disassembly syntax: {@code streq         r0, [r0], -r0, lsl #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 277, Serial#: 277
    public void strsublslpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06000000;
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
     * Example disassembly syntax: {@code streq         r0, [r0], +r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 278, Serial#: 278
    public void straddlsrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06800020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], -r0, lsr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 279, Serial#: 279
    public void strsublsrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06000020;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], +r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 280, Serial#: 280
    public void straddasrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06800040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], -r0, asr #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm % 32 && shift_imm % 32 <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 281, Serial#: 281
    public void strsubasrpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06000040;
        checkConstraint(0 <= shift_imm % 32 && shift_imm % 32 <= 31, "0 <= shift_imm % 32 && shift_imm % 32 <= 31");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((shift_imm % 32 & 0x1f) << 7);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>, <i>shift_imm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], +r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 282, Serial#: 282
    public void straddrorpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06800060;
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
     * Example disassembly syntax: {@code streq         r0, [r0], -r0, ror #0x0}
     * <p>
     * Constraint: {@code 0 <= shift_imm && shift_imm <= 31}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 283, Serial#: 283
    public void strsubrorpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm, final int shift_imm) {
        int instruction = 0x06000060;
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
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], +r0, rrx}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 284, Serial#: 284
    public void straddrrxpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06800060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code str{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rn</i>, <i>Rm</i>
     * Example disassembly syntax: {@code streq         r0, [r0], -r0, rrx}
     * <p>
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rm.value() != Rn.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.44"
     */
    // Template#: 285, Serial#: 285
    public void strsubrrxpost(final ConditionCode cond, final GPR Rd, final GPR Rn, final GPR Rm) {
        int instruction = 0x06000060;
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rm.value() != Rn.value(), "Rm.value() != Rn.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= ((Rn.value() & 0xf) << 16);
        instruction |= (Rm.value() & 0xf);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code swp{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rm</i>, <i>Rn</i>
     * Example disassembly syntax: {@code swpeq         r0, r0, [r0]}
     * <p>
     * Constraint: {@code Rd.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rn.value() != Rd.value()}<br />
     * Constraint: {@code Rn.value() != Rm.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.51"
     */
    // Template#: 286, Serial#: 286
    public void swp(final ConditionCode cond, final GPR Rd, final GPR Rm, final GPR Rn) {
        int instruction = 0x01000090;
        checkConstraint(Rd.value() != 15, "Rd.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rn.value() != Rd.value(), "Rn.value() != Rd.value()");
        checkConstraint(Rn.value() != Rm.value(), "Rn.value() != Rm.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rn.value() & 0xf) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code swpb{eq|ne|cs|hs|cc|lo|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al|nv}  }<i>Rd</i>, <i>Rm</i>, <i>Rn</i>
     * Example disassembly syntax: {@code swpbeq        r0, r0, [r0]}
     * <p>
     * Constraint: {@code Rd.value() != 15}<br />
     * Constraint: {@code Rm.value() != 15}<br />
     * Constraint: {@code Rn.value() != 15}<br />
     * Constraint: {@code Rn.value() != Rd.value()}<br />
     * Constraint: {@code Rn.value() != Rm.value()}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.52"
     */
    // Template#: 287, Serial#: 287
    public void swpb(final ConditionCode cond, final GPR Rd, final GPR Rm, final GPR Rn) {
        int instruction = 0x01400090;
        checkConstraint(Rd.value() != 15, "Rd.value() != 15");
        checkConstraint(Rm.value() != 15, "Rm.value() != 15");
        checkConstraint(Rn.value() != 15, "Rn.value() != 15");
        checkConstraint(Rn.value() != Rd.value(), "Rn.value() != Rd.value()");
        checkConstraint(Rn.value() != Rm.value(), "Rn.value() != Rm.value()");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= ((Rd.value() & 0xf) << 12);
        instruction |= (Rm.value() & 0xf);
        instruction |= ((Rn.value() & 0xf) << 16);
        emitInt(instruction);
    }

    /**
     * Pseudo-external assembler syntax: {@code bkpt  }<i>immediate</i>
     * Example disassembly syntax: {@code bkpt          0x0}
     * <p>
     * Constraint: {@code 0 <= ((immediate >> 4) & 4095) && ((immediate >> 4) & 4095) <= 4095}<br />
     * Constraint: {@code 0 <= (immediate & 15) && (immediate & 15) <= 15}<br />
     *
     * @see "ARM Architecture Reference Manual, Second Edition - Section 4.1.7"
     */
    // Template#: 288, Serial#: 288
    public void bkpt(final int immediate) {
        int instruction = 0xE1200070;
        checkConstraint(0 <= ((immediate >> 4) & 4095) && ((immediate >> 4) & 4095) <= 4095, "0 <= ((immediate >> 4) & 4095) && ((immediate >> 4) & 4095) <= 4095");
        checkConstraint(0 <= (immediate & 15) && (immediate & 15) <= 15, "0 <= (immediate & 15) && (immediate & 15) <= 15");
        instruction |= ((((immediate >> 4) & 4095) & 0xfff) << 8);
        instruction |= ((immediate & 15) & 0xf);
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
    // Template#: 289, Serial#: 289
    public void swi(final ConditionCode cond, final int immed_24) {
        int instruction = 0x0F000000;
        checkConstraint(0 <= immed_24 && immed_24 <= 16777215, "0 <= immed_24 && immed_24 <= 16777215");
        instruction |= ((cond.value() & 0xf) << 28);
        instruction |= (immed_24 & 0xffffff);
        emitInt(instruction);
    }

// END GENERATED RAW ASSEMBLER METHODS
}
