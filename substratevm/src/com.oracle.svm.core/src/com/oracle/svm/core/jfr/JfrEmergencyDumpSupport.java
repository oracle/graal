package com.oracle.svm.core.jfr;
import com.oracle.svm.core.os.RawFileOperationSupport;
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
    RawFileOperationSupport.RawFileDescriptor chunkPath();
    void onVmError();
    void teardown();
}
