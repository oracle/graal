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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

public final class JSRegexParser implements RegexParser {

    private static final EnumSet<Token.Kind> QUANTIFIER_PREV = EnumSet.of(Token.Kind.literalChar, Token.Kind.charClass, Token.Kind.charClassEnd, Token.Kind.classSet, Token.Kind.groupEnd,
                    Token.Kind.backReference);
    private final RegexSource source;
    private final JSRegexLexer lexer;
    private final RegexASTBuilder astBuilder;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();

    @TruffleBoundary
    public JSRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.source = source;
        RegexFlags flags = RegexFlags.parseFlags(source);
        this.lexer = new JSRegexLexer(source, flags, compilationBuffer);
        this.astBuilder = new RegexASTBuilder(language, source, flags, flags.isEitherUnicode(), compilationBuffer);
    }

    private RegexFlags getLocalFlags() {
        return lexer.getLocalFlags();
    }

    @Override
    public RegexFlags getFlags() {
        return lexer.getGlobalFlags();
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        Map<String, List<Integer>> namedCaptureGroups = lexer.getNamedCaptureGroups();
        return AbstractRegexObject.createNamedCaptureGroupMapListInt(namedCaptureGroups.isEmpty() ? null : namedCaptureGroups);
    }

    @Override
    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        astBuilder.pushRootGroup(true);
        Token token = null;
        Token.Kind prevKind;
        while (lexer.hasNext()) {
            prevKind = token == null ? null : token.kind;
            token = lexer.next();
            if (!source.getOptions().getFlavor().nestedCaptureGroupsKeptOnLoopReentry() && token.kind != Token.Kind.quantifier && astBuilder.getCurTerm() != null &&
                            astBuilder.getCurTerm().isBackReference() && astBuilder.getCurTerm().asBackReference().isNestedOrForwardReference() &&
                            !isNestedInLookBehindAssertion(astBuilder.getCurTerm())) {
                // In JavaScript, nested backreferences are dropped as no-ops.
                // However, in Python and Ruby, they are valid, since the contents of capture groups
                // are not cleared when re-entering a loop.
                astBuilder.removeCurTerm();
            }
            switch (token.kind) {
                case caret:
                    if (prevKind != Token.Kind.caret) {
                        if (getLocalFlags().isMultiline()) {
                            addMultiLineCaret(token);
                        } else {
                            astBuilder.addPositionAssertion(token);
                        }
                    }
                    break;
                case dollar:
                    if (prevKind != Token.Kind.dollar) {
                        if (getLocalFlags().isMultiline()) {
                            addMultiLineDollar(token);
                        } else {
                            astBuilder.addPositionAssertion(token);
                        }
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
                    if (getLocalFlags().isEitherUnicode() && getLocalFlags().isIgnoreCase()) {
                        astBuilder.addWordBoundaryAssertion(token, Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE_FOR_JS_LOOKAROUND);
                    } else {
                        astBuilder.addWordBoundaryAssertion(token, Constants.WORD_CHARS, Constants.NON_WORD_CHARS_FOR_JS_LOOKAROUND);
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
                    if (getLocalFlags().isEitherUnicode() && getLocalFlags().isIgnoreCase()) {
                        astBuilder.addWordNonBoundaryAssertion(token, Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE_FOR_JS_LOOKAROUND);
                    } else {
                        astBuilder.addWordNonBoundaryAssertion(token, Constants.WORD_CHARS, Constants.NON_WORD_CHARS_FOR_JS_LOOKAROUND);
                    }
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token, getLocalFlags().isIgnoreCase());
                    break;
                case quantifier:
                    if (astBuilder.getCurTerm() == null || !QUANTIFIER_PREV.contains(prevKind)) {
                        throw syntaxError(JsErrorMessages.QUANTIFIER_WITHOUT_TARGET, ErrorCode.InvalidQuantifier);
                    }
                    if (prevKind == Token.Kind.quantifier) {
                        throw syntaxError(JsErrorMessages.QUANTIFIER_ON_QUANTIFIER, ErrorCode.InvalidQuantifier);
                    }
                    if (getLocalFlags().isEitherUnicode() && astBuilder.getCurTerm().isLookAheadAssertion()) {
                        throw syntaxError(JsErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION, ErrorCode.InvalidQuantifier);
                    }
                    if (astBuilder.getCurTerm().isLookBehindAssertion()) {
                        throw syntaxError(JsErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION, ErrorCode.InvalidQuantifier);
                    }
                    astBuilder.addQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    astBuilder.nextSequence();
                    break;
                case captureGroupBegin:
                    astBuilder.pushCaptureGroup(token);
                    break;
                case nonCaptureGroupBegin:
                    astBuilder.pushGroup(token);
                    break;
                case inlineFlags:
                    Token.InlineFlags inlineFlags = (Token.InlineFlags) token;
                    assert !inlineFlags.isGlobal();
                    astBuilder.pushGroup(inlineFlags);
                    lexer.pushLocalFlags((RegexFlags) inlineFlags.getFlags());
                    break;
                case lookAheadAssertionBegin:
                    astBuilder.pushLookAheadAssertion(token, ((Token.LookAheadAssertionBegin) token).isNegated());
                    break;
                case lookBehindAssertionBegin:
                    astBuilder.pushLookBehindAssertion(token, ((Token.LookBehindAssertionBegin) token).isNegated());
                    break;
                case groupEnd:
                    if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                        throw syntaxError(JsErrorMessages.UNMATCHED_RIGHT_PARENTHESIS, ErrorCode.UnmatchedParenthesis);
                    }
                    if (astBuilder.getCurGroup().isLocalFlags()) {
                        lexer.popLocalFlags();
                    }
                    astBuilder.popGroup(token);
                    break;
                case literalChar:
                    literalChar((Token.LiteralCharacter) token);
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                case charClassBegin:
                    curCharClass.clear();
                    break;
                case charClassAtom:
                    curCharClass.addSet(((Token.CharacterClassAtom) token).getContents().getCodePointSet());
                    break;
                case charClassEnd:
                    boolean wasSingleChar = !lexer.isCurCharClassInverted() && curCharClass.matchesSingleChar();
                    if (getLocalFlags().isIgnoreCase()) {
                        lexer.caseFoldUnfold(curCharClass);
                    }
                    CodePointSet cps = curCharClass.toCodePointSet();
                    astBuilder.addCharClass(lexer.isCurCharClassInverted() ? cps.createInverse(source.getEncoding()) : cps, wasSingleChar, token.getSourceSection());
                    break;
                case classSet:
                    astBuilder.addClassSet((Token.ClassSet) token, getLocalFlags().isIgnoreCase() ? CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptUnicode : null);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxError(JsErrorMessages.UNTERMINATED_GROUP, ErrorCode.UnmatchedParenthesis);
        }
        RegexAST ast = astBuilder.popRootGroup();
        checkNamedCaptureGroups(ast);
        return ast;
    }

    private void literalChar(Token.LiteralCharacter literalCharacter) {
        if (getLocalFlags().isIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addCodePoint(literalCharacter.getCodePoint());
            lexer.caseFoldUnfold(curCharClass);
            astBuilder.addCharClass(curCharClass.toCodePointSet(), true, literalCharacter.getSourceSection());
        } else {
            astBuilder.addLiteralChar(literalCharacter);
        }
    }

    private static boolean isNestedInLookBehindAssertion(Term t) {
        RegexASTSubtreeRootNode parent = t.getSubTreeParent();
        while (parent.isLookAroundAssertion()) {
            if (parent.isLookBehindAssertion()) {
                return true;
            }
            parent = parent.getParent().getSubTreeParent();
        }
        return false;
    }

    private void checkNamedCaptureGroups(RegexAST ast) {
        if (lexer.getNamedCaptureGroups() != null) {
            for (Map.Entry<String, List<Integer>> entry : lexer.getNamedCaptureGroups().entrySet()) {
                for (int i = 0; i < entry.getValue().size() - 1; i++) {
                    for (int j = i + 1; j < entry.getValue().size(); j++) {
                        if (canBothParticipate(ast.getGroup(entry.getValue().get(i)).get(0), ast.getGroup(entry.getValue().get(j)).get(0))) {
                            throw syntaxError(JsErrorMessages.MULTIPLE_GROUPS_SAME_NAME, ErrorCode.InvalidNamedGroup);
                        }
                    }
                }
            }
        }
    }

    private static boolean canBothParticipate(Group a, Group b) {
        // Find the lowest common ancestor Group between Groups `a` and `b` and check whether
        // `a` and `b` lie in the same alternative of that ancestor group.
        EconomicMap<Group, Integer> ancestorsA = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        Group ancestorA = a;
        while (ancestorA != null && !ancestorA.getParent().isRoot()) {
            Sequence sequenceA = ancestorA.getParent().isSubtreeRoot() ? ancestorA.getParent().getParent().asSequence() : ancestorA.getParent().asSequence();
            ancestorA = sequenceA.getParent().asGroup();
            int indexA = ancestorA.getAlternatives().indexOf(sequenceA);
            assert indexA >= 0;
            ancestorsA.put(ancestorA, indexA);
        }
        Group ancestorB = b;
        while (ancestorB != null && !ancestorB.getParent().isRoot()) {
            Sequence sequenceB = ancestorB.getParent().isSubtreeRoot() ? ancestorB.getParent().getParent().asSequence() : ancestorB.getParent().asSequence();
            ancestorB = sequenceB.getParent().asGroup();
            if (ancestorsA.containsKey(ancestorB)) {
                int indexA = ancestorsA.get(ancestorB);
                int indexB = ancestorB.getAlternatives().indexOf(sequenceB);
                assert indexB >= 0;
                return indexA == indexB;
            }
        }
        throw CompilerDirectives.shouldNotReachHere("no common ancestor found for named capture groups in regexp");
    }

    private void addMultiLineCaret(Token token) {
        astBuilder.setOverrideSourceSection(token.getSourceSection());
        // (?:^|(?<=[\r\n\u2028\u2029]))
        astBuilder.addCaretOrLookBehind(Constants.LINE_TERMINATOR);
        astBuilder.clearOverrideSourceSection();
    }

    private void addMultiLineDollar(Token token) {
        astBuilder.setOverrideSourceSection(token.getSourceSection());
        // (?:$|(?=[\r\n\u2028\u2029]))
        astBuilder.addDollarOrLookAhead(Constants.LINE_TERMINATOR);
        astBuilder.clearOverrideSourceSection();
    }

    private RegexSyntaxException syntaxError(String msg, ErrorCode errorCode) {
        return RegexSyntaxException.createPattern(source, msg, lexer.getLastTokenPosition(), errorCode);
    }
}
