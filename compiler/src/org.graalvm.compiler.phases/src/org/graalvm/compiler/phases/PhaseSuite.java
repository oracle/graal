/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.graalvm.compiler.core.common.util.PhasePlan;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * A compiler phase that can apply an ordered collection of phases to a graph.
 */
public class PhaseSuite<C> extends BasePhase<C> implements PhasePlan<BasePhase<? super C>> {

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
            if (phaseClass.isInstance(phase)) {
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
    @SuppressWarnings("unchecked")
    public boolean removePhase(Class<? extends BasePhase<? super C>> phaseClass) {
        ListIterator<BasePhase<? super C>> it = phases.listIterator();
        while (it.hasNext()) {
            BasePhase<? super C> phase = it.next();
            if (phaseClass.isInstance(phase)) {
                it.remove();
                return true;
            } else if (phase instanceof PhaseSuite) {
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
     * Removes the first instance of the given phase class, looking recursively into inner phase
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

    @Override
    protected void run(StructuredGraph graph, C context) {
        for (BasePhase<? super C> phase : phases) {
            phase.apply(graph, context);
        }
    }

    public PhaseSuite<C> copy() {
        PhaseSuite<C> suite = new PhaseSuite<>();
        suite.phases.addAll(phases);
        return suite;
    }
}
