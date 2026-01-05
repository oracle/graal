package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

import java.util.Arrays;
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
    public PhiResolution phiResolution;

    // FromUsage resolver
    public Map<Variable, RAVInstruction.Op> labelMap;
    public Map<Variable, RegisterValue> variableRegisterMap;
    public Map<Variable, Variable> usageAliasMap;

    // FromPredecessors resolver
    public BlockMap<VerifierState> blockDefinitions;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, PhiResolution phiResolution) {
        this.lir = lir;

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);
        this.blockEntryStates.put(this.lir.getControlFlowGraph().getStartBlock(), new VerifierState());

        this.blockStates = new BlockMap<>(cfg);

        this.phiResolution = phiResolution;

        this.blockDefinitions = new BlockMap<>(cfg);

        this.labelMap = new HashMap<>();
        this.variableRegisterMap = new HashMap<>();
        this.usageAliasMap = new HashMap<>();
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

    /**
     * Resolves label variable registers by finding where they are used.
     */
    private void resolvePhiFromUsage() {
        Queue<LinkedList<BasicBlock<?>>> worklist = new LinkedList<>();

        // TODO: need to store paths in a more memory friendly way
        // because we always copy the whole path when entering successor
        // which is unnecessary and explodes in memory usage, making
        // this method by far the most resource demanding, while
        // not being the best.

        LinkedList<BasicBlock<?>> firstPath = new LinkedList<>();
        firstPath.add(this.lir.getControlFlowGraph().getStartBlock());
        worklist.add(firstPath);

        Map<RAVInstruction.Op, BasicBlock<?>> labelToBlockMap = new HashMap<>();

        Set<BasicBlock<?>> visited = new HashSet<>();
        while (!worklist.isEmpty()) {
            var path = worklist.poll();
            var block = path.getLast();

            if (visited.contains(block)) {
                // TODO: for some reason blocks that were already visited
                // are present here which causes few issues.
                continue;
            }

            visited.add(block);

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
                        if (LIRValueUtil.isVariable(move.variableOrConstant) && move.location instanceof RegisterValue register) {
                            this.mapLabelVariableFromUsage(labelToBlockMap, LIRValueUtil.asVariable(move.variableOrConstant), register, path, instruction);
                        }
                    }
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.uses.count; i++) {
                            if (LIRValueUtil.isVariable(op.uses.orig[i]) && op.uses.curr[i] instanceof RegisterValue register) {
                                this.mapLabelVariableFromUsage(labelToBlockMap, LIRValueUtil.asVariable(op.uses.orig[i]), register, path, instruction);
                            }
                        }

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
                        } else {
                            for (int i = 0; i < op.alive.count; i++) {
                                if (LIRValueUtil.isVariable(op.alive.orig[i]) && op.alive.curr[i] instanceof RegisterValue register) {
                                    this.mapLabelVariableFromUsage(labelToBlockMap, LIRValueUtil.asVariable(op.alive.orig[i]), register, path, instruction);
                                }
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

        // We no longer throw an error when label map is not empty
        // because if such thing happens, then variable was not used,
        // and thus we cannot determine its location.
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
    private boolean resolvePhiFromPredecessors(BasicBlock<?> block, RAVInstruction.Op labelInstr) {
        for (int i = 0; i < labelInstr.dests.count; i++) {
            Set<Register> locations = null;
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

                // TODO: maybe we just need the locations to have size 1 to stop
                locations.retainAll(varLoc);
            }

            if (locations.size() != 1) {
                return false;
            }

            Register location = locations.stream().findFirst().get();
            var registerValue = location.asValue(labelInstr.dests.orig[i].getValueKind());

            labelInstr.dests.curr[i] = registerValue;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                jump.alive.curr[i] = registerValue;
            }
        }

        return true;
    }

    private void propagateLabelChangeFromPredecessors(BasicBlock<?> defBlock) {
        var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(defBlock).getFirst();

        // Definition block needs to have this set.
        var propagateMap = new HashMap<BasicBlock<?>, List<Variable>>();
        var variableToRegisters = new HashMap<Variable, Register>();
        var defBlockVariablesToPropagate = new LinkedList<Variable>();
        var defForEntry = this.blockDefinitions.get(defBlock);
        for (int i = 0; i < labelInstr.dests.count; i++) {
            var register = ValueUtil.asRegister(labelInstr.dests.curr[i]);
            var variable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);

            var registerDefinition = defForEntry.registerValues.get(register);
            if (registerDefinition.isUnknown()) {
                defForEntry.registerValues.put(register, new ValueAllocationState(variable));
            }

            defBlockVariablesToPropagate.add(variable);
            variableToRegisters.put(variable, register);
        }

        Queue<BasicBlock<?>> worklist = new LinkedList<>();
        Set<BasicBlock<?>> processed = new HashSet<>();
        worklist.add(defBlock);
        propagateMap.put(defBlock, defBlockVariablesToPropagate);

        while (!worklist.isEmpty()) {
            var curr = worklist.remove();
            if (processed.contains(curr)) {
                continue;
            }
            processed.add(curr);

            var def = this.blockDefinitions.get(curr);
            var state = this.blockStates.get(curr);

            var variablesToPropagate = propagateMap.get(curr);
            var itToPropagate = variablesToPropagate.iterator();
            var variablesToBePropagated = new LinkedList<Variable>();
            while (itToPropagate.hasNext()) {
                var variable = LIRValueUtil.asVariable(itToPropagate.next());
                var register = variableToRegisters.get(variable);

                var registerDefinition = def.registerValues.get(register);
                if (!registerDefinition.isUnknown() && registerDefinition instanceof ValueAllocationState valState && !valState.getValue().equals(variable)) {
                    // This block has redefined the value of said register,
                    // and we will not pass it further.
                    continue;
                }

                variablesToBePropagated.add(variable);

                if (state != null) {
                    state.registerValues.put(register, new ValueAllocationState(variable));
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
                    throw new GraalError("Circular definition for variable detected.");
                }

                boolean dominates = succ.dominates(defBlock);
                if (dominates) {
                    continue;
                }

                var itToBePropagated = variablesToBePropagated.iterator();
                while (itToBePropagated.hasNext()) {
                    var variable = LIRValueUtil.asVariable(itToBePropagated.next());
                    var register = variableToRegisters.get(variable);
                    succEntryState.registerValues.put(register, new ValueAllocationState(variable));
                }

                propagateMap.put(succ, variablesToBePropagated);
                worklist.add(succ);
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
            if (LIRValueUtil.isVariable(inputValue) || inputValue instanceof ConstantValue) {
                // Does not work for constants if there is more of them
                var locations = state.registerValues.getValueLocations(inputValue);
                if (locations.isEmpty()) {
                    continue;
                }

                var register = locations.stream().findFirst().get();
                if (locations.size() != 1) {
                    int time = -1;
                    for (var location : locations) {
                        var locTime = state.registerValues.getTime(location);
                        if (locTime > time) {
                            register = location;
                            time = locTime;
                        }
                    }
                }

                var registerValue = register.asValue(jumpInstr.alive.orig[i].getValueKind());
                jumpInstr.alive.curr[i] = registerValue;

                for (int j = 0; j < block.getSuccessorCount(); j++) {
                    var succ = block.getSuccessorAt(j);
                    var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(succ).getFirst();

                    labelInstr.dests.curr[i] = jumpInstr.alive.curr[i];

                    for (int k = 0; k < succ.getPredecessorCount(); k++) {
                        // Sibling jumps need to be updated as well in order to pass input checks.
                        var sibling = succ.getPredecessorAt(k);
                        if (sibling.equals(block)) {
                            continue;
                        }

                        var siblingJumpInstr = (RAVInstruction.Op) this.blockInstructions.get(sibling).getLast();
                        siblingJumpInstr.alive.curr[i] = jumpInstr.alive.curr[i];
                    }
                }
            }
        }
    }

    private boolean hasMissingRegistersInLabel(RAVInstruction.Op op) {
        return op.hasMissingDefinitions();
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

            boolean resolvedPhi = false;
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();
            if (this.phiResolution == PhiResolution.FromPredecessors && this.doPrecessorsHaveStates(block) && this.hasMissingRegistersInLabel(labelInstr)) {
                resolvedPhi = this.resolvePhiFromPredecessors(block, labelInstr);
            }

            if (this.phiResolution == PhiResolution.FromJump && this.hasMissingRegistersInLabel(labelInstr)) {
                if (!doPrecessorsHaveStates(block)) {
                    // A hot fix, if not all predecessors have state, then we wait until all of them do
                    continue;
                }
            }

            // Create new entry state for successor blocks out of current block state
            var state = new VerifierState(this.blockEntryStates.get(block));
            for (var instr : instructions) {
                state.update(instr);
            }
            this.blockStates.put(block, state);

            if (this.phiResolution == PhiResolution.FromJump) {
                this.resolvePhiFromJump(block);
            }

            if (resolvedPhi) {
                this.propagateLabelChangeFromPredecessors(block);
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

                var succLabelOp = (RAVInstruction.Op) this.blockInstructions.get(succ).getFirst();
                if (succState.meetWith(state) || this.hasMissingRegistersInLabel(succLabelOp)) {
                    // if we resolved a phi, then we also need to process children again,
                    // TODO: might be better to do something in propagateLabelChangeFromPredecessors

                    // State changed or labels have not yet been determined, add to worklist
                    this.blockEntryStates.put(succ, succState);
                    worklist.remove(succ); // Always at the end, for predecessors to be processed first.
                    worklist.add(succ);
                }
            }

            if (phiResolution == PhiResolution.FromPredecessors && worklist.isEmpty()) {
                this.addMissingLabelBlocks(worklist);
            }
        }
    }

    private int missingLabelBlocksCount = -1;
    private int missingLabelCheckRunCount = 0;

    /**
     * This method needs to be run, because sometimes already processed blocks
     * do not get their labels updated with newly defined variables and thus
     * resolution is not complete.
     *
     * TODO: this might be better be handled by some dependency graph
     *
     * @param worklist Worklist to which we add new blocks for processing.
     */
    private void addMissingLabelBlocks(Queue<BasicBlock<?>> worklist) {
        if (missingLabelBlocksCount != -1 && missingLabelBlocksCount >= missingLabelCheckRunCount) {
            // First time around it counts number of missing label blocks
            // then it uses said count to make sure this function is not ran more
            // than said amount of times to prevent infinite cycles,
            // this can happen if there's a dependency loop between said label blocks
            // and should be avoided with other resolution methods.
            return;
        }

        missingLabelCheckRunCount++;
        int currentMissingLabelBlockCount = 0;
        for (var blockId : this.lir.getBlocks()) {
            var pBlock = this.lir.getBlockById(blockId);
            var label = (RAVInstruction.Op) this.blockInstructions.get(pBlock).getFirst();
            if (this.hasMissingRegistersInLabel(label)) {
                if (!this.doPrecessorsHaveStates(pBlock)) {
                    throw new RuntimeException("Could not determine label registers for " + pBlock);
                }

                currentMissingLabelBlockCount++;
                worklist.add(pBlock);
            }
        }

        if (missingLabelBlocksCount == -1) {
            missingLabelBlocksCount = currentMissingLabelBlockCount;
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
            this.resolvePhiFromUsage();
        }

        if (this.phiResolution == PhiResolution.FromPredecessors) {
            // Currently only useful to this resolution type, but later
            // we should only go through instructions once and then just merge
            // with entry state and successors.
            for (var blockId : this.lir.getBlocks()) {
                var block = this.lir.getBlockById(blockId);
                var instructions = this.blockInstructions.get(block);
                var state = new VerifierState();
                for (var instr : instructions) {
                    state.update(instr);
                }
                this.blockDefinitions.put(block, state);
            }
        }

        this.calculateEntryBlocks();
        return this.verifyInstructionInputs();
    }

    public class VerifierState {
        protected HashAllocationStateMap<Register> registerValues;
        protected HashAllocationStateMap<Value> spillSlotValues;

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

        public HashAllocationStateMap<Register> getRegisterValues() {
            return registerValues;
        }

        public HashAllocationStateMap<Value> getSpillSlotValues() {
            return spillSlotValues;
        }

        public boolean meetWith(VerifierState other) {
            var regChanged = this.registerValues.mergeWith(other.getRegisterValues());
            var stackChanged = this.spillSlotValues.mergeWith(other.getSpillSlotValues());
            return regChanged || stackChanged;
        }

        protected boolean checkInputs(RAVInstruction.ValueArrayPair values, boolean isJump) {
            // Check that incoming values are not unknown or conflicted - these only matter if used
            for (int idx = 0; idx < values.count; idx++) {
                var orig = values.orig[idx];
                var curr = values.curr[idx];

                if (curr == null && isJump && phiResolution == PhiResolution.FromUsage) {
                    // Whenever PhiResolution = FromUsage, variable is not used and thus no register present.
                    continue;
                }

                assert orig != null;

                if (curr == null) {
                    if (isJump) {
                        throw new RuntimeException(this.getMissingLabelOrJumpErrMsg("JUMP", values));
                    }

                    assert false;
                }

                if (orig.equals(curr)) {
                    // In this case nothing has changed so we have nothing to verify
                    continue;
                }

                AllocationState state;
                if (curr instanceof RegisterValue) {
                    state = this.registerValues.get(ValueUtil.asRegister(curr));
                } else {
                    state = this.spillSlotValues.get(curr);
                }

                if (!kindsEqual(orig, curr)) {
                    return false;
                }

                if (state.isConflicted() || state.isUnknown()) {
                    return false;
                }

                if (state instanceof ValueAllocationState valAllocState) {
                    if (!valAllocState.value.equals(orig)) {
                        if (orig instanceof CastValue castValue && valAllocState.value.equals(castValue.underlyingValue())) {
                            // check for underlying value for CastValue.
                            continue;
                        }

                        // Kind sizes should be checked here as well.
                        return false;
                    }

                    continue;
                }

                throw new IllegalStateException(); // Should never reach here.
            }

            return true;
        }

        protected boolean kindsEqual(Value orig, Value curr) {
            var origKind = orig.getValueKind();
            var currKind = curr.getValueKind();

            if (currKind.equals(origKind)) {
                return true;
            }

            if (origKind instanceof LIRKindWithCast || currKind instanceof LIRKindWithCast) {
                // TestCase: BoxingTest.boxShort
                // MOV (x: [v11|QWORD[.] + 12], y: reinterpret: v0|DWORD as: WORD) size: WORD
                // MOV (x: [rax|QWORD[.] + 12], y: r10|WORD(DWORD)) size: WORD
                // TODO: figure out the correct semantics for these casts
                return origKind.getPlatformKind().equals(currKind.getPlatformKind());
            }

            return false;
        }

        protected boolean checkAliveConstraint(RAVInstruction.Op instruction) {
            for (int i = 0; i < instruction.alive.count; i++) {
                Value value = instruction.alive.curr[i];
                if (Value.ILLEGAL.equals(value)) {
                    continue; // TODO: remove IllegalValues from these arrays.
                }

                for (int j = 0; j < instruction.temp.count; j++) {
                    if (value.equals(instruction.temp.curr[j])) {
                        return false;
                    }
                }

                for (int j = 0; j < instruction.dests.count; j++) {
                    if (value.equals(instruction.dests.curr[j])) {
                        return false;
                    }
                }
            }

            return true;
        }

        public boolean check(RAVInstruction.Base instruction) {
            if (instruction instanceof RAVInstruction.Op op) {
                boolean isJump = op.lirInstruction instanceof StandardOp.JumpOp;

                if (!checkInputs(op.uses, isJump)) {
                    return false;
                }

                if (!checkInputs(op.alive, isJump)) {
                    return false;
                }

                for (int i = 0; i < op.temp.count; i++) {
                    var curr = op.temp.curr[i];
                    var orig = op.temp.orig[i];

                    if (!kindsEqual(orig, curr)) {
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

        public String getMissingLabelOrJumpErrMsg(String subject, RAVInstruction.ValueArrayPair values) {
            String errorMsg = "[";
            for (int j = 0; j < values.count; j++) {
                errorMsg += values.orig[j].toString();
                if (values.curr[j] != null) {
                    errorMsg += " -> " + values.curr[j].toString();
                } else {
                    errorMsg += " -> ?";
                }

                if (j != values.count-1) {
                    errorMsg += ", ";
                }
            }

            return "Failed to resolve: " + subject + " " + errorMsg + "]";
        }

        public void update(RAVInstruction.Base instruction) {
            switch (instruction) {
                case RAVInstruction.Op op -> {
                    for (int i = 0; i < op.dests.count; i++) {
                        if (Value.ILLEGAL.equals(op.dests.orig[i])) {
                            continue; // Safe to ignore, when destination is illegal value, not when used.
                        }

                        if ((phiResolution == PhiResolution.FromPredecessors
                                        || phiResolution == PhiResolution.FromUsage)
                                        && op.dests.curr[i] == null) {
                            // This can happen for certain instructions - jump or label, and we need to
                            // resolve appropriate registers for these, if we do not, we throw in check()
                            continue;
                        }

                        // Here, FromJump resolution will fail if it was not completed
                        if (op.dests.curr[i] == null && phiResolution == PhiResolution.FromJump) {
                            throw new RuntimeException(this.getMissingLabelOrJumpErrMsg("LABEL", op.dests));
                        }

                        assert op.dests.curr[i] != null;
                        assert op.dests.orig[i] != null;

                        Value location = op.dests.curr[i];
                        Value variable = op.dests.orig[i];
                        if (ValueUtil.isRegister(location)) {
                            this.registerValues.put(ValueUtil.asRegister(location), new ValueAllocationState(variable));
                        } else {
                            this.spillSlotValues.put(location, new ValueAllocationState(variable));
                        }
                    }

                    for (int i = 0; i < op.temp.count; i++) {
                        var value = op.temp.curr[i];
                        if (Value.ILLEGAL.equals(value)) {
                            continue;
                        }

                        // We cannot believe the contents of registers used as temp, thus we need to reset.
                        Value location = op.temp.curr[i];
                        if (ValueUtil.isRegister(location)) {
                            this.registerValues.put(ValueUtil.asRegister(location), UnknownAllocationState.INSTANCE);
                        } else {
                            this.spillSlotValues.put(location, UnknownAllocationState.INSTANCE);
                        }
                    }
                }
                case RAVInstruction.Spill spill ->
                        this.spillSlotValues.putClone(spill.to, this.registerValues.get(ValueUtil.asRegister(spill.from)));
                case RAVInstruction.Reload reload ->
                        this.registerValues.putClone(ValueUtil.asRegister(reload.to), this.spillSlotValues.get(reload.from));
                case RAVInstruction.Move move -> {
                        var value = this.registerValues.get(ValueUtil.asRegister(move.from));
                        if (value.isUnknown()) {
                            // Hotfix for blockDefinitions, where if we moved a Value we need to make
                            // sure it's saved in the state, so that value is not propagated further
                            // causing Circular Exception
                            // TestCase: ConditionalElimination17
                            value = new ValueAllocationState(Value.ILLEGAL);
                        }

                        this.registerValues.putClone(ValueUtil.asRegister(move.to), value);
                }
                case RAVInstruction.StackMove move ->
                    this.spillSlotValues.putClone(move.to, this.spillSlotValues.get(move.from));
                case RAVInstruction.VirtualMove virtMove -> {
                    if (virtMove.location instanceof RegisterValue) {
                        this.registerValues.put(ValueUtil.asRegister(virtMove.location), new ValueAllocationState(virtMove.variableOrConstant));
                    } else if (LIRValueUtil.isVariable(virtMove.location)) {
                        // v4|QWORD[.] = MOVE input: v3|QWORD[.] moveKind: QWORD
                        // Move before allocation
                        // TODO: maybe handle this better than VirtualMove with location as Variable
                        // TestCase: BoxingTest.boxBoolean
                        var locations = this.registerValues.getValueLocations(virtMove.variableOrConstant);
                        for (var location : locations) {
                            this.registerValues.put(location, new ValueAllocationState(virtMove.location));
                        }
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

        @Override
        public String toString() {
            return "Conflicted {" + Arrays.toString(this.conflictedValues.toArray()) + "}";
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

        @Override
        public String toString() {
            return "Unknown";
        }
    }

    public static class ValueAllocationState extends AllocationState implements Cloneable {
        protected Value value;

        public ValueAllocationState(Value value) {
            if (value instanceof RegisterValue || LIRValueUtil.isVariable(value) || value instanceof ConstantValue || value instanceof StackSlot || value instanceof VirtualStackSlot || Value.ILLEGAL.equals(value)) {
                // StackSlot, RegisterValue is present in start block in label as predefined argument
                // VirtualStackSlot is used for RESTORE_REGISTERS and SAVE_REGISTERS
                // ConstantValue act as Variable

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

        @Override
        public String toString() {
            return "Value {" + this.value + "}";
        }
    }

    public interface AllocationStateMap<K> extends Map<K, AllocationState>, Cloneable {
        AllocationState get(Object key);

        AllocationState putClone(K key, AllocationState value);

        boolean mergeWith(AllocationStateMap<K> other);

        Set<K> getValueLocations(Value value);

        int getTime(K key);
    }

    @SuppressWarnings("serial")
    public class HashAllocationStateMap<K> extends HashMap<K, AllocationState> implements AllocationStateMap<K> {
        protected Map<K, Integer> locationTimings;
        protected int time;

        public HashAllocationStateMap() {
            super();

            locationTimings = new HashMap<>();
            time = 0;
        }

        public HashAllocationStateMap(HashAllocationStateMap<K> other) {
            super(other);

            locationTimings = new HashMap<>(other.locationTimings);
            time = other.time + 1;
        }

        @Override
        public AllocationState put(K key, AllocationState value) {
            locationTimings.put(key, time++);
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

        public int getTime(K key) {
            return locationTimings.get(key);
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
