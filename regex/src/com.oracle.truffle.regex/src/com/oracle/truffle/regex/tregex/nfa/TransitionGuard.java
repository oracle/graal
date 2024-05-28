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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.Arrays;
import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.ConditionalBackReferenceGroup;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Transition guards introduced by bounded {@link Quantifier}s.
 */
public final class TransitionGuard {

    public enum Kind {
        /**
         * Transition represents a back-edge in the quantifier loop. Check if the loop count is
         * below {@link Quantifier#getMax()}, then increase loop count.
         */
        loop,
        /**
         * Transition represents either a first entry into a quantified expression, or a back-edge
         * in a quantifier loop without upper bound, i.e. quantifiers where
         * {@link Quantifier#isInfiniteLoop()} is {@code true}. Just increase the loop count.
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
         * {@link #getGroupNumber(long)} must be matched in order to proceed.
         */
        checkGroupMatched,
        /**
         * Transition is entering the else-branch (the second alternative) of a
         * {@link ConditionalBackReferenceGroup}. The capture group identified by
         * {@link #getGroupNumber(long)} must be *not* matched in order to proceed.
         */
        checkGroupNotMatched
    }

    @CompilationFinal(dimensions = 1) private static final Kind[] KIND_VALUES = Arrays.copyOf(Kind.values(), Kind.values().length);

    private static final EnumSet<Kind> QUANTIFIER_GUARDS = EnumSet.of(Kind.loop, Kind.loopInc, Kind.exit, Kind.exitReset);
    private static final EnumSet<Kind> ZERO_WIDTH_QUANTIFIER_GUARDS = EnumSet.of(Kind.enterZeroWidth, Kind.exitZeroWidth, Kind.escapeZeroWidth);
    private static final EnumSet<Kind> GROUP_NUMBER_GUARDS = EnumSet.of(Kind.updateRecursiveBackrefPointer, Kind.checkGroupMatched, Kind.checkGroupNotMatched);
    private static final EnumSet<Kind> GROUP_BOUNDARY_INDEX_GUARDS = EnumSet.of(Kind.updateCG);

    public static final long[] NO_GUARDS = {};

    public static long createLoop(Quantifier quantifier) {
        return create(Kind.loop, quantifier);
    }

    public static long createLoopInc(Quantifier quantifier) {
        return create(Kind.loopInc, quantifier);
    }

    public static long createExit(Quantifier quantifier) {
        return create(Kind.exit, quantifier);
    }

    public static long createExitReset(Quantifier quantifier) {
        return create(Kind.exitReset, quantifier);
    }

    public static long createEnterZeroWidth(Quantifier quantifier) {
        return createZeroWidth(Kind.enterZeroWidth, quantifier);
    }

    public static long createEnterZeroWidthFromExit(long guard) {
        assert is(guard, Kind.exitZeroWidth) || is(guard, Kind.escapeZeroWidth);
        return create(Kind.enterZeroWidth, getZeroWidthQuantifierIndex(guard));
    }

    public static long createExitZeroWidth(Quantifier quantifier) {
        return createZeroWidth(Kind.exitZeroWidth, quantifier);
    }

    public static long createEscapeZeroWidth(Quantifier quantifier) {
        return createZeroWidth(Kind.escapeZeroWidth, quantifier);
    }

    public static long createUpdateCG(int index) {
        return create(Kind.updateCG, index);
    }

    public static long createUpdateRecursiveBackref(int index) {
        return create(Kind.updateRecursiveBackrefPointer, index);
    }

    public static long createCheckGroupMatched(int groupNumber) {
        return create(Kind.checkGroupMatched, groupNumber);
    }

    public static long createCheckGroupNotMatched(int groupNumber) {
        return create(Kind.checkGroupNotMatched, groupNumber);
    }

    private static long create(Kind kind, Quantifier q) {
        assert q.hasIndex();
        return create(kind, q.getIndex());
    }

    private static long createZeroWidth(Kind kind, Quantifier q) {
        assert q.hasZeroWidthIndex();
        return create(kind, q.getZeroWidthIndex());
    }

    private static long create(Kind kind, int index) {
        return ((long) kind.ordinal()) << 32 | index;
    }

    private static int getKindOrdinal(long guard) {
        return (int) ((guard >>> 32) & 0xff);
    }

    public static Kind getKind(long guard) {
        return KIND_VALUES[getKindOrdinal(guard)];
    }

    public static boolean is(long guard, Kind kind) {
        return getKindOrdinal(guard) == kind.ordinal();
    }

    public static int getQuantifierIndex(long guard) {
        assert QUANTIFIER_GUARDS.contains(getKind(guard));
        return (int) guard;
    }

    public static int getZeroWidthQuantifierIndex(long guard) {
        assert ZERO_WIDTH_QUANTIFIER_GUARDS.contains(getKind(guard));
        return (int) guard;
    }

    public static int getGroupNumber(long guard) {
        assert GROUP_NUMBER_GUARDS.contains(getKind(guard));
        return (int) guard;
    }

    /**
     * Returns the capture group boundary index for {@code updateCG} guards.
     */
    public static int getGroupBoundaryIndex(long guard) {
        assert GROUP_BOUNDARY_INDEX_GUARDS.contains(getKind(guard));
        return (int) guard;
    }

    @TruffleBoundary
    public static String toString(long guard) {
        return getKind(guard) + " " + ((int) guard);
    }

    @TruffleBoundary
    public static String dump(long[] guards) {
        StringBuilder sb = new StringBuilder();
        for (long guard : guards) {
            sb.append(toString(guard)).append('\n');
        }
        return sb.toString();
    }

    @TruffleBoundary
    public static JsonValue toJson(long guard) {
        return Json.val(toString(guard));
    }
}
