/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors.java;

import java.util.regex.Pattern;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.util.TBitSet;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * An immutable representation of a set of java.util.Pattern regular expression flags.
 */
@ExportLibrary(InteropLibrary.class)
public final class JavaFlags extends AbstractConstantKeysObject {

    private static final String PROP_CANON_EQ = "CANON_EQ";
    private static final String PROP_UNICODE_CHARACTER_CLASS = "UNICODE_CHARACTER_CLASS";
    private static final String PROP_UNIX_LINES = "UNIX_LINES";
    private static final String PROP_CASE_INSENSITIVE = "CASE_INSENSITIVE";
    private static final String PROP_MULTILINE = "MULTILINE";
    private static final String PROP_DOTALL = "DOTALL";
    private static final String PROP_UNICODE_CASE = "UNICODE_CASE";
    private static final String PROP_COMMENTS = "COMMENTS";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(
                    PROP_CANON_EQ,
                    PROP_UNICODE_CHARACTER_CLASS,
                    PROP_UNIX_LINES,
                    PROP_CASE_INSENSITIVE,
                    PROP_MULTILINE,
                    PROP_DOTALL,
                    PROP_UNICODE_CASE,
                    PROP_COMMENTS);
    private static final TBitSet FLAGS = TBitSet.valueOf('U', 'c', 'd', 'i', 'm', 's', 'u', 'x');
    private final int value;

    public JavaFlags(int bits) {
        this.value = bits;
    }

    public static JavaFlags parseFlags(String source) {
        int flags = 0;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            switch (ch) {
                case 'c':
                    flags |= Pattern.CANON_EQ;
                    break;
                case 'U':
                    flags |= Pattern.UNICODE_CHARACTER_CLASS;
                    break;
                case 'd':
                    flags |= Pattern.UNIX_LINES;
                    break;
                case 'i':
                    flags |= Pattern.CASE_INSENSITIVE;
                    break;
                case 'm':
                    flags |= Pattern.MULTILINE;
                    break;
                case 's':
                    flags |= Pattern.DOTALL;
                    break;
                case 'u':
                    flags |= Pattern.UNICODE_CASE;
                    break;
                case 'x':
                    flags |= Pattern.COMMENTS;
                    break;
            }
        }
        return new JavaFlags(flags);
    }

    private static int maskForFlag(int flagChar) {
        switch (flagChar) {
            case 'c':
                return Pattern.CANON_EQ;
            case 'U':
                return Pattern.UNICODE_CHARACTER_CLASS;
            case 'd':
                return Pattern.UNIX_LINES;
            case 'i':
                return Pattern.CASE_INSENSITIVE;
            case 'm':
                return Pattern.MULTILINE;
            case 's':
                return Pattern.DOTALL;
            case 'u':
                return Pattern.UNICODE_CASE;
            case 'x':
                return Pattern.COMMENTS;
            default:
                throw new IllegalStateException("should not reach here");
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(KEYS.size());
        if (isSet(Pattern.CANON_EQ)) {
            sb.append('c');
        }
        if (isSet(Pattern.UNICODE_CHARACTER_CLASS)) {
            sb.append('U');
        }
        if (isSet(Pattern.UNIX_LINES)) {
            sb.append('d');
        }
        if (isSet(Pattern.CASE_INSENSITIVE)) {
            sb.append('i');
        }
        if (isSet(Pattern.MULTILINE)) {
            sb.append('m');
        }
        if (isSet(Pattern.DOTALL)) {
            sb.append('s');
        }
        if (isSet(Pattern.UNICODE_CASE)) {
            sb.append('u');
        }
        if (isSet(Pattern.COMMENTS)) {
            sb.append('x');
        }
        return sb.toString();
    }

    public boolean isCanonEq() {
        return isSet(Pattern.CANON_EQ);
    }

    public boolean isUnicodeCharacterClass() {
        return isSet(Pattern.UNICODE_CHARACTER_CLASS);
    }

    public boolean isUnixLines() {
        return isSet(Pattern.UNIX_LINES);
    }

    public boolean isCaseInsensitive() {
        return isSet(Pattern.CASE_INSENSITIVE);
    }

    public boolean isMultiline() {
        return isSet(Pattern.MULTILINE);
    }

    public boolean isDotAll() {
        return isSet(Pattern.DOTALL);
    }

    public boolean isComments() {
        return isSet(Pattern.COMMENTS);
    }

    public boolean isUnicodeCase() {
        return isSet(Pattern.UNICODE_CASE);
    }

    private boolean isSet(int flag) {
        return (value & flag) != 0;
    }

    public JavaFlags addFlag(int flagChar) {
        return new JavaFlags(this.value | maskForFlag(flagChar));
    }

    public JavaFlags delFlag(int flagChar) {
        return new JavaFlags(this.value & ~maskForFlag(flagChar));
    }

    public static boolean isValidFlagChar(int candidateChar) {
        return FLAGS.get(candidateChar);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JavaFlags && this.value == ((JavaFlags) other).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "TRegexJavaFlags{flags=" + this + '}';
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_CANON_EQ:
            case PROP_UNICODE_CHARACTER_CLASS:
            case PROP_UNIX_LINES:
            case PROP_CASE_INSENSITIVE:
            case PROP_MULTILINE:
            case PROP_DOTALL:
            case PROP_UNICODE_CASE:
            case PROP_COMMENTS:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_CANON_EQ:
                return isCanonEq();
            case PROP_UNICODE_CHARACTER_CLASS:
                return isUnicodeCharacterClass();
            case PROP_UNIX_LINES:
                return isUnixLines();
            case PROP_CASE_INSENSITIVE:
                return isCaseInsensitive();
            case PROP_MULTILINE:
                return isMultiline();
            case PROP_DOTALL:
                return isDotAll();
            case PROP_UNICODE_CASE:
                return isUnicodeCase();
            case PROP_COMMENTS:
                return isComments();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}