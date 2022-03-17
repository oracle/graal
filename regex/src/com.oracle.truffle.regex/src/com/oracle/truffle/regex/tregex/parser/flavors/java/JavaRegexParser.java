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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.errors.RbErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.JSRegexParser;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlags;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonRegexParser;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyCaseFolding;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyRegexParser;
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
     * An enumeration of the possible grammatical categories of Ruby regex terms, for use with
     * parsing quantifiers.
     */
    private enum TermCategory { // TODO what is necessarily needed in JAVA
        /**
         * A lookahead or lookbehind assertion.
         */
        LookAroundAssertion,
        /**
         * An assertion other than lookahead or lookbehind, e.g. beginning-of-string/line,
         * end-of-string/line or (non)-word-boundary assertion.
         */
        OtherAssertion,
        /**
         * A literal character, a character class or a group.
         */
        Atom,
        /**
         * A term followed by any kind of quantifier.
         */
        Quantifier,
        /**
         * Used as the grammatical category when the term in question does not exist.
         */
        None
    }

    /**
     * Metadata about an enclosing capture group.
     */
    private static final class Group {
        /**
         * The index of the capture group.
         */
        public final int groupNumber;

        Group(int groupNumber) {
            this.groupNumber = groupNumber;
        }
    }

    /**
     * Characters considered as whitespace in Java's regex verbose mode.
     */
//    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\u000b', '\r', ' '); // '\x0B' = '\u000b'

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
    private StringBuilder outPattern;

    /**
     * The contents of the character class that is currently being parsed.
     */
    private CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    /**
     * The characters which are allowed to be full case-foldable (i.e. they are allowed to cross the
     * ASCII boundary) in this character class. This set is constructed as the set of all characters
     * that are included in the character class by being mentioned either:
     * <ul>
     * <li>literally, as in [a]</li>
     * <li>as part of a range, e.g. [a-c]</li>
     * <li>through a POSIX character property other than [[:word:]] and [[:ascii:]]</li>
     * <li>through a Unicode property other than \p{Ascii}</li>
     * <li>through a character type other than \w or \W</li>
     * </ul>
     * This includes character mentioned inside negations, intersections and other nested character
     * classes.
     */
    private CodePointSetAccumulator fullyFoldableCharacters = new CodePointSetAccumulator();
    /**
     * A temporary buffer for case-folding character classes.
     */
    private CodePointSetAccumulator charClassTmp = new CodePointSetAccumulator();
    /**
     * When parsing nested character classes, we need several instances of
     * {@link CodePointSetAccumulator}s. In order to avoid having to repeatedly allocate new ones,
     * we return unused instances to this shared pool, to be reused later.
     */
    private final List<CodePointSetAccumulator> charClassPool = new ArrayList<>();

    /**
     * The grammatical category of the last term parsed. This is needed to detect improper usage of
     * quantifiers.
     */
    private TermCategory lastTerm;

    private RegexLanguage language;
    private CompilationBuffer compilationBuffer;
    private RegexASTBuilder astBuilder;

    /**
     * A reusable buffer for storing the codepoint contents of literal strings. We need to scan the
     * entire string before case folding so that we correctly handle cases when several codepoints
     * in sequence can case-unfold to a single codepoint.
     */
    private final IntArrayBuffer codepointsBuffer = new IntArrayBuffer();

    private static RegexFlags makeTRegexFlags(boolean sticky) {
        // We need to set the Unicode flag to true so that character classes will treat the entire
        // Unicode code point range as the set of all characters, not just the UTF-16 code units.
        // We will also need to set the sticky flag to properly reflect both the sticky flag in the
        // incoming regex flags and the any \G assertions used in the expression.
        return RegexFlags.builder().unicode(true).sticky(sticky).build();
    }

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

    @CompilerDirectives.TruffleBoundary
    public JavaRegexParser(RegexSource inSource, RegexASTBuilder astBuilder) throws RegexSyntaxException {
        this.inSource = inSource;
        this.inPattern = inSource.getPattern();
        this.inFlags = inSource.getFlags();
        this.position = 0;
        this.lastTerm = TermCategory.None;

        this.astBuilder = astBuilder;
    }

    public static RegexParser createParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
//        return new JavaRegexParser(language, source, compilationBuffer);
        return new JavaRegexParser(source, new RegexASTBuilder(language, source, makeTRegexFlags(false), compilationBuffer));
    }

    public void validate() throws RegexSyntaxException {
        silent = true;
        parseInternal();
    }

    public RegexAST parse() {
        astBuilder.pushRootGroup();
        parseInternal();
        RegexAST ast = astBuilder.popRootGroup();

        // ast.setFlags(...);

        return ast;
    }

//    private RegexSource toECMAScriptRegex() {
//        silent = false;
//        parseInternal();
//
//        System.out.println("out: " + outPattern);
////        return inSource;
//        return new RegexSource(outPattern.toString(), "", inSource.getOptions(), inSource.getSource());
//    }
//
//    @Override
//    public RegexAST parse() throws RegexSyntaxException, UnsupportedRegexException {
//        RegexSource ecmascriptSource = toECMAScriptRegex();
//        JSRegexParser ecmascriptParser = new JSRegexParser(language, ecmascriptSource, compilationBuffer, inSource);
//        return ecmascriptParser.parse();
//
//    }

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
    // equivalent to run() in Ruby

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
        System.out.println("Hi");
        while (true) {
            alternative();

            if (match("|")) {
                nextSequence();
                lastTerm = TermCategory.None;
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

    /// Input scanning

    private int curChar() {
        return inPattern.codePointAt(position);
    }

    private int consumeChar() {
        final int c = curChar();
        advance();
        return c;
    }

    private String getMany(Predicate<Integer> pred) {
        StringBuilder out = new StringBuilder();
        while (!atEnd() && pred.test(curChar())) {
            out.appendCodePoint(consumeChar());
        }
        return out.toString();
    }

    private String getUpTo(int count, Predicate<Integer> pred) {
        StringBuilder out = new StringBuilder();
        int found = 0;
        while (found < count && !atEnd() && pred.test(curChar())) {
            out.appendCodePoint(consumeChar());
            found++;
        }
        return out.toString();
    }

    private void advance() {
        if (atEnd()) {
            throw syntaxErrorAtEnd(RbErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
        advance(1);
    }

    private void retreat() {
        advance(-1);
    }

    private void advance(int len) {
        position = inPattern.offsetByCodePoints(position, len);
    }

    private boolean match(String next) {
        if (inPattern.regionMatches(position, next, 0, next.length())) {
            position += next.length();
            return true;
        } else {
            return false;
        }
    }

    private void mustMatch(String next) {
        assert "}".equals(next) || ")".equals(next);
        if (!match(next)) {
            throw syntaxErrorHere("}".equals(next) ? RbErrorMessages.EXPECTED_BRACE : RbErrorMessages.EXPECTED_PAREN);
        }
    }

    private boolean atEnd() {
        return position >= inPattern.length();
    }

    // Error reporting

    private RegexSyntaxException syntaxErrorAtEnd(String message) {
        return RegexSyntaxException.createPattern(inSource, message, inPattern.length() - 1);
    }

    private RegexSyntaxException syntaxErrorHere(String message) {
        return RegexSyntaxException.createPattern(inSource, message, position);
    }

    private RegexSyntaxException syntaxErrorAt(String message, int pos) {
        return RegexSyntaxException.createPattern(inSource, message, pos);
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

//    private PythonFlags getLocalFlags() {
//        return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
//    }

    // TODO how to emitSnippet with ASTBuilder?

    /**
     * Parses a string of literal characters starting with the {@code firstCodepoint}.
     */
    private void string(int firstCodepoint) {
        codepointsBuffer.clear();
        codepointsBuffer.add(firstCodepoint);

        stringLoop: while (!atEnd() && curChar() != '|' && curChar() != ')') {
            int ch = consumeChar();

//            if (getLocalFlags().isExtended()) {
//                if (WHITESPACE.get(ch)) {
//                    continue;
//                }
//                if (ch == '#') {
//                    comment();
//                    continue;
//                }
//            }

            switch (ch) {
                case '\\':
//                    if (isProperEscapeNext()) {
//                        retreat();
//                        break stringLoop;
//                    }
//                    codepointsBuffer.add(fetchEscapedChar());
                    break;
                case '[':
                case '*':
                case '+':
                case '?':
                case '{':
                case '.':
                case '(':
                case '^':
                case '$':
                    retreat();
                    break stringLoop;
                default:
                    codepointsBuffer.add(ch);
            }
        }

//        boolean isQuantifierNext = isQuantifierNext();
        int last = codepointsBuffer.get(codepointsBuffer.length() - 1);

//        if (!silent) {
//            if (isQuantifierNext) {
//                codepointsBuffer.setLength(codepointsBuffer.length() - 1);
//            }
//
//            if (getLocalFlags().isIgnoreCase()) {
//                RubyCaseFolding.caseFoldUnfoldString(codepointsBuffer.toArray(), inSource.getEncoding().getFullSet(), astBuilder);
//            } else {
//                for (int i = 0; i < codepointsBuffer.length(); i++) {
//                    addChar(codepointsBuffer.get(i));
//                }
//            }
//
//            if (isQuantifierNext) {
//                buildChar(last);
//            }
//        }

        lastTerm = TermCategory.Atom;
    }

    /**
     * A comment starts with a '#' and ends at the end of the line. The leading '#' is assumed to
     * have already been parsed.
     */
    private void comment() {
        while (!atEnd()) {
            int ch = consumeChar();
            if (ch == '\\' && !atEnd()) {
                advance();
            } else if (ch == '\n') {
                break;
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
                lastTerm = TermCategory.Atom;
                break;
            case '*':
            case '+':
            case '?':
            case '{':
//                quantifier(ch);
                break;
            case '.':
//                if (getLocalFlags().isMultiline()) {
//                    addCharClass(inSource.getEncoding().getFullSet());
//                } else {
//                    addCharClass(CodePointSet.create('\n').createInverse(inSource.getEncoding()));
//                }
                lastTerm = TermCategory.Atom;
                break;
            case '(':
//                parens();
                break;
            case '^':
                // (?:^|(?<=[\n])(?=.))
                pushGroup(); // (?:
                addCaret(); // ^
                nextSequence(); // |
                pushLookBehindAssertion(false); // (?<=
                addCharClass(CodePointSet.create('\n')); // [\n]
                popGroup(); // )
                pushLookAheadAssertion(false); // (?=
                addCharClass(inSource.getEncoding().getFullSet()); // .
                popGroup(); // )
                popGroup(); // )
                lastTerm = TermCategory.OtherAssertion;
                break;
            case '$':
                // (?:$|(?=[\n]))
                pushGroup(); // (?:
                addDollar(); // $
                nextSequence(); // |
                pushLookAheadAssertion(false); // (?=
                addCharClass(CodePointSet.create('\n')); // [\n]
                popGroup(); // )
                popGroup(); // )
                lastTerm = TermCategory.OtherAssertion;
                break;
            default:
                string(ch);
                lastTerm = TermCategory.Atom;
        }
    }

    /**
     * Parses a character class. The syntax of Ruby character classes is quite different to the one
     * in ECMAScript (set intersections, nested char classes, POSIX brackets...). For that reason,
     * we do not transpile the character class expression piece-by-piece, but we parse it completely
     * to build up a character set representation and then we generate an ECMAScript character class
     * expression that describes that character set. Assumes that the opening {@code '['} was
     * already parsed.
     */
//    private void characterClass() {
//        curCharClassClear();
//        collectCharClass();
//        buildCharClass();
//    }

    private void characterClass() {
        string(consumeChar()); //emitSnippet("[");
        int start = position - 1;
        if (match("^")) {
            string(consumeChar());    // TODO find a way of emitting literal strings
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

        // TODO RegexLexer verwenden und schauen ob man Codes von dort nehmen kann, RegexLexer = JSRegexParser und daher ähnlich zu JavaRegexParser

        classBody:
        while (true) {
            if (atEnd()) {
                // throw syntaxErrorAtAbs()
            }

            int ch = consumeChar();
            switch (ch) {
                case ']':
                    if (position != firstPosInside + 1) {
                        string(consumeChar()); //emitSnippet("]");
                        break classBody;
                    }
                    break;
                default:
                    string(consumeChar()); //emitChar(ch);
            }

        }
    }

    private void bailOut(String reason) throws UnsupportedRegexException {
        if (!silent) {
            throw new UnsupportedRegexException(reason);
        }
    }

    /// RegexASTBuilder method wrappers

    private void pushGroup() {
        if (!silent) {
            astBuilder.pushGroup();
        }
    }

    private void pushCaptureGroup() {
        if (!silent) {
            astBuilder.pushCaptureGroup();
        }
    }

    private void pushLookAheadAssertion(boolean negate) {
        if (!silent) {
            astBuilder.pushLookAheadAssertion(negate);
        }
    }

    private void pushLookBehindAssertion(boolean negate) {
        if (!silent) {
            astBuilder.pushLookBehindAssertion(negate);
        }
    }

    private void popGroup() {
        if (!silent) {
            astBuilder.popGroup();
        }
    }

    private void nextSequence() {
        if (!silent) {
            astBuilder.nextSequence();
        }
    }

    private void addCharClass(CodePointSet charSet) {
        if (!silent) {
            astBuilder.addCharClass(charSet);
        }
    }

    private void addChar(int codepoint) {
        if (!silent) {
            astBuilder.addCharClass(CodePointSet.create(codepoint), true);
        }
    }

    private void addBackReference(int groupNumber) {
        if (!silent) {
            astBuilder.addBackReference(groupNumber);
        }
    }

    private void addSubexpressionCall(int groupNumber) {
        if (!silent) {
            astBuilder.addSubexpressionCall(groupNumber);
        }
    }

    private void addCaret() {
        if (!silent) {
            astBuilder.addCaret();
        }
    }

    private void addDollar() {
        if (!silent) {
            astBuilder.addDollar();
        }
    }

    private void addQuantifier(Token.Quantifier quantifier) {
        if (!silent) {
            astBuilder.addQuantifier(quantifier);
        }
    }

    private void addDeadNode() {
        if (!silent) {
            astBuilder.addDeadNode();
        }
    }

    private void wrapCurTermInGroup() {
        if (!silent) {
            astBuilder.wrapCurTermInGroup();
        }
    }

    // Character predicates

    private static boolean isOctDigit(int c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isDecDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHexDigit(int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    static boolean isAscii(int c) {
        return c < 128;
    }

    // ---- Quantifier

    private static final class Quantifier {
        public static final int INFINITY = -1;

        public int lower;
        public int upper;
        public boolean greedy;

        Quantifier(int lower, int upper, boolean greedy) {
            this.lower = lower;
            this.upper = upper;
            this.greedy = greedy;
        }

        @Override
        public String toString() {
            StringBuilder output = new StringBuilder();
            if (lower == 0 && upper == INFINITY) {
                output.append("*");
            } else if (lower == 1 && upper == INFINITY) {
                output.append("+");
            } else if (lower == 0 && upper == 1) {
                output.append("?");
            } else {
                output.append("{");
                output.append(lower);
                output.append(",");
                if (upper != INFINITY) {
                    output.append(upper);
                }
                output.append("}");
            }
            if (!greedy) {
                output.append("?");
            }
            return output.toString();
        }
    }

    private void quantifier(int ch) {
        int start = position - 1;
        Quantifier quantifier = parseQuantifier(ch);
        if (quantifier != null) {
            buildQuantifier(quantifier, start);
        } else {
            string(consumeChar());
        }
    }

    /**
     * Parses a quantifier whose first character is the argument {@code ch}.
     */
    private Quantifier parseQuantifier(int ch) {
        int start = position - 1;
        if (ch == '{') {
            if (match("}") || match(",}")) {
                position = start;
                return null;
            } else {
                Optional<BigInteger> lowerBound = Optional.empty();
                Optional<BigInteger> upperBound = Optional.empty();
                boolean canBeNonGreedy = true;
                String lower = getMany(JavaRegexParser::isDecDigit);
                if (!lower.isEmpty()) {
                    lowerBound = Optional.of(new BigInteger(lower));
                }
                if (match(",")) {
                    String upper = getMany(JavaRegexParser::isDecDigit);
                    if (!upper.isEmpty()) {
                        upperBound = Optional.of(new BigInteger(upper));
                    }
                } else {
                    upperBound = lowerBound;
                    canBeNonGreedy = false;
                }
                if (!match("}")) {
                    position = start;
                    return null;
                }
                if (lowerBound.isPresent() && upperBound.isPresent() && lowerBound.get().compareTo(upperBound.get()) > 0) {
                    throw syntaxErrorAt(RbErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT, start);
                }
                boolean greedy = true;
                if (canBeNonGreedy && match("?")) {
                    greedy = false;
                }
                return new Quantifier(lowerBound.orElse(BigInteger.ZERO).intValue(),
                        upperBound.orElse(BigInteger.valueOf(Quantifier.INFINITY)).intValue(),
                        greedy);
            }
        } else {
            int lower;
            int upper;
            switch (ch) {
                case '*':
                    lower = 0;
                    upper = Quantifier.INFINITY;
                    break;
                case '+':
                    lower = 1;
                    upper = Quantifier.INFINITY;
                    break;
                case '?':
                    lower = 0;
                    upper = 1;
                    break;
                default:
                    throw new IllegalStateException("should not reach here");
            }
            boolean greedy = true;
            if (match("?")) {
                greedy = false;
            } else if (match("+")) {
                bailOut("possessive quantifiers not supported");
            }
            return new Quantifier(lower, upper, greedy);
        }
    }

    private void buildQuantifier(Quantifier quantifier, int start) {
        switch (lastTerm) {
            case None:
                throw syntaxErrorAt(RbErrorMessages.NOTHING_TO_REPEAT, start);
            case LookAroundAssertion:
                // A lookaround assertion might contain capture groups and thus have side effects.
                // ECMAScript regular expressions do not accept extraneous empty matches. Therefore,
                // an expression like /(?:(?=(a)))?/ would capture the 'a' in a capture group in
                // Ruby but it would not do so in ECMAScript. To avoid this, we bail out on
                // quantifiers on complex assertions (i.e. lookaround assertions), which might
                // contain capture groups.
                // NB: This could be made more specific. We could target only lookaround assertions
                // which contain capture groups and only when the quantifier is actually optional
                // (min = 0, such as ?, *, or {,x}).
                bailOut("quantifiers on lookaround assertions not supported");
                lastTerm = TermCategory.Quantifier;
                break;
            case Quantifier:
            case OtherAssertion:
                wrapCurTermInGroup();
                addQuantifier(Token.createQuantifier(quantifier.lower, quantifier.upper, quantifier.greedy));
                lastTerm = TermCategory.Quantifier;
                break;
            case Atom:
                addQuantifier(Token.createQuantifier(quantifier.lower, quantifier.upper, quantifier.greedy));
                lastTerm = TermCategory.Quantifier;
                break;
        }
    }
}
