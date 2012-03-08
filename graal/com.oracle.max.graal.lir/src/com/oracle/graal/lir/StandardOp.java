/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.lir;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.lir.asm.*;

/**
 * A collection of machine-independent LIR operations, as well as interfaces to be implemented for specific kinds or LIR
 * operations.
 */
public class StandardOp {

    private static CiValue[] EMPTY = new CiValue[0];

    /**
     * LIR operation that defines the position of a label.
     * The first operation of every block must implement this interface.
     */
    public static class LabelOp extends LIRInstruction {
        private final Label label;
        private final boolean align;

        protected LabelOp(Object opcode, CiValue[] outputs, LIRDebugInfo info, CiValue[] inputs, CiValue[] alives, CiValue[] temps, Label label, boolean align) {
            super(opcode, outputs, info, inputs, alives, temps);
            this.label = label;
            this.align = align;
        }

        public LabelOp(Label label, boolean align) {
            this("LABEL", LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, label, align);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            if (align) {
                tasm.asm.align(tasm.target.wordSize);
            }
            tasm.asm.bind(label);
        }

        @Override
        public String operationString() {
            return label.toString() + " " + super.operationString();
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            throw GraalInternalError.shouldNotReachHere();
        }

        public Label getLabel() {
            return label;
        }
    }

    public static class PhiLabelOp extends LabelOp {
        public PhiLabelOp(Label label, boolean align, CiValue[] phiDefinitions) {
            super("PHI_LABEL", phiDefinitions, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, label, align);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Output) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            }
            throw GraalInternalError.shouldNotReachHere();
        }

        public void markResolved() {
            outputs = EMPTY;
        }

        public CiValue[] getPhiDefinitions() {
            return outputs;
        }
    }

    /**
     * LIR operation that is an unconditional jump to {@link #destination()}.
     * When the LIR is constructed, the last operation of every block must implement this interface. After
     * register allocation, unnecessary jumps can be deleted.
     *
     * TODO (cwimmer) Currently, a block can also end with an XIR operation.
     */
    public static class JumpOp extends LIRInstruction {
        private final LabelRef destination;

        protected JumpOp(Object opcode, CiValue[] outputs, LIRDebugInfo info, CiValue[] inputs, CiValue[] alives, CiValue[] temps, LabelRef destination) {
            super(opcode, outputs, info, inputs, alives, temps);
            this.destination = destination;
        }

        public JumpOp(LabelRef destination, LIRDebugInfo info) {
            this("JUMP", LIRInstruction.NO_OPERANDS, info, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, destination);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            tasm.asm.jmp(destination.label());
        }

        @Override
        public String operationString() {
            return  destination + " " + super.operationString();
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            throw GraalInternalError.shouldNotReachHere();
        }

        public LabelRef destination() {
            return destination;
        }
    }

    public static class PhiJumpOp extends JumpOp {
        public PhiJumpOp(LabelRef destination, CiValue[] phiInputs) {
            super("PHI_JUMP", LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, phiInputs, LIRInstruction.NO_OPERANDS, destination);
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Alive) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack, OperandFlag.Constant);
            }
            throw GraalInternalError.shouldNotReachHere();
        }

        public void markResolved() {
            alives = EMPTY;
        }

        public CiValue[] getPhiInputs() {
            return alives;
        }
    }

    /**
     * Marker interface for a LIR operation that is a conditional jump to {@link #destination()}.
     * Conditional jumps may be negated or optimized away after register allocation.
     */
    public interface BranchOp {
        LabelRef destination();
        void negate(LabelRef newDestination);
    }

    /**
     * Marker interface for a LIR operation that moves a value from {@link #getInput()} to {@link #getResult()}.
     */
    public interface MoveOp {
        CiValue getInput();
        CiValue getResult();
    }

    /**
     * Marker interface for a LIR operation that calls a method, i.e., destroys all caller-saved registers.
     */
    public interface CallOp {
    }


    /**
     * Meta-operation that defines the incoming method parameters. In the LIR, every register and variable must be
     * defined before it is used. This operation is the definition point of method parameters, but is otherwise a no-op.
     * In particular, it is not the actual method prologue.
     */
    public static final class ParametersOp extends LIRInstruction {
        public ParametersOp(CiValue[] params) {
            super("PARAMS", params, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS);
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            // No code to emit.
        }

        @Override
        protected EnumSet<OperandFlag> flagsFor(OperandMode mode, int index) {
            if (mode == OperandMode.Output) {
                return EnumSet.of(OperandFlag.Register, OperandFlag.Stack);
            }
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
