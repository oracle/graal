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

import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

public class ASTTransitionSetBuilder extends TransitionBuilder<ASTTransitionSet> {

    private final ASTTransitionSet transitionSet;
    private MatcherBuilder matcherBuilder;

    public ASTTransitionSetBuilder(ASTTransitionSet transitionSet, MatcherBuilder matcherBuilder) {
        this.transitionSet = transitionSet;
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
    public ASTTransitionSet getTargetState() {
        return transitionSet;
    }

    @Override
    public ASTTransitionSetBuilder createMerged(TransitionBuilder<ASTTransitionSet> other, MatcherBuilder mergedMatcher) {
        return new ASTTransitionSetBuilder(transitionSet.createMerged(other.getTargetState()), mergedMatcher);
    }

    @Override
    public void mergeInPlace(TransitionBuilder<ASTTransitionSet> other, MatcherBuilder mergedMatcher) {
        transitionSet.mergeInPlace(other);
        matcherBuilder = mergedMatcher;
    }

    public DebugUtil.Table toTable() {
        return new DebugUtil.Table("ASTTransitionSetBuilder",
                        new DebugUtil.Value("matcherBuilder", matcherBuilder),
                        transitionSet.toTable());
    }
}
