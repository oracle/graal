/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

/**
 * An assertion that succeeds depending on whether or not text surrounding the current position
 * matches a given regular expression.
 * <p>
 * See its two subclasses, {@link LookAheadAssertion} and {@link LookBehindAssertion}.
 */
public abstract class LookAroundAssertion extends RegexASTSubtreeRootNode {

    LookAroundAssertion(boolean negated) {
        setFlag(FLAG_LOOK_AROUND_NEGATED, negated);
    }

    LookAroundAssertion(LookAroundAssertion copy, RegexAST ast, boolean recursive) {
        super(copy, ast, recursive);
    }

    /**
     * Indicates whether this is a negative lookaround assertion (written as {@code (?!...)}
     * {@code (?<!...)}) or a positive one (written as {@code (?=...)} or {@code (?<=)}).
     * <p>
     * Positive lookaround assertions match if and only if the text around the current position
     * matches the contents of the assertion. Negative lookaround assertions match if and only if
     * the text around the current position <em>does not</em> match the contents of the assertion.
     */
    public boolean isNegated() {
        return isFlagSet(FLAG_LOOK_AROUND_NEGATED);
    }
}
