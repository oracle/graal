package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.meta.Value;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class RegisterAllocationVerifier {
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;
    protected BlockMap<MergedBlockVerifierState> blockStates;
    protected BlockMap<MergedBlockVerifierState> blockEntryStates;

    protected LIR lir;
    protected RegisterAllocationConfig registerAllocationConfig;
    protected PhiResolution phiResolution;

    protected FromPredecessorsResolver fromPredecessorsResolver;
    protected FromJumpResolver fromJumpResolver;
    protected FromUsageResolver fromUsageResolver;
    protected FromUsageResolverGlobal fromUsageResolverGlobal;

    protected ConflictResolver constantMaterializationConflictResolver;
    protected ConflictResolver labelConflictResolver;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, PhiResolution phiResolution, RegisterAllocationConfig registerAllocationConfig) {
        this.lir = lir;
        this.registerAllocationConfig = registerAllocationConfig;

        this.constantMaterializationConflictResolver = new ConstantMaterializationConflictResolver();
        this.labelConflictResolver = new LabelConflictResolver();

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);

        this.blockStates = new BlockMap<>(cfg);
        this.phiResolution = phiResolution;

        this.fromPredecessorsResolver = new FromPredecessorsResolver(lir, blockInstructions, blockStates, blockEntryStates);
        this.fromJumpResolver = new FromJumpResolver(lir, blockInstructions, blockStates);
        this.fromUsageResolver = new FromUsageResolver(lir, blockInstructions);
        this.fromUsageResolverGlobal = new FromUsageResolverGlobal(lir, blockInstructions);
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

        this.blockEntryStates.put(this.lir.getControlFlowGraph().getStartBlock(), new MergedBlockVerifierState(registerAllocationConfig, phiResolution, constantMaterializationConflictResolver, labelConflictResolver));
        worklist.add(this.lir.getControlFlowGraph().getStartBlock());

        while (!worklist.isEmpty()) {
            var block = worklist.poll();
            var instructions = this.blockInstructions.get(block);

            boolean resolvedPhi = false;
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();
            if (this.phiResolution == PhiResolution.FromPredecessors && this.doPrecessorsHaveStates(block) && this.hasMissingRegistersInLabel(labelInstr)) {
                resolvedPhi = this.fromPredecessorsResolver.resolvePhiFromPredecessors(block, labelInstr);
            }

            if (this.phiResolution == PhiResolution.FromJump && this.hasMissingRegistersInLabel(labelInstr)) {
                boolean skip = false;
                for (int i = 0; i < block.getPredecessorCount(); i++) {
                    var pred = block.getPredecessorAt(i);
                    if (worklist.contains(pred)) {
                        skip = true;
                        worklist.add(block);
                        break;
                    }
                }

                if (skip) {
                    continue;
                }

                this.fromJumpResolver.resolvePhi(block);
            }

            // Create new entry state for successor blocks out of current block state
            var state = new MergedBlockVerifierState(this.blockEntryStates.get(block), registerAllocationConfig, phiResolution, constantMaterializationConflictResolver, labelConflictResolver);
            for (var instr : instructions) {
                state.update(instr, block);
            }
            this.blockStates.put(block, state);

            // if (this.phiResolution == PhiResolution.FromJump) {
            //     this.fromJumpResolver.resolvePhiFromJump(block);
            // }

            if (resolvedPhi) {
                this.fromPredecessorsResolver.propagateLabelChangeFromPredecessors(block);
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);

                MergedBlockVerifierState succState;
                if (this.blockEntryStates.get(succ) == null) {
                    succState = new MergedBlockVerifierState(registerAllocationConfig, phiResolution, constantMaterializationConflictResolver, labelConflictResolver);

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

            RAVInstruction.Op labelInstrOfSucc = null;
            if (block.getSuccessorCount() == 1) {
                labelInstrOfSucc = (RAVInstruction.Op) this.blockInstructions.get(block.getSuccessorAt(0)).getFirst();
            }

            for (var instr : instructions) {
                state.check(instr, block, labelInstrOfSucc);
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
        this.constantMaterializationConflictResolver.prepare(lir, blockInstructions);
        if (this.phiResolution == PhiResolution.FromConflicts) {
            this.labelConflictResolver.prepare(lir, blockInstructions);
        }

        if (this.phiResolution == PhiResolution.FromUsage) {
            this.fromUsageResolver.resolvePhiFromUsage();
        }

        if (this.phiResolution == PhiResolution.FromUsageGlobal) {
            this.fromUsageResolverGlobal.resolvePhiFromUsage();
        }

        this.calculateEntryBlocks();
        this.verifyInstructionInputs();
    }
}
