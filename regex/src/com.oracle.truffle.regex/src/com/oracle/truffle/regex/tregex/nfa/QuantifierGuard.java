/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.ConditionalBackReferenceGroup;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Objects;

/**
 * Transition guards introduced by bounded {@link Quantifier}s.
 */
public final class QuantifierGuard {

    public enum Kind {
        /**
         * Transition is entering a quantified expression. Just increase the loop count.
         */
        enter,
        /**
         * Transition represents a back-edge in the quantifier loop. Check if the loop count is
         * below {@link Quantifier#getMax()}, then increase loop count.
         */
        loop,
        /**
         * Transition represents a back-edge in a quantifier loop without upper bound, i.e.
         * quantifiers where {@link Quantifier#isInfiniteLoop()} is {@code true}. Just increase the
         * loop count.
         */
        loopInc,
        /**
         * Transition is leaving a quantified expression. Check if the loop count is above
         * {@link Quantifier#getMin()}, then reset the loop count.
         */
        exit,
        /**
         * Transition is leaving a quantified expression without lower bound, i.e. quantifiers where
         * {@link Quantifier#getMin()} {@code == 0}. Just reset the loop count.
         */
        exitReset,
        /**
         * Transition is entering a quantified expression that may match the empty string. Save the
         * current index.
         */
        enterZeroWidth,
        /**
         * Transition is leaving a quantified expression that may match the empty string. Check if
         * the current index is greater than the saved index. In the case of flavors in which
         * {@link RegexFlavor#emptyChecksMonitorCaptureGroups()}, also check if any capture groups
         * were modified.
         */
        exitZeroWidth,
        /**
         * Transition is leaving a quantified expression that may match the empty string and it is
         * about to continue to what follows the loop. This is possible in flavors in which
         * {@link RegexFlavor#failingEmptyChecksDontBacktrack()} and only when the last iteration of
         * the quantified expression fails the empty check (the check for the index and the state of
         * capture groups tested by {@link #exitZeroWidth}).
         */
        escapeZeroWidth,
        /**
         * Transition is passing a capture group boundary. We need this information in order to
         * implement the empty check test in {@link #exitZeroWidth}, which, in the case of flavors
         * in which {@link RegexFlavor#emptyChecksMonitorCaptureGroups()}, where we need to monitor
         * the state of capture groups in between {@link #enterZeroWidth} and
         * {@link #exitZeroWidth}.
         */
        updateCG,
        /**
         * Transition is leaving a group containing recursive back-references.
         */
        updateRecursiveBackrefPointer,
        /**
         * Transition is entering the then-branch (the first alternative) of a
         * {@link ConditionalBackReferenceGroup}. The capture group identified by
         * {@link #getIndex()} must be matched in order to proceed.
         */
        checkGroupMatched,
        /**
         * Transition is entering the else-branch (the second alternative) of a
         * {@link ConditionalBackReferenceGroup}. The capture group identified by
         * {@link #getIndex()} must be *not* matched in order to proceed.
         */
        checkGroupNotMatched
    }

    public static final QuantifierGuard[] NO_GUARDS = {};

    private final Kind kind;
    private final Quantifier quantifier;
    private final int index;

    private QuantifierGuard(Kind kind, Quantifier quantifier) {
        this.kind = kind;
        this.quantifier = quantifier;
        this.index = -1;
    }

    private QuantifierGuard(Kind kind, int index) {
        this.kind = kind;
        this.quantifier = null;
        this.index = index;
    }

    public static QuantifierGuard createEnter(Quantifier quantifier) {
        return new QuantifierGuard(Kind.enter, quantifier);
    }

    public static QuantifierGuard createLoop(Quantifier quantifier) {
        return new QuantifierGuard(Kind.loop, quantifier);
    }

    public static QuantifierGuard createLoopInc(Quantifier quantifier) {
        return new QuantifierGuard(Kind.loopInc, quantifier);
    }

    public static QuantifierGuard createExit(Quantifier quantifier) {
        return new QuantifierGuard(Kind.exit, quantifier);
    }

    public static QuantifierGuard createClear(Quantifier quantifier) {
        return new QuantifierGuard(Kind.exitReset, quantifier);
    }

    public static QuantifierGuard createEnterZeroWidth(Quantifier quantifier) {
        return new QuantifierGuard(Kind.enterZeroWidth, quantifier);
    }

    public static QuantifierGuard createExitZeroWidth(Quantifier quantifier) {
        return new QuantifierGuard(Kind.exitZeroWidth, quantifier);
    }

    public static QuantifierGuard createEscapeZeroWidth(Quantifier quantifier) {
        return new QuantifierGuard(Kind.escapeZeroWidth, quantifier);
    }

    public static QuantifierGuard createUpdateCG(int index) {
        return new QuantifierGuard(Kind.updateCG, index);
    }

    public static QuantifierGuard createUpdateRecursiveBackref(int index) {
        return new QuantifierGuard(Kind.updateRecursiveBackrefPointer, index);
    }

    public static QuantifierGuard createCheckGroupMatched(int groupNumber) {
        return new QuantifierGuard(Kind.checkGroupMatched, groupNumber);
    }

    public static QuantifierGuard createCheckGroupNotMatched(int groupNumber) {
        return new QuantifierGuard(Kind.checkGroupNotMatched, groupNumber);
    }

    public Kind getKind() {
        return kind;
    }

    public Quantifier getQuantifier() {
        return quantifier;
    }

    /**
     * Returns the capture group boundary index for {@code updateCG} guards.
     */
    public int getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QuantifierGuard)) {
            return false;
        }
        QuantifierGuard other = (QuantifierGuard) obj;
        return this.kind == other.kind && Objects.equals(this.quantifier, other.quantifier) && this.index == other.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, quantifier, index);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        if (quantifier != null) {
            return kind + " " + quantifier;
        } else {
            return kind + " " + index;
        }
    }

    @TruffleBoundary
    public JsonValue toJson() {
        return Json.val(toString());
    }
}
