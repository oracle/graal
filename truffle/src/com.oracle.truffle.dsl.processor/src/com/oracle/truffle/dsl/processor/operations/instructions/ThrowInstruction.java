package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

public class ThrowInstruction extends Instruction {
    public ThrowInstruction(int id) {
        super("throw", id, new ResultType[0], InputType.LOCAL);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        // TODO since we do not have a typecheck in a catch
        // we can convert this to a jump to a statically determined handler
        // or a throw out of a function

        b.startAssign("int slot").startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end(2);

        b.startThrow();
        b.cast(ProcessorContext.getInstance().getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"));
        b.startCall(vars.frame, "getObject").string("slot").end();
        b.end();

        return b.build();
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
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
