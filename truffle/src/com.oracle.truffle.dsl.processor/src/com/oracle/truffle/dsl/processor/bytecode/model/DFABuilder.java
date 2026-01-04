/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.model;

import static com.oracle.truffle.dsl.processor.bytecode.model.DFABuilder.DFAModel.DFAState;
import static com.oracle.truffle.dsl.processor.bytecode.model.DFABuilder.NFAModel.NFAState;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.dsl.processor.bytecode.model.InstructionRewriteRuleModel.ResolvedInstructionPatternModel;

public final class DFABuilder {
    private static final String EPSILON = "EPSILON";

    public static DFAModel buildDFA(InstructionRewriteRuleModel[] rules) {
        /*
         * Step 1: Create an NFA. For each rewrite rule, construct a path recognizing the LHS.
         */
        NFAModel nfa = new NFAModel();
        for (InstructionRewriteRuleModel rule : rules) {
            NFAState current = nfa.addState(new RewriteRuleState(rule, 0));
            nfa.addTransition(nfa.startState, EPSILON, new LinkedHashSet<>(List.of(current)));
            for (int i = 0; i < rule.lhs.length; i++) {
                NFAState next = nfa.addState(new RewriteRuleState(rule, i + 1));
                nfa.addTransition(current, rule.lhs[i].instruction().getName(), new LinkedHashSet<>(List.of(next)));
                current = next;
            }
        }

        /*
         * Step 2: For each state, add an epsilon transition to the start state. These transitions
         * allow us to recognize a rule mid-way through recognizing another.
         */
        for (NFAState state : nfa.states) {
            if (state == nfa.startState) {
                continue;
            }
            if (state.isUnconditionallyAccepting()) {
                /*
                 * If an accepting state always triggers a rewrite, an epsilon transition out of it
                 * would never be taken.
                 */
                continue;
            }
            nfa.addTransition(state, EPSILON, new LinkedHashSet<>(List.of(nfa.startState)));
        }

        /*
         * Step 3: Convert the NFA to a DFA. This process iteratively discovers sets of NFA states
         * (i.e., states that an NFA can concurrently be "in") by following transition edges and
         * adds corresponding DFA states.
         */
        SequencedSet<NFAState> start = epsilonClosure(new LinkedHashSet<>(List.of(nfa.startState)), nfa.startState);
        DFAModel dfa = new DFAModel(start.stream().map(NFAState::getRewriteState).toList());
        Map<Set<NFAState>, DFAState> nfaToDfaMapping = new HashMap<>();
        nfaToDfaMapping.put(start, dfa.startState);

        List<SequencedSet<NFAState>> worklist = new LinkedList<>();
        worklist.add(start);

        while (!worklist.isEmpty()) {
            SequencedSet<NFAState> currentSet = worklist.removeFirst();
            DFAState currentDfaState = nfaToDfaMapping.get(currentSet);
            if (currentDfaState == null) {
                throw new AssertionError("Did not allocate a DFA state for NFA state set: " + currentSet);
            }
            if (!epsilonClosure(currentSet, nfa.startState).equals(currentSet)) {
                throw new AssertionError("Created a state set that wasn't an epsilon closure: " + currentSet);
            }

            // Compute the set of values the current set can transition on.
            SequencedSet<String> transitions = new LinkedHashSet<>();
            for (NFAState nfaState : currentSet) {
                transitions.addAll(nfaState.transitions.keySet());
            }

            // For each transition, compute and register the new state it leads to.
            for (String input : transitions) {
                if (input == EPSILON) {
                    // Ignore epsilon transitions (current state is an epsilon closure).
                    continue;
                }
                SequencedSet<NFAState> nextSet = computeNextSet(currentSet, input, nfa.startState);

                DFAState nextDfaState = nfaToDfaMapping.get(nextSet);
                if (nextDfaState == null) {
                    // New state set found. Add it to the DFA and worklist.
                    nextDfaState = dfa.addState(nextSet.stream().map(NFAState::getRewriteState).toList());
                    nfaToDfaMapping.put(nextSet, nextDfaState);
                    worklist.add(nextSet);
                }
                dfa.addTransition(currentDfaState, input, nextDfaState);
            }
        }

        return dfa;
    }

    private static SequencedSet<NFAState> computeNextSet(SequencedSet<NFAState> states, String input, NFAState startState) {
        SequencedSet<NFAState> result = new LinkedHashSet<>();
        for (NFAState state : states) {
            result.addAll(state.transitions.getOrDefault(input, new LinkedHashSet<>()));
        }

        List<NFAState> acceptingStates = result.stream().filter(NFAState::isAccepting).toList();
        if (acceptingStates.size() > 1) {
            throw new IllegalStateException("Instruction rewriter DFA state has multiple rewrite rules that could apply: %s".formatted(acceptingStates));
        } else if (acceptingStates.size() == 1 && acceptingStates.getFirst().isUnconditionallyAccepting()) {
            // If the state unconditionally accepts, the other states are irrelevant.
            return new LinkedHashSet<>(List.of(acceptingStates.getFirst()));
        } else {
            return epsilonClosure(result, startState);
        }
    }

    /**
     * Returns the epsilon-closure of the given set of states. Drops the start state (it is only a
     * convenience for constructing the NFA; we do not want it in our DFA).
     */
    private static SequencedSet<NFAState> epsilonClosure(SequencedSet<NFAState> states, NFAState startState) {
        SequencedSet<NFAState> result = new LinkedHashSet<>(states);
        List<NFAState> worklist = new LinkedList<>(states);
        while (!worklist.isEmpty()) {
            NFAState current = worklist.removeFirst();
            for (NFAState next : current.transitions.getOrDefault(EPSILON, new LinkedHashSet<>())) {
                if (!result.contains(next)) {
                    // New node found. Add it to the worklist.
                    result.add(next);
                    worklist.add(next);
                }
            }
        }

        result.remove(startState);
        return result;
    }

    private abstract static sealed class FiniteAutomaton<S extends State<TState, TTransition>, TState, TTransition> permits DFAModel, NFAModel {
        private int stateCount = 0;

        public final SequencedSet<S> states;
        public final S startState;

        protected FiniteAutomaton(TState startState) {
            this.states = new LinkedHashSet<>();
            this.startState = addState(startState);
        }

        public S addState(TState state) {
            S newState = createState(stateCount++, state);
            states.add(newState);
            return newState;
        }

        public abstract void addTransition(S current, String transition, TTransition next);

        protected abstract S createState(int id, TState state);

        public List<S> getAcceptingStates() {
            return states.stream().filter(s -> s.isAccepting()).toList();
        }

        public List<S> getNonAcceptingStates() {
            return states.stream().filter(s -> !s.isAccepting()).toList();
        }
    }

    /**
     * Represents a state in a finite automaton.
     *
     * @param <TState> the logical state tracked by this state.
     * @param <TTransition> the result type from a transition step.
     */
    private abstract static class State<TState, TTransition> {
        public final int id;
        public final TState label;
        public final SequencedMap<String, TTransition> transitions;

        State(int id, TState label) {
            this.id = id;
            this.label = label;
            this.transitions = new LinkedHashMap<>();
        }

        protected abstract String getDescription();

        public abstract boolean isAccepting();

        @Override
        public final String toString() {
            return getDescription();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof State<?, ?> otherState && id == otherState.id && label.equals(otherState.label);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    public record RewriteRuleState(InstructionRewriteRuleModel rule, int index) implements Comparable<RewriteRuleState> {
        public RewriteRuleState {
            Objects.requireNonNull(rule);
            if (index < 0 || index > rule.lhs.length) {
                throw new IllegalArgumentException("Invalid rewrite rule index " + index + " for rule " + rule);
            }
        }

        @Override
        public String toString() {
            return rule.toString(index);
        }

        public boolean isAccepting() {
            return index == rule.lhs.length;
        }

        public boolean leadsToAcceptingState() {
            return index == rule.lhs.length - 1;
        }

        public ResolvedInstructionPatternModel getNextInstruction() {
            if (index == rule.lhs.length) {
                return null;
            }
            return rule.lhs[index];
        }

        public int compareTo(RewriteRuleState other) {
            // Rules with less progress first.
            int cmp = Integer.compare(this.index, other.index);
            if (cmp != 0) {
                return cmp;
            }

            return this.rule.compareTo(other.rule);
        }
    }

    public static final class DFAModel extends FiniteAutomaton<DFAModel.DFAState, List<RewriteRuleState>, DFAModel.DFAState> {

        protected DFAModel(List<RewriteRuleState> startState) {
            super(startState);
        }

        Map<InstructionRewriteRuleModel, DFAState> ruleToAcceptingState = new HashMap<>();

        public static class DFAState extends State<List<RewriteRuleState>, DFAState> {
            private final InstructionRewriteRuleModel acceptingRule;

            DFAState(int id, List<RewriteRuleState> label) {
                super(id, label);
                this.acceptingRule = getAcceptingRule(label);
            }

            private static InstructionRewriteRuleModel getAcceptingRule(List<RewriteRuleState> states) {
                if (states == null) {
                    // start state
                    return null;
                }

                InstructionRewriteRuleModel result = null;
                for (RewriteRuleState state : states) {
                    if (state.isAccepting()) {
                        InstructionRewriteRuleModel rule = state.rule();
                        // Give priority to the rule with the longest match.
                        if (result == null || rule.lhs.length > result.lhs.length) {
                            result = rule;
                        }
                    }
                }
                return result;
            }

            @Override
            protected String getDescription() {
                return label.stream().sorted().map(RewriteRuleState::toString).collect(Collectors.joining("\n"));
            }

            @Override
            public boolean isAccepting() {
                return acceptingRule != null;
            }

            public List<RewriteRuleState> getRewriteStates() {
                return label;
            }

            public InstructionRewriteRuleModel getAcceptingRule() {
                return acceptingRule;
            }
        }

        @Override
        protected DFAState createState(int id, List<RewriteRuleState> label) {
            DFAState newState = new DFAState(id, label);

            if (newState.isAccepting()) {
                DFAState existing = ruleToAcceptingState.put(newState.getAcceptingRule(), newState);
                if (existing != null) {
                    throw new AssertionError("Created multiple accepting states for a given rewrite rule %s:%n%s%n%s".formatted(newState.getAcceptingRule(), existing, newState));
                }
            }
            return newState;
        }

        @Override
        public void addTransition(DFAState current, String transition, DFAState next) {
            current.transitions.put(transition, next);
        }

        public DFAState getAcceptingState(InstructionRewriteRuleModel rule) {
            return ruleToAcceptingState.get(rule);
        }

    }

    static final class NFAModel extends FiniteAutomaton<NFAModel.NFAState, RewriteRuleState, SequencedSet<NFAModel.NFAState>> {

        protected NFAModel() {
            super(null);
        }

        static class NFAState extends State<RewriteRuleState, SequencedSet<NFAState>> {
            NFAState(int id, RewriteRuleState label) {
                super(id, label);
            }

            @Override
            protected String getDescription() {
                if (label == null) {
                    return "START";
                }
                return label.toString();
            }

            @Override
            public boolean isAccepting() {
                return label.isAccepting();
            }

            public boolean isUnconditionallyAccepting() {
                return isAccepting() && !label.rule().hasImmediateConstraints();
            }

            public RewriteRuleState getRewriteState() {
                return label;
            }
        }

        @Override
        protected NFAState createState(int id, RewriteRuleState label) {
            return new NFAState(id, label);
        }

        @Override
        public void addTransition(NFAState current, String transition, SequencedSet<NFAState> next) {
            current.transitions.computeIfAbsent(transition, t -> new LinkedHashSet<>()).addAll(next);
        }
    }

}
