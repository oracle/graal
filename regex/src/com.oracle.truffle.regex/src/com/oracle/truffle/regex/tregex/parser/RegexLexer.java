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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.util.TBitSet;

public abstract class RegexLexer {

    private static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'S', 'W', 'd', 's', 'w');
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\u000b', '\f', '\r', ' ');
    public final RegexSource source;
    /**
     * The source of the input pattern.
     */
    protected final String pattern;
    private final Encoding encoding;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();
    /**
     * The index of the next character in {@link #pattern} to be parsed.
     */
    protected int position = 0;
    protected Map<String, Integer> namedCaptureGroups = null;
    private int curStartIndex = 0;
    private int charClassCurAtomStartIndex = 0;
    private int nGroups = 1;
    private boolean identifiedAllGroups = false;

    public RegexLexer(RegexSource source) {
        this.source = source;
        this.pattern = source.getPattern();
        this.encoding = source.getEncoding();
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
     * Returns {@code true} if empty minimum values in bounded quantifiers (e.g. {@code {,1}}) are
     * allowed and treated as zero.
     */
    protected abstract boolean featureEnabledBoundedQuantifierEmptyMin();

    /**
     * Returns {@code true} if the first character in a character class must be interpreted as part
     * of the character set, even if it is the closing bracket {@code ']'}.
     */
    protected abstract boolean featureEnabledCharClassFirstBracketIsLiteral();

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
     * Returns {@code true} if octal escapes (e.g. {@code \012}) are supported.
     */
    protected abstract boolean featureEnabledOctalEscapes();

    /**
     * Returns {@code true} if unicode property escapes (e.g. {@code \p{...}}) are supported.
     */
    protected abstract boolean featureEnabledUnicodePropertyEscapes();

    /**
     * Case folds a given character class.
     */
    protected abstract void caseFold(CodePointSetAccumulator charClass);

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
     * Returns the CodePointSet associated with the given predefined character class (e.g.
     * {@code \d}).
     * <p>
     * Note that the CodePointSet returned by this function has already been case-folded and
     * negated.
     */
    protected abstract CodePointSet getPredefinedCharClass(char c);

    /**
     * Handle {@code {2,1}}.
     */
    protected abstract RegexSyntaxException handleBoundedQuantifierOutOfOrder();

    /**
     * Handle syntax errors in bounded quantifiers (missing {@code }}, non-digit characters).
     */
    protected abstract Token handleBoundedQuantifierSyntaxError();

    /**
     * Handle out of order character class range elements, e.g. {@code [b-a]}.
     */
    protected abstract RegexSyntaxException handleCCRangeOutOfOrder(int startPos);

    /**
     * Handle non-codepoint character class range elements, e.g. {@code [\w-a]}.
     */
    protected abstract void handleCCRangeWithPredefCharClass(int startPos);

    /**
     * Handle empty group name in group references.
     */
    protected abstract RegexSyntaxException handleEmptyGroupName();

    protected abstract RegexSyntaxException handleGroupRedefinition(String name, int newId, int oldId);

    /**
     * Handle incomplete hex escapes, e.g. {@code \x1}.
     */
    protected abstract void handleIncompleteEscapeX();

    /**
     * Handle group references to non-existent groups.
     */
    protected abstract void handleInvalidBackReference(int reference);

    /**
     * Handle group references to non-existent groups.
     */
    protected abstract void handleInvalidBackReference(String reference);

    /**
     * Handle groups starting with {@code (?} and invalid next char.
     */
    protected abstract RegexSyntaxException handleInvalidGroupBeginQ();

    /**
     * Handle octal values larger than 255.
     */
    protected abstract void handleOctalOutOfRange();

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
        }
        if (featureEnabledGroupComments()) {
            while (consumingLookahead("(?#")) {
                if (!skipComment(')')) {
                    handleUnfinishedGroupComment();
                }
            }
        }
        return !atEnd();
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
        while (!atEnd() && isWhitespace(curChar())) {
            advance();
        }
    }

    private static boolean isWhitespace(char curChar) {
        return WHITESPACE.get(curChar);
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

    private boolean consumingLookahead(char terminator) {
        if (atEnd()) {
            return false;
        }
        if (curChar() == terminator) {
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

    public Map<String, Integer> getNamedCaptureGroups() throws RegexSyntaxException {
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
        return getNamedCaptureGroups() != null;
    }

    private void registerCaptureGroup() {
        if (!identifiedAllGroups) {
            nGroups++;
        }
    }

    protected void registerNamedCaptureGroup(String name) {
        if (!identifiedAllGroups) {
            if (namedCaptureGroups == null) {
                namedCaptureGroups = new HashMap<>();
            }
            if (namedCaptureGroups.containsKey(name)) {
                throw handleGroupRedefinition(name, nGroups, namedCaptureGroups.get(name));
            }
            namedCaptureGroups.put(name, nGroups);
        }
        registerCaptureGroup();
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

    protected Token charClass(int codePoint) {
        if (featureEnabledIgnoreCase()) {
            curCharClass.clear();
            curCharClass.appendRange(codePoint, codePoint);
            return charClass(false);
        } else {
            return Token.createCharClass(CodePointSet.create(codePoint), true);
        }
    }

    private Token charClass(CodePointSet codePointSet) {
        if (featureEnabledIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addSet(codePointSet);
            return charClass(false);
        } else {
            return Token.createCharClass(codePointSet);
        }
    }

    private Token charClass(boolean invert) {
        boolean wasSingleChar = !invert && curCharClass.matchesSingleChar();
        if (featureEnabledIgnoreCase()) {
            caseFold(curCharClass);
        }
        CodePointSet cps = curCharClass.toCodePointSet();
        return Token.createCharClass(invert ? cps.createInverse(encoding) : cps, wasSingleChar);
    }

    /* lexer */

    private Token getNext() throws RegexSyntaxException {
        final char c = consumeChar();
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
                return charClass(c);
            case '|':
                return Token.createAlternation();
            case '(':
                return parseGroupBegin();
            case ')':
                return Token.createGroupEnd();
            case '[':
                return parseCharClass();
            case ']':
                handleUnmatchedRightBracket();
                return charClass(c);
            case '\\':
                return parseEscape();
            default:
                return charClass(toCodePoint(c));
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
        if ('1' <= c && c <= '9') {
            final int restoreIndex = position;
            final int backRefNumber = parseIntSaturated(c - '0', countDecimalDigits(getMaxBackReferenceDigits() - 1), Integer.MAX_VALUE);
            if (backRefNumber < (featureEnabledForwardReferences() ? totalNumberOfCaptureGroups() : nGroups)) {
                return Token.createBackReference(backRefNumber, false);
            } else {
                handleInvalidBackReference(backRefNumber);
            }
            position = restoreIndex;
        }
        if (featureEnabledAZPositionAssertions()) {
            if (c == 'A') {
                return Token.createA();
            } else if (c == 'Z') {
                return Token.createZ();
            }
        }
        switch (c) {
            case 'b':
                return Token.createWordBoundary();
            case 'B':
                return Token.createNonWordBoundary();
            default:
                // Here we differentiate the case when parsing one of the six basic pre-defined
                // character classes (\w, \W, \d, \D, \s, \S) and Unicode character property
                // escapes. Both result in sets of characters, but in the former case, we can skip
                // the case-folding step in the `charClass` method and call `Token::createCharClass`
                // directly.
                if (isPredefCharClass(c)) {
                    return Token.createCharClass(getPredefinedCharClass(c));
                } else if (featureEnabledUnicodePropertyEscapes() && (c == 'p' || c == 'P')) {
                    return charClass(parseUnicodeCharacterProperty(c == 'P'));
                } else {
                    return charClass(parseEscapeChar(c, false));
                }
        }
    }

    private Token parseGroupBegin() throws RegexSyntaxException {
        if (consumingLookahead("?")) {
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
        final int min;
        final int max;
        if (c == '{') {
            if (lookahead("}")) {
                return handleBoundedQuantifierSyntaxError();
            }
            final int resetIndex = position;
            final int lengthMin = countDecimalDigits();
            if (lengthMin == 0) {
                if (featureEnabledBoundedQuantifierEmptyMin()) {
                    min = 0;
                } else {
                    return handleBoundedQuantifierSyntaxError();
                }
            } else {
                min = parseIntSaturated(0, lengthMin, -1);
            }
            if (consumingLookahead(",}")) {
                max = -1;
            } else if (consumingLookahead("}")) {
                max = min;
            } else {
                if (!consumingLookahead(",")) {
                    return handleBoundedQuantifierSyntaxError();
                }
                final int lengthMax = countDecimalDigits();
                max = parseIntSaturated(0, lengthMax, -1);
                if (!consumingLookahead("}")) {
                    return handleBoundedQuantifierSyntaxError();
                }
                if (isQuantifierOutOfOrder(min, max, resetIndex, lengthMin, lengthMax)) {
                    throw handleBoundedQuantifierOutOfOrder();
                }
            }
        } else {
            min = c == '+' ? 1 : 0;
            max = c == '?' ? 1 : -1;
        }
        return Token.createQuantifier(min, max, !consumingLookahead("?"));
    }

    private boolean isQuantifierOutOfOrder(int parsedMin, int parsedMax, int startMin, int lengthMin, int lengthMax) {
        if (Integer.compareUnsigned(parsedMin, parsedMax) > 0) {
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
        int fromIndex = position;
        position += length;
        int ret = firstDigit;
        for (int i = fromIndex; i < fromIndex + length; i++) {
            int nextDigit = pattern.charAt(i) - '0';
            if (ret > Integer.MAX_VALUE / 10) {
                return returnOnOverflow;
            }
            ret *= 10;
            if (ret > Integer.MAX_VALUE - nextDigit) {
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

    private Token parseCharClass() throws RegexSyntaxException {
        final boolean invert = consumingLookahead("^");
        curCharClass.clear();
        int startPos = position;
        while (!atEnd()) {
            final char c = consumeChar();
            if (c == ']' && (!featureEnabledCharClassFirstBracketIsLiteral() || position != startPos + 1)) {
                return charClass(invert);
            }
            parseCharClassRange(c);
        }
        throw handleUnmatchedLeftBracket();
    }

    private CodePointSet parseCharClassAtomPredefCharClass(char c) throws RegexSyntaxException {
        if (c == '\\') {
            if (atEnd()) {
                handleUnfinishedEscape();
            }
            if (isEscapeCharClass(curChar())) {
                return parseEscapeCharClass(consumeChar());
            }
        }
        return null;
    }

    private int parseCharClassAtomCodePoint(char c) throws RegexSyntaxException {
        if (c == '\\') {
            assert !atEnd();
            assert !isEscapeCharClass(curChar());
            return parseEscapeChar(consumeChar(), true);
        } else {
            return toCodePoint(c);
        }
    }

    private void parseCharClassRange(char c) throws RegexSyntaxException {
        int startPos = position - 1;
        charClassCurAtomStartIndex = position - 1;
        CodePointSet firstAtomCC = parseCharClassAtomPredefCharClass(c);
        int firstAtomCP = firstAtomCC == null ? parseCharClassAtomCodePoint(c) : -1;
        if (consumingLookahead("-")) {
            if (atEnd() || lookahead("]")) {
                addCharClassAtom(firstAtomCC, firstAtomCP);
                curCharClass.addRange('-', '-');
            } else {
                char nextC = consumeChar();
                charClassCurAtomStartIndex = position - 1;
                CodePointSet secondAtomCC = parseCharClassAtomPredefCharClass(nextC);
                int secondAtomCP = secondAtomCC == null ? parseCharClassAtomCodePoint(nextC) : -1;
                // Runtime Semantics: CharacterRangeOrUnion(firstAtom, secondAtom)
                if (firstAtomCC != null || secondAtomCC != null) {
                    handleCCRangeWithPredefCharClass(startPos);
                    addCharClassAtom(firstAtomCC, firstAtomCP);
                    addCharClassAtom(secondAtomCC, secondAtomCP);
                    curCharClass.addRange('-', '-');
                } else {
                    if (secondAtomCP < firstAtomCP) {
                        throw handleCCRangeOutOfOrder(startPos);
                    } else {
                        curCharClass.addRange(firstAtomCP, secondAtomCP);
                    }
                }
            }
        } else {
            addCharClassAtom(firstAtomCC, firstAtomCP);
        }
    }

    private void addCharClassAtom(CodePointSet preDefCharClass, int codePoint) {
        if (preDefCharClass != null) {
            curCharClass.addSet(preDefCharClass);
        } else {
            curCharClass.addRange(codePoint, codePoint);
        }
    }

    private CodePointSet parseEscapeCharClass(char c) throws RegexSyntaxException {
        if (isPredefCharClass(c)) {
            return getPredefinedCharClass(c);
        } else if (featureEnabledUnicodePropertyEscapes() && (c == 'p' || c == 'P')) {
            return parseUnicodeCharacterProperty(c == 'P');
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private CodePointSet parseUnicodeCharacterProperty(boolean invert) throws RegexSyntaxException {
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
            CodePointSet propertySet = encoding.getFullSet().createIntersection(UnicodeProperties.getProperty(pattern.substring(namePos, position - 1)), curCharClass.getTmp());
            return invert ? propertySet.createInverse(encoding) : propertySet;
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
                    handleIncompleteEscapeX();
                    return c;
                }
                return Integer.parseInt(pattern, position - 2, position, 16);
            default:
                if (featureEnabledOctalEscapes() && isOctalDigit(c)) {
                    return parseOctal(c - '0');
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

    protected int parseOctal(int firstDigit) {
        int ret = firstDigit;
        for (int i = 0; !atEnd() && isOctalDigit(curChar()) && i < 2; i++) {
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

    @FunctionalInterface
    protected interface ErrorHandler extends Runnable {
    }

    private static boolean isPredefCharClass(char c) {
        return PREDEFINED_CHAR_CLASSES.get(c);
    }

    protected static boolean isDecimalDigit(int c) {
        return '0' <= c && c <= '9';
    }

    protected static boolean isOctalDigit(int c) {
        return '0' <= c && c <= '7';
    }

    protected static boolean isHexDigit(int c) {
        return '0' <= c && c <= '9' || 'a' <= c && c <= 'f' || 'A' <= c && c <= 'F';
    }
}
