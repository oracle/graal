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

import static com.oracle.truffle.dsl.processor.bytecode.model.DFABuilder.DFAModel.DFAState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionRewriterModel;
import com.oracle.truffle.dsl.processor.bytecode.model.DFABuilder.RewriteRuleState;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediateEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionRewriteRuleModel.ResolvedInstructionPatternModel;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class InstructionRewriterElement extends CodeTypeElement {
    public final ProcessorContext context;
    public final InstructionRewriterModel model;
    private final Function<InstructionModel, CodeVariableElement> instructionConstantSupplier;
    private final Map<InstructionEncoding, StepMethod> stepMethods;
    public final Map<DFAState, CodeVariableElement> stateConstants;
    public final CodeVariableElement startState;

    @SuppressWarnings("this-escape")
    public InstructionRewriterElement(ProcessorContext context, TypeElement templateType, InstructionRewriterModel model, Function<InstructionModel, CodeVariableElement> instructionConstantSupplier) {
        super(Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL), ElementKind.CLASS, ElementUtils.findPackageElement(templateType), model.getGeneratedTypeName());
        this.context = context;
        this.model = model;
        this.instructionConstantSupplier = instructionConstantSupplier;
        this.stepMethods = new LinkedHashMap<>();
        this.stateConstants = new LinkedHashMap<>();
        this.startState = createConstant(model.dfa.startState);
        for (DFAState state : model.dfa.states) {
            if (state == model.dfa.startState) {
                continue;
            }
            createConstant(state);
        }
        TreeMap<InstructionEncoding, List<InstructionModel>> instructionsByEncoding = model.getInstructionsByEncoding();
        for (var entry : instructionsByEncoding.entrySet()) {
            InstructionEncoding encoding = entry.getKey();
            List<InstructionModel> instructions = entry.getValue();
            StepMethod step = createStepMethod(encoding, instructions);
            if (step != null) {
                this.stepMethods.put(encoding, step);
                this.add(step.method);
            }
        }
    }

    private String fieldName(DFAState state) {
        if (state == model.dfa.startState) {
            return "START";
        }
        return "s" + state.id;
    }

    private CodeVariableElement createConstant(DFAState state) {
        CodeVariableElement field = new CodeVariableElement(Set.of(Modifier.STATIC, Modifier.FINAL), context.getType(int.class), fieldName(state));
        field.createInitBuilder().string(state.id);

        CodeTreeBuilder docBuilder = field.createDocBuilder().startJavadoc();
        docBuilder.string("Rewrite rule state:").newLine();
        for (RewriteRuleState ruleState : state.getRewriteStates().stream().sorted().toList()) {
            docBuilder.string("  ").string(ruleState.toString()).newLine();
        }
        docBuilder.string("Transitions:").newLine();
        if (state.transitions.isEmpty()) {
            docBuilder.string("  none").newLine();
        }
        List<InstructionModel> orderedTransitions = state.transitions.keySet().stream().map(model::getInstruction).sorted(model.instructionComparator()).toList();
        for (InstructionModel instruction : orderedTransitions) {
            docBuilder.string("  ").string(instruction.getName()).string(" -> ").string(fieldName(state.transitions.get(instruction.getName()))).newLine();
        }

        docBuilder.end();
        stateConstants.put(state, add(field));
        return field;
    }

    private StepMethod createStepMethod(InstructionEncoding encoding, List<InstructionModel> instructions) {
        SequencedMap<DFAState, List<RewriteRuleState>> steppingStates = computeRelevantRulesByState(instructions);
        if (steppingStates.isEmpty()) {
            return null;
        }

        StringBuilder methodName = new StringBuilder("step");
        for (InstructionImmediateEncoding immediate : encoding.immediates()) {
            methodName.append(immediate.width().toEncodedName());
        }

        CodeExecutableElement ex = new CodeExecutableElement(Set.of(Modifier.STATIC), context.getType(int.class), methodName.toString(),
                        new CodeVariableElement(context.getType(int.class), "currentState"),
                        new CodeVariableElement(context.getType(short.class), "opcode"));
        SequencedSet<RewriteRuleState> allRewriteStates = steppingStates.values().stream().flatMap(List::stream).sorted().collect(Collectors.toCollection(LinkedHashSet::new));
        boolean canRewrite = allRewriteStates.stream().anyMatch(RewriteRuleState::leadsToAcceptingState);

        CodeTreeBuilder docBuilder = ex.createDocBuilder().startJavadoc();
        docBuilder.string("Instructions: ").string(instructionsString(instructions)).newLine();
        docBuilder.string("Rewrite rules: ").newLine();
        for (RewriteRuleState rewriteState : allRewriteStates) {
            docBuilder.string("  ").string(rewriteState.toString()).newLine();
        }
        docBuilder.end();

        CodeTreeBuilder p = ex.createBuilder();

        Map<EqualityCodeTree, List<Map.Entry<DFAState, List<RewriteRuleState>>>> stateGrouping = EqualityCodeTree.group(p, steppingStates.entrySet(), (entry, b) -> {
            DFAState state = entry.getKey();
            List<RewriteRuleState> rewriteStates = entry.getValue();

            b.startSwitch().string("opcode").end().startBlock();
            Set<String> seen = new HashSet<>();
            for (var rewriteState : rewriteStates) {
                InstructionModel instruction = rewriteState.getNextInstruction().instruction();
                if (!seen.add(instruction.getName())) {
                    continue; // case for this instruction was already emitted.
                }

                b.startCase().staticReference(getInstructionConstant(instruction)).end().startCaseBlock();
                b.startReturn().staticReference(stateConstants.get(state.transitions.get(instruction.getName()))).end();
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.startReturn().staticReference(startState).string(" /* reset */").end();
            b.end();
            b.end(); // switch(opcode)
        });

        CodeTreeBuilder b = p;
        b.startSwitch().string("currentState").end().startBlock();
        for (var group : stateGrouping.entrySet()) {
            EqualityCodeTree key = group.getKey();
            for (var entry : group.getValue()) {
                b.startCase().staticReference(stateConstants.get(entry.getKey())).end();
            }
            b.startCaseBlock();
            b.tree(key.getTree());
            b.end();
        }
        // Accepting states have no transitions.
        b.caseDefault().startCaseBlock();
        b.startReturn().staticReference(startState).string(" /* reset */").end();
        b.end();
        b.end(); // switch(currentState)

        return new StepMethod(ex, allRewriteStates, canRewrite);
    }

    /**
     * Compute the subset of the DFA states and rules that the given instructions can transition on.
     */
    private SequencedMap<DFAState, List<RewriteRuleState>> computeRelevantRulesByState(List<InstructionModel> instructions) {
        Set<String> instructionNames = instructions.stream().map(InstructionModel::getName).collect(Collectors.toSet());
        SequencedMap<DFAState, List<RewriteRuleState>> result = new LinkedHashMap<>();
        for (DFAState state : model.dfa.states) {
            // Collect all rewrite states _ * X _ -> _ where X is one of the given instructions.
            for (RewriteRuleState rewriteState : state.getRewriteStates()) {
                ResolvedInstructionPatternModel nextInstruction = rewriteState.getNextInstruction();
                if (nextInstruction != null && instructionNames.contains(nextInstruction.instruction().getName())) {
                    if (!state.transitions.containsKey(nextInstruction.instruction().getName())) {
                        throw new AssertionError("DFA state with rewrite state " + rewriteState + " does not have a transition on " + nextInstruction);
                    }

                    result.computeIfAbsent(state, unused -> new ArrayList<>()).add(rewriteState);
                }
            }
        }
        return result;
    }

    public record StepMethod(CodeExecutableElement method, SequencedCollection<RewriteRuleState> rewriteStates, boolean canRewrite) {

    }

    public StepMethod getStepMethod(InstructionEncoding encoding) {
        return stepMethods.get(encoding);
    }

    public CodeVariableElement getInstructionConstant(InstructionModel instruction) {
        CodeVariableElement instructionConstant = instructionConstantSupplier.apply(instruction);
        if (instructionConstant == null) {
            throw new AssertionError("Missing instruction constant for instruction " + instruction);
        }
        return instructionConstant;
    }

    public static String instructionsString(List<InstructionModel> instructions) {
        return instructions.stream() //
                        .map(InstructionModel::getName) //
                        .sorted() //
                        .collect(Collectors.joining(", ", "[", "]"));
    }

}
