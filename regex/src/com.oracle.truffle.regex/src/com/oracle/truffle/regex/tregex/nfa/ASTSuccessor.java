/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

final class ASTSuccessor implements JsonConvertible {

    private ArrayList<TransitionBuilder<ASTTransitionSet>> mergedStates = new ArrayList<>();
    private boolean lookAroundsMerged = false;
    private List<ASTStep> lookAheads = Collections.emptyList();
    private List<ASTStep> lookBehinds = Collections.emptyList();

    private final CompilationBuffer compilationBuffer;

    ASTSuccessor(CompilationBuffer compilationBuffer) {
        this.compilationBuffer = compilationBuffer;
    }

    ASTSuccessor(CompilationBuffer compilationBuffer, ASTTransition initialTransition) {
        this.compilationBuffer = compilationBuffer;
        addInitialTransition(initialTransition);
    }

    public void addInitialTransition(ASTTransition transition) {
        CharSet charSet = CharSet.getFull();
        if (transition.getTarget() instanceof CharacterClass) {
            charSet = ((CharacterClass) transition.getTarget()).getCharSet();
        }
        mergedStates.add(new TransitionBuilder<>(new ASTTransitionSet(transition), charSet));
    }

    public void setLookAheads(ArrayList<ASTStep> lookAheads) {
        this.lookAheads = lookAheads;
    }

    public void setLookBehinds(ArrayList<ASTStep> lookBehinds) {
        this.lookBehinds = lookBehinds;
    }

    public void addLookBehinds(Collection<ASTStep> addLookBehinds) {
        if (lookBehinds.isEmpty()) {
            lookBehinds = new ArrayList<>();
        }
        lookBehinds.addAll(addLookBehinds);
    }

    public ArrayList<TransitionBuilder<ASTTransitionSet>> getMergedStates(ASTTransitionCanonicalizer canonicalizer) {
        if (!lookAroundsMerged) {
            mergeLookArounds(canonicalizer);
            lookAroundsMerged = true;
        }
        return mergedStates;
    }

    private void mergeLookArounds(ASTTransitionCanonicalizer canonicalizer) {
        assert mergedStates.size() == 1;
        TransitionBuilder<ASTTransitionSet> successor = mergedStates.get(0);
        for (ASTStep lookBehind : lookBehinds) {
            addAllIntersecting(canonicalizer, successor, lookBehind, mergedStates);
        }
        TransitionBuilder<ASTTransitionSet>[] mergedLookBehinds = canonicalizer.run(mergedStates, compilationBuffer);
        mergedStates.clear();
        Collections.addAll(mergedStates, mergedLookBehinds);
        ArrayList<TransitionBuilder<ASTTransitionSet>> newMergedStates = new ArrayList<>();
        for (ASTStep lookAhead : lookAheads) {
            for (TransitionBuilder<ASTTransitionSet> state : mergedStates) {
                addAllIntersecting(canonicalizer, state, lookAhead, newMergedStates);
            }
            ArrayList<TransitionBuilder<ASTTransitionSet>> tmp = mergedStates;
            mergedStates = newMergedStates;
            newMergedStates = tmp;
            newMergedStates.clear();
        }
    }

    private void addAllIntersecting(ASTTransitionCanonicalizer canonicalizer, TransitionBuilder<ASTTransitionSet> state, ASTStep lookAround, ArrayList<TransitionBuilder<ASTTransitionSet>> result) {
        for (ASTSuccessor successor : lookAround.getSuccessors()) {
            for (TransitionBuilder<ASTTransitionSet> lookAroundState : successor.getMergedStates(canonicalizer)) {
                CharSet intersection = state.getMatcherBuilder().createIntersection(lookAroundState.getMatcherBuilder(), compilationBuffer);
                if (intersection.matchesSomething()) {
                    result.add(state.createMerged(lookAroundState, intersection));
                }
            }
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        // ensure merged states are calculated
        getMergedStates(new ASTTransitionCanonicalizer());
        return Json.obj(Json.prop("lookAheads", lookAheads.stream().map(x -> Json.val(x.getRoot().getId())).collect(Collectors.toList())),
                        Json.prop("lookBehinds", lookBehinds.stream().map(x -> Json.val(x.getRoot().getId())).collect(Collectors.toList())),
                        Json.prop("mergedStates", mergedStates));
    }
}
