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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.ASTStepVisitor;
import com.oracle.truffle.regex.tregex.nfa.QuantifierGuard;
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
 *
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
    private final StateSet<RegexAST, Group> insideLoops;
    /**
     * This set is needed to make sure that a quantified term cannot match the empty string, as is
     * specified in step 2a of RepeatMatcher from ECMAScript draft 2018, chapter 21.2.2.5.1.
     */
    private final StateSet<RegexAST, Group> insideEmptyGuardGroup;
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
     * <li>the {@link QuantifierGuard}s incurred by the traversal</li>
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

    private final StateSet<RegexAST, RegexASTNode> lookAroundsOnPath;
    private final StateSet<RegexAST, RegexASTNode> dollarsOnPath;
    private final StateSet<RegexAST, RegexASTNode> caretsOnPath;
    private final int[] nodeVisitCount;

    /**
     * This is a stack of all the changes that have been made to {@link #captureGroupUpdates},
     * {@link #captureGroupClears} and {@link #lastGroup} with enough metadata to backtrack on any
     * of them.
     */
    private final List<CaptureGroupEvent> captureGroupEvents = new ArrayList<>();
    private TBitSet captureGroupUpdates;
    private TBitSet captureGroupClears;
    private int lastGroup = -1;

    /**
     * Quantifier guards are stored in an immutable linked list, which allows for cheap sharing of
     * snapshots for the purposes of deduplication.
     */
    private QuantifierGuardsLinkedList quantifierGuards = null;
    private QuantifierGuard[] quantifierGuardsResult = null;
    private final int[] quantifierGuardsLoop;
    private final int[] quantifierGuardsExited;

    protected NFATraversalRegexASTVisitor(RegexAST ast) {
        this.ast = ast;
        this.insideLoops = StateSet.create(ast);
        this.insideEmptyGuardGroup = StateSet.create(ast);
        this.lookAroundsOnPath = StateSet.create(ast);
        this.dollarsOnPath = StateSet.create(ast);
        this.caretsOnPath = StateSet.create(ast);
        this.nodeVisitCount = new int[ast.getNumberOfStates()];
        this.captureGroupUpdates = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.captureGroupClears = new TBitSet(ast.getNumberOfCaptureGroups() * 2);
        this.quantifierGuardsLoop = new int[ast.getQuantifierCount().getCount()];
        this.quantifierGuardsExited = new int[ast.getQuantifierCount().getCount()];
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
            if (captureGroupClears.get(Group.groupNumberToBoundaryIndexEnd(conditionGroup))) {
                currentMatchedConditionGroups.clear(conditionGroup);
            }
            if (captureGroupUpdates.get(Group.groupNumberToBoundaryIndexEnd(conditionGroup))) {
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
        assert insideLoops.isEmpty();
        assert insideEmptyGuardGroup.isEmpty();
        assert curPath.isEmpty();
        assert dollarsOnPath.isEmpty();
        assert caretsOnPath.isEmpty();
        assert lookAroundsOnPath.isEmpty();
        assert nodeVisitsEmpty() : Arrays.toString(nodeVisitCount);
        assert !shouldRetreat;
        assert Arrays.stream(quantifierGuardsLoop).allMatch(x -> x == 0);
        assert Arrays.stream(quantifierGuardsExited).allMatch(x -> x == 0);
        assert quantifierGuards == null;
        assert captureGroupEvents.isEmpty();
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
        return !caretsOnPath.isEmpty();
    }

    protected boolean dollarsOnPath() {
        return !dollarsOnPath.isEmpty();
    }

    protected boolean hasQuantifierGuards() {
        calcQuantifierGuards();
        return quantifierGuardsResult.length > 0;
    }

    protected QuantifierGuard[] getQuantifierGuardsOnPath() {
        calcQuantifierGuards();
        return quantifierGuardsResult;
    }

    protected void calcQuantifierGuards() {
        if (quantifierGuardsResult == null) {
            assert useQuantifierGuards() || quantifierGuards == null;
            quantifierGuardsResult = quantifierGuards == null ? QuantifierGuard.NO_GUARDS : quantifierGuards.toArray();
            if (ast.getOptions().getFlavor().supportsRecursiveBackreferences()) {
                // Note: Updating the recursive back-reference boundaries before all other
                // quantifier guards causes back-references to empty matches to fail. This
                // is expected behavior in OracleDBFlavor.
                Arrays.sort(quantifierGuardsResult,
                                (x, y) -> Boolean.compare(x.getKind() != QuantifierGuard.Kind.updateRecursiveBackrefPointer, y.getKind() != QuantifierGuard.Kind.updateRecursiveBackrefPointer));
            }
        }
    }

    @TruffleBoundary
    protected PositionAssertion getLastDollarOnPath() {
        assert dollarsOnPath();
        for (int i = curPath.length() - 1; i >= 0; i--) {
            long element = curPath.get(i);
            if (pathGetNode(element).isDollar()) {
                return (PositionAssertion) pathGetNode(element);
            }
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    protected GroupBoundaries getGroupBoundaries() {
        return ast.createGroupBoundaries(captureGroupUpdates, captureGroupClears, lastGroup);
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
        if (cur.isDead() || (!ast.getOptions().getFlavor().canHaveEmptyLoopIterations() && insideLoops.contains(cur))) {
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
                    unregisterInsideLoop(parent);
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
                insideEmptyGuardGroup.add(group);
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
                        addToVisitedSet(caretsOnPath);
                        if (canTraverseCaret) {
                            return advanceTerm(assertion);
                        } else {
                            return retreat();
                        }
                    case DOLLAR:
                        addToVisitedSet(dollarsOnPath);
                        return advanceTerm((Term) cur);
                    default:
                        throw CompilerDirectives.shouldNotReachHere();
                }
            } else if (canTraverseLookArounds() && cur.isLookAheadAssertion()) {
                enterLookAhead((LookAheadAssertion) cur);
                addToVisitedSet(lookAroundsOnPath);
                return advanceTerm((Term) cur);
            } else if (canTraverseLookArounds() && cur.isLookBehindAssertion()) {
                addToVisitedSet(lookAroundsOnPath);
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
            if (insideEmptyGuardGroup.contains(curTerm) && !(ast.getOptions().getFlavor().canHaveEmptyLoopIterations() && isBuildingDFA())) {
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
                        if (pathIsGroupPassThrough(lastVisited)) {
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
                            popGroupPassThrough(group);
                        }
                        if (pathIsGroupEnter(lastVisited)) {
                            // we only deregister the node from insideLoops if this was an enter
                            // node, if it was a passthrough node, it was already deregistered when
                            // it was transformed from an enter node in doAdvance
                            unregisterInsideLoop(group);
                        }
                        insideEmptyGuardGroup.remove(group);
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
                        popGroupExit(group);
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
                    removeFromVisitedSet(lastVisited, lookAroundsOnPath);
                } else if (node.isDollar()) {
                    removeFromVisitedSet(lastVisited, dollarsOnPath);
                } else if (node.isCaret()) {
                    removeFromVisitedSet(lastVisited, caretsOnPath);
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
        boolean captureGroupsMatter = ast.getOptions().getFlavor().backreferencesToUnmatchedGroupsFail() || (isBuildingDFA() && ast.getProperties().hasConditionalBackReferences());
        DeduplicationKey key = new DeduplicationKey(cur,
                        lookAroundsOnPath,
                        caretsOnPath,
                        dollarsOnPath,
                        quantifierGuards,
                        internal ? insideEmptyGuardGroup : null,
                        captureGroupsMatter ? captureGroupUpdates : null,
                        captureGroupsMatter ? captureGroupClears : null,
                        captureGroupsMatter ? lastGroup : -1);
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
     * Third field: group action. Every path element referencing a group must have one of three
     * possible group actions:
     * <ul>
     * <li>group enter</li>
     * <li>group exit</li>
     * <li>group pass through</li>
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

    private static QuantifierGuard getConditionalBackReferenceGroupQuantifierGuard(Group group, int groupAltIndex) {
        assert group.isConditionalBackReferenceGroup();
        int referencedGroupNumber = group.asConditionalBackReferenceGroup().getReferencedGroupNumber();
        if (groupAltIndex == 1) {
            return QuantifierGuard.createCheckGroupMatched(referencedGroupNumber);
        } else {
            assert groupAltIndex == 2;
            return QuantifierGuard.createCheckGroupNotMatched(referencedGroupNumber);
        }
    }

    /// Pushing and popping group elements to and from the path
    private void pushGroupEnter(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET) | PATH_GROUP_ACTION_ENTER);
        // Capture groups
        if (group.isCapturing()) {
            captureGroupUpdate(forward ? group.getBoundaryIndexStart() : group.getBoundaryIndexEnd());
        }
        if (!ast.getOptions().getFlavor().nestedCaptureGroupsKeptOnLoopReentry() && group.hasQuantifier() && group.hasEnclosedCaptureGroups()) {
            int lo = Group.groupNumberToBoundaryIndexStart(group.getEnclosedCaptureGroupsLow());
            int hi = Group.groupNumberToBoundaryIndexEnd(group.getEnclosedCaptureGroupsHigh() - 1);
            captureGroupClear(lo, hi);
        }
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    if (quantifierGuardsLoop[quantifier.getIndex()] > 0 && quantifierGuardsExited[quantifier.getIndex()] == 0) {
                        pushQuantifierGuard(quantifier.isInfiniteLoop() ? QuantifierGuard.createLoopInc(quantifier) : QuantifierGuard.createLoop(quantifier));
                    } else {
                        pushQuantifierGuard(QuantifierGuard.createEnter(quantifier));
                    }
                }
                if (quantifier.hasZeroWidthIndex() && (ast.getOptions().getFlavor().emptyChecksOnMandatoryLoopIterations() || !group.isMandatoryUnrolledQuantifier())) {
                    pushQuantifierGuard(QuantifierGuard.createEnterZeroWidth(quantifier));
                }
            }
            if (ast.getOptions().getFlavor().matchesTransitionsStepByStep() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(forward ? group.getBoundaryIndexStart() : group.getBoundaryIndexEnd()));
            }
            if (group.isConditionalBackReferenceGroup()) {
                pushQuantifierGuard(getConditionalBackReferenceGroupQuantifierGuard(group, groupAltIndex));
            }
        }
    }

    private int popGroupEnter() {
        long pathEntry = curPath.pop();
        assert pathIsGroupEnter(pathEntry);
        if (useQuantifierGuards()) {
            popQuantifierGuards();
        }
        popCaptureGroupEvents();
        return pathGetGroupAltIndex(pathEntry);
    }

    private void switchNextGroupAlternative(Group group) {
        int groupAltIndex;
        if (pathIsGroupEnter(curPath.peek())) {
            groupAltIndex = popGroupEnter();
        } else {
            assert pathIsGroupPassThrough(curPath.peek());
            groupAltIndex = popGroupPassThrough(group);
        }
        pushGroupEnter(group, groupAltIndex + 1);
    }

    private void pushGroupExit(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_EXIT);
        // Capture groups
        if (group.isCapturing()) {
            captureGroupUpdate(forward ? group.getBoundaryIndexEnd() : group.getBoundaryIndexStart());
            if (ast.getOptions().getFlavor().usesLastGroupResultField() && group.getGroupNumber() != 0) {
                lastGroupUpdate(group.getGroupNumber());
            }
        }
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    quantifierGuardsLoop[quantifier.getIndex()]++;
                }
                if (quantifier.hasZeroWidthIndex() && (ast.getOptions().getFlavor().emptyChecksOnMandatoryLoopIterations() || !group.isMandatoryUnrolledQuantifier())) {
                    pushQuantifierGuard(QuantifierGuard.createExitZeroWidth(quantifier));
                }
            }
            pushRecursiveBackrefUpdates(group);
            if (ast.getOptions().getFlavor().matchesTransitionsStepByStep() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(forward ? group.getBoundaryIndexEnd() : group.getBoundaryIndexStart()));
            }
        }
    }

    private void popGroupExit(Group group) {
        long pathEntry = curPath.pop();
        assert pathIsGroupExit(pathEntry);
        if (useQuantifierGuards()) {
            popQuantifierGuards();
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    quantifierGuardsLoop[quantifier.getIndex()]--;
                }
            }
        }
        popCaptureGroupEvents();
    }

    private void pushRecursiveBackrefUpdates(Group group) {
        if (ast.getOptions().getFlavor().supportsRecursiveBackreferences() && ast.getProperties().hasRecursiveBackReferences()) {
            if (group.isCapturing() && ast.isGroupRecursivelyReferenced(group.getGroupNumber())) {
                pushQuantifierGuard(QuantifierGuard.createUpdateRecursiveBackref(group.getGroupNumber()));
            }
        }
    }

    private void pushGroupPassThrough(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_PASS_THROUGH | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET));
        if (useQuantifierGuards()) {
            assert group.size() == 2 && groupAltIndex - 1 >= 0 && groupAltIndex - 1 <= 1;
            int otherAltIndex = (groupAltIndex - 1) ^ 1;
            Sequence otherAlternative = group.getAlternatives().get(otherAltIndex);
            assert otherAlternative.size() >= 1 && otherAlternative.get(0).isGroup();
            Group quantifierGroup = otherAlternative.get(0).asGroup();
            assert quantifierGroup.hasQuantifier();
            Quantifier quantifier = quantifierGroup.getQuantifier();
            if (quantifier.hasIndex()) {
                if (quantifier.getMin() > 0) {
                    quantifierGuardsExited[quantifier.getIndex()]++;
                    pushQuantifierGuard(QuantifierGuard.createExit(quantifier));
                } else {
                    pushQuantifierGuard(QuantifierGuard.createClear(quantifier));
                }
            }
        }
    }

    private int popGroupPassThrough(Group group) {
        long pathEntry = curPath.pop();
        int groupAltIndex = pathGetGroupAltIndex(pathEntry);
        assert pathIsGroupPassThrough(pathEntry);
        if (useQuantifierGuards()) {
            popQuantifierGuards();
            assert group.size() == 2 && groupAltIndex - 1 >= 0 && groupAltIndex - 1 <= 1;
            int otherAltIndex = (groupAltIndex - 1) ^ 1;
            Sequence otherAlternative = group.getAlternatives().get(otherAltIndex);
            assert otherAlternative.size() >= 1 && otherAlternative.get(0).isGroup();
            Group quantifierGroup = otherAlternative.get(0).asGroup();
            assert quantifierGroup.hasQuantifier();
            Quantifier quantifier = quantifierGroup.getQuantifier();
            if (quantifier.hasIndex() && quantifier.getMin() > 0) {
                quantifierGuardsExited[quantifier.getIndex()]--;
            }
        }
        return groupAltIndex;
    }

    private void switchEnterToPassThrough(Group group) {
        int groupAltIndex = popGroupEnter();
        pushGroupPassThrough(group, groupAltIndex);
    }

    private void switchExitToEscape(Group group) {
        popGroupExit(group);
        pushGroupEscape(group);
    }

    private void pushGroupEscape(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_ESCAPE);
        // Capture groups
        if (group.isCapturing()) {
            captureGroupUpdate(forward ? group.getBoundaryIndexEnd() : group.getBoundaryIndexStart());
            if (ast.getOptions().getFlavor().usesLastGroupResultField() && group.getGroupNumber() != 0) {
                lastGroupUpdate(group.getGroupNumber());
            }
        }
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    quantifierGuardsExited[quantifier.getIndex()]++;
                }
                if (quantifier.hasZeroWidthIndex()) {
                    pushQuantifierGuard(QuantifierGuard.createEscapeZeroWidth(quantifier));
                }
            }
            if (ast.getOptions().getFlavor().matchesTransitionsStepByStep() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(forward ? group.getBoundaryIndexEnd() : group.getBoundaryIndexStart()));
            }
        }
    }

    private void popGroupEscape(Group group) {
        long pathEntry = curPath.pop();
        assert pathIsGroupEscape(pathEntry);
        if (useQuantifierGuards()) {
            popQuantifierGuards();
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    quantifierGuardsExited[quantifier.getIndex()]--;
                }
            }
        }
        popCaptureGroupEvents();
    }

    /// Capture group data handling
    private void clearCaptureGroupData() {
        captureGroupEvents.clear();
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        lastGroup = -1;
    }

    private void captureGroupUpdate(int boundary) {
        captureGroupEvents.add(new CaptureGroupEvent.CaptureGroupUpdate(curPath.length(), boundary, captureGroupUpdates.get(boundary), captureGroupClears.get(boundary)));
        captureGroupUpdates.set(boundary);
        captureGroupClears.clear(boundary);
    }

    private void captureGroupClear(int low, int high) {
        captureGroupEvents.add(new CaptureGroupEvent.CaptureGroupClears(curPath.length(), captureGroupUpdates.copy(), captureGroupClears.copy()));
        captureGroupClears.setRange(low, high);
        captureGroupUpdates.clearRange(low, high);
    }

    private void lastGroupUpdate(int newLastGroup) {
        captureGroupEvents.add(new CaptureGroupEvent.LastGroupUpdate(curPath.length(), lastGroup));
        lastGroup = newLastGroup;
    }

    private void popCaptureGroupEvents() {
        for (int i = captureGroupEvents.size() - 1; i >= 0; i--) {
            if (captureGroupEvents.get(i).getPathDepth() > curPath.length()) {
                CaptureGroupEvent poppedEvent = captureGroupEvents.remove(i);
                poppedEvent.undo(this);
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
        quantifierGuards = null;
    }

    private void pushQuantifierGuard(QuantifierGuard guard) {
        assert useQuantifierGuards();
        // First, we check whether the guard can be resolved statically. If it is trivially true,
        // we ignore it (normalization). If it is impossible to satisfy, we backtrack.
        switch (guard.getKind()) {
            case updateCG: {
                QuantifierGuardsLinkedList curGuard = quantifierGuards;
                while (curGuard != null) {
                    if (curGuard.getGuard().equals(guard)) {
                        // redundant updateCG
                        return;
                    }
                    curGuard = curGuard.getPrev();
                }
                break;
            }
            case exitZeroWidth:
            case escapeZeroWidth: {
                boolean keptAliveByConsumedInput = false;
                boolean keptAliveByCaptureGroups = false;
                // Search for the last enterZeroWidth guard of the same group.
                QuantifierGuardsLinkedList curGuard = quantifierGuards;
                while (curGuard != null && (!(curGuard.getGuard().getKind() == QuantifierGuard.Kind.enterZeroWidth &&
                                curGuard.getGuard().getQuantifier().getZeroWidthIndex() == guard.getQuantifier().getZeroWidthIndex()))) {
                    if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && curGuard.getGuard().getKind() == QuantifierGuard.Kind.updateCG) {
                        keptAliveByCaptureGroups = true;
                    }
                    curGuard = curGuard.getPrev();
                }
                if (curGuard == null) {
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
                    if ((guard.getKind() == QuantifierGuard.Kind.exitZeroWidth && !keptAlive) || (guard.getKind() == QuantifierGuard.Kind.escapeZeroWidth && keptAlive)) {
                        shouldRetreat = true;
                    }
                    return;
                }
                break;
            }
            case enterZeroWidth: {
                // If there is another enterZeroWidth for the same group in the quantifier guards
                // and there are no CG
                // updates in between, then this new enterZeroWidth is redundant.
                QuantifierGuardsLinkedList curGuard = quantifierGuards;
                while (curGuard != null && (!ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() || curGuard.getGuard().getKind() != QuantifierGuard.Kind.updateCG)) {
                    if (curGuard.getGuard().getKind() == QuantifierGuard.Kind.enterZeroWidth && curGuard.getGuard().getQuantifier().getZeroWidthIndex() == guard.getQuantifier().getZeroWidthIndex()) {
                        return;
                    }
                    curGuard = curGuard.getPrev();
                }
                break;
            }
            case checkGroupMatched:
            case checkGroupNotMatched: {
                assert (isBuildingDFA() && getMatchedConditionGroups() != null) == this instanceof ASTStepVisitor;
                if (isBuildingDFA() && getMatchedConditionGroups() != null) {
                    int referencedGroupNumber = guard.getIndex();
                    boolean groupMatched = (getMatchedConditionGroups().get(referencedGroupNumber) && !captureGroupClears.get(Group.groupNumberToBoundaryIndexEnd(referencedGroupNumber))) ||
                                    captureGroupUpdates.get(Group.groupNumberToBoundaryIndexEnd(referencedGroupNumber));
                    if ((guard.getKind() == QuantifierGuard.Kind.checkGroupMatched) != groupMatched) {
                        shouldRetreat = true;
                    }
                    return;
                }
                break;
            }
        }
        quantifierGuards = new QuantifierGuardsLinkedList(curPath.length(), guard, quantifierGuards);
    }

    private void popQuantifierGuard() {
        assert quantifierGuards != null;
        quantifierGuards = quantifierGuards.getPrev();
    }

    private void popQuantifierGuards() {
        assert useQuantifierGuards();
        while (quantifierGuards != null && quantifierGuards.getPathDepth() > curPath.length()) {
            popQuantifierGuard();
        }
    }

    /// Visited set management
    private void addToVisitedSet(StateSet<RegexAST, RegexASTNode> visitedSet) {
        nodeVisitCount[cur.getId()]++;
        visitedSet.add(cur);
    }

    private void removeFromVisitedSet(long pathElement, StateSet<RegexAST, RegexASTNode> visitedSet) {
        if (--nodeVisitCount[pathGetNodeId(pathElement)] == 0) {
            visitedSet.remove(pathGetNode(pathElement));
        }
    }

    private boolean nodeVisitsEmpty() {
        for (int i : nodeVisitCount) {
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
                    System.out.println(String.format("ENTER (%d)   %s", pathGetGroupAltIndex(element), group));
                } else if (pathIsGroupExit(element)) {
                    System.out.println(String.format("EXIT        %s", group));
                } else if (pathIsGroupPassThrough(element)) {
                    System.out.println(String.format("PASSTHROUGH %s", group));
                } else {
                    System.out.println(String.format("ESCAPE      %s", group));
                }
            } else {
                System.out.println(String.format("NODE        %s", pathGetNode(element)));
            }
        }
    }

    private static final class DeduplicationKey {
        private final StateSet<RegexAST, RegexASTNode> nodesInvolved;
        private final QuantifierGuardsLinkedList quantifierGuards;
        private final StateSet<RegexAST, Group> insideEmptyGuardGroup;
        private final TBitSet captureGroupUpdates;
        private final TBitSet captureGroupClears;
        private final int lastGroup;
        private final int hashCode;

        DeduplicationKey(RegexASTNode targetNode, StateSet<RegexAST, RegexASTNode> lookAroundsOnPath, StateSet<RegexAST, RegexASTNode> caretsOnPath, StateSet<RegexAST, RegexASTNode> dollarsOnPath,
                        QuantifierGuardsLinkedList quantifierGuards, StateSet<RegexAST, Group> insideEmptyGuardGroup, TBitSet captureGroupUpdates, TBitSet captureGroupClears, int lastGroup) {
            this.nodesInvolved = lookAroundsOnPath.copy();
            this.nodesInvolved.addAll(caretsOnPath);
            this.nodesInvolved.addAll(dollarsOnPath);
            this.nodesInvolved.add(targetNode);
            this.quantifierGuards = quantifierGuards;
            this.insideEmptyGuardGroup = insideEmptyGuardGroup == null ? null : insideEmptyGuardGroup.copy();
            this.captureGroupUpdates = captureGroupUpdates == null ? null : captureGroupUpdates.copy();
            this.captureGroupClears = captureGroupClears == null ? null : captureGroupClears.copy();
            this.lastGroup = lastGroup;
            this.hashCode = Objects.hash(nodesInvolved, quantifierGuards, insideEmptyGuardGroup, captureGroupUpdates, captureGroupClears, lastGroup);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof DeduplicationKey other)) {
                return false;
            }
            return this.nodesInvolved.equals(other.nodesInvolved) && Objects.equals(this.quantifierGuards, other.quantifierGuards) &&
                            Objects.equals(insideEmptyGuardGroup, other.insideEmptyGuardGroup) && Objects.equals(captureGroupUpdates, other.captureGroupUpdates) &&
                            Objects.equals(captureGroupClears, other.captureGroupClears) && this.lastGroup == other.lastGroup;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class QuantifierGuardsLinkedList {

        private final int pathDepth;
        private final QuantifierGuard guard;
        private final QuantifierGuardsLinkedList prev;
        private final int length;
        private final int hashCode;

        QuantifierGuardsLinkedList(int pathDepth, QuantifierGuard guard, QuantifierGuardsLinkedList prev) {
            this.pathDepth = pathDepth;
            this.guard = guard;
            this.prev = prev;
            this.length = prev == null ? 1 : prev.length + 1;
            this.hashCode = guard.hashCode() + 31 * (prev == null ? 0 : prev.hashCode);
        }

        public int getPathDepth() {
            return pathDepth;
        }

        public QuantifierGuardsLinkedList getPrev() {
            return prev;
        }

        public QuantifierGuard getGuard() {
            return guard;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof QuantifierGuardsLinkedList)) {
                return false;
            }
            QuantifierGuardsLinkedList other = (QuantifierGuardsLinkedList) obj;
            return this.hashCode == other.hashCode && this.length == other.length && this.guard.equals(other.guard) && (prev == null || prev.equals(other.prev));
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        public QuantifierGuard[] toArray() {
            QuantifierGuard[] result = new QuantifierGuard[getLength()];
            QuantifierGuardsLinkedList cur = this;
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = cur.getGuard();
                cur = cur.getPrev();
            }
            return result;
        }
    }

    private abstract static class CaptureGroupEvent {

        private final int pathDepth;

        protected CaptureGroupEvent(int pathDepth) {
            this.pathDepth = pathDepth;
        }

        public final int getPathDepth() {
            return pathDepth;
        }

        public abstract void undo(NFATraversalRegexASTVisitor visitor);

        private static final class CaptureGroupUpdate extends CaptureGroupEvent {

            private final int boundary;
            private final boolean prevUpdate;
            private final boolean prevClear;

            CaptureGroupUpdate(int pathDepth, int boundary, boolean prevUpdate, boolean prevClear) {
                super(pathDepth);
                this.boundary = boundary;
                this.prevUpdate = prevUpdate;
                this.prevClear = prevClear;
            }

            @Override
            public void undo(NFATraversalRegexASTVisitor visitor) {
                if (prevUpdate) {
                    visitor.captureGroupUpdates.set(boundary);
                } else {
                    visitor.captureGroupUpdates.clear(boundary);
                }
                if (prevClear) {
                    visitor.captureGroupClears.set(boundary);
                } else {
                    visitor.captureGroupClears.clear(boundary);
                }
            }
        }

        private static final class CaptureGroupClears extends CaptureGroupEvent {

            private final TBitSet prevUpdates;
            private final TBitSet prevClears;

            CaptureGroupClears(int pathDepth, TBitSet prevUpdates, TBitSet prevClears) {
                super(pathDepth);
                this.prevUpdates = prevUpdates;
                this.prevClears = prevClears;
            }

            @Override
            public void undo(NFATraversalRegexASTVisitor visitor) {
                visitor.captureGroupUpdates = prevUpdates;
                visitor.captureGroupClears = prevClears;
            }
        }

        private static final class LastGroupUpdate extends CaptureGroupEvent {

            private final int prevLastGroup;

            LastGroupUpdate(int pathDepth, int prevLastGroup) {
                super(pathDepth);
                this.prevLastGroup = prevLastGroup;
            }

            @Override
            public void undo(NFATraversalRegexASTVisitor visitor) {
                visitor.lastGroup = prevLastGroup;
            }
        }
    }
}
