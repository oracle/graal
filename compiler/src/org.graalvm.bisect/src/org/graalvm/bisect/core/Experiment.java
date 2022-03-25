package org.graalvm.bisect.core;

import java.util.List;
import java.util.Map;

public interface Experiment {
    List<ExecutedMethod> getExecutedMethods();
    Map<String, List<ExecutedMethod>> getExecutedMethodsByCompilationMethodName();
}
