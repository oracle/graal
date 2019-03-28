/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

public class RegexSyntaxException extends RuntimeException implements TruffleException {

    private static final String template = "Invalid regular expression: /%s/%s: %s";
    private static final String templateNoFlags = "Invalid regular expression: %s: %s";
    private static final String templatePosition = "Invalid regular expression: /%s/%s:%d: %s";

    private String reason;
    private RegexSource regexSrc;
    private int position = -1;

    public RegexSyntaxException(String msg) {
        super(msg);
    }

    @TruffleBoundary
    public RegexSyntaxException(String pattern, String msg) {
        super(String.format(templateNoFlags, pattern, msg));
        this.reason = msg;
        this.regexSrc = new RegexSource(pattern);
    }

    @TruffleBoundary
    public RegexSyntaxException(String pattern, String flags, String msg) {
        super(String.format(template, pattern, flags, msg));
        this.reason = msg;
        this.regexSrc = new RegexSource(pattern, flags);
    }

    @TruffleBoundary
    public RegexSyntaxException(String pattern, String flags, String msg, int position) {
        super(String.format(templatePosition, pattern, flags, position, msg));
        this.reason = msg;
        this.regexSrc = new RegexSource(pattern, flags);
        this.position = position;
    }

    @TruffleBoundary
    public RegexSyntaxException(String pattern, String flags, String msg, Throwable ex) {
        super(String.format(template, pattern, flags, msg), ex);
        this.reason = msg;
        this.regexSrc = new RegexSource(pattern, flags);
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
