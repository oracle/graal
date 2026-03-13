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

import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotSafepointOp;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotSafepointOp;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
         * List of virtual moves to be inserted after this instruction,
         * virtual moves are ones removed by the allocator still holding
         * relevant information to the verification process, for example
         * first label always uses registers (instead of variables) based on ABI,
         * and a move is inserted indicating that those registers have certain
         * variables.
         *
         * @example [rsi, rbp] = LABEL
         * v1 = MOVE rsi
         */
        protected List<ValueMove> virtualMoveList;

        /**
         * List of speculative moves, these might be removed, but still
         * hold important information for us, so we add them to the verifier
         * IR in-case they are, this happens when a MOVE source and target
         * locations are equal (and thus redundant) but before allocation
         * their variable counter-parts are not equal.
         *
         * @example before alloc: v1 = MOVE v2
         * after alloc: rax = MOVE rax
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
         * Verify that all presumed values are present and that both sides have it.
         *
         * @return true, if contents have been successfully verified, false if there's null in either array.
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
                if (this.curr[i] != null) {
                    result.append(this.orig[i].toString()).append(" -> ").append(this.curr[i].toString());
                } else {
                    result.append(this.orig[i].toString());
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
     * RAV instruction that handles a regular operation
     * in an abstract way - we do not care about the function of said operation.
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
         * Pairs of inputs used by this operation
         * that need to be alive after it completes.
         */
        public ValueArrayPair alive;

        /**
         * Pairs of values retrieved from LIRFrameState,
         * verified same as any other input to make
         * sure GC has all necessary information.
         */
        public ValueArrayPair stateValues;

        /**
         * Bytecode frame information for this instruction.
         */
        public ArrayList<StateValuePair> bcFrames;

        /**
         * List of GC roots, calculated using LocationMarker class,
         * other references in state maps need to be nullified.
         */
        public List<RAValue> references;

        /**
         * Count number of values stored.
         */
        private final class GetCountProcedure implements InstructionValueProcedure {
            private int count = 0;

            public int getCount() {
                int count = this.count;
                // Reset the count and go again for different argument type
                this.count = 0;
                return count;
            }

            @Override
            public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                count++;
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

        public boolean isCall() {
           return lirInstruction instanceof AMD64Call.CallOp || lirInstruction instanceof AArch64Call.CallOp;
        }

        public boolean isSafePoint() {
            return references != null && (lirInstruction instanceof AMD64HotSpotSafepointOp || lirInstruction instanceof AArch64HotSpotSafepointOp || isCall());
        }

        /**
         * Check if stateValues have null values, if so
         * the state is not complete. This happens because
         * iterating over certain values in LIRFrameState is
         * ignored because they are a concrete stack slot and
         * not a virtual one (StackLockValue).
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
            return this.dests.toString() + " = Op " + this.uses.toString() + " " + this.alive.toString() + " " + this.temp.toString();
        }
    }

    /**
     * A move between to concrete locations
     * inserted by the register allocator.
     */
    public static class LocationMove extends Base {
        public RAValue from;
        public RAValue to;

        public LocationMove(LIRInstruction instr, Value from, Value to) {
            super(instr);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        public String toString() {
            return to.toString() + " = MOVE " + from.toString();
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

        public String toString() {
            return to.toString() + " = REGMOVE " + from.toString();
        }
    }

    /**
     * Move between two stack slots (virtual or not).
     */
    public static class StackMove extends LocationMove {
        public RAValue from;
        public RAValue to;

        public StackMove(LIRInstruction instr, Value from, Value to) {
            super(instr, from, to);

            assert from instanceof StackSlot || from instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";
            assert to instanceof StackSlot || to instanceof VirtualStackSlot : "StackMove needs to receive instanceof StackSlot or VirtualStackSlot";

            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        public String toString() {
            return to.toString() + " = STACKMOVE " + from.toString();
        }
    }

    /**
     * Reload symbol from stack slot to a register.
     */
    public static class Reload extends LocationMove {
        public RAValue from;
        public RAValue to;

        public Reload(LIRInstruction instr, RegisterValue to, Value from) {
            super(instr, from, to);
            this.from = RAValue.create(from);
            this.to = RAValue.create(to);
        }

        public String toString() {
            return to.toString() + " = RELOAD " + from.toString();
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

        public String toString() {
            return to.toString() + " = SPILL " + from.toString();
        }
    }

    /**
     * Move a value or variable to a concrete location,
     * inserted by allocator (materialization) or not (virtual move).
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

        public String toString() {
            return getLocation().toString() + " = VIRTMOVE " + variableOrConstant.toString();
        }

        public void setLocation(RAValue location) {
            this.location = location;
        }

        /**
         * Where variable (or constant) is being stored.
         */
        public RAValue getLocation() {
            if (LIRValueUtil.isVirtualStackSlot(location.getValue())) {
                Value moveLocation;
                if (StandardOp.LoadConstantOp.isLoadConstantOp(lirInstruction)) {
                    var loadConstOp = StandardOp.LoadConstantOp.asLoadConstantOp(lirInstruction);
                    moveLocation = loadConstOp.getResult();
                } else {
                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(lirInstruction);
                    moveLocation = valueMov.getInput();
                }

                if (ValueUtil.isStackSlot(moveLocation)) {
                    // Change vstack to allocated stack slot, because it was changed
                    location.value = moveLocation;
                }
            }

            return location;
        }
    }
}
