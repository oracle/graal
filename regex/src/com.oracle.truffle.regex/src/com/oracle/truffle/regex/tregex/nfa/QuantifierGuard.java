/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;

public final class QuantifierGuard {

    public static final QuantifierGuard[] NO_GUARDS = {};

    public enum Kind {
        enter,
        exit,
        enterZeroWidth,
        exitZeroWidth
    }

    private final Kind kind;
    private final int quantifierIndex;
    private final int threshold;

    private QuantifierGuard(Kind kind, int quantifierIndex, int threshold) {
        this.kind = kind;
        this.quantifierIndex = quantifierIndex;
        this.threshold = threshold;
        assert quantifierIndex >= 0;
    }

    public static QuantifierGuard create(Quantifier quantifier, boolean enter) {
        return enter ? createEnter(quantifier) : createExit(quantifier);
    }

    public static QuantifierGuard createEnter(Quantifier quantifier) {
        return new QuantifierGuard(Kind.enter, quantifier.getIndex(), quantifier.getMax());
    }

    public static QuantifierGuard createExit(Quantifier quantifier) {
        return new QuantifierGuard(Kind.exit, quantifier.getIndex(), quantifier.getMin());
    }

    public static QuantifierGuard createZeroWidth(Quantifier quantifier, boolean enter) {
        return enter ? createEnterZeroWidth(quantifier) : createExitZeroWidth(quantifier);
    }

    public static QuantifierGuard createEnterZeroWidth(Quantifier quantifier) {
        return new QuantifierGuard(Kind.enterZeroWidth, quantifier.getZeroWidthIndex(), -1);
    }

    public static QuantifierGuard createExitZeroWidth(Quantifier quantifier) {
        return new QuantifierGuard(Kind.exitZeroWidth, quantifier.getZeroWidthIndex(), -1);
    }

    public Kind getKind() {
        return kind;
    }

    public int getQuantifierIndex() {
        return quantifierIndex;
    }

    public int getThreshold() {
        return threshold;
    }
}
