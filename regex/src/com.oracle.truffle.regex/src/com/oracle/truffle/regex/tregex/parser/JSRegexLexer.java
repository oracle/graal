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

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.UnicodeProperties;
import com.oracle.truffle.regex.errors.JsErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.util.TBitSet;

public final class JSRegexLexer extends RegexLexer {

    private static final CodePointSet ID_START = UnicodeProperties.getProperty("ID_Start").union(CodePointSet.createNoDedup('$', '$', '_', '_'));
    private static final CodePointSet ID_CONTINUE = UnicodeProperties.getProperty("ID_Continue").union(CodePointSet.createNoDedup('$', '$', '\u200c', '\u200d'));
    private static final TBitSet SYNTAX_CHARS = TBitSet.valueOf('$', '(', ')', '*', '+', '.', '/', '?', '[', '\\', ']', '^', '{', '|', '}');
    private static final TBitSet CLASS_SET_SYNTAX_CHARS = TBitSet.valueOf('(', ')', '-', '/', '[', '\\', ']', '{', '|', '}');
    private static final TBitSet CLASS_SET_RESERVED_PUNCTUATORS = TBitSet.valueOf('!', '#', '%', '&', ',', '-', ':', ';', '<', '=', '>', '@', '`', '~');
    private static final TBitSet CLASS_SET_RESERVED_DOUBLE_PUNCTUATORS = TBitSet.valueOf('!', '#', '$', '%', '&', '*', '+', ',', '.', ':', ';', '<', '=', '>', '?', '@', '^', '`', '~');
    private final RegexFlags flags;

    public JSRegexLexer(RegexSource source, RegexFlags flags, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        this.flags = flags;
    }

    @Override
    protected boolean featureEnabledIgnoreCase() {
        return flags.isIgnoreCase();
    }

    @Override
    protected boolean featureEnabledAZPositionAssertions() {
        return false;
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
        return false;
    }

    @Override
    protected boolean featureEnabledPossessiveQuantifiers() {
        return false;
    }

    @Override
    protected boolean featureEnabledCharClassFirstBracketIsLiteral() {
        return false;
    }

    @Override
    protected boolean featureEnabledCCRangeWithPredefCharClass() {
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
        return true;
    }

    @Override
    protected boolean featureEnabledGroupComments() {
        return false;
    }

    @Override
    protected boolean featureEnabledLineComments() {
        return false;
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
        return !flags.isEitherUnicode();
    }

    @Override
    protected boolean featureEnabledSpecialGroups() {
        return true;
    }

    @Override
    protected boolean featureEnabledUnicodePropertyEscapes() {
        return flags.isEitherUnicode();
    }

    @Override
    protected boolean featureEnabledClassSetExpressions() {
        return flags.isUnicodeSets();
    }

    @Override
    protected boolean featureEnabledClassSetDifference() {
        return true;
    }

    @Override
    protected void caseFoldUnfold(CodePointSetAccumulator charClass) {
        CaseFoldData.CaseFoldUnfoldAlgorithm caseFolding = flags.isEitherUnicode() ? CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptUnicode : CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptNonUnicode;
        CodePointSetAccumulator tmp = compilationBuffer.getCodePointSetAccumulator1();
        CaseFoldData.applyCaseFoldUnfold(charClass, tmp, caseFolding);
    }

    @Override
    protected CodePointSet complementClassSet(CodePointSet codePointSet) {
        if (flags.isUnicodeSets() && flags.isIgnoreCase()) {
            return codePointSet.createInverse(CaseFoldData.FOLDED_CHARACTERS, compilationBuffer);
        } else {
            return codePointSet.createInverse(source.getEncoding());
        }
    }

    @Override
    protected ClassSetContents caseFoldClassSetAtom(ClassSetContents classSetContents) {
        if (flags.isUnicodeSets() && flags.isIgnoreCase()) {
            return classSetContents.caseFold(compilationBuffer.getCodePointSetAccumulator1());
        } else {
            return classSetContents;
        }
    }

    @Override
    protected CodePointSet getDotCodePointSet() {
        return flags.isDotAll() ? Constants.DOT_ALL : Constants.DOT;
    }

    @Override
    protected CodePointSet getIdContinue() {
        return ID_CONTINUE;
    }

    @Override
    protected CodePointSet getIdStart() {
        return ID_START;
    }

    @Override
    protected int getMaxBackReferenceDigits() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected CodePointSet getPredefinedCharClass(char c) {
        switch (c) {
            case 's':
                if (source.getOptions().isU180EWhitespace()) {
                    return Constants.LEGACY_WHITE_SPACE;
                } else {
                    return Constants.WHITE_SPACE;
                }
            case 'S':
                if (source.getOptions().isU180EWhitespace()) {
                    return Constants.LEGACY_NON_WHITE_SPACE;
                } else {
                    return Constants.NON_WHITE_SPACE;
                }
            case 'd':
                return Constants.DIGITS;
            case 'D':
                return Constants.NON_DIGITS;
            case 'w':
                if (flags.isUnicodeSets() && flags.isIgnoreCase()) {
                    return Constants.WORD_CHARS_UNICODE_SETS_IGNORE_CASE;
                } else if (flags.isUnicode() && flags.isIgnoreCase()) {
                    return Constants.WORD_CHARS_UNICODE_IGNORE_CASE;
                } else {
                    return Constants.WORD_CHARS;
                }
            case 'W':
                if (flags.isUnicodeSets() && flags.isIgnoreCase()) {
                    return Constants.NON_WORD_CHARS_UNICODE_SETS_IGNORE_CASE;
                } else if (flags.isUnicode() && flags.isIgnoreCase()) {
                    return Constants.NON_WORD_CHARS_UNICODE_IGNORE_CASE;
                } else {
                    return Constants.NON_WORD_CHARS;
                }
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected void checkClassSetCharacter(int codePoint) throws RegexSyntaxException {
        if (CLASS_SET_SYNTAX_CHARS.get(codePoint)) {
            throw syntaxError(JsErrorMessages.unexpectedCharacterInClassSet(codePoint));
        }
        if (CLASS_SET_RESERVED_DOUBLE_PUNCTUATORS.get(codePoint)) {
            String punctuator = Character.toString(codePoint);
            if (lookahead(punctuator)) {
                throw syntaxError(JsErrorMessages.unexpectedDoublePunctuatorInClassSet(punctuator));
            }
        }
    }

    @Override
    protected long boundedQuantifierMaxValue() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected RegexSyntaxException handleBoundedQuantifierOutOfOrder() {
        return syntaxError(JsErrorMessages.QUANTIFIER_OUT_OF_ORDER);
    }

    @Override
    protected Token handleBoundedQuantifierEmptyOrMissingMin() throws RegexSyntaxException {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.INCOMPLETE_QUANTIFIER);
        }
        position = getLastTokenPosition() + 1;
        return literalChar('{');
    }

    @Override
    protected Token handleBoundedQuantifierInvalidCharacter() {
        return handleBoundedQuantifierEmptyOrMissingMin();
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
    protected RegexSyntaxException handleCCRangeOutOfOrder(int startPos) {
        return syntaxError(JsErrorMessages.CHAR_CLASS_RANGE_OUT_OF_ORDER);
    }

    @Override
    protected void handleCCRangeWithPredefCharClass(int startPos, ClassSetContents firstAtom, ClassSetContents secondAtom) {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.INVALID_CHARACTER_CLASS);
        }
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
        return syntaxError(JsErrorMessages.invalidRegularExpression(source, JsErrorMessages.COMPLEMENT_OF_STRING_SET));
    }

    @Override
    protected void handleGroupRedefinition(String name, int newId, int oldId) {
        // checking for clashing group names is done in JSRegexParser
    }

    @Override
    protected int handleIncompleteEscapeX() {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.INVALID_ESCAPE);
        }
        return -1;
    }

    @Override
    protected Token handleInvalidBackReference(int reference) {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
        }
        return null;
    }

    @Override
    protected ClassSetOperator handleTripleAmpersandInClassSetExpression() {
        throw syntaxError(JsErrorMessages.INVALID_CHARACTER_IN_CHARACTER_CLASS);
    }

    private int handleInvalidEscape(int c) {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.INVALID_ESCAPE);
        }
        return c;
    }

    @Override
    protected RegexSyntaxException handleInvalidGroupBeginQ() {
        return syntaxError(JsErrorMessages.INVALID_GROUP);
    }

    @Override
    protected RegexSyntaxException handleMixedClassSetOperators(ClassSetOperator leftOperator, ClassSetOperator rightOperator) {
        return syntaxError(JsErrorMessages.mixedOperatorsInClassSet(leftOperator, rightOperator));
    }

    @Override
    protected RegexSyntaxException handleMissingClassSetOperand(ClassSetOperator operator) {
        return syntaxError(JsErrorMessages.missingClassSetOperand(operator));
    }

    @Override
    protected void handleOctalOutOfRange() {
    }

    @Override
    protected RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator) {
        return syntaxError(JsErrorMessages.rangeAsClassSetOperand(operator));
    }

    @Override
    protected void handleUnfinishedEscape() {
        throw syntaxError(JsErrorMessages.ENDS_WITH_UNFINISHED_ESCAPE_SEQUENCE);
    }

    @Override
    protected void handleUnfinishedGroupComment() {
    }

    @Override
    protected RegexSyntaxException handleUnfinishedGroupQ() {
        return syntaxError(JsErrorMessages.INVALID_GROUP);
    }

    @Override
    protected RegexSyntaxException handleUnfinishedRangeInClassSet() {
        return syntaxError(JsErrorMessages.UNTERMINATED_CHARACTER_RANGE);
    }

    @Override
    protected void handleUnmatchedRightBrace() {
        if (flags.isEitherUnicode()) {
            // In ECMAScript regular expressions, syntax characters such as '}' and ']'
            // cannot be used as atomic patterns. However, Annex B relaxes this condition
            // and allows the use of unmatched '}' and ']', which then match themselves.
            // Nevertheless, in Unicode mode, we should still be strict.
            throw syntaxError(JsErrorMessages.UNMATCHED_RIGHT_BRACE);
        }
    }

    @Override
    protected RegexSyntaxException handleUnmatchedLeftBracket() {
        return syntaxError(JsErrorMessages.UNMATCHED_LEFT_BRACKET);
    }

    @Override
    protected void handleUnmatchedRightBracket() {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.UNMATCHED_RIGHT_BRACKET);
        }
    }

    @Override
    protected int parseCodePointInGroupName() throws RegexSyntaxException {
        if (consumingLookahead("\\u")) {
            final int unicodeEscape = parseUnicodeEscapeChar(true);
            if (unicodeEscape < 0) {
                throw syntaxError(JsErrorMessages.INVALID_UNICODE_ESCAPE);
            } else {
                return unicodeEscape;
            }
        }
        final char c = consumeChar();
        return Character.isHighSurrogate(c) ? finishSurrogatePair(c) : c;
    }

    private String jsParseGroupName() {
        ParseGroupNameResult result = parseGroupName('>');
        switch (result.state) {
            case empty:
                throw syntaxError(JsErrorMessages.EMPTY_GROUP_NAME);
            case unterminated:
                throw syntaxError(JsErrorMessages.UNTERMINATED_GROUP_NAME);
            case invalidStart:
                throw syntaxError(JsErrorMessages.INVALID_GROUP_NAME_START);
            case invalidRest:
                throw syntaxError(JsErrorMessages.INVALID_GROUP_NAME_PART);
            case valid:
                return result.groupName;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected Token parseCustomEscape(char c) {
        if (c == 'k') {
            if (flags.isEitherUnicode() || hasNamedCaptureGroups()) {
                if (atEnd()) {
                    handleUnfinishedEscape();
                }
                if (consumeChar() != '<') {
                    throw syntaxError(JsErrorMessages.MISSING_GROUP_NAME);
                }
                String groupName = jsParseGroupName();
                // backward reference
                if (namedCaptureGroups.containsKey(groupName)) {
                    return Token.createBackReference(namedCaptureGroups.get(groupName).stream().mapToInt(x -> x).toArray(), false);
                }
                // possible forward reference
                Map<String, List<Integer>> allNamedCaptureGroups = getNamedCaptureGroups();
                if (allNamedCaptureGroups != null && allNamedCaptureGroups.containsKey(groupName)) {
                    return Token.createBackReference(allNamedCaptureGroups.get(groupName).stream().mapToInt(x -> x).toArray(), false);
                }
                throw syntaxError(JsErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
            } else {
                return literalChar(c);
            }
        }
        return null;
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        switch (c) {
            case '0':
                if (flags.isEitherUnicode() && lookahead(RegexLexer::isDecimalDigit, 1)) {
                    throw syntaxError(JsErrorMessages.INVALID_ESCAPE);
                }
                if (!flags.isEitherUnicode() && lookahead(RegexLexer::isOctalDigit, 1)) {
                    return parseOctal(0, 2);
                }
                return '\0';
            case 'c':
                if (atEnd()) {
                    retreat();
                    return handleInvalidControlEscape();
                }
                final char controlLetter = curChar();
                if (!flags.isEitherUnicode() && (isDecimalDigit(controlLetter) || controlLetter == '_') && inCharClass) {
                    advance();
                    return controlLetter % 32;
                }
                if (!('a' <= controlLetter && controlLetter <= 'z' || 'A' <= controlLetter && controlLetter <= 'Z')) {
                    retreat();
                    return handleInvalidControlEscape();
                }
                advance();
                return Character.toUpperCase(controlLetter) - ('A' - 1);
            case 'u':
                final int unicodeEscape = parseUnicodeEscapeChar(flags.isEitherUnicode());
                return unicodeEscape < 0 ? c : unicodeEscape;
            default:
                return -1;
        }
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        if (inCharClass && flags.isUnicodeSets()) {
            // parsing a ClassSetCharacter in ClassSetExpression
            if (!SYNTAX_CHARS.get(c) && !CLASS_SET_RESERVED_PUNCTUATORS.get(c)) {
                return handleInvalidEscape(c);
            }
        } else if (inCharClass) {
            // parsing a ClassAtom in NonemptyClassRanges
            if (!SYNTAX_CHARS.get(c) && c != '-') {
                return handleInvalidEscape(c);
            }
        } else {
            // parsing an AtomEscape in Atom
            if (!SYNTAX_CHARS.get(c)) {
                return handleInvalidEscape(c);
            }
        }
        return c;
    }

    private char handleInvalidControlEscape() throws RegexSyntaxException {
        if (flags.isEitherUnicode()) {
            throw syntaxError(JsErrorMessages.INVALID_CONTROL_CHAR_ESCAPE);
        }
        return '\\';
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
        return null;
    }

    @Override
    protected Token parseGroupLt() {
        registerNamedCaptureGroup(jsParseGroupName());
        return Token.createCaptureGroupBegin();
    }

    /**
     * Parse a {@code RegExpUnicodeEscapeSequence}, assuming that the prefix '&#92;u' has already
     * been read.
     *
     * @param unicodeMode whether we are in Unicode mode, which allows '&#92;u{...} escapes and
     *            treats surrogate pairs as single code points
     *
     * @return the code point of the escaped character, or -1 if the escape was malformed
     */
    private int parseUnicodeEscapeChar(boolean unicodeMode) throws RegexSyntaxException {
        if (unicodeMode && consumingLookahead("{")) {
            final int value = parseHex(1, Integer.MAX_VALUE, 0x10ffff);
            if (!consumingLookahead("}")) {
                throw syntaxError(JsErrorMessages.INVALID_UNICODE_ESCAPE);
            }
            return value;
        } else {
            final int value = parseHex(4, 4, 0xffff);
            if (unicodeMode && Character.isHighSurrogate((char) value)) {
                final int resetIndex = position;
                if (consumingLookahead("\\u") && !lookahead("{")) {
                    final char lead = (char) value;
                    final char trail = (char) parseHex(4, 4, 0xffff);
                    if (Character.isLowSurrogate(trail)) {
                        return Character.toCodePoint(lead, trail);
                    } else {
                        position = resetIndex;
                    }
                } else {
                    position = resetIndex;
                }
            }
            return value;
        }
    }

    private int parseHex(int minDigits, int maxDigits, int maxValue) throws RegexSyntaxException {
        int ret = 0;
        int initialIndex = position;
        for (int i = 0; i < maxDigits; i++) {
            if (atEnd() || !isHexDigit(curChar())) {
                if (i < minDigits) {
                    if (flags.isEitherUnicode()) {
                        throw syntaxError(JsErrorMessages.INVALID_UNICODE_ESCAPE);
                    } else {
                        position = initialIndex;
                        return -1;
                    }
                } else {
                    break;
                }
            }
            final char c = consumeChar();
            ret *= 16;
            if (c >= 'a') {
                ret += c - ('a' - 10);
            } else if (c >= 'A') {
                ret += c - ('A' - 10);
            } else {
                ret += c - '0';
            }
            if (ret > maxValue) {
                throw syntaxError(JsErrorMessages.INVALID_UNICODE_ESCAPE);
            }
        }
        return ret;
    }

    @Override
    protected boolean parseLiteralStart(char c) {
        return false;
    }

    @Override
    protected boolean parseLiteralEnd(char c) {
        return false;
    }
}
