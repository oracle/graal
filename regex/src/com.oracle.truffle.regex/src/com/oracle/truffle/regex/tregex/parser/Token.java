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
package com.oracle.truffle.regex.tregex.parser;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.chardata.CodePointSet;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class Token implements JsonConvertible {

    public static Token create(Kind kind) {
        return new Token(kind);
    }

    public static Token createBackReference(int groupNr) {
        return new BackReference(groupNr);
    }

    public static Token createQuantifier(int min, int max, boolean greedy) {
        return new Quantifier(min, max, greedy);
    }

    public static Token createCharClass(CodePointSet codePointSet) {
        return new CharacterClass(codePointSet);
    }

    public static Token createLookAheadAssertionBegin(boolean negated) {
        return new LookAheadAssertionBegin(negated);
    }

    public static Token createLookBehindAssertionBegin(boolean negated) {
        return new LookBehindAssertionBegin(negated);
    }

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

        public CharacterClass(CodePointSet codePointSet) {
            super(Kind.charClass);
            this.codePointSet = codePointSet;
        }

        @TruffleBoundary
        @Override
        public JsonObject toJson() {
            return super.toJson().append(Json.prop("codePointSet", codePointSet));
        }

        public CodePointSet getCodePointSet() {
            return codePointSet;
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
