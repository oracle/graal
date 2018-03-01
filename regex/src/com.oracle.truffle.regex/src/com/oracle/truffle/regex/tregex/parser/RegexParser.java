/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CalcMinPathsVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DeleteVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.InitIDVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.MarkLookBehindEntriesVisitor;
import com.oracle.truffle.regex.util.Constants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.function.Function;

public final class RegexParser {

    private static final Group WORD_BOUNDARY_SUBSTITUTION;
    private static final Group NON_WORD_BOUNDARY_SUBSTITUTION;
    private static final Group UNICODE_IGNORE_CASE_WORD_BOUNDARY_SUBSTITUTION;
    private static final Group UNICODE_IGNORE_CASE_NON_WORD_BOUNDARY_SUBSTITUTION;
    private static final Group MULTI_LINE_CARET_SUBSTITUTION;
    private static final Group MULTI_LINE_DOLLAR_SUBSTITUTION;
    private static final Group NO_LEAD_SURROGATE_BEHIND;
    private static final Group NO_TRAIL_SURROGATE_AHEAD;

    static {
        try {
            final String wordBoundarySrc = "(?:^|(?<=\\W))(?=\\w)|(?<=\\w)(?:(?=\\W)|$)";
            final String nonWordBoundarySrc = "(?:^|(?<=\\W))(?:(?=\\W)|$)|(?<=\\w)(?=\\w)";
            WORD_BOUNDARY_SUBSTITUTION = parseRootLess(wordBoundarySrc);
            NON_WORD_BOUNDARY_SUBSTITUTION = parseRootLess(nonWordBoundarySrc);
            // The definitions of \w and \W depend on whether or not we are using the 'u' and 'i'
            // regexp flags. This means that we cannot substitute \b and \B by the same regular
            // expression all the time; we need an alternative for when both the Unicode and
            // IgnoreCase flags are enabled. The straightforward way to do so would be to parse the
            // expressions `wordBoundarySrc` and `nonWordBoundarySrc` with the 'u' and 'i' flags.
            // However, the resulting expressions would be needlessly complicated (the unicode
            // expansion for \W matches complete surrogate pairs, which we do not care about in
            // these look-around assertions). More importantly, the engine currently does not
            // support look-behind of length greater than 1 and so \W, which can match two code
            // units in Unicode mode, would break the engine. Therefore, we make use of the fact
            // that the difference between /\w/ and /\w/ui is only in the two characters \u017F and
            // \u212A and we just slightly adjust the expressions `wordBoundarySrc` and
            // `nonWordBoundarySrc` and parse them in non-Unicode mode.
            final Function<String, String> includeExtraCases = s -> s.replace("\\w", "[\\w\\u017F\\u212A]").replace("\\W", "[^\\w\\u017F\\u212A]");
            UNICODE_IGNORE_CASE_WORD_BOUNDARY_SUBSTITUTION = parseRootLess(includeExtraCases.apply(wordBoundarySrc));
            UNICODE_IGNORE_CASE_NON_WORD_BOUNDARY_SUBSTITUTION = parseRootLess(includeExtraCases.apply(nonWordBoundarySrc));
            MULTI_LINE_CARET_SUBSTITUTION = parseRootLess("(?:^|(?<=[\\r\\n\\u2028\\u2029]))");
            MULTI_LINE_DOLLAR_SUBSTITUTION = parseRootLess("(?:$|(?=[\\r\\n\\u2028\\u2029]))");
            NO_LEAD_SURROGATE_BEHIND = parseRootLess("(?:^|(?<=[^\\uD800-\\uDBFF]))");
            NO_TRAIL_SURROGATE_AHEAD = parseRootLess("(?:$|(?=[^\\uDC00-\\uDFFF]))");
        } catch (RegexSyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private final RegexAST ast;
    private final RegexSource source;
    private final RegexLexer lexer;
    private final RegexProperties properties;
    private final Counter.ThresholdCounter groupCount;
    private final CopyVisitor copyVisitor;
    private final DeleteVisitor deleteVisitor;

    private Sequence curSequence;
    private Group curGroup;
    private Term curTerm;

    @TruffleBoundary
    public RegexParser(RegexSource source, RegexOptions options) {
        this.source = source;
        this.lexer = new RegexLexer(source, options);
        this.ast = new RegexAST(source);
        this.properties = ast.getProperties();
        this.groupCount = ast.getGroupCount();
        this.copyVisitor = new CopyVisitor(ast);
        this.deleteVisitor = new DeleteVisitor(ast);
    }

    private static Group parseRootLess(String pattern) throws RegexSyntaxException {
        try {
            return new RegexParser(new RegexSource(pattern, RegexFlags.DEFAULT), RegexOptions.DEFAULT).parse(false);
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.flush();
            throw e;
        }
    }

    @TruffleBoundary
    public static RegexAST parse(RegexSource source, RegexOptions options) throws RegexSyntaxException {
        return new RegexParser(source, options).parse();
    }

    @TruffleBoundary
    public static void validate(RegexSource source) throws RegexSyntaxException {
        new RegexParser(source, RegexOptions.DEFAULT).parseDryRun();
    }

    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        ast.setRoot(parse(true));
        final CalcMinPathsVisitor calcMinPathsVisitor = new CalcMinPathsVisitor();
        calcMinPathsVisitor.runReverse(ast.getRoot());
        calcMinPathsVisitor.run(ast.getRoot());
        ast.removeUnreachablePositionAssertions();
        ast.createPrefix();
        InitIDVisitor.init(ast);
        if (!ast.getProperties().hasBackReferences()) {
            new MarkLookBehindEntriesVisitor(ast).run();
        }
        return ast;
    }

    @TruffleBoundary
    public void validate() throws RegexSyntaxException {
        parseDryRun();
    }

    @TruffleBoundary
    public Map<String, Integer> getNamedCaptureGroups() {
        return lexer.getNamedCaptureGroups();
    }

    /* AST manipulation */

    private void setComplexLookAround() {
        if (curGroup.isInLookAheadAssertion()) {
            properties.setComplexLookAheadAssertions();
        }
        if (curGroup.isInLookBehindAssertion()) {
            properties.setComplexLookBehindAssertions();
        }
    }

    private void createGroup() {
        createGroup(true, false, null);
    }

    private void createCaptureGroup() {
        createGroup(true, true, null);
    }

    private Group createGroup(boolean addToSeq, boolean capture, RegexASTSubtreeRootNode parent) {
        Group group = capture ? ast.createCaptureGroup(groupCount.inc()) : ast.createGroup();
        if (parent != null) {
            parent.setGroup(group);
        }
        if (addToSeq) {
            setComplexLookAround();
            addTerm(group);
        }
        curGroup = group;
        curGroup.setEnclosedCaptureGroupsLow(groupCount.getCount());
        addSequence();
        return group;
    }

    private void addSequence() {
        if (!curGroup.getAlternatives().isEmpty()) {
            setComplexLookAround();
        }
        curSequence = curGroup.addSequence(ast);
        curTerm = null;
    }

    private void popGroup() throws RegexSyntaxException {
        curGroup.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        curTerm = curGroup;
        RegexASTNode parent = curGroup.getParent();
        if (parent instanceof RegexASTRootNode) {
            throw syntaxError(ErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
        }
        if (parent instanceof RegexASTSubtreeRootNode) {
            curSequence = (Sequence) parent.getParent();
            curTerm = (Term) parent;
        } else {
            curSequence = (Sequence) parent;
        }
        curGroup = curSequence.getParent();
    }

    private void addTerm(Term term) {
        curSequence.add(term);
        curTerm = term;
    }

    private void addLookBehindAssertion() {
        LookBehindAssertion lookBehind = ast.createLookBehindAssertion();
        addTerm(lookBehind);
        createGroup(false, false, lookBehind);
    }

    private void addLookAheadAssertion(boolean negate) {
        LookAheadAssertion lookAhead = ast.createLookAheadAssertion(negate);
        addTerm(lookAhead);
        createGroup(false, false, lookAhead);
    }

    private Term translateUnicodeCharClass(CodePointSet codePointSet) {
        Group group = ast.createGroup();
        group.setEnclosedCaptureGroupsLow(groupCount.getCount());
        group.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        CodePointSet bmpRanges = Constants.BMP_WITHOUT_SURROGATES.createIntersection(codePointSet);
        CodePointSet astralRanges = Constants.ASTRAL_SYMBOLS.createIntersection(codePointSet);
        CodePointSet loneLeadSurrogateRanges = Constants.LEAD_SURROGATES.createIntersection(codePointSet);
        CodePointSet loneTrailSurrogateRanges = Constants.TRAIL_SURROGATES.createIntersection(codePointSet);

        if (bmpRanges.matchesSomething()) {
            Sequence bmpAlternative = group.addSequence(ast);
            bmpAlternative.add(ast.createCharacterClass(bmpRanges));
        }

        if (loneLeadSurrogateRanges.matchesSomething()) {
            Sequence loneLeadSurrogateAlternative = group.addSequence(ast);
            loneLeadSurrogateAlternative.add(ast.createCharacterClass(loneLeadSurrogateRanges));
            loneLeadSurrogateAlternative.add(NO_TRAIL_SURROGATE_AHEAD.copy(ast));
            properties.setAlternations();
        }

        if (loneTrailSurrogateRanges.matchesSomething()) {
            Sequence loneTrailSurrogateAlternative = group.addSequence(ast);
            loneTrailSurrogateAlternative.add(NO_LEAD_SURROGATE_BEHIND.copy(ast));
            loneTrailSurrogateAlternative.add(ast.createCharacterClass(loneTrailSurrogateRanges));
            properties.setAlternations();
        }

        if (astralRanges.matchesSomething()) {
            // completeRanges matches surrogate pairs where leading surrogates can be followed by
            // any trailing surrogates
            CodePointSet completeRanges = CodePointSet.createEmpty();

            char curLead = Character.highSurrogate(astralRanges.getRanges().get(0).lo);
            CodePointSet curTrails = CodePointSet.createEmpty();
            for (CodePointRange astralRange : astralRanges.getRanges()) {
                char startLead = Character.highSurrogate(astralRange.lo);
                char startTrail = Character.lowSurrogate(astralRange.lo);
                char endLead = Character.highSurrogate(astralRange.hi);
                char endTrail = Character.lowSurrogate(astralRange.hi);

                if (startLead > curLead) {
                    if (curTrails.matchesSomething()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(ast.createCharacterClass(MatcherBuilder.create(curLead)));
                        finishedAlternative.add(ast.createCharacterClass(curTrails));
                    }
                    curLead = startLead;
                    curTrails = CodePointSet.createEmpty();
                }
                if (startLead == endLead) {
                    curTrails.addRange(new CodePointRange(startTrail, endTrail));
                } else {
                    if (startTrail != Constants.TRAIL_SURROGATE_RANGE.lo) {
                        curTrails.addRange(new CodePointRange(startTrail, Constants.TRAIL_SURROGATE_RANGE.hi));
                        assert startLead < Character.MAX_VALUE;
                        startLead = (char) (startLead + 1);
                    }

                    if (curTrails.matchesSomething()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(ast.createCharacterClass(MatcherBuilder.create(curLead)));
                        finishedAlternative.add(ast.createCharacterClass(curTrails));
                    }
                    curLead = endLead;
                    curTrails = CodePointSet.createEmpty();

                    if (endTrail != Constants.TRAIL_SURROGATE_RANGE.hi) {
                        curTrails.addRange(new CodePointRange(Constants.TRAIL_SURROGATE_RANGE.lo, endTrail));
                        assert endLead > Character.MIN_VALUE;
                        endLead = (char) (endLead - 1);
                    }

                    if (startLead <= endLead) {
                        completeRanges.addRange(new CodePointRange(startLead, endLead));
                    }

                }
            }
            if (curTrails.matchesSomething()) {
                Sequence lastAlternative = group.addSequence(ast);
                lastAlternative.add(ast.createCharacterClass(MatcherBuilder.create(curLead)));
                lastAlternative.add(ast.createCharacterClass(curTrails));
            }

            if (completeRanges.matchesSomething()) {
                // Complete ranges match more often and so we want them as an early alternative
                Sequence completeRangesAlt = ast.createSequence();
                group.insertFirst(completeRangesAlt);
                completeRangesAlt.add(ast.createCharacterClass(completeRanges));
                completeRangesAlt.add(ast.createCharacterClass(MatcherBuilder.createTrailSurrogateRange()));
            }
        }

        if (group.getAlternatives().size() > 1) {
            properties.setAlternations();
        }

        if (group.getAlternatives().size() == 1 && group.getAlternatives().get(0).getTerms().size() == 1) {
            // If we are generating a group with only one alternative consisting of one term, then
            // we unwrap the group and return the term directly (this makes inspecting the resulting
            // terms easier).
            // NB: This happens when the codePointSet contains only elements from the BMP which are
            // not surrogates.
            return group.getAlternatives().get(0).getTerms().get(0);
        }
        return group;
    }

    private void addCharClass(CodePointSet codePointSet) {
        if (source.getFlags().isUnicode()) {
            if (codePointSet.matchesNothing()) {
                // We need this branch because a Group with no alternatives is invalid
                addCharClass(MatcherBuilder.createEmpty());
            } else {
                addTerm(translateUnicodeCharClass(codePointSet));
            }
        } else {
            addCharClass(MatcherBuilder.create(codePointSet));
        }
    }

    private void addCharClass(MatcherBuilder matcherBuilder) {
        addTerm(ast.createCharacterClass(matcherBuilder));
    }

    private void createOptionalBranch(Term term, boolean greedy, int recurse) throws RegexSyntaxException {
        addTerm(copyVisitor.copy(term));
        // When translating a quantified expression that allows zero occurrences into a
        // disjunction of the form (curTerm|), we must make sure that curTerm cannot match the
        // empty string, as is specified in step 2a of RepeatMatcher from ECMAScript draft 2018,
        // chapter 21.2.2.5.1.
        curTerm.setEmptyGuard(true);
        if (curTerm instanceof Group) {
            ((Group) curTerm).setExpandedQuantifier(true);
        }
        createOptional(term, greedy, recurse - 1);
    }

    private void createOptional(Term term, boolean greedy, int recurse) throws RegexSyntaxException {
        if (recurse < 0) {
            return;
        }
        properties.setAlternations();
        createGroup();
        if (term instanceof Group) {
            curGroup.setEnclosedCaptureGroupsLow(((Group) term).getEnclosedCaptureGroupsLow());
            curGroup.setEnclosedCaptureGroupsHigh(((Group) term).getEnclosedCaptureGroupsHigh());
        }
        if (greedy) {
            createOptionalBranch(term, greedy, recurse);
            addSequence();
        } else {
            addSequence();
            createOptionalBranch(term, greedy, recurse);
        }
        popGroup();
    }

    private void setLoop() {
        properties.setLoops();
        assert curTerm instanceof Group;
        ((Group) curTerm).setLoop(true);
    }

    private boolean curTermIsAnchor(PositionAssertion.Type type) {
        return curTerm != null && curTerm instanceof PositionAssertion && ((PositionAssertion) curTerm).type == type;
    }

    private void substitute(Group substitution) {
        addTerm(substitution.copy(ast));
    }

    /* parser */

    private Group parse(boolean rootCapture) throws RegexSyntaxException {
        RegexASTRootNode rootParent = ast.createRootNode();
        Group root = createGroup(false, rootCapture, rootParent);
        while (lexer.hasNext()) {
            Token token = lexer.next();
            switch (token.kind) {
                case caret:
                    if (source.getFlags().isMultiline()) {
                        substitute(MULTI_LINE_CARET_SUBSTITUTION);
                        properties.setAlternations();
                    } else if (!curTermIsAnchor(PositionAssertion.Type.CARET)) {
                        PositionAssertion caret = ast.createPositionAssertion(PositionAssertion.Type.CARET);
                        addTerm(caret);
                    }
                    break;
                case dollar:
                    if (source.getFlags().isMultiline()) {
                        substitute(MULTI_LINE_DOLLAR_SUBSTITUTION);
                        properties.setAlternations();
                    } else if (!curTermIsAnchor(PositionAssertion.Type.DOLLAR)) {
                        PositionAssertion dollar = ast.createPositionAssertion(PositionAssertion.Type.DOLLAR);
                        addTerm(dollar);
                    }
                    break;
                case wordBoundary:
                    if (source.getFlags().isUnicode() && source.getFlags().isIgnoreCase()) {
                        substitute(UNICODE_IGNORE_CASE_WORD_BOUNDARY_SUBSTITUTION);
                    } else {
                        substitute(WORD_BOUNDARY_SUBSTITUTION);
                    }
                    properties.setAlternations();
                    break;
                case nonWordBoundary:
                    if (source.getFlags().isUnicode() && source.getFlags().isIgnoreCase()) {
                        substitute(UNICODE_IGNORE_CASE_NON_WORD_BOUNDARY_SUBSTITUTION);
                    } else {
                        substitute(NON_WORD_BOUNDARY_SUBSTITUTION);
                    }
                    properties.setAlternations();
                    break;
                case backReference:
                    addTerm(ast.createBackReference(((Token.BackReference) token).getGroupNr()));
                    break;
                case quantifier:
                    parseQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    addSequence();
                    properties.setAlternations();
                    break;
                case captureGroupBegin:
                    properties.setCaptureGroups();
                    createCaptureGroup();
                    break;
                case nonCaptureGroupBegin:
                    createGroup();
                    break;
                case lookAheadAssertionBegin:
                    addLookAheadAssertion(false);
                    break;
                case lookBehindAssertionBegin:
                    addLookBehindAssertion();
                    break;
                case negativeLookAheadAssertionBegin:
                    addLookAheadAssertion(true);
                    break;
                case groupEnd:
                    popGroup();
                    break;
                case charClass:
                    addCharClass(((Token.CharacterClass) token).getCodePointSet());
                    break;
            }
        }
        if (curGroup != root) {
            throw syntaxError(ErrorMessages.UNTERMINATED_GROUP);
        }
        return root;
    }

    private void parseQuantifier(Token.Quantifier quantifier) throws RegexSyntaxException {
        if (curTerm == null) {
            throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        final boolean onAssertion = curTerm instanceof RegexASTSubtreeRootNode;
        if (source.getFlags().isUnicode() && onAssertion) {
            throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKAROUND_ASSERTION);
        }
        assert curTerm == curSequence.getLastTerm();
        if (quantifier.getMin() == -1) {
            deleteVisitor.run(curSequence.getLastTerm());
            curSequence.removeLast();
            addCharClass(MatcherBuilder.createEmpty());
            curSequence.markAsDead();
            return;
        }
        if (quantifier.getMin() == 0) {
            deleteVisitor.run(curSequence.getLastTerm());
            curSequence.removeLast();
        }
        Term t = curTerm;
        if (!onAssertion) {
            if (quantifier.getMin() > TRegexOptions.TRegexMaxCountedRepetition || quantifier.getMax() > TRegexOptions.TRegexMaxCountedRepetition) {
                properties.setLargeCountedRepetitions();
                // avoid tree explosion. note that this will result in an incorrect parse tree!
                return;
            }
            for (int i = quantifier.getMin(); i > 1; i--) {
                addTerm(copyVisitor.copy(t));
                if (curTerm instanceof Group) {
                    ((Group) curTerm).setExpandedQuantifier(true);
                }
            }
            if (quantifier.isInfiniteLoop()) {
                createOptional(t, quantifier.isGreedy(), 0);
                setLoop();
            } else {
                createOptional(t, quantifier.isGreedy(), (quantifier.getMax() - quantifier.getMin()) - 1);
            }
        }
    }

    private RegexSyntaxException syntaxError(String msg) {
        return new RegexSyntaxException(source.getPattern(), source.getFlags(), msg);
    }

    /**
     * A type representing an entry in the stack of currently open parenthesized expressions in a
     * RegExp.
     */
    private enum RegexStackElem {
        Group,
        LookAroundAssertion
    }

    /**
     * Information about the state of the {@link #curTerm} field. The field can be either null,
     * point to a lookaround assertion node or to some other non-null node.
     */
    private enum CurTermState {
        Null,
        LookAroundAssertion,
        Other
    }

    /**
     * Like {@link #parse(boolean)}, but does not construct any AST, only checks for syntax errors.
     * <p>
     * This method simulates the state of {@link RegexParser} as it runs {@link #parse(boolean)}.
     * Most of the syntax errors are handled by {@link RegexLexer}. In order to correctly identify
     * the remaining syntax errors, we need to track only a fraction of the parser's state (the
     * stack of open parenthesized expressions and a short characterization of the last term).
     * <p>
     * Unlike {@link #parse(boolean)}, this method will never throw an
     * {@link UnsupportedRegexException}.
     * 
     * @throws RegexSyntaxException when a syntax error is detected in the RegExp
     */
    private void parseDryRun() throws RegexSyntaxException {
        List<RegexStackElem> syntaxStack = new ArrayList<>();
        CurTermState curTermState = CurTermState.Null;
        while (lexer.hasNext()) {
            Token token = lexer.next();
            switch (token.kind) {
                case caret:
                case dollar:
                case wordBoundary:
                case nonWordBoundary:
                case backReference:
                case charClass:
                    curTermState = CurTermState.Other;
                    break;
                case quantifier:
                    if (curTermState == CurTermState.Null) {
                        throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
                    }
                    if (source.getFlags().isUnicode() && curTermState == CurTermState.LookAroundAssertion) {
                        throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKAROUND_ASSERTION);
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
                case lookBehindAssertionBegin:
                case negativeLookAheadAssertionBegin:
                    syntaxStack.add(RegexStackElem.LookAroundAssertion);
                    curTermState = CurTermState.Null;
                    break;
                case groupEnd:
                    if (syntaxStack.isEmpty()) {
                        throw syntaxError(ErrorMessages.UNMATCHED_RIGHT_PARENTHESIS);
                    }
                    RegexStackElem poppedElem = syntaxStack.remove(syntaxStack.size() - 1);
                    if (poppedElem == RegexStackElem.LookAroundAssertion) {
                        curTermState = CurTermState.LookAroundAssertion;
                    } else { // poppedElem == RegexStackElem.Group
                        curTermState = CurTermState.Other;
                    }
                    break;
            }
        }
        if (!syntaxStack.isEmpty()) {
            throw syntaxError(ErrorMessages.UNTERMINATED_GROUP);
        }
    }

}
