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
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.charset.SortedListOfRanges;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
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
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
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

    private final CompilationBuffer compilationBuffer;

    @TruffleBoundary
    public RegexParser(RegexSource source, RegexOptions options, CompilationBuffer compilationBuffer) throws RegexSyntaxException {
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
        this.compilationBuffer = compilationBuffer;
    }

    private static Group parseRootLess(String pattern) throws RegexSyntaxException {
        return new RegexParser(new RegexSource(pattern), RegexOptions.DEFAULT, new CompilationBuffer()).parse(false);
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
        if (properties.hasQuantifiers() && !properties.hasLargeCountedRepetitions()) {
            UnrollQuantifiersVisitor.unrollQuantifiers(this, ast.getRoot());
        }
        final CalcMinPathsVisitor calcMinPathsVisitor = new CalcMinPathsVisitor();
        calcMinPathsVisitor.runReverse(ast.getRoot());
        calcMinPathsVisitor.run(ast.getRoot());
        ast.removeUnreachablePositionAssertions();
        if (!properties.hasNonLiteralLookBehindAssertions()) {
            ast.createPrefix();
            InitIDVisitor.init(ast);
            if (!properties.hasBackReferences() && !properties.hasLargeCountedRepetitions()) {
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
            if (t instanceof CharacterClass && (((CharacterClass) t).getCharSet().matchesSingleChar() || ((CharacterClass) t).getCharSet().matches2CharsWith1BitDifference())) {
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
            properties.setInnerLiteral(literalStart, literalEnd);
        }
    }

    public RegexFlags getFlags() {
        return flags;
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

    private Term translateUnicodeCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        SourceSection src = token.getSourceSection();
        Group group = ast.createGroup();
        group.setEnclosedCaptureGroupsLow(groupCount.getCount());
        group.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        IntRangesBuffer tmp = compilationBuffer.getIntRangesBuffer1();
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
            IntRangesBuffer completeRanges = compilationBuffer.getIntRangesBuffer2();
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

    private void createOptionalBranch(Term term, boolean greedy, boolean copy, int recurse) throws RegexSyntaxException {
        addTerm(copy ? copyVisitor.copy(term) : term);
        // When translating a quantified expression that allows zero occurrences into a
        // disjunction of the form (curTerm|), we must make sure that curTerm cannot match the
        // empty string, as is specified in step 2a of RepeatMatcher from ECMAScript draft 2018,
        // chapter 21.2.2.5.1.
        curTerm.setEmptyGuard(true);
        if (curTerm instanceof Group) {
            ((Group) curTerm).setExpandedQuantifier(true);
        }
        createOptional(term, greedy, true, recurse - 1);
    }

    private void createOptional(Term term, boolean greedy, boolean copy, int recurse) throws RegexSyntaxException {
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
            createOptionalBranch(term, greedy, copy, recurse);
            addSequence(null);
        } else {
            addSequence(null);
            createOptionalBranch(term, greedy, copy, recurse);
        }
        popGroup(null);
    }

    private void setLoop() {
        assert curTerm instanceof Group;
        ((Group) curTerm).setLoop(true);
    }

    private void expandQuantifier(Term toExpand) {
        assert toExpand.hasQuantifier();
        Token.Quantifier quantifier = toExpand.getQuantifier();
        toExpand.setQuantifier(null);
        assert quantifier.getMin() <= TRegexOptions.TRegexMaxCountedRepetition && quantifier.getMax() <= TRegexOptions.TRegexMaxCountedRepetition;
        curTerm = toExpand;
        curSequence = (Sequence) curTerm.getParent();
        curGroup = curSequence.getParent();

        ObjectArrayBuffer buf = compilationBuffer.getObjectBuffer1();
        int size = curSequence.size();
        for (int i = curTerm.getSeqIndex() + 1; i < size; i++) {
            buf.add(curSequence.getLastTerm());
            curSequence.removeLastTerm();
        }

        if (quantifier.getMin() == 0) {
            curSequence.removeLastTerm();
        }
        Term t = curTerm;
        for (int i = quantifier.getMin(); i > 1; i--) {
            addTerm(copyVisitor.copy(t));
            if (curTerm instanceof Group) {
                ((Group) curTerm).setExpandedQuantifier(true);
            }
        }
        if (quantifier.isInfiniteLoop()) {
            createOptional(t, quantifier.isGreedy(), quantifier.getMin() > 0, 0);
            setLoop();
        } else {
            createOptional(t, quantifier.isGreedy(), quantifier.getMin() > 0, (quantifier.getMax() - quantifier.getMin()) - 1);
        }
        for (int i = buf.length() - 1; i >= 0; i--) {
            curSequence.add((Term) buf.get(i));
        }
    }

    private static final class UnrollQuantifiersVisitor extends DepthFirstTraversalRegexASTVisitor {

        private final RegexParser parser;

        private UnrollQuantifiersVisitor(RegexParser parser) {
            this.parser = parser;
        }

        public static void unrollQuantifiers(RegexParser parser, RegexASTNode runRoot) {
            new UnrollQuantifiersVisitor(parser).run(runRoot);
        }

        @Override
        protected void visit(CharacterClass characterClass) {
            expand(characterClass);
        }

        @Override
        protected void leave(Group group) {
            expand(group);
        }

        private void expand(Term t) {
            if (t.hasQuantifier()) {
                parser.expandQuantifier(t);
            }
        }
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
                        ast.getNodeCount().dec();
                    }
                    tryMergeCommonPrefixes();
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
            return;
        }
        if (quantifier.getMax() == 0 || (curTerm instanceof LookAroundAssertion && quantifier.getMin() == 0)) {
            deleteVisitor.run(curSequence.getLastTerm());
            curSequence.removeLastTerm();
            return;
        }
        if (curTerm instanceof LookAroundAssertion) {
            // quantifying LookAroundAssertions doesn't do anything if quantifier.getMin() > 0, so
            // ignore.
            return;
        }
        if (quantifier.getMin() > TRegexOptions.TRegexMaxCountedRepetition || quantifier.getMax() > TRegexOptions.TRegexMaxCountedRepetition) {
            properties.setLargeCountedRepetitions();
        }
        curTerm.setQuantifier(quantifier);
        properties.setQuantifiers();
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
                prevCC.setCharSet(prevCC.getCharSet().union(curCC.getCharSet()));
                curSequence.removeLastTerm();
                ast.getNodeCount().dec();
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

    /**
     * Simplify redundant alternation prefixes, e.g. {@code /ab|ac/ -> /a(?:b|c)/}. This method
     * should be called when {@code curGroup} is about to be closed.
     */
    private void tryMergeCommonPrefixes() {
        if (curGroup.size() < 2) {
            return;
        }
        int prefixSize = 0;
        while (groupHasEqualCharClassesAt(prefixSize)) {
            prefixSize++;
        }
        if (prefixSize > 0) {
            Sequence prefixSeq = ast.createSequence();
            for (int i = 0; i < prefixSize; i++) {
                prefixSeq.add(curGroup.getAlternatives().get(0).getTerms().get(i));
            }
            Group innerGroup = ast.createGroup();
            innerGroup.setEnclosedCaptureGroupsLow(curGroup.getEnclosedCaptureGroupsLow());
            innerGroup.setEnclosedCaptureGroupsHigh(curGroup.getEnclosedCaptureGroupsHigh());
            prefixSeq.add(innerGroup);
            for (Sequence s : curGroup.getAlternatives()) {
                assert s.size() >= prefixSize;
                Sequence copy = innerGroup.addSequence(ast);
                for (int i = prefixSize; i < s.size(); i++) {
                    copy.add(s.getTerms().get(i));
                }
            }
            curGroup.getAlternatives().clear();
            curGroup.add(prefixSeq);
        }
    }

    private boolean groupHasEqualCharClassesAt(int i) {
        CharacterClass cmp = null;
        for (Sequence s : curGroup.getAlternatives()) {
            if (s.size() <= i || s.getTerms().get(i).hasQuantifier() || !(s.getTerms().get(i) instanceof CharacterClass)) {
                return false;
            }
            CharacterClass cc = (CharacterClass) s.getTerms().get(i);
            if (cmp == null) {
                cmp = cc;
            } else {
                if (!cc.getCharSet().equals(cmp.getCharSet())) {
                    return false;
                }
            }
        }
        return true;
    }

    private RegexSyntaxException syntaxError(String msg) {
        return new RegexSyntaxException(source, msg);
    }
}
