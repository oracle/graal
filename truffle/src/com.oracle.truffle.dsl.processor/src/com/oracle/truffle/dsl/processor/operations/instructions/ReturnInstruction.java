package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class ReturnInstruction extends Instruction {

    public ReturnInstruction(int id) {
        super("return", id, ResultType.RETURN, InputType.STACK_VALUE);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign(vars.returnValue).startCall(vars.frame, "getObject");
        b.startGroup().variable(vars.sp).string(" - 1").end();
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
