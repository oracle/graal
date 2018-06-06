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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * This class represents a power-set automaton state transition fragment to be used by
 * {@link StateTransitionCanonicalizer}.<br>
 * A transition in a power-set automaton consists of a set of transitions of the NFA that the
 * power-set automaton is being built from.
 *
 * @param <TS> a type that should represent the set of NFA transitions currently contained in this
 *            fragment.
 */
public class TransitionBuilder<TS extends TransitionSet> implements JsonConvertible {

    private final TS transitionSet;
    private MatcherBuilder matcherBuilder;
    private TransitionBuilder<TS> next;

    public TransitionBuilder(TS transitionSet, MatcherBuilder matcherBuilder) {
        this.transitionSet = transitionSet;
        this.matcherBuilder = matcherBuilder;
    }

    /**
     * Represents the set of NFA transitions currently contained in this transition fragment.
     */
    public TS getTransitionSet() {
        return transitionSet;
    }

    /**
     * Represents the character set matched by this transition fragment.
     */
    public MatcherBuilder getMatcherBuilder() {
        return matcherBuilder;
    }

    public void setMatcherBuilder(MatcherBuilder matcherBuilder) {
        this.matcherBuilder = matcherBuilder;
    }

    /**
     * Used by {@link StateTransitionCanonicalizer} for creating linked lists of
     * {@link TransitionBuilder} instances on the fly.
     */
    public TransitionBuilder<TS> getNext() {
        return next;
    }

    /**
     * Used by {@link StateTransitionCanonicalizer} for creating linked lists of
     * {@link TransitionBuilder} instances on the fly.
     */
    public void setNext(TransitionBuilder<TS> next) {
        this.next = next;
    }

    /**
     * Merge {@code this} and {@code other} into a newly created {@link TransitionBuilder} . The new
     * {@code transitionSet} is created by calling {@link TransitionSet#createMerged(TransitionSet)}
     * on {@code this.transitionSet} with {@code other.transitionSet} as parameter. The
     * {@code matcherBuilder} of the new {@link TransitionBuilder} will be set to
     * {@code mergedMatcher} directly.
     * 
     * @return the newly created {@link TransitionBuilder}. Overriding classes are expected to
     *         return an instance of their own type!
     */
    @SuppressWarnings("unchecked")
    public TransitionBuilder<TS> createMerged(TransitionBuilder<TS> other, MatcherBuilder mergedMatcher) {
        return new TransitionBuilder<>((TS) transitionSet.createMerged(other.transitionSet), mergedMatcher);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("matcherBuilder", getMatcherBuilder()),
                        Json.prop("transitionSet", getTransitionSet()));
    }
}
