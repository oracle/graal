/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.framemap;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.options.*;

/**
 * Linear Scan {@link StackSlotAllocator}.
 */
public class LSStackSlotAllocator implements StackSlotAllocator {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable linear scan stack slot allocation.")
        public static final OptionValue<Boolean> EnableLSStackSlotAllocation = new OptionValue<>(true);
        // @formatter:on
    }

    public void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res) {
        new Allocator(res.getLIR(), builder).allocate();
    }

    static final class Allocator extends InstructionNumberer {
        private final LIR lir;
        private final FrameMapBuilderTool frameMapBuilder;
        private final StackInterval[] stackSlotMap;
        private LinkedList<StackInterval> unhandled;
        private LinkedList<StackInterval> active;

        private List<? extends AbstractBlock<?>> sortedBlocks;

        private Allocator(LIR lir, FrameMapBuilderTool frameMapBuilder) {
            this.lir = lir;
            this.frameMapBuilder = frameMapBuilder;
            this.stackSlotMap = new StackInterval[frameMapBuilder.getNumberOfStackSlots()];
        }

        private void allocate() {
            // create block ordering
            List<? extends AbstractBlock<?>> blocks = lir.getControlFlowGraph().getBlocks();
            assert blocks.size() > 0;

            sortedBlocks = lir.getControlFlowGraph().getBlocks();
            numberInstructions(lir, sortedBlocks);
            Debug.dump(lir, "After StackSlot numbering");

            long currentFrameSize = Debug.isMeterEnabled() ? frameMapBuilder.getFrameMap().currentFrameSize() : 0;
            // build intervals
            // buildIntervals();
            try (Scope s = Debug.scope("StackSlotAllocationBuildIntervals"); Indent indent = Debug.logAndIndent("BuildIntervals")) {
                buildIntervalsSlow();
            }
            if (Debug.isEnabled()) {
                verifyIntervals();
            }
            if (Debug.isDumpEnabled()) {
                dumpIntervals("Before stack slot allocation");
            }
            // allocate stack slots
            allocateStackSlots();
            if (Debug.isDumpEnabled()) {
                dumpIntervals("After stack slot allocation");
            }

            // assign stack slots
            assignStackSlots();
            Debug.dump(lir, "After StackSlot assignment");
            StackSlotAllocator.allocatedFramesize.add(frameMapBuilder.getFrameMap().currentFrameSize() - currentFrameSize);
        }

        private void buildIntervalsSlow() {
            new SlowIntervalBuilder().build();
        }

        private class SlowIntervalBuilder {
            final BlockMap<BitSet> liveInMap;
            final BlockMap<BitSet> liveOutMap;

            private SlowIntervalBuilder() {
                liveInMap = new BlockMap<>(lir.getControlFlowGraph());
                liveOutMap = new BlockMap<>(lir.getControlFlowGraph());
            }

            private void build() {
                Deque<AbstractBlock<?>> worklist = new ArrayDeque<>();
                for (int i = lir.getControlFlowGraph().getBlocks().size() - 1; i >= 0; i--) {
                    worklist.add(lir.getControlFlowGraph().getBlocks().get(i));
                }
                for (AbstractBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                    liveInMap.put(block, new BitSet(frameMapBuilder.getNumberOfStackSlots()));
                }
                while (!worklist.isEmpty()) {
                    AbstractBlock<?> block = worklist.poll();
                    processBlock(block, worklist);
                }
            }

            /**
             * Merge outSet with in-set of successors.
             */
            private boolean updateOutBlock(AbstractBlock<?> block) {
                BitSet union = new BitSet(frameMapBuilder.getNumberOfStackSlots());
                block.getSuccessors().forEach(succ -> union.or(liveInMap.get(succ)));
                BitSet outSet = liveOutMap.get(block);
                // check if changed
                if (outSet == null || !union.equals(outSet)) {
                    liveOutMap.put(block, union);
                    return true;
                }
                return false;
            }

            private void processBlock(AbstractBlock<?> block, Deque<AbstractBlock<?>> worklist) {
                if (updateOutBlock(block)) {
                    try (Indent indent = Debug.logAndIndent("handle block %s", block)) {
                        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                        // get out set and mark intervals
                        BitSet outSet = liveOutMap.get(block);
                        markOutInterval(outSet, getBlockEnd(instructions));
                        printLiveSet("liveOut", outSet);

                        // process instructions
                        BlockClosure closure = new BlockClosure((BitSet) outSet.clone());
                        for (int i = instructions.size() - 1; i >= 0; i--) {
                            LIRInstruction inst = instructions.get(i);
                            closure.processInstructionBottomUp(inst);
                        }

                        // add predecessors to work list
                        worklist.addAll(block.getPredecessors());
                        // set in set and mark intervals
                        BitSet inSet = closure.getCurrentSet();
                        liveInMap.put(block, inSet);
                        markInInterval(inSet, getBlockBegin(instructions));
                        printLiveSet("liveIn", inSet);
                    }
                }
            }

            private void printLiveSet(String label, BitSet liveSet) {
                if (Debug.isLogEnabled()) {
                    try (Indent indent = Debug.logAndIndent(label)) {
                        Debug.log("%s", liveSetToString(liveSet));
                    }
                }
            }

            private String liveSetToString(BitSet liveSet) {
                StringBuilder sb = new StringBuilder();
                for (int i = liveSet.nextSetBit(0); i >= 0; i = liveSet.nextSetBit(i + 1)) {
                    StackInterval interval = getIntervalFromStackId(i);
                    sb.append(interval.getOperand()).append(" ");
                }
                return sb.toString();
            }

            protected void markOutInterval(BitSet outSet, int blockEndOpId) {
                for (int i = outSet.nextSetBit(0); i >= 0; i = outSet.nextSetBit(i + 1)) {
                    StackInterval interval = getIntervalFromStackId(i);
                    Debug.log("mark live operand: %s", interval.getOperand());
                    interval.addTo(blockEndOpId);
                }
            }

            protected void markInInterval(BitSet inSet, int blockFirstOpId) {
                for (int i = inSet.nextSetBit(0); i >= 0; i = inSet.nextSetBit(i + 1)) {
                    StackInterval interval = getIntervalFromStackId(i);
                    Debug.log("mark live operand: %s", interval.getOperand());
                    interval.addFrom(blockFirstOpId);
                }
            }

            private final class BlockClosure {
                private final BitSet currentSet;

                private BlockClosure(BitSet set) {
                    currentSet = set;
                }

                private BitSet getCurrentSet() {
                    return currentSet;
                }

                /**
                 * Process all values of an instruction bottom-up, i.e. definitions before usages.
                 * Values that start or end at the current operation are not included.
                 */
                private void processInstructionBottomUp(LIRInstruction op) {
                    try (Indent indent = Debug.logAndIndent("handle op %d, %s", op.id(), op)) {
                        // kills
                        op.visitEachTemp(this::defConsumer);
                        op.visitEachOutput(this::defConsumer);
                        // forEachDestroyedCallerSavedRegister(op, this::defConsumer);

                        // gen - values that are considered alive for this state
                        op.visitEachAlive(this::useConsumer);
                        op.visitEachState(this::useConsumer);
                        // mark locations
                        // gen
                        op.visitEachInput(this::useConsumer);
                    }
                }

                /**
                 * @see InstructionValueConsumer
                 *
                 * @param inst
                 * @param operand
                 * @param mode
                 * @param flags
                 */
                private void useConsumer(LIRInstruction inst, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVirtualStackSlot(operand)) {
                        VirtualStackSlot vslot = asVirtualStackSlot(operand);
                        addUse(vslot, inst);
                        Debug.log("set operand: %s", operand);
                        currentSet.set(vslot.getId());
                    }
                }

                /**
                 *
                 * @see InstructionValueConsumer
                 *
                 * @param inst
                 * @param operand
                 * @param mode
                 * @param flags
                 */
                private void defConsumer(LIRInstruction inst, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                    if (isVirtualStackSlot(operand)) {
                        VirtualStackSlot vslot = asVirtualStackSlot(operand);
                        addDef(vslot, inst);
                        Debug.log("clear operand: %s", operand);
                        currentSet.clear(vslot.getId());
                    }

                }

                private void addUse(VirtualStackSlot stackSlot, LIRInstruction inst) {
                    StackInterval interval = getOrCreateInterval(stackSlot);
                    interval.addUse(inst.id());
                }

                private void addDef(VirtualStackSlot stackSlot, LIRInstruction inst) {
                    StackInterval interval = getOrCreateInterval(stackSlot);
                    interval.addDef(inst.id());
                }

            }
        }

        private static int getBlockBegin(List<LIRInstruction> instructions) {
            return instructions.get(0).id();
        }

        private static int getBlockEnd(List<LIRInstruction> instructions) {
            return instructions.get(instructions.size() - 1).id() + 1;
        }

        private StackInterval getOrCreateInterval(VirtualStackSlot stackSlot) {
            StackInterval interval = get(stackSlot);
            if (interval == null) {
                interval = new StackInterval(stackSlot, stackSlot.getLIRKind());
                put(stackSlot, interval);
            }
            return interval;
        }

        private StackInterval get(VirtualStackSlot stackSlot) {
            return stackSlotMap[stackSlot.getId()];
        }

        private StackInterval getIntervalFromStackId(int id) {
            return stackSlotMap[id];
        }

        private void put(VirtualStackSlot stackSlot, StackInterval interval) {
            stackSlotMap[stackSlot.getId()] = interval;
        }

        private void verifyIntervals() {
            forEachInterval(interval -> {
                assert interval.verify(this);
            });
        }

        private void forEachInterval(Consumer<StackInterval> proc) {
            for (StackInterval interval : stackSlotMap) {
                if (interval != null) {
                    proc.accept(interval);
                }
            }
        }

        public void dumpIntervals(String label) {
            Debug.dump(stackSlotMap, label);
        }

        private void createUnhandled() {
            unhandled = new LinkedList<>();
            active = new LinkedList<>();
            forEachInterval(this::insertSortedByFrom);
        }

        private void insertSortedByFrom(StackInterval interval) {
            unhandled.add(interval);
            unhandled.sort((a, b) -> a.from() - b.from());
        }

        private void insertSortedByTo(StackInterval interval) {
            active.add(interval);
            active.sort((a, b) -> a.to() - b.to());
        }

        private void allocateStackSlots() {
            // create interval lists
            createUnhandled();

            for (StackInterval current = activateNext(); current != null; current = activateNext()) {
                try (Indent indent = Debug.logAndIndent("allocate %s", current)) {
                    allocateSlot(current);
                }
            }

        }

        private void allocateSlot(StackInterval current) {
            VirtualStackSlot virtualSlot = current.getOperand();
            final StackSlot location;
            if (virtualSlot instanceof VirtualStackSlotRange) {
                // No reuse of ranges (yet).
                VirtualStackSlotRange slotRange = (VirtualStackSlotRange) virtualSlot;
                location = frameMapBuilder.getFrameMap().allocateStackSlots(slotRange.getSlots(), slotRange.getObjects());
                StackSlotAllocator.virtualFramesize.add(frameMapBuilder.getFrameMap().spillSlotRangeSize(slotRange.getSlots()));
                StackSlotAllocator.allocatedSlots.increment();
            } else {
                assert virtualSlot instanceof SimpleVirtualStackSlot : "Unexpexted VirtualStackSlot type: " + virtualSlot;
                StackSlot slot = findFreeSlot((SimpleVirtualStackSlot) virtualSlot);
                if (slot != null) {
                    /*
                     * Free stack slot available. Note that we create a new one because the kind
                     * might not match.
                     */
                    location = StackSlot.get(current.kind(), slot.getRawOffset(), slot.getRawAddFrameSize());
                    StackSlotAllocator.reusedSlots.increment();
                    Debug.log(1, "Reuse stack slot %s (reallocated from %s) for virtual stack slot %s", location, slot, virtualSlot);
                } else {
                    // Allocate new stack slot.
                    location = frameMapBuilder.getFrameMap().allocateSpillSlot(virtualSlot.getLIRKind());
                    StackSlotAllocator.virtualFramesize.add(frameMapBuilder.getFrameMap().spillSlotSize(virtualSlot.getLIRKind()));
                    StackSlotAllocator.allocatedSlots.increment();
                    Debug.log(1, "New stack slot %s for virtual stack slot %s", location, virtualSlot);
                }
            }
            Debug.log("Allocate location %s for interval %s", location, current);
            current.setLocation(location);
        }

        private static enum SlotSize {
            Size1,
            Size2,
            Size4,
            Size8,
            Illegal;
        }

        private SlotSize forKind(LIRKind kind) {
            switch (frameMapBuilder.getFrameMap().spillSlotSize(kind)) {
                case 1:
                    return SlotSize.Size1;
                case 2:
                    return SlotSize.Size2;
                case 4:
                    return SlotSize.Size4;
                case 8:
                    return SlotSize.Size8;
                default:
                    return SlotSize.Illegal;
            }
        }

        private EnumMap<SlotSize, LinkedList<StackSlot>> freeSlots = new EnumMap<>(SlotSize.class);

        private StackSlot findFreeSlot(SimpleVirtualStackSlot slot) {
            assert slot != null;
            SlotSize size = forKind(slot.getLIRKind());
            LinkedList<StackSlot> freeList = size == SlotSize.Illegal ? null : freeSlots.get(size);
            if (freeList == null) {
                return null;
            }
            return freeList.pollFirst();
        }

        private void freeSlot(StackSlot slot) {
            SlotSize size = forKind(slot.getLIRKind());
            if (size == SlotSize.Illegal) {
                return;
            }
            LinkedList<StackSlot> freeList = freeSlots.get(size);
            if (freeList == null) {
                freeList = new LinkedList<>();
                freeSlots.put(size, freeList);
            }
            freeList.add(slot);
        }

        private StackInterval activateNext() {
            if (unhandled.isEmpty()) {
                return null;
            }
            StackInterval next = unhandled.pollFirst();
            for (int id = next.from(); activePeekId() < id;) {
                finished(active.pollFirst());
            }
            Debug.log("activte %s", next);
            insertSortedByTo(next);
            return next;
        }

        private int activePeekId() {
            StackInterval first = active.peekFirst();
            if (first == null) {
                return Integer.MAX_VALUE;
            }
            return first.to();
        }

        private void finished(StackInterval interval) {
            StackSlot location = interval.location();
            Debug.log("finished %s (freeing %s)", interval, location);
            freeSlot(location);
        }

        private void assignStackSlots() {
            for (AbstractBlock<?> block : sortedBlocks) {
                lir.getLIRforBlock(block).forEach(op -> {
                    op.forEachInput(this::assignSlot);
                    op.forEachAlive(this::assignSlot);
                    op.forEachState(this::assignSlot);

                    op.forEachTemp(this::assignSlot);
                    op.forEachOutput(this::assignSlot);
                });
            }
        }

        /**
         * @see ValueProcedure
         * @param value
         * @param mode
         * @param flags
         */
        private Value assignSlot(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (isVirtualStackSlot(value)) {
                VirtualStackSlot slot = asVirtualStackSlot(value);
                StackInterval interval = get(slot);
                assert interval != null;
                return interval.location();
            }
            return value;
        }
    }
}
