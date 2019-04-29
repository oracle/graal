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

    public static final RegexFlags DEFAULT = new RegexFlags("", false, false, false, false, false, false);

    private final String source;
    private final int value;

    private RegexFlags(String source, boolean ignoreCase, boolean multiline, boolean global, boolean sticky, boolean unicode, boolean dotAll) {
        this.source = source;
        this.value = (ignoreCase ? IGNORE_CASE : NONE) | (multiline ? MULTILINE : NONE) | (sticky ? STICKY : NONE) | (global ? GLOBAL : NONE) | (unicode ? UNICODE : NONE) | (dotAll ? DOT_ALL : NONE);
    }

    @TruffleBoundary
    public static RegexFlags parseFlags(String source) throws RegexSyntaxException {
        if (source.isEmpty()) {
            return DEFAULT;
        }
        boolean ignoreCase = false;
        boolean multiline = false;
        boolean global = false;
        boolean sticky = false;
        boolean unicode = false;
        boolean dotAll = false;

        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            boolean repeated;
            switch (ch) {
                case 'i':
                    repeated = ignoreCase;
                    ignoreCase = true;
                    break;
                case 'm':
                    repeated = multiline;
                    multiline = true;
                    break;
                case 'g':
                    repeated = global;
                    global = true;
                    break;
                case 'y':
                    repeated = sticky;
                    sticky = true;
                    break;
                case 'u':
                    repeated = unicode;
                    unicode = true;
                    break;
                case 's':
                    repeated = dotAll;
                    dotAll = true;
                    break;
                default:
                    throw new RegexSyntaxException(source, "unsupported regex flag: " + ch);
            }
            if (repeated) {
                throw new RegexSyntaxException(source, "repeated regex flag: " + ch);
            }
        }
        return new RegexFlags(source, ignoreCase, multiline, global, sticky, unicode, dotAll);
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
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
