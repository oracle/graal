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
package org.graalvm.bisect.matching.method;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.util.IteratorUtil;
import org.graalvm.bisect.util.SetUtil;

import java.util.List;
import java.util.Map;

/**
 * Matches methods of two experiments by compilation method names and then greedily matches their respective
 * compilations by descending execution periods.
 */
public class GreedyMethodMatcher implements MethodMatcher {
    /**
     * Matches pairs of methods by their signature and then greedily matches their respective compilation (executions).
     * For a given Java method (in both experiments), the hottest executions (with the longest execution period) are
     * matched first. Then again the pair of the hottest execution is paired until no more pairs are left. Only hot
     * executions and methods that have a hot execution are considered. Returns an object describing the pairs of
     * matched methods, matched executed methods and also the list of (executed) methods that do not have a pair - extra
     * methods.
     * @param experiment1 the first experiment
     * @param experiment2 the second experiment
     * @return the description of the computed matching
     * @see Experiment#groupHotMethodsByName()
     */
    @Override
    public MethodMatching match(Experiment experiment1, Experiment experiment2) {
        Map<String, List<ExecutedMethod>> methodMap1 = experiment1.groupHotMethodsByName();
        Map<String, List<ExecutedMethod>> methodMap2 = experiment2.groupHotMethodsByName();
        MethodMatchingImpl matching = new MethodMatchingImpl();

        for (String compilationMethodName : SetUtil.intersection(methodMap1.keySet(), methodMap2.keySet())) {
            MatchedMethod matchedMethod = matching.addMatchedMethod(compilationMethodName);
            IteratorUtil.zipLongest(
                    methodMap1.get(compilationMethodName)
                            .stream()
                            .sorted(GreedyMethodMatcher::greaterPeriodComparator)
                            .iterator(),
                    methodMap2.get(compilationMethodName)
                            .stream()
                            .sorted(GreedyMethodMatcher::greaterPeriodComparator)
                            .iterator()
            ).forEachRemaining(pair -> {
                if (pair.bothNotNull()) {
                    matchedMethod.addMatchedExecutedMethod(pair.getLhs(), pair.getRhs());
                } else if (pair.getLhs() == null) {
                    matchedMethod.addExtraExecutedMethod(pair.getRhs());
                } else {
                    matchedMethod.addExtraExecutedMethod(pair.getLhs());
                }
            });
        }

        analyzeExtraMethods(methodMap1, methodMap2, matching, experiment1.getExperimentId());
        analyzeExtraMethods(methodMap2, methodMap1, matching, experiment2.getExperimentId());

        return matching;
    }

    private static int greaterPeriodComparator(ExecutedMethod a, ExecutedMethod b) {
        return Long.compare(b.getPeriod(), a.getPeriod());
    }

    private void analyzeExtraMethods(Map<String, List<ExecutedMethod>> methodMap1,
                                     Map<String, List<ExecutedMethod>> methodMap2,
                                     MethodMatchingImpl matching,
                                     ExperimentId lhsExperimentId) {
        for (String compilationMethodName : SetUtil.difference(methodMap1.keySet(), methodMap2.keySet())) {
            matching.addExtraMethod(compilationMethodName, lhsExperimentId);
        }
    }
}
