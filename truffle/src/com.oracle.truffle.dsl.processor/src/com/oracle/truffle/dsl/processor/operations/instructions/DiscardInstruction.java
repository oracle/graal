package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;

public class DiscardInstruction extends Instruction {
    public DiscardInstruction(String name, int id, InputType input) {
        super(name, id, new ResultType[0], input);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        return null;
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars) {
        return null;
    }

    @Override
    public CodeTree createSetInputBoxed(ExecutionVariables vars, int index) {
        return null;
    }
}