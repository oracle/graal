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
package com.oracle.graal.lir.stackslotalloc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.phases.LIRPhase.Options.*;

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
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.options.*;

/**
 * Linear Scan {@link StackSlotAllocator}.
 * <p>
 * <b>Remark:</b> The analysis works under the assumption that a stack slot is no longer live after
 * its last usage. If an {@link LIRInstruction instruction} transfers the raw address of the stack
 * slot to another location, e.g. a registers, and this location is referenced later on, the
 * {@link com.oracle.graal.lir.LIRInstruction.Use usage} of the stack slot must be marked with the
 * {@link OperandFlag#UNINITIALIZED}. Otherwise the stack slot might be reused and its content
 * destroyed.
 */
public final class LSStackSlotAllocator extends AllocationPhase implements StackSlotAllocator {

    public static class Options {
        // @formatter:off
        @Option(help = "Use linear scan stack slot allocation.", type = OptionType.Debug)
        public static final NestedBooleanOptionValue LIROptLSStackSlotAllocator = new NestedBooleanOptionValue(LIROptimization, true);
        // @formatter:on
    }

    private static final DebugTimer MainTimer = Debug.timer("LSStackSlotAllocator");
    private static final DebugTimer NumInstTimer = Debug.timer("LSStackSlotAllocator[NumberInstruction]");
    private static final DebugTimer BuildIntervalsTimer = Debug.timer("LSStackSlotAllocator[BuildIntervals]");
    private static final DebugTimer VerifyIntervalsTimer = Debug.timer("LSStackSlotAllocator[VerifyIntervals]");
    private static final DebugTimer AllocateSlotsTimer = Debug.timer("LSStackSlotAllocator[AllocateSlots]");
    private static final DebugTimer AssignSlotsTimer = Debug.timer("LSStackSlotAllocator[AssignSlots]");

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, SpillMoveFactory spillMoveFactory) {
        lirGenRes.buildFrameMap(this);
    }

    public void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res) {
        try (DebugCloseable t = MainTimer.start()) {
            new Allocator(res.getLIR(), builder).allocate();
        }
    }

    private static final class Allocator {
        private final LIR lir;
        private final FrameMapBuilderTool frameMapBuilder;
        private final StackInterval[] stackSlotMap;
        private final PriorityQueue<StackInterval> unhandled;
        private final PriorityQueue<StackInterval> active;
        private final List<? extends AbstractBlockBase<?>> sortedBlocks;
        private final int maxOpId;

        private Allocator(LIR lir, FrameMapBuilderTool frameMapBuilder) {
            this.lir = lir;
            this.frameMapBuilder = frameMapBuilder;
            this.stackSlotMap = new StackInterval[frameMapBuilder.getNumberOfStackSlots()];
            this.sortedBlocks = lir.getControlFlowGraph().getBlocks();

            // insert by from
            this.unhandled = new PriorityQueue<>((a, b) -> a.from() - b.from());
            // insert by to
            this.active = new PriorityQueue<>((a, b) -> a.to() - b.to());

            try (DebugCloseable t = NumInstTimer.start()) {
                // step 1: number instructions
                this.maxOpId = numberInstructions(lir, sortedBlocks);
            }
        }

        private void allocate() {
            Debug.dump(lir, "After StackSlot numbering");

            long currentFrameSize = StackSlotAllocator.allocatedFramesize.isEnabled() ? frameMapBuilder.getFrameMap().currentFrameSize() : 0;
            Set<LIRInstruction> usePos;
            // step 2: build intervals
            try (Scope s = Debug.scope("StackSlotAllocationBuildIntervals"); Indent indent = Debug.logAndIndent("BuildIntervals"); DebugCloseable t = BuildIntervalsTimer.start()) {
                usePos = buildIntervals();
            }
            // step 3: verify intervals
            if (Debug.isEnabled()) {
                try (DebugCloseable t = VerifyIntervalsTimer.start()) {
                    verifyIntervals();
                }
            }
            if (Debug.isDumpEnabled()) {
                dumpIntervals("Before stack slot allocation");
            }
            // step 4: allocate stack slots
            try (DebugCloseable t = AllocateSlotsTimer.start()) {
                allocateStackSlots();
            }
            if (Debug.isDumpEnabled()) {
                dumpIntervals("After stack slot allocation");
            }

            // step 5: assign stack slots
            try (DebugCloseable t = AssignSlotsTimer.start()) {
                assignStackSlots(usePos);
            }
            Debug.dump(lir, "After StackSlot assignment");
            if (StackSlotAllocator.allocatedFramesize.isEnabled()) {
                StackSlotAllocator.allocatedFramesize.add(frameMapBuilder.getFrameMap().currentFrameSize() - currentFrameSize);
            }
        }

        // ====================
        // step 1: number instructions
        // ====================

        /**
         * Numbers all instructions in all blocks.
         *
         * @return The id of the last operation.
         */
        private static int numberInstructions(LIR lir, List<? extends AbstractBlockBase<?>> sortedBlocks) {
            int opId = 0;
            int index = 0;
            for (AbstractBlockBase<?> block : sortedBlocks) {

                List<LIRInstruction> instructions = lir.getLIRforBlock(block);

                int numInst = instructions.size();
                for (int j = 0; j < numInst; j++) {
                    LIRInstruction op = instructions.get(j);
                    op.setId(opId);

                    index++;
                    opId += 2; // numbering of lirOps by two
                }
            }
            assert (index << 1) == opId : "must match: " + (index << 1);
            return opId - 2;
        }

        // ====================
        // step 2: build intervals
        // ====================

        private Set<LIRInstruction> buildIntervals() {
            return new FixPointIntervalBuilder(lir, stackSlotMap, maxOpId()).build();
        }

        // ====================
        // step 3: verify intervals
        // ====================

        private void verifyIntervals() {
            forEachInterval(interval -> {
                assert interval.verify(maxOpId());
            });
        }

        // ====================
        // step 4: allocate stack slots
        // ====================

        private void allocateStackSlots() {
            // create unhandled lists
            forEachInterval(unhandled::add);

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
                assert virtualSlot instanceof SimpleVirtualStackSlot : "Unexpected VirtualStackSlot type: " + virtualSlot;
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

        private EnumMap<SlotSize, Deque<StackSlot>> freeSlots;

        /**
         * @return The list of free stack slots for {@code size} or {@code null} if there is none.
         */
        private Deque<StackSlot> getOrNullFreeSlots(SlotSize size) {
            if (freeSlots == null) {
                return null;
            }
            return freeSlots.get(size);
        }

        /**
         * @return the list of free stack slots for {@code size}. If there is none a list is
         *         created.
         */
        private Deque<StackSlot> getOrInitFreeSlots(SlotSize size) {
            assert size != SlotSize.Illegal;
            Deque<StackSlot> freeList;
            if (freeSlots != null) {
                freeList = freeSlots.get(size);
            } else {
                freeSlots = new EnumMap<>(SlotSize.class);
                freeList = null;
            }
            if (freeList == null) {
                freeList = new ArrayDeque<>();
                freeSlots.put(size, freeList);
            }
            assert freeList != null;
            return freeList;
        }

        /**
         * Gets a free stack slot for {@code slot} or {@code null} if there is none.
         */
        private StackSlot findFreeSlot(SimpleVirtualStackSlot slot) {
            assert slot != null;
            SlotSize size = forKind(slot.getLIRKind());
            if (size == SlotSize.Illegal) {
                return null;
            }
            Deque<StackSlot> freeList = getOrNullFreeSlots(size);
            if (freeList == null) {
                return null;
            }
            return freeList.pollLast();
        }

        /**
         * Adds a stack slot to the list of free slots.
         */
        private void freeSlot(StackSlot slot) {
            SlotSize size = forKind(slot.getLIRKind());
            if (size == SlotSize.Illegal) {
                return;
            }
            getOrInitFreeSlots(size).addLast(slot);
        }

        /**
         * Gets the next unhandled interval and finishes handled intervals.
         */
        private StackInterval activateNext() {
            if (unhandled.isEmpty()) {
                return null;
            }
            StackInterval next = unhandled.poll();
            // finish handled intervals
            for (int id = next.from(); activePeekId() < id;) {
                finished(active.poll());
            }
            Debug.log("active %s", next);
            active.add(next);
            return next;
        }

        /**
         * Gets the lowest {@link StackInterval#to() end position} of all active intervals. If there
         * is none {@link Integer#MAX_VALUE} is returned.
         */
        private int activePeekId() {
            StackInterval first = active.peek();
            if (first == null) {
                return Integer.MAX_VALUE;
            }
            return first.to();
        }

        /**
         * Finishes {@code interval} by adding its location to the list of free stack slots.
         */
        private void finished(StackInterval interval) {
            StackSlot location = interval.location();
            Debug.log("finished %s (freeing %s)", interval, location);
            freeSlot(location);
        }

        // ====================
        // step 5: assign stack slots
        // ====================

        private void assignStackSlots(Set<LIRInstruction> usePos) {
            for (LIRInstruction op : usePos) {
                op.forEachInput(assignSlot);
                op.forEachAlive(assignSlot);
                op.forEachState(assignSlot);

                op.forEachTemp(assignSlot);
                op.forEachOutput(assignSlot);
            }
        }

        ValueProcedure assignSlot = new ValueProcedure() {
            public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVirtualStackSlot(value)) {
                    VirtualStackSlot slot = asVirtualStackSlot(value);
                    StackInterval interval = get(slot);
                    assert interval != null;
                    return interval.location();
                }
                return value;
            }
        };

        // ====================
        //
        // ====================

        /**
         * Gets the highest instruction id.
         */
        private int maxOpId() {
            return maxOpId;
        }

        private StackInterval get(VirtualStackSlot stackSlot) {
            return stackSlotMap[stackSlot.getId()];
        }

        private void forEachInterval(Consumer<StackInterval> proc) {
            for (StackInterval interval : stackSlotMap) {
                if (interval != null) {
                    proc.accept(interval);
                }
            }
        }

        private void dumpIntervals(String label) {
            Debug.dump(stackSlotMap, label);
        }

    }
}
