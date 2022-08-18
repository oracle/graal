/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.graal.stubs;

import java.util.ArrayList;
import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.cpufeature.Stubs;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.graal.InternalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public class StubForeignCallsFeatureBase implements InternalFeature {

    static final class StubDescriptor {

        private final ForeignCallDescriptor[] foreignCallDescriptors;
        private final boolean isReexecutable;
        private final EnumSet<?> minimumRequiredFeatures;
        private final EnumSet<?> runtimeCheckedCPUFeatures;
        private SnippetRuntime.SubstrateForeignCallDescriptor[] stubs;

        StubDescriptor(ForeignCallDescriptor foreignCallDescriptors, boolean isReexecutable, EnumSet<?> minimumRequiredFeatures, EnumSet<?> runtimeCheckedCPUFeatures) {
            this(new ForeignCallDescriptor[]{foreignCallDescriptors}, isReexecutable, minimumRequiredFeatures, runtimeCheckedCPUFeatures);
        }

        StubDescriptor(ForeignCallDescriptor[] foreignCallDescriptors, boolean isReexecutable, EnumSet<?> minimumRequiredFeatures, EnumSet<?> runtimeCheckedCPUFeatures) {
            this.foreignCallDescriptors = foreignCallDescriptors;
            this.isReexecutable = isReexecutable;
            this.minimumRequiredFeatures = minimumRequiredFeatures;
            this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
        }

        private SnippetRuntime.SubstrateForeignCallDescriptor[] getStubs() {
            if (stubs == null) {
                stubs = mapStubs();
            }
            return stubs;
        }

        private SnippetRuntime.SubstrateForeignCallDescriptor[] mapStubs() {
            EnumSet<?> buildtimeCPUFeatures = getBuildtimeFeatures();
            boolean generateBaseline = buildtimeCPUFeatures.containsAll(minimumRequiredFeatures);
            // Currently we only support AMD64, see CPUFeatureRegionEnterNode.generate
            boolean generateRuntimeChecked = !buildtimeCPUFeatures.containsAll(runtimeCheckedCPUFeatures) && DeoptimizationSupport.enabled() && Platform.includedIn(Platform.AMD64.class);
            ArrayList<SnippetRuntime.SubstrateForeignCallDescriptor> ret = new ArrayList<>();
            for (ForeignCallDescriptor call : foreignCallDescriptors) {
                if (generateBaseline) {
                    ret.add(SnippetRuntime.findForeignCall(SVMIntrinsicStubsGen.class, call.getName(), isReexecutable));
                }
                if (generateRuntimeChecked) {
                    ret.add(SnippetRuntime.findForeignCall(SVMIntrinsicStubsGen.class, call.getName() + Stubs.RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX, isReexecutable));
                }
            }
            return ret.toArray(new SnippetRuntime.SubstrateForeignCallDescriptor[0]);
        }

    }

    private final StubDescriptor[] stubDescriptors;

    protected StubForeignCallsFeatureBase(StubDescriptor[] stubDescriptors) {
        this.stubDescriptors = stubDescriptors;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (StubDescriptor sd : stubDescriptors) {
            registerStubRoots(access, sd.getStubs());
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        for (StubDescriptor sd : this.stubDescriptors) {
            foreignCalls.register(sd.getStubs());
        }
    }

    /**
     * We register intrinsic stubs for compilation unconditionally, even though they may be unused
     * in images where JIT compilation is disabled, because the foreign calls to these stubs are
     * currently generated by custom nodes (such as {@link ArrayRegionEqualsNode}) at LIR generation
     * time, which cannot be tracked properly by the analysis. These custom nodes are used because
     * we want to be able to constant-fold the entire operation if all inputs are constant, and only
     * emit the foreign call if we are not able to do so.
     *
     * The resulting increase in AOT-compiled methods is negligible, and even decreases the overall
     * size of e.g. the {@code helloworld} image by 3KB, since with these stubs we no longer have to
     * inline intrinsics. Nevertheless, we should take care not to generate excessive amounts of
     * intrinsic variants to avoid unnecessary image size overhead in the future.
     */
    private static void registerStubRoots(BeforeAnalysisAccess access, SnippetRuntime.SubstrateForeignCallDescriptor[] foreignCalls) {
        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess metaAccess = impl.getMetaAccess();
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : foreignCalls) {
            AnalysisMethod method = (AnalysisMethod) descriptor.findMethod(metaAccess);
            impl.registerAsRoot(method, true);
        }
    }

    private static EnumSet<?> getBuildtimeFeatures() {
        Architecture arch = ImageSingletons.lookup(SubstrateTargetDescription.class).arch;
        if (arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures();
        }
        if (arch instanceof AArch64) {
            return ((AArch64) arch).getFeatures();
        }
        throw GraalError.shouldNotReachHere();
    }
}
