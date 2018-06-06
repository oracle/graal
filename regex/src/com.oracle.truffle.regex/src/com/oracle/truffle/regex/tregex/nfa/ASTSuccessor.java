/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
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
        MatcherBuilder matcherBuilder = MatcherBuilder.createFull();
        if (transition.getTarget() instanceof CharacterClass) {
            matcherBuilder = ((CharacterClass) transition.getTarget()).getMatcherBuilder();
        }
        mergedStates.add(new TransitionBuilder<>(new ASTTransitionSet(transition), matcherBuilder));
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
                MatcherBuilder intersection = state.getMatcherBuilder().createIntersectionMatcher(lookAroundState.getMatcherBuilder(), compilationBuffer);
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
