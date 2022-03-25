package org.graalvm.bisect.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.graalvm.bisect.NaiveMethodMatcher;
import org.graalvm.bisect.NaiveMethodMatching;
import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExecutedMethodImpl;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentImpl;
import org.junit.Test;

public class NaiveMethodMatcherTest {
    @Test
    public void testMethodMatcher() {
        ExecutedMethodImpl foo1 = new ExecutedMethodImpl("1000", "foo", List.of(),0.1);
        ExecutedMethodImpl foo2 = new ExecutedMethodImpl("2000", "foo", List.of(),0.2);
        ExecutedMethodImpl bar1 = new ExecutedMethodImpl("3000", "bar", List.of(),0.3);
        List<ExecutedMethod> methods1 = List.of(foo1, foo2, bar1);
        Experiment experiment1 = new ExperimentImpl(methods1);

        ExecutedMethodImpl foo3 = new ExecutedMethodImpl("100", "foo", List.of(),0.1);
        ExecutedMethodImpl bar2 = new ExecutedMethodImpl("200", "bar", List.of(),0.3);
        ExecutedMethodImpl baz = new ExecutedMethodImpl("300", "baz", List.of(), 0.05);
        List<ExecutedMethod> methods2 = List.of(foo3, bar2, baz);
        Experiment experiment2 = new ExperimentImpl(methods2);

        NaiveMethodMatcher matcher = new NaiveMethodMatcher();
        NaiveMethodMatching matching = matcher.match(experiment1, experiment2);

        assertEquals(2, matching.getMatchedMethods().size());
        assertEquals(1, matching.getExtraMethods().size());
    }

    @Test
    public void testMultipleExecutions() {
        ExecutedMethodImpl foo1 = new ExecutedMethodImpl("1000", "foo", List.of(),0.1);
        ExecutedMethodImpl foo2 = new ExecutedMethodImpl("2000", "foo", List.of(),0.2);
        List<ExecutedMethod> methods1 = List.of(foo1, foo2);
        Experiment experiment1 = new ExperimentImpl(methods1);

        ExecutedMethodImpl foo3 = new ExecutedMethodImpl("100", "foo", List.of(),0.1);
        ExecutedMethodImpl bar4 = new ExecutedMethodImpl("200", "foo", List.of(),0.3);
        List<ExecutedMethod> methods2 = List.of(foo3, bar4);
        Experiment experiment2 = new ExperimentImpl(methods2);

        NaiveMethodMatcher matcher = new NaiveMethodMatcher();
        NaiveMethodMatching matching = matcher.match(experiment1, experiment2);

        assertEquals(1, matching.getMatchedMethods().size());
        NaiveMethodMatching.MatchedMethod matchedMethod = matching.getMatchedMethods().get(0);
        assertEquals(2, matchedMethod.getMatchedExecutedMethods().size());
    }
}
