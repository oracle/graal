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
import com.oracle.truffle.dsl.processor.operations.OperationsContext;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;

public class YieldInstruction extends Instruction {

    private static final String CONTINUATION_POINT = "continuation-point";

    public YieldInstruction(OperationsContext ctx, int id) {
        super(ctx, "yield", id, 1);
        addPopSimple("value");
        addConstant(CONTINUATION_POINT, null);
    }

    @Override
    protected CodeTree createCustomEmitCode(BuilderVariables vars, EmitArguments args) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startIf().string("yieldLocations == null").end().startBlock();
        b.statement("yieldLocations = new ContinuationLocationImpl[8]");
        b.end().startElseIf().string("yieldLocations.length <= yieldCount").end().startBlock();
        b.statement("yieldLocations = Arrays.copyOf(yieldLocations, yieldCount * 2)");
        b.end();
        return b.build();
    }

    @Override
    protected CodeTree createConstantInitCode(BuilderVariables vars, EmitArguments args, Object marker, int index) {
        if (!CONTINUATION_POINT.equals(marker) && index != 0) {
            throw new AssertionError("what constant?");
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.string("yieldLocations[yieldCount] = ");

        b.startNew("ContinuationLocationImpl");
        b.string("yieldCount++");
        b.startGroup().string("(curStack << 16) | (bci + ").tree(createLength()).string(")").end();
        b.end();
        return b.build();
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().string("ContinuationLocationImpl cont = (ContinuationLocationImpl) $consts[").tree(createConstantIndex(vars, 0)).string("]").end();
        b.statement("$stackFrame.copyTo($this._maxLocals, $localFrame, $this._maxLocals, ($sp - 1 - $this._maxLocals))");
        b.statement("$stackFrame.setObject($sp - 1, cont.createResult($localFrame, $stackFrame.getObject($sp - 1)))");

        // b.statement("System.err.printf(\" yielding: %s %s %d%n\", $stackFrame, $localFrame,
        // $sp)");

        b.startReturn().string("(($sp - 1) << 16) | 0xffff").end();

        return b.build();
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return null;
    }

    @Override
    public boolean alwaysBoxed() {
        return true;
    }

    @Override
    public boolean neverWrapInMethod() {
        return true;
    }
}
