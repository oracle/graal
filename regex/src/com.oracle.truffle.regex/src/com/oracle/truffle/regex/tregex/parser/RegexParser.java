/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
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
        this.setSourceSectionVisitor = options.isDumpAutomata() ? new SetSourceSectionVisitor(ast) : null;
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
        return ast;
    }

    public void prepareForDFA() {
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
    }

    private void checkInnerLiteral() {
        if (ast.isLiteralString() || ast.getRoot().startsWithCaret() || ast.getRoot().endsWithDollar() || ast.getRoot().size() != 1) {
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
        ast.addSourceSection(group, token);
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
        if (!curGroup.isEmpty()) {
            setComplexLookAround();
        }
        curSequence = curGroup.addSequence(ast);
        curTerm = null;
    }

    private void popGroup(Token token) throws RegexSyntaxException {
        curGroup.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        ast.addSourceSection(curGroup, token);
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
        if (Constants.BMP_WITHOUT_SURROGATES.contains(token.getCodePointSet())) {
            return createCharClass(codePointSet, token, token.wasSingleChar());
        }
        Group group = ast.createGroup();
        group.setEnclosedCaptureGroupsLow(groupCount.getCount());
        group.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        IntRangesBuffer tmp = compilationBuffer.getIntRangesBuffer1();
        CodePointSet bmpRanges = codePointSet.createIntersection(Constants.BMP_WITHOUT_SURROGATES, tmp);
        CodePointSet astralRanges = codePointSet.createIntersection(Constants.ASTRAL_SYMBOLS, tmp);
        CodePointSet loneLeadSurrogateRanges = codePointSet.createIntersection(Constants.LEAD_SURROGATES, tmp);
        CodePointSet loneTrailSurrogateRanges = codePointSet.createIntersection(Constants.TRAIL_SURROGATES, tmp);

        assert astralRanges.matchesSomething() || loneLeadSurrogateRanges.matchesSomething() || loneTrailSurrogateRanges.matchesSomething();

        if (bmpRanges.matchesSomething()) {
            Sequence bmpAlternative = group.addSequence(ast);
            bmpAlternative.add(createCharClass(bmpRanges, token));
        }

        if (loneLeadSurrogateRanges.matchesSomething()) {
            Sequence loneLeadSurrogateAlternative = group.addSequence(ast);
            loneLeadSurrogateAlternative.add(createCharClass(loneLeadSurrogateRanges, token));
            loneLeadSurrogateAlternative.add(NO_TRAIL_SURROGATE_AHEAD.copy(ast, true));
            properties.setAlternations();
        }

        if (loneTrailSurrogateRanges.matchesSomething()) {
            Sequence loneTrailSurrogateAlternative = group.addSequence(ast);
            loneTrailSurrogateAlternative.add(NO_LEAD_SURROGATE_BEHIND.copy(ast, true));
            loneTrailSurrogateAlternative.add(createCharClass(loneTrailSurrogateRanges, token));
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
                        finishedAlternative.add(createCharClass(CharSet.create(curLead), token));
                        finishedAlternative.add(createCharClass(curTrails, token));
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
                        finishedAlternative.add(createCharClass(CharSet.create(curLead), token));
                        finishedAlternative.add(createCharClass(curTrails, token));
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
                lastAlternative.add(createCharClass(CharSet.create(curLead), token));
                lastAlternative.add(createCharClass(curTrails, token));
            }

            if (completeRanges.matchesSomething()) {
                // Complete ranges match more often and so we want them as an early alternative
                Sequence completeRangesAlt = ast.createSequence();
                group.insertFirst(completeRangesAlt);
                completeRangesAlt.add(createCharClass(completeRanges, token));
                completeRangesAlt.add(createCharClass(CharSet.getTrailSurrogateRange(), token));
            }
        }

        if (group.size() > 1) {
            properties.setAlternations();
        }
        assert !(group.size() == 1 && group.getAlternatives().get(0).getTerms().size() == 1);
        return group;
    }

    private void addCharClass(Token.CharacterClass token) {
        CodePointSet codePointSet = token.getCodePointSet();
        if (flags.isUnicode()) {
            if (codePointSet.matchesNothing()) {
                // We need this branch because a Group with no alternatives is invalid
                addTerm(createCharClass(CharSet.getEmpty(), token));
            } else {
                addTerm(translateUnicodeCharClass(token));
            }
        } else {
            addTerm(createCharClass(codePointSet, token, token.wasSingleChar()));
        }
    }

    private CharacterClass createCharClass(IntRangesBuffer buf, Token token) {
        return createCharClass(CharSet.fromSortedRanges(buf), token);
    }

    private CharacterClass createCharClass(CodePointSet codePointSet, Token token) {
        return createCharClass(CharSet.fromSortedRanges(codePointSet), token);
    }

    private CharacterClass createCharClass(CodePointSet codePointSet, Token token, boolean wasSingleChar) {
        return createCharClass(CharSet.fromSortedRanges(codePointSet), token, wasSingleChar);
    }

    private CharacterClass createCharClass(CharSet charSet, Token token) {
        return createCharClass(charSet, token, false);
    }

    private CharacterClass createCharClass(CharSet charSet, Token token, boolean wasSingleChar) {
        CharacterClass characterClass = ast.createCharacterClass(charSet);
        ast.addSourceSection(characterClass, token);
        if (wasSingleChar) {
            characterClass.setWasSingleChar();
        }
        return characterClass;
    }

    private void createOptionalBranch(Term term, boolean greedy, boolean copy, int recurse) throws RegexSyntaxException {
        addTerm(copy ? copyVisitor.copy(term) : term);
        if (curTerm instanceof Group) {
            // When translating a quantified expression that allows zero occurrences into a
            // disjunction of the form (curTerm|), we must make sure that curTerm cannot match the
            // empty string, as is specified in step 2a of RepeatMatcher from ECMAScript draft 2018,
            // chapter 21.2.2.5.1.
            curTerm.setEmptyGuard(true);
        }
        createOptional(term, greedy, true, recurse - 1);
    }

    private void createOptional(Term term, boolean greedy, boolean copy, int recurse) throws RegexSyntaxException {
        if (recurse < 0) {
            return;
        }
        properties.setAlternations();
        createGroup(null);
        curGroup.setExpandedQuantifier(true);
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

    private void expandQuantifier(Term toExpand) {
        assert toExpand.hasQuantifier();
        Token.Quantifier quantifier = toExpand.getQuantifier();
        assert quantifier.getMin() <= TRegexOptions.TRegexMaxCountedRepetition && quantifier.getMax() <= TRegexOptions.TRegexMaxCountedRepetition : toExpand + " in " + source;
        toExpand.setQuantifier(null);
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
        createOptional(t, quantifier.isGreedy(), quantifier.getMin() > 0, quantifier.isInfiniteLoop() ? 0 : (quantifier.getMax() - quantifier.getMin()) - 1);
        if (quantifier.isInfiniteLoop()) {
            ((Group) curTerm).setLoop(true);
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
            setSourceSectionVisitor.run(copy, token);
        }
        addTerm(copy);
    }

    /* parser */

    private Group parse(boolean rootCapture) throws RegexSyntaxException {
        RegexASTRootNode rootParent = ast.createRootNode();
        Group root = createGroup(null, false, rootCapture, rootParent);
        if (options.isDumpAutomata()) {
            // set leading and trailing '/' as source sections of root
            ast.addSourceSections(root, Arrays.asList(ast.getSource().getSource().createSection(0, 1), ast.getSource().getSource().createSection(ast.getSource().getPattern().length() + 1, 1)));
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
                        ast.addSourceSection(caret, token);
                        addTerm(caret);
                    }
                    break;
                case dollar:
                    if (flags.isMultiline()) {
                        substitute(token, MULTI_LINE_DOLLAR_SUBSTITUTION);
                        properties.setAlternations();
                    } else if (!curTermIsAnchor(PositionAssertion.Type.DOLLAR)) {
                        PositionAssertion dollar = ast.createPositionAssertion(PositionAssertion.Type.DOLLAR);
                        ast.addSourceSection(dollar, token);
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
                    ast.addSourceSection(backReference, token);
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
                    optimizeGroup();
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
        optimizeGroup();
        root.setEnclosedCaptureGroupsHigh(groupCount.getCount());
        return root;
    }

    private void optimizeGroup() {
        sortAlternatives(curGroup);
        mergeCommonPrefixes(curGroup);
        singleCharNegativeLookAroundToPositive(curGroup);
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
            replaceCurTermWithDeadNode();
            return;
        }
        if (quantifier.getMax() == 0 ||
                        quantifier.getMin() == 0 && (curTerm instanceof LookAroundAssertion || curTerm instanceof CharacterClass && ((CharacterClass) curTerm).getCharSet().matchesNothing())) {
            removeCurTerm();
            return;
        }
        ast.addSourceSection(curTerm, quantifier);
        if (curTerm instanceof LookAroundAssertion) {
            // quantifying LookAroundAssertions doesn't do anything if quantifier.getMin() > 0, so
            // ignore.
            return;
        }
        if (quantifier.getMin() == 1 && quantifier.getMax() == 1) {
            // x{1,1} -> x
            return;
        }
        setQuantifier(curTerm, quantifier);
        // merge equal successive quantified terms
        if (curSequence.size() > 1) {
            Term prev = curSequence.getTerms().get(curSequence.size() - 2);
            if (prev.hasQuantifier() && curTerm.equalsSemantic(prev, true)) {
                removeCurTerm();
                long min = (long) prev.getQuantifier().getMin() + quantifier.getMin();
                long max = prev.getQuantifier().isInfiniteLoop() || quantifier.isInfiniteLoop() ? -1 : (long) prev.getQuantifier().getMax() + quantifier.getMax();
                if (min > Integer.MAX_VALUE) {
                    replaceCurTermWithDeadNode();
                    return;
                }
                if (max > Integer.MAX_VALUE) {
                    max = -1;
                }
                setQuantifier(prev, new Token.Quantifier((int) min, (int) max, prev.getQuantifier().isGreedy() || quantifier.isGreedy()));
            }
        }
    }

    private void removeCurTerm() {
        deleteVisitor.run(curSequence.getLastTerm());
        curSequence.removeLastTerm();
        if (!curSequence.isEmpty()) {
            curTerm = curSequence.getLastTerm();
        }
    }

    private void replaceCurTermWithDeadNode() {
        removeCurTerm();
        addTerm(createCharClass(CharSet.getEmpty(), null));
    }

    private void setQuantifier(Term term, Token.Quantifier quantifier) {
        if (quantifier.getMin() > TRegexOptions.TRegexMaxCountedRepetition || quantifier.getMax() > TRegexOptions.TRegexMaxCountedRepetition) {
            properties.setLargeCountedRepetitions();
        }
        term.setQuantifier(quantifier);
        properties.setQuantifiers();
        if (quantifier.getMin() != quantifier.getMax()) {
            properties.setAlternations();
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
                mergeCharClasses((CharacterClass) prevSequence.getFirstTerm(), (CharacterClass) curSequence.getFirstTerm());
                curSequence.removeLastTerm();
                ast.getNodeCount().dec();
                return true;
            }
        }
        return false;
    }

    /**
     * Merge {@code src} into {@code dst}.
     */
    private void mergeCharClasses(CharacterClass dst, CharacterClass src) {
        dst.setCharSet(dst.getCharSet().union(src.getCharSet()));
        dst.setWasSingleChar(false);
        ast.addSourceSections(dst, ast.getSourceSections(src));
    }

    /**
     * Stable-sort consecutive alternations that start with single characters, to enable more
     * simplifications with {@link #mergeCommonPrefixes(Group)}. This also works in ignore-case
     * mode, since we track character classes that were generated from single characters via
     * {@link CharacterClass#wasSingleChar()}.
     */
    private static void sortAlternatives(Group group) {
        if (group.size() < 2) {
            return;
        }
        int begin = 0;
        while (begin + 1 < group.size()) {
            int end = findSingleCharAlternatives(group, begin);
            if (end > begin + 1) {
                group.getAlternatives().subList(begin, end).sort((Sequence a, Sequence b) -> {
                    return ((CharacterClass) a.getFirstTerm()).getCharSet().getLo(0) - ((CharacterClass) b.getFirstTerm()).getCharSet().getLo(0);
                });
                begin = end;
            } else {
                begin++;
            }
        }
    }

    /**
     * Simplify redundant alternation prefixes, e.g. {@code /ab|ac/ -> /a(?:b|c)/}. This method
     * should be called when {@code curGroup} is about to be closed.
     */
    private void mergeCommonPrefixes(Group group) {
        if (group.size() < 2) {
            return;
        }
        ArrayList<Sequence> newAlternatives = null;
        int lastEnd = 0;
        int begin = 0;
        while (begin + 1 < group.size()) {
            int end = findMatchingAlternatives(group, begin);
            if (end < 0) {
                begin++;
            } else {
                if (newAlternatives == null) {
                    newAlternatives = new ArrayList<>();
                }
                for (int i = lastEnd; i < begin; i++) {
                    newAlternatives.add(group.getAlternatives().get(i));
                }
                lastEnd = end;
                int prefixSize = 1;
                while (alternativesAreEqualAt(group, begin, end, prefixSize)) {
                    prefixSize++;
                }
                Sequence prefixSeq = ast.createSequence();
                Group innerGroup = ast.createGroup();
                int enclosedCGLo = Integer.MAX_VALUE;
                int enclosedCGHi = Integer.MIN_VALUE;
                boolean emptyAlt = false;
                for (int i = begin; i < end; i++) {
                    Sequence s = group.getAlternatives().get(i);
                    assert s.size() >= prefixSize;
                    for (int j = 0; j < prefixSize; j++) {
                        Term t = s.getTerms().get(j);
                        if (i == begin) {
                            prefixSeq.add(t);
                        } else {
                            ast.addSourceSections(prefixSeq.getTerms().get(j), ast.getSourceSections(t));
                            deleteVisitor.run(t);
                        }
                    }
                    // merge successive single-character-class alternatives
                    if (i > begin && s.size() - prefixSize == 1 &&
                                    s.getLastTerm() instanceof CharacterClass && !s.getLastTerm().hasQuantifier() &&
                                    innerGroup.getLastAlternative().isSingleCharClass()) {
                        mergeCharClasses((CharacterClass) innerGroup.getLastAlternative().getFirstTerm(), (CharacterClass) s.getLastTerm());
                    } else {
                        // avoid creation of multiple empty alternatives in one group
                        if (prefixSize == s.size()) {
                            if (!emptyAlt) {
                                innerGroup.addSequence(ast);
                            }
                            emptyAlt = true;
                        } else {
                            Sequence copy = innerGroup.addSequence(ast);
                            for (int j = prefixSize; j < s.size(); j++) {
                                Term t = s.getTerms().get(j);
                                copy.add(t);
                                if (t instanceof Group) {
                                    Group g = (Group) t;
                                    if (g.getEnclosedCaptureGroupsLow() != g.getEnclosedCaptureGroupsHigh()) {
                                        enclosedCGLo = Math.min(enclosedCGLo, g.getEnclosedCaptureGroupsLow());
                                        enclosedCGHi = Math.max(enclosedCGHi, g.getEnclosedCaptureGroupsHigh());
                                    }
                                    if (g.isCapturing()) {
                                        enclosedCGLo = Math.min(enclosedCGLo, g.getGroupNumber());
                                        enclosedCGHi = Math.max(enclosedCGHi, g.getGroupNumber() + 1);
                                    }
                                }
                            }
                        }
                    }
                }
                if (enclosedCGLo != Integer.MAX_VALUE) {
                    innerGroup.setEnclosedCaptureGroupsLow(enclosedCGLo);
                    innerGroup.setEnclosedCaptureGroupsHigh(enclosedCGHi);
                }
                if (!innerGroup.isEmpty() && !(innerGroup.size() == 1 && innerGroup.getAlternatives().get(0).isEmpty())) {
                    mergeCommonPrefixes(innerGroup);
                    prefixSeq.add(innerGroup);
                }
                newAlternatives.add(prefixSeq);
                begin = end;
            }
        }
        if (newAlternatives != null) {
            for (int i = lastEnd; i < group.size(); i++) {
                newAlternatives.add(group.getAlternatives().get(i));
            }
            group.setAlternatives(newAlternatives);
        }
    }

    /**
     * Returns {@code true} iff the term at index {@code iTerm} of all alternatives in {@code group}
     * from index {@code altBegin} (inclusive) to {@code altEnd} (exclusive) are semantically equal.
     */
    private static boolean alternativesAreEqualAt(Group group, int altBegin, int altEnd, int iTerm) {
        if (group.getAlternatives().get(altBegin).size() <= iTerm) {
            return false;
        }
        Term cmp = group.getAlternatives().get(altBegin).getTerms().get(iTerm);
        for (int i = altBegin + 1; i < altEnd; i++) {
            Sequence s = group.getAlternatives().get(i);
            if (s.size() <= iTerm) {
                return false;
            }
            if (!s.getTerms().get(iTerm).equalsSemantic(cmp)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns an index {@code end} where the following condition holds: The first term of all
     * alternatives in {@code group} from alternative index {@code begin} (inclusive) to {@code end}
     * (exclusive) is semantically equivalent. If no such index exists, returns {@code -1}.
     */
    private static int findMatchingAlternatives(Group group, int begin) {
        if (group.getAlternatives().get(begin).isEmpty()) {
            return -1;
        }
        Term cmp = group.getAlternatives().get(begin).getFirstTerm();
        int ret = -1;
        for (int i = begin + 1; i < group.size(); i++) {
            Sequence s = group.getAlternatives().get(i);
            if (!s.isEmpty() && cmp.equalsSemantic(s.getFirstTerm())) {
                ret = i + 1;
            } else {
                return ret;
            }
        }
        return ret;
    }

    /**
     * Returns an index {@code end} where the following condition holds: The first term of all
     * alternatives in {@code group} from alternative index {@code begin} (inclusive) to {@code end}
     * (exclusive) is a character class generated from a single character.
     */
    private static int findSingleCharAlternatives(Group group, int begin) {
        int ret = -1;
        for (int i = begin; i < group.size(); i++) {
            Sequence s = group.getAlternatives().get(i);
            if (s.isEmpty() || !(s.getFirstTerm() instanceof CharacterClass) || !((CharacterClass) s.getFirstTerm()).wasSingleChar()) {
                return ret;
            }
            ret = i + 1;
        }
        return ret;
    }

    /**
     * Convert single-character-class negative lookarounds to positive ones by the following
     * expansion: {@code (?!x) -> (?=[^x]|$)}. This simplifies things for the DFA generator.
     */
    private void singleCharNegativeLookAroundToPositive(Group group) {
        if (group.getParent() instanceof LookAroundAssertion && ((LookAroundAssertion) group.getParent()).isNegated() && group.size() == 1 && group.getAlternatives().get(0).isSingleCharClass()) {
            ast.invertNegativeLookAround((LookAroundAssertion) group.getParent());
            CharacterClass cc = (CharacterClass) group.getAlternatives().get(0).getFirstTerm();
            // we don't have to expand the inverse in unicode mode here, because the character set
            // is guaranteed to be in BMP range, and its inverse will match all surrogates
            cc.setCharSet(cc.getCharSet().createInverse());
            Sequence empty = ast.createSequence();
            if (group.getParent() instanceof LookAheadAssertion) {
                empty.add(ast.createPositionAssertion(PositionAssertion.Type.DOLLAR));
                properties.setComplexLookAheadAssertions();
            } else {
                empty.add(ast.createPositionAssertion(PositionAssertion.Type.CARET));
                properties.setComplexLookBehindAssertions();
            }
            group.add(empty);
        }
    }

    private RegexSyntaxException syntaxError(String msg) {
        return new RegexSyntaxException(source, msg);
    }
}
