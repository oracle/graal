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
import org.graalvm.profdiff.core.inlining.InliningPath;
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
     * Gets the experiment with {@link ExperimentId#ONE}.
     */
    public Experiment getExperiment1() {
        return experiment1;
    }

    /**
     * Gets the experiment with {@link ExperimentId#TWO}.
     */
    public Experiment getExperiment2() {
        return experiment2;
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
     * Creates compilation fragments for both experiments. Fragments are created for all
     * {@link #findFragmentationPaths() fragmentation paths} in all hot compilation units.
     *
     * @throws ExperimentParserError failed to load a tree pair for an experiment
     */
    public void createCompilationFragments() throws ExperimentParserError {
        EconomicSet<InliningPath> fragmentationPaths = findFragmentationPaths();
        for (MethodPair methodPair : getHotMethodPairs()) {
            for (Method method : List.of(methodPair.getMethod1(), methodPair.getMethod2())) {
                List<CompilationUnit> compilationUnitsSnapshot = new ArrayList<>();
                for (CompilationUnit compilationUnit : method.getHotCompilationUnits()) {
                    if (!(compilationUnit instanceof CompilationFragment)) {
                        compilationUnitsSnapshot.add(compilationUnit);
                    }
                }
                for (CompilationUnit compilationUnit : compilationUnitsSnapshot) {
                    EconomicSet<InliningPath> usedFragmentationPaths = EconomicSet.create();
                    compilationUnit.loadTrees().getInliningTree().getRoot().forEach(node -> {
                        if (node.getName() != null && node.isPositive() && node.getParent() != null) {
                            InliningPath path = InliningPath.fromRootToNode(node);
                            if (fragmentationPaths.contains(path) && !usedFragmentationPaths.contains(path)) {
                                usedFragmentationPaths.add(path);
                                method.getExperiment().getMethodOrCreate(node.getName()).addCompilationFragment(compilationUnit, path);
                            }
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

    /**
     * Finds the set of fragmentation paths. If an inlining path {@code P} is a fragmentation path,
     * it is desirable to create compilation fragments from hot compilation units containing an
     * inlinee at {@code P}.
     *
     * Let {@code P} be an inlining path from a root method {@code R} to some method {@code M}. The
     * path {@code P} is a fragmentation path iff:
     *
     * <ul>
     * <li>{@code |P| > 1},</li>
     * <li>and {@code M} is {@link #isHot(String) hot} in at least one of the experiments,</li>
     * <li>and in any of the experiments, there exists a hot compilation unit of {@code R} with an
     * inlined method at {@code P},</li>
     * <li>and in the other experiment, it is not true that for each hot compilation unit of
     * {@code R} there is an inlined method at {@code P}.</li>
     * </ul>
     *
     * @return the set of fragment-root methods for the given method pair.
     * @throws ExperimentParserError r failed to load an inlining tree of a compilation unit
     */
    private EconomicSet<InliningPath> findFragmentationPaths() throws ExperimentParserError {
        EconomicSet<InliningPath> result = EconomicSet.create();
        for (MethodPair methodPair : getHotMethodPairs()) {
            MethodInliningPaths paths1 = getMethodInliningPaths(methodPair.getMethod1());
            MethodInliningPaths paths2 = getMethodInliningPaths(methodPair.getMethod2());
            for (InliningPath path : paths1.atLeastOnceInlined) {
                if (!paths2.isAlwaysInlined(path)) {
                    result.add(path);
                }
            }
            for (InliningPath path : paths2.atLeastOnceInlined) {
                if (!paths1.isAlwaysInlined(path)) {
                    result.add(path);
                }
            }
        }
        return result;
    }

    /**
     * Statistics about inlining paths leading to hot inlinees of one method that is necessary to
     * identify fragmentation paths.
     *
     * The hotness of an inlinee is determined by {@link #isHot(String)}. Only hot inlinees are
     * considered, because fragments are created only for hot methods (both the parent compilation
     * unit of the fragment must be {@link CompilationUnit#isHot() hot} and the fragment's root
     * method must be {@link #isHot(String) hot} in at least one of the experiments).
     */
    private static final class MethodInliningPaths {
        /**
         * Paths to hot inlined methods that are present in each hot compilation unit of the method.
         * Initially {@code null}, which is interpreted as the set which contains all paths.
         */
        private EconomicSet<InliningPath> alwaysInlined = null;

        /**
         * Paths to hot inlined methods that are present in at least one hot compilation unit of the
         * method.
         */
        private final EconomicSet<InliningPath> atLeastOnceInlined = EconomicSet.create();

        /**
         * Returns {@code true} iff the given inlined path is inlined in each hot compilation unit
         * of this method.
         *
         * @param inliningPath the inlining path to be tested
         * @return {@code true} {@code true} iff the given inlined path is inlined in each hot
         *         compilation unit of this method
         */
        private boolean isAlwaysInlined(InliningPath inliningPath) {
            return alwaysInlined != null && alwaysInlined.contains(inliningPath);
        }
    }

    /**
     * Computes statistics about inlining paths leading to hot methods in hot compilation units of
     * the given method.
     *
     * @param method the method to compute statistics for
     * @return statistics about inlining paths
     * @throws ExperimentParserError failed to load an inlining tree of a compilation unit
     */
    private MethodInliningPaths getMethodInliningPaths(Method method) throws ExperimentParserError {
        MethodInliningPaths methodInliningPaths = new MethodInliningPaths();
        for (CompilationUnit compilationUnit : method.getHotCompilationUnits()) {
            EconomicSet<InliningPath> inlinees = EconomicSet.create();
            compilationUnit.loadTrees().getInliningTree().getRoot().forEach(node -> {
                if (node.getParent() != null && node.isPositive() && node.getName() != null && isHot(node.getName())) {
                    inlinees.add(InliningPath.fromRootToNode(node));
                }
            });
            if (methodInliningPaths.alwaysInlined == null) {
                methodInliningPaths.alwaysInlined = inlinees;
            } else {
                methodInliningPaths.alwaysInlined.retainAll(inlinees);
            }
            methodInliningPaths.atLeastOnceInlined.addAll(inlinees);
        }
        return methodInliningPaths;
    }
}
