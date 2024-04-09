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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import java.util.Arrays;
import java.util.Set;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.ASTStepVisitor;
import com.oracle.truffle.regex.tregex.nfa.TransitionGuard;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.GroupsWithGuardsIndex;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * Special AST visitor that will find all immediate successors of a given Term when the AST is seen
 * as a NFA, in priority order. A successor can either be a {@link CharacterClass} or a
 * {@link MatchFound} node.
 *
 * <pre>
 * {@code
 * Examples:
 *
 * Successors of "b" in (a|b)c:
 * 1.: "c"
 *
 * Successors of "b" in (a|b)*c:
 * 1.: "a"
 * 2.: "b"
 * 3.: "c"
 * }
 * </pre>
 * <p>
 * For every successor, the visitor will find the full path of AST nodes that have been traversed
 * from the initial node to the successor node, where {@link Group} nodes are treated specially: The
 * path will contain separate entries for <em>entering</em> and <em>leaving</em> a {@link Group},
 * and a special <em>pass-through</em> node for empty sequences marked with
 * {@link Sequence#isQuantifierPassThroughSequence()}. Furthermore, the visitor will not descend
 * into lookaround assertions, it will jump over them and just add their corresponding
 * {@link LookAheadAssertion} or {@link LookBehindAssertion} node to the path.
 *
 * <pre>
 * {@code
 * Examples with full path information:
 *
 * Successors of "b" in (a|b)c:
 * 1.: [leave group 1], [CharClass c]
 *
 * Successors of "b" in (a|b)*c:
 * 1.: [leave group 1], [enter group 1], [CharClass a]
 * 2.: [leave group 1], [enter group 1], [CharClass b]
 * 3.: [leave group 1], [CharClass c]
 * }
 * </pre>
 */
public abstract class NFATraversalRegexASTVisitor {

    protected final RegexAST ast;
    private Term root;
    /**
     * This buffer of long values represents the path of {@link RegexASTNode}s traversed so far.
     * Every path element consists of an ast node id, a "group-action" flag indicating whether we
     * enter, exit, or pass through a group, and a group alternation index, indicating the next
     * alternation we should visit when back-tracking to find the next successor.
     */
    private final LongArrayBuffer curPath = new LongArrayBuffer(8);
    /**
     * insideLoops is the set of looping groups that we are currently inside of. We need to maintain
     * this in order to detect infinite loops in the NFA traversal. If we enter a looping group,
     * traverse it without encountering a CharacterClass node or a MatchFound node and arrive back
     * at the same group, then we are bound to loop like this forever. Using insideLoops, we can
     * detect this situation and proceed with the search using another alternative. For example, in
     * the RegexAST {@code ((|[a])*|)*}, which corresponds to the regex {@code /(a*?)* /}, we can
     * traverse the inner loop, {@code (|[a])*}, without hitting any CharacterClass node by choosing
     * the first alternative and we will then arrive back at the outer loop. There, we detect an
     * infinite loop, which causes us to backtrack and choose the second alternative in the inner
     * loop, leading us to the CharacterClass node {@code [a]}. <br>
     * NB: For every looping group, this set tells us whether there is an {@code enter} node for it
     * on the current path.
     */
    private final StateSet<GroupsWithGuardsIndex, Group> insideLoops;
    /**
     * This set is needed to make sure that a quantified term cannot match the empty string, as is
     * specified in step 2a of RepeatMatcher from ECMAScript draft 2018, chapter 21.2.2.5.1.
     */
    private final TBitSet insideEmptyGuardGroup;
    private RegexASTNode cur;
    private Set<LookBehindAssertion> traversableLookBehindAssertions;
    private boolean canTraverseCaret = false;
    private TBitSet matchedConditionGroups;
    private boolean forward = true;
    private boolean done = false;
    /**
     * This is set to true whenever a nested call discovers that the current path is dead. One of
     * the top-levels methods should check for this flag regularly and back-track if needed.
     */
    private boolean shouldRetreat = false;
    private boolean recalcTransitionGuards = false;

    /**
     * The exhaustive path traversal may result in some duplicate successors, e.g. on a regex like
     * {@code /(a?|b?)(a?|b?)/}. In order to avoid a combinatorial explosion of duplicate
     * successors, we prune the search for successors whenever we enter into a state that is
     * equivalent to one visited before. In order for two traversal states to be considered
     * equivalent, they must be equal in the following parameters:
     * <ul>
     * <li>the {@link #cur current node}</li>
     * <li>the set of traversed {@link PositionAssertion position assertions}</li>
     * <li>the set of traversed {@link LookAroundAssertion}s</li>
     * <li>the {@link TransitionGuard}s incurred by the traversal</li>
     * <li>the resulting {@link GroupBoundaries} (updates, clears, last group)</li>
     * </ul>
     * Deduplication is performed when {@link #cur the current node} is updated. However, in order
     * to reduce this costly operation, we only deduplicate when update {@link #cur the current
     * node} to a {@link Sequence}. This is sufficient for our purposes since runaway traversals
     * will need to regularly enter new {@link Sequence} nodes. NB: It also simplifies the
     * equivalence test for {@link DeduplicationKey}, because if we considered {@link Group}s, we
     * would need to distinguish the current alternative index stored in the last element of
     * {@link #curPath}.
     */
    private final EconomicSet<DeduplicationKey> pathDeduplicationSet = EconomicSet.create();
    private final LongArrayBuffer dedupKey = new LongArrayBuffer(8);

    private final TBitSet lookAroundsOnPath;
    private int dollarsOnPath = 0;
    private int caretsOnPath = 0;
    private final int[] lookAroundVisitiedCount;
    private final TBitSet captureGroupUpdates;
    private final TBitSet captureGroupClears;
    private final TBitSet referencedGroupBoundaries;
    private int lastGroup = -1;
    private final TBitSet boundedQuantifiersLoop;
    private final TBitSet boundedQuantifiersExited;

    /**
     * Quantifier guards are stored in an immutable linked list, which allows for cheap sharing of
     * snapshots for the purposes of deduplication.
     */
    private final LongArrayBuffer transitionGuards = new LongArrayBuffer(8);
    private long[] quantifierGuardsResult = null;

    protected NFATraversalRegexASTVisitor(RegexAST ast) {
        this.ast = ast;
        this.insideLoops = StateSet.create(ast.getGroupsWithGuards());
        this.insideEmptyGuardGroup = new TBitSet(ast.getGroupsWithGuards().size());
        this.lookAroundsOnPath = new TBitSet(ast.getSubtrees().size());
        this.lookAroundVisitiedCount = new int[ast.getSubtrees().size()];
        this.captureGroupUpdates = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.captureGroupClears = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.referencedGroupBoundaries = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.boundedQuantifiersLoop = new TBitSet(ast.getQuantifierCount());
        this.boundedQuantifiersExited = new TBitSet(ast.getQuantifierCount());
        for (int i : ast.getReferencedGroups()) {
            referencedGroupBoundaries.set(Group.groupNumberToBoundaryIndexStart(i));
            referencedGroupBoundaries.set(Group.groupNumberToBoundaryIndexEnd(i));
        }
    }

    public Set<LookBehindAssertion> getTraversableLookBehindAssertions() {
        return traversableLookBehindAssertions;
    }

    public void setTraversableLookBehindAssertions(Set<LookBehindAssertion> traversableLookBehindAssertions) {
        this.traversableLookBehindAssertions = traversableLookBehindAssertions;
    }

    public boolean canTraverseCaret() {
        return canTraverseCaret;
    }

    public void setCanTraverseCaret(boolean canTraverseCaret) {
        this.canTraverseCaret = canTraverseCaret;
    }

    public void setMatchedConditionGroups(TBitSet matchedConditionGroups) {
        this.matchedConditionGroups = matchedConditionGroups;
    }

    public TBitSet getMatchedConditionGroups() {
        assert isBuildingDFA();
        return matchedConditionGroups;
    }

    protected TBitSet getCurrentMatchedConditionGroups() {
        assert isBuildingDFA();
        if (!ast.getProperties().hasConditionalBackReferences()) {
            return matchedConditionGroups;
        }
        TBitSet currentMatchedConditionGroups = matchedConditionGroups.copy();
        for (int conditionGroup : ast.getConditionGroups()) {
            if (getCaptureGroupClears().get(Group.groupNumberToBoundaryIndexEnd(conditionGroup))) {
                currentMatchedConditionGroups.clear(conditionGroup);
            }
            if (getCaptureGroupUpdates().get(Group.groupNumberToBoundaryIndexEnd(conditionGroup))) {
                currentMatchedConditionGroups.set(conditionGroup);
            }
        }
        return currentMatchedConditionGroups;
    }

    protected boolean isReverse() {
        return !forward;
    }

    public void setReverse(boolean reverse) {
        this.forward = !reverse;
    }

    protected abstract boolean isBuildingDFA();

    protected abstract boolean canPruneAfterUnconditionalFinalState();

    private boolean canTraverseLookArounds() {
        return isBuildingDFA();
    }

    protected void run(Term runRoot) {
        clearCaptureGroupData();
        recalcTransitionGuards = false;
        assert insideLoops.isEmpty();
        assert insideEmptyGuardGroup.isEmpty();
        assert curPath.isEmpty();
        assert dollarsOnPath == 0;
        assert caretsOnPath == 0;
        assert lookAroundsOnPath.isEmpty();
        assert nodeVisitsEmpty() : Arrays.toString(lookAroundVisitiedCount);
        assert !shouldRetreat;
        assert transitionGuards.isEmpty();
        assert captureGroupUpdates.isEmpty();
        assert captureGroupClears.isEmpty();
        assert lastGroup == -1;
        root = runRoot;
        pathDeduplicationSet.clear();
        if (runRoot.isGroup() && runRoot.getParent().isSubtreeRoot()) {
            cur = runRoot;
        } else {
            // Before we call advanceTerm, we always push a group exit or pass-through to the path.
            // advanceTerm will when push any further group exits as it walks up the AST.
            if (runRoot.isGroup()) {
                pushGroupExit(runRoot.asGroup());
            }
            advanceTerm(runRoot);
        }
        boolean foundNextTarget = false;
        while (!done) {
            while (!done && !foundNextTarget) {
                // advance until we reach the next node to visit
                foundNextTarget = doAdvance();
                if (foundNextTarget) {
                    foundNextTarget = deduplicatePath(false);
                }
            }
            if (done) {
                break;
            }
            RegexASTNode target = pathGetNode(curPath.peek());
            visit(target);
            if (canPruneAfterUnconditionalFinalState() && target.isMatchFound() && !dollarsOnPath() && !caretsOnPath() && lookAroundsOnPath.isEmpty() && !hasQuantifierGuards()) {
                /*
                 * Transitions after an unconditional final state transition will never be taken, so
                 * it is safe to prune them.
                 */
                insideLoops.clear();
                insideEmptyGuardGroup.clear();
                curPath.clear();
                clearCaptureGroupData();
                clearQuantifierGuards();
                quantifierGuardsResult = null;
                /*
                 * no need to clear nodeVisitedCount here, because !dollarsOnPath() &&
                 * lookAroundsOnPath.isEmpty() implies nodeVisitsEmpty()
                 */
                break;
            }
            quantifierGuardsResult = null;
            retreat();
            foundNextTarget = false;
            // If we have back-tracked into an empty-match transition, then we must continue by
            // advancing past the empty-match group using advanceTerm instead of entering the group
            // again using doAdvance.
            if (cur.isGroup() && cur.hasEmptyGuard()) {
                foundNextTarget = advanceTerm(cur.asGroup());
            }
        }
        if (useQuantifierGuards()) {
            clearQuantifierGuards();
        }
        done = false;
    }

    /**
     * Visit the next successor found.
     */
    protected abstract void visit(RegexASTNode target);

    protected abstract void enterLookAhead(LookAheadAssertion assertion);

    protected abstract void leaveLookAhead(LookAheadAssertion assertion);

    protected boolean caretsOnPath() {
        return caretsOnPath > 0;
    }

    protected boolean dollarsOnPath() {
        return dollarsOnPath > 0;
    }

    protected boolean hasQuantifierGuards() {
        calcQuantifierGuards();
        return quantifierGuardsResult.length > 0;
    }

    protected long[] getQuantifierGuardsOnPath() {
        calcQuantifierGuards();
        return quantifierGuardsResult;
    }

    protected void calcQuantifierGuards() {
        if (quantifierGuardsResult == null) {
            assert useQuantifierGuards() || getQuantifierGuards().isEmpty();
            quantifierGuardsResult = getQuantifierGuards().isEmpty() ? TransitionGuard.NO_GUARDS : getQuantifierGuards().toArray();
            if (ast.getOptions().getFlavor().supportsRecursiveBackreferences()) {
                // Note: Updating the recursive back-reference boundaries before all other
                // quantifier guards causes back-references to empty matches to fail. This
                // is expected behavior in OracleDBFlavor.
                long[] reordered = new long[quantifierGuardsResult.length];
                int i = 0;
                for (long guard : quantifierGuardsResult) {
                    if (TransitionGuard.is(guard, TransitionGuard.Kind.updateRecursiveBackrefPointer)) {
                        reordered[i++] = guard;
                    }
                }
                if (i == 0) {
                    return;
                }
                for (long guard : quantifierGuardsResult) {
                    if (!TransitionGuard.is(guard, TransitionGuard.Kind.updateRecursiveBackrefPointer)) {
                        reordered[i++] = guard;
                    }
                }
                assert i == quantifierGuardsResult.length;
                quantifierGuardsResult = reordered;
            }
        }
    }

    protected GroupBoundaries getGroupBoundaries() {
        return ast.createGroupBoundaries(getCaptureGroupUpdates(), getCaptureGroupClears(), getLastGroup());
    }

    /**
     * Advances the traversal by diving into {@link #cur current node} in pursuit of the next
     * successor.
     *
     * @return {@code true} if a successor was reached in this step
     */
    private boolean doAdvance() {
        // We only use the insideLoops optimization when the regex flavor does not allow empty loop
        // iterations. Empty loop iterations can occur when a regex flavor monitor capture groups
        // in its empty check, or when it doesn't use backtracking when exiting a loop.
        if (cur.isDead() || (!ast.getOptions().getFlavor().canHaveEmptyLoopIterations() && cur.isGroupWithGuards() && insideLoops.contains(cur.asGroup()))) {
            return retreat();
        }
        if (cur.isSequence()) {
            final Sequence sequence = (Sequence) cur;
            if (sequence.isEmpty()) {
                Group parent = sequence.getParent();
                if (sequence.isQuantifierPassThroughSequence()) {
                    // this empty sequence was inserted during quantifier expansion, so it is
                    // allowed to pass through the parent quantified group.
                    assert pathGetNode(curPath.peek()) == parent && pathIsGroupEnter(curPath.peek());
                    switchEnterToPassThrough(parent);
                    if (shouldRetreat) {
                        return retreat();
                    }
                    if (parent.isLoop()) {
                        unregisterInsideLoop(parent);
                    }
                } else {
                    pushGroupExit(parent);
                    if (shouldRetreat) {
                        return retreat();
                    }
                }
                return advanceTerm(parent);
            } else {
                cur = forward ? sequence.getFirstTerm() : sequence.getLastTerm();
                return false;
            }
        } else if (cur.isGroup()) {
            final Group group = (Group) cur;
            pushGroupEnter(group, 1);
            if (shouldRetreat) {
                return retreat();
            }
            if (group.hasEmptyGuard()) {
                insideEmptyGuardGroup.set(group.getGroupsWithGuardsIndex());
            }
            if (group.isLoop()) {
                registerInsideLoop(group);
            }
            // This path will only be hit when visiting a group for the first time. All groups
            // must have at least one child sequence, so no check is needed here.
            // createGroupEnterPathElement initializes the group alternation index with 1, so we
            // don't have to increment it here, either.
            cur = group.getFirstAlternative();
            return deduplicatePath(true);
        } else {
            curPath.add(createPathElement(cur));
            if (cur.isPositionAssertion()) {
                final PositionAssertion assertion = (PositionAssertion) cur;
                switch (assertion.type) {
                    case CARET:
                        caretsOnPath++;
                        if (canTraverseCaret) {
                            return advanceTerm(assertion);
                        } else {
                            return retreat();
                        }
                    case DOLLAR:
                        dollarsOnPath++;
                        return advanceTerm((Term) cur);
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            } else if (canTraverseLookArounds() && cur.isLookAheadAssertion()) {
                enterLookAhead((LookAheadAssertion) cur);
                addLookAroundToVisitedSet();
                return advanceTerm((Term) cur);
            } else if (canTraverseLookArounds() && cur.isLookBehindAssertion()) {
                addLookAroundToVisitedSet();
                if (traversableLookBehindAssertions == null || traversableLookBehindAssertions.contains(cur)) {
                    return advanceTerm((LookBehindAssertion) cur);
                } else {
                    return retreat();
                }
            } else {
                assert cur.isCharacterClass() || cur.isBackReference() || cur.isMatchFound() || cur.isAtomicGroup() || (!canTraverseLookArounds() && cur.isLookAroundAssertion());
                if ((forward && dollarsOnPath() || !forward && caretsOnPath()) && cur.isCharacterClass()) {
                    // don't visit CharacterClass nodes if we traversed PositionAssertions already
                    return retreat();
                }
                return true;
            }
        }
    }

    /**
     * Advances past the given {@link Term} and updates {@link #cur the current node}.
     *
     * @return {@code true} if a successor was reached in this step (possible if
     *         {@link #advanceEmptyGuard} returns {@code true} and we have the quantified group as
     *         the successor)
     */
    private boolean advanceTerm(Term term) {
        if (ast.isNFAInitialState(term) || (term.getParent().isSubtreeRoot() && (term.isPositionAssertion() || term.isMatchFound()))) {
            assert term.isPositionAssertion() || term.isMatchFound();
            if (term.isPositionAssertion()) {
                cur = term.asPositionAssertion().getNext();
            } else {
                cur = term.asMatchFound().getNext();
            }
            return false;
        }
        Term curTerm = term;
        while (!curTerm.getParent().isSubtreeRoot()) {
            // We are leaving curTerm, which is a quantified group that we have already entered
            // during this step.
            // Unless we are building a DFA in a flavor which can have empty loop iterations, we
            // call into advanceEmptyGuard. This is crucial to preserve the termination of the AST
            // traversal/NFA generation. In the case of building a DFA in a flavor which can have
            // empty loop iterations:
            // a) we cannot use advanceEmptyGuard because it might introduce empty transitions,
            // which are forbidden in the DFA,
            // and b) termination is ensured by resolving exitZeroWidth/escapeZeroWidth guards
            // statically.
            if (curTerm.isGroupWithGuards() && insideEmptyGuardGroup.get(curTerm.asGroup().getGroupsWithGuardsIndex()) &&
                            !(ast.getOptions().getFlavor().canHaveEmptyLoopIterations() && isBuildingDFA())) {
                return advanceEmptyGuard(curTerm);
            }
            Sequence parentSeq = (Sequence) curTerm.getParent();
            if (curTerm == (forward ? parentSeq.getLastTerm() : parentSeq.getFirstTerm())) {
                final Group parentGroup = parentSeq.getParent();
                pushGroupExit(parentGroup);
                if (shouldRetreat) {
                    return retreat();
                }
                if (parentGroup.isLoop()) {
                    cur = parentGroup;
                    return false;
                }
                curTerm = parentGroup;
            } else {
                cur = parentSeq.getTerms().get(curTerm.getSeqIndex() + (forward ? 1 : -1));
                return false;
            }
        }
        assert curTerm.isGroup();
        assert curTerm.getParent().isSubtreeRoot();
        cur = curTerm.getSubTreeParent().getMatchFound();
        return false;
    }

    /**
     * Advances past a {@link Group} with an empty-guard. This can produce a transition to the
     * special empty-match state that is represented by setting the successor to the quantified
     * group.
     *
     * @return {@code true} if a successor (the quantified group) was reached in this step
     */
    private boolean advanceEmptyGuard(Term curTerm) {
        // We found a zero-width match group with a quantifier.
        // In flavors where we cannot have empty loop iterations (JavaScript), we generate
        // transitions to the special empty-match state only for bounded quantifiers which haven't
        // been unrolled. In flavors where we can have empty loop iterations, we generate
        // transitions to the empty-match state unconditionally. This ensures that we do not try to
        // generate NFA transitions that span multiple repetitions of the same quantified group,
        // potentially leading to non-terminating NFA generation.
        if (ast.getOptions().getFlavor().canHaveEmptyLoopIterations() ||
                        (curTerm.isQuantifiableTerm() && curTerm.asQuantifiableTerm().hasNotUnrolledQuantifier() && curTerm.asQuantifiableTerm().getQuantifier().getMin() > 0)) {
            assert curTerm.isGroup();
            // By returning the quantified group itself, we map the transition target to the special
            // empty-match state.
            cur = curTerm;
            return true;
        } else {
            return retreat();
        }
    }

    /**
     * Backtrack through the traversal and find an unexplored alternative.
     *
     * @return {@code true} if a successor was found in this step
     */
    private boolean retreat() {
        shouldRetreat = false;
        while (!curPath.isEmpty()) {
            long lastVisited = curPath.peek();
            RegexASTNode node = pathGetNode(lastVisited);
            if (pathIsGroup(lastVisited)) {
                Group group = (Group) node;
                if (pathIsGroupEnter(lastVisited) || pathIsGroupPassThrough(lastVisited)) {
                    if (pathGroupHasNext(lastVisited)) {
                        if (pathIsGroupPassThrough(lastVisited) && group.isLoop()) {
                            // a passthrough node was changed to an enter node,
                            // so we register the loop in insideLoops
                            registerInsideLoop(group);
                        }
                        switchNextGroupAlternative(group);
                        if (shouldRetreat) {
                            return retreat();
                        }
                        cur = pathGroupGetNext(lastVisited);
                        return deduplicatePath(true);
                    } else {
                        if (pathIsGroupEnter(lastVisited)) {
                            popGroupEnter();
                        } else {
                            assert pathIsGroupPassThrough(lastVisited);
                            popGroupPassThrough();
                        }
                        if (pathIsGroupEnter(lastVisited) && group.isLoop()) {
                            // we only deregister the node from insideLoops if this was an enter
                            // node, if it was a passthrough node, it was already deregistered when
                            // it was transformed from an enter node in doAdvance
                            unregisterInsideLoop(group);
                        }
                        if (group.hasEmptyGuard()) {
                            insideEmptyGuardGroup.clear(group.getGroupsWithGuardsIndex());
                        }
                    }
                } else if (ast.getOptions().getFlavor().failingEmptyChecksDontBacktrack() && pathIsGroupExit(lastVisited) && group.hasQuantifier() && group.getQuantifier().hasZeroWidthIndex()) {
                    // In Ruby, Python and OracleDB, when we finish an iteration of a loop, there is
                    // an empty check. If we pass the empty check, we return to the beginning of the
                    // loop where we get to make a non-deterministic choice whether we want to start
                    // another iteration of the loop (so far the same as ECMAScript). However, if we
                    // fail the empty check, we continue to the expression that follows the loop. We
                    // implement this by introducing two transitions, one leading to the start of
                    // the loop (empty check passes) and one escaping past the loop (empty check
                    // fails). The two transitions are then annotated with complementary guards
                    // (exitZeroWidth and escapeZeroWidth, respectively), so that at runtime, only
                    // one of the two transitions will be admissible. The clause below lets us
                    // generate the second transition by replacing the loop exit with a loop escape.
                    switchExitToEscape(group);
                    if (shouldRetreat) {
                        return retreat();
                    }
                    // When we expand quantifiers, we wrap them in a group. This lets us escape past
                    // the expansion of the quantifier even in cases when we are in the mandatory
                    // prefix (e.g. empty-check fails in the first A in (AA((A)((A)|)|))).
                    Group parentGroup = group.getParent().getParent().asGroup();
                    pushGroupExit(parentGroup);
                    return advanceTerm(parentGroup);
                } else {
                    if (pathIsGroupExit(lastVisited)) {
                        popGroupExit();
                    } else {
                        assert pathIsGroupEscape(lastVisited);
                        popGroupEscape(group);
                    }
                }
            } else {
                curPath.pop();
                if (canTraverseLookArounds() && node.isLookAroundAssertion()) {
                    if (node.isLookAheadAssertion()) {
                        leaveLookAhead(node.asLookAheadAssertion());
                    }
                    removeLookAroundFromVisitedSet(lastVisited);
                } else if (node.isDollar()) {
                    dollarsOnPath--;
                } else if (node.isCaret()) {
                    caretsOnPath--;
                }
            }
        }
        done = true;
        return false;
    }

    /**
     * This should be called whenever {@link #cur} is set to some {@link Sequence}.
     *
     * @return {@code true} if a successor was found in this step
     */
    private boolean deduplicatePath(boolean internal) {
        calcTransitionGuards();
        if (shouldRetreat) {
            return retreat();
        }
        // interal == true means that this is being called during traversal, before reaching a
        // successor node (these calls are made in regular intervals, whenever a new Sequence is
        // entered).
        // This method is also called for every successor we have found (internal == false). In
        // these cases, we can use a broader notion of state equivalence to prune more aggressively.
        assert internal == cur.isSequence();
        // In regex flavors where backreferences to unmatched capture groups always pass (i.e. they
        // behave as if the capture group matched the empty string), we don't have to distinguish
        // two states of the traversal that differ only in capture groups, since the state that was
        // encountered first will dominate the one found later and any empty capture groups that
        // would have been matched along the way cannot affect future matching.
        boolean captureGroupsMatter = !cur.isMatchFound() &&
                        ((ast.getOptions().getFlavor().backreferencesToUnmatchedGroupsFail() && ast.getProperties().hasBackReferences()) ||
                                        (isBuildingDFA() && ast.getProperties().hasConditionalBackReferences()));

        long id = cur.getId();
        if (caretsOnPath()) {
            id |= 1L << 32;
        }
        if (dollarsOnPath()) {
            id |= 1L << 33;
        }
        dedupKey.clear();
        dedupKey.add(id);
        dedupKey.addAll(lookAroundsOnPath.getInternalArray());
        if (internal) {
            dedupKey.addAll(insideEmptyGuardGroup.getInternalArray());
        }
        if (captureGroupsMatter) {
            dedupKeyAddGroupBoundaries(getCaptureGroupUpdates());
            dedupKeyAddGroupBoundaries(getCaptureGroupClears());
        }
        for (long guard : getQuantifierGuards()) {
            if (!TransitionGuard.is(guard, TransitionGuard.Kind.updateCG)) {
                dedupKey.add(guard);
            }
        }
        DeduplicationKey key = new DeduplicationKey(dedupKey.toArray());
        boolean isDuplicate = !pathDeduplicationSet.add(key);
        if (isDuplicate) {
            return retreat();
        } else {
            // When called in the middle of traversal (internal == true), we can return false, since
            // the current target node is a Sequence and these can never be successors.
            // When called at the end of traversal (internal == false), we can return true, since
            // the current target node is a valid successor.
            return !internal;
        }
    }

    private void dedupKeyAddGroupBoundaries(TBitSet boundaries) {
        long[] bitset = boundaries.getInternalArray();
        long[] referenced = referencedGroupBoundaries.getInternalArray();
        assert bitset.length == referenced.length;
        for (int i = 0; i < bitset.length; i++) {
            dedupKey.add(bitset[i] & referenced[i]);
        }
    }

    /**
     * First field: (short) group alternation index. This value is used to iterate the alternations
     * of groups referenced in a group-enter path element. <br>
     * Since the same group can appear multiple times on the path, we cannot reuse {@link Group}'s
     * implementation of {@link RegexASTVisitorIterable}. Therefore, every occurrence of a group on
     * the path has its own index for iterating and back-tracking over its alternatives.
     */
    private static final int PATH_GROUP_ALT_INDEX_OFFSET = 0;
    /**
     * Second field: (int) id of the path element's {@link RegexASTNode}.
     */
    private static final int PATH_NODE_OFFSET = Short.SIZE;
    /**
     * Third field: group action. Every path element referencing a group must have one of four
     * possible group actions:
     * <ul>
     * <li>group enter</li>
     * <li>group exit</li>
     * <li>group pass through</li>
     * <li>group escape</li>
     * </ul>
     */
    private static final int PATH_GROUP_ACTION_OFFSET = Short.SIZE + Integer.SIZE;
    private static final long PATH_GROUP_ACTION_ENTER = 1L << PATH_GROUP_ACTION_OFFSET;
    private static final long PATH_GROUP_ACTION_EXIT = 1L << PATH_GROUP_ACTION_OFFSET + 1;
    private static final long PATH_GROUP_ACTION_PASS_THROUGH = 1L << PATH_GROUP_ACTION_OFFSET + 2;
    private static final long PATH_GROUP_ACTION_ESCAPE = 1L << PATH_GROUP_ACTION_OFFSET + 3;
    private static final long PATH_GROUP_ACTION_ANY = PATH_GROUP_ACTION_ENTER | PATH_GROUP_ACTION_EXIT | PATH_GROUP_ACTION_PASS_THROUGH | PATH_GROUP_ACTION_ESCAPE;

    /**
     * Create a new path element containing the given node.
     */
    private static long createPathElement(RegexASTNode node) {
        return (long) node.getId() << PATH_NODE_OFFSET;
    }

    private static int pathGetNodeId(long pathElement) {
        return (int) (pathElement >>> PATH_NODE_OFFSET);
    }

    /**
     * Get the {@link RegexASTNode} contained in the given path element.
     */
    private RegexASTNode pathGetNode(long pathElement) {
        return ast.getState(pathGetNodeId(pathElement));
    }

    /**
     * Get the group alternation index of the given path element.
     */
    private static int pathGetGroupAltIndex(long pathElement) {
        return (short) (pathElement >>> PATH_GROUP_ALT_INDEX_OFFSET);
    }

    /**
     * Returns {@code true} if the given path element has any group action set. Every path element
     * containing a group must have one group action.
     */
    private static boolean pathIsGroup(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_ANY) != 0;
    }

    private static boolean pathIsGroupEnter(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_ENTER) != 0;
    }

    private static boolean pathIsGroupExit(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_EXIT) != 0;
    }

    private static boolean pathIsGroupPassThrough(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_PASS_THROUGH) != 0;
    }

    private static boolean pathIsGroupEscape(long pathElement) {
        return (pathElement & PATH_GROUP_ACTION_ESCAPE) != 0;
    }

    private static boolean pathIsGroupExitOrEscape(long pathElement) {
        return (pathElement & (PATH_GROUP_ACTION_EXIT | PATH_GROUP_ACTION_ESCAPE)) != 0;
    }

    /**
     * Returns {@code true} if the path element's group alternation index is still in bounds.
     */
    private boolean pathGroupHasNext(long pathElement) {
        return pathGetGroupAltIndex(pathElement) < ((Group) pathGetNode(pathElement)).size();
    }

    /**
     * Returns the next alternative of the group contained in this path element. Does not increment
     * the group alternation index!
     */
    private Sequence pathGroupGetNext(long pathElement) {
        return ((Group) pathGetNode(pathElement)).getAlternatives().get(pathGetGroupAltIndex(pathElement));
    }

    /// Pushing and popping group elements to and from the path
    private void pushGroupEnter(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET) | PATH_GROUP_ACTION_ENTER);
        recalcTransitionGuards = true;
    }

    private int popGroupEnter() {
        long pathEntry = curPath.pop();
        assert pathIsGroupEnter(pathEntry);
        recalcTransitionGuards = true;
        return pathGetGroupAltIndex(pathEntry);
    }

    private void switchNextGroupAlternative(Group group) {
        int groupAltIndex;
        if (pathIsGroupEnter(curPath.peek())) {
            groupAltIndex = popGroupEnter();
        } else {
            assert pathIsGroupPassThrough(curPath.peek());
            groupAltIndex = popGroupPassThrough();
        }
        pushGroupEnter(group, groupAltIndex + 1);
    }

    private void pushGroupExit(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_EXIT);
        recalcTransitionGuards = true;
    }

    private void popGroupExit() {
        long pathEntry = curPath.pop();
        assert pathIsGroupExit(pathEntry);
        recalcTransitionGuards = true;
    }

    private void pushGroupPassThrough(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_PASS_THROUGH | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET));
        recalcTransitionGuards = true;
    }

    private int popGroupPassThrough() {
        long pathEntry = curPath.pop();
        int groupAltIndex = pathGetGroupAltIndex(pathEntry);
        assert pathIsGroupPassThrough(pathEntry);
        recalcTransitionGuards = true;
        return groupAltIndex;
    }

    private void switchEnterToPassThrough(Group group) {
        int groupAltIndex = popGroupEnter();
        pushGroupPassThrough(group, groupAltIndex);
    }

    private void switchExitToEscape(Group group) {
        popGroupExit();
        pushGroupEscape(group);
    }

    private void pushGroupEscape(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_ESCAPE);
        recalcTransitionGuards = true;
    }

    private void popGroupEscape(Group group) {
        long pathEntry = curPath.pop();
        assert pathIsGroupEscape(pathEntry);
        recalcTransitionGuards = true;
    }

    /// Capture group data handling
    private void clearCaptureGroupData() {
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        lastGroup = -1;
    }

    private TBitSet getCaptureGroupUpdates() {
        calcTransitionGuards();
        return captureGroupUpdates;
    }

    private TBitSet getCaptureGroupClears() {
        calcTransitionGuards();
        return captureGroupClears;
    }

    private int getLastGroup() {
        calcTransitionGuards();
        return lastGroup;
    }

    private LongArrayBuffer getQuantifierGuards() {
        calcTransitionGuards();
        return transitionGuards;
    }

    private void calcTransitionGuards() {
        if (recalcTransitionGuards) {
            if (useQuantifierGuards()) {
                calculateTransitionGuards();
            } else {
                calculateGroupBoundaries();
            }
            recalcTransitionGuards = false;
        }
    }

    private void calculateGroupBoundaries() {
        clearCaptureGroupData();
        for (long element : curPath) {
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (pathIsGroupEnter(element)) {
                    calcGroupBoundariesEnter(group);
                } else if (pathIsGroupExitOrEscape(element)) {
                    calcGroupBoundariesExit(group);
                }
            }
        }
    }

    private int getBoundaryIndexStart(Group group) {
        return forward ? group.getBoundaryIndexStart() : group.getBoundaryIndexEnd();
    }

    private int getBoundaryIndexEnd(Group group) {
        return forward ? group.getBoundaryIndexEnd() : group.getBoundaryIndexStart();
    }

    private void calcGroupBoundariesEnter(Group group) {
        if (group.isCapturing()) {
            captureGroupUpdate(getBoundaryIndexStart(group));
        }
        if (!ast.getOptions().getFlavor().nestedCaptureGroupsKeptOnLoopReentry() && group.hasQuantifier() && group.hasEnclosedCaptureGroups()) {
            int lo = Group.groupNumberToBoundaryIndexStart(group.getEnclosedCaptureGroupsLow());
            int hi = Group.groupNumberToBoundaryIndexEnd(group.getEnclosedCaptureGroupsHigh() - 1);
            captureGroupClears.setRange(lo, hi);
            captureGroupUpdates.clearRange(lo, hi);
        }
    }

    private void calcGroupBoundariesExit(Group group) {
        if (group.isCapturing()) {
            captureGroupUpdate(getBoundaryIndexEnd(group));
            if (ast.getOptions().getFlavor().usesLastGroupResultField() && group.getGroupNumber() != 0) {
                lastGroup = group.getGroupNumber();
            }
        }
    }

    private void captureGroupUpdate(int boundary) {
        captureGroupUpdates.set(boundary);
        captureGroupClears.clear(boundary);
    }

    private void calculateTransitionGuards() {
        clearCaptureGroupData();
        boundedQuantifiersLoop.clear();
        boundedQuantifiersExited.clear();
        transitionGuards.clear();
        for (long element : curPath) {
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                int groupAltIndex = pathGetGroupAltIndex(element);
                if (pathIsGroupEnter(element)) {
                    if (group.hasQuantifier()) {
                        Quantifier quantifier = group.getQuantifier();
                        if (quantifier.hasIndex()) {
                            if (!quantifier.isInfiniteLoop() && boundedQuantifiersLoop.get(quantifier.getIndex()) && !boundedQuantifiersExited.get(quantifier.getIndex())) {
                                pushQuantifierGuard(TransitionGuard.createLoop(quantifier));
                            } else {
                                pushQuantifierGuard(TransitionGuard.createLoopInc(quantifier));
                            }
                        }
                        if (needsEmptyCheck(group)) {
                            pushQuantifierGuard(TransitionGuard.createEnterZeroWidth(quantifier));
                        }
                    }
                    if (needsUpdateCGStepByStep(group) && !captureGroupUpdates.get(getBoundaryIndexStart(group))) {
                        pushQuantifierGuard(TransitionGuard.createUpdateCG(getBoundaryIndexStart(group)));
                    }
                    calcGroupBoundariesEnter(group);
                    if (group.isConditionalBackReferenceGroup()) {
                        pushQuantifierGuard(getConditionalBackReferenceGroupQuantifierGuard(group, groupAltIndex));
                    }
                } else if (pathIsGroupExitOrEscape(element)) {
                    if (pathIsGroupExit(element)) {
                        if (group.hasQuantifier()) {
                            Quantifier quantifier = group.getQuantifier();
                            if (quantifier.hasIndex()) {
                                boundedQuantifiersLoop.set(quantifier.getIndex());
                            }
                            if (needsEmptyCheck(group)) {
                                pushQuantifierGuard(TransitionGuard.createExitZeroWidth(quantifier));
                            }
                        }
                        pushRecursiveBackrefUpdates(group);
                    } else if (pathIsGroupEscape(element)) {
                        if (group.hasQuantifier()) {
                            Quantifier quantifier = group.getQuantifier();
                            if (quantifier.hasIndex()) {
                                boundedQuantifiersExited.set(quantifier.getIndex());
                            }
                            if (quantifier.hasZeroWidthIndex()) {
                                pushQuantifierGuard(TransitionGuard.createEscapeZeroWidth(quantifier));
                            }
                        }
                    }
                    if (needsUpdateCGStepByStep(group) && !captureGroupUpdates.get(getBoundaryIndexEnd(group))) {
                        pushQuantifierGuard(TransitionGuard.createUpdateCG(getBoundaryIndexEnd(group)));
                    }
                    calcGroupBoundariesExit(group);
                } else if (pathIsGroupPassThrough(element)) {
                    Group quantifierGroup = getQuantifiedGroupFromPassthrough(group, groupAltIndex);
                    Quantifier quantifier = quantifierGroup.getQuantifier();
                    if (!quantifierGroup.isExpandedQuantifier()) {
                        if (quantifier.hasIndex()) {
                            if (quantifier.getMin() > 0) {
                                boundedQuantifiersExited.set(quantifier.getIndex());
                                pushQuantifierGuard(TransitionGuard.createExit(quantifier));
                            } else {
                                pushQuantifierGuard(TransitionGuard.createExitReset(quantifier));
                            }
                        } else {
                            assert quantifierGroup.isDead();
                            if (quantifier.getMin() > 0) {
                                shouldRetreat = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private static Group getQuantifiedGroupFromPassthrough(Group group, int groupAltIndex) {
        assert group.size() == 2 && groupAltIndex - 1 >= 0 && groupAltIndex - 1 <= 1;
        int otherAltIndex = (groupAltIndex - 1) ^ 1;
        Sequence otherAlternative = group.getAlternatives().get(otherAltIndex);
        assert !otherAlternative.isEmpty() && otherAlternative.get(0).isGroup();
        Group quantifierGroup = otherAlternative.get(0).asGroup();
        assert quantifierGroup.hasQuantifier();
        return quantifierGroup;
    }

    private boolean needsUpdateCGStepByStep(Group group) {
        return ast.getOptions().getFlavor().matchesTransitionsStepByStep() && group.isCapturing();
    }

    private boolean needsEmptyCheck(Group group) {
        assert group.hasQuantifier();
        return group.getQuantifier().hasZeroWidthIndex() && (ast.getOptions().getFlavor().emptyChecksOnMandatoryLoopIterations() || !group.isMandatoryUnrolledQuantifier());
    }

    private static long getConditionalBackReferenceGroupQuantifierGuard(Group group, int groupAltIndex) {
        assert group.isConditionalBackReferenceGroup();
        int referencedGroupNumber = group.asConditionalBackReferenceGroup().getReferencedGroupNumber();
        if (groupAltIndex == 1) {
            return TransitionGuard.createCheckGroupMatched(referencedGroupNumber);
        } else {
            assert groupAltIndex == 2;
            return TransitionGuard.createCheckGroupNotMatched(referencedGroupNumber);
        }
    }

    private void pushRecursiveBackrefUpdates(Group group) {
        if (ast.getOptions().getFlavor().supportsRecursiveBackreferences() && ast.getProperties().hasRecursiveBackReferences()) {
            if (group.isCapturing() && ast.isGroupRecursivelyReferenced(group.getGroupNumber())) {
                pushQuantifierGuard(TransitionGuard.createUpdateRecursiveBackref(group.getGroupNumber()));
            }
        }
    }

    /// Quantifier guard data handling
    private boolean useQuantifierGuards() {
        // In some flavors, we need to calculate quantifier guards even when building DFAs, since
        // these guards represent critical semantic details. While these guards would be ignored by
        // the DFA at runtime, they are all resolved statically during this traversal. This is
        // checked by ASTStepVisitor#noPredicatesInGuards.
        return !isBuildingDFA() || ast.getOptions().getFlavor().canHaveEmptyLoopIterations();
    }

    private void clearQuantifierGuards() {
        transitionGuards.clear();
    }

    private void pushQuantifierGuard(long guard) {
        assert useQuantifierGuards();
        // First, we check whether the guard can be resolved statically. If it is trivially true,
        // we ignore it (normalization). If it is impossible to satisfy, we backtrack.
        switch (TransitionGuard.getKind(guard)) {
            case exitZeroWidth:
            case escapeZeroWidth: {
                boolean keptAliveByConsumedInput = false;
                boolean keptAliveByCaptureGroups = false;
                if (!transitionGuards.isEmpty() && transitionGuards.peek() == guard) {
                    return;
                }
                // Search for the last enterZeroWidth guard of the same group.
                long enter = TransitionGuard.createEnterZeroWidthFromExit(guard);
                boolean enterFound = false;
                for (int i = transitionGuards.length() - 1; i >= 0; i--) {
                    long tg = transitionGuards.get(i);
                    if (tg == enter) {
                        enterFound = true;
                        break;
                    }
                    if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && TransitionGuard.is(tg, TransitionGuard.Kind.updateCG)) {
                        keptAliveByCaptureGroups = true;
                    }
                }
                if (!enterFound) {
                    // We did not find any corresponding enterZeroWidth, so exitZeroWidth will
                    // pass because of
                    // input being consumed.
                    keptAliveByConsumedInput = isBuildingDFA() || root.isCharacterClass();
                }
                boolean keptAlive = keptAliveByConsumedInput || keptAliveByCaptureGroups;
                if (isBuildingDFA()) {
                    // TODO: We should be able to eliminate some of these
                    // exitZeroWidth/escapeZeroWidth guards even
                    // when not building a DFA.
                    if ((TransitionGuard.is(guard, TransitionGuard.Kind.exitZeroWidth) && !keptAlive) || (TransitionGuard.is(guard, TransitionGuard.Kind.escapeZeroWidth) && keptAlive)) {
                        shouldRetreat = true;
                    }
                    return;
                }
                break;
            }
            case enterZeroWidth: {
                // If there is another enterZeroWidth for the same group in the quantifier guards
                // and there are no CG updates in between, then this new enterZeroWidth is
                // redundant.
                for (int i = transitionGuards.length() - 1; i >= 0; i--) {
                    long tg = transitionGuards.get(i);
                    if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && TransitionGuard.is(tg, TransitionGuard.Kind.updateCG)) {
                        break;
                    }
                    if (tg == guard) {
                        return;
                    }
                }
                break;
            }
            case checkGroupMatched:
            case checkGroupNotMatched: {
                assert (isBuildingDFA() && getMatchedConditionGroups() != null) == this instanceof ASTStepVisitor;
                if (isBuildingDFA() && getMatchedConditionGroups() != null) {
                    int referencedGroupNumber = TransitionGuard.getIndex(guard);
                    int groupEndIndex = Group.groupNumberToBoundaryIndexEnd(referencedGroupNumber);
                    boolean groupMatched = (getMatchedConditionGroups().get(referencedGroupNumber) && !captureGroupClears.get(groupEndIndex)) || captureGroupUpdates.get(groupEndIndex);
                    if ((TransitionGuard.is(guard, TransitionGuard.Kind.checkGroupMatched)) != groupMatched) {
                        shouldRetreat = true;
                    }
                    return;
                }
                break;
            }
        }
        transitionGuards.add(guard);
    }

    /// Visited set management
    private void addLookAroundToVisitedSet() {
        LookAroundAssertion la = (LookAroundAssertion) cur;
        lookAroundVisitiedCount[la.getGlobalSubTreeId()]++;
        lookAroundsOnPath.set(la.getGlobalSubTreeId());
    }

    private void removeLookAroundFromVisitedSet(long pathElement) {
        LookAroundAssertion la = (LookAroundAssertion) pathGetNode(pathElement);
        if (--lookAroundVisitiedCount[la.getGlobalSubTreeId()] == 0) {
            lookAroundsOnPath.clear(la.getGlobalSubTreeId());
        }
    }

    private boolean nodeVisitsEmpty() {
        for (int i : lookAroundVisitiedCount) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    /// insideLoops management
    private void registerInsideLoop(Group group) {
        if (!ast.getOptions().getFlavor().canHaveEmptyLoopIterations()) {
            insideLoops.add(group);
        }
    }

    private void unregisterInsideLoop(Group group) {
        if (!ast.getOptions().getFlavor().canHaveEmptyLoopIterations()) {
            insideLoops.remove(group);
        }
    }

    @SuppressWarnings("unused")
    private void dumpPath() {
        System.out.println("NEW PATH");
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (pathIsGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (pathIsGroupEnter(element)) {
                    System.out.printf("ENTER (%d)   %s%n", pathGetGroupAltIndex(element), group);
                } else if (pathIsGroupExit(element)) {
                    System.out.printf("EXIT        %s%n", group);
                } else if (pathIsGroupPassThrough(element)) {
                    System.out.printf("PASSTHROUGH %s%n", group);
                } else {
                    System.out.printf("ESCAPE      %s%n", group);
                }
            } else {
                System.out.printf("NODE        %s%n", pathGetNode(element));
            }
        }
    }

    private static final class DeduplicationKey {
        private final long[] key;
        private final int hashCode;

        DeduplicationKey(long[] key) {
            this.key = key;
            this.hashCode = Arrays.hashCode(key);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DeduplicationKey other)) {
                return false;
            }
            return Arrays.equals(key, other.key);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
