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
package com.oracle.truffle.dsl.processor.operations;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.lang.model.element.Element;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.java.transform.AbstractCodeWriter;
import com.oracle.truffle.dsl.processor.operations.Operation.BuilderVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.FrameKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.EmitArguments;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;

public class OperationGeneratorUtils {

    public static TruffleTypes getTypes() {
        return ProcessorContext.getInstance().getTypes();
    }

    public static String toScreamCase(String s) {
        return s.replaceAll("([a-z]|[A-Z]+)([A-Z])", "$1_$2").replace('.', '_').toUpperCase();
    }

    public static CodeTree createEmitInstruction(BuilderVariables vars, Instruction instr, EmitArguments arguments) {
        return instr.createEmitCode(vars, arguments);
    }

    public static CodeTree createEmitBranchInstruction(BuilderVariables vars, Instruction instr, CodeVariableElement arguments) {
        return createEmitBranchInstruction(vars, instr, CodeTreeBuilder.singleVariable(arguments));
    }

    public static CodeTree createEmitBranchInstruction(BuilderVariables vars, Instruction instr, CodeTree arguments) {
        EmitArguments args = new EmitArguments();
        args.branchTargets = new CodeTree[]{arguments};
        return createEmitInstruction(vars, instr, args);
    }

    public static CodeTree createEmitLocalInstruction(BuilderVariables vars, Instruction instr, CodeTree arguments) {
        EmitArguments args = new EmitArguments();
        args.locals = new CodeTree[]{arguments};
        return createEmitInstruction(vars, instr, args);
    }

    public static CodeTree createCreateLabel() {
        return CodeTreeBuilder.createBuilder().startCall("(OperationLabelImpl) createLabel").end().build();
    }

    @SuppressWarnings("unused")
    public static CodeTree createEmitLabel(BuilderVariables vars, CodeTree label) {
        return CodeTreeBuilder.createBuilder().startStatement().startCall("doEmitLabel").tree(label).end(2).build();
    }

    public static CodeTree createEmitLabel(BuilderVariables vars, CodeVariableElement label) {
        return createEmitLabel(vars, CodeTreeBuilder.singleVariable(label));
    }

    public static CodeTree createReadOpcode(CodeTree bc, CodeTree bci) {
        return CodeTreeBuilder.createBuilder().tree(bc).string("[").tree(bci).string("]").build();
    }

    public static CodeTree createReadOpcode(CodeVariableElement bc, CodeVariableElement bci) {
        return createReadOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci));
    }

    public static CodeTree createWriteOpcode(CodeTree bc, CodeTree bci, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startStatement().tree(bc).string("[").tree(bci).string("] = (short) (").tree(value).string(")").end().build();
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, CodeVariableElement bci, CodeVariableElement value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci),
                        CodeTreeBuilder.singleVariable(value));
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, String bci, CodeVariableElement value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleString(bci),
                        CodeTreeBuilder.singleVariable(value));
    }

    public static String printCode(Element el) {
        StringWriter wr = new StringWriter();
        new AbstractCodeWriter() {
            {
                writer = wr;
            }

            @Override
            protected Writer createWriter(CodeTypeElement clazz) throws IOException {
                return wr;
            }
        }.visit(el);

        return wr.toString();
    }

    public static CodeTree createInstructionSwitch(OperationsData data, ExecutionVariables vars, Function<Instruction, CodeTree> body) {
        return createInstructionSwitch(data, vars, true, body);
    }

    private static final CodeTree BREAK_TREE = CodeTreeBuilder.createBuilder().statement("break").build();

    public static CodeTree createInstructionSwitch(OperationsData data, ExecutionVariables vars, boolean instrumentation, Function<Instruction, CodeTree> body) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        List<CodeTree> trees = new ArrayList<>();
        List<List<Instruction>> treeInstructions = new ArrayList<>();

        CodeTree defaultTree = body.apply(null);
        boolean hasDefault;

        if (defaultTree == null) {
            hasDefault = false;
        } else {
            hasDefault = true;
            trees.add(defaultTree);
            treeInstructions.add(null);
        }

        outerLoop: for (Instruction instr : data.getInstructions()) {
            if (instr.isInstrumentationOnly() && !instrumentation) {
                continue;
            }

            CodeTree result = body.apply(instr);
            if (result == null) {
                if (hasDefault) {
                    // we have to explicitly do nothing for this instruction, since we have default
                    result = BREAK_TREE;
                } else {
                    continue;
                }
            }

            for (int i = 0; i < trees.size(); i++) {
                if (result.isEqualTo(trees.get(i))) {
                    treeInstructions.get(i).add(instr);
                    continue outerLoop;
                }
            }

            trees.add(result);

            List<Instruction> newList = new ArrayList<>(1);
            newList.add(instr);
            treeInstructions.add(newList);

        }

        b.startSwitch().tree(createReadOpcode(vars.bc, vars.bci)).end().startBlock();
        for (int i = 0; i < trees.size(); i++) {
            if (treeInstructions.get(i) == null) {
                b.caseDefault();
            } else {
                for (Instruction instr : treeInstructions.get(i)) {
                    b.startCase().variable(instr.opcodeIdField).end();
                }
            }
            b.startBlock();
            b.tree(trees.get(i));
            b.end();
        }
        b.end();

        return b.build();
    }

    public static CodeTree callSetResultBoxed(CodeTree bciOffset, FrameKind kind) {
        return callSetResultBoxed(bciOffset, toFrameTypeConstant(kind));
    }

    public static CodeTree callSetResultBoxed(CodeTree bciOffset, CodeTree kind) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startStatement().startCall("doSetResultBoxed");
        b.string("$bc");
        b.string("$bci");
        b.tree(bciOffset);
        b.tree(kind);
        b.end(2);

        return b.build();
    }

    public static CodeTree toFrameTypeConstant(FrameKind kind) {
        return CodeTreeBuilder.singleString(kind.ordinal() + " /* " + kind + " */");
    }
}
