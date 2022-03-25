package org.graalvm.bisect;

import org.graalvm.bisect.core.Experiment;

public interface MethodMatcher {
    MethodMatching match(Experiment experiment1, Experiment experiment2);
}
