package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class InstrumentationLeaveInstruction extends Instruction {
    public InstrumentationLeaveInstruction(int id) {
        super("instrument.leave", id, ResultType.BRANCH, InputType.INSTRUMENT, InputType.BRANCH_TARGET, InputType.BRANCH_TARGET);
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

        b.startAssign("Object result");
        b.startCall("probe", "onReturnExceptionalOrUnwind");
        b.variable(vars.frame);
        b.string("null");
        b.string("false");
        b.end(2);

        b.startIf().string("result == ProbeNode.UNWIND_ACTION_REENTER").end();
        b.startBlock();

        b.startAssign(vars.results[0]).variable(vars.inputs[1]).end();

        b.end().startElseIf().string("result != null").end();
        b.startBlock();

        // HACK, refactor this push somehow
        b.startStatement().startCall(vars.frame, "setObject").string("sp++").string("result").end(2);
        b.startAssign(vars.results[0]).variable(vars.inputs[2]).end();

        b.end().startElseBlock();

        b.startAssign(vars.results[0]).variable(vars.bci).string(" + " + length()).end();

        b.end();
        b.end().startElseBlock();

        b.startAssign(vars.results[0]).variable(vars.bci).string(" + " + length()).end();

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
