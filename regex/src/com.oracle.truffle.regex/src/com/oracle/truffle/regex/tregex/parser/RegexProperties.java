/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class RegexProperties implements JsonConvertible {

    private boolean alternations = false;
    private boolean backReferences = false;
    private boolean captureGroups = false;
    private boolean charClasses = false;
    private boolean lookAheadAssertions = false;
    private boolean complexLookAheadAssertions = false;
    private boolean negativeLookAheadAssertions = false;
    private boolean lookBehindAssertions = false;
    private boolean nonLiteralLookBehindAssertions = false;
    private boolean complexLookBehindAssertions = false;
    private boolean negativeLookBehindAssertions = false;
    private boolean largeCountedRepetitions = false;
    private String innerLiteral = null;

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

    public boolean hasLargeCountedRepetitions() {
        return largeCountedRepetitions;
    }

    public void setLargeCountedRepetitions() {
        largeCountedRepetitions = true;
    }

    public void setInnerLiteral(String containedLiteral) {
        this.innerLiteral = containedLiteral;
    }

    public boolean hasInnerLiteral() {
        return innerLiteral != null;
    }

    public String getInnerLiteral() {
        return innerLiteral;
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
