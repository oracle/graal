package com.oracle.svm.core.debug;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.InstalledCodeObserverSupportFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.List;

@AutomaticallyRegisteredFeature
public class SubstrateDebugInfoFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Platform.includedIn(Platform.LINUX.class) && SubstrateOptions.useDebugInfoGeneration() && SubstrateOptions.RuntimeDebugInfo.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(InstalledCodeObserverSupportFeature.class);
    }

    @Override
    public void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
        // This is called at image build-time -> the factory then creates a RuntimeDebugInfoProvider at runtime
        ImageSingletons.lookup(InstalledCodeObserverSupport.class).addObserverFactory(new SubstrateDebugInfoInstaller.Factory(runtimeConfig.getProviders().getMetaAccess(), runtimeConfig));
        ImageSingletons.add(SubstrateDebugInfoInstaller.Accessor.class, new SubstrateDebugInfoInstaller.Accessor());
    }
}
