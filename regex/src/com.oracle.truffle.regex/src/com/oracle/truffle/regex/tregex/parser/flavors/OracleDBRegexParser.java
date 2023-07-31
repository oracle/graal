/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.errors.OracleDBErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;

public final class OracleDBRegexParser implements RegexParser {

    private final RegexSource source;
    private final OracleDBFlags flags;
    private final OracleDBRegexLexer lexer;
    private final RegexASTBuilder astBuilder;

    @TruffleBoundary
    public OracleDBRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this(language, source, compilationBuffer, source);
    }

    public OracleDBRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer, RegexSource originalSource) throws RegexSyntaxException {
        this.source = source;
        this.flags = OracleDBFlags.parseFlags(source);
        this.lexer = new OracleDBRegexLexer(source, flags, compilationBuffer);
        this.astBuilder = new RegexASTBuilder(language, originalSource,
                        RegexFlags.builder().dotAll(flags.isDotAll()).ignoreCase(flags.isIgnoreCase()).multiline(flags.isMultiline()).build(),
                        false,
                        compilationBuffer);
    }

    @Override
    public OracleDBFlags getFlags() {
        return flags;
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        return AbstractRegexObject.createNamedCaptureGroupMapInt(lexer.getNamedCaptureGroups());
    }

    @Override
    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        astBuilder.pushRootGroup();
        Token token = null;
        Token.Kind prevKind;
        while (lexer.hasNext()) {
            prevKind = token == null ? null : token.kind;
            token = lexer.next();
            switch (token.kind) {
                case A, z:
                    astBuilder.addPositionAssertion(token);
                    break;
                case caret:
                    if (prevKind != Token.Kind.caret) {
                        if (flags.isMultiline()) {
                            // (?:^|(?<=\n))
                            astBuilder.pushGroup();
                            astBuilder.addCaret();
                            astBuilder.nextSequence();
                            astBuilder.pushLookBehindAssertion(false);
                            astBuilder.addCharClass(CodePointSet.create('\n'));
                            astBuilder.popGroup();
                            astBuilder.popGroup();
                        } else {
                            astBuilder.addPositionAssertion(token);
                        }
                    }
                    break;
                case dollar, Z:
                    if (prevKind != Token.Kind.dollar) {
                        // multiline mode:
                        // (?:$|(?=\n))
                        // otherwise:
                        // (?:$|(?=\n$))
                        astBuilder.pushGroup();
                        astBuilder.addDollar();
                        astBuilder.nextSequence();
                        astBuilder.pushLookAheadAssertion(false);
                        astBuilder.addCharClass(CodePointSet.create('\n'));
                        if (token.kind == Token.Kind.Z || !flags.isMultiline()) {
                            astBuilder.addDollar();
                        }
                        astBuilder.popGroup();
                        astBuilder.popGroup();
                    }
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token, flags.isIgnoreCase());
                    break;
                case quantifier:
                    if (astBuilder.getCurTerm() == null || prevKind == Token.Kind.captureGroupBegin) {
                        // quantifiers without target are ignored
                        break;
                    }
                    astBuilder.addQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    astBuilder.nextSequence();
                    break;
                case captureGroupBegin:
                    astBuilder.pushCaptureGroup(token);
                    break;
                case groupEnd:
                    if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                        throw syntaxError(OracleDBErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                    }
                    astBuilder.popGroup(token);
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxError(OracleDBErrorMessages.UNTERMINATED_GROUP);
        }
        return astBuilder.popRootGroup();
    }

    private RegexSyntaxException syntaxError(String msg) {
        return RegexSyntaxException.createPattern(source, msg, lexer.getLastTokenPosition());
    }
}
