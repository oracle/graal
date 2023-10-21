/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.LinkToNativeSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.LinkToNativeSupportImpl;
import com.oracle.svm.core.foreign.RuntimeSystemLookup;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsFeature implements InternalFeature {
    private static boolean isPreviewEnabled() {
        try {
            return (boolean) ReflectionUtil.lookupMethod(
                            ReflectionUtil.lookupClass(false, "jdk.internal.misc.PreviewFeatures"),
                            "isEnabled").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static final int FIRST_SUPPORTED_PREVIEW = 21;
    private static final int FIRST_SUPPORTED_NON_PREVIEW = Integer.MAX_VALUE - 1; // TBD

    private static final Map<String, String[]> REQUIRES_CONCEALED = Map.of(
                    "jdk.internal.vm.ci", new String[]{"jdk.vm.ci.code", "jdk.vm.ci.meta", "jdk.vm.ci.amd64"},
                    "java.base", new String[]{
                                    "jdk.internal.foreign",
                                    "jdk.internal.foreign.abi",
                                    "jdk.internal.foreign.abi.x64",
                                    "jdk.internal.foreign.abi.x64.sysv",
                                    "jdk.internal.foreign.abi.x64.windows"});

    private boolean sealed = false;
    private final RuntimeForeignAccessSupportImpl accessSupport = new RuntimeForeignAccessSupportImpl();

    private final Set<Pair<FunctionDescriptor, Linker.Option[]>> registeredDowncalls = ConcurrentHashMap.newKeySet();
    private int downcallCount = 0;

    @Fold
    public static ForeignFunctionsFeature singleton() {
        return ImageSingletons.lookup(ForeignFunctionsFeature.class);
    }

    private void checkNotSealed() {
        UserError.guarantee(!sealed, "Registration of foreign functions was closed.");
    }

    private class RuntimeForeignAccessSupportImpl extends ConditionalConfigurationRegistry implements StronglyTypedRuntimeForeignAccessSupport {
        @Override
        public void registerForDowncall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            registerConditionalConfiguration(condition, () -> registeredDowncalls.add(Pair.create(desc, options)));
        }
    }

    ForeignFunctionsFeature() {
        /*
         * We add these exports systematically in the constructor, as to avoid access errors from
         * plugins when the feature is disabled in the config.
         */
        for (var modulePackages : REQUIRES_CONCEALED.entrySet()) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, ForeignFunctionsFeature.class, false, modulePackages.getKey(), modulePackages.getValue());
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateUtil.getArchitectureName().contains("amd64") && !SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        assert (JavaVersionUtil.JAVA_SPEC >= FIRST_SUPPORTED_PREVIEW && isPreviewEnabled()) ||
                        JavaVersionUtil.JAVA_SPEC >= FIRST_SUPPORTED_NON_PREVIEW;

        UserError.guarantee(!SubstrateOptions.useLLVMBackend(), "Foreign functions interface is in use, but is not supported together with the LLVM backend.");

        ImageSingletons.add(AbiUtils.class, AbiUtils.create());
        ImageSingletons.add(ForeignFunctionsRuntime.class, new ForeignFunctionsRuntime());
        ImageSingletons.add(RuntimeForeignAccessSupport.class, accessSupport);
        ImageSingletons.add(LinkToNativeSupport.class, new LinkToNativeSupportImpl());

        var access = (FeatureImpl.DuringSetupAccessImpl) a;
        ConfigurationParser parser = new ForeignFunctionsConfigurationParser(accessSupport);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "panama foreign",
                        ConfigurationFiles.Options.ForeignConfigurationFiles, ConfigurationFiles.Options.ForeignResources, ConfigurationFile.FOREIGN.getFileName());
    }

    private void createDowncallStubs(FeatureImpl.BeforeAnalysisAccessImpl access) {
        this.downcallCount = createStubs(
                        registeredDowncalls,
                        access,
                        AbiUtils.singleton()::makeNativeEntrypoint,
                        n -> new DowncallStub(n, access.getMetaAccess().getWrapped()),
                        ForeignFunctionsRuntime.singleton()::addDowncallStubPointer);
    }

    private <S> int createStubs(
                    Set<Pair<FunctionDescriptor, Linker.Option[]>> source,
                    FeatureImpl.BeforeAnalysisAccessImpl access,
                    BiFunction<FunctionDescriptor, Linker.Option[], S> stubGenerator,
                    Function<S, ResolvedJavaMethod> wrapper,
                    BiConsumer<S, CFunctionPointer> register) {

        Map<S, ResolvedJavaMethod> created = new HashMap<>();

        for (Pair<FunctionDescriptor, Linker.Option[]> fdOptionsPair : source) {
            S nepi = stubGenerator.apply(fdOptionsPair.getLeft(), fdOptionsPair.getRight());

            if (!created.containsKey(nepi)) {
                ResolvedJavaMethod stub = wrapper.apply(nepi);
                AnalysisMethod analysisStub = access.getUniverse().lookup(stub);
                access.getBigBang().addRootMethod(analysisStub, false, "Foreign stub, registered in " + ForeignFunctionsFeature.class);
                created.put(nepi, analysisStub);
                register.accept(nepi, new MethodPointer(analysisStub));
            }
        }
        source.clear();

        return created.size();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        sealed = true;

        AbiUtils.singleton().checkLibrarySupport();

        /*
         * Specializing an adapter would define a new class at runtime, which is not allowed in
         * SubstrateVM
         */
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(false, "jdk.internal.foreign.abi.DowncallLinker"),
                                        "USE_SPEC"),
                        (receiver, originalValue) -> false);

        RuntimeClassInitialization.initializeAtRunTime(RuntimeSystemLookup.class);
        access.registerAsRoot(ReflectionUtil.lookupMethod(ForeignFunctionsRuntime.class, "captureCallState", int.class, CIntPointer.class), false,
                        "Runtime support, registered in " + ForeignFunctionsFeature.class);

        createDowncallStubs(access);
        ProgressReporter.singleton().setForeignFunctionsInfo(getCreatedDowncallStubsCount());
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(ForeignFunctionsRuntime.CAPTURE_CALL_STATE);
    }

    /* Testing interface */

    public int getCreatedDowncallStubsCount() {
        assert sealed;
        return this.downcallCount;
    }
}
