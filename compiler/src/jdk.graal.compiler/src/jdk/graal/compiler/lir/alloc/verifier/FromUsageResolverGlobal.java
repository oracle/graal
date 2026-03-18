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

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Resolve variables phi variables back to labels and jumps by find their first usage and handling
 * any reg allocator inserted moves back to the defining label.
 *
 * Register allocator strips us of this information that is necessary for the verification. In order
 * to avoid modifying the existing allocators, we rather try to resolve this information from other
 * instructions.
 *
 * Variables with only usage in jump instructions are marked as aliases and are resolved after their
 * successors.
 *
 * If a variable has no usage, then no location is resolved and verification continues without
 * issues.
 *
 * In the case the first usage of a label-defined variable is wrong, then JUMP instructions from
 * predecessors fail the verification - wrong register will be chosen.
 */
public class FromUsageResolverGlobal {
    /**
     * LIR of the compilation unit we are resolving for.
     */
    protected LIR lir;

    /**
     * Verifier IR.
     */
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    /**
     * Mapping of variables to the labels that defined them.
     */
    public Map<RAVariable, RAVInstruction.Op> labelMap;

    /**
     * Is this a label-defined variable?
     */
    public Map<RAVariable, Boolean> defined;

    /**
     * Was this label-defined variable reached?
     */
    public Map<RAVariable, Boolean> reached;

    /**
     * Mapping of operation to a set of variable for which this operation is a first usage.
     */
    public Map<RAVInstruction.Op, Set<RAVariable>> firstUsages;

    /**
     * Initial locations of label-defined variables to set them to when their first usage is found.
     */
    public Map<RAVariable, RAValue> initialLocations;

    /**
     * Variable and a block where it's coming from (last jump instruction), this variable is aliased
     * by the succeeding label, which needs to be resolved first, before this one can be.
     */
    class AliasPair {
        RAVariable variable;
        BasicBlock<?> block;

        AliasPair(RAVariable variable, BasicBlock<?> block) {
            this.variable = variable;
            this.block = block;
        }
    }

    /**
     * Map of variables are aliases for a list of variables used in predecessor jump instructions.
     * First the successor label variable needs to be resolved and after the predecessor labels.
     */
    private Map<RAVariable, List<AliasPair>> aliasMap;

    /**
     * Block map of their usages objects.
     */
    public BlockMap<BlockUsage> blockUsageMap;

    /**
     * Set of blocks that have no successors.
     */
    public Set<BasicBlock<?>> endBlocks;

    protected final class BlockUsage {
        private final Set<RAVariable> reached;
        private final Map<RAVariable, RAValue> locations;

        private BlockUsage() {
            this.reached = new EconomicHashSet<>();
            this.locations = new EconomicHashMap<>();
        }

        private BlockUsage(BlockUsage blockDefs) {
            this.reached = new EconomicHashSet<>(blockDefs.reached);
            this.locations = new EconomicHashMap<>(blockDefs.locations);
        }

        private BlockUsage merge(BlockUsage other) {
            var newDefs = new BlockUsage(this);
            newDefs.reached.addAll(other.reached);

            for (RAVariable variable : other.locations.keySet()) {
                var defValue = other.locations.get(variable);
                if (defValue == null) {
                    continue;
                }

                if (defValue.isIllegal() && newDefs.locations.containsKey(variable)) {
                    continue;
                }

                newDefs.locations.put(variable, defValue);
            }

            return newDefs;
        }
    }

    protected FromUsageResolverGlobal(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        this.lir = lir;
        this.blockInstructions = blockInstructions;

        this.labelMap = new EconomicHashMap<>();
        this.defined = new EconomicHashMap<>();
        this.reached = new EconomicHashMap<>();
        this.firstUsages = new EconomicHashMap<>();
        this.initialLocations = new EconomicHashMap<>();
        this.aliasMap = new EconomicHashMap<>();
        this.aliasMap = new EconomicHashMap<>();
        this.endBlocks = new EconomicHashSet<>();

        this.blockUsageMap = new BlockMap<>(lir.getControlFlowGraph());
    }

    /**
     * Resolves label variable locations by finding where they are first used. Walk back from their
     * usage to their defining label (bottom-up), handling any spills, reloads and moves along the
     * way to set the location in label back after register allocator strips this information.
     */
    public void resolvePhiFromUsage() {
        Queue<BasicBlock<?>> worklist = new ArrayDeque<>();

        this.initializeUsages();

        for (var block : endBlocks) {
            blockUsageMap.put(block, new BlockUsage());
            worklist.add(block);
        }

        while (!worklist.isEmpty()) {
            var block = worklist.remove();

            var usage = new BlockUsage(blockUsageMap.get(block));
            var instructions = blockInstructions.get(block);
            for (var instruction : instructions.reversed()) {
                switch (instruction) {
                    case RAVInstruction.LocationMove move -> handleMove(usage, move.from, move.to);
                    case RAVInstruction.Op op -> {
                        if (op.lirInstruction instanceof StandardOp.LabelOp) {
                            this.resolveLabel(usage, op, block);
                            continue;
                        }

                        if (firstUsages.containsKey(op)) {
                            var iterator = firstUsages.get(op).iterator();
                            while (iterator.hasNext()) {
                                var variable = iterator.next();
                                usage.locations.put(variable, initialLocations.get(variable));
                                usage.reached.add(variable);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }

            for (int i = 0; i < block.getPredecessorCount(); i++) {
                var pred = block.getPredecessorAt(i);

                if (this.blockUsageMap.get(pred) == null) {
                    this.blockUsageMap.put(pred, new BlockUsage(usage));
                } else {
                    var predReached = this.blockUsageMap.get(pred);
                    var newReached = predReached.merge(usage);

                    this.blockUsageMap.put(pred, newReached);
                    if (predReached.reached.size() == newReached.reached.size()) {
                        continue;
                    }
                }

                worklist.remove(pred);
                worklist.add(pred);
            }
        }
    }

    /**
     * Initialize first usages for variables, top-down in-order to collect all necessary information
     * for the resolution.
     */
    protected void initializeUsages() {
        Queue<BasicBlock<?>> worklist = new ArrayDeque<>();

        var startBlock = this.lir.getControlFlowGraph().getStartBlock();
        worklist.add(startBlock);

        // Calculate what is defined when + usages
        Set<BasicBlock<?>> visited = new EconomicHashSet<>();
        while (!worklist.isEmpty()) {
            var block = worklist.remove();
            if (visited.contains(block)) {
                continue;
            }

            visited.add(block);

            var instructions = blockInstructions.get(block);
            var label = (RAVInstruction.Op) instructions.getFirst();

            for (var i = 0; i < label.dests.count; i++) {
                if (label.dests.orig[i].isVariable()) {
                    if (label.dests.curr[i] != null) {
                        // TestCase: TruffleSafepointTest
                        // java.concurrent.ForkJoinPool
                        // some methods for this class have location kept in
                        // them after the register allocation is complete
                        // but such information should be stripped by the allocator.
                        // This information uses one register for 2 variables in a label
                        // and triggers an error in the verification
                        label.dests.curr[i] = null;
                    }

                    var variable = label.dests.orig[i].asVariable();
                    defined.put(variable, true);
                    labelMap.put(variable, label);
                }
            }

            for (var instruction : instructions) {
                if (instruction instanceof RAVInstruction.Op op) {
                    if (op.lirInstruction instanceof StandardOp.JumpOp) {
                        continue;
                    }

                    handleUsages(op.uses, op, block);
                    handleUsages(op.alive, op, block);

                    if (op.hasCompleteState()) {
                        handleUsages(op.stateValues, op, block);
                    }
                }
            }

            var jump = (RAVInstruction.Op) instructions.getLast();
            for (var i = 0; i < jump.alive.count; i++) {
                if (!jump.alive.orig[i].isVariable()) {
                    continue;
                }

                var variable = jump.alive.orig[i].asVariable();
                if (defined.containsKey(variable) && !reached.containsKey(variable)) {
                    // No usage found before this jump
                    var succ = block.getSuccessorAt(0);
                    var succLabel = (RAVInstruction.Op) blockInstructions.get(succ).getFirst();
                    var alias = succLabel.dests.orig[i].asVariable();
                    if (alias.equals(variable)) {
                        continue; // Loop
                    }

                    var aliasedVariables = aliasMap.getOrDefault(alias, new ArrayList<>());
                    aliasedVariables.add(new AliasPair(variable, block));

                    aliasMap.put(alias, aliasedVariables);
                }
            }

            if (block.getSuccessorCount() == 0) {
                endBlocks.add(block);
                continue;
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);

                worklist.remove(succ);
                worklist.add(succ);
            }
        }
    }

    /**
     * Find first usages for variables defined in labels.
     *
     * @param values Values of this instruction where are looking for usage
     * @param op Instruction that holds values
     * @param block Block where this instruction is in
     */
    protected void handleUsages(RAVInstruction.ValueArrayPair values, RAVInstruction.Op op, BasicBlock<?> block) {
        for (var i = 0; i < values.count; i++) {
            if (!values.orig[i].isVariable()) {
                continue;
            }

            var variable = values.orig[i].asVariable();
            if (defined.containsKey(variable) && !reached.containsKey(variable)) {
                // Defined - variable comes from label
                // Reached does not contain variable - there's no other first usage.
                reached.put(variable, false);

                if (!firstUsages.containsKey(op)) {
                    firstUsages.put(op, new EconomicHashSet<>());
                }

                firstUsages.get(op).add(variable);
                initialLocations.put(variable, values.curr[i]);

                for (var entry : aliasMap.entrySet()) {
                    var aliasedVariables = entry.getValue();

                    aliasedVariables.removeIf(pair -> pair.variable.equals(variable));
                }
            }
        }
    }

    /**
     * Handle a register allocator inserted move, change locations of variables based on the
     * locations.
     *
     * If a variable is in location reg1 and a move is found <code>reg1 = MOVE reg2</code>, then
     * said variable will now be in <code>reg2</code>, because reg1 will now have different content
     * when walking through the instructions in reverse.
     *
     * @param usage Variable locations for this block
     * @param from Source location
     * @param to Destination location
     */
    protected void handleMove(BlockUsage usage, RAValue from, RAValue to) {
        var updatedVariables = new EconomicHashMap<RAVariable, RAValue>();
        for (var entry : usage.locations.entrySet()) {
            var variable = entry.getKey();
            var location = entry.getValue();
            if (location == null || location.isIllegal()) {
                continue;
            }

            if (location.equals(to) && usage.reached.contains(variable)) {
                updatedVariables.put(variable, from);
            }
        }

        usage.locations.putAll(updatedVariables);
    }

    /**
     * Resolve locations for all variables in a label, also mark first usage for aliased variables -
     * variables used in predecessors that have no other usage.
     *
     * @param usage usage for the block we are resolving, contains the locations
     * @param label label we are resolving
     * @param block block of the label
     */
    protected void resolveLabel(BlockUsage usage, RAVInstruction.Op label, BasicBlock<?> block) {
        for (int i = 0; i < label.dests.count; i++) {
            if (!label.dests.orig[i].isVariable()) {
                continue;
            }

            var variable = label.dests.orig[i].asVariable();
            if (usage.locations.get(variable) == null) {
                continue; // Not resolved yet
            }

            if (label.dests.curr[i] != null) {
                continue; // Already resolved
            }

            var location = usage.locations.get(variable);
            if (location == null || location.isIllegal()) {
                GraalError.shouldNotReachHere("Location is " + location + " when resolving " + variable + " should not happen.");
            }

            label.dests.curr[i] = location;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();

                jump.alive.curr[i] = location; // Set predecessor location
            }

            // Variables that are passed into jumps without any other usage are resolved
            // after variable, it's alias, in successor label.
            if (aliasMap.containsKey(variable)) {
                var aliasedVariables = aliasMap.get(variable);
                for (var aliasPair : aliasedVariables) {
                    if (blockUsageMap.get(aliasPair.block) == null) {
                        this.blockUsageMap.put(aliasPair.block, new BlockUsage());
                    }

                    var aliasBlockUsage = blockUsageMap.get(aliasPair.block);
                    aliasBlockUsage.locations.put(aliasPair.variable, location);
                    aliasBlockUsage.reached.add(aliasPair.variable);
                }
            }

            usage.locations.put(variable, null);
        }
    }
}
