/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;

/**
 * An assertion that succeeds depending on whether or not text surrounding the current position
 * matches a given regular expression.
 * <p>
 * See its two subclasses, {@link LookAheadAssertion} and {@link LookBehindAssertion}.
 */
public abstract class LookAroundAssertion extends RegexASTSubtreeRootNode {

    LookAroundAssertion(boolean negated) {
        setNegated(negated);
    }

    LookAroundAssertion(LookAroundAssertion copy, RegexAST ast) {
        super(copy, ast);
    }

    LookAroundAssertion(LookAroundAssertion copy, RegexAST ast, CompilationBuffer compilationBuffer) {
        super(copy, ast, compilationBuffer);
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

    public void setNegated(boolean negated) {
        setFlag(FLAG_LOOK_AROUND_NEGATED, negated);
    }

    boolean groupEqualsSemantic(LookAroundAssertion o) {
        return isNegated() == o.isNegated() && getGroup().equalsSemantic(o.getGroup());
    }

    public boolean startsWithCharClass() {
        if (getGroup().size() != 1 || getGroup().getFirstAlternative().isEmpty()) {
            return false;
        }
        return getGroup().getFirstAlternative().getFirstTerm().isCharacterClass();
    }

    public boolean endsWithCharClass() {
        if (getGroup().size() != 1 || getGroup().getFirstAlternative().isEmpty()) {
            return false;
        }
        return getGroup().getFirstAlternative().getLastTerm().isCharacterClass();
    }

    /**
     * Checks if the contents of this assertion ({@link #getGroup()}) are in "literal" form.
     *
     * This means that there is only a single alternative which is composed of a sequence of
     * {@link CharacterClass} nodes.
     */
    public boolean isLiteral() {
        if (getGroup().size() != 1) {
            return false;
        }
        for (Term t : getGroup().getFirstAlternative().getTerms()) {
            if (!(t.isCharacterClass())) {
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
    public int getLiteralLength() {
        assert isLiteral();
        return getGroup().getFirstAlternative().getTerms().size();
    }

    /**
     * Returns {@code true} iff this {@link #isLiteral() is a literal} of {@link #getLiteralLength()
     * size} 1, without any capturing groups.
     */
    public boolean isSingleCCNonCapturingLiteral() {
        return getGroup().size() == 1 && getGroup().getFirstAlternative().size() == 1 && getGroup().getFirstAlternative().getFirstTerm().isCharacterClass() && !getGroup().isCapturing();
    }
}
