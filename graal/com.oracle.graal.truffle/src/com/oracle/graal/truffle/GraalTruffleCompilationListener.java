package com.oracle.graal.truffle;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.truffle.api.*;

public interface GraalTruffleCompilationListener {

    void notifyCompilationQueued(OptimizedCallTarget target);

    void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason);

    void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t);

    void notifyCompilationSplit(OptimizedDirectCallNode callNode);

    void notifyCompilationStarted(OptimizedCallTarget target);

    void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph);

    void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result);

    void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason);

    void notifyShutdown(TruffleRuntime runtime);
}
