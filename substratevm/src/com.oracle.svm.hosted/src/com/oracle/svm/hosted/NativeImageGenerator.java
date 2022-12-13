/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.pointsto.api.PointstoOptions.UseExperimentalReachabilityAnalysis;
import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsDir;
import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsMode;
import static org.graalvm.compiler.hotspot.JVMCIVersionCheck.OPEN_LABSJDK_RELEASE_URL_PATTERN;
import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.IOException;
import java.lang.annotation.Annotation;
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
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.java.BciBlockMapping;
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
import org.graalvm.compiler.phases.FloatingGuardPhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.Speculative;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.TargetGraphBuilderPlugins;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.AnnotationAccess;
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
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.CConstantValueSupport;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.AnalysisObjectScanningObserver;
import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisFactory;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisFactory;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.reports.AnalysisReporter;
import com.oracle.graal.pointsto.typestate.DefaultAnalysisPolicy;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.graal.reachability.DirectMethodProcessingHandler;
import com.oracle.graal.reachability.MethodSummaryBasedHandler;
import com.oracle.graal.reachability.MethodSummaryProvider;
import com.oracle.graal.reachability.ReachabilityAnalysisFactory;
import com.oracle.graal.reachability.ReachabilityMethodProcessingHandler;
import com.oracle.graal.reachability.ReachabilityObjectScanner;
import com.oracle.graal.reachability.SimpleInMemoryMethodSummaryProvider;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ClassLoaderSupport;
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
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.NoLibC;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.cpufeature.RuntimeCPUFeatureCheck;
import com.oracle.svm.core.graal.EconomyGraalConfiguration;
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
import com.oracle.svm.core.graal.phases.OptimizeExceptionPathsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.SubstrateSafepointInsertionPhase;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.word.SubstrateWordOperationPlugins;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.option.HostedOptionValues;
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
import com.oracle.svm.hosted.FeatureImpl.ConcurrentAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;
import com.oracle.svm.hosted.ProgressReporter.ReporterClosable;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.NativeImagePointsToAnalysis;
import com.oracle.svm.hosted.analysis.NativeImageReachabilityAnalysisEngine;
import com.oracle.svm.hosted.analysis.SVMAnalysisMetaAccess;
import com.oracle.svm.hosted.analysis.SubstrateUnsupportedFeatures;
import com.oracle.svm.hosted.annotation.AnnotationSupport;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.c.CConstantValueSupportImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.OffsetOfSupportImpl;
import com.oracle.svm.hosted.c.SizeOfSupportImpl;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.libc.HostedLibCBase;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperSubstitutionProcessor;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.CFunctionSubstitutionProcessor;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.HostedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.NativeMethodSubstitutionProcessor;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.SubstrateGraphMakerFactory;
import com.oracle.svm.hosted.heap.SVMImageHeapScanner;
import com.oracle.svm.hosted.heap.SVMImageHeapVerifier;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
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
import com.oracle.svm.hosted.substitute.DeletedFieldsPlugin;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;
import com.oracle.svm.util.ClassUtil;
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
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NativeImageGenerator {

    protected final FeatureHandler featureHandler;
    protected final ImageClassLoader loader;
    protected final HostedOptionProvider optionProvider;
    protected final ProgressReporter reporter;

    protected AnalysisUniverse aUniverse;
    protected HostedUniverse hUniverse;
    protected Inflation bb;
    protected NativeLibraries nativeLibraries;
    protected AbstractImage image;
    protected AtomicBoolean buildStarted = new AtomicBoolean();

    protected Pair<Method, CEntryPointData> mainEntryPoint;

    protected final Map<ArtifactType, List<Path>> buildArtifacts = new EnumMap<>(ArtifactType.class);

    public NativeImageGenerator(ImageClassLoader loader, HostedOptionProvider optionProvider, Pair<Method, CEntryPointData> mainEntryPoint, ProgressReporter reporter) {
        this.loader = loader;
        this.mainEntryPoint = mainEntryPoint;
        this.featureHandler = new FeatureHandler();
        this.optionProvider = optionProvider;
        this.reporter = reporter;
        /*
         * Substrate VM parses all graphs, including snippets, early. We do not support bytecode
         * parsing at run time.
         */
        optionProvider.getHostedValues().put(GraalOptions.EagerSnippets, true);
        optionProvider.getRuntimeValues().put(GraalOptions.EagerSnippets, true);

        if (!optionProvider.getHostedValues().containsKey(BciBlockMapping.Options.MaxDuplicationFactor)) {
            /*
             * Not being able to parse irreducible loops and jsr/ret structures means that image
             * generation fails. We therefore allow a good bit of duplication.
             */
            optionProvider.getHostedValues().put(BciBlockMapping.Options.MaxDuplicationFactor, 10.0);
        }
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

    protected SubstrateTargetDescription createTarget() {
        return createTarget(loader.platform);
    }

    public static SubstrateTargetDescription createTarget(Platform platform) {
        if (includedIn(platform, Platform.AMD64.class)) {
            Architecture architecture;
            EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);
            if (NativeImageOptions.NativeArchitecture.getValue()) {
                features.addAll(((AMD64) GraalAccess.getOriginalTarget().arch).getFeatures());
            } else {
                // SSE and SSE2 are added by default as they are required by Graal
                features.add(AMD64.CPUFeature.SSE);
                features.add(AMD64.CPUFeature.SSE2);

                features.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AMD64.CPUFeature.values()));
            }
            // GR-33542 RTM is only intermittently detected and is not used by Graal
            features.remove(AMD64.CPUFeature.RTM);
            // set up the runtime checked cpu features
            EnumSet<AMD64.CPUFeature> runtimeCheckedFeatures = features.clone();
            if (NativeImageOptions.RuntimeCheckedCPUFeatures.hasBeenSet()) {
                runtimeCheckedFeatures.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.RuntimeCheckedCPUFeatures.getValue().values(), AMD64.CPUFeature.values()));
            } else {
                var disabledFeatures = RuntimeCPUFeatureCheck.getDefaultDisabledFeatures(GraalAccess.getOriginalTarget().arch);
                for (Enum<?> feature : RuntimeCPUFeatureCheck.getSupportedFeatures(GraalAccess.getOriginalTarget().arch)) {
                    if (!disabledFeatures.contains(feature)) {
                        runtimeCheckedFeatures.add((AMD64.CPUFeature) feature);
                    }
                }
            }
            architecture = new AMD64(features, AMD64CPUFeatureAccess.allAMD64Flags());
            assert architecture instanceof AMD64 : "using AMD64 platform with a different architecture";
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers: rax and xmm0
            return new SubstrateTargetDescription(architecture, true, 16, 0, deoptScratchSpace, runtimeCheckedFeatures);
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
            // runtime checked features are the same as static features on AArch64 for now
            EnumSet<AArch64.CPUFeature> runtimeCheckedFeatures = ((AArch64) architecture).getFeatures().clone();
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers: r0 and v0.
            return new SubstrateTargetDescription(architecture, true, 16, 0, deoptScratchSpace, runtimeCheckedFeatures);
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
                    EconomicSet<String> allOptionNames, TimerCollection timerCollection) {
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
                                "Please use the latest JVMCI JDK from %s.", System.getProperty("java.home"), OPEN_LABSJDK_RELEASE_URL_PATTERN);
            }

            setSystemPropertiesForImageLate(k);

            ImageSingletonsSupportImpl.HostedManagement.install(new ImageSingletonsSupportImpl.HostedManagement());

            ImageSingletons.add(ProgressReporter.class, reporter);
            ImageSingletons.add(TimerCollection.class, timerCollection);
            ImageSingletons.add(ImageBuildStatistics.TimerCollectionPrinter.class, timerCollection);
            ImageSingletons.add(AnnotationExtractor.class, loader.classLoaderSupport.annotationExtractor);
            ImageSingletons.add(BuildArtifacts.class, (type, artifact) -> buildArtifacts.computeIfAbsent(type, t -> new ArrayList<>()).add(artifact));
            ImageSingletons.add(HostedOptionValues.class, new HostedOptionValues(optionProvider.getHostedValues()));
            ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));
            try (TemporaryBuildDirectoryProviderImpl tempDirectoryProvider = new TemporaryBuildDirectoryProviderImpl()) {
                ImageSingletons.add(TemporaryBuildDirectoryProvider.class, tempDirectoryProvider);
                doRun(entryPoints, javaMainSupport, imageName, k, harnessSubstitutions, compilationExecutor, analysisExecutor);
            } finally {
                reporter.ensureCreationStageEndCompleted();
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

    public static void clearSystemPropertiesForImage() {
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY);
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY);
    }

    @SuppressWarnings("try")
    protected void doRun(Map<Method, CEntryPointData> entryPoints,
                    JavaMainSupport javaMainSupport, String imageName, NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();

        OptionValues options = HostedOptionValues.singleton();
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        try (DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(originalSnippetReflection)).build();
                        DebugCloseable featureCleanup = () -> featureHandler.forEachFeature(Feature::cleanup)) {
            setupNativeImage(options, entryPoints, javaMainSupport, harnessSubstitutions, analysisExecutor, originalSnippetReflection, debug);
            reporter.printFeatures(featureHandler.getUserSpecificFeatures());

            boolean returnAfterAnalysis = runPointsToAnalysis(imageName, options, debug);
            if (returnAfterAnalysis) {
                return;
            }

            NativeImageHeap heap;
            HostedMetaAccess hMetaAccess;
            SharedRuntimeConfigurationBuilder runtime;
            try (ReporterClosable c = reporter.printUniverse()) {
                bb.getHeartbeatCallback().run();

                hUniverse = new HostedUniverse(bb);
                hMetaAccess = new HostedMetaAccess(hUniverse, bb.getMetaAccess());
                ((SVMImageHeapScanner) aUniverse.getHeapScanner()).setHostedMetaAccess(hMetaAccess);

                BeforeUniverseBuildingAccessImpl beforeUniverseBuildingConfig = new BeforeUniverseBuildingAccessImpl(featureHandler, loader, debug, hMetaAccess);
                featureHandler.forEachFeature(feature -> feature.beforeUniverseBuilding(beforeUniverseBuildingConfig));

                new UniverseBuilder(aUniverse, bb.getMetaAccess(), hUniverse, hMetaAccess, HostedConfiguration.instance().createStaticAnalysisResultsBuilder(bb, hUniverse),
                                bb.getUnsupportedFeatures()).build(debug);

                BuildPhaseProvider.markHostedUniverseBuilt();
                ClassInitializationSupport classInitializationSupport = bb.getHostVM().getClassInitializationSupport();
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(hMetaAccess);
                runtime = new HostedRuntimeConfigurationBuilder(options, bb.getHostVM(), hUniverse, hMetaAccess, bb.getProviders(), nativeLibraries, classInitializationSupport,
                                GraalAccess.getOriginalProviders().getLoopsDataProvider(), platformConfig).build();
                registerGraphBuilderPlugins(featureHandler, runtime.getRuntimeConfig(), (HostedProviders) runtime.getRuntimeConfig().getProviders(), bb.getMetaAccess(), aUniverse,
                                hMetaAccess, hUniverse,
                                nativeLibraries, loader, ParsingReason.AOTCompilation, bb.getAnnotationSubstitutionProcessor(),
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                                ConfigurationValues.getTarget());

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

            NativeImageCodeCache codeCache;
            CompileQueue compileQueue;
            try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.COMPILE_TOTAL)) {
                compileQueue = HostedConfiguration.instance().createCompileQueue(debug, featureHandler, hUniverse, runtime, DeoptTester.enabled(), bb.getProviders().getSnippetReflection(),
                                compilationExecutor);
                compileQueue.finish(debug);

                /* release memory taken by graphs for the image writing */
                hUniverse.getMethods().forEach(HostedMethod::clear);

                try (ProgressReporter.ReporterClosable ac = reporter.printLayouting()) {
                    codeCache = NativeImageCodeCacheFactory.get().newCodeCache(compileQueue, heap, loader.platform,
                                    ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                    codeCache.layoutConstants();
                    codeCache.layoutMethods(debug, bb, compilationExecutor);
                }

                BuildPhaseProvider.markCompilationFinished();
                AfterCompilationAccessImpl config = new AfterCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, compileQueue.getCompilations(), codeCache, heap, debug, runtime);
                featureHandler.forEachFeature(feature -> feature.afterCompilation(config));
            }
            CodeCacheProvider codeCacheProvider = runtime.getRuntimeConfig().getBackendForNormalMethod().getProviders().getCodeCache();
            reporter.printCreationStart();
            try (Indent indent = debug.logAndIndent("create native image")) {
                try (DebugContext.Scope buildScope = debug.scope("CreateImage", codeCacheProvider)) {
                    try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.IMAGE)) {
                        bb.getHeartbeatCallback().run();

                        buildNativeImageHeap(heap, codeCache);

                        AfterHeapLayoutAccessImpl config = new AfterHeapLayoutAccessImpl(featureHandler, loader, heap, hMetaAccess, debug);
                        featureHandler.forEachFeature(feature -> feature.afterHeapLayout(config));

                        createAbstractImage(k, hostedEntryPoints, heap, hMetaAccess, codeCache);
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

            ProgressReporter.singleton().createBreakdowns(hMetaAccess, compileQueue.getCompilationTasks(), image.getHeap().getObjects());
            compileQueue.purge();

            int numCompilations = codeCache.getOrderedCompilations().size();

            try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.WRITE)) {
                bb.getHeartbeatCallback().run();
                BeforeImageWriteAccessImpl beforeConfig = new BeforeImageWriteAccessImpl(featureHandler, loader, imageName, image,
                                runtime.getRuntimeConfig(), aUniverse, hUniverse, optionProvider, hMetaAccess, debug);
                featureHandler.forEachFeature(feature -> feature.beforeImageWrite(beforeConfig));

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
            reporter.printCreationEnd(image.getImageFileSize(), heap.getObjectCount(), image.getImageHeapSize(), codeCache.getCodeAreaSize(),
                            numCompilations, image.getDebugInfoSize());
        }
    }

    protected void buildNativeImageHeap(NativeImageHeap heap, NativeImageCodeCache codeCache) {
        // Start building the model of the native image heap.
        heap.addInitialObjects();
        // Then build the model of the code cache, which can
        // add objects to the native image heap.
        codeCache.addConstantsToHeap();
        // Finish building the model of the native image heap.
        heap.addTrailingObjects();
    }

    protected void createAbstractImage(NativeImageKind k, List<HostedMethod> hostedEntryPoints, NativeImageHeap heap, HostedMetaAccess hMetaAccess, NativeImageCodeCache codeCache) {
        this.image = AbstractImage.create(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader.getClassLoader());
    }

    @SuppressWarnings("try")
    protected boolean runPointsToAnalysis(String imageName, OptionValues options, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("run analysis")) {
            try (Indent ignored1 = debug.logAndIndent("process analysis initializers")) {
                BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                bb.getHostVM().getClassInitializationSupport().setConfigurationSealed(true);
            }

            try (ReporterClosable c = ProgressReporter.singleton().printAnalysis(bb.getUniverse(), nativeLibraries.getLibraries())) {
                DuringAnalysisAccessImpl config = new DuringAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                try {
                    ConcurrentAnalysisAccessImpl concurrentConfig = new ConcurrentAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                    aUniverse.setConcurrentAnalysisAccess(concurrentConfig);
                    bb.runAnalysis(debug, (universe) -> {
                        try (StopTimer t2 = TimerCollection.createTimerAndStart(TimerCollection.Registry.FEATURES)) {
                            bb.getHostVM().notifyClassReachabilityListener(universe, config);
                            featureHandler.forEachFeature(feature -> feature.duringAnalysis(config));
                        }
                        return !config.getAndResetRequireAnalysisIteration();
                    });
                } catch (AnalysisError e) {
                    throw UserError.abort(e, "Analysis step failed. Reason: %s.", e.getMessage());
                }
                assert verifyAssignableTypes();

                /*
                 * Libraries defined via @CLibrary annotations are added at the end of the list of
                 * libraries so that the written object file AND the static JDK libraries can depend
                 * on them.
                 */
                nativeLibraries.processAnnotated();

                BuildPhaseProvider.markAnalysisFinished();
                AfterAnalysisAccessImpl postConfig = new AfterAnalysisAccessImpl(featureHandler, loader, bb, debug);
                featureHandler.forEachFeature(feature -> feature.afterAnalysis(postConfig));

                checkUniverse();

                /* report the unsupported features by throwing UnsupportedFeatureException */
                bb.getUnsupportedFeatures().report(bb);
                bb.checkUserLimitations();

                bb.afterAnalysis();
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
            AnalysisReporter.printAnalysisReports(imageName, options, SubstrateOptions.reportsPath(), bb);
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
    protected boolean verifyAssignableTypes() {
        /*
         * This verification has quadratic complexity, so do it only once after the static analysis
         * has finished, and can be disabled with an option.
         */
        if (SubstrateOptions.DisableTypeIdResultVerification.getValue()) {
            return true;
        }
        try (StopTimer t = TimerCollection.createTimerAndStart("(verifyAssignableTypes)")) {
            return AnalysisType.verifyAssignableTypes(bb);
        }
    }

    @SuppressWarnings("try")
    protected void setupNativeImage(OptionValues options, Map<Method, CEntryPointData> entryPoints, JavaMainSupport javaMainSupport,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool analysisExecutor, SnippetReflectionProvider originalSnippetReflection, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("setup native-image builder")) {
            try (StopTimer ignored1 = TimerCollection.createTimerAndStart(TimerCollection.Registry.SETUP)) {
                SubstrateTargetDescription target = createTarget();
                ImageSingletons.add(Platform.class, loader.platform);
                ImageSingletons.add(SubstrateTargetDescription.class, target);

                ImageSingletons.add(SubstrateOptions.ReportingSupport.class, new SubstrateOptions.ReportingSupport(
                                DiagnosticsMode.getValue() ? DiagnosticsDir.getValue() : Paths.get("reports").toString()));

                if (javaMainSupport != null) {
                    ImageSingletons.add(JavaMainSupport.class, javaMainSupport);
                }

                Providers originalProviders = GraalAccess.getOriginalProviders();
                MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();

                ClassLoaderSupportImpl classLoaderSupport = new ClassLoaderSupportImpl(loader.classLoaderSupport);
                ImageSingletons.add(ClassLoaderSupport.class, classLoaderSupport);
                ImageSingletons.add(LinkAtBuildTimeSupport.class, new LinkAtBuildTimeSupport(loader, classLoaderSupport));

                ClassInitializationSupport classInitializationSupport = ClassInitializationSupport.create(originalMetaAccess, loader);
                ImageSingletons.add(RuntimeClassInitializationSupport.class, classInitializationSupport);
                ClassInitializationFeature.processClassInitializationOptions(classInitializationSupport);

                if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(options)) {
                    ImageSingletons.add(ImageBuildStatistics.class, new ImageBuildStatistics());
                }

                if (SubstrateOptions.useEconomyCompilerConfig()) {
                    GraalConfiguration.setHostedInstanceIfEmpty(new EconomyGraalConfiguration());
                }

                /* Init the BuildPhaseProvider before any features need it. */
                BuildPhaseProvider.init();

                featureHandler.registerFeatures(loader, debug);
                AfterRegistrationAccessImpl access = new AfterRegistrationAccessImpl(featureHandler, loader, originalMetaAccess, mainEntryPoint, debug);
                featureHandler.forEachFeature(feature -> feature.afterRegistration(access));
                setDefaultLibCIfMissing();
                if (!Pair.<Method, CEntryPointData> empty().equals(access.getMainEntryPoint())) {
                    setAndVerifyMainEntryPoint(access, entryPoints);
                }
                registerEntryPoints(entryPoints);

                setDefaultConfiguration();

                AnnotationSubstitutionProcessor annotationSubstitutions = createAnnotationSubstitutionProcessor(originalMetaAccess, loader, classInitializationSupport);
                CEnumCallWrapperSubstitutionProcessor cEnumProcessor = new CEnumCallWrapperSubstitutionProcessor();
                aUniverse = createAnalysisUniverse(options, target, loader, originalMetaAccess, originalSnippetReflection, annotationSubstitutions, cEnumProcessor,
                                classInitializationSupport, Collections.singletonList(harnessSubstitutions));

                AnalysisMetaAccess aMetaAccess = new SVMAnalysisMetaAccess(aUniverse, originalMetaAccess);

                AnalysisConstantReflectionProvider aConstantReflection = new AnalysisConstantReflectionProvider(
                                aUniverse, aMetaAccess, originalProviders.getConstantReflection(), classInitializationSupport);
                WordTypes aWordTypes = new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind());
                HostedSnippetReflectionProvider aSnippetReflection = new HostedSnippetReflectionProvider(aWordTypes);

                ForeignCallsProvider aForeignCalls = new SubstrateForeignCallsProvider(aMetaAccess, null);
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(aMetaAccess);
                bb = createBigBang(options, target, aUniverse, analysisExecutor, loader.watchdog::recordActivity, aMetaAccess, aConstantReflection, aWordTypes, aSnippetReflection,
                                annotationSubstitutions, aForeignCalls, classInitializationSupport, originalProviders, platformConfig);
                aUniverse.setBigBang(bb);

                /* Create the HeapScanner and install it into the universe. */
                ImageHeap imageHeap = new ImageHeap();
                ObjectScanningObserver aScanningObserver;
                if (!UseExperimentalReachabilityAnalysis.getValue(options)) {
                    aScanningObserver = new AnalysisObjectScanningObserver(bb);
                } else {
                    aScanningObserver = new ReachabilityObjectScanner(bb, aMetaAccess);
                }
                ImageHeapScanner heapScanner = new SVMImageHeapScanner(bb, imageHeap, loader, aMetaAccess, aSnippetReflection, aConstantReflection, aScanningObserver);
                aUniverse.setHeapScanner(heapScanner);
                HeapSnapshotVerifier heapVerifier = new SVMImageHeapVerifier(bb, imageHeap, heapScanner);
                aUniverse.setHeapVerifier(heapVerifier);

                /* Register already created types as assignable. */
                aUniverse.getTypes().forEach(t -> {
                    t.registerAsAssignable(bb);
                    if (t.isReachable()) {
                        bb.onTypeInitialized(t);
                    }
                });

                boolean withoutCompilerInvoker = CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration.getValue() ||
                                (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue() && CAnnotationProcessorCache.Options.UseCAPCache.getValue());

                if (!withoutCompilerInvoker) {
                    CCompilerInvoker compilerInvoker = CCompilerInvoker.create(ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                    compilerInvoker.verifyCompiler();
                    ImageSingletons.add(CCompilerInvoker.class, compilerInvoker);
                }

                nativeLibraries = setupNativeLibraries(aConstantReflection, aMetaAccess, aSnippetReflection, cEnumProcessor, classInitializationSupport, debug);
                try (Indent ignored2 = debug.logAndIndent("process startup initializers")) {
                    FeatureImpl.DuringSetupAccessImpl config = new FeatureImpl.DuringSetupAccessImpl(featureHandler, loader, bb, debug);
                    featureHandler.forEachFeature(feature -> feature.duringSetup(config));
                }

                initializeBigBang(bb, options, featureHandler, nativeLibraries, debug, aMetaAccess, aUniverse.getSubstitutions(), loader, true,
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()));
                registerEntryPointStubs(entryPoints);
            }

            ProgressReporter.singleton().printInitializeEnd();
        }
    }

    protected void registerEntryPointStubs(Map<Method, CEntryPointData> entryPoints) {
        entryPoints.forEach((method, entryPointData) -> CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> entryPointData));
    }

    protected void setDefaultConfiguration() {
        /*
         * Check if any configuration factory class was registered. If not, register the basic one.
         */
        HostedConfiguration.setDefaultIfEmpty();
        GraalConfiguration.setDefaultIfEmpty();
    }

    protected SubstratePlatformConfigurationProvider getPlatformConfig(MetaAccessProvider aMetaAccess) {
        BarrierSet barrierSet = ImageSingletons.lookup(BarrierSetProvider.class).createBarrierSet(aMetaAccess);
        return new SubstratePlatformConfigurationProvider(barrierSet);
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
                    ClassInitializationSupport classInitializationSupport, List<SubstitutionProcessor> additionalSubstitutions) {
        UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = createAutomaticUnsafeSubstitutions(originalSnippetReflection, annotationSubstitutions);
        SubstitutionProcessor aSubstitutions = createAnalysisSubstitutionProcessor(originalMetaAccess, originalSnippetReflection, cEnumProcessor, automaticSubstitutions,
                        annotationSubstitutions, additionalSubstitutions);

        SVMHost hostVM = HostedConfiguration.instance().createHostVM(options, loader.getClassLoader(), classInitializationSupport, automaticSubstitutions, loader.platform, originalSnippetReflection);

        automaticSubstitutions.init(loader, originalMetaAccess);
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);
        AnalysisFactory analysisFactory;
        if (UseExperimentalReachabilityAnalysis.getValue(options)) {
            analysisFactory = new ReachabilityAnalysisFactory();
        } else {
            analysisFactory = new PointsToAnalysisFactory();
        }
        return new AnalysisUniverse(hostVM, target.wordJavaKind, analysisPolicy, aSubstitutions, originalMetaAccess, originalSnippetReflection,
                        new SubstrateSnippetReflectionProvider(new SubstrateWordTypes(originalMetaAccess, FrameAccess.getWordKind())), analysisFactory);
    }

    public static AnnotationSubstitutionProcessor createAnnotationSubstitutionProcessor(MetaAccessProvider originalMetaAccess, ImageClassLoader loader,
                    ClassInitializationSupport classInitializationSupport) {
        AnnotationSubstitutionProcessor annotationSubstitutions = new AnnotationSubstitutionProcessor(loader, originalMetaAccess, classInitializationSupport);
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
            bb.registerTypeAsInHeap(bb.addRootClass(Object.class, false, false), "root class");
            bb.addRootField(DynamicHub.class, "vtable");
            bb.registerTypeAsInHeap(bb.addRootClass(String.class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(String[].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootField(String.class, "value"), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(long[].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(byte[].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(byte[][].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(Object[].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(CFunctionPointer[].class, false, false), "root class");
            bb.registerTypeAsInHeap(bb.addRootClass(PointerBase[].class, false, false), "root class");

            bb.addRootMethod(ReflectionUtil.lookupMethod(SubstrateArraycopySnippets.class, "doArraycopy", Object.class, int.class, Object.class, int.class, int.class), true);
            bb.addRootMethod(ReflectionUtil.lookupMethod(Object.class, "getClass"), true);

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bb.addRootClass(kind.toJavaClass(), false, true);
                    bb.addRootClass(kind.toBoxedJavaClass(), false, true).registerAsInHeap("root class");
                    bb.addRootField(kind.toBoxedJavaClass(), "value");
                    bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass()), true);
                    bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value"), true);
                    /*
                     * Register the cache location as reachable.
                     * BoxingSnippets$Templates#getCacheLocation accesses the cache field.
                     */
                    Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
                    if (innerClasses != null && innerClasses.length > 0) {
                        bb.getMetaAccess().lookupJavaType(innerClasses[0]).registerAsReachable("inner class of root class");
                    }
                }
            }
            /* SubstrateTemplates#toLocationIdentity accesses the Counter.value field. */
            bb.getMetaAccess().lookupJavaType(JavaKind.Void.toJavaClass()).registerAsReachable("root class");
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.util.Counter.class).registerAsReachable("root class");
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.allocationprofile.AllocationCounter.class).registerAsReachable("root class");

            NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, null, null, nativeLibraries, loader, ParsingReason.PointsToAnalysis,
                            bb.getAnnotationSubstitutionProcessor(), classInitializationPlugin, ConfigurationValues.getTarget());
            registerReplacements(debug, featureHandler, null, aProviders, true, initForeignCalls);

            Collection<StructuredGraph> snippetGraphs = aReplacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options), options);
            if (bb instanceof NativeImagePointsToAnalysis) {
                for (StructuredGraph graph : snippetGraphs) {
                    MethodTypeFlowBuilder methodTypeFlowBuilder = HostedConfiguration.instance().createMethodTypeFlowBuilder(((NativeImagePointsToAnalysis) bb), graph);
                    methodTypeFlowBuilder.registerUsedElements(false);
                }
            } else if (bb instanceof NativeImageReachabilityAnalysisEngine) {
                NativeImageReachabilityAnalysisEngine reachabilityAnalysis = (NativeImageReachabilityAnalysisEngine) bb;
                for (StructuredGraph graph : snippetGraphs) {
                    reachabilityAnalysis.processGraph(graph);
                }
            } else {
                throw VMError.shouldNotReachHere("Unknown analysis type - please specify how to handle snippets");
            }
        }
    }

    public static Inflation createBigBang(OptionValues options, TargetDescription target, AnalysisUniverse aUniverse, ForkJoinPool analysisExecutor,
                    Runnable heartbeatCallback, AnalysisMetaAccess aMetaAccess, AnalysisConstantReflectionProvider aConstantReflection, WordTypes aWordTypes,
                    SnippetReflectionProvider aSnippetReflection, AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ForeignCallsProvider aForeignCalls,
                    ClassInitializationSupport classInitializationSupport, Providers originalProviders, SubstratePlatformConfigurationProvider platformConfig) {
        assert aUniverse != null : "Analysis universe must be initialized.";
        AnalysisConstantFieldProvider aConstantFieldProvider = new AnalysisConstantFieldProvider(aUniverse, aMetaAccess, aConstantReflection, classInitializationSupport);
        /*
         * Install all snippets so that the types, methods, and fields used in the snippets get
         * added to the universe.
         */
        MetaAccessExtensionProvider aMetaAccessExtensionProvider = HostedConfiguration.instance().createAnalysisMetaAccessExtensionProvider();
        LoweringProvider aLoweringProvider = SubstrateLoweringProvider.createForHosted(aMetaAccess, null, platformConfig, aMetaAccessExtensionProvider);
        StampProvider aStampProvider = new SubstrateStampProvider(aMetaAccess);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, null, aStampProvider, aSnippetReflection,
                        aWordTypes, platformConfig, aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        SubstrateReplacements aReplacments = new SubstrateReplacements(aProviders, aSnippetReflection, bytecodeProvider, target, aWordTypes, new SubstrateGraphMakerFactory(aWordTypes));
        aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, aReplacments, aStampProvider,
                        aSnippetReflection, aWordTypes, platformConfig, aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());

        if (PointstoOptions.UseExperimentalReachabilityAnalysis.getValue(options)) {
            ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler;
            if (PointstoOptions.UseReachabilityMethodSummaries.getValue(options)) {
                MethodSummaryProvider methodSummaryProvider = new SimpleInMemoryMethodSummaryProvider();
                ImageSingletons.add(MethodSummaryProvider.class, methodSummaryProvider);
                reachabilityMethodProcessingHandler = new MethodSummaryBasedHandler(methodSummaryProvider, ImageSingletons.lookup(TimerCollection.class));
            } else {
                reachabilityMethodProcessingHandler = new DirectMethodProcessingHandler();
            }
            return new NativeImageReachabilityAnalysisEngine(options, aUniverse, aProviders, annotationSubstitutionProcessor, analysisExecutor, heartbeatCallback,
                            ImageSingletons.lookup(TimerCollection.class), reachabilityMethodProcessingHandler);
        }
        return new NativeImagePointsToAnalysis(options, aUniverse, aProviders, annotationSubstitutionProcessor, analysisExecutor, heartbeatCallback, new SubstrateUnsupportedFeatures(),
                        ImageSingletons.lookup(TimerCollection.class));
    }

    @SuppressWarnings("try")
    protected NativeLibraries setupNativeLibraries(ConstantReflectionProvider aConstantReflection, MetaAccessProvider aMetaAccess,
                    SnippetReflectionProvider aSnippetReflection, CEnumCallWrapperSubstitutionProcessor cEnumProcessor, ClassInitializationSupport classInitializationSupport, DebugContext debug) {
        try (StopTimer ignored = TimerCollection.createTimerAndStart("(cap)")) {
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

    @SuppressWarnings("deprecation")
    protected void registerEntryPoints(Map<Method, CEntryPointData> entryPoints) {
        for (Method m : loader.findAnnotatedMethods(CEntryPoint.class)) {
            if (!Modifier.isStatic(m.getModifiers())) {
                throw UserError.abort("Entry point method %s.%s is not static. Add a static modifier to the method.", m.getDeclaringClass().getName(), m.getName());
            }

            Class<? extends BooleanSupplier> cEntryPointIncludeClass = m.getAnnotation(CEntryPoint.class).include();
            if (ReflectionUtil.newInstance(cEntryPointIncludeClass).getAsBoolean()) {
                entryPoints.put(m, CEntryPointData.create(m));
            }
        }
    }

    /**
     * Record callees of methods that must not allocate.
     */
    protected static void recordRestrictHeapAccessCallees(Collection<AnalysisMethod> methods) {
        ((RestrictHeapAccessCalleesImpl) ImageSingletons.lookup(RestrictHeapAccessCallees.class)).aggregateMethods(methods);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static class SubstitutionInvocationPlugins extends InvocationPlugins {

        private AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
        private EconomicMap<String, Integer> missingIntrinsicMetrics;

        SubstitutionInvocationPlugins(AnnotationSubstitutionProcessor annotationSubstitutionProcessor) {
            this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
            this.missingIntrinsicMetrics = null;
        }

        @Override
        protected void register(Type declaringClass, InvocationPlugin plugin, boolean allowOverwrite) {
            Type targetClass;
            if (declaringClass instanceof Class) {
                targetClass = annotationSubstitutionProcessor.getTargetClass((Class<?>) declaringClass);
            } else {
                targetClass = declaringClass;
            }
            super.register(targetClass, plugin, allowOverwrite);
        }

        @Override
        public void notifyNoPlugin(ResolvedJavaMethod targetMethod, OptionValues options) {
            if (Options.WarnMissingIntrinsic.getValue(options)) {
                for (Class<?> annotationType : AnnotationAccess.getAnnotationTypes(targetMethod)) {
                    if (ClassUtil.getUnqualifiedName(annotationType).contains("IntrinsicCandidate")) {
                        String method = String.format("%s.%s%s", targetMethod.getDeclaringClass().toJavaName().replace('.', '/'), targetMethod.getName(),
                                        targetMethod.getSignature().toMethodDescriptor());
                        synchronized (this) {
                            if (missingIntrinsicMetrics == null) {
                                missingIntrinsicMetrics = EconomicMap.create();
                                try {
                                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                        if (missingIntrinsicMetrics.size() > 0) {
                                            System.out.format("[Warning] Missing intrinsics found: %d\n", missingIntrinsicMetrics.size());
                                            List<Pair<String, Integer>> data = new ArrayList<>();
                                            final MapCursor<String, Integer> cursor = missingIntrinsicMetrics.getEntries();
                                            while (cursor.advance()) {
                                                data.add(Pair.create(cursor.getKey(), cursor.getValue()));
                                            }
                                            data.stream().sorted(Comparator.comparing(Pair::getRight, Comparator.reverseOrder()))
                                                            .forEach(pair -> System.out.format("        - %d occurrences during parsing: %s\n", pair.getRight(), pair.getLeft()));
                                        }
                                    }));
                                } catch (IllegalStateException e) {
                                    // shutdown in progress, no need to register the hook
                                }
                            }
                            if (missingIntrinsicMetrics.containsKey(method)) {
                                missingIntrinsicMetrics.put(method, missingIntrinsicMetrics.get(method) + 1);
                            } else {
                                System.out.format("[Warning] Missing intrinsic %s found during parsing.\n", method);
                                missingIntrinsicMetrics.put(method, 1);
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void registerGraphBuilderPlugins(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, HostedProviders providers, AnalysisMetaAccess aMetaAccess,
                    AnalysisUniverse aUniverse, HostedMetaAccess hMetaAccess, HostedUniverse hUniverse, NativeLibraries nativeLibs, ImageClassLoader loader, ParsingReason reason,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ClassInitializationPlugin classInitializationPlugin,
                    TargetDescription target) {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new SubstitutionInvocationPlugins(annotationSubstitutionProcessor));

        WordOperationPlugin wordOperationPlugin = new SubstrateWordOperationPlugins(providers.getSnippetReflection(), providers.getWordTypes());

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        plugins.appendInlineInvokePlugin(replacements);

        plugins.appendNodePlugin(new IntrinsifyMethodHandlesInvocationPlugin(reason, providers, aUniverse, hUniverse));
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(providers.getMetaAccess(), providers.getSnippetReflection()));
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin(reason));
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), providers.getWordTypes(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        plugins.appendNodePlugin(wordOperationPlugin);

        plugins.setClassInitializationPlugin(classInitializationPlugin);

        featureHandler.forEachGraalFeature(feature -> feature.registerGraphBuilderPlugins(providers, plugins, reason));

        HostedSnippetReflectionProvider hostedSnippetReflection = new HostedSnippetReflectionProvider(new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind()));

        NodeIntrinsificationProvider nodeIntrinsificationProvider;
        if (SubstrateUtil.isBuildingLibgraal()) {
            HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
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
        } else {
            nodeIntrinsificationProvider = new NodeIntrinsificationProvider(providers.getMetaAccess(), hostedSnippetReflection, providers.getForeignCalls(),
                            providers.getWordTypes(), target);
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

        final boolean useExactMathPlugins = SubstrateOptions.useLIRBackend();
        final boolean supportsStubBasedPlugins = !SubstrateOptions.useLLVMBackend();
        registerInvocationPlugins(providers.getSnippetReflection(), plugins.getInvocationPlugins(), replacements,
                        useExactMathPlugins, true, supportsStubBasedPlugins, providers.getLowerer());

        Architecture architecture = ConfigurationValues.getTarget().arch;
        OptionValues options = aUniverse.hostVM().options();
        ImageSingletons.lookup(TargetGraphBuilderPlugins.class).register(plugins, replacements, architecture,
                        /* registerForeignCallMath */ false, options);

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
        SubstrateGraphBuilderPlugins.registerInvocationPlugins(annotationSubstitutionProcessor,
                        pluginsMetaAccess,
                        hostedSnippetReflection,
                        plugins.getInvocationPlugins(),
                        replacements,
                        reason,
                        architecture,
                        supportsStubBasedPlugins);

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
                    boolean hosted, boolean initForeignCalls) {
        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();

        SubstrateForeignCallsProvider foreignCallsProvider = (SubstrateForeignCallsProvider) providers.getForeignCalls();
        if (initForeignCalls) {
            SnippetRuntime.registerForeignCalls(foreignCallsProvider);
        }
        featureHandler.forEachGraalFeature(feature -> feature.registerForeignCalls(foreignCallsProvider));
        try (DebugContext.Scope s = debug.scope("RegisterLowerings", new DebugDumpScope("RegisterLowerings"))) {
            SubstrateLoweringProvider lowerer = (SubstrateLoweringProvider) providers.getLowerer();
            Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings = lowerer.getLowerings();

            lowerer.setConfiguration(runtimeConfig, options, providers);
            TypeSnippets.registerLowerings(options, providers, lowerings);

            featureHandler.forEachGraalFeature(feature -> feature.registerLowerings(runtimeConfig, options, providers, lowerings, hosted));
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
        Suites suites;
        if (hosted) {
            suites = GraalConfiguration.hostedInstance().createSuites(HostedOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        } else {
            suites = GraalConfiguration.runtimeInstance().createSuites(RuntimeOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        }
        return modifySuites(backend, suites, featureHandler, runtimeConfig, snippetReflection, hosted, false);
    }

    public static Suites createFirstTierSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, SnippetReflectionProvider snippetReflection, boolean hosted) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        Suites suites;
        if (hosted) {
            suites = GraalConfiguration.hostedInstance().createFirstTierSuites(HostedOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        } else {
            suites = GraalConfiguration.runtimeInstance().createFirstTierSuites(RuntimeOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        }
        return modifySuites(backend, suites, featureHandler, runtimeConfig, snippetReflection, hosted, true);
    }

    @SuppressWarnings("unused")
    private static Suites modifySuites(SubstrateBackend backend, Suites suites, FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig,
                    SnippetReflectionProvider snippetReflection, boolean hosted, boolean firstTier) {
        Providers runtimeCallProviders = backend.getProviders();

        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();

        final boolean economy = firstTier || SubstrateOptions.useEconomyCompilerConfig();

        ListIterator<BasePhase<? super HighTierContext>> position;
        if (hosted) {
            position = GraalConfiguration.hostedInstance().createHostedInliners(highTier);
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
        lowTier.replacePlaceholder(AddressLoweringPhase.class, addressLoweringPhase);

        if (SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * Graal inserts only loop safepoints. We want a SafepointNode also before every return.
             * Our safepoint insertion phase inserts both kinds of safepoints.
             */
            midTier.findPhase(LoopSafepointInsertionPhase.class).set(new SubstrateSafepointInsertionPhase());
        } else {
            /* No need for safepoints when we have only one thread. */
            VMError.guarantee(midTier.removePhase(LoopSafepointInsertionPhase.class));
        }

        if (hosted) {
            lowTier.appendPhase(new VerifyNoGuardsPhase());

            /* Disable the Graal method inlining, since we have our own inlining system. */
            highTier.removePhase(InliningPhase.class);

            /* Remove phases that are not suitable for AOT compilation. */
            highTier.removePhase(ConvertDeoptimizeToGuardPhase.class);
            midTier.removePhase(DeoptimizationGroupingPhase.class);
        } else {
            if (economy) {
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

        if (hosted) {
            removePhases(suites, Speculative.class);
            removePhases(suites, FloatingGuardPhase.class);
        }

        return suites;
    }

    private static void removePhases(Suites suites, Class<?> c) {
        suites.getHighTier().removeSubTypePhases(c);
        suites.getMidTier().removeSubTypePhases(c);
        suites.getLowTier().removeSubTypePhases(c);
    }

    @SuppressWarnings("unused")
    public static LIRSuites createLIRSuites(FeatureHandler featureHandler, Providers providers, boolean hosted) {
        LIRSuites lirSuites;

        if (hosted) {
            lirSuites = GraalConfiguration.hostedInstance().createLIRSuites(HostedOptionValues.singleton());
            /*
             * Even though this is a verification phase, we want it enabled all the time and not
             * just when assertions are enabled.
             */
            lirSuites.getFinalCodeAnalysisStage().appendPhase(new VerifyCFunctionReferenceMapsLIRPhase());
        } else {
            lirSuites = GraalConfiguration.runtimeInstance().createLIRSuites(RuntimeOptionValues.singleton());
        }

        /* Add phases that just perform assertion checking. */
        assert addAssertionLIRPhases(lirSuites, hosted);
        return lirSuites;
    }

    @SuppressWarnings("unused")
    public static LIRSuites createFirstTierLIRSuites(FeatureHandler featureHandler, Providers providers, boolean hosted) {
        LIRSuites lirSuites;
        if (hosted) {
            lirSuites = GraalConfiguration.hostedInstance().createFirstTierLIRSuites(HostedOptionValues.singleton());
            lirSuites.getFinalCodeAnalysisStage().appendPhase(new VerifyCFunctionReferenceMapsLIRPhase());
        } else {
            lirSuites = GraalConfiguration.runtimeInstance().createFirstTierLIRSuites(RuntimeOptionValues.singleton());
        }

        /* Add phases that just perform assertion checking. */
        assert addAssertionLIRPhases(lirSuites, hosted);
        return lirSuites;
    }

    private static boolean addAssertionLIRPhases(LIRSuites lirSuites, boolean hosted) {
        if (hosted) {
            lirSuites.getFinalCodeAnalysisStage().appendPhase(new VerifyDeoptFrameStatesLIRPhase());
        }
        return true;
    }

    private static String format(BigBang bb, TypeState state) {
        return state.typesStream(bb).map(t -> t.toJavaName(true)).collect(Collectors.joining(","));
    }

    private void checkUniverse() {
        if (bb instanceof NativeImagePointsToAnalysis) {
            NativeImagePointsToAnalysis bigbang = (NativeImagePointsToAnalysis) this.bb;
            /*
             * Check that the type states for method parameters and fields are compatible with the
             * declared type. This is required for interface types because interfaces are not
             * trusted according to the Java language specification, but we trust all interface
             * types (see HostedType.isTrustedInterfaceType)
             *
             * TODO Enable checks for non-interface types too.
             */
            for (AnalysisMethod m : aUniverse.getMethods()) {
                PointsToAnalysisMethod method = PointsToAnalysis.assertPointsToAnalysisMethod(m);
                for (TypeFlow<?> parameter : method.getTypeFlow().getParameters()) {
                    TypeState parameterState = method.getTypeFlow().foldTypeFlow(bigbang, parameter);
                    if (parameterState != null) {
                        AnalysisType declaredType = parameter.getDeclaredType();
                        if (declaredType.isInterface()) {
                            TypeState declaredTypeState = declaredType.getAssignableTypes(true);
                            parameterState = TypeState.forSubtraction(bigbang, parameterState, declaredTypeState);
                            if (!parameterState.isEmpty()) {
                                String methodKey = method.format("%H.%n(%p)");
                                bigbang.getUnsupportedFeatures().addMessage(methodKey, method,
                                                "Parameter " + ((FormalParamTypeFlow) parameter).position() + " of " + methodKey + " has declared type " + declaredType.toJavaName(true) +
                                                                ", with assignable types: " + format(bb, declaredTypeState) +
                                                                ", which is incompatible with analysis inferred types: " + format(bb, parameterState) + ".");
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
                        TypeState declaredTypeState = declaredType.getAssignableTypes(true);
                        state = TypeState.forSubtraction(bigbang, state, declaredTypeState);
                        if (!state.isEmpty()) {
                            String fieldKey = field.format("%H.%n");
                            bigbang.getUnsupportedFeatures().addMessage(fieldKey, null,
                                            "Field " + fieldKey + " has declared type " + declaredType.toJavaName(true) +
                                                            ", with assignable types: " + format(bb, declaredTypeState) +
                                                            ", which is incompatible with analysis inferred types: " + format(bb, state) + ".");
                        }
                    }
                }
            }
        }

        if (SubstrateOptions.VerifyNamingConventions.getValue()) {
            for (AnalysisMethod method : aUniverse.getMethods()) {
                if ((method.isInvoked() || method.isReachable()) && method.getAnnotation(Fold.class) == null) {
                    checkName(method.format("%H.%n(%p)"), method, bb);
                }
            }
            for (AnalysisField field : aUniverse.getFields()) {
                if (field.isAccessed()) {
                    checkName(field.format("%H.%n"), null, bb);
                }
            }
            for (AnalysisType type : aUniverse.getTypes()) {
                if (type.isReachable()) {
                    checkName(type.toJavaName(true), null, bb);
                }
            }
        }

        checkForInvalidCallsToEntryPoints();
    }

    /**
     * Entry points use a different calling convention (the native C ABI calling convention), so
     * they must not be called from other Java methods.
     */
    protected void checkForInvalidCallsToEntryPoints() {
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

    public static void checkName(String name, AnalysisMethod method, BigBang bb) {
        /*
         * We do not want any parts of the native image generator in the generated image. Therefore,
         * no element whose name contains "hosted" must be seen as reachable by the static analysis.
         * The same holds for "hotspot" elements, which come from the hosting HotSpot VM, unless
         * they are JDK internal types.
         */
        String lname = name.toLowerCase();
        String message = null;
        if (lname.contains("hosted")) {
            message = "Hosted element used at run time: " + name;
        } else if (!name.startsWith("jdk.internal") && lname.contains("hotspot")) {
            message = "HotSpot element used at run time: " + name;
        }

        if (message != null) {
            if (bb != null) {
                bb.getUnsupportedFeatures().addMessage(name, method, message);
            } else {
                throw new UnsupportedFeatureException(message);
            }
        }
    }

    @SuppressWarnings("try")
    protected void processNativeLibraryImports(NativeLibraries nativeLibs, MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {

        for (Method method : loader.findAnnotatedMethods(CConstant.class)) {
            if (HostedLibCBase.isMethodProvidedInCurrentLibc(method)) {
                initializeAtBuildTime(method.getDeclaringClass(), classInitializationSupport, CConstant.class);
                nativeLibs.loadJavaMethod(metaAccess.lookupJavaMethod(method));
            }
        }
        for (Method method : loader.findAnnotatedMethods(CFunction.class)) {
            if (HostedLibCBase.isMethodProvidedInCurrentLibc(method)) {
                nativeLibs.loadJavaMethod(metaAccess.lookupJavaMethod(method));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CStruct.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                initializeAtBuildTime(clazz, classInitializationSupport, CStruct.class);
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(RawStructure.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                initializeAtBuildTime(clazz, classInitializationSupport, RawStructure.class);
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CPointerTo.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                initializeAtBuildTime(clazz, classInitializationSupport, CPointerTo.class);
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(RawPointerTo.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                initializeAtBuildTime(clazz, classInitializationSupport, RawPointerTo.class);
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CEnum.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                ResolvedJavaType type = metaAccess.lookupJavaType(clazz);
                initializeAtBuildTime(clazz, classInitializationSupport, CEnum.class);
                nativeLibs.loadJavaType(type);
            }
        }
        for (Class<?> clazz : loader.findAnnotatedClasses(CContext.class, false)) {
            if (HostedLibCBase.isTypeProvidedInCurrentLibc(clazz)) {
                initializeAtBuildTime(clazz, classInitializationSupport, CContext.class);
            }
        }
        nativeLibs.processCLibraryAnnotations(loader);

        nativeLibs.finish();
        nativeLibs.reportErrors();
    }

    protected static void initializeAtBuildTime(Class<?> clazz, ClassInitializationSupport classInitializationSupport, Class<? extends Annotation> annotationForMessage) {
        String message = "classes annotated with " + ClassUtil.getUnqualifiedName(annotationForMessage) + " are always initialized at image build time";
        classInitializationSupport.initializeAtBuildTime(clazz, message);
        classInitializationSupport.forceInitializeHosted(clazz, message, false);
    }

    public AbstractImage getBuiltImage() {
        return image;
    }

    public BigBang getBigbang() {
        return bb;
    }

    public Map<ArtifactType, List<Path>> getBuildArtifacts() {
        return buildArtifacts;
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
            } else if (LayoutEncoding.isPureInstance(le)) {
                System.out.format("instance size %d  ", LayoutEncoding.getPureInstanceSize(le).rawValue());
            } else if (LayoutEncoding.isArrayLike(le)) {
                String arrayType = LayoutEncoding.isHybrid(le) ? "hybrid" : "array";
                String elements = LayoutEncoding.isArrayLikeWithPrimitiveElements(le) ? "primitives" : "objects";
                System.out.format("%s containing %s, base %d shift %d scale %d  ", arrayType, elements, LayoutEncoding.getArrayBaseOffset(le).rawValue(), LayoutEncoding.getArrayIndexShift(le),
                                LayoutEncoding.getArrayIndexScale(le));

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
        for (String enumValue : csvEnumValues) {
            try {
                result.add(Enum.valueOf(enumType, enumValue));
            } catch (IllegalArgumentException iae) {
                throw VMError.shouldNotReachHere("Value '" + enumValue + "' does not exist. Available values are:\n" + Arrays.toString(availValues));
            }
        }
        return result;
    }
}
