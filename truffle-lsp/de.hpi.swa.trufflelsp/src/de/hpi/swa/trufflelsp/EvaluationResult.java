package de.hpi.swa.trufflelsp;

public class EvaluationResult {
    private final Object result;
    private final boolean error;
    private final boolean evaluationDone;

    private EvaluationResult(Object result, boolean error, boolean evaluationDone) {
        this.result = result;
        this.error = error;
        this.evaluationDone = evaluationDone;
    }

    public Object getResult() {
        return result;
    }

    public boolean isError() {
        return error;
    }

    public boolean isEvaluationDone() {
        return evaluationDone;
    }

    public static EvaluationResult createError() {
        return new EvaluationResult(null, true, true);
    }

    public static EvaluationResult createResult(Object result) {
        return new EvaluationResult(result, false, true);
    }

    public static EvaluationResult createEvaluationSectionNotReached() {
        return new EvaluationResult(null, false, false);
    }
}
