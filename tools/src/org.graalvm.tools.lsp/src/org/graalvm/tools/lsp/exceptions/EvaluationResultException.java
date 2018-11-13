package org.graalvm.tools.lsp.exceptions;

public class EvaluationResultException extends ThreadDeath {

    private static final long serialVersionUID = 4334314735613697864L;
    private final Object result;
    private final boolean isError;

    public EvaluationResultException(Object result) {
        this(result, false);
    }

    public EvaluationResultException(Object result, boolean isError) {
        this.result = result;
        this.isError = isError;
    }

    public Object getResult() {
        return result;
    }

    public boolean isError() {
        return isError;
    }
}
