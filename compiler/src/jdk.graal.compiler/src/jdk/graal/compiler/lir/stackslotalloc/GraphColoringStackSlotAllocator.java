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
package jdk.graal.compiler.lir.stackslotalloc;

import static jdk.graal.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static jdk.graal.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.EnumSet;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.InstructionValueConsumer;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.ValueProcedure;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.framemap.FrameMapBuilderTool;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlot;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlotAlias;
import jdk.graal.compiler.lir.framemap.VirtualStackSlotRange;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.lir.util.BlockWorkList;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Reuses stack slots by coloring an interference graph of simple virtual stack slots. Slots with
 * different spill sizes are colored separately. Virtual stack slot ranges are allocated separately
 * because their size and alignment are not represented by a color.
 */
public final class GraphColoringStackSlotAllocator extends AllocationPhase {

    /** Options controlling use of this allocator. */
    public static class Options {
        // @formatter:off
        @Option(help = "Use graph coloring stack slot allocation in the regular low tier.", type = OptionType.Debug)
        public static final OptionKey<Boolean> LIROptGraphColoringStackSlotAllocator = new OptionKey<>(true);
        // @formatter:on
    }

    private static final TimerKey MainTimer = DebugContext.timer("GraphColoringStackSlotAllocator");
    private static final TimerKey LivenessTimer = DebugContext.timer("GraphColoringStackSlotAllocator[Liveness]");
    private static final TimerKey SummaryTimer = DebugContext.timer("GraphColoringStackSlotAllocator[Summary]");
    private static final TimerKey FixedPointTimer = DebugContext.timer("GraphColoringStackSlotAllocator[FixedPoint]");
    private static final TimerKey GraphTimer = DebugContext.timer("GraphColoringStackSlotAllocator[Graph]");
    private static final TimerKey ColoringTimer = DebugContext.timer("GraphColoringStackSlotAllocator[Coloring]");
    private static final CounterKey GraphEdges = DebugContext.counter("GraphColoringStackSlotAllocator[Edges]");
    private static final CounterKey GraphBytes = DebugContext.counter("GraphColoringStackSlotAllocator[AdjacencyBytes]");
    private static final CounterKey LivenessBits = DebugContext.counter("GraphColoringStackSlotAllocator[LivenessBits]");
    private static final CounterKey LivenessBytes = DebugContext.counter("GraphColoringStackSlotAllocator[LivenessCapacityBytes]");
    private static final CounterKey Colors = DebugContext.counter("GraphColoringStackSlotAllocator[Colors]");
    private static final CounterKey VirtualSlots = DebugContext.counter("GraphColoringStackSlotAllocator[VirtualSlots]");
    private static final CounterKey UninitializedSlots = DebugContext.counter("StackSlotAllocator[uninitializedSlots]");
    private static final CounterKey UninitializedBytes = DebugContext.counter("StackSlotAllocator[uninitializedBytes]").doc(
                    "Reserved bytes for stack slots with UNINITIALIZED uses, excluding alignment padding.");

    /** Allocates stack slots for the LIR and finalizes its frame map. */
    @Override
    @SuppressWarnings("try")
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        FrameMapBuilderTool builder = (FrameMapBuilderTool) lirGenRes.getFrameMapBuilder();
        if (builder.getNumberOfStackSlots() > 0) {
            DebugContext debug = lirGenRes.getLIR().getDebug();
            try (DebugCloseable timer = MainTimer.start(debug)) {
                new Allocator(lirGenRes.getLIR(), builder).allocate();
            }
        }
        lirGenRes.buildFrameMap();
    }

    /** Reused while scanning instructions; no per-instruction sets are retained. */
    private static final class InstructionScratch {
        final BitSet uses = new BitSet();
        final BitSet kills = new BitSet();
        final BitSet touched = new BitSet();

        /** Clears operand sets before visiting the next instruction. */
        void clear() {
            uses.clear();
            kills.clear();
            touched.clear();
        }
    }

    /** A simple virtual stack slot and its interference-graph state. */
    private static final class Node {
        final SimpleVirtualStackSlot slot;
        final int size;
        /** Shared with every node of this spill size; private-color slots are removed after liveness. */
        final BitSet sameSizeSlots;
        BitSet neighbors;
        int degree;
        int color = -1;
        long referencingInstructions;

        /** Creates a node in the given spill-size class. */
        Node(SimpleVirtualStackSlot slot, int size, BitSet sameSizeSlots) {
            this.slot = slot;
            this.size = size;
            this.sameSizeSlots = sameSizeSlots;
        }
    }

    /** Nodes and physical locations for one spill size. */
    private static final class SizeClass {
        final int size;
        final ArrayList<Node> nodes = new ArrayList<>();
        final ArrayList<StackSlot> locationsByColor = new ArrayList<>();

        /** Creates a spill-size class. */
        SizeClass(int size) {
            this.size = size;
        }
    }

    /** A color assigned to one spill-size class before its physical stack location is known. */
    private static final class PhysicalColor {
        final SizeClass sizeClass;
        final int index;
        Node representative;
        boolean containsReference;
        long referencingInstructions;

        /** Creates a color whose location will be assigned after sorting. */
        PhysicalColor(SizeClass sizeClass, int index) {
            this.sizeClass = sizeClass;
            this.index = index;
        }
    }

    /** Block summaries needed only while solving liveness. */
    private static final class BlockSummaries {
        final BlockMap<BitSet> gen;
        final BlockMap<BitSet> kill;

        /** Creates block-indexed gen and kill summaries. */
        BlockSummaries(LIR lir) {
            gen = new BlockMap<>(lir.getControlFlowGraph());
            kill = new BlockMap<>(lir.getControlFlowGraph());
        }
    }

    /** Fixed-point results needed only while constructing the interference graph. */
    private static final class LiveSets {
        final BlockMap<BitSet> in;

        /** Creates block-indexed live-in sets. */
        LiveSets(LIR lir) {
            in = new BlockMap<>(lir.getControlFlowGraph());
        }
    }

    /** Per-compilation state for graph construction, coloring, and LIR rewriting. */
    private static final class Allocator {
        private final LIR lir;
        private final FrameMapBuilderTool builder;
        private final DebugContext debug;
        private final Node[] nodes;
        private final StackSlot[] locationsBySlotId;
        private final InstructionScratch instructionScratch = new InstructionScratch();
        private final InstructionValueConsumer useConsumer = (inst, value, mode, flags) -> record(value, flags, false);
        private final InstructionValueConsumer killConsumer = (inst, value, mode, flags) -> record(value, flags, true);
        private final BitSet uninitializedSlots = new BitSet();
        private long edgeCount;

        /** Indexes the builder's simple virtual stack slots by slot ID and spill size. */
        Allocator(LIR lir, FrameMapBuilderTool builder) {
            this.lir = lir;
            this.builder = builder;
            this.debug = lir.getDebug();
            this.nodes = new Node[builder.getNumberOfStackSlots()];
            this.locationsBySlotId = new StackSlot[nodes.length];
            EconomicMap<Integer, BitSet> slotsBySize = EconomicMap.create();
            for (VirtualStackSlot slot : builder.getStackSlots()) {
                if (slot instanceof SimpleVirtualStackSlot simple) {
                    int size = builder.getFrameMap().spillSlotSize(simple.getValueKind());
                    BitSet sameSizeSlots = slotsBySize.computeIfAbsent(size, unused -> new BitSet(nodes.length));
                    sameSizeSlots.set(slot.getId());
                    nodes[slot.getId()] = new Node(simple, size, sameSizeSlots);
                } else if (!(slot instanceof VirtualStackSlotRange)) {
                    throw GraalError.shouldNotReachHere("Unexpected VirtualStackSlot type: " + slot); // ExcludeFromJacocoGeneratedReport
                }
            }
        }

        /** Builds the graph, allocates ranges and colors, and rewrites virtual slots. */
        @SuppressWarnings("try")
        void allocate() {
            boolean frameSizeEnabled = StackSlotAllocatorUtil.allocatedFramesize.isEnabled(debug);
            long initialFrameSize = frameSizeEnabled ? builder.getFrameMap().currentFrameSize() : 0;
            buildInterferenceGraph();
            // Ranges must precede reference slots so they cannot increase oop-map stack offsets.
            allocateRanges();
            try (DebugCloseable timer = ColoringTimer.start(debug)) {
                colorAndAllocate();
            }
            rewriteLIR();
            if (frameSizeEnabled) {
                StackSlotAllocatorUtil.allocatedFramesize.add(debug, builder.getFrameMap().currentFrameSize() - initialFrameSize);
            }
        }

        /** Builds the graph while keeping liveness scratch local to this call. */
        @SuppressWarnings("try")
        private void buildInterferenceGraph() {
            LiveSets liveSets;
            try (DebugCloseable timer = LivenessTimer.start(debug)) {
                liveSets = analyzeLiveness();
            }
            // These shared size-class sets are used only to find graph neighbors. Private-color
            // slots need no edges, so remove them once rather than filtering every connection.
            for (int id = uninitializedSlots.nextSetBit(0); id >= 0; id = uninitializedSlots.nextSetBit(id + 1)) {
                if (nodes[id] != null) {
                    nodes[id].sameSizeSlots.clear(id);
                }
            }
            try (DebugCloseable timer = GraphTimer.start(debug)) {
                BitSet pendingEdges = new BitSet();
                buildGraph(liveSets, pendingEdges);
                recordGraphMetrics();
            }
        }

        /** Computes the block summaries and solves liveness to a fixed point. */
        @SuppressWarnings("try")
        private LiveSets analyzeLiveness() {
            BlockSummaries summaries;
            try (DebugCloseable timer = SummaryTimer.start(debug)) {
                summaries = summarizeBlocks();
            }
            try (DebugCloseable timer = FixedPointTimer.start(debug)) {
                return computeLiveness(summaries);
            }
        }

        /** Summarizes each block's virtual-slot uses and kills. */
        private BlockSummaries summarizeBlocks() {
            BlockSummaries summaries = new BlockSummaries(lir);
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                BitSet gen = new BitSet();
                BitSet kill = new BitSet();
                ArrayList<LIRInstruction> ops = lir.getLIRforBlock(block);
                for (int i = ops.size() - 1; i >= 0; i--) {
                    scanInstruction(ops.get(i), true);
                    gen.andNot(instructionScratch.kills);
                    gen.or(instructionScratch.uses);
                    kill.or(instructionScratch.kills);
                }
                summaries.gen.put(block, gen);
                summaries.kill.put(block, kill);
            }
            recordUninitializedSlots();
            return summaries;
        }

        /** Records the number and reserved size of slots with uninitialized uses. */
        private void recordUninitializedSlots() {
            if (UninitializedSlots.isEnabled(debug)) {
                UninitializedSlots.add(debug, uninitializedSlots.cardinality());
            }
            if (UninitializedBytes.isEnabled(debug)) {
                long bytes = 0;
                for (VirtualStackSlot slot : builder.getStackSlots()) {
                    int id = slot.getId();
                    if (uninitializedSlots.get(id)) {
                        bytes += slot instanceof VirtualStackSlotRange range ? range.getSizeInBytes() : nodes[id].size;
                    }
                }
                UninitializedBytes.add(debug, bytes);
            }
        }

        /** Collects one instruction's slot uses, kills, and optional access counts. */
        private void scanInstruction(LIRInstruction op, boolean countOperands) {
            instructionScratch.clear();
            // USE_KILL is both a read and a kill. A slot mentioned by any operand is touched,
            // including outputs and temporary operands that are not live before the instruction.
            op.visitEachUseKill(killConsumer);
            op.visitEachTemp(killConsumer);
            op.visitEachOutput(killConsumer);
            op.visitEachAlive(useConsumer);
            op.visitEachInput(useConsumer);
            op.visitEachUseKill(useConsumer);
            // Count each virtual slot once per instruction. State operands do not themselves
            // generate stack accesses, so visit them after recording the instruction operands.
            if (countOperands) {
                for (int id = instructionScratch.touched.nextSetBit(0); id >= 0; id = instructionScratch.touched.nextSetBit(id + 1)) {
                    nodes[id].referencingInstructions++;
                }
            }
            op.visitEachState(useConsumer);
        }

        /** Adds a virtual-stack operand to the current instruction's scratch sets. */
        private void record(Value value, EnumSet<OperandFlag> flags, boolean kill) {
            if (isVirtualStackSlot(value)) {
                VirtualStackSlot slot = asVirtualStackSlot(value);
                // An alias has its own value kind but shares the base slot's lifetime and color.
                int id = slot instanceof SimpleVirtualStackSlotAlias alias ? alias.getAliasedSlot().getId() : slot.getId();
                if (!kill && flags.contains(OperandFlag.UNINITIALIZED)) {
                    uninitializedSlots.set(id);
                }
                if (nodes[id] != null) {
                    instructionScratch.touched.set(id);
                    if (kill) {
                        instructionScratch.kills.set(id);
                    } else {
                        instructionScratch.uses.set(id);
                    }
                }
            }
        }

        /** Solves live-in sets using a predecessor worklist and reusable live-out scratch. */
        private LiveSets computeLiveness(BlockSummaries summaries) {
            LiveSets liveSets = new LiveSets(lir);
            BasicBlock<?>[] blocks = lir.getControlFlowGraph().getBlocks();
            BlockWorkList worklist = new BlockWorkList(blocks.length);
            for (BasicBlock<?> block : blocks) {
                liveSets.in.put(block, new BitSet());
            }
            // Visit successors before predecessors where the block order permits it.
            for (int i = blocks.length - 1; i >= 0; i--) {
                worklist.add(blocks[i]);
            }
            BitSet candidateIn = new BitSet();
            while (!worklist.isEmpty()) {
                BasicBlock<?> block = worklist.poll();
                candidateIn.clear();
                collectLiveOut(block, liveSets, candidateIn);
                // LiveIn = Gen union (LiveOut minus Kill).
                candidateIn.andNot(summaries.kill.get(block));
                candidateIn.or(summaries.gen.get(block));
                BitSet in = liveSets.in.get(block);
                if (!candidateIn.equals(in)) {
                    in.clear();
                    in.or(candidateIn);
                    for (int i = 0; i < block.getPredecessorCount(); i++) {
                        worklist.add(block.getPredecessorAt(i));
                    }
                }
            }
            recordLivenessSize(summaries, liveSets, blocks);
            return liveSets;
        }

        /** Unions successor live-in sets into an empty reusable set. */
        private static void collectLiveOut(BasicBlock<?> block, LiveSets liveSets, BitSet out) {
            assert out.isEmpty();
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                out.or(liveSets.in.get(block.getSuccessorAt(i)));
            }
        }

        /** Counts retained liveness capacity and set bits only when diagnostics are enabled. */
        private void recordLivenessSize(BlockSummaries summaries, LiveSets liveSets, BasicBlock<?>[] blocks) {
            boolean countBits = LivenessBits.isEnabled(debug);
            boolean countBytes = LivenessBytes.isEnabled(debug);
            boolean log = debug.isLogEnabled();
            if (countBits || countBytes || log) {
                long entries = 0;
                long outBits = 0;
                long bytes = 0;
                int maxLiveIn = 0;
                BitSet out = countBits ? new BitSet() : null;
                for (BasicBlock<?> block : blocks) {
                    BitSet gen = summaries.gen.get(block);
                    BitSet kill = summaries.kill.get(block);
                    BitSet in = liveSets.in.get(block);
                    if (countBits || log) {
                        entries += (long) gen.cardinality() + kill.cardinality() + in.cardinality();
                    }
                    if (countBits) {
                        out.clear();
                        collectLiveOut(block, liveSets, out);
                        outBits += out.cardinality();
                    }
                    if (countBytes || log) {
                        bytes += ((long) gen.size() + kill.size() + in.size()) / Byte.SIZE;
                    }
                    if (log) {
                        maxLiveIn = Math.max(maxLiveIn, in.cardinality());
                    }
                }
                if (countBits) {
                    LivenessBits.add(debug, entries + outBits);
                }
                if (countBytes) {
                    LivenessBytes.add(debug, bytes);
                }
                if (log) {
                    debug.log("GraphColoringStackSlotAllocator liveness: slotIds=%d blocks=%d entries=%d maxLiveIn=%d capacityBytes=%d", nodes.length, blocks.length, entries, maxLiveIn, bytes);
                }
            }
        }

        /** Adds edges for instruction operands that overlap live slots. */
        private void buildGraph(LiveSets liveSets, BitSet pendingEdges) {
            BitSet instructionFootprint = new BitSet();
            BitSet live = new BitSet();
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                ArrayList<LIRInstruction> ops = lir.getLIRforBlock(block);
                live.clear();
                collectLiveOut(block, liveSets, live);
                for (int i = ops.size() - 1; i >= 0; i--) {
                    scanInstruction(ops.get(i), false);
                    // Live slots may belong to mutually exclusive successor paths, so do not
                    // connect them to each other. Add edges only from operands touched here.
                    if (!instructionScratch.touched.isEmpty()) {
                        // Connect touched operands to both each other and the live set in one
                        // pass instead of constructing the two sets of edges separately.
                        instructionFootprint.clear();
                        instructionFootprint.or(live);
                        instructionFootprint.or(instructionScratch.touched);
                        for (int id = instructionScratch.touched.nextSetBit(0); id >= 0; id = instructionScratch.touched.nextSetBit(id + 1)) {
                            connect(id, instructionFootprint, pendingEdges);
                        }
                    }
                    live.andNot(instructionScratch.kills);
                    live.or(instructionScratch.uses);
                }
                assert live.equals(liveSets.in.get(block)) : Assertions.errorMessageContext("block", block, "expected live-in", liveSets.in.get(block), "actual live-in", live);
            }
        }

        /** Records edge count and, when enabled, adjacency-bitset capacity. */
        private void recordGraphMetrics() {
            GraphEdges.add(debug, edgeCount);
            if (GraphBytes.isEnabled(debug)) {
                long bytes = 0;
                for (Node node : nodes) {
                    if (node != null && node.neighbors != null) {
                        bytes += (long) node.neighbors.size() / Byte.SIZE;
                    }
                }
                GraphBytes.add(debug, bytes);
            }
        }

        /** Adds previously absent same-size edges from one node to candidate nodes. */
        private void connect(int id, BitSet candidates, BitSet pendingEdges) {
            // UNINITIALIZED slots receive private colors, so no edge involving one is needed.
            if (uninitializedSlots.get(id)) {
                return;
            }
            Node first = nodes[id];
            // Retain only new same-size edges. Every edge is stored in both endpoint bitsets.
            pendingEdges.clear();
            pendingEdges.or(candidates);
            pendingEdges.and(first.sameSizeSlots);
            pendingEdges.clear(id);
            if (first.neighbors != null) {
                pendingEdges.andNot(first.neighbors);
            }
            int other = pendingEdges.nextSetBit(0);
            if (other < 0) {
                return;
            }
            if (first.neighbors == null) {
                first.neighbors = new BitSet();
            }
            first.neighbors.or(pendingEdges);
            for (; other >= 0; other = pendingEdges.nextSetBit(other + 1)) {
                Node second = nodes[other];
                if (second.neighbors == null) {
                    second.neighbors = new BitSet();
                }
                second.neighbors.set(id);
                first.degree++;
                second.degree++;
                edgeCount++;
            }
        }

        /** Colors simple slots and assigns physical locations to their colors. */
        private void colorAndAllocate() {
            ArrayList<SizeClass> sizeClasses = new ArrayList<>();
            int virtualCount = 0;
            for (Node node : nodes) {
                if (node != null) {
                    virtualCount++;
                    SizeClass sizeClass = null;
                    for (SizeClass candidate : sizeClasses) {
                        if (candidate.size == node.size) {
                            sizeClass = candidate;
                            break;
                        }
                    }
                    if (sizeClass == null) {
                        sizeClass = new SizeClass(node.size);
                        sizeClasses.add(sizeClass);
                    }
                    sizeClass.nodes.add(node);
                }
            }
            VirtualSlots.add(debug, virtualCount);
            sizeClasses.sort(Comparator.comparingInt(sizeClass -> sizeClass.size));
            ArrayList<PhysicalColor> physicalColors = new ArrayList<>();
            for (SizeClass sizeClass : sizeClasses) {
                // Greedy coloring visits high-degree nodes first to reduce the number of colors.
                // Slot ID breaks ties so the result does not depend on hash iteration order.
                sizeClass.nodes.sort(Comparator.comparingInt((Node node) -> node.degree).reversed().thenComparingInt(node -> node.slot.getId()));
                int colorCount = 0;
                BitSet unavailable = new BitSet();
                for (Node node : sizeClass.nodes) {
                    if (uninitializedSlots.get(node.slot.getId())) {
                        continue;
                    }
                    unavailable.clear();
                    if (node.neighbors != null) {
                        for (int id = node.neighbors.nextSetBit(0); id >= 0; id = node.neighbors.nextSetBit(id + 1)) {
                            if (nodes[id].color >= 0) {
                                unavailable.set(nodes[id].color);
                            }
                        }
                    }
                    node.color = unavailable.nextClearBit(0);
                    colorCount = Math.max(colorCount, node.color + 1);
                }
                // An UNINITIALIZED use may observe a value beyond its apparent LIR lifetime.
                // Give each such slot a private color instead of materializing edges to all peers.
                for (Node node : sizeClass.nodes) {
                    if (uninitializedSlots.get(node.slot.getId())) {
                        node.color = colorCount++;
                    }
                }
                assert verifyColors(sizeClass);
                int firstColor = physicalColors.size();
                for (int color = 0; color < colorCount; color++) {
                    physicalColors.add(new PhysicalColor(sizeClass, color));
                    sizeClass.locationsByColor.add(null);
                }
                for (Node node : sizeClass.nodes) {
                    PhysicalColor physical = physicalColors.get(firstColor + node.color);
                    boolean reference = !((LIRKind) node.slot.getValueKind()).isValue();
                    // A reference representative makes the physical location suitable for a
                    // reference even if other virtual slots sharing its color are primitives.
                    if (physical.representative == null || reference) {
                        physical.representative = node;
                    }
                    physical.containsReference |= reference;
                    physical.referencingInstructions += node.referencingInstructions;
                }
                Colors.add(debug, colorCount);
            }
            // Reference-bearing colors are placed after primitive-only colors to keep oop-map
            // stack offsets compact even when the same physical slot also holds primitive values.
            // Later allocations have smaller final stack-pointer offsets. Within a reference
            // and size group, put frequently referenced colors later without changing alignment
            // or the reference-map ordering.
            physicalColors.sort(Comparator.comparing((PhysicalColor color) -> color.containsReference).thenComparingInt(color -> color.sizeClass.size).thenComparingLong(
                            color -> color.referencingInstructions).thenComparingInt(color -> color.index));
            for (PhysicalColor physical : physicalColors) {
                StackSlot location = builder.getFrameMap().allocateSpillSlot(physical.representative.slot.getValueKind());
                physical.sizeClass.locationsByColor.set(physical.index, location);
                StackSlotAllocatorUtil.allocatedSlots.increment(debug);
                StackSlotAllocatorUtil.virtualFramesize.add(debug, physical.sizeClass.size);
            }
            for (SizeClass sizeClass : sizeClasses) {
                for (Node node : sizeClass.nodes) {
                    StackSlot location = sizeClass.locationsByColor.get(node.color);
                    locationsBySlotId[node.slot.getId()] = StackSlot.get(node.slot.getValueKind(), location.getRawOffset(), location.getRawAddFrameSize());
                }
            }
            // Each color allocates one slot; every remaining virtual slot reuses a color.
            StackSlotAllocatorUtil.reusedSlots.add(debug, virtualCount - physicalColors.size());
        }

        /** Checks that adjacent nodes never share a color. */
        private boolean verifyColors(SizeClass sizeClass) {
            for (Node node : sizeClass.nodes) {
                if (node.neighbors != null) {
                    for (int id = node.neighbors.nextSetBit(0); id >= 0; id = node.neighbors.nextSetBit(id + 1)) {
                        assert node.color != nodes[id].color : Assertions.errorMessageContext("slot", node.slot, "neighbor", nodes[id].slot, "color", node.color);
                    }
                }
            }
            return true;
        }

        /** Allocates virtual stack-slot ranges outside the simple-slot color groups. */
        private void allocateRanges() {
            for (VirtualStackSlot slot : builder.getStackSlots()) {
                if (slot instanceof VirtualStackSlotRange range) {
                    locationsBySlotId[slot.getId()] = builder.getFrameMap().allocateStackMemory(range.getSizeInBytes(), range.getAlignmentInBytes());
                    StackSlotAllocatorUtil.allocatedSlots.increment(debug);
                    StackSlotAllocatorUtil.virtualFramesize.add(debug, range.getSizeInBytes());
                }
            }
        }

        /** Replaces virtual stack-slot operands and frame-state values with locations. */
        private void rewriteLIR() {
            ValueProcedure assign = (value, mode, flags) -> {
                if (isVirtualStackSlot(value)) {
                    VirtualStackSlot virtual = asVirtualStackSlot(value);
                    StackSlot location = locationsBySlotId[virtual.getId()];
                    GraalError.guarantee(location != null, "Missing stack slot allocation for %s", virtual);
                    return StackSlotAllocatorUtil.toStackSlot(virtual, location, mode);
                }
                return value;
            };
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                for (LIRInstruction op : lir.getLIRforBlock(block)) {
                    op.forEachValue(assign);
                    op.forEachState(assign);
                }
            }
        }
    }
}
