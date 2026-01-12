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
package com.oracle.truffle.dsl.processor.bytecode.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionPatternModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionRewriteRuleModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionRewriterModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.GenerateInstructionRewriterTemplate;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;
import com.oracle.truffle.dsl.processor.bytecode.generator.InstructionRewriterTestGenerator;

/**
 * Test-only. Parses a {@code @GenerateInstructionRewriter} specification, which is used by
 * {@link InstructionRewriterTestGenerator} to generate an instruction rewriter.
 * <p>
 * Normally, an instruction rewriter is generated during regular Bytecode DSL code generation. This
 * parser enables whitebox testing of rewrite rule validation and DFA generation without requiring a
 * Bytecode DSL interpreter.
 */
public class InstructionRewriterTestParser extends AbstractParser<GenerateInstructionRewriterTemplate> {

    @Override
    protected GenerateInstructionRewriterTemplate parse(Element element, List<AnnotationMirror> mirrors) {
        assert mirrors.size() == 1;
        AnnotationMirror mirror = mirrors.getFirst();

        try {
            SequencedMap<String, InstructionModel> instructionSet = parseInstructionSet(ElementUtils.getAnnotationValueList(AnnotationMirror.class, mirror, "instructionSet"));

            List<AnnotationMirror> rewriteRuleMirrors = ElementUtils.getAnnotationValueList(AnnotationMirror.class, mirror, "rules");
            InstructionRewriteRuleModel[] rules = new InstructionRewriteRuleModel[rewriteRuleMirrors.size()];
            for (int i = 0; i < rules.length; i++) {
                rules[i] = parseInstructionRewriteRule(rewriteRuleMirrors.get(i), instructionSet);
            }

            InstructionRewriterModel model = InstructionRewriterModel.create(element.getSimpleName().toString() + "Gen", instructionSet.sequencedValues(), rules);
            return new GenerateInstructionRewriterTemplate(context, (TypeElement) element, mirror, model, instructionSet);
        } catch (IllegalArgumentException ex) {
            return reportError((TypeElement) element, mirror, ex.getMessage());
        }
    }

    private SequencedMap<String, InstructionModel> parseInstructionSet(List<AnnotationMirror> instructions) {
        SequencedMap<String, InstructionModel> instructionSet = new LinkedHashMap<>();
        for (var instruction : instructions) {
            String name = ElementUtils.getAnnotationValue(String.class, instruction, "name");
            int stackEffect = ElementUtils.getAnnotationValue(Integer.class, instruction, "stackEffect");
            InstructionModel instructionModel = new InstructionModel(InstructionKind.LOAD_CONSTANT, name, createDummySignature(stackEffect));

            for (String immediate : ElementUtils.getAnnotationValueList(String.class, instruction, "immediates")) {
                instructionModel.addImmediate(ImmediateKind.CONSTANT, immediate);
            }

            instructionSet.put(name, instructionModel);
        }
        return instructionSet;
    }

    private Signature createDummySignature(int stackEffect) {
        if (stackEffect > 1) {
            throw new AssertionError("invalid stack effect " + stackEffect);
        }
        int numParams = 1 - stackEffect;
        return new Signature(context.getDeclaredType(Object.class), Collections.nCopies(numParams, context.getDeclaredType(Object.class)));
    }

    private static InstructionRewriteRuleModel parseInstructionRewriteRule(AnnotationMirror rewriteRuleMirror, Map<String, InstructionModel> instructionSet) {
        return new InstructionRewriteRuleModel(
                        parseInstructionPatternModels(ElementUtils.getAnnotationValueList(AnnotationMirror.class, rewriteRuleMirror, "lhs"), instructionSet),
                        parseInstructionPatternModels(ElementUtils.getAnnotationValueList(AnnotationMirror.class, rewriteRuleMirror, "rhs"), instructionSet));
    }

    private static InstructionPatternModel[] parseInstructionPatternModels(List<AnnotationMirror> instructionPatternMirrors, Map<String, InstructionModel> instructionSet) {
        InstructionPatternModel[] result = new InstructionPatternModel[instructionPatternMirrors.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = parseInstructionPatternModel(instructionPatternMirrors.get(i), instructionSet);
        }
        return result;
    }

    private static InstructionPatternModel parseInstructionPatternModel(AnnotationMirror instructionPatternMirror, Map<String, InstructionModel> instructionSet) {
        String name = ElementUtils.getAnnotationValue(String.class, instructionPatternMirror, "value");
        InstructionModel instruction = instructionSet.get(name);
        if (instruction == null) {
            throw new IllegalArgumentException("Unknown instruction " + name);
        }

        return new InstructionPatternModel(
                        instruction,
                        parseImmediates(instruction, instructionPatternMirror));
    }

    private static String[] parseImmediates(InstructionModel instruction, AnnotationMirror instructionPatternMirror) {
        List<String> immediates = ElementUtils.getAnnotationValueList(String.class, instructionPatternMirror, "immediates");
        if (immediates.isEmpty() && !instruction.immediates.isEmpty()) {
            return new String[instruction.immediates.size()];
        } else {
            return immediates.toArray(String[]::new);
        }
    }

    public GenerateInstructionRewriterTemplate reportError(TypeElement templateType, AnnotationMirror annotation, String text, Object... params) {
        GenerateInstructionRewriterTemplate result = new GenerateInstructionRewriterTemplate(context, templateType, annotation, null, null);
        result.addError(text, params);
        return result;
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.GenerateInstructionRewriter;
    }
}
