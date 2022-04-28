package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadLocalInstruction extends Instruction {

    private final OperationsContext ctx;
    private final FrameKind kind;

    public LoadLocalInstruction(OperationsContext ctx, int id, FrameKind kind) {
        super("load.local." + kind.getTypeName().toLowerCase(), id, ResultType.STACK_VALUE, InputType.LOCAL);
        this.ctx = ctx;
        this.kind = kind;
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind == FrameKind.OBJECT) {
            b.startStatement().startCall(vars.frame, "copy");
        } else {
            b.startAssign("Object value");
            b.startCall(vars.frame, "getObject");
        }

        b.startGroup();
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end();
        b.string(" + VALUES_OFFSET");
        b.end();

        if (kind == FrameKind.OBJECT) {
            b.variable(vars.sp);
        }

        b.end(2);

        if (kind != FrameKind.OBJECT) {
            b.startIf().string("value instanceof " + kind.getTypeNameBoxed()).end().startBlock();
            {
                b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
                b.variable(vars.sp);
                b.string("(", kind.getTypeName(), ") value");
                b.end(2);
            }
            b.end().startElseBlock();
            {
                b.startStatement().startCall(vars.frame, "setObject");
                b.variable(vars.sp);
                b.string("value");
                b.end(2);
            }
            b.end();
        }

        b.startStatement().variable(vars.sp).string("++").end();

        return b.build();
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars, CodeVariableElement varBoxed, CodeVariableElement varTargetType) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind == FrameKind.OBJECT) {
            b.startIf().string("!").variable(varBoxed).end().startBlock();

            boolean elseIf = false;
            for (FrameKind okind : ctx.getData().getFrameKinds()) {
                if (okind == FrameKind.OBJECT) {
                    continue;
                }

                elseIf = b.startIf(elseIf);
                b.variable(varTargetType).string(" == FRAME_TYPE_" + okind.getFrameName().toUpperCase()).end().startBlock();

                b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, ctx.loadLocalInstructions[okind.ordinal()].opcodeIdField));

                b.end();
            }

            b.end();
        } else {
            b.startIf().variable(varBoxed).end().startBlock();

            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, ctx.loadLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField));

            b.end();
        }

        return b.build();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[]{
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_LOAD_LOCAL"),
                        CodeTreeBuilder.singleString("LE_BYTES.getShort(bc, bci + " + getArgumentOffset(0) + ")")
        };
    }
}
