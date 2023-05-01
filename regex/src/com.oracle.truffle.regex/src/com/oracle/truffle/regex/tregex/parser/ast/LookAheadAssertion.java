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
import com.oracle.truffle.regex.util.TBitSet;

/**
 * An assertion that succeeds depending on whether another regular expression can be matched at the
 * current position.
 * <p>
 * Corresponds to the <strong>( ? =</strong> <em>Disjunction</em> <strong>)</strong> and <strong>( ?
 * !</strong> <em>Disjunction</em> <strong>)</strong> right-hand sides of the <em>Assertion</em>
 * goal symbol in the ECMAScript RegExp syntax.
 */
public class LookAheadAssertion extends LookAroundAssertion {

    private TBitSet referencedConditionGroups;

    /**
     * Creates a new lookahead assertion AST node.
     *
     * Note that for this node to be complete, {@link RegexASTSubtreeRootNode#setGroup(Group)} has
     * to be called with the {@link Group} that represents the contents of this lookahead assertion.
     *
     * @param negated whether this lookahead assertion is negative or not
     */
    LookAheadAssertion(boolean negated) {
        super(negated);
    }

    private LookAheadAssertion(LookAheadAssertion copy, RegexAST ast) {
        super(copy, ast);
    }

    private LookAheadAssertion(LookAheadAssertion copy, RegexAST ast, CompilationBuffer compilationBuffer) {
        super(copy, ast, compilationBuffer);
    }

    public void registerReferencedConditionGroup(int groupNumber) {
        if (referencedConditionGroups == null) {
            referencedConditionGroups = new TBitSet(Long.SIZE);
        }
        referencedConditionGroups.set(groupNumber);
    }

    public TBitSet getReferencedConditionGroups() {
        if (referencedConditionGroups == null) {
            return TBitSet.getEmptyInstance();
        } else {
            return referencedConditionGroups;
        }
    }

    @Override
    public LookAheadAssertion copy(RegexAST ast) {
        return ast.register(new LookAheadAssertion(this, ast));
    }

    @Override
    public LookAheadAssertion copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return ast.register(new LookAheadAssertion(this, ast, compilationBuffer));
    }

    @Override
    public String getPrefix() {
        return isNegated() ? "?!" : "?=";
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj) {
        return this == obj || (obj.isLookAheadAssertion() && groupEqualsSemantic(obj.asLookAheadAssertion()));
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson(isNegated() ? "NegativeLookAheadAssertion" : "LookAheadAssertion");
    }
}
