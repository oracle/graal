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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import org.graalvm.shadowed.com.ibm.icu.lang.UCharacter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.chardata.UnicodeCharacterAliases;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.PyErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

public final class PythonRegexLexer extends RegexLexer {

    private static final CodePointSet ASCII_WHITESPACE = CodePointSet.createNoDedup(0x09, 0x0d, 0x20, 0x20);
    private static final CodePointSet ASCII_NON_WHITESPACE = CodePointSet.createNoDedup(0x00, 0x08, 0x0e, 0x1f, 0x21, 0x10ffff);

    /**
     * The (slightly modified) version of the XID_Start Unicode property used to check names of
     * capture groups.
     */
    private static final CodePointSet XID_START = UnicodeProperties.getProperty("XID_Start").union(CodePointSet.create('_'));
    /**
     * The XID_Continue Unicode character property.
     */
    private static final CodePointSet XID_CONTINUE = UnicodeProperties.getProperty("XID_Continue");

    /**
     * Maps Python's predefined Unicode character classes to sets containing the characters to be
     * matched.
     */
    private static final Map<Character, CodePointSet> UNICODE_CHAR_CLASS_SETS;
    // "[^\n]"
    private static final CodePointSet PYTHON_DOT = CodePointSet.createNoDedup(0, '\n' - 1, '\n' + 1, 0x10ffff);

    static {
        UNICODE_CHAR_CLASS_SETS = new HashMap<>();

        // Digits: \d
        // Python accepts characters with the Numeric_Type=Decimal property.
        // As of Unicode 11.0.0, these happen to be exactly the characters
        // in the Decimal_Number General Category.
        UNICODE_CHAR_CLASS_SETS.put('d', UnicodeProperties.getProperty("General_Category=Decimal_Number"));
        // Non-digits: \D
        UNICODE_CHAR_CLASS_SETS.put('D', UnicodeProperties.getProperty("General_Category=Decimal_Number").createInverse(Encodings.UTF_32));

        // Spaces: \s
        // Python accepts characters with either the Space_Separator General Category
        // or one of the WS, B or S Bidi_Classes. A close analogue available in
        // ECMAScript regular expressions is the White_Space Unicode property,
        // which is only missing the characters \\u001c-\\u001f (as of Unicode 11.0.0).
        // Non-spaces: \S
        // If we are translating an occurrence of \S inside a character class, we cannot
        // use the negated Unicode character property \P{White_Space}, because then we would
        // need to subtract the code points \\u001c-\\u001f from the resulting character class,
        // which is not possible in ECMAScript regular expressions. Therefore, we have to expand
        // the definition of the White_Space property, do the set subtraction and then list the
        // contents of the resulting set.
        CodePointSet unicodeSpaces = UnicodeProperties.getProperty("White_Space");
        CodePointSet spaces = unicodeSpaces.union(CodePointSet.createNoDedup('\u001c', '\u001f'));
        CodePointSet nonSpaces = spaces.createInverse(Encodings.UTF_32);
        UNICODE_CHAR_CLASS_SETS.put('s', spaces);
        UNICODE_CHAR_CLASS_SETS.put('S', nonSpaces);

        // Word characters: \w
        // As alphabetic characters, Python accepts those in the general category L.
        // As numeric, it takes any character with either Numeric_Type=Decimal,
        // Numeric_Type=Digit or Numeric_Type=Numeric. As of Unicode 11.0.0, this
        // corresponds to the general category Number, along with the following
        // code points:
        // F96B;CJK COMPATIBILITY IDEOGRAPH-F96B;Lo;0;L;53C3;;;3;N;;;;;
        // F973;CJK COMPATIBILITY IDEOGRAPH-F973;Lo;0;L;62FE;;;10;N;;;;;
        // F978;CJK COMPATIBILITY IDEOGRAPH-F978;Lo;0;L;5169;;;2;N;;;;;
        // F9B2;CJK COMPATIBILITY IDEOGRAPH-F9B2;Lo;0;L;96F6;;;0;N;;;;;
        // F9D1;CJK COMPATIBILITY IDEOGRAPH-F9D1;Lo;0;L;516D;;;6;N;;;;;
        // F9D3;CJK COMPATIBILITY IDEOGRAPH-F9D3;Lo;0;L;9678;;;6;N;;;;;
        // F9FD;CJK COMPATIBILITY IDEOGRAPH-F9FD;Lo;0;L;4EC0;;;10;N;;;;;
        // 2F890;CJK COMPATIBILITY IDEOGRAPH-2F890;Lo;0;L;5EFE;;;9;N;;;;;
        // Non-word characters: \W
        // Similarly as for \S, we will not be able to produce a replacement string for \W.
        // We will need to construct the set ourselves.
        CodePointSet alpha = UnicodeProperties.getProperty("General_Category=Letter");
        CodePointSet numericExtras = CodePointSet.createNoDedup(0xf96b, 0xf973, 0xf978, 0xf9b2, 0xf9d1, 0xf9d3, 0xf9fd, 0x2f890);
        CodePointSet numeric = UnicodeProperties.getProperty("General_Category=Number").union(numericExtras);
        CodePointSet wordChars = alpha.union(numeric).union(CodePointSet.create('_'));
        CodePointSet nonWordChars = wordChars.createInverse(Encodings.UTF_32);
        UNICODE_CHAR_CLASS_SETS.put('w', wordChars);
        UNICODE_CHAR_CLASS_SETS.put('W', nonWordChars);
    }

    /**
     * Indicates whether the regex being parsed is a 'str' pattern or a 'bytes' pattern.
     */
    private final PythonREMode mode;
    /**
     * A stack of the locally enabled flags. Python enables the setting and unsetting of the flags
     * for subexpressions of the regex.
     * <p>
     * The currently active flags are at the top, the flags that would become active after the end
     * of the next (?aiLmsux-imsx:...) expression are just below.
     */
    private final Deque<PythonFlags> flagsStack = new ArrayDeque<>();
    /**
     * The global flags are the flags given when compiling the regular expression. Note that these
     * flags <em>can</em> be changed inline, in the pattern.
     */
    private PythonFlags globalFlags;
    private final CodePointSetAccumulator caseFoldTmp = new CodePointSetAccumulator();
    private PythonLocaleData localeData;

    public PythonRegexLexer(RegexSource source, PythonREMode mode, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        this.mode = mode;
        this.globalFlags = new PythonFlags(source.getFlags());
        parseInlineGlobalFlags();
        if (globalFlags.isVerbose() && source.getFlags().indexOf('x') == -1) {
            // The global verbose flag was set inside the expression. Let's scan it for global
            // flags again.
            globalFlags = new PythonFlags(source.getFlags()).addFlag('x');
            parseInlineGlobalFlags();
        }
        globalFlags = globalFlags.fixFlags(source, mode);
    }

    private static int lookupCharacterByName(String characterName) {
        // CPython's logic for resolving these character names goes like this:
        // 1) handle Hangul Syllables in region AC00-D7A3
        // 2) handle CJK Ideographs
        // 3) handle character names as given in UnicodeData.txt
        // 4) handle all aliases as given in NameAliases.txt
        // With ICU's UCharacter, we get cases 1), 2) and 3). As for 4), the aliases, ICU only
        // handles aliases of type 'correction'. Therefore, we extract the contents of
        // NameAliases.txt and handle aliases by ourselves.
        String normalizedName = characterName.trim().toUpperCase(Locale.ROOT);
        if (UnicodeCharacterAliases.CHARACTER_ALIASES.containsKey(normalizedName)) {
            return UnicodeCharacterAliases.CHARACTER_ALIASES.get(normalizedName);
        } else {
            return UCharacter.getCharFromName(characterName);
        }
    }

    public PythonLocaleData getLocaleData() {
        if (localeData == null) {
            try {
                localeData = PythonLocaleData.getLocaleData(source.getOptions().getPythonLocale());
            } catch (IllegalArgumentException e) {
                throw new UnsupportedRegexException(e.getMessage(), source);
            }
        }
        return localeData;
    }

    private void parseInlineGlobalFlags() {
        Deque<Boolean> groupVerbosityStack = new ArrayDeque<>();
        groupVerbosityStack.push(globalFlags.isVerbose());
        while (findChars('[', '(', ')', '#')) {
            if (isEscaped()) {
                advance();
            } else {
                switch (consumeChar()) {
                    case '[':
                        // skip first char after '[' because a ']' directly after the opening
                        // bracket doesn't close the character class in python
                        advance();
                        // find end of character class
                        while (findChars(']')) {
                            if (!isEscaped()) {
                                break;
                            }
                            advance();
                        }
                        break;
                    case '(':
                        if (consumingLookahead("?") && !atEnd()) {
                            int ch = consumeChar();
                            if (ch == '#') {
                                while (findChars(')')) {
                                    if (!isEscaped()) {
                                        break;
                                    }
                                    advance();
                                }
                            } else {
                                PythonFlags positiveFlags = PythonFlags.EMPTY_INSTANCE;
                                while (!atEnd() && PythonFlags.isValidFlagChar(ch)) {
                                    positiveFlags = addFlag(positiveFlags, ch);
                                    ch = consumeChar();
                                }
                                if (!positiveFlags.equals(PythonFlags.EMPTY_INSTANCE) || ch == '-') {
                                    switch (ch) {
                                        case ')':
                                            globalFlags = globalFlags.addFlags(positiveFlags);
                                            break;
                                        case ':':
                                            groupVerbosityStack.push(groupVerbosityStack.peek() || positiveFlags.isVerbose());
                                            break;
                                        case '-':
                                            if (atEnd()) {
                                                // malformed local flags
                                                continue;
                                            }
                                            ch = consumeChar();
                                            PythonFlags negativeFlags = PythonFlags.EMPTY_INSTANCE;
                                            while (!atEnd() && PythonFlags.isValidFlagChar(ch)) {
                                                negativeFlags = negativeFlags.addFlag(ch);
                                                ch = consumeChar();
                                            }
                                            groupVerbosityStack.push((groupVerbosityStack.peek() || positiveFlags.isVerbose()) && !negativeFlags.isVerbose());
                                            break;
                                        default:
                                            // malformed local flags
                                            break;
                                    }
                                } else {
                                    // not inline flags after all
                                    groupVerbosityStack.push(groupVerbosityStack.peek());
                                }
                            }
                        } else {
                            groupVerbosityStack.push(groupVerbosityStack.peek());
                        }
                        break;
                    case ')':
                        if (groupVerbosityStack.size() > 1) {
                            groupVerbosityStack.pop();
                        }
                        break;
                    case '#':
                        if (groupVerbosityStack.peek() && findChars('\n')) {
                            advance();
                        }
                        break;
                }
            }
        }
        position = 0;
    }

    PythonFlags getGlobalFlags() {
        return globalFlags;
    }

    PythonFlags getLocalFlags() {
        return flagsStack.isEmpty() ? globalFlags : flagsStack.peek();
    }

    public void popLocalFlags() {
        flagsStack.pop();
    }

    @Override
    protected boolean featureEnabledIgnoreCase() {
        return getLocalFlags().isIgnoreCase();
    }

    @Override
    protected boolean featureEnabledAZPositionAssertions() {
        return true;
    }

    @Override
    protected boolean featureEnabledZLowerCaseAssertion() {
        return false;
    }

    @Override
    protected boolean featureEnabledWordBoundaries() {
        return true;
    }

    @Override
    protected boolean featureEnabledBoundedQuantifierEmptyMin() {
        return true;
    }

    @Override
    protected boolean featureEnabledCharClassFirstBracketIsLiteral() {
        return true;
    }

    @Override
    protected boolean featureEnabledNestedCharClasses() {
        return false;
    }

    @Override
    protected boolean featureEnabledPOSIXCharClasses() {
        return false;
    }

    @Override
    protected boolean featureEnabledForwardReferences() {
        return false;
    }

    @Override
    protected boolean featureEnabledGroupComments() {
        return true;
    }

    @Override
    protected boolean featureEnabledLineComments() {
        return getLocalFlags().isVerbose();
    }

    @Override
    protected boolean featureEnabledIgnoreWhiteSpace() {
        return false;
    }

    @Override
    protected TBitSet getWhitespace() {
        return DEFAULT_WHITESPACE;
    }

    @Override
    protected boolean featureEnabledOctalEscapes() {
        return true;
    }

    @Override
    protected boolean featureEnabledSpecialGroups() {
        return true;
    }

    @Override
    protected boolean featureEnabledUnicodePropertyEscapes() {
        return false;
    }

    @Override
    protected boolean featureEnabledClassSetExpressions() {
        return false;
    }

    @Override
    protected CodePointSet getDotCodePointSet() {
        return getLocalFlags().isDotAll() ? Constants.DOT_ALL : PYTHON_DOT;
    }

    @Override
    protected CodePointSet getIdContinue() {
        return XID_CONTINUE;
    }

    @Override
    protected CodePointSet getIdStart() {
        return XID_START;
    }

    @Override
    protected int getMaxBackReferenceDigits() {
        return 2;
    }

    @Override
    protected void caseFoldUnfold(CodePointSetAccumulator charClass) {
        if (getLocalFlags().isLocale()) {
            getLocaleData().caseFoldUnfold(charClass, caseFoldTmp);
        } else {
            CaseFoldData.CaseFoldUnfoldAlgorithm caseFolding = getLocalFlags().isUnicode(mode) ? CaseFoldData.CaseFoldUnfoldAlgorithm.PythonUnicode : CaseFoldData.CaseFoldUnfoldAlgorithm.PythonAscii;
            CaseFoldData.applyCaseFoldUnfold(charClass, caseFoldTmp, caseFolding);
        }
    }

    @Override
    protected CodePointSet complementClassSet(CodePointSet codePointSet) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected ClassSetContents caseFoldClassSetAtom(ClassSetContents classSetContents) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected CodePointSet getPredefinedCharClass(char c) {
        if (getLocalFlags().isUnicode(mode)) {
            return UNICODE_CHAR_CLASS_SETS.get(c);
        }
        switch (c) {
            case 'd':
                return Constants.DIGITS;
            case 'D':
                return Constants.NON_DIGITS;
            case 's':
                if (mode == PythonREMode.Bytes || getLocalFlags().isAscii()) {
                    return ASCII_WHITESPACE;
                }
                return Constants.WHITE_SPACE;
            case 'S':
                if (mode == PythonREMode.Bytes || getLocalFlags().isAscii()) {
                    return ASCII_NON_WHITESPACE;
                }
                return Constants.NON_WHITE_SPACE;
            case 'w':
                if (getLocalFlags().isLocale()) {
                    return getLocaleData().getWordCharacters();
                } else {
                    return Constants.WORD_CHARS;
                }
            case 'W':
                if (getLocalFlags().isLocale()) {
                    return getLocaleData().getNonWordCharacters();
                } else {
                    return Constants.NON_WORD_CHARS;
                }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected void checkClassSetCharacter(int codePoint) throws RegexSyntaxException {
    }

    @Override
    protected long boundedQuantifierMaxValue() {
        return Integer.MAX_VALUE;
    }

    private RegexSyntaxException handleBadCharacterInGroupName(ParseGroupNameResult result) {
        return syntaxErrorAtRel(PyErrorMessages.badCharacterInGroupName(result.groupName), result.groupName.length() + 1);
    }

    @Override
    protected RegexSyntaxException handleBoundedQuantifierOutOfOrder() {
        return syntaxErrorAtAbs(PyErrorMessages.MIN_REPEAT_GREATER_THAN_MAX_REPEAT, getLastTokenPosition() + 1);
    }

    @Override
    protected Token handleBoundedQuantifierSyntaxError() throws RegexSyntaxException {
        position = getLastTokenPosition() + 1;
        return literalChar('{');
    }

    @Override
    protected Token handleBoundedQuantifierOverflow(long min, long max) {
        return null;
    }

    @Override
    protected Token handleBoundedQuantifierOverflowMin(long min, long max) {
        return null;
    }

    @Override
    protected RegexSyntaxException handleCCRangeOutOfOrder(int rangeStart) {
        return syntaxErrorAtAbs(PyErrorMessages.badCharacterRange(pattern.substring(rangeStart, position)), rangeStart);
    }

    @Override
    protected void handleCCRangeWithPredefCharClass(int rangeStart, ClassSetContents firstAtom, ClassSetContents secondAtom) {
        throw syntaxErrorAtAbs(PyErrorMessages.badCharacterRange(pattern.substring(rangeStart, position)), rangeStart);
    }

    @Override
    protected CodePointSet getPOSIXCharClass(String name) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void validatePOSIXCollationElement(String sequence) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void validatePOSIXEquivalenceClass(String sequence) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleComplementOfStringSet() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleEmptyGroupName() {
        return syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
    }

    @Override
    protected void handleGroupRedefinition(String name, int newId, int oldId) {
        throw syntaxErrorAtRel(PyErrorMessages.redefinitionOfGroupName(name, newId, oldId), name.length() + 1);
    }

    @Override
    protected void handleIncompleteEscapeX() {
        throw syntaxError(PyErrorMessages.incompleteEscape(substring(2 + count(RegexLexer::isHexDigit))));
    }

    @Override
    protected void handleInvalidBackReference(int reference) {
        String ref = Integer.toString(reference);
        throw syntaxErrorAtRel(PyErrorMessages.invalidGroupReference(ref), ref.length());
    }

    @Override
    protected void handleInvalidBackReference(String reference) {
        throw syntaxErrorAtRel(PyErrorMessages.invalidGroupReference(reference), reference.length());
    }

    @Override
    protected RegexSyntaxException handleInvalidCharInCharClass() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleInvalidGroupBeginQ() {
        retreat();
        return syntaxErrorAtAbs(PyErrorMessages.unknownExtensionQ(curChar()), getLastTokenPosition() + 1);
    }

    @Override
    protected RegexSyntaxException handleMixedClassSetOperators(ClassSetOperator leftOperator, ClassSetOperator rightOperator) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleMissingClassSetOperand(ClassSetOperator operator) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleOctalOutOfRange() {
        throw syntaxError(PyErrorMessages.invalidOctalEscape(substring(4)));
    }

    @Override
    protected RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnfinishedEscape() {
        throw syntaxError(PyErrorMessages.BAD_ESCAPE_END_OF_PATTERN);
    }

    @Override
    protected void handleUnfinishedGroupComment() {
        throw syntaxError(PyErrorMessages.UNTERMINATED_COMMENT);
    }

    @Override
    protected RegexSyntaxException handleUnfinishedGroupQ() {
        return syntaxErrorHere(PyErrorMessages.UNEXPECTED_END_OF_PATTERN);
    }

    @Override
    protected RegexSyntaxException handleUnfinishedRangeInClassSet() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnmatchedRightBrace() {
        // not an error in python
    }

    @Override
    protected RegexSyntaxException handleUnmatchedLeftBracket() {
        return syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_CHARACTER_SET, getLastCharacterClassBeginPosition());
    }

    @Override
    protected void handleUnmatchedRightBracket() {
        // not an error in python
    }

    @Override
    protected int parseCodePointInGroupName() throws RegexSyntaxException {
        final char c = consumeChar();
        return Character.isHighSurrogate(c) ? finishSurrogatePair(c) : c;
    }

    @Override
    protected Token parseCustomEscape(char c) {
        if (isOctalDigit(c) && lookahead(RegexLexer::isOctalDigit, 2)) {
            int codePoint = (c - '0') * 64 + (consumeChar() - '0') * 8 + (consumeChar() - '0');
            if (codePoint > 0xff) {
                handleOctalOutOfRange();
            }
            return literalChar(codePoint);
        }
        return null;
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        switch (c) {
            case 'a':
                return '\u0007';
            case 'u':
            case 'U':
                // 'u' and 'U' escapes are supported only in 'str' patterns
                if (mode == PythonREMode.Str) {
                    int escapeLength;
                    switch (c) {
                        case 'u':
                            escapeLength = 4;
                            break;
                        case 'U':
                            escapeLength = 8;
                            break;
                        default:
                            throw CompilerDirectives.shouldNotReachHere();
                    }
                    int length = countUpTo(RegexLexer::isHexDigit, escapeLength);
                    if (length != escapeLength) {
                        throw syntaxError(PyErrorMessages.incompleteEscape(substring(2 + length)));
                    }
                    advance(length);
                    try {
                        int codePoint = Integer.parseInt(pattern, position - length, position, 16);
                        if (codePoint > 0x10FFFF) {
                            throw syntaxError(PyErrorMessages.invalidUnicodeEscape(substring(2 + length)));
                        }
                        return codePoint;
                    } catch (NumberFormatException e) {
                        throw syntaxError(PyErrorMessages.incompleteEscape(substring(2 + length)));
                    }
                } else {
                    // \\u or \\U in 'bytes' patterns
                    throw syntaxError(PyErrorMessages.badEscape(c));
                }
            case 'N': {
                if (mode != PythonREMode.Str) {
                    throw syntaxError(PyErrorMessages.badEscape(c));
                }
                if (!consumingLookahead("{")) {
                    throw syntaxErrorHere(PyErrorMessages.missing("{"));
                }
                int nameStart = position;
                int nameEnd = pattern.indexOf('}', position);
                if (atEnd() || nameEnd == position) {
                    throw syntaxErrorHere(PyErrorMessages.missing("character name"));
                }
                if (nameEnd < 0) {
                    throw syntaxErrorHere(PyErrorMessages.missingUnterminatedName('}'));
                }
                String characterName = pattern.substring(nameStart, nameEnd);
                position = nameEnd + 1;
                int codePoint = lookupCharacterByName(characterName);
                if (codePoint == -1) {
                    throw syntaxError(PyErrorMessages.undefinedCharacterName(characterName));
                }
                return codePoint;
            }
            default:
                return -1;
        }
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
            throw syntaxError(PyErrorMessages.badEscape(c));
        }
        return c;
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
        switch (charAfterQuestionMark) {
            case 'P': {
                mustHaveMore();
                final int ch2 = consumeChar();
                switch (ch2) {
                    case '<': {
                        int pos = position;
                        ParseGroupNameResult result = parseGroupName('>');
                        switch (result.state) {
                            case empty:
                                throw syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
                            case unterminated:
                                throw syntaxErrorAtAbs(PyErrorMessages.UNTERMINATED_NAME_ANGLE_BRACKET, pos);
                            case invalidStart:
                            case invalidRest:
                                throw handleBadCharacterInGroupName(result);
                            case valid:
                                registerNamedCaptureGroup(result.groupName);
                                break;
                            default:
                                throw CompilerDirectives.shouldNotReachHere();
                        }
                        return Token.createCaptureGroupBegin();
                    }
                    case '=': {
                        return parseNamedBackReference();
                    }
                    default:
                        throw syntaxErrorAtRel(PyErrorMessages.unknownExtensionP(ch2), 3);
                }
            }
            case '(':
                return parseConditionalBackReference();
            case '-':
            case 'i':
            case 'L':
            case 'm':
            case 's':
            case 'x':
            case 'a':
            case 't':
            case 'u':
                return parseInlineFlags(charAfterQuestionMark);
            default:
                return null;
        }
    }

    @Override
    protected Token parseGroupLt() {
        if (atEnd()) {
            throw syntaxErrorHere(PyErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
        throw syntaxErrorAtAbs(PyErrorMessages.unknownExtensionLt(curChar()), getLastTokenPosition() + 1);
    }

    /**
     * Parses a conditional back-reference, assuming that the prefix '(?(' was already parsed.
     */
    private Token parseConditionalBackReference() {
        final int groupNumber;
        final boolean namedReference;
        ParseGroupNameResult result = parseGroupName(')');
        switch (result.state) {
            case empty:
                throw syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
            case unterminated:
                throw syntaxErrorAtRel(PyErrorMessages.UNTERMINATED_NAME, result.groupName.length());
            case invalidStart:
            case invalidRest:
                position -= result.groupName.length() + 1;
                assert lookahead(result.groupName + ")");
                int groupNumberLength = countDecimalDigits();
                if (groupNumberLength != result.groupName.length()) {
                    position += result.groupName.length() + 1;
                    throw handleBadCharacterInGroupName(result);
                }
                groupNumber = parseIntSaturated(0, result.groupName.length(), -1);
                namedReference = false;
                assert curChar() == ')';
                advance();
                if (groupNumber == 0) {
                    throw syntaxErrorAtRel(PyErrorMessages.BAD_GROUP_NUMBER, result.groupName.length() + 1);
                } else if (groupNumber == -1) {
                    throw syntaxErrorAtRel(PyErrorMessages.invalidGroupReference(result.groupName), result.groupName.length() + 1);
                }
                break;
            case valid:
                // group referenced by name
                if (namedCaptureGroups != null && namedCaptureGroups.containsKey(result.groupName)) {
                    assert namedCaptureGroups.get(result.groupName).size() == 1;
                    groupNumber = namedCaptureGroups.get(result.groupName).get(0);
                    namedReference = true;
                } else {
                    throw syntaxErrorAtRel(PyErrorMessages.unknownGroupName(result.groupName), result.groupName.length() + 1);
                }
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
        return Token.createConditionalBackReference(groupNumber, namedReference);
    }

    /**
     * Parses a local flag block or an inline declaration of a global flags. Assumes that the prefix
     * '(?' was already parsed, as well as the first flag which is passed as the argument.
     */
    private Token parseInlineFlags(int ch0) {
        int ch = ch0;
        PythonFlags positiveFlags = PythonFlags.EMPTY_INSTANCE;
        while (PythonFlags.isValidFlagChar(ch)) {
            positiveFlags = addFlag(positiveFlags, ch);
            ch = consumeChar();
        }
        switch (ch) {
            case ')':
                return Token.createInlineFlags(positiveFlags, true);
            case ':':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_ON_GLOBAL_FLAG, 1);
                }
                return parseLocalFlags(positiveFlags, PythonFlags.EMPTY_INSTANCE);
            case '-':
                if (positiveFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_ON_GLOBAL_FLAG, 1);
                }
                if (atEnd()) {
                    throw syntaxErrorHere(PyErrorMessages.MISSING_FLAG);
                }
                ch = consumeChar();
                if (!PythonFlags.isValidFlagChar(ch)) {
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                    } else {
                        throw syntaxErrorAtRel(PyErrorMessages.MISSING_FLAG, 1);
                    }
                }
                PythonFlags negativeFlags = PythonFlags.EMPTY_INSTANCE;
                while (PythonFlags.isValidFlagChar(ch)) {
                    negativeFlags = negativeFlags.addFlag(ch);
                    if (PythonFlags.isTypeFlagChar(ch)) {
                        throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_OFF_FLAGS_A_U_AND_L);
                    }
                    if (atEnd()) {
                        throw syntaxErrorHere(PyErrorMessages.MISSING_COLON);
                    }
                    ch = consumeChar();
                }
                if (ch != ':') {
                    if (Character.isAlphabetic(ch)) {
                        throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                    } else {
                        throw syntaxErrorAtRel(PyErrorMessages.MISSING_COLON, 1);
                    }
                }
                if (negativeFlags.includesGlobalFlags()) {
                    throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_CANNOT_TURN_OFF_GLOBAL_FLAG, 1);
                }
                return parseLocalFlags(positiveFlags, negativeFlags);
            default:
                if (Character.isAlphabetic(ch)) {
                    throw syntaxErrorAtRel(PyErrorMessages.UNKNOWN_FLAG, 1);
                } else {
                    throw syntaxErrorAtRel(PyErrorMessages.MISSING_DASH_COLON_PAREN, 1);
                }
        }
    }

    private PythonFlags addFlag(PythonFlags flagsArg, int ch) {
        PythonFlags flags = flagsArg.addFlag(ch);
        if (mode == PythonREMode.Str && ch == 'L') {
            throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_USE_L_FLAG_WITH_A_STR_PATTERN);
        }
        if (mode == PythonREMode.Bytes && ch == 'u') {
            throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_CANNOT_USE_U_FLAG_WITH_A_BYTES_PATTERN);
        }
        if (flags.numberOfTypeFlags() > 1) {
            throw syntaxErrorHere(PyErrorMessages.INLINE_FLAGS_FLAGS_A_U_AND_L_ARE_INCOMPATIBLE);
        }
        if (atEnd()) {
            throw syntaxErrorHere(PyErrorMessages.MISSING_DASH_COLON_PAREN);
        }
        return flags;
    }

    /**
     * Parses a block with local flags, assuming that the opening parenthesis, the flags and the ':'
     * have been parsed.
     *
     * @param positiveFlags - the flags to be turned on in the block
     * @param negativeFlags - the flags to be turned off in the block
     */
    private Token parseLocalFlags(PythonFlags positiveFlags, PythonFlags negativeFlags) {
        if (positiveFlags.overlaps(negativeFlags)) {
            throw syntaxErrorAtRel(PyErrorMessages.INLINE_FLAGS_FLAG_TURNED_ON_AND_OFF, 1);
        }
        PythonFlags newFlags = getLocalFlags().addFlags(positiveFlags).delFlags(negativeFlags);
        if (positiveFlags.numberOfTypeFlags() > 0) {
            PythonFlags otherTypes = PythonFlags.TYPE_FLAGS_INSTANCE.delFlags(positiveFlags);
            newFlags = newFlags.delFlags(otherTypes);
        }
        flagsStack.push(newFlags);
        return Token.createInlineFlags(newFlags, false);
    }

    private void mustHaveMore() {
        if (atEnd()) {
            throw syntaxErrorHere(PyErrorMessages.UNEXPECTED_END_OF_PATTERN);
        }
    }

    /**
     * Parses a named backreference, assuming that the prefix '(?P=' was already parsed.
     */
    private Token parseNamedBackReference() {
        ParseGroupNameResult result = parseGroupName(')');
        switch (result.state) {
            case empty:
                throw syntaxErrorHere(PyErrorMessages.MISSING_GROUP_NAME);
            case unterminated:
                throw syntaxErrorAtRel(PyErrorMessages.UNTERMINATED_NAME, result.groupName.length());
            case invalidStart:
            case invalidRest:
                throw handleBadCharacterInGroupName(result);
            case valid:
                if (namedCaptureGroups != null && namedCaptureGroups.containsKey(result.groupName)) {
                    assert namedCaptureGroups.get(result.groupName).size() == 1;
                    return Token.createBackReference(namedCaptureGroups.get(result.groupName).get(0), true);
                } else {
                    throw syntaxErrorAtRel(PyErrorMessages.unknownGroupName(result.groupName), result.groupName.length() + 1);
                }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private String substring(int length) {
        return pattern.substring(getLastAtomPosition(), getLastAtomPosition() + length);
    }

    public RegexSyntaxException syntaxErrorAtAbs(String msg, int i) {
        return RegexSyntaxException.createPattern(source, msg, i);
    }

    private RegexSyntaxException syntaxErrorAtRel(String msg, int i) {
        return RegexSyntaxException.createPattern(source, msg, position - i);
    }

    public RegexSyntaxException syntaxErrorHere(String msg) {
        return RegexSyntaxException.createPattern(source, msg, position);
    }
}
