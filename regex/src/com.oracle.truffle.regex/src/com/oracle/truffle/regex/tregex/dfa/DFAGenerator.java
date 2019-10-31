/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.RangesAccumulator;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.TRegexCompilationRequest;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.buffer.CharArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.CharRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.AnyMatcher;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.nfa.ASTNodeSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.tregex.nodes.dfa.BackwardDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupLazyTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAFindInnerLiteralStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCG;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCGTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode;
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
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.AddToSetVisitor;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
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
    private final DFAStateNodeBuilder lookupDummyState = new DFAStateNodeBuilder((short) -1, null, false, false);
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

    private final List<DFAStateTransitionBuilder> expandDFATransitions = new ArrayList<>();
    private List<DFAStateTransitionBuilder[]> bfsTraversalCur;
    private List<DFAStateTransitionBuilder[]> bfsTraversalNext;
    private EconomicMap<Short, DFAAbstractStateNode> stateReplacements;

    public DFAGenerator(TRegexCompilationRequest compilationReqest, NFA nfa, TRegexDFAExecutorProperties executorProps, CompilationBuffer compilationBuffer, RegexOptions engineOptions) {
        this.compilationReqest = compilationReqest;
        this.nfa = nfa;
        this.executorProps = executorProps;
        this.pruneUnambiguousPaths = executorProps.isBackward() && nfa.isTraceFinderNFA() && nfa.hasReverseUnAnchoredEntry();
        this.canonicalizer = new DFATransitionCanonicalizer(isGenericCG());
        this.compilationBuffer = compilationBuffer;
        this.engineOptions = engineOptions;
        this.cgPartialTransitions = debugMode() ? new ArrayList<>() : null;
        this.bfsTraversalCur = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.bfsTraversalNext = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.initialCGTransition = isGenericCG() ? new DFACaptureGroupTransitionBuilder(null, null, null) : null;
        this.transitionIDCounter.inc(); // zero is reserved for initialCGTransition
        this.cgPartialTransitionIDCounter.inc(); // zero is reserved for static empty instance
        if (debugMode()) {
            registerCGPartialTransitionDebugInfo(new DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo(DFACaptureGroupPartialTransition.getEmptyInstance()));
        }
        assert !nfa.isDead();
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
        for (DFAStateTransitionBuilder t : copy.getTransitions()) {
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
        if (s.getTransitions() != null) {
            bfsTraversalNext.add(s.getTransitions());
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
                maxNumberOfEntryStateSuccessors = Math.max(entryState.getTransitions().length, maxNumberOfEntryStateSuccessors);
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
                entryStateIDs[i] = entryStates[i].getId();
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
            NFATransitionSet anchoredEntryStateSet = createNFATransitionSet(nfa.getAnchoredEntry()[i]);
            if (nfa.getUnAnchoredEntry()[i].getTarget().getNext().length == 0) {
                entryStates[numberOfEntryPoints + i] = null;
            } else {
                NFATransitionSet unAnchoredEntryStateSet = createNFATransitionSet(nfa.getUnAnchoredEntry()[i]);
                anchoredEntryStateSet.addAll(unAnchoredEntryStateSet);
                DFAStateTransitionBuilder unAnchoredEntryTransition = createTransitionBuilder(unAnchoredEntryStateSet);
                entryStates[numberOfEntryPoints + i] = createInitialState(unAnchoredEntryTransition);
            }
            DFAStateTransitionBuilder anchoredEntryTransition = createTransitionBuilder(anchoredEntryStateSet);
            entryStates[i] = createInitialState(anchoredEntryTransition);
        }
    }

    private void createInitialStatesBackward() {
        NFATransitionSet anchoredEntry = createNFATransitionSet(nfa.getReverseAnchoredEntry());
        entryStates = new DFAStateNodeBuilder[]{null, null};
        if (nfa.hasReverseUnAnchoredEntry()) {
            anchoredEntry.add(nfa.getReverseUnAnchoredEntry());
            NFATransitionSet unAnchoredEntry = createNFATransitionSet(nfa.getReverseUnAnchoredEntry());
            entryStates[1] = createInitialState(createTransitionBuilder(unAnchoredEntry));
        }
        entryStates[0] = createInitialState(createTransitionBuilder(anchoredEntry));
    }

    private DFAStateNodeBuilder createInitialState(DFAStateTransitionBuilder transition) {
        DFAStateNodeBuilder lookup = lookupState(transition.getTransitionSet(), false);
        if (lookup == null) {
            lookup = createState(transition.getTransitionSet(), false, true);
            lookup.updateFinalStateData(this);
        }
        transition.setTarget(lookup);
        if (isGenericCG()) {
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
        outer: for (NFAStateTransition transition : state.getNfaTransitionSet()) {
            NFAState nfaState = transition.getTarget(isForward());
            for (NFAStateTransition nfaTransition : nfaState.getNext(isForward())) {
                NFAState target = nfaTransition.getTarget(isForward());
                if (!target.isFinalState(isForward()) && (!state.isBackwardPrefixState() || target.hasPrefixStates())) {
                    anyPrefixStateSuccessors |= target.hasPrefixStates();
                    allPrefixStateSuccessors &= target.hasPrefixStates();
                    expandDFATransitions.add(createTransitionBuilder(target.getCharSet(), createNFATransitionSet(nfaTransition)));
                } else if (isForward() && target.isForwardUnAnchoredFinalState()) {
                    assert target == nfa.getReverseUnAnchoredEntry().getSource();
                    break outer;
                }
            }
        }
        if (!isForward() && anyPrefixStateSuccessors) {
            if (allPrefixStateSuccessors) {
                state.setBackwardPrefixState(state.getId());
            } else {
                assert !state.isBackwardPrefixState();
                DFAStateNodeBuilder lookup = lookupState(state.getNfaTransitionSet(), true);
                if (lookup == null) {
                    lookup = createState(state.getNfaTransitionSet(), true, false);
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
                successorState = createState(transition.getTransitionSet(), false, false);
            } else if (pruneUnambiguousPaths) {
                reScheduleFinalStateSuccessors(state, successorState);
            }
            if (pruneUnambiguousPaths && (state.isFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                successorState.setFinalStateSuccessor();
            }
            transition.setTarget(successorState);
            successorState.updateFinalStateData(this);
            if (isGenericCG()) {
                transition.getTarget().addPrecedingTransition((DFACaptureGroupTransitionBuilder) transition);
            }
            if (state.isFinalState() && !(successorState.isFinalState() || successorState.isAnchoredFinalState())) {
                simpleCGMustCopy = true;
            }
        }
        state.setTransitions(transitions);
    }

    private DFAStateTransitionBuilder createTransitionBuilder(NFATransitionSet transitionSet) {
        return createTransitionBuilder(null, transitionSet);
    }

    private DFAStateTransitionBuilder createTransitionBuilder(CharSet matcherBuilder, NFATransitionSet transitionSet) {
        if (isGenericCG()) {
            return new DFACaptureGroupTransitionBuilder(matcherBuilder, transitionSet, this);
        } else {
            return new DFAStateTransitionBuilder(matcherBuilder, transitionSet);
        }
    }

    private NFATransitionSet createNFATransitionSet(NFAStateTransition initialTransition) {
        if (isForward()) {
            return PrioritySensitiveNFATransitionSet.create(nfa, true, initialTransition);
        } else {
            return NFATransitionSet.create(nfa, false, initialTransition);
        }
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
        for (NFAStateTransition transition : state.getNfaTransitionSet()) {
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
            state.setTransitions(EMPTY_TRANSITIONS_ARRAY);
            return true;
        }
        return false;
    }

    private void reScheduleFinalStateSuccessors(DFAStateNodeBuilder state, DFAStateNodeBuilder successorState) {
        assert nfa.isTraceFinderNFA();
        if ((state.isFinalState() || state.isFinalStateSuccessor()) && !successorState.isFinalStateSuccessor()) {
            reScheduleFinalStateSuccessor(successorState);
            bfsTraversalCur.clear();
            if (successorState.getTransitions() != null) {
                bfsTraversalCur.add(successorState.getTransitions());
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

    private DFAStateNodeBuilder lookupState(NFATransitionSet transitionSet, boolean isBackWardPrefixState) {
        lookupDummyState.setNfaTransitionSet(transitionSet);
        lookupDummyState.setIsBackwardPrefixState(isBackWardPrefixState);
        return stateMap.get(lookupDummyState);
    }

    private DFAStateNodeBuilder createState(NFATransitionSet transitionSet, boolean isBackwardPrefixState, boolean isInitialState) {
        assert stateIndexMap == null : "state index map created before dfa generation!";
        DFAStateNodeBuilder dfaState = new DFAStateNodeBuilder(nextID++, transitionSet, isBackwardPrefixState, isInitialState);
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

        doSimpleCG = executorProps.isAllowSimpleCG() &&
                        !hasAmbiguousStates &&
                        !nfa.isTraceFinderNFA() &&
                        !isGenericCG() &&
                        (isSearching() || props.hasCaptureGroups()) &&
                        (props.hasAlternations() || props.hasLookAroundAssertions());

        // inner-literal-optimization
        if (isForward() && isSearching() && !isGenericCG() && !nfa.getAst().getFlags().isSticky() && props.hasInnerLiteral()) {
            int literalEnd = props.getInnerLiteralEnd();
            int literalStart = props.getInnerLiteralStart();
            Sequence rootSeq = nfa.getAst().getRoot().getAlternatives().get(0);

            // find all parser tree nodes of the prefix
            ASTNodeSet<RegexASTNode> prefixAstNodes = new ASTNodeSet<>(nfa.getAst());
            for (int i = 0; i < literalStart; i++) {
                AddToSetVisitor.addCharacterClasses(prefixAstNodes, rootSeq.getTerms().get(i));
            }

            // find NFA states of the prefix and the beginning and end of the literal
            NFAStateSet prefixNFAStates = new NFAStateSet(nfa);
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
            DFAStateNodeBuilder unanchoredInitialState = entryStates[nfa.getAnchoredEntry().length];
            CompilationFinalBitSet visited = new CompilationFinalBitSet(nextID);
            visited.set(unanchoredInitialState.getId());
            bfsTraversalCur.clear();
            bfsTraversalCur.add(unanchoredInitialState.getTransitions());
            while (!bfsTraversalCur.isEmpty()) {
                bfsTraversalNext.clear();
                for (DFAStateTransitionBuilder[] cur : bfsTraversalCur) {
                    for (DFAStateTransitionBuilder t : cur) {
                        DFAStateNodeBuilder target = t.getTarget();
                        if (visited.get(target.getId())) {
                            continue;
                        }
                        visited.set(target.getId());
                        NFAStateSet targetStateSet = target.getNfaTransitionSet().getTargetStateSet();
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
                RangesAccumulator<CharRangesBuffer> acc = new RangesAccumulator<>(new CharRangesBuffer());
                for (DFAStateNodeBuilder s : stateMap.values()) {
                    acc.clear();
                    if (!prefixNFAStates.containsAll(s.getNfaTransitionSet().getTargetStateSet())) {
                        DFAStateTransitionBuilder mergedTransition = null;
                        ObjectArrayBuffer newTransitions = null;
                        for (int i = 0; i < s.getTransitions().length; i++) {
                            DFAStateTransitionBuilder t = s.getTransitions()[i];
                            if (prefixNFAStates.containsAll(t.getTarget().getNfaTransitionSet().getTargetStateSet())) {
                                if (mergedTransition == null) {
                                    t.setTarget(unanchoredInitialState);
                                    mergedTransition = t;
                                } else if (newTransitions == null) {
                                    newTransitions = compilationBuffer.getObjectBuffer1();
                                    newTransitions.addAll(s.getTransitions(), 0, i);
                                    acc.addSet(mergedTransition.getMatcherBuilder());
                                    acc.addSet(t.getMatcherBuilder());
                                } else {
                                    acc.addSet(t.getMatcherBuilder());
                                }
                            } else if (newTransitions != null) {
                                newTransitions.add(t);
                            }
                        }
                        // GR-17760: newTransitions != null implies mergedTransition != null, but
                        // the "Fortify" tool does not see that
                        if (newTransitions != null && mergedTransition != null) {
                            mergedTransition.setMatcherBuilder(CharSet.create(acc.get()));
                            s.setTransitions(newTransitions.toArray(new DFAStateTransitionBuilder[newTransitions.length()]));
                            Arrays.sort(s.getTransitions(), Comparator.comparing(TransitionBuilder::getMatcherBuilder));
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
                                nfa.getAst().getNumberOfCaptureGroups(), rootSeq.getTerms().get(literalStart - 1).getMinPath()), "innerLiteralPrefix");
                prefixMatcher.setRoot(compilationReqest.getRoot());
                prefixMatcher.getProperties().setSimpleCGMustCopy(false);
                doSimpleCG = doSimpleCG && prefixMatcher.isSimpleCG();
                nfa.setInitialLoopBack(true);
                nfa.getReverseAnchoredEntry().setSource(reverseAnchoredInitialState);
                nfa.getReverseUnAnchoredEntry().setSource(reverseUnAnchoredInitialState);
            }

            // extract the literal from the parser tree
            CharArrayBuffer literal = compilationBuffer.getCharRangesBuffer1();
            CharArrayBuffer mask = compilationBuffer.getCharRangesBuffer2();
            literal.ensureCapacity(literalEnd - literalStart);
            mask.ensureCapacity(literalEnd - literalStart);
            boolean hasMask = false;
            for (int i = literalStart; i < literalEnd; i++) {
                CharacterClass cc = (CharacterClass) rootSeq.getTerms().get(i);
                assert cc.getCharSet().matchesSingleChar() || cc.getCharSet().matches2CharsWith1BitDifference();
                cc.extractSingleChar(literal, mask);
                hasMask |= cc.getCharSet().matches2CharsWith1BitDifference();
            }
            registerStateReplacement(unanchoredInitialState.getId(), new DFAFindInnerLiteralStateNode(unanchoredInitialState.getId(),
                            new short[]{literalLastDFAState.getId()}, new String(literal.toArray()), hasMask ? new String(mask.toArray()) : null, prefixMatcher));
        }
    }

    private boolean innerLiteralMatchesPrefix(NFAStateSet prefixNFAStates) {
        int literalEnd = nfa.getAst().getProperties().getInnerLiteralEnd();
        int literalStart = nfa.getAst().getProperties().getInnerLiteralStart();
        Sequence rootSeq = nfa.getAst().getRoot().getAlternatives().get(0);
        NFAStateSet curState = entryStates[0].getNfaTransitionSet().getTargetStateSet().copy();
        NFAStateSet nextState = new NFAStateSet(nfa);
        for (int i = literalStart; i < literalEnd; i++) {
            CharSet c = ((CharacterClass) rootSeq.getTerms().get(i)).getCharSet();
            for (NFAState s : curState) {
                for (NFAStateTransition t : s.getNext()) {
                    if (i == literalStart && !prefixNFAStates.contains(t.getTarget())) {
                        continue;
                    }
                    if (c.intersects(t.getTarget().getCharSet())) {
                        nextState.add(t.getTarget());
                    }
                }
            }
            if (nextState.isEmpty()) {
                return false;
            }
            NFAStateSet tmp = curState;
            curState = nextState;
            nextState = tmp;
            nextState.clear();
        }
        return true;
    }

    private void registerStateReplacement(short id, DFAAbstractStateNode replacement) {
        if (stateReplacements == null) {
            stateReplacements = EconomicMap.create();
        }
        stateReplacements.put(id, replacement);
    }

    private DFAAbstractStateNode getReplacement(short id) {
        return stateReplacements == null ? null : stateReplacements.get(id);
    }

    private DFAAbstractStateNode[] createDFAExecutorStates() {
        DFAAbstractStateNode[] ret = new DFAAbstractStateNode[stateMap.values().size() + 1];
        for (DFAStateNodeBuilder s : stateMap.values()) {
            DFAAbstractStateNode replacement = getReplacement(s.getId());
            if (replacement != null) {
                ret[s.getId()] = replacement;
                continue;
            }
            CharMatcher[] matchers = (s.getTransitions().length > 0) ? new CharMatcher[s.getTransitions().length] : CharMatcher.EMPTY;
            DFASimpleCGTransition[] simpleCGTransitions = doSimpleCG ? new DFASimpleCGTransition[matchers.length] : null;
            int nRanges = 0;
            int estimatedTransitionsCost = 0;
            boolean coversCharSpace = s.coversFullCharSpace(compilationBuffer);
            for (int i = 0; i < matchers.length; i++) {
                DFAStateTransitionBuilder t = s.getTransitions()[i];
                CharSet matcherBuilder = t.getMatcherBuilder();
                if (i == matchers.length - 1 && (coversCharSpace || (pruneUnambiguousPaths && !s.isFinalStateSuccessor()))) {
                    // replace the last matcher with an AnyMatcher, since it must always cover the
                    // remaining input space
                    matchers[i] = AnyMatcher.create();
                } else {
                    nRanges += matcherBuilder.size();
                    matchers[i] = matcherBuilder.createMatcher(compilationBuffer);
                }
                estimatedTransitionsCost += matchers[i].estimatedCost();

                if (doSimpleCG) {
                    assert t.getTransitionSet().size() <= 2;
                    assert t.getTransitionSet().size() == 1 || t.getTransitionSet().getTransition(0) != nfa.getInitialLoopBackTransition();
                    simpleCGTransitions[i] = createSimpleCGTransition(t.getTransitionSet().getTransition(0));
                }
            }

            // Very conservative heuristic for whether we should use AllTransitionsInOneTreeMatcher.
            // TODO: Potential benefits of this should be further explored.
            AllTransitionsInOneTreeMatcher allTransitionsInOneTreeMatcher = null;
            boolean useTreeTransitionMatcher = nRanges > 1 && MathUtil.log2ceil(nRanges + 2) * 8 < estimatedTransitionsCost;
            if (useTreeTransitionMatcher) {
                if (!getOptions().isRegressionTestMode()) {
                    // in regression test mode, we compare results of regular matchers and
                    // AllTransitionsInOneTreeMatcher
                    matchers = null;
                }
                allTransitionsInOneTreeMatcher = createAllTransitionsInOneTreeMatcher(s);
            }

            short[] successors = s.getNumberOfSuccessors() > 0 ? new short[s.getNumberOfSuccessors()] : EMPTY_SHORT_ARRAY;
            short[] cgTransitions = null;
            short[] cgPrecedingTransitions = null;
            if (isGenericCG()) {
                cgTransitions = new short[s.getTransitions().length];
                List<DFACaptureGroupTransitionBuilder> precedingTransitions = s.getPrecedingTransitions();
                assert !precedingTransitions.isEmpty();
                cgPrecedingTransitions = new short[precedingTransitions.size()];
                for (int i = 0; i < precedingTransitions.size(); i++) {
                    DFACaptureGroupTransitionBuilder transitionBuilder = precedingTransitions.get(i);
                    cgPrecedingTransitions[i] = transitionBuilder.toLazyTransition(compilationBuffer).getId();
                }
            }
            char[] indexOfChars = null;
            short loopToSelf = -1;
            for (int i = 0; i < successors.length - (s.hasBackwardPrefixState() ? 1 : 0); i++) {
                successors[i] = s.getTransitions()[i].getTarget().getId();
                if (successors[i] == s.getId()) {
                    loopToSelf = (short) i;
                    CharSet loopMB = s.getTransitions()[i].getMatcherBuilder();
                    if (coversCharSpace && !loopMB.matchesEverything() && loopMB.inverseValueCount() <= 4) {
                        indexOfChars = loopMB.inverseToCharArray();
                    }
                }
                assert successors[i] >= 0 && successors[i] < ret.length;
                if (isGenericCG()) {
                    final DFACaptureGroupLazyTransition transition = ((DFACaptureGroupTransitionBuilder) s.getTransitions()[i]).toLazyTransition(compilationBuffer);
                    cgTransitions[i] = transition.getId();
                    registerCGTransition(transition);
                }
            }
            if (s.hasBackwardPrefixState()) {
                successors[successors.length - 1] = s.getBackwardPrefixState();
            }
            byte flags = DFAStateNode.buildFlags(s.isFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState());
            DFAStateNode.LoopOptimizationNode loopOptimizationNode = null;
            if (loopToSelf != -1) {
                loopOptimizationNode = DFAStateNode.buildLoopOptimizationNode(loopToSelf, indexOfChars);
            }
            DFASimpleCG simpleCG = null;
            if (doSimpleCG) {
                simpleCG = DFASimpleCG.create(simpleCGTransitions,
                                createSimpleCGTransition(s.getUnAnchoredFinalStateTransition()),
                                createSimpleCGTransition(s.getAnchoredFinalStateTransition()));
            }
            DFAStateNode stateNode;
            if (isGenericCG()) {
                stateNode = new CGTrackingDFAStateNode(s.getId(), flags, loopOptimizationNode, successors, matchers, allTransitionsInOneTreeMatcher, cgTransitions, cgPrecedingTransitions,
                                createCGFinalTransition(s.getAnchoredFinalStateTransition()),
                                createCGFinalTransition(s.getUnAnchoredFinalStateTransition()));
            } else if (nfa.isTraceFinderNFA()) {
                stateNode = new TraceFinderDFAStateNode(s.getId(), flags, loopOptimizationNode, successors, matchers,
                                allTransitionsInOneTreeMatcher, s.getPreCalculatedUnAnchoredResult(), s.getPreCalculatedAnchoredResult());
            } else if (isForward()) {
                stateNode = new DFAStateNode(s.getId(), flags, loopOptimizationNode, successors, matchers, simpleCG, allTransitionsInOneTreeMatcher);
            } else {
                stateNode = new BackwardDFAStateNode(s.getId(), flags, loopOptimizationNode, successors, matchers, simpleCG, allTransitionsInOneTreeMatcher);
            }
            ret[s.getId()] = stateNode;
        }
        return ret;
    }

    private DFASimpleCGTransition createSimpleCGTransition(NFAStateTransition nfaTransition) {
        return DFASimpleCGTransition.create(nfaTransition, isForward() && nfaTransition != null && nfaTransition.getSource() == nfa.getInitialLoopBackTransition().getSource());
    }

    private AllTransitionsInOneTreeMatcher createAllTransitionsInOneTreeMatcher(DFAStateNodeBuilder state) {
        DFAStateTransitionBuilder[] transitions = state.getTransitions();
        CharArrayBuffer sortedRangesBuf = compilationBuffer.getCharRangesBuffer1();
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
                CharSet mb = transitions[i].getMatcherBuilder();
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

    private void registerCGTransition(DFACaptureGroupLazyTransition cgTransition) {
        assert captureGroupTransitions[cgTransition.getId()] == null;
        captureGroupTransitions[cgTransition.getId()] = cgTransition;
    }

    private DFACaptureGroupPartialTransition createCGFinalTransition(NFAStateTransition transition) {
        if (transition == null) {
            return null;
        }
        GroupBoundaries groupBoundaries = transition.getGroupBoundaries();
        byte[][] indexUpdates = DFACaptureGroupPartialTransition.EMPTY_INDEX_UPDATES;
        byte[][] indexClears = DFACaptureGroupPartialTransition.EMPTY_INDEX_CLEARS;
        if (groupBoundaries.hasIndexUpdates()) {
            indexUpdates = new byte[][]{groupBoundaries.updatesToPartialTransitionArray(0)};
        }
        if (groupBoundaries.hasIndexClears()) {
            indexClears = new byte[][]{groupBoundaries.clearsToPartialTransitionArray(0)};
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
            for (DFAStateTransitionBuilder t : s.getTransitions()) {
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
