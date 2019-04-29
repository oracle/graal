/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

/**
 * Matcher that matches a single character.
 */
public abstract class SingleCharMatcher extends InvertibleCharMatcher {

    private final char c;

    /**
     * Constructs a new {@link SingleCharMatcher}.
     * 
     * @param invert see {@link InvertibleCharMatcher}.
     * @param c character to match.
     */
    SingleCharMatcher(boolean invert, char c) {
        super(invert);
        this.c = c;
    }

    public static SingleCharMatcher create(boolean invert, char c) {
        return SingleCharMatcherNodeGen.create(invert, c);
    }

    /**
     * @return the character to match.
     */
    public char getChar() {
        return c;
    }

    @Specialization
    public boolean match(char m, boolean compactString) {
        return result((!compactString || c < 256) && c == m);
    }

    @Override
    public int estimatedCost() {
        return 1;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return modifiersToString() + DebugUtil.charToString(c);
    }
}
