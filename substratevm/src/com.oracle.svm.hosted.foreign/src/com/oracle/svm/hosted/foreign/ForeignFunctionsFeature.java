/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.serviceprovider.JavaVersionUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
@Platforms(Platform.HOSTED_ONLY.class)
public class ForeignFunctionsFeature implements InternalFeature {
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
    private int downcallCount = -1;

    private final Set<Pair<FunctionDescriptor, Linker.Option[]>> registeredUpcalls = ConcurrentHashMap.newKeySet();
    private int upcallCount = -1;

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
            registerConditionalConfiguration(condition, (cnd) -> registeredDowncalls.add(Pair.create(desc, options)));
        }

        @Override
        public void registerForUpcall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            registerConditionalConfiguration(condition, (ignored) -> registeredUpcalls.add(Pair.create(desc, options)));
        }
    }

    ForeignFunctionsFeature() {
        /*
         * We intentionally add these exports in the constructor to avoid access errors from plugins
         * when the feature is disabled in the config.
         */
        for (var modulePackages : REQUIRES_CONCEALED.entrySet()) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, ForeignFunctionsFeature.class, false, modulePackages.getKey(), modulePackages.getValue());
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (!SubstrateOptions.ForeignAPISupport.getValue()) {
            return false;
        }
        UserError.guarantee(JavaVersionUtil.JAVA_SPEC >= 22, "Support for the Foreign Function and Memory API is available only with JDK 22 and later.");
        UserError.guarantee(SubstrateUtil.getArchitectureName().contains("amd64"), "Support for the Foreign Function and Memory API is currently available only on the AMD64 architecture.");
        UserError.guarantee(!SubstrateOptions.useLLVMBackend(), "Support for the Foreign Function and Memory API is not available with the LLVM backend.");
        return true;
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
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
                        false,
                        AbiUtils.singleton()::makeNativeEntrypoint,
                        n -> new DowncallStub(n, access.getMetaAccess().getWrapped()),
                        ForeignFunctionsRuntime.singleton()::addDowncallStubPointer);
    }

    private void createUpcallStubs(FeatureImpl.BeforeAnalysisAccessImpl access) {
        this.upcallCount = createStubs(
                        registeredUpcalls,
                        access,
                        true,
                        AbiUtils.singleton()::makeJavaEntryPoint,
                        jepi -> LowLevelUpcallStub.make(jepi, access.getUniverse(), access.getMetaAccess().getWrapped()),
                        ForeignFunctionsRuntime.singleton()::addUpcallStubPointer);
    }

    private <S> int createStubs(
                    Set<Pair<FunctionDescriptor, Linker.Option[]>> source,
                    FeatureImpl.BeforeAnalysisAccessImpl access,
                    boolean registerAsEntryPoints,
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
                if (registerAsEntryPoints) {
                    analysisStub.registerAsEntryPoint(CEntryPointData.createCustomUnpublished());
                }
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

        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(false, "jdk.internal.foreign.abi.UpcallLinker"),
                                        "USE_SPEC"),
                        (receiver, originalValue) -> false);

        RuntimeClassInitialization.initializeAtRunTime(RuntimeSystemLookup.class);

        access.registerAsRoot(ReflectionUtil.lookupMethod(ForeignFunctionsRuntime.class, "captureCallState", int.class, CIntPointer.class), false,
                        "Runtime support, registered in " + ForeignFunctionsFeature.class);

        createDowncallStubs(access);
        createUpcallStubs(access);
        ProgressReporter.singleton().setForeignFunctionsInfo(getCreatedDowncallStubsCount(), getCreatedUpcallStubsCount());
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(ForeignFunctionsRuntime.CAPTURE_CALL_STATE);
    }

    /* Testing interface */

    public int getCreatedDowncallStubsCount() {
        assert sealed;
        assert downcallCount >= 0;
        return downcallCount;
    }

    public int getCreatedUpcallStubsCount() {
        assert sealed;
        assert upcallCount >= 0;
        return upcallCount;
    }
}
