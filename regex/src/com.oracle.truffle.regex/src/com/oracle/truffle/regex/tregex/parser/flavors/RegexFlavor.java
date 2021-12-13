/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.regex.RegexSource;

/**
 * An implementation of a dialect (flavor) of regular expressions other than ECMAScript. The goal of
 * a flavor implementation is to translate regular expressions written in one flavor of regex (e.g.
 * Python) into equivalent regexes in ECMAScript.
 */
public abstract class RegexFlavor {

    protected static final int BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL = 1 << 0;
    protected static final int EMPTY_CHECKS_MONITOR_CAPTURE_GROUPS = 1 << 1;
    protected static final int NESTED_CAPTURE_GROUPS_KEPT_ON_LOOP_REENTRY = 1 << 2;
    protected static final int FAILING_EMPTY_CHECKS_DONT_BACKTRACK = 1 << 3;
    protected static final int USES_LAST_GROUP_RESULT_FIELD = 1 << 4;
    protected static final int LOOKBEHINDS_RUN_LEFT_TO_RIGHT = 1 << 5;

    private final int traits;

    protected RegexFlavor(int traits) {
        this.traits = traits;
    }

    /**
     * Given a {@link RegexSource}, returns a {@link RegexFlavorProcessor} that can be used to parse
     * and translate the flavored regex into an ECMAScript regex.
     */
    public abstract RegexFlavorProcessor forRegex(RegexSource source);

    private boolean hasTrait(int traitMask) {
        return (traits & traitMask) != 0;
    }

    public boolean backreferencesToUnmatchedGroupsFail() {
        return hasTrait(BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL);
    }

    public boolean emptyChecksMonitorCaptureGroups() {
        return hasTrait(EMPTY_CHECKS_MONITOR_CAPTURE_GROUPS);
    }

    public boolean nestedCaptureGroupsKeptOnLoopReentry() {
        return hasTrait(NESTED_CAPTURE_GROUPS_KEPT_ON_LOOP_REENTRY);
    }

    public boolean failingEmptyChecksDontBacktrack() {
        return hasTrait(FAILING_EMPTY_CHECKS_DONT_BACKTRACK);
    }

    public boolean canHaveEmptyLoopIterations() {
        return emptyChecksMonitorCaptureGroups() || failingEmptyChecksDontBacktrack();
    }

    public boolean usesLastGroupResultField() {
        return hasTrait(USES_LAST_GROUP_RESULT_FIELD);
    }

    public boolean lookBehindsRunLeftToRight() {
        return hasTrait(LOOKBEHINDS_RUN_LEFT_TO_RIGHT);
    }
}
