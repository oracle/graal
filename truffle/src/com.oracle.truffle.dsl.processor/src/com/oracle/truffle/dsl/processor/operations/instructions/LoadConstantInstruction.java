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

import java.util.EnumSet;

import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class LoadConstantInstruction extends Instruction {

    public LoadConstantInstruction(OperationsContext ctx, int id) {
        super(ctx, "load.constant", id, 1);
        addConstant("constant", ProcessorContext.getInstance().getType(Object.class));
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {

        String helperName = "do_loadConstant_" + (ctx.hasBoxingElimination() ? vars.specializedKind : "");

        OperationGeneratorUtils.createHelperMethod(ctx.outerType, helperName, () -> {
            CodeExecutableElement metImpl = new CodeExecutableElement(
                            EnumSet.of(Modifier.PRIVATE, Modifier.STATIC),
                            context.getType(void.class),
                            helperName);

            metImpl.addParameter(vars.stackFrame);
            metImpl.addParameter(vars.bc);
            metImpl.addParameter(vars.bci);
            metImpl.addParameter(vars.sp);
            metImpl.addParameter(vars.consts);

            CodeTreeBuilder b = metImpl.getBuilder();

            if (ctx.hasBoxingElimination()) {
                b.startStatement().startCall("UFA", "unsafeSet" + vars.specializedKind.getFrameName());
                b.variable(vars.stackFrame);
                b.variable(vars.sp);
                b.tree(createGetArgument(vars, vars.specializedKind));
                b.end(2);
                b.end();
            } else {
                b.startStatement().startCall("UFA", "unsafeSetObject");
                b.variable(vars.stackFrame);
                b.variable(vars.sp);
                b.tree(createGetArgument(vars, FrameKind.OBJECT));
                b.end(2);
            }

            return metImpl;
        });

        CodeTreeBuilder bOuter = CodeTreeBuilder.createBuilder();
        bOuter.startStatement().startCall(helperName);
        bOuter.variable(vars.stackFrame);
        bOuter.variable(vars.bc);
        bOuter.variable(vars.bci);
        bOuter.variable(vars.sp);
        bOuter.variable(vars.consts);
        bOuter.end(2);

        bOuter.startAssign(vars.sp).variable(vars.sp).string(" + 1").end();

        return bOuter.build();

    }

    private CodeTree createGetArgument(ExecutionVariables vars, FrameKind kind) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (kind != FrameKind.OBJECT) {
            b.string("(", kind.getTypeName(), ") ");
        }
        b.startCall("UFA", "unsafeObjectArrayRead");
        b.variable(vars.consts);
        b.tree(createConstantIndex(vars, 0));
        b.end();

        return b.build();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public boolean splitOnBoxingElimination() {
        return true;
    }
}
