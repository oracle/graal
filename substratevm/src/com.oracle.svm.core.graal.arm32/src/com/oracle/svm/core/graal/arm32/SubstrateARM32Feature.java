/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * ...
 */

// ファイルパス: substratevm/src/com.oracle.svm.core.graal.arm32/src/com/oracle/svm/core/graal/arm32/SubstrateARM32Feature.java

package com.oracle.svm.core.graal.arm32;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig.ConfigKind;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

@AutomaticallyRegisteredFeature
@Platforms(Platform.ARM32.class)
class SubstrateARM32Feature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {

        ImageSingletons.add(SubstrateRegisterConfigFactory.class, new SubstrateARM32RegisterConfigFactory());

        ImageSingletons.add(ReservedRegisters.class, new ARM32ReservedRegisters());

        if (!SubstrateOptions.useLLVMBackend()) {
            throw GraalError.unimplemented("The ARM 32-bit native backend is currently unimplemented. Use the LLVM backend (-H:CompilerBackend=llvm).");
        }
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
class SubstrateARM32RegisterConfigFactory implements SubstrateRegisterConfigFactory {
    @Override
    public RegisterConfig newRegisterFactory(ConfigKind config, MetaAccessProvider metaAccess, TargetDescription target, Boolean preserveFramePointer) {
        return new SubstrateARM32RegisterConfig(config, metaAccess, target, preserveFramePointer);
    }
}
