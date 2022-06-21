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

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadArgumentInstruction extends Instruction {

    private FrameKind kind;
    private OperationsContext ctx;

    public LoadArgumentInstruction(OperationsContext ctx, int id, FrameKind kind) {
        super("load.argument." + kind.getTypeName().toLowerCase(), id, ResultType.STACK_VALUE, InputType.ARGUMENT);
        this.ctx = ctx;
        this.kind = kind;
    }

    @Override
    public boolean standardPrologue() {
        return false;
    }

    private CodeTree createGetValue(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startCall(vars.frame, "getArguments").end();
        b.string("[");
        b.variable(vars.bc);
        b.string("[").variable(vars.bci).string(" + " + getArgumentOffset(0)).string("]");
        b.string("]");

        return b.build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration("Object", "value", createGetValue(vars));

        if (kind == FrameKind.OBJECT) {
            b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
            b.variable(vars.sp);
            b.string("value");
            b.end(2);
        } else {
            b.startIf().string("value instanceof " + kind.getTypeNameBoxed()).end().startBlock();
            // {
            b.startStatement().startCall(vars.frame, "set" + kind.getFrameName());
            b.variable(vars.sp);
            b.string("(", kind.getTypeName(), ") value");
            b.end(2);
            // }
            b.end().startElseBlock();
            // {
            b.startStatement().startCall(vars.frame, "setObject");
            b.variable(vars.sp);
            b.string("value");
            b.end(2);
            // }
            b.end();
        }

        b.startAssign(vars.sp).variable(vars.sp).string(" + 1").end();

        return b.build();
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.REPLACE;
    }

    @Override
    public CodeVariableElement boxingEliminationReplacement(FrameKind targetKind) {
        if (kind == FrameKind.OBJECT) {
            return ctx.loadArgumentInstructions[targetKind.ordinal()].opcodeIdField;
        } else {
            if (targetKind == FrameKind.OBJECT) {
                return ctx.loadArgumentInstructions[targetKind.ordinal()].opcodeIdField;
            } else {
                return opcodeIdField;
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
                        CodeTreeBuilder.singleString("ExecutionTracer.INSTRUCTION_TYPE_LOAD_ARGUMENT"),
                        CodeTreeBuilder.singleString("bc[bci + " + getArgumentOffset(0) + "]")
        };
    }
}
