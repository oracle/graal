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

import java.util.Collections;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.JSRegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * Implements the parsing and translating of java.util.Pattern regular expressions to ECMAScript
 * regular expressions.
 */
public final class JavaRegexParser implements RegexParser {

    /**
     * Characters that are considered special in ECMAScript regexes. To match these characters, they
     * need to be escaped using a backslash.
     */
    private static final TBitSet SYNTAX_CHARACTERS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '?', '[', '\\', ']', '^', '{', '|', '}');
    /**
     * Characters that are considered special in ECMAScript regex character classes.
     */
    private static final TBitSet CHAR_CLASS_SYNTAX_CHARACTERS = TBitSet.valueOf('-', '\\', ']', '^');


    /**
     * The source object of the input pattern.
     */
    private final RegexSource inSource;

    /**
     * The source of the input pattern.
     */
    private final String inPattern;

    /**
     * The source of the flags of the input pattern.
     */
    private final String inFlags;

    /**
     * Whether or not the parser should attempt to construct an ECMAScript regex during parsing or
     * not. Setting this to {@code false} is not there to gain efficiency, but to avoid triggering
     * {@link UnsupportedRegexException}s when checking for syntax errors.
     */
    private boolean silent;

    /**
     * The index of the next character in {@link #inPattern} to be parsed.
     */
    private int position;

    /**
     * A {@link StringBuilder} hosting the resulting ECMAScript pattern.
     */
    private final StringBuilder outPattern;

    private final RegexLanguage language;
    private final CompilationBuffer compilationBuffer;

    @CompilerDirectives.TruffleBoundary
    public JavaRegexParser(RegexLanguage language, RegexSource inSource, CompilationBuffer compilationBuffer) {
        this.inSource = inSource;
        this.inPattern = inSource.getPattern();
        this.inFlags = inSource.getFlags();
        this.position = 0;
        this.outPattern = new StringBuilder(inPattern.length());
        this.compilationBuffer = compilationBuffer;
        this.language = language;

        System.out.println(inPattern);
        System.out.println(this.inFlags);
    }

    public static RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        return new JavaRegexParser(language, source, compilationBuffer);
    }

    public void validate() throws RegexSyntaxException {
        silent = true;
        parse();
    }

    private RegexSource toECMAScriptRegex() {
        silent = false;
        parseInternal();

        System.out.println("out: " + outPattern);
//        return inSource;
        return new RegexSource(outPattern.toString(), "", inSource.getOptions(), inSource.getSource());
    }

    @Override
    public RegexAST parse() throws RegexSyntaxException, UnsupportedRegexException {
        RegexSource ecmascriptSource = toECMAScriptRegex();
        JSRegexParser ecmascriptParser = new JSRegexParser(language, ecmascriptSource, compilationBuffer, inSource);
        return ecmascriptParser.parse();

    }

//    @Override
//    public int getNumberOfCaptureGroups() {
//        return 1;
//    }

    @Override
    public Map<String, Integer> getNamedCaptureGroups() {
        return Collections.emptyMap();
    }

    @Override
    public AbstractRegexObject getFlags() {
        return new JavaFlags();
    }

//    @Override
//    public boolean isUnicodePattern() {
//        return false;
//    }


    // The parser
    // TODO do I need to check if the regex is correct (syntax checking) or if regex is incorrect, just parse it and let the TRegex deal with it?

    private void parseInternal() {
        // ...

        disjunction();

        // ...


        // check if we are at the end and if not --> SyntaxError
    }

    /**
     * Disjunction, the topmost syntactic category, is a series of alternatives separated by
     * vertical bars.
     */
    private void disjunction() {
        while (true) {
            alternative();

            if (match("|")) {
                emitSnippet("|");
            } else {
                break;
            }
        }
    }

    /**
     * An alternative is a sequence of Terms.
     */
    private void alternative() {
        while (!atEnd() && curChar() != '|' && curChar() != ')') {
            term();
        }
    }

    private boolean match(String match) {
        if (inPattern.regionMatches(position, match, 0, match.length())) {
            position += match.length();
            return true;
        } else {
            return false;
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    /// Input scanning

    private int curChar() {
        return inPattern.codePointAt(position);
    }

    private int consumeChar() {
        final int c = curChar();
        advance();
        return c;
    }

    private void advance() {
        if (atEnd()) {
//            throw syntaxErrorAtEnd(RbErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
        advance(1);
    }

    private void advance(int len) {
        position = inPattern.offsetByCodePoints(position, len);
    }

    /**
     * Emits the argument into the output pattern <em>verbatim</em>. This is useful for syntax
     * characters or for prebaked snippets.
     *
     * @param snippet
     */
    private void emitSnippet(String snippet) {
        if (!silent) {
            outPattern.append(snippet);
        }
    }

    /**
     * Shorthand for {@link #emitChar}{@code (codepoint, false)}.
     */
    private void emitChar(int codepoint) {
        emitChar(codepoint, false);
    }

    /**
     * Emits a matcher or a character class expression that can match a given character. Since
     * case-folding (IGNORECASE flag) can be enabled, a single character in the pattern could
     * correspond to a variety of different characters in the input.
     *
     * @param codepoint   the character to be matched
     * @param inCharClass if {@code false}, emits a matcher matching {@code codepoint}; if
     *                    {@code true}, emits a sequence of characters and/or character ranges that can be
     *                    used to match {@code codepoint}
     */
    private void emitChar(int codepoint, boolean inCharClass) {
        if (!silent) {
//            if (getLocalFlags().isIgnoreCase()) {
//                curCharClass.clear();
//                curCharClass.addRange(codepoint, codepoint);
//                caseFold();
//                if (curCharClass.matchesSingleChar()) {
//                    emitCharNoCasing(codepoint, inCharClass);
//                } else if (inCharClass) {
//                    emitCharSetNoCasing();
//                } else {
//                    emitSnippet("[");
//                    emitCharSetNoCasing();
//                    emitSnippet("]");
//                }
//            } else {
            emitCharNoCasing(codepoint, inCharClass);
//            }
        }
    }

    /**
     * Like {@link #emitChar(int, boolean)}, but does not do any case-folding.
     */
    private void emitCharNoCasing(int codepoint, boolean inCharClass) {
        if (!silent) {
            TBitSet syntaxChars = inCharClass ? CHAR_CLASS_SYNTAX_CHARACTERS : SYNTAX_CHARACTERS;
            if (syntaxChars.get(codepoint)) {
                emitSnippet("\\");
            }
            emitRawCodepoint(codepoint);
        }
    }

    /**
     * Emits the codepoint into the output pattern <em>verbatim</em>. This is a special case of
     * {@link #emitSnippet} that avoids going through the trouble of converting a code point to a
     * {@link String} in Java (i.e. no need for new String(Character.toChars(codepoint))).
     *
     * @param codepoint
     */
    private void emitRawCodepoint(int codepoint) {
        if (!silent) {
            outPattern.appendCodePoint(codepoint);
        }
    }

    /**
     * Emits a series of matchers that would match the characters in {@code string}.
     */
    private void emitString(String string) {
        if (!silent) {
            for (int i = 0; i < string.length(); i = string.offsetByCodePoints(i, 1)) {
                emitChar(string.codePointAt(i));
            }
        }
    }

    /**
     * Parses a term. A term is either:
     * <ul>
     * <li>whitespace (if in vebose mode)</li>
     * <li>a comment (if in verbose mode)</li>
     * <li>an escape sequence</li>
     * <li>a character class</li>
     * <li>a quantifier</li>
     * <li>a group</li>
     * <li>an assertion</li>
     * <li>a literal character</li>
     * </ul>
     */
    private void term() {
        int ch = consumeChar();

        System.out.println(ch + " : " + (char) ch);
//
//        if (getLocalFlags().isVerbose()) {
//            if (WHITESPACE.get(ch)) {
//                return;
//            }
//            if (ch == '#') {
//                comment();
//                return;
//            }
//        }

        switch (ch) {
            case '\\':
//                escape();
                break;
            case '[':
                characterClass();
//                lastTerm = PythonFlavorProcessor.TermCategory.Atom;
                break;
            case '*':
            case '+':
            case '?':
            case '{':
//                quantifier(ch);
                break;
            case '.':
//                if (getLocalFlags().isDotAll()) {
//                    emitSnippet(".");
//                } else {
//                    emitSnippet("[^\n]");
//                }
//                lastTerm = PythonFlavorProcessor.TermCategory.Atom;
                break;
            case '(':
//                parens();
                break;
            case '^':
//                if (getLocalFlags().isMultiLine()) {
//                    emitSnippet("(?:^|(?<=\n))");
//                } else {
//                    emitSnippet("^");
//                }
//                lastTerm = PythonFlavorProcessor.TermCategory.Assertion;
                break;
            case '$':
//                if (getLocalFlags().isMultiLine()) {
//                    emitSnippet("(?:$|(?=\n))");
//                } else {
//                    emitSnippet("(?:$|(?=\n$))");
//                }
//                lastTerm = PythonFlavorProcessor.TermCategory.Assertion;
                break;
            default:
                emitChar(ch);
//                lastTerm = PythonFlavorProcessor.TermCategory.Atom;
        }
    }

    private void characterClass() {
        emitSnippet("[");
        int start = position - 1;
        if (match("^")) {
            emitSnippet("^");
        }
        int firstPosInside = position;

        // allows nested Char Classes --> see https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
        // allows classes like [[[abd[de]]&&[d]]] and so on but JavaScript does not
        // TODO how to go about that? https://stackoverflow.com/questions/6595477/is-there-a-javascript-regex-equivalent-to-the-intersection-operator-in-java
        // similar as Ruby nested classes?
        // for now I leave the nested classes be

        // besides nested classes it should work the same as ECMA
        // so is there a need to make it like PythonFlavorProessor.java Lines 1173 - 1232?
        // or can I just pass it on?

        classBody:
        while (true) {
            if (atEnd()) {
                // throw syntaxErrorAtAbs()
            }

            int ch = consumeChar();
            switch (ch) {
                case ']':
                    if (position != firstPosInside + 1) {
                        emitSnippet("]");
                        break classBody;
                    }
                    break;
                default:
                    emitChar(ch);
            }

        }
    }
}
