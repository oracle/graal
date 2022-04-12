package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class LoadLocalInstruction extends Instruction {

    public LoadLocalInstruction(int id) {
        super("load.local", id, ResultType.STACK_VALUE, InputType.LOCAL);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().startCall(vars.frame, "copy");

        b.startGroup();
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + 1").end();
        b.end();
        b.string(" + VALUES_OFFSET");
        b.end();

        b.startGroup().variable(vars.sp).string("++").end();

        b.end(2);

        return b.build();
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars) {
        // TODO implement local (un)boxing
        return null;
    }

    @Override
    public CodeTree createSetInputBoxed(ExecutionVariables vars, CodeTree index) {
        return null;
    }
}
