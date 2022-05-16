package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;

public class ShortCircuitInstruction extends CustomInstruction {

    public ShortCircuitInstruction(String name, int id, SingleOperationData data) {
        super(name, id, data, new ResultType[]{ResultType.BRANCH}, new InputType[]{InputType.STACK_VALUE, InputType.BRANCH_TARGET});
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.DO_NOTHING;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        createTracerCode(vars, b);

        b.startIf();
        if (!getData().getShortCircuitContinueWhen()) {
            b.string("!");
        }

        b.startCall("this", executeMethod);
        b.variable(vars.frame);
        b.variable(vars.bci);
        b.variable(vars.sp);
        b.end(2).startBlock();
        {
            b.startAssign(vars.sp).variable(vars.sp).string(" - 1").end();
            b.startAssign(vars.bci).variable(vars.bci).string(" + " + length()).end();
            b.statement("continue loop");
        }
        b.end().startElseBlock();
        {
            b.startAssign(vars.bci).tree(createReadArgumentCode(1, vars)).end();

            b.statement("continue loop");
        }
        b.end();

        return b.build();
    }

}
