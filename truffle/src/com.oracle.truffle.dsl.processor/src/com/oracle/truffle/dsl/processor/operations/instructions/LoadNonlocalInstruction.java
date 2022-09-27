package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class LoadNonlocalInstruction extends Instruction {

    public LoadNonlocalInstruction(int id) {
        super("load.nonlocal", id, 1);
        addPopSimple("frame");
        addArgument("index");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration(types.Frame, "outerFrame", (CodeTree) null);

        b.startAssign("outerFrame").cast(types.Frame).startCall("UFA", "unsafeGetObject");
        b.variable(vars.stackFrame);
        b.string("$sp - 1");
        b.end(2);

        b.startStatement().startCall("UFA", "unsafeSetObject");
        b.variable(vars.stackFrame);
        b.string("$sp - 1");
        b.startCall("outerFrame", "getObject");
        b.tree(createArgumentIndex(vars, 0, false));
        b.end();
        b.end(2);

        return b.build();
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.DO_NOTHING;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

}
