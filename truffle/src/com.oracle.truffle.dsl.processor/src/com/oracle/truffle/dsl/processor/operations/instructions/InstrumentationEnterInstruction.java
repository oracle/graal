package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class InstrumentationEnterInstruction extends Instruction {

    public InstrumentationEnterInstruction(int id) {
        super("instrument.enter", id, new ResultType[0], InputType.INSTRUMENT);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("ProbeNode probe");
        b.variable(vars.probeNodes).string("[").variable(vars.inputs[0]).string("].");
        b.startCall("getTreeProbeNode");
        b.end(2);

        b.startIf().string("probe != null").end();
        b.startBlock();

        b.startStatement();
        b.startCall("probe", "onEnter");
        b.variable(vars.frame);
        b.end(2);

        b.end();

        return b.build();
    }

    @Override
    public boolean isInstrumentationOnly() {
        return true;
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars, CodeVariableElement varBoxed, CodeVariableElement varTargetType) {
        return null;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }
}
