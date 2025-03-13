package com.oracle.svm.core.jfr;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.graal.compiler.api.replacements.Fold;
public interface JfrEmergencyDumpSupport {
    @Fold
    static boolean isPresent() {
        return ImageSingletons.contains(JfrEmergencyDumpSupport.class);
    }

    @Fold
    static JfrEmergencyDumpSupport singleton() {
        return ImageSingletons.lookup(JfrEmergencyDumpSupport.class);
    }

    void initialize();
    void setRepositoryLocation(String dirText);
    void setDumpPath(String dumpPathText);
    String getDumpPath();
    void onVmError();
    void teardown();
}
