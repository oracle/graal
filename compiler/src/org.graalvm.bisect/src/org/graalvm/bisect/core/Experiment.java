package org.graalvm.bisect.core;

import java.util.List;
import java.util.Map;

/**
 * A parsed experiment consisting of all graal-compiled methods and metadata.
 */
public interface Experiment {
    ExperimentId getExperimentId();
    String getExecutionId();

    /**
     * Gets the total period of all executed methods including non-graal executions.
     * @return the total period of execution
     */
    long getTotalPeriod();

    /**
     * Sums up the periods of all graal-compiled methods.
     * @return the total period of graal-compiled methods
     */
    long sumGraalPeriod();

    /**
     * Gets the list of all graal-compiled methods, including non-hot methods.
     * @return the list of graal-compiled methods
     */
    List<ExecutedMethod> getExecutedMethods();

    /**
     * Groups hot graal-compiled methods by compilation method name.
     * @see ExecutedMethod#getCompilationMethodName()
     * @return a map of lists of executed methods grouped by compilation method name
     */
    Map<String, List<ExecutedMethod>> groupHotMethodsByName();

    /**
     * Creates a summary of the experiment. Includes the number of methods collected (proftool and optimization log),
     * relative period of graal-compiled methods, the number and relative period of hot methods.
     * @return a summary of the experiment
     */
    String createSummary();
}
