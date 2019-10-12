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
package com.oracle.truffle.regex.tregex.parser;

/**
 * This class describes the features found in a regular expression which are salient for determining
 * whether or not the regular expression can be supported by our regex engines.
 * <p>
 * Instances of this class are created and populated by calls to {@link RegexValidator#validate()}.
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
