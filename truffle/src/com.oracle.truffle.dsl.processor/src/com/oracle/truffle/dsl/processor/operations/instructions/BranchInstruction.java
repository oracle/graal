package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class BranchInstruction extends Instruction {

    private static final ProcessorContext context = ProcessorContext.getInstance();
    private static final DeclaredType TRUFFLE_SAFEPOINT = context.getDeclaredType("com.oracle.truffle.api.TruffleSafepoint");
    private static final DeclaredType BYTECODE_OSR_NODE = context.getDeclaredType("com.oracle.truffle.api.nodes.BytecodeOSRNode");

    public BranchInstruction(int id) {
        super("branch", id, ResultType.BRANCH, InputType.BRANCH_TARGET);
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    private CodeTree createGetBranchTarget(ExecutionVariables vars) {
        return CodeTreeBuilder.createBuilder().startCall("LE_BYTES", "getShort")//
                        .variable(vars.bc) //
                        .startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end() //
                        .end().build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration("int", "targetBci", createGetBranchTarget(vars));

        b.startIf().string("targetBci <= ").variable(vars.bci).end().startBlock();
        {
            b.startStatement().startStaticCall(TRUFFLE_SAFEPOINT, "poll");
            b.string("this");
            b.end(2);

            // TODO reporting loop count

            b.startIf();
            b.tree(GeneratorUtils.createInInterpreter());
            b.string(" && ");
            b.startStaticCall(BYTECODE_OSR_NODE, "pollOSRBackEdge").string("this").end();
            b.end().startBlock();
            {
                b.startAssign("Object osrResult").startStaticCall(BYTECODE_OSR_NODE, "tryOSR");
                b.string("this");
                b.string("targetBci");
                b.variable(vars.sp);
                b.string("null");
                b.variable(vars.frame);
                b.end(2);

                b.startIf().string("osrResult != null").end().startBlock();
                {
                    b.startReturn().string("osrResult").end();
                }
                b.end();
            }
            b.end();
        }
        b.end();

        b.startAssign(vars.bci).string("targetBci").end();

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

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[]{
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_BRANCH"),
                        createGetBranchTarget(vars)
        };
    }
}