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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.cfg.*;

/**
 * A collection of machine-independent LIR operations, as well as interfaces to be implemented for
 * specific kinds or LIR operations.
 */
public class StandardOp {

    /**
     * Marker interface for LIR ops that can fall through to the next operation, like a switch
     * statement. setFallThroughTarget(null) can be used to make the operation fall through to the
     * next one.
     */
    public interface FallThroughOp {

        LabelRef fallThroughTarget();

        void setFallThroughTarget(LabelRef target);
    }

    public interface NullCheck {
        Value getCheckedValue();

        LIRFrameState getState();
    }

    public interface ImplicitNullCheck {
        boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit);
    }

    /**
     * LIR operation that defines the position of a label. The first operation of every block must
     * implement this interface.
     */
    public static class LabelOp extends LIRInstruction {

        private static final Value[] NO_VALUES = new Value[0];

        /**
         * In the LIR, every register and variable must be defined before it is used. For method
         * parameters that are passed in fixed registers, exception objects passed to the exception
         * handler in a fixed register, or any other use of a fixed register not defined in this
         * method, an artificial definition is necessary. To avoid spill moves to be inserted
         * between the label at the beginning of a block an an actual definition in the second
         * instruction of a block, the registers are defined here in the label.
         */
        @Def({REG, STACK}) private Value[] incomingValues;

        private final Label label;
        private final boolean align;

        public LabelOp(Label label, boolean align) {
            this.label = label;
            this.align = align;
            this.incomingValues = NO_VALUES;
        }

        public void setIncomingValues(Value[] values) {
            assert incomingValues.length == 0;
            incomingValues = values;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (align) {
                crb.asm.align(crb.target.wordSize * 2);
            }
            crb.asm.bind(label);
        }

        public Label getLabel() {
            return label;
        }
    }

    /**
     * LIR operation that is an unconditional jump to {@link #destination()}. When the LIR is
     * constructed, the last operation of every block must implement this interface. After register
     * allocation, unnecessary jumps can be deleted.
     */
    public static class JumpOp extends LIRInstruction {

        private final LabelRef destination;

        public JumpOp(LabelRef destination) {
            this.destination = destination;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            crb.asm.jmp(destination.label());
        }

        public LabelRef destination() {
            return destination;
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
     * Marker interface for a LIR operation that moves a value from {@link #getInput()} to
     * {@link #getResult()}.
     */
    public interface MoveOp {

        Value getInput();

        AllocatableValue getResult();
    }

    /**
     * An operation that saves registers to the stack. The set of saved registers can be
     * {@linkplain #remove(Set) pruned} and a mapping from registers to the frame slots in which
     * they are saved can be {@linkplain #getMap(FrameMap) retrieved}.
     */
    public interface SaveRegistersOp {

        /**
         * Determines if the {@link #remove(Set)} operation is supported for this object.
         */
        boolean supportsRemove();

        /**
         * Prunes {@code doNotSave} from the registers saved by this operation.
         * 
         * @param doNotSave registers that should not be saved by this operation
         * @return the number of registers pruned
         * @throws UnsupportedOperationException if removal is not {@linkplain #supportsRemove()
         *             supported}
         */
        int remove(Set<Register> doNotSave);

        /**
         * Gets a map from the saved registers saved by this operation to the frame slots in which
         * they are saved.
         * 
         * @param frameMap used to {@linkplain FrameMap#indexForStackSlot(StackSlot) convert} a
         *            virtual slot to a frame slot index
         */
        RegisterSaveLayout getMap(FrameMap frameMap);

    }

    /**
     * Placeholder for a LIR instruction that will be subsequently replaced.
     */
    public static class PlaceholderOp extends LIRInstruction {

        /**
         * The block in which this instruction is located.
         */
        final Block block;

        /**
         * The block index of this instruction.
         */
        final int index;

        public PlaceholderOp(Block block, int index) {
            this.block = block;
            this.index = index;
        }

        public void replace(LIR lir, LIRInstruction replacement) {
            lir.lir(block).set(index, replacement);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            throw new GraalInternalError(this + " should have been replaced");
        }
    }
}
