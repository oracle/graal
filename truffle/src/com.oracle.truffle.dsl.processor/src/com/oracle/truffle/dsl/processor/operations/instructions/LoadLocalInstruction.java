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

import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.combineBoxingBits;
import static com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils.createWriteOpcode;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorFlags;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadLocalInstruction extends Instruction {

    private final boolean boxed;

    public LoadLocalInstruction(OperationsContext ctx, int id, boolean boxed) {
        super(ctx, "load.local" + (boxed ? ".boxed" : ""), id, 1);
        this.boxed = boxed;
        addLocal("local");
    }

    @Override
    public CodeTree createExecuteUncachedCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("int localIdx").tree(createLocalIndex(vars, 0, false)).end();

        if (ctx.getData().enableYield) {
            b.startStatement().startCall("UFA", "unsafeCopyTo");
            b.variable(vars.localFrame);
            b.string("localIdx");
            b.variable(vars.stackFrame);
            b.string("$sp");
            b.string("1");
            b.end(2);
        } else {
            b.startStatement().startCall("UFA", "unsafeCopyObject");
            b.variable(vars.stackFrame);
            b.string("localIdx");
            b.string("$sp");
            b.end(2);
        }

        b.statement("$sp += 1");

        return b.build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {

        boolean useBoxed = vars.specializedKind != FrameKind.OBJECT && boxed;

        String helperName = "do_loadLocal" + (useBoxed ? "Boxed" : "") + "_" + (ctx.hasBoxingElimination() ? vars.specializedKind : "");
        OperationGeneratorUtils.createHelperMethod(ctx.outerType, helperName, () -> {
            CodeExecutableElement res = new CodeExecutableElement(Set.of(Modifier.STATIC, Modifier.PRIVATE), context.getType(void.class), helperName);

            res.addParameter(new CodeVariableElement(ctx.outerType.asType(), "$this"));
            res.addParameter(vars.stackFrame);
            if (ctx.getData().enableYield) {
                res.addParameter(vars.localFrame);
            }
            res.addParameter(vars.bc);
            res.addParameter(vars.bci);
            res.addParameter(vars.sp);
            if (ctx.hasBoxingElimination()) {
                res.addParameter(new CodeVariableElement(context.getType(byte[].class), "localTags"));
            }
            res.addParameter(new CodeVariableElement(context.getType(int.class), "localIdx"));

            CodeTreeBuilder b = res.createBuilder();

            if (ctx.hasBoxingElimination()) {
                FrameKind kind = vars.specializedKind;
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

                        if (OperationGeneratorFlags.LOG_LOCAL_LOADS) {
                            b.statement("System.err.printf(\" [load]  local=%d value=%s kind=" + localKind + " (as OBJECT)%n\", localIdx, $frame.getValue(localIdx))");
                        }

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

                    b.startGroup();
                    if (boxed) {
                        b.string("(", kind.getTypeName() + ") ");
                        b.startCall("UFA", "unsafeGetObject");
                    } else {
                        b.startCall("UFA", "unsafeGet" + kind.getFrameName());
                    }
                    b.variable(vars.localFrame);
                    b.string("localIdx");
                    b.end(2);

                    b.end(2);

                    if (OperationGeneratorFlags.LOG_LOCAL_LOADS) {
                        b.statement("System.err.printf(\" [load]  local=%d value=%s kind=" + kind + " boxed=" + boxed + "%n\", localIdx, $frame.getValue(localIdx))");
                    }

                    b.returnStatement();

                    if (boxed) {
                        b.end().startCatchBlock(new TypeMirror[]{types.FrameSlotTypeException, context.getType(ClassCastException.class)}, "ex");
                    } else {
                        b.end().startCatchBlock(types.FrameSlotTypeException, "ex");
                    }

                    b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                    b.startAssign("Object result").startCall(vars.localFrame, "getValue").string("localIdx").end(2);

                    b.startIf().string("result instanceof " + kind.getTypeNameBoxed()).end().startBlock();

                    b.startIf().startCall("UFA", "unsafeByteArrayRead").string("localTags").string("localIdx").end().string(" == 7").end().startBlock();

                    if (OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                        b.statement("System.err.printf(\" [load-spec]  local=%d value=%s kind=OBJECT->" + kind + " (boxed)%n\", localIdx, $frame.getValue(localIdx))");
                    }

                    b.tree(createWriteOpcode(vars.bc, vars.bci, combineBoxingBits(ctx, ctx.loadLocalBoxed, kind)));

                    b.end().startElseBlock();

                    if (OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                        b.statement("System.err.printf(\" [load-spec]  local=%d value=%s kind=OBJECT->" + kind + "%n\", localIdx, $frame.getValue(localIdx))");
                    }

                    b.startStatement().startCall("UFA", "unsafeByteArrayWrite");
                    b.string("localTags");
                    b.string("localIdx");
                    b.string("(byte) ", kind.toOrdinal());
                    b.end(2);

                    b.startStatement().startCall("UFA", "unsafeSet" + kind.getFrameName()).variable(vars.localFrame).string("localIdx").string("(", kind.getTypeName(), ") result").end(2);
                    b.startStatement().startCall("UFA", "unsafeSet" + kind.getFrameName()).variable(vars.stackFrame).variable(vars.sp).string("(", kind.getTypeName(), ") result").end(2);
                    b.returnStatement();

                    b.end(); // if tag
                    b.end(); // if instanceof
                    b.end(); // try
                }

                if (kind != FrameKind.OBJECT) {
                    b.startAssign("Object result").startCall(vars.localFrame, "getValue").string("localIdx").end(2);

                    if (OperationGeneratorFlags.LOG_LOCAL_LOADS_SPEC) {
                        b.statement("System.err.printf(\" [load-spec]  local=%d value=%s kind=" + kind + "->OBJECT boxed=" + boxed + "%n\", localIdx, $frame.getValue(localIdx))");
                    }

                    b.startStatement().startCall("UFA", "unsafeByteArrayWrite");
                    b.string("localTags");
                    b.string("localIdx");
                    b.string("(byte) 7");
                    b.end(2);

                    b.startStatement().startCall("UFA", "unsafeSetObject").variable(vars.localFrame).string("localIdx").string("result").end(2);
                    b.startStatement().startCall("UFA", "unsafeSetObject").variable(vars.stackFrame).variable(vars.sp).string("result").end(2);
                }
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

        if (boxed && vars.specializedKind == FrameKind.OBJECT) {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.tree(createWriteOpcode(vars.bc, vars.bci, combineBoxingBits(ctx, ctx.loadLocalUnboxed, 0)));
        }

        b.startAssign("int localIdx");
        b.tree(createLocalIndex(vars, 0, false));
        b.end();

        b.startStatement().startCall(helperName);
        b.string("$this");
        b.variable(vars.stackFrame);
        if (ctx.getData().enableYield) {
            b.variable(vars.localFrame);
        }
        b.variable(vars.bc);
        b.variable(vars.bci);
        b.variable(vars.sp);
        if (ctx.hasBoxingElimination()) {
            b.string("$localTags");
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

    @Override
    public boolean splitOnBoxingElimination() {
        return true;
    }

    @Override
    public List<FrameKind> getBoxingEliminationSplits() {
        return ctx.getBoxingKinds();
    }

    @Override
    public boolean alwaysBoxed() {
        return boxed;
    }

}
