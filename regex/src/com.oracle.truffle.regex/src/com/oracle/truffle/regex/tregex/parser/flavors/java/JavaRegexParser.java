/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.errors.JavaErrorMessages;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.parser.flavors.MatchingMode;
import com.oracle.truffle.regex.tregex.string.Encodings;

/**
 * Implements the parsing and translating of java.util.regex.Pattern regular expressions to
 * ECMAScript regular expressions.
 */
public final class JavaRegexParser implements RegexParser {

    /**
     * The source object of the input pattern.
     */
    private final RegexSource source;

    private final RegexASTBuilder astBuilder;

    private final JavaLexer lexer;

    private static RegexFlags makeTRegexFlags(boolean sticky) {
        // We need to set the Unicode flag to true so that character classes will treat the entire
        // Unicode code point range as the set of all characters, not just the UTF-16 code units.
        // We will also need to set the sticky flag to properly reflect both the sticky flag in the
        // incoming regex flags and any \G assertions used in the expression.
        return RegexFlags.builder().unicode(true).sticky(sticky).build();
    }

    @CompilerDirectives.TruffleBoundary
    public JavaRegexParser(RegexSource source, RegexASTBuilder astBuilder, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this.source = source;
        this.astBuilder = astBuilder;
        this.lexer = new JavaLexer(source, JavaFlags.parseFlags(source.getFlags()), compilationBuffer);
    }

    public static RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        return new JavaRegexParser(source, new RegexASTBuilder(language, source, makeTRegexFlags(source.getOptions().getMatchingMode() != MatchingMode.search), true, compilationBuffer),
                        compilationBuffer);
    }

    @Override
    public RegexAST parse() {
        astBuilder.pushRootGroup();
        if (lexer.source.getOptions().getMatchingMode() == MatchingMode.fullmatch) {
            astBuilder.pushGroup();
        }
        Token token = null;
        Token last;
        while (lexer.hasNext()) {
            last = token;
            token = lexer.next();
            switch (token.kind) {
                case A:
                    addCaret();
                    break;
                case Z:
                    pushGroup(); // (?:
                    lineTerminators();
                    nextSequence();
                    popGroup(); // )
                    addDollar();
                    break;
                case z:
                    addDollar();
                    break;
                case caret:
                    caret();
                    break;
                case dollar:
                    dollar();
                    break;
                case wordBoundary:
                    if (lexer.getLocalFlags().isUnicodeCharacterClass()) {
                        buildWordBoundaryAssertion(lexer.unicode.word);
                    } else {
                        buildWordBoundaryAssertion(Constants.WORD_CHARS);
                    }
                    break;
                case nonWordBoundary:
                    if (lexer.getLocalFlags().isUnicodeCharacterClass()) {
                        buildWordNonBoundaryAssertion(lexer.unicode.word, lexer.unicode.nonWord);
                    } else {
                        buildWordNonBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token, getFlags().isCaseInsensitive(), getFlags().isUnicodeCase() || getFlags().isUnicodeCharacterClass());
                    break;
                case quantifier:
                    Token.Quantifier quantifier = (Token.Quantifier) token;
                    // quantifiers of type *, + or ? cannot directly follow another quantifier
                    if (last instanceof Token.Quantifier && quantifier.isSingleChar()) {
                        throw syntaxErrorHere(JavaErrorMessages.danglingMetaCharacter(quantifier));
                    }
                    if (astBuilder.getCurTerm() != null) {
                        if (quantifier.isPossessive()) {
                            throw new UnsupportedRegexException("possessive quantifiers are not supported");
                        }
                        addQuantifier((Token.Quantifier) token);
                    } else {
                        if (quantifier.isSingleChar()) {
                            throw syntaxErrorHere(JavaErrorMessages.danglingMetaCharacter(quantifier));
                        }
                    }
                    break;
                case alternation:
                    astBuilder.nextSequence();
                    break;
                case inlineFlags:
                    // flagStack push is handled in the lexer
                    if (!((Token.InlineFlags) token).isGlobal()) {
                        astBuilder.pushGroup(token);
                        astBuilder.getCurGroup().setLocalFlags(true);
                        lexer.pushLocalFlags();
                    }
                    lexer.applyFlags();
                    break;
                case captureGroupBegin:
                    lexer.pushLocalFlags();
                    astBuilder.pushCaptureGroup(token);
                    break;
                case nonCaptureGroupBegin:
                    lexer.pushLocalFlags();
                    astBuilder.pushGroup(token);
                    break;
                case lookAheadAssertionBegin:
                    lexer.pushLocalFlags();
                    astBuilder.pushLookAheadAssertion(token, ((Token.LookAheadAssertionBegin) token).isNegated());
                    break;
                case lookBehindAssertionBegin:
                    lexer.pushLocalFlags();
                    astBuilder.pushLookBehindAssertion(token, ((Token.LookBehindAssertionBegin) token).isNegated());
                    break;
                case groupEnd:
                    if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                        throw syntaxErrorHere(JsErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                    }
                    lexer.popLocalFlags();
                    astBuilder.popGroup(token);
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                case classSet:
                    astBuilder.addClassSet((Token.ClassSet) token,
                                    getFlags().isCaseInsensitive() ? JavaFlavor.getCaseFoldingAlgorithm(getFlags().isUnicodeCase() || getFlags().isUnicodeCharacterClass()) : null);
                    break;
                case linebreak:
                    pushGroup(); // (?:
                    addCharClass(CodePointSet.create('\r'));
                    addCharClass(CodePointSet.create('\n'));
                    nextSequence(); // |
                    addCharClass(CodePointSet.createNoDedup(0x000A, 0x000D, 0x0085, 0x0085, 0x2028, 0x2029));
                    popGroup(); // )
                    break;
            }
        }
        if (lexer.source.getOptions().getMatchingMode() == MatchingMode.fullmatch) {
            astBuilder.popGroup();
            astBuilder.addDollar();
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxErrorHere(JavaErrorMessages.UNCLOSED_GROUP);
        }
        return astBuilder.popRootGroup();
    }

    @Override
    public JavaFlags getFlags() {
        return lexer.getLocalFlags();
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        return AbstractRegexObject.createNamedCaptureGroupMapInt(lexer.getNamedCaptureGroups());
    }

    // Error reporting

    private RegexSyntaxException syntaxErrorHere(String message) {
        return RegexSyntaxException.createPattern(source, message, lexer.getLastTokenPosition());
    }

    // The behavior of the word-boundary assertions depends on the notion of a word character.
    // Java's notion differs from that of ECMAScript and so we cannot compile Java word-boundary
    // assertions to ECMAScript word-boundary assertions. Furthermore, the notion of a word
    // character is dependent on whether the Java regular expression is set to use the ASCII range
    // only.
    private void buildWordBoundaryAssertion(CodePointSet wordChars) {
        CodePointSet nsm = lexer.unicode.getProperty("Mn", false);
        CodePointSet notWordNorNsm = wordChars.union(nsm).createInverse(Encodings.UTF_16);
        pushGroup();

        // Case 1: not word -> word
        // before ((start or any character that's not an accent nor a word) followed by any number
        // of accents)
        pushLookBehindAssertion();
        pushGroup();
        addCaret();
        nextSequence();
        addCharClass(notWordNorNsm);
        popGroup();
        addCharClass(nsm);
        addQuantifier(Token.createQuantifier(0, Token.Quantifier.INFINITY, true, false, true));
        popGroup();
        // after (any word character)
        pushLookAheadAssertion();
        addCharClass(wordChars);
        popGroup();
        nextSequence();
        // Case 2: word -> not word
        // before (word character followed by any number of accents)
        pushLookBehindAssertion();
        addCharClass(wordChars);
        addCharClass(nsm);
        addQuantifier(Token.createQuantifier(0, Token.Quantifier.INFINITY, true, false, true));
        popGroup();

        // after (any character that's not an accent nor a word character, or EOI)
        pushGroup();
        pushLookAheadAssertion();
        addCharClass(notWordNorNsm);
        popGroup();
        nextSequence();
        addDollar();
        popGroup();

        popGroup();
    }

    private void buildWordNonBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?:(?=\W)|$)|(?<=\w)(?=\w))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushLookAheadAssertion(); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        popGroup(); // )
    }

    private void dollar() {
        if (lexer.getLocalFlags().isMultiline()) {
            pushGroup();
            addDollar();
            nextSequence(); // |
            pushLookAheadAssertion();
            lineTerminators();
            popGroup();
            popGroup();
        } else {
            pushGroup();
            addDollar();
            nextSequence(); // |
            pushLookAheadAssertion();
            pushGroup();
            lineTerminators();
            popGroup();
            addDollar();
            popGroup();
            popGroup();
        }
    }

    private void caret() {
        if (lexer.getLocalFlags().isMultiline()) {
            // easy case: only caret
            pushGroup();
            addCaret();
            nextSequence(); // |
            if (getFlags().isUnixLines()) {
                pushLookBehindAssertion();
                addCharClass(CodePointSet.create('\n'));
                popGroup();
            } else {
                // \r\n
                pushLookBehindAssertion();
                addCharClass(CodePointSet.create('\r'));
                addCharClass(CodePointSet.create('\n'));
                popGroup();
                nextSequence(); // |
                // single character terminator (not \r)
                pushLookBehindAssertion();
                addCharClass(CodePointSet.createNoDedup('\n', '\n', 0x0085, 0x0085, 0x2028, 0x2029));
                popGroup();
                nextSequence(); // |
                // \r, we have to make sure it's not followed by \n because \r\n is handled as it
                // was a single character here
                pushLookBehindAssertion();
                addCharClass(CodePointSet.create('\r'));
                popGroup();
                pushLookAheadAssertion();
                addCharClass(CodePointSet.createInverse(CodePointSet.create('\n'), Encodings.UTF_8));
                popGroup();
            }
            popGroup();
            // ^ should not match at the end of input (we also don't want to use (?!$) as it results
            // in backtracking)
            pushLookAheadAssertion();
            addCharClass(Constants.DOT_ALL);
            popGroup();
        } else {
            addCaret();
        }
    }

    private void lineTerminators() {
        if (getFlags().isUnixLines()) {
            addCharClass(CodePointSet.create('\n'));
        } else {
            addCharClass(CodePointSet.create('\r'));
            addCharClass(CodePointSet.create('\n'));
            nextSequence(); // |
            addCharClass(CodePointSet.createNoDedup('\n', '\n', '\r', '\r', 0x0085, 0x0085, 0x2028, 0x2029));
        }
    }

    /// RegexASTBuilder method wrappers

    private void pushGroup() {
        astBuilder.pushGroup();
    }

    private void pushLookAheadAssertion() {
        astBuilder.pushLookAheadAssertion(false);
    }

    private void pushLookBehindAssertion() {
        astBuilder.pushLookBehindAssertion(false);
    }

    private void popGroup() {
        astBuilder.popGroup();
    }

    private void nextSequence() {
        astBuilder.nextSequence();
    }

    private void addCharClass(CodePointSet charSet) {
        astBuilder.addCharClass(charSet);
    }

    private void addCaret() {
        astBuilder.addCaret();
    }

    private void addDollar() {
        astBuilder.addDollar();
    }

    private void addQuantifier(Token.Quantifier quantifier) {
        astBuilder.addQuantifier(quantifier);
    }

}
