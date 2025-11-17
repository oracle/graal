package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.graph.Node;

import java.io.Serial;

public class AbsintException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AbsintException(String message) {
        super(message);
    }

    AbsintException(String message, Throwable cause) {
        super(message, cause);
    }

    AbsintException(Throwable ex) {
        super(ex);
    }

    public static WideningThresholdExceededException exceededWideningThreshold(Node node, AnalysisMethod method) {
        throw new WideningThresholdExceededException(("Widen iteration threshold exceeded for node: " + node + ", in method: " + method.getName() + " please check the provided widening operator/abstract interpreter"));
    }

    public static class WideningThresholdExceededException extends AbsintException {

        @Serial
        private static final long serialVersionUID = 1L;

        WideningThresholdExceededException(String message) {
            super(message);
        }

        WideningThresholdExceededException(String message, Throwable cause) {
            super(message, cause);
        }

        WideningThresholdExceededException(Throwable ex) {
            super(ex);
        }
    }

}
