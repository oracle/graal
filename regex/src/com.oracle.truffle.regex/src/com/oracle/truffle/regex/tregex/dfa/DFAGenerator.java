/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CharArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.AnyMatcher;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.nfa.GroupBoundaries;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.tregex.nodes.BackwardDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.CGTrackingDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupLazyTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupPartialTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorDebugRecorder;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplitBailoutException;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DFAGenerator implements JsonConvertible {

    private static final DFAStateTransitionBuilder[] EMPTY_TRANSITIONS_ARRAY = new DFAStateTransitionBuilder[0];
    private static final short[] EMPTY_SHORT_ARRAY = {};

    private final NFA nfa;
    private final TRegexDFAExecutorProperties executorProps;
    private final CompilationBuffer compilationBuffer;

    private final boolean forward;
    private final boolean trackCaptureGroups;
    private final boolean pruneUnambiguousPaths;

    private final Map<DFAStateNodeBuilder, DFAStateNodeBuilder> stateMap = new HashMap<>();
    private final ArrayDeque<DFAStateNodeBuilder> expansionQueue = new ArrayDeque<>();
    private DFAStateNodeBuilder[] stateIndexMap = null;

    private short nextID = 1;
    private final DFAStateNodeBuilder lookupDummyState = new DFAStateNodeBuilder((short) -1, null, false);
    private final Counter transitionIDCounter = new Counter.ThresholdCounter(Integer.MAX_VALUE, "too many transitions");
    private final Counter cgTransitionIDCounter = new Counter.ThresholdCounter(Short.MAX_VALUE, "too many capture group transitions");
    private int maxNumberOfNfaStates = 0;

    private DFAStateNodeBuilder[] entryStates;
    private final DFACaptureGroupTransitionBuilder initialCGTransition;
    private final List<DFACaptureGroupLazyTransitionNode> captureGroupTransitions = new ArrayList<>();
    private final DFATransitionCanonicalizer canonicalizer;

    private final List<DFAStateTransitionBuilder> expandDFATransitions = new ArrayList<>();
    private List<DFAStateTransitionBuilder[]> expandDFAPruneTraverseCur;
    private List<DFAStateTransitionBuilder[]> expandDFAPruneTraverseNext;

    public DFAGenerator(NFA nfa, TRegexDFAExecutorProperties executorProps, CompilationBuffer compilationBuffer) {
        this.nfa = nfa;
        this.executorProps = executorProps;
        this.forward = executorProps.isForward();
        this.trackCaptureGroups = executorProps.isTrackCaptureGroups();
        this.pruneUnambiguousPaths = executorProps.isBackward() && nfa.isTraceFinderNFA() && nfa.hasReverseUnAnchoredEntry();
        this.canonicalizer = new DFATransitionCanonicalizer(trackCaptureGroups);
        this.compilationBuffer = compilationBuffer;
        this.expandDFAPruneTraverseCur = pruneUnambiguousPaths ? new ArrayList<>() : null;
        this.expandDFAPruneTraverseNext = pruneUnambiguousPaths ? new ArrayList<>() : null;
        this.initialCGTransition = trackCaptureGroups ? new DFACaptureGroupTransitionBuilder(null, null, null, false) : null;
    }

    public NFA getNfa() {
        return nfa;
    }

    public DFAStateNodeBuilder[] getEntryStates() {
        return entryStates;
    }

    public Map<DFAStateNodeBuilder, DFAStateNodeBuilder> getStateMap() {
        return stateMap;
    }

    public DFAStateNodeBuilder getState(short stateNodeID) {
        if (stateIndexMap == null) {
            stateIndexMap = new DFAStateNodeBuilder[nextID];
            for (DFAStateNodeBuilder s : stateMap.values()) {
                stateIndexMap[s.getId()] = s;
            }
        }
        return stateIndexMap[stateNodeID];
    }

    /**
     * Calculates the DFA. Run this method before calling {@link #createDFAExecutor()} or
     * {@link #toJson()} or passing the generator object to
     * {@link com.oracle.truffle.regex.tregex.util.DFAExport}.
     */
    @TruffleBoundary
    public void calcDFA() {
        if (forward) {
            createInitialStatesForward();
        } else {
            createInitialStatesBackward();
        }
        while (!expansionQueue.isEmpty()) {
            expandState(expansionQueue.pop());
        }
    }

    /**
     * Transforms the generator's DFA representation to the data structure required by
     * {@link TRegexDFAExecutorNode} and returns the dfa as a new {@link TRegexDFAExecutorNode}.
     * Make sure to calculate the DFA with {@link #calcDFA()} before calling this method!
     * 
     * @return a {@link TRegexDFAExecutorNode} that runs the DFA generated by this object.
     */
    @TruffleBoundary
    public TRegexDFAExecutorNode createDFAExecutor() {
        if (trackCaptureGroups) {
            int maxNumberOfEntryStateSuccessors = 0;
            for (DFAStateNodeBuilder entryState : entryStates) {
                if (entryState == null) {
                    continue;
                }
                maxNumberOfEntryStateSuccessors = Math.max(entryState.getTransitions().length, maxNumberOfEntryStateSuccessors);
            }
            DFACaptureGroupPartialTransitionNode[] partialTransitions = new DFACaptureGroupPartialTransitionNode[maxNumberOfEntryStateSuccessors];
            Arrays.fill(partialTransitions, DFACaptureGroupPartialTransitionNode.createEmpty());
            assert cgTransitionIDCounter.getCount() == 0;
            DFACaptureGroupLazyTransitionNode emptyInitialTransition = new DFACaptureGroupLazyTransitionNode(
                            (short) cgTransitionIDCounter.inc(), partialTransitions,
                            DFACaptureGroupPartialTransitionNode.createEmpty(),
                            DFACaptureGroupPartialTransitionNode.createEmpty());
            registerCGTransition(emptyInitialTransition);
            initialCGTransition.setLazyTransition(emptyInitialTransition);
        }
        DFAAbstractStateNode[] states = createDFAExecutorStates();
        DFACaptureGroupLazyTransitionNode[] transitionsArray = null;
        if (trackCaptureGroups) {
            transitionsArray = new DFACaptureGroupLazyTransitionNode[captureGroupTransitions.size()];
            for (DFACaptureGroupLazyTransitionNode t : captureGroupTransitions) {
                assert transitionsArray[t.getId()] == null;
                transitionsArray[t.getId()] = t;
            }
            assert Arrays.stream(transitionsArray).noneMatch(Objects::isNull);
        }
        assert states[0] == null;
        short[] entryStateIDs = new short[entryStates.length];
        for (int i = 0; i < entryStates.length; i++) {
            if (entryStates[i] == null) {
                entryStateIDs[i] = -1;
            } else {
                entryStateIDs[i] = entryStates[i].getId();
            }
        }
        states[0] = new DFAInitialStateNode(entryStateIDs, executorProps.isSearching(), trackCaptureGroups);
        if (TRegexOptions.TRegexEnableNodeSplitter) {
            states = tryMakeReducible(states);
        }
        return new TRegexDFAExecutorNode(executorProps, maxNumberOfNfaStates, states, transitionsArray,
                        DebugUtil.DEBUG_STEP_EXECUTION ? new TRegexDFAExecutorDebugRecorder(this) : null);
    }

    private void createInitialStatesForward() {
        final int numberOfEntryPoints = nfa.getAnchoredEntry().length;
        entryStates = new DFAStateNodeBuilder[numberOfEntryPoints * 2];
        nfa.setInitialLoopBack(executorProps.isSearching() && !nfa.getAst().getSource().getFlags().isSticky());
        for (int i = 0; i < numberOfEntryPoints; i++) {
            NFATransitionSet anchoredEntryStateSet = NFATransitionSet.create(nfa, forward, forward, nfa.getAnchoredEntry()[i]);
            if (nfa.getUnAnchoredEntry()[i].getTarget().getNext().isEmpty()) {
                entryStates[numberOfEntryPoints + i] = null;
            } else {
                NFATransitionSet unAnchoredEntryStateSet = NFATransitionSet.create(nfa, forward, forward, nfa.getUnAnchoredEntry()[i]);
                anchoredEntryStateSet.addAll(unAnchoredEntryStateSet);
                DFAStateTransitionBuilder unAnchoredEntryTransition = new DFAStateTransitionBuilder(null, unAnchoredEntryStateSet);
                entryStates[numberOfEntryPoints + i] = createInitialState(unAnchoredEntryTransition);
            }
            DFAStateTransitionBuilder anchoredEntryTransition = new DFAStateTransitionBuilder(null, anchoredEntryStateSet);
            entryStates[i] = createInitialState(anchoredEntryTransition);
        }
    }

    private void createInitialStatesBackward() {
        NFATransitionSet anchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseAnchoredEntry());
        entryStates = new DFAStateNodeBuilder[]{null, null};
        if (nfa.hasReverseUnAnchoredEntry()) {
            anchoredEntry.add(nfa.getReverseUnAnchoredEntry());
            NFATransitionSet unAnchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseUnAnchoredEntry());
            entryStates[1] = createInitialState(new DFAStateTransitionBuilder(null, unAnchoredEntry));
        }
        entryStates[0] = createInitialState(new DFAStateTransitionBuilder(null, anchoredEntry));
    }

    private DFAStateNodeBuilder createInitialState(DFAStateTransitionBuilder transition) {
        DFAStateNodeBuilder lookup = lookupState(transition.getTransitionSet(), false);
        if (lookup == null) {
            lookup = createState(transition.getTransitionSet(), false);
            lookup.setInitialState(true);
            lookup.updateFinalStateData(this);
        }
        transition.setTarget(lookup);
        if (trackCaptureGroups) {
            lookup.addPrecedingTransition(initialCGTransition);
        }
        return lookup;
    }

    private void expandState(DFAStateNodeBuilder state) {
        if (pruneUnambiguousPaths && tryPruneTraceFinderState(state)) {
            return;
        }
        expandDFATransitions.clear();
        boolean anyPrefixStateSuccessors = false;
        boolean allPrefixStateSuccessors = true;
        outer: for (NFAStateTransition transition : state.getNfaStateSet()) {
            NFAState nfaState = transition.getTarget(forward);
            for (NFAStateTransition nfaTransition : nfaState.getNext(forward)) {
                NFAState target = nfaTransition.getTarget(forward);
                if (!target.isFinalState(forward) && (!state.isBackwardPrefixState() || target.hasPrefixStates())) {
                    anyPrefixStateSuccessors |= target.hasPrefixStates();
                    allPrefixStateSuccessors &= target.hasPrefixStates();
                    expandDFATransitions.add(new DFAStateTransitionBuilder(target.getMatcherBuilder(), nfaTransition, nfa, forward, forward));
                } else if (forward && target.isForwardUnAnchoredFinalState()) {
                    assert target == nfa.getReverseUnAnchoredEntry().getSource();
                    break outer;
                }
            }
        }
        if (!forward && anyPrefixStateSuccessors) {
            if (allPrefixStateSuccessors) {
                state.setBackwardPrefixState(state.getId());
            } else {
                assert !state.isBackwardPrefixState();
                DFAStateNodeBuilder lookup = lookupState(state.getNfaStateSet(), true);
                if (lookup == null) {
                    lookup = createState(state.getNfaStateSet(), true);
                }
                state.setBackwardPrefixState(lookup.getId());
            }
        }
        DFAStateTransitionBuilder[] transitions = canonicalizer.run(expandDFATransitions, compilationBuffer);
        Arrays.sort(transitions, Comparator.comparing(TransitionBuilder::getMatcherBuilder));
        for (DFAStateTransitionBuilder transition : transitions) {
            assert !transition.getTransitionSet().isEmpty();
            transition.setId(transitionIDCounter.inc());
            transition.setSource(state);
            DFAStateNodeBuilder successorState = lookupState(transition.getTransitionSet(), false);
            if (successorState == null) {
                successorState = createState(transition.getTransitionSet(), false);
            } else if (pruneUnambiguousPaths) {
                reScheduleFinalStateSuccessors(state, successorState);
            }
            if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                successorState.setFinalStateSuccessor();
            }
            transition.setTarget(successorState);
            successorState.updateFinalStateData(this);
            if (trackCaptureGroups) {
                transition.setCaptureGroupTransition(new DFACaptureGroupTransitionBuilder(cgTransitionIDCounter, nfa, transition,
                                !executorProps.isSearching() && state.isInitialState()));
                transition.getTarget().addPrecedingTransition(transition.getCaptureGroupTransition());
            }
        }
        state.setTransitions(transitions);
    }

    /**
     * When compiling a "trace finder" NFA, each state implicitly contains a list of possible regex
     * search results - the union of {@link NFAState#getPossibleResults()} of all NFA states
     * contained in one DFA state. If all possible results of one DFA state are equal, we can
     * "prune" it. This means that we convert it to a final state and don't calculate any of its
     * successors. Pruning a state is not valid if it is a direct or indirect successor of another
     * final state, since the preceding final state implicitly removes one result from the list of
     * possible results - this could be fine-tuned further.
     *
     * @param state the state to inspect.
     * @return <code>true</code> if the state was pruned, <code>false</code> otherwise.
     * @see com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator
     */
    private boolean tryPruneTraceFinderState(DFAStateNodeBuilder state) {
        assert nfa.isTraceFinderNFA();
        if (state.isFinalStateSuccessor()) {
            return false;
        }
        PreCalculatedResultFactory result = null;
        int resultIndex = -1;
        assert !state.getNfaStateSet().isEmpty();
        for (NFAStateTransition transition : state.getNfaStateSet()) {
            NFAState nfaState = transition.getTarget(forward);
            for (int i : nfaState.getPossibleResults()) {
                if (result == null) {
                    result = nfa.getPreCalculatedResults()[i];
                    resultIndex = (byte) i;
                } else if (result != nfa.getPreCalculatedResults()[i]) {
                    return false;
                }
            }
        }
        if (resultIndex >= 0) {
            state.setOverrideFinalState(true);
            state.updatePreCalcUnAnchoredResult(resultIndex);
            state.setTransitions(EMPTY_TRANSITIONS_ARRAY);
            return true;
        }
        return false;
    }

    private void reScheduleFinalStateSuccessors(DFAStateNodeBuilder state, DFAStateNodeBuilder successorState) {
        assert nfa.isTraceFinderNFA();
        if ((state.isFinalState() || state.isFinalStateSuccessor()) && !successorState.isFinalStateSuccessor()) {
            reScheduleFinalStateSuccessor(successorState);
            expandDFAPruneTraverseCur.clear();
            if (successorState.getTransitions() != null) {
                expandDFAPruneTraverseCur.add(successorState.getTransitions());
            }
            while (!expandDFAPruneTraverseCur.isEmpty()) {
                expandDFAPruneTraverseNext.clear();
                for (DFAStateTransitionBuilder[] cur : expandDFAPruneTraverseCur) {
                    for (DFAStateTransitionBuilder t : cur) {
                        if (t.getTarget().isFinalStateSuccessor()) {
                            continue;
                        }
                        if (t.getTarget().getTransitions() != null) {
                            expandDFAPruneTraverseNext.add(t.getTarget().getTransitions());
                        }
                        reScheduleFinalStateSuccessor(t.getTarget());
                    }
                }
                List<DFAStateTransitionBuilder[]> tmp = expandDFAPruneTraverseCur;
                expandDFAPruneTraverseCur = expandDFAPruneTraverseNext;
                expandDFAPruneTraverseNext = tmp;
            }
        }
    }

    private void reScheduleFinalStateSuccessor(DFAStateNodeBuilder finalStateSuccessor) {
        finalStateSuccessor.setFinalStateSuccessor();
        finalStateSuccessor.setOverrideFinalState(false);
        finalStateSuccessor.clearPreCalculatedResults();
        finalStateSuccessor.updateFinalStateData(this);
        expansionQueue.push(finalStateSuccessor);
    }

    private DFAStateNodeBuilder lookupState(NFATransitionSet transitionSet, boolean isBackWardPrefixState) {
        lookupDummyState.setNfaStateSet(transitionSet);
        lookupDummyState.setIsBackwardPrefixState(isBackWardPrefixState);
        return stateMap.get(lookupDummyState);
    }

    private DFAStateNodeBuilder createState(NFATransitionSet transitionSet, boolean isBackwardPrefixState) {
        assert stateIndexMap == null : "state index map created before dfa generation!";
        if (transitionSet.size() > maxNumberOfNfaStates) {
            maxNumberOfNfaStates = transitionSet.size();
            if (maxNumberOfNfaStates > TRegexOptions.TRegexMaxNumberOfNFAStatesInOneDFAState) {
                throw new UnsupportedRegexException("DFA state size explosion");
            }
        }
        DFAStateNodeBuilder dfaState = new DFAStateNodeBuilder(nextID++, transitionSet, isBackwardPrefixState);
        stateMap.put(dfaState, dfaState);
        if (stateMap.size() + (forward ? expansionQueue.size() : 0) > TRegexOptions.TRegexMaxDFASize) {
            throw new UnsupportedRegexException((forward ? (trackCaptureGroups ? "CG" : "Forward") : "Backward") + " DFA explosion");
        }
        expansionQueue.push(dfaState);
        return dfaState;
    }

    private DFAAbstractStateNode[] createDFAExecutorStates() {
        DFAAbstractStateNode[] ret = new DFAAbstractStateNode[stateMap.values().size() + 1];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            CharMatcher[] matchers = (s.getTransitions().length > 0) ? new CharMatcher[s.getTransitions().length] : CharMatcher.EMPTY;
            MatcherBuilder acc = MatcherBuilder.createEmpty();
            int nCheckingTransitions = matchers.length;
            int nRanges = 0;
            int estimatedTransitionsCost = 0;
            for (int i = 0; i < matchers.length; i++) {
                MatcherBuilder matcherBuilder = s.getTransitions()[i].getMatcherBuilder();
                nRanges += matcherBuilder.size();
                acc = acc.union(matcherBuilder, compilationBuffer);
                if (i == matchers.length - 1 && (acc.matchesEverything() || (pruneUnambiguousPaths && !s.isFinalStateSuccessor()))) {
                    // replace the last matcher with an AnyMatcher, since it must always cover the
                    // remaining input space
                    matchers[i] = AnyMatcher.create();
                    nCheckingTransitions--;
                } else {
                    matchers[i] = matcherBuilder.createMatcher(compilationBuffer);
                }
                estimatedTransitionsCost += matchers[i].estimatedCost();
            }
            if (matchers.length == 1 && matchers[0] == AnyMatcher.INSTANCE) {
                matchers = AnyMatcher.INSTANCE_ARRAY;
            }

            // Very conservative heuristic for whether we should use AllTransitionsInOneTreeMatcher.
            // TODO: Potential benefits of this should be further explored.
            boolean useTreeTransitionMatcher = nCheckingTransitions > 1 && MathUtil.log2ceil(nRanges + 2) * 8 < estimatedTransitionsCost;
            if (useTreeTransitionMatcher) {
                // ====
                // TODO: Can be changed to
                //
                // matchers = new CharMatcher[]{createAllTransitionsInOneTreeMatcher(s)};
                //
                // with some minor changes in DFAStateNode. We'll keep this for testing purposes
                // for the moment.

                matchers = Arrays.copyOf(matchers, matchers.length + 1);
                matchers[matchers.length - 1] = createAllTransitionsInOneTreeMatcher(s);

                // ====
            }

            short[] successors = s.getNumberOfSuccessors() > 0 ? new short[s.getNumberOfSuccessors()] : EMPTY_SHORT_ARRAY;
            short[] cgTransitions = null;
            short[] cgPrecedingTransitions = null;
            if (trackCaptureGroups) {
                cgTransitions = new short[s.getTransitions().length];
                List<DFACaptureGroupTransitionBuilder> precedingTransitions = s.getPrecedingTransitions();
                assert !precedingTransitions.isEmpty();
                cgPrecedingTransitions = new short[precedingTransitions.size()];
                for (int i = 0; i < precedingTransitions.size(); i++) {
                    DFACaptureGroupTransitionBuilder transitionBuilder = precedingTransitions.get(i);
                    cgPrecedingTransitions[i] = transitionBuilder.toLazyTransition(compilationBuffer).getId();
                }
            }
            short loopToSelf = -1;
            for (int i = 0; i < successors.length - (s.hasBackwardPrefixState() ? 1 : 0); i++) {
                successors[i] = s.getTransitions()[i].getTarget().getId();
                if (successors[i] == s.getId()) {
                    loopToSelf = (short) i;
                }
                assert successors[i] >= 0 && successors[i] < ret.length;
                if (trackCaptureGroups) {
                    final DFACaptureGroupLazyTransitionNode transition = s.getTransitions()[i].getCaptureGroupTransition().toLazyTransition(compilationBuffer);
                    cgTransitions[i] = transition.getId();
                    registerCGTransition(transition);
                }
            }
            if (s.hasBackwardPrefixState()) {
                successors[successors.length - 1] = s.getBackwardPrefixState();
            }
            DFAStateNode stateNode;
            if (trackCaptureGroups) {
                stateNode = new CGTrackingDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers, cgTransitions, cgPrecedingTransitions,
                                createCGFinalTransition(s.getAnchoredFinalStateTransition()),
                                createCGFinalTransition(s.getUnAnchoredFinalStateTransition()));
            } else if (nfa.isTraceFinderNFA()) {
                stateNode = new TraceFinderDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers, s.getPreCalculatedUnAnchoredResult(), s.getPreCalculatedAnchoredResult());
            } else if (forward) {
                stateNode = new DFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers);
            } else {
                stateNode = new BackwardDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers);
            }
            ret[s.getId()] = stateNode;
        }
        return ret;
    }

    private AllTransitionsInOneTreeMatcher createAllTransitionsInOneTreeMatcher(DFAStateNodeBuilder state) {
        DFAStateTransitionBuilder[] transitions = state.getTransitions();
        CharArrayBuffer sortedRangesBuf = compilationBuffer.getRangesArrayBuffer1();
        ShortArrayBuffer rangeTreeSuccessorsBuf = compilationBuffer.getShortArrayBuffer();
        int[] matcherIndices = new int[transitions.length];
        boolean rangesLeft = false;
        for (int i = 0; i < transitions.length; i++) {
            matcherIndices[i] = 0;
            rangesLeft |= matcherIndices[i] < transitions[i].getMatcherBuilder().size();
        }
        int lastHi = 0;
        while (rangesLeft) {
            int minLo = Integer.MAX_VALUE;
            int minMb = -1;
            for (int i = 0; i < transitions.length; i++) {
                MatcherBuilder mb = transitions[i].getMatcherBuilder();
                if (matcherIndices[i] < mb.size() && mb.getLo(matcherIndices[i]) < minLo) {
                    minLo = mb.getLo(matcherIndices[i]);
                    minMb = i;
                }
            }
            if (minMb == -1) {
                break;
            }
            if (minLo != lastHi) {
                rangeTreeSuccessorsBuf.add((short) -1);
                sortedRangesBuf.add((char) minLo);
            }
            rangeTreeSuccessorsBuf.add((short) minMb);
            lastHi = transitions[minMb].getMatcherBuilder().getHi(matcherIndices[minMb]) + 1;
            if (lastHi <= Character.MAX_VALUE) {
                sortedRangesBuf.add((char) (lastHi));
            }
            matcherIndices[minMb]++;
        }
        if (lastHi != Character.MAX_VALUE + 1) {
            rangeTreeSuccessorsBuf.add((short) -1);
        }
        return new AllTransitionsInOneTreeMatcher(sortedRangesBuf.toArray(), rangeTreeSuccessorsBuf.toArray());
    }

    private void registerCGTransition(DFACaptureGroupLazyTransitionNode cgTransition) {
        captureGroupTransitions.add(cgTransition);
    }

    private DFACaptureGroupPartialTransitionNode createCGFinalTransition(NFAStateTransition transition) {
        if (transition == null) {
            return null;
        }
        GroupBoundaries groupBoundaries = transition.getGroupBoundaries();
        byte[][] indexUpdates = DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_UPDATES;
        byte[][] indexClears = DFACaptureGroupPartialTransitionNode.EMPTY_INDEX_CLEARS;
        if (groupBoundaries.hasIndexUpdates()) {
            indexUpdates = new byte[][]{DFACaptureGroupTransitionBuilder.createIndexManipulationArray(0, groupBoundaries.getUpdateIndices())};
        }
        if (groupBoundaries.hasIndexClears()) {
            indexClears = new byte[][]{DFACaptureGroupTransitionBuilder.createIndexManipulationArray(0, groupBoundaries.getClearIndices())};
        }
        return DFACaptureGroupPartialTransitionNode.create(null, DFACaptureGroupPartialTransitionNode.EMPTY_ARRAY_COPIES, indexUpdates, indexClears);
    }

    private static DFAAbstractStateNode[] tryMakeReducible(DFAAbstractStateNode[] states) {
        try {
            return DFANodeSplit.createReducibleGraph(states);
        } catch (DFANodeSplitBailoutException e) {
            return states;
        }
    }

    public String getDebugDumpName() {
        if (forward) {
            if (trackCaptureGroups) {
                if (executorProps.isSearching()) {
                    return "eagerCG";
                } else {
                    return "lazyCG";
                }
            } else {
                return "forward";
            }
        } else {
            if (nfa.isTraceFinderNFA()) {
                return "traceFinder";
            } else {
                return "backward";
            }
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        nfa.setInitialLoopBack(forward && executorProps.isSearching() && !nfa.getAst().getSource().getFlags().isSticky());
        DFAStateNodeBuilder[] stateList = new DFAStateNodeBuilder[nextID];
        DFAStateTransitionBuilder[] transitionList = new DFAStateTransitionBuilder[transitionIDCounter.getCount()];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            stateList[s.getId()] = s;
            for (DFAStateTransitionBuilder t : s.getTransitions()) {
                transitionList[t.getId()] = t;
            }
        }
        return Json.obj(Json.prop("pattern", nfa.getAst().getSource().toString()),
                        Json.prop("nfa", nfa.toJson(forward)),
                        Json.prop("dfa", Json.obj(
                                        Json.prop("states", Arrays.stream(stateList)),
                                        Json.prop("transitions", Arrays.stream(transitionList)),
                                        Json.prop("entryStates", Arrays.stream(entryStates).map(x -> Json.val(x.getId()))))));
    }
}
