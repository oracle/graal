package org.graalvm.bisect;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExperimentId;

import java.util.ArrayList;
import java.util.List;

public class NaiveMethodMatching implements MethodMatching {
    private final ArrayList<MatchedMethod> matchedMethods = new ArrayList<>();
    private final ArrayList<ExtraMethod> extraMethods = new ArrayList<>();

    public static class MatchedMethod {
        public String getCompilationMethodName() {
            return compilationMethodName;
        }

        public ArrayList<MatchedExecutedMethod> getMatchedExecutedMethods() {
            return matchedExecutedMethods;
        }

        public ArrayList<ExtraExecutedMethod> getExtraExecutedMethods() {
            return extraExecutedMethods;
        }

        private final String compilationMethodName;
        private final ArrayList<MatchedExecutedMethod> matchedExecutedMethods = new ArrayList<>();
        private final ArrayList<ExtraExecutedMethod> extraExecutedMethods = new ArrayList<>();

        MatchedMethod(String compilationMethodName) {
            this.compilationMethodName = compilationMethodName;
        }

        public MatchedExecutedMethod addMatchedExecutedMethod(ExecutedMethod method1, ExecutedMethod method2) {
            MatchedExecutedMethod matchedExecutedMethod = new MatchedExecutedMethod(method1, method2);
            matchedExecutedMethods.add(matchedExecutedMethod);
            return matchedExecutedMethod;
        }

        public ExtraExecutedMethod addExtraExecutedMethod(ExecutedMethod method, ExperimentId experimentId) {
            ExtraExecutedMethod extraExecutedMethod = new ExtraExecutedMethod(experimentId, method);
            extraExecutedMethods.add(extraExecutedMethod);
            return extraExecutedMethod;
        }
    }

    static class MatchedExecutedMethod {
        public ExecutedMethod getMethod1() {
            return method1;
        }

        public ExecutedMethod getMethod2() {
            return method2;
        }

        private final ExecutedMethod method1;
        private final ExecutedMethod method2;

        MatchedExecutedMethod(ExecutedMethod method1, ExecutedMethod method2) {
            this.method1 = method1;
            this.method2 = method2;
        }
    }

    static class ExtraExecutedMethod {
        private final ExperimentId experimentId;
        private final ExecutedMethod executedMethod;

        ExtraExecutedMethod(ExperimentId experimentId, ExecutedMethod executedMethod) {
            this.experimentId = experimentId;
            this.executedMethod = executedMethod;
        }

        public ExperimentId getExperimentId() {
            return experimentId;
        }

        public ExecutedMethod getExecutedMethod() {
            return executedMethod;
        }
    }

    static class ExtraMethod {
        public ExperimentId getExperimentId() {
            return experimentId;
        }

        private final ExperimentId experimentId;

        public String getCompilationMethodName() {
            return compilationMethodName;
        }

        private final String compilationMethodName;

        ExtraMethod(ExperimentId experimentId, String compilationMethodName) {
            this.experimentId = experimentId;
            this.compilationMethodName = compilationMethodName;
        }
    }

    public MatchedMethod addMatchedMethod(String compilationMethodName) {
        MatchedMethod matchedMethod = new MatchedMethod(compilationMethodName);
        matchedMethods.add(matchedMethod);
        return matchedMethod;
    }

    public List<MatchedMethod> getMatchedMethods() {
        return matchedMethods;
    }

    public ExtraMethod addExtraMethod(String compilationMethodName, ExperimentId experimentId) {
        ExtraMethod extraMethod = new ExtraMethod(experimentId, compilationMethodName);
        extraMethods.add(extraMethod);
        return extraMethod;
    }

    public List<ExtraMethod> getExtraMethods() {
        return extraMethods;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (MatchedMethod matchedMethod : matchedMethods) {
            sb.append(matchedMethod.compilationMethodName).append('\n');
            for (MatchedExecutedMethod matchedExecutedMethod : matchedMethod.matchedExecutedMethods) {
                sb.append("\tmatched compilations ")
                        .append(matchedExecutedMethod.method1.getCompilationId())
                        .append(", ")
                        .append(matchedExecutedMethod.method2.getCompilationId())
                        .append('\n');
            }
            for (ExtraExecutedMethod extraExecutedMethod : matchedMethod.extraExecutedMethods) {
                sb.append("\textra compilation ")
                        .append(extraExecutedMethod.executedMethod.getCompilationId())
                        .append(" in experiment ")
                        .append(extraExecutedMethod.experimentId)
                        .append('\n');
            }
        }
        for (ExtraMethod extraMethod : extraMethods) {
            sb.append("extra method ")
                    .append(extraMethod.compilationMethodName)
                    .append(" compiled only in experiment ")
                    .append(extraMethod.experimentId)
                    .append('\n');
        }
        return sb.toString();
    }
}
