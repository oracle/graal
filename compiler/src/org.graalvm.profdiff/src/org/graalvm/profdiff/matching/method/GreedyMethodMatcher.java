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
package org.graalvm.profdiff.matching.method;

import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.profdiff.core.CompilationUnit;
import org.graalvm.profdiff.core.Experiment;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.EconomicSet;
import org.graalvm.util.CollectionsUtil;

/**
 * Matches methods of two experiments by compilation method names and then greedily matches their
 * respective compilations by descending execution periods.
 */
public class GreedyMethodMatcher implements MethodMatcher {
    /**
     * Matches pairs of methods by their signature and then greedily matches their respective
     * compilations. For a given Java method (in both experiments), the hottest compilation units
     * (with the longest execution period) are matched first. Then again the pair of the hottest
     * compilation units is matched until no more pairs are left. Only hot compilation units and
     * methods that have a hot compilation units are considered. Returns an object describing the
     * pairs of matched methods, matched compilation units and also the list of (executed) methods
     * that do not have a pair - unmatched methods.
     *
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     * @return the description of the computed matching
     * @see Experiment#groupHotCompilationUnitsByMethod()
     */
    @Override
    public MethodMatching match(Experiment experiment1, Experiment experiment2) {
        EconomicMap<String, List<CompilationUnit>> methodMap1 = experiment1.groupHotCompilationUnitsByMethod();
        EconomicMap<String, List<CompilationUnit>> methodMap2 = experiment2.groupHotCompilationUnitsByMethod();
        MethodMatching matching = new MethodMatching();

        EconomicSet<String> intersection = EconomicMapUtil.keySet(methodMap1);
        intersection.retainAll(EconomicMapUtil.keySet(methodMap2));
        for (String compilationMethodName : intersection) {
            List<CompilationUnit> leftMethods = methodMap1.get(compilationMethodName).stream().sorted(GreedyMethodMatcher::greaterPeriodComparator).collect(Collectors.toList());
            List<CompilationUnit> rightMethods = methodMap2.get(compilationMethodName).stream().sorted(GreedyMethodMatcher::greaterPeriodComparator).collect(Collectors.toList());
            MatchedMethod matchedMethod = matching.addMatchedMethod(compilationMethodName, leftMethods, rightMethods);
            CollectionsUtil.zipLongest(leftMethods, rightMethods).iterator().forEachRemaining(pair -> {
                if (pair.getLeft() != null && pair.getRight() != null) {
                    matchedMethod.addMatchedCompilationUnit(pair.getLeft(), pair.getRight());
                } else if (pair.getLeft() == null) {
                    matchedMethod.addUnmatchedCompilationUnit(pair.getRight());
                } else {
                    matchedMethod.addUnmatchedCompilationUnit(pair.getLeft());
                }
            });
        }

        analyzeUnmatchedMethods(methodMap1, methodMap2, matching, experiment1);
        analyzeUnmatchedMethods(methodMap2, methodMap1, matching, experiment2);

        return matching;
    }

    private static int greaterPeriodComparator(CompilationUnit a, CompilationUnit b) {
        return Long.compare(b.getPeriod(), a.getPeriod());
    }

    private static void analyzeUnmatchedMethods(EconomicMap<String, List<CompilationUnit>> methodMap1,
                    EconomicMap<String, List<CompilationUnit>> methodMap2,
                    MethodMatching matching,
                    Experiment lhsExperiment) {
        EconomicSet<String> difference = EconomicMapUtil.keySet(methodMap1);
        difference.removeAll(EconomicMapUtil.keySet(methodMap2));
        for (String compilationMethodName : difference) {
            matching.addUnmatchedMethod(compilationMethodName, lhsExperiment, methodMap1.get(compilationMethodName));
        }
    }
}
