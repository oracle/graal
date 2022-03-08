package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;

public class OperationsBuilder {

    private int instructionId = 1;
    private int operationId = 1;

    public Instruction commonPop;
    public Instruction commonBranch;
    public Instruction commonBranchFalse;

    public final ArrayList<Instruction> instructions = new ArrayList<>();
    public final ArrayList<Operation> operations = new ArrayList<>();

    public OperationsBuilder() {
        createCommonInstructions();
        createBuiltinOperations();
    }

    private void createCommonInstructions() {
        commonPop = add(new Instruction.Pop(instructionId++));
        commonBranch = add(new Instruction.Branch(instructionId++));
        commonBranchFalse = add(new Instruction.BranchFalse(instructionId++));
    }

    private void createBuiltinOperations() {
        add(new Operation.Block(this, operationId++));
        add(new Operation.IfThen(this, operationId++));
        add(new Operation.IfThenElse(this, operationId++, false));
        add(new Operation.IfThenElse(this, operationId++, true));
        add(new Operation.While(this, operationId++));

        add(new Operation.Label(this, operationId++));
        add(new Operation.Simple(this, "Branch", operationId++, 0, commonBranch));
        add(new Operation.Simple(this, "ConstObject", operationId++, 0, add(new Instruction.ConstObject(instructionId++))));
        add(new Operation.Simple(this, "LoadArgument", operationId++, 0, add(new Instruction.LoadArgument(instructionId++))));
        add(new Operation.Simple(this, "LoadLocal", operationId++, 0, add(new Instruction.LoadLocal(instructionId++))));
        add(new Operation.Simple(this, "StoreLocal", operationId++, 1, add(new Instruction.StoreLocal(instructionId++))));
        add(new Operation.Simple(this, "Return", operationId++, 1, add(new Instruction.Return(instructionId++))));
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
        // TODO Auto-generated method stub
        return instructionId++;
    }

    public int getNextOperationId() {
        // TODO Auto-generated method stub
        return operationId++;
    }
}
