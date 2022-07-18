/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorFlags;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.BoxingEliminationBehaviour;

public class StoreLocalInstruction extends Instruction {
    private final FrameKind kind;
    private final OperationsContext context;

    static final DeclaredType FrameSlotKind = ProcessorContext.getInstance().getDeclaredType("com.oracle.truffle.api.frame.FrameSlotKind");

    public StoreLocalInstruction(OperationsContext context, int id, FrameKind kind) {
        super("store.local." + (kind == null ? "uninit" : kind.getTypeName().toLowerCase()), id, 0);
        this.context = context;
        this.kind = kind;

        addPopIndexed("value");
        addLocal("target");
    }

    public static CodeExecutableElement createStoreLocalInitialization(OperationsContext context) {
        ProcessorContext ctx = ProcessorContext.getInstance();

        CodeExecutableElement method = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), ctx.getType(int.class), "storeLocalInitialization");
        method.addParameter(new CodeVariableElement(ctx.getTypes().VirtualFrame, "frame"));
        method.addParameter(new CodeVariableElement(ctx.getType(int.class), "localIdx"));
        method.addParameter(new CodeVariableElement(ctx.getType(int.class), "localTag"));
        method.addParameter(new CodeVariableElement(ctx.getType(int.class), "sourceSlot"));

        CodeTreeBuilder b = method.createBuilder();

        b.startAssign("Object value").string("frame.getValue(sourceSlot)").end();

        for (FrameKind kind : context.getData().getFrameKinds()) {
            if (kind == FrameKind.OBJECT) {
                continue;
            }
            b.startIf();
            // {
            b.string("localTag == " + kind.ordinal() + " /* " + kind + " */");
            b.string(" && ");
            b.string("value instanceof " + kind.getTypeNameBoxed());
            // }
            b.end().startBlock();
            // {
            b.startStatement().startCall("frame", "set" + kind.getFrameName());
            b.string("localIdx");
            b.startGroup().cast(kind.getType()).string("value").end();
            b.end(2);

            b.startReturn().string(kind.ordinal() + " /* " + kind + " */").end();
            // }
            b.end();
        }

        b.startStatement().startCall("frame", "setObject");
        b.string("localIdx");
        b.string("value");
        b.end(2);

        b.startReturn().string("0 /* OBJECT */").end();

        return method;
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        // todo: implement version w/o BE, if a language does not need it

        b.startAssign("int localIdx");
        b.tree(createLocalIndex(vars, 0));
        b.end();

        b.startAssign("int sourceSlot").variable(vars.sp).string(" - 1").end();

        if (kind == null) {
            b.startAssign("FrameSlotKind localTag").startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end(2);

            b.startIf().string("localTag == ").staticReference(FrameSlotKind, "Illegal").end().startBlock();
            // {
            if (OperationGeneratorFlags.LOG_LOCAL_STORES) {
                b.statement("System.err.printf(\" local store %2d : %s [uninit]%n\", localIdx, $frame.getValue(sourceSlot))");
            }
            b.startAssert().startCall(vars.frame, "isObject").string("sourceSlot").end(2);
            createCopyObject(vars, b);
            // }
            b.end().startElseBlock();
            // {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.startAssign("int resultTag").startCall("storeLocalInitialization");
            b.variable(vars.frame);
            b.string("localIdx");
            b.string("localTag.tag");
            b.string("sourceSlot");
            b.end(2);

            if (OperationGeneratorFlags.LOG_LOCAL_STORES || OperationGeneratorFlags.LOG_LOCAL_STORES_SPEC) {
                b.statement("System.err.printf(\" local store %2d : %s [init -> %s]%n\", localIdx, $frame.getValue(sourceSlot), FrameSlotKind.fromTag((byte) resultTag))");
            }

            b.startStatement().startCall("setResultBoxedImpl");
            b.variable(vars.bc);
            b.variable(vars.bci);
            b.string("resultTag");
            b.string("BOXING_DESCRIPTORS[resultTag]");
            b.end(2);

            createSetChildBoxing(vars, b, "resultTag");
            // }
            b.end();
        } else if (kind == FrameKind.OBJECT) {
            if (OperationGeneratorFlags.LOG_LOCAL_STORES) {
                b.statement("System.err.printf(\" local store %2d : %s [generic]%n\", localIdx, $frame.getValue(sourceSlot))");
            }

            b.startStatement().startCall(vars.frame, "setObject");
            b.string("localIdx");
            b.startCall("expectObject").variable(vars.frame).string("sourceSlot").end();
            b.end(2);
        } else {
            if (OperationGeneratorFlags.LOG_LOCAL_STORES) {
                b.statement("System.err.printf(\" local store %2d : %s [" + kind + "]%n\", localIdx, $frame.getValue(sourceSlot))");
            }
            b.startIf().string("!").startCall("storeLocal" + kind.getFrameName() + "Check");
            b.variable(vars.frame);
            b.string("localIdx");
            b.string("sourceSlot");
            b.end(2).startBlock(); // {
            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, context.storeLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField));
            createSetChildBoxing(vars, b, "0 /* OBJECT */");
            b.end(); // }
        }

        b.startStatement().variable(vars.sp).string("--").end();

        return b.build();
    }

    @Override
    public CodeTree createExecuteUncachedCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind != null) {
            throw new AssertionError("only store.local.uninit should appear uncached");
        }

        b.startAssign("int localIdx");
        b.tree(createLocalIndex(vars, 0));
        b.end();

        b.startAssign("int sourceSlot").variable(vars.sp).string(" - 1").end();

        createCopyObject(vars, b);

        b.startStatement().variable(vars.sp).string("--").end();

        return b.build();
    }

    private static final boolean USE_SPEC_FRAME_COPY = true;

    private static void createCopyPrimitive(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, USE_SPEC_FRAME_COPY ? "copyPrimitive" : "copy");
        b.string("sourceSlot");
        b.string("localIdx");
        b.end(2);
    }

    private static void createCopyObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, USE_SPEC_FRAME_COPY ? "copyObject" : "copy");
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
        b.startGroup().tree(createPopIndexedIndex(vars, 0)).end();
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
        if (kind == FrameKind.OBJECT) {
            return BoxingEliminationBehaviour.DO_NOTHING;
        } else {
            return BoxingEliminationBehaviour.REPLACE;
        }
    }

    @Override
    public CodeVariableElement boxingEliminationReplacement(FrameKind newKind) {
        if (kind == null) {
            // unitialized -> anything
            return context.storeLocalInstructions[newKind.ordinal()].opcodeIdField;
        } else {
            if (newKind == kind || kind == FrameKind.OBJECT) {
                // do nothing
                return new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "0");
            } else {
                // prim -> anything different = object
                return context.storeLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField;
            }
        }
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public boolean neverInUncached() {
        return kind != null;
    }
}
