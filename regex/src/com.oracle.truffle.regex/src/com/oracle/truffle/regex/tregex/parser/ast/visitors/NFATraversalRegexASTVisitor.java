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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import java.util.Arrays;
import java.util.Set;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.ASTStepVisitor;
import com.oracle.truffle.regex.tregex.nfa.TransitionGuard;
import com.oracle.truffle.regex.tregex.parser.Token.Quantifier;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookAroundAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.flavors.RegexFlavor;
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
     * This set is needed to make sure that a quantified term cannot match the empty string, as is
     * specified in step 2a of RepeatMatcher from ECMAScript draft 2018, chapter 21.2.2.5.1.
     */
    private final TBitSet insideEmptyGuardGroup;
    private RegexASTNode cur;
    private Set<LookBehindAssertion> traversableLookBehindAssertions;
    private boolean canTraverseCaret = false;
    private boolean ignoreMatchBoundaryAssertions = false;
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
    private int matchBeginAssertionsOnPath = 0;
    private int matchEndAssertionsOnPath = 0;
    private final int[] lookAroundVisitedCount;
    private final TBitSet captureGroupUpdates;
    private final TBitSet captureGroupClears;
    private final TBitSet referencedGroupBoundaries;
    private int firstGroup = -1;
    private int lastGroup = -1;
    /**
     * Per-quantifier position of last quantified group exit or escape in the transition guards
     * array.
     */
    private final int[] bqLastCounterReset;
    /**
     * Per-quantifier position of last quantified group zero-width-enter guard in the transition
     * guards array.
     */
    private final int[] bqLastZeroWidthEnter;
    /**
     * Tracks whether a given quantifier has been exited "normally" on the current path.
     */
    private final TBitSet bqExited;
    /**
     * Tracks whether a given quantifier has been bypassed using either a group passthrough or a
     * group escape on the current path.
     */
    private final TBitSet bqBypassed;
    private final TBitSet referencedCaptureGroupsTmp;

    private final LongArrayBuffer transitionGuards = new LongArrayBuffer(8);
    private final LongArrayBuffer transitionGuardsCanonicalized = new LongArrayBuffer(8);
    private long[] transitionGuardsResult = null;

    protected NFATraversalRegexASTVisitor(RegexAST ast) {
        this.ast = ast;
        this.insideEmptyGuardGroup = new TBitSet(ast.getGroupsWithGuards().size());
        this.lookAroundsOnPath = new TBitSet(ast.getSubtrees().size());
        this.lookAroundVisitedCount = new int[ast.getSubtrees().size()];
        this.captureGroupUpdates = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.captureGroupClears = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.referencedGroupBoundaries = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.bqLastCounterReset = new int[ast.getQuantifierCount()];
        this.bqLastZeroWidthEnter = new int[ast.getGroupsWithGuards().size()];
        this.bqExited = new TBitSet(ast.getGroupsWithGuards().size());
        this.bqBypassed = new TBitSet(ast.getGroupsWithGuards().size());
        for (int i : ast.getReferencedGroups()) {
            referencedGroupBoundaries.set(Group.groupNumberToBoundaryIndexStart(i));
            referencedGroupBoundaries.set(Group.groupNumberToBoundaryIndexEnd(i));
        }
        this.referencedCaptureGroupsTmp = new TBitSet(ast.getNumberOfCaptureGroups());
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

    public void setIgnoreMatchBoundaryAssertions(boolean ignoreMatchBoundaryAssertions) {
        this.ignoreMatchBoundaryAssertions = ignoreMatchBoundaryAssertions;
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

    private void setShouldRetreat() {
        shouldRetreat = true;
    }

    protected RegexFlavor getFlavor() {
        return ast.getOptions().getFlavor();
    }

    protected abstract boolean isBuildingDFA();

    protected abstract boolean canPruneAfterUnconditionalFinalState();

    private boolean canTraverseLookArounds() {
        return isBuildingDFA();
    }

    protected void run(Term runRoot) {
        clearCaptureGroupData();
        recalcTransitionGuards = false;
        assert insideEmptyGuardGroup.isEmpty();
        assert curPath.isEmpty();
        assert dollarsOnPath == 0;
        assert caretsOnPath == 0;
        assert matchBeginAssertionsOnPath == 0;
        assert matchEndAssertionsOnPath == 0;
        assert lookAroundsOnPath.isEmpty();
        assert isEmpty(lookAroundVisitedCount) : Arrays.toString(lookAroundVisitedCount);
        assert !shouldRetreat;
        assert transitionGuards.isEmpty();
        assert captureGroupUpdates.isEmpty();
        assert captureGroupClears.isEmpty();
        assert firstGroup == -1;
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
            assert cur == pathGetNode(curPath.peek());
            visit(cur);
            if (canPruneAfterUnconditionalFinalState() && cur.isMatchFound() && !dollarsOnPath() && !caretsOnPath() && lookAroundsOnPath.isEmpty() && !hasTransitionGuards()) {
                /*
                 * Transitions after an unconditional final state transition will never be taken, so
                 * it is safe to prune them.
                 */
                insideEmptyGuardGroup.clear();
                curPath.clear();
                clearCaptureGroupData();
                clearTransitionGuards();
                transitionGuardsResult = null;
                matchBeginAssertionsOnPath = 0;
                matchEndAssertionsOnPath = 0;
                /*
                 * no need to clear nodeVisitedCount here, because !dollarsOnPath() &&
                 * lookAroundsOnPath.isEmpty() implies nodeVisitsEmpty()
                 */
                break;
            }
            transitionGuardsResult = null;
            retreat();
            foundNextTarget = false;
            // If we have back-tracked into an empty-match transition, then we must continue by
            // advancing past the empty-match group using advanceTerm instead of entering the group
            // again using doAdvance.
            if (cur.isGroup() && cur.hasEmptyGuard() && !done) {
                foundNextTarget = advanceTerm(cur.asGroup());
            }
        }
        clearTransitionGuards();
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

    protected boolean matchBeginAssertionsOnPath() {
        return matchBeginAssertionsOnPath > 0;
    }

    protected boolean matchEndAssertionsOnPath() {
        return matchEndAssertionsOnPath > 0;
    }

    protected boolean hasTransitionGuards() {
        calcTransitionGuardsResult();
        return transitionGuardsResult.length > 0;
    }

    protected long[] getTransitionGuardsOnPath() {
        calcTransitionGuardsResult();
        return transitionGuardsResult;
    }

    protected void calcTransitionGuardsResult() {
        if (transitionGuardsResult == null) {
            transitionGuardsResult = getTransitionGuards().isEmpty() ? TransitionGuard.NO_GUARDS : getTransitionGuards().toArray();
        }
    }

    protected GroupBoundaries getGroupBoundaries() {
        return ast.createGroupBoundaries(getCaptureGroupUpdates(), getCaptureGroupClears(), getFirstGroup(), getLastGroup());
    }

    /**
     * Advances the traversal by diving into {@link #cur current node} in pursuit of the next
     * successor.
     *
     * @return {@code true} if a successor was reached in this step
     */
    private boolean doAdvance() {
        if (cur.isDead()) {
            return retreat();
        }
        if (cur.isSequence()) {
            final Sequence sequence = (Sequence) cur;
            if (sequence.isEmpty()) {
                Group parent = sequence.getParent();
                if (sequence.isQuantifierPassThroughSequence()) {
                    // this empty sequence was inserted during quantifier expansion, so it is
                    // allowed to pass through the parent quantified group.
                    assert pathGetNode(curPath.peek()) == parent && PathElement.isGroupEnter(curPath.peek());
                    switchEnterToPassThrough(parent);
                } else {
                    pushGroupExit(parent);
                }
                if (shouldRetreat) {
                    return retreat();
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
            // This path will only be hit when visiting a group for the first time. All groups
            // must have at least one child sequence, so no check is needed here.
            // createGroupEnterPathElement initializes the group alternation index with 1, so we
            // don't have to increment it here, either.
            cur = group.getFirstAlternative();
            return deduplicatePath(true);
        } else {
            curPath.add(PathElement.create(cur));
            if (cur.isPositionAssertion()) {
                return advancePositionAssertion(cur.asPositionAssertion());
            } else if (cur.isLookAroundAssertion()) {
                return advanceLookAround(cur.asLookAroundAssertion());
            } else {
                assert cur.isCharacterClass() || cur.isBackReference() || cur.isMatchFound() || cur.isAtomicGroup();
                if ((forward && dollarsOnPath() || !forward && caretsOnPath()) && !canMatchEmptyString(cur)) {
                    return retreat();
                }
                if (!ignoreMatchBoundaryAssertions) {
                    if ((forward && matchBeginAssertionsOnPath() || !forward && matchEndAssertionsOnPath()) && !isRootEnterOnPath() && !canMatchEmptyString(cur)) {
                        return retreat();
                    }
                    if (matchEndAssertionsOnPath() && (cur.isCharacterClass() || cur.isBackReference())) {
                        return retreat();
                    }
                }
                return true;
            }
        }
    }

    private boolean advanceLookAround(LookAroundAssertion lookAround) {
        if (canTraverseLookArounds()) {
            if (lookAround.isLookAheadAssertion()) {
                enterLookAhead(lookAround.asLookAheadAssertion());
                addLookAroundToVisitedSet();
                return advanceTerm(lookAround);
            } else {
                assert lookAround.isLookBehindAssertion();
                addLookAroundToVisitedSet();
                if (traversableLookBehindAssertions == null || traversableLookBehindAssertions.contains(lookAround.asLookBehindAssertion())) {
                    return advanceTerm(lookAround);
                } else {
                    return retreat();
                }
            }
        }
        return true;
    }

    private boolean advancePositionAssertion(PositionAssertion assertion) {
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
                return advanceTerm(assertion);
            case MATCH_BEGIN:
                if (!ignoreMatchBoundaryAssertions) {
                    matchBeginAssertionsOnPath++;
                    if (forward && isBuildingDFA() && !isRootEnterOnPath()) {
                        return retreat();
                    }
                }
                return advanceTerm(assertion);
            case MATCH_END:
                if (!ignoreMatchBoundaryAssertions) {
                    matchEndAssertionsOnPath++;
                    if (!forward && isBuildingDFA() && !isRootEnterOnPath()) {
                        return retreat();
                    }
                }
                return advanceTerm(assertion);
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Advances past the given {@link Term} and updates {@link #cur the current node}.
     *
     * @return {@code true} if a successor was reached in this step (possible if we want to generate
     *         a transition to the special EMPTY_STATE, which by itself doesn't match anything, but
     *         acts as a helper for simulating the backtracking behavior of the ECMAScript flavor)
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
            /*
             * We are leaving curTerm, which is a quantified group that we have already entered
             * during this step.
             *
             * We avoid infinite loops on these groups by statically resolving TransitionGuards and
             * de-duplicating equivalent transitions, but we have to apply special treatment for
             * ECMAScript and Python's behavior on empty loop iterations here.
             *
             * ECMAScript and Python don't stop quantifier loops on empty matches as long as their
             * minimum count has not been reached. Unfortunately, we have to simulate this behavior
             * in cases where it is observable via capture groups, back-references or position
             * assertions.
             */
            if (curTerm.isGroupWithGuards() && insideEmptyGuardGroup.get(curTerm.asGroup().getGroupsWithGuardsIndex()) &&
                            !getFlavor().emptyChecksMonitorCaptureGroups()) {
                Group curGroup = curTerm.asGroup();
                Quantifier quantifier = curGroup.getQuantifier();
                // If we are:
                // - in ECMAScript or Python flavor
                // - in the mandatory split part of a quantifier
                // - that has not been unrolled
                // - and capture groups are visible to the caller, or the expression contains
                // back-references, or we crossed a caret
                if (!getFlavor().emptyChecksOnMandatoryLoopIterations() &&
                                curGroup.isMandatoryQuantifier() &&
                                !curGroup.isExpandedQuantifier() &&
                                (!ast.getOptions().isBooleanMatch() || ast.getProperties().hasBackReferences() || caretsOnPath())) {
                    // the existence of a mandatory copy of the quantifier loop implies a minimum
                    // greater than zero
                    assert !curGroup.isMandatoryQuantifier() || quantifier.getMin() > 0;
                    popGroupExit();
                    cur = curTerm;
                    // Set the current group node as the path's target to indicate we want to
                    // generate an EMPTY_STATE for it. The empty state allows the backtracking
                    // engine to loop without consuming characters.
                    curPath.add(PathElement.create(cur));
                    return true;
                }
                // otherwise, retreat.
                return retreat();
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
     * Backtrack through the traversal and find an unexplored alternative.
     *
     * @return {@code true} if a successor was found in this step
     */
    private boolean retreat() {
        shouldRetreat = false;
        while (!curPath.isEmpty()) {
            long lastElement = curPath.peek();
            RegexASTNode node = pathGetNode(lastElement);
            if (PathElement.isGroup(lastElement)) {
                Group group = (Group) node;
                if (PathElement.isGroupEnter(lastElement) || PathElement.isGroupPassThrough(lastElement)) {
                    if (pathGroupHasNext(lastElement)) {
                        switchNextGroupAlternative(group);
                        if (shouldRetreat) {
                            return retreat();
                        }
                        cur = pathGroupGetNext(lastElement);
                        return deduplicatePath(true);
                    } else {
                        if (PathElement.isGroupEnter(lastElement)) {
                            popGroupEnter();
                        } else {
                            assert PathElement.isGroupPassThrough(lastElement);
                            popGroupPassThrough();
                        }
                        if (group.hasEmptyGuard()) {
                            insideEmptyGuardGroup.clear(group.getGroupsWithGuardsIndex());
                        }
                    }
                } else if (PathElement.isGroupExit(lastElement) && needsZeroWidthEscape(group)) {
                    // In Ruby and OracleDB, when we finish an iteration of a loop, there is
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
                    // In ECMAScript, we use the same mechanism to fast-forward mandatory quantifier
                    // parts when we find a zero-width match for the quantified expression.
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
                    if (PathElement.isGroupExit(lastElement)) {
                        popGroupExit();
                    } else {
                        assert PathElement.isGroupEscape(lastElement);
                        popGroupEscape(group);
                    }
                }
            } else {
                curPath.pop();
                if (canTraverseLookArounds() && node.isLookAroundAssertion()) {
                    popLookAround(node, lastElement);
                } else if (node.isPositionAssertion()) {
                    popPositionAssertion(node);
                }
            }
        }
        done = true;
        return false;
    }

    private void popLookAround(RegexASTNode node, long pathElement) {
        if (node.isLookAheadAssertion()) {
            leaveLookAhead(node.asLookAheadAssertion());
        }
        removeLookAroundFromVisitedSet(pathElement);
    }

    private void popPositionAssertion(RegexASTNode node) {
        switch (node.asPositionAssertion().type) {
            case CARET -> {
                caretsOnPath--;
            }
            case DOLLAR -> {
                dollarsOnPath--;
            }
            case MATCH_BEGIN -> {
                if (!ignoreMatchBoundaryAssertions) {
                    matchBeginAssertionsOnPath--;
                }
            }
            case MATCH_END -> {
                if (!ignoreMatchBoundaryAssertions) {
                    matchEndAssertionsOnPath--;
                }
            }
        }
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
        if (internal && getFlavor().emptyChecksMonitorCaptureGroups()) {
            // in Ruby, we don't deduplicate on intermediate Sequence nodes, because due to the
            // "empty checks monitor capture groups" property, we may have to generate transitions
            // that represent multiple loop iterations in a quantified expression.
            return false;
        }
        // internal == true means that this is being called during traversal, before reaching a
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
                        ((getFlavor().backreferencesToUnmatchedGroupsFail() && ast.getProperties().hasBackReferences()) ||
                                        (isBuildingDFA() && ast.getProperties().hasConditionalBackReferences()));

        long id = cur.getId();
        if (caretsOnPath()) {
            id |= 1L << 32;
        }
        if (dollarsOnPath()) {
            id |= 1L << 33;
        }
        if (matchBeginAssertionsOnPath()) {
            id |= 1L << 34;
        }
        if (matchEndAssertionsOnPath()) {
            id |= 1L << 35;
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
        for (long guard : getTransitionGuards()) {
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
        // We only care about groups referenced by back-references when de-duplicating transitions.
        // Without back-references, the first possible transition to the same target always
        // dominates the others.
        long[] bitset = boundaries.getInternalArray();
        long[] referenced = referencedGroupBoundaries.getInternalArray();
        assert bitset.length == referenced.length;
        for (int i = 0; i < bitset.length; i++) {
            dedupKey.add(bitset[i] & referenced[i]);
        }
    }

    private static boolean canMatchEmptyString(RegexASTNode node) {
        if (node.isBackReference()) {
            return node.asBackReference().mayMatchEmptyString();
        }
        return !node.isCharacterClass();
    }

    /**
     * Get the {@link RegexASTNode} contained in the given path element.
     */
    private RegexASTNode pathGetNode(long pathElement) {
        return ast.getState(PathElement.getNodeId(pathElement));
    }

    /**
     * Returns {@code true} if the path element's group alternation index is still in bounds.
     */
    private boolean pathGroupHasNext(long pathElement) {
        return PathElement.getGroupAltIndex(pathElement) < ((Group) pathGetNode(pathElement)).size();
    }

    /**
     * Returns the next alternative of the group contained in this path element. Does not increment
     * the group alternation index!
     */
    private Sequence pathGroupGetNext(long pathElement) {
        return ((Group) pathGetNode(pathElement)).getAlternatives().get(PathElement.getGroupAltIndex(pathElement));
    }

    protected boolean isRootEnterOnPath() {
        return isGroupEnterOnPath(ast.getRoot());
    }

    private boolean isGroupEnterOnPath(Group group) {
        int groupNodeId = group.getId();
        for (long element : curPath) {
            if (PathElement.getNodeId(element) == groupNodeId && PathElement.isGroupEnter(element)) {
                return true;
            }
        }
        return false;
    }

    /// Pushing and popping group elements to and from the path
    private void pushGroupEnter(Group group, int groupAltIndex) {
        curPath.add(PathElement.createGroupEnter(group, groupAltIndex));
        recalcTransitionGuards = true;
    }

    private int popGroupEnter() {
        long pathEntry = curPath.pop();
        assert PathElement.isGroupEnter(pathEntry);
        recalcTransitionGuards = true;
        return PathElement.getGroupAltIndex(pathEntry);
    }

    private void switchNextGroupAlternative(Group group) {
        int groupAltIndex;
        if (PathElement.isGroupEnter(curPath.peek())) {
            groupAltIndex = popGroupEnter();
        } else {
            assert PathElement.isGroupPassThrough(curPath.peek());
            groupAltIndex = popGroupPassThrough();
        }
        pushGroupEnter(group, groupAltIndex + 1);
    }

    private void pushGroupExit(Group group) {
        curPath.add(PathElement.createGroupExit(group));
        recalcTransitionGuards = true;
    }

    private void popGroupExit() {
        long pathEntry = curPath.pop();
        assert PathElement.isGroupExit(pathEntry);
        recalcTransitionGuards = true;
    }

    private void pushGroupPassThrough(Group group, int groupAltIndex) {
        curPath.add(PathElement.createGroupPassThrough(group, groupAltIndex));
        recalcTransitionGuards = true;
    }

    private int popGroupPassThrough() {
        long pathEntry = curPath.pop();
        int groupAltIndex = PathElement.getGroupAltIndex(pathEntry);
        assert PathElement.isGroupPassThrough(pathEntry);
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
        long groupEscape = PathElement.createGroupEscape(group);
        curPath.add(groupEscape);
        recalcTransitionGuards = true;
    }

    private void popGroupEscape(Group group) {
        long pathEntry = curPath.pop();
        assert PathElement.isGroupEscape(pathEntry);
        assert group == pathGetNode(pathEntry);
        recalcTransitionGuards = true;
    }

    /// Capture group data handling
    private void clearCaptureGroupData() {
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        firstGroup = -1;
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

    private int getFirstGroup() {
        calcTransitionGuards();
        return firstGroup;
    }

    private int getLastGroup() {
        calcTransitionGuards();
        return lastGroup;
    }

    private LongArrayBuffer getTransitionGuards() {
        calcTransitionGuards();
        return transitionGuardsCanonicalized;
    }

    private void calcTransitionGuards() {
        if (recalcTransitionGuards) {
            calculateTransitionGuards();
            recalcTransitionGuards = false;
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
            if (updatesLastGroupField(group) && firstGroup == -1) {
                firstGroup = group.getGroupNumber();
            }
        }
        if (clearsEnclosedGroups(group)) {
            int lo = Group.groupNumberToBoundaryIndexStart(group.getEnclosedCaptureGroupsLo());
            int hi = Group.groupNumberToBoundaryIndexEnd(group.getEnclosedCaptureGroupsHi() - 1);
            captureGroupClears.setRange(lo, hi);
            captureGroupUpdates.clearRange(lo, hi);
        }
    }

    private void calcGroupBoundariesExit(Group group) {
        if (group.isCapturing()) {
            captureGroupUpdate(getBoundaryIndexEnd(group));
            if (updatesLastGroupField(group)) {
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
        bqExited.clear();
        bqBypassed.clear();
        Arrays.fill(bqLastZeroWidthEnter, -1);
        Arrays.fill(bqLastCounterReset, -1);
        transitionGuards.clear();
        transitionGuardsCanonicalized.clear();
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (PathElement.isGroup(element)) {
                Group group = (Group) pathGetNode(element);
                int groupAltIndex = PathElement.getGroupAltIndex(element);
                if (PathElement.isGroupEnter(element)) {
                    if (group.hasQuantifier()) {
                        Quantifier quantifier = group.getQuantifier();
                        if (quantifier.hasIndex()) {
                            if (bqExited.get(group.getGroupsWithGuardsIndex()) && !bqBypassed.get(group.getGroupsWithGuardsIndex())) {
                                if (group.isMandatoryQuantifier()) {
                                    pushTransitionGuard(TransitionGuard.createCountLtMin(quantifier));
                                } else if (!quantifier.isInfiniteLoop()) {
                                    pushTransitionGuard(TransitionGuard.createCountLtMax(quantifier));
                                }
                                pushTransitionGuard(TransitionGuard.createCountInc(quantifier));
                            } else {
                                if (group.isOptionalQuantifier()) {
                                    pushTransitionGuard(TransitionGuard.createCountSetMin(quantifier));
                                } else {
                                    pushTransitionGuard(TransitionGuard.createCountSet1(quantifier));
                                }
                            }
                        }
                        if (group.getEnclosedZeroWidthGroupsHi() - group.getEnclosedZeroWidthGroupsLo() > 0) {
                            bqBypassed.clearRange(group.getEnclosedZeroWidthGroupsLo(), group.getEnclosedZeroWidthGroupsHi() - 1);
                            bqExited.clearRange(group.getEnclosedZeroWidthGroupsLo(), group.getEnclosedZeroWidthGroupsHi() - 1);
                        }
                        if (needsEmptyCheck(group)) {
                            pushTransitionGuard(TransitionGuard.createEnterZeroWidth(quantifier));
                        }
                    }
                    if (needsUpdateCGStepByStep(group) && (getFlavor().usesLastGroupResultField() || !captureGroupUpdates.get(getBoundaryIndexStart(group)))) {
                        pushTransitionGuard(TransitionGuard.createUpdateCG(getBoundaryIndexStart(group)));
                    }
                    calcGroupBoundariesEnter(group);
                    if (group.isConditionalBackReferenceGroup()) {
                        pushTransitionGuard(getConditionalBackReferenceGroupTransitionGuard(group, groupAltIndex));
                    }
                } else if (PathElement.isGroupExitOrEscape(element)) {
                    if (PathElement.isGroupExit(element)) {
                        if (group.hasQuantifier()) {
                            Quantifier quantifier = group.getQuantifier();
                            if (quantifier.hasIndex()) {
                                if (!root.isGroup()) {
                                    bqLastCounterReset[quantifier.getIndex()] = transitionGuards.length();
                                }
                                bqExited.set(group.getGroupsWithGuardsIndex());
                            }
                            if (needsEmptyCheck(group)) {
                                pushTransitionGuard(TransitionGuard.createExitZeroWidth(quantifier));
                            }
                        }
                    } else if (PathElement.isGroupEscape(element)) {
                        if (group.hasQuantifier()) {
                            Quantifier quantifier = group.getQuantifier();
                            if (quantifier.hasIndex()) {
                                bqLastCounterReset[quantifier.getIndex()] = transitionGuards.length();
                                if (bqBypassed.get(group.getGroupsWithGuardsIndex())) {
                                    setShouldRetreat();
                                }
                                bqBypassed.set(group.getGroupsWithGuardsIndex());
                            }
                            if (quantifier.hasZeroWidthIndex()) {
                                pushTransitionGuard(TransitionGuard.createEscapeZeroWidth(quantifier));
                            }
                        }
                    }
                    pushRecursiveBackrefUpdates(group);
                    if (needsUpdateCGStepByStep(group) && (getFlavor().usesLastGroupResultField() || !captureGroupUpdates.get(getBoundaryIndexEnd(group)))) {
                        pushTransitionGuard(TransitionGuard.createUpdateCG(getBoundaryIndexEnd(group)));
                    }
                    calcGroupBoundariesExit(group);
                } else if (PathElement.isGroupPassThrough(element)) {
                    Group quantifierGroup = getQuantifiedGroupFromPassthrough(group, groupAltIndex);
                    Quantifier quantifier = quantifierGroup.getQuantifier();
                    if (!quantifierGroup.isExpandedQuantifier()) {
                        if (quantifierGroup.isDead()) {
                            if (quantifier.getMin() > 0) {
                                setShouldRetreat();
                            }
                        } else if (quantifier.hasIndex()) {
                            if (bqBypassed.get(quantifierGroup.getGroupsWithGuardsIndex())) {
                                setShouldRetreat();
                            }
                            bqBypassed.set(quantifierGroup.getGroupsWithGuardsIndex());
                            if (quantifierGroup.isMandatoryQuantifier() || quantifier.getMin() > 0 && !quantifierGroup.isOptionalQuantifier()) {
                                if (!bqExited.get(quantifierGroup.getGroupsWithGuardsIndex())) {
                                    setShouldRetreat();
                                }
                                if (quantifier.getMin() > 0) {
                                    pushTransitionGuard(TransitionGuard.createCountGeMin(quantifier));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < transitionGuards.length(); i++) {
            long guard = transitionGuards.get(i);
            if (shouldKeepGuard(guard, i)) {
                transitionGuardsCanonicalized.add(guard);
            }
        }
    }

    private boolean shouldKeepGuard(long guard, int guardPosition) {
        switch (TransitionGuard.getKind(guard)) {
            case countSet1, countInc, countSetMin -> {
                return getFlavor().emptyChecksMonitorCaptureGroups() || guardPosition >= bqLastCounterReset[TransitionGuard.getQuantifierIndex(guard)];
            }
            case enterZeroWidth -> {
                int zeroWidthQuantifierIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                Group quantifiedTerm = (Group) ast.getZeroWidthQuantifiables().get(zeroWidthQuantifierIndex);
                // we need to keep enterZeroWidth guards if the quantified expression can contain
                // NFA states that don't consume any characters, or the expression contains capture
                // groups referred to by back-references. In the case of referenced groups, the
                // guard is needed just to differentiate transitions in nested quantifiers,
                // because these may require additional backtracking, e.g. matching
                // /a(b*)*c\\1d/ against "abbbbcbbd"
                return bqLastZeroWidthEnter[zeroWidthQuantifierIndex] == guardPosition && (quantifiedTerm.hasCaret() ||
                                quantifiedTerm.hasLookArounds() ||
                                quantifiedTerm.hasBackReferences() ||
                                quantifiedTerm.hasAtomicGroups() ||
                                hasReferencedCaptureGroups(quantifiedTerm) ||
                                (cur.isGroup() && cur.asGroup().getQuantifier().getZeroWidthIndex() != zeroWidthQuantifierIndex));
            }
            case updateRecursiveBackrefPointer -> {
                for (int i = transitionGuards.length() - 1; i > guardPosition; i--) {
                    if (transitionGuards.get(i) == guard) {
                        return false;
                    }
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private boolean hasReferencedCaptureGroups(Group quantifiedTerm) {
        if (!ast.getProperties().hasBackReferences() || !quantifiedTerm.hasCaptureGroups()) {
            return false;
        }
        referencedCaptureGroupsTmp.clear();
        referencedCaptureGroupsTmp.setRange(quantifiedTerm.getCaptureGroupsLo(), quantifiedTerm.getCaptureGroupsHi() - 1);
        return !ast.getReferencedGroups().isDisjoint(referencedCaptureGroupsTmp);
    }

    private static Group getQuantifiedGroupFromPassthrough(Group group, int groupAltIndex) {
        assert group.size() == 2 && groupAltIndex - 1 >= 0 && groupAltIndex - 1 <= 1;
        int otherAltIndex = (groupAltIndex - 1) ^ 1;
        Sequence otherAlternative = group.getAlternatives().get(otherAltIndex);
        Term quantifiedTerm = group.isInLookBehindAssertion() ? otherAlternative.getLastTerm() : otherAlternative.getFirstTerm();
        assert !otherAlternative.isEmpty() && quantifiedTerm.isGroup();
        Group quantifierGroup = quantifiedTerm.asGroup();
        assert quantifierGroup.hasQuantifier();
        return quantifierGroup;
    }

    private boolean needsUpdateCGStepByStep(Group group) {
        return getFlavor().matchesTransitionsStepByStep() && group.isCapturing();
    }

    private boolean needsEmptyCheck(Group group) {
        assert group.hasQuantifier();
        return group.getQuantifier().hasZeroWidthIndex() && (getFlavor().emptyChecksOnMandatoryLoopIterations() || !group.isMandatoryUnrolledQuantifier());
    }

    private boolean needsZeroWidthEscape(Group group) {
        if (getFlavor().failingEmptyChecksDontBacktrack()) {
            return group.hasQuantifier() && group.getQuantifier().hasZeroWidthIndex();
        } else {
            return group.hasNotUnrolledQuantifier() && group.getQuantifier().hasZeroWidthIndex() && group.getQuantifier().getMin() > 0 && group.isMandatoryQuantifier();
        }
    }

    private boolean clearsEnclosedGroups(Group group) {
        return !getFlavor().nestedCaptureGroupsKeptOnLoopReentry() && group.hasQuantifier() && group.hasEnclosedCaptureGroups();
    }

    private boolean updatesLastGroupField(Group group) {
        return getFlavor().usesLastGroupResultField() && group.isCapturing() && group.getGroupNumber() != 0;
    }

    private static long getConditionalBackReferenceGroupTransitionGuard(Group group, int groupAltIndex) {
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
        if (getFlavor().supportsRecursiveBackreferences() && ast.getProperties().hasRecursiveBackReferences()) {
            if (group.isCapturing() && ast.isGroupRecursivelyReferenced(group.getGroupNumber())) {
                pushTransitionGuard(TransitionGuard.createUpdateRecursiveBackref(group.getGroupNumber()));
            }
        }
    }

    private void clearTransitionGuards() {
        transitionGuards.clear();
        transitionGuardsCanonicalized.clear();
    }

    private void pushTransitionGuard(long guard) {
        // First, we check whether the guard can be resolved statically. If it is trivially true,
        // we ignore it (normalization). If it is impossible to satisfy, we backtrack.
        switch (TransitionGuard.getKind(guard)) {
            case countSet1, countSetMin -> {
                bqLastCounterReset[TransitionGuard.getQuantifierIndex(guard)] = transitionGuards.length();
            }
            case countLtMin, countGeMin -> {
                if (canOmitCounterMinCheck(guard)) {
                    return;
                }
            }
            case countLtMax -> {
                if (canOmitCounterMaxCheck(guard)) {
                    return;
                }
            }
            case exitZeroWidth, escapeZeroWidth -> {
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
                    if (getFlavor().emptyChecksMonitorCaptureGroups() && TransitionGuard.is(tg, TransitionGuard.Kind.updateCG)) {
                        keptAliveByCaptureGroups = true;
                    }
                }
                if (!enterFound) {
                    // We did not find any corresponding enterZeroWidth, so exitZeroWidth will
                    // pass because of input being consumed.
                    keptAliveByConsumedInput = isBuildingDFA() || !canMatchEmptyString(root);
                }
                boolean keptAlive = keptAliveByConsumedInput || keptAliveByCaptureGroups;
                boolean isExit = TransitionGuard.is(guard, TransitionGuard.Kind.exitZeroWidth);
                boolean isEscape = TransitionGuard.is(guard, TransitionGuard.Kind.escapeZeroWidth);
                int zeroWidthQuantifierIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                if (isEscape) {
                    bqLastZeroWidthEnter[zeroWidthQuantifierIndex] = -1;
                }
                if ((isExit && !keptAlive) || (isEscape && keptAlive)) {
                    if (isBuildingDFA() || (isExit && enterFound) || !canMatchEmptyString(root) || root.isMatchFound()) {
                        setShouldRetreat();
                    }
                }
                if (isBuildingDFA() || !canMatchEmptyString(root) || root.isMatchFound() ||
                                (root.isGroup() && root.asGroup().getQuantifier().getZeroWidthIndex() == zeroWidthQuantifierIndex) ||
                                (isEscape && enterFound && !keptAliveByCaptureGroups)) {
                    return;
                }
            }
            case enterZeroWidth -> {
                int zeroWidthQuantifierIndex = TransitionGuard.getZeroWidthQuantifierIndex(guard);
                if (bqLastZeroWidthEnter[zeroWidthQuantifierIndex] < 0) {
                    bqLastZeroWidthEnter[zeroWidthQuantifierIndex] = transitionGuards.length();
                } else if (getFlavor().emptyChecksMonitorCaptureGroups()) {
                    // If there is another enterZeroWidth for the same group in the quantifier
                    // guards and there are no CG updates in between, then this new enterZeroWidth
                    // is redundant.
                    for (int i = transitionGuards.length() - 1; i >= bqLastZeroWidthEnter[zeroWidthQuantifierIndex]; i--) {
                        if (TransitionGuard.is(transitionGuards.get(i), TransitionGuard.Kind.updateCG)) {
                            bqLastZeroWidthEnter[zeroWidthQuantifierIndex] = transitionGuards.length();
                            break;
                        }
                    }
                }
            }
            case checkGroupMatched, checkGroupNotMatched -> {
                assert (isBuildingDFA() && getMatchedConditionGroups() != null) == this instanceof ASTStepVisitor;
                if (isBuildingDFA() && getMatchedConditionGroups() != null) {
                    int referencedGroupNumber = TransitionGuard.getGroupNumber(guard);
                    int groupEndIndex = Group.groupNumberToBoundaryIndexEnd(referencedGroupNumber);
                    boolean groupMatched = (getMatchedConditionGroups().get(referencedGroupNumber) && !captureGroupClears.get(groupEndIndex)) || captureGroupUpdates.get(groupEndIndex);
                    if ((TransitionGuard.is(guard, TransitionGuard.Kind.checkGroupMatched)) != groupMatched) {
                        setShouldRetreat();
                    }
                    return;
                }
            }
        }
        transitionGuards.add(guard);
    }

    private boolean canOmitCounterMinCheck(long guard) {
        assert TransitionGuard.is(guard, TransitionGuard.Kind.countLtMin) || TransitionGuard.is(guard, TransitionGuard.Kind.countGeMin);
        int quantifierIndex = TransitionGuard.getQuantifierIndex(guard);
        long countInc = TransitionGuard.createCountInc(quantifierIndex);
        long countSetMin = TransitionGuard.createCountSetMin(quantifierIndex);
        boolean isLtMin = TransitionGuard.is(guard, TransitionGuard.Kind.countLtMin);
        long inverseGuard = isLtMin ? TransitionGuard.createCountGeMin(quantifierIndex) : TransitionGuard.createCountLtMin(quantifierIndex);
        boolean omit = false;
        boolean existingIsSetMin = false;
        for (long existingGuard : transitionGuards) {
            if (existingGuard == countSetMin) {
                existingIsSetMin = true;
            }
            if (existingGuard == guard) {
                omit = true;
            }
            if (existingGuard == countInc && !(!isLtMin && existingIsSetMin)) {
                omit = false;
            }
            if (existingGuard == inverseGuard || isLtMin && existingIsSetMin) {
                setShouldRetreat();
                return true;
            }
        }
        return omit;
    }

    private boolean canOmitCounterMaxCheck(long guard) {
        assert TransitionGuard.is(guard, TransitionGuard.Kind.countLtMax);
        int quantifierIndex = TransitionGuard.getQuantifierIndex(guard);
        long countInc = TransitionGuard.createCountInc(quantifierIndex);
        boolean omit = false;
        for (long existingGuard : transitionGuards) {
            if (existingGuard == guard) {
                omit = true;
            }
            if (existingGuard == countInc) {
                omit = false;
            }
        }
        return omit;
    }

    /// Visited set management
    private void addLookAroundToVisitedSet() {
        LookAroundAssertion la = (LookAroundAssertion) cur;
        lookAroundVisitedCount[la.getGlobalSubTreeId()]++;
        lookAroundsOnPath.set(la.getGlobalSubTreeId());
    }

    private void removeLookAroundFromVisitedSet(long pathElement) {
        LookAroundAssertion la = (LookAroundAssertion) pathGetNode(pathElement);
        if (--lookAroundVisitedCount[la.getGlobalSubTreeId()] == 0) {
            lookAroundsOnPath.clear(la.getGlobalSubTreeId());
        }
    }

    private static boolean isEmpty(int[] array) {
        for (int i : array) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unused")
    private void dumpPath() {
        System.out.println("NEW PATH");
        for (int i = 0; i < curPath.length(); i++) {
            long element = curPath.get(i);
            if (PathElement.isGroup(element)) {
                Group group = (Group) pathGetNode(element);
                if (PathElement.isGroupEnter(element)) {
                    System.out.printf("ENTER (%2d)  %2d %s%n", PathElement.getGroupAltIndex(element), group.getId(), group);
                } else if (PathElement.isGroupExit(element)) {
                    System.out.printf("EXIT        %2d %s%n", group.getId(), group);
                } else if (PathElement.isGroupPassThrough(element)) {
                    System.out.printf("PASSTHROUGH %2d %s%n", group.getId(), group);
                } else {
                    System.out.printf("ESCAPE      %2d %s%n", group.getId(), group);
                }
            } else {
                System.out.printf("NODE        %2d %s%n", PathElement.getNodeId(element), pathGetNode(element));
            }
        }
    }

    @SuppressWarnings("unused")
    protected void dumpTransitionGuards(long[] guards) {
        for (long guard : guards) {
            System.out.println(TransitionGuard.toString(guard));
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

    private static final class PathElement {

        /**
         * First field: (short) group alternation index. This value is used to iterate the
         * alternations of groups referenced in a group-enter path element. <br>
         * Since the same group can appear multiple times on the path, we cannot reuse
         * {@link Group}'s implementation of {@link RegexASTVisitorIterable}. Therefore, every
         * occurrence of a group on the path has its own index for iterating and back-tracking over
         * its alternatives.
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
        private static final int GROUP_ACTION_OFFSET = Short.SIZE + Integer.SIZE;
        private static final long GROUP_ACTION_ENTER = 1L << GROUP_ACTION_OFFSET;
        private static final long GROUP_ACTION_EXIT = 1L << GROUP_ACTION_OFFSET + 1;
        private static final long GROUP_ACTION_PASS_THROUGH = 1L << GROUP_ACTION_OFFSET + 2;
        private static final long GROUP_ACTION_ESCAPE = 1L << GROUP_ACTION_OFFSET + 3;
        private static final long GROUP_ACTION_ANY = GROUP_ACTION_ENTER | GROUP_ACTION_EXIT | GROUP_ACTION_PASS_THROUGH | GROUP_ACTION_ESCAPE;

        /**
         * Create a new path element containing the given node.
         */
        private static long create(RegexASTNode node) {
            return (long) node.getId() << PATH_NODE_OFFSET;
        }

        private static long createGroupEnter(Group group, int groupAltIndex) {
            return create(group) | (groupAltIndex << PathElement.PATH_GROUP_ALT_INDEX_OFFSET) | PathElement.GROUP_ACTION_ENTER;
        }

        public static long createGroupPassThrough(Group group, int groupAltIndex) {
            return create(group) | (groupAltIndex << PathElement.PATH_GROUP_ALT_INDEX_OFFSET) | PathElement.GROUP_ACTION_PASS_THROUGH;
        }

        public static long createGroupExit(Group group) {
            return create(group) | PathElement.GROUP_ACTION_EXIT;
        }

        public static long createGroupEscape(Group group) {
            return create(group) | PathElement.GROUP_ACTION_ESCAPE;
        }

        private static int getNodeId(long pathElement) {
            return (int) (pathElement >>> PATH_NODE_OFFSET);
        }

        /**
         * Get the group alternation index of the given path element.
         */
        private static int getGroupAltIndex(long pathElement) {
            return (short) (pathElement >>> PATH_GROUP_ALT_INDEX_OFFSET);
        }

        /**
         * Returns {@code true} if the given path element has any group action set. Every path
         * element containing a group must have one group action.
         */
        private static boolean isGroup(long pathElement) {
            return (pathElement & GROUP_ACTION_ANY) != 0;
        }

        private static boolean isGroupEnter(long pathElement) {
            return (pathElement & GROUP_ACTION_ENTER) != 0;
        }

        private static boolean isGroupExit(long pathElement) {
            return (pathElement & GROUP_ACTION_EXIT) != 0;
        }

        private static boolean isGroupPassThrough(long pathElement) {
            return (pathElement & GROUP_ACTION_PASS_THROUGH) != 0;
        }

        private static boolean isGroupEscape(long pathElement) {
            return (pathElement & GROUP_ACTION_ESCAPE) != 0;
        }

        private static boolean isGroupExitOrEscape(long pathElement) {
            return (pathElement & (GROUP_ACTION_EXIT | GROUP_ACTION_ESCAPE)) != 0;
        }
    }
}
