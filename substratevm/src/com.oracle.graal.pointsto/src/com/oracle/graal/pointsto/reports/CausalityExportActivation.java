package com.oracle.graal.pointsto.reports;

import com.oracle.graal.pointsto.reports.causality.Impl;
import com.oracle.graal.pointsto.reports.causality.TypeflowImpl;
import com.oracle.graal.pointsto.util.AnalysisError;

public enum CausalityExportActivation {
    DISABLED,
    ENABLED_WITHOUT_TYPEFLOW,
    ENABLED;

    private static CausalityExportActivation requestedLevel = DISABLED;

    private static final class InitializationOnDemandHolder {
        private static final CausalityExportActivation frozenLevel = CausalityExportActivation.requestedLevel;
        private static final CausalityExport.AbstractImpl instance = switch(CausalityExportActivation.requestedLevel) {
            case ENABLED -> TypeflowImpl.createWithTypeflowTracking();
            case ENABLED_WITHOUT_TYPEFLOW -> Impl.create();
            case DISABLED -> new CausalityExport.AbstractImpl();
        };
    }

    /**
     * Must be called before any usage of {@link #get()}
     */
    public static void activate(CausalityExportActivation level) {
        requestedLevel = level;
        if (level != InitializationOnDemandHolder.frozenLevel) {
            throw AnalysisError.shouldNotReachHere("Causality Export must have been activated before the first usage of CausalityExport");
        }
    }

    public static CausalityExportActivation getActivationStatus() {
        return InitializationOnDemandHolder.frozenLevel;
    }

    static CausalityExport.AbstractImpl get() {
        return InitializationOnDemandHolder.instance;
    }
}
