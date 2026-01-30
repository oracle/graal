package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LabelConflictResolver implements ConflictResolver {
    protected LIR lir;
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    protected Map<Variable, RAVInstruction.Op> labelMap;
    protected Map<Variable, Set<Value>> rules;
    protected Map<Variable, Set<Value>> expandedRules;

    public LabelConflictResolver() {
        this.labelMap = new HashMap<>();
        this.rules = new HashMap<>();
        this.expandedRules = new HashMap<>();
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
                if (!LIRValueUtil.isVariable(labelInstr.dests.orig[i])) {
                    continue;
                }

                var labelVariable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);
                labelMap.put(labelVariable, labelInstr);
            }
        }
    }

    protected void buildRules() {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (int i = 0; i < labelInstr.dests.count; i++) {
                if (!LIRValueUtil.isVariable(labelInstr.dests.orig[i])) {
                    continue;
                }

                var labelVariable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);
                Set<Value> resolutions = new HashSet<>();

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
            var variable = LIRValueUtil.asVariable(entry.getKey());

            ArrayList<Value> sources = new ArrayList<>(entry.getValue());
            int sourceCount = sources.size();
            for (int i = 0; i < sourceCount; i++) {
                var source = sources.get(i);
                if (!LIRValueUtil.isVariable(source)) {
                    continue;
                }

                var sourceVariable = LIRValueUtil.asVariable(source);
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

            expandedRules.put(variable, new HashSet<>(sources));
        }
    }

    protected boolean isSubsetOfValues(Set<Value> superset, Set<ValueAllocationState> subset) {
        for (var v : subset) {
            if (!superset.contains(v.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ValueAllocationState resolveConflictedState(Variable target, ConflictedAllocationState conflictedState, Value location) {
        if (!this.expandedRules.containsKey(target)) {
            return null;
        }

        var ruleSet = this.expandedRules.get(target);
        var confStates = conflictedState.getConflictedStates();

        if (!this.isSubsetOfValues(ruleSet, confStates)) {
            return null;
        }

        return new ValueAllocationState(target, labelMap.get(target).getLIRInstruction());
    }

    @Override
    public ValueAllocationState resolveValueState(Variable target, ValueAllocationState valueState, Value location) {
        if (!this.expandedRules.containsKey(target)) {
            return null;
        }

        var ruleSet = this.expandedRules.get(target);
        if (!ruleSet.contains(valueState.getValue())) {
            return null;
        }

        return new ValueAllocationState(target, labelMap.get(target).getLIRInstruction());
    }
}
