package com.oracle.svm.hosted.c.libc;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.CosmoLibC;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.hosted.image.AbstractImage;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import org.graalvm.nativeimage.Platform;

import java.util.List;

public class HostedCosmoLibC extends CosmoLibC implements HostedLibCBase{
    @Override
    public String getTargetCompiler() {
        if (Platform.includedIn(Platform.AMD64.class)) {
            return "x86_64-unknown-cosmo-cc";
        } else if (Platform.includedIn(Platform.AARCH64.class)) {
            return "aarch64-unknown-cosmo-cc";
        }
        return "gcc";
    }

    @Override
    public List<String> getAdditionalQueryCodeCompilerOptions() {
        return List.of();
    }

    @Override
    public List<String> getAdditionalLinkerOptions(AbstractImage.NativeImageKind imageKind) {
        return List.of("-no-pie");
    }

    @Override
    public boolean requiresLibCSpecificStaticJDKLibraries() {
        return true;
    }
}
