/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.regex.tregex.parser.flavors.OracleDBConstants.POSIX_CHAR_CLASSES;
import static com.oracle.truffle.regex.tregex.parser.flavors.OracleDBConstants.WORD_CHARACTERS;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.errors.OracleDBErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.RegexLexer;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.JavaStringUtil;
import com.oracle.truffle.regex.util.TBitSet;

public final class OracleDBRegexLexer extends RegexLexer {
    private static final CodePointSet EMPTY_POSIX_CHAR_CLASS = CodePointSet.create(':', ':', '[', '[', ']', ']');
    private static final TBitSet WHITESPACE = TBitSet.valueOf('\n', ' ');
    private final OracleDBFlags flags;
    private final CodePointSetAccumulator caseFoldTmp = new CodePointSetAccumulator();

    public OracleDBRegexLexer(RegexSource source, OracleDBFlags flags, CompilationBuffer compilationBuffer) {
        super(source, compilationBuffer);
        this.flags = flags;
    }

    @Override
    public boolean hasNext() {
        // calling super.hasNext first to skip whitespace in ignore-whitespace mode
        boolean hasNext = super.hasNext();
        // trailing back-slashes are ignored
        if (position == pattern.length() - 1 && pattern.charAt(pattern.length() - 1) == '\\') {
            return false;
        }
        return hasNext;
    }

    @Override
    protected boolean featureEnabledIgnoreCase() {
        return false;
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
    protected boolean featureEnabledWordBoundaries() {
        return false;
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
        return true;
    }

    @Override
    protected boolean featureEnabledNestedCharClasses() {
        return true;
    }

    @Override
    protected boolean featureEnabledPOSIXCharClasses() {
        return true;
    }

    @Override
    protected CodePointSet getPOSIXCharClass(String name) {
        if (name.isEmpty()) {
            // oracledb quirk: [::] inside a character class is treated as [:] instead of re-parsing
            return EMPTY_POSIX_CHAR_CLASS;
        }
        CodePointSet cps = POSIX_CHAR_CLASSES.get(name);
        if (cps != null) {
            return cps;
        }
        throw syntaxError(OracleDBErrorMessages.INVALID_CHARACTER_CLASS);
    }

    @Override
    protected void validatePOSIXCollationElement(String sequence) {
        assert !JavaStringUtil.isSingleCodePoint(sequence);
        throw syntaxError(OracleDBErrorMessages.INVALID_COLLATION_ELEMENT);
    }

    @Override
    protected void validatePOSIXEquivalenceClass(String sequence) {
        assert !JavaStringUtil.isSingleCodePoint(sequence);
        throw syntaxError(OracleDBErrorMessages.INVALID_EQUIVALENCE_CLASS);
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
        return false;
    }

    @Override
    protected boolean featureEnabledIgnoreWhiteSpace() {
        return flags.isIgnoreWhitespace();
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
        return false;
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
    protected void caseFoldUnfold(CodePointSetAccumulator charClass) {
        CaseFoldData.applyCaseFoldUnfold(charClass, caseFoldTmp, CaseFoldData.CaseFoldUnfoldAlgorithm.ECMAScriptUnicode);
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
        return flags.isDotAll() ? Constants.DOT_ALL : Constants.NO_NEWLINE;
    }

    @Override
    protected CodePointSet getIdContinue() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected CodePointSet getIdStart() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected int getMaxBackReferenceDigits() {
        return 1;
    }

    @Override
    protected boolean isPredefCharClass(char c) {
        // OracleDB ignores \s \d \w inside character classes, and interprets them as literal
        // characters instead
        return !inCharacterClass() && PREDEFINED_CHAR_CLASSES.get(c);
    }

    @Override
    protected CodePointSet getPredefinedCharClass(char c) {
        CodePointSet cps = getPOSIXCharClass(c);
        if (isLowerCase(c)) {
            return cps;
        } else {
            return cps.createInverse(Encodings.UTF_32);
        }
    }

    private static int toLowerCase(char c) {
        return c | 0x20;
    }

    private static boolean isLowerCase(char c) {
        return (c & 0x20) != 0;
    }

    private static CodePointSet getPOSIXCharClass(char c) {
        switch (toLowerCase(c)) {
            case 's':
                return POSIX_CHAR_CLASSES.get("space");
            case 'd':
                return POSIX_CHAR_CLASSES.get("digit");
            case 'w':
                return WORD_CHARACTERS;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @Override
    protected long boundedQuantifierMaxValue() {
        return 0xffff_ffffL;
    }

    @Override
    protected RegexSyntaxException handleBoundedQuantifierOutOfOrder() {
        return syntaxError(OracleDBErrorMessages.QUANTIFIER_OUT_OF_ORDER);
    }

    @Override
    protected Token handleBoundedQuantifierSyntaxError() throws RegexSyntaxException {
        // invalid bounded quantifiers are treated as string literals
        position = getLastTokenPosition() + 1;
        return literalChar('{');
    }

    @Override
    protected Token handleBoundedQuantifierOverflow(long min, long max) {
        if (min == -1 || max == -1) {
            // bounded quantifiers outside uint32 range are treated as string literals
            position = getLastTokenPosition() + 1;
            return literalChar('{');
        }
        if (Long.compareUnsigned(min, max) > 0) {
            throw handleBoundedQuantifierOutOfOrder();
        }
        // oracledb quirk: values between 0x7fff_ffff and 0xffff_ffff are treated as uint32 in the
        // quantifier order check, but are later "cast" to int32 by stripping the sign bit.
        return new Token.Quantifier((int) (min & Integer.MAX_VALUE), (int) (max & Integer.MAX_VALUE), !consumingLookahead("?"));
    }

    @Override
    protected Token handleBoundedQuantifierOverflowMin(long min, long max) {
        if (min == -1) {
            // bounded quantifiers outside uint32 range are treated as string literals
            position = getLastTokenPosition() + 1;
            return literalChar('{');
        }
        // oracledb quirk: values between 0x7fff_ffff and 0xffff_ffff are treated as uint32 in the
        // quantifier order check, but are later "cast" to int32 by stripping the sign bit.
        return new Token.Quantifier((int) (min & Integer.MAX_VALUE), (int) (max & Integer.MAX_VALUE), !consumingLookahead("?"));
    }

    @Override
    protected RegexSyntaxException handleCCRangeOutOfOrder(int startPos) {
        return syntaxError(OracleDBErrorMessages.INVALID_RANGE);
    }

    @Override
    protected void handleCCRangeWithPredefCharClass(int startPos, ClassSetContents firstAtom, ClassSetContents secondAtom) {
        if (firstAtom.isAllowedInRange()) {
            throw syntaxError(OracleDBErrorMessages.INVALID_RANGE);
        }
    }

    @Override
    protected RegexSyntaxException handleComplementOfStringSet() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleEmptyGroupName() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleGroupRedefinition(String name, int newId, int oldId) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleIncompleteEscapeX() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleInvalidBackReference(int reference) {
        throw syntaxError(OracleDBErrorMessages.MISSING_GROUP_FOR_BACKREFERENCE);
    }

    @Override
    protected void handleInvalidBackReference(String reference) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleInvalidCharInCharClass() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleInvalidGroupBeginQ() {
        throw CompilerDirectives.shouldNotReachHere();
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
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleRangeAsClassSetOperand(ClassSetOperator operator) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnfinishedEscape() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected void handleUnfinishedGroupComment() {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected RegexSyntaxException handleUnfinishedGroupQ() {
        throw CompilerDirectives.shouldNotReachHere();
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
        return syntaxError(OracleDBErrorMessages.UNMATCHED_LEFT_BRACKET);
    }

    @Override
    protected void handleUnmatchedRightBracket() {
    }

    @Override
    protected void checkClassSetCharacter(int codePoint) throws RegexSyntaxException {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected int parseCodePointInGroupName() throws RegexSyntaxException {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected Token parseCustomEscape(char c) {
        return null;
    }

    @Override
    protected int parseCustomEscapeChar(char c, boolean inCharClass) {
        if (inCharClass) {
            // oracleDB treats the backslash as a literal character inside character classes
            position--;
            return '\\';
        } else {
            // outside character classes, all escaped characters are simply treated as literals,
            // there are no escape sequences in oracleDB
            return c;
        }
    }

    @Override
    protected int parseCustomEscapeCharFallback(int c, boolean inCharClass) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected Token parseCustomGroupBeginQ(char charAfterQuestionMark) {
        throw CompilerDirectives.shouldNotReachHere();
    }

    @Override
    protected Token parseGroupLt() {
        throw CompilerDirectives.shouldNotReachHere();
    }
}
