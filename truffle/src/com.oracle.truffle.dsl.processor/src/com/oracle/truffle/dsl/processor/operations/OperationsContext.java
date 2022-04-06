package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;

import com.oracle.truffle.dsl.processor.operations.SingleOperationData.MethodProperties;
import com.oracle.truffle.dsl.processor.operations.instructions.BranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.DiscardInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ResultType;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationEnterInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationExitInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.InstrumentationLeaveInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.LoadLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.StoreLocalInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.SuperInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.TransferInstruction;

public class OperationsContext {

    private int instructionId = 1;
    private int operationId = 1;

    public Instruction commonPop;
    public Instruction commonBranch;
    public Instruction commonBranchFalse;

    public final ArrayList<Instruction> instructions = new ArrayList<>();
    public final ArrayList<Operation> operations = new ArrayList<>();

    public OperationsContext() {
        createCommonInstructions();
        createBuiltinOperations();
    }

    private void createCommonInstructions() {
        commonPop = add(new DiscardInstruction("pop", instructionId++, InputType.STACK_VALUE_IGNORED));
        commonBranch = add(new BranchInstruction(instructionId++));
        commonBranchFalse = add(new ConditionalBranchInstruction(instructionId++));
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
        add(new Operation.Simple(this, "ConstObject", operationId++, 0, add(new TransferInstruction("load.constant", instructionId++, ResultType.STACK_VALUE, InputType.CONST_POOL))));
        add(new Operation.Simple(this, "LoadArgument", operationId++, 0, add(new TransferInstruction("load.argument", instructionId++, ResultType.STACK_VALUE, InputType.ARGUMENT))));
        add(new Operation.Simple(this, "LoadLocal", operationId++, 0, add(new LoadLocalInstruction(instructionId++))));
        add(new Operation.Simple(this, "StoreLocal", operationId++, 1, add(new StoreLocalInstruction(instructionId++))));
        add(new Operation.Simple(this, "Return", operationId++, 1, add(new TransferInstruction("return", instructionId++, ResultType.RETURN, InputType.STACK_VALUE))));

        add(new Operation.Instrumentation(this, operationId++,
                        add(new InstrumentationEnterInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++)),
                        add(new InstrumentationExitInstruction(instructionId++, true)),
                        add(new InstrumentationLeaveInstruction(instructionId++))));

// Instruction iSuper = add(new SuperInstruction(instructionId++, new Instruction[]{iConst,
// iStloc}));
    }

    public Instruction add(Instruction elem) {
        instructions.add(elem);
        return elem;
    }

    public Operation add(Operation elem) {
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
