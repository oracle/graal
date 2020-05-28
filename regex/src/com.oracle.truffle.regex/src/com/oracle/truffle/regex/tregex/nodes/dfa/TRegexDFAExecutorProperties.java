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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class TRegexDFAExecutorProperties {

    private final boolean forward;
    private final boolean searching;
    private final boolean genericCG;
    private final boolean allowSimpleCG;
    @CompilationFinal private boolean simpleCG;
    @CompilationFinal private boolean simpleCGMustCopy;
    private final boolean regressionTestMode;
    private final int minResultLength;

    public TRegexDFAExecutorProperties(
                    boolean forward,
                    boolean searching,
                    boolean genericCG,
                    boolean allowSimpleCG,
                    boolean regressionTestMode,
                    int minResultLength) {
        this.forward = forward;
        this.searching = searching;
        this.genericCG = genericCG;
        this.allowSimpleCG = allowSimpleCG;
        this.regressionTestMode = regressionTestMode;
        this.minResultLength = minResultLength;
    }

    public boolean isForward() {
        return forward;
    }

    public boolean isBackward() {
        return !forward;
    }

    public boolean isSearching() {
        return searching;
    }

    /**
     * True if the DFA executor must track capture groups via {@link CGTrackingDFAStateNode}s.
     */
    public boolean isGenericCG() {
        return genericCG;
    }

    public boolean isAllowSimpleCG() {
        return allowSimpleCG;
    }

    /**
     * True if the DFA executor tracks capture groups via {@link DFASimpleCG}.
     */
    public boolean isSimpleCG() {
        return simpleCG;
    }

    public void setSimpleCG(boolean simpleCG) {
        this.simpleCG = simpleCG;
    }

    /**
     * True if the DFA executor tracks capture groups via {@link DFASimpleCG}, but must save the
     * current result every time a final state is reached. This is necessary if any non-final states
     * are reachable from a final state in the DFA.
     */
    public boolean isSimpleCGMustCopy() {
        return simpleCGMustCopy;
    }

    public void setSimpleCGMustCopy(boolean simpleCGMustCopy) {
        this.simpleCGMustCopy = simpleCGMustCopy;
    }

    public boolean isRegressionTestMode() {
        return regressionTestMode;
    }

    public int getMinResultLength() {
        return minResultLength;
    }
}
