package org.graalvm.bisect;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.util.ListUtil;
import org.graalvm.bisect.util.Pair;
import org.graalvm.bisect.util.SetUtil;

import java.util.List;
import java.util.Map;

public class NaiveMethodMatcher implements MethodMatcher {

    @Override
    public NaiveMethodMatching match(Experiment experiment1, Experiment experiment2) {
        Map<String, List<ExecutedMethod>> methodMap1 = experiment1.getExecutedMethodsByCompilationMethodName();
        Map<String, List<ExecutedMethod>> methodMap2 = experiment2.getExecutedMethodsByCompilationMethodName();
        NaiveMethodMatching matching = new NaiveMethodMatching();

        for (String compilationMethodName : SetUtil.intersection(methodMap1.keySet(), methodMap2.keySet())) {
            NaiveMethodMatching.MatchedMethod matchedMethod = matching.addMatchedMethod(compilationMethodName);
            List<Pair<ExecutedMethod>> methodPairs = ListUtil.zipLongest(
                    methodMap1.get(compilationMethodName),
                    methodMap2.get(compilationMethodName));
            for (Pair<ExecutedMethod> pair : methodPairs) {
                if (pair.bothNotNull()) {
                    matchedMethod.addMatchedExecutedMethod(pair.getLhs(), pair.getRhs());
                } else if (pair.getLhs() == null) {
                    matchedMethod.addExtraExecutedMethod(pair.getRhs(), ExperimentId.TWO);
                } else {
                    matchedMethod.addExtraExecutedMethod(pair.getLhs(), ExperimentId.ONE);
                }
            }
        }

        analyzeExtraMethods(methodMap1, methodMap2, matching, ExperimentId.ONE);
        analyzeExtraMethods(methodMap2, methodMap1, matching, ExperimentId.TWO);

        return matching;
    }

    private void analyzeExtraMethods(Map<String, List<ExecutedMethod>> methodMap1,
                                     Map<String, List<ExecutedMethod>> methodMap2,
                                     NaiveMethodMatching matching,
                                     ExperimentId lhsExperimentId) {
        for (String compilationMethodName : SetUtil.difference(methodMap1.keySet(), methodMap2.keySet())) {
            matching.addExtraMethod(compilationMethodName, lhsExperimentId);
        }
    }
}
