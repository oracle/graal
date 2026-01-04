/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.AnnotationProcessor;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.generator.InstructionRewriterElement.StepMethod;
import com.oracle.truffle.dsl.processor.bytecode.model.GenerateInstructionRewriterTemplate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.bytecode.parser.InstructionRewriterTestParser;

/**
 * Test-only. See {@link InstructionRewriterTestParser} for more information.
 */
public class InstructionRewriterTestGenerator extends CodeTypeElementFactory<GenerateInstructionRewriterTemplate> {

    @Override
    public List<CodeTypeElement> create(ProcessorContext context, AnnotationProcessor<?> processor, GenerateInstructionRewriterTemplate template) {
        OpcodesElement opcodes = new OpcodesElement(template);
        InstructionRewriterElement result = new InstructionRewriterElement(context, template.getTemplateType(), template.model, opcodes::getConstant);
        GeneratorUtils.addGeneratedBy(context, result, template.getTemplateType());
        result.getModifiers().removeAll(List.of(Modifier.PRIVATE, Modifier.STATIC));
        result.add(opcodes);
        result.addAll(createTestHelpers(result, template));
        return List.of(result);

    }

    private static class OpcodesElement extends CodeTypeElement {

        final Map<String, CodeVariableElement> constants = new HashMap<>();

        OpcodesElement(GenerateInstructionRewriterTemplate template) {
            super(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), ElementKind.CLASS, null, "Opcodes");

            int i = 1;
            ProcessorContext ctx = template.getContext();
            for (InstructionModel instruction : template.instructionSet.values()) {
                CodeVariableElement constant = new CodeVariableElement(Set.of(Modifier.STATIC, Modifier.FINAL), ctx.getType(short.class), instruction.getConstantName());
                constant.createInitBuilder().string(i++);
                constants.put(instruction.getName(), this.add(constant));
            }
        }

        CodeVariableElement getConstant(InstructionModel instruction) {
            return constants.get(instruction.getName());
        }

    }

    /**
     * Creates helper functions useful for testing (but not needed in Bytecode DSL interpreters).
     */
    private static List<CodeExecutableElement> createTestHelpers(InstructionRewriterElement rewriter, GenerateInstructionRewriterTemplate template) {
        return List.of(createRewrite(rewriter, template), createRewritesTo(rewriter, template));
    }

    private static CodeExecutableElement createRewrite(InstructionRewriterElement rewriter, GenerateInstructionRewriterTemplate template) {
        ProcessorContext context = rewriter.context;
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.STATIC), context.getType(String[].class), "rewrite");
        ex.addParameter(new CodeVariableElement(context.getType(String.class), "instructions"));
        ex.setVarArgs(true);

        CodeTreeBuilder b = ex.createBuilder();

        b.startDeclaration(context.getType(int.class), "state").variable(rewriter.startState).end();
        b.startFor().type(context.getDeclaredType(String.class)).string(" instruction : instructions").end().startBlock();

        b.startSwitch().string("instruction").end().startBlock();
        for (var entry : template.instructionSet.entrySet()) {
            String instructionName = entry.getKey();
            InstructionModel instruction = entry.getValue();
            b.startCase().doubleQuote(instructionName).end().startCaseBlock();
            b.startAssign("state");
            StepMethod stepMethod = rewriter.getStepMethod(instruction.getInstructionEncoding());
            if (stepMethod == null) {
                b.variable(rewriter.startState);
            } else {
                b.startStaticCall(stepMethod.method()).string("state").staticReference(rewriter.getInstructionConstant(instruction)).end();
            }
            b.end();
            b.statement("break");
            b.end(); // case
        }
        b.caseDefault().startCaseBlock();
        b.startThrow().startNew(context.getDeclaredType(AssertionError.class));
        b.startGroup().doubleQuote("Unexpected instruction ").string(" + instruction").end();
        b.end(2);
        b.end(); // case default
        b.end(); // switch

        b.end(); // for

        b.startReturn().startCall("rewritesTo").string("state").end(2);

        return ex;
    }

    private static CodeExecutableElement createRewritesTo(InstructionRewriterElement rewriter, GenerateInstructionRewriterTemplate template) {
        ProcessorContext context = rewriter.context;
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.PUBLIC, Modifier.STATIC), context.getType(String[].class), "rewritesTo");
        ex.addParameter(new CodeVariableElement(context.getType(int.class), "state"));

        Map<InstructionModel, String> instructionModelToString = template.instructionSet.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        CodeTreeBuilder b = ex.createBuilder();
        b.startSwitch().string("state").end().startBlock();
        for (var state : rewriter.model.dfa.getAcceptingStates()) {
            b.startCase().staticReference(rewriter.stateConstants.get(state)).end().startCaseBlock();
            b.startReturn().startNewArray(new CodeTypeMirror.ArrayCodeTypeMirror(context.getDeclaredType(String.class)), null);
            for (var pattern : state.getAcceptingRule().rhs) {
                String instructionString = instructionModelToString.get(pattern.instruction());
                if (instructionString == null) {
                    throw new AssertionError();
                }
                b.doubleQuote(instructionString);
            }
            b.end(2);
            b.end(); // case
        }
        b.end(); // switch

        b.startReturn().string("null").end();

        return ex;
    }
}
