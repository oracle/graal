/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionPatternModel.Binding;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionPatternModel.ImmediatePattern;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionPatternModel.Literal;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionPatternModel.Wildcard;

public class InstructionRewriteRuleModel implements Comparable<InstructionRewriteRuleModel> {
    public final ResolvedInstructionPatternModel[] lhs;
    public final ResolvedInstructionPatternModel[] rhs;
    public final Map<String, ImmediateReference> bindings;
    private final RewriteSection[] sections;
    private final RewriteKind rewriteKind;
    private InstructionRewriterModel parent;
    private boolean endsWithReturn;
    private boolean returnFinalInstructionBci;

    public enum RewriteSectionKind {
        DELETE,
        IDENTITY,
        REPLACE
    }

    public record RewriteSection(RewriteSectionKind kind, InstructionPatternModel[] patterns, InstructionPatternModel[] replacementPatterns) {
        public RewriteSection {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(patterns);
            if (patterns.length == 0) {
                throw new IllegalArgumentException("Rewrite sections must contain at least one instruction pattern.");
            }
            if (kind == RewriteSectionKind.REPLACE) {
                if (replacementPatterns == null || replacementPatterns.length == 0) {
                    throw new IllegalArgumentException("REPLACE sections must contain at least one replacement instruction pattern.");
                }
                /*
                 * We currently only support a single instruction replacement for simplicity.
                 * Multiple instructions will require checking for ambiguous bcis inside the
                 * replaced range before applying a rewrite rule.
                 */
                if (patterns.length != 1 || replacementPatterns.length != 1) {
                    throw new IllegalArgumentException("REPLACE sections currently support only one instruction replacing another.");
                }
            } else {
                if (replacementPatterns != null) {
                    throw new IllegalArgumentException("Unexpected replacement patterns for " + kind + " section.");
                }
            }
        }
    }

    public record ImmediateReference(int instructionIndex, int immediateIndex) implements Comparable<ImmediateReference> {

        public int compareTo(ImmediateReference other) {
            int cmp = Integer.compare(instructionIndex, other.instructionIndex);
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(immediateIndex, other.immediateIndex);
        }

    }

    /**
     * Models a resolved immediate in an instruction pattern.
     */
    public sealed interface ResolvedImmediate permits ResolvedWildcard, ResolvedBinding, ResolvedLiteral {
        InstructionImmediate immediate();

        default int offset() {
            return immediate().offset();
        }
    }

    public record ResolvedWildcard(InstructionImmediate immediate) implements ResolvedImmediate {
        public ResolvedWildcard {
            Objects.requireNonNull(immediate);
        }

        @Override
        public String toString() {
            return "_";
        }
    }

    public record ResolvedBinding(InstructionImmediate immediate, String name, ImmediateReference constraint) implements ResolvedImmediate {
        public ResolvedBinding {
            Objects.requireNonNull(immediate);
            Objects.requireNonNull(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public record ResolvedLiteral(InstructionImmediate immediate, long value) implements ResolvedImmediate {
        public ResolvedLiteral {
            Objects.requireNonNull(immediate);
        }

        @Override
        public String toString() {
            return Long.toString(value);
        }
    }

    /**
     * Models a resolved {@link InstructionPatternModel}, with a computed offset and resolved
     * immediates.
     */
    public record ResolvedInstructionPatternModel(int offset, InstructionModel instruction, ResolvedImmediate[] immediates) {
        @Override
        public final String toString() {
            return "%s(%s)".formatted(instruction.getName(), Stream.of(immediates).map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    public enum RewriteKind {
        /**
         * Splits the matched instruction sequence into kept and removed sections. Because removal
         * shifts later BCIs, BCIs written to side tables must be remapped to preserve validity.
         */
        SECTIONED,
        /**
         * Keeps the instruction layout unchanged and rewrites the first opcode. No BCI fix-up is
         * needed, but applicability must still be validated, e.g. against exception handler ranges.
         */
        SUPERINSTRUCTION
    }

    /**
     * Section-based constructor for sectioned rewrite rules.
     *
     * The sections represent contiguous parts of the matched instruction sequence. IDENTITY
     * sections are kept as-is, DELETE sections are removed.
     */
    public InstructionRewriteRuleModel(RewriteSection... sections) {
        validateSections(sections);
        this.sections = sections;
        InstructionPatternModel[] lhsPatterns = flattenLhsPatterns(this.sections);
        InstructionPatternModel[] rhsPatterns = deriveRhsPatterns(this.sections);
        this.rewriteKind = RewriteKind.SECTIONED;
        this.lhs = new ResolvedInstructionPatternModel[lhsPatterns.length];
        this.rhs = new ResolvedInstructionPatternModel[rhsPatterns.length];
        this.bindings = new HashMap<>();
        initialize(lhsPatterns, rhsPatterns);
    }

    /**
     * Test-only constructor used by {@code @GenerateInstructionRewriter} tests.
     */
    public InstructionRewriteRuleModel(InstructionPatternModel[] lhsPattern, InstructionPatternModel[] rhsPattern) {
        this.sections = rhsPattern.length == 0
                        ? new RewriteSection[]{new RewriteSection(RewriteSectionKind.DELETE, lhsPattern.clone(), null)}
                        : new RewriteSection[0];
        // The test-only @GenerateInstructionRewriter codegen uses lhs/rhs directly.
        this.rewriteKind = null;
        this.lhs = new ResolvedInstructionPatternModel[lhsPattern.length];
        this.rhs = new ResolvedInstructionPatternModel[rhsPattern.length];
        this.bindings = new HashMap<>();
        initialize(lhsPattern, rhsPattern);
    }

    private void initialize(InstructionPatternModel[] lhsPattern, InstructionPatternModel[] rhsPattern) {
        /*
         * First, resolve the LHS. The first occurrence of an immediate binding declares it and
         * subsequent occurrences become immediate constraints.
         */
        int offset = 0;
        for (int i = 0; i < lhsPattern.length; i++) {
            InstructionModel instruction = lhsPattern[i].instruction();
            List<InstructionImmediate> encodedImmediates = instruction.getEncodedImmediates();
            ResolvedImmediate[] immediates = new ResolvedImmediate[encodedImmediates.size()];
            for (int j = 0; j < immediates.length; j++) {
                immediates[j] = resolveLhsImmediate(lhsPattern[i].immediates()[j], encodedImmediates.get(j), new ImmediateReference(i, j));
            }
            this.lhs[i] = new ResolvedInstructionPatternModel(offset, instruction, immediates);
            offset += instruction.getInstructionLength();
        }

        /*
         * Then, resolve the RHS. All instructions on the RHS should have immediates specified
         * either as literals or as bindings declared on the LHS.
         */
        offset = 0;
        for (int i = 0; i < rhsPattern.length; i++) {
            InstructionModel instruction = rhsPattern[i].instruction();
            List<InstructionImmediate> encodedImmediates = instruction.getEncodedImmediates();
            ResolvedImmediate[] immediates = new ResolvedImmediate[encodedImmediates.size()];
            for (int j = 0; j < immediates.length; j++) {
                immediates[j] = resolveRhsImmediate(rhsPattern[i].immediates()[j], encodedImmediates.get(j), lhsPattern, rhsPattern, i);
            }
            this.rhs[i] = new ResolvedInstructionPatternModel(offset, instruction, immediates);
            offset += instruction.getInstructionLength();
        }

        /*
         * Validate the well-formedness of the rewrite rule.
         */
        validateRewriteRuleSide(lhs, lhsPattern, rhsPattern, "lhs");
        validateRewriteRuleSide(rhs, lhsPattern, rhsPattern, "rhs");

        this.endsWithReturn = endsWithReturn(lhs);
        if (this.endsWithReturn != endsWithReturn(rhs)) {
            throw new IllegalArgumentException("Rewrite rule %s cannot end with a return on only one side.".formatted(formatRewriteRule(lhsPattern, rhsPattern, -1)));
        }

        if (!this.endsWithReturn) {
            // Net stack effect should be the same.
            int lhsStackEffect = stackEffect(lhs);
            int rhsStackEffect = stackEffect(rhs);
            if (lhsStackEffect != rhsStackEffect) {
                throw new IllegalArgumentException(
                                "The instructions on the lhs and rhs of rewrite rule %s have different stack effects (%d vs. %d).".formatted(formatRewriteRule(lhsPattern, rhsPattern, -1),
                                                lhsStackEffect, rhsStackEffect));
            }

            // If the last instruction produces a value, doEmitInstruction needs to return the new childBci.
            if (endsWithValue(lhs)) {
                if (endsWithValue(rhs)) {
                    // applyRewriteRule can return the bci of rhs[rhs.length-1] instead of -1 for childBci.
                    this.returnFinalInstructionBci = true;
                } else {
                    // When rhs[rhs.length-1] doesn't produce a result, we need a stronger data flow analysis to figure out the resulting childBci.
                    // Fail loudly here if we ever try to declare such a rule instead of silently failing to BE the result.
                    throw new IllegalArgumentException("Rewrite rule %s cannot end with a value-producing instruction on only the lhs.".formatted(formatRewriteRule(lhsPattern, rhsPattern, -1)));
                }
            }
        }
    }

    public RewriteKind getRewriteKind() {
        return rewriteKind;
    }

    public RewriteSection[] getSections() {
        return sections;
    }

    public boolean hasImmediateConstraints() {
        for (var resolvedPattern : lhs) {
            for (var resolvedImmediate : resolvedPattern.immediates()) {
                if (resolvedImmediate instanceof ResolvedBinding binding && binding.constraint() != null) {
                    return true;
                } else if (resolvedImmediate instanceof ResolvedLiteral) {
                    return true;
                }
            }
        }
        return false;
    }

    public int stackEffect() {
        return lhsStackEffect();
    }

    public int lhsStackEffect() {
        return stackEffect(lhs);
    }

    public int rhsStackEffect() {
        return stackEffect(rhs);
    }

    public boolean endsWithReturn() {
        return endsWithReturn;
    }

    public boolean returnFinalInstructionBci() {
        return returnFinalInstructionBci;
    }

    private static int stackEffect(ResolvedInstructionPatternModel[] instructions) {
        int result = 0;
        for (var pattern : instructions) {
            result += pattern.instruction().getStackEffect();
        }
        return result;
    }

    private static void validateRewriteRuleSide(ResolvedInstructionPatternModel[] pattern, InstructionPatternModel[] lhsPattern, InstructionPatternModel[] rhsPattern, String side) {
        for (int i = 0; i < pattern.length; i++) {
            InstructionModel instruction = pattern[i].instruction();
            validateRewritableInstruction(lhsPattern, rhsPattern, instruction);
            if (instruction.kind == InstructionModel.InstructionKind.RETURN) {
                if (i != pattern.length - 1) {
                    throw new IllegalArgumentException("Return can only be the final instruction on the %s of rewrite rule %s.".formatted(side, formatRewriteRule(lhsPattern, rhsPattern, -1)));
                }
            }
        }
    }

    private static boolean endsWithReturn(ResolvedInstructionPatternModel[] pattern) {
        return pattern.length > 0 && pattern[pattern.length - 1].instruction().kind == InstructionModel.InstructionKind.RETURN;
    }

    private static boolean endsWithValue(ResolvedInstructionPatternModel[] pattern) {
        return pattern.length > 0 && !pattern[pattern.length - 1].instruction().signature.isVoid();
    }

    private static void validateRewritableInstruction(InstructionPatternModel[] lhsPattern, InstructionPatternModel[] rhsPattern, InstructionModel instruction) {
        switch (instruction.kind) {
            case BRANCH, BRANCH_FALSE, CUSTOM_SHORT_CIRCUIT:
                /*
                 * We remember the BCIs of the branch target immediates so they can be patched later.
                 * Remapping does not (currently) fix up these BCIs, so we disallow these instructions.
                 * We can extend the remapping logic later if we need branches in rewrite rules.
                 */
                throw new IllegalArgumentException("Rewrite rule %s cannot contain instruction %s because it has a patchable BCI immediate.".formatted(
                                formatRewriteRule(lhsPattern, rhsPattern, -1), instruction.getName()));
            default:
                break;
        }
    }

    private static void validateSections(RewriteSection[] sections) {
        if (sections == null || sections.length == 0) {
            throw new IllegalArgumentException("Expected at least one rewrite section.");
        }

        // A sectioned rule must change the matched instruction sequence. An all-IDENTITY rule
        // would re-emit the same instruction sequence and could immediately match again forever.
        boolean seenNonIdentity = false;
        for (RewriteSection section : sections) {
            if (section.kind() != RewriteSectionKind.IDENTITY) {
                seenNonIdentity = true;
                break;
            }
        }
        if (!seenNonIdentity) {
            throw new IllegalArgumentException("Expected at least one non-IDENTITY rewrite section.");
        }
    }

    private static InstructionPatternModel[] flattenLhsPatterns(RewriteSection[] sections) {
        List<InstructionPatternModel> lhs = new ArrayList<>();
        for (RewriteSection section : sections) {
            lhs.addAll(List.of(section.patterns()));
        }
        return lhs.toArray(InstructionPatternModel[]::new);
    }

    private static InstructionPatternModel[] deriveRhsPatterns(RewriteSection[] sections) {
        List<InstructionPatternModel> rhs = new ArrayList<>();
        for (RewriteSection section : sections) {
            switch (section.kind()) {
                case IDENTITY -> rhs.addAll(List.of(section.patterns()));
                case REPLACE -> rhs.addAll(List.of(section.replacementPatterns()));
            }
        }
        return rhs.toArray(InstructionPatternModel[]::new);
    }

    private ResolvedImmediate resolveLhsImmediate(ImmediatePattern immediatePattern, InstructionImmediate instructionImmediate, ImmediateReference immediateReference) {
        if (immediatePattern instanceof Wildcard) {
            return new ResolvedWildcard(instructionImmediate);
        } else if (immediatePattern instanceof Binding binding) {
            ImmediateReference constraint = this.bindings.putIfAbsent(binding.name(), immediateReference);
            return new ResolvedBinding(instructionImmediate, binding.name(), constraint);
        } else if (immediatePattern instanceof Literal literal) {
            return resolveLiteralImmediate(instructionImmediate, literal);
        }
        throw new AssertionError("Unexpected immediate pattern: " + immediatePattern);
    }

    private ResolvedImmediate resolveRhsImmediate(ImmediatePattern immediatePattern, InstructionImmediate instructionImmediate, InstructionPatternModel[] lhsPattern,
                    InstructionPatternModel[] rhsPattern, int rhsInstructionIndex) {
        if (immediatePattern instanceof Wildcard) {
            throw new IllegalArgumentException(
                            "Instruction %s in the rhs of rewrite rule %s is missing an immediate binding. All immediates for instructions on the rhs must be specified.".formatted(
                                            rhsPattern[rhsInstructionIndex].instruction().getName(),
                                            formatRewriteRule(lhsPattern, rhsPattern, -1)));
        } else if (immediatePattern instanceof Binding binding) {
            ImmediateReference constraint = this.bindings.get(binding.name());
            if (constraint == null) {
                throw new IllegalArgumentException("Found unbound immediate %s in the rhs of rewrite rule %s. No corresponding immediate was bound on the lhs.".formatted(binding.name(),
                                formatRewriteRule(lhsPattern, rhsPattern, -1)));
            }
            return new ResolvedBinding(instructionImmediate, binding.name(), constraint);
        } else if (immediatePattern instanceof Literal literal) {
            return resolveLiteralImmediate(instructionImmediate, literal);
        }
        throw new AssertionError("Unexpected immediate pattern: " + immediatePattern);
    }

    private static ResolvedLiteral resolveLiteralImmediate(InstructionImmediate instructionImmediate, Literal literal) {
        validateLiteralValue(instructionImmediate, literal);
        return new ResolvedLiteral(instructionImmediate, literal.value());
    }

    private static void validateLiteralValue(InstructionImmediate instructionImmediate, Literal literal) {
        InstructionModel.ImmediateKind kind = instructionImmediate.kind();
        if (kind.width == InstructionModel.ImmediateWidth.NONE) {
            throw new AssertionError("Literal immediates must be encoded.");
        }
        int bits = kind.width.byteSize * Byte.SIZE;
        long value = literal.value();
        long min;
        long max;
        if (kind.width == InstructionModel.ImmediateWidth.LONG) {
            min = kind.isUnsigned() ? 0 : Long.MIN_VALUE;
            max = Long.MAX_VALUE;
        } else if (kind.isUnsigned()) {
            min = 0;
            max = (1L << bits) - 1;
        } else {
            long limit = 1L << (bits - 1);
            min = -limit;
            max = limit - 1;
        }
        if (value < min || max < value) {
            throw new IllegalArgumentException("Immediate literal %d does not fit immediate %s with kind %s.".formatted(value, instructionImmediate.name(), kind));
        }
    }

    public ResolvedImmediate resolveImmediateReference(ImmediateReference immediateReference) {
        return lhs[immediateReference.instructionIndex].immediates()[immediateReference.immediateIndex];
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof InstructionRewriteRuleModel other && rewriteKind == other.rewriteKind && Arrays.equals(lhs, other.lhs) && Arrays.equals(rhs, other.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rewriteKind, Arrays.hashCode(lhs), Arrays.hashCode(rhs));
    }

    @Override
    public final String toString() {
        return toString(-1);
    }

    /**
     * Prints the rule with an optional marker character at the provided index of the lhs.
     */
    final String toString(int lhsMarkerIndex) {
        return formatRewriteRule(lhs, rhs, lhsMarkerIndex);
    }

    private static String formatRewriteRule(Object[] lhs, Object[] rhs, int lhsMarkerIndex) {
        return "%s -> %s".formatted(formatPatterns(lhs, lhsMarkerIndex), formatPatterns(rhs, -1));
    }

    private static String formatPatterns(Object[] patterns, int markerIndex) {
        String result = "";
        for (int i = 0; i < patterns.length; i++) {
            if (i != 0) {
                result += " ";
            }
            if (i == markerIndex) {
                result += "* "; // optional progress marker
            }
            result += patterns[i];
        }
        if (markerIndex == patterns.length) {
            result += " *";
        }
        return result;
    }

    public void setParent(InstructionRewriterModel parent) {
        this.parent = parent;
    }

    public int compareTo(InstructionRewriteRuleModel other) {
        // Shorter rules first.
        int cmp = Integer.compare(lhs.length, other.lhs.length);
        if (cmp != 0) {
            return cmp;
        }

        // Sort equal-length rules by instruction order.
        for (int i = 0; i < lhs.length; i++) {
            cmp = parent.instructionComparator().compare(lhs[i].instruction, other.lhs[i].instruction());
            if (cmp != 0) {
                return cmp;
            }
        }

        if (!this.equals(other)) {
            throw new IllegalArgumentException("Two different rules with the same opcode sequence found. Rewrite rules should be validated to prevent this.");
        }
        return 0;

    }
}
