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

import java.util.List;
import java.util.function.Function;

import javax.lang.model.type.ArrayType;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.operations.OperationGeneratorUtils;
import com.oracle.truffle.dsl.processor.operations.OperationsContext;

public class SuperInstruction extends Instruction {

    private final Instruction[] instructions;

    public Instruction[] getInstructions() {
        return instructions;
    }

    public SuperInstruction(OperationsContext ctx, int id, Instruction... instrs) {
        super(ctx, makeName(instrs), id, 0);
        this.instructions = instrs;
    }

    private static String makeName(Instruction[] instrs) {
        StringBuilder sb = new StringBuilder("si");
        for (Instruction i : instrs) {
            sb.append(".");
            sb.append(i.name);
        }

        return sb.toString();
    }

    private static void createExecute(CodeTreeBuilder b, ExecutionVariables vars, Instruction instr, Function<ExecutionVariables, CodeTree> exec) {
        // todo: merge this with OpByCoGe code, since now we have duplication (and probably bugs)
        if (instr.splitOnBoxingElimination()) {
            b.startSwitch().string("unsafeFromBytecode($bc, $bci) & 7").end().startBlock();

            for (FrameKind kind : instr.getBoxingEliminationSplits()) {
                b.startCase().string(kind.toOrdinal()).end().startBlock();
                vars.specializedKind = kind;
                b.tree(exec.apply(vars));
                vars.specializedKind = null;
                b.statement("break");
                b.end();
            }

            if (instr.hasGeneric()) {
                b.startCase().string("7 /* generic */").end().startBlock();
                b.tree(exec.apply(vars));
                b.statement("break");
                b.end();
            }

            b.end();
        } else if (instr.numPushedValues == 0 || instr.alwaysBoxed()) {
            b.tree(exec.apply(vars));
        } else {
            b.startBlock();
            b.declaration("int", "primitiveTag", "unsafeFromBytecode($bc, $bci) & 7");
            b.tree(exec.apply(vars));
            b.end();
        }
    }

    @Override
    public CodeTree createExecuteCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        for (Instruction instr : instructions) {
            createExecute(b, vars, instr, instr::createExecuteCode);
            if (!instr.isBranchInstruction()) {
                b.startAssign(vars.bci).variable(vars.bci).string(" + ").tree(instr.createLength()).end();
            }
        }

        if (!instructions[instructions.length - 1].isBranchInstruction()) {
            b.tree(OperationGeneratorUtils.encodeExecuteReturn());
        }

        return b.build();
    }

    @Override
    public CodeTree createExecuteUncachedCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        for (Instruction instr : instructions) {
            b.startBlock();
            b.tree(instr.createExecuteUncachedCode(vars));
            if (!instr.isBranchInstruction()) {
                b.startAssign(vars.bci).variable(vars.bci).string(" + ").tree(instr.createLength()).end();
            }
            b.end();
        }
        if (!instructions[instructions.length - 1].isBranchInstruction()) {
            b.tree(OperationGeneratorUtils.encodeExecuteReturn());
        }

        return b.build();
    }

    @Override
    public boolean isBranchInstruction() {
        return true;
    }

    @Override
    protected int length() {
        int len = 0;
        for (Instruction i : instructions) {
            len += i.length();
        }

        return len;
    }

    @Override
    public CodeTree createDumpCode(ExecutionVariables vars) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.statement("int oldBci = $bci");

        b.startAssign("Object[] decSuper");
        b.startNewArray((ArrayType) context.getType(Object[].class), null);
        b.string("$bci");
        b.doubleQuote(name);
        b.startGroup().string("Arrays.copyOfRange($bc, $bci, $bci + ").tree(createLength()).string(")").end();
        b.startNewArray((ArrayType) context.getType(Object[].class), null).end();
        b.startNewArray((ArrayType) context.getType(Object[].class), CodeTreeBuilder.singleString("" + instructions.length)).end();
        b.end(2); // outer array, assign

        for (int i = 0; i < instructions.length; i++) {
            b.startBlock();
            b.tree(instructions[i].createDumpCode(vars));
            b.startAssign("((Object[]) decSuper[4])[" + i + "]").string("dec").end();
            b.end();

            b.startStatement().string("$bci += ").tree(instructions[i].createLength()).end();
        }

        b.startAssign("Object[] dec").string("decSuper").end();

        b.statement("$bci = oldBci");

        return b.build();
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        // todo: implement
        return null;
    }

    @Override
    public boolean alwaysBoxed() {
        return instructions[0].alwaysBoxed();
    }

    @Override
    public List<FrameKind> getBoxingEliminationSplits() {
        return instructions[0].getBoxingEliminationSplits();
    }

    @Override
    public boolean splitOnBoxingElimination() {
        return false;
    }
}
