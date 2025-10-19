package com.oracle.svm.hosted.analysis.ai.config;

public enum AbsintMode {
    INTRA_ANALYZE_MAIN_ONLY,
    INTRA_ANALYZE_ALL_INVOKED_METHODS,
    INTER_ANALYZE_MAIN_ONLY,
    INTER_ANALYZE_ALL_ENTRY_POINTS;

    public boolean isIntraProcedural() {
        return this == INTRA_ANALYZE_MAIN_ONLY || this == INTRA_ANALYZE_ALL_INVOKED_METHODS;
    }

    public boolean isInterProcedural() {
        return this == INTER_ANALYZE_MAIN_ONLY || this == INTER_ANALYZE_ALL_ENTRY_POINTS;
    }

    public boolean isMainOnly() {
        return this == INTRA_ANALYZE_MAIN_ONLY || this == INTER_ANALYZE_MAIN_ONLY;
    }
    }
