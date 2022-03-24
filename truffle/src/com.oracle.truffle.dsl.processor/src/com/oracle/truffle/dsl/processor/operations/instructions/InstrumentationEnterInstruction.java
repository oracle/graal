package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class InstrumentationEnterInstruction extends Instruction {

    public InstrumentationEnterInstruction(int id) {
        super("instrument.enter", id, new ResultType[0], InputType.INSTRUMENT);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement();
        b.variable(vars.probeNodes).string("[").variable(vars.inputs[0]).string("].");
        b.startCall("onEnter");
        b.variable(vars.frame);
        b.end(2);

        return b.build();
    }

    @Override
    public boolean isInstrumentationOnly() {
        return true;
    }
}
