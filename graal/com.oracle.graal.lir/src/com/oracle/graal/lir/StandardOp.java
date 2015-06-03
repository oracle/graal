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
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.framemap.*;
import com.oracle.jvmci.asm.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.common.*;
import com.oracle.jvmci.meta.*;

/**
 * A collection of machine-independent LIR operations, as well as interfaces to be implemented for
 * specific kinds or LIR operations.
 */
public class StandardOp {

    /**
     * A block delimiter. Every well formed block must contain exactly one such operation and it
     * must be the last operation in the block.
     */
    public interface BlockEndOp {
    }

    public interface NullCheck {
        Value getCheckedValue();

        LIRFrameState getState();
    }

    public interface ImplicitNullCheck {
        boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit);
    }

    /**
     * LIR operation that defines the position of a label.
     */
    public static final class LabelOp extends LIRInstruction {
        public static final LIRInstructionClass<LabelOp> TYPE = LIRInstructionClass.create(LabelOp.class);

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
            super(TYPE);
            this.label = label;
            this.align = align;
            this.incomingValues = NO_VALUES;
        }

        public void setIncomingValues(Value[] values) {
            assert incomingValues.length == 0;
            assert values != null;
            incomingValues = values;
        }

        public int getIncomingSize() {
            return incomingValues.length;
        }

        public Value getIncomingValue(int idx) {
            return incomingValues[idx];
        }

        public void clearIncomingValues() {
            incomingValues = NO_VALUES;
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

        /**
         * @return true if this label acts as a PhiIn.
         */
        public boolean isPhiIn() {
            return getIncomingSize() > 0 && isVariable(getIncomingValue(0));
        }
    }

    /**
     * LIR operation that is an unconditional jump to a {@link #destination()}.
     */
    public static class JumpOp extends LIRInstruction implements BlockEndOp {
        public static final LIRInstructionClass<JumpOp> TYPE = LIRInstructionClass.create(JumpOp.class);

        private static final Value[] NO_VALUES = new Value[0];

        @Alive({REG, STACK, CONST}) private Value[] outgoingValues;

        private final LabelRef destination;

        public JumpOp(LabelRef destination) {
            this(TYPE, destination);
        }

        protected JumpOp(LIRInstructionClass<? extends JumpOp> c, LabelRef destination) {
            super(c);
            this.destination = destination;
            this.outgoingValues = NO_VALUES;
        }

        public void setOutgoingValues(Value[] values) {
            assert outgoingValues.length == 0;
            assert values != null;
            outgoingValues = values;
        }

        public int getOutgoingSize() {
            return outgoingValues.length;
        }

        public Value getOutgoingValue(int idx) {
            return outgoingValues[idx];
        }

        public void clearOutgoingValues() {
            outgoingValues = NO_VALUES;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (!crb.isSuccessorEdge(destination)) {
                crb.asm.jmp(destination.label());
            }
        }

        public LabelRef destination() {
            return destination;
        }
    }

    /**
     * Marker interface for a LIR operation that is a conditional jump.
     */
    public interface BranchOp extends BlockEndOp {
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
         * @param frameMap used to {@linkplain FrameMap#offsetForStackSlot(StackSlot) convert} a
         *            virtual slot to a frame slot index
         */
        RegisterSaveLayout getMap(FrameMap frameMap);

    }

    /**
     * An operation that takes one input and stores it in a stack slot as well as to an ordinary
     * variable.
     */
    public interface StackStoreOp {

        Value getInput();

        AllocatableValue getResult();

        StackSlotValue getStackSlot();
    }

    /**
     * A LIR operation that does nothing. If the operation records its position, it can be
     * subsequently {@linkplain #replace(LIR, LIRInstruction) replaced}.
     */
    public static final class NoOp extends LIRInstruction {
        public static final LIRInstructionClass<NoOp> TYPE = LIRInstructionClass.create(NoOp.class);

        /**
         * The block in which this instruction is located.
         */
        final AbstractBlockBase<?> block;

        /**
         * The block index of this instruction.
         */
        final int index;

        public NoOp(AbstractBlockBase<?> block, int index) {
            super(TYPE);
            this.block = block;
            this.index = index;
        }

        public void replace(LIR lir, LIRInstruction replacement) {
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.get(index).equals(this) : String.format("Replacing the wrong instruction: %s instead of %s", instructions.get(index), this);
            instructions.set(index, replacement);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            if (block != null) {
                throw new JVMCIError(this + " should have been replaced");
            }
        }
    }

    @Opcode("BLACKHOLE")
    public static final class BlackholeOp extends LIRInstruction {
        public static final LIRInstructionClass<BlackholeOp> TYPE = LIRInstructionClass.create(BlackholeOp.class);

        @Use({REG, STACK}) private Value value;

        public BlackholeOp(Value value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            // do nothing, just keep value alive until at least here
        }
    }

    public static final class StackMove extends LIRInstruction implements MoveOp {
        public static final LIRInstructionClass<StackMove> TYPE = LIRInstructionClass.create(StackMove.class);

        @Def({STACK, HINT}) protected AllocatableValue result;
        @Use({STACK}) protected Value input;

        public StackMove(AllocatableValue result, Value input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            throw new JVMCIError(this + " should have been removed");
        }

        @Override
        public Value getInput() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }
    }

}
