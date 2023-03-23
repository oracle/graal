package com.oracle.svm.hosted.panama;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Arrays;
import java.util.List;

import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.PanamaForeignConfigurationParser;
import com.oracle.svm.core.panama.downcalls.PanamaDowncallsSupport;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.reflect.ReflectionFeature;

import jdk.vm.ci.meta.MetaAccessProvider;

public class PanamaForeignFunctionsDowncallFeature implements Feature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(ReflectionFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) arg;

        PanamaDowncallsSupport.create();

        ConfigurationParserUtils.parseAndRegisterConfigurations(new PanamaForeignConfigurationParser(), access.getImageClassLoader(), "panama foreign",
                ConfigurationFiles.Options.PanamaForeignConfigurationFiles, ConfigurationFiles.Options.PanamaForeignResources, ConfigurationFile.PANAMA_FOREIGN.getFileName());
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        // Implement doLinkToNative
        config.registerSubstitutionProcessor(new PanamaSubstitutionProcessor(metaAccess));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        Feature.super.beforeAnalysis(a);
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;

        // Everybody is using @Alias, even though the documentation says to use this instead...
        try {
            // Specializing the lambda form would define a new class, which is not allowed in SubstrateVM
            access.registerFieldValueTransformer(
                    Class.forName("jdk.internal.foreign.abi.DowncallLinker").getDeclaredField("USE_SPEC"),
                    (receiver, originalValue) -> false
            );
        } catch (NoSuchFieldException | ClassNotFoundException ignore) {
            throw shouldNotReachHere("jdk.internal.foreign.abi.DowncallLinker.USE_SPEC should be defined");
        }
    }

    public void afterAnalysis(AfterAnalysisAccess access) {
        ProgressReporter.singleton().setPanamaInfo(PanamaDowncallsSupport.singleton().stubCount());
    }
}
