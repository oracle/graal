/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.matchers.SingleCharMatcher;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAMatcherState;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.tregex.nodes.BackwardDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.CGTrackingDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFACaptureGroupLazyTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.nodes.DFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplitBailoutException;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.util.MathUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DFAGenerator {

    private static final DFAStateNodeBuilder[] EMPTY_SUCCESSORS_ARRAY = new DFAStateNodeBuilder[0];
    private static final MatcherBuilder[] EMPTY_MATCHER_BUILDERS_ARRAY = new MatcherBuilder[0];
    private static final short[] EMPTY_SHORT_ARRAY = {};

    private final NFA nfa;
    private final TRegexDFAExecutorProperties executorProps;
    private final CompilationBuffer compilationBuffer;

    private final boolean forward;
    private final boolean trackCaptureGroups;
    private final boolean pruneUnambiguousPaths;

    private final Map<NFATransitionSet, DFAStateNodeBuilder> stateMap = new HashMap<>();
    private final ArrayDeque<DFAStateNodeBuilder> expansionQueue = new ArrayDeque<>();

    private short nextID = 1;
    private final Counter cgTransitionIDCounter = new Counter.ThresholdCounter(Short.MAX_VALUE, "too many capture group transitions");
    private int maxNumberOfNfaStates = 0;

    private short[] entryStates;
    private short[] initCaptureGroups;
    private final List<DFACaptureGroupLazyTransitionNode> transitions = new ArrayList<>();
    private final DFATransitionCanonicalizer canonicalizer;

    private final List<DFAStateTransitionBuilder> expandDFAConnections = new ArrayList<>();
    private List<DFAStateNodeBuilder[]> expandDFAPruneTraverseCur;
    private List<DFAStateNodeBuilder[]> expandDFAPruneTraverseNext;

    private DFAGenerator(NFA nfa, TRegexDFAExecutorProperties executorProps, CompilationBuffer compilationBuffer) {
        this.nfa = nfa;
        this.executorProps = executorProps;
        this.forward = executorProps.isForward();
        this.trackCaptureGroups = executorProps.isTrackCaptureGroups();
        this.pruneUnambiguousPaths = executorProps.isBackward() && nfa.isTraceFinderNFA() && nfa.hasReverseUnAnchoredEntry();
        this.canonicalizer = new DFATransitionCanonicalizer(trackCaptureGroups);
        this.compilationBuffer = compilationBuffer;
        this.expandDFAPruneTraverseCur = pruneUnambiguousPaths ? new ArrayList<>() : null;
        this.expandDFAPruneTraverseNext = pruneUnambiguousPaths ? new ArrayList<>() : null;
    }

    @TruffleBoundary
    public static DFAGenerator createDFA(NFA nfa, TRegexDFAExecutorProperties executorProperties, CompilationBuffer compilationBuffer) {
        DFAGenerator gen = new DFAGenerator(nfa, executorProperties, compilationBuffer);
        gen.expandAllStates();
        return gen;
    }

    @TruffleBoundary
    public TRegexDFAExecutorNode createDFAExecutor() {
        DFAAbstractStateNode[] states = createDFAExecutorStates();
        DFACaptureGroupLazyTransitionNode[] transitionsArray = null;
        if (trackCaptureGroups) {
            transitionsArray = new DFACaptureGroupLazyTransitionNode[transitions.size()];
            for (DFACaptureGroupLazyTransitionNode t : transitions) {
                assert transitionsArray[t.getId()] == null;
                transitionsArray[t.getId()] = t;
            }
            assert Arrays.stream(transitionsArray).noneMatch(Objects::isNull);
        }
        assert states[0] == null;
        states[0] = new DFAInitialStateNode(entryStates, initCaptureGroups, executorProps.isSearching(), trackCaptureGroups);
        if (TRegexOptions.TRegexEnableNodeSplitter) {
            states = tryMakeReducible(states);
        }
        return new TRegexDFAExecutorNode(executorProps, maxNumberOfNfaStates, states, transitionsArray);
    }

    public short[] getEntryStates() {
        return entryStates;
    }

    public Map<NFATransitionSet, DFAStateNodeBuilder> getStateMap() {
        return stateMap;
    }

    private void expandAllStates() {
        int numberOfEntryPoints = 0;
        DFACaptureGroupTransitionBuilder[] anchoredInitialCGBuilder = null;
        DFACaptureGroupTransitionBuilder[] unAnchoredInitialCGBuilder = null;
        if (trackCaptureGroups) {
            numberOfEntryPoints = nfa.getAnchoredEntry().size();
            anchoredInitialCGBuilder = new DFACaptureGroupTransitionBuilder[numberOfEntryPoints];
            unAnchoredInitialCGBuilder = new DFACaptureGroupTransitionBuilder[numberOfEntryPoints];
        }
        if (forward) {
            createInitialStatesForward(anchoredInitialCGBuilder, unAnchoredInitialCGBuilder);
        } else {
            createInitialStatesBackward();
        }
        while (!expansionQueue.isEmpty()) {
            expandState(expansionQueue.pop());
        }
        if (trackCaptureGroups) {
            for (int i = 0; i < numberOfEntryPoints; i++) {
                DFACaptureGroupLazyTransitionNode ti = anchoredInitialCGBuilder[i].toLazyTransition(cgTransitionIDCounter, compilationBuffer);
                initCaptureGroups[i] = ti.getId();
                registerTransition(ti);
                if (unAnchoredInitialCGBuilder[i] != null) {
                    ti = unAnchoredInitialCGBuilder[i].toLazyTransition(cgTransitionIDCounter, compilationBuffer);
                    initCaptureGroups[numberOfEntryPoints + i] = ti.getId();
                    registerTransition(ti);
                }
            }
        }
    }

    private void createInitialStatesForward(DFACaptureGroupTransitionBuilder[] anchoredInitialCGBuilder, DFACaptureGroupTransitionBuilder[] unAnchoredInitialCGBuilder) {
        final int numberOfEntryPoints = nfa.getAnchoredEntry().size();
        entryStates = new short[numberOfEntryPoints * 2];
        initCaptureGroups = trackCaptureGroups ? new short[numberOfEntryPoints * 2] : null;
        NFAState loopBackMatcher = null;
        for (int i = 0; i < numberOfEntryPoints; i++) {
            NFATransitionSet anchoredEntryStateSet = NFATransitionSet.create(nfa, forward, forward, nfa.getAnchoredEntry().get(i).getNext());
            if (nfa.getUnAnchoredEntry().get(i).getNext().isEmpty()) {
                entryStates[numberOfEntryPoints + i] = -1;
            } else {
                final boolean createLoopBack = executorProps.isSearching() && !nfa.getAst().getSource().getFlags().isSticky();
                NFATransitionSet unAnchoredEntryStateSet = NFATransitionSet.create(nfa, forward, forward, nfa.getUnAnchoredEntry().get(i).getNext());
                if (createLoopBack) {
                    if (i == 0) {
                        loopBackMatcher = nfa.createLoopBackMatcher();
                        unAnchoredEntryStateSet.addAll(loopBackMatcher.getNext());
                    } else {
                        assert loopBackMatcher != null;
                        // add transition to loopBackMatcher
                        unAnchoredEntryStateSet.add(loopBackMatcher.getNext().get(loopBackMatcher.getNext().size() - 1));
                    }
                }
                anchoredEntryStateSet.addAll(unAnchoredEntryStateSet);
                DFAStateTransitionBuilder unAnchoredEntryConnection = new DFAStateTransitionBuilder(null, unAnchoredEntryStateSet);
                DFAStateNodeBuilder unAnchoredEntryState = lookupOrCreateState(unAnchoredEntryConnection);
                entryStates[numberOfEntryPoints + i] = unAnchoredEntryState.getId();
                if (trackCaptureGroups) {
                    unAnchoredInitialCGBuilder[i] = new DFACaptureGroupTransitionBuilder(nfa, unAnchoredEntryConnection, unAnchoredEntryState, true);
                    unAnchoredEntryState.addPrecedingTransition(unAnchoredInitialCGBuilder[i]);
                }
            }
            DFAStateTransitionBuilder anchoredEntryConnection = new DFAStateTransitionBuilder(null, anchoredEntryStateSet);
            DFAStateNodeBuilder anchoredEntryState = lookupOrCreateState(anchoredEntryConnection);
            entryStates[i] = anchoredEntryState.getId();
            if (trackCaptureGroups) {
                anchoredInitialCGBuilder[i] = new DFACaptureGroupTransitionBuilder(nfa, anchoredEntryConnection, anchoredEntryState, true);
                anchoredEntryState.addPrecedingTransition(anchoredInitialCGBuilder[i]);
            }
        }
    }

    private void createInitialStatesBackward() {
        NFATransitionSet anchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseAnchoredEntry().getPrev());
        entryStates = new short[]{-1, -1};
        if (nfa.hasReverseUnAnchoredEntry()) {
            anchoredEntry.addAll(nfa.getReverseUnAnchoredEntry().getPrev());
            NFATransitionSet unAnchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseUnAnchoredEntry().getPrev());
            entryStates[1] = lookupOrCreateState(new DFAStateTransitionBuilder(null, unAnchoredEntry)).getId();
        }
        entryStates[0] = lookupOrCreateState(new DFAStateTransitionBuilder(null, anchoredEntry)).getId();
    }

    private DFAAbstractStateNode[] createDFAExecutorStates() {
        DFAAbstractStateNode[] ret = new DFAAbstractStateNode[stateMap.values().size() + 1];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            CharMatcher[] matchers = (s.getMatcherBuilders().length > 0) ? new CharMatcher[s.getMatcherBuilders().length] : CharMatcher.EMPTY;
            MatcherBuilder acc = MatcherBuilder.createEmpty();
            int nCheckingTransitions = matchers.length;
            int nRanges = 0;
            int estimatedTransitionsCost = 0;
            for (int i = 0; i < matchers.length; i++) {
                MatcherBuilder matcherBuilder = s.getMatcherBuilders()[i];
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
                MatcherBuilder[] matcherBuilders = s.getMatcherBuilders();
                CharArrayBuffer sortedRangesBuf = compilationBuffer.getRangesArrayBuffer1();
                ShortArrayBuffer rangeTreeSuccessorsBuf = compilationBuffer.getShortArrayBuffer();
                int[] matcherIndices = new int[matcherBuilders.length];
                boolean rangesLeft = false;
                for (int i = 0; i < matcherBuilders.length; i++) {
                    matcherIndices[i] = 0;
                    rangesLeft |= matcherIndices[i] < matcherBuilders[i].size();
                }
                int lastHi = 0;
                while (rangesLeft) {
                    int minLo = Integer.MAX_VALUE;
                    int minMb = -1;
                    for (int i = 0; i < matcherBuilders.length; i++) {
                        MatcherBuilder mb = matcherBuilders[i];
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
                    lastHi = matcherBuilders[minMb].getHi(matcherIndices[minMb]) + 1;
                    if (lastHi <= Character.MAX_VALUE) {
                        sortedRangesBuf.add((char) (lastHi));
                    }
                    matcherIndices[minMb]++;
                }
                if (lastHi != Character.MAX_VALUE + 1) {
                    rangeTreeSuccessorsBuf.add((short) -1);
                }
                // ====
                // TODO: Can be changed to

                // matchers = new CharMatcher[]{new AllTransitionsInOneTreeMatcher(
                // sortedRangesBuf.toArray(), rangeTreeSuccessorsBuf.toArray())};

                // with some minor changes in DFAStateNode. We'll keep this for testing purposes
                // for the moment.

                matchers = Arrays.copyOf(matchers, matchers.length + 1);
                matchers[matchers.length - 1] = new AllTransitionsInOneTreeMatcher(sortedRangesBuf.toArray(), rangeTreeSuccessorsBuf.toArray());

                // ====
            }

            short[] successors = s.getNumberOfSuccessors() > 0 ? new short[s.getNumberOfSuccessors()] : EMPTY_SHORT_ARRAY;
            short[] cgTransitions = null;
            short[] cgPrecedingTransitions = null;
            if (trackCaptureGroups) {
                cgTransitions = new short[s.getCaptureGroupTransitions().length];
                List<DFACaptureGroupTransitionBuilder> precedingTransitions = s.getPrecedingTransitions();
                assert !precedingTransitions.isEmpty();
                cgPrecedingTransitions = new short[precedingTransitions.size()];
                for (int i = 0; i < precedingTransitions.size(); i++) {
                    DFACaptureGroupTransitionBuilder transitionBuilder = precedingTransitions.get(i);
                    cgPrecedingTransitions[i] = transitionBuilder.toLazyTransition(cgTransitionIDCounter, compilationBuffer).getId();
                }
            }
            short loopToSelf = -1;
            for (int i = 0; i < successors.length - (s.hasBackwardPrefixState() ? 1 : 0); i++) {
                successors[i] = s.getSuccessors()[i].getId();
                if (successors[i] == s.getId()) {
                    loopToSelf = (short) i;
                }
                assert successors[i] >= 0 && successors[i] < ret.length;
                if (trackCaptureGroups) {
                    final DFACaptureGroupLazyTransitionNode transition = s.getCaptureGroupTransitions()[i].toLazyTransition(cgTransitionIDCounter, compilationBuffer);
                    cgTransitions[i] = transition.getId();
                    registerTransition(transition);
                }
            }
            if (s.hasBackwardPrefixState()) {
                successors[successors.length - 1] = s.getBackwardPrefixState();
            }
            boolean findSingleChar = matchers.length == 2 &&
                            matchers[0] instanceof SingleCharMatcher &&
                            matchers[1] instanceof AnyMatcher &&
                            successors[1] == s.getId();
            DFAStateNode stateNode;
            if (trackCaptureGroups) {
                stateNode = new CGTrackingDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), findSingleChar, s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers, cgTransitions, cgPrecedingTransitions);
            } else if (nfa.isTraceFinderNFA()) {
                stateNode = new TraceFinderDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), findSingleChar, s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers, s.getUnAnchoredResult(), s.getAnchoredResult());
            } else if (forward) {
                stateNode = new DFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), findSingleChar, s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers);
            } else {
                stateNode = new BackwardDFAStateNode(s.getId(), s.isFinalState(), s.isAnchoredFinalState(), findSingleChar, s.hasBackwardPrefixState(),
                                loopToSelf, successors, matchers);
            }
            ret[s.getId()] = stateNode;
        }
        return ret;
    }

    private static DFAAbstractStateNode[] tryMakeReducible(DFAAbstractStateNode[] states) {
        try {
            return DFANodeSplit.createReducibleGraph(states);
        } catch (DFANodeSplitBailoutException e) {
            return states;
        }
    }

    private void registerTransition(DFACaptureGroupLazyTransitionNode cgTransition) {
        transitions.add(cgTransition);
    }

    private void expandState(DFAStateNodeBuilder state) {
        if (pruneUnambiguousPaths && !state.isFinalStateSuccessor()) {
            PreCalculatedResultFactory result = null;
            byte resultIndex = 0;
            boolean allSameResult = true;
            assert !state.getNfaStateSet().isEmpty();
            for (NFAStateTransition transition : state.getNfaStateSet()) {
                NFAState nfaState = transition.getTarget(forward);
                assert nfaState.hasPossibleResults();
                for (int i : nfaState.getPossibleResults()) {
                    if (result == null) {
                        result = nfa.getPreCalculatedResults()[i];
                        resultIndex = (byte) i;
                    } else if (result != nfa.getPreCalculatedResults()[i]) {
                        allSameResult = false;
                        break;
                    }
                }
            }
            if (allSameResult) {
                state.setOverrideFinalState(true);
                state.setUnAnchoredResult(resultIndex);
                state.setSuccessors(EMPTY_SUCCESSORS_ARRAY);
                state.setMatcherBuilders(EMPTY_MATCHER_BUILDERS_ARRAY);
                return;
            }
        }
        expandDFAConnections.clear();
        for (NFAStateTransition transition : state.getNfaStateSet()) {
            NFAState nfaState = transition.getTarget(forward);
            if (nfaState instanceof NFAMatcherState) {
                expandDFAConnections.add(connectionBuilder((NFAMatcherState) nfaState));
            }
        }
        DFAStateTransitionBuilder[] successors = canonicalizer.run(expandDFAConnections, compilationBuffer);
        Arrays.sort(successors, Comparator.comparing(TransitionBuilder<NFATransitionSet>::getMatcherBuilder));
        DFAStateNodeBuilder[] successorStates = new DFAStateNodeBuilder[successors.length];
        MatcherBuilder[] matchers = new MatcherBuilder[successors.length];
        DFACaptureGroupTransitionBuilder[] captureGroupTransitions = trackCaptureGroups ? new DFACaptureGroupTransitionBuilder[successors.length] : null;
        for (int i = 0; i < successors.length; i++) {
            assert !successors[i].getTransitionSet().isEmpty();
            DFAStateNodeBuilder lookupSuccessorState = stateMap.get(successors[i].getTransitionSet());
            if (lookupSuccessorState == null) {
                successorStates[i] = createState(successors[i]);
                expansionQueue.push(successorStates[i]);
            } else {
                if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor()) && !lookupSuccessorState.isFinalStateSuccessor()) {
                    reScheduleFinalStateSuccessor(lookupSuccessorState);
                    expandDFAPruneTraverseCur.clear();
                    expandDFAPruneTraverseCur.add(lookupSuccessorState.getSuccessors());
                    while (!expandDFAPruneTraverseCur.isEmpty()) {
                        expandDFAPruneTraverseNext.clear();
                        for (DFAStateNodeBuilder[] tc : expandDFAPruneTraverseCur) {
                            for (DFAStateNodeBuilder s : tc) {
                                if (s.isFinalStateSuccessor()) {
                                    continue;
                                }
                                expandDFAPruneTraverseNext.add(s.getSuccessors());
                                reScheduleFinalStateSuccessor(s);
                            }
                        }
                        List<DFAStateNodeBuilder[]> tmp = expandDFAPruneTraverseCur;
                        expandDFAPruneTraverseCur = expandDFAPruneTraverseNext;
                        expandDFAPruneTraverseNext = tmp;
                    }
                }
                successorStates[i] = lookupSuccessorState;
            }
            if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                successorStates[i].setFinalStateSuccessor();
            }
            matchers[i] = successors[i].getMatcherBuilder();
            if (trackCaptureGroups) {
                captureGroupTransitions[i] = new DFACaptureGroupTransitionBuilder(nfa, successors[i], successorStates[i], false);
                successorStates[i].addPrecedingTransition(captureGroupTransitions[i]);
            }
        }
        state.setSuccessors(successorStates);
        state.setMatcherBuilders(matchers);
        state.setCaptureGroupTransitions(captureGroupTransitions);
    }

    private void reScheduleFinalStateSuccessor(DFAStateNodeBuilder finalStateSuccessor) {
        finalStateSuccessor.setFinalStateSuccessor();
        finalStateSuccessor.setOverrideFinalState(false);
        finalStateSuccessor.setUnAnchoredResult(finalStateSuccessor.getNfaStateSet().getPreCalculatedUnAnchoredResult());
        expansionQueue.push(finalStateSuccessor);
    }

    private DFAStateNodeBuilder lookupOrCreateState(DFAStateTransitionBuilder connection) {
        DFAStateNodeBuilder lookup = stateMap.get(connection.getTransitionSet());
        if (lookup == null) {
            lookup = createState(connection);
            expansionQueue.push(lookup);
        }
        return lookup;
    }

    private DFAStateNodeBuilder createState(DFAStateTransitionBuilder connection) {
        DFAStateNodeBuilder dfaState = createStateInner(connection.getTransitionSet());
        if (!forward) {
            if (dfaState.getNfaStateSet().containsPrefixStates()) {
                NFATransitionSet prefixStateSet = NFATransitionSet.create(nfa, false, false);
                for (NFAStateTransition transition : dfaState.getNfaStateSet()) {
                    if (transition.getSource().hasPrefixStates()) {
                        prefixStateSet.add(transition);
                    }
                }
                DFAStateNodeBuilder lookupPrefixState = stateMap.get(prefixStateSet);
                if (lookupPrefixState == null) {
                    lookupPrefixState = createStateInner(prefixStateSet);
                    expansionQueue.push(lookupPrefixState);
                }
                dfaState.setBackwardPrefixState(lookupPrefixState.getId());
                lookupPrefixState.setBackwardPrefixState(lookupPrefixState.getId());
            }
        }
        return dfaState;
    }

    private DFAStateNodeBuilder createStateInner(NFATransitionSet transitionSet) {
        if (transitionSet.size() > maxNumberOfNfaStates) {
            maxNumberOfNfaStates = transitionSet.size();
            if (maxNumberOfNfaStates > TRegexOptions.TRegexMaxNumberOfNFAStatesInOneDFAState) {
                throw new UnsupportedRegexException("DFA state size explosion");
            }
        }
        DFAStateNodeBuilder dfaState = new DFAStateNodeBuilder(nextID++, transitionSet);
        stateMap.put(transitionSet, dfaState);
        if (stateMap.size() + (forward ? expansionQueue.size() : 0) > TRegexOptions.TRegexMaxDFASize) {
            throw new UnsupportedRegexException((forward ? (trackCaptureGroups ? "CG" : "Forward") : "Backward") + " DFA explosion");
        }
        return dfaState;
    }

    private DFAStateTransitionBuilder connectionBuilder(NFAMatcherState matcherState) {
        return new DFAStateTransitionBuilder(matcherState.getMatcherBuilder(), forward ? matcherState.getNext() : matcherState.getPrev(), nfa, forward, forward);
    }

    private void dumpDFA() {
        System.out.println("DFA:");
        for (DFAStateNodeBuilder s : stateMap.values()) {
            System.out.println(s.toTable());
        }
        System.out.println();
    }
}
