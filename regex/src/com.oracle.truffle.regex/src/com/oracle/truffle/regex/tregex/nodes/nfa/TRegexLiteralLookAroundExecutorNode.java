/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.nodes.nfa;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorNode;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;

/**
 * Specialized {@link TRegexExecutorNode} for matching {@link LookAroundAssertion#isLiteral()
 * literal} {@link LookAroundAssertion}s.
 */
public final class TRegexLiteralLookAroundExecutorNode extends TRegexBacktrackerSubExecutorNode {

    private final boolean forward;
    private final boolean negated;
    @CompilationFinal(dimensions = 1) private CharMatcher[] matchers;

    private TRegexLiteralLookAroundExecutorNode(RegexAST ast, int numberOfTransitions, boolean forward, boolean negated, CharMatcher[] matchers) {
        super(ast, numberOfTransitions, null);
        this.forward = forward;
        this.negated = negated;
        this.matchers = matchers;
    }

    private TRegexLiteralLookAroundExecutorNode(TRegexLiteralLookAroundExecutorNode copy) {
        super(copy);
        this.forward = copy.forward;
        this.negated = copy.negated;
        this.matchers = copy.matchers;
    }

    public static TRegexLiteralLookAroundExecutorNode create(RegexAST ast, LookAroundAssertion lookAround, CompilationBuffer compilationBuffer) {
        assert lookAround.isLiteral();
        boolean forward = lookAround.isLookAheadAssertion();
        boolean negated = lookAround.isNegated();
        CharMatcher[] matchers = new CharMatcher[lookAround.getLiteralLength()];
        for (int i = 0; i < matchers.length; i++) {
            CharMatcher matcher = CharMatchers.createMatcher(lookAround.getGroup().getFirstAlternative().get(i).asCharacterClass().getCharSet(), compilationBuffer);
            matchers[forward ? i : matchers.length - (i + 1)] = matcher;
        }
        return new TRegexLiteralLookAroundExecutorNode(ast, matchers.length, forward, negated, matchers);
    }

    @Override
    public TRegexLiteralLookAroundExecutorNode shallowCopy() {
        return new TRegexLiteralLookAroundExecutorNode(this);
    }

    @Override
    public int getNumberOfStates() {
        return matchers.length;
    }

    @Override
    public String getName() {
        return "la";
    }

    @Override
    public boolean isForward() {
        return forward;
    }

    @Override
    public boolean writesCaptureGroups() {
        return false;
    }

    @TruffleBoundary
    @Override
    public TRegexExecutorLocals createLocals(TruffleString input, int fromIndex, int maxIndex, int regionFrom, int regionTo, int index) {
        throw new UnsupportedOperationException();
    }

    @ExplodeLoop
    @Override
    public Object execute(VirtualFrame frame, TRegexExecutorLocals abstractLocals, TruffleString.CodeRange codeRange) {
        TRegexBacktrackingNFAExecutorLocals locals = (TRegexBacktrackingNFAExecutorLocals) abstractLocals;
        for (int i = 0; i < matchers.length; i++) {
            if (!inputHasNext(locals) || !matchers[i].match(inputReadAndDecode(locals, codeRange))) {
                return negated;
            }
            inputAdvance(locals);
        }
        return !negated;
    }
}
