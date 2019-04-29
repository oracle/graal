/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.Node;

public abstract class CharMatcher extends Node {

    public static final CharMatcher[] EMPTY = {};

    /**
     * Check if a given character matches this {@link CharMatcher}.
     * 
     * @param c any character.
     * @param compactString {@code true} if {@code c} was read from a compact string and can
     *            therefore be treated as a {@code byte}. This parameter must always be partial
     *            evaluation constant!
     * @return {@code true} if the character matches.
     * @see com.oracle.truffle.api.CompilerDirectives#isPartialEvaluationConstant(Object)
     */
    public abstract boolean execute(char c, boolean compactString);

    /**
     * Conservatively estimate the equivalent number of integer comparisons of calling
     * {@link #execute(char, boolean)}.
     * 
     * @return the number of integer comparisons one call to {@link #execute(char, boolean)} is
     *         roughly equivalent to. Array loads are treated as two comparisons.
     */
    public abstract int estimatedCost();
}
