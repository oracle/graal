package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Resolve label variable locations based on their first usage,
 * globally - spanning over all program blocks for every variable.
 */
class FromUsageResolverGlobal {
    protected LIR lir;
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    public Map<Variable, RAVInstruction.Op> labelMap;
    public Map<Variable, Boolean> defined;
    public Map<Variable, Boolean> reached;
    public Map<RAVInstruction.Op, Set<Variable>> firstUsages;
    public Map<Variable, Value> initialLocations;
    public Map<Variable, Variable> aliasMap;
    public Map<Variable, BasicBlock<?>> aliasBlockMap;
    public BlockMap<BlockUsage> blockUsageMap; // Entry blocks!
    public List<BasicBlock<?>> endBlocks;

    class BlockUsage {
        private final DefinitionSet reached;
        private final Map<Variable, Value> locations;

        private BlockUsage() {
            this.reached = new DefinitionSet();
            this.locations = new HashMap<>();
        }

        private BlockUsage(BlockUsage blockDefs) {
            this.reached = new DefinitionSet(blockDefs.reached);
            this.locations = new HashMap<>(blockDefs.locations);
        }

        private BlockUsage merge(BlockUsage other) {
            var newDefs = new BlockUsage(this);
            for (var defString : other.reached.internalSet) {
                var defValue = other.reached.valueMap.get(defString);
                newDefs.reached.add(defValue);
            }

            var iterator = other.locations.keySet().iterator();
            while (iterator.hasNext()) {
                var variable = LIRValueUtil.asVariable(iterator.next());
                var defValue = other.locations.get(variable);

                if (Value.ILLEGAL.equals(defValue) && newDefs.locations.containsKey(variable)) {
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

        this.labelMap = new HashMap<>();
        this.defined = new HashMap<>();
        this.reached = new HashMap<>();
        this.firstUsages = new HashMap<>();
        this.initialLocations = new HashMap<>();
        this.aliasMap = new HashMap<>();
        this.aliasBlockMap = new HashMap<>();
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
                    case RAVInstruction.VirtualMove ignored -> {}
                    case RAVInstruction.Move move -> handleMove(usage, move.from, move.to);
                    case RAVInstruction.Op op -> {
                        if (op.lirInstruction instanceof StandardOp.LabelOp) {
                            this.resolveLabel(usage, op, block);
                            continue;
                        }
                        // TODO: decide how to deal with locations being overwritten.

                        if (firstUsages.containsKey(op)) {
                            var iterator = firstUsages.get(op).iterator();
                            while (iterator.hasNext()) {
                                var variable = LIRValueUtil.asVariable(iterator.next());
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
                    if (predReached.reached.internalSet.size() == newReached.reached.internalSet.size()) {
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
        Set<BasicBlock<?>> visited = new HashSet<>();
        while (!worklist.isEmpty()) {
            var block = worklist.remove();
            if (visited.contains(block)) {
                continue;
            }

            visited.add(block);

            var instructions = blockInstructions.get(block);
            var label = (RAVInstruction.Op) instructions.getFirst();

            for (var i = 0; i < label.dests.count; i++) {
                if (LIRValueUtil.isVariable(label.dests.orig[i])) {
                    var variable = LIRValueUtil.asVariable(label.dests.orig[i]);
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
                if (!LIRValueUtil.isVariable(jump.alive.orig[i])) {
                    continue;
                }

                var variable = LIRValueUtil.asVariable(jump.alive.orig[i]);
                if (defined.containsKey(variable) && !reached.containsKey(variable)) {
                    // No usage found before this jump
                    var succ = block.getSuccessorAt(0);
                    var succLabel = (RAVInstruction.Op) blockInstructions.get(succ).getFirst();

                    aliasBlockMap.put(variable, block);
                    aliasMap.put(variable, LIRValueUtil.asVariable(succLabel.dests.orig[i]));
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
            if (!LIRValueUtil.isVariable(values.orig[i])) {
                continue;
            }

            var variable = LIRValueUtil.asVariable(values.orig[i]);
            if (defined.containsKey(variable) && !reached.containsKey(variable)) {
                // Defined - variable comes from label
                // Reached does not contain variable - there's no other first usage.
                reached.put(variable, false);

                if (!firstUsages.containsKey(op)) {
                    firstUsages.put(op, new HashSet<>());
                }

                firstUsages.get(op).add(variable);
                initialLocations.put(variable, values.curr[i]);

                aliasMap.remove(variable);
            }
        }
    }

    protected void handleMove(BlockUsage usage, Value from, Value to) {
        for (var entry : usage.locations.entrySet()) {
            var variable = LIRValueUtil.asVariable(entry.getKey());
            var location = entry.getValue();
            if (location == null || Value.ILLEGAL.equals(location)) {
                continue;
            }

            if (location.equals(to) && usage.reached.contains(variable)) {
                usage.locations.put(variable, from);
            }
        }
    }

    protected void resolveLabel(BlockUsage usage, RAVInstruction.Op label, BasicBlock<?> block) {
        for (int i = 0; i < label.dests.count; i++) {
            if (!LIRValueUtil.isVariable(label.dests.orig[i])) {
                continue;
            }

            var variable = LIRValueUtil.asVariable(label.dests.orig[i]);
            if (usage.locations.get(variable) == null) {
                continue;
            }

            if (label.dests.curr[i] != null) {
                continue;
            }

            var location = usage.locations.get(variable);
            if (location == null || Value.ILLEGAL.equals(location)) {
                throw new IllegalStateException();
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
                var aliased = LIRValueUtil.asVariable(entry.getValue());
                if (variable.equals(aliased)) {
                    var alias = LIRValueUtil.asVariable(entry.getKey());
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
