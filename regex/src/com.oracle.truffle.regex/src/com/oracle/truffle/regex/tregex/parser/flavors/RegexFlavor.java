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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;

import java.util.function.BiPredicate;

/**
 * An implementation of a dialect (flavor) of regular expressions other than ECMAScript. It provides
 * support for validating and parsing (building the AST) of regular expressions.
 */
public abstract class RegexFlavor {

    protected static final int BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL = 1 << 0;
    protected static final int EMPTY_CHECKS_MONITOR_CAPTURE_GROUPS = 1 << 1;
    protected static final int NESTED_CAPTURE_GROUPS_KEPT_ON_LOOP_REENTRY = 1 << 2;
    protected static final int FAILING_EMPTY_CHECKS_DONT_BACKTRACK = 1 << 3;
    protected static final int USES_LAST_GROUP_RESULT_FIELD = 1 << 4;
    protected static final int LOOKBEHINDS_RUN_LEFT_TO_RIGHT = 1 << 5;
    protected static final int NEEDS_GROUP_START_POSITIONS = 1 << 6;
    protected static final int HAS_CONDITIONAL_BACKREFERENCES = 1 << 7;
    protected static final int SUPPORTS_RECURSIVE_BACKREFERENCES = 1 << 8;
    protected static final int EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS = 1 << 9;

    private final int traits;

    protected RegexFlavor(int traits) {
        this.traits = traits;
    }

    public abstract RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer);

    public abstract RegexValidator createValidator(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer);

    public abstract BiPredicate<Integer, Integer> getEqualsIgnoreCasePredicate(RegexAST ast);

    private boolean hasTrait(int traitMask) {
        return (traits & traitMask) != 0;
    }

    public boolean backreferencesToUnmatchedGroupsFail() {
        return hasTrait(BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL);
    }

    public boolean supportsRecursiveBackreferences() {
        return hasTrait(SUPPORTS_RECURSIVE_BACKREFERENCES);
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

    public boolean needsGroupStartPositions() {
        return hasTrait(NEEDS_GROUP_START_POSITIONS);
    }

    public boolean hasConditionalBackReferences() {
        return hasTrait(HAS_CONDITIONAL_BACKREFERENCES);
    }

    public boolean matchesTransitionsStepByStep() {
        return emptyChecksMonitorCaptureGroups() || hasConditionalBackReferences() || failingEmptyChecksDontBacktrack();
    }

    /**
     * Regex flavors with this feature perform on empty-check on all iterations of a loop, including
     * on mandatory iterations. As such, a loop can terminate before having been executed the
     * required number of times.
     */
    public boolean emptyChecksOnMandatoryLoopIterations() {
        return hasTrait(EMPTY_CHECKS_ON_MANDATORY_LOOP_ITERATIONS);
    }
}
