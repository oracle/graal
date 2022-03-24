package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;

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

        b.declaration(OperationGeneratorUtils.getTypes().ProbeNode, "probe",
                        CodeTreeBuilder.createBuilder().variable(vars.probeNodes) //
                                        .string("[").variable(vars.inputs[0]).string("].") //
                                        .startCall("getTreeProbeNode").end(2).build());

        b.startIf().string("probe != null").end();
        b.startBlock();

        b.startStatement();
        b.startCall("probe", "onReturnValue");
        b.variable(vars.frame);
        if (returnsValue) {
            b.variable(vars.inputs[1]);
        } else {
            b.string("null");
        }
        b.end(2);

        b.end();

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
