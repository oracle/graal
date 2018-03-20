/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplitBailoutException;
import com.oracle.truffle.regex.tregex.parser.Counter;
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

    private final Map<NFATransitionSet, DFAStateNodeBuilder> stateMap = new HashMap<>();
    private final ArrayDeque<DFAStateNodeBuilder> expansionQueue = new ArrayDeque<>();

    private short nextID = 1;
    private final Counter transitionIDCounter = new Counter.ThresholdCounter(Integer.MAX_VALUE, "too many transitions");
    private final Counter cgTransitionIDCounter = new Counter.ThresholdCounter(Short.MAX_VALUE, "too many capture group transitions");
    private int maxNumberOfNfaStates = 0;

    private short[] entryStates;
    private short[] initCaptureGroups;
    private final List<DFACaptureGroupLazyTransitionNode> captureGroupTransitions = new ArrayList<>();
    private final DFATransitionCanonicalizer canonicalizer;

    private final List<DFAStateTransitionBuilder> expandDFATransitions = new ArrayList<>();
    private List<DFAStateTransitionBuilder[]> expandDFAPruneTraverseCur;
    private List<DFAStateTransitionBuilder[]> expandDFAPruneTraverseNext;

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
            transitionsArray = new DFACaptureGroupLazyTransitionNode[captureGroupTransitions.size()];
            for (DFACaptureGroupLazyTransitionNode t : captureGroupTransitions) {
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
                DFAStateTransitionBuilder unAnchoredEntryTransition = new DFAStateTransitionBuilder(null, unAnchoredEntryStateSet);
                entryStates[numberOfEntryPoints + i] = createEntryState(unAnchoredEntryTransition);
                if (trackCaptureGroups) {
                    unAnchoredInitialCGBuilder[i] = new DFACaptureGroupTransitionBuilder(nfa, unAnchoredEntryTransition, true);
                    unAnchoredEntryTransition.getTarget().addPrecedingTransition(unAnchoredInitialCGBuilder[i]);
                }
            }
            DFAStateTransitionBuilder anchoredEntryTransition = new DFAStateTransitionBuilder(null, anchoredEntryStateSet);
            entryStates[i] = createEntryState(anchoredEntryTransition);
            if (trackCaptureGroups) {
                anchoredInitialCGBuilder[i] = new DFACaptureGroupTransitionBuilder(nfa, anchoredEntryTransition, true);
                anchoredEntryTransition.getTarget().addPrecedingTransition(anchoredInitialCGBuilder[i]);
            }
        }
    }

    private void createInitialStatesBackward() {
        NFATransitionSet anchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseAnchoredEntry().getPrev());
        entryStates = new short[]{-1, -1};
        if (nfa.hasReverseUnAnchoredEntry()) {
            anchoredEntry.addAll(nfa.getReverseUnAnchoredEntry().getPrev());
            NFATransitionSet unAnchoredEntry = NFATransitionSet.create(nfa, false, false, nfa.getReverseUnAnchoredEntry().getPrev());
            entryStates[1] = createEntryState(new DFAStateTransitionBuilder(null, unAnchoredEntry));
        }
        entryStates[0] = createEntryState(new DFAStateTransitionBuilder(null, anchoredEntry));
    }

    private short createEntryState(DFAStateTransitionBuilder transition) {
        DFAStateNodeBuilder lookup = stateMap.get(transition.getTransitionSet());
        if (lookup == null) {
            lookup = createState(transition);
            expansionQueue.push(lookup);
        }
        transition.setTarget(lookup);
        return lookup.getId();
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
                DFAStateTransitionBuilder[] transitions = s.getTransitions();
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
                cgTransitions = new short[s.getTransitions().length];
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
                successors[i] = s.getTransitions()[i].getTarget().getId();
                if (successors[i] == s.getId()) {
                    loopToSelf = (short) i;
                }
                assert successors[i] >= 0 && successors[i] < ret.length;
                if (trackCaptureGroups) {
                    final DFACaptureGroupLazyTransitionNode transition = s.getTransitions()[i].getCaptureGroupTransition().toLazyTransition(cgTransitionIDCounter, compilationBuffer);
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
        captureGroupTransitions.add(cgTransition);
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
                state.setTransitions(EMPTY_TRANSITIONS_ARRAY);
                return;
            }
        }
        expandDFATransitions.clear();
        for (NFAStateTransition transition : state.getNfaStateSet()) {
            NFAState nfaState = transition.getTarget(forward);
            if (nfaState instanceof NFAMatcherState) {
                expandDFATransitions.add(transitionBuilder((NFAMatcherState) nfaState));
            }
        }
        DFAStateTransitionBuilder[] transitions = canonicalizer.run(expandDFATransitions, compilationBuffer);
        Arrays.sort(transitions, Comparator.comparing(TransitionBuilder::getMatcherBuilder));
        for (DFAStateTransitionBuilder transition : transitions) {
            assert !transition.getTransitionSet().isEmpty();
            transition.setId(transitionIDCounter.inc());
            transition.setSource(state);
            DFAStateNodeBuilder lookupSuccessorState = stateMap.get(transition.getTransitionSet());
            if (lookupSuccessorState == null) {
                transition.setTarget(createState(transition));
                expansionQueue.push(transition.getTarget());
            } else {
                if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor()) && !lookupSuccessorState.isFinalStateSuccessor()) {
                    reScheduleFinalStateSuccessor(lookupSuccessorState);
                    expandDFAPruneTraverseCur.clear();
                    expandDFAPruneTraverseCur.add(lookupSuccessorState.getTransitions());
                    while (!expandDFAPruneTraverseCur.isEmpty()) {
                        expandDFAPruneTraverseNext.clear();
                        for (DFAStateTransitionBuilder[] cur : expandDFAPruneTraverseCur) {
                            for (DFAStateTransitionBuilder t : cur) {
                                if (t.getTarget().isFinalStateSuccessor()) {
                                    continue;
                                }
                                expandDFAPruneTraverseNext.add(t.getTarget().getTransitions());
                                reScheduleFinalStateSuccessor(t.getTarget());
                            }
                        }
                        List<DFAStateTransitionBuilder[]> tmp = expandDFAPruneTraverseCur;
                        expandDFAPruneTraverseCur = expandDFAPruneTraverseNext;
                        expandDFAPruneTraverseNext = tmp;
                    }
                }
                transition.setTarget(lookupSuccessorState);
            }
            if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                transition.getTarget().setFinalStateSuccessor();
            }
            if (trackCaptureGroups) {
                transition.setCaptureGroupTransition(new DFACaptureGroupTransitionBuilder(nfa, transition, false));
                transition.getTarget().addPrecedingTransition(transition.getCaptureGroupTransition());
            }
        }
        state.setTransitions(transitions);
    }

    private void reScheduleFinalStateSuccessor(DFAStateNodeBuilder finalStateSuccessor) {
        finalStateSuccessor.setFinalStateSuccessor();
        finalStateSuccessor.setOverrideFinalState(false);
        finalStateSuccessor.setUnAnchoredResult(finalStateSuccessor.getNfaStateSet().getPreCalculatedUnAnchoredResult());
        expansionQueue.push(finalStateSuccessor);
    }

    private DFAStateNodeBuilder createState(DFAStateTransitionBuilder transition) {
        DFAStateNodeBuilder dfaState = createStateInner(transition.getTransitionSet());
        if (!forward) {
            if (dfaState.getNfaStateSet().containsPrefixStates()) {
                NFATransitionSet prefixStateSet = NFATransitionSet.create(nfa, false, false);
                for (NFAStateTransition nfaTransition : dfaState.getNfaStateSet()) {
                    if (nfaTransition.getSource().hasPrefixStates()) {
                        prefixStateSet.add(nfaTransition);
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

    private DFAStateTransitionBuilder transitionBuilder(NFAMatcherState matcherState) {
        return new DFAStateTransitionBuilder(matcherState.getMatcherBuilder(), forward ? matcherState.getNext() : matcherState.getPrev(), nfa, forward, forward);
    }

    @Override
    public JsonValue toJson() {
        DFAStateNodeBuilder[] stateList = new DFAStateNodeBuilder[nextID];
        DFAStateTransitionBuilder[] transitionList = new DFAStateTransitionBuilder[transitionIDCounter.getCount()];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            stateList[s.getId()] = s;
            for (DFAStateTransitionBuilder t : s.getTransitions()) {
                transitionList[t.getId()] = t;
            }
        }
        return Json.obj(Json.prop("nfa", nfa),
                        Json.prop("dfa", Json.obj(
                                        Json.prop("states", Arrays.stream(stateList)),
                                        Json.prop("transitions", Arrays.stream(transitionList)),
                                        Json.prop("entryStates", Json.array(entryStates)))));
    }
}
