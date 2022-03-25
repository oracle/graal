package org.graalvm.bisect.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExperimentImpl implements Experiment {
    private final List<ExecutedMethod> executedMethods;

    public ExperimentImpl(List<ExecutedMethod> executedMethods) {
        this.executedMethods = executedMethods;
    }

    @Override
    public List<ExecutedMethod> getExecutedMethods() {
        return executedMethods;
    }

    @Override
    public Map<String, List<ExecutedMethod>> getExecutedMethodsByCompilationMethodName() {
        Map<String, List<ExecutedMethod>> map = new HashMap<>();
        for (ExecutedMethod method : executedMethods) {
            List<ExecutedMethod> methods = map.computeIfAbsent(method.getCompilationMethodName(), k -> new ArrayList<>());
            methods.add(method);
        }
        return map;
    }
}
