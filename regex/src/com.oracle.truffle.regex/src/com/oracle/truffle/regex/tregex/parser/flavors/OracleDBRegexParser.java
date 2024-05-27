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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.ClassSetContents;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.errors.OracleDBErrorMessages;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData;
import com.oracle.truffle.regex.tregex.parser.CaseFoldData.CaseFoldAlgorithm;
import com.oracle.truffle.regex.tregex.parser.MultiCharacterCaseFolding;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.string.Encodings;

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
                        // (?:^|(?<=\n))
                        astBuilder.pushGroup();
                        astBuilder.addCaret();
                        astBuilder.nextSequence();
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
                    // (?:$|(?=\n))
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
                    astBuilder.popGroup();
                    break;
                case backReference:
                    astBuilder.addBackReference((Token.BackReference) token, flags.isIgnoreCase());
                    break;
                case quantifier:
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
                        throw syntaxError(OracleDBErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
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
            throw syntaxError(OracleDBErrorMessages.UNTERMINATED_GROUP);
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
            addCCAtomCodePointSet(contents.getCodePointSet());
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
        } else if (contents.isRange()) {
            CodePointSet range = ccAtomRangeIgnoreCase(contents.getCodePointSet());
            charClassTmpCaseClosure.clear();
            charClassTmpCaseClosure.addSet(range);
            CaseFoldData.applyCaseFoldUnfold(charClassTmpCaseClosure, charClassTmp2, CaseFoldData.CaseFoldUnfoldAlgorithm.OracleDBSimple);
            addCCAtomCodePointSet(charClassTmpCaseClosure.toCodePointSet());
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
        CaseFoldData.getTable(algorithm).caseFold(contents.getCodePointSet().iterator().next(), (codepoint, caseFolded) -> {
            if (caseFolded.length > 1) {
                CodePointSet encodingRange = Encodings.UTF_8.getFullSet();
                CompilationBuffer compilationBuffer = lexer.getCompilationBuffer();
                MultiCharacterCaseFolding.caseFoldUnfoldString(algorithm, caseFolded, encodingRange, false, false, null, curCharClass, compilationBuffer);
            }
        });
    }

    private void caseClosure(CaseFoldAlgorithm algorithm, CodePointSet codePointSet) {
        charClassTmpCaseClosure.clear();
        charClassTmpCaseClosure.addSet(codePointSet);
        MultiCharacterCaseFolding.caseClosure(algorithm, charClassTmpCaseClosure, charClassTmp2, (a, b) -> true, Encodings.UTF_8.getFullSet(), false);
    }

    private CodePointSet ccAtomRangeIgnoreCase(CodePointSet range) {
        assert range.size() == 1;
        assert flags.isIgnoreCase();
        int lo = range.getMin();
        int hi = range.getMax();
        CaseFoldData.CaseFoldTable caseFoldTable = CaseFoldData.getTable(CaseFoldAlgorithm.OracleDBSimple);
        int loLC = caseFoldSingle(caseFoldTable, lo);
        int hiLC = caseFoldSingle(caseFoldTable, hi);
        int rangeLo = Math.min(loLC, hiLC);
        int rangeHi = Math.max(loLC, hiLC);
        CodePointSetAccumulator toRemove = new CodePointSetAccumulator();
        if (UPPER_CASE.contains(lo) != UPPER_CASE.contains(hi)) {
            // oracledb-specific quirk: if range bounds are not of the same case, the range
            // comparison flips from
            // lowercase(lo) <= chr && chr <= lowercase(hi)
            // to
            // lowercase(lo) <= chr || chr <= lowercase(hi)
            if (loLC <= hiLC || hiLC == loLC - 1) {
                return Encodings.UTF_8.getFullSet();
            } else {
                CodePointSet ret = CodePointSet.create(Character.MIN_CODE_POINT, hiLC, loLC, Character.MAX_CODE_POINT);
                for (Range r : ret) {
                    caseFoldTable.caseFold(r, (codePoint, caseFolded) -> {
                        if (!(loLC <= caseFolded[0] || caseFolded[0] <= hiLC)) {
                            toRemove.addCodePoint(codePoint);
                        }
                    });
                }
                return ret.subtract(toRemove.toCodePointSet());
            }
        } else {
            caseFoldTable.caseFold(new Range(rangeLo, rangeHi), (codePoint, caseFolded) -> {
                if (caseFolded[0] < loLC || caseFolded[0] > hiLC) {
                    toRemove.addCodePoint(codePoint);
                }
            });
            return CodePointSet.create(rangeLo, rangeHi).subtract(toRemove.toCodePointSet());
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
            CodePointSet encodingRange = Encodings.UTF_8.getFullSet();
            CompilationBuffer compilationBuffer = lexer.getCompilationBuffer();
            MultiCharacterCaseFolding.caseFoldUnfoldString(CaseFoldAlgorithm.OracleDB, codepoints, encodingRange, false, false, astBuilder, null, compilationBuffer);
        } else {
            for (int i = 0; i < literalStringBuffer.length(); i++) {
                astBuilder.addCharClass(CodePointSet.create(literalStringBuffer.get(i)), true);
            }
        }
        literalStringBuffer.clear();
    }

    private RegexSyntaxException syntaxError(String msg) {
        return RegexSyntaxException.createPattern(source, msg, lexer.getLastTokenPosition());
    }
}
