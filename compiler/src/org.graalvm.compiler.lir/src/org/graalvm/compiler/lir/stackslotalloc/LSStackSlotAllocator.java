/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.stackslotalloc;

import static org.graalvm.compiler.debug.DebugContext.BASIC_LEVEL;
import static org.graalvm.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static org.graalvm.compiler.lir.phases.LIRPhase.Options.LIROptimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.PriorityQueue;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.ValueProcedure;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlot;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlotAlias;
import org.graalvm.compiler.lir.framemap.VirtualStackSlotRange;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Linear Scan {@link StackSlotAllocatorUtil stack slot allocator}.
 * <p>
 * <b>Remark:</b> The analysis works under the assumption that a stack slot is no longer live after
 * its last usage. If an {@link LIRInstruction instruction} transfers the raw address of the stack
 * slot to another location, e.g. a registers, and this location is referenced later on, the
 * {@link org.graalvm.compiler.lir.LIRInstruction.Use usage} of the stack slot must be marked with
 * the {@link OperandFlag#UNINITIALIZED}. Otherwise the stack slot might be reused and its content
 * destroyed.
 */
public final class LSStackSlotAllocator extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use linear scan stack slot allocation.", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptLSStackSlotAllocator = new NestedBooleanOptionKey(LIROptimization, true);
        // @formatter:on
    }

    private static final TimerKey MainTimer = DebugContext.timer("LSStackSlotAllocator");
    private static final TimerKey NumInstTimer = DebugContext.timer("LSStackSlotAllocator[NumberInstruction]");
    private static final TimerKey BuildIntervalsTimer = DebugContext.timer("LSStackSlotAllocator[BuildIntervals]");
    private static final TimerKey VerifyIntervalsTimer = DebugContext.timer("LSStackSlotAllocator[VerifyIntervals]");
    private static final TimerKey AllocateSlotsTimer = DebugContext.timer("LSStackSlotAllocator[AllocateSlots]");
    private static final TimerKey AssignSlotsTimer = DebugContext.timer("LSStackSlotAllocator[AssignSlots]");

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        allocateStackSlots((FrameMapBuilderTool) lirGenRes.getFrameMapBuilder(), lirGenRes);
        lirGenRes.buildFrameMap();
    }

    @SuppressWarnings("try")
    public static void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res) {
        if (builder.getNumberOfStackSlots() > 0) {
            try (DebugCloseable t = MainTimer.start(res.getLIR().getDebug())) {
                new Allocator(res.getLIR(), builder).allocate();
            }
        }
    }

    private static final class Allocator {

        private final LIR lir;
        private final DebugContext debug;
        private final FrameMapBuilderTool frameMapBuilder;
        private final StackInterval[] stackSlotMap;
        private final PriorityQueue<StackInterval> unhandled;
        private final PriorityQueue<StackInterval> active;
        private final AbstractBlockBase<?>[] sortedBlocks;
        private final int maxOpId;

        @SuppressWarnings("try")
        private Allocator(LIR lir, FrameMapBuilderTool frameMapBuilder) {
            this.lir = lir;
            this.debug = lir.getDebug();
            this.frameMapBuilder = frameMapBuilder;
            this.stackSlotMap = new StackInterval[frameMapBuilder.getNumberOfStackSlots()];
            this.sortedBlocks = lir.getControlFlowGraph().getBlocks();

            // insert by from
            this.unhandled = new PriorityQueue<>((a, b) -> a.from() - b.from());
            // insert by to
            this.active = new PriorityQueue<>((a, b) -> a.to() - b.to());

            try (DebugCloseable t = NumInstTimer.start(debug)) {
                // step 1: number instructions
                this.maxOpId = numberInstructions(lir, sortedBlocks);
            }
        }

        @SuppressWarnings("try")
        private void allocate() {
            debug.dump(DebugContext.VERBOSE_LEVEL, lir, "After StackSlot numbering");

            boolean allocationFramesizeEnabled = StackSlotAllocatorUtil.allocatedFramesize.isEnabled(debug);
            long currentFrameSize = allocationFramesizeEnabled ? frameMapBuilder.getFrameMap().currentFrameSize() : 0;
            EconomicSet<LIRInstruction> usePos;
            // step 2: build intervals
            try (DebugContext.Scope s = debug.scope("StackSlotAllocationBuildIntervals"); Indent indent = debug.logAndIndent("BuildIntervals"); DebugCloseable t = BuildIntervalsTimer.start(debug)) {
                usePos = buildIntervals();
            }
            // step 3: verify intervals
            if (debug.areScopesEnabled()) {
                try (DebugCloseable t = VerifyIntervalsTimer.start(debug)) {
                    assert verifyIntervals();
                }
            }
            if (debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                dumpIntervals("Before stack slot allocation");
            }
            // step 4: allocate stack slots
            try (DebugCloseable t = AllocateSlotsTimer.start(debug)) {
                /*
                 * Allocate primitive spill slots before reference spill slots. This ensures a
                 * ReferenceMap will be as compact as possible and only exceed the encoding limit of
                 * a stack offset if there are really too many objects live on the stack at an
                 * instruction with a ReferenceMap (as opposed to the method simply having a very
                 * large frame).
                 */
                allocateStackSlots(IS_PRIMITIVE_INTERVAL);
                allocateStackSlots(IS_REFERENCE_INTERVAL);
            }
            if (debug.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                dumpIntervals("After stack slot allocation");
            }

            // step 5: assign stack slots
            try (DebugCloseable t = AssignSlotsTimer.start(debug)) {
                assignStackSlots(usePos);
            }
            if (allocationFramesizeEnabled) {
                StackSlotAllocatorUtil.allocatedFramesize.add(debug, frameMapBuilder.getFrameMap().currentFrameSize() - currentFrameSize);
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
        private static int numberInstructions(LIR lir, AbstractBlockBase<?>[] sortedBlocks) {
            int opId = 0;
            int index = 0;
            for (AbstractBlockBase<?> block : sortedBlocks) {

                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

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

        private EconomicSet<LIRInstruction> buildIntervals() {
            return new FixPointIntervalBuilder(lir, stackSlotMap, maxOpId()).build();
        }

        // ====================
        // step 3: verify intervals
        // ====================

        private boolean verifyIntervals() {
            for (StackInterval interval : stackSlotMap) {
                if (interval != null) {
                    assert interval.verify(maxOpId());
                }
            }
            return true;
        }

        // ====================
        // step 4: allocate stack slots
        // ====================

        @SuppressWarnings("try")
        private void allocateStackSlots(Predicate<StackInterval> predicate) {
            for (StackInterval interval : stackSlotMap) {
                if (interval != null && (predicate == null || predicate.test(interval))) {
                    unhandled.add(interval);
                }
            }
            for (StackInterval current = activateNext(); current != null; current = activateNext()) {
                try (Indent indent = debug.logAndIndent("allocate %s", current)) {
                    allocateSlot(current);
                }
            }

            // Cannot re-use free slots between rounds of slot allocation
            freeSlots = null;
            active.clear();
        }

        private static final Predicate<StackInterval> IS_REFERENCE_INTERVAL = new Predicate<>() {
            @Override
            public boolean test(StackInterval interval) {
                return !((LIRKind) interval.kind()).isValue();
            }
        };

        private static final Predicate<StackInterval> IS_PRIMITIVE_INTERVAL = new Predicate<>() {
            @Override
            public boolean test(StackInterval interval) {
                return ((LIRKind) interval.kind()).isValue();
            }
        };

        private void allocateSlot(StackInterval current) {
            VirtualStackSlot virtualSlot = current.getOperand();
            final StackSlot location;
            if (virtualSlot instanceof VirtualStackSlotRange) {
                // No reuse of ranges (yet).
                VirtualStackSlotRange slotRange = (VirtualStackSlotRange) virtualSlot;
                location = frameMapBuilder.getFrameMap().allocateStackMemory(slotRange.getSizeInBytes(), slotRange.getAlignmentInBytes());
                StackSlotAllocatorUtil.virtualFramesize.add(debug, slotRange.getSizeInBytes());
                StackSlotAllocatorUtil.allocatedSlots.increment(debug);
            } else {
                SimpleVirtualStackSlot simpleSlot;
                if (virtualSlot instanceof SimpleVirtualStackSlot) {
                    simpleSlot = (SimpleVirtualStackSlot) virtualSlot;
                } else if (virtualSlot instanceof SimpleVirtualStackSlotAlias) {
                    simpleSlot = ((SimpleVirtualStackSlotAlias) virtualSlot).getAliasedSlot();
                } else {
                    throw GraalError.shouldNotReachHere("Unexpected VirtualStackSlot type: " + virtualSlot);
                }
                StackSlot slot = findFreeSlot(simpleSlot);
                if (slot != null) {
                    /*
                     * Free stack slot available. Note that we create a new one because the kind
                     * might not match.
                     */
                    location = StackSlot.get(current.kind(), slot.getRawOffset(), slot.getRawAddFrameSize());
                    StackSlotAllocatorUtil.reusedSlots.increment(debug);
                    debug.log(BASIC_LEVEL, "Reuse stack slot %s (reallocated from %s) for virtual stack slot %s", location, slot, virtualSlot);
                } else {
                    // Allocate new stack slot.
                    ValueKind<?> slotKind = simpleSlot.getValueKind();
                    location = frameMapBuilder.getFrameMap().allocateSpillSlot(slotKind);
                    StackSlotAllocatorUtil.virtualFramesize.add(debug, frameMapBuilder.getFrameMap().spillSlotSize(slotKind));
                    StackSlotAllocatorUtil.allocatedSlots.increment(debug);
                    debug.log(BASIC_LEVEL, "New stack slot %s for virtual stack slot %s", location, virtualSlot);
                }
            }
            debug.log("Allocate location %s for interval %s", location, current);
            current.setLocation(location);
        }

        /**
         * Map from log2 of {@link FrameMap#spillSlotSize(ValueKind) a spill slot size} to a list of
         * free stack slots.
         */
        private ArrayList<Deque<StackSlot>> freeSlots;

        /**
         * @return The list of free stack slots for {@code index} or {@code null} if there is none.
         */
        private Deque<StackSlot> getNullOrFreeSlots(int index) {
            if (freeSlots == null) {
                return null;
            }
            if (index < freeSlots.size()) {
                return freeSlots.get(index);
            }
            return null;
        }

        /**
         * @return the list of free stack slots for {@code index}. If there is none a list is
         *         created.
         */
        private Deque<StackSlot> getOrInitFreeSlots(int index) {
            Deque<StackSlot> freeList = null;
            if (freeSlots == null) {
                freeSlots = new ArrayList<>(6);
            } else if (index < freeSlots.size()) {
                freeList = freeSlots.get(index);
            }
            if (freeList == null) {
                int requiredSize = index + 1;
                for (int i = freeSlots.size(); i < requiredSize; i++) {
                    freeSlots.add(null);
                }
                freeList = new ArrayDeque<>();
                freeSlots.set(index, freeList);
            }
            return freeList;
        }

        /**
         * Gets a free stack slot for {@code slot} or {@code null} if there is none.
         */
        private StackSlot findFreeSlot(SimpleVirtualStackSlot slot) {
            assert slot != null;
            int size = log2SpillSlotSize(slot.getValueKind());
            Deque<StackSlot> freeList = getNullOrFreeSlots(size);
            if (freeList == null) {
                return null;
            }
            return freeList.pollLast();
        }

        /**
         * Adds a stack slot to the list of free slots.
         */
        private void freeSlot(StackSlot slot) {
            int size = log2SpillSlotSize(slot.getValueKind());
            getOrInitFreeSlots(size).addLast(slot);
        }

        private int log2SpillSlotSize(ValueKind<?> kind) {
            int size = frameMapBuilder.getFrameMap().spillSlotSize(kind);
            assert CodeUtil.isPowerOf2(size) : "kind: " + kind + ", size: " + size;
            return CodeUtil.log2(size);
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
            debug.log("active %s", next);
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
            if (interval.getOperand() instanceof VirtualStackSlotRange) {
                /* Memory block with a non-standard size. Cannot re-use, so no need to free. */
                debug.log("finished %s (not freeing VirtualStackSlotRange)", interval);
            } else {
                StackSlot location = interval.location();
                debug.log("finished %s (freeing %s)", interval, location);
                freeSlot(location);
            }
        }

        // ====================
        // step 5: assign stack slots
        // ====================

        private void assignStackSlots(EconomicSet<LIRInstruction> usePos) {
            for (LIRInstruction op : usePos) {
                op.forEachInput(assignSlot);
                op.forEachAlive(assignSlot);
                op.forEachState(assignSlot);

                op.forEachTemp(assignSlot);
                op.forEachOutput(assignSlot);
            }
        }

        ValueProcedure assignSlot = new ValueProcedure() {
            @Override
            public Value doValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVirtualStackSlot(value)) {
                    VirtualStackSlot virtualSlot = asVirtualStackSlot(value);
                    StackInterval interval = get(virtualSlot);
                    assert interval != null;
                    StackSlot slot = interval.location();
                    if (virtualSlot instanceof SimpleVirtualStackSlotAlias) {
                        GraalError.guarantee(mode == LIRInstruction.OperandMode.USE || mode == LIRInstruction.OperandMode.ALIVE, "Invalid application of SimpleVirtualStackSlotAlias");
                        // return the same slot, but with the alias's kind.
                        return StackSlot.get(virtualSlot.getValueKind(), slot.getRawOffset(), slot.getRawAddFrameSize());
                    }
                    return slot;
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

        private void dumpIntervals(String label) {
            debug.dump(DebugContext.VERBOSE_LEVEL, new StackIntervalDumper(Arrays.copyOf(stackSlotMap, stackSlotMap.length)), label);
        }

    }
}
