/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.flavor.oracledb;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.RegexSyntaxException.ErrorCode;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData.CaseFoldAlgorithm;
import com.oracle.truffle.regex.tregex.parser.MultiCharacterCaseFolding;
import com.oracle.truffle.regex.tregex.parser.OracleDBCharClassTrieNode;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.string.Encoding;

public final class OracleDBRegexParser implements RegexParser {

    private static final CodePointSet UPPER_CASE = OracleDBConstants.POSIX_CHAR_CLASSES.get("upper");

    private final RegexSource source;
    private final OracleDBFlags flags;
    private final OracleDBRegexLexer lexer;
    private final RegexASTBuilder astBuilder;
    private final OracleDBCharClassTrieNode curCharClass = OracleDBCharClassTrieNode.createTreeRoot();
    private final CodePointSetAccumulator charClassTmpCaseClosure = new CodePointSetAccumulator();
    private final CodePointSetAccumulator charClassTmp2 = new CodePointSetAccumulator();

    @TruffleBoundary
    public OracleDBRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
        this(language, source, compilationBuffer, source);
    }

    public OracleDBRegexParser(RegexLanguage language, RegexSource source, CompilationBuffer compilationBuffer, RegexSource originalSource) throws RegexSyntaxException {
        this.source = source;
        this.flags = OracleDBFlags.parseFlags(source);
        this.lexer = new OracleDBRegexLexer(source, flags, compilationBuffer);
        this.astBuilder = new RegexASTBuilder(language, originalSource,
                        RegexFlags.builder().dotAll(flags.isDotAll()).ignoreCase(flags.isIgnoreCase()).multiline(flags.isMultiline()).build(),
                        false,
                        compilationBuffer);
    }

    @Override
    public OracleDBFlags getFlags() {
        return flags;
    }

    @Override
    public AbstractRegexObject getNamedCaptureGroups() {
        return AbstractRegexObject.createNamedCaptureGroupMapInt(lexer.getNamedCaptureGroups());
    }

    @Override
    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        IntArrayBuffer literalStringBuffer = new IntArrayBuffer();
        astBuilder.pushRootGroup();
        Token token = null;
        Token.Kind prevKind;
        while (lexer.hasNext()) {
            prevKind = token == null ? null : token.kind;
            token = lexer.next();
            if (token.kind != Token.Kind.literalChar && !literalStringBuffer.isEmpty()) {
                int last = -1;
                if (token.kind == Token.Kind.quantifier) {
                    last = literalStringBuffer.removeLast();
                }
                addLiteralString(literalStringBuffer);
                if (last >= 0) {
                    assert literalStringBuffer.isEmpty();
                    literalStringBuffer.add(last);
                    addLiteralString(literalStringBuffer);
                }
            }
            switch (token.kind) {
                case A, z:
                    astBuilder.addPositionAssertion(token);
                    break;
                case caret:
                    if (flags.isMultiline()) {
                        // (?:^|_MATCH_BEGIN_(?<=\n))
                        astBuilder.pushGroup();
                        astBuilder.addCaret();
                        astBuilder.nextSequence();
                        astBuilder.addPositionAssertion(PositionAssertion.Type.MATCH_BEGIN);
                        astBuilder.pushLookBehindAssertion(false);
                        astBuilder.addCharClass(CodePointSet.create('\n'));
                        astBuilder.popGroup();
                        astBuilder.popGroup();
                    } else {
                        astBuilder.addPositionAssertion(token);
                    }
                    break;
                case dollar, Z:
                    // multiline mode:
                    // (?:$|(?=\n)_MATCH_END_)
                    // otherwise:
                    // (?:$|(?=\n$))
                    astBuilder.pushGroup();
                    astBuilder.addDollar();
                    astBuilder.nextSequence();
                    astBuilder.pushLookAheadAssertion(false);
                    astBuilder.addCharClass(CodePointSet.create('\n'));
                    if (token.kind == Token.Kind.Z || !flags.isMultiline()) {
                        astBuilder.addDollar();
                    }
                    astBuilder.popGroup();
                    if (token.kind == Token.Kind.dollar && flags.isMultiline()) {
                        astBuilder.addPositionAssertion(PositionAssertion.Type.MATCH_END);
                    }
                    astBuilder.popGroup();
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token, flags.isIgnoreCase());
                    break;
                case quantifier:
                    if (prevKind == Token.Kind.quantifier) {
                        throw new UnsupportedRegexException(OracleDBErrorMessages.NESTED_QUANTIFIER);
                    }
                    if (astBuilder.getCurTerm() == null || prevKind == Token.Kind.captureGroupBegin) {
                        // quantifiers without target are ignored
                        break;
                    }
                    astBuilder.addQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    astBuilder.nextSequence();
                    break;
                case captureGroupBegin:
                    if (lexer.numberOfCaptureGroupsSoFar() <= 10) {
                        // oracledb only tracks capture groups 0 - 9
                        astBuilder.pushCaptureGroup(token);
                    } else {
                        astBuilder.pushGroup(token);
                    }
                    break;
                case groupEnd:
                    if (astBuilder.getCurGroup().getParent() instanceof RegexASTRootNode) {
                        throw syntaxError(OracleDBErrorMessages.UNMATCHED_RIGHT_PARENTHESIS, ErrorCode.UnmatchedParenthesis);
                    }
                    astBuilder.popGroup(token);
                    break;
                case literalChar:
                    literalStringBuffer.add(((Token.LiteralCharacter) token).getCodePoint());
                    break;
                case charClass:
                    astBuilder.addCharClass((Token.CharacterClass) token);
                    break;
                case charClassBegin:
                    curCharClass.clear();
                    break;
                case charClassAtom:
                    ClassSetContents contents = ((Token.CharacterClassAtom) token).getContents();
                    if (flags.isIgnoreCase()) {
                        addCCAtomIgnoreCase(contents);
                    } else {
                        addCCAtom(contents);
                    }
                    break;
                case charClassEnd:
                    addCharClass(token);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }
        if (!astBuilder.curGroupIsRoot()) {
            throw syntaxError(OracleDBErrorMessages.UNTERMINATED_GROUP, ErrorCode.UnmatchedParenthesis);
        }
        if (!literalStringBuffer.isEmpty()) {
            addLiteralString(literalStringBuffer);
        }
        return astBuilder.popRootGroup();
    }

    private void addCCAtom(ClassSetContents contents) {
        if (contents.isPosixCollationEquivalenceClass()) {
            addCCAtomMultiCharExpansion(contents, CaseFoldAlgorithm.OracleDBAI);
        } else {
            final CodePointSet cps;
            if (lexer.emulateUTF16RangeQuirk() && (contents.isRangeOrBrokenRange())) {
                cps = utf16RangeQuirkTransform(contents);
            } else {
                cps = contents.getCodePointSet();
            }
            addCCAtomCodePointSet(cps);
        }
    }

    /**
     * OracleDB's regex engine compares UTF-16BE ranges byte-wise in their encoded form when the
     * {@code ignore-case} flag is not set. This leads to some non-intuitive behavior, especially
     * when compared to the behavior on UTF-8:
     *
     * <pre>
     * .al32utf8:
     *
     * Pattern                      Matching Codepoints
     * ------------------------------------------------------
     * /[\\u{d7ff}-\\u{10000}]/:    0x00d7ff, 0x00e000-0x010000
     * /[\\u{d7ff}-\\u{10000}]/i:   0x00d7ff, 0x00e000-0x010000
     * /[\\u{f000}-\\u{10000}]/:    0x00f000-0x010000
     * /[\\u{f000}-\\u{10000}]/i:   0x00f000-0x010000
     * /[\\u{10000}-\\u{f000}]/:    syntax error
     * /[\\u{10000}-\\u{f000}]/i:   syntax error
     * /[\\u{d7ff}-\\u{e000}]/:     0x00d7ff, 0x00e000
     * /[\\u{d7ff}-\\u{e000}]/i:    0x00d7ff, 0x00e000
     *
     *
     * .AL16UTF16:
     *
     * Pattern                      Matching Codepoints
     * ------------------------------------------------------
     * /[\\u{d7ff}-\\u{10000}]/:    0x00d7ff, 0x010000
     * /[\\u{d7ff}-\\u{10000}]/i:   0x00d7ff, 0x00e000-0x010000
     * /[\\u{f000}-\\u{10000}]/:    syntax error
     * /[\\u{f000}-\\u{10000}]/i:   syntax error
     * /[\\u{10000}-\\u{f000}]/:    0x00e000-0x00f000, 0x010000-0x10ffff
     * /[\\u{10000}-\\u{f000}]/i:   0x010000
     * /[\\u{d7ff}-\\u{e000}]/:     0x00d7ff, 0x00e000, 0x010000-0x10ffff
     * /[\\u{d7ff}-\\u{e000}]/i:    0x00d7ff, 0x00e000
     * </pre>
     *
     * This function transforms a given range to match LXR's behavior in case-sensitive mode.
     */
    private CodePointSet utf16RangeQuirkTransform(ClassSetContents contents) {
        int lo = contents.getRangeLo();
        int hi = contents.getRangeHi();
        if (contents.isBrokenRange()) {
            assert lo > Character.MAX_VALUE && hi > Character.MAX_SURROGATE && hi <= Character.MAX_VALUE && Character.highSurrogate(lo) <= hi;
            if (hi + 1 == lo) {
                return CodePointSet.createNoDedup(Character.MAX_SURROGATE + 1, Character.MAX_CODE_POINT);
            } else {
                return CodePointSet.createNoDedup(Character.MAX_SURROGATE + 1, hi, lo, Character.MAX_CODE_POINT);
            }
        }
        if (hi < Character.MIN_SURROGATE) {
            return contents.getCodePointSet();
        }
        if (Character.MIN_SURROGATE <= lo && lo <= Character.MAX_SURROGATE || hi <= Character.MAX_SURROGATE) {
            throw new UnsupportedRegexException("UTF-16 range with surrogate values as upper or lower bound");
        }
        if (lo < Character.MIN_SURROGATE) {
            if (hi == Character.MAX_VALUE) {
                // range contains the surrogate range => surrogate pairs will match as well.
                return CodePointSet.create(lo, Character.MAX_CODE_POINT);
            } else if (hi < Character.MAX_VALUE) {
                // range contains the surrogate range => surrogate pairs will match as well.
                return CodePointSet.create(lo, hi, Character.MAX_VALUE + 1, Character.MAX_CODE_POINT);
            } else {
                // lower bound is less than surrogate range and upper bound is a surrogate
                // pair => exclude the range from surrogate range to 0xffff.
                return CodePointSet.create(lo, Character.MIN_SURROGATE - 1, Character.MAX_VALUE + 1, hi);
            }
        } else {
            if (hi <= Character.MAX_VALUE || lo > Character.MAX_VALUE) {
                // either both values are encoded as a surrogate pair or both are single
                // char values
                return contents.getCodePointSet();
            } else {
                // lower bound is greater than surrogate range and upper bound will be
                // encoded as a surrogate pair => considered invalid because lower bound
                // is greater than upper bound's high surrogate
                throw syntaxError(OracleDBErrorMessages.INVALID_RANGE, ErrorCode.InvalidCharacterClass);
            }
        }
    }

    private void addCCAtomCodePointSet(CodePointSet codePointSet) {
        if (!codePointSet.isEmpty()) {
            for (OracleDBCharClassTrieNode child : curCharClass.getOrAddChildren(codePointSet, true, lexer.getCompilationBuffer())) {
                child.setEndOfString();
            }
        }
    }

    private void addCCAtomIgnoreCase(ClassSetContents contents) {
        if (contents.isPosixCollationEquivalenceClass()) {
            addCCAtomMultiCharExpansion(contents, CaseFoldAlgorithm.OracleDBAI);
        } else if (contents.isRange() || contents.isBrokenRange()) {
            assert !contents.isBrokenRange() || lexer.emulateUTF16RangeQuirk();
            int lo = contents.getRangeLo();
            int hi = contents.getRangeHi();
            if (lexer.emulateUTF16RangeQuirk() && contents.isRange() && lo > Character.MAX_SURROGATE && lo <= Character.MAX_VALUE && hi > Character.MAX_VALUE) {
                throw syntaxError(OracleDBErrorMessages.INVALID_RANGE, ErrorCode.InvalidCharacterClass);
            }
            CodePointSet range = ccAtomRangeIgnoreCase(lo, hi);
            addCCAtomCodePointSet(range);
        } else if (contents.isCharacterClass()) {
            addCCAtomCodePointSet(contents.getCodePointSet());
        } else {
            assert contents.isCharacter() || contents.isPosixCollationElement();
            addCCAtomMultiCharExpansion(contents, CaseFoldAlgorithm.OracleDB);
        }
    }

    private void addCCAtomMultiCharExpansion(ClassSetContents contents, CaseFoldAlgorithm algorithm) {
        caseClosure(algorithm, contents.getCodePointSet());
        addCCAtomCodePointSet(charClassTmpCaseClosure.toCodePointSet());
        assert contents.isCharacter() || contents.isPosixCollationElement() || contents.isPosixCollationEquivalenceClass();
        assert contents.getCodePointSet().matchesSingleChar();
        // No transitive closure
        CaseFoldData.getTable(algorithm).caseFold(contents.getCodePointSet(), (codepoint, caseFolded) -> {
            if (caseFolded.length > 1) {
                CodePointSet encodingRange = Encoding.UTF_8.getFullSet();
                CompilationBuffer compilationBuffer = lexer.getCompilationBuffer();
                MultiCharacterCaseFolding.caseFoldUnfoldString(algorithm, caseFolded, encodingRange, false, false, null, curCharClass, compilationBuffer);
            }
        });
    }

    private void caseClosure(CaseFoldAlgorithm algorithm, CodePointSet codePointSet) {
        charClassTmpCaseClosure.clear();
        charClassTmpCaseClosure.addSet(codePointSet);
        MultiCharacterCaseFolding.caseClosure(algorithm, charClassTmpCaseClosure, charClassTmp2, (a, b) -> true, Encoding.UTF_8.getFullSet(), false);
    }

    private CodePointSet ccAtomRangeIgnoreCase(int lo, int hi) {
        assert flags.isIgnoreCase();
        CaseFoldData.CaseFoldTable caseFoldTable = CaseFoldData.getTable(CaseFoldAlgorithm.OracleDBSimple);
        int loLC = caseFoldSingle(caseFoldTable, lo);
        int hiLC = caseFoldSingle(caseFoldTable, hi);
        CodePointSetAccumulator toAdd = new CodePointSetAccumulator();
        CodePointSetAccumulator toRemove = new CodePointSetAccumulator();
        if (UPPER_CASE.contains(lo) != UPPER_CASE.contains(hi)) {
            // oracledb-specific quirk: if range bounds are not of the same case, the range
            // comparison flips from
            // lowercase(lo) <= chr && chr <= lowercase(hi)
            // to
            // lowercase(lo) <= chr || chr <= lowercase(hi)
            if (loLC <= hiLC || hiLC == loLC - 1) {
                return Encoding.UTF_8.getFullSet();
            } else {
                CodePointSet ret = CodePointSet.create(Character.MIN_CODE_POINT, hiLC, loLC, Character.MAX_CODE_POINT);
                caseFoldTable.caseFold(ret, (codePoint, caseFolded) -> {
                    if (!(loLC <= caseFolded[0] || caseFolded[0] <= hiLC)) {
                        toRemove.addCodePoint(codePoint);
                    }
                });
                caseFoldTable.caseFold(new Range(hiLC, loLC), (codePoint, caseFolded) -> {
                    if (loLC <= caseFolded[0] || caseFolded[0] <= hiLC) {
                        toAdd.addCodePoint(codePoint);
                    }
                });
                return ret.subtract(toRemove.toCodePointSet()).union(toAdd.toCodePointSet());
            }
        } else {
            final CodePointSet range;
            if (loLC > hiLC) {
                range = CodePointSet.create(loLC);
            } else {
                range = CodePointSet.create(loLC, hiLC);
            }
            caseFoldTable.caseFold(Constants.ALL_WITHOUT_SURROGATES.subtract(range), (codePoint, caseFolded) -> {
                if (loLC <= caseFolded[0] && caseFolded[0] <= hiLC) {
                    toAdd.addCodePoint(codePoint);
                }
            });
            caseFoldTable.caseFold(range, (codePoint, caseFolded) -> {
                if (caseFolded[0] < loLC || caseFolded[0] > hiLC) {
                    toRemove.addCodePoint(codePoint);
                }
            });
            return range.subtract(toRemove.toCodePointSet()).union(toAdd.toCodePointSet());
        }
    }

    private static int caseFoldSingle(CaseFoldData.CaseFoldTable caseFoldTable, int codepoint) {
        int[] caseFolded = caseFoldTable.caseFold(codepoint);
        return caseFolded == null ? codepoint : caseFolded[0];
    }

    private void addCharClass(Token ccEnd) {
        astBuilder.setOverrideSourceSection(ccEnd.getSourceSection());
        curCharClass.generateAST(astBuilder, lexer.isCurCharClassInverted());
        astBuilder.clearOverrideSourceSection();
    }

    private void addLiteralString(IntArrayBuffer literalStringBuffer) {
        if (flags.isIgnoreCase()) {
            int[] codepoints = literalStringBuffer.toArray();
            CodePointSet encodingRange = Encoding.UTF_8.getFullSet();
            CompilationBuffer compilationBuffer = lexer.getCompilationBuffer();
            MultiCharacterCaseFolding.caseFoldUnfoldString(CaseFoldAlgorithm.OracleDB, codepoints, encodingRange, false, false, astBuilder, null, compilationBuffer);
        } else {
            for (int i = 0; i < literalStringBuffer.length(); i++) {
                astBuilder.addCharClass(CodePointSet.create(literalStringBuffer.get(i)), true);
            }
        }
        literalStringBuffer.clear();
    }

    private RegexSyntaxException syntaxError(String msg, ErrorCode errorCode) {
        return RegexSyntaxException.createPattern(source, msg, lexer.getLastTokenPosition(), errorCode);
    }
}
