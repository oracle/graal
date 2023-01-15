/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.errors.PyErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;

public final class PythonRegexParser implements RegexParser {

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.charClass, Token.Kind.groupEnd, Token.Kind.backReference);

    /**
     * Indicates whether the regex being parsed is a 'str' pattern or a 'bytes' pattern.
     */
    private final PythonREMode mode;
    private final PythonRegexLexer lexer;
    private final RegexASTBuilder astBuilder;

    public PythonRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.mode = PythonREMode.fromEncoding(source.getEncoding());
        this.lexer = new PythonRegexLexer(source, mode);
        this.astBuilder = new RegexASTBuilder(language, source, createECMAScriptFlags(source), false, compilationBuffer);
    }

    private static RegexFlags createECMAScriptFlags(RegexSource source) {
        boolean sticky = source.getOptions().getPythonMethod() == PythonMethod.match || source.getOptions().getPythonMethod() == PythonMethod.fullmatch;
        return RegexFlags.builder().dotAll(true).unicode(true).sticky(sticky).build();
    }

    private PythonFlags getLocalFlags() {
        return lexer.getLocalFlags();
    }

    @Override
    public PythonFlags getFlags() {
        return lexer.getGlobalFlags();
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        return AbstractRegexObject.createNamedCaptureGroupMapInt(lexer.getNamedCaptureGroups());
    }

    @Override
    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        astBuilder.pushRootGroup(true);
        if (lexer.source.getOptions().getPythonMethod() == PythonMethod.fullmatch) {
            astBuilder.pushGroup();
        }
        Token token = null;
        Token.Kind prevKind;
        while (lexer.hasNext()) {
            prevKind = token == null ? null : token.kind;
            token = lexer.next();
            switch (token.kind) {
                case a:
                case z:
                    astBuilder.addPositionAssertion(token);
                    break;
                case caret:
                    if (prevKind != Token.Kind.caret) {
                        if (getLocalFlags().isMultiLine()) {
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
                case dollar:
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
                        if (!getLocalFlags().isMultiLine()) {
                            astBuilder.addDollar();
                        }
                        astBuilder.popGroup();
                        astBuilder.popGroup();
                    }
                    break;
                case wordBoundary:
                    if (prevKind == Token.Kind.wordBoundary) {
                        // ignore
                        break;
                    } else if (prevKind == Token.Kind.nonWordBoundary) {
                        astBuilder.replaceCurTermWithDeadNode();
                        break;
                    }
                    if (getLocalFlags().isUnicode(mode)) {
                        astBuilder.addWordBoundaryAssertion(lexer.getPredefinedCharClass('w'), lexer.getPredefinedCharClass('W'));
                    } else if (getLocalFlags().isLocale()) {
                        bailOut("locale-specific word boundary assertions not supported");
                    } else {
                        astBuilder.addWordBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case nonWordBoundary:
                    if (prevKind == Token.Kind.nonWordBoundary) {
                        // ignore
                        break;
                    } else if (prevKind == Token.Kind.wordBoundary) {
                        astBuilder.replaceCurTermWithDeadNode();
                        break;
                    }
                    if (getLocalFlags().isUnicode(mode)) {
                        astBuilder.addWordNonBoundaryAssertionPython(lexer.getPredefinedCharClass('w'), lexer.getPredefinedCharClass('W'));
                    } else if (getLocalFlags().isLocale()) {
                        bailOut("locale-specific word boundary assertions not supported");
                    } else {
                        astBuilder.addWordNonBoundaryAssertionPython(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case backReference:
                    verifyGroupReference(((Token.BackReference) token).getGroupNr());
                    astBuilder.addBackReference((Token.BackReference) token, getLocalFlags().isIgnoreCase());
                    break;
                case quantifier:
                    if (prevKind == Token.Kind.quantifier) {
                        throw syntaxError(PyErrorMessages.MULTIPLE_REPEAT);
                    }
                    if (astBuilder.getCurTerm() == null || !QUANTIFIER_PREV.contains(prevKind)) {
                        throw syntaxError(PyErrorMessages.NOTHING_TO_REPEAT);
                    }
                    astBuilder.addQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    if (astBuilder.getCurGroup().isConditionalBackReferenceGroup() && astBuilder.getCurGroup().getAlternatives().size() == 2) {
                        throw syntaxError(PyErrorMessages.CONDITIONAL_BACKREF_WITH_MORE_THAN_TWO_BRANCHES);
                    }
                    astBuilder.nextSequence();
                    break;
                case captureGroupBegin:
                    astBuilder.pushCaptureGroup(token);
                    break;
                case nonCaptureGroupBegin:
                    astBuilder.pushGroup(token);
                    break;
                case lookAheadAssertionBegin:
                    astBuilder.pushLookAheadAssertion(token, ((Token.LookAheadAssertionBegin) token).isNegated());
                    break;
                case lookBehindAssertionBegin:
                    astBuilder.pushLookBehindAssertion(token, ((Token.LookBehindAssertionBegin) token).isNegated());
                    break;
                case groupEnd:
                    if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                        throw syntaxError(PyErrorMessages.UNBALANCED_PARENTHESIS);
                    }
                    if (astBuilder.getCurGroup().isLocalFlags()) {
                        lexer.popLocalFlags();
                    }
                    if (astBuilder.getCurGroup().isConditionalBackReferenceGroup() && astBuilder.getCurGroup().getAlternatives().size() == 1) {
                        // generate the implicit empty else branch
                        astBuilder.nextSequence();
                    }
                    astBuilder.popGroup(token);
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                case conditionalBackreference:
                    int referencedGroupNumber = ((Token.BackReference) token).getGroupNr();
                    verifyGroupReference(referencedGroupNumber);
                    astBuilder.pushConditionalBackReferenceGroup(token, referencedGroupNumber);
                    break;
                case inlineFlags:
                    // flagStack push is handled in the lexer
                    if (!((Token.InlineFlags) token).isGlobal()) {
                        astBuilder.pushGroup(token);
                        astBuilder.getCurGroup().setLocalFlags(true);
                    }
                    break;
            }
        }
        if (lexer.source.getOptions().getPythonMethod() == PythonMethod.fullmatch) {
            astBuilder.popGroup();
            astBuilder.addDollar();
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, astBuilder.getCurGroupStartPosition());
        }
        return astBuilder.popRootGroup();
    }

    private static void bailOut(String s) {
        throw new UnsupportedRegexException(s);
    }

    /**
     * Verifies that making a backreference to a certain group is legal in the current context.
     *
     * @param groupNumber the index of the referred group
     * @throws RegexSyntaxException if the backreference is not valid
     */
    private void verifyGroupReference(int groupNumber) throws RegexSyntaxException {
        RegexASTNode parent = astBuilder.getCurGroup();
        while (parent != null) {
            if (parent instanceof Group && ((Group) parent).getGroupNumber() == groupNumber) {
                throw syntaxError(PyErrorMessages.CANNOT_REFER_TO_AN_OPEN_GROUP);
            }
            parent = parent.getParent();
        }
        if (astBuilder.getCurGroup() == null) {
            return;
        }
        parent = astBuilder.getCurGroup().getSubTreeParent();
        while (parent != null) {
            if (parent instanceof LookBehindAssertion && ((LookBehindAssertion) parent).getGroup().getEnclosedCaptureGroupsLow() <= groupNumber) {
                throw syntaxErrorHere(PyErrorMessages.CANNOT_REFER_TO_GROUP_DEFINED_IN_THE_SAME_LOOKBEHIND_SUBPATTERN);
            }
            parent = parent.getSubTreeParent();
        }
    }

    private RegexSyntaxException syntaxError(String msg) {
        return lexer.syntaxError(msg);
    }

    private RegexSyntaxException syntaxErrorHere(String msg) {
        return lexer.syntaxErrorHere(msg);
    }

    private RegexSyntaxException syntaxErrorAtAbs(String msg, int i) {
        return lexer.syntaxErrorAtAbs(msg, i);
    }
}
