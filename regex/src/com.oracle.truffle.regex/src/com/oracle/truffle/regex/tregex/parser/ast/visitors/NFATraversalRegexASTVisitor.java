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
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
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
import org.graalvm.collections.EconomicSet;

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
 * and a special <em>pass-through</em> node for empty sequences of {@link Group}s marked with
 * {@link Group#isExpandedQuantifier()}. Furthermore, the visitor will not descend into lookaround
 * assertions, it will jump over them and just add their corresponding {@link LookAheadAssertion} or
 * {@link LookBehindAssertion} node to the path.
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
     * insideLoops is the multiset of looping groups that we are currently inside of. We need to
     * maintain this in order to detect infinite loops in the NFA traversal. If we enter a looping
     * group, traverse it without encountering a CharacterClass node or a MatchFound node and arrive
     * back at the same group, then we are bound to loop like this forever. Using insideLoops, we
     * can detect this situation and proceed with the search using another alternative. For example,
     * in the RegexAST {@code ((|[a])*|)*}, which corresponds to the regex {@code /(a*?)* /}, we can
     * traverse the inner loop, {@code (|[a])*}, without hitting any CharacterClass node by choosing
     * the first alternative and we will then arrive back at the outer loop. There, we detect an
     * infinite loop, which causes us to backtrack and choose the second alternative in the inner
     * loop, leading us to the CharacterClass node {@code [a]}. <br>
     * This is a multiset, because in some cases, we want to admit one empty traversal through a
     * loop, but still disallow any further iterations to prevent infinite loops. The value stored
     * in this map tells us how many times we have entered the current search. <br>
     * NB: For every looping group, this map tells us how many {@code enter} nodes there are on the
     * current path.
     */
    private final EconomicMap<RegexASTNode, Integer> insideLoops;
    /**
     * This set is needed to make sure that a quantified term cannot match the empty string, as is
     * specified in step 2a of RepeatMatcher from ECMAScript draft 2018, chapter 21.2.2.5.1.
     */
    private final StateSet<RegexAST, Group> insideEmptyGuardGroup;
    private RegexASTNode cur;
    private Set<LookBehindAssertion> traversableLookBehindAssertions;
    private boolean canTraverseCaret = false;
    private boolean forward = true;
    private boolean done = false;

    /**
     * The exhaustive path traversal may result in some duplicate successors, e.g. on a regex like
     * {@code /(a?|b?)(a?|b?)/}. In order to avoid a combinatorial explosion of duplicate
     * successors, we prune the search for successors whenever we enter into a state that is
     * equivalent to one visited before. In order for two traversal states to be considered
     * equivalent, they must be equal in the following parameters:
     * <ul>
     * <li>the {@link #cur current node}</li>
     * <li>the set of traversed {@link PositionAssertion dollar-assertions}</li>
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
        this.insideLoops = EconomicMap.create();
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

    public void setReverse() {
        this.forward = false;
    }

    protected abstract boolean canTraverseLookArounds();

    protected void run(Term runRoot) {
        assert insideLoops.isEmpty();
        assert insideEmptyGuardGroup.isEmpty();
        assert curPath.isEmpty();
        assert dollarsOnPath.isEmpty();
        assert caretsOnPath.isEmpty();
        assert lookAroundsOnPath.isEmpty();
        assert nodeVisitsEmpty() : Arrays.toString(nodeVisitCount);
        assert Arrays.stream(quantifierGuardsLoop).allMatch(x -> x == 0);
        assert Arrays.stream(quantifierGuardsExited).allMatch(x -> x == 0);
        assert quantifierGuards == null;
        assert captureGroupEvents.isEmpty();
        assert captureGroupUpdates.isEmpty();
        assert captureGroupClears.isEmpty();
        assert lastGroup == -1;
        root = runRoot;
        if (useQuantifierGuards()) {
            if (root.isGroup() && !root.getParent().isSubtreeRoot()) {
                Group emptyMatch = root.getParent().getParent().asGroup();
                pushQuantifierGuard(QuantifierGuard.createExitEmptyMatch(emptyMatch.getQuantifier()));
            }
        }
        pathDeduplicationSet.clear();
        if (runRoot.isGroup() && runRoot.getParent().isSubtreeRoot()) {
            cur = runRoot;
        } else {
            advanceTerm(runRoot);
        }
        while (!done) {
            boolean foundNextTarget = false;
            while (!done && !foundNextTarget) {
                // advance until we reach the next node to visit
                foundNextTarget = doAdvance();
            }
            if (done) {
                break;
            }
            RegexASTNode target = pathGetNode(curPath.peek());
            visit(target);
            if (target.isMatchFound() && forward && !dollarsOnPath() && lookAroundsOnPath.isEmpty() && !hasQuantifierGuards() && !caretsOnPath()) {
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
        }
        if (useQuantifierGuards()) {
            if (root.isGroup() && !root.getParent().isSubtreeRoot()) {
                Group emptyMatch = root.getParent().getParent().asGroup();
                popQuantifierGuard(QuantifierGuard.createExitEmptyMatch(emptyMatch.getQuantifier()));
            }
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
            RegexASTNode target = curPath.isEmpty() ? null : pathGetNode(curPath.peek());
            if (useQuantifierGuards() && target != null && target.isGroup() && !target.getParent().isSubtreeRoot() && target.getParent().getParent().asGroup().hasQuantifier()) {
                Group emptyMatch = target.getParent().getParent().asGroup();
                // If the successor in an empty-match group, we will need to do 2 things:
                // 1) Add a final enterEmptyMatch guard.
                QuantifierGuard finalGuard = QuantifierGuard.createEnterEmptyMatch(emptyMatch.getQuantifier());
                pushQuantifierGuard(finalGuard);
                // 2) Filter out any guards concerning the empty-match group, except for
                // enterEmptyMatch and exitEmptyMatch.
                quantifierGuardsResult = quantifierGuards.toArray(guard -> guard.getKind() == QuantifierGuard.Kind.enterEmptyMatch || guard.getKind() == QuantifierGuard.Kind.exitEmptyMatch ||
                                guard.getQuantifier() != emptyMatch.getQuantifier());
                // We do not forget to pop the final guard that was introduced above.
                popQuantifierGuard(finalGuard);
            } else {
                quantifierGuardsResult = quantifierGuards == null ? QuantifierGuard.NO_GUARDS : quantifierGuards.toArray();
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
        // emptyLoopIterations tells us how many extra empty iterations of a loop do we admit.
        // In Ruby and Python, we admit 1, while in other dialects, we admit 0. This extra iteration
        // will not match any characters, but it might store an empty string in a capture group.
        int extraEmptyLoopIterations = ast.getOptions().getFlavor().canHaveEmptyLoopIterations() ? 1 : 0;
        if (cur.isDead() || insideLoops.get(cur, 0) > extraEmptyLoopIterations) {
            return retreat();
        }
        if (cur.isSequence()) {
            final Sequence sequence = (Sequence) cur;
            if (sequence.isEmpty()) {
                Group parent = sequence.getParent();
                if (sequence.isExpandedQuantifier()) {
                    // this empty sequence was inserted during quantifier expansion, so it is
                    // allowed to pass through the parent quantified group.
                    assert pathGetNode(curPath.peek()) == parent && pathIsGroupEnter(curPath.peek());
                    if (parent.hasNotUnrolledQuantifier() && parent.getQuantifier().getMin() > 0) {
                        if (!isGroupExitOnPath(parent)) {
                            // non-unrolled quantifiers with min > 0 may be exited from within their
                            // respective group only.
                            return retreat();
                        }
                    }
                    switchEnterToPassThrough(parent);
                    unregisterInsideLoop(parent);
                } else {
                    pushGroupExit(parent);
                }
                return advanceTerm(parent);
            } else {
                cur = forward ? sequence.getFirstTerm() : sequence.getLastTerm();
                return false;
            }
        } else if (cur.isGroup()) {
            final Group group = (Group) cur;
            pushGroupEnter(group, 1);
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
            return deduplicatePath();
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
                if (forward && dollarsOnPath() && cur.isCharacterClass()) {
                    // don't visit CharacterClass nodes if we traversed dollar - PositionAssertions
                    // already
                    return retreat();
                }
                return true;
            }
        }
    }

    /**
     * Advances past the given {@link Term} and updates {@link #cur the current node}.
     * 
     * @return {@true} if a successor was reached in this step (possible if
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
            // We are leaving curTerm. If curTerm is a quantified group and we have already entered
            // curTerm during this step, then we stop and retreat. Otherwise, we would end up
            // letting curTerm match the empty string, which is forbidden.
            if (insideEmptyGuardGroup.contains(curTerm)) {
                return advanceEmptyGuard(curTerm);
            }
            Sequence parentSeq = (Sequence) curTerm.getParent();
            if (curTerm == (forward ? parentSeq.getLastTerm() : parentSeq.getFirstTerm())) {
                final Group parentGroup = parentSeq.getParent();
                pushGroupExit(parentGroup);
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
        if (insideEmptyGuardGroup.contains(curTerm)) {
            return advanceEmptyGuard(curTerm);
        }
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
        Group parent = curTerm.getParent().getParent().asGroup();
        if (parent.hasNotUnrolledQuantifier() && parent.getQuantifier().getMin() > 0) {
            assert curTerm.isGroup();
            // We found a zero-width match group with a bounded quantifier.
            // By returning the quantified group itself, we map the transition target to the special
            // empty-match state.
            cur = curTerm;
            return true;
        }
        return retreat();
    }

    /**
     * Backtrack through the traversal and find an unexplored alternative.
     * 
     * @return {@code true} if a successor was found in this step
     */
    private boolean retreat() {
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
                        cur = pathGroupGetNext(lastVisited);
                        return deduplicatePath();
                    } else {
                        if (pathIsGroupEnter(lastVisited)) {
                            popGroupEnter(group);
                        } else {
                            assert pathIsGroupPassThrough(lastVisited);
                            popGroupPassThrough(group);
                        }
                        assert noEmptyGuardGroupEnterOnPath(group);
                        if (pathIsGroupEnter(lastVisited)) {
                            // we only deregister the node from insideLoops if this was an enter
                            // node, if it was a passthrough node, it was already deregistered when
                            // it was transformed from an enter node in doAdvance
                            unregisterInsideLoop(group);
                        }
                        insideEmptyGuardGroup.remove(group);
                    }
                } else if (ast.getOptions().getFlavor().failingEmptyChecksDontBacktrack() && pathIsGroupExit(lastVisited) && group.hasQuantifier() && group.getQuantifier().hasZeroWidthIndex() &&
                                (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    // In Ruby (and also in Python), when we finish an iteration of a loop, there is
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
                    return advanceTerm(group);
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
    private boolean deduplicatePath() {
        // In regex flavors where backreferences to unmatched capture groups always pass (i.e. they
        // behave as if the capture group matched the empty string), we don't have to distinguish
        // two states of the traversal that differ only in capture groups, since the state that was
        // encountered first will dominate the one found later and any empty capture groups that
        // would have been matched along the way cannot affect future matching.
        boolean captureGroupsMatter = ast.getOptions().getFlavor().backreferencesToUnmatchedGroupsFail();
        DeduplicationKey key;
        if (captureGroupsMatter) {
            key = CGSensitiveDeduplicationKey.create(cur, lookAroundsOnPath, dollarsOnPath, quantifierGuards, captureGroupUpdates, captureGroupClears, lastGroup);
        } else {
            key = DeduplicationKey.create(cur, lookAroundsOnPath, dollarsOnPath, quantifierGuards);
        }
        boolean isDuplicate = !pathDeduplicationSet.add(key);
        if (isDuplicate) {
            return retreat();
        } else {
            // We can return false, since this is only called when the current target node is a
            // Sequence and these can never be successors.
            return false;
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

    private boolean isGroupExitOnPath(Group group) {
        assert !curPath.isEmpty() && pathIsGroupEnter(curPath.peek()) && pathGetNode(curPath.peek()) == group;
        return curPath.length() >= 2 && pathIsGroupExit(curPath.get(curPath.length() - 2)) && pathGetNode(curPath.get(curPath.length() - 2)) == group;
    }

    private boolean noEmptyGuardGroupEnterOnPath(Group group) {
        if (!group.hasEmptyGuard()) {
            return true;
        }
        for (int i = 0; i < curPath.length(); i++) {
            if (pathGetNode(curPath.get(i)) == group && pathIsGroupEnter(curPath.get(i))) {
                return false;
            }
        }
        return true;
    }

    /// Pushing and popping group elements to and from the path
    private void pushGroupEnter(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET) | PATH_GROUP_ACTION_ENTER);
        // Capture groups
        if (group.isCapturing()) {
            captureGroupUpdate(group.getBoundaryIndexStart());
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
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    pushQuantifierGuard(QuantifierGuard.createEnterZeroWidth(quantifier));
                }
            }
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexStart()));
            }
        }
    }

    private int popGroupEnter(Group group) {
        assert pathIsGroupEnter(curPath.peek());
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                popQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexStart()));
            }
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    popQuantifierGuard(QuantifierGuard.createEnterZeroWidth(quantifier));
                }
                if (quantifier.hasIndex()) {
                    if (quantifierGuardsLoop[quantifier.getIndex()] > 0 && quantifierGuardsExited[quantifier.getIndex()] == 0) {
                        popQuantifierGuard(quantifier.isInfiniteLoop() ? QuantifierGuard.createLoopInc(quantifier) : QuantifierGuard.createLoop(quantifier));
                    } else {
                        popQuantifierGuard(QuantifierGuard.createEnter(quantifier));
                    }
                }
            }
        }
        // Capture groups
        if (!ast.getOptions().getFlavor().nestedCaptureGroupsKeptOnLoopReentry() && group.hasQuantifier() && group.hasEnclosedCaptureGroups()) {
            popCaptureGroupEvent();
        }
        if (group.isCapturing()) {
            popCaptureGroupEvent();
        }
        return pathGetGroupAltIndex(curPath.pop());
    }

    private void switchNextGroupAlternative(Group group) {
        int groupAltIndex;
        if (pathIsGroupEnter(curPath.peek())) {
            groupAltIndex = popGroupEnter(group);
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
            captureGroupUpdate(group.getBoundaryIndexEnd());
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
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    if (ast.getOptions().getFlavor().canHaveEmptyLoopIterations() || !root.isCharacterClass()) {
                        pushQuantifierGuard(QuantifierGuard.createExitZeroWidth(quantifier));
                    }
                }
            }
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
            }
        }
    }

    private void popGroupExit(Group group) {
        assert pathIsGroupExit(curPath.peek());
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                popQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
            }
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    if (ast.getOptions().getFlavor().canHaveEmptyLoopIterations() || !root.isCharacterClass()) {
                        popQuantifierGuard(QuantifierGuard.createExitZeroWidth(quantifier));
                    }
                }
                if (quantifier.hasIndex()) {
                    quantifierGuardsLoop[quantifier.getIndex()]--;
                }
            }
        }
        // Capture groups
        if (group.isCapturing()) {
            if (ast.getOptions().getFlavor().usesLastGroupResultField() && group.getGroupNumber() != 0) {
                popCaptureGroupEvent();
            }
            popCaptureGroupEvent();
        }
        curPath.pop();
    }

    private void pushGroupPassThrough(Group group, int groupAltIndex) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_PASS_THROUGH | (groupAltIndex << PATH_GROUP_ALT_INDEX_OFFSET));
        if (useQuantifierGuards()) {
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    if (quantifier.getMin() > 0) {
                        quantifierGuardsExited[quantifier.getIndex()]++;
                        pushQuantifierGuard(QuantifierGuard.createExit(quantifier));
                    } else {
                        pushQuantifierGuard(QuantifierGuard.createClear(quantifier));
                    }
                }
            }
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexStart()));
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
            }
        }
    }

    private int popGroupPassThrough(Group group) {
        assert pathIsGroupPassThrough(curPath.peek());
        if (useQuantifierGuards()) {
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                popQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
                popQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexStart()));
            }
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    if (quantifier.getMin() > 0) {
                        popQuantifierGuard(QuantifierGuard.createExit(quantifier));
                        quantifierGuardsExited[quantifier.getIndex()]--;
                    } else {
                        popQuantifierGuard(QuantifierGuard.createClear(quantifier));
                    }
                }
            }
        }
        return pathGetGroupAltIndex(curPath.pop());
    }

    private void switchEnterToPassThrough(Group group) {
        int groupAltIndex = popGroupEnter(group);
        pushGroupPassThrough(group, groupAltIndex);
    }

    private void switchExitToEscape(Group group) {
        popGroupExit(group);
        pushGroupEscape(group);
    }

    private void pushGroupEscape(Group group) {
        curPath.add(createPathElement(group) | PATH_GROUP_ACTION_ESCAPE);
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasIndex()) {
                    quantifierGuardsExited[quantifier.getIndex()]++;
                }
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    pushQuantifierGuard(QuantifierGuard.createEscapeZeroWidth(quantifier));
                }
            }
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                pushQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
            }
        }
    }

    private void popGroupEscape(Group group) {
        assert pathIsGroupEscape(curPath.peek());
        // Quantifier guards
        if (useQuantifierGuards()) {
            if (ast.getOptions().getFlavor().emptyChecksMonitorCaptureGroups() && group.isCapturing()) {
                popQuantifierGuard(QuantifierGuard.createUpdateCG(group.getBoundaryIndexEnd()));
            }
            if (group.hasQuantifier()) {
                Quantifier quantifier = group.getQuantifier();
                if (quantifier.hasZeroWidthIndex() && (group.getFirstAlternative().isExpandedQuantifier() || group.getLastAlternative().isExpandedQuantifier())) {
                    popQuantifierGuard(QuantifierGuard.createEscapeZeroWidth(quantifier));
                }
                if (quantifier.hasIndex()) {
                    quantifierGuardsExited[quantifier.getIndex()]--;
                }
            }
        }
        curPath.pop();
    }

    /// Capture group data handling
    private void clearCaptureGroupData() {
        captureGroupEvents.clear();
        captureGroupUpdates.clear();
        captureGroupClears.clear();
        lastGroup = -1;
    }

    private void captureGroupUpdate(int boundary) {
        captureGroupEvents.add(new CaptureGroupEvent.CaptureGroupUpdate(boundary, captureGroupUpdates.get(boundary), captureGroupClears.get(boundary)));
        captureGroupUpdates.set(boundary);
        captureGroupClears.clear(boundary);
    }

    private void captureGroupClear(int low, int high) {
        captureGroupEvents.add(new CaptureGroupEvent.CaptureGroupClears(captureGroupUpdates.copy(), captureGroupClears.copy()));
        captureGroupClears.setRange(low, high);
        captureGroupUpdates.clearRange(low, high);
    }

    private void lastGroupUpdate(int newLastGroup) {
        captureGroupEvents.add(new CaptureGroupEvent.LastGroupUpdate(lastGroup));
        lastGroup = newLastGroup;
    }

    private void popCaptureGroupEvent() {
        assert !captureGroupEvents.isEmpty();
        CaptureGroupEvent poppedEvent = captureGroupEvents.remove(captureGroupEvents.size() - 1);
        poppedEvent.undo(this);
    }

    /// Quantifier guard data handling
    private boolean useQuantifierGuards() {
        return !canTraverseLookArounds();
    }

    private void clearQuantifierGuards() {
        quantifierGuards = null;
    }

    private void pushQuantifierGuard(QuantifierGuard guard) {
        assert useQuantifierGuards();
        quantifierGuards = new QuantifierGuardsLinkedList(guard, quantifierGuards);
    }

    private void popQuantifierGuard(QuantifierGuard expectedGuard) {
        assert useQuantifierGuards();
        assert quantifierGuards != null;
        QuantifierGuard droppedGuard = quantifierGuards.getGuard();
        quantifierGuards = quantifierGuards.getPrev();
        assert droppedGuard.equals(expectedGuard);
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
        insideLoops.put(group, insideLoops.get(group, 0) + 1);
    }

    private void unregisterInsideLoop(Group group) {
        int depth = insideLoops.get(group, 0);
        if (depth == 1) {
            insideLoops.removeKey(group);
        } else if (depth > 1) {
            insideLoops.put(group, depth - 1);
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

    private static class DeduplicationKey {
        protected final StateSet<RegexAST, RegexASTNode> nodesInvolved;
        protected final QuantifierGuardsLinkedList quantifierGuards;
        protected int hashCode;

        protected DeduplicationKey(RegexASTNode targetNode, StateSet<RegexAST, RegexASTNode> lookAroundsOnPath, StateSet<RegexAST, RegexASTNode> dollarsOnPath,
                        QuantifierGuardsLinkedList quantifierGuards) {
            this.nodesInvolved = lookAroundsOnPath.copy();
            this.nodesInvolved.addAll(dollarsOnPath);
            this.nodesInvolved.add(targetNode);
            this.quantifierGuards = quantifierGuards;
        }

        public static DeduplicationKey create(RegexASTNode targetNode, StateSet<RegexAST, RegexASTNode> lookAroundsOnPath, StateSet<RegexAST, RegexASTNode> dollarsOnPath,
                        QuantifierGuardsLinkedList quantifierGuards) {
            DeduplicationKey key = new DeduplicationKey(targetNode, lookAroundsOnPath, dollarsOnPath, quantifierGuards);
            key.hashCode = key.calculateHashCode();
            return key;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || (getClass() != obj.getClass())) {
                return false;
            }
            DeduplicationKey other = (DeduplicationKey) obj;
            return this.nodesInvolved.equals(other.nodesInvolved) && Objects.equals(this.quantifierGuards, other.quantifierGuards);
        }

        protected int calculateHashCode() {
            return Objects.hash(nodesInvolved, quantifierGuards);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class CGSensitiveDeduplicationKey extends DeduplicationKey {
        private final TBitSet captureGroupUpdates;
        private final TBitSet captureGroupClears;
        private final int lastGroup;

        protected CGSensitiveDeduplicationKey(RegexASTNode targetNode, StateSet<RegexAST, RegexASTNode> lookAroundsOnPath, StateSet<RegexAST, RegexASTNode> dollarsOnPath,
                        QuantifierGuardsLinkedList quantifierGuards, TBitSet captureGroupUpdates, TBitSet captureGroupClears, int lastGroup) {
            super(targetNode, lookAroundsOnPath, dollarsOnPath, quantifierGuards);
            this.captureGroupUpdates = captureGroupUpdates.copy();
            this.captureGroupClears = captureGroupClears.copy();
            this.lastGroup = lastGroup;
        }

        public static CGSensitiveDeduplicationKey create(RegexASTNode targetNode, StateSet<RegexAST, RegexASTNode> lookAroundsOnPath, StateSet<RegexAST, RegexASTNode> dollarsOnPath,
                        QuantifierGuardsLinkedList quantifierGuards, TBitSet captureGroupUpdates, TBitSet captureGroupClears, int lastGroup) {
            CGSensitiveDeduplicationKey key = new CGSensitiveDeduplicationKey(targetNode, lookAroundsOnPath, dollarsOnPath, quantifierGuards, captureGroupUpdates, captureGroupClears, lastGroup);
            key.hashCode = key.calculateHashCode();
            return key;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || (getClass() != obj.getClass())) {
                return false;
            }
            CGSensitiveDeduplicationKey other = (CGSensitiveDeduplicationKey) obj;
            return this.nodesInvolved.equals(other.nodesInvolved) && Objects.equals(this.quantifierGuards, other.quantifierGuards) && Objects.equals(captureGroupUpdates, other.captureGroupUpdates) &&
                            Objects.equals(captureGroupClears, other.captureGroupClears) && this.lastGroup == other.lastGroup;
        }

        @Override
        protected int calculateHashCode() {
            return Objects.hash(super.calculateHashCode(), captureGroupUpdates, captureGroupClears, lastGroup);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class QuantifierGuardsLinkedList {
        private final QuantifierGuard guard;
        private final QuantifierGuardsLinkedList prev;
        private final int length;
        private final int hashCode;

        QuantifierGuardsLinkedList(QuantifierGuard guard, QuantifierGuardsLinkedList prev) {
            this.guard = guard;
            this.prev = prev;
            this.length = prev == null ? 1 : prev.length + 1;
            this.hashCode = guard.hashCode() + 31 * (prev == null ? 0 : prev.hashCode);
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

        public QuantifierGuard[] toArray(Predicate<QuantifierGuard> filter) {
            int resultSize = 0;
            QuantifierGuardsLinkedList cur = this;
            while (cur != null) {
                if (filter.test(cur.getGuard())) {
                    resultSize++;
                }
                cur = cur.getPrev();
            }
            QuantifierGuard[] result = new QuantifierGuard[resultSize];
            cur = this;
            while (cur != null) {
                if (filter.test(cur.getGuard())) {
                    result[--resultSize] = cur.getGuard();
                }
                cur = cur.getPrev();
            }
            return result;
        }
    }

    private abstract static class CaptureGroupEvent {

        public abstract void undo(NFATraversalRegexASTVisitor visitor);

        private static final class CaptureGroupUpdate extends CaptureGroupEvent {

            private final int boundary;
            private final boolean prevUpdate;
            private final boolean prevClear;

            CaptureGroupUpdate(int boundary, boolean prevUpdate, boolean prevClear) {
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

            CaptureGroupClears(TBitSet prevUpdates, TBitSet prevClears) {
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

            LastGroupUpdate(int prevLastGroup) {
                this.prevLastGroup = prevLastGroup;
            }

            @Override
            public void undo(NFATraversalRegexASTVisitor visitor) {
                visitor.lastGroup = prevLastGroup;
            }
        }
    }
}
