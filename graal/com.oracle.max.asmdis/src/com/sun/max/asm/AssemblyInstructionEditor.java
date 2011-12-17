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
package com.sun.max.asm;

import com.sun.max.lang.*;

/**
 * Interface for target code edition. Currently, used by the JIT only, for template-based code generation.
 * The interface is currently customized to the need of the JIT.
 */
public interface AssemblyInstructionEditor {

    /**
     * Returns the displacement in the edited instruction.
     * @param displacementWidth the width of the displacement in the instruction
     * @return  the displacement in the edited instruction
     */
    int getIntDisplacement(WordWidth displacementWidth) throws AssemblyException;

    /**
     * Returns the immediate int value in the edited instruction.
     * @param displacementWidth the width of the displacement in the instruction
     * @return  the displacement in the edited instruction
     */
    int getIntImmediate(WordWidth immediateWidth) throws AssemblyException;

    /**
     * Replaces the value of the immediate displacement in a load/store instruction.
     * The original instruction must have an immediate displacement operand that can hold 8-bit immediate value.
     * @param displacementWidth width of the displacement in the original instruction
     * @param withIndex  indicate if the instruction uses a register index.
     * @param disp8 new value of the displacement.
     */
    void fixDisplacement(WordWidth displacementWidth, boolean withIndex, byte disp8);

    /**
     * Replaces the value of the immediate displacement in a load/store instruction.
     * The original instruction must have an immediate displacement operand that can hold 32-bit immediate value.
     * @param disp32
     * @throws AssemblyException
     *      if the replacement is not allowed (e.g., the instruction is
     *      not a load/store with immediate displacement parameter).
     */
    void fixDisplacement(WordWidth displacementWidth, boolean withIndex, int disp32) throws AssemblyException;

    /**
     * Replaces the value of the immediate source operand of an  instruction.
     * (tested with store instruction only).
     * @param operandWidth the width of the operand being replaced
     * @param imm8 the new immediate value of the operand.
     */
    void fixImmediateOperand(WordWidth operandWidth, byte imm8);

    /**
     * Replaces the value of the immediate source operand of an  instruction.
     * (tested with store instruction only).
     * @param operandWidth the width of the operand being replaced
     * @param imm16 the new immediate value of the operand.
     */
    void fixImmediateOperand(WordWidth operandWidth, short imm16);

    /**
     * Replaces the value of the immediate source operand of an  instruction.
     * (tested with store instruction only).
     * @param operandWidth the width of the operand being replaced
     * @param imm32 the new immediate value of the operand.
     */
    void fixImmediateOperand(WordWidth operandWidth, int imm32);

    /**
     * Replaces the value of the immediate source operand of an  instruction.
     * @param imm64 the new immediate value of the operand.
     */
    void fixImmediateOperand(int imm32);

    /**
     * Replaces the value of the immediate source operand of an  instruction.
     * @param imm64 the new immediate value of the operand.
     */
    void fixImmediateOperand(long imm64);

    /**
     * Fix relative displacement of a branch instruction.
     * 
     * @param originalDisplacementWidth
     * @param displacementWidth width of the relative displacement in the original instruction
     * @param disp32 new relative displacement
     */
    void fixBranchRelativeDisplacement(WordWidth displacementWidth, int disp32) throws AssemblyException;
}
