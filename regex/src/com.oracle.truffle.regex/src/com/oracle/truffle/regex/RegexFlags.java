/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.TBitSet;
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
    private static final String PROP_UNICODE_SETS = "unicodeSets";

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(
                    PROP_SOURCE,
                    PROP_IGNORE_CASE,
                    PROP_MULTILINE,
                    PROP_STICKY,
                    PROP_GLOBAL,
                    PROP_UNICODE,
                    PROP_DOT_ALL,
                    PROP_HAS_INDICES,
                    PROP_UNICODE_SETS);

    private static final TBitSet ALL_FLAG_CHARS = TBitSet.valueOf('d', 'g', 'i', 'm', 's', 'u', 'v', 'y');
    private static final TBitSet LOCAL_FLAG_CHARS = TBitSet.valueOf('i', 'm', 's');

    private static final int NONE = 0;
    private static final int IGNORE_CASE = 1;
    private static final int MULTILINE = 1 << 1;
    private static final int STICKY = 1 << 2;
    private static final int GLOBAL = 1 << 3;
    private static final int UNICODE = 1 << 4;
    private static final int DOT_ALL = 1 << 5;
    private static final int HAS_INDICES = 1 << 6;
    private static final int UNICODE_SETS = 1 << 7;

    private static final int[] FLAG_LOOKUP = {
                    HAS_INDICES, 0, 0, GLOBAL, 0, IGNORE_CASE, 0, 0, 0, MULTILINE, 0, 0, 0, 0, 0, DOT_ALL, 0, UNICODE, UNICODE_SETS, 0, 0, STICKY
    };

    public static final RegexFlags DEFAULT = new RegexFlags("", NONE);

    private final String source;
    private final int value;

    private RegexFlags(String source, int value) {
        this.source = source;
        this.value = value;
    }

    private RegexFlags(int value) {
        this.source = generateSource(value);
        this.value = value;
    }

    private static int maskForFlag(char flagChar) {
        assert ALL_FLAG_CHARS.get(flagChar);
        // flagChar must be one of [d-y].
        return FLAG_LOOKUP[flagChar - 'd'];
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
            if (!isValidFlagChar(ch)) {
                throw RegexSyntaxException.createFlags(source, JsErrorMessages.UNSUPPORTED_FLAG, i);
            }
            int flag = maskForFlag(ch);
            if ((flags & flag) != 0) {
                throw RegexSyntaxException.createFlags(source, JsErrorMessages.REPEATED_FLAG, i);
            }
            flags |= flag;
            if ((flags & (UNICODE | UNICODE_SETS)) == (UNICODE | UNICODE_SETS)) {
                throw RegexSyntaxException.createFlags(source, JsErrorMessages.BOTH_FLAGS_SET_U_V, i);
            }
        }
        return new RegexFlags(flagsStr, flags);
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

    public boolean isUnicodeSets() {
        return isSet(UNICODE_SETS);
    }

    public boolean isEitherUnicode() {
        return isSet(UNICODE | UNICODE_SETS);
    }

    public boolean isNone() {
        return value == NONE;
    }

    private boolean isSet(int flag) {
        return (value & flag) != NONE;
    }

    public static boolean isValidFlagChar(char candidateChar) {
        return ALL_FLAG_CHARS.get(candidateChar);
    }

    public static boolean isValidLocalFlagChar(char candidateChar) {
        return LOCAL_FLAG_CHARS.get(candidateChar);
    }

    public RegexFlags addNewFlagModifier(RegexSource regexSource, char flagChar) {
        int flag = maskForFlag(flagChar);
        if (isSet(flag)) {
            throw RegexSyntaxException.createFlags(regexSource, JsErrorMessages.REPEATED_FLAG_IN_MODIFIER);
        }
        return new RegexFlags(this.value | flag);
    }

    public RegexFlags addFlags(RegexFlags otherFlags) {
        return new RegexFlags(this.value | otherFlags.value);
    }

    public RegexFlags delFlags(RegexFlags otherFlags) {
        return new RegexFlags(this.value & ~otherFlags.value);
    }

    public boolean overlaps(RegexFlags otherFlags) {
        return (this.value & otherFlags.value) != 0;
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
                        Json.prop(PROP_HAS_INDICES, hasIndices()),
                        Json.prop(PROP_UNICODE_SETS, isUnicodeSets()));
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
            case PROP_UNICODE_SETS:
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
            case PROP_UNICODE_SETS:
                return isUnicodeSets();
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

    private static String generateSource(int value) {
        StringBuilder sb = new StringBuilder(8);
        if ((value & IGNORE_CASE) != 0) {
            sb.append("i");
        }
        if ((value & MULTILINE) != 0) {
            sb.append("m");
        }
        if ((value & STICKY) != 0) {
            sb.append("y");
        }
        if ((value & GLOBAL) != 0) {
            sb.append("g");
        }
        if ((value & UNICODE) != 0) {
            sb.append("u");
        }
        if ((value & DOT_ALL) != 0) {
            sb.append("s");
        }
        if ((value & HAS_INDICES) != 0) {
            sb.append("d");
        }
        if ((value & UNICODE_SETS) != 0) {
            sb.append("v");
        }
        return sb.toString();
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
            if (enabled) {
                updateFlag(false, UNICODE_SETS);
            }
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

        public Builder unicodeSets(boolean enabled) {
            if (enabled) {
                updateFlag(false, UNICODE);
            }
            updateFlag(enabled, UNICODE_SETS);
            return this;
        }

        @TruffleBoundary
        public RegexFlags build() {
            return new RegexFlags(generateSource(this.value), this.value);
        }

        private void updateFlag(boolean enabled, int bitMask) {
            if (enabled) {
                this.value |= bitMask;
            } else {
                this.value &= ~bitMask;
            }
        }
    }
}
