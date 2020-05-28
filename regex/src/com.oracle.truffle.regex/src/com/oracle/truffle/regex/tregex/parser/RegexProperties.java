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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class RegexProperties implements JsonConvertible {

    private boolean alternations = false;
    private boolean backReferences = false;
    private boolean captureGroups = false;
    private boolean charClasses = false;
    private boolean loneSurrogates = false;
    private boolean quantifiers = false;
    private boolean lookAheadAssertions = false;
    private boolean complexLookAheadAssertions = false;
    private boolean negativeLookAheadAssertions = false;
    private boolean lookBehindAssertions = false;
    private boolean nonLiteralLookBehindAssertions = false;
    private boolean complexLookBehindAssertions = false;
    private boolean negativeLookBehindAssertions = false;
    private boolean largeCountedRepetitions = false;
    private boolean charClassesCanBeMatchedWithMask = true;
    private boolean fixedCodePointWidth = true;
    private int innerLiteralStart = -1;
    private int innerLiteralEnd = -1;

    public boolean hasAlternations() {
        return alternations;
    }

    public void setAlternations() {
        alternations = true;
    }

    public boolean hasBackReferences() {
        return backReferences;
    }

    public void setBackReferences() {
        backReferences = true;
    }

    public boolean hasCaptureGroups() {
        return captureGroups;
    }

    public void setCaptureGroups() {
        captureGroups = true;
    }

    public boolean hasCharClasses() {
        return charClasses;
    }

    public void setCharClasses() {
        charClasses = true;
    }

    public boolean hasLoneSurrogates() {
        return loneSurrogates;
    }

    public void setLoneSurrogates() {
        loneSurrogates = true;
    }

    public boolean hasQuantifiers() {
        return quantifiers;
    }

    public void setQuantifiers() {
        quantifiers = true;
    }

    public boolean hasLookAroundAssertions() {
        return hasLookAheadAssertions() || hasLookBehindAssertions();
    }

    public boolean hasLookAheadAssertions() {
        return lookAheadAssertions;
    }

    public void setLookAheadAssertions() {
        lookAheadAssertions = true;
    }

    public boolean hasComplexLookAheadAssertions() {
        return complexLookAheadAssertions;
    }

    public void setComplexLookAheadAssertions() {
        complexLookAheadAssertions = true;
    }

    public boolean hasNegativeLookAheadAssertions() {
        return negativeLookAheadAssertions;
    }

    public void setNegativeLookAheadAssertions() {
        negativeLookAheadAssertions = true;
    }

    public void setNegativeLookAheadAssertions(boolean negativeLookAheadAssertions) {
        this.negativeLookAheadAssertions = negativeLookAheadAssertions;
    }

    public boolean hasLookBehindAssertions() {
        return lookBehindAssertions;
    }

    public void setLookBehindAssertions() {
        lookBehindAssertions = true;
    }

    public boolean hasNonLiteralLookBehindAssertions() {
        return nonLiteralLookBehindAssertions;
    }

    public void setNonLiteralLookBehindAssertions() {
        nonLiteralLookBehindAssertions = true;
    }

    public boolean hasComplexLookBehindAssertions() {
        return complexLookBehindAssertions;
    }

    public void setComplexLookBehindAssertions() {
        complexLookBehindAssertions = true;
    }

    public boolean hasNegativeLookBehindAssertions() {
        return negativeLookBehindAssertions;
    }

    public void setNegativeLookBehindAssertions() {
        negativeLookBehindAssertions = true;
    }

    public void setNegativeLookBehindAssertions(boolean negativeLookBehindAssertions) {
        this.negativeLookBehindAssertions = negativeLookBehindAssertions;
    }

    public boolean hasLargeCountedRepetitions() {
        return largeCountedRepetitions;
    }

    public void setLargeCountedRepetitions() {
        largeCountedRepetitions = true;
    }

    public boolean charClassesCanBeMatchedWithMask() {
        return charClassesCanBeMatchedWithMask;
    }

    public void unsetCharClassesCanBeMatchedWithMask() {
        charClassesCanBeMatchedWithMask = false;
    }

    /**
     * Returns {@code true} iff no {@link CharacterClass} node in the expression may match a
     * variable amount of array slots in an encoded string.
     */
    public boolean isFixedCodePointWidth() {
        return fixedCodePointWidth;
    }

    public void setFixedCodePointWidth(boolean fixedCodePointWidth) {
        this.fixedCodePointWidth = fixedCodePointWidth;
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

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("alternations", alternations),
                        Json.prop("backReferences", backReferences),
                        Json.prop("charClasses", charClasses),
                        Json.prop("captureGroups", captureGroups),
                        Json.prop("lookAheadAssertions", lookAheadAssertions),
                        Json.prop("complexLookAheadAssertions", complexLookAheadAssertions),
                        Json.prop("negativeLookAheadAssertions", negativeLookAheadAssertions),
                        Json.prop("lookBehindAssertions", lookBehindAssertions),
                        Json.prop("nonLiteralLookBehindAssertions", nonLiteralLookBehindAssertions),
                        Json.prop("complexLookBehindAssertions", complexLookBehindAssertions),
                        Json.prop("negativeLookBehindAssertions", negativeLookBehindAssertions),
                        Json.prop("largeCountedRepetitions", largeCountedRepetitions));
    }
}
