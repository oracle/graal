/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;

public class RegexValidator {

    private final RegexSource source;
    private final RegexFlags flags;
    private final RegexLexer lexer;
    private RegexFeatures features;

    public RegexValidator(RegexSource source, RegexOptions options) {
        this.source = source;
        this.flags = RegexFlags.parseFlags(source.getFlags());
        this.lexer = new RegexLexer(source, flags, options);
    }

    @TruffleBoundary
    public static void validate(RegexSource source) throws RegexSyntaxException {
        new RegexValidator(source, RegexOptions.DEFAULT).validate();
    }

    @TruffleBoundary
    public void validate() throws RegexSyntaxException {
        features = new RegexFeatures();
        parseDryRun();
    }

    /**
     * Returns the features used by the regular expression that was just validated. This property is
     * only populated after a call to {@link #validate()} and should therefore only be accessed
     * then.
     */
    public RegexFeatures getFeatures() {
        assert features != null;
        return features;
    }

    @TruffleBoundary
    public int getNumberOfCaptureGroups() {
        return lexer.numberOfCaptureGroups();
    }

    @TruffleBoundary
    public Map<String, Integer> getNamedCaptureGroups() {
        return lexer.getNamedCaptureGroups();
    }

    /**
     * A type representing an entry in the stack of currently open parenthesized expressions in a
     * RegExp.
     */
    private enum RegexStackElem {
        Group,
        LookAheadAssertion,
        LookBehindAssertion
    }

    /**
     * Information about the state of the current term. It can be either null, point to a lookahead
     * assertion node, to a lookbehind assertion node or to some other non-null node.
     */
    private enum CurTermState {
        Null,
        LookAheadAssertion,
        LookBehindAssertion,
        Other
    }

    /**
     * Like {@link RegexParser#parse()}, but does not construct any AST, only checks for syntax
     * errors.
     * <p>
     * This method simulates the state of {@link RegexParser} running {@link RegexParser#parse()}.
     * Most of the syntax errors are handled by {@link RegexLexer}. In order to correctly identify
     * the remaining syntax errors, we need to track only a fraction of the parser's state (the
     * stack of open parenthesized expressions and a short characterization of the last term).
     * <p>
     * Unlike {@link RegexParser#parse()}, this method will never throw an
     * {@link UnsupportedRegexException}.
     *
     * @throws RegexSyntaxException when a syntax error is detected in the RegExp
     */
    private void parseDryRun() throws RegexSyntaxException {
        List<RegexStackElem> syntaxStack = new ArrayList<>();
        int lookBehindDepth = 0;
        CurTermState curTermState = CurTermState.Null;
        while (lexer.hasNext()) {
            Token token = lexer.next();
            if (lookBehindDepth > 0 && token.kind != Token.Kind.charClass && token.kind != Token.Kind.groupEnd) {
                features.setNonLiteralLookBehindAssertions();
            }
            switch (token.kind) {
                case caret:
                    curTermState = CurTermState.Other;
                    break;
                case dollar:
                    if (lookBehindDepth > 0 && !flags.isMultiline()) {
                        features.setEndOfStringAssertionsInLookBehind();
                    }
                    curTermState = CurTermState.Other;
                    break;
                case wordBoundary:
                case nonWordBoundary:
                    if (lookBehindDepth > 0) {
                        features.setWordBoundaryAssertionsInLookBehind();
                    }
                    curTermState = CurTermState.Other;
                    break;
                case backReference:
                    features.setBackReferences();
                    if (lookBehindDepth > 0) {
                        features.setBackReferencesInLookBehind();
                    }
                    curTermState = CurTermState.Other;
                    break;
                case charClass:
                    curTermState = CurTermState.Other;
                    break;
                case quantifier:
                    switch (curTermState) {
                        case Null:
                            throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
                        case LookAheadAssertion:
                            if (flags.isUnicode()) {
                                throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION);
                            }
                            break;
                        case LookBehindAssertion:
                            throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION);
                        case Other:
                            Token.Quantifier quantifier = (Token.Quantifier) token;
                            if (lookBehindDepth > 0 && quantifier.getMin() != quantifier.getMax()) {
                                features.setNonTrivialQuantifiersInLookBehind();
                            }
                            int threshold = Math.max(TRegexOptions.TRegexQuantifierUnrollThresholdSingleCC, TRegexOptions.TRegexQuantifierUnrollThresholdGroup);
                            if (quantifier.getMin() > threshold || quantifier.getMax() > threshold) {
                                features.setLargeCountedRepetitions();
                            }
                            break;
                    }
                    curTermState = CurTermState.Other;
                    break;
                case alternation:
                    curTermState = CurTermState.Null;
                    break;
                case captureGroupBegin:
                case nonCaptureGroupBegin:
                    syntaxStack.add(RegexStackElem.Group);
                    curTermState = CurTermState.Null;
                    break;
                case lookAheadAssertionBegin:
                    if (((Token.LookAheadAssertionBegin) token).isNegated()) {
                        features.setNegativeLookAheadAssertions();
                    }
                    if (lookBehindDepth > 0) {
                        features.setLookAheadAssertionsInLookBehind();
                    }
                    syntaxStack.add(RegexStackElem.LookAheadAssertion);
                    curTermState = CurTermState.Null;
                    break;
                case lookBehindAssertionBegin:
                    if (((Token.LookBehindAssertionBegin) token).isNegated()) {
                        features.setNegativeLookBehindAssertions();
                        if (lookBehindDepth > 0) {
                            features.setNegativeLookBehindAssertionsInLookBehind();
                        }
                    }
                    syntaxStack.add(RegexStackElem.LookBehindAssertion);
                    lookBehindDepth++;
                    curTermState = CurTermState.Null;
                    break;
                case groupEnd:
                    if (syntaxStack.isEmpty()) {
                        throw syntaxError(ErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                    }
                    RegexStackElem poppedElem = syntaxStack.remove(syntaxStack.size() - 1);
                    switch (poppedElem) {
                        case LookAheadAssertion:
                            curTermState = CurTermState.LookAheadAssertion;
                            break;
                        case LookBehindAssertion:
                            lookBehindDepth--;
                            curTermState = CurTermState.LookBehindAssertion;
                            break;
                        case Group:
                            curTermState = CurTermState.Other;
                            break;
                    }
                    break;
            }
        }
        if (!syntaxStack.isEmpty()) {
            throw syntaxError(ErrorMessages.UNTERMINATED_GROUP);
        }
    }

    private RegexSyntaxException syntaxError(String msg) {
        return new RegexSyntaxException(source, msg);
    }
}
