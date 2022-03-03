/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;

/**
 * Implements the parsing and translating of java.util.Pattern regular expressions to ECMAScript
 * regular expressions.
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
        return new JavaRegexParser(source, new RegexASTBuilder(language, source, makeTRegexFlags(false), true, compilationBuffer), compilationBuffer);
    }

    public RegexAST parse() {
        astBuilder.pushRootGroup();
        Token token;
        while (lexer.hasNext()) {
            token = lexer.next();
            switch (token.kind) {
                case A:
                    addCaret();
                    break;
                case Z:
                    // (?:$|(?=[\r\n]$))
                    pushGroup(); // (?:
                    addDollar(); // $
                    nextSequence(); // |
                    pushLookAheadAssertion(); // (?=
                    addCharClass(CodePointSet.create('\n', '\n', '\r', '\r')); // [\r\n]
                    addDollar(); // $
                    popGroup(); // )
                    popGroup(); // )
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
                    if (lexer.getLocalFlags().isUnicodeCase() && lexer.getLocalFlags().isCaseInsensitive()) {
                        buildWordBoundaryAssertion(Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE);
                    } else {
                        buildWordBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case nonWordBoundary:
                    if (lexer.getLocalFlags().isUnicodeCase() && lexer.getLocalFlags().isCaseInsensitive()) {
                        buildWordNonBoundaryAssertion(Constants.WORD_CHARS_UNICODE_IGNORE_CASE, Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE);
                    } else {
                        buildWordNonBoundaryAssertion(Constants.WORD_CHARS, Constants.NON_WORD_CHARS);
                    }
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token);
                    break;
                case quantifier:
                    if (astBuilder.getCurTerm() == null) {
                        throw syntaxErrorHere(JsErrorMessages.QUANTIFIER_WITHOUT_TARGET);
                    }
                    if (lexer.getLocalFlags().isUnicodeCase() && astBuilder.getCurTerm().isLookAheadAssertion()) {
                        throw syntaxErrorHere(JsErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION);
                    }
                    if (astBuilder.getCurTerm().isLookBehindAssertion()) {
                        throw syntaxErrorHere(JsErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION);
                    }
                    addQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    astBuilder.nextSequence();
                    break;
                case inlineFlags:
                    // flagStack push is handled in the lexer
                    if (!((Token.InlineFlags) token).isGlobal()) {
                        astBuilder.pushGroup(token);
                        astBuilder.getCurGroup().setLocalFlags(true);
                    }
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
                        throw syntaxErrorHere(JsErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                    }
                    if (astBuilder.getCurGroup().isLocalFlags()) {
                        lexer.popLocalFlags();
                    }
                    astBuilder.popGroup(token);
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
            }
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxErrorHere(JsErrorMessages.UNTERMINATED_GROUP);
        }
        return astBuilder.popRootGroup();
    }

    @Override
    public AbstractRegexObject getFlags() {
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

    private void bailOut(String reason) throws UnsupportedRegexException {
        throw new UnsupportedRegexException(reason);
    }

    // The behavior of the word-boundary assertions depends on the notion of a word character.
    // Java's notion differs from that of ECMAScript and so we cannot compile Java word-boundary
    // assertions to ECMAScript word-boundary assertions. Furthermore, the notion of a word
    // character is dependent on whether the Java regular expression is set to use the ASCII range
    // only.
    private void buildWordBoundaryAssertion(CodePointSet wordChars, CodePointSet nonWordChars) {
        // (?:(?:^|(?<=\W))(?=\w)|(?<=\w)(?:(?=\W)|$))
        pushGroup(); // (?:
        pushGroup(); // (?:
        addCaret(); // ^
        nextSequence(); // |
        pushLookBehindAssertion(); // (?<=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        popGroup(); // )
        pushLookAheadAssertion(); // (?=
        addCharClass(wordChars); // \w
        popGroup(); // )
        nextSequence(); // |
        pushLookBehindAssertion(); // (?<=
        addCharClass(wordChars); // \w
        popGroup(); // )
        pushGroup(); // (?:
        pushLookAheadAssertion(); // (?=
        addCharClass(nonWordChars); // \W
        popGroup(); // )
        nextSequence(); // |
        addDollar(); // $
        popGroup(); // )
        popGroup(); // )
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
        // (?:$|(?=[\n])) only, when multiline flag is set, otherwise just dollar
        if (lexer.getLocalFlags().isMultiline()) {
            pushGroup(); // (?:
            addDollar(); // $
            nextSequence(); // |
            pushLookAheadAssertion(); // (?=
            addCharClass(CodePointSet.create('\n')); // [\n]
            popGroup(); // )
            popGroup(); // )
        } else {
            /*
             * From doc of Dollar extends Node in java.util.Pattern Node to anchor at the end of a
             * line or the end of input based on the multiline mode.
             *
             * When not in multiline mode, the $ can only match at the very end of the input, unless
             * the input ends in a line terminator in which it matches right before the last line
             * terminator.
             *
             * Note that \r\n is considered an atomic line terminator.
             *
             * Like ^ the $ operator matches at a position, it does not match the line terminators
             * themselves.
             */
            // (?:$|(?=(?:\r?\n$)))
            pushGroup();    // (?:
            addDollar();    // $
            nextSequence(); // |
            pushLookAheadAssertion();  // (?=
            pushGroup(); // (?:
            addCharClass(CodePointSet.create('\r')); // [\r]
            nextSequence(); // |
            popGroup(); // )
            addCharClass(CodePointSet.create('\n')); // [\n]
            addDollar(); // $
            popGroup(); // )
            popGroup(); // )
        }
    }

    private void caret() {
        // (?:^|(?<=[\n])) only, when multiline flag is set, otherwise just caret
        if (lexer.getLocalFlags().isMultiline()) {
            pushGroup(); // (?:
            addCaret(); // ^
            nextSequence(); // |
            pushLookBehindAssertion(); // (?<=
            addCharClass(CodePointSet.create('\n')); // [\n]
            popGroup(); // )
            popGroup(); // )
        } else {
            addCaret();
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