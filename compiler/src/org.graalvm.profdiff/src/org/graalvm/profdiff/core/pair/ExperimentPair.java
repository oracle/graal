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
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.parser.ExperimentParserError;
import org.graalvm.util.CollectionsUtil;

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
        EconomicSet<String> union = EconomicMapUtil.keySet(experiment1.getHotMethodsByName());
        union.addAll(EconomicMapUtil.keySet(experiment2.getHotMethodsByName()));

        List<MethodPair> methodPairs = new ArrayList<>();
        for (String methodName : union) {
            methodPairs.add(new MethodPair(experiment1.getMethodOrCreate(methodName), experiment2.getMethodOrCreate(methodName)));
        }
        return () -> methodPairs.stream().sorted(Comparator.comparingLong(pair -> -pair.getTotalPeriod())).iterator();
    }

    /**
     * Creates compilation fragments for both experiments.
     *
     * @throws ExperimentParserError failed to load a tree pair for an experiment
     */
    public void createCompilationFragments() throws ExperimentParserError {
        createCompilationFragments(experiment1, experiment2);
        createCompilationFragments(experiment2, experiment1);
    }

    /**
     * Creates compilation fragments for the experiment {@code destination} by testing the
     * requirements against the experiment {@code source}.
     *
     * The method executes the following algorithm to find appropriate candidates for a compilation
     * fragment:
     *
     * <pre>
     *     for each method M in the destination experiment:
     *         for each hot compilation unit CU of the method M:
     *             for each inlined method I in CU:
     *                 if (1) the path to I in CU is unique
     *                    (2) and there is not any hot compilation unit of M in the other experiment
     *                            or there exists a hot compilation unit of M in the other experiment
     *                                            where I is not inlined:
     *                    (3) and the method I has a hot compilation unit in either experiment
     *                    create a fragment from CU rooted in I
     * </pre>
     *
     * The first condition (1) ensures that optimizations can be properly attributed to the
     * fragments. A path to an inlining {@code node} in a compilation unit's {@code inliningTree} is
     * unique iff {@code inliningTree.findNodesAt(InliningPath.fromRootToNode(node)).size() == 1}.
     * In other words, the {@link InliningPath} leads exactly to our {@code node} and not any other
     * node. This property is usually violated after code duplication.
     *
     * The second condition (2) filters out the cases where there is clearly no inlining difference.
     *
     * The third condition (3) ensures that we are creating a fragment for a method that is
     * important, i.e., it is hot in at least one of the experiments.
     *
     * @param destination the destination experiment where fragments are created
     * @param source the source experiments, which is only used to test requirements
     * @throws ExperimentParserError failed to load a tree pair for an experiment
     */
    private static void createCompilationFragments(Experiment destination, Experiment source) throws ExperimentParserError {
        for (Method method : collect(destination.getMethodsByName().getValues())) {
            if (!method.isHot()) {
                continue;
            }
            for (CompilationUnit compilationUnit : collect(method.getHotCompilationUnits())) {
                if (compilationUnit instanceof CompilationFragment) {
                    continue;
                }
                InliningTree inliningTree = compilationUnit.loadTrees().getInliningTree();
                inliningTree.getRoot().forEach(node -> {
                    // make sure the node is actually inlined, and it is not the root node
                    if (node.getName() == null || !node.isPositive() || method.getMethodName().equals(node.getName())) {
                        return;
                    }
                    // make sure the path is unique
                    InliningPath pathToNode = InliningPath.fromRootToNode(node);
                    if (inliningTree.findNodesAt(pathToNode).size() != 1) {
                        return;
                    }
                    // make sure the method of the node (the root of the fragment to be created) is
                    // hot in either
                    // experiment
                    if (!destination.getMethodOrCreate(node.getName()).isHot() && !source.getMethodOrCreate(node.getName()).isHot()) {
                        return;
                    }
                    // make sure the parent method is either not hot in the other experiment
                    boolean hasHotCompilationUnit = false;
                    // or there exists a hot compilation unit of the parent method where the node is
                    // not inlined
                    boolean notInlinedInAtLeastOneCompilationUnit = false;
                    for (CompilationUnit otherCompilationUnit : source.getMethodOrCreate(method.getMethodName()).getHotCompilationUnits()) {
                        if (otherCompilationUnit instanceof CompilationFragment) {
                            continue;
                        }
                        hasHotCompilationUnit = true;
                        try {
                            List<InliningTreeNode> otherNodes = otherCompilationUnit.loadTrees().getInliningTree().findNodesAt(pathToNode);
                            if (!CollectionsUtil.anyMatch(otherNodes, InliningTreeNode::isPositive)) {
                                notInlinedInAtLeastOneCompilationUnit = true;
                                break;
                            }
                        } catch (ExperimentParserError e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (!notInlinedInAtLeastOneCompilationUnit && hasHotCompilationUnit) {
                        return;
                    }
                    destination.getMethodOrCreate(node.getName()).addCompilationFragment(compilationUnit, pathToNode);
                });
            }
        }
    }

    private static <T> List<T> collect(Iterable<T> iterable) {
        List<T> list = new ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }
}
