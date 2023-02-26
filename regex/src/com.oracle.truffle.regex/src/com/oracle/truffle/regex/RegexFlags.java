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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.errors.ErrorMessages;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

@ExportLibrary(InteropLibrary.class)
public final class RegexFlags extends AbstractConstantKeysObject implements JsonConvertible {

    private static final String PROP_SOURCE = "source";
    private static final String PROP_IGNORE_CASE = "ignoreCase";
    private static final String PROP_MULTILINE = "multiline";
    private static final String PROP_STICKY = "sticky";
    private static final String PROP_GLOBAL = "global";
    private static final String PROP_UNICODE = "unicode";
    private static final String PROP_DOT_ALL = "dotAll";
    private static final String PROP_HAS_INDICES = "hasIndices";

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(
                    PROP_SOURCE,
                    PROP_IGNORE_CASE,
                    PROP_MULTILINE,
                    PROP_STICKY,
                    PROP_GLOBAL,
                    PROP_UNICODE,
                    PROP_DOT_ALL,
                    PROP_HAS_INDICES);

    private static final int NONE = 0;
    private static final int IGNORE_CASE = 1;
    private static final int MULTILINE = 1 << 1;
    private static final int STICKY = 1 << 2;
    private static final int GLOBAL = 1 << 3;
    private static final int UNICODE = 1 << 4;
    private static final int DOT_ALL = 1 << 5;
    private static final int HAS_INDICES = 1 << 6;

    public static final RegexFlags DEFAULT = new RegexFlags("", NONE);

    private final String source;
    private final int value;

    private RegexFlags(String source, int value) {
        this.source = source;
        this.value = value;
    }

    public static Builder builder() {
        return new Builder();
    }

    @TruffleBoundary
    public static RegexFlags parseFlags(RegexSource source) throws RegexSyntaxException {
        String flagsStr = source.getFlags();
        if (flagsStr.isEmpty()) {
            return DEFAULT;
        }
        int flags = NONE;
        for (int i = 0; i < flagsStr.length(); i++) {
            char ch = flagsStr.charAt(i);
            switch (ch) {
                case 'i':
                    flags = addFlag(source, flags, i, IGNORE_CASE);
                    break;
                case 'm':
                    flags = addFlag(source, flags, i, MULTILINE);
                    break;
                case 'g':
                    flags = addFlag(source, flags, i, GLOBAL);
                    break;
                case 'y':
                    flags = addFlag(source, flags, i, STICKY);
                    break;
                case 'u':
                    flags = addFlag(source, flags, i, UNICODE);
                    break;
                case 's':
                    flags = addFlag(source, flags, i, DOT_ALL);
                    break;
                case 'd':
                    flags = addFlag(source, flags, i, HAS_INDICES);
                    break;
                default:
                    throw RegexSyntaxException.createFlags(source, ErrorMessages.UNSUPPORTED_FLAG, i);
            }
        }
        return new RegexFlags(flagsStr, flags);
    }

    private static int addFlag(RegexSource source, int flags, int i, int flag) {
        if ((flags & flag) != 0) {
            throw RegexSyntaxException.createFlags(source, ErrorMessages.REPEATED_FLAG, i);
        }
        return flags | flag;
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

    public boolean hasIndices() {
        return isSet(HAS_INDICES);
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
        return Json.obj(Json.prop(PROP_IGNORE_CASE, isIgnoreCase()),
                        Json.prop(PROP_MULTILINE, isMultiline()),
                        Json.prop(PROP_GLOBAL, isGlobal()),
                        Json.prop(PROP_STICKY, isSticky()),
                        Json.prop(PROP_UNICODE, isUnicode()),
                        Json.prop(PROP_DOT_ALL, isDotAll()),
                        Json.prop(PROP_HAS_INDICES, hasIndices()));
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_SOURCE:
            case PROP_IGNORE_CASE:
            case PROP_MULTILINE:
            case PROP_STICKY:
            case PROP_GLOBAL:
            case PROP_UNICODE:
            case PROP_DOT_ALL:
            case PROP_HAS_INDICES:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_SOURCE:
                return getSource();
            case PROP_IGNORE_CASE:
                return isIgnoreCase();
            case PROP_MULTILINE:
                return isMultiline();
            case PROP_STICKY:
                return isSticky();
            case PROP_GLOBAL:
                return isGlobal();
            case PROP_UNICODE:
                return isUnicode();
            case PROP_DOT_ALL:
                return isDotAll();
            case PROP_HAS_INDICES:
                return hasIndices();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "TRegexJSFlags{flags=" + toString() + '}';
    }

    public static final class Builder {

        private int value;

        private Builder() {
        }

        public Builder ignoreCase(boolean enabled) {
            updateFlag(enabled, IGNORE_CASE);
            return this;
        }

        public Builder multiline(boolean enabled) {
            updateFlag(enabled, MULTILINE);
            return this;
        }

        public Builder sticky(boolean enabled) {
            updateFlag(enabled, STICKY);
            return this;
        }

        public Builder global(boolean enabled) {
            updateFlag(enabled, GLOBAL);
            return this;
        }

        public Builder unicode(boolean enabled) {
            updateFlag(enabled, UNICODE);
            return this;
        }

        public Builder dotAll(boolean enabled) {
            updateFlag(enabled, DOT_ALL);
            return this;
        }

        public Builder hasIndices(boolean enabled) {
            updateFlag(enabled, HAS_INDICES);
            return this;
        }

        @TruffleBoundary
        public RegexFlags build() {
            return new RegexFlags(generateSource(), this.value);
        }

        private void updateFlag(boolean enabled, int bitMask) {
            if (enabled) {
                this.value |= bitMask;
            } else {
                this.value &= ~bitMask;
            }
        }

        private boolean isSet(int flag) {
            return (value & flag) != NONE;
        }

        private String generateSource() {
            StringBuilder sb = new StringBuilder(7);
            if (isSet(IGNORE_CASE)) {
                sb.append("i");
            }
            if (isSet(MULTILINE)) {
                sb.append("m");
            }
            if (isSet(STICKY)) {
                sb.append("y");
            }
            if (isSet(GLOBAL)) {
                sb.append("g");
            }
            if (isSet(UNICODE)) {
                sb.append("u");
            }
            if (isSet(DOT_ALL)) {
                sb.append("s");
            }
            if (isSet(HAS_INDICES)) {
                sb.append("d");
            }
            return sb.toString();
        }
    }
}
