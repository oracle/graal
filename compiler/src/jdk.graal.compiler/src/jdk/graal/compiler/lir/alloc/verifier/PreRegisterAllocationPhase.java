package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.InstructionStateProcedure;
import jdk.graal.compiler.lir.InstructionValueProcedure;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.ValueProcedure;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreRegisterAllocationPhase extends AllocationPhase {
    protected Map<LIRInstruction, RAVInstruction.Base> preallocMap;
    protected PhiResolution phiResolution;
    protected TaggedConstantFactory taggedConstantFactory;
    protected boolean moveConstants;

    protected PreRegisterAllocationPhase(PhiResolution phiResolution, boolean moveConstants) {
        this.preallocMap = new HashMap<>();
        this.phiResolution = phiResolution;
        this.taggedConstantFactory = new TaggedConstantFactory();
        this.moveConstants = moveConstants;
    }

    public static class ConstantOverrideValueProcedure implements ValueProcedure {
        private LIR lir;
        private List<Variable> variables;
        private Map<Variable, ConstantValue> constantValueMap;
        private Method nextVariableMethod;

        public ConstantOverrideValueProcedure(LIR lir, Map<Variable, ConstantValue> constantValueMap) {
            this.lir = lir;
            this.constantValueMap = constantValueMap;

            try {
                this.nextVariableMethod = LIRGenerator.VariableProvider.class.getDeclaredMethod("nextVariable");
                this.nextVariableMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public void setVariableList(List<Variable> variables) {
            this.variables = variables;
        }

        public int nextVariable() {
            try {
                return (int) nextVariableMethod.invoke((LIRGenerator.VariableProvider) this.lir);
            } catch (InvocationTargetException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            if (value instanceof ConstantValue constant) {
                var variable = new Variable(constant.getValueKind(), nextVariable());
                constantValueMap.put(variable, constant);
                variables.add(variable);
                return variable;
            }

            return value;
        }
    }

    protected InstructionValueProcedure taggedConstantValueProc = new InstructionValueProcedure() {
        @Override
        public Value doValue(LIRInstruction instruction, Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            if (value instanceof ConstantValue constantValue) {
                return new ConstantValue(constantValue.getValueKind(), taggedConstantFactory.createNew(constantValue.getConstant()));
            }

            return value;
        }
    };

    public Map<LIRInstruction, RAVInstruction.Base> getPreallocMap() {
        return preallocMap;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        var compUnitName = lirGenRes.getCompilationUnitName();
        if (RegisterAllocationVerifierPhase.isIgnored(compUnitName)) {
            return;
        }

        LIR lir = lirGenRes.getLIR();

        Map<Variable, ConstantValue> constantValueMap = new HashMap<>();
        var constOverwriteProc = new ConstantOverrideValueProcedure(lir, constantValueMap);

        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            List<Variable> newVars = new LinkedList<>();
            constOverwriteProc.setVariableList(newVars);

            RAVInstruction.Base previousInstr = null;
            for (var instruction : instructions) {
                if (instruction instanceof StandardOp.JumpOp && this.phiResolution == PhiResolution.FromJump) {
                    if (this.moveConstants) {
                        instruction.forEachAlive(constOverwriteProc);
                    } else {
                        instruction.forEachAlive(taggedConstantValueProc);
                    }
                }

                if (this.isVirtualMove(instruction)) {
                    // Virtual moves (variable = MOV real register) are going to be removed by the allocator,
                    // but we still need the information about which variables are associated to which real
                    // registers, and so we store them. They are generally associated to other instructions
                    // that's why we append them here to the previous instruction (for example Label or Foreign Call)
                    // use these, if this instruction was deleted in the allocator, then they will be missing too.
                    assert previousInstr != null;

                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
                    var location = valueMov.getInput();
                    var variable = LIRValueUtil.asVariable(valueMov.getResult());

                    var virtualMove = new RAVInstruction.VirtualMove(instruction, variable, location);
                    previousInstr.addVirtualMove(virtualMove);
                    continue; // No need to store virtual move here, it is stored into previous instruction.
                }

                boolean speculative = false;
                if (this.isSpeculativeMove(instruction)) {
                    speculative = true;
                    // Speculative moves are in form ry = MOVE vx, which could be removed if variable
                    // ends up being allocated to the same register as ry. If it was removed
                    // we need to re-add it because it holds important information about where value of
                    // this variable is placed - for label resolution after the label.
                    assert previousInstr != null;

                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
                    var variable = LIRValueUtil.asVariable(valueMov.getInput());
                    var register = valueMov.getResult();

                    var virtualMove = new RAVInstruction.VirtualMove(instruction, variable, register);
                    previousInstr.addSpeculativeMove(virtualMove);
                }

                var opRAVInstr = new RAVInstruction.Op(instruction);

                instruction.forEachInput(opRAVInstr.uses.copyOriginalProc);
                instruction.forEachOutput(opRAVInstr.dests.copyOriginalProc);
                instruction.forEachTemp(opRAVInstr.temp.copyOriginalProc);
                instruction.forEachAlive(opRAVInstr.alive.copyOriginalProc);

                this.preallocMap.put(instruction, opRAVInstr);

                if (!speculative) {
                    previousInstr = opRAVInstr;
                }
            }

            if (newVars.isEmpty()) {
                continue;
            }

            var j = instructions.removeLast();
            var it = newVars.iterator();
            while (it.hasNext()) {
                var v = LIRValueUtil.asVariable(it.next());
                var mov = context.spillMoveFactory.createMove(v, constantValueMap.get(v));
                var ravInstr = new RAVInstruction.Op(mov);
                mov.forEachOutput(ravInstr.dests.copyOriginalProc);
                this.preallocMap.put(mov, ravInstr);
                instructions.add(mov);
            }

            instructions.add(j);
        }
    }

    public BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir) {
        Set<LIRInstruction> presentInstructions = new HashSet<>();
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            presentInstructions.addAll(instructions);
        }

        BlockMap<List<RAVInstruction.Base>> blockInstructions = new BlockMap<>(lir.getControlFlowGraph());
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            var instructionList = new LinkedList<RAVInstruction.Base>();

            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            for (var instruction : instructions) {
                var rAVInstr = preallocMap.get(instruction);
                if (rAVInstr == null) {
                    var movOp = this.getRAVMoveInstruction(instruction);
                    if (movOp != null) {
                        instructionList.add(movOp);
                        continue;
                    }

                    throw new VerErr.UnknownInstructionError(instruction, block);
                }

                var opRAVInstr = (RAVInstruction.Op) rAVInstr;

                instruction.forEachInput(opRAVInstr.uses.copyCurrentProc);
                instruction.forEachOutput(opRAVInstr.dests.copyCurrentProc);
                instruction.forEachTemp(opRAVInstr.temp.copyCurrentProc);
                instruction.forEachAlive(opRAVInstr.alive.copyCurrentProc);
                instruction.forEachState(new InstructionStateProcedure() {
                    @Override
                    public void doState(LIRInstruction instruction, LIRFrameState state) {

                    }
                });

                instructionList.add(opRAVInstr);
                var speculativeMoves = opRAVInstr.getSpeculativeMoveList();

                if (!speculativeMoves.isEmpty()) {
                    for (var speculativeMove : speculativeMoves) {
                        if (!presentInstructions.contains(speculativeMove.getLIRInstruction())) {
                            instructionList.add(speculativeMove);
                        }
                    }
                }

                var virtualMoves = opRAVInstr.getVirtualMoveList();
                instructionList.addAll(virtualMoves);
            }

            blockInstructions.put(block, instructionList);
        }
        return blockInstructions;
    }

    /**
     * Create Register Verifier Instruction that was created by the Register Allocator.
     * Generally speaking, it's always a move instruction, other ones return null.
     *
     * @param instruction LIRInstruction newly created by Register Allocator
     * @return Spill, Reload, Move or null if instruction is not a move
     */
    protected RAVInstruction.Base getRAVMoveInstruction(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            if (instruction.isLoadConstantOp()) {
                var constatLoad = StandardOp.LoadConstantOp.asLoadConstantOp(instruction);
                var constant = constatLoad.getConstant();
                var result = constatLoad.getResult(); // Can be RegisterValue or VirtualStackSlot

                // This isn't really a virtual move, but it currently acts the same, so we keep it,
                // we take constants as variables. TODO: maybe remove virtual move altogether for Move(reg, var/constant)
                return new RAVInstruction.VirtualMove(instruction, new ConstantValue(result.getValueKind(), constant), result);
            }

            return null;
        }
        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);

        var input = valueMov.getInput();
        var result = valueMov.getResult();

        if (input instanceof VirtualStackSlot stackSlot && result instanceof RegisterValue reg) {
            return new RAVInstruction.Reload(instruction, reg, stackSlot);
        } else if (result instanceof VirtualStackSlot stackSlot && input instanceof RegisterValue reg) {
            return new RAVInstruction.Spill(instruction, stackSlot, reg);
        } else if (input instanceof RegisterValue reg1 && result instanceof RegisterValue reg2) {
            return new RAVInstruction.Move(instruction, reg1, reg2);
        } else if (input instanceof StackSlot stackSlot && result instanceof RegisterValue reg) {
            return new RAVInstruction.Reload(instruction, reg, stackSlot);
        } else if (input instanceof RegisterValue reg && result instanceof StackSlot stackSlot) {
            return new RAVInstruction.Spill(instruction, stackSlot, reg);
        }

        if (input instanceof StackSlot stackSlot1 && result instanceof StackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof VirtualStackSlot stackSlot1 && result instanceof VirtualStackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof StackSlot stackSlot1 && result instanceof VirtualStackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof VirtualStackSlot stackSlot1 && result instanceof StackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        }

        return null;
    }

    /**
     * Determines if instruction is a virtual move, a virtual move is
     * a move instruction that moves a real register value into a variable,
     * which is something that will always get removed from the final allocated
     * IR.
     *
     * @param instruction LIRInstruction we are looking at
     * @return true, if instruction is a virtual move, otherwise false
     */
    protected boolean isVirtualMove(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            return false;
        }

        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
        var input = valueMov.getInput();
        return (input instanceof RegisterValue || input instanceof StackSlot /*|| input instanceof AbstractAddress*/) && LIRValueUtil.isVariable(valueMov.getResult());
    }

    protected boolean isSpeculativeMove(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            return false;
        }

        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
        var result = valueMov.getResult(); // Result could be variable or register
        return (result instanceof RegisterValue || LIRValueUtil.isVariable(result)) && LIRValueUtil.isVariable(valueMov.getInput());
    }
}
