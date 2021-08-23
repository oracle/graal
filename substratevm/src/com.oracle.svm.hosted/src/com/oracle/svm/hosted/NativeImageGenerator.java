/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsDir;
import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsMode;
import static org.graalvm.compiler.hotspot.JVMCIVersionCheck.JVMCI11_RELEASES_URL;
import static org.graalvm.compiler.hotspot.JVMCIVersionCheck.JVMCI8_RELEASES_URL;
import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.Reference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.ExpandLogicPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawPointerTo;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess;
import org.graalvm.nativeimage.impl.CConstantValueSupport;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.DefaultAnalysisPolicy;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccessExtensionProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.AnalysisHeapHistogramPrinter;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.graal.pointsto.reports.ObjectTreePrinter;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.aarch64.AArch64CPUFeatureAccess;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.NoLibC;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.jdk.SubstrateArraycopySnippets;
import com.oracle.svm.core.graal.lir.VerifyCFunctionReferenceMapsLIRPhase;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.phases.CollectDeoptimizationSourcePositionsPhase;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.MethodSafepointInsertionPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionPathsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.snippets.DeoptHostedSnippets;
import com.oracle.svm.core.graal.snippets.DeoptRuntimeSnippets;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.ExceptionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.jdk.localization.LocalizationFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeUniverseBuildingAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.SVMAnalysisMetaAccess;
import com.oracle.svm.hosted.annotation.AnnotationSupport;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.c.CConstantValueSupportImpl;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.OffsetOfSupportImpl;
import com.oracle.svm.hosted.c.SizeOfSupportImpl;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperSubstitutionProcessor;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.ConfigurableClassInitialization;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.CFunctionSubstitutionProcessor;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.HostedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.NativeMethodSubstitutionProcessor;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.SubstrateGraphMakerFactory;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInterface;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.UniverseBuilder;
import com.oracle.svm.hosted.option.HostedOptionProvider;
import com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.ImageBuildStatisticsCounterPhase;
import com.oracle.svm.hosted.phases.InjectedAccessorsPlugin;
import com.oracle.svm.hosted.phases.IntrinsifyMethodHandlesInvocationPlugin;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.phases.VerifyDeoptFrameStatesLIRPhase;
import com.oracle.svm.hosted.phases.VerifyNoGuardsPhase;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeclarativeSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedFieldsPlugin;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;
import com.oracle.svm.util.ImageBuildStatistics;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NativeImageGenerator {

    protected final FeatureHandler featureHandler;
    protected final ImageClassLoader loader;
    protected final HostedOptionProvider optionProvider;

    private DeadlockWatchdog watchdog;
    private AnalysisUniverse aUniverse;
    private HostedUniverse hUniverse;
    private Inflation bb;
    private NativeLibraries nativeLibraries;
    private AbstractImage image;
    private AtomicBoolean buildStarted = new AtomicBoolean();

    private Pair<Method, CEntryPointData> mainEntryPoint;

    final Map<ArtifactType, List<Path>> buildArtifacts = new EnumMap<>(ArtifactType.class);

    public NativeImageGenerator(ImageClassLoader loader, HostedOptionProvider optionProvider, Pair<Method, CEntryPointData> mainEntryPoint) {
        this.loader = loader;
        this.mainEntryPoint = mainEntryPoint;
        this.featureHandler = new FeatureHandler();
        this.optionProvider = optionProvider;
        /*
         * Substrate VM parses all graphs, including snippets, early. We do not support bytecode
         * parsing at run time.
         */
        optionProvider.getHostedValues().put(GraalOptions.EagerSnippets, true);
        optionProvider.getRuntimeValues().put(GraalOptions.EagerSnippets, true);
    }

    public static Platform loadPlatform(ClassLoader classLoader, String platformClassName) throws ClassNotFoundException {
        Class<?> platformClass;

        platformClass = classLoader.loadClass(platformClassName);

        Object result;
        try {
            result = ReflectionUtil.newInstance(platformClass);
        } catch (ReflectionUtilError ex) {
            throw UserError.abort(ex.getCause(), "Could not instantiate platform class %s. Ensure the class is not abstract and has a no-argument constructor.", platformClassName);
        }

        if (!(result instanceof Platform)) {
            throw UserError.abort("Platform class %s does not implement %s", platformClassName, Platform.class.getTypeName());
        }
        return (Platform) result;
    }

    public static Platform loadPlatform(String os, String arch) {
        ServiceLoader<Platform> loader = ServiceLoader.load(Platform.class);
        for (Platform platform : loader) {
            if (platform.getOS().equals(os) && platform.getArchitecture().equals(arch)) {
                return platform;
            }
        }
        throw UserError.abort("Platform specified as " + os + "-" + arch + " isn't supported.");
    }

    public static Platform getTargetPlatform(ClassLoader classLoader) {
        /*
         * We cannot use a regular hosted option for the platform class: The code that instantiates
         * the platform class runs before options are parsed, because option parsing depends on the
         * platform (there can be platform-specific options). So we need to use a regular system
         * property to specify a platform class explicitly on the command line.
         */

        String platformClassName = System.getProperty(Platform.PLATFORM_PROPERTY_NAME);
        if (platformClassName != null) {
            try {
                return loadPlatform(classLoader, platformClassName);
            } catch (ClassNotFoundException ex) {
                throw UserError.abort("Could not find platform class %s that was specified explicitly on the command line using the system property %s",
                                platformClassName, Platform.PLATFORM_PROPERTY_NAME);
            }
        }

        String os = System.getProperty("svm.targetPlatformOS");
        if (os == null) {
            os = OS.getCurrent().className.toLowerCase();
        }

        String arch = System.getProperty("svm.targetPlatformArch");
        if (arch == null) {
            arch = SubstrateUtil.getArchitectureName();
        }

        return loadPlatform(os, arch);
    }

    /**
     * Duplicates the logic in {@link Platform#includedIn(Class)}, but can be used in cases where
     * the VMConfiguration is not yet set up.
     */
    public static boolean includedIn(Platform platform, Class<? extends Platform> platformGroup) {
        return platformGroup.isInstance(platform);
    }

    /**
     * Returns true if the provided platform is included in at least one of the provided platform
     * groups defined by the annotation. Also returns true if no annotation is provided.
     */
    public static boolean includedIn(Platform platform, Platforms platformsAnnotation) {
        if (platformsAnnotation == null) {
            return true;
        }
        for (Class<? extends Platform> platformGroup : platformsAnnotation.value()) {
            if (includedIn(platform, platformGroup)) {
                return true;
            }
        }
        return false;
    }

    public static SubstrateTargetDescription createTarget(Platform platform) {
        if (includedIn(platform, Platform.AMD64.class)) {
            Architecture architecture;
            if (NativeImageOptions.NativeArchitecture.getValue()) {
                architecture = GraalAccess.getOriginalTarget().arch;
            } else {
                EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);
                // SSE and SSE2 are added by defaults as they are required by Graal
                features.add(AMD64.CPUFeature.SSE);
                features.add(AMD64.CPUFeature.SSE2);

                features.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AMD64.CPUFeature.values()));

                architecture = new AMD64(features, AMD64CPUFeatureAccess.allAMD64Flags());
            }
            assert architecture instanceof AMD64 : "using AMD64 platform with a different architecture";
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers: rax and xmm0
            return new SubstrateTargetDescription(architecture, true, 16, 0, deoptScratchSpace);
        } else if (includedIn(platform, Platform.AARCH64.class)) {
            Architecture architecture;
            if (NativeImageOptions.NativeArchitecture.getValue()) {
                architecture = GraalAccess.getOriginalTarget().arch;
            } else {
                EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);
                /*
                 * FP is added by default, as floating-point operations are required by Graal.
                 */
                features.add(AArch64.CPUFeature.FP);
                /*
                 * ASIMD is added by default, as it is available in all AArch64 machines with
                 * floating-port support.
                 */
                features.add(AArch64.CPUFeature.ASIMD);

                features.addAll(parseCSVtoEnum(AArch64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AArch64.CPUFeature.values()));

                architecture = new AArch64(features, AArch64CPUFeatureAccess.enabledAArch64Flags());
            }
            assert architecture instanceof AArch64 : "using AArch64 platform with a different architecture";
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers.
            return new SubstrateTargetDescription(architecture, true, 16, 0, deoptScratchSpace);
        } else {
            throw UserError.abort("Architecture specified by platform is not supported: %s", platform.getClass().getTypeName());
        }
    }

    /**
     * Executes the image build. Only one image can be built with this generator.
     */
    public void run(Map<Method, CEntryPointData> entryPoints,
                    JavaMainSupport javaMainSupport, String imageName,
                    NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor,
                    EconomicSet<String> allOptionNames) {
        try {
            if (!buildStarted.compareAndSet(false, true)) {
                throw UserError.abort("An image build has already been performed with this generator.");
            }

            try {
                /*
                 * JVMCI 20.2-b01 introduced new methods for linking and querying whether an
                 * interface has default methods. Fail early if these methods are missing.
                 */
                ResolvedJavaType.class.getDeclaredMethod("link");
            } catch (ReflectiveOperationException ex) {
                throw UserError.abort("JVMCI version provided %s is missing the 'ResolvedJavaType.link()' method added in jvmci-20.2-b01. " +
                                "Please use the latest JVMCI JDK from %s or %s.", System.getProperty("java.home"), JVMCI8_RELEASES_URL, JVMCI11_RELEASES_URL);
            }

            setSystemPropertiesForImageLate(k);

            ImageSingletonsSupportImpl.HostedManagement.install(new ImageSingletonsSupportImpl.HostedManagement());

            ImageSingletons.add(BuildArtifacts.class, (type, artifact) -> buildArtifacts.computeIfAbsent(type, t -> new ArrayList<>()).add(artifact));
            ImageSingletons.add(HostedOptionValues.class, new HostedOptionValues(optionProvider.getHostedValues()));
            ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));
            watchdog = new DeadlockWatchdog();
            try (TemporaryBuildDirectoryProviderImpl tempDirectoryProvider = new TemporaryBuildDirectoryProviderImpl()) {
                ImageSingletons.add(TemporaryBuildDirectoryProvider.class, tempDirectoryProvider);
                doRun(entryPoints, javaMainSupport, imageName, k, harnessSubstitutions, compilationExecutor, analysisExecutor);
            } finally {
                watchdog.close();
            }
        } finally {
            analysisExecutor.shutdownNow();
            compilationExecutor.shutdownNow();
        }
    }

    protected static void setSystemPropertiesForImageEarly() {
        System.setProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME);
    }

    private static void setSystemPropertiesForImageLate(NativeImageKind imageKind) {
        VMError.guarantee(ImageInfo.inImageBuildtimeCode(), "System property to indicate image build time is set earlier, before listing classes");
        if (imageKind.isExecutable) {
            System.setProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, ImageInfo.PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE);
        } else {
            System.setProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, ImageInfo.PROPERTY_IMAGE_KIND_VALUE_SHARED_LIBRARY);
        }
    }

    protected static void clearSystemPropertiesForImage() {
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY);
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY);
    }

    @SuppressWarnings("try")
    private void doRun(Map<Method, CEntryPointData> entryPoints,
                    JavaMainSupport javaMainSupport, String imageName, NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();

        OptionValues options = HostedOptionValues.singleton();
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        try (DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(originalSnippetReflection)).build();
                        DebugCloseable featureCleanup = () -> featureHandler.forEachFeature(Feature::cleanup)) {
            setupNativeImage(imageName, options, entryPoints, javaMainSupport, harnessSubstitutions, analysisExecutor, originalSnippetReflection, debug);

            boolean returnAfterAnalysis = runPointsToAnalysis(imageName, options, debug);
            if (returnAfterAnalysis) {
                return;
            }

            NativeImageHeap heap;
            HostedMetaAccess hMetaAccess;
            SharedRuntimeConfigurationBuilder runtime;
            try (StopTimer t = new Timer(imageName, "universe").start()) {
                hUniverse = new HostedUniverse(bb);
                hMetaAccess = new HostedMetaAccess(hUniverse, bb.getMetaAccess());

                BeforeUniverseBuildingAccessImpl beforeUniverseBuildingConfig = new BeforeUniverseBuildingAccessImpl(featureHandler, loader, debug, hMetaAccess);
                featureHandler.forEachFeature(feature -> feature.beforeUniverseBuilding(beforeUniverseBuildingConfig));

                new UniverseBuilder(aUniverse, bb.getMetaAccess(), hUniverse, hMetaAccess, HostedConfiguration.instance().createStaticAnalysisResultsBuilder(bb, hUniverse),
                                bb.getUnsupportedFeatures()).build(debug);

                ClassInitializationSupport classInitializationSupport = bb.getHostVM().getClassInitializationSupport();
                runtime = new HostedRuntimeConfigurationBuilder(options, bb.getHostVM(), hUniverse, hMetaAccess, bb.getProviders(), nativeLibraries, classInitializationSupport,
                                GraalAccess.getOriginalProviders().getLoopsDataProvider()).build();
                registerGraphBuilderPlugins(featureHandler, runtime.getRuntimeConfig(), (HostedProviders) runtime.getRuntimeConfig().getProviders(), bb.getMetaAccess(), aUniverse,
                                hMetaAccess, hUniverse,
                                nativeLibraries, loader, ParsingReason.AOTCompilation, bb.getAnnotationSubstitutionProcessor(),
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                                classInitializationSupport, ConfigurationValues.getTarget());

                if (NativeImageOptions.PrintUniverse.getValue()) {
                    printTypes();
                }

                /* Find the entry point methods in the hosted world. */
                for (AnalysisMethod m : aUniverse.getMethods()) {
                    if (m.isEntryPoint()) {
                        HostedMethod found = hUniverse.lookup(m);
                        assert found != null;
                        hostedEntryPoints.add(found);
                    }
                }
                if (hostedEntryPoints.size() == 0) {
                    throw UserError.abort("Warning: no entry points found, i.e., no method annotated with @%s", CEntryPoint.class.getSimpleName());
                }

                bb.getUnsupportedFeatures().report(bb);

                recordRestrictHeapAccessCallees(aUniverse.getMethods());

                /*
                 * After this point, all TypeFlow (and therefore also TypeState) objects are
                 * unreachable and can be garbage collected. This is important to keep the overall
                 * memory footprint low. However, this also means we no longer have complete call
                 * chain information. Only the summarized information stored in the
                 * StaticAnalysisResult objects is available after this point.
                 */
                bb.cleanupAfterAnalysis();
            } catch (UnsupportedFeatureException ufe) {
                throw FallbackFeature.reportAsFallback(ufe);
            }

            heap = new NativeImageHeap(aUniverse, hUniverse, hMetaAccess, ImageSingletons.lookup(ImageHeapLayouter.class));

            BeforeCompilationAccessImpl beforeCompilationConfig = new BeforeCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, heap, debug, runtime);
            featureHandler.forEachFeature(feature -> feature.beforeCompilation(beforeCompilationConfig));

            runtime.updateLazyState(hMetaAccess);

            NativeImageCodeCache codeCache;
            CompileQueue compileQueue;
            try (StopTimer t = new Timer(imageName, "compile").start()) {
                compileQueue = HostedConfiguration.instance().createCompileQueue(debug, featureHandler, hUniverse, runtime, DeoptTester.enabled(), bb.getProviders().getSnippetReflection(),
                                compilationExecutor);
                compileQueue.finish(debug);

                /* release memory taken by graphs for the image writing */
                hUniverse.getMethods().forEach(HostedMethod::clear);

                codeCache = NativeImageCodeCacheFactory.get().newCodeCache(compileQueue, heap, loader.platform,
                                ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                codeCache.layoutConstants();
                codeCache.layoutMethods(debug, imageName, bb, compilationExecutor);

                AfterCompilationAccessImpl config = new AfterCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, compileQueue.getCompilationTasks(), heap, debug, runtime);
                featureHandler.forEachFeature(feature -> feature.afterCompilation(config));
            }
            CodeCacheProvider codeCacheProvider = runtime.getRuntimeConfig().getBackendForNormalMethod().getProviders().getCodeCache();
            try (Indent indent = debug.logAndIndent("create native image")) {
                try (DebugContext.Scope buildScope = debug.scope("CreateImage", codeCacheProvider)) {
                    try (StopTimer t = new Timer(imageName, "image").start()) {

                        // Start building the model of the native image heap.
                        heap.addInitialObjects();
                        // Then build the model of the code cache, which can
                        // add objects to the native image heap.
                        codeCache.addConstantsToHeap();
                        // Finish building the model of the native image heap.
                        heap.addTrailingObjects();

                        AfterHeapLayoutAccessImpl config = new AfterHeapLayoutAccessImpl(featureHandler, loader, heap, hMetaAccess, debug);
                        featureHandler.forEachFeature(feature -> feature.afterHeapLayout(config));

                        this.image = AbstractImage.create(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader.getClassLoader());
                        image.build(imageName, debug);
                        if (NativeImageOptions.PrintUniverse.getValue()) {
                            /*
                             * This debug output must be printed _after_ and not _during_ image
                             * building, because it adds some PrintStream objects to static fields,
                             * which disrupts the heap.
                             */
                            codeCache.printCompilationResults();
                        }
                    }
                } catch (Throwable e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }

            BeforeImageWriteAccessImpl beforeConfig = new BeforeImageWriteAccessImpl(featureHandler, loader, imageName, image,
                            runtime.getRuntimeConfig(), aUniverse, hUniverse, optionProvider, hMetaAccess, debug);
            featureHandler.forEachFeature(feature -> feature.beforeImageWrite(beforeConfig));

            try (StopTimer t = new Timer(imageName, "write").start()) {
                /*
                 * This will write the debug info too -- i.e. we may be writing more than one file,
                 * if the debug info is in a separate file. We need to push writing the file to the
                 * image implementation, because whether the debug info and image share a file or
                 * not is an implementation detail of the image.
                 */
                Path tmpDir = ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory();
                LinkerInvocation inv = image.write(debug, generatedFiles(HostedOptionValues.singleton()), tmpDir, imageName, beforeConfig);
                if (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue()) {
                    return;
                }

                AfterImageWriteAccessImpl afterConfig = new AfterImageWriteAccessImpl(featureHandler, loader, hUniverse, inv, tmpDir, image.getImageKind(), debug);
                featureHandler.forEachFeature(feature -> feature.afterImageWrite(afterConfig));
            }
        }
    }

    void reportBuildArtifacts(String imageName) {
        Path buildDir = generatedFiles(HostedOptionValues.singleton());
        Consumer<PrintWriter> writerConsumer = writer -> buildArtifacts.forEach((artifactType, paths) -> {
            writer.println("[" + artifactType + "]");
            if (artifactType == BuildArtifacts.ArtifactType.JDK_LIB_SHIM) {
                writer.println("# Note that shim JDK libraries depend on this");
                writer.println("# particular native image (including its name)");
                writer.println("# and therefore cannot be used with others.");
            }
            paths.stream().map(Path::toAbsolutePath).map(buildDir::relativize).forEach(writer::println);
            writer.println();
        });
        ReportUtils.report("build artifacts", buildDir.resolve(imageName + ".build_artifacts.txt"), writerConsumer);
    }

    @SuppressWarnings("try")
    private boolean runPointsToAnalysis(String imageName, OptionValues options, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("run analysis")) {
            try (Indent ignored1 = debug.logAndIndent("process analysis initializers")) {
                BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                bb.getHostVM().getClassInitializationSupport().setConfigurationSealed(true);
            }

            try (StopTimer t = bb.analysisTimer.start()) {

                /*
                 * Iterate until analysis reaches a fixpoint.
                 */
                DuringAnalysisAccessImpl config = new DuringAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                int numIterations = 0;
                while (true) {
                    try (Indent indent2 = debug.logAndIndent("new analysis iteration")) {
                        /*
                         * Do the analysis (which itself is done in a similar iterative process)
                         */
                        boolean analysisChanged = bb.finish();

                        numIterations++;
                        if (numIterations > 1000) {
                            /*
                             * Usually there are < 10 iterations. If we have so many iterations, we
                             * probably have an endless loop (but at least we have a performance
                             * problem because we re-start the analysis so often).
                             */
                            throw UserError.abort("Static analysis did not reach a fix point after %d iterations because a Feature keeps requesting new analysis iterations. " +
                                            "The analysis itself %s find a change in type states in the last iteration.",
                                            numIterations, analysisChanged ? "DID" : "DID NOT");
                        }

                        /*
                         * Allow features to change the universe.
                         */
                        try (StopTimer t2 = bb.processFeaturesTimer.start()) {
                            int numTypes = aUniverse.getTypes().size();
                            int numMethods = aUniverse.getMethods().size();
                            int numFields = aUniverse.getFields().size();

                            bb.getHostVM().notifyClassReachabilityListener(aUniverse, config);
                            featureHandler.forEachFeature(feature -> feature.duringAnalysis(config));

                            if (!config.getAndResetRequireAnalysisIteration()) {
                                if (numTypes != aUniverse.getTypes().size() || numMethods != aUniverse.getMethods().size() || numFields != aUniverse.getFields().size()) {
                                    throw UserError.abort(
                                                    "When a feature makes more types, methods, or fields reachable, it must require another analysis iteration via DuringAnalysisAccess.requireAnalysisIteration()");
                                }
                                break;
                            }
                        }
                    }
                }
                assert verifyAssignableTypes(imageName);

                /*
                 * Libraries defined via @CLibrary annotations are added at the end of the list of
                 * libraries so that the written object file AND the static JDK libraries can depend
                 * on them.
                 */
                nativeLibraries.processAnnotated();

                AfterAnalysisAccessImpl postConfig = new AfterAnalysisAccessImpl(featureHandler, loader, bb, debug);
                featureHandler.forEachFeature(feature -> feature.afterAnalysis(postConfig));

                checkUniverse();

                bb.typeFlowTimer.print();
                bb.checkObjectsTimer.print();
                bb.processFeaturesTimer.print();

                /* report the unsupported features by throwing UnsupportedFeatureException */
                bb.getUnsupportedFeatures().report(bb);
                bb.checkUserLimitations();
            } catch (UnsupportedFeatureException ufe) {
                throw FallbackFeature.reportAsFallback(ufe);
            }
        } catch (InterruptedException ie) {
            throw new InterruptImageBuilding();
        } finally {
            OnAnalysisExitAccess onExitConfig = new OnAnalysisExitAccessImpl(featureHandler, loader, bb, debug);
            featureHandler.forEachFeature(feature -> feature.onAnalysisExit(onExitConfig));

            /*
             * Execute analysis reporting here. This code is executed even if unsupported features
             * are reported or the analysis fails due to any other reasons.
             */
            if (bb != null) {
                if (AnalysisReportsOptions.PrintAnalysisStatistics.getValue(options)) {
                    StatisticsPrinter.print(bb, SubstrateOptions.reportsPath(), ReportUtils.extractImageName(imageName));
                }

                if (AnalysisReportsOptions.PrintAnalysisCallTree.getValue(options)) {
                    CallTreePrinter.print(bb, SubstrateOptions.reportsPath(), ReportUtils.extractImageName(imageName));
                }

                if (AnalysisReportsOptions.PrintImageObjectTree.getValue(options)) {
                    ObjectTreePrinter.print(bb, SubstrateOptions.reportsPath(), ReportUtils.extractImageName(imageName));
                    AnalysisHeapHistogramPrinter.print(bb, SubstrateOptions.reportsPath(), ReportUtils.extractImageName(imageName));
                }

                if (PointstoOptions.PrintPointsToStatistics.getValue(options)) {
                    PointsToStats.report(bb, ReportUtils.extractImageName(imageName));
                }

                if (PointstoOptions.PrintSynchronizedAnalysis.getValue(options)) {
                    TypeState allSynchronizedTypeState = bb.getAllSynchronizedTypeState();
                    String typesString = allSynchronizedTypeState.closeToAllInstantiated(bb) ? "close to all instantiated" : //
                                    StreamSupport.stream(allSynchronizedTypeState.types().spliterator(), false).map(AnalysisType::getName).collect(Collectors.joining(", "));
                    System.out.println();
                    System.out.println("AllSynchronizedTypes");
                    System.out.println("Synchronized types #: " + allSynchronizedTypeState.typesCount());
                    System.out.println("Types: " + typesString);
                    System.out.println();
                }
            }
        }
        if (NativeImageOptions.ReturnAfterAnalysis.getValue()) {
            return true;
        }
        if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
            throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(NativeImageOptions.ExitAfterAnalysis, "+"));
        }
        return false;
    }

    @SuppressWarnings("try")
    private boolean verifyAssignableTypes(String imageName) {
        /*
         * This verification has quadratic complexity, so do it only once after the static analysis
         * has finished, and can be disabled with an option.
         */
        if (SubstrateOptions.DisableTypeIdResultVerification.getValue()) {
            return true;
        }
        try (StopTimer t = new Timer(imageName, "(verifyAssignableTypes)").start()) {
            return AnalysisType.verifyAssignableTypes(bb);
        }
    }

    @SuppressWarnings("try")
    private void setupNativeImage(String imageName, OptionValues options, Map<Method, CEntryPointData> entryPoints, JavaMainSupport javaMainSupport, SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool analysisExecutor, SnippetReflectionProvider originalSnippetReflection, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("setup native-image builder")) {
            try (StopTimer ignored1 = new Timer(imageName, "setup").start()) {
                SubstrateTargetDescription target = createTarget(loader.platform);
                ImageSingletons.add(Platform.class, loader.platform);
                ImageSingletons.add(SubstrateTargetDescription.class, target);

                ImageSingletons.add(SubstrateOptions.ReportingSupport.class, new SubstrateOptions.ReportingSupport(
                                DiagnosticsMode.getValue() ? DiagnosticsDir.getValue() : Paths.get("reports").toString()));

                if (javaMainSupport != null) {
                    ImageSingletons.add(JavaMainSupport.class, javaMainSupport);
                }

                Providers originalProviders = GraalAccess.getOriginalProviders();
                MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();

                ClassInitializationSupport classInitializationSupport = new ConfigurableClassInitialization(originalMetaAccess, loader);
                ImageSingletons.add(RuntimeClassInitializationSupport.class, classInitializationSupport);
                ClassInitializationFeature.processClassInitializationOptions(classInitializationSupport);

                if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(options)) {
                    ImageSingletons.add(ImageBuildStatistics.class, new ImageBuildStatistics());
                }

                featureHandler.registerFeatures(loader, debug);
                AfterRegistrationAccessImpl access = new AfterRegistrationAccessImpl(featureHandler, loader, originalMetaAccess, mainEntryPoint, debug);
                featureHandler.forEachFeature(feature -> feature.afterRegistration(access));
                setDefaultLibCIfMissing();
                if (!Pair.<Method, CEntryPointData> empty().equals(access.getMainEntryPoint())) {
                    setAndVerifyMainEntryPoint(access, entryPoints);
                }
                registerEntryPoints(entryPoints);

                /*
                 * Check if any configuration factory class was registered. If not, register the
                 * basic one.
                 */
                HostedConfiguration.setDefaultIfEmpty();
                GraalConfiguration.setDefaultIfEmpty();

                AnnotationSubstitutionProcessor annotationSubstitutions = createDeclarativeSubstitutionProcessor(originalMetaAccess, loader, classInitializationSupport);
                CEnumCallWrapperSubstitutionProcessor cEnumProcessor = new CEnumCallWrapperSubstitutionProcessor();
                aUniverse = createAnalysisUniverse(options, target, loader, originalMetaAccess, originalSnippetReflection, annotationSubstitutions, cEnumProcessor,
                                classInitializationSupport, Collections.singletonList(harnessSubstitutions), analysisExecutor);

                AnalysisMetaAccess aMetaAccess = new SVMAnalysisMetaAccess(aUniverse, originalMetaAccess);
                AnalysisConstantReflectionProvider aConstantReflection = new AnalysisConstantReflectionProvider(
                                aUniverse, aMetaAccess, originalProviders.getConstantReflection(), classInitializationSupport);
                WordTypes aWordTypes = new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind());
                HostedSnippetReflectionProvider aSnippetReflection = new HostedSnippetReflectionProvider(aWordTypes);

                ForeignCallsProvider aForeignCalls = new SubstrateForeignCallsProvider();
                bb = createBigBang(options, target, aUniverse, analysisExecutor, watchdog::recordActivity, aMetaAccess, aConstantReflection, aWordTypes, aSnippetReflection,
                                annotationSubstitutions, aForeignCalls, classInitializationSupport, originalProviders);
                aUniverse.setBigBang(bb);
                /* Register already created types as assignable. */
                aUniverse.getTypes().forEach(t -> t.registerAsAssignable(bb));

                boolean withoutCompilerInvoker = CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration.getValue() ||
                                (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue() && CAnnotationProcessorCache.Options.UseCAPCache.getValue());

                if (!withoutCompilerInvoker) {
                    CCompilerInvoker compilerInvoker = CCompilerInvoker.create(ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                    compilerInvoker.verifyCompiler();
                    ImageSingletons.add(CCompilerInvoker.class, compilerInvoker);
                }

                nativeLibraries = setupNativeLibraries(imageName, aConstantReflection, aMetaAccess, aSnippetReflection, cEnumProcessor, classInitializationSupport, debug);

                try (Indent ignored2 = debug.logAndIndent("process startup initializers")) {
                    FeatureImpl.DuringSetupAccessImpl config = new FeatureImpl.DuringSetupAccessImpl(featureHandler, loader, bb, debug);
                    featureHandler.forEachFeature(feature -> feature.duringSetup(config));
                }

                initializeBigBang(bb, options, featureHandler, nativeLibraries, debug, aMetaAccess, aUniverse.getSubstitutions(), loader, true,
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()));
                entryPoints.forEach((method, entryPointData) -> CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> entryPointData));
            }
        }
    }

    private static void setDefaultLibCIfMissing() {
        if (!ImageSingletons.contains(LibCBase.class)) {
            ImageSingletons.add(LibCBase.class, new NoLibC());
        }
    }

    private void setAndVerifyMainEntryPoint(AfterRegistrationAccessImpl access, Map<Method, CEntryPointData> entryPoints) {
        mainEntryPoint = access.getMainEntryPoint();
        entryPoints.put(mainEntryPoint.getLeft(), mainEntryPoint.getRight());
    }

    public static AnalysisUniverse createAnalysisUniverse(OptionValues options, TargetDescription target, ImageClassLoader loader, MetaAccessProvider originalMetaAccess,
                    SnippetReflectionProvider originalSnippetReflection, AnnotationSubstitutionProcessor annotationSubstitutions, SubstitutionProcessor cEnumProcessor,
                    ClassInitializationSupport classInitializationSupport, List<SubstitutionProcessor> additionalSubstitutions, ForkJoinPool buildExecutor) {
        UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = createAutomaticUnsafeSubstitutions(originalSnippetReflection, annotationSubstitutions);
        SubstitutionProcessor aSubstitutions = createAnalysisSubstitutionProcessor(originalMetaAccess, originalSnippetReflection, cEnumProcessor, automaticSubstitutions,
                        annotationSubstitutions, additionalSubstitutions);

        SVMHost hostVM = HostedConfiguration.instance().createHostVM(options, buildExecutor, loader.getClassLoader(), classInitializationSupport, automaticSubstitutions, loader.platform);

        automaticSubstitutions.init(loader, originalMetaAccess);
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);
        return new AnalysisUniverse(hostVM, target.wordJavaKind, analysisPolicy, aSubstitutions, originalMetaAccess, originalSnippetReflection,
                        new SubstrateSnippetReflectionProvider(new SubstrateWordTypes(originalMetaAccess, FrameAccess.getWordKind())));
    }

    public static AnnotationSubstitutionProcessor createDeclarativeSubstitutionProcessor(MetaAccessProvider originalMetaAccess, ImageClassLoader loader,
                    ClassInitializationSupport classInitializationSupport) {
        AnnotationSubstitutionProcessor annotationSubstitutions = new DeclarativeSubstitutionProcessor(loader, originalMetaAccess, classInitializationSupport);
        annotationSubstitutions.init();
        return annotationSubstitutions;
    }

    public static UnsafeAutomaticSubstitutionProcessor createAutomaticUnsafeSubstitutions(SnippetReflectionProvider originalSnippetReflection,
                    AnnotationSubstitutionProcessor annotationSubstitutions) {
        return new UnsafeAutomaticSubstitutionProcessor(annotationSubstitutions, originalSnippetReflection);
    }

    public static SubstitutionProcessor createAnalysisSubstitutionProcessor(MetaAccessProvider originalMetaAccess, SnippetReflectionProvider originalSnippetReflection,
                    SubstitutionProcessor cEnumProcessor, SubstitutionProcessor automaticSubstitutions, SubstitutionProcessor annotationSubstitutions,
                    List<SubstitutionProcessor> additionalSubstitutionProcessors) {
        List<SubstitutionProcessor> allProcessors = new ArrayList<>();
        SubstitutionProcessor cFunctionSubstitutions = new CFunctionSubstitutionProcessor();
        allProcessors.addAll(Arrays.asList(new AnnotationSupport(originalMetaAccess, originalSnippetReflection),
                        annotationSubstitutions, cFunctionSubstitutions, automaticSubstitutions, cEnumProcessor));
        allProcessors.addAll(additionalSubstitutionProcessors);
        return SubstitutionProcessor.chainUpInOrder(allProcessors.toArray(new SubstitutionProcessor[0]));
    }

    @SuppressWarnings("try")
    public static void initializeBigBang(Inflation bb, OptionValues options, FeatureHandler featureHandler, NativeLibraries nativeLibraries, DebugContext debug,
                    AnalysisMetaAccess aMetaAccess, SubstitutionProcessor substitutions, ImageClassLoader loader, boolean initForeignCalls, ClassInitializationPlugin classInitializationPlugin) {
        SubstrateReplacements aReplacements = bb.getReplacements();
        HostedProviders aProviders = bb.getProviders();
        AnalysisUniverse aUniverse = bb.getUniverse();

        /*
         * Eagerly register all target fields of recomputed value fields as unsafe accessed.
         */
        bb.getAnnotationSubstitutionProcessor().processComputedValueFields(bb);

        /*
         * Install feature supported substitutions.
         */
        SubstitutionProcessor[] featureNativeSubstitutions = aUniverse.getFeatureNativeSubstitutions();
        if (featureNativeSubstitutions.length > 0) {
            SubstitutionProcessor chain = SubstitutionProcessor.chainUpInOrder(featureNativeSubstitutions);
            SubstitutionProcessor nativeSubstitutionProcessor = new NativeMethodSubstitutionProcessor(chain, aReplacements);
            SubstitutionProcessor.extendsTheChain(substitutions, new SubstitutionProcessor[]{nativeSubstitutionProcessor});
        }
        SubstitutionProcessor.extendsTheChain(substitutions, aUniverse.getFeatureSubstitutions());

        /*
         * System classes and fields are necessary to tell the static analysis that certain things
         * really "exist". The most common reason for that is that there are no instances and
         * allocations of these classes seen during the static analysis. The heap chunks are one
         * good example.
         */
        try (Indent ignored = debug.logAndIndent("add initial classes/fields/methods")) {
            bb.addSystemClass(Object.class, false, false).registerAsInHeap();
            bb.addSystemField(DynamicHub.class, "vtable");
            bb.addSystemClass(String.class, false, false).registerAsInHeap();
            bb.addSystemClass(String[].class, false, false).registerAsInHeap();
            bb.addSystemField(String.class, "value").registerAsInHeap();
            bb.addSystemClass(long[].class, false, false).registerAsInHeap();
            bb.addSystemClass(byte[].class, false, false).registerAsInHeap();
            bb.addSystemClass(byte[][].class, false, false).registerAsInHeap();
            bb.addSystemClass(Object[].class, false, false).registerAsInHeap();
            bb.addSystemClass(CFunctionPointer[].class, false, false).registerAsInHeap();
            bb.addSystemClass(PointerBase[].class, false, false).registerAsInHeap();

            try {
                bb.addRootMethod(SubstrateArraycopySnippets.class.getDeclaredMethod("doArraycopy", Object.class, int.class, Object.class, int.class, int.class));
                bb.addRootMethod(Object.class.getDeclaredMethod("getClass"));
            } catch (NoSuchMethodException ex) {
                throw VMError.shouldNotReachHere(ex);
            }

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bb.addSystemClass(kind.toJavaClass(), false, true);
                    bb.addSystemField(kind.toBoxedJavaClass(), "value");
                    bb.addSystemMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
                    bb.addSystemMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value");
                    /*
                     * Register the cache location as reachable.
                     * BoxingSnippets$Templates#getCacheLocation accesses the cache field.
                     */
                    Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
                    if (innerClasses != null && innerClasses.length > 0) {
                        bb.getMetaAccess().lookupJavaType(innerClasses[0]).registerAsReachable();
                    }
                }
            }
            /* SubstrateTemplates#toLocationIdentity accesses the Counter.value field. */
            bb.getMetaAccess().lookupJavaType(JavaKind.Void.toJavaClass()).registerAsReachable();
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.util.Counter.class).registerAsReachable();
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.allocationprofile.AllocationCounter.class).registerAsReachable();

            NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, null, null, nativeLibraries, loader, ParsingReason.PointsToAnalysis,
                            bb.getAnnotationSubstitutionProcessor(), classInitializationPlugin, bb.getHostVM().getClassInitializationSupport(), ConfigurationValues.getTarget());
            registerReplacements(debug, featureHandler, null, aProviders, aProviders.getSnippetReflection(), true, initForeignCalls);

            for (StructuredGraph graph : aReplacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options), options)) {
                HostedConfiguration.instance().createMethodTypeFlowBuilder(bb, graph).registerUsedElements(false);
            }
        }
    }

    public static Inflation createBigBang(OptionValues options, TargetDescription target, AnalysisUniverse aUniverse, ForkJoinPool analysisExecutor,
                    Runnable heartbeatCallback, AnalysisMetaAccess aMetaAccess, AnalysisConstantReflectionProvider aConstantReflection, WordTypes aWordTypes,
                    SnippetReflectionProvider aSnippetReflection, AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ForeignCallsProvider aForeignCalls,
                    ClassInitializationSupport classInitializationSupport, Providers originalProviders) {
        assert aUniverse != null : "Analysis universe must be initialized.";
        aMetaAccess.lookupJavaType(String.class).registerAsReachable();
        AnalysisConstantFieldProvider aConstantFieldProvider = new AnalysisConstantFieldProvider(aUniverse, aMetaAccess, aConstantReflection, classInitializationSupport);
        /*
         * Install all snippets so that the types, methods, and fields used in the snippets get
         * added to the universe.
         */
        aMetaAccess.lookupJavaType(Reference.class).registerAsReachable();
        BarrierSet barrierSet = ImageSingletons.lookup(Heap.class).createBarrierSet(aMetaAccess);
        SubstratePlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(barrierSet);
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider();
        LoweringProvider aLoweringProvider = SubstrateLoweringProvider.create(aMetaAccess, null, platformConfig, aMetaAccessExtensionProvider);
        StampProvider aStampProvider = new SubstrateStampProvider(aMetaAccess);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, null, aStampProvider, aSnippetReflection,
                        aWordTypes, platformConfig, aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        SubstrateReplacements aReplacments = new SubstrateReplacements(aProviders, aSnippetReflection, bytecodeProvider, target, aWordTypes, new SubstrateGraphMakerFactory(aWordTypes));
        aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, aReplacments, aStampProvider,
                        aSnippetReflection, aWordTypes, platformConfig, aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());

        return new Inflation(options, aUniverse, aProviders, annotationSubstitutionProcessor, analysisExecutor, heartbeatCallback);
    }

    @SuppressWarnings("try")
    private NativeLibraries setupNativeLibraries(String imageName, ConstantReflectionProvider aConstantReflection, MetaAccessProvider aMetaAccess,
                    SnippetReflectionProvider aSnippetReflection, CEnumCallWrapperSubstitutionProcessor cEnumProcessor, ClassInitializationSupport classInitializationSupport, DebugContext debug) {
        try (StopTimer ignored = new Timer(imageName, "(cap)").start()) {
            NativeLibraries nativeLibs = new NativeLibraries(aConstantReflection, aMetaAccess, aSnippetReflection, ConfigurationValues.getTarget(), classInitializationSupport,
                            ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory(), debug);
            cEnumProcessor.setNativeLibraries(nativeLibs);
            processNativeLibraryImports(nativeLibs, aMetaAccess, classInitializationSupport);

            ImageSingletons.add(SizeOfSupport.class, new SizeOfSupportImpl(nativeLibs, aMetaAccess));
            ImageSingletons.add(OffsetOf.Support.class, new OffsetOfSupportImpl(nativeLibs, aMetaAccess));
            ImageSingletons.add(CConstantValueSupport.class, new CConstantValueSupportImpl(nativeLibs, aMetaAccess));

            if (CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration.getValue()) {
                throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration, "+"));
            }

            if (CAnnotationProcessorCache.Options.ExitAfterCAPCache.getValue()) {
                throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterCAPCache, "+"));
            }

            return nativeLibs;
        }
    }

    private void registerEntryPoints(Map<Method, CEntryPointData> entryPoints) {
        for (Method m : loader.findAnnotatedMethods(CEntryPoint.class)) {
            if (!Modifier.isStatic(m.getModifiers())) {
                throw UserError.abort("Entry point method %s.%s is not static. Add a static modifier to the method.", m.getDeclaringClass().getName(), m.getName());
            }

            boolean include = true;
            CEntryPointOptions options = m.getAnnotation(CEntryPointOptions.class);
            if (options != null) {
                include = ReflectionUtil.newInstance(options.include()).getAsBoolean();
            }
            if (include) {
                entryPoints.put(m, CEntryPointData.create(m));
            }
        }
    }

    /**
     * Record callees of methods that must not allocate.
     */
    private static void recordRestrictHeapAccessCallees(Collection<AnalysisMethod> methods) {
        ((RestrictHeapAccessCalleesImpl) ImageSingletons.lookup(RestrictHeapAccessCallees.class)).aggregateMethods(methods);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static class SubstitutionInvocationPlugins extends InvocationPlugins {

        private AnnotationSubstitutionProcessor annotationSubstitutionProcessor;

        SubstitutionInvocationPlugins(AnnotationSubstitutionProcessor annotationSubstitutionProcessor) {
            this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
        }

        @Override
        protected void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
            Type targetClass;
            if (declaringClass instanceof Class) {
                targetClass = annotationSubstitutionProcessor.getTargetClass((Class<?>) declaringClass);
            } else {
                targetClass = declaringClass;
            }
            super.register(plugin, isOptional, allowOverwrite, targetClass, name, argumentTypes);
        }
    }

    public static void registerGraphBuilderPlugins(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, HostedProviders providers, AnalysisMetaAccess aMetaAccess,
                    AnalysisUniverse aUniverse, HostedMetaAccess hMetaAccess, HostedUniverse hUniverse, NativeLibraries nativeLibs, ImageClassLoader loader, ParsingReason reason,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ClassInitializationPlugin classInitializationPlugin, ClassInitializationSupport classInitializationSupport,
                    TargetDescription target) {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new SubstitutionInvocationPlugins(annotationSubstitutionProcessor));

        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(providers.getSnippetReflection(), providers.getWordTypes());

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        plugins.appendInlineInvokePlugin(replacements);

        plugins.appendNodePlugin(new IntrinsifyMethodHandlesInvocationPlugin(reason, providers, aUniverse, hUniverse));
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        ResolvedJavaType resolvedJavaType = providers.getMetaAccess().lookupJavaType(ClassInitializationTracking.class);
        if (resolvedJavaType instanceof AnalysisType) {
            ((AnalysisType) resolvedJavaType).registerAsReachable();
            ResolvedJavaField field = providers.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(ClassInitializationTracking.class, "IS_IMAGE_BUILD_TIME"));
            ((AnalysisField) field).registerAsAccessed();
        }
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(providers.getMetaAccess(), providers.getSnippetReflection()));
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin(classInitializationSupport));
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), providers.getWordTypes(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        plugins.appendNodePlugin(wordOperationPlugin);

        plugins.setClassInitializationPlugin(classInitializationPlugin);

        featureHandler.forEachGraalFeature(feature -> feature.registerGraphBuilderPlugins(providers, plugins, reason));

        HostedSnippetReflectionProvider hostedSnippetReflection = new HostedSnippetReflectionProvider(new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind()));
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();

        NodeIntrinsificationProvider nodeIntrinsificationProvider;
        if (!SubstrateUtil.isBuildingLibgraal()) {
            nodeIntrinsificationProvider = new NodeIntrinsificationProvider(providers.getMetaAccess(), hostedSnippetReflection, providers.getForeignCalls(),
                            providers.getWordTypes(), target);

        } else {
            nodeIntrinsificationProvider = new NodeIntrinsificationProvider(providers.getMetaAccess(), hostedSnippetReflection,
                            providers.getForeignCalls(), providers.getWordTypes(), target) {
                @Override
                public <T> T getInjectedArgument(Class<T> type) {
                    if (type.isAssignableFrom(GraalHotSpotVMConfig.class)) {
                        return type.cast(compiler.getGraalRuntime().getVMConfig());
                    }
                    if (type.isAssignableFrom(HotSpotGraalRuntimeProvider.class)) {
                        return type.cast(compiler.getGraalRuntime());
                    }
                    return super.getInjectedArgument(type);
                }
            };
        }
        for (Class<? extends GeneratedPluginFactory> factoryClass : loader.findSubclasses(GeneratedPluginFactory.class, true)) {
            if (!Modifier.isAbstract(factoryClass.getModifiers()) && !factoryClass.getName().contains("hotspot")) {
                GeneratedPluginFactory factory;
                try {
                    factory = factoryClass.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw VMError.shouldNotReachHere(ex);
                }
                factory.registerPlugins(plugins.getInvocationPlugins(), nodeIntrinsificationProvider);
            }
        }

        final boolean arrayEqualsSubstitution = !SubstrateOptions.useLLVMBackend();
        registerInvocationPlugins(providers.getMetaAccess(), providers.getSnippetReflection(), plugins.getInvocationPlugins(), replacements,
                        reason == ParsingReason.JITCompilation, true, arrayEqualsSubstitution, providers.getLowerer());

        Architecture architecture = ConfigurationValues.getTarget().arch;
        OptionValues options = aUniverse.hostVM().options();
        ImageSingletons.lookup(TargetGraphBuilderPlugins.class).register(plugins, replacements, architecture,
                        /* registerForeignCallMath */ false, JavaVersionUtil.JAVA_SPEC >= 11, options);

        /*
         * When the context is hosted, i.e., ahead-of-time compilation, and after the analysis we
         * need the hosted meta access.
         */
        MetaAccessProvider pluginsMetaAccess;
        if (reason == ParsingReason.PointsToAnalysis || reason == ParsingReason.JITCompilation) {
            pluginsMetaAccess = aMetaAccess;
        } else {
            VMError.guarantee(reason == ParsingReason.AOTCompilation);
            pluginsMetaAccess = hMetaAccess;
        }

        assert pluginsMetaAccess != null;
        SubstrateGraphBuilderPlugins.registerInvocationPlugins(annotationSubstitutionProcessor, pluginsMetaAccess,
                        hostedSnippetReflection, plugins.getInvocationPlugins(), replacements, reason);

        featureHandler.forEachGraalFeature(feature -> feature.registerInvocationPlugins(providers, hostedSnippetReflection, plugins, reason));

        providers.setGraphBuilderPlugins(plugins);
        replacements.setGraphBuilderPlugins(plugins);
        if (runtimeConfig != null && runtimeConfig.getProviders() instanceof HostedProviders) {
            ((HostedProviders) runtimeConfig.getProviders()).setGraphBuilderPlugins(plugins);
            for (SubstrateBackend backend : runtimeConfig.getBackends()) {
                ((HostedProviders) backend.getProviders()).setGraphBuilderPlugins(plugins);
            }
        }
    }

    @SuppressWarnings("try")
    public static void registerReplacements(DebugContext debug, FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, Providers providers,
                    SnippetReflectionProvider snippetReflection, boolean hosted, boolean initForeignCalls) {
        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();

        Providers runtimeCallProviders = runtimeConfig != null ? runtimeConfig.getBackendForNormalMethod().getProviders() : providers;
        SubstrateForeignCallsProvider foreignCallsProvider = (SubstrateForeignCallsProvider) providers.getForeignCalls();
        if (initForeignCalls) {
            SnippetRuntime.registerForeignCalls(runtimeCallProviders, foreignCallsProvider);
        }
        featureHandler.forEachGraalFeature(feature -> feature.registerForeignCalls(runtimeConfig, runtimeCallProviders, snippetReflection, foreignCallsProvider, hosted));
        try (DebugContext.Scope s = debug.scope("RegisterLowerings", new DebugDumpScope("RegisterLowerings"))) {
            SubstrateLoweringProvider lowerer = (SubstrateLoweringProvider) providers.getLowerer();
            Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings = lowerer.getLowerings();

            Iterable<DebugHandlersFactory> factories = runtimeConfig != null ? runtimeConfig.getDebugHandlersFactories() : Collections.singletonList(new GraalDebugHandlersFactory(snippetReflection));
            lowerer.setConfiguration(runtimeConfig, options, factories, providers, snippetReflection);
            TypeSnippets.registerLowerings(runtimeConfig, options, factories, providers, snippetReflection, lowerings);
            ExceptionSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);

            if (hosted) {
                DeoptHostedSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            } else {
                DeoptRuntimeSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            }

            featureHandler.forEachGraalFeature(feature -> feature.registerLowerings(runtimeConfig, options, factories, providers, snippetReflection, lowerings, hosted));
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        assert checkInvocationPluginMethods(replacements);
        replacements.encodeSnippets();
    }

    private static boolean checkInvocationPluginMethods(SubstrateReplacements replacements) {
        for (ResolvedJavaMethod method : replacements.getDelayedInvocationPluginMethods()) {
            ResolvedJavaMethod unwrapped = method;
            while (unwrapped instanceof WrappedJavaMethod) {
                unwrapped = ((WrappedJavaMethod) unwrapped).getWrapped();
            }
            if (method != unwrapped) {
                String runtimeDescriptor = method.getSignature().toMethodDescriptor();
                String hostedDescriptor = unwrapped.getSignature().toMethodDescriptor();
                if (!runtimeDescriptor.equals(hostedDescriptor)) {
                    String name = method.format("%H.%n");
                    throw new AssertionError(
                                    String.format("Cannot have invocation plugin for a method whose runtime signature is different from its hosted signature:%n" +
                                                    "            method: %s%n" +
                                                    "  hosted signature: %s%n" +
                                                    " runtime signature: %s",
                                                    name, runtimeDescriptor, hostedDescriptor));
                }
            }
            assert method == unwrapped || method.getSignature().toMethodDescriptor().equals(unwrapped.getSignature().toMethodDescriptor());
        }
        return true;
    }

    public static Suites createSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, SnippetReflectionProvider snippetReflection, boolean hosted) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();

        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();
        Suites suites = GraalConfiguration.instance().createSuites(options, hosted);
        return modifySuites(backend, suites, featureHandler, runtimeConfig, snippetReflection, hosted, false);
    }

    public static Suites createFirstTierSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, SnippetReflectionProvider snippetReflection, boolean hosted) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();
        Suites suites = GraalConfiguration.instance().createFirstTierSuites(options, hosted);
        return modifySuites(backend, suites, featureHandler, runtimeConfig, snippetReflection, hosted, true);
    }

    @SuppressWarnings("unused")
    private static Suites modifySuites(SubstrateBackend backend, Suites suites, FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig,
                    SnippetReflectionProvider snippetReflection, boolean hosted, boolean firstTier) {
        Providers runtimeCallProviders = backend.getProviders();

        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();

        ListIterator<BasePhase<? super HighTierContext>> position;
        if (hosted) {
            position = GraalConfiguration.instance().createHostedInliners(highTier);
        } else {
            /* Find the runtime inliner. */
            position = highTier.findPhase(InliningPhase.class);
        }
        if (position != null) {
            /* These two phases must be after all method inlining. */
            position.add(new DeadStoreRemovalPhase());
            position.add(new RemoveUnwindPhase());
        } else {
            /* There is no inlining, so prepend them in reverse order. */
            highTier.prependPhase(new RemoveUnwindPhase());
            highTier.prependPhase(new DeadStoreRemovalPhase());
        }

        lowTier.addBeforeLast(new OptimizeExceptionPathsPhase());

        BasePhase<CoreProviders> addressLoweringPhase = backend.newAddressLoweringPhase(runtimeCallProviders.getCodeCache());
        if (firstTier) {
            lowTier.findPhase(ExpandLogicPhase.class, true).add(addressLoweringPhase);
        } else {
            lowTier.findPhase(UseTrappingNullChecksPhase.class).add(addressLoweringPhase);
        }

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Graal inserts only loop safepoints. We want a SafepointNode also before every return.
             */
            midTier.findPhase(LoopSafepointInsertionPhase.class).add(new MethodSafepointInsertionPhase());
        } else {
            /* No need for safepoints when we have only one thread. */
            VMError.guarantee(midTier.removePhase(LoopSafepointInsertionPhase.class));
        }

        if (hosted) {
            lowTier.appendPhase(new VerifyNoGuardsPhase());

            /* Disable the Graal method inlining, since we have our own inlining system. */
            highTier.removePhase(InliningPhase.class);

            /* Remove phases that are not suitable for AOT compilation. */
            highTier.findPhase(ConvertDeoptimizeToGuardPhase.class, true).remove();
            midTier.findPhase(DeoptimizationGroupingPhase.class).remove();

        } else {
            if (firstTier) {
                ListIterator<BasePhase<? super MidTierContext>> it = midTier.findPhase(FrameStateAssignmentPhase.class);
                it.add(new CollectDeoptimizationSourcePositionsPhase());

                // On SVM, the economy configuration requires a canonicalization run at the end of
                // mid tier.
                it = midTier.findLastPhase();
                it.add(CanonicalizerPhase.create());
            } else {
                ListIterator<BasePhase<? super MidTierContext>> it = midTier.findPhase(DeoptimizationGroupingPhase.class);
                it.previous();
                it.add(new CollectDeoptimizationSourcePositionsPhase());
            }
        }

        featureHandler.forEachGraalFeature(feature -> feature.registerGraalPhases(runtimeCallProviders, snippetReflection, suites, hosted));

        if (hosted && ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(HostedOptionValues.singleton())) {
            highTier.prependPhase(new ImageBuildStatisticsCounterPhase(ImageBuildStatistics.CheckCountLocation.BEFORE_HIGH_TIER));
            highTier.prependPhase(CanonicalizerPhase.create());
            highTier.findLastPhase().add(CanonicalizerPhase.create());
            highTier.findLastPhase().add(new ImageBuildStatisticsCounterPhase(ImageBuildStatistics.CheckCountLocation.AFTER_HIGH_TIER));
        }

        return suites;
    }

    @SuppressWarnings("unused")
    public static LIRSuites createLIRSuites(FeatureHandler featureHandler, Providers providers, boolean hosted) {
        LIRSuites lirSuites = GraalConfiguration.instance().createLIRSuites(hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton());

        if (hosted) {
            /*
             * Even though this is a verification phase, we want it enabled all the time and not
             * just when assertions are enabled.
             */
            lirSuites.getPostAllocationOptimizationStage().appendPhase(new VerifyCFunctionReferenceMapsLIRPhase());
        }

        /* Add phases that just perform assertion checking. */
        assert addAssertionLIRPhases(lirSuites, hosted);
        return lirSuites;
    }

    @SuppressWarnings("unused")
    public static LIRSuites createFirstTierLIRSuites(FeatureHandler featureHandler, Providers providers, boolean hosted) {
        LIRSuites lirSuites = GraalConfiguration.instance().createFirstTierLIRSuites(hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton());

        if (hosted) {
            lirSuites.getPostAllocationOptimizationStage().appendPhase(new VerifyCFunctionReferenceMapsLIRPhase());
        }

        /* Add phases that just perform assertion checking. */
        assert addAssertionLIRPhases(lirSuites, hosted);
        return lirSuites;
    }

    private static boolean addAssertionLIRPhases(LIRSuites lirSuites, boolean hosted) {
        if (hosted) {
            lirSuites.getPostAllocationOptimizationStage().appendPhase(new VerifyDeoptFrameStatesLIRPhase());
        }
        return true;
    }

    private void checkUniverse() {
        /*
         * Check that the type states for method parameters and fields are compatible with the
         * declared type. This is required for interface types because interfaces are not trusted
         * according to the Java language specification, but we trust all interface types (see
         * HostedType.isTrustedInterfaceType)
         *
         * TODO Enable checks for non-interface types too.
         */
        for (AnalysisMethod method : aUniverse.getMethods()) {
            for (int i = 0; i < method.getTypeFlow().getOriginalMethodFlows().getParameters().length; i++) {
                TypeState parameterState = method.getTypeFlow().getParameterTypeState(bb, i);
                if (parameterState != null) {
                    AnalysisType declaredType = method.getTypeFlow().getOriginalMethodFlows().getParameter(i).getDeclaredType();
                    if (declaredType.isInterface()) {
                        TypeState declaredTypeState = declaredType.getAssignableTypes(true);
                        parameterState = TypeState.forSubtraction(bb, parameterState, declaredTypeState);
                        if (!parameterState.isEmpty()) {
                            String methodKey = method.format("%H.%n(%p)");
                            bb.getUnsupportedFeatures().addMessage(methodKey, method,
                                            "Parameter " + i + " of " + methodKey + " has declared type " + declaredType.toJavaName(true) +
                                                            " with state " + declaredTypeState + " which is incompatible with types in parameter state: " + parameterState);
                        }
                    }
                }
            }
        }
        for (AnalysisField field : aUniverse.getFields()) {
            TypeState state = field.getTypeState();
            if (state != null) {
                AnalysisType declaredType = field.getType();
                if (declaredType.isInterface()) {
                    state = TypeState.forSubtraction(bb, state, declaredType.getAssignableTypes(true));
                    if (!state.isEmpty()) {
                        String fieldKey = field.format("%H.%n");
                        bb.getUnsupportedFeatures().addMessage(fieldKey, null,
                                        "Field " + fieldKey + " has declared type " + declaredType.toJavaName(true) + " which is incompatible with types in state: " + state);
                    }
                }
            }
        }

        if (SubstrateOptions.VerifyNamingConventions.getValue()) {
            for (AnalysisMethod method : aUniverse.getMethods()) {
                if ((method.isInvoked() || method.isReachable()) && method.getAnnotation(Fold.class) == null) {
                    checkName(method.format("%H.%n(%p)"), method);
                }
            }
            for (AnalysisField field : aUniverse.getFields()) {
                if (field.isAccessed()) {
                    checkName(field.format("%H.%n"), null);
                }
            }
            for (AnalysisType type : aUniverse.getTypes()) {
                if (type.isReachable()) {
                    checkName(type.toJavaName(true), null);
                }
            }
        }

        /*
         * Entry points use a different calling convention (the native C ABI calling convention), so
         * they must not be called from other Java methods.
         */
        for (AnalysisMethod method : aUniverse.getMethods()) {
            if (method.isEntryPoint()) {
                Set<AnalysisMethod> invocations = method.getCallers();
                if (invocations.size() > 0) {
                    String name = method.format("%H.%n(%p)");
                    StringBuilder msg = new StringBuilder("Native entry point is also called from within Java. Invocations: ");
                    String sep = "";
                    for (AnalysisMethod invocation : invocations) {
                        msg.append(sep).append(invocation.format("%H.%n(%p)"));
                        sep = ", ";
                    }
                    bb.getUnsupportedFeatures().addMessage(name, method, msg.toString());
                }
            }
        }

        // the unsupported features are reported after checkUniverse is invoked
    }

    private void checkName(String name, AnalysisMethod method) {
        /*
         * We do not want any parts of the native image generator in the generated image. Therefore,
         * no element whose name contains "hosted" must be seen as reachable by the static analysis.
         * The same holds for "hotspot" elements, which come from the hosting HotSpot VM, unless
         * they are JDK internal types.
         */
        String lname = name.toLowerCase();
        if (lname.contains("hosted")) {
            bb.getUnsupportedFeatures().addMessage(name, method, "Hosted element used at run time: " + name);
        } else if (SubstrateUtil.isBuildingLibgraal() && (!name.startsWith("jdk.internal")) && (lname.contains("hotspot"))) {
            bb.getUnsupportedFeatures().addMessage(name, method, "HotSpot element used at run time: " + name);
        }
    }

    @SuppressWarnings("try")
    private void processNativeLibraryImports(NativeLibraries nativeLibs, MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {

        for (Method method : loader.findAnnotatedMethods(CConstant.class)) {
            if (LibCBase.isMethodProvidedInCurrentLibc(method)) {
                classInitializationSupport.initializeAtBuildTime(method.getDeclaringClass(), "classes with " + CConstant.class.getSimpleName() + " annotations are always initialized");
                nativeLibs.loadJavaMethod(metaAccess.lookupJavaMethod(method));
            }
        }
        for (Method method : loader.findAnnotatedMethods(CFunction.class)) {
            if (LibCBase.isMethodProvidedInCurrentLibc(method)) {
                nativeLibs.loadJavaMethod(metaAccess.lookupJavaMethod(method));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CStruct.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + CStruct.class.getSimpleName() + " are always initialized");
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(RawStructure.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + RawStructure.class.getSimpleName() + " are always initialized");
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CPointerTo.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + CPointerTo.class.getSimpleName() + " are always initialized");
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(RawPointerTo.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + RawPointerTo.class.getSimpleName() + " are always initialized");
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CEnum.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                ResolvedJavaType type = metaAccess.lookupJavaType(clazz);
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + CEnum.class.getSimpleName() + " are always initialized");
                nativeLibs.loadJavaType(type);
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CContext.class, false)) {
            if (LibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                classInitializationSupport.initializeAtBuildTime(clazz, "classes annotated with " + CContext.class.getSimpleName() + " are always initialized");
            }
        }
        nativeLibs.processCLibraryAnnotations(loader);

        nativeLibs.finish();
        nativeLibs.reportErrors();
    }

    public AbstractImage getBuiltImage() {
        return image;
    }

    public BigBang getBigbang() {
        return bb;
    }

    private void printTypes() {
        for (HostedType type : hUniverse.getTypes()) {
            System.out.format("%8d %s  ", type.getTypeID(), type.toJavaName(true));
            if (type.getSuperclass() != null) {
                System.out.format("extends %d %s  ", type.getSuperclass().getTypeID(), type.getSuperclass().toJavaName(false));
            }
            if (type.getInterfaces().length > 0) {
                System.out.print("implements ");
                String sep = "";
                for (HostedInterface interf : type.getInterfaces()) {
                    System.out.format("%s%d %s", sep, interf.getTypeID(), interf.toJavaName(false));
                    sep = ", ";
                }
                System.out.print("  ");
            }

            if (type.getWrapped().isInstantiated()) {
                System.out.print("instantiated  ");
            }
            if (type.getWrapped().isReachable()) {
                System.out.print("reachable  ");
            }

            System.out.format("type check start %d range %d slot # %d ", type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot());
            System.out.format("type check slots %s  ", slotsToString(type.getTypeCheckSlots()));
            // if (type.findLeafConcreteSubtype() != null) {
            // System.out.format("unique %d %s ", type.findLeafConcreteSubtype().getTypeID(),
            // type.findLeafConcreteSubtype().toJavaName(false));
            // }

            int le = type.getHub().getLayoutEncoding();
            if (LayoutEncoding.isPrimitive(le)) {
                System.out.print("primitive  ");
            } else if (LayoutEncoding.isInterface(le)) {
                System.out.print("interface  ");
            } else if (LayoutEncoding.isAbstract(le)) {
                System.out.print("abstract  ");
            } else if (LayoutEncoding.isInstance(le)) {
                System.out.format("instance size %d  ", LayoutEncoding.getInstanceSize(le).rawValue());
            } else if (LayoutEncoding.isObjectArray(le)) {
                System.out.format("object array base %d shift %d scale %d  ", LayoutEncoding.getArrayBaseOffset(le).rawValue(), LayoutEncoding.getArrayIndexShift(le),
                                LayoutEncoding.getArrayIndexScale(le));
            } else if (LayoutEncoding.isPrimitiveArray(le)) {
                System.out.format("primitive array base %d shift %d scale %d  ", LayoutEncoding.getArrayBaseOffset(le).rawValue(), LayoutEncoding.getArrayIndexShift(le),
                                LayoutEncoding.getArrayIndexScale(le));
            } else if (LayoutEncoding.isStoredContinuation(le)) {
                System.out.print("stored continuation  ");
            } else {
                throw VMError.shouldNotReachHere();
            }

            System.out.println();

            for (HostedType sub : type.getSubTypes()) {
                System.out.format("               s %d %s\n", sub.getTypeID(), sub.toJavaName(false));
            }
            if (type.isInterface()) {
                for (HostedMethod method : hUniverse.getMethods()) {
                    if (method.getDeclaringClass() == type) {
                        printMethod(method, -1);
                    }
                }

            } else if (type.isInstanceClass()) {

                HostedField[] fields = type.getInstanceFields(false);
                fields = Arrays.copyOf(fields, fields.length);
                Arrays.sort(fields, Comparator.comparing(HostedField::toString));
                for (HostedField field : fields) {
                    System.out.println("               f " + field.getLocation() + ": " + field.format("%T %n"));
                }
                HostedMethod[] vtable = type.getVTable();
                for (int i = 0; i < vtable.length; i++) {
                    if (vtable[i] != null) {
                        printMethod(vtable[i], i);
                    }
                }
                for (HostedMethod method : hUniverse.getMethods()) {
                    if (method.getDeclaringClass() == type && !method.hasVTableIndex()) {
                        printMethod(method, -1);
                    }
                }
            }
        }
    }

    private static void printMethod(HostedMethod method, int vtableIndex) {
        if (vtableIndex != -1) {
            System.out.print("               v " + vtableIndex + " ");
        } else {
            System.out.print("               m ");
        }
        if (method.hasVTableIndex()) {
            System.out.print(method.getVTableIndex() + " ");
        }
        System.out.print(method.format("%r %n(%p)") + ": " + method.getImplementations().length + " [");
        if (method.getImplementations().length <= 10) {
            String sep = "";
            for (HostedMethod impl : method.getImplementations()) {
                System.out.print(sep + impl.getDeclaringClass().toJavaName(false));
                sep = ", ";
            }
        }
        System.out.println("]");
    }

    void printImageBuildStatistics(String imageName) {
        Consumer<PrintWriter> reporter = ImageSingletons.lookup(ImageBuildStatistics.class).getReporter();
        String description = "image build statistics";
        if (ImageBuildStatistics.Options.ImageBuildStatisticsFile.hasBeenSet(bb.getOptions())) {
            final File file = new File(ImageBuildStatistics.Options.ImageBuildStatisticsFile.getValue(bb.getOptions()));
            ReportUtils.report(description, file.getAbsoluteFile().toPath(), reporter);
        } else {
            String name = "image_build_statistics_" + ReportUtils.extractImageName(imageName);
            String path = SubstrateOptions.Path.getValue() + File.separatorChar + "reports";
            ReportUtils.report(description, path, name, "json", reporter);
        }
    }

    private static String slotsToString(short[] slots) {
        if (slots == null) {
            return "null";
        }
        StringBuilder result = new StringBuilder();
        for (short slot : slots) {
            result.append(Short.toUnsignedInt(slot)).append(" ");
        }
        return result.toString();
    }

    public static Path generatedFiles(OptionValues optionValues) {
        String pathName = SubstrateOptions.Path.getValue(optionValues);
        Path path = FileSystems.getDefault().getPath(pathName);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        if (!Files.isDirectory(path)) {
            throw VMError.shouldNotReachHere("Output path is not a directory: " + pathName);
        }
        return path.toAbsolutePath();
    }

    private static <T extends Enum<T>> Set<T> parseCSVtoEnum(Class<T> enumType, List<String> csvEnumValues, T[] availValues) {
        EnumSet<T> result = EnumSet.noneOf(enumType);
        for (String enumValue : OptionUtils.flatten(",", csvEnumValues)) {
            try {
                result.add(Enum.valueOf(enumType, enumValue));
            } catch (IllegalArgumentException iae) {
                throw VMError.shouldNotReachHere("Value '" + enumValue + "' does not exist. Available values are:\n" + Arrays.toString(availValues));
            }
        }
        return result;
    }
}
