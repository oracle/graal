package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

public class ReturnInstruction extends Instruction {

    private final ReturnInstruction init;

    public ReturnInstruction(int id, ReturnInstruction init) {
        super("return.uninit", id, ResultType.RETURN, InputType.STACK_VALUE);
        this.init = init;
    }

    public ReturnInstruction(int id) {
        super("return", id, ResultType.RETURN, InputType.STACK_VALUE);
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

            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField));
        }

        b.startAssign(vars.returnValue).startCall(vars.frame, this == init ? "getObject" : "getValue");
        b.startGroup().variable(vars.sp).string(" - 1").end();
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
