/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexSyntaxException;
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
                throw new IllegalStateException();
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
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(symbol);
        }
    }
}
