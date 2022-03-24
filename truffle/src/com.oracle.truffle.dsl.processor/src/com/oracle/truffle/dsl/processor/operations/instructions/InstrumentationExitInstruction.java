package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class InstrumentationExitInstruction extends Instruction {
    private final boolean returnsValue;

    public InstrumentationExitInstruction(int id) {
        super("instrument.exit.void", id, new ResultType[0], InputType.INSTRUMENT);
        this.returnsValue = false;
    }

    public InstrumentationExitInstruction(int id, boolean returnsValue) {
        super("instrument.exit", id, ResultType.STACK_VALUE, InputType.INSTRUMENT, InputType.STACK_VALUE);
        assert returnsValue;
        this.returnsValue = true;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement();
        b.variable(vars.probeNodes).string("[").variable(vars.inputs[0]).string("].");
        b.startCall("onReturnValue");
        b.variable(vars.frame);
        if (returnsValue) {
            b.variable(vars.inputs[1]);
        } else {
            b.string("null");
        }
        b.end(2);

        if (returnsValue) {
            b.startAssign(vars.results[0]).variable(vars.inputs[1]).end();
        }

        return b.build();
    }

    @Override
    public boolean isInstrumentationOnly() {
        return true;
    }
}
