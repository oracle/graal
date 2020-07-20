/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.tregex.automaton.BasicState;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

/**
 * Represents a state of a {@link PureNFA}. All {@link PureNFAState}s correspond to a single
 * {@link RegexASTNode}, referenced by {@link #getAstNodeId()}. Initial and final states correspond
 * to the NFA helper nodes contained in {@link RegexASTSubtreeRootNode}. All other states correspond
 * to either {@link CharacterClass}es or {@link BackReference}s.
 */
public final class PureNFAState extends BasicState<PureNFAState, PureNFATransition> {

    private static final PureNFATransition[] EMPTY_TRANSITIONS = {};

    public static final byte KIND_INITIAL_OR_FINAL_STATE = 0;
    public static final byte KIND_CHARACTER_CLASS = 1;
    public static final byte KIND_LOOK_AROUND = 2;
    public static final byte KIND_BACK_REFERENCE = 3;
    public static final byte KIND_EMPTY_MATCH = 4;

    private static final byte FLAG_IS_LOOK_AROUND_NEGATED = 1 << N_FLAGS;
    private static final byte FLAG_IS_DETERMINISTIC = 1 << N_FLAGS + 1;

    private final int astNodeId;
    private final int extraId;
    private final byte kind;
    private final CodePointSet charSet;

    public PureNFAState(int id, Term t) {
        super(id, EMPTY_TRANSITIONS);
        this.astNodeId = t.getId();
        this.kind = getKind(t);
        this.extraId = isLookAround() ? t.asLookAroundAssertion().getSubTreeId() : isBackReference() ? t.asBackReference().getGroupNr() : -1;
        this.charSet = isCharacterClass() ? t.asCharacterClass().getCharSet() : null;
        setLookAroundNegated(isLookAround() && t.asLookAroundAssertion().isNegated());
    }

    public int getAstNodeId() {
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
     * State represents a {@link LookAroundAssertion}.
     */
    public boolean isLookAround() {
        return kind == KIND_LOOK_AROUND;
    }

    public boolean isLookAhead(RegexAST ast) {
        return isLookAround() && ast.getLookArounds().get(getLookAroundId()).isLookAheadAssertion();
    }

    public boolean isLookBehind(RegexAST ast) {
        return isLookAround() && ast.getLookArounds().get(getLookAroundId()).isLookBehindAssertion();
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

    public int getLookAroundId() {
        assert isLookAround();
        return extraId;
    }

    public int getBackRefNumber() {
        assert isBackReference();
        return extraId;
    }

    public boolean isLookAroundNegated() {
        return getFlag(FLAG_IS_LOOK_AROUND_NEGATED);
    }

    public void setLookAroundNegated(boolean value) {
        setFlag(FLAG_IS_LOOK_AROUND_NEGATED, value);
    }

    /**
     * A state is considered "deterministic" iff it either has only one successor, or all of its
     * successors/predecessors (depending on {@code forward}) represent {@link #isCharacterClass()
     * character classes}, and none of those character classes intersect.
     *
     * @see #initIsDeterministic(boolean, CompilationBuffer)
     */
    public boolean isDeterministic() {
        return getFlag(FLAG_IS_DETERMINISTIC);
    }

    public void setDeterministic(boolean value) {
        setFlag(FLAG_IS_DETERMINISTIC, value);
    }

    /**
     * Initializes this state's {@link #isDeterministic()}-property, depending on {@code forward}.
     */
    public void initIsDeterministic(boolean forward, CompilationBuffer compilationBuffer) {
        setDeterministic(calcIsDeterministic(forward, compilationBuffer));
    }

    private boolean calcIsDeterministic(boolean forward, CompilationBuffer compilationBuffer) {
        PureNFATransition[] successors = getSuccessors(forward);
        if (successors.length <= 1) {
            return true;
        }
        if (!successors[0].getTarget(forward).isCharacterClass()) {
            return false;
        }
        CodePointSetAccumulator acc = compilationBuffer.getCodePointSetAccumulator1();
        if (successors.length > 8) {
            acc.addSet(successors[0].getTarget(forward).getCharSet());
        }
        for (int i = 1; i < successors.length; i++) {
            PureNFAState target = successors[i].getTarget(forward);
            if (!target.isCharacterClass()) {
                return false;
            }
            if (successors.length <= 8) {
                // avoid calculating union sets on low number of successors
                for (int j = 0; j < i; j++) {
                    if (successors[j].getTarget(forward).getCharSet().intersects(target.getCharSet())) {
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
        if (t.isLookAroundAssertion()) {
            return KIND_LOOK_AROUND;
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
        throw Exceptions.shouldNotReachHere();
    }

    public boolean canMatchZeroWidth() {
        return isLookAround() || isBackReference() || isEmptyMatch();
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
            case KIND_LOOK_AROUND:
                return "?=" + getLookAroundId();
            case KIND_BACK_REFERENCE:
                return "\\" + getBackRefNumber();
            case KIND_EMPTY_MATCH:
                return "EMPTY";
            default:
                throw Exceptions.shouldNotReachHere();
        }
    }

    @TruffleBoundary
    public JsonObject toJson(RegexAST ast) {
        return Json.obj(Json.prop("id", getId()),
                        Json.prop("stateSet", Json.array(new int[]{getAstNodeId()})),
                        Json.prop("sourceSections", RegexAST.sourceSectionsToJson(ast.getSourceSections(getAstNode(ast)))),
                        Json.prop("matcherBuilder", isCharacterClass() ? Json.val(charSet.toString()) : Json.nullValue()),
                        Json.prop("lookAround", isLookAround() ? Json.val(getLookAroundId()) : Json.nullValue()),
                        Json.prop("backReference", isBackReference() ? Json.val(getBackRefNumber()) : Json.nullValue()),
                        Json.prop("anchoredFinalState", isAnchoredFinalState()),
                        Json.prop("unAnchoredFinalState", isUnAnchoredFinalState()),
                        Json.prop("transitions", Arrays.stream(getSuccessors()).map(x -> Json.val(x.getId()))));
    }
}
