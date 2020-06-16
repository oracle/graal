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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

public class Token implements JsonConvertible {

    public enum Kind {
        caret,
        dollar,
        wordBoundary,
        nonWordBoundary,
        backReference,
        quantifier,
        alternation,
        captureGroupBegin,
        nonCaptureGroupBegin,
        lookAheadAssertionBegin,
        lookBehindAssertionBegin,
        groupEnd,
        charClass
    }

    private static final Token CARET = new Token(Kind.caret);
    private static final Token DOLLAR = new Token(Kind.dollar);
    private static final Token WORD_BOUNDARY = new Token(Kind.wordBoundary);
    private static final Token NON_WORD_BOUNDARY = new Token(Kind.nonWordBoundary);
    private static final Token ALTERNATION = new Token(Kind.alternation);
    private static final Token CAPTURE_GROUP_BEGIN = new Token(Kind.captureGroupBegin);
    private static final Token NON_CAPTURE_GROUP_BEGIN = new Token(Kind.nonCaptureGroupBegin);
    private static final Token LOOK_AHEAD_ASSERTION_BEGIN = new LookAheadAssertionBegin(false);
    private static final Token NEGATIVE_LOOK_AHEAD_ASSERTION_BEGIN = new LookAheadAssertionBegin(true);
    private static final Token LOOK_BEHIND_ASSERTION_BEGIN = new LookBehindAssertionBegin(false);
    private static final Token NEGATIVE_LOOK_BEHIND_ASSERTION_BEGIN = new LookBehindAssertionBegin(true);
    private static final Token GROUP_END = new Token(Kind.groupEnd);

    public static Token createCaret() {
        return CARET;
    }

    public static Token createDollar() {
        return DOLLAR;
    }

    public static Token createWordBoundary() {
        return WORD_BOUNDARY;
    }

    public static Token createNonWordBoundary() {
        return NON_WORD_BOUNDARY;
    }

    public static Token createAlternation() {
        return ALTERNATION;
    }

    public static Token createCaptureGroupBegin() {
        return CAPTURE_GROUP_BEGIN;
    }

    public static Token createNonCaptureGroupBegin() {
        return NON_CAPTURE_GROUP_BEGIN;
    }

    public static Token createLookAheadAssertionBegin() {
        return LOOK_AHEAD_ASSERTION_BEGIN;
    }

    public static Token createLookBehindAssertionBegin() {
        return LOOK_BEHIND_ASSERTION_BEGIN;
    }

    public static Token createGroupEnd() {
        return GROUP_END;
    }

    public static Token createBackReference(int groupNr) {
        return new BackReference(groupNr);
    }

    public static Quantifier createQuantifier(int min, int max, boolean greedy) {
        return new Quantifier(min, max, greedy);
    }

    public static Token createCharClass(CodePointSet codePointSet) {
        return new CharacterClass(codePointSet, false);
    }

    public static Token createCharClass(CodePointSet codePointSet, boolean wasSingleChar) {
        return new CharacterClass(codePointSet, wasSingleChar);
    }

    public static Token createLookAheadAssertionBegin(boolean negated) {
        return negated ? NEGATIVE_LOOK_AHEAD_ASSERTION_BEGIN : LOOK_AHEAD_ASSERTION_BEGIN;
    }

    public static Token createLookBehindAssertionBegin(boolean negated) {
        return negated ? NEGATIVE_LOOK_BEHIND_ASSERTION_BEGIN : LOOK_BEHIND_ASSERTION_BEGIN;
    }

    public final Kind kind;
    private SourceSection sourceSection;

    public Token(Kind kind) {
        this.kind = kind;
    }

    public SourceSection getSourceSection() {
        return sourceSection;
    }

    public void setSourceSection(SourceSection sourceSection) {
        this.sourceSection = sourceSection;
    }

    @TruffleBoundary
    @Override
    public JsonObject toJson() {
        return Json.obj(Json.prop("kind", kind.name()));
    }

    public static final class Quantifier extends Token {

        private final int min;
        private final int max;
        private final boolean greedy;
        @CompilationFinal private int index = -1;
        @CompilationFinal private int zeroWidthIndex = -1;

        public Quantifier(int min, int max, boolean greedy) {
            super(Kind.quantifier);
            this.min = min;
            this.max = max;
            this.greedy = greedy;
        }

        public boolean isInfiniteLoop() {
            return getMax() == -1;
        }

        /**
         * The minimum number of times the quantified element must appear. Can be -1 to represent a
         * virtually infinite number of occurrences are necessary (e.g. as in
         * <code>a{1111111111111111111,}</code>). Any number which is larger than the maximum size
         * of the platform's String data type is considered "virtually infinite".
         */
        public int getMin() {
            return min;
        }

        public int getMax() {
            return max;
        }

        public boolean isGreedy() {
            return greedy;
        }

        public boolean hasIndex() {
            return index >= 0;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public boolean hasZeroWidthIndex() {
            return zeroWidthIndex >= 0;
        }

        public int getZeroWidthIndex() {
            return zeroWidthIndex;
        }

        public void setZeroWidthIndex(int zeroWidthIndex) {
            this.zeroWidthIndex = zeroWidthIndex;
        }

        /**
         * Returns {@code true} iff both {@link #getMin()} and {@link #getMax()} are less or equal
         * to the given threshold, or infinite {@link #isInfiniteLoop()}.
         */
        public boolean isWithinThreshold(int threshold) {
            return min <= threshold && max <= threshold;
        }

        /**
         * Returns {@code true} iff "unrolling" this quantifier is trivial, i.e. nothing has to be
         * duplicated. This is the case for quantifiers {@code ?} and {@code *}.
         */
        public boolean isUnrollTrivial() {
            return min == 0 && max <= 1;
        }

        @Override
        public int hashCode() {
            return 31 * min + 31 * max + (greedy ? 1 : 0);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Quantifier)) {
                return false;
            }
            Quantifier o = (Quantifier) obj;
            return min == o.min && max == o.max && greedy == o.greedy;
        }

        @TruffleBoundary
        @Override
        public String toString() {
            String ret = minMaxToString();
            return isGreedy() ? ret : ret + "?";
        }

        private String minMaxToString() {
            if (min == 0 && max == 1) {
                return "?";
            }
            if (min == 0 && isInfiniteLoop()) {
                return "*";
            }
            if (min == 1 && isInfiniteLoop()) {
                return "+";
            }
            return String.format("{%d,%s}", min, isInfiniteLoop() ? "" : String.valueOf(max));
        }

        @TruffleBoundary
        @Override
        public JsonObject toJson() {
            return super.toJson().append(
                            Json.prop("min", getMin()),
                            Json.prop("max", getMax()),
                            Json.prop("greedy", isGreedy()));
        }
    }

    public static final class CharacterClass extends Token {

        private final CodePointSet codePointSet;
        private final boolean wasSingleChar;

        public CharacterClass(CodePointSet codePointSet, boolean wasSingleChar) {
            super(Kind.charClass);
            this.codePointSet = codePointSet;
            this.wasSingleChar = wasSingleChar;
        }

        @TruffleBoundary
        @Override
        public JsonObject toJson() {
            return super.toJson().append(Json.prop("codePointSet", codePointSet));
        }

        public CodePointSet getCodePointSet() {
            return codePointSet;
        }

        public boolean wasSingleChar() {
            return wasSingleChar;
        }
    }

    public static final class BackReference extends Token {

        private final int groupNr;

        public BackReference(int groupNr) {
            super(Kind.backReference);
            this.groupNr = groupNr;
        }

        @TruffleBoundary
        @Override
        public JsonObject toJson() {
            return super.toJson().append(Json.prop("groupNr", groupNr));
        }

        public int getGroupNr() {
            return groupNr;
        }
    }

    public static class LookAroundAssertionBegin extends Token {

        private final boolean negated;

        protected LookAroundAssertionBegin(Token.Kind kind, boolean negated) {
            super(kind);
            this.negated = negated;
        }

        public boolean isNegated() {
            return negated;
        }
    }

    public static final class LookAheadAssertionBegin extends LookAroundAssertionBegin {

        public LookAheadAssertionBegin(boolean negated) {
            super(Token.Kind.lookAheadAssertionBegin, negated);
        }
    }

    public static final class LookBehindAssertionBegin extends LookAroundAssertionBegin {

        public LookBehindAssertionBegin(boolean negated) {
            super(Token.Kind.lookBehindAssertionBegin, negated);
        }
    }
}
