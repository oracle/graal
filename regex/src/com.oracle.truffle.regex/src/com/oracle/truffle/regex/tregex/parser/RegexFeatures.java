/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * This class describes the features found in a regular expression which are salient for determining
 * whether or not the regular expression can be supported by our regex engines.
 * <p>
 * Instances of this class are created and populated by calls to {@link RegexParser#validate()}.
 * </p>
 */
public class RegexFeatures {

    // Features not supported by TRegex.
    private boolean backReferences = false;
    private boolean negativeLookAheadAssertions = false;
    private boolean nonLiteralLookBehindAssertions = false;
    private boolean negativeLookBehindAssertions = false;
    private boolean largeCountedRepetitions = false;

    // Features not supported by Joni.
    private boolean backReferencesInLookBehind = false;
    private boolean nonTrivialQuantifiersInLookBehind = false;
    private boolean wordBoundaryAssertionsInLookBehind = false;
    private boolean endOfStringAssertionsInLookBehind = false;
    private boolean negativeLookBehindAssertionsInLookBehind = false;
    private boolean lookAheadAssertionsInLookBehind = false;

    public boolean hasBackReferences() {
        return backReferences;
    }

    public void setBackReferences() {
        backReferences = true;
    }

    public boolean hasNegativeLookAheadAssertions() {
        return negativeLookAheadAssertions;
    }

    public void setNegativeLookAheadAssertions() {
        negativeLookAheadAssertions = true;
    }

    public boolean hasNonLiteralLookBehindAssertions() {
        return nonLiteralLookBehindAssertions;
    }

    public void setNonLiteralLookBehindAssertions() {
        nonLiteralLookBehindAssertions = true;
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

    public boolean hasBackReferencesInLookBehind() {
        return backReferencesInLookBehind;
    }

    public void setBackReferencesInLookBehind() {
        backReferencesInLookBehind = true;
    }

    public boolean hasNonTrivialQuantifiersInLookBehind() {
        return nonTrivialQuantifiersInLookBehind;
    }

    public void setNonTrivialQuantifiersInLookBehind() {
        nonTrivialQuantifiersInLookBehind = true;
    }

    public boolean hasWordBoundaryAssertionsInLookBehind() {
        return wordBoundaryAssertionsInLookBehind;
    }

    public void setWordBoundaryAssertionsInLookBehind() {
        wordBoundaryAssertionsInLookBehind = true;
    }

    public boolean hasEndOfStringAssertionsInLookBehind() {
        return endOfStringAssertionsInLookBehind;
    }

    public void setEndOfStringAssertionsInLookBehind() {
        endOfStringAssertionsInLookBehind = true;
    }

    public boolean hasNegativeLookBehindAssertionsInLookBehind() {
        return negativeLookBehindAssertionsInLookBehind;
    }

    public void setNegativeLookBehindAssertionsInLookBehind() {
        negativeLookBehindAssertionsInLookBehind = true;
    }

    public boolean hasLookAheadAssertionsInLookBehind() {
        return lookAheadAssertionsInLookBehind;
    }

    public void setLookAheadAssertionsInLookBehind() {
        lookAheadAssertionsInLookBehind = true;
    }
}
