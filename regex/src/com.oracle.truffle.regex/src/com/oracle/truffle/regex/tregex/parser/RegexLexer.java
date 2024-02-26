/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.ClassSetContentsAccumulator;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.JavaStringUtil;
import com.oracle.truffle.regex.util.TBitSet;

public abstract class RegexLexer {

    protected static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'S', 'W', 'd', 's', 'w');
    protected static final TBitSet DEFAULT_WHITESPACE = TBitSet.valueOf('\t', '\n', '\u000b', '\f', '\r', ' ');
    public final RegexSource source;
    /**
     * The source of the input pattern.
     */
    protected final String pattern;
    private final Encoding encoding;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    private boolean curCharClassInverted;
    /**
     * The index of the next character in {@link #pattern} to be parsed.
     */
    protected int position = 0;
    // use a LinkedHashMap so that the order of capture groups is preserved
    protected Map<String, List<Integer>> namedCaptureGroups = new LinkedHashMap<>();
    private int curStartIndex = 0;
    private int curCharClassStartIndex = -1;
    private int charClassCurAtomStartIndex = 0;
    private int charClassEmitInvalidRangeAtoms = 0;
    private int nGroups = 1;
    private boolean identifiedAllGroups = false;
    protected final CompilationBuffer compilationBuffer;
    private boolean literalMode = false;

    public RegexLexer(RegexSource source, CompilationBuffer compilationBuffer) {
        this.source = source;
        this.pattern = source.getPattern();
        this.encoding = source.getEncoding();
        this.compilationBuffer = compilationBuffer;
    }

    /**
     * Returns {@code true} if ignore-case mode is currently enabled.
     */
    protected abstract boolean featureEnabledIgnoreCase();

    /**
     * Returns {@code true} if {@code \A} and {@code \Z} position assertions are supported.
     */
    protected abstract boolean featureEnabledAZPositionAssertions();

    /**
     * Returns {@code true} if {@code \z} position assertion is supported.
     */
    protected abstract boolean featureEnabledZLowerCaseAssertion();

    /**
     * Returns {@code true} if {@code \w} and {@code \W} word boundary position assertions are
     * supported.
     */
    protected abstract boolean featureEnabledWordBoundaries();

    /**
     * Returns {@code true} if empty minimum values in bounded quantifiers (e.g. {@code {,1}}) are
     * allowed and treated as zero.
     */
    protected abstract boolean featureEnabledBoundedQuantifierEmptyMin();

    /**
     * Returns {@code true} if possessive quantifiers ({@code +} suffix) are allowed.
     */
    protected abstract boolean featureEnabledPossessiveQuantifiers();

    /**
     * Returns {@code true} if the first character in a character class must be interpreted as part
     * of the character set, even if it is the closing bracket {@code ']'}.
     */
    protected abstract boolean featureEnabledCharClassFirstBracketIsLiteral();

    /**
     * Returns {@code true} if nested character classes are supported. This is required for
     * {@link #featureEnabledPOSIXCharClasses()} .
     */
    protected abstract boolean featureEnabledNestedCharClasses();

    /**
     * Returns {@code true} if POSIX character classes, character equivalence classes, and the POSIX
     * Collating Element Operator are supported. Requires
     * {@link #featureEnabledNestedCharClasses()}.
     */
    protected abstract boolean featureEnabledPOSIXCharClasses();

    /**
     * Returns the POSIX character class associated to the given name.
     */
    protected abstract CodePointSet getPOSIXCharClass(String name);

    /**
     * Checks if the given string is a valid collation element.
     */
    protected abstract void validatePOSIXCollationElement(String sequence);

    /**
     * Checks if the given string is a valid equivalence class.
     */
    protected abstract void validatePOSIXEquivalenceClass(String sequence);

    /**
     * Returns {@code true} if forward references are allowed.
     */
    protected abstract boolean featureEnabledForwardReferences();

    /**
     * Returns {@code true} if group comments (e.g. {@code (# ... )}) are supported.
     */
    protected abstract boolean featureEnabledGroupComments();

    /**
     * Returns {@code true} if line comments (e.g. {@code # ... }) are supported.
     */
    protected abstract boolean featureEnabledLineComments();

    /**
     * Returns {@code true} if white space in the pattern is ignored. This is relevant only if line
     * comments are not supported.
     */
    protected abstract boolean featureEnabledIgnoreWhiteSpace();

    /**
     * The set of codepoints to consider as whitespace in comments and "ignore white space" mode.
     */
    protected abstract TBitSet getWhitespace();

    /**
     * Returns {@code true} if octal escapes (e.g. {@code \012}) are supported.
     */
    protected abstract boolean featureEnabledOctalEscapes();

    /**
     * Returns {@code true} if any constructs that alter a capture group's function, such as
     * non-capturing groups {@code (?:)} or look-around assertions {@code (?=)}, are supported. If
     * this flag is {@code false}, groups starting with a question mark {@code (?} do not have any
     * special meaning.
     */
    protected abstract boolean featureEnabledSpecialGroups();

    /**
     * Returns {@code true} if unicode property escapes (e.g. {@code \p{...}}) are supported.
     */
    protected abstract boolean featureEnabledUnicodePropertyEscapes();

    /**
     * Returns {@code true} if class set expressions (e.g. {@code [[\w\q{abc|xyz}]--[a-cx-z]]}) are
     * supported.
     */
    protected abstract boolean featureEnabledClassSetExpressions();

    /**
     * Returns {@code true} if class set expressions support the difference (--) operator.
     */
    protected abstract boolean featureEnabledClassSetDifference();

    /**
     * Updates a character set by expanding it to the set of characters that case fold to the same
     * characters as the characters currently in the set. This is done by case folding the set and
     * then "unfolding" it by finding all inverse case fold mappings.
     */
    protected abstract void caseFoldUnfold(CodePointSetAccumulator charClass);

    /**
     * Case folds an atom in a class set expression. This maps the elements of the expression into
     * their case folded variant.
     */
    protected abstract ClassSetContents caseFoldClassSetAtom(ClassSetContents classSetContents);

    /**
     * Returns the complement of a class set element. In ECMAScript, this behavior can vary with the
     * flags.
     */
    protected abstract CodePointSet complementClassSet(CodePointSet codePointSet);

    /**
     * Returns the code point set represented by the dot operator.
     */
    protected abstract CodePointSet getDotCodePointSet();

    /**
     * Returns the set of all codepoints a group identifier may begin with.
     */
    protected abstract CodePointSet getIdStart();

    /**
     * Returns the set of all codepoints a group identifier may continue with.
     */
    protected abstract CodePointSet getIdContinue();

    /**
     * Returns the maximum number of digits to parse when parsing a back-reference.
     */
    protected abstract int getMaxBackReferenceDigits();

    /**
     * Returns {@code true} iff the given character is a predefined character class when preceded
     * with a backslash (e.g. \d).
     */
    protected boolean isPredefCharClass(char c) {
        return PREDEFINED_CHAR_CLASSES.get(c);
    }

    /**
     * Returns the CodePointSet associated with the given predefined character class (e.g.
     * {@code \d}).
     * <p>
     * Note that the CodePointSet returned by this function has already been case-folded and
     * negated.
     */
    protected abstract CodePointSet getPredefinedCharClass(char c);

    /**
     * The maximum value allowed while parsing bounded quantifiers. Larger values will cause a call
     * to {@link #handleBoundedQuantifierOverflow(long, long)}.
     */
    protected abstract long boundedQuantifierMaxValue();

    /**
     * Handle {@code {2,1}}.
     */
    protected abstract RegexSyntaxException handleBoundedQuantifierOutOfOrder();

    /**
     * Handle missing {@code }} or minimum value in bounded quantifiers.
     */
    protected abstract Token handleBoundedQuantifierEmptyOrMissingMin();

    /**
     * Handle non-digit characters in bounded quantifiers.
     */
    protected abstract Token handleBoundedQuantifierInvalidCharacter();

    /**
     * Handle integer overflows in quantifier bounds, e.g. {@code {2147483649}}. If this method
     * returns a non-null value, it will be returned instead of the current quantifier.
     */
    protected abstract Token handleBoundedQuantifierOverflow(long min, long max);

    /**
     * Handle integer overflows in quantifier bounds, e.g. {@code {2147483649}}. If this method
     * returns a non-null value, it will be returned instead of the current quantifier. This method
     * is called when no explicit {@code max} value is present.
     */
    protected abstract Token handleBoundedQuantifierOverflowMin(long min, long max);

    /**
     * Handle out of order character class range elements, e.g. {@code [b-a]}.
     */
    protected abstract RegexSyntaxException handleCCRangeOutOfOrder(int startPos);

    /**
     * Handle non-codepoint character class range elements, e.g. {@code [\w-a]}.
     */
    protected abstract void handleCCRangeWithPredefCharClass(int startPos, ClassSetContents firstAtom, ClassSetContents secondAtom);

    /**
     * Handle complement of class set expressions containing strings, e.g. {@code [^\q{abc}]} or
     * {@code \P{RGI_Emoji}}.
     */
    protected abstract RegexSyntaxException handleComplementOfStringSet();

    protected abstract void handleGroupRedefinition(String name, int newId, int oldId);

    /**
     * Handle incomplete hex escapes, e.g. {@code \x1}.
     */
    protected abstract int handleIncompleteEscapeX();

    /**
     * Handle group references to non-existent groups.
     */
    protected abstract Token handleInvalidBackReference(int reference);

    protected abstract ClassSetOperator handleTripleAmpersandInClassSetExpression();

    /**
     * Handle groups starting with {@code (?} and invalid next char.
     */
    protected abstract RegexSyntaxException handleInvalidGroupBeginQ();

    /**
     * Handle class set expressions with mixed set operators in the same nested set.
     */
    protected abstract RegexSyntaxException handleMixedClassSetOperators(ClassSetOperator leftOperator, ClassSetOperator rightOperator);

    /**
     * Handle missing operands in class set expressions, e.g. {@code [\s&&]} or {@code [\w--]}.
     */
    protected abstract RegexSyntaxException handleMissingClassSetOperand(ClassSetOperator operator);

    /**
     * Handle octal values larger than 255.
     */
    protected abstract void handleOctalOutOfRange();

    /**
     * Handle character ranges as operands in class set expressions with operators other than union.
     */
    protected abstract RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator);

    /**
     * Handle unfinished escape (e.g. {@code \}).
     */
    protected abstract void handleUnfinishedEscape();

    /**
     * Handle unfinished group comment {@code (#...)}.
     */
    protected abstract void handleUnfinishedGroupComment();

    /**
     * Handle unfinished group with question mark {@code (?}.
     */
    protected abstract RegexSyntaxException handleUnfinishedGroupQ();

    /**
     * Handle unfinished range in class set expression {@code [a-]}.
     */
    protected abstract RegexSyntaxException handleUnfinishedRangeInClassSet();

    /**
     * Handle unmatched {@code }}.
     */
    protected abstract void handleUnmatchedRightBrace();

    /**
     * Handle unmatched {@code [}.
     */
    protected abstract RegexSyntaxException handleUnmatchedLeftBracket();

    /**
     * Handle unmatched {@code ]}.
     */
    protected abstract void handleUnmatchedRightBracket();

    /**
     * Checks whether {@code codepoint} can appear as an unescaped literal class set character.
     */
    protected abstract void checkClassSetCharacter(int codePoint) throws RegexSyntaxException;

    /**
     * Parse the next codepoint in a group name and return it.
     */
    protected abstract int parseCodePointInGroupName() throws RegexSyntaxException;

    /**
     * Parse any escape sequence starting with {@code \} and the argument {@code c}.
     */
    protected abstract Token parseCustomEscape(char c);

    /**
     * Parse an escape character sequence (inside character class, or other escapes have already
     * been tried) starting with {@code \} and the argument {code c}.
     */
    protected abstract int parseCustomEscapeChar(char c, boolean inCharClass);

    /**
     * Parse an escape character sequence (inside character class, or other escapes have already
     * been tried) starting with {@code \} and the code point {@code c}.This method is called after
     * all other means of parsing the escape sequence have been exhausted.
     */
    protected abstract int parseCustomEscapeCharFallback(int c, boolean inCharClass);

    /**
     * Parse group starting with {@code (?}.
     */
    protected abstract Token parseCustomGroupBeginQ(char charAfterQuestionMark);

    /**
     * Parse group starting with {@code (<}.
     */
    protected abstract Token parseGroupLt();

    /* input string access */

    protected boolean findChars(char... chars) {
        if (atEnd()) {
            return false;
        }
        int i = ArrayUtils.indexOf(pattern, position, pattern.length(), chars);
        if (i < 0) {
            position = pattern.length();
            return false;
        }
        position = i;
        return true;
    }

    protected void advance() {
        advance(1);
    }

    protected void retreat() {
        advance(-1);
    }

    public boolean hasNext() {
        if (!inCharacterClass()) {
            if (featureEnabledLineComments()) {
                int p;
                do {
                    p = position;
                    skipWhitespace();
                    if (consumingLookahead("#")) {
                        skipComment('\n');
                    } else if (featureEnabledGroupComments() && consumingLookahead("(?#")) {
                        if (!skipComment(')')) {
                            handleUnfinishedGroupComment();
                        }
                    }
                } while (p != position);
            } else if (featureEnabledIgnoreWhiteSpace()) {
                skipWhitespace();
            }
            if (featureEnabledGroupComments()) {
                while (consumingLookahead("(?#")) {
                    if (!skipComment(')')) {
                        handleUnfinishedGroupComment();
                    }
                }
            }
        }
        if (atEnd()) {
            if (inCharacterClass()) {
                throw handleUnmatchedLeftBracket();
            }
            return false;
        }
        return true;
    }

    private boolean skipComment(char terminator) {
        while (findChars('\\', terminator)) {
            if (consumeChar() == '\\' && !atEnd()) {
                advance();
            } else {
                return true;
            }
        }
        return false;
    }

    private void skipWhitespace() {
        while (!atEnd()) {
            char curChar = curChar();
            if (!getWhitespace().get(curChar)) {
                break;
            }
            advance();
        }
    }

    public Token next() throws RegexSyntaxException {
        curStartIndex = position;
        Token t = getNext();
        t.setPosition(curStartIndex);
        setSourceSection(t, curStartIndex, position);
        return t;
    }

    /**
     * Returns the last token's position in the pattern string.
     */
    public int getLastTokenPosition() {
        return curStartIndex;
    }

    public int getLastCharacterClassBeginPosition() {
        return curCharClassStartIndex - 1;
    }

    protected int getLastAtomPosition() {
        return Math.max(curStartIndex, charClassCurAtomStartIndex);
    }

    protected char curChar() {
        return pattern.charAt(position);
    }

    protected char consumeChar() {
        final char c = pattern.charAt(position);
        advance();
        return c;
    }

    protected void advance(int len) {
        position += len;
    }

    protected boolean lookahead(String match) {
        if (pattern.length() - position < match.length()) {
            return false;
        }
        return pattern.regionMatches(position, match, 0, match.length());
    }

    protected boolean lookahead(Predicate<Character> predicate, int length) {
        if (pattern.length() - position < length) {
            return false;
        }
        for (int i = position; i < position + length; i++) {
            if (!predicate.test(pattern.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    protected boolean consumingLookahead(char character) {
        if (atEnd()) {
            return false;
        }
        if (curChar() == character) {
            advance();
            return true;
        }
        return false;
    }

    protected boolean consumingLookahead(String match) {
        final boolean matches = lookahead(match);
        if (matches) {
            position += match.length();
        }
        return matches;
    }

    protected boolean consumingLookahead(Predicate<Character> predicate, int length) {
        final boolean matches = lookahead(predicate, length);
        if (matches) {
            position += length;
        }
        return matches;
    }

    protected boolean lookbehind(char c) {
        if (position < 1) {
            return false;
        }
        return pattern.charAt(position - 1) == c;
    }

    protected boolean isEscaped() {
        int backslashPosition = position - 1;
        while (backslashPosition >= 0 && pattern.charAt(backslashPosition) == '\\') {
            backslashPosition--;
        }
        return (position - backslashPosition) % 2 == 0;
    }

    protected int count(Predicate<Character> predicate) {
        return count(predicate, position, pattern.length());
    }

    protected int countUpTo(Predicate<Character> predicate, int max) {
        return count(predicate, position, (int) Math.min(((long) position) + max, pattern.length()));
    }

    protected int countFrom(Predicate<Character> predicate, int fromIndex) {
        return count(predicate, fromIndex, pattern.length());
    }

    protected int count(Predicate<Character> predicate, int fromIndex, int toIndex) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (!predicate.test(pattern.charAt(i))) {
                return i - fromIndex;
            }
        }
        return toIndex - fromIndex;
    }

    protected boolean atEnd() {
        return position >= pattern.length();
    }

    public boolean inCharacterClass() {
        return curCharClassStartIndex >= 0;
    }

    public boolean isCurCharClassInverted() {
        return curCharClassInverted;
    }

    /**
     * Sets the {@link com.oracle.truffle.api.source.SourceSection} of a given {@link Token} in
     * respect of {@link RegexSource#getSource()}.
     *
     * @param startIndex inclusive start index of the source section in respect of
     *            {@link RegexSource#getPattern()}.
     * @param endIndex exclusive end index of the source section in respect of
     *            {@link RegexSource#getPattern()}.
     */
    private void setSourceSection(Token t, int startIndex, int endIndex) {
        if (source.getOptions().isDumpAutomataWithSourceSections()) {
            // RegexSource#getSource() prepends a slash ('/') to the pattern, so we have to add an
            // offset of 1 here.
            t.setSourceSection(source.getSource().createSection(startIndex + 1, endIndex - startIndex));
        }
    }

    /**
     * Get the number of capture groups parsed <em>so far</em>.
     */
    protected int getNumberOfParsedGroups() {
        return nGroups;
    }

    public int totalNumberOfCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return nGroups;
    }

    public int numberOfCaptureGroupsSoFar() {
        assert !identifiedAllGroups;
        return nGroups;
    }

    public Map<String, List<Integer>> getNamedCaptureGroups() throws RegexSyntaxException {
        if (!identifiedAllGroups) {
            identifyCaptureGroups();
            identifiedAllGroups = true;
        }
        return namedCaptureGroups;
    }

    /**
     * Checks whether this regular expression contains any named capture groups.
     * <p>
     * This method is a way to check whether we are parsing the goal symbol Pattern[~U, +N] or
     * Pattern[~U, ~N] (see the ECMAScript RegExp grammar).
     */
    protected boolean hasNamedCaptureGroups() throws RegexSyntaxException {
        return !getNamedCaptureGroups().isEmpty();
    }

    private void registerCaptureGroup() {
        if (!identifiedAllGroups) {
            nGroups++;
        }
    }

    protected void registerNamedCaptureGroup(String name) {
        if (!identifiedAllGroups) {
            List<Integer> groupsWithSameName = namedCaptureGroups.get(name);
            if (groupsWithSameName != null) {
                handleGroupRedefinition(name, nGroups, groupsWithSameName.get(0));
                groupsWithSameName.add(nGroups);
            } else {
                groupsWithSameName = new ArrayList<>();
                groupsWithSameName.add(nGroups);
                namedCaptureGroups.put(name, groupsWithSameName);
            }
        }
        registerCaptureGroup();
    }

    protected int getSingleNamedGroupNumber(String name) {
        List<Integer> groups = namedCaptureGroups.get(name);
        assert groups.size() == 1;
        return groups.get(0);
    }

    /**
     * Only call this from languages which do not support comments. Also, must not be called from
     * languages that disallow forward references (and therefore need {@link #nGroups} to represent
     * the number of capture groups found *so far*). Currently, only being called from JS.
     */
    private void identifyCaptureGroups() throws RegexSyntaxException {
        // We are counting capture groups, so we only care about '(' characters and special
        // characters which can cancel the meaning of '(' - those include '\' for escapes, '[' for
        // character classes (where '(' stands for a literal '(') and any characters after the '('
        // which might turn into a non-capturing group or a look-around assertion.
        boolean insideCharClass = false;
        final int restoreIndex = position;
        while (findChars('\\', '[', ']', '(')) {
            switch (consumeChar()) {
                case '\\':
                    // skip escaped char
                    advance();
                    break;
                case '[':
                    insideCharClass = true;
                    break;
                case ']':
                    insideCharClass = false;
                    break;
                case '(':
                    if (!insideCharClass) {
                        parseGroupBegin();
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        position = restoreIndex;
    }

    protected Token literalChar(int codePoint) {
        return Token.createLiteralCharacter(codePoint);
    }

    private Token charClass(CodePointSet codePointSet) {
        if (featureEnabledIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addSet(codePointSet);
            boolean wasSingleChar = curCharClass.matchesSingleChar();
            caseFoldUnfold(curCharClass);
            return Token.createCharClass(curCharClass.toCodePointSet(), wasSingleChar);
        } else {
            return Token.createCharClass(codePointSet);
        }
    }

    /* lexer */

    private Token getNext() throws RegexSyntaxException {
        final char c = consumeChar();

        if (literalMode) {
            if (parseLiteralEnd(c)) {
                literalMode = false;
                return Token.createNop();
            } else {
                return charClass(CodePointSet.create(toCodePoint(c)));
            }
        }

        if (inCharacterClass()) {
            if (c == ']' && (!featureEnabledCharClassFirstBracketIsLiteral() || position != curCharClassStartIndex + (curCharClassInverted ? 2 : 1))) {
                curCharClassStartIndex = -1;
                return Token.createCharacterClassEnd();
            }
            ClassSetContents atom = parseCharClassAtom(c);
            return Token.createCharacterClassAtom(atom.getCodePointSet(), atom.isPosixCollationEquivalenceClass());
        }
        switch (c) {
            case '.':
                return Token.createCharClass(getDotCodePointSet());
            case '^':
                return Token.createCaret();
            case '$':
                return Token.createDollar();
            case '{':
            case '*':
            case '+':
            case '?':
                return parseQuantifier(c);
            case '}':
                handleUnmatchedRightBrace();
                return literalChar(c);
            case '|':
                return Token.createAlternation();
            case '(':
                return parseGroupBegin();
            case ')':
                return Token.createGroupEnd();
            case '[':
                if (featureEnabledClassSetExpressions()) {
                    return Token.createClassSetExpression(parseClassSetExpression());
                }
                curCharClassStartIndex = position;
                curCharClassInverted = consumingLookahead("^");
                return Token.createCharacterClassBegin();
            case ']':
                handleUnmatchedRightBracket();
                return literalChar(c);
            case '\\':
                return parseEscape();
            default:
                return literalChar(toCodePoint(c));
        }
    }

    private Token parseEscape() throws RegexSyntaxException {
        if (atEnd()) {
            handleUnfinishedEscape();
        }
        final char c = consumeChar();
        Token custom = parseCustomEscape(c);
        if (custom != null) {
            return custom;
        }

        if (parseLiteralStart(c)) {
            literalMode = true;
            return Token.createNop();
        }

        if ('1' <= c && c <= '9') {
            final int restoreIndex = position;
            final int backRefNumber = parseIntSaturated(c - '0', countDecimalDigits(getMaxBackReferenceDigits() - 1), Integer.MAX_VALUE);
            if (backRefNumber < (featureEnabledForwardReferences() ? totalNumberOfCaptureGroups() : nGroups)) {
                return Token.createBackReference(backRefNumber, false);
            } else {
                Token replacement = handleInvalidBackReference(backRefNumber);
                if (replacement != null) {
                    return replacement;
                }
            }
            position = restoreIndex;
        }
        if (featureEnabledAZPositionAssertions()) {
            if (c == 'A') {
                return Token.createA();
            } else if (c == 'Z') {
                return Token.createZ();
            } else if (featureEnabledZLowerCaseAssertion() && c == 'z') {
                return Token.createZLowerCase();
            }
        }
        if (featureEnabledWordBoundaries()) {
            if (c == 'b') {
                return handleWordBoundary();
            } else if (c == 'B') {
                return Token.createNonWordBoundary();
            }
        }
        // Here we differentiate the case when parsing one of the six basic pre-defined
        // character classes (\w, \W, \d, \D, \s, \S) and Unicode character property
        // escapes. Both result in sets of characters, but in the former case, we can skip
        // the case-folding step in the `charClass` method and call `Token::createCharClass`
        // directly.
        if (isPredefCharClass(c)) {
            return Token.createCharClass(getPredefinedCharClass(c));
        } else if (featureEnabledUnicodePropertyEscapes() && (c == 'p' || c == 'P')) {
            ClassSetContents unicodePropertyContents = parseUnicodeCharacterProperty(c == 'P');
            if (featureEnabledClassSetExpressions()) {
                return Token.createClassSetExpression(unicodePropertyContents);
            } else {
                assert unicodePropertyContents.isCodePointSetOnly();
                return charClass(unicodePropertyContents.getCodePointSet());
            }
        } else {
            return literalChar(parseEscapeChar(c, false));
        }
    }

    protected abstract boolean parseLiteralStart(char c);

    protected abstract boolean parseLiteralEnd(char c);

    protected Token handleWordBoundary() {
        return Token.createWordBoundary();
    }

    protected Token parseUnicodePropertyEscape(char c) {
        ClassSetContents unicodePropertyContents = parseUnicodeCharacterProperty(c == 'P');
        assert unicodePropertyContents.isCodePointSetOnly();
        return charClass(unicodePropertyContents.getCodePointSet());
    }

    private Token parseGroupBegin() throws RegexSyntaxException {
        if (featureEnabledSpecialGroups() && consumingLookahead("?")) {
            if (atEnd()) {
                throw handleUnfinishedGroupQ();
            }
            char c = consumeChar();
            Token custom = parseCustomGroupBeginQ(c);
            if (custom != null) {
                return custom;
            }
            switch (c) {
                case '=':
                    return Token.createLookAheadAssertionBegin(false);
                case '!':
                    return Token.createLookAheadAssertionBegin(true);
                case ':':
                    return Token.createNonCaptureGroupBegin();
                case '<':
                    if (consumingLookahead("=")) {
                        return Token.createLookBehindAssertionBegin(false);
                    } else if (consumingLookahead("!")) {
                        return Token.createLookBehindAssertionBegin(true);
                    } else {
                        return parseGroupLt();
                    }
                default:
                    throw handleInvalidGroupBeginQ();
            }
        } else {
            registerCaptureGroup();
            return Token.createCaptureGroupBegin();
        }
    }

    protected enum ParseGroupNameResultState {
        empty,
        unterminated,
        invalidStart,
        invalidRest,
        valid;
    }

    public static final class ParseGroupNameResult {
        public final ParseGroupNameResultState state;
        public final String groupName;

        ParseGroupNameResult(ParseGroupNameResultState state, String groupName) {
            this.state = state;
            this.groupName = groupName;
        }
    }

    /**
     * Parse a {@code GroupName}, i.e. {@code <RegExpIdentifierName>}, assuming that the opening
     * {@code <} bracket was already read.
     *
     * @return the StringValue of the {@code RegExpIdentifierName}
     */
    protected ParseGroupNameResult parseGroupName(char terminator) throws RegexSyntaxException {
        StringBuilder sb = new StringBuilder();
        if (atEnd() || curChar() == terminator) {
            return new ParseGroupNameResult(ParseGroupNameResultState.empty, "");
        }
        int codePoint = parseCodePointInGroupName();
        boolean validFirstChar = getIdStart().contains(codePoint);
        boolean validRest = true;
        sb.appendCodePoint(codePoint);
        while (!atEnd() && curChar() != terminator) {
            codePoint = parseCodePointInGroupName();
            validRest &= getIdContinue().contains(codePoint);
            sb.appendCodePoint(codePoint);
        }
        String groupName = sb.toString();
        if (!consumingLookahead(terminator)) {
            return new ParseGroupNameResult(ParseGroupNameResultState.unterminated, groupName);
        }
        if (!validFirstChar) {
            return new ParseGroupNameResult(ParseGroupNameResultState.invalidStart, groupName);
        }
        if (!validRest) {
            return new ParseGroupNameResult(ParseGroupNameResultState.invalidRest, groupName);
        }
        return new ParseGroupNameResult(ParseGroupNameResultState.valid, groupName);
    }

    private Token parseQuantifier(char c) throws RegexSyntaxException {
        final long min;
        final long max;
        final boolean braces = c == '{';
        if (braces) {
            if (lookahead("}")) {
                return handleBoundedQuantifierEmptyOrMissingMin();
            }
            final int resetIndex = position;
            final int lengthMin = countDecimalDigits();
            if (lengthMin == 0) {
                if (featureEnabledBoundedQuantifierEmptyMin()) {
                    min = 0;
                } else {
                    return handleBoundedQuantifierEmptyOrMissingMin();
                }
            } else {
                min = parseIntSaturated(0, lengthMin, -1, boundedQuantifierMaxValue());
            }
            if (consumingLookahead(",}")) {
                max = -1;
                if (min == -1 || min > Integer.MAX_VALUE) {
                    Token ret = handleBoundedQuantifierOverflowMin(min, max);
                    if (ret != null) {
                        return ret;
                    }
                }
            } else if (consumingLookahead("}")) {
                max = min;
                if (min == -1 || min > Integer.MAX_VALUE) {
                    Token ret = handleBoundedQuantifierOverflowMin(min, max);
                    if (ret != null) {
                        return ret;
                    }
                }
            } else {
                if (!consumingLookahead(",")) {
                    return handleBoundedQuantifierInvalidCharacter();
                }
                final int lengthMax = countDecimalDigits();
                max = parseIntSaturated(0, lengthMax, -1, boundedQuantifierMaxValue());
                if (!consumingLookahead("}")) {
                    return handleBoundedQuantifierInvalidCharacter();
                }
                if (min == -1 || max == -1 || min > Integer.MAX_VALUE || max > Integer.MAX_VALUE) {
                    Token ret = handleBoundedQuantifierOverflow(min, max);
                    if (ret != null) {
                        return ret;
                    }
                }
                if (isQuantifierOutOfOrder(min, max, resetIndex, lengthMin, lengthMax)) {
                    throw handleBoundedQuantifierOutOfOrder();
                }
            }
        } else {
            min = c == '+' ? 1 : 0;
            max = c == '?' ? 1 : -1;
        }
        boolean greedy = true;
        boolean possessive = false;
        if (consumingLookahead('?')) {
            greedy = false;
        } else if (featureEnabledPossessiveQuantifiers() && consumingLookahead('+')) {
            possessive = true;
        }

        return Token.createQuantifier((int) min, (int) max, greedy, possessive, !braces);
    }

    private boolean isQuantifierOutOfOrder(long parsedMin, long parsedMax, int startMin, int lengthMin, int lengthMax) {
        if (Long.compareUnsigned(parsedMin, parsedMax) > 0) {
            return true;
        } else if (parsedMin == -1 && parsedMax == -1) {
            int startMax = startMin + lengthMin + 1;
            int nZerosMin = countZeros(startMin);
            int nZerosMax = countZeros(startMax);
            int lengthMinTrunc = lengthMin - nZerosMin;
            int lengthMaxTrunc = lengthMax - nZerosMax;
            return lengthMinTrunc > lengthMaxTrunc || lengthMinTrunc == lengthMaxTrunc &&
                            pattern.substring(startMin + nZerosMin, startMin + lengthMin).compareTo(pattern.substring(startMax + nZerosMax, startMax + lengthMax)) > 0;
        }
        return false;
    }

    protected int parseIntSaturated(int firstDigit, int length, int returnOnOverflow) {
        return (int) parseIntSaturated(firstDigit, length, returnOnOverflow, Integer.MAX_VALUE);
    }

    protected long parseIntSaturated(int firstDigit, int length, int returnOnOverflow, long maxValue) {
        int fromIndex = position;
        position += length;
        long ret = firstDigit;
        for (int i = fromIndex; i < fromIndex + length; i++) {
            int nextDigit = pattern.charAt(i) - '0';
            if (ret > maxValue / 10) {
                return returnOnOverflow;
            }
            ret *= 10;
            if (ret > maxValue - nextDigit) {
                return returnOnOverflow;
            }
            ret += nextDigit;
        }
        return ret;
    }

    protected int countDecimalDigits() {
        return count(RegexLexer::isDecimalDigit);
    }

    private int countDecimalDigits(int maxLength) {
        return countUpTo(RegexLexer::isDecimalDigit, maxLength);
    }

    private int countZeros(int fromIndex) {
        return countFrom((c) -> c == '0', fromIndex);
    }

    protected ClassSetContents parseCharClassAtomPredefCharClass(char c) throws RegexSyntaxException {
        if (c == '\\') {
            if (atEnd()) {
                handleUnfinishedEscape();
            }
            if (isEscapeCharClass(curChar())) {
                ClassSetContents contents = parseEscapeCharClass(consumeChar());
                assert featureEnabledClassSetExpressions() || contents.isCodePointSetOnly();
                return contents;
            }
        } else if (featureEnabledNestedCharClasses() && c == '[') {
            if (atEnd()) {
                throw handleUnmatchedLeftBracket();
            }
            if (featureEnabledPOSIXCharClasses()) {
                return parsePOSIXCharClassElement();
            }
        }
        return null;
    }

    private ClassSetContents parsePOSIXCharClassElement() {
        int resetIndex = position;
        char delim = consumeChar();
        if (delim == ':' || delim == '.' || delim == '=') {
            int end = pattern.indexOf(getPosixCharClassEndStr(delim), position);
            if (end >= 0) {
                String name = pattern.substring(position, end);
                position = end + 2;
                switch (delim) {
                    case ':':
                        return ClassSetContents.createCharacterClass(getPOSIXCharClass(name));
                    case '.':
                        if (JavaStringUtil.isSingleCodePoint(name)) {
                            return ClassSetContents.createPOSIXCollationElement(name.codePointAt(0));
                        } else {
                            validatePOSIXCollationElement(name);
                            return ClassSetContents.createPOSIXCollationElement(name);
                        }
                    case '=':
                        if (JavaStringUtil.isSingleCodePoint(name)) {
                            return ClassSetContents.createPOSIXCollationEquivalenceClass(name.codePointAt(0));
                        } else {
                            validatePOSIXCollationElement(name);
                            return ClassSetContents.createPOSIXCollationEquivalenceClass(name);
                        }
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }
        position = resetIndex;
        return ClassSetContents.createCharacter('[');
    }

    private static String getPosixCharClassEndStr(char delim) {
        switch (delim) {
            case ':':
                return ":]";
            case '.':
                return ".]";
            case '=':
                return "=]";
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    protected int parseCharClassAtomCodePoint(char c) throws RegexSyntaxException {
        if (c == '\\') {
            assert !atEnd();
            assert !isEscapeCharClass(curChar());
            return parseEscapeChar(consumeChar(), true);
        } else {
            int codePoint = toCodePoint(c);
            if (featureEnabledClassSetExpressions()) {
                checkClassSetCharacter(codePoint);
            }
            return codePoint;
        }
    }

    private ClassSetContents parseCharClassAtomInner(char c) throws RegexSyntaxException {
        ClassSetContents cc = parseCharClassAtomPredefCharClass(c);
        if (cc != null) {
            return cc;
        }
        return ClassSetContents.createCharacter(parseCharClassAtomCodePoint(c));
    }

    private ClassSetContents parseCharClassAtom(char c) throws RegexSyntaxException {
        int startPos = position - 1;
        charClassCurAtomStartIndex = position - 1;
        ClassSetContents firstAtom = parseCharClassAtomInner(c);
        if (charClassEmitInvalidRangeAtoms > 0) {
            charClassEmitInvalidRangeAtoms--;
            return firstAtom;
        }
        if (consumingLookahead("-")) {
            if (atEnd() || lookahead("]")) {
                position--;
                return firstAtom;
            } else {
                char nextC = consumeChar();
                charClassCurAtomStartIndex = position - 1;
                ClassSetContents secondAtom = parseCharClassAtomInner(nextC);
                // Runtime Semantics: CharacterRangeOrUnion(firstAtom, secondAtom)
                if (!firstAtom.isAllowedInRange() || !secondAtom.isAllowedInRange()) {
                    handleCCRangeWithPredefCharClass(startPos, firstAtom, secondAtom);
                    // no syntax error thrown, so we have to emit the range as three separate atoms
                    position = charClassCurAtomStartIndex - 1;
                    charClassEmitInvalidRangeAtoms = 2;
                    return firstAtom;
                } else {
                    if (secondAtom.getCodePoint() < firstAtom.getCodePoint()) {
                        throw handleCCRangeOutOfOrder(startPos);
                    } else {
                        return ClassSetContents.createRange(firstAtom.getCodePoint(), secondAtom.getCodePoint());
                    }
                }
            }
        } else {
            return firstAtom;
        }
    }

    private ClassSetContents parseEscapeCharClass(char c) throws RegexSyntaxException {
        if (isPredefCharClass(c)) {
            return ClassSetContents.createCharacterClass(getPredefinedCharClass(c));
        } else if (featureEnabledUnicodePropertyEscapes() && (c == 'p' || c == 'P')) {
            return parseUnicodeCharacterProperty(c == 'P');
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    public enum ClassSetOperator {
        Union("implicit union"),
        Intersection("&&"),
        Difference("--");

        private final String repr;

        ClassSetOperator(String repr) {
            this.repr = repr;
        }

        @Override
        public String toString() {
            return repr;
        }
    }

    protected ClassSetContents parseClassSetExpression() throws RegexSyntaxException {
        final boolean invert = consumingLookahead("^");
        ClassSetContentsAccumulator curClassSet = new ClassSetContentsAccumulator();
        ClassSetOperator operator = null;
        boolean firstOperandIsRange = false;
        int startPos = position;
        while (!atEnd()) {
            if (curChar() == ']' && (!featureEnabledCharClassFirstBracketIsLiteral() || position != startPos)) {
                advance();
                if (invert && curClassSet.mayContainStrings()) {
                    throw handleComplementOfStringSet();
                }
                if (invert) {
                    assert !curClassSet.mayContainStrings() && curClassSet.isCodePointSetOnly();
                    return ClassSetContents.createCharacterClass(complementClassSet(curClassSet.getCodePointSet()));
                } else {
                    EconomicSet<String> stringsCopy = EconomicSet.create(curClassSet.getStrings().size());
                    stringsCopy.addAll(curClassSet.getStrings());
                    return ClassSetContents.createClass(curClassSet.getCodePointSet(), stringsCopy, curClassSet.mayContainStrings());
                }
            }

            ClassSetOperator newOperator = parseClassSetOperator();
            if (position == startPos) {
                if (newOperator != ClassSetOperator.Union) {
                    throw handleMissingClassSetOperand(newOperator);
                }
            } else {
                if (operator == null) {
                    // first operator
                    operator = newOperator;
                    if (firstOperandIsRange && operator != ClassSetOperator.Union) {
                        throw handleRangeAsClassSetOperand(operator);
                    }
                } else if (operator != newOperator) {
                    throw handleMixedClassSetOperators(operator, newOperator);
                }
            }

            if (atEnd()) {
                break;
            }
            if (curChar() == ']') {
                throw handleMissingClassSetOperand(newOperator);
            }

            ClassSetContents operand = parseClassSetOperandOrRange();
            if (operand.isRange() && operator != null && operator != ClassSetOperator.Union) {
                throw handleRangeAsClassSetOperand(operator);
            }
            if (operator == null) {
                // first operand
                curClassSet.addAll(operand);
                firstOperandIsRange = operand.isRange();
            } else {
                switch (operator) {
                    case Union -> curClassSet.addAll(operand);
                    case Intersection -> curClassSet.retainAll(operand);
                    case Difference -> curClassSet.removeAll(operand, encoding);
                }
            }
        }
        throw handleUnmatchedLeftBracket();
    }

    private ClassSetOperator parseClassSetOperator() {
        if (consumingLookahead("&&")) {
            if (lookahead("&")) {
                return handleTripleAmpersandInClassSetExpression();
            }
            return ClassSetOperator.Intersection;
        } else if (featureEnabledClassSetDifference() && consumingLookahead("--")) {
            return ClassSetOperator.Difference;
        } else {
            return ClassSetOperator.Union;
        }
    }

    private ClassSetContents parseClassSetOperandOrRange() {
        int startPos = position;
        charClassCurAtomStartIndex = position;
        char c = consumeChar();
        ClassSetContents contents = parseClassSetStrings(c);
        if (contents != null) {
            return caseFoldClassSetAtom(contents);
        }
        contents = parseCharClassAtomPredefCharClass(c);
        if (contents != null) {
            return contents;
        }
        if (c == '[') {
            return parseClassSetExpression();
        } else {
            int firstCodePoint = parseCharClassAtomCodePoint(c);
            if (lookahead("-") && !lookahead("--")) {
                advance();
                if (atEnd()) {
                    throw handleUnmatchedLeftBracket();
                }
                if (curChar() == ']') {
                    throw handleUnfinishedRangeInClassSet();
                }
                int secondCodePoint = parseCharClassAtomCodePoint(consumeChar());
                if (secondCodePoint < firstCodePoint) {
                    throw handleCCRangeOutOfOrder(startPos);
                }
                return caseFoldClassSetAtom(ClassSetContents.createRange(firstCodePoint, secondCodePoint));
            } else {
                return caseFoldClassSetAtom(ClassSetContents.createCharacter(firstCodePoint));
            }
        }
    }

    private ClassSetContents parseClassSetStrings(char c) {
        if (c == '\\' && consumingLookahead("q{")) {
            EconomicSet<String> strings = EconomicSet.create();
            CodePointSetAccumulator singleCodePoints = new CodePointSetAccumulator();
            do {
                String string = parseClassSetString();
                if (string.codePointCount(0, string.length()) == 1) {
                    singleCodePoints.addCodePoint(string.codePointAt(0));
                } else {
                    strings.add(string);
                }
                if (atEnd()) {
                    throw syntaxError(JsErrorMessages.UNTERMINATED_STRING_SET);
                }
            } while (consumingLookahead('|'));
            if (atEnd()) {
                throw syntaxError(JsErrorMessages.UNTERMINATED_STRING_SET);
            }
            assert curChar() == '}';
            advance();
            return ClassSetContents.createStrings(singleCodePoints.toCodePointSet(), strings);
        } else {
            return null;
        }
    }

    private String parseClassSetString() {
        StringBuilder sb = new StringBuilder();
        while (!atEnd() && curChar() != '|' && curChar() != '}') {
            if (consumingLookahead('\\')) {
                if (atEnd()) {
                    handleUnfinishedEscape();
                }
                sb.appendCodePoint(parseEscapeChar(consumeChar(), true));
            } else {
                sb.append(consumeChar());
            }
        }
        return sb.toString();
    }

    protected ClassSetContents parseUnicodeCharacterProperty(boolean invert) throws RegexSyntaxException {
        if (!consumingLookahead("{")) {
            throw syntaxError(JsErrorMessages.INVALID_UNICODE_PROPERTY);
        }
        int namePos = position;
        while (!atEnd() && curChar() != '}') {
            advance();
        }
        if (!consumingLookahead("}")) {
            throw syntaxError(JsErrorMessages.ENDS_WITH_UNFINISHED_UNICODE_PROPERTY);
        }
        try {
            String propertyName = pattern.substring(namePos, position - 1);
            if (featureEnabledClassSetExpressions()) {
                ClassSetContents property = UnicodeProperties.getPropertyOfStrings(propertyName);
                if (invert) {
                    if (property.mayContainStrings()) {
                        throw handleComplementOfStringSet();
                    }
                    assert property.isCodePointSetOnly();
                    property = caseFoldClassSetAtom(property);
                    return ClassSetContents.createCharacterClass(complementClassSet(property.getCodePointSet()));
                } else {
                    return caseFoldClassSetAtom(property);
                }
            } else {
                CodePointSet propertySet = UnicodeProperties.getProperty(propertyName);
                return ClassSetContents.createCharacterClass(invert ? propertySet.createInverse(encoding) : propertySet);
            }
        } catch (IllegalArgumentException e) {
            throw syntaxError(e.getMessage());
        }
    }

    private int parseEscapeChar(char c, boolean inCharClass) throws RegexSyntaxException {
        int custom = parseCustomEscapeChar(c, inCharClass);
        if (custom >= 0) {
            return custom;
        }
        switch (c) {
            case 'b':
                assert inCharClass;
                return '\b';
            case 'f':
                return '\f';
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'v':
                return '\u000B';
            case 'x':
                if (!consumingLookahead(RegexLexer::isHexDigit, 2)) {
                    int handle = handleIncompleteEscapeX();
                    if (handle != -1) {
                        return handle;
                    }

                    return c;
                }
                return Integer.parseInt(pattern, position - 2, position, 16);
            default:
                if (featureEnabledOctalEscapes() && isOctalDigit(c)) {
                    return parseOctal(c - '0', 2);
                }
                return parseCustomEscapeCharFallback(toCodePoint(c), inCharClass);
        }
    }

    private int toCodePoint(char c) {
        if (encoding != Encodings.UTF_16_RAW && Character.isHighSurrogate(c)) {
            return finishSurrogatePair(c);
        }
        return c;
    }

    protected int finishSurrogatePair(char c) {
        assert Character.isHighSurrogate(c);
        if (!atEnd() && Character.isLowSurrogate(curChar())) {
            return Character.toCodePoint(c, consumeChar());
        } else {
            return c;
        }
    }

    protected int parseOctal(int firstDigit, int maxDigits) {
        int ret = firstDigit;
        for (int i = 0; !atEnd() && isOctalDigit(curChar()) && i < maxDigits; i++) {
            if (ret * 8 > 255) {
                handleOctalOutOfRange();
                return ret;
            }
            ret *= 8;
            ret += consumeChar() - '0';
        }
        return ret;
    }

    private boolean isEscapeCharClass(char c) {
        return isPredefCharClass(c) || (featureEnabledUnicodePropertyEscapes() && (c == 'p' || c == 'P'));
    }

    public RegexSyntaxException syntaxError(String msg) {
        return RegexSyntaxException.createPattern(source, msg, getLastAtomPosition());
    }

    public static boolean isDecimalDigit(int c) {
        return '0' <= c && c <= '9';
    }

    public static boolean isOctalDigit(int c) {
        return '0' <= c && c <= '7';
    }

    public static boolean isHexDigit(int c) {
        return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
    }

    public static boolean isAscii(int c) {
        return Integer.compareUnsigned(c, 128) < 0;
    }
}
