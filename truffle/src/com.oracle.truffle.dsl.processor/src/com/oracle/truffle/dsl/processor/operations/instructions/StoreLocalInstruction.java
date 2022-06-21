package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class StoreLocalInstruction extends Instruction {
    private final FrameKind kind;
    private final OperationsContext context;

    static final DeclaredType FrameSlotKind = ProcessorContext.getInstance().getDeclaredType("com.oracle.truffle.api.frame.FrameSlotKind");

    private static final boolean LOG_LOCAL_STORES = false;

    public StoreLocalInstruction(OperationsContext context, int id, FrameKind kind) {
        super("store.local." + (kind == null ? "uninit" : kind.getTypeName().toLowerCase()), id, ResultType.SET_LOCAL, InputType.STACK_VALUE);
        this.context = context;
        this.kind = kind;
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    public static CodeExecutableElement createStoreLocalInitialization(OperationsContext context) {
        ProcessorContext ctx = ProcessorContext.getInstance();

        CodeExecutableElement method = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), new CodeTypeMirror(TypeKind.INT), "storeLocalInitialization");
        method.addParameter(new CodeVariableElement(ctx.getTypes().VirtualFrame, "frame"));
        method.addParameter(new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "localIdx"));
        method.addParameter(new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "localTag"));
        method.addParameter(new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "sourceSlot"));

        CodeTreeBuilder b = method.createBuilder();

        b.startAssign("Object value").string("frame.getValue(sourceSlot)").end();

        for (FrameKind kind : context.getData().getFrameKinds()) {
            if (kind == FrameKind.OBJECT) {
                continue;
            }
            b.startIf();
            {
                b.string("localTag == FRAME_TYPE_" + kind);
                b.string(" && ");
                b.string("value instanceof " + kind.getTypeNameBoxed());
            }
            b.end().startBlock();
            {
                b.startStatement().startCall("frame", "set" + kind.getFrameName());
                b.string("localIdx");
                b.startGroup().cast(kind.getType()).string("value").end();
                b.end(2);

                b.startReturn().string("FRAME_TYPE_" + kind).end();
            }
            b.end();
        }

        b.startStatement().startCall("frame", "setObject");
        b.string("localIdx");
        b.string("value");
        b.end(2);

        b.startReturn().string("FRAME_TYPE_OBJECT").end();

        return method;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        // TODO: implement version w/o BE, if a language does not need it

        b.startAssign("int localIdx");
        b.variable(vars.bc);
        b.string("[").variable(vars.bci).string(" + " + getArgumentOffset(1)).string("]");
        b.string(" + VALUES_OFFSET");
        b.end();

        b.startAssign("int sourceSlot").variable(vars.sp).string(" - 1").end();

        if (kind == null) {
            b.startAssign("FrameSlotKind localTag").startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end(2);

            b.startIf().string("localTag == ").staticReference(FrameSlotKind, "Illegal").end().startBlock();
            {
                if (LOG_LOCAL_STORES) {
                    b.statement("System.out.printf(\" local store %2d : %s [uninit]%n\", localIdx, frame.getValue(sourceSlot))");
                }
                b.startAssert().startCall(vars.frame, "isObject").string("sourceSlot").end(2);
                createCopy(vars, b);
            }
            b.end().startElseBlock();
            {
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                b.startAssign("int resultTag").startCall("storeLocalInitialization");
                b.variable(vars.frame);
                b.string("localIdx");
                b.string("localTag.tag");
                b.string("sourceSlot");
                b.end(2);

                if (LOG_LOCAL_STORES) {
                    b.statement("System.out.printf(\" local store %2d : %s [init -> %s]%n\", localIdx, frame.getValue(sourceSlot), FrameSlotKind.fromTag((byte) resultTag))");
                }

                b.startStatement().startCall("setResultBoxedImpl");
                b.variable(vars.bc);
                b.variable(vars.bci);
                b.string("resultTag");
                b.string("BOXING_DESCRIPTORS[resultTag]");
                b.end(2);

                createSetChildBoxing(vars, b, "resultTag");
            }
            b.end();
        } else if (kind == FrameKind.OBJECT) {
            if (LOG_LOCAL_STORES) {
                b.statement("System.out.printf(\" local store %2d : %s [generic]%n\", localIdx, frame.getValue(sourceSlot))");
            }

            b.startStatement().startCall(vars.frame, "setObject");
            b.string("localIdx");
            b.startCall("expectObject").variable(vars.frame).string("sourceSlot").end();
            b.end(2);
        } else {
            // primitive
            b.startAssign("FrameSlotKind localTag").startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end(2);

            b.startDoBlock();
            {
                b.startIf().string("localTag == ").staticReference(FrameSlotKind, kind.getFrameName()).end().startBlock();
                {
                    b.startTryBlock();
                    {
                        if (LOG_LOCAL_STORES) {
                            b.statement("System.out.printf(\" local store %2d : %s [" + kind + "]%n\", localIdx, frame.getValue(sourceSlot))");
                        }
                        b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
                        b.string("localIdx");
                        b.startCall("expect" + kind.getFrameName()).variable(vars.frame).string("sourceSlot").end();
                        b.end(2);

                        b.statement("break /* goto here */");
                    }
                    b.end().startCatchBlock(OperationGeneratorUtils.getTypes().UnexpectedResultException, "ex");
                    {

                    }
                    b.end();
                }
                b.end();

                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                if (LOG_LOCAL_STORES) {
                    b.statement("System.out.printf(\" local store %2d : %s [" + kind + " -> generic]%n\", localIdx, frame.getValue(sourceSlot))");
                }

                createSetSlotKind(vars, b, "FrameSlotKind.Object");
                createGenerifySelf(vars, b);
                createSetChildBoxing(vars, b, "FRAME_TYPE_OBJECT");
                createCopyAsObject(vars, b);
            }
            b.end().startDoWhile().string("false").end(2);
        }

        b.lineComment("here:");
        b.startStatement().variable(vars.sp).string("--").end();

        return b.build();
    }

    private static void createCopy(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, "copy");
        b.string("sourceSlot");
        b.string("localIdx");
        b.end(2);
    }

    private static void createSetSlotKind(ExecutionVariables vars, CodeTreeBuilder b, String tag) {
        b.startStatement().startCall(vars.frame, "getFrameDescriptor().setSlotKind");
        b.string("localIdx");
        b.string(tag);
        b.end(2);
    }

    private void createSetChildBoxing(ExecutionVariables vars, CodeTreeBuilder b, String tag) {
        b.startStatement().startCall("doSetResultBoxed");
        b.variable(vars.bc);
        b.variable(vars.bci);
        b.startGroup().variable(vars.bc).string("[").variable(vars.bci).string(" + " + getArgumentOffset(0)).string("]").end();
        b.string(tag);
        b.end(2);
    }

    private static void createCopyAsObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, "setObject");
        b.string("localIdx");
        b.startCall("expectObject").variable(vars.frame).string("sourceSlot").end();
        b.end(2);
    }

    private void createGenerifySelf(ExecutionVariables vars, CodeTreeBuilder b) {
        b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, context.storeLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField));
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.REPLACE;
    }

    @Override
    public CodeVariableElement boxingEliminationReplacement(FrameKind newKind) {
        if (kind == null) {
            return context.storeLocalInstructions[newKind.ordinal()].opcodeIdField;
        } else {
            return context.storeLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField;
        }
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return new CodeTree[]{
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_STORE_LOCAL"),
                        CodeTreeBuilder.singleString("bc[bci + " + getArgumentOffset(1) + "]"),
                        CodeTreeBuilder.singleString("frame.getValue(sp - 1).getClass()")
        };
    }
}
