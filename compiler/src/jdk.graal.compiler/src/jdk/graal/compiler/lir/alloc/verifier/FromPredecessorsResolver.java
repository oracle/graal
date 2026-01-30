package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class FromPredecessorsResolver {
    public LIR lir;
    public BlockMap<List<RAVInstruction.Base>> blockInstructions;
    public BlockMap<MergedBlockVerifierState> blockStates; // Current states
    public BlockMap<MergedBlockVerifierState> blockEntryStates; // State on entry to block

    public FromPredecessorsResolver(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, BlockMap<MergedBlockVerifierState> blockStates, BlockMap<MergedBlockVerifierState> blockEntryStates) {
        this.lir = lir;
        this.blockInstructions = blockInstructions;
        this.blockStates = blockStates;
        this.blockEntryStates = blockEntryStates;
    }

    /**
     * Fill in missing variable locations for current block's label instruction and predecessor
     * jump instructions.
     * <p>
     * We are looking for intersection between locations of individual processors, this should
     * give us a single register that is used for the phi, and is necessary for jump to verify
     * that contents are alive that that point and for label to define where the result of phi
     * will be held to used in said block.
     *
     * @param block      Block that needs phi function output
     * @param labelInstr Label instruction of said block
     */
    public boolean resolvePhiFromPredecessors(BasicBlock<?> block, RAVInstruction.Op labelInstr) {
        for (int i = 0; i < labelInstr.dests.count; i++) {
            Set<Value> locations = null;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var state = this.blockStates.get(pred);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                var inputValue = jump.alive.orig[i];

                var varLoc = state.values.getValueLocations(inputValue);
                if (locations == null) {
                    locations = varLoc;
                    continue;
                }

                locations.retainAll(varLoc);
            }

            Value location = null;
            if (locations.size() != 1) {
                if (locations.isEmpty()) {
                    return false;
                }

                for (int j = 0; j < block.getPredecessorCount(); j++) {
                    int time = -1;
                    Value blockReg = null;
                    for (var loc : locations) {
                        var pred = block.getPredecessorAt(j);
                        var state = this.blockStates.get(pred);

                        var regTime = state.values.getKeyTime(loc);
                        if (regTime > time) {
                            // TODO: replace time with priority of Moves inserted by the Register Allocator.
                            time = regTime; // Max time
                            blockReg = loc;
                        }
                    }

                    if (location == null) {
                        location = blockReg;
                    } else if (!location.equals(blockReg)) {
                        // Not same for all blocks, so none choosen.
                        return false;
                    }
                }
            } else {
                location = locations.stream().findFirst().get();
            }

            var registerValue = location;
            // var registerValue = location.asValue(labelInstr.dests.orig[i].getValueKind());

            labelInstr.dests.curr[i] = registerValue;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                jump.alive.curr[i] = registerValue;
            }
        }

        return true;
    }

    class VariableLocations implements Iterable<Value> {
        protected Set<String> internalList;
        protected Map<String, Value> valueMap;

        public VariableLocations() {
            this.internalList = new HashSet<>();
            this.valueMap = new HashMap<>();
        }

        public VariableLocations(VariableLocations other) {
            this.internalList = new HashSet<>(other.internalList);
            this.valueMap = new HashMap<>(other.valueMap);
        }

        public void add(Value location) {
            var locString = getValueKeyString(location);
            internalList.add(locString);
            valueMap.put(locString, location);
        }

        public void remove(Value location) {
            var locString = getValueKeyString(location);
            internalList.remove(locString);
            valueMap.remove(locString);
        }

        public boolean isEmpty() {
            return internalList.isEmpty();
        }

        public boolean contains(Value location) {
            var locString = getValueKeyString(location);
            return valueMap.containsKey(locString);
        }

        protected String getValueKeyString(Value value) {
            if (value instanceof RegisterValue regValue) {
                return regValue.getRegister().toString();
            }

            return value.toString();
        }

        @Override
        public Iterator<Value> iterator() {
            return internalList.stream().map(valueMap::get).iterator();
        }
    }

    public void propagateLabelChangeFromPredecessors(BasicBlock<?> defBlock) {
        var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(defBlock).getFirst();

        // Definition block needs to have this set.
        var propagateMap = new HashMap<BasicBlock<?>, List<Variable>>();
        var locationMap = new HashMap<BasicBlock<?>, Map<Variable, VariableLocations>>();

        var defVariableToLocations = new HashMap<Variable, VariableLocations>();
        var defBlockVariablesToPropagate = new LinkedList<Variable>();
        for (int i = 0; i < labelInstr.dests.count; i++) {
            var register = labelInstr.dests.curr[i];
            var variable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);

            defBlockVariablesToPropagate.add(variable);

            var variableLocationList = new VariableLocations();
            variableLocationList.add(register);
            defVariableToLocations.put(variable, variableLocationList);
        }

        Queue<BasicBlock<?>> worklist = new LinkedList<>();
        Set<BasicBlock<?>> processed = new HashSet<>();
        worklist.add(defBlock);
        propagateMap.put(defBlock, defBlockVariablesToPropagate);
        locationMap.put(defBlock, defVariableToLocations);

        while (!worklist.isEmpty()) {
            var curr = worklist.remove();
            if (processed.contains(curr)) {
                continue;
            }
            processed.add(curr);

            var state = this.blockStates.get(curr);
            var variablesToPropagate = propagateMap.get(curr);
            var variableToLocations = locationMap.get(curr);

            var instructions = blockInstructions.get(curr);
            for (var instruction : instructions) {
                if (curr.equals(defBlock) && instruction.lirInstruction instanceof StandardOp.LabelOp) {
                    continue;
                }

                Value fromLocation;
                Value toLocation;

                switch (instruction) {
                    case RAVInstruction.Move move -> {
                        fromLocation = move.from;
                        toLocation = move.to;
                    }
                    case RAVInstruction.Spill spill -> {
                        fromLocation = spill.from;
                        toLocation = spill.to;
                    }
                    case RAVInstruction.StackMove stack -> {
                        fromLocation = stack.from;
                        toLocation = stack.to;
                    }
                    case RAVInstruction.Reload reload -> {
                        fromLocation = reload.from;
                        toLocation = reload.to;
                    }
                    case RAVInstruction.VirtualMove virtMove -> {
                        toLocation = virtMove.location;
                        fromLocation = null;
                    }
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.dests.count; i++) {
                            var location = op.dests.curr[i];
                            if (location == null) {
                                continue;
                            }

                            var itToPropagate = variablesToPropagate.iterator();
                            while (itToPropagate.hasNext()) {
                                var variable = LIRValueUtil.asVariable(itToPropagate.next());
                                var locations = variableToLocations.get(variable);
                                locations.remove(location);
                            }
                        }

                        continue;
                    }
                    default -> {
                        continue;
                    }
                }

                var itToPropagate = variablesToPropagate.iterator();
                while (itToPropagate.hasNext()) {
                    var variable = LIRValueUtil.asVariable(itToPropagate.next());
                    var locations = variableToLocations.get(variable);
                    if (fromLocation != null && locations.contains(fromLocation)) {
                        locations.add(toLocation);
                    } else if (locations.contains(toLocation)) {
                        locations.remove(toLocation); // Overwritten
                    }
                }
            }

            var variablesToBePropagated = new LinkedList<Variable>();
            var iterator = variablesToPropagate.iterator();
            while (iterator.hasNext()) {
                var variable = LIRValueUtil.asVariable(iterator.next());
                var locations = variableToLocations.get(variable);
                if (locations.isEmpty()) {
                    continue;
                }

                variablesToBePropagated.add(variable);
                for (var location : locations) {
                    if (state != null) {
                        state.values.put(location, new ValueAllocationState(variable, labelInstr.lirInstruction));
                    }
                }
            }

            if (variablesToBePropagated.isEmpty()) {
                continue;
            }

            for (int i = 0; i < curr.getSuccessorCount(); i++) {
                var succ = curr.getSuccessorAt(i);
                var succEntryState = this.blockEntryStates.get(succ);
                if (succEntryState == null) {
                    continue;
                }

                if (succ.equals(defBlock)) {
                    // This means that the definition block would have same value as predecessor
                    // for example: B0 defines [v0] in label, B1 is it's successor as well as it's
                    // predecessor, and if it does not overwrite this register, it would change
                    // entry state for B0 to include v0, which is defined by B0.

                    for (int j = 0; j < labelInstr.dests.count; j++) {
                        var variable = LIRValueUtil.asVariable(labelInstr.dests.orig[j]);

                        if (!variablesToPropagate.contains(variable)) {
                            continue;
                        }

                        var location = labelInstr.dests.curr[j];
                        var locations = variableToLocations.get(variable);
                        if (locations.contains(location)) {
                            // Only throw this error if location in label was not overwritten.
                            throw new CircularDefinitionError(defBlock, curr, labelInstr, variablesToBePropagated);
                        }
                    }

                    continue;
                }

                boolean dominates = succ.dominates(defBlock);
                if (dominates) {
                    continue;
                }

                Map<Variable, VariableLocations> newLoc = new HashMap<>();
                var itToBePropagated = variablesToBePropagated.iterator();
                while (itToBePropagated.hasNext()) {
                    var variable = LIRValueUtil.asVariable(itToBePropagated.next());
                    var locations = variableToLocations.get(variable);
                    for (var location : locations) {
                        succEntryState.values.put(location, new ValueAllocationState(variable, labelInstr.lirInstruction));
                    }

                    newLoc.put(variable, new VariableLocations(locations));
                }

                locationMap.put(succ, newLoc);
                propagateMap.put(succ, variablesToBePropagated);
                worklist.add(succ);
            }
        }
    }
}
