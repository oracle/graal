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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.util.EmptyArrays;
import com.oracle.truffle.regex.util.TBitSet;

/**
 * This class provides an algorithm for converting a list of NFA transitions into a set of DFA
 * transitions.
 *
 * @param <TB> represents a DFA transition fragment. This type is used for both intermediate and
 *            final results.
 * @see TransitionBuilder
 * @see TransitionSet
 */
public abstract class StateTransitionCanonicalizer<SI extends StateIndex<? super S>, S extends AbstractState<S, T>, T extends AbstractTransition<S, T>, TB extends TransitionBuilder<SI, S, T>> {

    private final ObjectArrayBuffer<T> argTransitions = new ObjectArrayBuffer<>();
    private final ObjectArrayBuffer<CodePointSet> argCharSets = new ObjectArrayBuffer<>();
    private final ObjectArrayBuffer<long[]> argConstraints = new ObjectArrayBuffer<>();
    private final ObjectArrayBuffer<long[]> argOperations = new ObjectArrayBuffer<>();
    private boolean anyArgConstraints;
    private final IntArrayBuffer stack = new IntArrayBuffer();
    private final IntArrayBuffer skipStack = new IntArrayBuffer();
    private final EconomicSet<ConstraintDeduplicationKey> constraintDeduplicationSet = EconomicSet.create();

    private static final int INITIAL_CAPACITY = 8;

    private TBitSet[] intersectingArgs = new TBitSet[INITIAL_CAPACITY];
    private CodePointSet[] matcherBuilders = new CodePointSet[INITIAL_CAPACITY];
    private long[][] constraintBuilder = new long[INITIAL_CAPACITY][];
    @SuppressWarnings("unchecked") private StateSet<SI, S>[] targetStateSets = new StateSet[INITIAL_CAPACITY];
    @SuppressWarnings("unchecked") private ObjectArrayBuffer<T>[] transitionLists = new ObjectArrayBuffer[INITIAL_CAPACITY];
    private LongArrayBuffer[] operationLists = new LongArrayBuffer[INITIAL_CAPACITY];

    private final TBitSet leadsToFinalState = new TBitSet(INITIAL_CAPACITY);
    private int resultLength = 0;
    private int resultLengthStage1 = 0;

    private final SI stateIndex;
    private final boolean forward;
    private final boolean prioritySensitive;
    private final boolean booleanMatch;

    public StateTransitionCanonicalizer(SI stateIndex, boolean forward, boolean prioritySensitive, boolean booleanMatch) {
        this.stateIndex = stateIndex;
        this.forward = forward;
        this.prioritySensitive = prioritySensitive;
        this.booleanMatch = booleanMatch;
    }

    /**
     * If priority-sensitive mode, transition sets are pruned after transitions to final states.
     * Also, target state sets are considered equal iff their order is equal as well.
     */
    protected boolean isPrioritySensitive() {
        return prioritySensitive;
    }

    /**
     * If boolean match mode, transition sets are pruned after transitions to final states.
     */
    protected boolean isBooleanMatch() {
        return booleanMatch;
    }

    protected boolean shouldPruneAfterFinalState() {
        return isBooleanMatch() || isPrioritySensitive();
    }

    /**
     * Submits an argument to be processed by {@link #run(CompilationBuffer)}.
     */
    public void addArgument(T transition, CodePointSet charSet, long[] constraintsArg, long[] operations) {
        long[] constraints = constraintsArg;
        if (!TransitionConstraint.isNormalized(constraints)) {
            var temp = new LongArrayBuffer(constraints.length);
            temp.addAll(constraints);
            constraints = TransitionConstraint.normalize(temp);
        }

        assert TransitionConstraint.isNormalized(constraints);
        argTransitions.add(transition);
        argCharSets.add(charSet);
        argConstraints.add(constraints);
        argOperations.add(operations);
        anyArgConstraints |= constraints.length > 0;
    }

    private void addToStack(T transition, CodePointSet charSet, long[] constraints, long[] operations, int j) {
        argTransitions.add(transition);
        argCharSets.add(charSet);
        argConstraints.add(constraints);
        argOperations.add(operations);
        stack.add(argTransitions.length() - 1);
        skipStack.add(j);
    }

    /**
     * Runs the NFA to DFA transition conversion algorithm on the NFA transitions given by previous
     * calls to {@link #addArgument(AbstractTransition, CodePointSet, long[], long[])}. This
     * algorithm has two phases:
     * <ol>
     * <li>Merge NFA transitions according to their expected character sets. The result of this
     * phase is a list of {@link TransitionBuilder}s whose {@link CodePointSet}s have no more
     * intersections.</li>
     * <li>Merge {@link TransitionBuilder}s generated by the first phase if their <em>target
     * state</em> is equal and {@link #canMerge(TransitionBuilder, TransitionBuilder)} returns
     * {@code true}.</li>
     * </ol>
     *
     * @return a set of transition builders representing the DFA transitions generated from the
     *         given NFA transitions.
     */
    public TB[] run(CompilationBuffer compilationBuffer) {
        calcDisjointTransitions(compilationBuffer);
        TB[] result = mergeSameTargets(compilationBuffer);
        resultLength = 0;
        leadsToFinalState.clear();
        argTransitions.clear();
        argCharSets.clear();
        argConstraints.clear();
        argOperations.clear();
        anyArgConstraints = false;
        return result;
    }

    /**
     * Merges NFA transitions according to their expected character sets as returned
     * {@link TransitionBuilder#getCodePointSet()}, in the following way: <br>
     * <ul>
     * <li>The result of the algorithm is a list of transitions where no two elements have an
     * intersection in their respective set of expected characters. We initially define the result
     * as an empty list.</li>
     * <li>For every element <em>e</em> of the input list, we compare the expected character sets of
     * <em>e</em> and every element in the result list. If an intersection with an element
     * <em>r</em> of the result list is found, we distinguish the following two cases, where the
     * character set of an element <em>x</em> is denoted as <em>x.cs</em> and the transition set of
     * an element <em>x</em> is denoted as <em>x.ts</em>:
     * <ul>
     * <li>If <em>e.cs</em> contains <em>r.cs</em>, <em>e.ts</em> is merged into <em>r.ts</em> using
     * TransitionSet#addAll(TransitionSet).</li>
     * <li>Otherwise, a new transition containing <em>e.ts</em> and <em>r.ts</em> and the
     * intersection of <em>e.cs</em> and <em>r.cs</em> is added to the result list. This new
     * transition is created using TransitionBuilder#createMerged(TransitionBuilder, CodePointSet).
     * The intersection of <em>e.cs</em> and <em>r.cs</em> is removed from <em>r.cs</em>.</li>
     * </ul>
     * </li>
     * <li>Every time an intersection is found, that intersection is removed from <em>e.cs</em>. If
     * <em>e.cs</em> is not empty after <em>e</em> has been compared to every element of the result
     * list, <em>e</em> is added to the result list.</li>
     * <li>The result list at all times fulfills the property that no two elements in the list
     * intersect.</li>
     * </ul>
     * This algorithm has an important property: every entry in the input list will be merged with
     * the elements it intersects with <em>in the order of the input list</em>. This means that
     * given an input list {@code [1, 2, 3]} where all elements intersect with each other, element
     * {@code 2} will be merged with {@code 1} before {@code 3} is merged with {@code 1}. This
     * property is crucial for generating priority-sensitive DFAs, since we track priorities of NFA
     * transitions by their order!
     * <p>
     * Example: <br>
     *
     * <pre>
     * input: [
     *   {transitionSet {1}, matcherBuilder [ab]},
     *   {transitionSet {2}, matcherBuilder [bc]}
     * ]
     * output: [
     *   {transitionSet {1},    matcherBuilder [a]},
     *   {transitionSet {1, 2}, matcherBuilder [b]},
     *   {transitionSet {2},    matcherBuilder [c]}
     * ]
     * </pre>
     */
    private void calcDisjointTransitions(CompilationBuffer compilationBuffer) {

        /*
         * Stage 1: merge transitions based on their codepoint sets, ignoring any other constraints.
         */

        for (int i = 0; i < argTransitions.length(); i++) {
            T argTransition = argTransitions.get(i);
            CodePointSet argCharSet = argCharSets.get(i);
            long[] argOps = argOperations.get(i);
            int currentResultLength = resultLength;
            for (int j = 0; j < currentResultLength; j++) {
                CodePointSet.IntersectAndSubtractResult<CodePointSet> result = matcherBuilders[j].intersectAndSubtract(argCharSet, compilationBuffer);
                CodePointSet rSubtractedMatcher = result.subtractedA;
                CodePointSet eSubtractedMatcher = result.subtractedB;
                CodePointSet intersection = result.intersection;
                if (intersection.matchesSomething()) {
                    if (rSubtractedMatcher.matchesNothing()) {
                        addTransitionToStage1(i, j, argTransition, argOps);
                    } else {
                        matcherBuilders[j] = rSubtractedMatcher;
                        duplicateSlot(j, intersection, EmptyArrays.LONG);
                        intersectingArgs[resultLength].union(intersectingArgs[j]);
                        addTransitionToStage1(i, resultLength, argTransition, argOps);
                        resultLength++;
                    }
                    argCharSet = eSubtractedMatcher;
                    if (eSubtractedMatcher.matchesNothing()) {
                        break;
                    }
                }
            }
            if (argCharSet.matchesSomething()) {
                createSlot();
                targetStateSets[resultLength] = StateSet.create(stateIndex);
                matcherBuilders[resultLength] = argCharSet;
                addTransitionToStage1(i, resultLength, argTransition, argOps);
                resultLength++;
            }
        }

        if (!anyArgConstraints) {
            resultLengthStage1 = 0;
            return;
        }

        resultLengthStage1 = resultLength;

        /*
         * Stage 2: split transitions based on constraints.
         *
         * Stage 1 yields a list of all possible combinations of transitions based on their
         * codepoint sets. In stage 2, we calculate their possible subsets based on their respective
         * constraints.
         */

        for (int iStage1 = 0; iStage1 < resultLengthStage1; iStage1++) {
            /*
             * For every iStage1, the codepoint set (matcher) is fixed, and intersectingArgs
             * contains all transitions that whose codepoint set intersects with the current
             * matcher. With the non-intersecting transitions already filtered out, we can
             * completely ignore the remaining transitions' codepoint sets, and only look at their
             * constraints.
             */
            CodePointSet matcher = matcherBuilders[iStage1];
            for (int i : intersectingArgs[iStage1]) {
                addToStack(argTransitions.get(i), matcher, argConstraints.get(i), argOperations.get(i), resultLength);
            }
            constraintDeduplicationSet.clear();
            outer: while (!stack.isEmpty()) {
                int i = stack.pop();
                T argTransition = argTransitions.get(i);
                long[] argConstraint = argConstraints.get(i);
                long[] argOperation = argOperations.get(i);
                assert argCharSets.get(i).equals(matcher);

                int currentResultLength = resultLength;
                int initial = skipStack.pop();
                for (int j = initial; j < currentResultLength; j++) {
                    TransitionConstraint.MergeResult constraintMerge = TransitionConstraint.intersectAndSubtract(constraintBuilder[j], argConstraint);
                    if (constraintMerge == null) {
                        continue;
                    }

                    // if the slot already contains an unconditional transition to the final state,
                    // we don't have to check any other constraints.
                    if (!(shouldPruneAfterFinalState() && leadsToFinalState.get(j))) {
                        if (constraintMerge.lhs().length == 0) {
                            addTransitionTo(j, argTransition, argOperation);
                        } else {
                            assert TransitionConstraint.isNormalized(constraintMerge.lhs()[0]);
                            constraintBuilder[j] = constraintMerge.lhs()[0];
                            for (int k = 1; k < constraintMerge.lhs().length; ++k) {
                                duplicateSlot(j, matcher, constraintMerge.lhs()[k]);
                                resultLength++;
                            }
                            duplicateSlot(j, matcher, constraintMerge.middle());
                            addTransitionTo(resultLength, argTransition, argOperation);
                            resultLength++;
                        }
                    }
                    for (long[] rhs : constraintMerge.rhs()) {
                        // TransitionConstraint.intersectAndSubtract may yield the same constraint
                        // formula for the same transition multiple times when intersecting a large
                        // set of transitions. We drop those duplicates here, otherwise they would
                        // increase this stage's run time exponentially.
                        if (constraintDeduplicationSet.add(new ConstraintDeduplicationKey(argTransition.getId(), rhs))) {
                            addToStack(argTransition, matcher, rhs, argOperation, j + 1);
                        }
                    }
                    continue outer;
                }
                assert TransitionConstraint.isNormalized(argConstraint);
                createSlot();
                matcherBuilders[resultLength] = matcher;
                constraintBuilder[resultLength] = argConstraint;
                targetStateSets[resultLength] = StateSet.create(stateIndex);
                addTransitionTo(resultLength, argTransition, argOperation);
                resultLength++;
            }
        }
    }

    private void duplicateSlot(int i, CodePointSet matcher, long[] constraints) {
        assert TransitionConstraint.isNormalized(constraints);
        createSlot();
        targetStateSets[resultLength] = targetStateSets[i].copy();
        transitionLists[resultLength].addAll(transitionLists[i]);
        operationLists[resultLength].addAll(operationLists[i]);
        matcherBuilders[resultLength] = matcher;
        constraintBuilder[resultLength] = constraints;
        if ((shouldPruneAfterFinalState() && leadsToFinalState.get(i))) {
            leadsToFinalState.set(resultLength);
        }
    }

    private void createSlot() {
        if (matcherBuilders.length <= resultLength) {
            transitionLists = Arrays.copyOf(transitionLists, resultLength * 2);
            operationLists = Arrays.copyOf(operationLists, resultLength * 2);
            targetStateSets = Arrays.copyOf(targetStateSets, resultLength * 2);
            matcherBuilders = Arrays.copyOf(matcherBuilders, resultLength * 2);
            constraintBuilder = Arrays.copyOf(constraintBuilder, resultLength * 2);
            intersectingArgs = Arrays.copyOf(intersectingArgs, resultLength * 2);
        }
        if (transitionLists[resultLength] == null) {
            transitionLists[resultLength] = new ObjectArrayBuffer<>();
        }
        transitionLists[resultLength].clear();
        constraintBuilder[resultLength] = EmptyArrays.LONG;
        if (operationLists[resultLength] == null) {
            operationLists[resultLength] = new LongArrayBuffer();
        }
        operationLists[resultLength].clear();
        if (intersectingArgs[resultLength] == null) {
            intersectingArgs[resultLength] = new TBitSet(INITIAL_CAPACITY);
        }
        intersectingArgs[resultLength].clear();
    }

    private void addTransitionToStage1(int iArg, int i, T transition, long[] operations) {
        if (anyArgConstraints) {
            intersectingArgs[i].set(iArg);
        } else {
            addTransitionTo(i, transition, operations);
        }
    }

    private void addTransitionTo(int i, T transition, long[] operations) {
        if (shouldPruneAfterFinalState() && leadsToFinalState.get(i)) {
            return;
        }
        // This is a bit hacky but seems to work. The idea is
        // that when we add a NFA transition to an existing DFA transition
        // if the target state is already present, we drop that NFA transition.
        // However, we still have to take its side effects (operations) into account,
        // hence why we add them.
        // A cleaner way would be to duplicate states
        // based on the kind of side effect performed when entering such state.
        // Also, it can add the same operations multiple times, but duplicates are removed
        // afterward.
        operationLists[i].addAll(operations);

        S target = transition.getTarget(forward);
        if (targetStateSets[i].add(target)) {
            transitionLists[i].add(transition);
            if (shouldPruneAfterFinalState()) {
                var targetState = (BasicState<?, ?>) target;
                if (forward ? targetState.hasUnGuardedTransitionToUnAnchoredFinalState(true) : targetState.isUnAnchoredFinalState(false)) {
                    leadsToFinalState.set(i);
                }
            }
        }
    }

    /**
     * Merges transitions calculated by {@link #calcDisjointTransitions(CompilationBuffer)} if their
     * target state set is equal <strong>and</strong>
     * {@link #canMerge(TransitionBuilder, TransitionBuilder)} returns {@code true}.
     */
    private TB[] mergeSameTargets(CompilationBuffer compilationBuffer) {

        ObjectArrayBuffer<TB> resultBuffer1 = compilationBuffer.getObjectBuffer1();
        resultBuffer1.ensureCapacity(resultLength);
        for (int i = resultLengthStage1; i < resultLength; i++) {
            resultBuffer1.add(createTransitionBuilder(transitionLists[i].toArray(this::createTransitionArray), targetStateSets[i], matcherBuilders[i], constraintBuilder[i],
                            operationLists[i].toArray()));
        }

        if (shouldPruneAfterFinalState() && leadsToFinalState.isEmpty() || anyArgConstraints) {
            // If there are no transitions to final state, no transitions were pruned, so no equal
            // transition sets are possible.
            // If there are transitions with constraints, we deliberately don't merge transitions,
            // because we want to guarantee that for any two transitions, their respective codepoint
            // sets are either completely identical or disjunct.
            return resultBuffer1.toArray(createResultArray(resultBuffer1.length()));
        }
        resultBuffer1.sort((TB o1, TB o2) -> {
            TransitionSet<SI, S, T> t1 = o1.getTransitionSet();
            TransitionSet<SI, S, T> t2 = o2.getTransitionSet();
            int cmp = t1.size() - t2.size();
            if (cmp != 0) {
                return cmp;
            }
            if (isPrioritySensitive()) {
                // Transition sets are equal iff they lead to the same target states in the same
                // order.
                for (int i = 0; i < t1.size(); i++) {
                    cmp = t1.getTransition(i).getTarget(forward).getId() - t2.getTransition(i).getTarget(forward).getId();
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return cmp;
            } else {
                // Transition sets are equal iff they lead to the same set of target states.
                // Here, we abuse the fact that our state set iterators yield states ordered by id.
                Iterator<S> i1 = t1.getTargetStateSet().iterator();
                Iterator<S> i2 = t2.getTargetStateSet().iterator();
                while (i1.hasNext()) {
                    assert i2.hasNext();
                    cmp = i1.next().getId() - i2.next().getId();
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return cmp;
            }
        });
        ObjectArrayBuffer<TB> resultBuffer2 = compilationBuffer.getObjectBuffer2();
        TB last = null;
        for (TB tb : resultBuffer1) {
            if (last != null && canMerge(last, tb)) {
                last.setMatcherBuilder(last.getCodePointSet().union(tb.getCodePointSet(), compilationBuffer));
            } else {
                resultBuffer2.add(tb);
                last = tb;
            }
        }
        return resultBuffer2.toArray(createResultArray(resultBuffer2.length()));
    }

    private record ConstraintDeduplicationKey(int transitionID, long[] constraints) {

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ConstraintDeduplicationKey o)) {
                return false;
            }
            return transitionID == o.transitionID && Arrays.equals(constraints, o.constraints);
        }

        @Override
        public int hashCode() {
            return Objects.hash(transitionID, Arrays.hashCode(constraints));
        }
    }

    protected abstract TB createTransitionBuilder(T[] transitions, StateSet<SI, S> targetStateSet, CodePointSet matcherBuilder, long[] constraints, long[] operations);

    /**
     * Returns {@code true} if two DFA transitions are allowed to be merged into one.
     */
    protected abstract boolean canMerge(TB a, TB b);

    protected abstract T[] createTransitionArray(int size);

    /**
     * Returns an array suitable for holding the result of {@link #run(CompilationBuffer)}.
     */
    protected abstract TB[] createResultArray(int size);

}
