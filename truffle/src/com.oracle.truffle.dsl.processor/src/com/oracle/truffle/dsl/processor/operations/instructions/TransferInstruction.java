package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ResultType;

public class TransferInstruction extends Instruction {
    public TransferInstruction(String name, int id, ResultType result, InputType input) {
        super(name, id, result, input);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        return CodeTreeBuilder.createBuilder().startAssign(vars.results[0]).variable(vars.inputs[0]).end().build();
    }
}