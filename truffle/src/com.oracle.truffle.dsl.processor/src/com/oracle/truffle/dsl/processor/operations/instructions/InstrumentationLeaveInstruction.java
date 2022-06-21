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

public class InstrumentationLeaveInstruction extends Instruction {
    public InstrumentationLeaveInstruction(int id) {
        super("instrument.leave", id, ResultType.BRANCH, InputType.INSTRUMENT, InputType.BRANCH_TARGET, InputType.BRANCH_TARGET);
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startAssign("ProbeNode probe");
        b.variable(vars.probeNodes).string("[").variable(vars.inputs[0]).string("].");
        b.startCall("getTreeProbeNode");
        b.end(2);

        b.startIf().string("probe != null").end();
        b.startBlock();

        b.startAssign("Object result");
        b.startCall("probe", "onReturnExceptionalOrUnwind");
        b.variable(vars.frame);
        b.string("null");
        b.string("false");
        b.end(2);

        b.startIf().string("result == ProbeNode.UNWIND_ACTION_REENTER").end();
        b.startBlock();

        b.startAssign(vars.results[0]).variable(vars.inputs[1]).end();

        b.end().startElseIf().string("result != null").end();
        b.startBlock();

        // HACK, refactor this push somehow
        b.startStatement().startCall(vars.frame, "setObject").string("sp++").string("result").end(2);
        b.startAssign(vars.results[0]).variable(vars.inputs[2]).end();

        b.end().startElseBlock();

        b.startAssign(vars.results[0]).variable(vars.bci).string(" + " + length()).end();

        b.end();
        b.end().startElseBlock();

        b.startAssign(vars.results[0]).variable(vars.bci).string(" + " + length()).end();

        b.end();

        return b.build();
    }

    @Override
    public boolean isInstrumentationOnly() {
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
