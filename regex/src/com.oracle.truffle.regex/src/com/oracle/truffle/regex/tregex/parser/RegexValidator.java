/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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

    public RegexValidator(RegexSource source, RegexFlags flags, RegexOptions options) {
        this.source = source;
        this.flags = flags;
        this.lexer = new RegexLexer(source, flags, options);
    }

    @TruffleBoundary
    public static void validate(RegexSource source) throws RegexSyntaxException {
        new RegexValidator(source, RegexFlags.parseFlags(source.getFlags()), RegexOptions.DEFAULT).validate();
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
                            if (quantifier.getMin() > TRegexOptions.TRegexMaxCountedRepetition || quantifier.getMax() > TRegexOptions.TRegexMaxCountedRepetition) {
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
