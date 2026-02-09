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
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionEncoding;

public final class InstructionRewriterModel {
    private final String generatedTypeName;
    private final TreeMap<String, InstructionModel> nameToInstruction;
    // Assigns an index to each instruction; used for stable code generation.
    private final Map<InstructionModel, Integer> stableInstructionOrdering;
    public final DFABuilder.DFAModel dfa;
    public final InstructionRewriteRuleModel[] rules;

    private InstructionRewriterModel(String generatedTypeName, SequencedCollection<InstructionModel> instructionSet, InstructionRewriteRuleModel[] rules) {
        this.generatedTypeName = generatedTypeName;

        if (instructionSet.isEmpty()) {
            throw new AssertionError("Instruction set cannot be empty.");
        }
        validateRules(rules);

        this.nameToInstruction = new TreeMap<>();
        this.stableInstructionOrdering = new IdentityHashMap<>(instructionSet.size());
        for (InstructionModel instruction : instructionSet) {
            InstructionModel existing = nameToInstruction.put(instruction.getName(), instruction);
            if (existing != null) {
                throw new IllegalArgumentException("Instruction name %s was assigned to multiple instructions: %s, %s.".formatted(instruction.getName(), existing, instruction));
            }
            stableInstructionOrdering.put(instruction, stableInstructionOrdering.size());
        }

        this.dfa = DFABuilder.buildDFA(rules);
        this.rules = rules;
    }

    public static InstructionRewriterModel create(String generatedTypeName, SequencedCollection<InstructionModel> instructionSet, InstructionRewriteRuleModel[] rules) {
        InstructionRewriterModel model = new InstructionRewriterModel(generatedTypeName, instructionSet, rules);
        for (var rule : rules) {
            rule.setParent(model);
        }
        return model;
    }

    private static void validateRules(InstructionRewriteRuleModel[] rules) {
        if (rules.length == 0) {
            throw new IllegalArgumentException("Rewrite rules cannot be empty.");
        }

        for (int i = 0; i < rules.length; i++) {
            for (int j = i + 1; j < rules.length; j++) {
                checkConflictingRules(rules[i], rules[j]);
            }
        }
    }

    private static void checkConflictingRules(InstructionRewriteRuleModel a, InstructionRewriteRuleModel b) {
        if (a.lhs.length == b.lhs.length) {
            // Check if the patterns are identical.
            for (int i = 0; i < a.lhs.length; i++) {
                if (a.lhs[i].instruction() != b.lhs[i].instruction()) {
                    // Different patterns.
                    return;
                }
            }
            throw new IllegalArgumentException("Multiple rewrite rules declared with the same lhs: %s, %s".formatted(a, b));
        } else {
            // Check if the smaller pattern is a substring of the larger pattern.
            InstructionRewriteRuleModel smaller;
            InstructionRewriteRuleModel larger;
            if (a.lhs.length < b.lhs.length) {
                smaller = a;
                larger = b;
            } else {
                smaller = b;
                larger = a;
            }

            outer: for (int i = 0; i < larger.lhs.length - smaller.lhs.length + 1; i++) {
                for (int j = 0; j < smaller.lhs.length; j++) {
                    if (larger.lhs[i + j].instruction() != smaller.lhs[j].instruction()) {
                        // No match starting at i. Try next substring.
                        continue outer;
                    }
                }
                throw new IllegalArgumentException("Rewrite rule %s has a lhs containing the lhs of rewrite rule %s".formatted(larger, smaller));
            }
        }
    }

    public String getGeneratedTypeName() {
        return generatedTypeName;
    }

    public InstructionModel getInstruction(String instructionName) {
        return nameToInstruction.get(instructionName);
    }

    public TreeMap<String, InstructionModel> getInstructions() {
        return nameToInstruction;
    }

    public TreeMap<InstructionEncoding, List<InstructionModel>> getInstructionsByEncoding() {
        return getInstructions().values().stream().collect(
                        Collectors.groupingBy(InstructionModel::getInstructionEncoding, TreeMap::new, Collectors.toList()));
    }

    public Comparator<InstructionModel> instructionComparator() {
        return (i1, i2) -> Integer.compare(stableInstructionOrdering.get(i1), stableInstructionOrdering.get(i2));
    }

}
