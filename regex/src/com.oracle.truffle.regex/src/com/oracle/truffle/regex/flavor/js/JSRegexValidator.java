/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.flavor.js;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.Token;

public class JSRegexValidator implements RegexValidator {

    private final RegexLanguage language;
    private final RegexSource source;
    private final RegexFlags flags;
    private final CompilationBuffer compilationBuffer;
    private final JSRegexLexer lexer;

    public JSRegexValidator(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) {
        this.language = language;
        this.source = source;
        this.flags = RegexFlags.parseFlags(source);
        this.compilationBuffer = compilationBuffer;
        this.lexer = new JSRegexLexer(source, flags, compilationBuffer);
    }

    @Override
    @TruffleBoundary
    public void validate() throws RegexSyntaxException {
        parseDryRun();
    }

    /**
     * A type representing an entry in the stack of currently open parenthesized expressions in a
     * RegExp.
     */
    private enum RegexStackElem {
        Group,
        LookAheadAssertion,
        LookBehindAssertion
    }

    /**
     * Information about the state of the current term. It can be either null, point to a lookahead
     * assertion node, to a lookbehind assertion node or to some other non-null node.
     */
    private enum CurTermState {
        Null,
        LookAheadAssertion,
        LookBehindAssertion,
        Other
    }

    /**
     * Like {@link JSRegexParser#parse()}, but does not construct any AST, only checks for syntax
     * errors.
     * <p>
     * This method simulates the state of {@link JSRegexParser} running
     * {@link JSRegexParser#parse()}. Most of the syntax errors are handled by {@link JSRegexLexer}.
     * In order to correctly identify the remaining syntax errors, we need to track only a fraction
     * of the parser's state (the stack of open parenthesized expressions and a short
     * characterization of the last term).
     * <p>
     * Unlike {@link JSRegexParser#parse()}, this method will never throw an
     * {@link UnsupportedRegexException}.
     *
     * @throws RegexSyntaxException when a syntax error is detected in the RegExp
     */
    private void parseDryRun() throws RegexSyntaxException {
        List<RegexStackElem> syntaxStack = new ArrayList<>();
        CurTermState curTermState = CurTermState.Null;
        while (lexer.hasNext()) {
            Token token = lexer.next();
            switch (token.kind) {
                case caret:
                case dollar:
                case wordBoundary:
                case nonWordBoundary:
                case backReference:
                case literalChar:
                case charClass:
                case charClassBegin:
                case charClassAtom:
                case charClassEnd:
                case classSet:
                    curTermState = CurTermState.Other;
                    break;
                case quantifier:
                    switch (curTermState) {
                        case Null:
                            throw syntaxError(JsErrorMessages.QUANTIFIER_WITHOUT_TARGET, ErrorCode.InvalidQuantifier);
                        case LookAheadAssertion:
                            if (flags.isEitherUnicode()) {
                                throw syntaxError(JsErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION, ErrorCode.InvalidQuantifier);
                            }
                            break;
                        case LookBehindAssertion:
                            throw syntaxError(JsErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION, ErrorCode.InvalidQuantifier);
                        case Other:
                            break;
                    }
                    curTermState = CurTermState.Other;
                    break;
                case alternation:
                    curTermState = CurTermState.Null;
                    break;
                case captureGroupBegin:
                case nonCaptureGroupBegin:
                case inlineFlags:
                    syntaxStack.add(RegexStackElem.Group);
                    curTermState = CurTermState.Null;
                    break;
                case lookAheadAssertionBegin:
                    syntaxStack.add(RegexStackElem.LookAheadAssertion);
                    curTermState = CurTermState.Null;
                    break;
                case lookBehindAssertionBegin:
                    syntaxStack.add(RegexStackElem.LookBehindAssertion);
                    curTermState = CurTermState.Null;
                    break;
                case groupEnd:
                    if (syntaxStack.isEmpty()) {
                        throw syntaxError(JsErrorMessages.UNMATCHED_RIGHT_PARENTHESIS, ErrorCode.UnmatchedParenthesis);
                    }
                    RegexStackElem poppedElem = syntaxStack.remove(syntaxStack.size() - 1);
                    switch (poppedElem) {
                        case LookAheadAssertion:
                            curTermState = CurTermState.LookAheadAssertion;
                            break;
                        case LookBehindAssertion:
                            curTermState = CurTermState.LookBehindAssertion;
                            break;
                        case Group:
                            curTermState = CurTermState.Other;
                            break;
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        if (lexer.inCharacterClass()) {
            throw syntaxError(JsErrorMessages.UNMATCHED_LEFT_BRACKET, ErrorCode.UnmatchedBracket);
        }
        if (!syntaxStack.isEmpty()) {
            throw syntaxError(JsErrorMessages.UNTERMINATED_GROUP, ErrorCode.UnmatchedParenthesis);
        }
        checkNamedCaptureGroups();
    }

    private void checkNamedCaptureGroups() {
        if (lexer.getNamedCaptureGroups() != null) {
            for (Map.Entry<String, List<Integer>> entry : lexer.getNamedCaptureGroups().entrySet()) {
                if (entry.getValue().size() > 1) {
                    // if the regexp contains duplicate names of capture groups, we need to parse
                    // with an actual AST to check whether the two duplicate capture groups can
                    // participate in the same match
                    new JSRegexParser(language, source, compilationBuffer).parse();
                    break;
                }
            }
        }
    }

    private RegexSyntaxException syntaxError(String msg, ErrorCode errorCode) {
        return RegexSyntaxException.createPattern(source, msg, lexer.getLastTokenPosition(), errorCode);
    }
}
