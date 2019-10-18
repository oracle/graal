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
package com.oracle.truffle.regex.tregex.automaton;

import com.oracle.truffle.regex.charset.CharSet;
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
    private CharSet matcherBuilder;
    private TransitionBuilder<TS> next;

    public TransitionBuilder(TS transitionSet, CharSet matcherBuilder) {
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
    public CharSet getMatcherBuilder() {
        return matcherBuilder;
    }

    public void setMatcherBuilder(CharSet matcherBuilder) {
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
    public TransitionBuilder<TS> createMerged(TransitionBuilder<TS> other, CharSet mergedMatcher) {
        return new TransitionBuilder<>((TS) transitionSet.createMerged(other.transitionSet), mergedMatcher);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("matcherBuilder", getMatcherBuilder()),
                        Json.prop("transitionSet", getTransitionSet()));
    }
}
