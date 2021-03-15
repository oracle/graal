package com.oracle.truffle.espresso;

import com.oracle.truffle.espresso.substitutions.Target_java_lang_ref_Reference;
import org.graalvm.nativeimage.hosted.Feature;

public final class EspressoFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        Target_java_lang_ref_Reference.ensureInitialized();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        Target_java_lang_ref_Reference.ensureInitialized();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Target_java_lang_ref_Reference.ensureInitialized();
    }
}
