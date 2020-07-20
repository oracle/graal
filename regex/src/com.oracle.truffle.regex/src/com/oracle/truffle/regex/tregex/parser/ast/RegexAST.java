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
package com.oracle.truffle.regex.tregex.parser.ast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.ASTDebugDumpVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.CopyVisitor;
import com.oracle.truffle.regex.tregex.string.AbstractStringBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

public final class RegexAST implements StateIndex<RegexASTNode>, JsonConvertible {

    /**
     * Original pattern as seen by the parser.
     */
    private final RegexSource source;
    private final RegexFlags flags;
    private final RegexOptions options;
    private final Counter.ThresholdCounter nodeCount = new Counter.ThresholdCounter(TRegexOptions.TRegexParserTreeMaxSize, "parse tree explosion");
    private final Counter.ThresholdCounter groupCount = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNumberOfCaptureGroups, "too many capture groups");
    private final Counter quantifierCount = new Counter();
    private final Counter zeroWidthQuantifierCount = new Counter();
    private final RegexProperties properties = new RegexProperties();
    private RegexASTNode[] nodes;
    /**
     * AST as parsed from the expression.
     */
    private Group root;
    /**
     * Possibly wrapped root for NFA generation (see {@link #createPrefix()}).
     */
    private Group wrappedRoot;
    private Group[] captureGroups;
    private final LookAroundIndex lookArounds = new LookAroundIndex();
    private final List<PositionAssertion> reachableCarets = new ArrayList<>();
    private final List<PositionAssertion> reachableDollars = new ArrayList<>();
    private StateSet<RegexAST, PositionAssertion> nfaAnchoredInitialStates;
    private StateSet<RegexAST, RegexASTNode> hardPrefixNodes;
    private final EconomicMap<GroupBoundaries, GroupBoundaries> groupBoundariesDeduplicationMap = EconomicMap.create();

    private int negativeLookaheads = 0;
    private int negativeLookbehinds = 0;

    private final EconomicMap<RegexASTNode, List<SourceSection>> sourceSections;

    public RegexAST(RegexSource source, RegexFlags flags, RegexOptions options) {
        this.source = source;
        this.flags = flags;
        this.options = options;
        sourceSections = options.isDumpAutomata() ? EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE) : null;
    }

    public RegexSource getSource() {
        return source;
    }

    public RegexFlags getFlags() {
        return flags;
    }

    public RegexOptions getOptions() {
        return options;
    }

    public Encoding getEncoding() {
        return source.getEncoding();
    }

    public Group getRoot() {
        return root;
    }

    public void setRoot(Group root) {
        this.root = root;
    }

    public Group getWrappedRoot() {
        return wrappedRoot;
    }

    public boolean rootIsWrapped() {
        return wrappedRoot != null && root != wrappedRoot;
    }

    public Counter.ThresholdCounter getNodeCount() {
        return nodeCount;
    }

    public int getNumberOfNodes() {
        return nodeCount.getCount();
    }

    public Counter.ThresholdCounter getGroupCount() {
        return groupCount;
    }

    /**
     * @return the number of capturing groups in the AST, including group 0.
     */
    public int getNumberOfCaptureGroups() {
        return groupCount.getCount();
    }

    public Counter getQuantifierCount() {
        return quantifierCount;
    }

    public Counter getZeroWidthQuantifierCount() {
        return zeroWidthQuantifierCount;
    }

    public Group getGroupByBoundaryIndex(int index) {
        if (captureGroups == null) {
            captureGroups = new Group[getNumberOfCaptureGroups()];
            for (RegexASTNode n : nodes) {
                if (n instanceof Group && ((Group) n).isCapturing()) {
                    captureGroups[((Group) n).getGroupNumber()] = (Group) n;
                }
            }
        }
        return captureGroups[index / 2];
    }

    public RegexProperties getProperties() {
        return properties;
    }

    public boolean isLiteralString() {
        Group r = getRoot();
        RegexProperties p = getProperties();
        return !((p.hasBackReferences() || p.hasAlternations() || p.hasLookAroundAssertions() || r.hasLoops()) || ((r.startsWithCaret() || r.endsWithDollar()) && getFlags().isMultiline())) &&
                        (!p.hasCharClasses() || p.charClassesCanBeMatchedWithMask());
    }

    @Override
    public int getNumberOfStates() {
        return nodes.length;
    }

    @Override
    public int getId(RegexASTNode state) {
        return state.getId();
    }

    @Override
    public RegexASTNode getState(int id) {
        return nodes[id];
    }

    public void setIndex(RegexASTNode[] index) {
        this.nodes = index;
    }

    /**
     * @return length of prefix possibly generated by {@link #createPrefix()}.
     */
    public int getWrappedPrefixLength() {
        if (rootIsWrapped()) {
            // The single alternative in the wrappedRoot is composed of N non-optional prefix
            // matchers, 1 group of optional matchers and the original root. By
            // taking size() - 2, we get the number of non-optional prefix matchers.
            return wrappedRoot.getFirstAlternative().size() - 2;
        }
        return 0;
    }

    /**
     * @return first element of sequence of optional any-char matchers possibly generated by
     *         {@link #createPrefix()}.
     */
    public RegexASTNode getEntryAfterPrefix() {
        if (rootIsWrapped()) {
            return wrappedRoot.getFirstAlternative().getTerms().get(getWrappedPrefixLength());
        }
        return wrappedRoot;
    }

    public LookAroundIndex getLookArounds() {
        return lookArounds;
    }

    public List<PositionAssertion> getReachableCarets() {
        return reachableCarets;
    }

    public List<PositionAssertion> getReachableDollars() {
        return reachableDollars;
    }

    public StateSet<RegexAST, PositionAssertion> getNfaAnchoredInitialStates() {
        return nfaAnchoredInitialStates;
    }

    public StateSet<RegexAST, RegexASTNode> getHardPrefixNodes() {
        return hardPrefixNodes;
    }

    public RegexASTRootNode createRootNode() {
        final RegexASTRootNode node = new RegexASTRootNode();
        createNFAHelperNodes(node);
        return node;
    }

    public BackReference createBackReference(int groupNumber) {
        return register(new BackReference(groupNumber));
    }

    public CharacterClass createCharacterClass(CodePointSet matcherBuilder) {
        assert getEncoding().getFullSet().contains(matcherBuilder);
        return register(new CharacterClass(matcherBuilder));
    }

    public Group createGroup() {
        return register(new Group());
    }

    public Group createCaptureGroup(int groupNumber) {
        return register(new Group(groupNumber));
    }

    public LookAheadAssertion createLookAheadAssertion(boolean negated) {
        final LookAheadAssertion assertion = new LookAheadAssertion(negated);
        createNFAHelperNodes(assertion);
        return register(assertion);
    }

    public LookBehindAssertion createLookBehindAssertion(boolean negated) {
        final LookBehindAssertion assertion = new LookBehindAssertion(negated);
        createNFAHelperNodes(assertion);
        return register(assertion);
    }

    public void createNFAHelperNodes(RegexASTSubtreeRootNode rootNode) {
        nodeCount.inc(4);
        PositionAssertion anchored = new PositionAssertion(PositionAssertion.Type.CARET);
        rootNode.setAnchoredInitialState(anchored);
        MatchFound unAnchored = new MatchFound();
        rootNode.setUnAnchoredInitialState(unAnchored);
        MatchFound end = new MatchFound();
        rootNode.setMatchFound(end);
        PositionAssertion anchoredEnd = new PositionAssertion(PositionAssertion.Type.DOLLAR);
        rootNode.setAnchoredFinalState(anchoredEnd);
    }

    public PositionAssertion createPositionAssertion(PositionAssertion.Type type) {
        return register(new PositionAssertion(type));
    }

    public Sequence createSequence() {
        return register(new Sequence());
    }

    public BackReference register(BackReference backReference) {
        nodeCount.inc();
        properties.setBackReferences();
        return backReference;
    }

    public CharacterClass register(CharacterClass characterClass) {
        nodeCount.inc();
        updatePropsCC(characterClass);
        return characterClass;
    }

    public void updatePropsCC(CharacterClass characterClass) {
        if (!characterClass.getCharSet().matchesSingleChar()) {
            if (!characterClass.getCharSet().matches2CharsWith1BitDifference()) {
                properties.unsetCharClassesCanBeMatchedWithMask();
            }
            if (!getEncoding().isFixedCodePointWidth(characterClass.getCharSet())) {
                properties.setFixedCodePointWidth(false);
            }
            properties.setCharClasses();
        }
        if (Constants.SURROGATES.intersects(characterClass.getCharSet())) {
            properties.setLoneSurrogates();
        }
    }

    public Group register(Group group) {
        nodeCount.inc();
        if (group.isCapturing() && group.getGroupNumber() != 0) {
            properties.setCaptureGroups();
        }
        return group;
    }

    public LookAheadAssertion register(LookAheadAssertion lookAheadAssertion) {
        nodeCount.inc();
        properties.setLookAheadAssertions();
        if (lookAheadAssertion.isNegated()) {
            negativeLookaheads++;
            properties.setNegativeLookAheadAssertions();
        }
        return lookAheadAssertion;
    }

    public LookBehindAssertion register(LookBehindAssertion lookBehindAssertion) {
        nodeCount.inc();
        properties.setLookBehindAssertions();
        if (lookBehindAssertion.isNegated()) {
            negativeLookbehinds++;
            properties.setNegativeLookBehindAssertions();
        }
        return lookBehindAssertion;
    }

    public void invertNegativeLookAround(LookAroundAssertion assertion) {
        assert assertion.isNegated();
        assertion.setNegated(false);
        if (assertion.isLookAheadAssertion()) {
            assert negativeLookaheads > 0;
            if (--negativeLookaheads == 0) {
                properties.setNegativeLookAheadAssertions(false);
            }
        } else {
            assert negativeLookbehinds > 0;
            if (--negativeLookbehinds == 0) {
                properties.setNegativeLookBehindAssertions(false);
            }
        }
    }

    public PositionAssertion register(PositionAssertion positionAssertion) {
        nodeCount.inc();
        return positionAssertion;
    }

    public Sequence register(Sequence sequence) {
        nodeCount.inc();
        return sequence;
    }

    public boolean isNFAInitialState(RegexASTNode node) {
        return node.getId() >= 1 && node.getId() <= getWrappedPrefixLength() * 2 + 2;
    }

    private void createNFAInitialStates() {
        if (nfaAnchoredInitialStates != null) {
            return;
        }
        hardPrefixNodes = StateSet.create(this);
        nfaAnchoredInitialStates = StateSet.create(this);
        int nextID = 1;
        MatchFound mf = new MatchFound();
        initNodeId(mf, nextID++);
        mf.setNext(getEntryAfterPrefix());
        PositionAssertion pos = new PositionAssertion(PositionAssertion.Type.CARET);
        initNodeId(pos, nextID++);
        nfaAnchoredInitialStates.add(pos);
        pos.setNext(getEntryAfterPrefix());
        for (int i = getWrappedPrefixLength() - 1; i >= 0; i--) {
            RegexASTNode prefixNode = getWrappedRoot().getFirstAlternative().getTerms().get(i);
            hardPrefixNodes.add(prefixNode);
            mf = new MatchFound();
            initNodeId(mf, nextID++);
            mf.setNext(prefixNode);
            pos = new PositionAssertion(PositionAssertion.Type.CARET);
            initNodeId(pos, nextID++);
            nfaAnchoredInitialStates.add(pos);
            pos.setNext(prefixNode);
        }
    }

    public MatchFound getNFAUnAnchoredInitialState(int prefixOffset) {
        createNFAInitialStates();
        assert nodes[prefixOffset * 2 + 1] != null;
        return (MatchFound) nodes[prefixOffset * 2 + 1];
    }

    public PositionAssertion getNFAAnchoredInitialState(int prefixOffset) {
        createNFAInitialStates();
        assert nodes[prefixOffset * 2 + 2] != null;
        return (PositionAssertion) nodes[prefixOffset * 2 + 2];
    }

    /**
     * Inserts a prefix of matchers that match any characters at the beginning of the AST. The
     * length of the prefix is determined by the look-behind assertions present in the regex. Any
     * necessary context that could be matched by the look-behind assertions but not by the original
     * regex can be captured by the prefix. Exemplary prefix: {@code
     * regex: /(?<=ab)/
     *  -> prefix length: 2
     *  -> result: /(?:[_any_][_any_](?:|[_any_](?:|[_any_])))(?<=ab)/
     *      -> the non-optional [_any_] - matchers will be used if fromIndex > 0,
     *                                    the optional matchers will always be used
     * }
     */
    public void createPrefix() {
        if (root.startsWithCaret() || properties.hasNonLiteralLookBehindAssertions()) {
            wrappedRoot = root;
            return;
        }
        int prefixLength = 0;
        for (LookAroundAssertion lb : lookArounds) {
            if (lb instanceof LookAheadAssertion) {
                continue;
            }
            int minPath = lb.getMinPath();
            RegexASTSubtreeRootNode laParent = lb.getSubTreeParent();
            while (!(laParent instanceof RegexASTRootNode)) {
                if (laParent instanceof LookBehindAssertion) {
                    throw new UnsupportedRegexException("nested look-behind assertions");
                }
                minPath += laParent.getMinPath();
                laParent = laParent.getSubTreeParent();
            }
            prefixLength = Math.max(prefixLength, lb.getLiteralLength() - minPath);
        }
        if (prefixLength == 0) {
            wrappedRoot = root;
            return;
        }
        final Group wrapRoot = createGroup();
        wrapRoot.setPrefix();
        final Sequence wrapRootSeq = createSequence();
        wrapRoot.add(wrapRootSeq);
        wrapRootSeq.setPrefix();
        // create non-optional matchers ([_any_][_any_]...)
        for (int i = 0; i < prefixLength; i++) {
            wrapRootSeq.add(createPrefixAnyMatcher());
        }
        Group prevOpt = null;
        // create optional matchers ((?:|[_any_](?:|[_any_]))...)
        for (int i = 0; i < prefixLength; i++) {
            Group opt = createGroup();
            opt.setPrefix();
            opt.add(createSequence());
            opt.add(createSequence());
            opt.getFirstAlternative().setPrefix();
            opt.getAlternatives().get(1).setPrefix();
            opt.getAlternatives().get(1).add(createPrefixAnyMatcher());
            if (prevOpt != null) {
                opt.getAlternatives().get(1).add(prevOpt);
            }
            prevOpt = opt;
        }
        root.getSubTreeParent().setGroup(wrapRoot);
        wrapRootSeq.add(prevOpt);
        wrapRootSeq.add(root);
        wrappedRoot = wrapRoot;
    }

    public void hidePrefix() {
        if (wrappedRoot != root) {
            root.getSubTreeParent().setGroup(root);
        }
    }

    public void unhidePrefix() {
        if (wrappedRoot != root) {
            root.getSubTreeParent().setGroup(wrappedRoot);
        }
    }

    public GroupBoundaries createGroupBoundaries(CompilationFinalBitSet updateIndices, CompilationFinalBitSet clearIndices) {
        GroupBoundaries staticInstance = GroupBoundaries.getStaticInstance(updateIndices, clearIndices);
        if (staticInstance != null) {
            return staticInstance;
        }
        GroupBoundaries lookup = new GroupBoundaries(updateIndices, clearIndices);
        if (groupBoundariesDeduplicationMap.containsKey(lookup)) {
            return groupBoundariesDeduplicationMap.get(lookup);
        } else {
            GroupBoundaries gb = new GroupBoundaries(updateIndices.copy(), clearIndices.copy());
            groupBoundariesDeduplicationMap.put(gb, gb);
            return gb;
        }
    }

    /**
     * Creates a {@link CharacterClass} node which matches any character and whose 'prefix' flag is
     * set to true.
     */
    private CharacterClass createPrefixAnyMatcher() {
        final CharacterClass anyMatcher = createCharacterClass(getEncoding().getFullSet());
        anyMatcher.setPrefix();
        return anyMatcher;
    }

    private void addToIndex(RegexASTNode node) {
        assert node.getId() >= 0;
        assert node.getId() < nodes.length;
        assert nodes[node.getId()] == null;
        nodes[node.getId()] = node;
    }

    private void initNodeId(RegexASTNode node, int id) {
        node.setId(id);
        addToIndex(node);
    }

    /**
     * Get a list of all source sections associated with the given {@link RegexASTNode}. The parser
     * will map nodes to source sections in the following way:
     * <ul>
     * <li>{@link Group}: sections of the respective opening and closing brackets, in that order.
     * For example, the source sections of a look-ahead assertion will be {@code ["(?=", ")"]}.
     * Groups generated by the parser, e.g. {@code (?:a|)} generated for {@code a?}, don't have
     * source sections.</li>
     * <li>{@link CharacterClass}: normally these nodes correspond to a single
     * {@link com.oracle.truffle.regex.tregex.parser.Token.CharacterClass Token.CharacterClass}, but
     * the parser may optimize redundant nodes away and add their source sections to existing nodes.
     * Example: {@code a|b} will be optimized to {@code [ab]}, which will be mapped to both original
     * characters.</li>
     * <li>{@link Sequence}, {@link MatchFound}, {@link RegexASTSubtreeRootNode}: no mapping.</li>
     * <li>{@link PositionAssertion}, {@link BackReference}: mapped to their respective
     * {@link Token}s.</li>
     * <li>Nodes generated by {@link CopyVisitor} are mapped to the same source sections as their
     * counterparts.</li>
     * <li>Nodes inserted as substitutions for e.g. {@code \b} will simply point to the source
     * section they are substituting.</li>
     * <li>Source sections of {@link com.oracle.truffle.regex.tregex.parser.Token.Quantifier
     * quantifiers} are mapped to their respective {@link Term}.</li>
     * </ul>
     */
    public List<SourceSection> getSourceSections(RegexASTNode node) {
        return options.isDumpAutomata() ? sourceSections.get(node) : null;
    }

    public void addSourceSection(RegexASTNode node, Token token) {
        if (options.isDumpAutomata() && token != null && token.getSourceSection() != null) {
            getOrCreateSourceSections(node).add(token.getSourceSection());
        }
    }

    public void addSourceSections(RegexASTNode node, Collection<SourceSection> src) {
        if (options.isDumpAutomata() && src != null) {
            getOrCreateSourceSections(node).addAll(src);
        }
    }

    private List<SourceSection> getOrCreateSourceSections(RegexASTNode node) {
        List<SourceSection> sections = sourceSections.get(node);
        if (sections == null) {
            sections = new ArrayList<>();
            sourceSections.put(node, sections);
        }
        return sections;
    }

    public InnerLiteral extractInnerLiteral() {
        assert properties.hasInnerLiteral();
        int literalEnd = properties.getInnerLiteralEnd();
        int literalStart = properties.getInnerLiteralStart();
        AbstractStringBuffer literal = getEncoding().createStringBuffer(literalEnd - literalStart);
        AbstractStringBuffer mask = getEncoding().createStringBuffer(literalEnd - literalStart);
        boolean hasMask = false;
        for (int i = literalStart; i < literalEnd; i++) {
            CharacterClass cc = root.getFirstAlternative().getTerms().get(i).asCharacterClass();
            assert cc.getCharSet().matchesSingleChar() || cc.getCharSet().matches2CharsWith1BitDifference();
            assert getEncoding().isFixedCodePointWidth(cc.getCharSet());
            cc.extractSingleChar(literal, mask);
            hasMask |= cc.getCharSet().matches2CharsWith1BitDifference();
        }
        return new InnerLiteral(literal.materialize(), hasMask ? mask.materialize() : null, root.getFirstAlternative().get(literalStart).getMaxPath() - 1);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("source", source),
                        Json.prop("root", root),
                        Json.prop("debugAST", ASTDebugDumpVisitor.getDump(wrappedRoot)),
                        Json.prop("wrappedRoot", wrappedRoot),
                        Json.prop("reachableCarets", reachableCarets),
                        Json.prop("startsWithCaret", root.startsWithCaret()),
                        Json.prop("endsWithDollar", root.endsWithDollar()),
                        Json.prop("reachableDollars", reachableDollars),
                        Json.prop("properties", properties));
    }

    @TruffleBoundary
    public static JsonArray sourceSectionsToJson(List<SourceSection> sourceSections) {
        if (sourceSections == null) {
            return Json.array();
        }
        return sourceSectionsToJson(sourceSections.stream());
    }

    @TruffleBoundary
    public static JsonArray sourceSectionsToJson(Stream<SourceSection> sourceSections) {
        if (sourceSections == null) {
            return Json.array();
        }
        return Json.array(sourceSections.map(x -> Json.obj(
                        Json.prop("start", x.getCharIndex()),
                        Json.prop("end", x.getCharEndIndex()))));
    }
}
