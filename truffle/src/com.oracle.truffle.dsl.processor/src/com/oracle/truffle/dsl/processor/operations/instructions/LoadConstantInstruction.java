package com.oracle.truffle.dsl.processor.operations.instructions;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadConstantInstruction extends Instruction {
    private final FrameKind kind;
    private final OperationsContext ctx;

    public LoadConstantInstruction(OperationsContext ctx, int id, FrameKind kind) {
        super("load.constant." + kind.toString().toLowerCase(), id, ResultType.STACK_VALUE, InputType.CONST_POOL);
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

        b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
        b.variable(vars.sp);
        b.tree(createGetArgument(vars));
        b.end(2);

        b.startAssign(vars.sp).variable(vars.sp).string(" + 1").end();

        return b.build();

    }

    private CodeTree createGetArgument(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind != FrameKind.OBJECT) {
            b.string("(", kind.getTypeName(), ") ");
        }
        b.variable(vars.consts).string("[");
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end().string("]");
        return b.build();
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.REPLACE;
    }

    @Override
    public CodeVariableElement boxingEliminationReplacement(FrameKind targetKind) {
        if (kind == FrameKind.OBJECT) {
            return ctx.loadConstantInstructions[targetKind.ordinal()].opcodeIdField;
        } else {
            if (targetKind == FrameKind.OBJECT) {
                return ctx.loadConstantInstructions[targetKind.ordinal()].opcodeIdField;
            } else {
                return opcodeIdField;
            }
        }
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        if (kind == FrameKind.OBJECT) {
            return null;
        }

        return OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, ctx.loadConstantInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField);
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[]{
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_LOAD_CONSTANT"),
                        CodeTreeBuilder.singleString("LE_BYTES.getShort(bc, bci + " + getArgumentOffset(0) + ")")
        };
    }
}
