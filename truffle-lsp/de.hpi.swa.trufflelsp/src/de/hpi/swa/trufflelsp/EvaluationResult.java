package de.hpi.swa.trufflelsp;

public class EvaluationResult {
    private final Object result;
    private final boolean error;
    private final boolean evaluationDone;
    private final boolean unknownEcutionTarget;

    private EvaluationResult(Object result, boolean error, boolean evaluationDone, boolean unknownEcutionTarget) {
        this.result = result;
        this.error = error;
        this.evaluationDone = evaluationDone;
        this.unknownEcutionTarget = unknownEcutionTarget;
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

    public boolean isUnknownEcutionTarget() {
        return unknownEcutionTarget;
    }

    public static EvaluationResult createError() {
        return new EvaluationResult(null, true, true, false);
    }

    public static EvaluationResult createResult(Object result) {
        return new EvaluationResult(result, false, true, false);
    }

    public static EvaluationResult createEvaluationSectionNotReached() {
        return new EvaluationResult(null, false, false, false);
    }

    public static EvaluationResult createUnknownExecutionTarget() {
        return new EvaluationResult(null, false, false, true);
    }
}
