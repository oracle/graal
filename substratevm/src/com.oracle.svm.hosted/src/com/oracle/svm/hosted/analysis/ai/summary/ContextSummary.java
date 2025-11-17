package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Per-context summary for an {@link AnalysisMethod}.
 * <p>
 * A context is identified by a {@link ContextKey} and typically represents one
 * abstract invocation context (e.g., a call string, receiver type, etc.).
 */
public final class ContextSummary<Domain extends AbstractDomain<Domain>> {

    /** The identity of the context this summary belongs to. */
    private final ContextKey contextKey;

    /** Abstract state at the method entry in this context. */
    private Domain entryState;

    /**
     * Abstract state at method exit for this context.
     * Typically, this is the join over the post-states of all return sites.
     */
    private Domain exitState;

    /**
     * Abstract value representing the method's return value in this context.
     * This is the join of the abstract values of all return expressions; can be
     * {@code null} for {@code void} methods or when the analysis does not track
     * return values explicitly.
     */
    private Domain returnValue;

    public ContextSummary(ContextKey contextKey, Domain entryState) {
        this.contextKey = contextKey;
        this.entryState = entryState;
    }

    public ContextKey getContextKey() {
        return contextKey;
    }

    public Domain getEntryState() {
        return entryState;
    }

    public void setEntryState(Domain entryState) {
        this.entryState = entryState;
    }

    public Domain getExitState() {
        return exitState;
    }

    public void setExitState(Domain exitState) {
        this.exitState = exitState;
    }

    public Domain getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Domain returnValue) {
        this.returnValue = returnValue;
    }

    /**
     * Join the provided exit state and return value into this summary.
     * <p>
     * If {@code newExitState} or {@code newReturnValue} are {@code null}, they are ignored.
     * This method returns {@code true} iff either the stored exit state or return value
     * has changed as a result of the update.
     */
    public boolean updateWith(Domain newExitState, Domain newReturnValue) {
        boolean changed = false;

        if (newExitState != null) {
            if (exitState == null) {
                exitState = newExitState.copyOf();
                changed = true;
            } else {
                Domain old = exitState.copyOf();
                exitState.joinWith(newExitState);
                if (!exitState.equals(old)) {
                    changed = true;
                }
            }
        }

        if (newReturnValue != null) {
            if (returnValue == null) {
                returnValue = newReturnValue.copyOf();
                changed = true;
            } else {
                Domain oldRet = returnValue.copyOf();
                returnValue.joinWith(newReturnValue);
                if (!returnValue.equals(oldRet)) {
                    changed = true;
                }
            }
        }

        return changed;
    }

    @Override
    public String toString() {
        return "ContextSummary{" +
                        "contextKey=" + contextKey +
                        ", entryState=" + entryState +
                        ", exitState=" + exitState +
                        ", returnValue=" + returnValue +
                        '}';
    }
}
