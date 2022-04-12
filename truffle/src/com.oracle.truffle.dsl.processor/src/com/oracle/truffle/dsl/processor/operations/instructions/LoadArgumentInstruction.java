package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

public class LoadArgumentInstruction extends Instruction {

    private final LoadArgumentInstruction init;

    public LoadArgumentInstruction(int id, LoadArgumentInstruction init) {
        super("load.argument.uninit", id, ResultType.STACK_VALUE, InputType.ARGUMENT);
        this.init = init;
    }

    public LoadArgumentInstruction(int id) {
        super("load.argument", id, ResultType.STACK_VALUE, InputType.ARGUMENT);
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

            b.startStatement().startCall("doSetInputBoxed").variable(vars.bci).end(2);
            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField));
        }

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
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, init.opcodeIdField));

        return b.build();
    }

    @Override
    public CodeTree createSetInputBoxed(ExecutionVariables vars, CodeTree index) {
        return null;
    }

}
