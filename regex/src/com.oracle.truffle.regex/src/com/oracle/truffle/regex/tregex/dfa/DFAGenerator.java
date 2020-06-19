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

package com.oracle.truffle.regex.tregex.dfa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.CompressedCodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.TRegexCompilationRequest;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.tregex.nodes.dfa.BackwardDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupLazyTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition.IndexOperation;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAFindInnerLiteralStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCG;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCGTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorDebugRecorder;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.dfa.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplit;
import com.oracle.truffle.regex.tregex.nodesplitter.DFANodeSplitBailoutException;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.AddToSetVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.BitSets;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

public final class DFAGenerator implements JsonConvertible {

    private static final DFAStateTransitionBuilder[] EMPTY_TRANSITIONS_ARRAY = new DFAStateTransitionBuilder[0];
    private static final short[] EMPTY_SHORT_ARRAY = {};

    private final TRegexCompilationRequest compilationReqest;
    private final NFA nfa;
    private final TRegexDFAExecutorProperties executorProps;
    private final CompilationBuffer compilationBuffer;
    private final RegexOptions engineOptions;

    private final boolean pruneUnambiguousPaths;

    private final Map<DFAStateNodeBuilder, DFAStateNodeBuilder> stateMap = new HashMap<>();
    private final ArrayDeque<DFAStateNodeBuilder> expansionQueue = new ArrayDeque<>();
    private DFAStateNodeBuilder[] stateIndexMap = null;

    private short nextID = 1;
    private final DFAStateNodeBuilder lookupDummyState;
    private final Counter transitionIDCounter = new Counter.ThresholdCounter(Integer.MAX_VALUE, "too many transitions");
    private final Counter cgPartialTransitionIDCounter = new Counter.ThresholdCounter(Integer.MAX_VALUE, "too many partial transitions");
    private int maxNumberOfNfaStates = 1;
    private boolean hasAmbiguousStates = false;
    private boolean doSimpleCG = false;
    private boolean simpleCGMustCopy = false;

    private DFAStateNodeBuilder[] entryStates;
    private final DFACaptureGroupTransitionBuilder initialCGTransition;
    private DFACaptureGroupLazyTransition[] captureGroupTransitions = null;
    private final List<DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo> cgPartialTransitions;
    private final DFATransitionCanonicalizer canonicalizer;

    private List<DFAStateTransitionBuilder[]> bfsTraversalCur;
    private List<DFAStateTransitionBuilder[]> bfsTraversalNext;
    private EconomicMap<Integer, DFAAbstractStateNode> stateReplacements;

    private final Matchers.Builder matchersBuilder;

    public DFAGenerator(TRegexCompilationRequest compilationReqest, NFA nfa, TRegexDFAExecutorProperties executorProps, CompilationBuffer compilationBuffer, RegexOptions engineOptions) {
        this.compilationReqest = compilationReqest;
        this.nfa = nfa;
        this.executorProps = executorProps;
        this.pruneUnambiguousPaths = executorProps.isBackward() && nfa.isTraceFinderNFA() && nfa.hasReverseUnAnchoredEntry();
        this.compilationBuffer = compilationBuffer;
        this.engineOptions = engineOptions;
        this.cgPartialTransitions = debugMode() ? new ArrayList<>() : null;
        this.bfsTraversalCur = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.bfsTraversalNext = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.initialCGTransition = isGenericCG() ? new DFACaptureGroupTransitionBuilder(null, null, null) : null;
        this.transitionIDCounter.inc(); // zero is reserved for initialCGTransition
        this.cgPartialTransitionIDCounter.inc(); // zero is reserved for static empty instance
        this.lookupDummyState = new DFAStateNodeBuilder((short) -1, null, false, false, isForward());
        if (debugMode()) {
            registerCGPartialTransitionDebugInfo(new DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo(DFACaptureGroupPartialTransition.getEmptyInstance()));
        }
        assert !nfa.isDead();
        this.canonicalizer = new DFATransitionCanonicalizer(this);
        this.matchersBuilder = nfa.getAst().getEncoding().createMatchersBuilder();
    }

    public NFA getNfa() {
        return nfa;
    }

    public DFAStateNodeBuilder[] getEntryStates() {
        return entryStates;
    }

    private DFAStateNodeBuilder getUnanchoredInitialState() {
        return entryStates[nfa.getAnchoredEntry().length];
    }

    public Map<DFAStateNodeBuilder, DFAStateNodeBuilder> getStateMap() {
        return stateMap;
    }

    public TRegexDFAExecutorProperties getProps() {
        return executorProps;
    }

    public boolean isForward() {
        return executorProps.isForward();
    }

    public boolean isGenericCG() {
        return executorProps.isGenericCG();
    }

    public boolean isSearching() {
        return executorProps.isSearching();
    }

    private RegexOptions getOptions() {
        return nfa.getAst().getOptions();
    }

    public RegexOptions getEngineOptions() {
        return engineOptions;
    }

    private Encoding getEncoding() {
        return nfa.getAst().getEncoding();
    }

    private DFAStateNodeBuilder[] getStateIndexMap() {
        if (stateIndexMap == null) {
            createStateIndexMap(nextID);
        }
        return stateIndexMap;
    }

    public DFAStateNodeBuilder getState(short stateNodeID) {
        assert debugMode();
        getStateIndexMap();
        return stateIndexMap[stateNodeID];
    }

    private void createStateIndexMap(int size) {
        assert debugMode();
        stateIndexMap = new DFAStateNodeBuilder[size];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            stateIndexMap[s.getId()] = s;
        }
    }

    public void nodeSplitSetNewDFASize(int size) {
        assert debugMode();
        assert stateIndexMap == null;
        createStateIndexMap(size);
    }

    public void nodeSplitRegisterDuplicateState(short oldID, short newID) {
        assert debugMode();
        DFAStateNodeBuilder copy = stateIndexMap[oldID].createNodeSplitCopy(newID);
        stateIndexMap[newID] = copy;
        for (DFAStateTransitionBuilder t : copy.getSuccessors()) {
            t.setId(transitionIDCounter.inc());
            t.setSource(copy);
        }
    }

    public void nodeSplitUpdateSuccessors(short stateID, short[] newSuccessors) {
        assert debugMode();
        assert stateIndexMap[stateID] != null;
        stateIndexMap[stateID].nodeSplitUpdateSuccessors(newSuccessors, stateIndexMap);
    }

    public Counter getCgPartialTransitionIDCounter() {
        return cgPartialTransitionIDCounter;
    }

    public void registerCGPartialTransitionDebugInfo(DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo partialTransition) {
        if (cgPartialTransitions.size() == partialTransition.getNode().getId()) {
            cgPartialTransitions.add(partialTransition);
        } else {
            assert partialTransition.getNode() == cgPartialTransitions.get(partialTransition.getNode().getId()).getNode();
        }
    }

    private boolean needBFSTraversalLists() {
        return pruneUnambiguousPaths || nfa.getAst().getProperties().hasInnerLiteral();
    }

    private void bfsExpand(DFAStateNodeBuilder s) {
        if (s.getSuccessors() != null) {
            bfsTraversalNext.add(s.getSuccessors());
        }
    }

    private void bfsSwapLists() {
        List<DFAStateTransitionBuilder[]> tmp = bfsTraversalCur;
        bfsTraversalCur = bfsTraversalNext;
        bfsTraversalNext = tmp;
    }

    /**
     * Calculates the DFA. Run this method before calling {@link #createDFAExecutor()} or
     * {@link #toJson()} or passing the generator object to
     * {@link com.oracle.truffle.regex.tregex.util.DFAExport}.
     */
    @TruffleBoundary
    public void calcDFA() {
        if (isForward()) {
            createInitialStatesForward();
        } else {
            createInitialStatesBackward();
        }
        while (!expansionQueue.isEmpty()) {
            expandState(expansionQueue.pop());
        }
        optimizeDFA();
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
        if (isGenericCG()) {
            int maxNumberOfEntryStateSuccessors = 0;
            for (DFAStateNodeBuilder entryState : entryStates) {
                if (entryState == null) {
                    continue;
                }
                maxNumberOfEntryStateSuccessors = Math.max(entryState.getSuccessors().length, maxNumberOfEntryStateSuccessors);
            }
            captureGroupTransitions = new DFACaptureGroupLazyTransition[transitionIDCounter.getCount()];
            DFACaptureGroupPartialTransition[] partialTransitions = new DFACaptureGroupPartialTransition[maxNumberOfEntryStateSuccessors];
            Arrays.fill(partialTransitions, DFACaptureGroupPartialTransition.getEmptyInstance());
            DFACaptureGroupLazyTransition emptyInitialTransition = new DFACaptureGroupLazyTransition(
                            (short) 0, partialTransitions,
                            DFACaptureGroupPartialTransition.getEmptyInstance(),
                            DFACaptureGroupPartialTransition.getEmptyInstance());
            registerCGTransition(emptyInitialTransition);
            initialCGTransition.setLazyTransition(emptyInitialTransition);
        }
        DFAAbstractStateNode[] states = createDFAExecutorStates();
        assert states[0] == null;
        short[] entryStateIDs = new short[entryStates.length];
        for (int i = 0; i < entryStates.length; i++) {
            if (entryStates[i] == null) {
                entryStateIDs[i] = -1;
            } else {
                entryStateIDs[i] = (short) entryStates[i].getId();
            }
        }
        states[0] = new DFAInitialStateNode(entryStateIDs, isSearching(), isGenericCG());
        if (TRegexOptions.TRegexEnableNodeSplitter) {
            states = tryMakeReducible(states);
        }
        executorProps.setSimpleCG(doSimpleCG);
        executorProps.setSimpleCGMustCopy(simpleCGMustCopy);
        return new TRegexDFAExecutorNode(executorProps, maxNumberOfNfaStates, states, captureGroupTransitions, TRegexDFAExecutorDebugRecorder.create(engineOptions, this));
    }

    private void createInitialStatesForward() {
        final int numberOfEntryPoints = nfa.getAnchoredEntry().length;
        entryStates = new DFAStateNodeBuilder[numberOfEntryPoints * 2];
        nfa.setInitialLoopBack(isSearching() && !nfa.getAst().getFlags().isSticky());
        for (int i = 0; i < numberOfEntryPoints; i++) {
            if (nfa.getUnAnchoredEntry()[i].getTarget().getSuccessors().length == 0) {
                entryStates[i] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getAnchoredEntry()[i])));
                entryStates[numberOfEntryPoints + i] = null;
            } else {
                entryStates[i] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getAnchoredEntry()[i], nfa.getUnAnchoredEntry()[i])));
                entryStates[numberOfEntryPoints + i] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getUnAnchoredEntry()[i])));
            }
        }
    }

    private void createInitialStatesBackward() {
        entryStates = new DFAStateNodeBuilder[]{null, null};
        if (nfa.hasReverseUnAnchoredEntry()) {
            entryStates[0] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getReverseAnchoredEntry(), nfa.getReverseUnAnchoredEntry())));
            entryStates[1] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getReverseUnAnchoredEntry())));
        } else {
            entryStates[0] = createInitialState(createTransitionBuilder(createNFATransitionSet(nfa.getReverseAnchoredEntry())));
            entryStates[1] = null;
        }
    }

    private DFAStateNodeBuilder createInitialState(DFAStateTransitionBuilder transition) {
        DFAStateNodeBuilder lookup = lookupState(transition.getTransitionSet(), false);
        if (lookup == null) {
            lookup = createState(transition.getTransitionSet(), false, true);
            lookup.updateFinalStateData(this);
            if (isGenericCG()) {
                lookup.incPredecessors();
            }
        }
        transition.setTarget(lookup);
        return lookup;
    }

    private void expandState(DFAStateNodeBuilder state) {
        if (pruneUnambiguousPaths && tryPruneTraceFinderState(state)) {
            return;
        }
        boolean anyPrefixStateSuccessors = false;
        boolean allPrefixStateSuccessors = true;
        outer: for (NFAStateTransition transition : state.getNfaTransitionSet().getTransitions()) {
            NFAState nfaState = transition.getTarget(isForward());
            for (NFAStateTransition nfaTransition : nfaState.getSuccessors(isForward())) {
                NFAState target = nfaTransition.getTarget(isForward());
                if (!target.isFinalState(isForward()) && (!state.isBackwardPrefixState() || target.hasPrefixStates())) {
                    anyPrefixStateSuccessors |= target.hasPrefixStates();
                    allPrefixStateSuccessors &= target.hasPrefixStates();
                    canonicalizer.addArgument(nfaTransition, isForward() ? nfaTransition.getCodePointSet() : target.getCharSet());
                } else if (isForward() && target.isUnAnchoredFinalState()) {
                    assert target == nfa.getReverseUnAnchoredEntry().getSource();
                    break outer;
                }
            }
        }
        if (!isForward() && anyPrefixStateSuccessors) {
            if (allPrefixStateSuccessors) {
                state.setBackwardPrefixState((short) state.getId());
            } else {
                assert !state.isBackwardPrefixState();
                DFAStateNodeBuilder lookup = lookupState(state.getNfaTransitionSet(), true);
                if (lookup == null) {
                    lookup = createState(state.getNfaTransitionSet(), true, false);
                }
                state.setBackwardPrefixState((short) lookup.getId());
            }
        }
        DFAStateTransitionBuilder[] transitions = canonicalizer.run(compilationBuffer);
        Arrays.sort(transitions, Comparator.comparing(TransitionBuilder::getCodePointSet));
        for (DFAStateTransitionBuilder transition : transitions) {
            assert !transition.getTransitionSet().isEmpty();
            transition.setId(transitionIDCounter.inc());
            transition.setSource(state);
            DFAStateNodeBuilder successorState = lookupState(transition.getTransitionSet(), false);
            if (successorState == null) {
                successorState = createState(transition.getTransitionSet(), false, false);
            } else if (pruneUnambiguousPaths) {
                reScheduleFinalStateSuccessors(state, successorState);
            }
            if (pruneUnambiguousPaths && (state.isUnAnchoredFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                successorState.setFinalStateSuccessor();
            }
            transition.setTarget(successorState);
            successorState.updateFinalStateData(this);
            if (isGenericCG()) {
                transition.getTarget().incPredecessors();
            }
            if (state.isUnAnchoredFinalState() && !successorState.isFinalState()) {
                simpleCGMustCopy = true;
            }
        }
        state.setSuccessors(transitions);
    }

    private DFAStateTransitionBuilder createTransitionBuilder(TransitionSet<NFA, NFAState, NFAStateTransition> transitionSet) {
        return createTransitionBuilder(null, transitionSet);
    }

    private DFAStateTransitionBuilder createTransitionBuilder(CodePointSet matcherBuilder, TransitionSet<NFA, NFAState, NFAStateTransition> transitionSet) {
        if (isGenericCG()) {
            return new DFACaptureGroupTransitionBuilder(matcherBuilder, transitionSet, this);
        } else {
            return new DFAStateTransitionBuilder(transitionSet, matcherBuilder);
        }
    }

    private TransitionSet<NFA, NFAState, NFAStateTransition> createNFATransitionSet(NFAStateTransition initialTransition) {
        return new TransitionSet<>(new NFAStateTransition[]{initialTransition}, StateSet.create(nfa, initialTransition.getTarget(isForward())));
    }

    private TransitionSet<NFA, NFAState, NFAStateTransition> createNFATransitionSet(NFAStateTransition t1, NFAStateTransition t2) {
        StateSet<NFA, NFAState> targetStateSet = StateSet.create(nfa, t1.getTarget(isForward()));
        targetStateSet.add(t2.getTarget(isForward()));
        return new TransitionSet<>(new NFAStateTransition[]{t1, t2}, targetStateSet);
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
     * @return {@code true} if the state was pruned, {@code false} otherwise.
     * @see com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator
     */
    private boolean tryPruneTraceFinderState(DFAStateNodeBuilder state) {
        assert nfa.isTraceFinderNFA();
        if (state.isFinalStateSuccessor()) {
            return false;
        }
        PreCalculatedResultFactory result = null;
        int resultIndex = -1;
        assert !state.getNfaTransitionSet().isEmpty();
        for (NFAStateTransition transition : state.getNfaTransitionSet().getTransitions()) {
            NFAState nfaState = transition.getTarget(isForward());
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
            state.setSuccessors(EMPTY_TRANSITIONS_ARRAY);
            return true;
        }
        return false;
    }

    private void reScheduleFinalStateSuccessors(DFAStateNodeBuilder state, DFAStateNodeBuilder successorState) {
        assert nfa.isTraceFinderNFA();
        if ((state.isUnAnchoredFinalState() || state.isFinalStateSuccessor()) && !successorState.isFinalStateSuccessor()) {
            reScheduleFinalStateSuccessor(successorState);
            bfsTraversalCur.clear();
            if (successorState.getSuccessors() != null) {
                bfsTraversalCur.add(successorState.getSuccessors());
            }
            while (!bfsTraversalCur.isEmpty()) {
                bfsTraversalNext.clear();
                for (DFAStateTransitionBuilder[] cur : bfsTraversalCur) {
                    for (DFAStateTransitionBuilder t : cur) {
                        if (t.getTarget().isFinalStateSuccessor()) {
                            continue;
                        }
                        bfsExpand(t.getTarget());
                        reScheduleFinalStateSuccessor(t.getTarget());
                    }
                }
                bfsSwapLists();
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

    private DFAStateNodeBuilder lookupState(TransitionSet<NFA, NFAState, NFAStateTransition> transitionSet, boolean isBackWardPrefixState) {
        lookupDummyState.setNfaTransitionSet(transitionSet);
        lookupDummyState.setIsBackwardPrefixState(isBackWardPrefixState);
        return stateMap.get(lookupDummyState);
    }

    private DFAStateNodeBuilder createState(TransitionSet<NFA, NFAState, NFAStateTransition> transitionSet, boolean isBackwardPrefixState, boolean isInitialState) {
        assert stateIndexMap == null : "state index map created before dfa generation!";
        DFAStateNodeBuilder dfaState = new DFAStateNodeBuilder(nextID++, transitionSet, isBackwardPrefixState, isInitialState, isForward());
        stateMap.put(dfaState, dfaState);
        if (stateMap.size() + (isForward() ? expansionQueue.size() : 0) > TRegexOptions.TRegexMaxDFASize) {
            throw new UnsupportedRegexException((isForward() ? (isGenericCG() ? "CG" : "Forward") : "Backward") + " DFA explosion");
        }
        if (!hasAmbiguousStates && (transitionSet.size() > 2 || (transitionSet.size() == 2 && transitionSet.getTransition(1) != nfa.getInitialLoopBackTransition()))) {
            hasAmbiguousStates = true;
        }
        expansionQueue.push(dfaState);
        return dfaState;
    }

    private void optimizeDFA() {
        RegexProperties props = nfa.getAst().getProperties();

        doSimpleCG = (isForward() || !nfa.getAst().getRoot().hasLoops()) &&
                        executorProps.isAllowSimpleCG() &&
                        !hasAmbiguousStates &&
                        !nfa.isTraceFinderNFA() &&
                        !isGenericCG() &&
                        (isSearching() || props.hasCaptureGroups()) &&
                        (props.hasAlternations() || props.hasLookAroundAssertions());

        // inner-literal-optimization
        if (isForward() && isSearching() && !isGenericCG() && !nfa.getAst().getFlags().isSticky() && props.hasInnerLiteral()) {
            int literalEnd = props.getInnerLiteralEnd();
            int literalStart = props.getInnerLiteralStart();
            Sequence rootSeq = nfa.getAst().getRoot().getFirstAlternative();

            // find all parser tree nodes of the prefix
            StateSet<RegexAST, RegexASTNode> prefixAstNodes = StateSet.create(nfa.getAst());
            for (int i = 0; i < literalStart; i++) {
                AddToSetVisitor.addCharacterClasses(prefixAstNodes, rootSeq.getTerms().get(i));
            }

            // find NFA states of the prefix and the beginning and end of the literal
            StateSet<NFA, NFAState> prefixNFAStates = StateSet.create(nfa);
            prefixNFAStates.add(nfa.getUnAnchoredInitialState());
            NFAState literalFirstState = null;
            NFAState literalLastState = null;
            for (NFAState s : nfa.getStates()) {
                if (s == null) {
                    continue;
                }
                if (!s.getStateSet().isEmpty() && prefixAstNodes.containsAll(s.getStateSet())) {
                    prefixNFAStates.add(s);
                }
                if (s.getStateSet().contains(rootSeq.getTerms().get(literalStart))) {
                    if (literalFirstState != null) {
                        // multiple look-ahead assertions in the prefix are unsupported
                        return;
                    }
                    literalFirstState = s;
                }
                if (s.getStateSet().contains(rootSeq.getTerms().get(literalEnd - 1))) {
                    if (literalLastState != null) {
                        // multiple look-ahead assertions in the prefix are unsupported
                        return;
                    }
                    literalLastState = s;
                }
            }
            assert literalFirstState != null;
            assert literalLastState != null;

            // find the literal's beginning and end in the DFA
            DFAStateNodeBuilder literalFirstDFAState = null;
            DFAStateNodeBuilder literalLastDFAState = null;
            DFAStateNodeBuilder unanchoredInitialState = getUnanchoredInitialState();
            CompilationFinalBitSet visited = new CompilationFinalBitSet(nextID);
            visited.set(unanchoredInitialState.getId());
            bfsTraversalCur.clear();
            bfsTraversalCur.add(unanchoredInitialState.getSuccessors());
            while (!bfsTraversalCur.isEmpty()) {
                bfsTraversalNext.clear();
                for (DFAStateTransitionBuilder[] cur : bfsTraversalCur) {
                    for (DFAStateTransitionBuilder t : cur) {
                        DFAStateNodeBuilder target = t.getTarget();
                        if (visited.get(target.getId())) {
                            continue;
                        }
                        visited.set(target.getId());
                        StateSet<NFA, NFAState> targetStateSet = target.getNfaTransitionSet().getTargetStateSet();
                        if (literalFirstDFAState == null && targetStateSet.contains(literalFirstState)) {
                            literalFirstDFAState = target;
                        }
                        if (targetStateSet.contains(literalLastState)) {
                            literalLastDFAState = target;
                            bfsTraversalNext.clear();
                            break;
                        }
                        bfsExpand(target);
                    }
                }
                bfsSwapLists();
            }
            assert literalFirstDFAState != null;
            assert literalLastDFAState != null;

            TRegexDFAExecutorNode prefixMatcher = null;

            if (literalStart > 0) {
                /*
                 * If the prefix is of variable length, we must check if it is possible to match the
                 * literal beginning from the prefix, and bail out if that is the case. Otherwise,
                 * the resulting DFA would produce wrong results on e.g. /a?aa/. In this exemplary
                 * expression, the DFA would match the literal "aa" and immediately jump to the
                 * successor of the literal's last state, which is the final state. It would never
                 * match "aaa".
                 */
                if (rootSeq.getTerms().get(literalStart - 1).getMinPath() < rootSeq.getTerms().get(literalStart - 1).getMaxPath()) {
                    nfa.setInitialLoopBack(false);
                    if (innerLiteralMatchesPrefix(prefixNFAStates)) {
                        nfa.setInitialLoopBack(true);
                        return;
                    }
                    nfa.setInitialLoopBack(true);
                }

                /*
                 * Redirect all transitions to prefix states to the initial state. The prefix states
                 * are all covered/duplicated by the backward matcher, so instead of going to the
                 * original prefix states (where "prefix" in this case means the part of the regex
                 * preceding the literal), we can go to the DFAFindInnerLiteralStateNode that is
                 * inserted as the initial state, and benefit from the vectorized search for the
                 * literal. An example for why this is important: Given the regex /.?abc[0-9]/, the
                 * "inner literal" is abc, and the prefix is .?. The dot (.) matches everything
                 * except newlines. When performing a search, the DFA would first search for abc,
                 * then check that the preceding character is no newline, and then check if the
                 * succeeding character is a digit ([0-9]). If the succeeding character is not a
                 * digit, the DFA would check if it is a newline, and only if it is, it would go
                 * back to the initial state and start the optimized search for abc again. In all
                 * other cases, it would go to the state representing the dot, which would loop to
                 * itself as long as no newline is encountered, and the optimized search would
                 * hardly ever trigger after the first occurrence of the literal.
                 */
                CodePointSetAccumulator acc = compilationBuffer.getCodePointSetAccumulator1();
                for (DFAStateNodeBuilder s : stateMap.values()) {
                    acc.clear();
                    if (!prefixNFAStates.containsAll(s.getNfaTransitionSet().getTargetStateSet())) {
                        DFAStateTransitionBuilder mergedTransition = null;
                        ObjectArrayBuffer<DFAStateTransitionBuilder> newTransitions = null;
                        for (int i = 0; i < s.getSuccessors().length; i++) {
                            DFAStateTransitionBuilder t = s.getSuccessors()[i];
                            if (prefixNFAStates.containsAll(t.getTarget().getNfaTransitionSet().getTargetStateSet())) {
                                if (mergedTransition == null) {
                                    t.setTarget(unanchoredInitialState);
                                    mergedTransition = t;
                                } else if (newTransitions == null) {
                                    newTransitions = compilationBuffer.getObjectBuffer1();
                                    newTransitions.addAll(s.getSuccessors(), 0, i);
                                    acc.addSet(mergedTransition.getCodePointSet());
                                    acc.addSet(t.getCodePointSet());
                                } else {
                                    acc.addSet(t.getCodePointSet());
                                }
                            } else if (newTransitions != null) {
                                newTransitions.add(t);
                            }
                        }
                        // GR-17760: newTransitions != null implies mergedTransition != null, but
                        // the "Fortify" tool does not see that
                        if (newTransitions != null && mergedTransition != null) {
                            mergedTransition.setMatcherBuilder(acc.toCodePointSet());
                            s.setSuccessors(newTransitions.toArray(new DFAStateTransitionBuilder[newTransitions.length()]));
                            Arrays.sort(s.getSuccessors(), Comparator.comparing(TransitionBuilder::getCodePointSet));
                        }
                    }
                }

                // generate a reverse matcher for the prefix
                nfa.setInitialLoopBack(false);
                NFAState reverseAnchoredInitialState = nfa.getReverseAnchoredEntry().getSource();
                NFAState reverseUnAnchoredInitialState = nfa.getReverseUnAnchoredEntry().getSource();
                nfa.getReverseAnchoredEntry().setSource(literalFirstState);
                nfa.getReverseUnAnchoredEntry().setSource(literalFirstState);
                prefixMatcher = compilationReqest.createDFAExecutor(nfa, new TRegexDFAExecutorProperties(false, false, false, doSimpleCG, getOptions().isRegressionTestMode(),
                                rootSeq.getTerms().get(literalStart - 1).getMinPath()), "innerLiteralPrefix");
                prefixMatcher.setRoot(compilationReqest.getRoot());
                prefixMatcher.getProperties().setSimpleCGMustCopy(false);
                doSimpleCG = doSimpleCG && prefixMatcher.isSimpleCG();
                nfa.setInitialLoopBack(true);
                nfa.getReverseAnchoredEntry().setSource(reverseAnchoredInitialState);
                nfa.getReverseUnAnchoredEntry().setSource(reverseUnAnchoredInitialState);
            }

            registerStateReplacement(unanchoredInitialState.getId(), new DFAFindInnerLiteralStateNode((short) unanchoredInitialState.getId(),
                            new short[]{(short) literalLastDFAState.getId()}, nfa.getAst().extractInnerLiteral(), prefixMatcher));
        }
    }

    private boolean innerLiteralMatchesPrefix(StateSet<NFA, NFAState> prefixNFAStates) {
        int literalEnd = nfa.getAst().getProperties().getInnerLiteralEnd();
        int literalStart = nfa.getAst().getProperties().getInnerLiteralStart();
        Sequence rootSeq = nfa.getAst().getRoot().getFirstAlternative();
        StateSet<NFA, NFAState> curState = entryStates[0].getNfaTransitionSet().getTargetStateSet().copy();
        StateSet<NFA, NFAState> nextState = StateSet.create(nfa);
        for (int i = literalStart; i < literalEnd; i++) {
            CodePointSet c = ((CharacterClass) rootSeq.getTerms().get(i)).getCharSet();
            for (NFAState s : curState) {
                for (NFAStateTransition t : s.getSuccessors()) {
                    if (i == literalStart && !prefixNFAStates.contains(t.getTarget())) {
                        continue;
                    }
                    if (c.intersects(t.getCodePointSet())) {
                        nextState.add(t.getTarget());
                    }
                }
            }
            if (nextState.isEmpty()) {
                return false;
            }
            StateSet<NFA, NFAState> tmp = curState;
            curState = nextState;
            nextState = tmp;
            nextState.clear();
        }
        return true;
    }

    private void registerStateReplacement(int id, DFAAbstractStateNode replacement) {
        if (stateReplacements == null) {
            stateReplacements = EconomicMap.create();
        }
        stateReplacements.put(id, replacement);
    }

    private DFAAbstractStateNode getReplacement(int id) {
        return stateReplacements == null ? null : stateReplacements.get(id);
    }

    private DFAAbstractStateNode[] createDFAExecutorStates() {
        if (isGenericCG()) {
            for (DFAStateNodeBuilder s : stateMap.values()) {
                if (s.isInitialState()) {
                    s.addPredecessorUnchecked(initialCGTransition);
                }
                for (DFAStateTransitionBuilder t : s.getSuccessors()) {
                    t.getTarget().addPredecessor(t);
                }
            }
        }
        boolean utf16MustDecode = false;
        DFAAbstractStateNode[] ret = new DFAAbstractStateNode[stateMap.values().size() + 1];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            matchersBuilder.reset(s.getSuccessors().length);
            assert s.getId() <= Short.MAX_VALUE;
            short id = (short) s.getId();
            DFAAbstractStateNode replacement = getReplacement(id);
            if (replacement != null) {
                ret[id] = replacement;
                continue;
            }
            DFASimpleCGTransition[] simpleCGTransitions = doSimpleCG ? new DFASimpleCGTransition[s.getSuccessors().length] : null;
            int nRanges = 0;
            int estimatedTransitionsCost = 0;
            boolean coversCharSpace = s.coversFullCharSpace(compilationBuffer);
            for (int i = 0; i < s.getSuccessors().length; i++) {
                DFAStateTransitionBuilder t = s.getSuccessors()[i];
                CodePointSet cps = t.getCodePointSet();
                utf16MustDecode |= Constants.ASTRAL_SYMBOLS_AND_LONE_SURROGATES.intersects(cps);
                if (i == s.getSuccessors().length - 1 && (coversCharSpace || (pruneUnambiguousPaths && !s.isFinalStateSuccessor()))) {
                    // replace the last matcher with an AnyMatcher, since it must always cover the
                    // remaining input space
                    matchersBuilder.setNoMatchSuccessor((short) i);
                } else {
                    nRanges += cps.size();
                    getEncoding().createMatcher(matchersBuilder, i, cps, compilationBuffer);

                }
                estimatedTransitionsCost += matchersBuilder.estimatedCost(i);

                if (doSimpleCG) {
                    assert t.getTransitionSet().size() <= 2;
                    assert t.getTransitionSet().size() == 1 || t.getTransitionSet().getTransition(0) != nfa.getInitialLoopBackTransition();
                    simpleCGTransitions[i] = createSimpleCGTransition(t.getTransitionSet().getTransition(0));
                }
            }

            Matchers matchers = null;
            // Very conservative heuristic for whether we should use AllTransitionsInOneTreeMatcher.
            // TODO: Potential benefits of this should be further explored.
            AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher = null;
            boolean useTreeTransitionMatcher = nRanges > 1 && MathUtil.log2ceil(nRanges + 2) * 8 < estimatedTransitionsCost;
            if (useTreeTransitionMatcher) {
                if (getOptions().isRegressionTestMode()) {
                    // in regression test mode, we compare results of regular matchers and
                    // AllTransitionsInOneTreeMatcher
                    matchers = getEncoding().toMatchers(matchersBuilder);
                }
                allTransitionsInOneTreeMatcher = createAllTransitionsInOneTreeMatcher(s, coversCharSpace);
            } else {
                matchers = getEncoding().toMatchers(matchersBuilder);
            }

            short[] successors = s.getNumberOfSuccessors() > 0 ? new short[s.getNumberOfSuccessors()] : EMPTY_SHORT_ARRAY;
            short[] cgTransitions = null;
            short[] cgPrecedingTransitions = null;
            DFAStateNode.LoopOptimizationNode loopOptimizationNode = null;
            if (isGenericCG()) {
                cgTransitions = new short[s.getSuccessors().length];
                DFAStateTransitionBuilder[] precedingTransitions = s.getPredecessors();
                assert precedingTransitions.length != 0;
                cgPrecedingTransitions = new short[precedingTransitions.length];
                for (int i = 0; i < precedingTransitions.length; i++) {
                    cgPrecedingTransitions[i] = ((DFACaptureGroupTransitionBuilder) precedingTransitions[i]).toLazyTransition(compilationBuffer).getId();
                }
            }
            short loopToSelf = -1;
            for (int i = 0; i < successors.length - (s.hasBackwardPrefixState() ? 1 : 0); i++) {
                successors[i] = (short) s.getSuccessors()[i].getTarget().getId();
                if (successors[i] == id) {
                    loopToSelf = (short) i;
                    CodePointSet loopMB = s.getSuccessors()[i].getCodePointSet();
                    if (coversCharSpace && !loopMB.matchesEverything(getEncoding()) && loopMB.inverseValueCount(getEncoding()) <= 4) {
                        loopOptimizationNode = getEncoding().extractLoopOptNode(loopMB);
                    }
                }
                assert successors[i] >= 0 && successors[i] < ret.length;
                if (isGenericCG()) {
                    final DFACaptureGroupLazyTransition transition = ((DFACaptureGroupTransitionBuilder) s.getSuccessors()[i]).toLazyTransition(compilationBuffer);
                    cgTransitions[i] = transition.getId();
                    registerCGTransition(transition);
                }
            }
            if (s.hasBackwardPrefixState()) {
                successors[successors.length - 1] = s.getBackwardPrefixState();
            }
            byte flags = DFAStateNode.buildFlags(s.isUnAnchoredFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(), utf16MustDecode);
            DFASimpleCG simpleCG = null;
            if (doSimpleCG) {
                simpleCG = DFASimpleCG.create(simpleCGTransitions,
                                createSimpleCGTransition(s.getUnAnchoredFinalStateTransition()),
                                createSimpleCGTransition(s.getAnchoredFinalStateTransition()));
            }
            DFAStateNode stateNode;
            if (isGenericCG()) {
                stateNode = new CGTrackingDFAStateNode(id, flags, loopToSelf, loopOptimizationNode, successors, matchers, allTransitionsInOneTreeMatcher, cgTransitions, cgPrecedingTransitions,
                                createCGFinalTransition(s.getAnchoredFinalStateTransition()),
                                createCGFinalTransition(s.getUnAnchoredFinalStateTransition()));
            } else if (nfa.isTraceFinderNFA()) {
                stateNode = new TraceFinderDFAStateNode(id, flags, loopToSelf, loopOptimizationNode, successors, matchers,
                                allTransitionsInOneTreeMatcher, s.getPreCalculatedUnAnchoredResult(), s.getPreCalculatedAnchoredResult());
            } else if (isForward()) {
                stateNode = new DFAStateNode(id, flags, loopToSelf, loopOptimizationNode, successors, matchers, simpleCG, allTransitionsInOneTreeMatcher);
            } else {
                stateNode = new BackwardDFAStateNode(id, flags, loopToSelf, loopOptimizationNode, successors, matchers, simpleCG, allTransitionsInOneTreeMatcher);
            }
            ret[id] = stateNode;
        }
        return ret;
    }

    private DFASimpleCGTransition createSimpleCGTransition(NFAStateTransition nfaTransition) {
        return DFASimpleCGTransition.create(nfaTransition, isForward() && nfaTransition != null && nfaTransition.getSource() == nfa.getInitialLoopBackTransition().getSource());
    }

    /**
     * Generate a new {@link AllTransitionsInOneTreeMatcher} from a given {@code state}.
     */
    private AllTransitionsInOneTreeMatcher createAllTransitionsInOneTreeMatcher(DFAStateNodeBuilder state, boolean coversCharSpace) {
        DFAStateTransitionBuilder[] transitions = state.getSuccessors();
        // convert all transition matchers to CompressedCodePointSets
        CompressedCodePointSet[] ccpss = new CompressedCodePointSet[coversCharSpace ? transitions.length - 1 : transitions.length];
        for (int i = 0; i < ccpss.length; i++) {
            ccpss[i] = CompressedCodePointSet.create(transitions[i].getCodePointSet(), compilationBuffer);
        }
        IntArrayBuffer ranges = compilationBuffer.getIntRangesBuffer1();
        IntArrayBuffer iterators = compilationBuffer.getIntRangesBuffer2().asFixedSizeArray(ccpss.length, 0);
        IntArrayBuffer byteRanges = compilationBuffer.getIntRangesBuffer3();
        ShortArrayBuffer successors = compilationBuffer.getShortArrayBuffer1();
        ShortArrayBuffer byteSuccessors = compilationBuffer.getShortArrayBuffer2();
        ObjectArrayBuffer<long[]> byteBitSets = compilationBuffer.getObjectBuffer1();
        ObjectArrayBuffer<AllTransitionsInOneTreeMatcher.AllTransitionsInOneTreeLeafMatcher> byteMatchers = compilationBuffer.getObjectBuffer2();
        short noMatchSuccessor = (short) (coversCharSpace ? transitions.length - 1 : -1);
        int lastHi = 0;
        // iterate all compressed code point sets in parallel, using the temporary "iterators" array
        while (true) {
            int minLo = Integer.MAX_VALUE;
            int minCPS = -1;
            // find the next lowest range of all code point sets
            for (int i = 0; i < ccpss.length; i++) {
                if (iterators.get(i) < ccpss[i].size() && ccpss[i].getLo(iterators.get(i)) < minLo) {
                    minLo = ccpss[i].getLo(iterators.get(i));
                    minCPS = i;
                }
            }
            if (minCPS == -1) {
                // all code point sets are exhausted, finish
                break;
            }
            if (minLo != lastHi) {
                // there is a gap between the last and current processed range, add a no-match
                // successor
                successors.add(noMatchSuccessor);
                ranges.add(minLo);
            }
            lastHi = ccpss[minCPS].getHi(iterators.get(minCPS)) + 1;

            if (ccpss[minCPS].hasBitSet(iterators.get(minCPS))) {
                // the current range is subdivided into a bit set, generate a corresponding
                // AllTransitionsInOneTreeLeafMatcher
                byteRanges.clear();
                byteSuccessors.clear();
                byteBitSets.clear();
                // find all bit-set ranges that intersect with the current range, and extend the
                // current range to fit all of these interleaved bit sets.
                for (int i = 0; i < ccpss.length; i++) {
                    if (iterators.get(i) < ccpss[i].size() && ccpss[i].hasBitSet(iterators.get(i)) && BitSets.highByte(ccpss[i].getLo(iterators.get(i))) == BitSets.highByte(lastHi - 1)) {
                        byteBitSets.add(ccpss[i].getBitSet(iterators.get(i)));
                        lastHi = Math.max(lastHi, ccpss[i].getHi(iterators.get(i)) + 1);
                        iterators.inc(i);
                        byteSuccessors.add((short) i);
                    }
                }
                int byteLastHi = minLo;
                // find all regular ranges that are contained in the current bit-set range, and add
                // them to the AllTransitionsInOneTreeLeafMatcher as well
                while (true) {
                    int byteMinLo = lastHi;
                    int byteMinCPS = -1;
                    for (int i = 0; i < ccpss.length; i++) {
                        if (iterators.get(i) < ccpss[i].size() && ccpss[i].getLo(iterators.get(i)) < byteMinLo) {
                            assert !ccpss[i].hasBitSet(iterators.get(i));
                            assert ccpss[i].getHi(iterators.get(i)) < lastHi;
                            byteMinLo = ccpss[i].getLo(iterators.get(i));
                            byteMinCPS = i;
                        }
                    }
                    if (byteMinCPS == -1) {
                        break;
                    }
                    if (byteMinLo != byteLastHi) {
                        // there is a gap between the last and current processed range, add a
                        // no-match successor
                        byteSuccessors.add(noMatchSuccessor);
                        byteRanges.add(byteMinLo);
                    }
                    byteSuccessors.add((short) byteMinCPS);
                    byteLastHi = ccpss[byteMinCPS].getHi(iterators.get(byteMinCPS)) + 1;
                    if (byteLastHi < lastHi) {
                        byteRanges.add(byteLastHi);
                    }
                    iterators.inc(byteMinCPS);
                }
                if (byteLastHi != lastHi) {
                    byteSuccessors.add(noMatchSuccessor);
                }
                successors.add((short) ((byteMatchers.length() + 2) * -1));
                byteMatchers.add(new AllTransitionsInOneTreeMatcher.AllTransitionsInOneTreeLeafMatcher(
                                byteBitSets.toArray(new long[byteBitSets.length()][]), byteSuccessors.toArray(), byteRanges.toArray()));
            } else {
                successors.add((short) minCPS);
                iterators.inc(minCPS);
            }
            if (lastHi <= getEncoding().getMaxValue()) {
                ranges.add(lastHi);
            }
        }
        if (lastHi != getEncoding().getMaxValue() + 1) {
            successors.add(noMatchSuccessor);
        }
        return new AllTransitionsInOneTreeMatcher(ranges.toArray(), successors.toArray(),
                        byteMatchers.toArray(new AllTransitionsInOneTreeMatcher.AllTransitionsInOneTreeLeafMatcher[byteMatchers.length()]));
    }

    private void registerCGTransition(DFACaptureGroupLazyTransition cgTransition) {
        assert captureGroupTransitions[cgTransition.getId()] == null;
        captureGroupTransitions[cgTransition.getId()] = cgTransition;
    }

    private DFACaptureGroupPartialTransition createCGFinalTransition(NFAStateTransition transition) {
        if (transition == null) {
            return null;
        }
        GroupBoundaries groupBoundaries = transition.getGroupBoundaries();
        IndexOperation[] indexUpdates = DFACaptureGroupPartialTransition.EMPTY_INDEX_UPDATES;
        IndexOperation[] indexClears = DFACaptureGroupPartialTransition.EMPTY_INDEX_CLEARS;
        if (groupBoundaries.hasIndexUpdates()) {
            indexUpdates = new IndexOperation[]{new IndexOperation(0, groupBoundaries.updatesToByteArray())};
        }
        if (groupBoundaries.hasIndexClears()) {
            indexClears = new IndexOperation[]{new IndexOperation(0, groupBoundaries.clearsToByteArray())};
        }
        DFACaptureGroupPartialTransition partialTransitionNode = DFACaptureGroupPartialTransition.create(this,
                        DFACaptureGroupPartialTransition.EMPTY_REORDER_SWAPS,
                        DFACaptureGroupPartialTransition.EMPTY_ARRAY_COPIES,
                        indexUpdates,
                        indexClears, (byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
        if (debugMode()) {
            DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo debugInfo = new DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo(partialTransitionNode, 1);
            debugInfo.mapResultToNFATransition(0, transition);
            registerCGPartialTransitionDebugInfo(debugInfo);
        }
        return partialTransitionNode;
    }

    void updateMaxNumberOfNFAStatesInOneTransition(int value) {
        if (value > maxNumberOfNfaStates) {
            maxNumberOfNfaStates = value;
            if (maxNumberOfNfaStates > TRegexOptions.TRegexMaxNumberOfNFAStatesInOneDFATransition) {
                throw new UnsupportedRegexException("DFA transition size explosion");
            }
        }
    }

    private DFAAbstractStateNode[] tryMakeReducible(DFAAbstractStateNode[] states) {
        try {
            if (debugMode()) {
                return DFANodeSplit.createReducibleGraphAndUpdateDFAGen(this, states);
            } else {
                return DFANodeSplit.createReducibleGraph(states);
            }
        } catch (DFANodeSplitBailoutException e) {
            return states;
        }
    }

    private boolean debugMode() {
        return engineOptions.isDumpAutomata() || engineOptions.isStepExecution();
    }

    public String getDebugDumpName(String name) {
        return name == null ? getDebugDumpName() : name;
    }

    public String getDebugDumpName() {
        if (isForward()) {
            if (isGenericCG()) {
                if (isSearching()) {
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
        if (isForward()) {
            nfa.setInitialLoopBack(isSearching() && !nfa.getAst().getFlags().isSticky());
        }
        DFAStateTransitionBuilder[] transitionList = new DFAStateTransitionBuilder[transitionIDCounter.getCount()];
        for (DFAStateNodeBuilder s : getStateIndexMap()) {
            if (s == null) {
                continue;
            }
            for (DFAStateTransitionBuilder t : s.getSuccessors()) {
                transitionList[t.getId()] = t;
            }
        }
        return Json.obj(Json.prop("pattern", nfa.getAst().getSource().toString()),
                        Json.prop("nfa", nfa.toJson(isForward())),
                        Json.prop("dfa", Json.obj(
                                        Json.prop("states", Arrays.stream(getStateIndexMap())),
                                        Json.prop("transitions", Arrays.stream(transitionList)),
                                        Json.prop("captureGroupPartialTransitions", cgPartialTransitions),
                                        Json.prop("entryStates", Arrays.stream(entryStates).filter(Objects::nonNull).map(x -> Json.val(x.getId()))))));
    }
}
