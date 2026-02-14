package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
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
import java.util.List;
import java.util.Map;

public class PreRegisterAllocationPhase extends AllocationPhase {
    protected TaggedConstantFactory taggedConstantFactory;
    protected RegisterAllocationVerifierPhaseState state;

    public PreRegisterAllocationPhase(RegisterAllocationVerifierPhaseState state) {
        this.taggedConstantFactory = new TaggedConstantFactory();
        this.state = state;
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

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        if (!state.shouldBeVerified(lirGenRes)) {
            return;
        }

        var preallocMap = state.createInstructionMap(lirGenRes);
        LIR lir = lirGenRes.getLIR();
        Map<Variable, ConstantValue> constantValueMap = new HashMap<>();
        var constOverwriteProc = new ConstantOverrideValueProcedure(lir, constantValueMap);

        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            List<Variable> newVars = new ArrayList<>();
            constOverwriteProc.setVariableList(newVars);

            RAVInstruction.Base previousInstr = null;
            for (var instruction : instructions) {
                if (instruction instanceof StandardOp.JumpOp && this.state.phiResolution == PhiResolution.FromJump) {
                    if (this.state.moveConstants) {
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
                instruction.forEachState(opRAVInstr.stateValues.copyOriginalProc);
                instruction.forEachState(new InstructionStateProcedure() {
                    @Override
                    public void doState(LIRInstruction instruction, LIRFrameState state) {
                        if (state.topFrame == null) {
                            return;
                        }

                        // Haven't found a case where there is multiple frame states on an instruction
                        // so this will work, otherwise appending them would do the job in that case
                        // if we could also get this information about VirtualObjects.
                        opRAVInstr.kinds = state.topFrame.getSlotKinds();
                    }
                });

                preallocMap.put(instruction, opRAVInstr);

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
                preallocMap.put(mov, ravInstr);
                instructions.add(mov);
            }

            instructions.add(j);
        }
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
