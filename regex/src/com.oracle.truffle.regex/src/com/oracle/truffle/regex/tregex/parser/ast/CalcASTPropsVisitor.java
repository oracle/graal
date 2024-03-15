/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings;

/**
 * This visitor computes various properties of {@link RegexAST} and its {@link RegexASTNode}s, in
 * two passes.
 *
 * <ul>
 * <li>{@link RegexASTNode#getMinPath()}:
 * <ul>
 * <li>The minPath of {@link BackReference}, {@link PositionAssertion}, {@link LookBehindAssertion}
 * and {@link LookAheadAssertion} nodes is the minimum number of CharacterClass nodes that need to
 * be traversed in order to reach the node.</li>
 * <li>The minPath of {@link MatchFound} nodes is undefined (or is always 0). Their minPath is never
 * set by {@link CalcASTPropsVisitor}.</li>
 * <li>The minPath of {@link Sequence} and {@link Group} nodes is the minimum number of
 * {@link CharacterClass} nodes that need to be traversed (starting at the AST root) in order to
 * reach the end of the node. The minPath field of {@link Sequence} nodes is used as a mutable
 * iteration variable when traversing their children (see {@link #visit(CharacterClass)}). The
 * resulting value after the traversal holds the minimum number of {@link CharacterClass} nodes that
 * need to be traversed to reach the end of the Sequence. The same holds for {@link Group} nodes.
 * </li>
 * <li>The contents of {@link LookAroundAssertion}s are treated separately, so their minPath values
 * have nothing to do with their parent expression.</li>
 * </ul>
 * </li>
 * <li>{@link RegexASTNode#getMaxPath()} is set analogous to {@link RegexASTNode#getMinPath()}, but
 * without taking loops ({@link Group#isLoop()}) into account.</li>
 * <li>{@link RegexASTNode#isDead()}: The following nodes are marked as dead:
 * <ul>
 * <li>{@link PositionAssertion}s whose minimum path is greater than zero (type
 * {@link com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion.Type#CARET} in forward mode
 * and {@link com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion.Type#DOLLAR} in backward
 * mode). Note that this algorithm will e.g. not mark the dollar assertion in {@code /(?=a$)bc/} as
 * dead, since it has a (reverse) minimum path of 0 inside the look-ahead assertion.</li>
 * <li>{@link CharacterClass}es that don't match anything ({@link CodePointSet#matchesNothing()}).
 * </li>
 * <li>{@link Sequence}s that contain a dead node.</li>
 * <li>{@link Group}s where all alternatives are dead.</li>
 * <li>{@link RegexASTSubtreeRootNode}s whose child group is dead.</li>
 * </ul>
 * </li>
 * <li>{@link RegexASTNode#startsWithCaret()}/{@link RegexASTNode#endsWithDollar()}:
 * <ul>
 * <li>{@link Sequence}s that start with a caret {@link PositionAssertion} / end with a dollar
 * {@link PositionAssertion}.</li>
 * <li>{@link Group}s where all alternatives start with a caret / end with a dollar.</li>
 * </ul>
 * </li>
 * <li>{@link RegexASTNode#hasCaret()}/{@link RegexASTNode#hasDollar()}:
 * <ul>
 * <li>{@link Sequence}s that contain a caret / dollar {@link PositionAssertion}.</li>
 * <li>{@link Group}s where any alternatives contains a caret / dollar {@link PositionAssertion}.
 * </li>
 * </ul>
 * </li>
 * </ul>
 *
 * @see RegexASTNode#hasCaret()
 * @see RegexASTNode#hasDollar()
 * @see RegexASTNode#startsWithCaret()
 * @see RegexASTNode#endsWithDollar()
 * @see RegexASTNode#getMinPath()
 * @see RegexASTNode#getMaxPath()
 * @see RegexASTNode#isDead()
 * @see RegexAST#createPrefix()
 */
public class CalcASTPropsVisitor extends DepthFirstTraversalRegexASTVisitor {

    /**
     * When processing a {@link Group}, these flags will be set in the group iff they are set in
     * <em>all</em> of its alternatives.
     */
    private static final int AND_FLAGS = RegexASTNode.FLAG_STARTS_WITH_CARET | RegexASTNode.FLAG_ENDS_WITH_DOLLAR | RegexASTNode.FLAG_DEAD;
    /**
     * When processing a {@link Group}, these flags will be set in the group iff they are set in
     * <em>any</em> of its alternatives.
     */
    private static final int OR_FLAGS = RegexASTNode.FLAG_HAS_CARET |
                    RegexASTNode.FLAG_HAS_DOLLAR |
                    RegexASTNode.FLAG_HAS_LOOPS |
                    RegexASTNode.FLAG_HAS_QUANTIFIERS |
                    RegexASTNode.FLAG_HAS_CAPTURE_GROUPS |
                    RegexASTNode.FLAG_HAS_LOOK_AHEADS |
                    RegexASTNode.FLAG_HAS_LOOK_BEHINDS |
                    RegexASTNode.FLAG_HAS_BACK_REFERENCES;
    private static final int CHANGED_FLAGS = AND_FLAGS | OR_FLAGS;

    private final RegexAST ast;
    private final CompilationBuffer compilationBuffer;
    private final EconomicMap<Integer, List<Group>> conditionalBackReferences;
    private final EconomicMap<Integer, List<Group>> conditionGroups;

    public CalcASTPropsVisitor(RegexAST ast, CompilationBuffer compilationBuffer) {
        this.ast = ast;
        this.compilationBuffer = compilationBuffer;
        this.conditionalBackReferences = EconomicMap.create(ast.getConditionGroups().numberOfSetBits());
        this.conditionGroups = EconomicMap.create(ast.getConditionGroups().numberOfSetBits());
    }

    public static void run(RegexAST ast, CompilationBuffer compilationBuffer) {
        CalcASTPropsVisitor visitor = new CalcASTPropsVisitor(ast, compilationBuffer);
        visitor.runReverse(ast.getRoot());
        visitor.run(ast.getRoot());
        visitor.checkConditionalBackReferences();
        visitor.registerConditionGroupsInLookAheadAssertions();
    }

    @Override
    protected void init(RegexASTNode runRoot) {
        runRoot.setMinPath(0);
        runRoot.setMaxPath(0);
    }

    @Override
    protected void visit(BackReference backReference) {
        ast.getProperties().setBackReferences();
        if (backReference.isNestedBackReference() && ast.getOptions().getFlavor().supportsRecursiveBackreferences()) {
            ast.getProperties().setRecursiveBackReferences();
        }
        backReference.setHasBackReferences();
        backReference.getParent().setHasBackReferences();
        if (backReference.hasQuantifier()) {
            // TODO: maybe check if the referenced group can produce a zero-width match
            setZeroWidthQuantifierIndex(backReference);
        }
        if (backReference.hasNotUnrolledQuantifier()) {
            backReference.getParent().setHasQuantifiers();
            setQuantifierIndex(backReference);
        }
        backReference.setMinPath(backReference.getParent().getMinPath());
        backReference.setMaxPath(backReference.getParent().getMaxPath());
    }

    @Override
    protected void visit(Group group) {
        if (group.getParent().isSequence() || group.getParent().isAtomicGroup()) {
            group.setMinPath(group.getParent().getMinPath());
            group.setMaxPath(group.getParent().getMaxPath());
        } else {
            assert group.getParent().isLookAroundAssertion() || group.getParent().isRoot();
            group.setMinPath(0);
            group.setMaxPath(0);
        }
    }

    @Override
    protected void leave(Group group) {
        if (group.size() > 1) {
            ast.getProperties().setAlternations();
        }
        if (group.isConditionalBackReferenceGroup()) {
            assert group.size() == 2;
            ast.getProperties().setConditionalBackReferences();
        }
        if (group.getGroupNumber() > 0) {
            ast.getProperties().setCaptureGroups();
        }
        if (isForward()) {
            int groupNumber = group.getGroupNumber();
            if (groupNumber > 0 && ast.getConditionGroups().get(groupNumber)) {
                if (!conditionGroups.containsKey(groupNumber)) {
                    conditionGroups.put(groupNumber, new ArrayList<>());
                }
                conditionGroups.get(groupNumber).add(group);
            }
            if (group.isConditionalBackReferenceGroup()) {
                int referencedGroupNumber = group.asConditionalBackReferenceGroup().getReferencedGroupNumber();
                if (!conditionalBackReferences.containsKey(referencedGroupNumber)) {
                    conditionalBackReferences.put(referencedGroupNumber, new ArrayList<>());
                }
                conditionalBackReferences.get(referencedGroupNumber).add(group);
            }
        }
        if (group.isDead()) {
            if (group.getParent() != null) {
                group.getParent().markAsDead();
            }
            return;
        }
        int minPath = Integer.MAX_VALUE;
        int maxPath = 0;
        int prefixLengthMin = 0;
        int prefixLengthMax = 0;
        int flags = (group.isLoop() ? RegexASTNode.FLAG_HAS_LOOPS : 0) | AND_FLAGS;
        for (Sequence s : group.getAlternatives()) {
            if (s.isDead()) {
                continue;
            }
            flags = (flags & (s.getFlags(AND_FLAGS) | ~AND_FLAGS)) | s.getFlags(OR_FLAGS);
            minPath = Math.min(minPath, s.getMinPath());
            maxPath = Math.max(maxPath, s.getMaxPath());
            if (isForward()) {
                prefixLengthMin = Math.min(prefixLengthMin, s.getPrefixLengthMin());
                prefixLengthMax = Math.max(prefixLengthMax, s.getPrefixLengthMax());
            }
        }
        if (group.hasQuantifier()) {
            if (!group.isUnrolledQuantifier()) {
                flags |= RegexASTNode.FLAG_HAS_QUANTIFIERS;
                setQuantifierIndex(group);
                if (group.getQuantifier().getMin() == 0) {
                    flags &= ~(RegexASTNode.FLAG_STARTS_WITH_CARET | RegexASTNode.FLAG_ENDS_WITH_DOLLAR);
                }
                /*
                 * group.minPath and group.maxPath are summed up from the beginning of the regex to
                 * the beginning of the group. the min and max path of the sequences are further
                 * summed up with min and max path of the group, so sequence.minPath - group.minPath
                 * is the sequence's "own" minPath
                 */
                minPath = group.getMinPath() + ((minPath - group.getMinPath()) * group.getQuantifier().getMin());
                if (group.getQuantifier().isInfiniteLoop()) {
                    flags |= RegexASTNode.FLAG_HAS_LOOPS;
                } else {
                    maxPath = group.getMaxPath() + ((maxPath - group.getMaxPath()) * group.getQuantifier().getMax());
                }
            }
            /*
             * If a quantifier can produce a zero-width match, we have to check this in
             * back-tracking mode. In flavors more complex than JS (where empty loop iterations
             * can be admitted), we have to check this at all times. In JS, we can afford to only do
             * this check when the expression contains back-references or lookarounds.
             */
            if (minPath - group.getMinPath() == 0) {
                setZeroWidthQuantifierIndex(group);
            }
        }
        if (group.isCapturing()) {
            flags |= RegexASTNode.FLAG_HAS_CAPTURE_GROUPS;
            if (group.getMinPath() == minPath && group.getMaxPath() == maxPath) {
                ast.getProperties().setEmptyCaptureGroups();
            }
        }
        group.setFlags(flags, CHANGED_FLAGS);
        group.setMinPath(minPath);
        group.setMaxPath(maxPath);
        if (isForward()) {
            group.setPrefixLengthMin(prefixLengthMin);
            group.setPrefixLengthMax(prefixLengthMax);
        }
        if (group.getParent().isSequence() || group.getParent().isAtomicGroup()) {
            group.getParent().setMinPath(minPath);
            group.getParent().setMaxPath(maxPath);
        }
        if (group.getParent() != null) {
            group.getParent().setFlags(group.getParent().getFlags(CHANGED_FLAGS) | flags, CHANGED_FLAGS);
            // Propagate prefix length to the parent of the group (a SubtreeRootNode like a
            // LookAroundAssertion or AtomicGroup).
            if (isForward()) {
                group.getParent().setPrefixLengthMin(prefixLengthMin);
                group.getParent().setPrefixLengthMax(prefixLengthMax);
            }
        }
    }

    @Override
    protected void visit(Sequence sequence) {
        sequence.setMinPath(sequence.getParent().getMinPath());
        sequence.setMaxPath(sequence.getParent().getMaxPath());
    }

    @Override
    protected void leave(Sequence sequence) {
        // remove dead negated lookaround expressions. we can't do this directly in their visit
        // methods, since that would mess up their parent Sequence's iterator state
        int i = 0;
        int prefixLengthMin = 0;
        int prefixLengthMax = 0;
        while (i < sequence.size()) {
            Term term = sequence.get(i);
            if (term.isLookAroundAssertion()) {
                LookAroundAssertion lookAround = term.asLookAroundAssertion();
                if (lookAround.isNegated() && lookAround.isDead()) {
                    sequence.removeTerm(i, compilationBuffer);
                    continue;
                }
            }
            if (isForward()) {
                // We take the max of both the lower and upper bound on the prefix. A longer prefix
                // in one part of the expression might dominate a smaller, but variable, prefix in
                // another part of the expression. This way, a variable prefix can be hidden by a
                // larger fixed prefix and TraceFinder can still be safely used.
                prefixLengthMin = Math.max(prefixLengthMin, term.getPrefixLengthMin());
                prefixLengthMax = Math.max(prefixLengthMax, term.getPrefixLengthMax());
            }
            i++;
        }
        if (isForward()) {
            sequence.setPrefixLengthMin(prefixLengthMin);
            sequence.setPrefixLengthMax(prefixLengthMax);
        }
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        switch (assertion.type) {
            case CARET:
                if (isForward()) {
                    assertion.getParent().setHasCaret();
                    if (assertion.getParent().getMinPath() > 0) {
                        assertion.markAsDead();
                        assertion.getParent().markAsDead();
                    } else {
                        assertion.getParent().setStartsWithCaret();
                    }
                }
                break;
            case DOLLAR:
                if (isReverse()) {
                    assertion.getParent().setHasDollar();
                    if (assertion.getParent().getMinPath() > 0) {
                        assertion.markAsDead();
                        assertion.getParent().markAsDead();
                    } else {
                        assertion.getParent().setEndsWithDollar();
                    }
                }
                break;
        }
        assertion.setMinPath(assertion.getParent().getMinPath());
        assertion.setMaxPath(assertion.getParent().getMaxPath());
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        assertion.getParent().setHasLookBehinds();
        assertion.setMinPath(assertion.getParent().getMinPath());
        assertion.setMaxPath(assertion.getParent().getMaxPath());
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        if (isForward() && !assertion.isDead()) {
            if (assertion.isNegated()) {
                ast.getProperties().setNegativeLookBehindAssertions();
            } else {
                ast.getProperties().setLookBehindAssertions();
            }
            if (!assertion.isLiteral()) {
                ast.getProperties().setNonLiteralLookBehindAssertions();
            }
            int minPath = assertion.getMinPath();
            int maxPath = assertion.getMaxPath();
            RegexASTSubtreeRootNode laParent = assertion.getSubTreeParent();
            while (!(laParent instanceof RegexASTRootNode)) {
                if (laParent instanceof LookBehindAssertion) {
                    ast.getProperties().setNestedLookBehindAssertions();
                }
                minPath += laParent.getMinPath();
                maxPath += laParent.getMaxPath();
                laParent = laParent.getSubTreeParent();
            }
            if (assertion.isLiteral()) {
                assertion.setPrefixLengthMin(Math.max(0, assertion.getLiteralLength() - maxPath));
                assertion.setPrefixLengthMax(Math.max(0, assertion.getLiteralLength() - minPath));
            }
        }
        leaveLookAroundAssertion(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        assertion.getParent().setHasLookAheads();
        assertion.setMinPath(assertion.getParent().getMinPath());
        assertion.setMaxPath(assertion.getParent().getMaxPath());
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        if (isForward() && !assertion.isDead()) {
            if (assertion.isNegated()) {
                ast.getProperties().setNegativeLookAheadAssertions();
            } else {
                ast.getProperties().setLookAheadAssertions();
            }
        }
        leaveLookAroundAssertion(assertion);
    }

    @Override
    protected void visit(AtomicGroup atomicGroup) {
        atomicGroup.setMinPath(atomicGroup.getParent().getMinPath());
        atomicGroup.setMaxPath(atomicGroup.getParent().getMaxPath());
    }

    @Override
    protected void leave(AtomicGroup atomicGroup) {
        if (isForward() && !atomicGroup.isDead()) {
            ast.getProperties().setAtomicGroups();
        }
        leaveSubtreeRootNode(atomicGroup, CHANGED_FLAGS);
        atomicGroup.getParent().setMinPath(atomicGroup.getMinPath());
        atomicGroup.getParent().setMaxPath(atomicGroup.getMaxPath());
    }

    private void leaveLookAroundAssertion(LookAroundAssertion assertion) {
        if (assertion.hasCaptureGroups()) {
            ast.getProperties().setCaptureGroupsInLookAroundAssertions();
        }
        // flag propagation to parent sequences:
        // - LookAhead expressions propagate all flags
        // - LookBehind expressions omit "startsWithCaret" and "endsWithDollar"
        // - negated lookarounds additionally don't propagate the "dead" flag
        leaveSubtreeRootNode(assertion, assertion.isNegated() ? OR_FLAGS : assertion.isLookBehindAssertion() ? OR_FLAGS | RegexASTNode.FLAG_DEAD : CHANGED_FLAGS);
    }

    private static void leaveSubtreeRootNode(RegexASTSubtreeRootNode subtreeRootNode, int flagMask) {
        subtreeRootNode.getParent().setFlags(subtreeRootNode.getFlags(flagMask) | subtreeRootNode.getParent().getFlags(flagMask), flagMask);
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        if (isForward()) {
            if (!characterClass.getCharSet().matchesSingleChar()) {
                if (!characterClass.getCharSet().matches2CharsWith1BitDifference()) {
                    ast.getProperties().unsetCharClassesCanBeMatchedWithMask();
                }
                if (!ast.getEncoding().isFixedCodePointWidth(characterClass.getCharSet())) {
                    ast.getProperties().unsetFixedCodePointWidth();
                }
                ast.getProperties().setCharClasses();
            }
            if (ast.getEncoding() == Encodings.UTF_16 && Constants.SURROGATES.intersects(characterClass.getCharSet())) {
                ast.getProperties().setLoneSurrogates();
            }
        }
        if (characterClass.hasNotUnrolledQuantifier()) {
            characterClass.getParent().setHasQuantifiers();
            setQuantifierIndex(characterClass);
            characterClass.getParent().incMinPath(characterClass.getQuantifier().getMin());
            if (characterClass.getQuantifier().isInfiniteLoop()) {
                characterClass.setHasLoops();
                characterClass.getParent().setHasLoops();
            } else {
                characterClass.getParent().incMaxPath(characterClass.getQuantifier().getMax());
            }
        } else {
            characterClass.getParent().incMinPath();
            characterClass.getParent().incMaxPath();
        }
        characterClass.setMinPath(characterClass.getParent().getMinPath());
        characterClass.setMaxPath(characterClass.getParent().getMaxPath());
        if (characterClass.getCharSet().matchesNothing()) {
            characterClass.markAsDead();
            characterClass.getParent().markAsDead();
        }
    }

    private void setQuantifierIndex(QuantifiableTerm term) {
        assert term.hasQuantifier();
        if (isForward() && term.getQuantifier().getIndex() < 0) {
            term.getQuantifier().setIndex(ast.getQuantifierCount().inc());
        }
    }

    private void setZeroWidthQuantifierIndex(QuantifiableTerm term) {
        if (isForward() && term.getQuantifier().getZeroWidthIndex() < 0) {
            ast.registerZeroWidthQuantifiable(term);
        }
    }

    @Override
    protected void visit(SubexpressionCall subexpressionCall) {
        throw CompilerDirectives.shouldNotReachHere("subexpression calls should be expanded by the parser");
    }

    private void checkConditionalBackReferences() {
        for (int conditionGroupNumber : ast.getConditionGroups()) {
            if (!conditionalBackReferences.containsKey(conditionGroupNumber)) {
                // All conditional back-references to this condition group must have been optimized
                // away.
                continue;
            }
            List<Group> references = conditionalBackReferences.get(conditionGroupNumber);
            assert conditionGroups.containsKey(conditionGroupNumber);
            List<Group> groups = conditionGroups.get(conditionGroupNumber);
            RegexASTSubtreeRootNode referencesAncestor = lowestCommonAncestor(references);
            for (Group conditionGroup : groups) {
                RegexASTSubtreeRootNode parent = conditionGroup.getSubTreeParent();
                RegexASTSubtreeRootNode commonAncestor = lowestCommonAncestor(parent, referencesAncestor);
                while (parent != commonAncestor) {
                    if (parent.isLookAheadAssertion()) {
                        ast.getProperties().setConditionalReferencesIntoLookAheads();
                        return;
                    }
                    parent = parent.getSubTreeParent();
                }
            }
        }
    }

    private static RegexASTSubtreeRootNode lowestCommonAncestor(List<? extends RegexASTNode> nodes) {
        if (nodes.size() == 1) {
            return nodes.get(0).getSubTreeParent();
        } else if (nodes.size() >= 2) {
            RegexASTSubtreeRootNode ancestor = lowestCommonAncestor(nodes.get(0).getSubTreeParent(), nodes.get(1).getSubTreeParent());
            for (int i = 2; i < nodes.size(); i++) {
                ancestor = lowestCommonAncestor(ancestor, nodes.get(i).getSubTreeParent());
            }
            return ancestor;
        } else {
            throw CompilerDirectives.shouldNotReachHere("lowestCommonAncestor called with empty list");
        }
    }

    private static RegexASTSubtreeRootNode lowestCommonAncestor(RegexASTSubtreeRootNode argA, RegexASTSubtreeRootNode argB) {
        EconomicSet<RegexASTSubtreeRootNode> ancestorsOfA = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        RegexASTSubtreeRootNode a = argA;
        while (a != null) {
            ancestorsOfA.add(a);
            a = a.getSubTreeParent();
        }
        RegexASTSubtreeRootNode b = argB;
        while (b != null) {
            if (ancestorsOfA.contains(b)) {
                return b;
            }
            b = b.getSubTreeParent();
        }
        return null;
    }

    private void registerConditionGroupsInLookAheadAssertions() {
        for (int conditionGroupNumber : ast.getConditionGroups()) {
            List<Group> references = conditionalBackReferences.get(conditionGroupNumber);
            if (references != null) {
                for (Group reference : references) {
                    RegexASTSubtreeRootNode subtreeParent = reference.getSubTreeParent();
                    while (subtreeParent != null) {
                        if (subtreeParent.isLookAheadAssertion()) {
                            subtreeParent.asLookAheadAssertion().registerReferencedConditionGroup(reference.asConditionalBackReferenceGroup().getReferencedGroupNumber());
                        }
                        subtreeParent = subtreeParent.getSubTreeParent();
                    }
                }
            }
        }
    }
}
