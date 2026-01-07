package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class RegisterAllocationVerifier {
    public BlockMap<List<RAVInstruction.Base>> blockInstructions;
    public BlockMap<MergedBlockVerifierState> blockStates; // Current states
    public BlockMap<MergedBlockVerifierState> blockEntryStates; // State on entry to block

    public LIR lir;
    public PhiResolution phiResolution;

    // FromUsage resolver
    public Map<Variable, RAVInstruction.Op> labelMap;
    public Map<Variable, RegisterValue> variableRegisterMap;
    public Map<Variable, Variable> usageAliasMap;

    // FromPredecessors resolver
    public BlockMap<DefinitionSet> blockDefinitions;

    public FromUsageResolver fromUsageResolver;

    protected RegisterAllocationConfig registerAllocationConfig;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, PhiResolution phiResolution, RegisterAllocationConfig registerAllocationConfig) {
        this.lir = lir;

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);
        this.blockEntryStates.put(cfg.getStartBlock(), new MergedBlockVerifierState(registerAllocationConfig, phiResolution));
        this.blockStates = new BlockMap<>(cfg);
        this.phiResolution = phiResolution;

        this.blockDefinitions = new BlockMap<>(cfg);

        this.labelMap = new HashMap<>();
        this.variableRegisterMap = new HashMap<>();
        this.usageAliasMap = new HashMap<>();

        this.fromUsageResolver = new FromUsageResolver(lir, blockInstructions);
        this.registerAllocationConfig = registerAllocationConfig;
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

    public BlockMap<DefinitionSet> getDefinitionSets() {
        BlockMap<DefinitionSet> blockDefinitions = new BlockMap<>(this.lir.getControlFlowGraph());
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);
            var definitions = new DefinitionSet();

            for (var instruction : instructions) {
                switch (instruction) {
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.dests.count; i++) {
                            var location = op.dests.curr[i];
                            if (location == null) {
                                continue;
                            }

                            definitions.add(location);
                        }

                        for (int i = 0; i < op.temp.count; i++) {
                            var location = op.temp.curr[i];
                            if (location == null || Value.ILLEGAL.equals(location)) {
                                continue;
                            }

                            definitions.add(location);
                        }
                    }
                    case RAVInstruction.Move move -> definitions.add(move.to);
                    case RAVInstruction.Reload reload -> definitions.add(reload.to);
                    case RAVInstruction.Spill spill -> definitions.add(spill.to);
                    case RAVInstruction.StackMove stackMove -> definitions.add(stackMove.to);
                    case RAVInstruction.VirtualMove virtMove -> {
                        if (!LIRValueUtil.isVariable(virtMove.location)) {
                            definitions.add(virtMove.location);
                        }
                    }
                    default -> GraalError.shouldNotReachHere("Invalid RAV instruction " + instruction);
                }
            }

            blockDefinitions.put(block, definitions);
        }

        return blockDefinitions;
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

    private void propagateLabelChangeFromPredecessors(BasicBlock<?> defBlock) {
        var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(defBlock).getFirst();

        // Definition block needs to have this set.
        var propagateMap = new HashMap<BasicBlock<?>, List<Variable>>();
        var variableToRegisters = new HashMap<Variable, RegisterValue>();
        var defBlockVariablesToPropagate = new LinkedList<Variable>();
        for (int i = 0; i < labelInstr.dests.count; i++) {
            var register = (RegisterValue) labelInstr.dests.curr[i];
            var variable = LIRValueUtil.asVariable(labelInstr.dests.orig[i]);

            defBlockVariablesToPropagate.add(variable);
            variableToRegisters.put(variable, register);
        }

        Queue<BasicBlock<?>> worklist = new LinkedList<>();
        Set<BasicBlock<?>> processed = new HashSet<>();
        worklist.add(defBlock);
        propagateMap.put(defBlock, defBlockVariablesToPropagate);

        // Monitor_notowner01 + Moniitor_contended01
        // B23  216       stack:48|QWORD[.] = MOVE input: rsi|QWORD[.] moveKind: QWORD // LSRAEliminateSpillMove: store at definition
        // old variable supposed to be overwriten stored in spillSpill slots...
        // TODO: any location we store the new variables in, needs to be propagated as well
        // and this can be done in successors and needs to be accounted for...
        // TODO: Reconsider any move with label variable.

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

                if (def.contains(register)) {
                    continue;
                }

                variablesToBePropagated.add(variable);
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
                    throw new CircularDefinitionError(defBlock, curr, labelInstr, variablesToBePropagated);
                }

                boolean dominates = succ.dominates(defBlock);
                if (dominates) {
                    continue;
                }

                var itToBePropagated = variablesToBePropagated.iterator();
                while (itToBePropagated.hasNext()) {
                    var variable = LIRValueUtil.asVariable(itToBePropagated.next());
                    var register = variableToRegisters.get(variable);
                    succEntryState.values.put(register, new ValueAllocationState(variable));
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
            if (!LIRValueUtil.isVariable(inputValue) && !(inputValue instanceof ConstantValue)) {
                continue;
            }

            var registerValue = this.getRegisterBeforeJump(state, inputValue);
            if (registerValue == null) {
                return;
            }

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

    private Value getRegisterBeforeJump(MergedBlockVerifierState state, Value inputValue) {
        // Does not work for constants if there is more of them
        var locations = state.values.getValueLocations(inputValue);
        if (locations.isEmpty()) {
            return null;
        }

        var register = locations.stream().findFirst().get();
        if (locations.size() != 1) {
            int time = -1;
            for (var location : locations) {
                var locTime = state.values.getKeyTime(location);
                if (locTime > time) {
                    register = location;
                    time = locTime;
                }
            }
        }

        return register;
        // return register.asValue(inputValue.getValueKind());
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
            var state = new MergedBlockVerifierState(this.blockEntryStates.get(block), registerAllocationConfig, phiResolution);
            for (var instr : instructions) {
                state.update(instr, block);
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

                MergedBlockVerifierState succState;
                if (this.blockEntryStates.get(succ) == null) {
                    succState = new MergedBlockVerifierState(registerAllocationConfig, phiResolution);

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
     * <p>
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
                    throw new LabelNotResolvedError(pBlock, label, phiResolution);
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
    public void verifyInstructionInputs() {
        for (var blockId : this.lir.getBlocks()) {
            var block = this.lir.getBlockById(blockId);
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (var instr : instructions) {
                state.check(instr, block, labelInstr);
                state.update(instr, block);
            }
        }
    }

    public void verifyInstructionsAndCollectErrors(String compUnitName) {
        List<RAVException> exceptions = new ArrayList<>();
        for (var blockId : this.lir.getBlocks()) {
            var block = this.lir.getBlockById(blockId);
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (var instr : instructions) {
                try {
                    state.check(instr, block, labelInstr);
                    state.update(instr, block);
                } catch (RAVException e) {
                    exceptions.add(e);
                }
            }
        }

        if (!exceptions.isEmpty()) {
            throw new RAVFailedVerificationException(compUnitName, exceptions);
        }
    }

    public void run() {
        if (this.phiResolution == PhiResolution.FromUsage) {
            this.fromUsageResolver.resolvePhiFromUsage();
        }

        if (this.phiResolution == PhiResolution.FromPredecessors) {
            // Currently only useful to this resolution type, but later
            // we should only go through instructions once and then just merge
            // with entry state and successors.
            this.blockDefinitions = this.getDefinitionSets();
        }

        this.calculateEntryBlocks();
        this.verifyInstructionInputs();
    }
}
