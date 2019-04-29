/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("serial")
public class UnsupportedRegexException extends RuntimeException implements TruffleException {

    private String reason;
    private RegexSource regexSrc;

    public UnsupportedRegexException(String reason) {
        super();
        this.reason = reason;
    }

    public UnsupportedRegexException(String reason, Throwable cause) {
        super(cause);
        this.reason = reason;
    }

    public UnsupportedRegexException(String reason, RegexSource regexSrc) {
        this(reason);
        this.regexSrc = regexSrc;
    }

    public RegexSource getRegex() {
        return regexSrc;
    }

    public void setRegex(RegexSource regexSrc) {
        this.regexSrc = regexSrc;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Unsupported regular expression");
        if (regexSrc != null) {
            sb.append(" /");
            sb.append(regexSrc.getPattern());
            sb.append("/");
            sb.append(regexSrc.getFlags());
        }
        if (reason != null) {
            sb.append(": ");
            sb.append(reason);
        }
        return sb.toString();
    }

    /**
     * For performance reasons, this exception does not record any stack trace information.
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public boolean isSyntaxError() {
        return true;
    }

    @Override
    public Node getLocation() {
        return null;
    }
}
