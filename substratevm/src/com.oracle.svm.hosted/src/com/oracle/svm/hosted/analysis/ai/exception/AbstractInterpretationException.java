package com.oracle.svm.hosted.analysis.ai.exception;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.graph.Node;

import java.io.Serial;

public class AbstractInterpretationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    AbstractInterpretationException(String message) {
        super(message);
    }

    AbstractInterpretationException(String message, Throwable cause) {
        super(message, cause);
    }

    AbstractInterpretationException(Throwable ex) {
        super(ex);
    }

    public static void exceededWideningThreshold(Node node, AnalysisMethod method) {
        throw new WideningThresholdExceededException(("Widen iteration threshold exceeded for node: " + node + ", in method: " + method.getName() + " please check the provided widening operator/abstract interpreter"));
    }

    public static void analysisMethodGraphNotFound(AnalysisMethod method) {
        throw new AnalysisMethodGraphUnavailableException(("The graph of analysis method: " + method.getQualifiedName() + " could not be found during abstract interpretation"));
    }

    public static void graphVerifyFailed(String description, AnalysisMethod method) {
        throw new GraphVerifyFailedException("[analysisMethod: " + method.getQualifiedName() + "] graph.verify() failed after performing fact applier: " + description);
    }

    public static class WideningThresholdExceededException extends AbstractInterpretationException {
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

    public static class AnalysisMethodGraphUnavailableException extends AbstractInterpretationException {
        @Serial
        private static final long serialVersionUID = 1L;

        AnalysisMethodGraphUnavailableException(String message) {
            super(message);
        }

        AnalysisMethodGraphUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        AnalysisMethodGraphUnavailableException(Throwable ex) {
            super(ex);
        }
    }

    public static class GraphVerifyFailedException extends AbstractInterpretationException {
        @Serial
        private static final long serialVersionUID = 1L;

        GraphVerifyFailedException(String message) {
            super(message);
        }

        GraphVerifyFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        GraphVerifyFailedException(Throwable ex) {
            super(ex);
        }
    }

}
