package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Resolve label variable locations based on their first usage,
 * globally - spanning over all program blocks for every variable.
 */
public class FromUsageResolverGlobal {
    protected LIR lir;
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    public Map<RAVariable, RAVInstruction.Op> labelMap;
    public Map<RAVariable, Boolean> defined;
    public Map<RAVariable, Boolean> reached;
    public Map<RAVInstruction.Op, Set<RAVariable>> firstUsages;
    public Map<RAVariable, RAValue> initialLocations;
    public Map<RAVariable, RAVariable> aliasMap;
    public Map<RAVariable, BasicBlock<?>> aliasBlockMap;
    public BlockMap<BlockUsage> blockUsageMap; // Entry blocks!
    public List<BasicBlock<?>> endBlocks;

    final class BlockUsage {
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
            for (var defValue : other.reached) {
                newDefs.reached.add(defValue);
            }

            var iterator = other.locations.keySet().iterator();
            while (iterator.hasNext()) {
                var variable = iterator.next();
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
        this.aliasBlockMap = new EconomicHashMap<>();
        this.endBlocks = new ArrayList<>();

        this.blockUsageMap = new BlockMap<>(lir.getControlFlowGraph());
    }

    /**
     * Resolves label variable registers by finding where they are used.
     */
    public void resolvePhiFromUsage() {
        Queue<BasicBlock<?>> worklist = new LinkedList<>();

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

    protected void initializeUsages() {
        Queue<BasicBlock<?>> worklist = new LinkedList<>();

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
                    handleUsages(op.stateValues, op, block);
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

                    aliasBlockMap.put(variable, block);
                    aliasMap.put(variable, succLabel.dests.orig[i].asVariable());
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

                aliasMap.remove(variable);
            }
        }
    }

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

    protected void resolveLabel(BlockUsage usage, RAVInstruction.Op label, BasicBlock<?> block) {
        for (int i = 0; i < label.dests.count; i++) {
            if (!label.dests.orig[i].isVariable()) {
                continue;
            }

            var variable = label.dests.orig[i].asVariable();
            if (usage.locations.get(variable) == null) {
                continue;
            }

            if (label.dests.curr[i] != null) {
                continue;
            }

            var location = usage.locations.get(variable);
            if (location == null || location.isIllegal()) {
                GraalError.shouldNotReachHere("Location is " + location + " when resolving " + variable + " should not happen.");
            }

            label.dests.curr[i] = location;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();

                jump.alive.curr[i] = location;
            }

            // Variables that are passed into jumps without any other usage are aliases
            // for same variable in successor label, whenever said variable is resolved
            // we now have a location for this variable and can take other moves into account.
            for (var entry : aliasMap.entrySet()) {
                var aliased = entry.getValue();
                if (variable.equals(aliased)) {
                    var alias = entry.getKey();
                    var aliasBlock = aliasBlockMap.get(alias);

                    if (blockUsageMap.get(aliasBlock) == null) {
                        this.blockUsageMap.put(aliasBlock, new BlockUsage());
                    }

                    var aliasBlockUsage = blockUsageMap.get(aliasBlock);
                    aliasBlockUsage.locations.put(alias, location);
                    aliasBlockUsage.reached.add(alias);
                }
            }

            usage.locations.put(variable, null);
        }
    }
}
