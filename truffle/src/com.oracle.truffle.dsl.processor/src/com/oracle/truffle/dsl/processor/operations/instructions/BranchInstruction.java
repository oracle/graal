package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class BranchInstruction extends Instruction {
    public BranchInstruction(int id) {
        super("branch", id, ResultType.BRANCH, InputType.BRANCH_TARGET);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign(vars.results[0]).variable(vars.inputs[0]).end();
        b.statement("continue loop");

        return b.build();
    }

    @Override
    public CodeTree createEmitCode(BuilderVariables vars, CodeTree[] arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        TruffleTypes types = ProcessorContext.getInstance().getTypes();

        b.startStatement().startCall("calculateLeaves");
        b.variable(vars.operationData);
        b.startGroup().cast(types.BuilderOperationLabel).tree(arguments[0]).end();
        b.end(2);

        b.tree(super.createEmitCode(vars, arguments));

        return b.build();
    }
}