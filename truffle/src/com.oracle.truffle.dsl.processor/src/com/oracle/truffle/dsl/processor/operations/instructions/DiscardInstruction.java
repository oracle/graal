package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ResultType;

public class DiscardInstruction extends Instruction {
    public DiscardInstruction(String name, int id, InputType input) {
        super(name, id, new ResultType[0], input);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        return null;
    }
}