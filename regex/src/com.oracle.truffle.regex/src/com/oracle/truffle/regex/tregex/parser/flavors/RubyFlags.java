/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

/**
 * An immutable representation of a set of Ruby regular expression flags.
 */
@ExportLibrary(InteropLibrary.class)
public final class RubyFlags extends AbstractConstantKeysObject {

    private static final String PROP_EXTENDED = "EXTENDED";
    private static final String PROP_IGNORECASE = "IGNORECASE";
    private static final String PROP_MULTILINE = "MULTILINE";
    private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_EXTENDED, PROP_IGNORECASE, PROP_MULTILINE);

    private final int value;
    private final Mode mode;

    private enum Mode {
        Ascii,
        Default,
        Unicode;

        public static final Mode[] VALUES = Mode.values();

        public static Mode fromFlagChar(int ch) {
            return VALUES[indexOfUnrolled(TYPE_FLAGS, ch)];
        }
    }

    private static final String FLAGS = "mixadu";
    private static final String BIT_FLAGS = "mixy";
    private static final String COMPILE_TIME_FLAGS = "mix";
    private static final String TYPE_FLAGS = "adu";

    public RubyFlags(String source) {
        int bits = 0;
        for (int i = 0; i < source.length(); i++) {
            assert isBitFlag(source.charAt(i));
            bits |= maskForFlag(source.charAt(i));
        }
        this.value = bits;
        this.mode = Mode.Default;
    }

    private RubyFlags(int value, Mode mode) {
        this.value = value;
        this.mode = mode;
    }

    private static int maskForFlag(int flagChar) {
        return 1 << indexOfUnrolled(BIT_FLAGS, flagChar);
    }

    public boolean hasFlag(int flagChar) {
        if (isTypeFlag(flagChar)) {
            return this.mode.equals(Mode.fromFlagChar(flagChar));
        } else {
            return (this.value & maskForFlag(flagChar)) != 0;
        }
    }

    public boolean isIgnoreCase() {
        return hasFlag('i');
    }

    public boolean isMultiline() {
        return hasFlag('m');
    }

    public boolean isExtended() {
        return hasFlag('x');
    }

    public boolean isSticky() {
        return hasFlag('y');
    }

    public boolean isAscii() {
        return hasFlag('a');
    }

    public boolean isDefault() {
        return hasFlag('d');
    }

    public boolean isUnicode() {
        return hasFlag('u');
    }

    public RubyFlags addFlag(int flagChar) {
        if (isTypeFlag(flagChar)) {
            return new RubyFlags(this.value, Mode.fromFlagChar(flagChar));
        } else {
            return new RubyFlags(this.value | maskForFlag(flagChar), this.mode);
        }
    }

    public RubyFlags delFlag(int flagChar) {
        assert isBitFlag(flagChar);
        return new RubyFlags(this.value & ~maskForFlag(flagChar), this.mode);
    }

    public static boolean isValidFlagChar(int candidateChar) {
        return indexOfUnrolled(FLAGS, candidateChar) >= 0;
    }

    public static boolean isBitFlag(int candidateChar) {
        return indexOfUnrolled(BIT_FLAGS, candidateChar) >= 0;
    }

    public static boolean isTypeFlag(int candidateChar) {
        return indexOfUnrolled(TYPE_FLAGS, candidateChar) >= 0;
    }

    @ExplodeLoop
    private static int indexOfUnrolled(String flags, int candidateChar) {
        for (int i = 0; i < flags.length(); i++) {
            int c = flags.charAt(i);
            if (candidateChar == c) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubyFlags && this.value == ((RubyFlags) other).value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(COMPILE_TIME_FLAGS.length());
        for (int i = 0; i < COMPILE_TIME_FLAGS.length(); i++) {
            char flag = COMPILE_TIME_FLAGS.charAt(i);
            if (this.hasFlag(flag)) {
                out.append(flag);
            }
        }
        return out.toString();
    }

    @TruffleBoundary
    @ExportMessage
    @Override
    public Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "TRegexRubyFlags{flags=" + this + '}';
    }

    @Override
    public TruffleReadOnlyKeysArray getKeys() {
        return KEYS;
    }

    @Override
    public boolean isMemberReadableImpl(String symbol) {
        switch (symbol) {
            case PROP_EXTENDED:
            case PROP_IGNORECASE:
            case PROP_MULTILINE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
        switch (symbol) {
            case PROP_EXTENDED:
                return isExtended();
            case PROP_IGNORECASE:
                return isIgnoreCase();
            case PROP_MULTILINE:
                return isMultiline();
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
