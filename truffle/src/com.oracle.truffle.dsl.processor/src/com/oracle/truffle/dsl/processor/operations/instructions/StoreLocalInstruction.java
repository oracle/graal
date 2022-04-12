package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class StoreLocalInstruction extends Instruction {

    public StoreLocalInstruction(int id) {
        super("store.local", id, ResultType.SET_LOCAL, InputType.STACK_VALUE);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssert().startCall(vars.frame, "isObject");
        b.startGroup().variable(vars.sp).string(" - 1").end();
        b.end(2);

        b.startStatement().startCall(vars.frame, "copy");

        b.startGroup().variable(vars.sp).string(" - 1").end();

        b.startGroup();
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + opcodeLength()).end();
        b.end();
        b.string(" + VALUES_OFFSET");
        b.end();

        b.end(2);

        b.startStatement().startCall(vars.frame, "clear");
        b.startGroup().string("--").variable(vars.sp).end();
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

    @Override
    public boolean isInputAlwaysBoxed(int index) {
        return true;
    }
}
