package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class StoreNonlocalInstruction extends Instruction {

    public StoreNonlocalInstruction(OperationsContext ctx, int id) {
        super(ctx, "store.nonlocal", id, 0);
        addPopSimple("frame");
        addPopSimple("value");
        addArgument("index");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration(types.Frame, "outerFrame", (CodeTree) null);

        b.startAssign("outerFrame").cast(types.Frame).startCall("UFA", "unsafeGetObject");
        b.variable(vars.stackFrame);
        b.string("$sp - 2");
        b.end(2);

        b.startStatement().startCall("outerFrame", "setObject");
        b.tree(createArgumentIndex(vars, 0, false));
        b.startCall("UFA", "unsafeGetObject").variable(vars.stackFrame).string("$sp - 1").end();
        b.end(2);

        b.statement("$sp -= 2");

        return b.build();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

}
