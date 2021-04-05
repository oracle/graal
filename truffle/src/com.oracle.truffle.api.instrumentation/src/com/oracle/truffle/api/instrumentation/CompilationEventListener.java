package com.oracle.truffle.api.instrumentation;

public interface CompilationEventListener {

    interface CompilationInfo {

        long getId();
        String getRootSimpleName();
        String getRootQualifiedName();
        int getTier();
        boolean isClone();
        int getTruffleNodeCount();

    }

    interface CompilationSuccessInfo extends CompilationInfo {

        long getCodeSize();
        long getGraalNodeCount();

    }

    interface CompilationAbortInfo extends CompilationInfo {

        boolean isTemporary();
        boolean isPermanent();
        boolean isFatal();
        String getReason();

    }

    void onQueued(CompilationInfo info);
    void onDequeued(CompilationAbortInfo info);
    void onStart(CompilationInfo info);
    void onSuccess(CompilationSuccessInfo info);
    void onFailed(CompilationAbortInfo info);
    void onInvalidate(CompilationAbortInfo info);

}
