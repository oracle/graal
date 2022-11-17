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
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
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
        return CodeTreeBuilder.createBuilder().startCall("unsafeFromBytecode").tree(bc).tree(bci).end().build();
    }

    public static CodeTree createReadOpcode(CodeVariableElement bc, CodeVariableElement bci) {
        return createReadOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci));
    }

    public static CodeTree createReadOpcode(CodeVariableElement bc, String bci) {
        return createReadOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleString(bci));
    }

    public static CodeTree createWriteOpcode(CodeTree bc, CodeTree bci, CodeTree value) {
        return CodeTreeBuilder.createBuilder().startStatement().startCall("unsafeWriteBytecode").tree(bc).tree(bci).startGroup().string("(short) ").tree(value).end(3).build();
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, CodeVariableElement bci, CodeVariableElement value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci),
                        CodeTreeBuilder.singleVariable(value));
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, CodeVariableElement bci, String value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci),
                        CodeTreeBuilder.singleString(value));
    }

    public static CodeTree createWriteOpcode(CodeVariableElement bc, CodeVariableElement bci, CodeTree value) {
        return createWriteOpcode(
                        CodeTreeBuilder.singleVariable(bc),
                        CodeTreeBuilder.singleVariable(bci),
                        value);
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

        b.startSwitch().tree(OperationGeneratorUtils.extractInstruction(data.getOperationsContext(), createReadOpcode(vars.bc, vars.bci))).end().startBlock();
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
        return CodeTreeBuilder.singleString(kind.toOrdinal());
    }

    public static void createHelperMethod(CodeTypeElement element, String name, Supplier<CodeExecutableElement> e) {
        if (!element.getEnclosedElements().stream().anyMatch(x -> x.getSimpleName().toString().equals(name))) {
            CodeExecutableElement ex = e.get();
            if (!ex.getSimpleName().toString().equals(name)) {
                throw new IllegalArgumentException("names do not match");
            }
            element.add(ex);
        }
    }

    public static void checkAccessibility(Element el) {
        checkAccessibility(el, "");
    }

    private static void checkAccessibility(Element el, String namePrefix) {
        Set<Modifier> mods = el.getModifiers();

        if (mods.contains(Modifier.PRIVATE) || el.getSimpleName().toString().equals("<cinit>")) {
            return;
        }

        if (mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.PROTECTED)) {
            List<? extends Element> els = el.getEnclosedElements();
            if (els != null) {
                for (Element cel : els) {
                    checkAccessibility(cel, namePrefix + el.getSimpleName() + ".");
                }
            }
            return;
        }

        throw new AssertionError(namePrefix + el.getSimpleName() + " must not be package-protected");
    }

    public static CodeTree combineBoxingBits(OperationsContext ctx, CodeTree instr, CodeTree kind) {
        if (ctx.hasBoxingElimination()) {
            return CodeTreeBuilder.createBuilder().startParantheses().startParantheses().tree(instr).string(" << " + OperationGeneratorFlags.BOXING_ELIM_BITS).end().string(
                            " | ").tree(kind).end().build();
        } else {
            return instr;
        }
    }

    public static CodeTree combineBoxingBits(OperationsContext ctx, Instruction instr, CodeTree kind) {
        return combineBoxingBits(ctx, CodeTreeBuilder.singleVariable(instr.opcodeIdField), kind);
    }

    public static CodeTree combineBoxingBits(OperationsContext ctx, Instruction instr, String kind) {
        return combineBoxingBits(ctx, instr, CodeTreeBuilder.singleString(kind));
    }

    public static CodeTree combineBoxingBits(OperationsContext ctx, Instruction instr, FrameKind kind) {
        return combineBoxingBits(ctx, instr, kind.toOrdinal());
    }

    public static CodeTree combineBoxingBits(OperationsContext ctx, Instruction instr, int kind) {
        return combineBoxingBits(ctx, instr, "" + kind);
    }

    public static CodeTree extractInstruction(OperationsContext ctx, CodeTree instr) {
        if (ctx.hasBoxingElimination()) {
            return CodeTreeBuilder.createBuilder().startParantheses().tree(instr).string(" >> " + OperationGeneratorFlags.BOXING_ELIM_BITS).end().string(
                            " & " + ((1 << (16 - OperationGeneratorFlags.BOXING_ELIM_BITS)) - 1)).build();
        } else {
            return instr;
        }
    }

    public static CodeTree extractInstruction(OperationsContext ctx, String instr) {
        return extractInstruction(ctx, CodeTreeBuilder.singleString(instr));
    }

    public static CodeTree extractBoxingBits(OperationsContext ctx, CodeTree instr) {
        if (ctx.hasBoxingElimination()) {
            return CodeTreeBuilder.createBuilder().tree(instr).string(" & " + ((1 << OperationGeneratorFlags.BOXING_ELIM_BITS) - 1)).build();
        } else {
            return CodeTreeBuilder.singleString("0");
        }
    }
}
