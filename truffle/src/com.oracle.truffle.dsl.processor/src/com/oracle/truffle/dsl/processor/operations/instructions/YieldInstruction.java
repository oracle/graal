package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class YieldInstruction extends Instruction {

    private static final String CONTINUATION_POINT = "continuation-point";

    public YieldInstruction(int id) {
        super("yield", id, 1);
        addPopSimple("value");
        addConstant(CONTINUATION_POINT, null);
    }

    @Override
    protected CodeTree createCustomEmitCode(BuilderVariables vars, EmitArguments args) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startIf().string("yieldLocations == null").end().startBlock();
        b.statement("yieldLocations = new ContinuationLocationImpl[8]");
        b.end().startElseIf().string("yieldLocations.length <= yieldCount").end().startBlock();
        b.statement("yieldLocations = Arrays.copyOf(yieldLocations, yieldCount * 2)");
        b.end();
        return b.build();
    }

    @Override
    protected CodeTree createConstantInitCode(BuilderVariables vars, EmitArguments args, Object marker, int index) {
        if (!CONTINUATION_POINT.equals(marker) && index != 0) {
            throw new AssertionError("what constant?");
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.string("yieldLocations[yieldCount] = ");

        b.startNew("ContinuationLocationImpl");
        b.string("yieldCount++");
        b.startGroup().string("(curStack << 16) | (bci + ").tree(createLength()).string(")").end();
        b.end();
        return b.build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().string("ContinuationLocationImpl cont = (ContinuationLocationImpl) $consts[").tree(createConstantIndex(vars, 0)).string("]").end();
        b.statement("$frame.setObject($sp - 1, cont.createResult($frame, $frame.getObject($sp - 1)))");

        b.startReturn().string("(($sp - 1) << 16) | 0xffff").end();

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
