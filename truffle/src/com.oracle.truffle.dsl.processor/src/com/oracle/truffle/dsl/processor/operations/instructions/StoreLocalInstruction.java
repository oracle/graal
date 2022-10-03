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

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class StoreLocalInstruction extends Instruction {

    public StoreLocalInstruction(OperationsContext ctx, int id) {
        super(ctx, "store.local", id, 0);

        addPopIndexed("value");
        addLocal("target");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        if (ctx.hasBoxingElimination()) {
            OperationGeneratorUtils.createHelperMethod(ctx.outerType, "do_storeLocalSpecialize", () -> {
                CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), context.getType(void.class), "do_storeLocalSpecialize");

                ex.addParameter(vars.stackFrame);
                if (ctx.getData().enableYield) {
                    ex.addParameter(vars.localFrame);
                }
                ex.addParameter(vars.bc);
                ex.addParameter(vars.bci);
                ex.addParameter(vars.sp);
                ex.addParameter(new CodeVariableElement(types.FrameDescriptor, "fd"));
                if (ctx.hasBoxingElimination()) {
                    ex.addParameter(new CodeVariableElement(context.getType(int.class), "primitiveTag"));
                }
                ex.addParameter(new CodeVariableElement(context.getType(int.class), "localIdx"));
                ex.addParameter(new CodeVariableElement(context.getType(int.class), "sourceSlot"));

                CodeTreeBuilder b = ex.getBuilder();

                b.tree(GeneratorUtils.createNeverPartOfCompilation());

                b.startAssign("Object value").startCall(vars.stackFrame, "getValue");
                b.string("sourceSlot");
                b.end(2);

                b.declaration(types.FrameSlotKind, "curKind", "fd.getSlotKind(localIdx)");

                b.statement("// System.err.printf(\"primitiveTag=%d value=%s %s curKind=%s tag=%s%n\", primitiveTag, value.getClass(), value, curKind, $frame.getTag(sourceSlot))");

                for (FrameKind primKind : ctx.getPrimitiveBoxingKinds()) {
                    // todo: use implicit conversions here

                    b.startIf();
                    b.string("(primitiveTag == 0 || primitiveTag == " + primKind.toOrdinal() + ")");
                    b.string(" && (curKind == FrameSlotKind.Illegal || curKind == FrameSlotKind." + primKind.getFrameName() + ")");
                    b.end().startBlock();

                    b.startIf().string("value instanceof ", primKind.getTypeNameBoxed()).end().startBlock();

                    createSetFrameDescriptorKind(vars, b, primKind.getFrameName());
                    createSetPrimitiveTag(vars, b, primKind.toOrdinal());
                    createBoxingEliminateChild(vars, b, primKind.toOrdinal());

                    b.startStatement().startCall("UFA", "unsafeSet" + primKind.getFrameName());
                    b.variable(vars.localFrame);
                    b.string("localIdx");
                    b.startGroup().cast(primKind.getType()).string("value").end();
                    b.end(2);

                    b.returnStatement();

                    b.end();

                    b.end();
                }

                createSetFrameDescriptorKind(vars, b, "Object");
                createSetPrimitiveTag(vars, b, "7 /* generic */");
                createBoxingEliminateChild(vars, b, "0 /* OBJECT */");

                b.startStatement().startCall("UFA", "unsafeSetObject");
                b.variable(vars.localFrame);
                b.string("localIdx");
                b.string("value");
                b.end(2);

                return ex;
            });
        }

        OperationGeneratorUtils.createHelperMethod(ctx.outerType, "do_storeLocal", () -> {
            CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.PRIVATE, Modifier.STATIC), context.getType(void.class), "do_storeLocal");

            ex.addParameter(vars.stackFrame);
            if (ctx.getData().enableYield) {
                ex.addParameter(vars.localFrame);
            }
            ex.addParameter(vars.bc);
            ex.addParameter(vars.bci);
            ex.addParameter(vars.sp);
            if (ctx.hasBoxingElimination()) {
                ex.addParameter(new CodeVariableElement(context.getType(int.class), "primitiveTag"));
            }
            ex.addParameter(new CodeVariableElement(context.getType(int.class), "localIdx"));

            CodeTreeBuilder b = ex.getBuilder();

            b.startAssign("int sourceSlot").variable(vars.sp).string(" - 1").end();

            if (!ctx.hasBoxingElimination()) {
                createCopyObject(vars, b);
            } else {
                // if Frame ever supports an 8th primitive type, we will need more bits here
                b.declaration(types.FrameDescriptor, "fd", vars.localFrame.getName() + ".getFrameDescriptor()");
                b.declaration(types.FrameSlotKind, "curKind", "fd.getSlotKind(localIdx)");
                b.startSwitch().string("primitiveTag").end().startBlock();
                for (FrameKind kind : ctx.getBoxingKinds()) {
                    b.startCase().string(kind.toOrdinal()).end().startCaseBlock();

                    if (kind == FrameKind.OBJECT) {
                        // this is the uninitialized case
                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        createCallSpecialize(vars, b);
                    } else {
                        // primitive case

                        b.startIf().string("curKind == FrameSlotKind." + kind.getFrameName()).end().startBlock();

                        b.startTryBlock();

                        b.startStatement().startCall("UFA", "unsafeSet" + kind.getFrameName());
                        b.variable(vars.localFrame);
                        b.string("localIdx");
                        b.startCall("UFA", "unsafeGet" + kind.getFrameName());
                        b.variable(vars.stackFrame);
                        b.string("sourceSlot");
                        b.end();
                        b.end(2);

                        b.returnStatement();

                        b.end().startCatchBlock(types.FrameSlotTypeException, "ex");
                        b.end(); // try catch

                        b.end(); // if

                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        createCallSpecialize(vars, b);
                    }

                    b.statement("break");
                    b.end();
                }

                b.startCase().string("7 /* generic */").end().startCaseBlock();

                b.startTryBlock();

                b.startStatement().startCall("UFA", "unsafeSetObject");
                b.variable(vars.localFrame);
                b.string("localIdx");
                b.startCall("UFA", "unsafeGetObject");
                b.variable(vars.stackFrame);
                b.string("sourceSlot");
                b.end();
                b.end(2);

                b.end().startCatchBlock(types.FrameSlotTypeException, "ex");

                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement().startCall("UFA", "unsafeSetObject");
                b.variable(vars.localFrame);
                b.string("localIdx");
                b.startCall(vars.stackFrame, "getValue");
                b.string("sourceSlot");
                b.end();
                b.end(2);

                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere());
                b.end();

                b.end();
            }

            return ex;
        });

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("int localIdx").tree(createLocalIndex(vars, 0, false)).end();

        b.startStatement().startCall("do_storeLocal");
        b.variable(vars.stackFrame);
        if (ctx.getData().enableYield) {
            b.variable(vars.localFrame);
        }
        b.variable(vars.bc);
        b.variable(vars.bci);
        b.variable(vars.sp);
        if (ctx.hasBoxingElimination()) {
            b.string("primitiveTag");
        }
        b.string("localIdx");
        b.end(2);

        b.startStatement().variable(vars.sp).string("--").end();

        return b.build();
    }

    private static final boolean USE_SPEC_FRAME_COPY = true;

    private void createCopyObject(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement();
        if (ctx.getData().enableYield) {
            b.startCall(vars.stackFrame, "copyTo");
            b.string("sourceSlot");
            b.variable(vars.localFrame);
            b.string("localIdx");
            b.string("1");
        } else {
            b.startCall("UFA", USE_SPEC_FRAME_COPY ? "unsafeCopyObject" : "unsafeCopy");
            b.variable(vars.localFrame);
            b.string("sourceSlot");
            b.string("localIdx");
        }
        b.end(2);
    }

    private void createCallSpecialize(ExecutionVariables vars, CodeTreeBuilder b) {
        b.startStatement().startCall("do_storeLocalSpecialize");
        b.variable(vars.stackFrame);
        if (ctx.getData().enableYield) {
            b.variable(vars.localFrame);
        }
        b.variable(vars.bc);
        b.variable(vars.bci);
        b.variable(vars.sp);
        b.string("fd");
        if (ctx.hasBoxingElimination()) {
            b.string("primitiveTag");
        }
        b.string("localIdx");
        b.string("sourceSlot");
        b.end(2);
    }

    @SuppressWarnings("unused")
    private void createSetFrameDescriptorKind(ExecutionVariables vars, CodeTreeBuilder b, String kind) {
        b.startStatement().startCall("fd", "setSlotKind");
        b.string("localIdx");
        b.staticReference(types.FrameSlotKind, kind);
        b.end(2);
    }

    private void createSetPrimitiveTag(ExecutionVariables vars, CodeTreeBuilder b, String tag) {
        b.tree(OperationGeneratorUtils.createWriteOpcode(vars.bc, vars.bci, "(short) ((" + tag + " << 13) | " + opcodeIdField.getName() + ")"));
    }

    private void createBoxingEliminateChild(ExecutionVariables vars, CodeTreeBuilder b, String tag) {
        b.tree(OperationGeneratorUtils.callSetResultBoxed(createPopIndexedIndex(vars, 0, false), CodeTreeBuilder.singleString(tag)));
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public boolean neverInUncached() {
        return false;
    }
}
