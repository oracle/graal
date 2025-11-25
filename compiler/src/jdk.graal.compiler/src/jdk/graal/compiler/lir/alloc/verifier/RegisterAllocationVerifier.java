package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class RegisterAllocationVerifier {
    public BlockMap<List<RAVInstruction.Base>> blockInstructions;
    public BlockMap<VerifierState> blockStates; // Current states
    public BlockMap<VerifierState> blockEntryStates; // State on entry to block
    public LIR lir;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        this.lir = lir;

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);
        this.blockEntryStates.put(this.lir.getControlFlowGraph().getStartBlock(), new VerifierState());

        this.blockStates = new BlockMap<>(cfg);
    }

    private boolean doPrecessorsHaveStates(BasicBlock<?> block) {
        for (int i = 0; i < block.getPredecessorCount(); i++) {
            var pred = block.getPredecessorAt(i);
            if (this.blockStates.get(pred) == null) {
                return false;
            }
        }
        return true;
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
     * @param block Block that needs phi function output
     * @param labelInstr Label instruction of said block
     */
    private void fillMissingVariableLocationsForPhi(BasicBlock<?> block, RAVInstruction.Op labelInstr) {
        for (int i = 0; i < labelInstr.dests.count; i++) {
            Set<Value> locations = null;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var state = this.blockStates.get(pred);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                var inputVariable = (Variable) jump.alive.orig[i];

                var varLoc = state.registerValues.getVariableLocations(inputVariable);
                if (locations == null) {
                    locations = varLoc;
                    continue;
                }

                locations.retainAll(varLoc);
            }

            if (locations.size() != 1) {
                throw new GraalError("Multiple locations for block " + block);
            }

            Value location = locations.stream().findFirst().get();
            labelInstr.dests.curr[i] = location;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                jump.alive.curr[i] = location;
            }
        }
    }

    /**
     * For every block, we need to calculate its entry state
     * which is a combination of states of blocks that are its
     * predecessors, merged into a state we use to verify
     * that inputs to instructions are correct symbols based
     * on instructions before allocation.
     */
    public void calculateEntryBlocks() {
        Queue<BasicBlock<?>> worklist = new LinkedList<>();
        worklist.add(this.lir.getControlFlowGraph().getStartBlock());

        while (!worklist.isEmpty()) {
            var block = worklist.poll();
            var instructions = this.blockInstructions.get(block);

            var labelInstr = (RAVInstruction.Op) instructions.getFirst();
            if (labelInstr.hasMissingDefinitions() && this.doPrecessorsHaveStates(block)) {
                this.fillMissingVariableLocationsForPhi(block, labelInstr);
            }

            // Create new entry state for successor blocks out of current block state
            var state = new VerifierState(this.blockEntryStates.get(block));
            for (var instr : instructions) {
                state.update(instr);
            }
            this.blockStates.put(block, state);

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);
                var succState = this.blockEntryStates.get(succ) == null ? new VerifierState() : this.blockEntryStates.get(succ);

                if (succState.meetWith(state)) { // State changed, add to worklist
                    this.blockEntryStates.put(succ, succState);
                    worklist.add(succ);
                }
            }
        }
    }

    /**
     * Here, use block entries to check if
     * inputs to instructions are correct.
     *
     * @return true, if valid, otherwise false
     */
    public boolean verifyInstructionInputs() {
        for (int i = 0; i < this.lir.getBlocks().length; i++) {
            var block = this.lir.getBlocks()[i];
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);

            for (var instr : instructions) {
                if (!state.check(instr)) {
                    return false;
                }
                state.update(instr);
            }
        }

        return true;
    }

    public boolean run() {
        this.calculateEntryBlocks();
        return this.verifyInstructionInputs();
    }

    public class VerifierState {
        protected AllocationStateMap<Value> registerValues;
        protected AllocationStateMap<Value> spillSlotValues;

        public VerifierState() {
            registerValues = new HashAllocationStateMap<>();
            spillSlotValues = new HashAllocationStateMap<>();
        }

        protected VerifierState(VerifierState other) {
            if (other == null) {
                registerValues = new HashAllocationStateMap<>();
                spillSlotValues = new HashAllocationStateMap<>();
                return;
            }

            registerValues = new HashAllocationStateMap<>(other.getRegisterValues());
            spillSlotValues = new HashAllocationStateMap<>(other.getSpillSlotValues());
        }

        public AllocationStateMap<Value> getRegisterValues() {
            return registerValues;
        }

        public AllocationStateMap<Value> getSpillSlotValues() {
            return spillSlotValues;
        }

        public boolean meetWith(VerifierState other) {
            var regChanged = this.registerValues.mergeWith(other.getRegisterValues());
            var stackChanged = this.spillSlotValues.mergeWith(other.getSpillSlotValues());
            return regChanged || stackChanged;
        }

        protected boolean checkInputs(RAVInstruction.ValueArrayPair values) {
            // Check that incoming values are not unknown or conflicted - these only matter if used
            for (int idx = 0; idx < values.count; idx++) {
                var orig = values.orig[idx];
                var curr = values.curr[idx];

                if (orig.equals(curr)) {
                    // In this case nothing has changed so we have nothing to verify
                    continue;
                }

                var state = this.registerValues.get(curr);
                if (state.isConflicted() || state.isUnknown()) {
                    return false;
                }

                if (state instanceof ValueAllocationState valAllocState) {
                    if (!valAllocState.value.equals(orig)) {
                        return false;
                    }

                    continue;
                }

                throw new IllegalStateException(); // Should never reach here.
            }

            return true;
        }

        protected boolean checkAliveConstraint(RAVInstruction.Op instruction) {
            for (int i = 0; i < instruction.alive.count; i++) {
                for (int j = 0; j < instruction.temp.count; j++) { // Cannot be a temp
                    if (instruction.alive.curr[i].equals(instruction.temp.curr[j])) {
                        return false;
                    }
                }

                for (int j = 0; j < instruction.uses.count; j++) { // Cannot be a use
                    if (instruction.alive.curr[i].equals(instruction.uses.curr[j])) {
                        return false;
                    }
                }

                // I don't think it can be a destination either
                for (int j = 0; j < instruction.dests.count; j++) {
                    if (instruction.alive.curr[i].equals(instruction.dests.curr[j])) {
                        return false;
                    }
                }
            }

            return true;
        }

        public boolean check(RAVInstruction.Base instruction) {
            if (instruction instanceof RAVInstruction.Op op) {
                if (!checkInputs(op.uses)) {
                    return false;
                }

                // if (!checkInputs(op.alive)) {
                //     // TODO: need to differentiate between alive and use
                //     // Also, alive cannot be in temp or use, need to enforce this constraint.
                //     return false;
                // }

                // if (!checkInputs(op.temp)) {
                //     // Memory or registers get passed here and they aren't overwritten by the allocator
                //     // so checking here comes down to see if arguments are equal.
                //     // TODO: mark values in registers and stack slots here as dead, so they cannot be used after
                //     // allocation
                //     return false;
                // }
            }

            return true;
        }

        public void update(RAVInstruction.Base instruction) {
            switch (instruction) {
                case RAVInstruction.Op op -> {
                    for (int i = 0; i < op.dests.count; i++) {
                        if (Value.ILLEGAL.equals(op.dests.orig[i])) {
                            continue; // Safe to ignore, when destination is illegal value, not when used.
                        }

                        if (op.dests.curr[i] == null) {
                            continue;
                        }

                        assert op.dests.orig[i] != null;

                        Value variable = op.dests.orig[i];
                        this.registerValues.put(op.dests.curr[i], new ValueAllocationState(variable));
                    }
                }
                case RAVInstruction.Spill spill ->
                        this.spillSlotValues.putClone(spill.to, this.registerValues.get(spill.from));
                case RAVInstruction.Reload reload ->
                        this.registerValues.putClone(reload.to, this.spillSlotValues.get(reload.from));
                case RAVInstruction.Move move ->
                        this.registerValues.putClone(move.to, this.registerValues.get(move.from));
                case RAVInstruction.VirtualMove virtMove ->
                        this.registerValues.put(virtMove.from, new ValueAllocationState(virtMove.to));
                default -> throw new IllegalStateException();
            }
        }
    }

    public abstract static class AllocationState {
        public static AllocationState getDefault() {
            return UnknownAllocationState.INSTANCE;
        }

        public boolean isUnknown() {
            return false;
        }

        public boolean isConflicted() {
            return false;
        }

        public abstract AllocationState clone();

        public abstract AllocationState meet(AllocationState other);

        public abstract boolean equals(AllocationState other);
    }

    public static class ConflictedAllocationState extends AllocationState {
        protected List<Value> conflictedValues;

        public ConflictedAllocationState(Value value1, Value value2) {
            this.conflictedValues = new LinkedList<>();
            this.conflictedValues.add(value1);
            this.conflictedValues.add(value2);
        }

        private ConflictedAllocationState(List<Value> conflictedValues) {
            this.conflictedValues = new LinkedList<>(conflictedValues);
        }

        public void addConflictedValue(Value value) {
            this.conflictedValues.add(value);
        }

        public List<Value> getConflictedValues() {
            return this.conflictedValues;
        }

        @Override
        public boolean isConflicted() {
            return true;
        }

        @Override
        public AllocationState meet(AllocationState other) {
            if (other instanceof ValueAllocationState valueState) {
                this.addConflictedValue(valueState.getValue());
            }

            if (other instanceof ConflictedAllocationState conflictedState) {
                this.conflictedValues.addAll(conflictedState.conflictedValues);
            }

            return this;
        }

        @Override
        public AllocationState clone() {
            return new ConflictedAllocationState(this.conflictedValues);
        }

        @Override
        public boolean equals(AllocationState other) {
            return other.isConflicted(); // TODO: handle contents
        }
    }

    public static class UnknownAllocationState extends AllocationState {
        public static UnknownAllocationState INSTANCE = new UnknownAllocationState();

        @Override
        public boolean isUnknown() {
            return true;
        }

        @Override
        public AllocationState meet(AllocationState other) {
            return other;
        }

        @Override
        public AllocationState clone() {
            return INSTANCE;
        }

        @Override
        public boolean equals(AllocationState other) {
            return other == INSTANCE;
        }
    }

    public static class ValueAllocationState extends AllocationState implements Cloneable {
        protected Value value;

        public ValueAllocationState(Value value) {
            if (value instanceof RegisterValue || value instanceof Variable) {
                // We use variables as symbols for register validation
                // but real registers can also be used as that, in some cases.
                this.value = value;
            } else {
                throw new IllegalStateException();
            }
        }

        public ValueAllocationState(ValueAllocationState other) {
            this.value = other.getValue();
        }

        public Value getValue() {
            return value;
        }

        public AllocationState meet(AllocationState other) {
            if (other.isUnknown()) {
                return this;
            }

            if (other.isConflicted()) {
                return other;
            }

            var otherValueAllocState = (ValueAllocationState) other;
            if (!this.value.equals(otherValueAllocState.getValue())) {
                return new ConflictedAllocationState(this.value, otherValueAllocState.getValue());
            }

            return this;
        }

        @Override
        public boolean equals(AllocationState other) {
            return other instanceof ValueAllocationState otherVal && this.value.equals(otherVal.getValue());
        }

        @Override
        public ValueAllocationState clone() {
            return new ValueAllocationState(this);
        }
    }

    public interface AllocationStateMap<K> extends Map<K, AllocationState>, Cloneable {
        AllocationState get(Object key);

        AllocationState putClone(K key, AllocationState value);

        boolean mergeWith(AllocationStateMap<K> other);

        Set<K> getVariableLocations(Variable variable);
    }

    @SuppressWarnings("serial")
    public class HashAllocationStateMap<K> extends HashMap<K, AllocationState> implements AllocationStateMap<K> {
        public HashAllocationStateMap() {
            super();
        }

        public HashAllocationStateMap(AllocationStateMap<K> other) {
            super(other);
        }

        @Override
        public AllocationState get(Object key) {
            var value = super.get(key);
            if (value == null) {
                return AllocationState.getDefault();
            }

            return value;
        }

        public Set<K> getVariableLocations(Variable variable) {
            Set<K> locations = new HashSet<>();
            for (Map.Entry<K, AllocationState> entry : entrySet()) {
                if (entry.getValue() instanceof ValueAllocationState valState) {
                    if (valState.getValue().equals(variable)) {
                        locations.add(entry.getKey());
                    }
                }
            }
            return locations;
        }

        public AllocationState putClone(K key, AllocationState value) {
            if (value.isConflicted() || value.isUnknown()) {
                return this.put(key, value);
            }

            return this.put(key, value.clone());
        }

        @Override
        public boolean mergeWith(AllocationStateMap<K> source) {
            boolean changed = false;
            for (var entry : source.entrySet()) {
                if (!this.containsKey(entry.getKey())) {
                    changed = true;

                    this.put(entry.getKey(), UnknownAllocationState.INSTANCE);
                }

                var currentValue = this.get(entry.getKey());
                var result = this.get(entry.getKey()).meet(entry.getValue());
                if (!currentValue.equals(result)) {
                    changed = true;
                }

                this.put(entry.getKey(), result);
            }

            return changed;
        }
    }
}
