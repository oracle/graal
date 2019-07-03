/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.SortedListOfRanges;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
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
import com.oracle.truffle.regex.tregex.parser.ast.visitors.SetSourceSectionVisitor;

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
        // support complex lookbehind and so \W, which can match anywhere between one or two code
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
    }

    private final RegexAST ast;
    private final RegexSource source;
    private final RegexFlags flags;
    private final RegexOptions options;
    private final RegexLexer lexer;
    private final RegexProperties properties;
    private final Counter.ThresholdCounter groupCount;
    private final CopyVisitor copyVisitor;
    private final DeleteVisitor deleteVisitor;
    private final SetSourceSectionVisitor setSourceSectionVisitor;

    private Sequence curSequence;
    private Group curGroup;
    private Term curTerm;

    private RegexFeatures features;

    private IntRangesBuffer tmpRangesBuffer1;
    private IntRangesBuffer tmpRangesBuffer2;

    @TruffleBoundary
    public RegexParser(RegexSource source, RegexOptions options) throws RegexSyntaxException {
        this.source = source;
        this.flags = RegexFlags.parseFlags(source.getFlags());
        this.options = options;
        this.lexer = new RegexLexer(source, flags, options);
        this.ast = new RegexAST(source, flags, options);
        this.properties = ast.getProperties();
        this.groupCount = ast.getGroupCount();
        this.copyVisitor = new CopyVisitor(ast);
        this.deleteVisitor = new DeleteVisitor(ast);
        this.setSourceSectionVisitor = options.isDumpAutomata() ? new SetSourceSectionVisitor() : null;
    }

    private static Group parseRootLess(String pattern) throws RegexSyntaxException {
        return new RegexParser(new RegexSource(pattern), RegexOptions.DEFAULT).parse(false);
    }

    @TruffleBoundary
    public static void validate(RegexSource source) throws RegexSyntaxException {
        new RegexParser(source, RegexOptions.DEFAULT).validate();
    }

    @TruffleBoundary
    public RegexAST parse() throws RegexSyntaxException {
        ast.setRoot(parse(true));
        for (LookBehindAssertion lb : ast.getLookBehinds()) {
            if (!lb.isLiteral()) {
                properties.setNonLiteralLookBehindAssertions();
                break;
            }
        }
        final CalcMinPathsVisitor calcMinPathsVisitor = new CalcMinPathsVisitor();
        calcMinPathsVisitor.runReverse(ast.getRoot());
        calcMinPathsVisitor.run(ast.getRoot());
        ast.removeUnreachablePositionAssertions();
        if (!properties.hasNonLiteralLookBehindAssertions()) {
            ast.createPrefix();
            InitIDVisitor.init(ast);
            if (!properties.hasBackReferences()) {
                new MarkLookBehindEntriesVisitor(ast).run();
            }
        }
        checkInnerLiteral();
        return ast;
    }

    private void checkInnerLiteral() {
        if (ast.isLiteralString() || ast.getRoot().startsWithCaret() || ast.getRoot().endsWithDollar() || ast.getRoot().getAlternatives().size() != 1) {
            return;
        }
        ArrayList<Term> terms = ast.getRoot().getAlternatives().get(0).getTerms();
        int literalStart = -1;
        int literalEnd = -1;
        int maxPath = 0;
        for (int i = 0; i < terms.size(); i++) {
            Term t = terms.get(i);
            if (t instanceof CharacterClass && ((CharacterClass) t).getMatcherBuilder().matchesSingleChar()) {
                if (literalStart < 0) {
                    literalStart = i;
                }
                literalEnd = i + 1;
            } else if (literalStart >= 0 || t.hasLoops()) {
                break;
            } else {
                maxPath = t.getMaxPath();
                if (maxPath > 4) {
                    return;
                }
            }
        }
        if (literalStart >= 0 && (literalStart > 0 || literalEnd - literalStart > 1)) {
            StringBuilder sb = new StringBuilder(literalEnd - literalStart);
            for (int i = literalStart; i < literalEnd; i++) {
                sb.append(((CharacterClass) terms.get(i)).getMatcherBuilder().getLo(0));
            }
            properties.setInnerLiteral(sb.toString());
        }
    }

    @TruffleBoundary
    public void validate() throws RegexSyntaxException {
        features = new RegexFeatures();
        parseDryRun();
    }

    @TruffleBoundary
    public int getNumberOfCaptureGroups() {
        return lexer.numberOfCaptureGroups();
    }

    @TruffleBoundary
    public Map<String, Integer> getNamedCaptureGroups() {
        return lexer.getNamedCaptureGroups();
    }

    public RegexFlags getFlags() {
        return flags;
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

    /* AST manipulation */

    private void setComplexLookAround() {
        if (curGroup.isInLookAheadAssertion()) {
            properties.setComplexLookAheadAssertions();
        }
        if (curGroup.isInLookBehindAssertion()) {
            properties.setComplexLookBehindAssertions();
        }
    }

    private void createGroup(Token token) {
        createGroup(token, true, false, null);
    }

    private void createCaptureGroup(Token token) {
        createGroup(token, true, true, null);
    }

    private Group createGroup(Token token, boolean addToSeq, boolean capture, RegexASTSubtreeRootNode parent) {
        Group group = capture ? ast.createCaptureGroup(groupCount.inc()) : ast.createGroup();
        if (parent != null) {
            parent.setGroup(group);
        }
        if (addToSeq) {
            setComplexLookAround();
            addTerm(group);
        }
        if (token != null) {
            group.setSourceSectionBegin(token.getSourceSection());
        }
        curGroup = group;
        curGroup.setEnclosedCaptureGroupsLow(groupCount.getCount());
        addSequence(token);
        return group;
    }

    /**
     * Adds a new {@link Sequence} to the current {@link Group}.
     *
     * @param token the opening bracket of the parent group ({@link Token.Kind#captureGroupBegin})
     *            or the alternation symbol ({@link Token.Kind#alternation}) that opens the new
     *            sequence.
     */
    private void addSequence(Token token) {
        if (!curGroup.getAlternatives().isEmpty()) {
            setComplexLookAround();
        }
        curSequence = curGroup.addSequence(ast);
        if (options.isDumpAutomata()) {
            if (token != null) {
                SourceSection src = token.getSourceSection();
                // set source section to empty string, it will be updated by the Sequence object
                // when new Terms are added to it
                curSequence.setSourceSection(src.getSource().createSection(src.getCharEndIndex(), 0));
            }
        }
        curTerm = null;
    }

    private void popGroup(Token token) throws RegexSyntaxException {
        curGroup.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        if (token != null) {
            curGroup.setSourceSectionEnd(token.getSourceSection());
        }
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

    private void addLookBehindAssertion(Token token, boolean negate) {
        LookBehindAssertion lookBehind = ast.createLookBehindAssertion(negate);
        addTerm(lookBehind);
        createGroup(token, false, false, lookBehind);
    }

    private void addLookAheadAssertion(Token token, boolean negate) {
        LookAheadAssertion lookAhead = ast.createLookAheadAssertion(negate);
        addTerm(lookAhead);
        createGroup(token, false, false, lookAhead);
    }

    private IntRangesBuffer getTmpRangesBuffer1() {
        if (tmpRangesBuffer1 == null) {
            tmpRangesBuffer1 = new IntRangesBuffer();
        }
        return tmpRangesBuffer1;
    }

    private IntRangesBuffer getTmpRangesBuffer2() {
        if (tmpRangesBuffer2 == null) {
            tmpRangesBuffer2 = new IntRangesBuffer();
        }
        return tmpRangesBuffer2;
    }

    private Term translateUnicodeCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        SourceSection src = token.getSourceSection();
        Group group = ast.createGroup();
        group.setEnclosedCaptureGroupsLow(groupCount.getCount());
        group.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        IntRangesBuffer tmp = getTmpRangesBuffer1();
        CodePointSet bmpRanges = codePointSet.createIntersection(Constants.BMP_WITHOUT_SURROGATES, tmp);
        CodePointSet astralRanges = codePointSet.createIntersection(Constants.ASTRAL_SYMBOLS, tmp);
        CodePointSet loneLeadSurrogateRanges = codePointSet.createIntersection(Constants.LEAD_SURROGATES, tmp);
        CodePointSet loneTrailSurrogateRanges = codePointSet.createIntersection(Constants.TRAIL_SURROGATES, tmp);

        if (bmpRanges.matchesSomething()) {
            Sequence bmpAlternative = group.addSequence(ast);
            bmpAlternative.add(createCharClass(bmpRanges, src));
        }

        if (loneLeadSurrogateRanges.matchesSomething()) {
            Sequence loneLeadSurrogateAlternative = group.addSequence(ast);
            loneLeadSurrogateAlternative.add(createCharClass(loneLeadSurrogateRanges, src));
            loneLeadSurrogateAlternative.add(NO_TRAIL_SURROGATE_AHEAD.copy(ast, true));
            properties.setAlternations();
        }

        if (loneTrailSurrogateRanges.matchesSomething()) {
            Sequence loneTrailSurrogateAlternative = group.addSequence(ast);
            loneTrailSurrogateAlternative.add(NO_LEAD_SURROGATE_BEHIND.copy(ast, true));
            loneTrailSurrogateAlternative.add(createCharClass(loneTrailSurrogateRanges, src));
            properties.setAlternations();
        }

        if (astralRanges.matchesSomething()) {
            // completeRanges matches surrogate pairs where leading surrogates can be followed by
            // any trailing surrogates
            IntRangesBuffer completeRanges = getTmpRangesBuffer2();
            completeRanges.clear();

            char curLead = Character.highSurrogate(astralRanges.getLo(0));
            IntRangesBuffer curTrails = tmp;
            curTrails.clear();
            for (int i = 0; i < astralRanges.size(); i++) {
                char startLead = Character.highSurrogate(astralRanges.getLo(i));
                final char startTrail = Character.lowSurrogate(astralRanges.getLo(i));
                char endLead = Character.highSurrogate(astralRanges.getHi(i));
                final char endTrail = Character.lowSurrogate(astralRanges.getHi(i));

                if (startLead > curLead) {
                    if (curTrails.matchesSomething()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(createCharClass(CharSet.create(curLead), src));
                        finishedAlternative.add(createCharClass(curTrails, src));
                    }
                    curLead = startLead;
                    curTrails.clear();
                }
                if (startLead == endLead) {
                    curTrails.addRange(startTrail, endTrail);
                } else {
                    if (startTrail != Constants.TRAIL_SURROGATE_RANGE.getLo(0)) {
                        curTrails.addRange(startTrail, Constants.TRAIL_SURROGATE_RANGE.getHi(0));
                        assert startLead < Character.MAX_VALUE;
                        startLead = (char) (startLead + 1);
                    }

                    if (curTrails.matchesSomething()) {
                        Sequence finishedAlternative = group.addSequence(ast);
                        finishedAlternative.add(createCharClass(CharSet.create(curLead), src));
                        finishedAlternative.add(createCharClass(curTrails, src));
                    }
                    curLead = endLead;
                    curTrails.clear();

                    if (endTrail != Constants.TRAIL_SURROGATE_RANGE.getHi(0)) {
                        curTrails.addRange(Constants.TRAIL_SURROGATE_RANGE.getLo(0), endTrail);
                        assert endLead > Character.MIN_VALUE;
                        endLead = (char) (endLead - 1);
                    }

                    if (startLead <= endLead) {
                        completeRanges.addRange(startLead, endLead);
                    }

                }
            }
            if (curTrails.matchesSomething()) {
                Sequence lastAlternative = group.addSequence(ast);
                lastAlternative.add(createCharClass(CharSet.create(curLead), src));
                lastAlternative.add(createCharClass(curTrails, src));
            }

            if (completeRanges.matchesSomething()) {
                // Complete ranges match more often and so we want them as an early alternative
                Sequence completeRangesAlt = ast.createSequence();
                group.insertFirst(completeRangesAlt);
                completeRangesAlt.add(createCharClass(completeRanges, src));
                completeRangesAlt.add(createCharClass(CharSet.getTrailSurrogateRange(), src));
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

    private void addCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        if (flags.isUnicode()) {
            if (codePointSet.matchesNothing()) {
                // We need this branch because a Group with no alternatives is invalid
                addTerm(createCharClass(CharSet.getEmpty(), token.getSourceSection()));
            } else {
                addTerm(translateUnicodeCharClass(token));
            }
        } else {
            addTerm(createCharClass(codePointSet, token.getSourceSection()));
        }
    }

    private CharacterClass createCharClass(SortedListOfRanges codePointSet, SourceSection sourceSection) {
        return createCharClass(CharSet.fromSortedRanges(codePointSet), sourceSection);
    }

    private CharacterClass createCharClass(CharSet matcherBuilder, SourceSection sourceSection) {
        CharacterClass characterClass = ast.createCharacterClass(matcherBuilder);
        characterClass.setSourceSection(sourceSection);
        return characterClass;
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
        createGroup(null);
        if (term instanceof Group) {
            curGroup.setEnclosedCaptureGroupsLow(((Group) term).getEnclosedCaptureGroupsLow());
            curGroup.setEnclosedCaptureGroupsHigh(((Group) term).getEnclosedCaptureGroupsHigh());
        }
        if (greedy) {
            createOptionalBranch(term, greedy, recurse);
            addSequence(null);
        } else {
            addSequence(null);
            createOptionalBranch(term, greedy, recurse);
        }
        popGroup(null);
    }

    private void setLoop() {
        assert curTerm instanceof Group;
        ((Group) curTerm).setLoop(true);
    }

    private boolean curTermIsAnchor(PositionAssertion.Type type) {
        return curTerm instanceof PositionAssertion && ((PositionAssertion) curTerm).type == type;
    }

    private void substitute(Token token, Group substitution) {
        Group copy = substitution.copy(ast, true);
        if (options.isDumpAutomata()) {
            setSourceSectionVisitor.run(copy, token.getSourceSection());
        }
        addTerm(copy);
    }

    /* parser */

    private Group parse(boolean rootCapture) throws RegexSyntaxException {
        RegexASTRootNode rootParent = ast.createRootNode();
        Group root = createGroup(null, false, rootCapture, rootParent);
        if (options.isDumpAutomata()) {
            root.setSourceSectionBegin(ast.getSource().getSource().createSection(0, 1));
            root.setSourceSectionEnd(ast.getSource().getSource().createSection(ast.getSource().getPattern().length() + 1, 1));
        }
        while (lexer.hasNext()) {
            Token token = lexer.next();
            switch (token.kind) {
                case caret:
                    if (flags.isMultiline()) {
                        substitute(token, MULTI_LINE_CARET_SUBSTITUTION);
                        properties.setAlternations();
                    } else if (!curTermIsAnchor(PositionAssertion.Type.CARET)) {
                        PositionAssertion caret = ast.createPositionAssertion(PositionAssertion.Type.CARET);
                        caret.setSourceSection(token.getSourceSection());
                        addTerm(caret);
                    }
                    break;
                case dollar:
                    if (flags.isMultiline()) {
                        substitute(token, MULTI_LINE_DOLLAR_SUBSTITUTION);
                        properties.setAlternations();
                    } else if (!curTermIsAnchor(PositionAssertion.Type.DOLLAR)) {
                        PositionAssertion dollar = ast.createPositionAssertion(PositionAssertion.Type.DOLLAR);
                        dollar.setSourceSection(token.getSourceSection());
                        addTerm(dollar);
                    }
                    break;
                case wordBoundary:
                    if (flags.isUnicode() && flags.isIgnoreCase()) {
                        substitute(token, UNICODE_IGNORE_CASE_WORD_BOUNDARY_SUBSTITUTION);
                    } else {
                        substitute(token, WORD_BOUNDARY_SUBSTITUTION);
                    }
                    properties.setAlternations();
                    break;
                case nonWordBoundary:
                    if (flags.isUnicode() && flags.isIgnoreCase()) {
                        substitute(token, UNICODE_IGNORE_CASE_NON_WORD_BOUNDARY_SUBSTITUTION);
                    } else {
                        substitute(token, NON_WORD_BOUNDARY_SUBSTITUTION);
                    }
                    properties.setAlternations();
                    break;
                case backReference:
                    BackReference backReference = ast.createBackReference(((Token.BackReference) token).getGroupNr());
                    backReference.setSourceSection(token.getSourceSection());
                    addTerm(backReference);
                    break;
                case quantifier:
                    parseQuantifier((Token.Quantifier) token);
                    break;
                case alternation:
                    if (!tryMergeSingleCharClassAlternations()) {
                        addSequence(token);
                        properties.setAlternations();
                    }
                    break;
                case captureGroupBegin:
                    properties.setCaptureGroups();
                    createCaptureGroup(token);
                    break;
                case nonCaptureGroupBegin:
                    createGroup(token);
                    break;
                case lookAheadAssertionBegin:
                    addLookAheadAssertion(token, ((Token.LookAheadAssertionBegin) token).isNegated());
                    break;
                case lookBehindAssertionBegin:
                    addLookBehindAssertion(token, ((Token.LookBehindAssertionBegin) token).isNegated());
                    break;
                case groupEnd:
                    if (tryMergeSingleCharClassAlternations()) {
                        curGroup.removeLastSequence();
                    }
                    popGroup(token);
                    break;
                case charClass:
                    addCharClass((Token.CharacterClass) token);
                    break;
            }
        }
        if (curGroup != root) {
            throw syntaxError(ErrorMessages.UNTERMINATED_GROUP);
        }
        root.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        return root;
    }

    private void parseQuantifier(Token.Quantifier quantifier) throws RegexSyntaxException {
        if (curTerm == null) {
            throw syntaxError(ErrorMessages.QUANTIFIER_WITHOUT_TARGET);
        }
        if (flags.isUnicode() && curTerm instanceof LookAheadAssertion) {
            throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKAHEAD_ASSERTION);
        }
        if (curTerm instanceof LookBehindAssertion) {
            throw syntaxError(ErrorMessages.QUANTIFIER_ON_LOOKBEHIND_ASSERTION);
        }
        assert curTerm == curSequence.getLastTerm();
        if (quantifier.getMin() == -1) {
            deleteVisitor.run(curSequence.getLastTerm());
            curSequence.removeLastTerm();
            addTerm(createCharClass(CharSet.getEmpty(), null));
            curSequence.markAsDead();
            return;
        }
        if (quantifier.getMin() == 0) {
            deleteVisitor.run(curSequence.getLastTerm());
            curSequence.removeLastTerm();
        }
        Term t = curTerm;
        if (!(curTerm instanceof LookAroundAssertion)) {
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

    /**
     * This method should be called when {@code curSequence} is about to be closed. If the current
     * {@link Sequence} <em>and</em> the last {@link Sequence} consist of a single
     * {@link CharacterClass} each, the {@link CharacterClass} contained in the current
     * {@link Sequence} will be removed and merged into the last {@link Sequence}'s
     * {@link CharacterClass}, resulting in a smaller NFA.
     *
     * @return {@code true} if the {@link CharacterClass} in the current sequence was merged with
     *         the {@link CharacterClass} in the last Sequence.
     */
    private boolean tryMergeSingleCharClassAlternations() {
        if (curGroup.size() > 1 && curSequence.isSingleCharClass()) {
            assert curSequence == curGroup.getAlternatives().get(curGroup.size() - 1);
            Sequence prevSequence = curGroup.getAlternatives().get(curGroup.size() - 2);
            if (prevSequence.isSingleCharClass()) {
                CharacterClass prevCC = (CharacterClass) prevSequence.getFirstTerm();
                CharacterClass curCC = (CharacterClass) curSequence.getFirstTerm();
                prevCC.setMatcherBuilder(prevCC.getMatcherBuilder().union(curCC.getMatcherBuilder()));
                curSequence.removeLastTerm();
                if (options.isDumpAutomata()) {
                    // set source section to cover both char classes and the "|" in between
                    SourceSection prevCCSrc = prevCC.getSourceSection();
                    prevCC.setSourceSection(prevCCSrc.getSource().createSection(
                                    prevCCSrc.getCharIndex(), prevCCSrc.getCharLength() + curCC.getSourceSection().getCharLength() + 1));
                }
                return true;
            }
        }
        return false;
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
        LookAheadAssertion,
        LookBehindAssertion
    }

    /**
     * Information about the state of the {@link #curTerm} field. The field can be either null,
     * point to a lookahead assertion node, to a lookbehind assertion node or to some other non-null
     * node.
     */
    private enum CurTermState {
        Null,
        LookAheadAssertion,
        LookBehindAssertion,
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

}
