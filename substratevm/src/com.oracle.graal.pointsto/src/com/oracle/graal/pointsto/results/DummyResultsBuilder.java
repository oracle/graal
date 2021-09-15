package com.oracle.graal.pointsto.results;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.infrastructure.Universe;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.TriState;

public class DummyResultsBuilder extends AbstractAnalysisResultsBuilder {
    public DummyResultsBuilder(BigBang bb, Universe converter) {
        super(bb, converter);
    }

    @Override
    public StaticAnalysisResults makeOrApplyResults(AnalysisMethod method) {
        return StaticAnalysisResults.NO_RESULTS;
    }

    @Override
    public JavaTypeProfile makeTypeProfile(AnalysisField field) {
        return new JavaTypeProfile(TriState.UNKNOWN, 1, new JavaTypeProfile.ProfiledType[0]);
    }
}
