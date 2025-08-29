/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFABQTrackingTransitionOpsNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This class represents a power-set automaton state transition fragment to be used by
 * {@link StateTransitionCanonicalizer}.<br>
 * A transition in a power-set automaton consists of a set of transitions of the NFA that the
 * power-set automaton is being built from, and the set of characters it can match.
 */
public class TransitionBuilder<SI extends StateIndex<? super S>, S extends AbstractState<S, T>, T extends AbstractTransition<S, T>> implements JsonConvertible {

    private final TransitionSet<SI, S, T> transitionSet;
    private CodePointSet cps;
    private long[] constraints;
    // This field is not final in order to be able to mutate it after preparing the operations for
    // the DFA executor.
    // This is only used so that it gets displayed correctly in the dfa visualizer.
    private long[] operations;
    private DFABQTrackingTransitionOpsNode bqTransition;

    public TransitionBuilder(T[] transitions, StateSet<SI, S> targetStateSet, CodePointSet matcherBuilder, long[] constraints, long[] operations) {
        this(new TransitionSet<>(transitions, targetStateSet), matcherBuilder, constraints, operations);
    }

    public TransitionBuilder(TransitionSet<SI, S, T> transitionSet, CodePointSet matcherBuilder, long[] constraints, long[] operations) {
        this.transitionSet = transitionSet;
        this.cps = matcherBuilder;
        this.constraints = constraints;
        this.operations = operations;
    }

    public TransitionSet<SI, S, T> getTransitionSet() {
        return transitionSet;
    }

    /**
     * Represents the character set matched by this transition fragment.
     */
    public CodePointSet getCodePointSet() {
        return cps;
    }

    public void setMatcherBuilder(CodePointSet cps) {
        this.cps = cps;
    }

    public long[] getConstraints() {
        return constraints;
    }

    public void setConstraints(long[] constraints) {
        this.constraints = constraints;
    }

    public boolean hasConstraints() {
        return constraints.length > 0;
    }

    public boolean hasNoConstraints() {
        return constraints.length == 0;
    }

    public long[] getOperations() {
        return operations;
    }

    public void setOperations(long[] operations) {
        this.operations = operations;
    }

    public boolean hasOperations() {
        return operations.length > 0;
    }

    public boolean hasBqTransition() {
        return bqTransition != null;
    }

    public DFABQTrackingTransitionOpsNode getBqTransition() {
        return bqTransition;
    }

    public void setBqTransition(DFABQTrackingTransitionOpsNode bqTransition) {
        assert this.bqTransition == null;
        this.bqTransition = bqTransition;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("matcherBuilder", getCodePointSet()));
    }
}
