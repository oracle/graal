/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.AtomicGroup;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

/**
 * Represents a state of a {@link PureNFA}. All {@link PureNFAState}s correspond to a single
 * {@link RegexASTNode}, referenced by {@link #getAstNodeId()}. Initial and final states correspond
 * to the NFA helper nodes contained in {@link RegexASTSubtreeRootNode}. All other states correspond
 * to either {@link CharacterClass}es, {@link BackReference}s, {@link LookAroundAssertion}s or
 * {@link AtomicGroup}s.
 */
public final class PureNFAState extends BasicState<PureNFAState, PureNFATransition> {

    private static final PureNFATransition[] EMPTY_TRANSITIONS = {};

    public static final short KIND_INITIAL_OR_FINAL_STATE = 0;
    public static final short KIND_CHARACTER_CLASS = 1;
    public static final short KIND_SUB_MATCHER = 2;
    public static final short KIND_BACK_REFERENCE = 3;
    public static final short KIND_EMPTY_MATCH = 4;

    private static final short FLAG_IS_LOOK_AROUND = 1 << N_FLAGS;
    private static final short FLAG_IS_SUB_MATCHER_NEGATED = 1 << N_FLAGS + 1;
    private static final short FLAG_IS_DETERMINISTIC = 1 << N_FLAGS + 2;
    private static final short FLAG_IS_IGNORE_CASE_REFERENCE = 1 << N_FLAGS + 3;
    private static final short FLAG_IS_RECURSIVE_REFERENCE = 1 << N_FLAGS + 4;
    private static final short FLAG_IS_IGNORE_CASE_REFERENCE_ALTERNATIVE_MODE = 1 << N_FLAGS + 5;

    private final int astNodeId;
    private final int subtreeId;
    private final int[] referencedGroupNumbers;
    private final byte kind;
    private final CodePointSet charSet;

    public PureNFAState(int id, Term t) {
        super(id, EMPTY_TRANSITIONS);
        this.astNodeId = t.getId();
        this.kind = getKind(t);
        this.subtreeId = isSubMatcher() ? t.asSubtreeRootNode().getSubTreeId() : -1;
        this.referencedGroupNumbers = isBackReference() ? t.asBackReference().getGroupNumbers() : null;
        this.charSet = isCharacterClass() ? t.asCharacterClass().getCharSet() : null;
        setLookAround(t.isLookAroundAssertion());
        if (t.isLookAroundAssertion()) {
            setSubMatcherNegated(t.asLookAroundAssertion().isNegated());
        }
        if (t.isBackReference()) {
            setIgnoreCaseReference(t.asBackReference().isIgnoreCaseReference());
            setIgnoreCaseReferenceAlternativeMode(t.asBackReference().isIgnoreCaseReferenceAltMode());
            setRecursiveReference(t.asBackReference().isNestedBackReference());
        }
    }

    private int getAstNodeId() {
        return astNodeId;
    }

    public Term getAstNode(RegexAST ast) {
        return (Term) ast.getState(astNodeId);
    }

    public byte getKind() {
        return kind;
    }

    /**
     * State represents a {@link CharacterClass}.
     */
    public boolean isCharacterClass() {
        return kind == KIND_CHARACTER_CLASS;
    }

    /**
     * State represents a {@link LookAroundAssertion} or an {@link AtomicGroup}.
     */
    public boolean isSubMatcher() {
        return kind == KIND_SUB_MATCHER;
    }

    public boolean isLookAhead(RegexAST ast) {
        return isSubMatcher() && getAstNode(ast).isLookAheadAssertion();
    }

    public boolean isLookBehind(RegexAST ast) {
        return isSubMatcher() && getAstNode(ast).isLookBehindAssertion();
    }

    public boolean isAtomicGroup() {
        return isSubMatcher() && !isLookAround();
    }

    /**
     * State represents a {@link BackReference}.
     */
    public boolean isBackReference() {
        return kind == KIND_BACK_REFERENCE;
    }

    /**
     * State represents an empty loop iteration in a quantified expression. This kind of state is
     * needed when a quantified expression where {@link Quantifier#getMin()} > 0 can match the empty
     * string - e.g. <code>(a|){10,20}</code>. In such expressions, the quantifier must match the
     * empty string until the minimum number of iterations is reached, but after that it is no
     * longer allowed to match the empty string. To model this behavior, we insert an "empty match"
     * state whenever a quantified expression that can match the empty string is encountered.
     */
    public boolean isEmptyMatch() {
        return kind == KIND_EMPTY_MATCH;
    }

    public CodePointSet getCharSet() {
        assert isCharacterClass();
        return charSet;
    }

    public int getSubtreeId() {
        assert isSubMatcher();
        return subtreeId;
    }

    public int[] getBackRefNumbers() {
        assert isBackReference();
        return referencedGroupNumbers;
    }

    public boolean isLookAround() {
        return getFlag(FLAG_IS_LOOK_AROUND);
    }

    public void setLookAround(boolean value) {
        setFlag(FLAG_IS_LOOK_AROUND, value);
    }

    public boolean isSubMatcherNegated() {
        return getFlag(FLAG_IS_SUB_MATCHER_NEGATED);
    }

    public void setSubMatcherNegated(boolean value) {
        setFlag(FLAG_IS_SUB_MATCHER_NEGATED, value);
    }

    public boolean isIgnoreCaseReference() {
        return getFlag(FLAG_IS_IGNORE_CASE_REFERENCE);
    }

    public void setIgnoreCaseReference(boolean value) {
        setFlag(FLAG_IS_IGNORE_CASE_REFERENCE, value);
    }

    public boolean isRecursiveReference() {
        return getFlag(FLAG_IS_RECURSIVE_REFERENCE);
    }

    public void setRecursiveReference(boolean value) {
        setFlag(FLAG_IS_RECURSIVE_REFERENCE, value);
    }

    public boolean isIgnoreCaseReferenceAlternativeMode() {
        return getFlag(FLAG_IS_IGNORE_CASE_REFERENCE_ALTERNATIVE_MODE);
    }

    public void setIgnoreCaseReferenceAlternativeMode(boolean value) {
        setFlag(FLAG_IS_IGNORE_CASE_REFERENCE_ALTERNATIVE_MODE, value);
    }

    /**
     * A state is considered "deterministic" iff it either has only one successor, or all of its
     * successors represent {@link #isCharacterClass() character classes}, and none of those
     * character classes intersect.
     *
     * @see #initIsDeterministic(CompilationBuffer)
     */
    public boolean isDeterministic() {
        return getFlag(FLAG_IS_DETERMINISTIC);
    }

    public void setDeterministic(boolean value) {
        setFlag(FLAG_IS_DETERMINISTIC, value);
    }

    /**
     * Initializes this state's {@link #isDeterministic()}-property.
     */
    public void initIsDeterministic(CompilationBuffer compilationBuffer) {
        setDeterministic(calcIsDeterministic(compilationBuffer));
    }

    private boolean calcIsDeterministic(CompilationBuffer compilationBuffer) {
        PureNFATransition[] successors = getSuccessors();
        if (successors.length <= 1) {
            return true;
        }
        if (!successors[0].getTarget().isCharacterClass()) {
            return false;
        }
        CodePointSetAccumulator acc = compilationBuffer.getCodePointSetAccumulator1();
        if (successors.length > 8) {
            acc.addSet(successors[0].getTarget().getCharSet());
        }
        for (int i = 1; i < successors.length; i++) {
            PureNFAState target = successors[i].getTarget();
            if (!target.isCharacterClass()) {
                return false;
            }
            if (successors.length <= 8) {
                // avoid calculating union sets on low number of successors
                for (int j = 0; j < i; j++) {
                    if (successors[j].getTarget().getCharSet().intersects(target.getCharSet())) {
                        return false;
                    }
                }
            } else {
                if (target.getCharSet().intersects(acc.get())) {
                    return false;
                }
                acc.addSet(target.getCharSet());
            }
        }
        return true;
    }

    @Override
    protected PureNFATransition[] createTransitionsArray(int length) {
        return new PureNFATransition[length];
    }

    public void addLoopBackNext(PureNFATransition transition) {
        PureNFATransition[] newSuccessors = Arrays.copyOf(getSuccessors(), getSuccessors().length + 1);
        newSuccessors[newSuccessors.length - 1] = transition;
        setSuccessors(newSuccessors);
    }

    public void removeLoopBackNext() {
        setSuccessors(Arrays.copyOf(getSuccessors(), getSuccessors().length - 1));
    }

    @Override
    protected boolean hasTransitionToUnAnchoredFinalState(boolean forward) {
        for (PureNFATransition t : (getSuccessors(forward))) {
            if (t.getTarget(forward).isUnAnchoredFinalState(forward)) {
                return true;
            }
        }
        return false;
    }

    private static byte getKind(Term t) {
        if (t.isCharacterClass()) {
            return KIND_CHARACTER_CLASS;
        }
        if (t.isMatchFound() || t.isPositionAssertion()) {
            return KIND_INITIAL_OR_FINAL_STATE;
        }
        if (t.isSubtreeRoot()) {
            return KIND_SUB_MATCHER;
        }
        if (t.isBackReference()) {
            return KIND_BACK_REFERENCE;
        }
        if (t.isGroup()) {
            if (t.getParent().isSubtreeRoot()) {
                // dummy initial state
                return KIND_INITIAL_OR_FINAL_STATE;
            } else {
                return KIND_EMPTY_MATCH;
            }
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    public boolean canMatchZeroWidth() {
        return isSubMatcher() || isBackReference() || isEmptyMatch();
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return getId() + " " + toStringIntl();
    }

    @TruffleBoundary
    private String toStringIntl() {
        switch (getKind()) {
            case KIND_INITIAL_OR_FINAL_STATE:
                if (isUnAnchoredInitialState()) {
                    return "I";
                } else if (isAnchoredInitialState()) {
                    return "^I";
                } else if (isUnAnchoredFinalState()) {
                    return "F";
                } else if (isAnchoredFinalState()) {
                    return "F$";
                } else {
                    return "Dummy Initial State";
                }
            case KIND_CHARACTER_CLASS:
                return charSet.toString();
            case KIND_SUB_MATCHER:
                return "?=" + getSubtreeId();
            case KIND_BACK_REFERENCE:
                if (referencedGroupNumbers.length == 1) {
                    return "\\" + referencedGroupNumbers[0];
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\\k<");
                    sb.append(referencedGroupNumbers[0]);
                    for (int i = 1; i < referencedGroupNumbers.length; i++) {
                        sb.append(",");
                        sb.append(referencedGroupNumbers[i]);
                    }
                    sb.append(">");
                    return sb.toString();
                }
            case KIND_EMPTY_MATCH:
                return "EMPTY";
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    public JsonObject toJson(RegexAST ast) {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("stateSet", Json.array(new int[]{getAstNodeId()})),
                        Json.prop("sourceSections", RegexAST.sourceSectionsToJson(ast.getSourceSections(getAstNode(ast)))),
                        Json.prop("matcherBuilder", isCharacterClass() ? Json.val(charSet.toString()) : Json.nullValue()),
                        Json.prop("subMatcher", isSubMatcher() ? Json.val(getSubtreeId()) : Json.nullValue()),
                        Json.prop("backReference", isBackReference() ? Json.array(Arrays.stream(getBackRefNumbers()).mapToObj(x -> Json.val(x))) : Json.nullValue()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("unAnchoredFinalState", isUnAnchoredFinalState()),
                        Json.prop("transitions", Arrays.stream(getSuccessors()).map(x -> Json.val(x.getId()))));
    }
}
