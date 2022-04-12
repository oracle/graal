package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

public class LoadLocalInstruction extends Instruction {

    private final LoadLocalInstruction init;

    public LoadLocalInstruction(int id) {
        super("load.local", id, ResultType.STACK_VALUE, InputType.LOCAL);
        this.init = this;
    }

    public LoadLocalInstruction(int id, LoadLocalInstruction init) {
        super("load.local.uninit", id, ResultType.STACK_VALUE, InputType.LOCAL);
        this.init = init;
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

            b.startStatement().startCall("doSetInputBoxed");
            b.variable(vars.bci);
            b.end(2);

            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField));
        }

        b.startStatement().startCall(vars.frame, "copy");

        b.startGroup();
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end();
        b.string(" + VALUES_OFFSET");
        b.end();

        b.startGroup().variable(vars.sp).string("++").end();

        b.end(2);

        return b.build();
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars) {
        if (this == init) {
            return null;
        } else {
            return OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField);
        }
    }

    @Override
    public CodeTree createSetInputBoxed(ExecutionVariables vars, CodeTree index) {
        return null;
    }
}
