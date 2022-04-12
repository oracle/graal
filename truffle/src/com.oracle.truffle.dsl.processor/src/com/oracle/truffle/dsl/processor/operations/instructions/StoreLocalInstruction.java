package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

public class StoreLocalInstruction extends Instruction {

    private final StoreLocalInstruction init;

    public StoreLocalInstruction(int id, StoreLocalInstruction init) {
        super("store.local.uninit", id, ResultType.SET_LOCAL, InputType.STACK_VALUE);
        this.init = init;
    }

    public StoreLocalInstruction(int id) {
        super("store.local", id, ResultType.SET_LOCAL, InputType.STACK_VALUE);
        this.init = this;
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (this != init) {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            // TODO lock

            b.startStatement().startCall("doSetResultBoxed");
            b.variable(vars.bci);
            b.string("0");
            b.end(2);

            b.startStatement().startCall(vars.frame, "setObject");

            b.startGroup();
            b.startCall("LE_BYTES", "getShort");
            b.variable(vars.bc);
            b.startGroup().variable(vars.bci).string(" + 1").end();
            b.end();
            b.string(" + VALUES_OFFSET");
            b.end();

            b.startCall(vars.frame, "getValue");
            b.startGroup().variable(vars.sp).string(" - 1").end();
            b.end();

            b.end(2);

        } else {

            b.startAssert().startCall(vars.frame, "isObject");
            b.startGroup().variable(vars.sp).string(" - 1").end();
            b.end(2);

            b.startStatement().startCall(vars.frame, "copy");

            b.startGroup().variable(vars.sp).string(" - 1").end();

            b.startGroup();
            b.startCall("LE_BYTES", "getShort");
            b.variable(vars.bc);
            b.startGroup().variable(vars.bci).string(" + 1").end();
            b.end();
            b.string(" + VALUES_OFFSET");
            b.end();

            b.end(2);
        }

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
    public CodeTree createSetInputBoxed(ExecutionVariables vars, CodeTree index) {
        if (this == init) {
            return null;
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssert().tree(index).string(" == 0 : \"invalid index\"").end();
        b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField));

        return b.build();
    }
}
