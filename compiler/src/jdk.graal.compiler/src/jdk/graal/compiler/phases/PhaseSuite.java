/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import jdk.graal.compiler.core.common.util.PhasePlan;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

import jdk.vm.ci.services.Services;

/**
 * A compiler phase that can apply an ordered collection of phases to a graph.
 */
public class PhaseSuite<C> extends BasePhase<C> implements PhasePlan<BasePhase<? super C>>, RecursivePhase {

    public static class Options {
        @Option(help = "Prints the difference in the graph state caused by each phase of the suite.") //
        public static final OptionKey<Boolean> PrintGraphStateDiff = new OptionKey<>(false);
    }

    private List<BasePhase<? super C>> phases;
    private boolean immutable;

    public PhaseSuite() {
        this.phases = new ArrayList<>();
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public synchronized void setImmutable() {
        if (!immutable) {
            phases = Collections.unmodifiableList(phases);
            immutable = true;
        }
    }

    /**
     * Add a new phase at the beginning of this suite.
     */
    public final void prependPhase(BasePhase<? super C> phase) {
        phases.add(0, phase);
    }

    /**
     * Add a new phase at the end of this suite.
     */
    public final void appendPhase(BasePhase<? super C> phase) {
        phases.add(phase);
    }

    /**
     * Inserts a phase before the last phase in the suite. If the suite contains no phases the new
     * phase will be inserted as the first phase.
     */
    public final void addBeforeLast(BasePhase<? super C> phase) {
        ListIterator<BasePhase<? super C>> last = findLastPhase();
        if (last.hasPrevious()) {
            last.previous();
        }
        last.add(phase);
    }

    /**
     * Insert a new phase at the specified index and shifts the element currently at that position
     * and any subsequent elements to the right.
     */
    public final void insertAtIndex(int index, BasePhase<? super C> phase) {
        phases.add(index, phase);
    }

    /**
     * Returns a {@link ListIterator} at the position of the last phase in the suite. If the suite
     * has no phases then it will return an empty iterator.
     */
    public ListIterator<BasePhase<? super C>> findLastPhase() {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            it.next();
        }
        return it;
    }

    /**
     * Gets an unmodifiable view on the phases in this suite.
     */
    @Override
    public List<BasePhase<? super C>> getPhases() {
        return Collections.unmodifiableList(phases);
    }

    @Override
    public String getPhaseName(BasePhase<? super C> phase) {
        return phase.contractorName();
    }

    @Override
    public String toString() {
        return String.format("%s:%n%s", getClass().getSimpleName(), new PhasePlan.Printer().toString(this));
    }

    /**
     * Returns a {@link ListIterator} at the position of the first phase which is an instance of
     * {@code phaseClass} or null if no such phase can be found.
     *
     * Calling {@link ListIterator#previous()} would return the phase that was found.
     *
     * @param phaseClass the type of phase to look for.
     */
    public final ListIterator<BasePhase<? super C>> findPhase(Class<? extends BasePhase<? super C>> phaseClass) {
        return findPhase(phaseClass, false);
    }

    /**
     * Returns a {@link ListIterator} at the position of the first phase which is an instance of
     * {@code phaseClass} or, if {@code recursive} is true, is a {@link PhaseSuite} containing a
     * phase which is an instance of {@code phaseClass}. This method returns null if no such phase
     * can be found.
     *
     * Calling {@link ListIterator#previous()} would return the phase or phase suite that was found.
     *
     * @param phaseClass the type of phase to look for
     * @param recursive whether to recursively look into phase suites.
     */
    public final ListIterator<BasePhase<? super C>> findPhase(Class<? extends BasePhase<? super C>> phaseClass, boolean recursive) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        if (findNextPhase(it, phaseClass, recursive)) {
            return it;
        } else {
            return null;
        }
    }

    public static <C> boolean findNextPhase(ListIterator<BasePhase<? super C>> it, Class<? extends BasePhase<? super C>> phaseClass) {
        return findNextPhase(it, phaseClass, false);
    }

    @SuppressWarnings("unchecked")
    public static <C> boolean findNextPhase(ListIterator<BasePhase<? super C>> it, Class<? extends BasePhase<? super C>> phaseClass, boolean recursive) {
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phaseClass.isInstance(phase) ||
                            (phase instanceof PlaceholderPhase && ((PlaceholderPhase<C>) phase).getPhaseClass().equals(phaseClass))) {
                return true;
            } else if (recursive && phase instanceof PhaseSuite) {
                PhaseSuite<C> suite = (PhaseSuite<C>) phase;
                if (suite.findPhase(phaseClass, true) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes the first instance of the given phase class, looking recursively into inner phase
     * suites.
     */
    public boolean removePhase(Class<? extends BasePhase<? super C>> phaseClass) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phaseClass.isInstance(phase)) {
                it.remove();
                return true;
            } else if (phase instanceof PhaseSuite) {
                @SuppressWarnings("unchecked")
                PhaseSuite<C> innerSuite = (PhaseSuite<C>) phase;
                if (innerSuite.removePhase(phaseClass)) {
                    if (innerSuite.phases.isEmpty()) {
                        it.remove();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Removes all phases in this suite that are assignable to {@code type}.
     */
    public boolean removeSubTypePhases(Class<?> type) {
        boolean hasRemovedSpeculativePhase = false;
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (type.isAssignableFrom(phase.getClass())) {
                it.remove();
                hasRemovedSpeculativePhase = true;
            } else if (phase instanceof PhaseSuite) {
                @SuppressWarnings("unchecked")
                PhaseSuite<C> innerSuite = (PhaseSuite<C>) phase;
                if (innerSuite.removeSubTypePhases(type)) {
                    if (innerSuite.phases.isEmpty()) {
                        it.remove();
                    }
                    hasRemovedSpeculativePhase = true;
                }
            }
        }
        return hasRemovedSpeculativePhase;
    }

    /**
     * Replaces the first instance of the given phase class, looking recursively into inner phase
     * suites.
     */
    @SuppressWarnings("unchecked")
    public boolean replacePhase(Class<? extends BasePhase<? super C>> phaseClass, BasePhase<? super C> newPhase) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phaseClass.isInstance(phase)) {
                it.set(newPhase);
                return true;
            } else if (phase instanceof PhaseSuite) {
                PhaseSuite<C> innerSuite = (PhaseSuite<C>) phase;
                if (innerSuite.replacePhase(phaseClass, newPhase)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Replaces all instances of the given phase class, looking recursively into inner phase suites.
     *
     * @return {@code true} if at least one replacement was made, {@code false} otherwise.
     */
    @SuppressWarnings("unchecked")
    public boolean replaceAllPhases(Class<? extends BasePhase<? super C>> phaseClass, Supplier<BasePhase<? super C>> newPhase) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        boolean replaced = false;
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phaseClass.isInstance(phase)) {
                it.set(newPhase.get());
                replaced = true;
            } else if (phase instanceof PhaseSuite) {
                PhaseSuite<C> innerSuite = (PhaseSuite<C>) phase;
                if (innerSuite.replaceAllPhases(phaseClass, newPhase)) {
                    replaced = true;
                }
            }
        }
        return replaced;
    }

    /**
     * Replaces the first {@linkplain PlaceholderPhase placeholder} of the given phase class,
     * looking recursively into inner phase suites.
     */
    @SuppressWarnings("unchecked")
    public boolean replacePlaceholder(Class<? extends BasePhase<? super C>> phaseClass, BasePhase<? super C> phaseInstance) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phase instanceof PlaceholderPhase && ((PlaceholderPhase<C>) phase).getPhaseClass().equals(phaseClass)) {
                it.set(phaseInstance);
                return true;
            } else if (phase instanceof PhaseSuite) {
                PhaseSuite<C> innerSuite = (PhaseSuite<C>) phase;
                if (innerSuite.replacePlaceholder(phaseClass, phaseInstance)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This phase suite must apply if any of its phases must apply.
     */
    @Override
    public boolean mustApply(GraphState graphState) {
        for (BasePhase<? super C> phase : phases) {
            if (phase.mustApply(graphState)) {
                return true;
            }
        }
        return super.mustApply(graphState);
    }

    /**
     * This phase suite can apply if all its phases can be applied one after the other. The effects
     * of each phase on the graph state is simulated using
     * {@link BasePhase#updateGraphState(GraphState)}.
     */
    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        Formatter cannotApplyBuf = new Formatter();
        GraphState simulationGraphState = graphState.copy();
        for (BasePhase<? super C> phase : getPhases()) {
            Optional<NotApplicable> phaseNotApplicable = phase.notApplicableTo(simulationGraphState);
            if (phaseNotApplicable.isPresent()) {
                String name = phase.getClass().getName();
                if (name.contains(".svm.") || name.contains(".truffle.")) {
                    // GR-39494: notApplicableTo(GraphState) not yet implemented by SVM or Truffle
                    // phases.
                } else {
                    cannotApplyBuf.format("%s : %s%n", phase.getClass().getName(), phaseNotApplicable.get().toString());
                }
            }
            try {
                if (phase instanceof PhaseSuite) {
                    ((PhaseSuite<? super C>) phase).updateGraphStateWithPhases(simulationGraphState);
                } else {
                    phase.updateGraphState(simulationGraphState);
                }
            } catch (Throwable t) {
                cannotApplyBuf.format("%s : cannot update the state of the graph.%n", phase.getClass().getName());
                return Optional.of(new NotApplicable(cannotApplyBuf.toString(), t));
            }
        }
        String cannotApply = cannotApplyBuf.toString();
        if (cannotApply.isEmpty()) {
            return ALWAYS_APPLICABLE;
        }
        return Optional.of(new NotApplicable(cannotApply));
    }

    /**
     * Updates the graph state with the phases that compose this phase suite and with its own
     * effects on the graph state.
     */
    private void updateGraphStateWithPhases(GraphState graphState) {
        for (BasePhase<? super C> phase : this.getPhases()) {
            if (phase instanceof PhaseSuite) {
                ((PhaseSuite<? super C>) phase).updateGraphStateWithPhases(graphState);
            } else {
                phase.updateGraphState(graphState);
            }
        }
        this.updateGraphState(graphState);
    }

    @Override
    protected void run(StructuredGraph graph, C context) {
        boolean printGraphStateDiff = Options.PrintGraphStateDiff.getValue(graph.getOptions());
        GraphState graphStateBefore = null;
        if (printGraphStateDiff) {
            graphStateBefore = graph.getGraphState().copy();
        }
        int index = 0;
        for (BasePhase<? super C> phase : phases) {
            try {
                phase.apply(graph, context);

                if (printGraphStateDiff && !graph.getGraphState().equals(graphStateBefore)) {
                    if (graphStateDiffs == null) {
                        graphStateDiffs = new HashMap<>();
                    }
                    graphStateDiffs.put(index, graph.getGraphState().updateFromPreviousToString(graphStateBefore));
                    graphStateBefore = graph.getGraphState().copy();
                }
            } catch (Throwable t) {
                if (Boolean.parseBoolean(Services.getSavedProperty("test.graal.compilationplan.fuzzing"))) {
                    TTY.println("========================================================================================================================");
                    TTY.println("An error occurred while executing phase %s.", phase.getClass().getName());
                    TTY.printf("The graph state after the failing phase is:%n%s", graph.getGraphState().toString("\t"));
                    TTY.println("========================================================================================================================");
                }
                failureIndex = index;
                throw t;
            }
            index++;
        }
    }

    public PhaseSuite<C> copy() {
        PhaseSuite<C> suite = new PhaseSuite<>();
        suite.phases.addAll(phases);
        return suite;
    }

    /**
     * Records the changes made to the {@link GraphState} caused by the phase at the given index in
     * this phase suite. It only maintains entries for phases that change the graph state.
     */
    private Map<Integer, String> graphStateDiffs;

    @Override
    public String getGraphStateDiff(int position) {
        if (graphStateDiffs == null) {
            return null;
        }
        return graphStateDiffs.get(position);
    }

    /**
     * Records the index of the phase that caused the phase suite to fail. {@code -1} means no
     * failure occurred.
     */
    private int failureIndex = -1;

    @Override
    public int getFailureIndex() {
        return failureIndex;
    }
}
