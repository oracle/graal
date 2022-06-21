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

import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class BranchInstruction extends Instruction {

    private static final ProcessorContext context = ProcessorContext.getInstance();
    private static final DeclaredType TRUFFLE_SAFEPOINT = context.getDeclaredType("com.oracle.truffle.api.TruffleSafepoint");
    private static final DeclaredType BYTECODE_OSR_NODE = context.getDeclaredType("com.oracle.truffle.api.nodes.BytecodeOSRNode");

    public BranchInstruction(int id) {
        super("branch", id, 0);
        addBranchTarget("target");
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.declaration("int", "targetBci", createBranchTargetIndex(vars, 0));

        b.startIf().string("targetBci <= ").variable(vars.bci).end().startBlock();
        // {
        b.startStatement().startStaticCall(TRUFFLE_SAFEPOINT, "poll");
        b.string("this");
        b.end(2);

        // todo: reporting loop count

        b.startIf();
        b.tree(GeneratorUtils.createInInterpreter());
        b.string(" && ");
        b.startStaticCall(BYTECODE_OSR_NODE, "pollOSRBackEdge").string("this").end();
        b.end().startBlock();
        // {
        b.startAssign("Object osrResult").startStaticCall(BYTECODE_OSR_NODE, "tryOSR");
        b.string("this");
        b.string("targetBci");
        b.variable(vars.sp);
        b.string("null");
        b.variable(vars.frame);
        b.end(2);

        b.startIf().string("osrResult != null").end().startBlock();
        // {
        b.startReturn().string("osrResult").end();
        // }
        b.end();
        // }
        b.end();
        // }
        b.end();

        b.startAssign(vars.bci).string("targetBci").end();

        b.statement("continue loop");

        return b.build();
    }

    @Override
    public CodeTree createCustomEmitCode(BuilderVariables vars, EmitArguments arguments) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().startCall("calculateLeaves");
        b.variable(vars.operationData);
        b.tree(arguments.branchTargets[0]);
        b.end(2);

        return b.build();
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
    }

    @Override
    public BoxingEliminationBehaviour boxingEliminationBehaviour() {
        return BoxingEliminationBehaviour.DO_NOTHING;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }
}
