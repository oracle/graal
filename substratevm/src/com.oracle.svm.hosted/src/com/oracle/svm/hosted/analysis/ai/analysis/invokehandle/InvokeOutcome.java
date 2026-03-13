package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.Map;

/**
 * A normalized result produced by the invoke handler for a call from the interpreter.
 */
public final class InvokeOutcome<Domain extends AbstractDomain<Domain>> {
    public enum Kind { ERROR, SUMMARY }

    private final Kind kind;
    private final Domain returnValue; /* may be null for void */
    private final Domain heapDelta; /* may be null */
    private final Map<String, Object> placeholderMapping; // mapping from placeholders to caller roots (access paths), typed loosely

    private InvokeOutcome(Kind kind, Domain returnValue, Domain heapDelta, Map<String, Object> placeholderMapping) {
        this.kind = kind;
        this.returnValue = returnValue;
        this.heapDelta = heapDelta;
        this.placeholderMapping = placeholderMapping;
    }

    public static <D extends AbstractDomain<D>> InvokeOutcome<D> error() {
        return new InvokeOutcome<>(Kind.ERROR, null, null, null);
    }

    public static <D extends AbstractDomain<D>> InvokeOutcome<D> summary(D returnValue, D heapDelta, Map<String, Object> placeholderMapping) {
        return new InvokeOutcome<>(Kind.SUMMARY, returnValue, heapDelta, placeholderMapping);
    }

    public Kind getKind() {
        return kind;
    }

    public Domain getReturnValue() {
        return returnValue;
    }

    public Domain getHeapDelta() {
        return heapDelta;
    }

    public Map<String, Object> getPlaceholderMapping() {
        return placeholderMapping;
    }
}

