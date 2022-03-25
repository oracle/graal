package org.graalvm.bisect;

import java.io.File;
import java.io.IOException;

import org.graalvm.bisect.core.Experiment;

public class ProfBisect {
    public static void main(String[] args) throws IOException {
        assert args.length == 4;

        ExperimentParser parser1 = new ExperimentParser(new File(args[0]), new File(args[1]).listFiles());
        Experiment experiment1 = parser1.parse();

        ExperimentParser parser2 = new ExperimentParser(new File(args[2]), new File(args[3]).listFiles());
        Experiment experiment2 = parser2.parse();

        NaiveMethodMatcher matcher = new NaiveMethodMatcher();
        NaiveMethodMatching matching = matcher.match(experiment1, experiment2);
        System.out.println(matching);
        System.out.println();

        NaiveOptimizationMatcher optimizationMatcher = new NaiveOptimizationMatcher();
        for (NaiveMethodMatching.MatchedMethod matchedMethod : matching.getMatchedMethods()) {
            for (NaiveMethodMatching.MatchedExecutedMethod matchedExecutedMethod : matchedMethod.getMatchedExecutedMethods()) {
                NaiveOptimizationMatching optimizationMatching = optimizationMatcher.match(
                       matchedExecutedMethod.getMethod1().getOptimizations(),
                       matchedExecutedMethod.getMethod2().getOptimizations()
                );
                for (NaiveOptimizationMatching.ExtraOptimization optimization : optimizationMatching.getExtraOptimizations()) {
                    System.out.println(
                           "experiment " + optimization.getExperimentId() +
                           " has an extra " + optimization.getOptimization().getDescription() +
                           " in " + matchedExecutedMethod.getMethod1().getCompilationMethodName() +
                           " at bci " + optimization.getOptimization().getBCI());
                }
            }
        }
    }
}
