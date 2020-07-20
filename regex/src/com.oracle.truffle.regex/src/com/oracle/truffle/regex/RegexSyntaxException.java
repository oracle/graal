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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class RegexSyntaxException extends RuntimeException implements TruffleException {

    private static final String template = "Invalid regular expression: /%s/%s: %s";
    private static final String templateNoFlags = "Invalid regular expression: %s: %s";
    private static final String templatePosition = "Invalid regular expression: /%s/%s:%d: %s";

    private final String reason;
    private final RegexSource regexSrc;
    private final int position;

    public RegexSyntaxException(String msg) {
        super(msg);
        reason = msg;
        regexSrc = null;
        position = -1;
    }

    @TruffleBoundary
    public RegexSyntaxException(String pattern, String msg) {
        this(String.format(templateNoFlags, pattern, msg), msg, null);
    }

    @TruffleBoundary
    public RegexSyntaxException(RegexSource source, String msg) {
        this(String.format(template, source.getPattern(), source.getFlags(), msg), msg, source);
    }

    @TruffleBoundary
    public RegexSyntaxException(RegexSource source, String msg, int position) {
        this(String.format(templatePosition, source.getPattern(), source.getFlags(), position, msg), msg, source, position);
    }

    private RegexSyntaxException(String exceptionMsg, String reason, RegexSource regexSrc) {
        this(exceptionMsg, reason, regexSrc, -1);
    }

    private RegexSyntaxException(String exceptionMsg, String reason, RegexSource regexSrc, int position) {
        super(exceptionMsg);
        this.reason = reason;
        this.regexSrc = regexSrc;
        this.position = position;
    }

    @Override
    public boolean isSyntaxError() {
        return true;
    }

    @Override
    public Node getLocation() {
        return null;
    }

    public String getReason() {
        return reason;
    }

    public RegexSource getRegex() {
        return regexSrc;
    }

    public Integer getPosition() {
        return position;
    }

    private static final long serialVersionUID = 1L;

}
