/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.jvmci.code.ValueUtil.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.jvmci.code.*;
import com.oracle.jvmci.debug.*;
import com.oracle.jvmci.meta.*;
import com.oracle.jvmci.options.*;

/**
 * Mark all live references for a frame state. The frame state use this information to build the OOP
 * maps.
 */
public final class LocationMarker extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use decoupled pass for location marking (instead of using LSRA marking)", type = OptionType.Debug)
        public static final OptionValue<Boolean> UseLocationMarker = new OptionValue<>(true);
        // @formatter:on
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        new Marker<B>(lirGenRes.getLIR(), lirGenRes.getFrameMap()).build();
    }

    /**
     * Ensures that an element is only in the worklist once.
     *
     * @param <T>
     */
    static class UniqueWorkList<T extends AbstractBlockBase<T>> extends ArrayDeque<T> {
        private static final long serialVersionUID = 8009554570990975712L;
        BitSet valid;

        public UniqueWorkList(int size) {
            this.valid = new BitSet(size);
        }

        @Override
        public T poll() {
            T result = super.poll();
            if (result != null) {
                valid.set(result.getId(), false);
            }
            return result;
        }

        @Override
        public boolean add(T pred) {
            if (!valid.get(pred.getId())) {
                valid.set(pred.getId(), true);
                return super.add(pred);
            }
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends T> collection) {
            boolean changed = false;
            for (T element : collection) {
                if (!valid.get(element.getId())) {
                    valid.set(element.getId(), true);
                    super.add(element);
                    changed = true;
                }
            }
            return changed;
        }
    }

    private static final class ValueSet {
        private Value[] values;

        ValueSet() {
            values = Value.NO_VALUES;
        }

        ValueSet(ValueSet other) {
            int limit = other.values.length;
            while (limit > 0) {
                if (other.values[limit - 1] == null) {
                    limit--;
                    continue;
                }
                break;
            }
            values = new Value[limit];
            System.arraycopy(other.values, 0, values, 0, values.length);
        }

        void put(int index, Value value) {
            if (value != null && value.getLIRKind().isValue()) {
                return;
            }
            if (values.length <= index) {
                if (value == null) {
                    return;
                }
                Value[] newValues = new Value[index + 1];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
                values[index] = value;
            } else {
                values[index] = value;
            }
        }

        public void putAll(ValueSet stack) {
            Value[] otherValues = stack.values;
            int limit = otherValues.length;
            if (limit > values.length) {
                while (limit > 0) {
                    if (otherValues[limit - 1] == null) {
                        limit--;
                        continue;
                    }
                    break;
                }
                if (limit > values.length) {
                    Value[] newValues = new Value[limit];
                    System.arraycopy(values, 0, newValues, 0, values.length);
                    values = newValues;
                }
            }
            for (int i = 0; i < limit; i++) {
                Value value = otherValues[i];
                if (value != null) {
                    values[i] = value;
                }
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ValueSet) {
                ValueSet that = (ValueSet) other;
                int limit = Math.min(values.length, that.values.length);
                for (int i = 0; i < limit; i++) {
                    if (!Objects.equals(values[i], that.values[i])) {
                        return false;
                    }
                }
                for (int i = limit; i < values.length; i++) {
                    if (values[i] != null) {
                        return false;
                    }
                }
                for (int i = limit; i < that.values.length; i++) {
                    if (that.values[i] != null) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        public void addLiveValues(ReferenceMap refMap) {
            for (Value v : values) {
                if (v != null) {
                    refMap.addLiveValue(v);
                }
            }
        }

        @Override
        public int hashCode() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class Marker<T extends AbstractBlockBase<T>> {

        private final class LiveValueSet implements Consumer<AbstractBlockBase<T>> {

            public void accept(AbstractBlockBase<T> succ) {
                putAll(liveInMap.get(succ));
            }

            private final ValueSet registers;
            private final ValueSet stack;
            private Set<Value> extraStack;

            public LiveValueSet() {
                registers = new ValueSet();
                stack = new ValueSet();
            }

            public LiveValueSet(LiveValueSet s) {
                registers = new ValueSet(s.registers);
                stack = new ValueSet(s.stack);
                if (s.extraStack != null) {
                    extraStack = new HashSet<>(s.extraStack);
                }
            }

            public void put(Value v) {
                if (isRegister(v)) {
                    int index = asRegister(v).getReferenceMapIndex();
                    registers.put(index, v);
                } else if (isStackSlot(v)) {
                    int index = frameMap.offsetForStackSlot(asStackSlot(v));
                    assert index >= 0;
                    if (index % 4 == 0) {
                        stack.put(index / 4, v);
                    } else {
                        if (extraStack == null) {
                            extraStack = new HashSet<>();
                        }
                        extraStack.add(v);
                    }
                }
            }

            public void putAll(LiveValueSet v) {
                registers.putAll(v.registers);
                stack.putAll(v.stack);
                if (v.extraStack != null) {
                    if (extraStack == null) {
                        extraStack = new HashSet<>();
                    }
                    extraStack.addAll(v.extraStack);
                }
            }

            public void remove(Value v) {
                if (isRegister(v)) {
                    int index = asRegister(v).getReferenceMapIndex();
                    registers.put(index, null);
                } else if (isStackSlot(v)) {
                    int index = frameMap.offsetForStackSlot(asStackSlot(v));
                    assert index >= 0;
                    if (index % 4 == 0) {
                        stack.put(index / 4, null);
                    } else if (extraStack != null) {
                        extraStack.remove(v);
                    }
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Marker.LiveValueSet) {
                    LiveValueSet other = (LiveValueSet) obj;
                    return registers.equals(other.registers) && stack.equals(other.stack) && Objects.equals(extraStack, other.extraStack);
                } else {
                    return false;
                }
            }

            @Override
            public int hashCode() {
                throw new UnsupportedOperationException();
            }

            public void addLiveValues(ReferenceMap refMap) {
                registers.addLiveValues(refMap);
                stack.addLiveValues(refMap);
                if (extraStack != null) {
                    for (Value v : extraStack) {
                        refMap.addLiveValue(v);
                    }
                }
            }
        }

        private final LIR lir;
        private final FrameMap frameMap;
        private final RegisterAttributes[] registerAttributes;
        private final BlockMap<LiveValueSet> liveInMap;
        private final BlockMap<LiveValueSet> liveOutMap;

        private Marker(LIR lir, FrameMap frameMap) {
            this.lir = lir;
            this.frameMap = frameMap;
            this.registerAttributes = frameMap.getRegisterConfig().getAttributesMap();
            liveInMap = new BlockMap<>(lir.getControlFlowGraph());
            liveOutMap = new BlockMap<>(lir.getControlFlowGraph());
        }

        @SuppressWarnings("unchecked")
        void build() {
            UniqueWorkList<T> worklist = new UniqueWorkList<>(lir.getControlFlowGraph().getBlocks().size());
            for (int i = lir.getControlFlowGraph().getBlocks().size() - 1; i >= 0; i--) {
                worklist.add((T) lir.getControlFlowGraph().getBlocks().get(i));
            }
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                liveInMap.put(block, new LiveValueSet());
            }
            while (!worklist.isEmpty()) {
                AbstractBlockBase<T> block = worklist.poll();
                processBlock(block, worklist);
            }
        }

        /**
         * Merge outSet with in-set of successors.
         */
        private boolean updateOutBlock(AbstractBlockBase<T> block) {
            LiveValueSet union = new LiveValueSet();
            block.getSuccessors().forEach(union);
            LiveValueSet outSet = liveOutMap.get(block);
            // check if changed
            if (outSet == null || !union.equals(outSet)) {
                liveOutMap.put(block, union);
                return true;
            }
            return false;
        }

        private void processBlock(AbstractBlockBase<T> block, UniqueWorkList<T> worklist) {
            if (updateOutBlock(block)) {
                try (Indent indent = Debug.logAndIndent("handle block %s", block)) {
                    currentSet = new LiveValueSet(liveOutMap.get(block));
                    List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                    for (int i = instructions.size() - 1; i >= 0; i--) {
                        LIRInstruction inst = instructions.get(i);
                        processInstructionBottomUp(inst);
                    }
                    liveInMap.put(block, currentSet);
                    currentSet = null;
                    worklist.addAll(block.getPredecessors());
                }
            }
        }

        private static final EnumSet<OperandFlag> REGISTER_FLAG_SET = EnumSet.of(OperandFlag.REG);
        private static final LIRKind REFERENCE_KIND = LIRKind.reference(Kind.Object);

        private LiveValueSet currentSet;

        /**
         * Process all values of an instruction bottom-up, i.e. definitions before usages. Values
         * that start or end at the current operation are not included.
         */
        private void processInstructionBottomUp(LIRInstruction op) {
            try (Indent indent = Debug.logAndIndent("handle op %d, %s", op.id(), op)) {
                // kills

                op.visitEachTemp(defConsumer);
                op.visitEachOutput(defConsumer);
                if (op.destroysCallerSavedRegisters()) {
                    for (Register reg : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                        defConsumer.visitValue(reg.asValue(REFERENCE_KIND), OperandMode.TEMP, REGISTER_FLAG_SET);
                    }
                }

                // gen - values that are considered alive for this state
                op.visitEachAlive(useConsumer);
                op.visitEachState(useConsumer);
                // mark locations
                op.forEachState(stateConsumer);
                // gen
                op.visitEachInput(useConsumer);
            }
        }

        InstructionStateProcedure stateConsumer = new InstructionStateProcedure() {
            public void doState(LIRInstruction inst, LIRFrameState info) {
                markLocation(inst, info, currentSet);
            }
        };

        ValueConsumer useConsumer = new ValueConsumer() {
            public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (shouldProcessValue(operand)) {
                    // no need to insert values and derived reference
                    if (Debug.isLogEnabled()) {
                        Debug.log("set operand: %s", operand);
                    }
                    currentSet.put(operand);
                }
            }
        };

        ValueConsumer defConsumer = new ValueConsumer() {
            public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (shouldProcessValue(operand)) {
                    if (Debug.isLogEnabled()) {
                        Debug.log("clear operand: %s", operand);
                    }
                    currentSet.remove(operand);
                } else {
                    assert isIllegal(operand) || operand.getPlatformKind() != Kind.Illegal || mode == OperandMode.TEMP : String.format("Illegal PlatformKind is only allowed for TEMP mode: %s, %s",
                                    operand, mode);
                }
            }
        };

        protected boolean shouldProcessValue(Value operand) {
            return (isRegister(operand) && attributes(asRegister(operand)).isAllocatable() || isStackSlot(operand)) && operand.getPlatformKind() != Kind.Illegal;
        }

        /**
         * This method does the actual marking.
         */
        private void markLocation(LIRInstruction op, LIRFrameState info, LiveValueSet values) {
            if (!info.hasDebugInfo()) {
                info.initDebugInfo(frameMap, !op.destroysCallerSavedRegisters() || !frameMap.getRegisterConfig().areAllAllocatableRegistersCallerSaved());
            }

            ReferenceMap refMap = info.debugInfo().getReferenceMap();
            refMap.reset();
            frameMap.addLiveValues(refMap);
            values.addLiveValues(refMap);
            refMap.finish();
        }

        /**
         * Gets an object describing the attributes of a given register according to this register
         * configuration.
         *
         * @see LinearScan#attributes
         */
        private RegisterAttributes attributes(Register reg) {
            return registerAttributes[reg.number];
        }

    }
}
