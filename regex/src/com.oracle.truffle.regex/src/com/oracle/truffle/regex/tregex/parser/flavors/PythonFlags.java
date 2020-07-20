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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * An immutable representation of a set of Python regular expression flags.
 */
public final class PythonFlags extends AbstractConstantKeysObject {

    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray("ASCII", "DOTALL", "IGNORECASE", "LOCALE", "MULTILINE", "TEMPLATE", "UNICODE", "VERBOSE");

    private final int value;

    private static final String FLAGS = "iLmsxatu";
    private static final String TYPE_FLAGS = "Lau";
    private static final String GLOBAL_FLAGS = "t";
    private static final String INTERNAL_FLAGS = "y";

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
        int index = FLAGS.indexOf(flagChar);
        if (index >= 0) {
            return 1 << index;
        } else {
            assert INTERNAL_FLAGS.indexOf(flagChar) >= 0;
            return 1 << (FLAGS.length() + INTERNAL_FLAGS.indexOf(flagChar));
        }
    }

    public boolean hasFlag(int flagChar) {
        return (this.value & maskForFlag(flagChar)) != 0;
    }

    public boolean isIgnoreCase() {
        return hasFlag('i');
    }

    public boolean isLocale() {
        return hasFlag('L');
    }

    public boolean isMultiLine() {
        return hasFlag('m');
    }

    public boolean isDotAll() {
        return hasFlag('s');
    }

    public boolean isVerbose() {
        return hasFlag('x');
    }

    public boolean isAscii() {
        return hasFlag('a');
    }

    public boolean isTemplate() {
        return hasFlag('t');
    }

    public boolean isUnicode() {
        return hasFlag('u');
    }

    public boolean isSticky() {
        return hasFlag('y');
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
    public PythonFlags fixFlags(PythonREMode mode) {
        switch (mode) {
            case Str:
                if (hasFlag('L')) {
                    throw new RegexSyntaxException("cannot use LOCALE flag with a str pattern");
                }
                if (hasFlag('a') && hasFlag('u')) {
                    throw new RegexSyntaxException("ASCII and UNICODE flags are incompatible");
                }
                if (!hasFlag('a')) {
                    return addFlag('u');
                } else {
                    return this;
                }
            case Bytes:
                if (hasFlag('u')) {
                    throw new RegexSyntaxException("cannot use UNICODE flag with a bytes pattern");
                }
                if (hasFlag('a') && hasFlag('L')) {
                    throw new RegexSyntaxException("ASCII and LOCALE flags are incompatible");
                }
                return this;
            default:
                throw Exceptions.shouldNotReachHere();
        }
    }

    public static boolean isValidFlagChar(int candidateChar) {
        return FLAGS.indexOf(candidateChar) >= 0;
    }

    public int numberOfTypeFlags() {
        int typeFlags = 0;
        for (int i = 0; i < TYPE_FLAGS.length(); i++) {
            if (hasFlag(TYPE_FLAGS.charAt(i))) {
                typeFlags++;
            }
        }
        return typeFlags;
    }

    public boolean includesGlobalFlags() {
        for (int i = 0; i < GLOBAL_FLAGS.length(); i++) {
            if (hasFlag(GLOBAL_FLAGS.charAt(i))) {
                return true;
            }
        }
        return false;
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

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(FLAGS.length());
        for (int i = 0; i < FLAGS.length(); i++) {
            char flag = FLAGS.charAt(i);
            if (this.hasFlag(flag)) {
                out.append(flag);
            }
        }
        return out.toString();
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case "ASCII":
                return isAscii();
            case "DOTALL":
                return isDotAll();
            case "IGNORECASE":
                return isIgnoreCase();
            case "LOCALE":
                return isLocale();
            case "MULTILINE":
                return isMultiLine();
            case "TEMPLATE":
                return isTemplate();
            case "UNICODE":
                return isUnicode();
            case "VERBOSE":
                return isVerbose();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
