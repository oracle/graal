/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.lir.LIRInstruction.OperandMode.*;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.asm.*;

/**
 * The base class for an {@code LIRInstruction}.
 */
public abstract class LIRInstructionBase implements LIRInstruction {

    /**
     * For validity checking of the operand flags defined by instruction subclasses.
     */
    protected static final EnumMap<OperandMode, EnumSet<OperandFlag>> ALLOWED_FLAGS;

    static {
        ALLOWED_FLAGS = new EnumMap<>(OperandMode.class);
        ALLOWED_FLAGS.put(USE, EnumSet.of(REG, STACK, COMPOSITE, CONST, ILLEGAL, HINT, UNINITIALIZED));
        ALLOWED_FLAGS.put(ALIVE, EnumSet.of(REG, STACK, COMPOSITE, CONST, ILLEGAL, HINT, UNINITIALIZED));
        ALLOWED_FLAGS.put(TEMP, EnumSet.of(REG, COMPOSITE, CONST, ILLEGAL, HINT));
        ALLOWED_FLAGS.put(DEF, EnumSet.of(REG, STACK, COMPOSITE, ILLEGAL, HINT));
    }

    /**
     * The flags of the base and index value of an address.
     */
    protected static final EnumSet<OperandFlag> ADDRESS_FLAGS = EnumSet.of(REG, ILLEGAL);

    private final LIRInstructionClass<?> instructionClass;

    /**
     * Instruction id for register allocation.
     */
    private int id;

    private static final DebugMetric LIR_NODE_COUNT = Debug.metric("LIRNodes");

    /**
     * Constructs a new LIR instruction.
     */
    public LIRInstructionBase() {
        LIR_NODE_COUNT.increment();
        instructionClass = LIRInstructionClass.get(getClass());
        id = -1;
    }

    public abstract void emitCode(CompilationResultBuilder crb);

    public final int id() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

    public final String name() {
        return instructionClass.getOpcode(this);
    }

    public final boolean hasOperands() {
        return instructionClass.hasOperands() || hasState() || destroysCallerSavedRegisters();
    }

    public final boolean hasState() {
        return instructionClass.hasState(this);
    }

    public boolean destroysCallerSavedRegisters() {
        return false;
    }

    // ValuePositionProcedures
    public final void forEachInputPos(ValuePositionProcedure proc) {
        instructionClass.forEachUsePos(this, proc);
    }

    public final void forEachAlivePos(ValuePositionProcedure proc) {
        instructionClass.forEachAlivePos(this, proc);
    }

    public final void forEachTempPos(ValuePositionProcedure proc) {
        instructionClass.forEachTempPos(this, proc);
    }

    public final void forEachOutputPos(ValuePositionProcedure proc) {
        instructionClass.forEachDefPos(this, proc);
    }

    // InstructionValueProcedures
    public final void forEachInput(InstructionValueProcedure proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void forEachAlive(InstructionValueProcedure proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void forEachTemp(InstructionValueProcedure proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void forEachOutput(InstructionValueProcedure proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void forEachState(InstructionValueProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // ValueProcedures
    public final void forEachInput(ValueProcedure proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void forEachAlive(ValueProcedure proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void forEachTemp(ValueProcedure proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void forEachOutput(ValueProcedure proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void forEachState(ValueProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // States
    public final void forEachState(InstructionStateProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    public final void forEachState(StateProcedure proc) {
        instructionClass.forEachState(this, proc);
    }

    // InstructionValueConsumers
    public final void visitEachInput(InstructionValueConsumer proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void visitEachAlive(InstructionValueConsumer proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void visitEachTemp(InstructionValueConsumer proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void visitEachOutput(InstructionValueConsumer proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void visitEachState(InstructionValueConsumer proc) {
        instructionClass.forEachState(this, proc);
    }

    // ValueConsumers
    public final void visitEachInput(ValueConsumer proc) {
        instructionClass.forEachUse(this, proc);
    }

    public final void visitEachAlive(ValueConsumer proc) {
        instructionClass.forEachAlive(this, proc);
    }

    public final void visitEachTemp(ValueConsumer proc) {
        instructionClass.forEachTemp(this, proc);
    }

    public final void visitEachOutput(ValueConsumer proc) {
        instructionClass.forEachDef(this, proc);
    }

    public final void visitEachState(ValueConsumer proc) {
        instructionClass.forEachState(this, proc);
    }

    public Value forEachRegisterHint(Value value, OperandMode mode, InstructionValueProcedure proc) {
        return instructionClass.forEachRegisterHint(this, mode, proc);
    }

    public Value forEachRegisterHint(Value value, OperandMode mode, ValueProcedure proc) {
        return instructionClass.forEachRegisterHint(this, mode, proc);
    }

    public void verify() {
    }

    public final String toStringWithIdPrefix() {
        if (id != -1) {
            return String.format("%4d %s", id, toString());
        }
        return "     " + toString();
    }

    @Override
    public String toString() {
        return instructionClass.toString(this);
    }

    public LIRInstructionClass<?> getLIRInstructionClass() {
        return instructionClass;
    }
}
