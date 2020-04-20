/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

final class ASTSuccessor implements JsonConvertible {

    private ASTTransition initialTransition;
    private ArrayList<TransitionBuilder<RegexAST, Term, ASTTransition>> mergedStates = new ArrayList<>();
    ObjectArrayBuffer<ASTTransition> mergedTransitions;
    private boolean lookAroundsMerged = false;
    private List<ASTStep> lookAheads = Collections.emptyList();
    private List<ASTStep> lookBehinds = Collections.emptyList();

    ASTSuccessor() {
    }

    ASTSuccessor(ASTTransition initialTransition) {
        this.initialTransition = initialTransition;
    }

    public ASTTransition getInitialTransition() {
        return initialTransition;
    }

    public CodePointSet getInitialTransitionCharSet(CompilationBuffer compilationBuffer) {
        return initialTransition.getTarget() instanceof CharacterClass ? ((CharacterClass) initialTransition.getTarget()).getCharSet() : compilationBuffer.getEncoding().getFullSet();
    }

    public void setInitialTransition(ASTTransition initialTransition) {
        this.initialTransition = initialTransition;
    }

    public void setLookAheads(ArrayList<ASTStep> lookAheads) {
        this.lookAheads = lookAheads;
    }

    public void setLookBehinds(ArrayList<ASTStep> lookBehinds) {
        this.lookBehinds = lookBehinds;
    }

    private boolean hasLookArounds() {
        return !lookBehinds.isEmpty() || !lookAheads.isEmpty();
    }

    public void addLookBehinds(Collection<ASTStep> addLookBehinds) {
        if (lookBehinds.isEmpty()) {
            lookBehinds = new ArrayList<>();
        }
        lookBehinds.addAll(addLookBehinds);
    }

    public ArrayList<TransitionBuilder<RegexAST, Term, ASTTransition>> getMergedStates(ASTTransitionCanonicalizer canonicalizer, CompilationBuffer compilationBuffer) {
        if (!lookAroundsMerged) {
            mergeLookArounds(canonicalizer, compilationBuffer);
            lookAroundsMerged = true;
        }
        return mergedStates;
    }

    private void mergeLookArounds(ASTTransitionCanonicalizer canonicalizer, CompilationBuffer compilationBuffer) {
        assert mergedStates.isEmpty();
        canonicalizer.addArgument(initialTransition, getInitialTransitionCharSet(compilationBuffer));
        for (ASTStep lookBehind : lookBehinds) {
            ASTSuccessor lb = lookBehind.getSuccessors().get(0);
            if (lookBehind.getSuccessors().size() > 1 || lb.hasLookArounds()) {
                throw new UnsupportedRegexException("nested look-behind assertions");
            }
            CodePointSet intersection = getInitialTransitionCharSet(compilationBuffer).createIntersection(lb.getInitialTransitionCharSet(compilationBuffer), compilationBuffer);
            if (intersection.matchesSomething()) {
                canonicalizer.addArgument(lb.getInitialTransition(), intersection);
            }
        }
        TransitionBuilder<RegexAST, Term, ASTTransition>[] mergedLookBehinds = canonicalizer.run(compilationBuffer);
        Collections.addAll(mergedStates, mergedLookBehinds);
        ArrayList<TransitionBuilder<RegexAST, Term, ASTTransition>> newMergedStates = new ArrayList<>();
        for (ASTStep lookAhead : lookAheads) {
            for (TransitionBuilder<RegexAST, Term, ASTTransition> state : mergedStates) {
                addAllIntersecting(canonicalizer, state, lookAhead, newMergedStates, compilationBuffer);
            }
            ArrayList<TransitionBuilder<RegexAST, Term, ASTTransition>> tmp = mergedStates;
            mergedStates = newMergedStates;
            newMergedStates = tmp;
            newMergedStates.clear();
        }
    }

    private void addAllIntersecting(ASTTransitionCanonicalizer canonicalizer, TransitionBuilder<RegexAST, Term, ASTTransition> state, ASTStep lookAround,
                    ArrayList<TransitionBuilder<RegexAST, Term, ASTTransition>> result, CompilationBuffer compilationBuffer) {
        for (ASTSuccessor successor : lookAround.getSuccessors()) {
            for (TransitionBuilder<RegexAST, Term, ASTTransition> lookAroundState : successor.getMergedStates(canonicalizer, compilationBuffer)) {
                CodePointSet intersection = state.getCodePointSet().createIntersection(lookAroundState.getCodePointSet(), compilationBuffer);
                if (intersection.matchesSomething()) {
                    if (mergedTransitions == null) {
                        mergedTransitions = new ObjectArrayBuffer<>();
                    }
                    mergedTransitions.clear();
                    StateSet<RegexAST, Term> mergedStateSet = state.getTransitionSet().getTargetStateSet().copy();
                    mergedTransitions.addAll(state.getTransitionSet().getTransitions());
                    for (int i = 0; i < lookAroundState.getTransitionSet().size(); i++) {
                        ASTTransition t = lookAroundState.getTransitionSet().getTransition(i);
                        if (mergedStateSet.add(t.getTarget())) {
                            mergedTransitions.add(t);
                        }
                    }
                    result.add(new TransitionBuilder<>(mergedTransitions.toArray(new ASTTransition[mergedTransitions.length()]), mergedStateSet, intersection));
                }
            }
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("lookAheads", lookAheads.stream().map(x -> Json.val(x.getRoot().getId())).collect(Collectors.toList())),
                        Json.prop("lookBehinds", lookBehinds.stream().map(x -> Json.val(x.getRoot().getId())).collect(Collectors.toList())),
                        Json.prop("mergedStates", mergedStates));
    }
}
