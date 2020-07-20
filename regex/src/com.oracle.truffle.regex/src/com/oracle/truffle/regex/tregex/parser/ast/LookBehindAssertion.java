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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

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

    private LookBehindAssertion(LookBehindAssertion copy, RegexAST ast) {
        super(copy, ast);
    }

    private LookBehindAssertion(LookBehindAssertion copy, RegexAST ast, CompilationBuffer compilationBuffer) {
        super(copy, ast, compilationBuffer);
    }

    @Override
    public LookBehindAssertion copy(RegexAST ast) {
        return ast.register(new LookBehindAssertion(this, ast));
    }

    @Override
    public LookBehindAssertion copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return ast.register(new LookBehindAssertion(this, ast, compilationBuffer));
    }

    @Override
    public String getPrefix() {
        return isNegated() ? "?<!" : "?<=";
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj) {
        return this == obj || (obj.isLookBehindAssertion() && groupEqualsSemantic(obj.asLookBehindAssertion()));
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson(isNegated() ? "NegativeLookBehindAssertion" : "LookBehindAssertion");
    }
}
