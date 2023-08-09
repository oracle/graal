/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.EconomicSet;
import org.graalvm.profdiff.core.CompilationFragment;
import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Method;
import org.graalvm.profdiff.parser.ExperimentParserError;

/**
 * A pair of experiments.
 */
public class ExperimentPair {
    /**
     * An experiment with {@link ExperimentId#ONE}.
     */
    private final Experiment experiment1;

    /**
     * An experiment with {@link ExperimentId#TWO}.
     */
    private final Experiment experiment2;

    /**
     * Constructs a pair of experiments.
     *
     * @param experiment1 an experiment with {@link ExperimentId#ONE}
     * @param experiment2 an experiment with {@link ExperimentId#TWO}
     */
    public ExperimentPair(Experiment experiment1, Experiment experiment2) {
        assert experiment1.getExperimentId() == ExperimentId.ONE && experiment2.getExperimentId() == ExperimentId.TWO;
        this.experiment1 = experiment1;
        this.experiment2 = experiment2;
    }

    /**
     * Gets an iterable over pairs of methods, where at least one of the methods is hot in its
     * respective experiment. The pairs are sorted by the sum of their execution periods, starting
     * with the highest period.
     *
     * @return an iterable over hot pairs of methods sorted by the execution period
     */
    public Iterable<MethodPair> getHotMethodPairsByDescendingPeriod() {
        return () -> getHotMethodPairs().stream().sorted(Comparator.comparingLong(pair -> -pair.getTotalPeriod())).iterator();
    }

    /**
     * Gets a list of hot method pairs. A method pair is hot iff at least one of its methods is hot.
     *
     * @return a list of hot method pairs
     */
    private List<MethodPair> getHotMethodPairs() {
        EconomicSet<String> union = EconomicMapUtil.keySet(experiment1.getHotMethodsByName());
        union.addAll(EconomicMapUtil.keySet(experiment2.getHotMethodsByName()));

        List<MethodPair> methodPairs = new ArrayList<>();
        for (String methodName : union) {
            methodPairs.add(new MethodPair(experiment1.getMethodOrCreate(methodName), experiment2.getMethodOrCreate(methodName)));
        }
        return methodPairs;
    }

    /**
     * Creates compilation fragments for both experiments. Fragments are created for all inlinees
     * where both the compilation unit is {@link CompilationUnit#isHot() hot} and the method of the
     * inlinee is {@link #isHot(String) hot}.
     *
     * @throws ExperimentParserError failed to load a tree pair for an experiment
     */
    public void createCompilationFragments() throws ExperimentParserError {
        for (MethodPair methodPair : getHotMethodPairs()) {
            for (Method method : List.of(methodPair.getMethod1(), methodPair.getMethod2())) {
                List<CompilationUnit> compilationUnitsSnapshot = new ArrayList<>();
                for (CompilationUnit compilationUnit : method.getHotCompilationUnits()) {
                    if (!(compilationUnit instanceof CompilationFragment)) {
                        compilationUnitsSnapshot.add(compilationUnit);
                    }
                }
                for (CompilationUnit compilationUnit : compilationUnitsSnapshot) {
                    compilationUnit.loadTrees().getInliningTree().getRoot().forEach(node -> {
                        if (node.getParent() != null && node.isPositive() && node.getName() != null && isHot(node.getName())) {
                            method.getExperiment().getMethodOrCreate(node.getName()).addCompilationFragment(compilationUnit, node);
                        }
                    });
                }
            }
        }
    }

    /**
     * Returns {@code true} iff the given method is hot in at least one of the experiments.
     *
     * @param methodName the name of the given method
     * @return {@code true} iff the given method is hot in at least one of the experiments
     */
    private boolean isHot(String methodName) {
        return experiment1.getMethodOrCreate(methodName).isHot() || experiment2.getMethodOrCreate(methodName).isHot();
    }
}
