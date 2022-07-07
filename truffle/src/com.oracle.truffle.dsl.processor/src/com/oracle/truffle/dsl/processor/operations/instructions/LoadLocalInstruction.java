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

import javax.lang.model.type.TypeKind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorFlags;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadLocalInstruction extends Instruction {

    private final OperationsContext ctx;
    private final FrameKind kind;

    public LoadLocalInstruction(OperationsContext ctx, int id, FrameKind kind) {
        super("load.local." + (kind == null ? "uninit" : kind.getTypeName().toLowerCase()), id, 1);
        this.ctx = ctx;
        this.kind = kind;
        addLocal("local");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("int localIdx");
        b.tree(createLocalIndex(vars, 0));
        b.end();

        if (OperationGeneratorFlags.INTERPRETER_ONLY_BOXING_ELIMINATION) {
            b.startIf().tree(GeneratorUtils.createInInterpreter()).end().startBlock(); // {
        }

        if (kind == null) {
            if (OperationGeneratorFlags.LOG_LOCAL_LOADS) {
                b.statement("System.err.printf(\" local load  %2d : %s [uninit]%n\", localIdx, $frame.getValue(localIdx))");
            }
            createCopyAsObject(vars, b);
        } else if (kind == FrameKind.OBJECT) {
            b.startIf();
            // {
            b.startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end();
            b.string(" != ");
            b.staticReference(StoreLocalInstruction.FrameSlotKind, "Object");
            // }
            b.end().startBlock();
            // {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            if (OperationGeneratorFlags.LOG_LOCAL_LOADS || OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                b.statement("System.err.printf(\" local load  %2d : %s [init object]%n\", localIdx, $frame.getValue(localIdx))");
            }

            createSetSlotKind(vars, b, "FrameSlotKind.Object");
            createReplaceObject(vars, b);
            // }
            b.end();

            if (OperationGeneratorFlags.LOG_LOCAL_LOADS) {
                b.statement("System.err.printf(\" local load  %2d : %s [generic]%n\", localIdx, $frame.getValue(localIdx))");
            }

            createCopyObject(vars, b);
        } else {

            b.declaration(StoreLocalInstruction.FrameSlotKind, "localType",
                            b.create().startCall(vars.frame, "getFrameDescriptor().getSlotKind").string("localIdx").end().build());

            b.startIf();
            b.string("localType != ").staticReference(StoreLocalInstruction.FrameSlotKind, kind.getFrameName());
            b.end().startBlock();
            // {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

            b.declaration("Object", "localValue", (CodeTree) null);

            b.startIf();
            // {
            b.string("localType == ").staticReference(StoreLocalInstruction.FrameSlotKind, "Illegal");
            b.string(" && ");
            b.string("(localValue = $frame.getObject(localIdx))").instanceOf(ElementUtils.boxType(kind.getType()));
            // }
            b.end().startBlock();
            // {

            if (OperationGeneratorFlags.LOG_LOCAL_LOADS || OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                b.statement("System.err.printf(\" local load  %2d : %s [init " + kind + "]%n\", localIdx, $frame.getValue(localIdx))");
            }

            createSetSlotKind(vars, b, "FrameSlotKind." + kind.getFrameName());

            b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
            b.string("localIdx");
            b.startGroup().cast(kind.getType()).string("localValue").end();
            b.end(2);

            createCopyPrimitive(vars, b);
            // }
            b.end().startElseBlock();
            // {

            if (OperationGeneratorFlags.LOG_LOCAL_LOADS || OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                b.statement("System.err.printf(\" local load  %2d : %s [" + kind + " -> generic]%n\", localIdx, $frame.getValue(localIdx))");
            }

            createSetSlotKind(vars, b, "FrameSlotKind.Object");
            b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, ctx.loadLocalInstructions[FrameKind.OBJECT.ordinal()].opcodeIdField));
            createCopyObject(vars, b);

            b.end(); // }
            b.end().startElseBlock(); // } else {

            if (OperationGeneratorFlags.LOG_LOCAL_LOADS) {
                b.statement("System.err.printf(\" local load  %2d : %s [" + kind + "]%n\", localIdx, $frame.getValue(localIdx))");
            }

            createCopyPrimitive(vars, b);
            b.end(); // }

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

    private static final boolean USE_SPEC_FRAME_COPY = true;

    private static void createCopyObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, USE_SPEC_FRAME_COPY ? "copyObject" : "copy");
        b.string("localIdx");
        b.variable(vars.sp);
        b.end(2);
    }

    private static void createCopyPrimitive(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall(vars.frame, USE_SPEC_FRAME_COPY ? "copyPrimitive" : "copy");
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

}
