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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

public final class RegexFlags extends AbstractConstantKeysObject implements JsonConvertible {

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray("source", "ignoreCase", "multiline", "sticky", "global", "unicode", "dotAll");

    private static final int NONE = 0;
    private static final int IGNORE_CASE = 1;
    private static final int MULTILINE = 1 << 1;
    private static final int STICKY = 1 << 2;
    private static final int GLOBAL = 1 << 3;
    private static final int UNICODE = 1 << 4;
    private static final int DOT_ALL = 1 << 5;

    public static final RegexFlags DEFAULT = new RegexFlags("", NONE);

    private final String source;
    private final int value;

    private RegexFlags(String source, int value) {
        this.source = source;
        this.value = value;
    }

    @TruffleBoundary
    public static RegexFlags parseFlags(String source) throws RegexSyntaxException {
        if (source.isEmpty()) {
            return DEFAULT;
        }
        int flags = NONE;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            int repeated = NONE;
            switch (ch) {
                case 'i':
                    repeated = flags & IGNORE_CASE;
                    flags |= IGNORE_CASE;
                    break;
                case 'm':
                    repeated = flags & MULTILINE;
                    flags |= MULTILINE;
                    break;
                case 'g':
                    repeated = flags & GLOBAL;
                    flags |= GLOBAL;
                    break;
                case 'y':
                    repeated = flags & STICKY;
                    flags |= STICKY;
                    break;
                case 'u':
                    repeated = flags & UNICODE;
                    flags |= UNICODE;
                    break;
                case 's':
                    repeated = flags & DOT_ALL;
                    flags |= DOT_ALL;
                    break;
                default:
                    throw new RegexSyntaxException(source, "unsupported regex flag: " + ch);
            }
            if (repeated != 0) {
                throw new RegexSyntaxException(source, "repeated regex flag: " + ch);
            }
        }
        return new RegexFlags(source, flags);
    }

    public String getSource() {
        return source;
    }

    public boolean isIgnoreCase() {
        return isSet(IGNORE_CASE);
    }

    public boolean isMultiline() {
        return isSet(MULTILINE);
    }

    public boolean isSticky() {
        return isSet(STICKY);
    }

    public boolean isGlobal() {
        return isSet(GLOBAL);
    }

    public boolean isUnicode() {
        return isSet(UNICODE);
    }

    public boolean isDotAll() {
        return isSet(DOT_ALL);
    }

    public boolean isNone() {
        return value == NONE;
    }

    private boolean isSet(int flag) {
        return (value & flag) != NONE;
    }

    @Override
    public String toString() {
        return source;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof RegexFlags && value == ((RegexFlags) obj).value;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("ignoreCase", isIgnoreCase()),
                        Json.prop("multiline", isMultiline()),
                        Json.prop("global", isGlobal()),
                        Json.prop("sticky", isSticky()),
                        Json.prop("unicode", isUnicode()),
                        Json.prop("dotAll", isDotAll()));
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case "source":
                return getSource();
            case "ignoreCase":
                return isIgnoreCase();
            case "multiline":
                return isMultiline();
            case "sticky":
                return isSticky();
            case "global":
                return isGlobal();
            case "unicode":
                return isUnicode();
            case "dotAll":
                return isDotAll();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
