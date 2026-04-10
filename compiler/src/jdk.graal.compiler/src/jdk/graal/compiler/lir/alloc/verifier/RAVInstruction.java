/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RAVInstruction {
    /**
     * Base class for all RAV instructions.
     */
    public abstract static class Base {
        /**
         * Underlying LIR instruction that this is representing.
         */
        protected LIRInstruction lirInstruction;

        /**
         * List of virtual moves to be inserted after this instruction, virtual moves are ones
         * removed by the allocator still holding relevant information to the verification process,
         * for example, the first label always uses registers (instead of variables) based on ABI,
         * and a move is inserted indicating that those registers have certain variables.
         *
         * <pre>
         * {@code [rsi, rbp] = LABEL v1 = MOVE rsi}
         * </pre>
         */
        protected List<ValueMove> virtualMoveList;

        /**
         * List of speculative moves, these might be removed but still hold important information
         * for us, so we add them to the verifier IR in case they are, this happens when a MOVE
         * source and target locations are equal (and thus redundant) but before allocation their
         * variable counter-parts are not equal.
         *
         * <p>
         * Before alloc: {@code v1 = MOVE v2} after alloc: {@code rax = MOVE rax}
         * </p>
         */
        protected List<ValueMove> speculativeMoveList;

        public Base(LIRInstruction lirInstruction) {
            this.lirInstruction = lirInstruction;
            this.virtualMoveList = new ArrayList<>();
            this.speculativeMoveList = new ArrayList<>();
        }

        public LIRInstruction getLIRInstruction() {
            return lirInstruction;
        }

        public void addVirtualMove(ValueMove virtualMove) {
            this.virtualMoveList.add(virtualMove);
        }

        public List<ValueMove> getVirtualMoveList() {
            return virtualMoveList;
        }

        public void addSpeculativeMove(ValueMove speculativeMove) {
            this.speculativeMoveList.add(speculativeMove);
        }

        public List<ValueMove> getSpeculativeMoveList() {
            return speculativeMoveList;
        }
    }

    /**
     * Helper class to handle pairs of locations and variables.
     */
    public static class ValueArrayPair {
        /**
         * Array of size count holding original variables before allocation.
         */
        public RAValue[] orig;
        /**
         * Array of size count holding current locations used after allocation.
         */
        public RAValue[] curr;

        public ArrayList<EnumSet<LIRInstruction.OperandFlag>> operandFlags;

        /**
         * Number of pairs of values stored here.
         */
        public int count;

        public ValueArrayPair(int count) {
            this.count = count;
            this.curr = new RAValue[count];
            this.orig = new RAValue[count];
            this.operandFlags = new ArrayList<>(count);
        }

        public InstructionValueProcedure copyOriginalProc = new InstructionValueProcedure() {
            private int index = 0;

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                ValueArrayPair.this.addOrig(index, value);
                ValueArrayPair.this.operandFlags.add(flags);
                index++;
                return value;
            }
        };

        public InstructionValueProcedure copyCurrentProc = new InstructionValueProcedure() {
            private int index = 0;

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                ValueArrayPair.this.addCurrent(index, value);

                if (index < operandFlags.size() && !operandFlags.get(index).equals(flags)) {
                    throw new IllegalStateException();
                }

                index++;
                return value;
            }
        };

        public RAValue getCurrent(int index) {
            assert index < this.count : "Index out of bounds";
            return this.curr[index];
        }

        public RAValue getOrig(int index) {
            assert index < this.count : "Index out of bounds";
            return this.orig[index];
        }

        public void addCurrent(int index, Value value) {
            if (index >= this.count) {
                // In test case DerivedOopTest, liveBasePointers has extra
                // values after allocation in frame state, so we skip them
                return;
            }

            this.curr[index] = RAValue.create(value);
        }

        public void addOrig(int index, Value value) {
            assert index < this.orig.length : "Index out of bounds";
            this.orig[index] = RAValue.create(value);
        }

        /**
         * Verify that all presumed values are present and that both sides have them.
         *
         * @return true, if contents have been successfully verified, false if there's null in
         *         either array.
         */
        public boolean verifyContents() {
            for (int i = 0; i < this.curr.length; i++) {
                if (this.curr[i] == null || this.orig[i] == null) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < this.count; i++) {
                var origVar = this.orig[i];
                var currLoc = this.curr[i];

                if (currLoc != null) {
                    result.append(origVar.getValue().toString()).append(" -> ").append(currLoc.getValue().toString());
                } else {
                    result.append(origVar.getValue().toString());
                }

                result.append(", ");
            }

            if (this.count > 0) {
                result.setLength(result.length() - 2);
            }

            result.append("]");
            return result.toString();
        }
    }

    public static class StateValuePair {
        public JavaKind[] kinds;
        public JavaValue[] curr;
        public JavaValue[] orig;

        StateValuePair(JavaValue[] orig, JavaKind[] kinds) {
            this.orig = orig;
            this.kinds = kinds;
        }

        public void setCurr(JavaValue[] curr) {
            this.curr = curr;
        }
    }

    /**
     * RAV instruction that handles a regular operation in an abstract way - we do not care about
     * the function of said operation.
     */
    public static class Op extends Base {
        /**
         * Pairs of outputs generated by this operation.
         */
        public ValueArrayPair dests;
        /**
         * Pairs of inputs used by this operation.
         */
        public ValueArrayPair uses;
        /**
         * Pairs of temporaries used by this operation.
         */
        public ValueArrayPair temp;
        /**
         * Pairs of inputs used by this operation that need to be alive after it completes.
         */
        public ValueArrayPair alive;

        /**
         * Pairs of values retrieved from LIRFrameState, verified the same as any other input to
         * make sure GC has all necessary information.
         */
        public ValueArrayPair stateValues;

        /**
         * Bytecode frame information for this instruction.
         */
        public ArrayList<StateValuePair> bcFrames;

        /**
         * List of GC roots, calculated using LocationMarker class, other references in state maps
         * need to be nullified.
         */
        public Set<RAValue> references;

        /**
         * Count the number of values stored.
         */
        private final class GetCountProcedure implements InstructionValueProcedure {
            private int valueCount = 0;

            public int getCount() {
                int count = this.valueCount;
                // Reset the count and go again for different argument type
                this.valueCount = 0;
                return count;
            }

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                valueCount++;
                return value;
            }
        }

        public Op(LIRInstruction instruction) {
            super(instruction);

            // We first count the number of entries, then allocate
            // an array of said size for both current locations and original variables.
            var countValuesProc = new GetCountProcedure();

            instruction.forEachInput(countValuesProc);
            this.uses = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachOutput(countValuesProc);
            this.dests = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachTemp(countValuesProc);
            this.temp = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachAlive(countValuesProc);
            this.alive = new ValueArrayPair(countValuesProc.getCount());

            instruction.forEachState(countValuesProc);
            this.stateValues = new ValueArrayPair(countValuesProc.getCount());

            this.bcFrames = new ArrayList<>();
        }

        public boolean hasMissingDefinitions() {
            for (int i = 0; i < this.dests.count; i++) {
                if (this.dests.curr[i] == null) {
                    return true;
                }
            }
            return false;
        }

        public boolean isLabel() {
            return lirInstruction instanceof StandardOp.LabelOp;
        }

        public boolean isJump() {
            return lirInstruction instanceof StandardOp.JumpOp;
        }

        /**
         * Check if stateValues have null values, if so the state is not complete. This happens
         * because iterating over certain values in LIRFrameState is ignored because they are a
         * concrete stack slot and not a virtual one (StackLockValue).
         *
         * @return true, if complete - non-null values after allocation
         */
        public boolean hasCompleteState() {
            for (int i = 0; i < stateValues.count; i++) {
                if (stateValues.curr[i] == null) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            var opString = lirInstruction.name();
            if (opString.isEmpty()) {
                opString = "OP";
            }

            return this.dests.toString() + " = " + opString.toUpperCase(Locale.ROOT) + " " + this.uses.toString() + " " + this.alive.toString() + " " + this.temp.toString();
        }
    }

    /**
     * A move between to concrete locations inserted by the register allocator.
     */
    public static class LocationMove extends Base {
        public RAValue from;
        public RAValue to;
        public boolean validateRegisters;

        public LocationMove(LIRInstruction instr, Value from, Value to) {
            this(instr, from, to, true);
        }

        /**
         * Create a location move instance.
         *
         * @param instr Underlying LIR instruction
         * @param from Source value
         * @param to Destination value
         * @param validateRegisters If checking, if register can be allocated to, should be done.
         *            This is false for moves that are not inserted or changed by the allocator.
         */
        public LocationMove(LIRInstruction instr, Value from, Value to, boolean validateRegisters) {
            super(instr);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
            this.validateRegisters = validateRegisters;
        }

        @Override
        public String toString() {
            return to.getValue().toString() + " = MOVE " + from.getValue().toString();
        }
    }

    /**
     * Move between two registers.
     */
    public static class RegMove extends LocationMove {
        public RAValue from;
        public RAValue to;

        public RegMove(LIRInstruction instr, RegisterValue from, RegisterValue to) {
            super(instr, from, to);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        @Override
        public String toString() {
            return to.getValue().toString() + " = REGMOVE " + from.getValue().toString();
        }
    }

    /**
     * Move between two stack slots (virtual or not).
     */
    public static class StackMove extends LocationMove {
        public RAValue from;
        public RAValue to;

        public RAValue backupSlot;

        public StackMove(LIRInstruction instr, Value from, Value to) {
            this(instr, from, to, null);
        }

        public StackMove(LIRInstruction instr, Value from, Value to, Value backupSlot) {
            super(instr, from, to);

            assert from instanceof StackSlot || from instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";
            assert to instanceof StackSlot || to instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";

            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
            this.backupSlot = RAValue.create(backupSlot);
        }

        @Override
        public String toString() {
            return to.getValue().toString() + " = STACKMOVE " + from.getValue().toString();
        }
    }

    /**
     * Reload a symbol from a stack slot to a register.
     */
    public static class Reload extends LocationMove {
        public RAValue from;
        public RAValue to;

        public Reload(LIRInstruction instr, RegisterValue to, Value from) {
            super(instr, from, to);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        @Override
        public String toString() {
            return to.getValue().toString() + " = RELOAD " + from.getValue().toString();
        }
    }

    /**
     * Spill symbol from register to a spill slot.
     */
    public static class Spill extends LocationMove {
        public RAValue from;
        public RAValue to;

        public Spill(LIRInstruction instr, Value to, RegisterValue from) {
            super(instr, from, to);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        @Override
        public String toString() {
            return to.getValue().toString() + " = SPILL " + from.getValue().toString();
        }
    }

    /**
     * Move a value or variable to a concrete location, inserted by allocator (materialization) or
     * not (virtual move).
     */
    public static class ValueMove extends Base {
        /**
         * Constant generated by materialization or variable from virtual/speculative move.
         */
        public RAValue variableOrConstant;

        protected RAValue location; // Can also be another variable!

        public ValueMove(LIRInstruction instr, Value variableOrConstant, Value location) {
            super(instr);
            this.variableOrConstant = RAValue.create(variableOrConstant);
            this.location = RAValue.create(location);
        }

        @Override
        public String toString() {
            return getLocation().getValue().toString() + " = VALUEMOVE " + variableOrConstant.getValue().toString();
        }

        public void setLocation(RAValue location) {
            this.location = location;
        }

        /**
         * Where variable (or constant) is being stored.
         */
        public RAValue getLocation() {
            return location;
        }
    }

    /**
     * Virtual move in from: v28|DWORD = MOVE input: rax|BYTE moveKind: DWORD, where the destination
     * is a variable. We flip the relation so that the input register actually stores the
     * symbol/variable, which keeps necessary verification information present.
     */
    public static class VirtualLocationMove extends ValueMove {
        public VirtualLocationMove(LIRInstruction instr, Value variableOrConstant, Value location) {
            super(instr, variableOrConstant, location);
        }

        @Override
        public String toString() {
            return getLocation().getValue().toString() + " = VIRTMOVE " + variableOrConstant.getValue().toString();
        }
    }
}
