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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * An assertion that succeeds depending on whether or not text preceding the current position
 * matches a given regular expression.
 * <p>
 * Corresponds to the <strong>( ? &lt;=</strong> <em>Disjunction</em> <strong>)</strong> and
 * <strong>( ? &lt;!</strong> <em>Disjunction</em> <strong>)</strong> right-hand sides of the
 * <em>Assertion</em> goal symbol in the ECMAScript RegExp syntax.
 * <p>
 * Currently, the fragment of regular expressions that TRegex supports in lookbehind assertions is
 * limited to so-called "literal" regular expressions, consisting only of concatenations and
 * character classes (which generalize literal characters). The method {@link #isLiteral} verifies
 * whether the body of the assertion ({@link #getGroup()}) is of this form.
 */
public class LookBehindAssertion extends LookAroundAssertion {

    /**
     * Creates a new look-behind assertion AST node.
     * 
     * Note that for this node to be complete, {@link RegexASTSubtreeRootNode#setGroup(Group)} has
     * to be called with the {@link Group} that represents the contents of this lookbehind
     * assertion.
     * 
     * @param negated whether this lookbehind assertion is negative or not
     */
    LookBehindAssertion(boolean negated) {
        super(negated);
    }

    private LookBehindAssertion(LookBehindAssertion copy, RegexAST ast, boolean recursive) {
        super(copy, ast, recursive);
    }

    @Override
    public LookBehindAssertion copy(RegexAST ast, boolean recursive) {
        return ast.register(new LookBehindAssertion(this, ast, recursive));
    }

    /**
     * Verifies that the contents of this assertion ({@link #getGroup()}) are in "literal" form.
     * 
     * This means that there is only a single alternative which is composed of a sequence of
     * {@link CharacterClass} nodes and terminated by a {@link MatchFound} node.
     */
    public boolean isLiteral() {
        if (getGroup().getAlternatives().size() != 1) {
            return false;
        }
        for (Term t : getGroup().getAlternatives().get(0).getTerms()) {
            if (!(t instanceof CharacterClass)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the length of the words that can be matched by the body of this lookbehind assertion.
     * <p>
     * Because we restrict the regular expressions used in lookbehind assertions to "literal"
     * regular expressions, all strings that match the body of the assertion are guaranteed to be of
     * the same length. This is critical to how lookbehind is implemented, because it tells us how
     * much do we have to rewind when matching a regular expression with lookbehind assertions.
     */
    public int getLength() {
        assert isLiteral();
        return getGroup().getAlternatives().get(0).getTerms().size();
    }

    @Override
    public String getPrefix() {
        return isNegated() ? "?<!" : "?<=";
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson(isNegated() ? "NegativeLookBehindAssertion" : "LookBehindAssertion");
    }
}
