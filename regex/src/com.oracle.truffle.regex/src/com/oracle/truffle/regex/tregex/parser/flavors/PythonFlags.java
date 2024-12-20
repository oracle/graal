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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.util.TBitSet;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * An immutable representation of a set of Python regular expression flags.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonFlags extends AbstractConstantKeysObject {

    private static final String PROP_ASCII = "ASCII";
    private static final String PROP_DOTALL = "DOTALL";
    private static final String PROP_IGNORECASE = "IGNORECASE";
    private static final String PROP_LOCALE = "LOCALE";
    private static final String PROP_MULTILINE = "MULTILINE";
    private static final String PROP_UNICODE = "UNICODE";
    private static final String PROP_VERBOSE = "VERBOSE";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(
                    PROP_ASCII,
                    PROP_DOTALL,
                    PROP_IGNORECASE,
                    PROP_LOCALE,
                    PROP_MULTILINE,
                    PROP_UNICODE,
                    PROP_VERBOSE);

    private final int value;

    private static final TBitSet ALL_FLAG_CHARS = TBitSet.valueOf('L', 'a', 'i', 'm', 's', 'u', 'x');
    private static final TBitSet TYPE_FLAG_CHARS = TBitSet.valueOf('L', 'a', 'u');

    private static final String FLAGS = "iLmsxau";

    private static final int FLAG_IGNORE_CASE = 1;
    private static final int FLAG_LOCALE = 1 << 1;
    private static final int FLAG_MULTILINE = 1 << 2;
    private static final int FLAG_DOT_ALL = 1 << 3;
    private static final int FLAG_VERBOSE = 1 << 4;
    private static final int FLAG_ASCII = 1 << 5;
    private static final int FLAG_UNICODE = 1 << 6;

    private static final int[] FLAG_LOOKUP = {
                    FLAG_ASCII, 0, 0, 0, 0, 0, 0, 0, FLAG_IGNORE_CASE, 0, 0, FLAG_LOCALE, FLAG_MULTILINE, 0, 0, 0,
                    0, 0, FLAG_DOT_ALL, 0, FLAG_UNICODE, 0, 0, FLAG_VERBOSE
    };

    private static final int TYPE_FLAGS = FLAG_LOCALE | FLAG_ASCII | FLAG_UNICODE;

    public static final PythonFlags EMPTY_INSTANCE = new PythonFlags("");
    public static final PythonFlags TYPE_FLAGS_INSTANCE = new PythonFlags(TYPE_FLAGS);

    public PythonFlags(String source) {
        int bits = 0;
        for (int i = 0; i < source.length(); i++) {
            bits |= maskForFlag(source.charAt(i));
        }
        this.value = bits;
    }

    private PythonFlags(int value) {
        this.value = value;
    }

    private static int maskForFlag(int flagChar) {
        assert ALL_FLAG_CHARS.get(flagChar);
        // flagChar must be one of [A-Xa-x].
        // (flagChar | 0x20) effectively downcases the character and allows us to use an array of
        // just 24 integers for the lookup.
        return FLAG_LOOKUP[(flagChar | 0x20) - 'a'];
    }

    private boolean hasFlag(int flag) {
        return (value & flag) != 0;
    }

    public boolean isIgnoreCase() {
        return hasFlag(FLAG_IGNORE_CASE);
    }

    public boolean isLocale() {
        return hasFlag(FLAG_LOCALE);
    }

    public boolean isMultiLine() {
        return hasFlag(FLAG_MULTILINE);
    }

    public boolean isDotAll() {
        return hasFlag(FLAG_DOT_ALL);
    }

    public boolean isVerbose() {
        return hasFlag(FLAG_VERBOSE);
    }

    public boolean isAscii() {
        return hasFlag(FLAG_ASCII);
    }

    public boolean isUnicodeExplicitlySet() {
        return hasFlag(FLAG_UNICODE);
    }

    /**
     * Returns {@code true} if the Unicode flag is set or if it would be set by default.
     */
    public boolean isUnicode(PythonREMode mode) {
        switch (mode) {
            case Str:
                return isUnicodeExplicitlySet() || !isAscii();
            case Bytes:
                return isUnicodeExplicitlySet();
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public PythonFlags addFlag(int flagChar) {
        return new PythonFlags(this.value | maskForFlag(flagChar));
    }

    public PythonFlags addFlags(PythonFlags otherFlags) {
        return new PythonFlags(this.value | otherFlags.value);
    }

    public PythonFlags delFlags(PythonFlags otherFlags) {
        return new PythonFlags(this.value & ~otherFlags.value);
    }

    /**
     * Verifies that there is at most one type flag and that the type flag is compatible with the
     * chosen regular expression mode. If a string pattern is used, ensures that the unicode flag is
     * set by default.
     */
    public PythonFlags fixFlags(RegexSource source, PythonREMode mode) {
        switch (mode) {
            case Str:
                if (isLocale()) {
                    throw RegexSyntaxException.createFlags(source, "cannot use LOCALE flag with a str pattern");
                }
                if (isAscii() && isUnicodeExplicitlySet()) {
                    throw RegexSyntaxException.createFlags(source, "ASCII and UNICODE flags are incompatible");
                }
                if (!isAscii() && !isUnicodeExplicitlySet()) {
                    return new PythonFlags(value | FLAG_UNICODE);
                } else {
                    return this;
                }
            case Bytes:
                if (isUnicodeExplicitlySet()) {
                    throw RegexSyntaxException.createFlags(source, "cannot use UNICODE flag with a bytes pattern");
                }
                if (isAscii() && isLocale()) {
                    throw RegexSyntaxException.createFlags(source, "ASCII and LOCALE flags are incompatible");
                }
                return this;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public static boolean isValidFlagChar(int candidateChar) {
        return ALL_FLAG_CHARS.get(candidateChar);
    }

    public static boolean isTypeFlagChar(int candidateChar) {
        return TYPE_FLAG_CHARS.get(candidateChar);
    }

    public int numberOfTypeFlags() {
        return Integer.bitCount(value & TYPE_FLAGS);
    }

    public boolean overlaps(PythonFlags otherFlags) {
        return (this.value & otherFlags.value) != 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PythonFlags && this.value == ((PythonFlags) other).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        char[] out = new char[Integer.bitCount(value)];
        int iOut = 0;
        for (int i = 0; i < FLAGS.length(); i++) {
            char flag = FLAGS.charAt(i);
            if (hasFlag(maskForFlag(flag))) {
                out[iOut++] = flag;
            }
        }
        return new String(out);
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "TRegexPythonFlags{flags=" + toString() + '}';
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_ASCII:
            case PROP_DOTALL:
            case PROP_IGNORECASE:
            case PROP_LOCALE:
            case PROP_MULTILINE:
            case PROP_UNICODE:
            case PROP_VERBOSE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_ASCII:
                return isAscii();
            case PROP_DOTALL:
                return isDotAll();
            case PROP_IGNORECASE:
                return isIgnoreCase();
            case PROP_LOCALE:
                return isLocale();
            case PROP_MULTILINE:
                return isMultiLine();
            case PROP_UNICODE:
                return isUnicodeExplicitlySet();
            case PROP_VERBOSE:
                return isVerbose();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
