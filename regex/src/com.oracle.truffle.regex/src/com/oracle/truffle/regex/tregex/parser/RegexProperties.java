/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class RegexProperties implements JsonConvertible {

    private static final int FLAG_ALTERNATIONS = 1;
    private static final int FLAG_CAPTURE_GROUPS = 1 << 1;
    private static final int FLAG_CHAR_CLASSES = 1 << 2;
    private static final int FLAG_LONE_SURROGATES = 1 << 3;
    private static final int FLAG_QUANTIFIERS = 1 << 4;
    private static final int FLAG_LOOK_AHEAD_ASSERTIONS = 1 << 5;
    private static final int FLAG_NEGATIVE_LOOK_AHEAD_ASSERTIONS = 1 << 6;
    private static final int FLAG_LOOK_BEHIND_ASSERTIONS = 1 << 7;
    private static final int FLAG_NON_LITERAL_LOOK_BEHIND_ASSERTIONS = 1 << 8;
    private static final int FLAG_NEGATIVE_LOOK_BEHIND_ASSERTIONS = 1 << 9;
    private static final int FLAG_LARGE_COUNTED_REPETITIONS = 1 << 10;
    private static final int FLAG_CHAR_CLASSES_CAN_BE_MATCHED_WITH_MASK = 1 << 11;
    private static final int FLAG_FIXED_CODEPOINT_WIDTH = 1 << 12;
    private static final int FLAG_CAPTURE_GROUPS_IN_LOOK_AROUND_ASSERTIONS = 1 << 13;
    private static final int FLAG_EMPTY_CAPTURE_GROUPS = 1 << 14;
    private static final int FLAG_ATOMIC_GROUPS = 1 << 15;
    private static final int FLAG_BACK_REFERENCES = 1 << 16;
    private static final int FLAG_RECURSIVE_BACK_REFERENCES = 1 << 17;
    private static final int FLAG_NESTED_LOOK_BEHIND_ASSERTIONS = 1 << 18;
    private static final int FLAG_CONDITIONAL_BACKREFERENCES = 1 << 19;
    private static final int FLAG_CONDITIONAL_REFERENCES_INTO_LOOK_AHEADS = 1 << 20;
    private static final int FLAG_MATCH_BOUNDARY_ASSERTIONS = 1 << 21;

    private int flags = FLAG_CHAR_CLASSES_CAN_BE_MATCHED_WITH_MASK | FLAG_FIXED_CODEPOINT_WIDTH;
    private int innerLiteralStart = -1;
    private int innerLiteralEnd = -1;

    protected boolean getFlag(int flag) {
        return (flags & flag) != 0;
    }

    private void setFlag(int flag) {
        flags |= flag;
    }

    private void clearFlag(int flag) {
        flags &= ~flag;
    }

    public boolean hasAlternations() {
        return getFlag(FLAG_ALTERNATIONS);
    }

    public void setAlternations() {
        setFlag(FLAG_ALTERNATIONS);
    }

    public boolean hasCaptureGroups() {
        return getFlag(FLAG_CAPTURE_GROUPS);
    }

    public void setCaptureGroups() {
        setFlag(FLAG_CAPTURE_GROUPS);
    }

    public boolean hasEmptyCaptureGroups() {
        return getFlag(FLAG_EMPTY_CAPTURE_GROUPS);
    }

    public void setEmptyCaptureGroups() {
        setFlag(FLAG_EMPTY_CAPTURE_GROUPS);
    }

    public boolean hasAtomicGroups() {
        return getFlag(FLAG_ATOMIC_GROUPS);
    }

    public void setAtomicGroups() {
        setFlag(FLAG_ATOMIC_GROUPS);
    }

    public boolean hasCharClasses() {
        return getFlag(FLAG_CHAR_CLASSES);
    }

    public void setCharClasses() {
        setFlag(FLAG_CHAR_CLASSES);
    }

    public boolean hasLoneSurrogates() {
        return getFlag(FLAG_LONE_SURROGATES);
    }

    public void setLoneSurrogates() {
        setFlag(FLAG_LONE_SURROGATES);
    }

    public boolean hasQuantifiers() {
        return getFlag(FLAG_QUANTIFIERS);
    }

    public void setQuantifiers() {
        setFlag(FLAG_QUANTIFIERS);
    }

    public boolean hasLookAroundAssertions() {
        return getFlag(FLAG_LOOK_AHEAD_ASSERTIONS | FLAG_LOOK_BEHIND_ASSERTIONS | FLAG_NEGATIVE_LOOK_AHEAD_ASSERTIONS | FLAG_NEGATIVE_LOOK_BEHIND_ASSERTIONS);
    }

    public boolean hasLookAheadAssertions() {
        return getFlag(FLAG_LOOK_AHEAD_ASSERTIONS);
    }

    public void setLookAheadAssertions() {
        setFlag(FLAG_LOOK_AHEAD_ASSERTIONS);
    }

    public boolean hasNegativeLookAheadAssertions() {
        return getFlag(FLAG_NEGATIVE_LOOK_AHEAD_ASSERTIONS);
    }

    public void setNegativeLookAheadAssertions() {
        setFlag(FLAG_NEGATIVE_LOOK_AHEAD_ASSERTIONS);
    }

    public boolean hasLookBehindAssertions() {
        return getFlag(FLAG_LOOK_BEHIND_ASSERTIONS);
    }

    public void setLookBehindAssertions() {
        setFlag(FLAG_LOOK_BEHIND_ASSERTIONS);
    }

    public boolean hasNonLiteralLookBehindAssertions() {
        return getFlag(FLAG_NON_LITERAL_LOOK_BEHIND_ASSERTIONS);
    }

    public void setNonLiteralLookBehindAssertions() {
        setFlag(FLAG_NON_LITERAL_LOOK_BEHIND_ASSERTIONS);
    }

    public boolean hasNegativeLookBehindAssertions() {
        return getFlag(FLAG_NEGATIVE_LOOK_BEHIND_ASSERTIONS);
    }

    public void setNegativeLookBehindAssertions() {
        setFlag(FLAG_NEGATIVE_LOOK_BEHIND_ASSERTIONS);
    }

    public boolean hasLargeCountedRepetitions() {
        return getFlag(FLAG_LARGE_COUNTED_REPETITIONS);
    }

    public void setLargeCountedRepetitions() {
        setFlag(FLAG_LARGE_COUNTED_REPETITIONS);
    }

    public boolean charClassesCanBeMatchedWithMask() {
        return getFlag(FLAG_CHAR_CLASSES_CAN_BE_MATCHED_WITH_MASK);
    }

    public void unsetCharClassesCanBeMatchedWithMask() {
        clearFlag(FLAG_CHAR_CLASSES_CAN_BE_MATCHED_WITH_MASK);
    }

    /**
     * Returns {@code true} iff no {@link CharacterClass} node in the expression may match a
     * variable amount of array slots in an encoded string.
     */
    public boolean isFixedCodePointWidth() {
        return getFlag(FLAG_FIXED_CODEPOINT_WIDTH);
    }

    public void unsetFixedCodePointWidth() {
        clearFlag(FLAG_FIXED_CODEPOINT_WIDTH);
    }

    public void setInnerLiteral(int start, int end) {
        this.innerLiteralStart = start;
        this.innerLiteralEnd = end;
    }

    public boolean hasInnerLiteral() {
        return innerLiteralStart >= 0;
    }

    public int getInnerLiteralStart() {
        return innerLiteralStart;
    }

    public int getInnerLiteralEnd() {
        return innerLiteralEnd;
    }

    public boolean hasCaptureGroupsInLookAroundAssertions() {
        return getFlag(FLAG_CAPTURE_GROUPS_IN_LOOK_AROUND_ASSERTIONS);
    }

    public void setCaptureGroupsInLookAroundAssertions() {
        setFlag(FLAG_CAPTURE_GROUPS_IN_LOOK_AROUND_ASSERTIONS);
    }

    public boolean hasBackReferences() {
        return getFlag(FLAG_BACK_REFERENCES);
    }

    public void setBackReferences() {
        setFlag(FLAG_BACK_REFERENCES);
    }

    public boolean hasRecursiveBackReferences() {
        return getFlag(FLAG_RECURSIVE_BACK_REFERENCES);
    }

    public void setRecursiveBackReferences() {
        setFlag(FLAG_RECURSIVE_BACK_REFERENCES);
    }

    public boolean hasNestedLookBehindAssertions() {
        return getFlag(FLAG_NESTED_LOOK_BEHIND_ASSERTIONS);
    }

    public void setNestedLookBehindAssertions() {
        setFlag(FLAG_NESTED_LOOK_BEHIND_ASSERTIONS);
    }

    public boolean hasConditionalBackReferences() {
        return getFlag(FLAG_CONDITIONAL_BACKREFERENCES);
    }

    public void setConditionalBackReferences() {
        setFlag(FLAG_CONDITIONAL_BACKREFERENCES);
    }

    public boolean hasConditionalReferencesIntoLookAheads() {
        return getFlag(FLAG_CONDITIONAL_REFERENCES_INTO_LOOK_AHEADS);
    }

    public void setConditionalReferencesIntoLookAheads() {
        setFlag(FLAG_CONDITIONAL_REFERENCES_INTO_LOOK_AHEADS);
    }

    public boolean hasMatchBoundaryAssertions() {
        return getFlag(FLAG_MATCH_BOUNDARY_ASSERTIONS);
    }

    public void setMatchBoundaryAssertions() {
        setFlag(FLAG_MATCH_BOUNDARY_ASSERTIONS);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("alternations", hasAlternations()),
                        Json.prop("charClasses", hasCharClasses()),
                        Json.prop("captureGroups", hasCaptureGroups()),
                        Json.prop("lookAheadAssertions", hasLookAheadAssertions()),
                        Json.prop("negativeLookAheadAssertions", hasNegativeLookAheadAssertions()),
                        Json.prop("lookBehindAssertions", hasLookBehindAssertions()),
                        Json.prop("nonLiteralLookBehindAssertions", hasNonLiteralLookBehindAssertions()),
                        Json.prop("negativeLookBehindAssertions", hasNegativeLookBehindAssertions()),
                        Json.prop("largeCountedRepetitions", hasLargeCountedRepetitions()),
                        Json.prop("captureGroupsInLookAroundAssertions", hasCaptureGroupsInLookAroundAssertions()),
                        Json.prop("backReferences", hasBackReferences()),
                        Json.prop("nestedLookBehindAssertions", hasNestedLookBehindAssertions()),
                        Json.prop("conditionalBackReferences", hasConditionalBackReferences()),
                        Json.prop("conditionalReferencesIntoLookAheads", hasConditionalReferencesIntoLookAheads()));
    }
}
