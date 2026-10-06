/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

@Platforms({Platform.AMD64.class, Platform.AARCH64.class, Platform.HOSTED_ONLY.class})
public class StubForeignCallsFeatureBase implements InternalFeature {

    private static final String RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX = "RTC";

    @FunctionalInterface
    protected interface CPUFeatureSetsGetter {
        /**
         * Returns the specified minimum or runtime-checked feature variants for a stub.
         */
        List<EnumSet<?>> get(String name);
    }

    public static final class StubDescriptor {

        private record CPUFeatureStubVariant(ForeignCallSignature signature, SubstrateForeignCallDescriptor variant, EnumSet<?> requiredFeatures) {
        }

        private record StubRegistration(SubstrateForeignCallDescriptor[] rootedStubs, List<SubstrateForeignCallDescriptor> buildtimeStubs, List<CPUFeatureStubVariant> cpuFeatureStubs) {
        }

        private final ForeignCallDescriptor[] foreignCallDescriptors;
        private StubRegistration registration;

        public StubDescriptor(ForeignCallDescriptor foreignCallDescriptor) {
            this(new ForeignCallDescriptor[]{foreignCallDescriptor});
        }

        public StubDescriptor(ForeignCallDescriptor[] foreignCallDescriptors) {
            this.foreignCallDescriptors = foreignCallDescriptors;
        }

        private StubRegistration getRegistration(Class<?> stubsHolder,
                        CPUFeatureSetsGetter minimumCPUFeaturesGetter,
                        CPUFeatureSetsGetter runtimeCPUFeaturesGetter) {
            if (registration == null) {
                registration = mapStubs(stubsHolder, minimumCPUFeaturesGetter, runtimeCPUFeaturesGetter);
            }
            return registration;
        }

        /**
         * Register one feature-unaware baseline stub when the build-time target supports any
         * minimum feature set. With runtime compilation enabled, register preferred feature-aware
         * runtime variants not covered by that baseline. If no baseline can be built, also register
         * all minimum variants. Otherwise, register minimum variants that precede the first
         * build-time-supported minimum, since they may become available at run time and are
         * preferred over the baseline. Runtime variants always precede minimum variants, and
         * annotation order determines preference within each group.
         */
        @SuppressWarnings("unlikely-arg-type")
        private StubRegistration mapStubs(Class<?> stubsHolder,
                        CPUFeatureSetsGetter minimumCPUFeaturesGetter,
                        CPUFeatureSetsGetter runtimeCPUFeaturesGetter) {
            EnumSet<?> buildtimeCPUFeatures = getBuildtimeFeatures();
            boolean isJITCompilationEnabled = RuntimeCompilation.isEnabled();
            List<SubstrateForeignCallDescriptor> allStubs = new ArrayList<>();
            List<CPUFeatureStubVariant> cpuFeatureVariants = new ArrayList<>();
            List<SubstrateForeignCallDescriptor> buildtimeStubs = new ArrayList<>();
            for (ForeignCallDescriptor call : foreignCallDescriptors) {
                List<EnumSet<?>> minimumCPUFeatures = minimumCPUFeaturesGetter.get(call.getName());
                List<EnumSet<?>> runtimeCPUFeatures = runtimeCPUFeaturesGetter.get(call.getName());
                int buildtimeMinimumVariant = -1;
                for (int i = 0; i < minimumCPUFeatures.size(); i++) {
                    if (buildtimeCPUFeatures.containsAll(minimumCPUFeatures.get(i))) {
                        buildtimeMinimumVariant = i;
                        break;
                    }
                }
                if (buildtimeMinimumVariant >= 0) {
                    SubstrateForeignCallDescriptor baseline = SnippetRuntime.findForeignCall(stubsHolder, call.getName(), call.getSideEffect(), call.getKilledLocations());
                    allStubs.add(baseline);
                    buildtimeStubs.add(baseline);
                }
                if (isJITCompilationEnabled) {
                    registerCPUFeatureVariants(stubsHolder, call, runtimeCPUFeatures,
                                    buildtimeCPUFeatures, buildtimeMinimumVariant, allStubs, cpuFeatureVariants);
                }
            }
            return new StubRegistration(allStubs.toArray(new SubstrateForeignCallDescriptor[0]), buildtimeStubs, cpuFeatureVariants);
        }

        @SuppressWarnings("unlikely-arg-type")
        private static void registerCPUFeatureVariants(Class<?> stubsHolder, ForeignCallDescriptor call, List<EnumSet<?>> runtimeCPUFeatures,
                        EnumSet<?> buildtimeCPUFeatures, int buildtimeMinimumVariant,
                        List<SubstrateForeignCallDescriptor> allStubs, List<CPUFeatureStubVariant> cpuFeatureVariants) {
            List<EnumSet<?>> registeredFeatures = new ArrayList<>(runtimeCPUFeatures.size());
            for (int i = 0; i < runtimeCPUFeatures.size(); i++) {
                EnumSet<?> requiredFeatures = runtimeCPUFeatures.get(i);
                if (buildtimeMinimumVariant >= 0 && buildtimeCPUFeatures.containsAll(requiredFeatures)) {
                    /*
                     * The baseline stub is already compiled with the cpu features of this variant.
                     * It must be preferred over every later variant, so preserve annotation order
                     * by letting CPU-feature lookup fall back to the baseline at this point.
                     */
                    break;
                } else if (registeredFeatures.contains(requiredFeatures)) {
                    /*
                     * Runtime alternatives precede minimum alternatives and may overlap, so
                     * retaining the first occurrence preserves preference and avoids duplicate
                     * variants when the same cpu feature set occurs in both.
                     */
                    continue;
                }
                SubstrateForeignCallDescriptor variant = SnippetRuntime.findForeignCall(stubsHolder,
                                rtcVariantName(call.getName(), i), call.getSideEffect(), call.getKilledLocations());
                allStubs.add(variant);
                cpuFeatureVariants.add(new CPUFeatureStubVariant(call.getSignature(), variant, requiredFeatures));
                registeredFeatures.add(requiredFeatures);
            }
        }

        private static String rtcVariantName(String stubName, int i) {
            return stubName + RUNTIME_CHECKED_CPU_FEATURES_NAME_SUFFIX + i;
        }
    }

    private final Class<?> stubsHolder;
    private final CPUFeatureSetsGetter minimumCPUFeaturesGetter;
    private final CPUFeatureSetsGetter runtimeCPUFeaturesGetter;
    private final StubDescriptor[] stubDescriptors;

    protected StubForeignCallsFeatureBase(Class<?> stubsHolder,
                    CPUFeatureSetsGetter minimumCPUFeaturesGetter,
                    CPUFeatureSetsGetter runtimeCPUFeaturesGetter,
                    StubDescriptor[] stubDescriptors) {
        this.stubsHolder = stubsHolder;
        this.minimumCPUFeaturesGetter = minimumCPUFeaturesGetter;
        this.runtimeCPUFeaturesGetter = runtimeCPUFeaturesGetter;
        this.stubDescriptors = stubDescriptors;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* Intrinsic foreign calls emitted during LIR generation are invisible to analysis. */
        for (StubDescriptor stubDescriptor : stubDescriptors) {
            StubDescriptor.StubRegistration registration = stubDescriptor.getRegistration(stubsHolder, minimumCPUFeaturesGetter, runtimeCPUFeaturesGetter);
            registerStubRoots(access, registration.rootedStubs());
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        for (StubDescriptor sd : this.stubDescriptors) {
            StubDescriptor.StubRegistration registration = sd.getRegistration(stubsHolder, minimumCPUFeaturesGetter, runtimeCPUFeaturesGetter);
            for (var stub : registration.buildtimeStubs()) {
                foreignCalls.register(stub);
            }
            for (var stub : registration.cpuFeatureStubs()) {
                foreignCalls.registerForeignCallWithCPUFeatures(stub.signature(), stub.variant(), stub.requiredFeatures());
            }
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
    private static void registerStubRoots(BeforeAnalysisAccess access, SubstrateForeignCallDescriptor[] foreignCalls) {
        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess metaAccess = impl.getMetaAccess();
        for (SubstrateForeignCallDescriptor descriptor : foreignCalls) {
            AnalysisMethod method = (AnalysisMethod) descriptor.findMethod(metaAccess);
            impl.registerAsRoot(method, true, "Foreign call stubs, registered in " + StubForeignCallsFeatureBase.class);
        }
    }

    private static EnumSet<?> getBuildtimeFeatures() {
        Architecture arch = ImageSingletons.lookup(SubstrateTarget.class).arch;
        if (arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures();
        }
        if (arch instanceof AArch64) {
            return ((AArch64) arch).getFeatures();
        }
        throw GraalError.unsupportedArchitecture(arch); // ExcludeFromJacocoGeneratedReport
    }
}
