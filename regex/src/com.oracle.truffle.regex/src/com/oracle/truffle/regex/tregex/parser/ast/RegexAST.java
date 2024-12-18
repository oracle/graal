/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.StringJoiner;
import java.util.stream.Stream;

import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.AbstractRegexObject;
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
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
import com.oracle.truffle.regex.util.TBitSet;

public final class RegexAST implements StateIndex<RegexASTNode>, JsonConvertible {

    /**
     * Original pattern as seen by the parser.
     */
    private final RegexLanguage language;
    private final RegexSource source;
    private RegexFlags flags;
    private AbstractRegexObject flavorSpecificFlags;
    private final Counter.ThresholdCounter nodeCount = new Counter.ThresholdCounter(TRegexOptions.TRegexParserTreeMaxSize, "parse tree explosion");
    private final Counter.ThresholdCounter groupCount = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxNumberOfCaptureGroups, "too many capture groups");
    private final RegexProperties properties = new RegexProperties();
    private final TBitSet referencedGroups = new TBitSet(Long.SIZE);
    private final TBitSet recursivelyReferencedGroups = new TBitSet(Long.SIZE);
    private final TBitSet conditionGroups = new TBitSet(Long.SIZE);
    private RegexASTNode[] nodes;
    /**
     * AST as parsed from the expression.
     */
    private Group root;
    /**
     * Possibly wrapped root for NFA generation (see {@link #createPrefix()}).
     */
    private Group wrappedRoot;
    private final ArrayList<ArrayList<Group>> captureGroups = new ArrayList<>();
    private final List<Token.Quantifier> quantifiers = new ArrayList<>();
    private final List<QuantifiableTerm> zeroWidthQuantifiables = new ArrayList<>();
    private final GlobalSubTreeIndex subtrees = new GlobalSubTreeIndex();
    private final GroupsWithGuardsIndex groupsWithGuards = new GroupsWithGuardsIndex();
    private final List<PositionAssertion> reachableCarets = new ArrayList<>();
    private final List<PositionAssertion> reachableDollars = new ArrayList<>();
    private StateSet<RegexAST, PositionAssertion> nfaAnchoredInitialStates;
    private StateSet<RegexAST, RegexASTNode> hardPrefixNodes;
    private final EconomicMap<GroupBoundaries, GroupBoundaries> groupBoundariesDeduplicationMap = EconomicMap.create();

    private final EconomicMap<RegexASTNode, List<SourceSection>> sourceSections;

    public RegexAST(RegexLanguage language, RegexSource source, RegexFlags flags) {
        this.language = language;
        this.source = source;
        this.flags = flags;
        this.sourceSections = source.getOptions().isDumpAutomataWithSourceSections() ? EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE) : null;
    }

    public RegexLanguage getLanguage() {
        return language;
    }

    public RegexSource getSource() {
        return source;
    }

    public RegexFlags getFlags() {
        return flags;
    }

    public void setFlags(RegexFlags flags) {
        this.flags = flags;
    }

    public AbstractRegexObject getFlavorSpecificFlags() {
        return flavorSpecificFlags;
    }

    public void setFlavorSpecificFlags(AbstractRegexObject flavorSpecificFlags) {
        this.flavorSpecificFlags = flavorSpecificFlags;
    }

    public RegexOptions getOptions() {
        return source.getOptions();
    }

    public RegexFlavor getFlavor() {
        return source.getOptions().getFlavor();
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

    public int getQuantifierCount() {
        return quantifiers.size();
    }

    public void registerQuantifier(QuantifiableTerm quantifiable) {
        quantifiable.getQuantifier().setIndex(quantifiers.size());
        quantifiers.add(quantifiable.getQuantifier());
    }

    public Token.Quantifier[] getQuantifierArray() {
        return quantifiers.toArray(Token.Quantifier[]::new);
    }

    public Token.Quantifier getQuantifier(int quantifierIndex) {
        return quantifiers.get(quantifierIndex);
    }

    public void registerZeroWidthQuantifiable(QuantifiableTerm zeroWidthQuantifiable) {
        zeroWidthQuantifiable.getQuantifier().setZeroWidthIndex(zeroWidthQuantifiables.size());
        zeroWidthQuantifiables.add(zeroWidthQuantifiable);
    }

    public List<QuantifiableTerm> getZeroWidthQuantifiables() {
        return zeroWidthQuantifiables;
    }

    /**
     * Get capture group with given capture group number. May return multiple nodes due to
     * quantifier unrolling.
     */
    public ArrayList<Group> getGroup(int groupNumber) {
        return captureGroups.get(groupNumber);
    }

    public ArrayList<Group> getGroupByBoundaryIndex(int index) {
        return captureGroups.get(index / 2);
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
            return wrappedRoot.getFirstAlternative().size() - (flags.isSticky() ? 1 : 2);
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

    public GlobalSubTreeIndex getSubtrees() {
        return subtrees;
    }

    public void registerGroupWithGuards(Group group) {
        assert group.getGroupsWithGuardsIndex() < 0;
        groupsWithGuards.add(group);
    }

    public GroupsWithGuardsIndex getGroupsWithGuards() {
        return groupsWithGuards;
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

    public BackReference createBackReference(int[] groupNumbers) {
        for (int groupNumber : groupNumbers) {
            referencedGroups.set(groupNumber);
        }
        return register(new BackReference(groupNumbers));
    }

    public TBitSet getReferencedGroups() {
        return referencedGroups;
    }

    public boolean isGroupReferenced(int groupNumber) {
        return referencedGroups.get(groupNumber);
    }

    public void setGroupRecursivelyReferenced(int groupNumber) {
        recursivelyReferencedGroups.set(groupNumber);
    }

    public boolean isGroupRecursivelyReferenced(int groupNumber) {
        return recursivelyReferencedGroups.get(groupNumber);
    }

    public CharacterClass createCharacterClass(CodePointSet matcherBuilder) {
        assert getEncoding().getFullSet().contains(matcherBuilder);
        return register(new CharacterClass(matcherBuilder));
    }

    public Group createGroup() {
        return register(new Group());
    }

    public Group createCaptureGroup(int groupNumber) {
        Group group = register(new Group(groupNumber));
        assert captureGroups.size() == groupNumber;
        ArrayList<Group> groupList = new ArrayList<>();
        groupList.add(group);
        captureGroups.add(groupList);
        return group;
    }

    public void registerCaptureGroupCopy(Group groupCopy) {
        assert !captureGroups.get(groupCopy.getGroupNumber()).contains(groupCopy);
        captureGroups.get(groupCopy.getGroupNumber()).add(groupCopy);
    }

    public void clearRegisteredCaptureGroups(int groupNumber) {
        captureGroups.get(groupNumber).clear();
    }

    public Group createConditionalBackReferenceGroup(int referencedGroupNumber) {
        referencedGroups.set(referencedGroupNumber);
        conditionGroups.set(referencedGroupNumber);
        return register(new ConditionalBackReferenceGroup(referencedGroupNumber));
    }

    public TBitSet getConditionGroups() {
        return conditionGroups;
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

    public AtomicGroup createAtomicGroup() {
        final AtomicGroup atomicGroup = new AtomicGroup();
        createNFAHelperNodes(atomicGroup);
        return register(atomicGroup);
    }

    public void createNFAHelperNodes(RegexASTSubtreeRootNode rootNode) {
        nodeCount.inc(5);
        PositionAssertion anchored = new PositionAssertion(PositionAssertion.Type.CARET);
        rootNode.setAnchoredInitialState(anchored);
        MatchFound unAnchored = new MatchFound();
        rootNode.setUnAnchoredInitialState(unAnchored);
        MatchFound end = new MatchFound();
        rootNode.setMatchFound(end);
        PositionAssertion anchoredEnd = new PositionAssertion(PositionAssertion.Type.DOLLAR);
        rootNode.setAnchoredFinalState(anchoredEnd);
        MatchFound endChecked = new MatchFound();
        rootNode.setMatchFoundChecked(endChecked);
    }

    public PositionAssertion createPositionAssertion(PositionAssertion.Type type) {
        return register(new PositionAssertion(type));
    }

    public Sequence createSequence() {
        return register(new Sequence());
    }

    public SubexpressionCall createSubexpressionCall(int groupNumber) {
        return register(new SubexpressionCall(groupNumber));
    }

    public BackReference register(BackReference backReference) {
        nodeCount.inc();
        return backReference;
    }

    public CharacterClass register(CharacterClass characterClass) {
        nodeCount.inc();
        return characterClass;
    }

    public Group register(Group group) {
        nodeCount.inc();
        return group;
    }

    public ConditionalBackReferenceGroup register(ConditionalBackReferenceGroup group) {
        nodeCount.inc();
        return group;
    }

    public LookAheadAssertion register(LookAheadAssertion lookAheadAssertion) {
        nodeCount.inc();
        return lookAheadAssertion;
    }

    public LookBehindAssertion register(LookBehindAssertion lookBehindAssertion) {
        nodeCount.inc();
        return lookBehindAssertion;
    }

    public AtomicGroup register(AtomicGroup atomicGroup) {
        nodeCount.inc();
        return atomicGroup;
    }

    public PositionAssertion register(PositionAssertion positionAssertion) {
        nodeCount.inc();
        return positionAssertion;
    }

    public Sequence register(Sequence sequence) {
        nodeCount.inc();
        return sequence;
    }

    public SubexpressionCall register(SubexpressionCall subexpressionCall) {
        nodeCount.inc();
        return subexpressionCall;
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
        if (properties.hasNestedLookBehindAssertions()) {
            throw new UnsupportedRegexException("nested look-behind assertions");
        }
        final int prefixLength = root.getPrefixLengthMax();
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
        if (!flags.isSticky()) {
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
            wrapRootSeq.add(prevOpt);
        }
        root.getSubTreeParent().setGroup(wrapRoot);
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

    public GroupBoundaries createGroupBoundaries(TBitSet updateIndices, TBitSet clearIndices, int firstGroup, int lastGroup) {
        if (!getOptions().getFlavor().usesLastGroupResultField()) {
            GroupBoundaries staticInstance = GroupBoundaries.getStaticInstance(language, updateIndices, clearIndices);
            if (staticInstance != null) {
                return staticInstance;
            }
        }
        GroupBoundaries lookup = new GroupBoundaries(updateIndices, clearIndices, firstGroup, lastGroup);
        if (groupBoundariesDeduplicationMap.containsKey(lookup)) {
            return groupBoundariesDeduplicationMap.get(lookup);
        } else {
            GroupBoundaries gb = new GroupBoundaries(updateIndices.copy(), clearIndices.copy(), firstGroup, lastGroup);
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
     * {@link Token#createCharClass(CodePointSet)} Token.CharacterClass}, but the parser may
     * optimize redundant nodes away and add their source sections to existing nodes. Example:
     * {@code a|b} will be optimized to {@code [ab]}, which will be mapped to both original
     * characters.</li>
     * <li>{@link Sequence}, {@link MatchFound}, {@link RegexASTSubtreeRootNode}: no mapping.</li>
     * <li>{@link PositionAssertion}, {@link BackReference}: mapped to their respective
     * {@link Token}s.</li>
     * <li>Nodes generated by {@link CopyVisitor} are mapped to the same source sections as their
     * counterparts.</li>
     * <li>Nodes inserted as substitutions for e.g. {@code \b} will simply point to the source
     * section they are substituting.</li>
     * <li>Source sections of {@link Token#createQuantifier(int, int, boolean, boolean, boolean)}
     * quantifiers} are mapped to their respective {@link Term}.</li>
     * </ul>
     */
    public List<SourceSection> getSourceSections(RegexASTNode node) {
        return getOptions().isDumpAutomataWithSourceSections() ? sourceSections.get(node) : null;
    }

    public void addSourceSection(RegexASTNode node, SourceSection sourceSection) {
        if (getOptions().isDumpAutomataWithSourceSections() && sourceSection != null) {
            getOrCreateSourceSections(node).add(sourceSection);
        }
    }

    public void addSourceSections(RegexASTNode node, Collection<SourceSection> src) {
        if (getOptions().isDumpAutomataWithSourceSections() && src != null) {
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
        int maxPrefixSize = root.getFirstAlternative().get(literalStart).getMaxPath() - 1;
        for (int i = 0; i < literalStart; i++) {
            if (root.getFirstAlternative().getTerms().get(i).hasLoops()) {
                maxPrefixSize = -1;
            }
        }
        return new InnerLiteral(literal.materialize(), hasMask ? mask.materialize() : null, maxPrefixSize);
    }

    public boolean canTransformToDFA() {
        boolean couldCalculateLastGroup = !getOptions().getFlavor().usesLastGroupResultField() || !getProperties().hasCaptureGroupsInLookAroundAssertions();
        return getNumberOfNodes() <= TRegexOptions.TRegexMaxParseTreeSizeForDFA &&
                        getNumberOfCaptureGroups() <= TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA &&
                        !(getProperties().hasBackReferences() ||
                                        getProperties().hasLargeCountedRepetitions() ||
                                        getProperties().hasNegativeLookAheadAssertions() ||
                                        getProperties().hasNonLiteralLookBehindAssertions() ||
                                        getProperties().hasNegativeLookBehindAssertions() ||
                                        getRoot().hasQuantifiers() ||
                                        getRoot().hasAtomicGroups() ||
                                        getProperties().hasConditionalReferencesIntoLookAheads() ||
                                        getProperties().hasLookAroundWithCaptureGroupsNestedInQuantifier()) &&
                        couldCalculateLastGroup;
    }

    @TruffleBoundary
    public String canTransformToDFAFailureReason() {
        StringJoiner sb = new StringJoiner(", ");
        if (getOptions().getFlavor().usesLastGroupResultField() && getProperties().hasCaptureGroupsInLookAroundAssertions()) {
            sb.add("regex has capture groups in look-around assertions while needing to calculate last group matched");
        }
        if (getNumberOfNodes() > TRegexOptions.TRegexMaxParseTreeSizeForDFA) {
            sb.add(String.format("Parser tree has too many nodes: %d (threshold: %d)", getNumberOfNodes(), TRegexOptions.TRegexMaxParseTreeSizeForDFA));
        }
        if (getNumberOfCaptureGroups() > TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA) {
            sb.add(String.format("regex has too many capture groups: %d (threshold: %d)", getNumberOfCaptureGroups(), TRegexOptions.TRegexMaxNumberOfCaptureGroupsForDFA));
        }
        if (getProperties().hasBackReferences()) {
            sb.add("regex has back-references");
        }
        if (getProperties().hasLargeCountedRepetitions()) {
            sb.add(String.format("regex has large counted repetitions (threshold: %d for single CC, %d for groups)",
                            TRegexOptions.TRegexQuantifierUnrollThresholdSingleCC, TRegexOptions.TRegexQuantifierUnrollThresholdGroup));
        }
        if (getProperties().hasNegativeLookAheadAssertions()) {
            sb.add("regex has negative look-ahead assertions");
        }
        if (getProperties().hasNegativeLookBehindAssertions()) {
            sb.add("regex has negative look-behind assertions");
        }
        if (getProperties().hasNonLiteralLookBehindAssertions()) {
            sb.add("regex has non-literal look-behind assertions");
        }
        if (getRoot().hasQuantifiers()) {
            sb.add("could not unroll all quantifiers");
        }
        if (getRoot().hasAtomicGroups()) {
            sb.add("regex has atomic groups");
        }
        if (getProperties().hasConditionalReferencesIntoLookAheads()) {
            sb.add("regex has conditional back-references into look-ahead assertions");
        }
        if (getProperties().hasLookAroundWithCaptureGroupsNestedInQuantifier()) {
            sb.add("regex has look-around assertion with capture groups nested in a quantified group");
        }
        return sb.toString();
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
