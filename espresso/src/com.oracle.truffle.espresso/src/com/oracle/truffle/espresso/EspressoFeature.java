package com.oracle.truffle.espresso;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.truffle.espresso.substitutions.FinalizationSupport;

public final class EspressoFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FinalizationSupport.ensureInitialized();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        FinalizationSupport.ensureInitialized();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FinalizationSupport.ensureInitialized();
    }
}
