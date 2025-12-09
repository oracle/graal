package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

// TODO: handle GC instructions
public final class RegisterAllocationVerifier {
    public BlockMap<List<RAVInstruction.Base>> blockInstructions;
    public BlockMap<VerifierState> blockStates; // Current states
    public BlockMap<VerifierState> blockEntryStates; // State on entry to block
    public Map<Variable, RAVInstruction.Op> labelMap;
    public LIR lir;
    public PhiResolution phiResolution;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, PhiResolution phiResolution) {
        this.lir = lir;

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);
        this.blockEntryStates.put(this.lir.getControlFlowGraph().getStartBlock(), new VerifierState());

        this.blockStates = new BlockMap<>(cfg);
        this.labelMap = new HashMap<>();

        this.phiResolution = phiResolution;
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

    private RegisterValue getRegisterFromUsage(
            LinkedList<BasicBlock<?>> path,
            BasicBlock<?> defBlock,
            RAVInstruction.Base usageInstruction,
            RegisterValue register,
            Variable variable
    ) {
        while (!path.isEmpty()) {
            var block = path.peek();
            if (block == defBlock) {
                break;
            }

            path.poll();
        }

        Value stackSlot = null;
        boolean reachedUsage = false;
        for (var block : path.reversed()) { // TODO: reconsider circular paths
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
                // get thrown out in the verification stage. TODO: maybe try to use multiple usages to be sure?
                switch (instruction) {
                    case RAVInstruction.Spill spill -> {
                        if (spill.to.equals(stackSlot)) {
                            register = spill.from;
                        }
                    }
                    case RAVInstruction.Move move -> {
                        if (move.to.equals(register)) {
                            register = move.from;
                        }
                    }
                    case RAVInstruction.Reload reload -> {
                        if (reload.to.equals(register)) {
                            register = null; // No longer holds the variable
                            stackSlot = reload.from;
                        }
                    }
                    case RAVInstruction.VirtualMove move -> {
                        if (move.location.equals(register) && !move.variableOrConstant.equals(variable)) {
                            throw new Error("Target register has different variable."); // TODO: deal with this when we find an example
                        }
                    }
                    // For Op, if there is a redefinition, we let the later stages handle that
                    default -> {
                    }
                }
            }
        }

        return register;
    }

    private void mapLabelVariableFromUsage(
            // @formatter:off
            Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap,
            Variable variable, RegisterValue regGuess,
            LinkedList<BasicBlock<?>> path, RAVInstruction.Base useInstruction)
            // @formatter:on
    {
        var variableLabelInstr = this.labelMap.get(variable);
        if (variableLabelInstr == null) {
            return;
        }

        var labelBlock = labelToBlockMap.get(variableLabelInstr);
        var register = this.getRegisterFromUsage(path, labelBlock, useInstruction, regGuess, variable);
        for (int j = 0; j < variableLabelInstr.dests.count; j++) {
            if (variableLabelInstr.dests.orig[j].equals(variable)) {
                variableLabelInstr.dests.curr[j] = register;
                this.labelMap.remove(variable);

                // Need to iterate over predecessors and fill jumps as well
                for (int k = 0; k < labelBlock.getPredecessorCount(); k++) {
                    var pred = labelBlock.getPredecessorAt(k);
                    var jumpOp = (RAVInstruction.Op) this.blockInstructions.get(pred).getLast();
                    jumpOp.alive.curr[j] = register;
                }
            }
        }
    }

    /**
     * Resolves label variable registers by finding where they are used.
     */
    private void resolveLabelsFromUsage() {
        Queue<LinkedList<BasicBlock<?>>> worklist = new LinkedList<>();

        LinkedList<BasicBlock<?>> firstPath = new LinkedList<>(); // TODO: maybe use something better than LinkedList?
        firstPath.add(this.lir.getControlFlowGraph().getStartBlock());
        worklist.add(firstPath);

        Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap = new HashMap<>();

        Set<BasicBlock<?>> visited = new HashSet<>();
        while (!worklist.isEmpty()) {
            var path = worklist.poll();
            var block = path.getLast();
            visited.add(block);

            var instructions = this.blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();
            for (int i = 0; i < labelInstr.dests.count; i++) {
                if (labelInstr.dests.curr[i] == null) {
                    Variable variable = (Variable) labelInstr.dests.orig[i];
                    this.labelMap.put(variable, labelInstr);
                    labelToBlockMap.put(labelInstr, block);
                }
            }

            for (var instruction : instructions) {
                switch (instruction) {
                    case RAVInstruction.VirtualMove move -> {
                        if (move.variableOrConstant instanceof Variable variable && move.location instanceof RegisterValue register) {
                            this.mapLabelVariableFromUsage(labelToBlockMap, variable, register, path, instruction);
                        }
                    }
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.uses.count; i++) {
                            if (op.uses.orig[i] instanceof Variable variable && op.uses.curr[i] instanceof RegisterValue register) {
                                this.mapLabelVariableFromUsage(labelToBlockMap, variable, register, path, instruction);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);
                if (visited.contains(succ)) {
                    continue;
                }

                var nextPath = new LinkedList<>(path);
                nextPath.add(succ);
                worklist.add(nextPath);
            }
        }

        if (!this.labelMap.isEmpty()) {
            // Maybe throwing is not the best way to do things, if variable is unused, we do not care
            // which register is used, but if in other case we haven't determined the register
            // And it is used somewhere, we need to mark the error with message that we could not determine it.
            throw new GraalError("Was not able to determine all label registers.");
        }
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
    private void resolveVariableLocationsForPhi(BasicBlock<?> block, RAVInstruction.Op labelInstr) {
        for (int i = 0; i < labelInstr.dests.count; i++) {
            Set<Value> locations = null;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var state = this.blockStates.get(pred);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                var inputValue = jump.alive.orig[i];

                var varLoc = state.registerValues.getValueLocations(inputValue);
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
     * Resolve phi registers from this jump point, can only be done if
     * the phi variables/constants only have one location.
     *
     * <p>
     * This way we can handle phi arguments before we reach said block.
     * But cannot really be done for loops, if their initial values
     * are the same and there are multiple ones.
     * </p>
     *
     * @param block From whom we are jumping from
     */
    private void resolvePhiFromJump(BasicBlock<?> block) {
        var state = this.blockStates.get(block);
        var jumpInstr = (RAVInstruction.Op) this.blockInstructions.get(block).getLast();
        for (int i = 0; i < jumpInstr.alive.count; i++) {
            if (jumpInstr.alive.curr[i] != null) {
                continue;
            }

            var inputValue = jumpInstr.alive.orig[i];
            if (inputValue instanceof Variable || inputValue instanceof ConstantValue) {
                // Does not work for constants if there is more of them
                var locations = state.registerValues.getValueLocations(inputValue);
                if (locations.size() == 1) {
                    var register = locations.stream().findFirst().get();
                    jumpInstr.alive.curr[i] = register;
                }

                for (int j = 0; j < block.getSuccessorCount(); j++) {
                    var succ = block.getSuccessorAt(j);
                    var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(succ).getFirst();

                    labelInstr.dests.curr[i] = jumpInstr.alive.curr[i];
                }
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

            // Create new entry state for successor blocks out of current block state
            var state = new VerifierState(this.blockEntryStates.get(block));
            for (var instr : instructions) {
                state.update(instr);
            }
            this.blockStates.put(block, state);

            if (this.phiResolution == PhiResolution.FromJump) {
                this.resolvePhiFromJump(block);
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);

                VerifierState succState;
                if (this.blockEntryStates.get(succ) == null) {
                    succState = new VerifierState();

                    // Either there's no state because it was not yet processed first part of the condition
                    // or, we need to reset it because label changed, second part of the condition

                    // Label change will hopefully work for children of children and there won't be need for us
                    // to reset all the children.
                    this.blockStates.put(succ, null);
                } else {
                    succState = this.blockEntryStates.get(succ);
                }

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
        if (this.phiResolution == PhiResolution.FromUsage) {
            this.resolveLabelsFromUsage();
        }
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

                assert curr != null;

                if (orig.equals(curr)) {
                    // In this case nothing has changed so we have nothing to verify
                    continue;
                }

                AllocationState state;
                if (curr instanceof RegisterValue) {
                    state = this.registerValues.get(curr);
                } else {
                    state = this.spillSlotValues.get(curr);
                }

                if (!orig.getValueKind().equals(curr.getValueKind())) {
                    return false; // Kind do not match
                }

                if (state.isConflicted() || state.isUnknown()) {
                    return false;
                }

                if (state instanceof ValueAllocationState valAllocState) {
                    if (!valAllocState.value.equals(orig)) {
                        // Kind sizes should be checked here as well.
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
                for (int j = 0; j < instruction.temp.count; j++) {
                    if (instruction.alive.curr[i].equals(instruction.temp.curr[j])) {
                        return false;
                    }
                }

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

                if (!checkInputs(op.alive)) {
                    return false;
                }

                for (int i = 0; i < op.temp.count; i++) {
                    var curr = op.temp.curr[i];
                    var orig = op.temp.orig[i];

                    if (!curr.getValueKind().equals(orig.getValueKind())) {
                        // Make sure the assigned register has the correct kind for temp.
                        return false;
                    }
                }

                if (!this.checkAliveConstraint(op)) {
                    return false;
                }
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
                            // This can happen for certain instructions - jump or label, and we need to
                            // resolve appropriate registers for these, if we do not, we throw in check()
                            continue;
                        }

                        assert op.dests.orig[i] != null;

                        Value variable = op.dests.orig[i];
                        this.registerValues.put(op.dests.curr[i], new ValueAllocationState(variable));
                    }

                    for (int i = 0; i < op.temp.count; i++) {
                        // We cannot believe the contents of registers used as temp, thus we need to reset.
                        this.registerValues.put(op.temp.curr[i], UnknownAllocationState.INSTANCE);
                    }
                }
                case RAVInstruction.Spill spill ->
                        this.spillSlotValues.putClone(spill.to, this.registerValues.get(spill.from));
                case RAVInstruction.Reload reload ->
                        this.registerValues.putClone(reload.to, this.spillSlotValues.get(reload.from));
                case RAVInstruction.Move move ->
                        this.registerValues.putClone(move.to, this.registerValues.get(move.from));
                case RAVInstruction.VirtualMove virtMove -> {
                    if (virtMove.location instanceof RegisterValue) {
                        this.registerValues.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant));
                    } else {
                        this.spillSlotValues.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant));
                    }
                }
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
        protected Set<Value> conflictedValues;

        public ConflictedAllocationState(Value value1, Value value2) {
            this.conflictedValues = new HashSet<>();
            this.conflictedValues.add(value1);
            this.conflictedValues.add(value2);
        }

        private ConflictedAllocationState(Set<Value> conflictedValues) {
            this.conflictedValues = new HashSet<>(conflictedValues);
        }

        public void addConflictedValue(Value value) {
            this.conflictedValues.add(value);
        }

        public Set<Value> getConflictedValues() {
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
            if (value instanceof RegisterValue || value instanceof Variable || value instanceof ConstantValue || value instanceof StackSlot) {
                // We use variables as symbols for register validation
                // but real registers can also be used as that, in some cases.
                // TODO: reconsider handling of StackSlots
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

        Set<K> getValueLocations(Value value);
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
        public AllocationState put(K key, AllocationState value) {
            return super.put(key, value);
        }

        @Override
        public AllocationState get(Object key) {
            var value = super.get(key);
            if (value == null) {
                return AllocationState.getDefault();
            }

            return value;
        }

        public Set<K> getValueLocations(Value value) {
            Set<K> locations = new HashSet<>();
            for (Map.Entry<K, AllocationState> entry : entrySet()) {
                if (entry.getValue() instanceof ValueAllocationState valState) {
                    if (valState.getValue().equals(value)) {
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

        // TODO: we need to not use size of the work for the hash map for registers at least
        private String getCorrectKeyString(Value value) {
            return switch (value) {
                case RegisterValue reg -> reg.getRegister().toString();
                default -> value.toString();
            };
        }
    }
}
