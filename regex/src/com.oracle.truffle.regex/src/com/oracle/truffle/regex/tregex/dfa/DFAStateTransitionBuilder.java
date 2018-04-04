/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.List;

public class DFAStateTransitionBuilder extends TransitionBuilder<NFATransitionSet> {

    private final NFATransitionSet transitions;
    private MatcherBuilder matcherBuilder;

    DFAStateTransitionBuilder(MatcherBuilder matcherBuilder, List<NFAStateTransition> transitions, NFA nfa, boolean forward, boolean prioritySensitive) {
        this.transitions = NFATransitionSet.create(nfa, forward, prioritySensitive, transitions);
        this.matcherBuilder = matcherBuilder;
    }

    DFAStateTransitionBuilder(MatcherBuilder matcherBuilder, NFATransitionSet transitions) {
        this.transitions = transitions;
        this.matcherBuilder = matcherBuilder;
    }

    @Override
    public MatcherBuilder getMatcherBuilder() {
        return matcherBuilder;
    }

    @Override
    public void setMatcherBuilder(MatcherBuilder matcherBuilder) {
        this.matcherBuilder = matcherBuilder;
    }

    @Override
    public DFAStateTransitionBuilder createMerged(TransitionBuilder<NFATransitionSet> other, MatcherBuilder mergedMatcher) {
        return new DFAStateTransitionBuilder(mergedMatcher, transitions.createMerged(other.getTargetState()));
    }

    @Override
    public void mergeInPlace(TransitionBuilder<NFATransitionSet> other, MatcherBuilder mergedMatcher) {
        transitions.addAll(other.getTargetState());
        matcherBuilder = mergedMatcher;
    }

    @Override
    public NFATransitionSet getTargetState() {
        return transitions;
    }

    @Override
    public String toString() {
        return toTable("DFAStateConnectionBuilder").toString();
    }

    @CompilerDirectives.TruffleBoundary
    public DebugUtil.Table toTable(String name) {
        return new DebugUtil.Table(name,
                        new DebugUtil.Value("matcherBuilder", getMatcherBuilder()),
                        new DebugUtil.Value("transitions", getTargetState()));
    }
}
