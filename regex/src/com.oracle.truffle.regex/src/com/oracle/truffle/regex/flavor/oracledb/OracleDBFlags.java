/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.flavor.oracledb;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * An immutable representation of a set of OracleDB regular expression flags (the
 * {@code match_param} parameter).
 */
@ExportLibrary(InteropLibrary.class)
public final class OracleDBFlags extends AbstractConstantKeysObject {

    private static final String PROP_IGNORE_CASE = "i";
    private static final String PROP_FORCE_ACCENT_AND_CASE_SENSITIVE = "c";
    private static final String PROP_DOTALL = "n";
    private static final String PROP_MULTILINE = "m";
    private static final String PROP_VERBOSE = "x";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(
                    PROP_IGNORE_CASE,
                    PROP_FORCE_ACCENT_AND_CASE_SENSITIVE,
                    PROP_DOTALL,
                    PROP_MULTILINE,
                    PROP_VERBOSE);

    private final int value;

    private static final int FLAG_IGNORE_CASE = 1;
    private static final int FLAG_FORCE_ACCENT_AND_CASE_SENSITIVE = 1 << 1;
    private static final int FLAG_DOT_ALL = 1 << 2;
    private static final int FLAG_MULTILINE = 1 << 3;
    private static final int FLAG_IGNORE_WHITESPACE = 1 << 4;

    private static final char[] ALL_FLAGS = {'i', 'c', 'n', 'm', 'x'};

    public static final OracleDBFlags EMPTY_INSTANCE = new OracleDBFlags(0);

    private OracleDBFlags(int value) {
        this.value = value;
    }

    public static OracleDBFlags parseFlags(RegexSource source) throws RegexSyntaxException {
        String flagsStr = source.getFlags();
        int flags = 0;
        for (int i = 0; i < flagsStr.length(); i++) {
            char ch = flagsStr.charAt(i);
            switch (ch) {
                case 'i':
                    flags |= FLAG_IGNORE_CASE;
                    /*
                     * From OracleDB documentation: If the value of match_param contains multiple
                     * contradictory characters, then Oracle uses the last character. For example,
                     * if you specify 'ic', then Oracle uses case-sensitive and accent-sensitive
                     * matching.
                     */
                    flags &= ~FLAG_FORCE_ACCENT_AND_CASE_SENSITIVE;
                    break;
                case 'c':
                    flags |= FLAG_FORCE_ACCENT_AND_CASE_SENSITIVE;
                    /*
                     * From OracleDB documentation: If the value of match_param contains multiple
                     * contradictory characters, then Oracle uses the last character. For example,
                     * if you specify 'ic', then Oracle uses case-sensitive and accent-sensitive
                     * matching.
                     */
                    flags &= ~FLAG_IGNORE_CASE;
                    break;
                case 'n':
                    flags |= FLAG_DOT_ALL;
                    break;
                case 'm':
                    flags |= FLAG_MULTILINE;
                    break;
                case 'x':
                    flags |= FLAG_IGNORE_WHITESPACE;
                    break;
                default:
                    throw RegexSyntaxException.createFlags(source, OracleDBErrorMessages.UNSUPPORTED_FLAG, i);
            }
        }
        return flags == 0 ? EMPTY_INSTANCE : new OracleDBFlags(flags);
    }

    private boolean hasFlag(int flag) {
        return (value & flag) != 0;
    }

    public boolean isIgnoreCase() {
        return hasFlag(FLAG_IGNORE_CASE);
    }

    public boolean isForceAccentAndCaseSensitive() {
        return hasFlag(FLAG_FORCE_ACCENT_AND_CASE_SENSITIVE);
    }

    public boolean isDotAll() {
        return hasFlag(FLAG_DOT_ALL);
    }

    public boolean isMultiline() {
        return hasFlag(FLAG_MULTILINE);
    }

    public boolean isIgnoreWhitespace() {
        return hasFlag(FLAG_IGNORE_WHITESPACE);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OracleDBFlags && this.value == ((OracleDBFlags) other).value;
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
        for (int i = 0; i < ALL_FLAGS.length; i++) {
            if (hasFlag(1 << i)) {
                out[iOut++] = ALL_FLAGS[i];
            }
        }
        return new String(out);
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "TRegexOracleDBFlags{flags=" + this + '}';
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_IGNORE_CASE:
            case PROP_FORCE_ACCENT_AND_CASE_SENSITIVE:
            case PROP_DOTALL:
            case PROP_MULTILINE:
            case PROP_VERBOSE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_IGNORE_CASE:
                return isIgnoreCase();
            case PROP_FORCE_ACCENT_AND_CASE_SENSITIVE:
                return isForceAccentAndCaseSensitive();
            case PROP_DOTALL:
                return isDotAll();
            case PROP_MULTILINE:
                return isMultiline();
            case PROP_VERBOSE:
                return isIgnoreWhitespace();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
