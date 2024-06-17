/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.errors.JavaErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TBitSet;

public final class JavaRegexLexer extends RegexLexer {

    // 0x0009, CHARACTER TABULATION, <TAB>
    // 0x0020, SPACE, <SP>
    // 0x00A0, NO-BREAK SPACE, <NBSP>
    // 0x1680, OGHAM SPACE MARK
    // 0x2000, EN QUAD
    // 0x2001, EM QUAD
    // 0x2002, EN SPACE
    // 0x2003, EM SPACE
    // 0x2004, THREE-PER-EM SPACE
    // 0x2005, FOUR-PER-EM SPACE
    // 0x2006, SIX-PER-EM SPACE
    // 0x2007, FIGURE SPACE
    // 0x2008, PUNCTUATION SPACE
    // 0x2009, THIN SPACE
    // 0x200A, HAIR SPACE
    // 0x202F, NARROW NO-BREAK SPACE
    // 0x205F, MEDIUM MATHEMATICAL SPACE
    // 0x3000, IDEOGRAPHIC SPACE
    public static final CodePointSet HORIZONTAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0009, 0x0009,
                    0x0020, 0x0020,
                    0x00a0, 0x00a0,
                    0x1680, 0x1680,
                    0x180e, 0x180e,
                    0x2000, 0x200a,
                    0x202f, 0x202f,
                    0x205f, 0x205f,
                    0x3000, 0x3000);

    public static final CodePointSet NOT_HORIZONTAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0008,
                    0x000a, 0x001f,
                    0x0021, 0x009f,
                    0x00a1, 0x167f,
                    0x1681, 0x180d,
                    0x180f, 0x1fff,
                    0x200b, 0x202e,
                    0x2030, 0x205e,
                    0x2060, 0x2fff,
                    0x3001, 0x10ffff);

    // 0x000A, LINE FEED (LF), <LF>
    // 0x000B, VERTICAL TAB (VT), <VT>
    // 0x000C, FORM FEED (FF), <FF>
    // 0x000D, CARRIAGE RETURN (CR), <CR>
    // 0x0085, NEXT LINE (NEL), <NEL>
    // 0x2028, LINE SEPARATOR, <LS>
    // 0x2029, PARAGRAPH SEPARATOR, <PS>
    public static final CodePointSet VERTICAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x000a, 0x000d,
                    0x0085, 0x0085,
                    0x2028, 0x2029);
    public static final CodePointSet NOT_VERTICAL_WHITE_SPACE = CodePointSet.createNoDedup(
                    0x0000, 0x0009,
                    0x000e, 0x0084,
                    0x0086, 0x2027,
                    0x202a, 0x10ffff);

    @Override
    protected Token literalChar(int codePoint) {
        if (featureEnabledIgnoreCase()) {
            curCharClass.clear();
            curCharClass.addCodePoint(codePoint);
            caseFoldUnfold(curCharClass);
            return Token.createCharClass(curCharClass.toCodePointSet());
        } else {
            return Token.createCharClass(CodePointSet.create(codePoint));
        }
    }

    private static final TBitSet WHITESPACE = TBitSet.valueOf('\t', '\n', '\f', '\r', ' ');
    private static final TBitSet PREDEFINED_CHAR_CLASSES = TBitSet.valueOf('D', 'H', 'S', 'V', 'W', 'd', 'h', 's', 'v', 'w');
    private static final TBitSet LATIN1_CHARS_THAT_CASE_FOLD_TO_NON_LATIN1_CHARS = TBitSet.valueOf(0x49, 0x4b, 0x53, 0x69, 0x6b, 0x73, 0xb5, 0xc5, 0xe5, 0xff);

    final JavaUnicodeProperties unicode;
    private final Deque<JavaFlags> flagsStack = new ArrayDeque<>();
    private JavaFlags currentFlags;
    private final CodePointSetAccumulator curCharClass = new CodePointSetAccumulator();

    public JavaRegexLexer(RegexSource source, JavaFlags flags, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        if (flags.isCanonEq()) {
            throw new UnsupportedRegexException("Canonical equivalence is not supported");
        }
        if (flags.isLiteral()) {
            throw new UnsupportedRegexException("Literal parsing is not supported");
        }
        this.unicode = JavaUnicodeProperties.create(source.getOptions());
        this.currentFlags = flags;
    }

    @Override
    protected boolean isPredefCharClass(char c) {
        return PREDEFINED_CHAR_CLASSES.get(c);
    }

    JavaFlags getLocalFlags() {
        return currentFlags;
    }

    public void pushLocalFlags() {
        flagsStack.push(currentFlags);
    }

    public void popLocalFlags() {
        currentFlags = flagsStack.pop();
    }

    public void setCurrentFlags(JavaFlags flags) {
        currentFlags = flags;
    }

    @Override
    protected boolean featureEnabledIgnoreCase() {
        return getLocalFlags().isCaseInsensitive();
    }

    @Override
    protected boolean featureEnabledAZPositionAssertions() {
        return true;
    }

    @Override
    protected boolean featureEnabledZLowerCaseAssertion() {
        return true;
    }

    @Override
    protected boolean featureEnabledBoundedQuantifierEmptyMin() {
        return false;
    }

    @Override
    protected boolean featureEnabledPossessiveQuantifiers() {
        return true;
    }

    @Override
    protected boolean featureEnabledCharClassFirstBracketIsLiteral() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected boolean featureEnabledCCRangeWithPredefCharClass() {
        throw CompilerDirectives.shouldNotReachHere();
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
    protected boolean featureEnabledForwardReferences() {
        return false;
    }

    @Override
    protected boolean featureEnabledGroupComments() {
        return false;
    }

    @Override
    protected boolean featureEnabledLineComments() {
        return getLocalFlags().isComments();
    }

    @Override
    protected boolean featureEnabledIgnoreWhiteSpace() {
        return getLocalFlags().isComments();
    }

    @Override
    protected TBitSet getWhitespace() {
        return WHITESPACE;
    }

    @Override
    protected boolean featureEnabledOctalEscapes() {
        return false;
    }

    @Override
    protected boolean featureEnabledSpecialGroups() {
        return true;
    }

    @Override
    protected boolean featureEnabledUnicodePropertyEscapes() {
        return true;
    }

    @Override
    protected boolean featureEnabledClassSetExpressions() {
        return true;
    }

    @Override
    protected void caseFoldUnfold(CodePointSetAccumulator charClass) {
        CaseFoldData.CaseFoldUnfoldAlgorithm caseFolding = (getLocalFlags().isUnicodeCase() || getLocalFlags().isUnicodeCharacterClass())
                        ? CaseFoldData.CaseFoldUnfoldAlgorithm.JavaUnicode
                        : CaseFoldData.CaseFoldUnfoldAlgorithm.Ascii;
        CaseFoldData.applyCaseFoldUnfold(charClass, compilationBuffer.getCodePointSetAccumulator1(), caseFolding);
    }

    @Override
    protected ClassSetContents caseFoldClassSetAtom(ClassSetContents classSetContents) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected CodePointSet complementClassSet(CodePointSet codePointSet) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected CodePointSet getDotCodePointSet() {
        return getLocalFlags().isDotAll() ? Constants.DOT_ALL : getLocalFlags().isUnixLines() ? JavaUnicodeProperties.DOT_UNIX : JavaUnicodeProperties.DOT;
    }

    @Override
    protected CodePointSet getIdStart() {
        return JavaASCII.ALPHA;
    }

    @Override
    protected CodePointSet getIdContinue() {
        return JavaASCII.ALNUM;
    }

    @Override
    protected int getMaxBackReferenceDigits() {
        int n = getNumberOfParsedGroups();
        int digits = 1;
        while (n >= 10) {
            n /= 10;
            digits++;
        }
        return digits;
    }

    @Override
    protected CodePointSet getPredefinedCharClass(char c) {
        char lowerCaseC = Character.toLowerCase(c);
        if (getLocalFlags().isUnicodeCharacterClass() && lowerCaseC != 'v' && lowerCaseC != 'h') {
            switch (c) {
                case 'd':
                    return unicode.digit;
                case 'D':
                    return unicode.nonDigit;
                case 's':
                    return unicode.space;
                case 'S':
                    return unicode.nonSpace;
                case 'w':
                    return unicode.word;
                case 'W':
                    return unicode.nonWord;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        switch (c) {
            case 's':
                return JavaASCII.SPACE;
            case 'S':
                return JavaASCII.NON_SPACE;
            case 'd':
                return Constants.DIGITS;
            case 'D':
                return Constants.NON_DIGITS;
            case 'w':
                return Constants.WORD_CHARS;
            case 'W':
                return Constants.NON_WORD_CHARS;
            case 'v':
                return VERTICAL_WHITE_SPACE;
            case 'V':
                return NOT_VERTICAL_WHITE_SPACE;
            case 'h':
                return HORIZONTAL_WHITE_SPACE;
            case 'H':
                return NOT_HORIZONTAL_WHITE_SPACE;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected long boundedQuantifierMaxValue() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected RegexSyntaxException handleBoundedQuantifierOutOfOrder() {
        return syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierEmptyOrMissingMin() {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierInvalidCharacter() {
        throw syntaxError(JavaErrorMessages.UNCLOSED_COUNTED_CLOSURE);
    }

    @Override
    protected Token handleBoundedQuantifierOverflow(long min, long max) {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected Token handleBoundedQuantifierOverflowMin(long min, long max) {
        throw syntaxError(JavaErrorMessages.ILLEGAL_REPETITION);
    }

    @Override
    protected RegexSyntaxException handleCCRangeOutOfOrder(int startPos) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleCCRangeWithPredefCharClass(int startPos, ClassSetContents firstAtom, ClassSetContents secondAtom) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleComplementOfStringSet() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleGroupRedefinition(String name, int newId, int oldId) {
        throw syntaxError(JavaErrorMessages.groupRedefinition(name));
    }

    @Override
    protected void handleIncompleteEscapeX() {
        throw syntaxError(JavaErrorMessages.ILLEGAL_HEX_ESCAPE);
    }

    @Override
    protected Token handleInvalidBackReference(int reference) {
        int clamped = reference;
        while (clamped >= 10 && clamped >= getNumberOfParsedGroups()) {
            clamped /= 10;
            position--;
        }
        if (clamped >= getNumberOfParsedGroups()) {
            return Token.createCharClass(CodePointSet.getEmpty());
        }
        return Token.createBackReference(clamped, false);
    }

    @Override
    protected RegexSyntaxException handleInvalidCharInCharClass() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleInvalidGroupBeginQ() {
        return syntaxError(JavaErrorMessages.UNKNOWN_INLINE_MODIFIER);
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
    }

    @Override
    protected RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnfinishedEscape() {
        throw syntaxError(JavaErrorMessages.UNESCAPED_TRAILING_BACKSLASH);
    }

    @Override
    protected void handleUnfinishedGroupComment() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleUnfinishedGroupQ() {
        return syntaxError(JavaErrorMessages.UNKNOWN_INLINE_MODIFIER);
    }

    @Override
    protected RegexSyntaxException handleUnfinishedRangeInClassSet() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnmatchedRightBrace() {
    }

    @Override
    protected RegexSyntaxException handleUnmatchedLeftBracket() {
        return syntaxError(JavaErrorMessages.UNCLOSED_CHARACTER_CLASS);
    }

    @Override
    protected void handleUnmatchedRightBracket() {
    }

    @Override
    protected void checkClassSetCharacter(int codePoint) throws RegexSyntaxException {
    }

    @Override
    protected int parseCodePointInGroupName() throws RegexSyntaxException {
        final char c = consumeChar();
        return Character.isHighSurrogate(c) ? finishSurrogatePair(c) : c;
    }

    @Override
    protected ClassSetContents parseClassSetExpression() throws RegexSyntaxException {
        return ClassSetContents.createCharacterClass(parseCharClassInternal(true));
    }

    @Override
    protected ClassSetContents parseUnicodeCharacterProperty(boolean invert) throws RegexSyntaxException {
        String name;
        if (atEnd()) {
            // necessary to match the reference error message
            name = String.valueOf((char) 0);
        } else if (!consumingLookahead("{")) {
            name = String.valueOf(consumeChar());
        } else {
            int namePos = position;
            while (!atEnd() && curChar() != '}') {
                advance();
            }
            if (!consumingLookahead("}")) {
                throw syntaxError(JavaErrorMessages.UNCLOSED_CHAR_FAMILY);
            }
            name = pattern.substring(namePos, position - 1);
            if (name.isEmpty()) {
                throw syntaxError(JavaErrorMessages.EMPTY_CHAR_FAMILY);
            }
        }
        CodePointSet p = null;
        int i = name.indexOf('=');
        if (i != -1) {
            // property construct \p{name=value}
            String value = name.substring(i + 1);
            name = name.substring(0, i).toLowerCase(Locale.ENGLISH);
            switch (name) {
                case "sc":
                case "script":
                    // p = CharPredicates.forUnicodeScript(value);
                    p = unicode.getScript(value);
                    break;
                case "blk":
                case "block":
                    // p = CharPredicates.forUnicodeBlock(value);
                    p = unicode.getBlock(value);
                    break;
                case "gc":
                case "general_category":
                    // p = CharPredicates.forProperty(value, has(CASE_INSENSITIVE));
                    p = unicode.getProperty(value, getLocalFlags().isCaseInsensitive());
                    break;
                default:
                    break;
            }
            if (p == null) {
                throw syntaxError(JavaErrorMessages.unknownUnicodeProperty(name, value));
            }
        } else {
            if (name.startsWith("In")) {
                // \p{InBlockName}
                p = unicode.getBlock(name.substring(2));
            } else if (name.startsWith("Is")) {
                // \p{IsGeneralCategory} and \p{IsScriptName}
                String shortName = name.substring(2);
                p = unicode.forUnicodeProperty(shortName, getLocalFlags().isCaseInsensitive());
                if (p == null) {
                    p = unicode.getProperty(shortName, getLocalFlags().isCaseInsensitive());
                }
                if (p == null) {
                    p = unicode.getScript(shortName);
                }
            } else {
                if (getLocalFlags().isUnicodeCharacterClass()) {
                    p = unicode.forPOSIXName(name, getLocalFlags().isCaseInsensitive());
                }
                if (p == null) {
                    p = unicode.getProperty(name, getLocalFlags().isCaseInsensitive());
                }
            }
            if (p == null) {
                throw syntaxError(JavaErrorMessages.unknownUnicodeCharacterProperty(name));
            }
        }
        if (invert) {
            // TODO reference implementation has something with hasSupplementary, do we care about
            // this?;
            p = p.createInverse(Encodings.UTF_16);
        }
        return ClassSetContents.createCharacterClass(p);
    }

    private CodePointSet parseCharClassInternal(boolean consume) throws RegexSyntaxException {
        boolean invert = false;
        // negation can only occur after a bracket, we cannot have negation after '&&' for example
        if (curChar() == '^' && pattern.charAt(position - 1) == '[') {
            advance();
            invert = true;
        }

        CodePointSet curr = null;
        CodePointSet prev = null;

        // we have to emulate the java.util.regex mechanism for compliance
        CodePointSet bits = CodePointSet.getEmpty();
        boolean hasBits = false;

        while (!atEnd()) {
            if (consumingLookahead('[')) {
                curr = parseCharClassInternal(true);
                if (prev == null) {
                    prev = curr;
                } else {
                    prev = prev.union(curr);
                }
                continue;
            } else if (consumingLookahead('&')) {
                if (consumingLookahead('&')) {
                    CodePointSet right = null;
                    if (!atEnd()) {
                        while (curChar() != ']' && curChar() != '&') {
                            if (consumingLookahead('[')) {
                                if (right == null) {
                                    right = parseCharClassInternal(true);
                                } else {
                                    right = right.union(parseCharClassInternal(true));
                                }
                            } else {
                                if (right == null) {
                                    right = parseCharClassInternal(false);
                                } else {
                                    right = right.union(parseCharClassInternal(false));
                                }
                            }
                        }
                        if (hasBits) {
                            if (prev == null) {
                                prev = curr = bits;
                            } else {
                                prev = prev.union(bits);
                            }
                            hasBits = false;
                        }
                        if (right != null) {
                            curr = right;
                        }
                        if (prev == null) {
                            if (right == null) {
                                throw syntaxError(JavaErrorMessages.BAD_CLASS_SYNTAX);
                            } else {
                                prev = right;
                            }
                        } else {
                            if (curr == null) {
                                throw syntaxError(JavaErrorMessages.BAD_INTERSECTION_SYNTAX);
                            }
                            prev = prev.createIntersection(curr, compilationBuffer);
                        }
                    }
                    continue;
                } else {
                    // the single '&' is treated as a literal
                    retreat();
                }
            } else if (curChar() == ']') {
                if (prev != null || hasBits) {
                    if (consume) {
                        advance();
                    }
                    if (prev == null) {
                        prev = bits;
                    } else if (hasBits) {
                        prev = prev.union(bits);
                    }
                    curCharClass.clear();
                    curCharClass.addSet(prev);
                    if (featureEnabledIgnoreCase()) {
                        caseFoldUnfold(curCharClass);
                    }
                    prev = curCharClass.toCodePointSet();
                    if (invert) {
                        return prev.createInverse(Encodings.UTF_16);
                    }
                    return prev;
                }
            }
            char ch = consumeChar();
            ClassSetContents predef = parseCharClassAtomPredefCharClass(ch);
            if (predef != null) {
                curr = predef.getCodePointSet();
                if (prev == null) {
                    prev = curr;
                } else {
                    prev = prev.union(curr);
                }
                continue;
            }
            CodePointSet range = parseRange(ch);
            curr = range;
            if (range.size() == 1 && range.getRanges()[0] == range.getRanges()[1]) {
                int c = range.getRanges()[0];
                if (c < 256 && !(getLocalFlags().isCaseInsensitive() && getLocalFlags().isUnicodeCase() && LATIN1_CHARS_THAT_CASE_FOLD_TO_NON_LATIN1_CHARS.get(c))) {
                    hasBits = true;
                    curr = null;
                    bits = bits.union(range);
                }
            }
            if (curr != null) {
                if (prev == null) {
                    prev = curr;
                } else {
                    prev = prev.union(curr);
                }
            }
        }
        throw syntaxError(JavaErrorMessages.UNCLOSED_CHARACTER_CLASS);
    }

    private CodePointSet parseRange(char c) {
        int ch = parseCharClassAtomCodePoint(c);
        if (consumingLookahead('-')) {
            if (atEnd()) {
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
            }
            if (curChar() == ']' || curChar() == '[') {
                // unmatched '-' is treated as literal
                retreat();
                return CodePointSet.create(ch);
            }
            int upper = parseCharClassAtomCodePoint(consumeChar());
            if (upper < ch) {
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_RANGE);
            }
            return CodePointSet.create(ch, upper);
        } else {
            return CodePointSet.create(ch);
        }
    }

    @Override
    protected Token parseCustomEscape(char c) {
        switch (c) {
            case 'b' -> {
                if (consumingLookahead("{g}")) {
                    throw new UnsupportedRegexException("Extended grapheme cluster boundaries are not supported");
                }
                return Token.createWordBoundary();
            }
            case 'B' -> {
                return Token.createNonWordBoundary();
            }
            case 'k' -> {
                if (atEnd()) {
                    handleUnfinishedEscape();
                }
                if (consumeChar() != '<') {
                    throw syntaxError(JavaErrorMessages.NAMED_CAPTURE_GROUP_REFERENCE_MISSING_BEGIN);
                }
                String groupName = javaParseGroupName();
                // backward reference
                if (namedCaptureGroups.containsKey(groupName)) {
                    return Token.createBackReference(getSingleNamedGroupNumber(groupName), false);
                }
                throw syntaxError(JavaErrorMessages.unknownGroupReference(groupName));
            }
            case 'R' -> {
                return Token.createLineBreak();
            }
            case 'X' -> throw new UnsupportedRegexException("Grapheme clusters are not supported");
            case 'G' -> throw new UnsupportedRegexException("End of previous match boundary matcher is not supported");
            case 'Q' -> {
                int start = position;
                int end = pattern.indexOf("\\E", start);
                if (end < 0) {
                    end = pattern.length();
                    position = end;
                } else {
                    position = end + 2;
                }
                return Token.createLiteralString(start, end);
            }
        }
        return null;
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        switch (c) {
            case 'b':
                // \b is only valid as the boundary matcher and not as a character escape
                throw syntaxError(JavaErrorMessages.ILLEGAL_ESCAPE_SEQUENCE);
            case '0':
                if (lookahead(RegexLexer::isOctalDigit, 1)) {
                    return parseOctal(0, 3);
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_OCT_ESCAPE);
            case 'u':
                int n = parseUnicodeHexEscape();
                if (Character.isHighSurrogate((char) n)) {
                    int cur = position;
                    if (consumingLookahead("\\u")) {
                        int n2 = parseUnicodeHexEscape();
                        if (Character.isLowSurrogate((char) n2)) {
                            return Character.toCodePoint((char) n, (char) n2);
                        }
                    }
                    position = cur;
                }
                return n;
            case 'x':
                if (consumingLookahead('{')) {
                    int hex = parseHex(1, 8, Character.MAX_CODE_POINT, () -> {
                        throw syntaxError(JavaErrorMessages.ILLEGAL_HEX_ESCAPE);
                    }, () -> {
                        throw syntaxError(JavaErrorMessages.HEX_TOO_BIG);
                    });
                    if (!consumingLookahead('}')) {
                        throw syntaxError(JavaErrorMessages.UNCLOSED_HEX);
                    }
                    return hex;
                }
                return -1;
            case 'c':
                if (atEnd()) {
                    throw syntaxError(JavaErrorMessages.ILLEGAL_CTRL_SEQ);
                }
                return consumeChar() ^ 64;
            case 'N':
                if (consumingLookahead('{')) {
                    int i = position;
                    if (!findChars('}')) {
                        throw syntaxError(JavaErrorMessages.UNCLOSED_CHAR_NAME);
                    }
                    advance(); // skip '}'
                    String name = pattern.substring(i, position - 1);
                    try {
                        return Character.codePointOf(name);
                    } catch (IllegalArgumentException x) {
                        throw syntaxError(JavaErrorMessages.unknownCharacterName(name));
                    }
                }
                throw syntaxError(JavaErrorMessages.ILLEGAL_CHARACTER_NAME);
            case 'a':
                return 0x7;
            case 'e':
                return 0x1b;
            case 'Q':
                throw new UnsupportedRegexException("Literal escape not supported in this context");
            default:
                return -1;
        }
    }

    private int parseUnicodeHexEscape() {
        if (consumingLookahead(RegexLexer::isHexDigit, 4)) {
            return Integer.parseInt(pattern, position - 4, position, 16);
        }
        throw syntaxError(JavaErrorMessages.ILLEGAL_UNICODE_ESC_SEQ);
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        // any non-alphabetic character can be used after an escape
        // digits are not accepted here since they should have been parsed as octal sequence or
        // backreference earlier
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
            throw syntaxError(JavaErrorMessages.ILLEGAL_ESCAPE_SEQUENCE);
        }
        return c;
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
        // TODO do we need to do something else than inline flags? check "Special constructs" in
        // documentation
        char c = charAfterQuestionMark;
        if (c == '>') {
            throw new UnsupportedRegexException("Independent non-capturing groups are not supported");
        }
        int firstCharPos = position;
        JavaFlags newFlags = getLocalFlags();
        while (JavaFlags.isValidFlagChar(c)) {
            newFlags = newFlags.addFlag(c);
            if (atEnd()) {
                throw handleUnfinishedGroupQ();
            }
            c = consumeChar();
        }
        if (c == '-') {
            if (atEnd()) {
                throw handleUnfinishedGroupQ();
            }
            c = consumeChar();
            while (JavaFlags.isValidFlagChar(c)) {
                newFlags = newFlags.delFlag(c);
                if (atEnd()) {
                    throw handleUnfinishedGroupQ();
                }
                c = consumeChar();
            }
        }
        if (c == ':') {
            if (firstCharPos == position) {
                return null;
            }
            return Token.createInlineFlags(newFlags, false);
        } else if (c == ')') {
            return Token.createInlineFlags(newFlags, true);
        }
        return null;
    }

    @Override
    protected Token parseGroupLt() {
        registerNamedCaptureGroup(javaParseGroupName());
        return Token.createCaptureGroupBegin();
    }

    private String javaParseGroupName() {
        ParseGroupNameResult result = parseGroupName('>');
        switch (result.state) {
            case empty, invalidStart:
                throw syntaxError(JavaErrorMessages.INVALID_GROUP_NAME_START);
            case unterminated, invalidRest:
                throw syntaxError(JavaErrorMessages.INVALID_GROUP_NAME_REST);
            case valid:
                return result.groupName;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
