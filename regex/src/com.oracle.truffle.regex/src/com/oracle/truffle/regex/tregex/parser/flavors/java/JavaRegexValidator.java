/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors.java;

import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.errors.JavaErrorMessages;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexValidator;
import com.oracle.truffle.regex.tregex.parser.Token;

import java.util.ArrayList;
import java.util.List;

public class JavaRegexValidator implements RegexValidator {
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
     * The source object of the input pattern.
     */
    private final RegexSource source;

    private final JavaRegexLexer lexer;

    public static JavaRegexValidator createValidator(RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        return new JavaRegexValidator(source, compilationBuffer);
    }

    public JavaRegexValidator(RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.source = source;
        this.lexer = new JavaRegexLexer(source, JavaFlags.parseFlags(source.getFlags()), compilationBuffer);
    }

    @Override
    public void validate() throws RegexSyntaxException {
        List<RegexStackElem> syntaxStack = new ArrayList<>();
        CurTermState curTermState = CurTermState.Null;
        Token token = null;
        Token last;
        while (lexer.hasNext()) {
            last = token;
            token = lexer.next();
            switch (token.kind) {
                case A:
                case Z:
                case z:
                case caret:
                case dollar:
                case wordBoundary:
                case nonWordBoundary:
                case charClass:
                case classSet:
                case linebreak:
                case backReference:
                    curTermState = CurTermState.Other;
                    break;
                case quantifier:
                    Token.Quantifier quantifier = (Token.Quantifier) token;
                    // quantifiers of type *, + or ? cannot directly follow another quantifier
                    if (last instanceof Token.Quantifier && quantifier.isSingleChar()) {
                        throw syntaxErrorHere(JavaErrorMessages.danglingMetaCharacter(quantifier), ErrorCode.InvalidQuantifier);
                    }
                    if (curTermState == CurTermState.Null && quantifier.isSingleChar()) {
                        throw syntaxErrorHere(JavaErrorMessages.danglingMetaCharacter(quantifier), ErrorCode.InvalidQuantifier);
                    }
                    if (quantifier.isPossessive()) {
                        throw new UnsupportedRegexException("possessive quantifiers are not supported");
                    }
                    break;
                case alternation:
                case inlineFlags:
                    curTermState = CurTermState.Null;
                    break;
                case captureGroupBegin:
                case nonCaptureGroupBegin:
                    curTermState = CurTermState.Null;
                    syntaxStack.add(RegexStackElem.Group);
                    break;
                case lookAheadAssertionBegin:
                    curTermState = CurTermState.Null;
                    syntaxStack.add(RegexStackElem.LookAheadAssertion);
                    break;
                case lookBehindAssertionBegin:
                    curTermState = CurTermState.Null;
                    syntaxStack.add(RegexStackElem.LookBehindAssertion);
                    break;
                case groupEnd:
                    if (syntaxStack.isEmpty()) {
                        throw syntaxErrorHere(JsErrorMessages.UNMATCHED_RIGHT_PARENTHESIS, ErrorCode.UnmatchedParenthesis);
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
            }
        }

        if (!syntaxStack.isEmpty()) {
            throw syntaxErrorHere(JavaErrorMessages.UNCLOSED_GROUP, ErrorCode.UnmatchedParenthesis);
        }
    }

    // Error reporting
    private RegexSyntaxException syntaxErrorHere(String message, ErrorCode errorCode) {
        return RegexSyntaxException.createPattern(source, message, lexer.getLastTokenPosition(), errorCode);
    }
}
