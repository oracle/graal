package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class DiscardInstruction extends Instruction {
    public DiscardInstruction(String name, int id, InputType input) {
        super(name, id, new ResultType[0], input);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign(vars.sp).variable(vars.sp).string(" - 1").end();

        b.startStatement().startCall(vars.frame, "clear");
        b.variable(vars.sp);
        b.end(2);

        return b.build();
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