package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadLocalInstruction extends Instruction {

    private final OperationsContext ctx;
    private final FrameKind kind;

    private static final boolean LOG_LOCAL_LOADS = false;

    public LoadLocalInstruction(OperationsContext ctx, int id, FrameKind kind) {
        super("load.local." + (kind == null ? "uninit" : kind.getTypeName().toLowerCase()), id, ResultType.STACK_VALUE, InputType.LOCAL);
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

        b.startAssign("int localIdx");
        b.startCall("LE_BYTES", "getShort");
        b.variable(vars.bc);
        b.startGroup().variable(vars.bci).string(" + " + getArgumentOffset(0)).end();
        b.end();
        b.string(" + VALUES_OFFSET");
        b.end();

        if (kind == null) {
            if (LOG_LOCAL_LOADS) {
                b.statement("System.out.printf(\" local load  %2d : %s [uninit]%n\", localIdx, frame.getValue(localIdx))");
            }
            createCopyAsObject(vars, b);
        } else if (kind == FrameKind.OBJECT) {
            b.startIf();
            {
                b.startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end();
                b.string(" != ");
                b.staticReference(StoreLocalInstruction.FrameSlotKind, "Object");
            }
            b.end().startBlock();
            {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                if (LOG_LOCAL_LOADS) {
                    b.statement("System.out.printf(\" local load  %2d : %s [init object]%n\", localIdx, frame.getValue(localIdx))");
                }

                createSetSlotKind(vars, b, "FrameSlotKind.Object");
                createReplaceObject(vars, b);
            }
            b.end();

            if (LOG_LOCAL_LOADS) {
                b.statement("System.out.printf(\" local load  %2d : %s [generic]%n\", localIdx, frame.getValue(localIdx))");
            }

            createCopy(vars, b);
        } else {

            b.declaration(StoreLocalInstruction.FrameSlotKind, "localType",
                            b.create().startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end().build());

            b.startIf();
            b.string("localType != ").staticReference(StoreLocalInstruction.FrameSlotKind, kind.getFrameName());
            b.end().startBlock();
            {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                b.declaration("Object", "localValue", (CodeTree) null);

                b.startIf();
                {
                    b.string("localType == ").staticReference(StoreLocalInstruction.FrameSlotKind, "Illegal");
                    b.string(" && ");
                    b.string("(localValue = frame.getObject(localIdx))").instanceOf(ElementUtils.boxType(kind.getType()));
                }
                b.end().startBlock();
                {
                    if (LOG_LOCAL_LOADS) {
                        b.statement("System.out.printf(\" local load  %2d : %s [init " + kind + "]%n\", localIdx, frame.getValue(localIdx))");
                    }

                    createSetSlotKind(vars, b, "FrameSlotKind." + kind.getFrameName());

                    b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
                    b.string("localIdx");
                    b.startGroup().cast(kind.getType()).string("localValue").end();
                    b.end(2);
                }
                b.end().startElseBlock();
                {
                    if (LOG_LOCAL_LOADS) {
                        b.statement("System.out.printf(\" local load  %2d : %s [" + kind + " -> generic]%n\", localIdx, frame.getValue(localIdx))");
                    }
                    createSetSlotKind(vars, b, "FrameSlotKind.Object");
                }
                b.end();
            }
            b.end();

            if (LOG_LOCAL_LOADS) {
                b.statement("System.out.printf(\" local load  %2d : %s [" + kind + "]%n\", localIdx, frame.getValue(localIdx))");
            }

            createCopy(vars, b);
        }

        b.startStatement().variable(vars.sp).string("++").end();

        return b.build();
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        if (kind == FrameKind.OBJECT) {
            return BoxingEliminationBehaviour.DO_NOTHING;
        } else {
            return BoxingEliminationBehaviour.REPLACE;
        }
    }

    private static void createCopy(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, "copy");
        b.string("localIdx");
        b.variable(vars.sp);
        b.end(2);
    }

    private static void createSetSlotKind(ExecutionVariables vars, CodeTreeBuilder b, String tag) {
        b.startStatement().startCall(vars.frame, "getFrameDescriptor().setSlotKind");
        b.string("localIdx");
        b.string(tag);
        b.end(2);
    }

    private static void createReplaceObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, "setObject");
        b.string("localIdx");
        b.startCall(vars.frame, "getValue").string("localIdx").end();
        b.end(2);
    }

    private static void createCopyAsObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, "setObject");
        b.variable(vars.sp);
        b.startCall("expectObject").variable(vars.frame).string("localIdx").end();
        b.end(2);
    }

    @Override
    public CodeVariableElement boxingEliminationReplacement(FrameKind targetKind) {
        if (kind == null) {
            // unitialized -> anything
            return ctx.loadLocalInstructions[targetKind.ordinal()].opcodeIdField;
        } else {
            if (targetKind == kind || kind == FrameKind.OBJECT) {
                // do nothing
                return new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "0");
            } else {
                // prim -> anything different = object
                return ctx.loadLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField;
            }
        }
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
