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
package com.oracle.svm.preview.panama.hosted;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeForeignFunctionsAccessSupport;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.preview.panama.core.AbiUtils;
import com.oracle.svm.preview.panama.core.ForeignFunctionsRuntime;
import com.oracle.svm.preview.panama.core.NativeEntryPointInfo;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
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

    private static final int FIRST_SUPPORTED_PREVIEW = 20;
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
    private final RuntimeForeignFunctionsAccessSupportImpl accessSupport = new RuntimeForeignFunctionsAccessSupportImpl();
    private final Set<NativeEntryPointInfo> stubsToRegister = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<NativeEntryPointInfo, ResolvedJavaMethod> stubsToCreate = new HashMap<>();

    @Fold
    public static ForeignFunctionsFeature singleton() {
        return ImageSingletons.lookup(ForeignFunctionsFeature.class);
    }

    private void checkNotSealed() {
        UserError.guarantee(!sealed, "Registration of foreign functions was closed.");
    }

    private class RuntimeForeignFunctionsAccessSupportImpl extends ConditionalConfigurationRegistry implements StronglyTypedRuntimeForeignFunctionsAccessSupport {
        @Override
        public void registerForDowncall(ConfigurationCondition condition, FunctionDescriptor desc, Linker.Option... options) {
            checkNotSealed();
            registerConditionalConfiguration(condition, () -> stubsToRegister.add(AbiUtils.getInstance().makeEntrypoint(desc, options)));
        }
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.ForeignFunctions.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess c) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) c;
        MetaAccessProvider metaAccess = config.getMetaAccess().getWrapped();

        assert (JavaVersionUtil.JAVA_SPEC >= FIRST_SUPPORTED_PREVIEW && isPreviewEnabled()) ||
                        JavaVersionUtil.JAVA_SPEC >= FIRST_SUPPORTED_NON_PREVIEW;

        boolean supportForeignFunctions = !SubstrateOptions.useLLVMBackend();

        if (supportForeignFunctions) {
            ImageSingletons.add(ForeignFunctionsRuntime.class, new ForeignFunctionsRuntime());
            ImageSingletons.add(RuntimeForeignFunctionsAccessSupport.class, accessSupport);
            config.registerSubstitutionProcessor(new ForeignFunctionsSubstitutionProcessor(metaAccess));
        } else {
            if (SubstrateOptions.useLLVMBackend()) {
                throw UserError.abort("Foreign functions interface is in use, but is not supported together with the LLVM backend.");
            } else {
                throw UserError.abort("Foreign functions interface is in use, but is not supported for an unspecified reason.");
            }
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        for (var modulePackages : REQUIRES_CONCEALED.entrySet()) {
            ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, ForeignFunctionsFeature.class, false, modulePackages.getKey(), modulePackages.getValue());
        }
        ModuleSupport.allowNativeAccess(ForeignFunctionsFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        var access = (FeatureImpl.BeforeAnalysisAccessImpl) a;

        ConfigurationParser parser = new ForeignFunctionsConfigurationParser(accessSupport);
        ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "panama foreign",
                        ConfigurationFiles.Options.ForeignFunctionsConfigurationFiles, ConfigurationFiles.Options.ForeignFunctionsResources, ConfigurationFile.FOREIGN_FUNCTIONS.getFileName());

        /*
         * Specializing the lambda form would define a new class, which is not allowed in
         * SubstrateVM
         */
        access.registerFieldValueTransformer(
                        ReflectionUtil.lookupField(
                                        ReflectionUtil.lookupClass(false, "jdk.internal.foreign.abi.DowncallLinker"),
                                        "USE_SPEC"),
                        (receiver, originalValue) -> false);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        if (stubsToRegister.isEmpty()) {
            return;
        }
        var access = (FeatureImpl.DuringAnalysisAccessImpl) a;

        for (NativeEntryPointInfo nepi : stubsToRegister) {
            stubsToCreate.computeIfAbsent(nepi, ignored -> {
                ResolvedJavaMethod stub = new DowncallStub(nepi, access.getMetaAccess().getWrapped()); // Why
                                                                                                       // getWrapped?
                AnalysisMethod analysisStub = access.getUniverse().lookup(stub);
                access.getBigBang().addRootMethod(analysisStub, false);
                return analysisStub;
            });
        }
        stubsToRegister.clear();

        access.requireAnalysisIteration();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        sealed = true;
        ProgressReporter.singleton().setForeignFunctionsInfo(stubsToCreate.size());
        for (var nepiStubPair : stubsToCreate.entrySet()) {
            ForeignFunctionsRuntime.singleton().addStubPointer(
                            nepiStubPair.getKey(),
                            new MethodPointer(nepiStubPair.getValue()));
        }
        stubsToCreate.clear();
    }
}
