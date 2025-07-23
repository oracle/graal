/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
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
import com.oracle.truffle.regex.tregex.automaton.StateSetToIntMap;
import com.oracle.truffle.regex.tregex.automaton.TransitionBuilder;
import com.oracle.truffle.regex.tregex.automaton.TransitionConstraint;
import com.oracle.truffle.regex.tregex.automaton.TransitionOp;
import com.oracle.truffle.regex.tregex.automaton.TransitionSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ShortArrayBuffer;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;
import com.oracle.truffle.regex.tregex.nfa.NFATraceFinderGenerator;
import com.oracle.truffle.regex.tregex.nodes.dfa.AllTransitionsInOneTreeMatcher;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingAnchoredFinalTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingDFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingPreFinalTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CGTrackingTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.CounterTracker;
import com.oracle.truffle.regex.tregex.nodes.dfa.CounterTrackerData;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAAbstractTransitionNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFABQTrackingStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFABQTrackingTransitionConstraintsNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFABQTrackingTransitionOpsNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupLazyTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition.IndexOperation;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFACaptureGroupPartialTransition.LastGroupUpdate;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAFindInnerLiteralStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAInitialStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCGTrackingStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFASimpleCGTransition;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorDebugRecorder;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorProperties;
import com.oracle.truffle.regex.tregex.nodes.dfa.TraceFinderDFAStateNode;
import com.oracle.truffle.regex.tregex.parser.Counter;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.AddToSetVisitor;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;
import com.oracle.truffle.regex.util.BitSets;
import com.oracle.truffle.regex.util.EmptyArrays;
import com.oracle.truffle.regex.util.TBitSet;

public final class DFAGenerator implements JsonConvertible {

    private static final DFAStateTransitionBuilder[] EMPTY_TRANSITIONS_ARRAY = new DFAStateTransitionBuilder[0];

    private final TRegexCompilationRequest compilationRequest;
    private final NFA nfa;
    private final TRegexDFAExecutorProperties executorProps;
    private final CompilationBuffer compilationBuffer;

    private final boolean pruneUnambiguousPaths;

    private final Map<DFAStateNodeBuilder, DFAStateNodeBuilder> stateMap = new HashMap<>();
    private final Map<DFAAbstractTransitionNode, DFAAbstractTransitionNode> dfaTransitionsDedupMap = new HashMap<>();
    private final ArrayDeque<DFAStateNodeBuilder> expansionQueue = new ArrayDeque<>();
    private final int[] boundedQuantifierTrackerSizes;
    private final TBitSet bqTrivialAlwaysReEnter;
    private final TBitSet bqTrivialNeverReEnter;
    private DFAStateNodeBuilder[] stateIndexMap = null;

    private short nextID = 1;
    private final DFAStateNodeBuilder lookupDummyState;
    private final Counter transitionIDCounter;
    private final Counter cgPartialTransitionIDCounter = new Counter.ThresholdCounter(TRegexOptions.TRegexMaxDFACGPartialTransitions, "too many partial transitions");
    private int maxNumberOfNfaStates = 1;
    private boolean hasAmbiguousStates = false;
    private boolean doSimpleCG = false;
    private boolean simpleCGMustCopy = false;
    private final StateSet<NFA, NFAState> nfaFirstStates;

    private DFAStateNodeBuilder[] entryStates;
    private DFACaptureGroupTransitionBuilder[] initialCGTransitions;
    private final List<DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo> cgPartialTransitions;
    private final DFATransitionCanonicalizer canonicalizer;

    private List<DFAStateTransitionBuilder[]> bfsTraversalCur;
    private List<DFAStateTransitionBuilder[]> bfsTraversalNext;
    private EconomicMap<Integer, DFAAbstractStateNode> stateReplacements;

    private TRegexDFAExecutorNode innerLiteralPrefixMatcher = null;

    private final SequentialMatchers.Builder matchersBuilder;
    private final List<TruffleString.CodePointSet> indexOfParams = new ArrayList<>();

    public DFAGenerator(TRegexCompilationRequest compilationRequest, NFA nfa, TRegexDFAExecutorProperties executorProps, CompilationBuffer compilationBuffer) {
        transitionIDCounter = new Counter.ThresholdCounter(nfa.getAst().getOptions().getMaxDFASize(), "too many transitions");
        this.compilationRequest = compilationRequest;
        this.nfa = nfa;
        this.executorProps = executorProps;
        this.pruneUnambiguousPaths = executorProps.isBackward() && nfa.isTraceFinderNFA() && nfa.hasReverseUnAnchoredEntry();
        this.compilationBuffer = compilationBuffer;
        this.cgPartialTransitions = debugMode() ? new ArrayList<>() : null;
        this.bfsTraversalCur = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.bfsTraversalNext = needBFSTraversalLists() ? new ArrayList<>() : null;
        this.cgPartialTransitionIDCounter.inc(); // zero is reserved for static empty instance
        this.lookupDummyState = new DFAStateNodeBuilder((short) -1, null, false, false, isForward(), isForward() && !isBooleanMatch());
        if (debugMode()) {
            registerCGPartialTransitionDebugInfo(new DFACaptureGroupTransitionBuilder.PartialTransitionDebugInfo(DFACaptureGroupPartialTransition.getEmptyInstance()));
        }
        assert !nfa.isDead();
        this.canonicalizer = new DFATransitionCanonicalizer(this);
        this.matchersBuilder = nfa.getAst().getEncoding().createMatchersBuilder();
        this.nfaFirstStates = StateSet.create(nfa);
        this.boundedQuantifierTrackerSizes = new int[nfa.getAst().getQuantifierCount()];
        this.bqTrivialAlwaysReEnter = new TBitSet(boundedQuantifierTrackerSizes.length);
        this.bqTrivialNeverReEnter = new TBitSet(boundedQuantifierTrackerSizes.length);
    }

    public NFA getNfa() {
        return nfa;
    }

    public DFAStateNodeBuilder[] getEntryStates() {
        return entryStates;
    }

    private DFAStateNodeBuilder getAnchoredInitialState() {
        return entryStates[0];
    }

    private DFAStateNodeBuilder getMaxOffsetAnchoredInitialState() {
        return entryStates[nfa.getAnchoredEntry().length - 1];
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

    public RegexOptions getOptions() {
        return nfa.getAst().getOptions();
    }

    private Encoding getEncoding() {
        return nfa.getAst().getEncoding();
    }

    private boolean isBooleanMatch() {
        return getOptions().isBooleanMatch();
    }

    public CompilationBuffer getCompilationBuffer() {
        return compilationBuffer;
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
        return pruneUnambiguousPaths || nfa.getAst().getProperties().hasInnerLiteral() || trackBoundedQuantifiers();
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

    private boolean trackBoundedQuantifiers() {
        return nfa.getAst().getRoot().hasQuantifiers();
    }

    private boolean trackPredecessors() {
        return isGenericCG() || trackBoundedQuantifiers();
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
            addSuccessors(nfaFirstStates, nfa.getUnAnchoredInitialState());
            addSuccessors(nfaFirstStates, nfa.getAnchoredInitialState());
        } else {
            createInitialStatesBackward();
        }
        while (!expansionQueue.isEmpty()) {
            expandState(expansionQueue.pop());
        }
        optimizeDFA();
    }

    private void addSuccessors(StateSet<NFA, NFAState> stateSet, NFAState state) {
        if (state != null) {
            for (NFAStateTransition t : state.getSuccessors()) {
                stateSet.add(t.getTarget(isForward()));
            }
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
        ObjectArrayBuffer<DFAAbstractNode> nodes = createDFAExecutorStates();
        short[] entryStateIDs = new short[entryStates.length];
        short[] cgLastTransition = isGenericCG() ? new short[entryStates.length] : null;
        for (int i = 0; i < entryStates.length; i++) {
            if (entryStates[i] == null) {
                entryStateIDs[i] = -1;
            } else {
                entryStateIDs[i] = (short) entryStates[i].getId();
                if (isGenericCG()) {
                    DFACaptureGroupLazyTransitionBuilder lt = getLazyTransitionBuilder(initialCGTransitions[i]);
                    cgLastTransition[i] = lt.getLastTransitionIndex();
                } else if (!isForward() && doSimpleCG) {
                    entryStateIDs[i] = createAndDedupSimpleCGTransition(nodes, entryStateIDs[i], entryStates[i].getNfaTransitionSet().getTransition(0));
                }
            }
        }
        if (executorProps.canFindStart()) {
            assert isForward() && getReplacement(getUnanchoredInitialState().getId()) instanceof DFAFindInnerLiteralStateNode;
            entryStateIDs = new short[]{(short) getUnanchoredInitialState().getId(), (short) getUnanchoredInitialState().getId()};
        }
        assert nodes.get(0) == null;
        nodes.set(0, new DFAInitialStateNode(entryStateIDs, cgLastTransition));
        executorProps.setSimpleCG(doSimpleCG);
        executorProps.setSimpleCGMustCopy(simpleCGMustCopy);
        TRegexDFAExecutorDebugRecorder debugRecorder = TRegexDFAExecutorDebugRecorder.create(getOptions(), this);
        int[] quantifierBounds = nfa.getAst().getAllQuantifierBounds();

        CounterTrackerData.Builder counterDataBuilder = new CounterTrackerData.Builder();
        CounterTracker[] counterTrackers = CounterTracker.build(quantifierBounds, boundedQuantifierTrackerSizes, counterDataBuilder, bqTrivialAlwaysReEnter, bqTrivialNeverReEnter,
                        getOptions().isRegressionTestMode());
        checkIfAllQuantifierOperationsAreSupported(nodes, counterTrackers);

        return new TRegexDFAExecutorNode(nfa.getAst().getSource(), executorProps, getNfa().getAst().getNumberOfCaptureGroups(), maxNumberOfNfaStates,
                        indexOfParams.toArray(TruffleString.CodePointSet[]::new), nodes.toArray(DFAAbstractNode[]::new),
                        debugRecorder, innerLiteralPrefixMatcher, counterDataBuilder, counterTrackers, nfa.getNumberOfStates(), getOptions().isRegressionTestMode());
    }

    /**
     * Temporary data used by {@link #optimizeBoundedQuantifierDataMapping()}.
     */
    private static final class QuantifierMappingBuilder {

        /**
         * This array maps every DFA state to a {@link StateMapping} object. Every
         * {@link StateMapping} contains a subset of its respective DFA state's NFA state set. This
         * subset consists of only those NFA states that are the source or target of
         * {@link TransitionConstraint}s or {@link TransitionOp}s. {@link #maxMapSize} stores the
         * size of the largest subset created so far.
         * <p>
         * The aforementioned subsets and consequently {@link #maxMapSize} are calculated in the
         * first phase of {@link #optimizeBoundedQuantifierDataMapping()} and stay stable for the
         * rest of the algorithm.
         */
        private final StateMapping[] stateMappings;
        private int maxMapSize = 0;

        /**
         * Temporary data used in the second phase of
         * {@link #optimizeBoundedQuantifierDataMapping()}. These data structures are reset for
         * every DFA state.
         */
        private int[] newOrder;
        private int[] copySource;
        private TBitSet targetsUsed;
        private TBitSet copyTargets;
        private final LongArrayBuffer ops = new LongArrayBuffer();
        private final LongArrayBuffer opsSet1 = new LongArrayBuffer();

        private QuantifierMappingBuilder(StateMapping[] stateMappings) {
            this.stateMappings = stateMappings;
        }

        private void addState(DFAStateNodeBuilder state, NFAState mapState) {
            StateSet<NFA, NFAState> states = stateMappings[state.getId()].states;
            if (states.add(mapState)) {
                maxMapSize = Math.max(maxMapSize, states.size());
                if (maxMapSize > 0xff) {
                    throw new UnsupportedRegexException("too many parallel NFA states in one DFA state for bounded quantifier tracking");
                }
            }
        }

        private void initBuffers() {
            newOrder = new int[maxMapSize];
            copySource = new int[maxMapSize];
            targetsUsed = new TBitSet(maxMapSize);
            copyTargets = new TBitSet(maxMapSize);
        }

        private void resetBuffers() {
            Arrays.fill(newOrder, -1);
            Arrays.fill(copySource, -1);
            targetsUsed.clear();
            copyTargets.clear();
            opsSet1.clear();
            ops.clear();
        }

        /**
         * Re-order the {@link TransitionOp}s in {@link #ops} such that no operation modifies the
         * {@link TransitionOp#getSource(long) source} of any subsequent operation. If no such order
         * exists, temporary copies of counter-sets may be inserted.
         */
        private void scheduleOps() {
            if (ops.length() <= 1) {
                return;
            }
            for (int last = ops.length() - 1; last > 0; last--) {
                int toSchedule = scheduleOpsFindCandidate(last);
                if (toSchedule == -1) {
                    // TODO
                    // System.out.println("dependency cycle:");
                    // for (int i = 0; i <= last; i++) {
                    // System.out.println(TransitionOp.toString(ops.get(i)));
                    // }
                    // resolveDependencyCycle(last + 1);
                    throw new UnsupportedRegexException("dependency cycle");
                }
                long tmp = ops.get(last);
                ops.set(last, ops.get(toSchedule));
                ops.set(toSchedule, tmp);
            }
        }

        /**
         * Returns the index of the first operation between index {@code 0} and {@code last}
         * (inclusive) whose {@link TransitionOp#getSource(long) source} is unmodified by all other
         * operations in the specified range. If no such operation exists, returns {@code -1}.
         */
        private int scheduleOpsFindCandidate(int last) {
            outer: for (int i = 0; i <= last; i++) {
                int source = TransitionOp.getSource(ops.get(i));
                for (int j = 0; j <= last; j++) {
                    if (i != j && TransitionOp.getTarget(ops.get(j)) == source) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }

        @SuppressWarnings("unused")
        private void resolveDependencyCycle(int length) {
            TBitSet[] unions = new TBitSet[maxMapSize];
            TBitSet[] inc = new TBitSet[maxMapSize];
            for (int i = 0; i < length; i++) {
                long op = ops.get(i);
                int kind = TransitionOp.getKind(op);
                int source = TransitionOp.getSource(op);
                int target = TransitionOp.getTarget(op);
                if (unions[target] == null) {
                    unions[target] = new TBitSet(maxMapSize);
                    inc[target] = new TBitSet(maxMapSize);
                }
                unions[target].set(source);
                if (TransitionOp.getModifier(op) == TransitionOp.union) {
                    unions[target].set(target);
                }
                if (kind == TransitionOp.inc) {
                    inc[target].set(source);
                }
            }
            for (int i = 0; i < unions.length; i++) {
                TBitSet bs = unions[i];
                System.out.println("unions " + i + ": " + bs);
            }
            for (int i = 0; i < inc.length; i++) {
                TBitSet bs = inc[i];
                System.out.println("inc " + i + ": " + bs);
            }
        }
    }

    private static final class StateMapping {

        private final StateSet<NFA, NFAState> states;
        private StateSetToIntMap<NFAState, NFAStateTransition> mapping;

        private StateMapping(StateSet<NFA, NFAState> states) {
            this.states = states;
        }

        private boolean hasMapping() {
            return mapping != null;
        }

        private StateSetToIntMap<NFAState, NFAStateTransition> getMappingOrDefault() {
            if (mapping == null) {
                mapping = StateSetToIntMap.create(states);
                int i = 0;
                for (NFAState s : states) {
                    mapping.put(s, i++);
                }
            }
            return mapping;
        }

        private StateSetToIntMap<NFAState, NFAStateTransition> createMapping() {
            mapping = StateSetToIntMap.create(states);
            return mapping;
        }

        public StateSetToIntMap<NFAState, NFAStateTransition> getMapping() {
            return mapping;
        }
    }

    /**
     * Before this method is called, {@link TransitionConstraint quantifier constraints} and
     * {@link TransitionOp quantifier count operations} refer to individual NFA states in their
     * {@link TransitionConstraint#getStateID(long) state id}, {@link TransitionOp#getSource(long)
     * source} and {@link TransitionOp#getTarget(long) target} fields.
     * <p>
     * For the DFA, we map these NFA state IDs to ascending zero-based indices <b>for each DFA state
     * individually.</b> This lets us avoid having to copy counter value sets when they get moved
     * from one NFA state to the next.
     */
    private void optimizeBoundedQuantifierDataMapping() {
        if (!trackBoundedQuantifiers()) {
            return;
        }
        /*
         * First phase: generate the subset of NFA states relevant for bounded quantifier tracking
         * for every DFA state.
         */
        int quantifierCount = nfa.getAst().getQuantifierCount();
        QuantifierMappingBuilder[] mappings = new QuantifierMappingBuilder[quantifierCount];
        for (int i = 0; i < mappings.length; i++) {
            mappings[i] = new QuantifierMappingBuilder(new StateMapping[nextID]);
        }
        for (DFAStateNodeBuilder state : stateMap.values()) {
            for (QuantifierMappingBuilder mapping : mappings) {
                mapping.stateMappings[state.getId()] = new StateMapping(StateSet.create(nfa));
            }
            for (long[] unAnchoredFinalConstraints : state.getUnAnchoredFinalConstraints()) {
                for (long cs : unAnchoredFinalConstraints) {
                    mappings[TransitionConstraint.getQuantifierID(cs)].addState(state, nfa.getState(TransitionConstraint.getStateID(cs)));
                }
            }
            for (long[] anchoredFinalConstraints : state.getAnchoredFinalConstraints()) {
                for (long cs : anchoredFinalConstraints) {
                    mappings[TransitionConstraint.getQuantifierID(cs)].addState(state, nfa.getState(TransitionConstraint.getStateID(cs)));
                }
            }
            for (DFAStateTransitionBuilder transition : state.getSuccessors()) {
                for (long cs : transition.getConstraints()) {
                    mappings[TransitionConstraint.getQuantifierID(cs)].addState(state, nfa.getState(TransitionConstraint.getStateID(cs)));
                }
                if (transition.getTarget().isUnAnchoredFinalState()) {
                    transition.setOperations(TransitionOp.NO_OP);
                } else {
                    for (long op : transition.getOperations()) {
                        if (TransitionOp.getSource(op) != TransitionOp.NO_SOURCE) {
                            mappings[TransitionOp.getQuantifierID(op)].addState(state, nfa.getState(TransitionOp.getSource(op)));
                        }
                    }
                }
            }
        }
        DFAStateNodeBuilder anchoredInitialState = getAnchoredInitialState();
        DFAStateNodeBuilder unAnchoredInitialState = getUnanchoredInitialState();
        TBitSet visited = new TBitSet(nextID);
        visited.set(anchoredInitialState.getId());
        anchoredInitialState.setReachable();
        bfsTraversalCur.clear();
        bfsTraversalCur.add(anchoredInitialState.getSuccessors());
        if (unAnchoredInitialState != null && anchoredInitialState != unAnchoredInitialState) {
            visited.set(unAnchoredInitialState.getId());
            unAnchoredInitialState.setReachable();
            bfsTraversalCur.add(unAnchoredInitialState.getSuccessors());
        }

        /*
         * Second phase: map NFA states to zero-based indices.
         */
        for (QuantifierMappingBuilder m : mappings) {
            m.initBuffers();
        }
        LongArrayBuffer opsNormalized = new LongArrayBuffer();
        LongArrayBuffer opsDeferred = new LongArrayBuffer();
        ObjectArrayBuffer<StateSetToIntMap<NFAState, NFAStateTransition>> propagatedMaps = new ObjectArrayBuffer<>();

        while (!bfsTraversalCur.isEmpty()) {
            bfsTraversalNext.clear();
            for (DFAStateTransitionBuilder[] cur : bfsTraversalCur) {
                for (DFAStateTransitionBuilder transition : cur) {
                    for (QuantifierMappingBuilder mb : mappings) {
                        mb.resetBuffers();
                    }
                    opsNormalized.clear();
                    opsDeferred.clear();
                    DFAStateNodeBuilder target = transition.getTarget();
                    target.setReachable();
                    transition.setConstraints(rewriteConstraints(transition.getSource(), mappings, transition.getConstraints()));
                    for (long op : transition.getOperations()) {
                        int quantifierID = TransitionOp.getQuantifierID(op);
                        int nfaTarget = TransitionOp.getTarget(op);
                        QuantifierMappingBuilder mb = mappings[quantifierID];
                        StateMapping targetStateMapping = mb.stateMappings[transition.getTarget().getId()];
                        if (targetStateMapping.states.contains(nfa.getState(nfaTarget))) {
                            // remove ops whose target is unused in the target DFA state
                            opsNormalized.add(op);
                        }
                    }
                    TransitionOp.normalize(opsNormalized);

                    /*
                     * First pass: try to propagate current mapping to target.
                     */
                    for (long op : opsNormalized) {
                        int quantifierID = TransitionOp.getQuantifierID(op);
                        int nfaSource = TransitionOp.getSource(op);
                        int nfaTarget = TransitionOp.getTarget(op);

                        QuantifierMappingBuilder mb = mappings[quantifierID];
                        StateMapping targetStateMapping = mb.stateMappings[transition.getTarget().getId()];
                        if (!targetStateMapping.hasMapping()) {
                            targetStateMapping.createMapping();
                            propagatedMaps.add(targetStateMapping.getMapping());
                        }
                        if (nfaSource == TransitionOp.NO_SOURCE) {
                            continue;
                        }
                        StateSetToIntMap<NFAState, NFAStateTransition> sourceMap = mb.stateMappings[transition.getSource().getId()].getMappingOrDefault();
                        StateSetToIntMap<NFAState, NFAStateTransition> targetMap = targetStateMapping.getMapping();
                        int nfaSourceMapped = sourceMap.getValue(nfaSource);
                        if (targetMap.getValue(nfaTarget) == -1 && !targetMap.isValueUsed(nfaSourceMapped)) {
                            targetMap.put(nfaTarget, nfaSourceMapped);
                        }
                    }
                    // assign slots that could not be propagated
                    for (StateSetToIntMap<NFAState, NFAStateTransition> propagated : propagatedMaps) {
                        propagated.fillRest();
                    }
                    propagatedMaps.clear();

                    /*
                     * Second pass: write re-mapped ops
                     */
                    for (long op : opsNormalized) {
                        int quantifierID = TransitionOp.getQuantifierID(op);
                        int nfaSource = TransitionOp.getSource(op);
                        int nfaTarget = TransitionOp.getTarget(op);
                        int kind = TransitionOp.getKind(op);

                        QuantifierMappingBuilder mb = mappings[quantifierID];
                        StateSetToIntMap<NFAState, NFAStateTransition> sourceMap = mb.stateMappings[transition.getSource().getId()].getMapping();
                        StateSetToIntMap<NFAState, NFAStateTransition> targetMap = mb.stateMappings[transition.getTarget().getId()].getMapping();
                        int nfaSourceMapped = nfaSource == TransitionOp.NO_SOURCE ? TransitionOp.NO_SOURCE : sourceMap.getValue(nfaSource);
                        int nfaTargetMapped = targetMap.getValue(nfaTarget);
                        assert nfaTargetMapped >= 0;
                        if (nfaSourceMapped != TransitionOp.NO_SOURCE) {
                            if (mb.copySource[nfaSourceMapped] < 0) {
                                if (mb.newOrder[nfaTargetMapped] < 0) {
                                    mb.newOrder[nfaTargetMapped] = nfaSourceMapped;
                                    mb.copySource[nfaSourceMapped] = nfaTargetMapped;
                                    if (kind == TransitionOp.maintain) {
                                        mb.targetsUsed.set(nfaTargetMapped);
                                    } else {
                                        mb.ops.add(TransitionOp.create(quantifierID, nfaTargetMapped, nfaTargetMapped, kind, TransitionOp.noModifier));
                                    }
                                } else {
                                    opsDeferred.add(op);
                                }
                            } else {
                                mb.ops.add(TransitionOp.create(quantifierID, mb.copySource[nfaSourceMapped], nfaTargetMapped, kind, TransitionOp.noModifier));
                            }
                        } else {
                            assert kind == TransitionOp.set1;
                            mb.opsSet1.add(TransitionOp.create(quantifierID, TransitionOp.NO_SOURCE, nfaTargetMapped, TransitionOp.set1));
                        }
                    }
                    for (QuantifierMappingBuilder mb : mappings) {
                        int order = 0;
                        for (int i = 0; i < mb.newOrder.length; i++) {
                            if (mb.newOrder[i] == -1) {
                                while (mb.copySource[order] >= 0) {
                                    order++;
                                }
                                mb.newOrder[i] = order;
                                mb.copySource[order++] = i;
                            }
                        }
                    }
                    for (long op : opsDeferred) {
                        int quantifierID = TransitionOp.getQuantifierID(op);
                        int nfaSource = TransitionOp.getSource(op);
                        int nfaTarget = TransitionOp.getTarget(op);
                        int kind = TransitionOp.getKind(op);

                        QuantifierMappingBuilder mb = mappings[quantifierID];
                        StateSetToIntMap<NFAState, NFAStateTransition> sourceMap = mb.stateMappings[transition.getSource().getId()].getMapping();
                        StateSetToIntMap<NFAState, NFAStateTransition> targetMap = mb.stateMappings[transition.getTarget().getId()].getMapping();
                        assert nfaSource != TransitionOp.NO_SOURCE;
                        int nfaSourceMapped = sourceMap.getValue(nfaSource);
                        int nfaTargetMapped = targetMap.getValue(nfaTarget);
                        assert nfaTargetMapped >= 0;
                        int copySource = mb.copySource[nfaSourceMapped];
                        mb.ops.add(TransitionOp.create(quantifierID, copySource, nfaTargetMapped, kind, TransitionOp.noModifier));
                    }

                    opsDeferred.clear();
                    for (int quantifierID = 0; quantifierID < mappings.length; quantifierID++) {
                        QuantifierMappingBuilder mb = mappings[quantifierID];
                        mb.scheduleOps();
                        newOrderToSequenceOfSwaps(quantifierID, mb.newOrder, opsDeferred);
                    }
                    for (QuantifierMappingBuilder mb : mappings) {
                        for (long op : mb.ops) {
                            int nfaTarget = TransitionOp.getTarget(op);
                            opsDeferred.add(TransitionOp.setModifier(mb.targetsUsed.get(nfaTarget) ? TransitionOp.union : TransitionOp.overwrite, op));
                            mb.targetsUsed.set(nfaTarget);
                        }
                        for (long op : mb.opsSet1) {
                            int nfaTarget = TransitionOp.getTarget(op);
                            opsDeferred.add(TransitionOp.setModifier(mb.targetsUsed.get(nfaTarget) ? TransitionOp.union : TransitionOp.overwrite, op));
                            mb.targetsUsed.set(nfaTarget);
                        }
                    }
                    transition.setOperations(opsDeferred.toArray());
                    if (visited.get(target.getId())) {
                        continue;
                    }
                    visited.set(target.getId());
                    bfsExpand(target);
                }
            }
            bfsSwapLists();
        }
        for (DFAStateNodeBuilder state : stateMap.values()) {
            rewriteConstraints(state, mappings, state.getUnAnchoredFinalConstraints());
            rewriteConstraints(state, mappings, state.getAnchoredFinalConstraints());
        }
        for (int i = 0; i < mappings.length; i++) {
            boundedQuantifierTrackerSizes[i] = mappings[i].maxMapSize;
        }
        checkTrivialQuantifiers();
    }

    private static void newOrderToSequenceOfSwaps(int quantifierID, int[] newOrder, LongArrayBuffer swapOps) {
        int lengthBefore = swapOps.length();
        for (int i = 0; i < newOrder.length; i++) {
            int swapSource = newOrder[i];
            int swapTarget = swapSource;
            if (swapSource == i) {
                continue;
            }
            do {
                swapSource = swapTarget;
                swapTarget = newOrder[swapTarget];
                swapOps.add(TransitionOp.create(quantifierID, swapSource, swapTarget, TransitionOp.maintain, TransitionOp.swap));
                newOrder[swapSource] = swapSource;
            } while (swapTarget != i);
        }
        assert swapOps.length() - lengthBefore < newOrder.length || newOrder.length == 0;
    }

    /**
     * Temporary data used by {@link #createBQTransitions(ObjectArrayBuffer)}.
     */
    private static final class BQOpBucket {
        private final ArrayList<DFAStateTransitionBuilder> transitions = new ArrayList<>();
        private DFABQTrackingTransitionOpsNode bqTransition = null;
        private BQOpBucket parent;

        DFABQTrackingTransitionOpsNode getBQTransition(DFAGenerator dfaGen, ObjectArrayBuffer<DFAAbstractNode> nodes, DFAStateTransitionBuilder t, int curLevel) {
            if (bqTransition != null) {
                return bqTransition;
            }
            long[] ops = t.getOperations();
            int fromIndex = ops.length - curLevel;
            int toIndex = fromIndex;
            BQOpBucket curBucket = this;
            DFABQTrackingTransitionOpsNode parentTransition = null;
            do {
                parentTransition = curBucket.bqTransition;
                if (parentTransition != null) {
                    break;
                }
                toIndex++;
                curBucket = curBucket.parent;
            } while (curBucket != null);
            short successorID = parentTransition == null ? (short) t.getTarget().getId() : parentTransition.getId();
            assert fromIndex >= 0 && fromIndex < ops.length && toIndex > fromIndex && toIndex <= ops.length;
            long[] opsSlice = Arrays.copyOfRange(ops, fromIndex, toIndex);
            bqTransition = insertNode(nodes, new DFABQTrackingTransitionOpsNode(dfaGen.nextID++, successorID, opsSlice));
            return bqTransition;
        }
    }

    /**
     * Transforms all DFA transitions' {@link TransitionOp quantifier operations} into
     * {@link DFABQTrackingTransitionOpsNode}s, de-duplicating equal operations where possible.
     * <p>
     * De-duplication is done per DFA state by comparing all preceding transition's operations in
     * reverse order and merging them into a reverse tree structure. For example, if a state has two
     * predecessors, where predecessor 0 performs a {@link TransitionOp#set1} followed by
     * {@link TransitionOp#inc}, and predecessor 1 performs a {@link TransitionOp#maintain} followed
     * by {@link TransitionOp#inc}, the two {@link TransitionOp#inc inc} operations would be merged
     * into a single {@link DFABQTrackingTransitionOpsNode} whose successor would be the DFA state,
     * and the {@link TransitionOp#set1 set1} and {@link TransitionOp#maintain maintain} operations
     * would be put into separate {@link DFABQTrackingTransitionOpsNode}s whose successor would be
     * the merged {@link TransitionOp#inc inc}-node.
     */
    private void createBQTransitions(ObjectArrayBuffer<DFAAbstractNode> nodes) {
        ArrayList<EconomicMap<Long, BQOpBucket>> bucketStack = new ArrayList<>();
        bucketStack.add(EconomicMap.create());
        for (DFAStateNodeBuilder s : stateMap.values()) {
            DFAStateTransitionBuilder[] predecessors = s.getPredecessors();
            if (predecessors.length == 0) {
                continue;
            }
            if (predecessors.length == 1) {
                if (predecessors[0].getOperations() == null || predecessors[0].getOperations().length == 0) {
                    continue;
                }
                predecessors[0].setBqTransition(insertNode(nodes, new DFABQTrackingTransitionOpsNode(nextID++, (short) s.getId(), predecessors[0].getOperations())));
                continue;
            }
            EconomicMap<Long, BQOpBucket> topLevelBuckets = bucketStack.get(0);
            for (DFAStateTransitionBuilder t : predecessors) {
                if (t == null) {
                    continue;
                }
                long[] ops = t.getOperations();
                if (ops == null || ops.length == 0) {
                    continue;
                }
                long lastOp = ops[ops.length - 1];
                BQOpBucket bucket = topLevelBuckets.get(lastOp);
                if (bucket == null) {
                    bucket = new BQOpBucket();
                    topLevelBuckets.put(lastOp, bucket);
                }
                bucket.transitions.add(t);
            }
            int level = 0;
            while (true) {
                EconomicMap<Long, BQOpBucket> curLevel = bucketStack.get(level);
                if (curLevel.isEmpty()) {
                    if (level == 0) {
                        break;
                    } else {
                        level--;
                        continue;
                    }
                }
                MapCursor<Long, BQOpBucket> cursor = curLevel.getEntries();
                cursor.advance();
                BQOpBucket curBucket = cursor.getValue();
                cursor.remove();
                if (++level == bucketStack.size()) {
                    bucketStack.add(EconomicMap.create());
                }
                EconomicMap<Long, BQOpBucket> nextLevel = bucketStack.get(level);
                assert nextLevel.isEmpty();
                for (DFAStateTransitionBuilder t : curBucket.transitions) {
                    long[] ops = t.getOperations();
                    int i = ops.length - (level + 1);
                    if (i < 0) {
                        t.setBqTransition(curBucket.getBQTransition(this, nodes, t, level));
                        continue;
                    }
                    long lastOp = ops[i];
                    BQOpBucket nextBucket = nextLevel.get(lastOp);
                    if (nextBucket == null) {
                        if (nextLevel.size() == 1) {
                            curBucket.getBQTransition(this, nodes, t, level);
                        }
                        nextBucket = new BQOpBucket();
                        nextBucket.parent = curBucket;
                        nextLevel.put(lastOp, nextBucket);
                    }
                    nextBucket.transitions.add(t);
                }
            }
        }
    }

    private static void rewriteConstraints(DFAStateNodeBuilder state, QuantifierMappingBuilder[] mappings, long[][] constraints) {
        for (int i = 0; i < constraints.length; i++) {
            constraints[i] = rewriteConstraints(state, mappings, constraints[i]);
        }
    }

    private static long[] rewriteConstraints(DFAStateNodeBuilder state, QuantifierMappingBuilder[] mappings, long[] constraints) {
        long[] ret = new long[constraints.length];
        for (int i = 0; i < constraints.length; i++) {
            long cs = constraints[i];
            QuantifierMappingBuilder mb = mappings[TransitionConstraint.getQuantifierID(cs)];
            StateSetToIntMap<NFAState, NFAStateTransition> mapping = mb.stateMappings[state.getId()].getMappingOrDefault();
            ret[i] = TransitionConstraint.setStateID(cs, mapping.getValue(TransitionConstraint.getStateID(cs)));
        }
        return ret;
    }

    /**
     * Identify quantifiers that don't need full counter set tracking, and can instead be tracked
     * with {@link com.oracle.truffle.regex.tregex.nodes.dfa.CounterTrackerTrivialAlwaysReEnter} or
     * {@link com.oracle.truffle.regex.tregex.nodes.dfa.CounterTrackerTrivialNeverReEnter}.
     */
    private void checkTrivialQuantifiers() {
        if (boundedQuantifierTrackerSizes.length == 0) {
            return;
        }
        bqTrivialAlwaysReEnter.setRange(0, boundedQuantifierTrackerSizes.length - 1);
        bqTrivialNeverReEnter.setRange(0, boundedQuantifierTrackerSizes.length - 1);
        ArrayList<TBitSet> inc = new ArrayList<>();
        ArrayList<TBitSet> set1 = new ArrayList<>();
        for (int numberOfCells : boundedQuantifierTrackerSizes) {
            if (inc.size() < numberOfCells) {
                for (int i = inc.size(); i < numberOfCells; i++) {
                    inc.add(new TBitSet(boundedQuantifierTrackerSizes.length));
                    set1.add(new TBitSet(boundedQuantifierTrackerSizes.length));
                }
            }
        }
        for (DFAStateNodeBuilder state : stateMap.values()) {
            if (!state.isReachable()) {
                continue;
            }
            for (DFAStateTransitionBuilder t : state.getSuccessors()) {
                for (long operation : t.getOperations()) {
                    int qId = TransitionOp.getQuantifierID(operation);
                    int kind = TransitionOp.getKind(operation);
                    int target = TransitionOp.getTarget(operation);
                    int modifier = TransitionOp.getModifier(operation);
                    if (modifier == TransitionOp.union) {
                        bqTrivialNeverReEnter.clear(qId);
                    }
                    switch (kind) {
                        case TransitionOp.inc -> {
                            inc.get(target).set(qId);
                        }
                        case TransitionOp.set1 -> {
                            set1.get(target).set(qId);
                        }
                    }
                }
                for (int i = 0; i < inc.size(); i++) {
                    inc.get(i).subtract(set1.get(i));
                    bqTrivialAlwaysReEnter.subtract(inc.get(i));
                    inc.get(i).clear();
                    set1.get(i).clear();
                }
            }
        }
    }

    private static void checkIfAllQuantifierOperationsAreSupported(ObjectArrayBuffer<DFAAbstractNode> nodes, CounterTracker[] counterTrackers) {
        for (DFAAbstractNode node : nodes) {
            if (node instanceof DFABQTrackingTransitionOpsNode t) {
                for (long op : t.getOperations()) {
                    int qId = TransitionOp.getQuantifierID(op);
                    if (!counterTrackers[qId].support(op)) {
                        throw new UnsupportedRegexException("Regex has unsupported bounded quantifier");
                    }
                }
            }
        }
    }

    private void createInitialStatesForward() {
        final int numberOfEntryPoints = nfa.getAnchoredEntry().length;
        entryStates = new DFAStateNodeBuilder[numberOfEntryPoints * 2];
        nfa.setInitialLoopBack(isSearching() && !nfa.getAst().getFlags().isSticky());
        for (int i = 0; i < numberOfEntryPoints; i++) {
            if (nfa.getAnchoredEntry()[i] == null) {
                assert nfa.getUnAnchoredEntry()[i] == null;
                entryStates[i] = null;
                entryStates[numberOfEntryPoints + i] = null;
            } else if (nfa.getUnAnchoredEntry()[i] == null) {
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
            entryStates[0] = createInitialStateBackward(nfa.getReverseAnchoredEntry(), nfa.getReverseUnAnchoredEntry());
            entryStates[1] = createInitialStateBackward(nfa.getReverseUnAnchoredEntry());
        } else {
            entryStates[0] = createInitialStateBackward(nfa.getReverseAnchoredEntry());
            entryStates[1] = null;
        }
    }

    private DFAStateNodeBuilder createInitialStateBackward(NFAStateTransition... entries) {
        /*
         * In forward mode, a DFA state consists of a set of NFA transitions, and a DFA state is
         * considered final if one of its NFA transitions' targets _contains a subsequent
         * transition_ to a NFA final state.
         * 
         * In backward mode, we skip the NFA's final state transitions, so the backward DFA states
         * consist of the same NFA transitions as the forward DFA states. As a consequence of this,
         * backward DFA states are considered final if one of their NFA transition's targets _is_ a
         * backward NFA final state.
         */
        ObjectArrayBuffer<NFAStateTransition> transitionSet = compilationBuffer.getObjectBuffer1();
        StateSet<NFA, NFAState> stateSet = StateSet.create(nfa);
        CodePointSet cps = null;
        GroupBoundaries groupBoundaries = null;
        for (NFAStateTransition entry : entries) {
            // skip past reverse entry transitions (i.e. forward final transitions)
            for (NFAStateTransition predecessor : entry.getTarget(false).getPredecessors()) {
                if (groupBoundaries == null) {
                    cps = predecessor.getCodePointSet();
                    groupBoundaries = predecessor.getGroupBoundaries();
                } else {
                    if (!predecessor.getGroupBoundaries().equals(groupBoundaries) || !predecessor.getCodePointSet().equals(cps)) {
                        hasAmbiguousStates = true;
                    }
                }
                stateSet.add(predecessor.getTarget(false));
                transitionSet.add(predecessor);
            }
        }
        return createInitialState(createTransitionBuilder(new TransitionSet<>(transitionSet.toArray(NFAStateTransition[]::new), stateSet)));
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
            if (isForward()) {
                for (NFAStateTransition nfaTransition : nfaState.getSuccessors(true)) {
                    NFAState target = nfaTransition.getTarget(true);
                    if (!target.isFinalState(true)) {
                        canonicalizer.addArgument(nfaTransition, nfaTransition.getCodePointSet(), nfaTransition.getConstraints(), nfaTransition.getOperations());
                    } else if (target.isUnAnchoredFinalState() && nfaTransition.getConstraints().length == 0) {
                        assert target == nfa.getReverseUnAnchoredEntry().getSource();
                        break outer;
                    }
                }
            } else if (!nfaState.isFinalState(false) && (!state.isBackwardPrefixState() || nfaState.hasPrefixStates())) {
                for (NFAStateTransition nfaTransition : nfaState.getSuccessors(false)) {
                    anyPrefixStateSuccessors |= nfaState.hasPrefixStates();
                    allPrefixStateSuccessors &= nfaState.hasPrefixStates();
                    canonicalizer.addArgument(nfaTransition, nfaTransition.getCodePointSet(), nfaTransition.getConstraints(), nfaTransition.getOperations());
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
            DFAStateNodeBuilder successorState = lookupState(transition.getTransitionSet(), state.isBackwardPrefixState());
            if (successorState == null) {
                successorState = createState(transition.getTransitionSet(), state.isBackwardPrefixState(), false);
            } else if (pruneUnambiguousPaths) {
                reScheduleFinalStateSuccessors(state, successorState);
            }
            if (pruneUnambiguousPaths && (state.isUnAnchoredFinalState() || state.isFinalStateSuccessor())) {
                state.setFinalStateSuccessor();
                successorState.setFinalStateSuccessor();
            }
            transition.setTarget(successorState);
            successorState.updateFinalStateData(this);
            if (trackPredecessors()) {
                transition.getTarget().incPredecessors();
            }
            if (state.isUnAnchoredFinalState() && successorState != state) {
                // final state transition must not overwrite the single capture group result in
                // simpleCG mode if there are more possible states after the current final state,
                // mostly due to side effects from zero-width group matches, e.g.:
                // in expression /a(?:b|())/, the first 'a' has a final state transition with a
                // zero-width match of capture group 1, and 'b' has another one without.
                simpleCGMustCopy = true;
            }
        }
        state.setSuccessors(transitions);
    }

    private DFAStateTransitionBuilder createTransitionBuilder(TransitionSet<NFA, NFAState, NFAStateTransition> transitionSet) {
        if (isGenericCG()) {
            return new DFACaptureGroupTransitionBuilder(transitionSet, null, TransitionConstraint.NO_CONSTRAINTS, TransitionOp.NO_OP, this);
        } else {
            return new DFAStateTransitionBuilder(transitionSet, null, TransitionConstraint.NO_CONSTRAINTS, TransitionOp.NO_OP);
        }
    }

    private TransitionSet<NFA, NFAState, NFAStateTransition> createNFATransitionSet(NFAStateTransition initialTransition) {
        return new TransitionSet<>(new NFAStateTransition[]{initialTransition}, StateSet.create(nfa, initialTransition.getTarget(isForward())));
    }

    private TransitionSet<NFA, NFAState, NFAStateTransition> createNFATransitionSet(NFAStateTransition t1, NFAStateTransition t2) {
        if (t1 == t2) {
            return createNFATransitionSet(t1);
        }
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
     * @see NFATraceFinderGenerator
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
        DFAStateNodeBuilder dfaState = new DFAStateNodeBuilder(nextID++, transitionSet, isBackwardPrefixState, isInitialState, isForward(), isForward() && !isBooleanMatch());
        stateMap.put(dfaState, dfaState);
        if (stateMap.size() + (isForward() ? expansionQueue.size() : 0) > TRegexOptions.TRegexMaxDFASize) {
            throw new UnsupportedRegexException((isForward() ? (isGenericCG() ? "CG" : "Forward") : "Backward") + " DFA explosion");
        }
        if (!hasAmbiguousStates && (transitionSet.size() > 2 || (transitionSet.size() == 2 && transitionSet.getTransition(1) != nfa.getInitialLoopBackTransition()))) {
            hasAmbiguousStates = true;
        }
        if (!(isBooleanMatch() && dfaState.updateFinalStateData(this).isUnAnchoredFinalState())) {
            expansionQueue.push(dfaState);
        }
        return dfaState;
    }

    private void optimizeDFA() {
        RegexProperties props = nfa.getAst().getProperties();
        Group root = nfa.getAst().getRoot();

        boolean doSimpleCGBackward = !props.hasQuantifiers() && !props.hasEmptyCaptureGroups() &&
                        (!root.hasCaret() || root.startsWithCaret()) &&
                        (!root.hasDollar() || root.endsWithDollar());

        doSimpleCG = (isForward() || doSimpleCGBackward) &&
                        !isBooleanMatch() &&
                        executorProps.isAllowSimpleCG() &&
                        !hasAmbiguousStates &&
                        !nfa.isTraceFinderNFA() &&
                        !isGenericCG() &&
                        !props.hasQuantifiers() &&
                        (isSearching() || props.hasCaptureGroups()) &&
                        (props.hasAlternations() || props.hasLookAroundAssertions());

        tryInnerLiteralOptimization();
    }

    private void tryInnerLiteralOptimization() {
        RegexProperties props = nfa.getAst().getProperties();
        if (!(isForward() && isSearching() && !isGenericCG() && !nfa.getAst().getFlags().isSticky() && props.hasInnerLiteral() && getUnanchoredInitialState() != null)) {
            return;
        }
        int literalEnd = props.getInnerLiteralEnd();
        int literalStart = props.getInnerLiteralStart();
        Sequence rootSeq = nfa.getAst().getRoot().getFirstAlternative();

        boolean boundedQuantifiersInPrefix = false;
        boolean maybeOverlappingLookArounds = false;
        // find all parser tree nodes of the prefix
        StateSet<RegexAST, RegexASTNode> prefixAstNodes = StateSet.create(nfa.getAst());
        for (int i = 0; i < literalStart; i++) {
            Term t = rootSeq.getTerms().get(i);
            boundedQuantifiersInPrefix |= t.hasQuantifiers();
            maybeOverlappingLookArounds |= t.hasLookArounds();
            AddToSetVisitor.addCharacterClasses(prefixAstNodes, t);
        }
        if (boundedQuantifiersInPrefix) {
            // not supported in backwards mode right now
            return;
        }
        boolean lookBehindsAfterLiteral = false;
        for (int i = literalEnd; i < rootSeq.size(); i++) {
            Term t = rootSeq.getTerms().get(i);
            lookBehindsAfterLiteral |= t.hasLookBehinds();
        }
        maybeOverlappingLookArounds |= lookBehindsAfterLiteral;

        // find NFA states of the prefix and the beginning and end of the literal
        StateSet<NFA, NFAState> prefixNFAStates = StateSet.create(nfa);
        NFAState unAnchoredInitialState = nfa.getMaxOffsetUnAnchoredInitialState();
        if (unAnchoredInitialState != null) {
            prefixNFAStates.add(unAnchoredInitialState);
        }
        NFAState literalFirstState = null;
        NFAState literalLastState = null;
        for (NFAState s : nfa.getStates()) {
            if (s == null) {
                continue;
            }
            if (!maybeOverlappingLookArounds && !s.getStateSet().isEmpty() && prefixAstNodes.containsAll(s.getStateSet())) {
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
        if (maybeOverlappingLookArounds) {
            // If there are look-ahead assertions in the prefix, we cannot decide whether a
            // given NFA state belongs to the prefix just by its AST nodes alone, since a
            // look-ahead may be merged with nodes of the postfix as well. Therefore, we instead
            // mark NFA states belonging to the prefix by traversing the NFA from the initial
            // state to the literal's first state.
            ArrayList<NFAState> bfsCur = new ArrayList<>(prefixNFAStates);
            ArrayList<NFAState> bfsNext = new ArrayList<>();
            NFAState anchoredInitialState = nfa.getMaxOffsetAnchoredInitialState();
            if (anchoredInitialState != null) {
                bfsCur.add(anchoredInitialState);
            }
            while (!bfsCur.isEmpty()) {
                for (NFAState s : bfsCur) {
                    for (NFAStateTransition t : s.getSuccessors()) {
                        NFAState target = t.getTarget();
                        if (target != literalFirstState && prefixNFAStates.add(target)) {
                            bfsNext.add(target);
                        }
                    }
                }
                ArrayList<NFAState> tmp = bfsCur;
                bfsCur = bfsNext;
                bfsNext = tmp;
                bfsNext.clear();
            }
        }
        if (literalFirstState == null || literalLastState == null) {
            // may happen when transitions to the literal have been pruned during NFA generation
            return;
        }

        // find the literal's beginning and end in the DFA
        DFAStateNodeBuilder literalFirstDFAState = null;
        DFAStateNodeBuilder literalLastDFAState = null;
        DFAStateNodeBuilder unanchoredInitialState = getUnanchoredInitialState();
        TBitSet visited = new TBitSet(nextID);
        visited.set(unanchoredInitialState.getId());
        bfsTraversalCur.clear();
        bfsTraversalCur.add(unanchoredInitialState.getSuccessors());
        outer: while (!bfsTraversalCur.isEmpty()) {
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
                        break outer;
                    }
                    bfsExpand(target);
                }
            }
            bfsSwapLists();
        }

        if (literalFirstDFAState == null || literalLastDFAState == null) {
            // may happen when transitions to the literal have been pruned during DFA generation
            return;
        }

        if (literalStart > 0 || lookBehindsAfterLiteral) {
            /*
             * Check if it is possible to match the literal beginning from the prefix, and bail out
             * if that is the case. Otherwise, the resulting DFA would produce wrong results on e.g.
             * /a?aa/. In this exemplary expression, the DFA would match the literal "aa" and
             * immediately jump to the successor of the literal's last state, which is the final
             * state. It would never match "aaa". If the prefix has unbounded quantifiers, the
             * runtime complexity of the overall match would also become quadratic in this case.
             */
            nfa.setInitialLoopBack(false);
            if (innerLiteralMatchesPrefix(prefixNFAStates)) {
                nfa.setInitialLoopBack(true);
                return;
            }
            nfa.setInitialLoopBack(true);

            /*
             * Redirect all transitions to prefix states to the initial state. The prefix states are
             * all covered/duplicated by the backward matcher, so instead of going to the original
             * prefix states (where "prefix" in this case means the part of the regex preceding the
             * literal), we can go to the DFAFindInnerLiteralStateNode that is inserted as the
             * initial state, and benefit from the vectorized search for the literal. An example for
             * why this is important: Given the regex /.?abc[0-9]/, the "inner literal" is abc, and
             * the prefix is .?. The dot (.) matches everything except newlines. When performing a
             * search, the DFA would first search for abc, then check that the preceding character
             * is no newline, and then check if the succeeding character is a digit ([0-9]). If the
             * succeeding character is not a digit, the DFA would check if it is a newline, and only
             * if it is, it would go back to the initial state and start the optimized search for
             * abc again. In all other cases, it would go to the state representing the dot, which
             * would loop to itself as long as no newline is encountered, and the optimized search
             * would hardly ever trigger after the first occurrence of the literal.
             */
            CodePointSetAccumulator acc = compilationBuffer.getCodePointSetAccumulator1();
            for (DFAStateNodeBuilder s : stateMap.values()) {
                acc.clear();
                if (!prefixNFAStates.containsAll(s.getNfaTransitionSet().getTargetStateSet())) {
                    DFAStateTransitionBuilder mergedTransition = null;
                    ObjectArrayBuffer<DFAStateTransitionBuilder> newTransitions = null;
                    for (int i = 0; i < s.getSuccessors().length; i++) {
                        DFAStateTransitionBuilder t = s.getSuccessors()[i];
                        if (t.hasNoConstraints() && prefixNFAStates.containsAll(t.getTarget().getNfaTransitionSet().getTargetStateSet())) {
                            if (mergedTransition == null) {
                                if (trackPredecessors()) {
                                    t.getTarget().decPredecessors();
                                    unanchoredInitialState.incPredecessors();
                                }
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
            assert innerLiteralPrefixMatcher == null;
            int minResultLength = rootSeq.getTerms().get(literalStart).getMinPath() - 1;
            innerLiteralPrefixMatcher = compilationRequest.createDFAExecutor(nfa, new TRegexDFAExecutorProperties(false, false, false, doSimpleCG, false, minResultLength), "innerLiteralPrefix");
            innerLiteralPrefixMatcher.getProperties().setSimpleCGMustCopy(false);
            doSimpleCG = doSimpleCG && innerLiteralPrefixMatcher.isSimpleCG();
            nfa.setInitialLoopBack(true);
            nfa.getReverseAnchoredEntry().setSource(reverseAnchoredInitialState);
            nfa.getReverseUnAnchoredEntry().setSource(reverseUnAnchoredInitialState);
        }
        executorProps.setCanFindStart(innerLiteralCanFindMatchStart(unanchoredInitialState, literalLastDFAState));
        registerStateReplacement(unanchoredInitialState.getId(), new DFAFindInnerLiteralStateNode((short) unanchoredInitialState.getId(),
                        new short[]{(short) literalLastDFAState.getId()}, nfa.getAst().extractInnerLiteral()));
    }

    /**
     * If the expression contains an inner literal, the forward searching DFA can eagerly determine
     * the match start if no DFA states after the inner literal contain transitions targeting the
     * beginning of the expression.
     */
    private boolean innerLiteralCanFindMatchStart(DFAStateNodeBuilder unanchoredInitialState, DFAStateNodeBuilder literalLastDFAState) {
        if (!literalLastDFAState.getNfaTransitionSet().getTargetStateSet().isDisjoint(nfaFirstStates)) {
            return false;
        }
        TBitSet visited = new TBitSet(nextID);
        visited.set(literalLastDFAState.getId());
        visited.set(unanchoredInitialState.getId());
        bfsTraversalCur.clear();
        bfsTraversalCur.add(literalLastDFAState.getSuccessors());
        while (!bfsTraversalCur.isEmpty()) {
            bfsTraversalNext.clear();
            for (DFAStateTransitionBuilder[] cur : bfsTraversalCur) {
                for (DFAStateTransitionBuilder t : cur) {
                    DFAStateNodeBuilder target = t.getTarget();
                    if (visited.get(target.getId())) {
                        continue;
                    }
                    visited.set(target.getId());
                    if (!target.getNfaTransitionSet().getTargetStateSet().isDisjoint(nfaFirstStates)) {
                        bfsTraversalNext.clear();
                        return false;
                    }
                    bfsExpand(target);
                }
            }
            bfsSwapLists();
        }
        return true;
    }

    private boolean innerLiteralMatchesPrefix(StateSet<NFA, NFAState> prefixNFAStates) {
        if (innerLiteralTryMatchPrefix(prefixNFAStates, getMaxOffsetAnchoredInitialState().getNfaTransitionSet().getTargetStateSet().copy())) {
            return true;
        }
        for (NFAState s : prefixNFAStates) {
            StateSet<NFA, NFAState> start = StateSet.create(nfa);
            start.add(s);
            if (innerLiteralTryMatchPrefix(prefixNFAStates, start)) {
                return true;
            }
        }
        return false;
    }

    private boolean innerLiteralTryMatchPrefix(StateSet<NFA, NFAState> prefixNFAStates, StateSet<NFA, NFAState> start) {
        int literalEnd = nfa.getAst().getProperties().getInnerLiteralEnd();
        int literalStart = nfa.getAst().getProperties().getInnerLiteralStart();
        Sequence rootSeq = nfa.getAst().getRoot().getFirstAlternative();
        StateSet<NFA, NFAState> curState = start.copy();
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

    private ObjectArrayBuffer<DFAAbstractNode> createDFAExecutorStates() {
        if (isGenericCG()) {
            initialCGTransitions = new DFACaptureGroupTransitionBuilder[entryStates.length];
            for (DFAStateNodeBuilder s : stateMap.values()) {
                if (s.isInitialState()) {
                    DFACaptureGroupTransitionBuilder initialCGTransition = createInitialCGTransition(s);
                    s.addPredecessorUnchecked(initialCGTransition);
                    for (int i = 0; i < entryStates.length; i++) {
                        if (entryStates[i] == s) {
                            assert initialCGTransitions[i] == null;
                            initialCGTransitions[i] = initialCGTransition;
                        }
                    }
                }
            }
        }
        if (trackPredecessors()) {
            for (DFAStateNodeBuilder s : stateMap.values()) {
                for (DFAStateTransitionBuilder t : s.getSuccessors()) {
                    t.getTarget().addPredecessor(t);
                }
            }
        }
        ObjectArrayBuffer<DFAAbstractNode> nodes = new ObjectArrayBuffer<>(stateMap.size() + 1);
        nodes.setLength(stateMap.size() + 1);
        optimizeBoundedQuantifierDataMapping();
        createBQTransitions(nodes);
        boolean utf16MustDecode = false;
        ObjectArrayBuffer<ObjectArrayBuffer<long[]>> constraints = new ObjectArrayBuffer<>();
        for (DFAStateNodeBuilder s : stateMap.values()) {
            matchersBuilder.reset(s.getSuccessors().length);
            constraints.clear();

            assert s.getId() <= Short.MAX_VALUE;
            short id = (short) s.getId();
            DFAAbstractStateNode replacement = getReplacement(id);
            if (replacement != null) {
                nodes.set(id, replacement);
                continue;
            }
            int nRanges = 0;
            int estimatedTransitionsCost = 0;
            boolean coversCharSpace = s.coversFullCharSpace(compilationBuffer);
            boolean anyConstraints = false;
            boolean anyOps = false;
            for (int i = 0; i < s.getSuccessors().length; i++) {
                DFAStateTransitionBuilder t = s.getSuccessors()[i];
                anyConstraints |= t.hasConstraints();
                anyOps |= t.hasOperations();
                utf16MustDecode |= Constants.ASTRAL_SYMBOLS_AND_LONE_SURROGATES.intersects(t.getCodePointSet());
                if (trackBoundedQuantifiers()) {
                    /*
                     * When tracking bounded quantifiers, states may have multiple transitions that
                     * share the same codepoint set and differ only in their quantifier constraints.
                     * We translate those to two-step transitions that first check their common
                     * character matcher, and then check the individual transitions' constraints.
                     */
                    if (i == 0 || !t.getCodePointSet().equals(s.getSuccessors()[i - 1].getCodePointSet())) {
                        constraints.ensureCapacity(constraints.length() + 1);
                        constraints.setLength(constraints.length() + 1);
                        if (constraints.peek() == null) {
                            constraints.set(constraints.length() - 1, new ObjectArrayBuffer<>());
                        } else {
                            constraints.peek().clear();
                        }
                    }
                    constraints.peek().add(t.getConstraints());
                }
            }
            assert !trackBoundedQuantifiers() || noOverlappingCharacterMatchersAfterDeduplication(s, constraints);

            int nSuccessors = trackBoundedQuantifiers() ? constraints.length() : s.getSuccessors().length;
            short[] successors = nSuccessors > 0 || s.hasBackwardPrefixState() ? new short[nSuccessors + (s.hasBackwardPrefixState() ? 1 : 0)] : EmptyArrays.SHORT;
            short indexOfNodeId = -1;
            byte indexOfIsFast = 0;
            short loopToSelf = -1;
            int iT = 0;
            for (int i = 0; i < nSuccessors; i++) {
                DFAStateTransitionBuilder t = s.getSuccessors()[iT];
                CodePointSet cps = t.getCodePointSet();
                // TODO: It might be interesting to still do IndexOf even when the looping
                // transition has some operations,
                // in case the operations are all idempotent (like set1).
                // This seems to happen very rarely.
                if (!anyConstraints && t.getTarget().getId() == id && t.getOperations().length == 0) {
                    loopToSelf = (short) i;
                    CodePointSet loopMB = t.getCodePointSet();
                    CodePointSet inverse = loopMB.createInverse(getEncoding());
                    if (coversCharSpace && !loopMB.matchesEverything(getEncoding()) && !(getEncoding() == Encodings.UTF_16 && inverse.intersects(Constants.SURROGATES))) {
                        TruffleString.CodePointSet indexOfParam = TruffleString.CodePointSet.fromRanges(inverse.getRanges(), getEncoding().getTStringEncoding());
                        indexOfIsFast = checkIndexOfIsFast(indexOfParam);
                        if (indexOfIsFast != 0) {
                            indexOfParams.add(indexOfParam);
                            assert indexOfParams.size() <= Short.MAX_VALUE;
                            indexOfNodeId = (short) (indexOfParams.size() - 1);
                        }
                    }
                }
                if (i == nSuccessors - 1 && (coversCharSpace || (pruneUnambiguousPaths && !s.isFinalStateSuccessor()))) {
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
                    successors[i] = createAndDedupSimpleCGTransition(nodes, (short) t.getTarget().getId(), t.getTransitionSet().getTransition(0));
                } else if (trackBoundedQuantifiers()) {
                    assert !constraints.get(i).isEmpty();
                    if (constraints.get(i).length() == 1 && constraints.get(i).peek().length == 0) {
                        successors[i] = t.getBqSuccessor();
                    } else {
                        long[][] tConstraints = constraints.get(i).toArray(long[][]::new);
                        short[] tSuccessors = new short[tConstraints.length];
                        for (int j = 0; j < tConstraints.length; j++) {
                            tSuccessors[j] = s.getSuccessors()[iT + j].getBqSuccessor();
                        }
                        successors[i] = insertNode(nodes, new DFABQTrackingTransitionConstraintsNode(nextID++, tSuccessors, tConstraints)).getId();
                    }
                } else {
                    successors[i] = (short) t.getTarget().getId();
                }
                assert successors[i] >= 0 && successors[i] < nodes.length();
                iT += trackBoundedQuantifiers() ? constraints.get(i).length() : 1;
            }

            final Matchers matchers;
            // Very conservative heuristic for whether we should use AllTransitionsInOneTreeMatcher.
            // TODO: Potential benefits of this should be further explored.
            boolean useTreeTransitionMatcher = nRanges > 1 && MathUtil.log2ceil(nRanges + 2) * 8 < estimatedTransitionsCost && !anyConstraints;
            if (useTreeTransitionMatcher) {
                matchers = createAllTransitionsInOneTreeMatcher(s, coversCharSpace);
            } else {
                matchers = getEncoding().toMatchers(matchersBuilder);
            }

            if (s.hasBackwardPrefixState()) {
                successors[successors.length - 1] = s.getBackwardPrefixState();
            }
            byte flags = DFAStateNode.buildFlags(s.isUnAnchoredFinalState(), s.isAnchoredFinalState(), s.hasBackwardPrefixState(), utf16MustDecode, s.isGuardedUnAnchoredFinalState(),
                            s.isGuardedAnchoredFinalState());
            DFAStateNode stateNode;
            if (isGenericCG()) {
                stateNode = createCGTrackingDFAState(nodes, s, id, matchers, successors, indexOfNodeId, indexOfIsFast, loopToSelf, flags);
            } else if (nfa.isTraceFinderNFA()) {
                stateNode = new TraceFinderDFAStateNode(id, flags, loopToSelf, indexOfNodeId, indexOfIsFast, successors, matchers,
                                s.getPreCalculatedUnAnchoredResult(),
                                s.getPreCalculatedAnchoredResult());
            } else if (anyConstraints || anyOps || s.isGuardedFinalState()) {
                assert isBooleanMatch();
                stateNode = new DFABQTrackingStateNode(id, flags, loopToSelf, indexOfNodeId, indexOfIsFast, successors, matchers,
                                s.getUnAnchoredFinalConstraints(),
                                s.getAnchoredFinalConstraints());
            } else if (doSimpleCG) {
                stateNode = new DFASimpleCGTrackingStateNode(id, flags, loopToSelf, indexOfNodeId, indexOfIsFast, successors, matchers,
                                createSimpleCGTransition((short) -1, (short) -1, s.getUnAnchoredFinalStateTransition()),
                                createAndDedupSimpleCGTransition(nodes, (short) -1, s.getAnchoredFinalStateTransition()));
            } else {
                stateNode = new DFAStateNode(id, flags, loopToSelf, indexOfNodeId, indexOfIsFast, successors, matchers, (short) -1);
            }
            nodes.set(id, stateNode);
        }
        if (isGenericCG()) {
            for (DFAStateNodeBuilder s : stateMap.values()) {
                CGTrackingDFAStateNode cgTrackingStateNode = (CGTrackingDFAStateNode) nodes.get(s.getId());
                DFACaptureGroupLazyTransition[] lazyTransitions = s.getLazyTransitions();
                short[] successors = cgTrackingStateNode.getSuccessors();
                for (int i = 0; i < successors.length; i++) {
                    successors[i] = dedupDFATransition(nodes, successors[i],
                                    CGTrackingTransitionNode.create(nextID, successors[i], lazyTransitions[i], getLazyTransitionBuilder(s.getSuccessors()[i]).getLastTransitionIndex()));
                }
            }
        }
        return nodes;
    }

    private static boolean noOverlappingCharacterMatchersAfterDeduplication(DFAStateNodeBuilder s, ObjectArrayBuffer<ObjectArrayBuffer<long[]>> constraints) {
        int iT = 0;
        for (int i = 0; i < constraints.length(); i++) {
            int jT = 0;
            for (int j = 0; j < constraints.length(); j++) {
                assert i == j || !s.getSuccessors()[iT].getCodePointSet().intersects(s.getSuccessors()[jT].getCodePointSet());
                jT += constraints.get(j).length();
            }
            iT += constraints.get(i).length();
        }
        return true;
    }

    private static byte checkIndexOfIsFast(TruffleString.CodePointSet indexOfParam) {
        byte indexOfIsFast = 0;
        for (TruffleString.CodeRange codeRange : TruffleString.CodeRange.values()) {
            if (indexOfParam.isIntrinsicCandidate(codeRange)) {
                assert codeRange.ordinal() < 8;
                indexOfIsFast |= 1 << codeRange.ordinal();
            }
        }
        return indexOfIsFast;
    }

    /**
     * In the capture group tracking DFA, we have to track one capture group result set per NFA
     * state in the current DFA state. To reduce the amount of work being done for this, we delay
     * updates to the capture group tracking data by one DFA transition. We achieve this by applying
     * the previous transition's capture group updates just after finding the current state's next
     * transition to take. The previous transition then only needs to update the capture group data
     * of NFA states contained in the next transition, not the entire current DFA state. To fully
     * compile this without indirections, we would have to generate a switch with one branch per
     * possible preceding transition, for every transition in the current state. Since this can
     * introduce quite a lot of additional code, we de-duplicate as much as possible by grouping
     * branches which would do the same update operations together.
     */
    @SuppressWarnings("unchecked")
    private CGTrackingDFAStateNode createCGTrackingDFAState(ObjectArrayBuffer<DFAAbstractNode> nodes, DFAStateNodeBuilder s,
                    short id, Matchers matchers, short[] successors, short indexOfNodeId, byte indexOfIsFast, short loopToSelf, byte flags) {
        DFACaptureGroupLazyTransition[] lazyTransitions = new DFACaptureGroupLazyTransition[s.getSuccessors().length];
        final DFACaptureGroupLazyTransition lazyPreFinalTransition;
        final DFACaptureGroupLazyTransition lazyPreAnchoredFinalTransition;
        DFACaptureGroupLazyTransitionBuilder firstPredecessor = getLazyTransitionBuilder(s.getPredecessors()[0]);
        int nMaps = s.getSuccessors().length;
        int iTransitionToFinalState = firstPredecessor.getTransitionToFinalState() == null ? -1 : nMaps++;
        int iTransitionToAnchoredFinalState = firstPredecessor.getTransitionToAnchoredFinalState() == null ? -1 : nMaps++;
        EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>>[] maps = new EconomicMap[nMaps];
        int maxDedupSize = 0;
        // for every successor, group all preceding transitions by DFACaptureGroupPartialTransition
        for (int i = 0; i < maps.length; i++) {
            EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>> dedup = EconomicMap.create();
            maps[i] = dedup;
            for (int j = 0; j < s.getPredecessors().length; j++) {
                DFACaptureGroupLazyTransitionBuilder predecessor = getLazyTransitionBuilder(s.getPredecessors()[j]);
                assert iTransitionToFinalState >= 0 || predecessor.getTransitionToFinalState() == null;
                assert iTransitionToAnchoredFinalState >= 0 || predecessor.getTransitionToAnchoredFinalState() == null;
                DFACaptureGroupPartialTransition partialTransition;
                if (i < predecessor.getPartialTransitions().length) {
                    partialTransition = predecessor.getPartialTransitions()[i];
                } else if (i == iTransitionToAnchoredFinalState) {
                    partialTransition = predecessor.getTransitionToAnchoredFinalState();
                } else {
                    assert i == iTransitionToFinalState;
                    partialTransition = predecessor.getTransitionToFinalState();
                }
                assert partialTransition != null;
                if (dedup.containsKey(partialTransition)) {
                    dedup.get(partialTransition).add(j);
                } else {
                    ArrayList<Integer> list = new ArrayList<>();
                    list.add(j);
                    dedup.put(partialTransition, list);
                }
            }
            maxDedupSize = Math.max(maxDedupSize, dedup.size());
        }
        if (nMaps == 0 || maxDedupSize == 1) {
            /*
             * This state has zero or one fixed DFACaptureGroupPartialTransition per successor. We
             * don't need information about the preceding transitions, so don't record them.
             */
            for (DFAStateTransitionBuilder p : s.getPredecessors()) {
                getLazyTransitionBuilder(p).setLastTransitionIndex(DFACaptureGroupLazyTransitionBuilder.DO_NOT_SET_LAST_TRANSITION);
            }
            lazyPreAnchoredFinalTransition = createSingleLazyTransition(maps, iTransitionToAnchoredFinalState);
            lazyPreFinalTransition = createSingleLazyTransition(maps, iTransitionToFinalState);
            for (int i = 0; i < lazyTransitions.length; i++) {
                lazyTransitions[i] = createSingleLazyTransition(maps, i);
            }
        } else if (allSameValues(maps)) {
            /*
             * All successors have the exact same grouping of preceding transition, which we can map
             * directly into lastTransitionIndex. The resulting code layout for all successors is an
             * if-else cascade with one branch per group.
             */
            int iPartialTransition = 0;
            MapCursor<DFACaptureGroupPartialTransition, ArrayList<Integer>> cursor = maps[0].getEntries();
            while (cursor.advance()) {
                for (int i : cursor.getValue()) {
                    getLazyTransitionBuilder(s.getPredecessors()[i]).setLastTransitionIndex(iPartialTransition);
                }
                iPartialTransition++;
            }
            for (int i = 0; i < lazyTransitions.length; i++) {
                lazyTransitions[i] = createBranchesDirect(s, maps, i);
            }
            lazyPreAnchoredFinalTransition = createBranchesDirect(s, maps, iTransitionToAnchoredFinalState);
            lazyPreFinalTransition = createBranchesDirect(s, maps, iTransitionToFinalState);
        } else {
            // There are different groupings, we will have to map them at runtime.
            for (int i = 0; i < s.getPredecessors().length; i++) {
                getLazyTransitionBuilder(s.getPredecessors()[i]).setLastTransitionIndex(i);
            }
            for (int i = 0; i < lazyTransitions.length; i++) {
                lazyTransitions[i] = createWithLookup(s, maps, i);
            }
            lazyPreAnchoredFinalTransition = createWithLookup(s, maps, iTransitionToAnchoredFinalState);
            lazyPreFinalTransition = createWithLookup(s, maps, iTransitionToFinalState);
        }
        s.setLazyTransitions(lazyTransitions);
        DFACaptureGroupPartialTransition cgLoopToSelf = null;
        boolean cgLoopToSelfHasDependency = false;
        if (loopToSelf >= 0) {
            cgLoopToSelf = getLazyTransitionBuilder(s.getSuccessors()[loopToSelf]).getPartialTransitions()[loopToSelf];
            cgLoopToSelfHasDependency = calcCGLoopToSelfDependency(cgLoopToSelf);
        }
        short preAnchoredFinalTransition;
        if (s.getAnchoredFinalStateTransition() == null) {
            preAnchoredFinalTransition = -1;
        } else {
            short anchoredFinalTransition = dedupDFATransition(nodes, (short) -1, new CGTrackingAnchoredFinalTransitionNode(nextID, createCGFinalTransition(s.getAnchoredFinalStateTransition())));
            preAnchoredFinalTransition = dedupDFATransition(nodes, anchoredFinalTransition, CGTrackingPreFinalTransitionNode.create(nextID, anchoredFinalTransition, lazyPreAnchoredFinalTransition));
        }
        return new CGTrackingDFAStateNode(id, flags, loopToSelf, indexOfNodeId, indexOfIsFast, successors, matchers,
                        preAnchoredFinalTransition, lazyPreFinalTransition, createCGFinalTransition(s.getUnAnchoredFinalStateTransition()), cgLoopToSelf, cgLoopToSelfHasDependency);
    }

    /**
     * Check if the capture group updates in the given looping transition have a dependency on their
     * previous application in the loop. This happens when the transition contains a copy operation
     * where the source array isn't updated at the same indices as the target array, for example:
     *
     * <pre>
     * {@code copy array 0 -> 1}
     * {@code update array 0, indices [0, 1]}
     * {@code update array 1, indices [1]}
     * </pre>
     * <p>
     * In this case, array 1 at index 0 will always contain the value of array 0 of the previous
     * loop iteration. We have to take this into account in {@link CGTrackingDFAStateNode}'s
     * {@code afterIndexOf}-method.
     */
    private boolean calcCGLoopToSelfDependency(DFACaptureGroupPartialTransition cgLoopToSelf) {
        byte[] arrayCopies = cgLoopToSelf.getArrayCopies();
        if (cgLoopToSelf.doesReorderResults() || arrayCopies.length == 0) {
            return false;
        }
        int maxArrayIndex = 0;
        for (byte i : arrayCopies) {
            maxArrayIndex = Math.max(maxArrayIndex, Byte.toUnsignedInt(i));
        }
        TBitSet[] updates = new TBitSet[maxArrayIndex + 1];
        for (IndexOperation op : cgLoopToSelf.getIndexUpdates()) {
            if (op.getTargetArray() > maxArrayIndex) {
                continue;
            }
            TBitSet bs = new TBitSet(nfa.getAst().getNumberOfCaptureGroups() * 2);
            for (int i = 0; i < op.getNumberOfIndices(); i++) {
                bs.set(op.getIndex(i));
            }
            assert updates[op.getTargetArray()] == null;
            updates[op.getTargetArray()] = bs;
        }
        TBitSet cmp = new TBitSet(nfa.getAst().getNumberOfCaptureGroups() * 2);
        for (int i = 0; i < arrayCopies.length; i += 2) {
            int src = Byte.toUnsignedInt(arrayCopies[i]);
            int dst = Byte.toUnsignedInt(arrayCopies[i + 1]);
            if (updates[src] == null) {
                continue;
            }
            if (updates[dst] == null) {
                return true;
            }
            cmp.clear();
            cmp.union(updates[src]);
            cmp.subtract(updates[dst]);
            if (!cmp.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private DFACaptureGroupLazyTransition createWithLookup(DFAStateNodeBuilder s,
                    EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>>[] maps, int i) {
        if (i < 0) {
            return null;
        }
        EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>> map = maps[i];
        if (map.size() == 1) {
            return createSingleLazyTransition(maps, i);
        }
        DFACaptureGroupPartialTransition[] transitions = new DFACaptureGroupPartialTransition[map.size()];
        if (lookupTableRequired(map)) {
            /*
             * Generate a lookup table to map lastTransitionIndex to the current successor's
             * grouping, followed by a regular if-else cascade.
             */
            if (map.size() > 0xff) {
                // bail out if we can't use byte[] as the lookup table
                throw new UnsupportedRegexException("too many branches in capture group tracking DFA", getNfa().getAst().getSource());
            }
            byte[] lookupTable = new byte[s.getPredecessors().length];
            MapCursor<DFACaptureGroupPartialTransition, ArrayList<Integer>> cursor = map.getEntries();
            int iCursor = 0;
            while (cursor.advance()) {
                transitions[iCursor] = cursor.getKey();
                for (int t : cursor.getValue()) {
                    lookupTable[t] = (byte) iCursor;
                }
                iCursor++;
            }
            return DFACaptureGroupLazyTransition.BranchesWithLookupTable.create(transitions, lookupTable);
        } else {
            /*
             * There is only one group with more than one element, so we can avoid the lookup table
             * by generating an if-else cascade where the last else-branch is the group with more
             * than one element.
             */
            short[] possibleValues = new short[map.size() - 1];
            MapCursor<DFACaptureGroupPartialTransition, ArrayList<Integer>> cursor = map.getEntries();
            int iCursor = 0;
            DFACaptureGroupPartialTransition last = null;
            while (cursor.advance()) {
                if (cursor.getValue().size() == 1 && iCursor < transitions.length - 1) {
                    transitions[iCursor] = cursor.getKey();
                    possibleValues[iCursor] = cursor.getValue().get(0).shortValue();
                    iCursor++;
                } else {
                    assert last == null;
                    last = cursor.getKey();
                }
            }
            if (last != null) {
                transitions[transitions.length - 1] = last;
            }
            return DFACaptureGroupLazyTransition.BranchesIndirect.create(transitions, possibleValues);
        }
    }

    /**
     * Checks if there are more than one lists with {@code size > 1} in {@code map}.
     */
    private static boolean lookupTableRequired(EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>> map) {
        boolean foundSizeGreaterOne = false;
        for (ArrayList<Integer> l : map.getValues()) {
            if (l.size() > 1) {
                if (foundSizeGreaterOne) {
                    return true;
                }
                foundSizeGreaterOne = true;
            }
        }
        return false;
    }

    private DFACaptureGroupLazyTransition.BranchesDirect createBranchesDirect(DFAStateNodeBuilder s, EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>>[] maps, int i) {
        if (i < 0) {
            return null;
        }
        DFACaptureGroupPartialTransition[] transitions = new DFACaptureGroupPartialTransition[maps[0].size()];
        MapCursor<DFACaptureGroupPartialTransition, ArrayList<Integer>> cursor = maps[i].getEntries();
        while (cursor.advance()) {
            int iT = getLazyTransitionBuilder(s.getPredecessors()[cursor.getValue().get(0)]).getLastTransitionIndex();
            assert iT >= 0;
            for (int j : cursor.getValue()) {
                assert getLazyTransitionBuilder(s.getPredecessors()[j]).getLastTransitionIndex() == iT;
            }
            assert transitions[iT] == null;
            transitions[iT] = cursor.getKey();
        }
        return DFACaptureGroupLazyTransition.BranchesDirect.create(transitions);
    }

    private static DFACaptureGroupLazyTransition.Single createSingleLazyTransition(EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>>[] maps, int i) {
        if (i < 0) {
            return null;
        }
        return DFACaptureGroupLazyTransition.Single.create(maps[i].getKeys().iterator().next());
    }

    private DFACaptureGroupTransitionBuilder createInitialCGTransition(DFAStateNodeBuilder target) {
        assert target.isInitialState();
        DFACaptureGroupTransitionBuilder ret = new DFACaptureGroupTransitionBuilder(null, null, null, null, null);
        DFACaptureGroupPartialTransition[] partialTransitions = new DFACaptureGroupPartialTransition[target.getSuccessors().length];
        Arrays.fill(partialTransitions, DFACaptureGroupPartialTransition.getEmptyInstance());
        short id = (short) transitionIDCounter.inc();
        DFACaptureGroupLazyTransitionBuilder emptyInitialTransition = new DFACaptureGroupLazyTransitionBuilder(
                        id,
                        partialTransitions,
                        target.isUnAnchoredFinalState() ? DFACaptureGroupPartialTransition.getEmptyInstance() : null,
                        target.isAnchoredFinalState() ? DFACaptureGroupPartialTransition.getEmptyInstance() : null);
        ret.setId(id);
        ret.setLazyTransition(emptyInitialTransition);
        return ret;
    }

    private DFACaptureGroupLazyTransitionBuilder getLazyTransitionBuilder(DFAStateTransitionBuilder precedingTransitions) {
        return ((DFACaptureGroupTransitionBuilder) precedingTransitions).toLazyTransitionBuilder(compilationBuffer);
    }

    private static boolean allSameValues(EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>>[] maps) {
        for (int i = 1; i < maps.length; i++) {
            if (maps[0].size() != maps[i].size()) {
                return false;
            }
        }
        for (ArrayList<Integer> value : maps[0].getValues()) {
            for (int i = 1; i < maps.length; i++) {
                if (!allSameValuesInner(maps[i], value)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allSameValuesInner(EconomicMap<DFACaptureGroupPartialTransition, ArrayList<Integer>> map, ArrayList<Integer> value) {
        for (ArrayList<Integer> v : map.getValues()) {
            if (value.equals(v)) {
                return true;
            }
        }
        return false;
    }

    private short createAndDedupSimpleCGTransition(ObjectArrayBuffer<DFAAbstractNode> nodes, short successor, NFAStateTransition nfaTransition) {
        return dedupDFATransition(nodes, successor, createSimpleCGTransition(nextID, successor, nfaTransition));
    }

    private DFASimpleCGTransition createSimpleCGTransition(short id, short successor, NFAStateTransition nfaTransition) {
        boolean fullClear = isForward() && nfaTransition != null && nfa.getInitialLoopBackTransition() != null && nfaTransition.getSource() == nfa.getInitialLoopBackTransition().getSource();
        boolean isFinalTransition = nfaTransition != null && nfaTransition.getTarget(isForward()).isFinalState();
        return DFASimpleCGTransition.create(id, successor, nfaTransition, fullClear, isFinalTransition);
    }

    private short dedupDFATransition(ObjectArrayBuffer<DFAAbstractNode> nodes, short successor, DFAAbstractTransitionNode t) {
        if (t == null) {
            return successor;
        }
        DFAAbstractTransitionNode existing = dfaTransitionsDedupMap.get(t);
        if (existing == null) {
            nextID++;
            insertNode(nodes, t);
            dfaTransitionsDedupMap.put(t, t);
            return t.getId();
        } else {
            return existing.getId();
        }
    }

    private static <T extends DFAAbstractNode> T insertNode(ObjectArrayBuffer<DFAAbstractNode> nodes, T t) {
        int length = Math.max(nodes.length(), t.getId() + 1);
        nodes.ensureCapacity(length);
        nodes.setLength(length);
        assert nodes.get(t.getId()) == null;
        nodes.set(t.getId(), t);
        return t;
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

    private DFACaptureGroupPartialTransition createCGFinalTransition(NFAStateTransition transition) {
        if (transition == null) {
            return null;
        }
        GroupBoundaries groupBoundaries = transition.getGroupBoundaries();
        IndexOperation[] indexUpdates = DFACaptureGroupPartialTransition.EMPTY_INDEX_OPS;
        IndexOperation[] indexClears = DFACaptureGroupPartialTransition.EMPTY_INDEX_OPS;
        LastGroupUpdate[] lastGroupUpdates = DFACaptureGroupPartialTransition.EMPTY_LAST_GROUP_UPDATES;
        if (groupBoundaries.hasIndexUpdates()) {
            indexUpdates = new IndexOperation[]{new IndexOperation(0, groupBoundaries.updatesToByteArray())};
        }
        if (groupBoundaries.hasIndexClears()) {
            indexClears = new IndexOperation[]{new IndexOperation(0, groupBoundaries.clearsToByteArray())};
        }
        if (groupBoundaries.hasLastGroup()) {
            lastGroupUpdates = new LastGroupUpdate[]{new LastGroupUpdate(0, groupBoundaries.getLastGroup())};
        }
        DFACaptureGroupPartialTransition partialTransitionNode = DFACaptureGroupPartialTransition.create(this,
                        DFACaptureGroupPartialTransition.EMPTY,
                        DFACaptureGroupPartialTransition.EMPTY,
                        indexUpdates,
                        indexClears,
                        lastGroupUpdates,
                        (byte) DFACaptureGroupPartialTransition.FINAL_STATE_RESULT_INDEX);
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

    private boolean debugMode() {
        return getOptions().isDumpAutomata() || getOptions().isStepExecution();
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
