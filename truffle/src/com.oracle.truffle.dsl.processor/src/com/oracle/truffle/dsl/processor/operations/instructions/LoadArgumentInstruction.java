package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class LoadArgumentInstruction extends Instruction {

    public LoadArgumentInstruction(int id) {
        super("load.argument", id, ResultType.STACK_VALUE, InputType.ARGUMENT);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().startCall(vars.frame, "setObject");
        b.variable(vars.sp);
        b.startGroup();
        b.startCall(vars.frame, "getArguments").end();
        b.string("[");
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end();
        b.string("]").end();
        b.end(2);

        b.startAssign(vars.sp).variable(vars.sp).string(" + 1").end();

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
    public boolean isResultAlwaysBoxed() {
        return true;
    }
}
