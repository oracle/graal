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
package org.graalvm.profdiff.matching.optimization;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.collections.EconomicMap;

/**
 * A matching of optimizations between two compilations of the same method in two experiments. Built
 * incrementally by adding matched/extra optimizations.
 */
public class OptimizationMatching {
    private final EconomicMap<ExperimentId, List<Optimization>> unmatchedOptimizations = EconomicMap.create();

    private final List<Optimization> matchedOptimizations = new ArrayList<>();

    OptimizationMatching() {
        for (ExperimentId experimentId : ExperimentId.values()) {
            unmatchedOptimizations.put(experimentId, new ArrayList<>());
        }
    }

    /**
     * Gets a list of optimizations from an experiment that were not matched with any other
     * optimization from the other method.
     *
     * @param experimentId the experiment ID of the returned optimizations
     * @return a list of extra optimizations
     */
    public List<Optimization> getUnmatchedOptimizations(ExperimentId experimentId) {
        return unmatchedOptimizations.get(experimentId);
    }

    /**
     * Gets a list of optimization that were present in both compiled methods.
     *
     * @return a list of optimizations present in both compiled methods
     */
    public List<Optimization> getMatchedOptimizations() {
        return matchedOptimizations;
    }

    public void addUnmatchedOptimization(Optimization optimization, ExperimentId experimentId) {
        unmatchedOptimizations.get(experimentId).add(optimization);
    }

    public void addMatchedOptimization(Optimization optimization) {
        matchedOptimizations.add(optimization);
    }
}
