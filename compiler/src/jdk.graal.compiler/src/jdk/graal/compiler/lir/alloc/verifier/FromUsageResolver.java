package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class FromUsageResolver {
    protected LIR lir;
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    public Map<Variable, RAVInstruction.Op> labelMap;
    public Map<Variable, Value> variableRegisterMap;
    public Map<Variable, Variable> usageAliasMap;

    class PathEntry {
        public BasicBlock<?> block;
        public PathEntry previous;

        PathEntry(BasicBlock<?> block) {
            this.block = block;
            this.previous = null;
        }

        PathEntry(BasicBlock<?> block, PathEntry previous) {
            this.block = block;
            this.previous = previous;
        }

        public PathEntry next(BasicBlock<?> block) {
            return new PathEntry(block, this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (o instanceof PathEntry le && le.block.equals(this.block)) {
                if (le.previous == null) {
                    return this.previous == null;
                }

                return le.previous.block.equals(this.previous.block);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.previous == null ? block.hashCode() : block.hashCode() ^ previous.block.hashCode();
        }

        @Override
        public String toString() {
            return (previous == null ? "" : previous.toString()) + " -> " + block.toString();
        }
    }

    protected FromUsageResolver(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        this.lir = lir;
        this.blockInstructions = blockInstructions;

        this.labelMap = new EconomicHashMap<>();
        this.variableRegisterMap = new EconomicHashMap<>();
        this.usageAliasMap = new EconomicHashMap<>();
    }

    private Value getLocationFromUsage(PathEntry path, BasicBlock<?> defBlock, RAVInstruction.Base usageInstruction, Value location, Variable variable) {
        boolean reachedUsage = false;
        while (true) { // TERMINATION ARGUMENT: Iterates over linked list using PathEntry nodes
            // Either we get an assertion about path being null or reaching def block - which always happens
            assert path != null;

            var block = path.block;
            var instructions = this.blockInstructions.get(block).reversed();
            for (var instruction : instructions) {
                if (instruction == usageInstruction) {
                    reachedUsage = true;
                    continue;
                }

                if (!reachedUsage) {
                    continue;
                }

                // Tracking the value bottom up, from the usage up to the label definition
                // looking for any changes to the target register that could highlight
                // different register is supposed to be used, in case of reload/spill combo or
                // register move. If we are wrong about the target register then, it will
                // get thrown out in the verification stage.
                switch (instruction) {
                    case RAVInstruction.VirtualMove move -> {
                        if (move.location.equals(location) && !move.variableOrConstant.equals(variable)) {
                            throw new TargetLocationOverwrittenException(move, block);
                        }
                    }
                    case RAVInstruction.Move move -> {
                        if (move.to.equals(location)) {
                            location = move.from;
                        }
                    }
                    // For Op, if there is a redefinition, we let the later stages handle that
                    default -> {
                    }
                }
            }

            if (path.block.equals(defBlock)) {
                break;
            }

            path = path.previous;
        }

        return location;
    }

    private void mapLabelVariableFromUsage(
            // @formatter:off
            Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap,
            Variable variable, Value location,
            PathEntry path, RAVInstruction.Base useInstruction) {
            // @formatter:on
        var variableLabelInstr = this.labelMap.get(variable);
        if (variableLabelInstr == null) {
            return;
        }

        var labelBlock = labelToBlockMap.get(variableLabelInstr);
        var register = this.getLocationFromUsage(path, labelBlock, useInstruction, location, variable);

        this.variableRegisterMap.put(variable, register);
        this.labelMap.remove(variable);
        this.usageAliasMap.remove(variable);

        for (int j = 0; j < variableLabelInstr.dests.count; j++) {
            if (variableLabelInstr.dests.orig[j].equals(variable)) {
                variableLabelInstr.dests.curr[j] = register;

                // Need to iterate over predecessors and fill jumps as well
                for (int k = 0; k < labelBlock.getPredecessorCount(); k++) {
                    var pred = labelBlock.getPredecessorAt(k);
                    var jumpOp = (RAVInstruction.Op) this.blockInstructions.get(pred).getLast();
                    jumpOp.alive.curr[j] = register;
                }
            }
        }

        for (var aliasEntry : this.usageAliasMap.entrySet()) {
            var originalVariable = LIRValueUtil.asVariable(aliasEntry.getValue());
            if (!originalVariable.equals(variable)) {
                continue;
            }

            var aliasVariable = LIRValueUtil.asVariable(aliasEntry.getKey());
            var aliasLabelInstr = this.labelMap.get(aliasVariable);
            if (aliasLabelInstr == null) {
                continue;
            }

            this.labelMap.remove(aliasVariable);

            var aliasLabelBlock = labelToBlockMap.get(aliasLabelInstr);
            for (int j = 0; j < aliasLabelInstr.dests.count; j++) {
                if (aliasLabelInstr.dests.orig[j].equals(aliasVariable)) {
                    aliasLabelInstr.dests.curr[j] = register;

                    // Need to iterate over predecessors and fill jumps as well
                    for (int k = 0; k < aliasLabelBlock.getPredecessorCount(); k++) {
                        var pred = aliasLabelBlock.getPredecessorAt(k);
                        var jumpOp = (RAVInstruction.Op) this.blockInstructions.get(pred).getLast();
                        jumpOp.alive.curr[j] = register;
                    }
                }
            }
        }
    }

    private void tryMappingVariable(
            // @formatter:off
            Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap, Value variable,
            Value location, PathEntry path, RAVInstruction.Base useInstruction
            // @formatter:on
    ) {
        if (!LIRValueUtil.isVariable(variable)) {
            return;
        }

        if (LIRValueUtil.isVariable(location) || location instanceof ConstantValue) {
            return;
        }

        this.mapLabelVariableFromUsage(labelToBlockMap, LIRValueUtil.asVariable(variable), location, path, useInstruction);
    }

    /**
     * Resolves label variable registers by finding where they are used.
     */
    public void resolvePhiFromUsage() {
        Queue<PathEntry> worklist = new LinkedList<>();
        worklist.add(new PathEntry(this.lir.getControlFlowGraph().getStartBlock()));

        Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap = new EconomicHashMap<>();

        Set<PathEntry> visited = new EconomicHashSet<>(); // Ignore already visited combinations.
        while (!worklist.isEmpty()) {
            var entry = worklist.poll();
            if (visited.contains(entry)) {
                continue;
            }
            visited.add(entry);

            var block = entry.block;
            var instructions = this.blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();
            for (int i = 0; i < labelInstr.dests.count; i++) {
                if (labelInstr.dests.curr[i] == null) {
                    Variable variable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);
                    this.labelMap.put(variable, labelInstr);
                    labelToBlockMap.put(labelInstr, block);
                }
            }

            for (var instruction : instructions) {
                switch (instruction) {
                    case RAVInstruction.VirtualMove move -> {
                        this.tryMappingVariable(labelToBlockMap, move.variableOrConstant, move.location, entry, instruction);
                    }
                    case RAVInstruction.Op op -> {
                        if (instruction.lirInstruction instanceof StandardOp.JumpOp) {
                            // Always only one successor for this jump op
                            // Assumption here is, that we resolve aliases with original registers immediately
                            // so in-case an alias was defined after that happened, it's not resolved and will fail.
                            var label = (RAVInstruction.Op) this.blockInstructions.get(block.getSuccessorAt(0)).getFirst();
                            for (int i = 0; i < op.alive.count; i++) {
                                if (!LIRValueUtil.isVariable(op.alive.orig[i])) {
                                    continue;
                                }

                                var variable = LIRValueUtil.asVariable(op.alive.orig[i]);
                                if (this.labelMap.get(variable) != null) {
                                    this.usageAliasMap.put(variable, LIRValueUtil.asVariable(label.dests.orig[i]));
                                }
                            }

                            continue;
                        }

                        for (int i = 0; i < op.uses.count; i++) {
                            this.tryMappingVariable(labelToBlockMap, op.uses.orig[i], op.uses.curr[i], entry, instruction);
                        }

                        for (int i = 0; i < op.alive.count; i++) {
                            this.tryMappingVariable(labelToBlockMap, op.alive.orig[i], op.alive.curr[i], entry, instruction);
                        }
                    }
                    default -> {
                    }
                }
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);
                worklist.add(entry.next(succ));
            }
        }
    }
}
