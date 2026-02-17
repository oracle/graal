package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LabelConflictResolver implements ConflictResolver {
    protected LIR lir;
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    protected Map<RAVariable, BasicBlock<?>> blockMap;
    protected Map<RAVariable, RAVInstruction.Op> labelMap;
    protected Map<RAVariable, Set<RAValue>> rules;
    protected Map<RAVariable, Set<RAValue>> expandedRules;

    public LabelConflictResolver() {
        this.blockMap = new EconomicHashMap<>();
        this.labelMap = new EconomicHashMap<>();
        this.rules = new EconomicHashMap<>();
        this.expandedRules = new EconomicHashMap<>();
    }

    @Override
    public void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        this.lir = lir;
        this.blockInstructions = blockInstructions;

        this.buildLabelMap();
        this.buildRules();
        this.expandRules();
    }

    protected void buildLabelMap() {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (int i = 0; i < labelInstr.dests.count; i++) {
                if (!labelInstr.dests.orig[i].isVariable()) {
                    continue;
                }

                var labelVariable = labelInstr.dests.orig[i];
                labelMap.put(labelVariable.asVariable(), labelInstr);
                blockMap.put(labelVariable.asVariable(), block);
            }
        }
    }

    protected void buildRules() {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (int i = 0; i < labelInstr.dests.count; i++) {
                if (!labelInstr.dests.orig[i].isVariable()) {
                    continue;
                }

                var labelVariable = labelInstr.dests.orig[i].asVariable();
                Set<RAValue> resolutions = new EconomicHashSet<>();

                for (int j = 0; j < block.getPredecessorCount(); j++) {
                    var pred = block.getPredecessorAt(j);
                    var predInstructions = blockInstructions.get(pred);
                    var jumpInstr = (RAVInstruction.Op) predInstructions.getLast();

                    resolutions.add(jumpInstr.alive.orig[i]);
                }

                rules.put(labelVariable, resolutions);
            }
        }
    }

    protected void expandRules() {
        for (var entry : rules.entrySet()) {
            var variable = entry.getKey();

            ArrayList<RAValue> sources = new ArrayList<>(entry.getValue());
            int sourceCount = sources.size();
            for (int i = 0; i < sourceCount; i++) {
                var source = sources.get(i);
                if (!source.isVariable()) {
                    continue;
                }

                var sourceVariable = source.asVariable();
                if (rules.containsKey(sourceVariable)) {
                    for (var newSource : rules.get(sourceVariable)) {
                        if (sources.contains(newSource)) {
                            continue;
                        }

                        sources.add(newSource);
                        sourceCount++;
                    }
                }
            }

            expandedRules.put(variable, new EconomicHashSet<>(sources));
        }
    }

    protected boolean isSubsetOfValues(Set<RAValue> superset, Set<ValueAllocationState> subset) {
        for (var v : subset) {
            if (!superset.contains(v.getRAValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location) {
        if (!this.expandedRules.containsKey(target)) {
            return null;
        }

        var ruleSet = this.expandedRules.get(target);
        var confStates = conflictedState.getConflictedStates();

        if (!this.isSubsetOfValues(ruleSet, confStates)) {
            return null;
        }

        return new ValueAllocationState(target, labelMap.get(target), blockMap.get(target));
    }

    @Override
    public ValueAllocationState resolveValueState(RAVariable target, ValueAllocationState valueState, RAValue location) {
        if (!this.expandedRules.containsKey(target)) {
            return null;
        }

        var ruleSet = this.expandedRules.get(target);
        if (!ruleSet.contains(valueState.getRAValue())) {
            return null;
        }

        return new ValueAllocationState(target, labelMap.get(target), blockMap.get(target));
    }
}
