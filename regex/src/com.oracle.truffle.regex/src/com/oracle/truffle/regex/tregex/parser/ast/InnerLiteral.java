/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.tregex.string.AbstractString;

/**
 * Represents a literal string inside the regular expression that can be searched for before
 * starting the actual regular expression matcher.
 */
public class InnerLiteral {

    private final AbstractString literal;
    private final AbstractString mask;
    private final int maxPrefixSize;

    private final TruffleString literalTString;
    private final TruffleString.WithMask maskTString;

    public InnerLiteral(AbstractString literal, AbstractString mask, int maxPrefixSize) {
        this.literal = literal;
        this.mask = mask;
        this.maxPrefixSize = maxPrefixSize;
        this.literalTString = literal.asTString();
        this.maskTString = mask == null ? null : mask.asTStringMask(literalTString);
    }

    /**
     * The literal string.
     */
    public AbstractString getLiteral() {
        return literal;
    }

    public Object getLiteralContent(Object input) {
        boolean isTruffleString = input instanceof TruffleString;
        CompilerAsserts.partialEvaluationConstant(isTruffleString);
        return isTruffleString ? literalTString : literal.content();
    }

    /**
     * An optional mask for matching the string in ignore-case mode.
     */
    public AbstractString getMask() {
        return mask;
    }

    public Object getMaskContent(Object input) {
        boolean isTruffleString = input instanceof TruffleString;
        CompilerAsserts.partialEvaluationConstant(isTruffleString);
        return mask == null ? null : isTruffleString ? maskTString : mask.content();
    }

    /**
     * The maximum number of code points the regular expression may match before matching this
     * literal. Example: the inner literal of {@code /a?b/} is {@code "b"}, with a max prefix size
     * of {@code 1}.
     */
    public int getMaxPrefixSize() {
        return maxPrefixSize;
    }
}
