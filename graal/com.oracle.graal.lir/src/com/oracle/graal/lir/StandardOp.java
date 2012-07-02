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
package com.oracle.graal.lir;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.max.asm.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

/**
 * A collection of machine-independent LIR operations, as well as interfaces to be implemented for specific kinds or LIR
 * operations.
 */
public class StandardOp {

    private static Value[] EMPTY = new Value[0];

    /**
     * Marker interface for LIR ops that can fall through to the next operation, like a switch statement.
     * setFallThroughTarget(null) can be used to make the operation fall through to the next one.
     */
    public interface FallThroughOp {
        LabelRef fallThroughTarget();
        void setFallThroughTarget(LabelRef target);
    }

    /**
     * LIR operation that defines the position of a label.
     * The first operation of every block must implement this interface.
     */
    public static class LabelOp extends LIRInstruction {
        private final Label label;
        private final boolean align;

        public LabelOp(Label label, boolean align) {
            this.label = label;
            this.align = align;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            if (align) {
                tasm.asm.align(tasm.target.wordSize);
            }
            tasm.asm.bind(label);
        }

        public Label getLabel() {
            return label;
        }
    }

    public static class PhiLabelOp extends LabelOp {
        @Def({REG, STACK}) protected Value[] phiDefinitions;

        public PhiLabelOp(Label label, boolean align, Value[] phiDefinitions) {
            super(label, align);
            this.phiDefinitions = phiDefinitions;
        }

        public void markResolved() {
            phiDefinitions = EMPTY;
        }

        public Value[] getPhiDefinitions() {
            return phiDefinitions;
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
        @State protected LIRFrameState state;

        public JumpOp(LabelRef destination, LIRFrameState state) {
            this.destination = destination;
            this.state = state;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            tasm.asm.jmp(destination.label());
        }

        public LabelRef destination() {
            return destination;
        }
    }

    public static class PhiJumpOp extends JumpOp {
        @Alive({REG, STACK, CONST}) protected Value[] phiInputs;

        public PhiJumpOp(LabelRef destination, Value[] phiInputs) {
            super(destination, null);
            this.phiInputs = phiInputs;
        }

        public void markResolved() {
            phiInputs = EMPTY;
        }

        public Value[] getPhiInputs() {
            return phiInputs;
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
        Value getInput();
        Value getResult();
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
        @Def({REG, STACK}) protected Value[] params;

        public ParametersOp(Value[] params) {
            this.params = params;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm) {
            // No code to emit.
        }
    }
}
