/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;

/**
 * The {@code LIRInstruction} class definition.
 */
public abstract class LIRInstruction {

    public static final Value[] NO_OPERANDS = {};

    /**
     * Iterator for iterating over a list of values. Subclasses must overwrite one of the doValue methods.
     * Clients of the class must only call the doValue method that takes additional parameters.
     */
    public abstract static class ValueProcedure {
        /**
         * Iterator method to be overwritten. This version of the iterator does not take additional parameters
         * to keep the signature short.
         *
         * @param value The value that is iterated.
         * @return The new value to replace the value that was passed in.
         */
        protected Value doValue(Value value) {
            throw GraalInternalError.shouldNotReachHere("One of the doValue() methods must be overwritten");
        }

        /**
         * Iterator method to be overwritten. This version of the iterator gets additional parameters about the
         * processed value.
         *
         * @param value The value that is iterated.
         * @param mode The operand mode for the value.
         * @param flags A set of flags for the value.
         * @return The new value to replace the value that was passed in.
         */
        public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            return doValue(value);
        }
    }


    /**
     * Constants denoting how a LIR instruction uses an operand.
     */
    public enum OperandMode {
        /**
         * The value must have been defined before. It is alive before the instruction until the beginning of the
         * instruction, but not necessarily throughout the instruction. A register assigned to it can also be assigend
         * to a Temp or Output operand. The value can be used again after the instruction, so the instruction must not
         * modify the register.
         */
        Input,

        /**
         * The value must have been defined before. It is alive before the instruction and throughout the instruction. A
         * register assigned to it cannot be assigned to a Temp or Output operand. The value can be used again after the
         * instruction, so the instruction must not modify the register.
         */
        Alive,

        /**
         * The value must not have been defined before, and must not be used after the instruction. The instruction can
         * do whatever it wants with the register assigned to it (or not use it at all).
         */
        Temp,

        /**
         * The value must not have been defined before. The instruction has to assign a value to the register. The
         * value can (and most likely will) be used after the instruction.
         */
        Output,
    }

    /**
     * Flags for an operand.
     */
    public enum OperandFlag {
        /**
         * The value can be a {@link RegisterValue}.
         */
        Register,

        /**
         * The value can be a {@link StackSlot}.
         */
        Stack,

        /**
         * The value can be a {@link Address}.
         */
        Address,

        /**
         * The value can be a {@link Constant}.
         */
        Constant,

        /**
         * The value can be {@link Value#IllegalValue}.
         */
        Illegal,

        /**
         * The register allocator should try to assign a certain register to improve code quality.
         * Use {@link LIRInstruction#forEachRegisterHint} to access the register hints.
         */
        RegisterHint,

        /**
         * The value can be uninitialized, e.g., a stack slot that has not written to before. This is only
         * used to avoid false positives in verification code.
         */
        Uninitialized,
    }

    /**
     * For validity checking of the operand flags defined by instruction subclasses.
     */
    private static final EnumMap<OperandMode, EnumSet<OperandFlag>> ALLOWED_FLAGS;

    static {
        ALLOWED_FLAGS = new EnumMap<>(OperandMode.class);
        ALLOWED_FLAGS.put(OperandMode.Input,  EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Address, OperandFlag.Constant, OperandFlag.Illegal, OperandFlag.RegisterHint, OperandFlag.Uninitialized));
        ALLOWED_FLAGS.put(OperandMode.Alive,  EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Address, OperandFlag.Constant, OperandFlag.Illegal, OperandFlag.RegisterHint, OperandFlag.Uninitialized));
        ALLOWED_FLAGS.put(OperandMode.Temp,   EnumSet.of(OperandFlag.Register, OperandFlag.Constant, OperandFlag.Illegal, OperandFlag.RegisterHint));
        ALLOWED_FLAGS.put(OperandMode.Output, EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Illegal, OperandFlag.RegisterHint));
    }

    /**
     * The opcode of this instruction.
     */
    protected final Object code;

    /**
     * The output operands for this instruction (modified by the register allocator).
     */
    protected Value[] outputs;

    /**
     * The input operands for this instruction (modified by the register allocator).
     */
    protected Value[] inputs;

    /**
     * The alive operands for this instruction (modified by the register allocator).
     */
    protected Value[] alives;

    /**
     * The temp operands for this instruction (modified by the register allocator).
     */
    protected Value[] temps;

    /**
     * Used to emit debug information.
     */
    public final LIRDebugInfo info;

    /**
     * Instruction id for register allocation.
     */
    private int id;

    /**
     * Constructs a new LIR instruction that has input and temp operands.
     *
     * @param opcode the opcode of the new instruction
     * @param outputs the operands that holds the operation results of this instruction.
     * @param info the {@link LIRDebugInfo} info that is to be preserved for the instruction. This will be {@code null} when no debug info is required for the instruction.
     * @param inputs the input operands for the instruction.
     * @param temps the temp operands for the instruction.
     */
    public LIRInstruction(Object opcode, Value[] outputs, LIRDebugInfo info, Value[] inputs, Value[] alives, Value[] temps) {
        this.code = opcode;
        this.outputs = outputs;
        this.inputs = inputs;
        this.alives = alives;
        this.temps = temps;
        this.info = info;
        this.id = -1;
    }

    public abstract void emitCode(TargetMethodAssembler tasm);


    public final int id() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

    /**
     * Gets an input operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th input operand.
     */
    protected final Value input(int index) {
        return inputs[index];
    }

    /**
     * Gets an alive operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th alive operand.
     */
    protected final Value alive(int index) {
        return alives[index];
    }

    /**
     * Gets a temp operand of this instruction.
     *
     * @param index the index of the operand requested.
     * @return the {@code index}'th temp operand.
     */
    protected final Value temp(int index) {
        return temps[index];
    }

    /**
     * Gets the result operand for this instruction.
     *
     * @return return the result operand
     */
    protected final Value output(int index) {
        return outputs[index];
    }

    /**
     * Gets the instruction name.
     */
    public String name() {
        return code.toString();
    }

    public boolean hasOperands() {
        return inputs.length > 0 || alives.length > 0 || temps.length > 0 || outputs.length > 0 || info != null || hasCall();
    }

    private static final EnumSet<OperandFlag> ADDRESS_FLAGS = EnumSet.of(OperandFlag.Register, OperandFlag.Illegal);

    private void forEach(Value[] values, OperandMode mode, ValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            assert ALLOWED_FLAGS.get(mode).containsAll(flagsFor(mode, i));

            Value value = values[i];
            if (isAddress(value)) {
                assert flagsFor(mode, i).contains(OperandFlag.Address);
                Address address = asAddress(value);
                address.setBase(proc.doValue(address.getBase(), mode, ADDRESS_FLAGS));
                address.setIndex(proc.doValue(address.getIndex(), mode, ADDRESS_FLAGS));
            } else {
                values[i] = proc.doValue(values[i], mode, flagsFor(mode, i));
            }
        }
    }

    public final void forEachInput(ValueProcedure proc) {
        forEach(inputs, OperandMode.Input, proc);
    }

    public final void forEachAlive(ValueProcedure proc) {
        forEach(alives, OperandMode.Alive, proc);
    }

    public final void forEachTemp(ValueProcedure proc) {
        forEach(temps, OperandMode.Temp, proc);
    }

    public final void forEachOutput(ValueProcedure proc) {
        forEach(outputs, OperandMode.Output, proc);
    }

    public final void forEachState(ValueProcedure proc) {
        if (info != null) {
            info.forEachState(proc);

            if (this instanceof LIRXirInstruction) {
                LIRXirInstruction xir = (LIRXirInstruction) this;
                if (xir.infoAfter != null) {
                    xir.infoAfter.forEachState(proc);
                }
            }
        }
    }

    /**
     * Returns true when this instruction is a call instruction that destroys all caller-saved registers.
     */
    public final boolean hasCall() {
        return this instanceof StandardOp.CallOp;
    }

    /**
     * Iterates all register hints for the specified value, i.e., all preferred candidates for the register to be
     * assigned to the value.
     * <br>
     * Subclasses can override this method. The default implementation processes all Input operands as the hints for
     * an Output operand, and all Output operands as the hints for an Input operand.
     *
     * @param value The value the hints are needed for.
     * @param mode The operand mode of the value.
     * @param proc The procedure invoked for all the hints. If the procedure returns a non-null value, the iteration is stopped
     *             and the value is returned by this method, i.e., clients can stop the iteration once a suitable hint has been found.
     * @return The non-null value returned by the procedure, or null.
     */
    public Value forEachRegisterHint(Value value, OperandMode mode, ValueProcedure proc) {
        Value[] hints;
        if (mode == OperandMode.Input) {
            hints = outputs;
        } else if (mode == OperandMode.Output) {
            hints = inputs;
        } else {
            return null;
        }

        for (int i = 0; i < hints.length; i++) {
            Value result = proc.doValue(hints[i], null, null);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Used by the register allocator to decide which kind of location can be assigned to the operand.
     * @param mode The kind of operand.
     * @param index The index of the operand.
     * @return The flags for the operand.
     */
    // TODO (cwimmer) this method will go away when we have named operands, the flags will be specified as annotations instead.
    protected abstract EnumSet<OperandFlag> flagsFor(OperandMode mode, int index);

    protected void verify() {
    }


    public final String toStringWithIdPrefix() {
        if (id != -1) {
            return String.format("%4d %s", id, toString());
        }
        return "     " + toString();
    }

    /**
     * Gets the operation performed by this instruction in terms of its operands as a string.
     */
    public String operationString() {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        if (outputs.length > 1) {
            buf.append("(");
        }
        for (Value output : outputs) {
            buf.append(sep).append(output);
            sep = ", ";
        }
        if (outputs.length > 1) {
            buf.append(")");
        }
        if (outputs.length > 0) {
            buf.append(" = ");
        }

        if (inputs.length + alives.length != 1) {
            buf.append("(");
        }
        sep = "";
        for (Value input : inputs) {
            buf.append(sep).append(input);
            sep = ", ";
        }
        for (Value input : alives) {
            buf.append(sep).append(input).append(" ~");
            sep = ", ";
        }
        if (inputs.length + alives.length != 1) {
            buf.append(")");
        }

        if (temps.length > 0) {
            buf.append(" [");
        }
        sep = "";
        for (Value temp : temps) {
            buf.append(sep).append(temp);
            sep = ", ";
        }
        if (temps.length > 0) {
            buf.append("]");
        }
        return buf.toString();
    }

    protected void appendDebugInfo(StringBuilder buf) {
        if (info != null) {
            buf.append(" [bci:");
            String sep = "";
            for (BytecodeFrame cur = info.topFrame; cur != null; cur = cur.caller()) {
                buf.append(sep).append(cur.getBCI());
                sep = ",";
            }
            buf.append("]");
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(name()).append(' ').append(operationString());
        appendDebugInfo(buf);
        return buf.toString();
    }
}
