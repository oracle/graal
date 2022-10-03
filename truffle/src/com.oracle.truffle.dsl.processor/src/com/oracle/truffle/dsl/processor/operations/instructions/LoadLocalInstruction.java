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

public class LoadLocalInstruction extends Instruction {

    public LoadLocalInstruction(OperationsContext ctx, int id) {
        super(ctx, "load.local", id, 1);
        addLocal("local");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        OperationGeneratorUtils.createHelperMethod(ctx.outerType, "do_loadLocal", () -> {
            CodeExecutableElement res = new CodeExecutableElement(Set.of(Modifier.STATIC, Modifier.PRIVATE), context.getType(void.class), "do_loadLocal");

            res.addParameter(vars.stackFrame);
            if (ctx.getData().enableYield) {
                res.addParameter(vars.localFrame);
            }
            res.addParameter(vars.bc);
            res.addParameter(vars.bci);
            res.addParameter(vars.sp);
            if (ctx.hasBoxingElimination()) {
                res.addParameter(new CodeVariableElement(context.getType(int.class), "primitiveTag"));
            }
            res.addParameter(new CodeVariableElement(context.getType(int.class), "localIdx"));

            CodeTreeBuilder b = res.createBuilder();

            if (ctx.hasBoxingElimination()) {
                b.startSwitch().string("primitiveTag").end().startBlock();
                for (FrameKind kind : ctx.getBoxingKinds()) {
                    b.startCase().string(kind.toOrdinal()).end().startCaseBlock();

                    if (kind == FrameKind.OBJECT) {
                        b.statement("Object value");
                        // this switch should be made to fold
                        b.startSwitch().startCall("UFA", "unsafeGetTag").variable(vars.localFrame).string("localIdx").end(2).startBlock();
                        for (FrameKind localKind : ctx.getBoxingKinds()) {
                            b.startCase().string(localKind.toOrdinal()).end().startCaseBlock();

                            b.startAssign("value").startCall("UFA", "unsafeUncheckedGet" + localKind.getFrameName());
                            b.variable(vars.localFrame);
                            b.string("localIdx");
                            b.end(2);

                            b.statement("break");

                            b.end();
                        }

                        b.caseDefault().startCaseBlock();
                        b.tree(GeneratorUtils.createShouldNotReachHere());
                        b.end();

                        b.end(); // switch

                        b.startStatement().startCall("UFA", "unsafeSetObject");
                        b.variable(vars.stackFrame);
                        b.variable(vars.sp);
                        b.string("value");
                        b.end(2);

                        b.returnStatement();
                    } else {
                        b.startTryBlock();
                        b.startStatement().startCall("UFA", "unsafeSet" + kind.getFrameName());
                        b.variable(vars.stackFrame);
                        b.variable(vars.sp);
                        b.startCall("UFA", "unsafeGet" + kind.getFrameName());
                        b.variable(vars.localFrame);
                        b.string("localIdx");
                        b.end();
                        b.end(2);

                        b.returnStatement();

                        b.end().startCatchBlock(types.FrameSlotTypeException, "ex");
                        b.end();

                        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                        b.statement("break");
                    }

                    b.end();
                }

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere());
                b.end();

                b.end();

                b.startAssign("Object result").startCall(vars.localFrame, "getValue").string("localIdx").end(2);

                b.startStatement().startCall(vars.localFrame, "getFrameDescriptor().setSlotKind");
                b.string("localIdx");
                b.string("primitiveTag == 7 ? FrameSlotKind.Object : FrameSlotKind.fromTag((byte) primitiveTag)");
                b.end(2);

                b.startStatement().startCall("UFA", "unsafeSetObject").variable(vars.localFrame).string("localIdx").string("result").end(2);
                b.startStatement().startCall("UFA", "unsafeSetObject").variable(vars.stackFrame).variable(vars.sp).string("result").end(2);

            } else {
                if (ctx.getData().enableYield) {
                    b.startStatement().startCall("UFA", "unsafeCopyTo");
                    b.variable(vars.localFrame);
                    b.string("localIdx");
                    b.variable(vars.stackFrame);
                    b.variable(vars.sp);
                    b.string("1");
                    b.end(2);
                } else {
                    b.startStatement().startCall("UFA", "unsafeCopy");
                    b.variable(vars.stackFrame);
                    b.string("localIdx");
                    b.variable(vars.sp);
                    b.end(2);
                }
            }

            return res;
        });

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("int localIdx");
        b.tree(createLocalIndex(vars, 0, false));
        b.end();

        b.startStatement().startCall("do_loadLocal");
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

        b.startStatement().variable(vars.sp).string("++").end();

        return b.build();
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
