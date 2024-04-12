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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
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

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.literalChar, Token.Kind.charClass, Token.Kind.charClassEnd, Token.Kind.groupEnd, Token.Kind.backReference);

    /**
     * Indicates whether the regex being parsed is a 'str' pattern or a 'bytes' pattern.
     */
    private final PythonREMode mode;
    private final PythonRegexLexer lexer;
    private final RegexASTBuilder astBuilder;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();

    public PythonRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.mode = PythonREMode.fromEncoding(source.getEncoding());
        this.lexer = new PythonRegexLexer(source, mode, compilationBuffer);
        this.astBuilder = new RegexASTBuilder(language, source, createECMAScriptFlags(source), false, compilationBuffer);
    }

    private static RegexFlags createECMAScriptFlags(RegexSource source) {
        boolean sticky = source.getOptions().getMatchingMode() == MatchingMode.match || source.getOptions().getMatchingMode() == MatchingMode.fullmatch;
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
        if (lexer.source.getOptions().getMatchingMode() == MatchingMode.fullmatch) {
            astBuilder.pushGroup();
        }
        List<Token.BackReference> conditionalBackReferences = new ArrayList<>();
        Token token = null;
        Token prev;
        Token.Kind prevKind;
        while (lexer.hasNext()) {
            prev = token;
            prevKind = prev == null ? null : prev.kind;
            token = lexer.next();
            switch (token.kind) {
                case A:
                case Z:
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
                        astBuilder.addWordBoundaryAssertion(lexer.getLocaleData().getWordCharacters(), lexer.getLocaleData().getNonWordCharacters());
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
                        astBuilder.addWordNonBoundaryAssertionPython(lexer.getLocaleData().getWordCharacters(), lexer.getLocaleData().getNonWordCharacters());
                    } else {
                        astBuilder.addWordNonBoundaryAssertionPython(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case backReference:
                    Token.BackReference backRefToken = (Token.BackReference) token;
                    verifyGroupReference(backRefToken);
                    astBuilder.addBackReference(backRefToken, getLocalFlags().isIgnoreCase());
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
                case atomicGroupBegin:
                    astBuilder.pushAtomicGroup(token);
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
                case literalChar:
                    literalChar(((Token.LiteralCharacter) token).getCodePoint());
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                case charClassBegin:
                    curCharClass.clear();
                    break;
                case charClassAtom:
                    curCharClass.addSet(((Token.CharacterClassAtom) token).getContents());
                    break;
                case charClassEnd:
                    boolean wasSingleChar = !lexer.isCurCharClassInverted() && curCharClass.matchesSingleChar();
                    if (lexer.featureEnabledIgnoreCase()) {
                        lexer.caseFoldUnfold(curCharClass);
                    }
                    CodePointSet cps = curCharClass.toCodePointSet();
                    astBuilder.addCharClass(lexer.isCurCharClassInverted() ? cps.createInverse(lexer.source.getEncoding()) : cps, wasSingleChar);
                    break;
                case conditionalBackreference:
                    Token.BackReference conditionalBackRefToken = (Token.BackReference) token;
                    verifyGroupReference(conditionalBackRefToken);
                    conditionalBackReferences.add(conditionalBackRefToken);
                    astBuilder.pushConditionalBackReferenceGroup(conditionalBackRefToken);
                    break;
                case inlineFlags:
                    Token.InlineFlags inlineFlags = (Token.InlineFlags) token;
                    if (inlineFlags.isGlobal()) {
                        boolean first = prev == null || (prevKind == Token.Kind.inlineFlags && ((Token.InlineFlags) prev).isGlobal());
                        if (!first) {
                            throw syntaxErrorAtAbs(PyErrorMessages.GLOBAL_FLAGS_NOT_AT_START, inlineFlags.getPosition());
                        }
                        lexer.addGlobalFlags((PythonFlags) inlineFlags.getFlags());
                    } else {
                        astBuilder.pushGroup(inlineFlags);
                        astBuilder.getCurGroup().setLocalFlags(true);
                        lexer.pushLocalFlags((PythonFlags) inlineFlags.getFlags());
                    }
                    break;
            }
        }
        if (lexer.source.getOptions().getMatchingMode() == MatchingMode.fullmatch) {
            astBuilder.popGroup();
            astBuilder.addDollar();
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_SUBPATTERN, astBuilder.getCurGroupStartPosition());
        }
        RegexAST ast = astBuilder.popRootGroup();
        for (Token.BackReference conditionalBackReference : conditionalBackReferences) {
            assert conditionalBackReference.getGroupNumbers().length == 1;
            if (conditionalBackReference.getGroupNumbers()[0] >= ast.getNumberOfCaptureGroups()) {
                throw syntaxErrorAtAbs(PyErrorMessages.invalidGroupReference(Integer.toString(conditionalBackReference.getGroupNumbers()[0])), conditionalBackReference.getPosition() + 3);
            }
        }
        lexer.fixFlags();
        return ast;
    }

    private void literalChar(int codePoint) {
        if (lexer.featureEnabledIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addCodePoint(codePoint);
            lexer.caseFoldUnfold(curCharClass);
            astBuilder.addCharClass(curCharClass.toCodePointSet(), true);
        } else {
            astBuilder.addCharClass(CodePointSet.create(codePoint));
        }
    }

    /**
     * Verifies that making a back-reference to a certain group is legal in the current context.
     *
     * @param backRefToken the back-reference in question
     * @throws RegexSyntaxException if the back-reference is not valid
     */
    private void verifyGroupReference(Token.BackReference backRefToken) throws RegexSyntaxException {
        boolean conditional = backRefToken.kind == Token.Kind.conditionalBackreference;
        assert backRefToken.getGroupNumbers().length == 1;
        int groupNumber = backRefToken.getGroupNumbers()[0];
        boolean insideLookBehind = insideLookBehind();
        // CPython allows conditional back-references to be forward references and to also refer to
        // an open group. However, this is not the case when inside a look-behind assertion. In such
        // cases, the 'cannot refer to an open group' error message is used when an open group is
        // references but also when a forward reference is made.
        if (conditional && insideLookBehind) {
            if (groupNumber >= lexer.numberOfCaptureGroupsSoFar()) {
                throw syntaxErrorHere(PyErrorMessages.CANNOT_REFER_TO_AN_OPEN_GROUP);
            }
        }
        if (!conditional || insideLookBehind) {
            RegexASTNode parent = astBuilder.getCurGroup();
            while (parent != null) {
                if (parent instanceof Group && ((Group) parent).getGroupNumber() == groupNumber) {
                    int errorPosition = backRefToken.isNamedReference() ? backRefToken.getPosition() + 4 : backRefToken.getPosition();
                    throw syntaxErrorAtAbs(PyErrorMessages.CANNOT_REFER_TO_AN_OPEN_GROUP, errorPosition);
                }
                parent = parent.getParent();
            }
            if (astBuilder.getCurGroup() == null) {
                return;
            }
            parent = astBuilder.getCurGroup().getSubTreeParent();
            // CPython allows forward references when using conditional "back"-reference groups.
            // The legality of forward references is checked at the end of parsing, so we do the
            // same here. If we were to check for this eagerly (e.g. by scanning for all available
            // capture groups ahead-of-time), we might end up reporting this error instead of some
            // other error that appears later in the expression. In such cases, we would not be
            // compatible with CPython error messages.
            while (parent != null) {
                if (parent instanceof LookBehindAssertion && ((LookBehindAssertion) parent).getGroup().getEnclosedCaptureGroupsLow() <= groupNumber) {
                    throw syntaxErrorHere(PyErrorMessages.CANNOT_REFER_TO_GROUP_DEFINED_IN_THE_SAME_LOOKBEHIND_SUBPATTERN);
                }
                parent = parent.getSubTreeParent();
            }
        }
    }

    private boolean insideLookBehind() {
        RegexASTNode subTreeParent = astBuilder.getCurGroup().getSubTreeParent();
        boolean insideLookBehind = false;
        while (subTreeParent != null) {
            if (subTreeParent.isLookBehindAssertion()) {
                insideLookBehind = true;
            }
            subTreeParent = subTreeParent.getSubTreeParent();
        }
        return insideLookBehind;
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
