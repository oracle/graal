package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;

import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.instructions.BranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.DiscardInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationEnterInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationExitInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationLeaveInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadArgumentInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadConstantInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadConstantInstruction.ConstantKind;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ReturnInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.StoreLocalInstruction;

public class OperationsContext {

    private int instructionId = 257;
    private int operationId = 1;

    public Instruction commonPop;

    public Instruction commonBranch;

    public Instruction commonBranchFalse;
    public Instruction commonBranchFalseBoxed;

    public final ArrayList<Instruction> instructions = new ArrayList<>();
    public final ArrayList<Operation> operations = new ArrayList<>();

    public OperationsContext() {
        createCommonInstructions();
        createBuiltinOperations();
    }

    private void createCommonInstructions() {
        commonPop = add(new DiscardInstruction("pop", instructionId++, InputType.STACK_VALUE_IGNORED));

        commonBranch = add(new BranchInstruction(instructionId++));

        commonBranchFalse = add(new ConditionalBranchInstruction(this, instructionId++, false));
        commonBranchFalseBoxed = add(new ConditionalBranchInstruction(this, instructionId++, true));
    }

    private void createBuiltinOperations() {
        add(new Operation.Block(this, operationId++));
        add(new Operation.IfThen(this, operationId++));
        add(new Operation.IfThenElse(this, operationId++, false));
        add(new Operation.IfThenElse(this, operationId++, true));
        add(new Operation.While(this, operationId++));
        add(new Operation.TryCatch(this, operationId++));

// Instruction iConst;
// Instruction iStloc;

        add(new Operation.Label(this, operationId++));
        add(new Operation.Simple(this, "Branch", operationId++, 0, commonBranch));

        createLoadConstant();
        createLoadArgument();
        createLoadStoreLocal();
        createReturn();

        add(new Operation.Instrumentation(this, operationId++,
                        add(new InstrumentationEnterInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++, true)),
                        add(new InstrumentationLeaveInstruction(instructionId++))));

// Instruction iSuper = add(new SuperInstruction(instructionId++, new Instruction[]{iConst,
// iStloc}));
    }

    private void createLoadStoreLocal() {
        StoreLocalInstruction slInit = add(new StoreLocalInstruction(instructionId++));
        StoreLocalInstruction slUninit = add(new StoreLocalInstruction(instructionId++, slInit));
        add(new Operation.Simple(this, "StoreLocal", operationId++, 1, slUninit));

        add(new Operation.Simple(this, "LoadLocal", operationId++, 0, add(new LoadLocalInstruction(instructionId++))));
    }

    private void createLoadArgument() {
        LoadArgumentInstruction ldargInit = add(new LoadArgumentInstruction(instructionId++));
        LoadArgumentInstruction ldargUninit = add(new LoadArgumentInstruction(instructionId++, ldargInit));
        add(new Operation.Simple(this, "LoadArgument", operationId++, 0, ldargUninit));
    }

    private void createLoadConstant() {
        LoadConstantInstruction loadObject = add(new LoadConstantInstruction(instructionId++, false, ConstantKind.OBJECT, null));

        LoadConstantInstruction[] instrs = new LoadConstantInstruction[ConstantKind.values().length];
        LoadConstantInstruction[] instrsBoxed = new LoadConstantInstruction[ConstantKind.values().length];

        for (ConstantKind kind : ConstantKind.values()) {
            if (kind.isSingleByte()) {
                instrsBoxed[kind.ordinal()] = add(new LoadConstantInstruction(instructionId++, true, kind, null));
            } else {
                instrsBoxed[kind.ordinal()] = loadObject;
            }
        }

        for (ConstantKind kind : ConstantKind.values()) {
            if (kind == ConstantKind.OBJECT) {
                instrs[kind.ordinal()] = loadObject;
            } else {
                instrs[kind.ordinal()] = add(new LoadConstantInstruction(instructionId++, false, kind, instrsBoxed[kind.ordinal()]));
            }
        }

        add(new Operation.LoadConstant(this, operationId++, instrs));
    }

    private void createReturn() {
        ReturnInstruction retInit = add(new ReturnInstruction(instructionId++));
        ReturnInstruction retUninit = add(new ReturnInstruction(instructionId++, retInit));
        add(new Operation.Simple(this, "Return", operationId++, 1, retUninit));
    }

    public <T extends Instruction> T add(T elem) {
        instructions.add(elem);
        return elem;
    }

    public <T extends Operation> T add(T elem) {
        operations.add(elem);
        return elem;
    }

    public int getNextInstructionId() {
        return instructionId++;
    }

    public int getNextOperationId() {
        return operationId++;
    }

    public void processOperation(SingleOperationData opData) {

        MethodProperties props = opData.getMainProperties();

        if (props == null) {
            opData.addError("Operation %s not initialized", opData.getName());
            return;
        }

        CustomInstruction instr = new CustomInstruction("custom." + opData.getName(), getNextInstructionId(), opData);
        add(instr);

        int numChildren = props.isVariadic ? -1 : props.numStackValues;

        Operation.Simple op = new Operation.Simple(this, opData.getName(), getNextOperationId(), numChildren, instr);
        add(op);
    }
}
