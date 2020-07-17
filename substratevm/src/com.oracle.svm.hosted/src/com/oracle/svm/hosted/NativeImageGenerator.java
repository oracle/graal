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

import static org.graalvm.compiler.hotspot.JVMCIVersionCheck.JVMCI11_RELEASES_URL;
import static org.graalvm.compiler.hotspot.JVMCIVersionCheck.JVMCI8_RELEASES_URL;
import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.ExpandLogicPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
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
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.Feature.OnAnalysisExitAccess;
import org.graalvm.nativeimage.impl.CConstantValueSupport;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;
import org.graalvm.nativeimage.impl.SizeOfSupport;
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
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.c.libc.NoLibC;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.c.struct.OffsetOf;
import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.jdk.ArraycopySnippets;
import com.oracle.svm.core.graal.lir.VerifyCFunctionReferenceMapsLIRPhase;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.phases.CollectDeoptimizationSourcePositionsPhase;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.MethodSafepointInsertionPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionCallsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.snippets.DeoptHostedSnippets;
import com.oracle.svm.core.graal.snippets.DeoptRuntimeSnippets;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.ExceptionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.stackvalue.StackValuePhase;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.jdk.LocalizationFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
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
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.SVMAnalysisMetaAccess;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
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
import com.oracle.svm.hosted.image.AbstractBootImage;
import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;
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

    private final FeatureHandler featureHandler;
    private final ImageClassLoader loader;
    private final HostedOptionProvider optionProvider;

    private ForkJoinPool imageBuildPool;
    private DeadlockWatchdog watchdog;
    private AnalysisUniverse aUniverse;
    private HostedUniverse hUniverse;
    private Inflation bigbang;
    private NativeLibraries nativeLibraries;
    private AbstractBootImage image;
    private AtomicBoolean buildStarted = new AtomicBoolean();

    private Pair<Method, CEntryPointData> mainEntryPoint;
    private TemporaryBuildDirectoryProviderImpl buildDirectoryProvider;

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

    public static Platform defaultPlatform(ClassLoader classLoader) {
        /*
         * We cannot use a regular hosted option for the platform class: The code that instantiates
         * the platform class runs before options are parsed, because option parsing depends on the
         * platform (there can be platform-specific options). So we need to use a regular system
         * property to specify a platform class explicitly on the command line.
         */
        String platformClassName = System.getProperty(Platform.PLATFORM_PROPERTY_NAME);
        if (platformClassName != null) {
            Class<?> platformClass;
            try {
                platformClass = classLoader.loadClass(platformClassName);
            } catch (ClassNotFoundException ex) {
                throw UserError.abort("Could not find platform class " + platformClassName +
                                " that was specified explicitly on the command line using the system property " + Platform.PLATFORM_PROPERTY_NAME);
            }

            Object result;
            try {
                result = ReflectionUtil.newInstance(platformClass);
            } catch (ReflectionUtilError ex) {
                throw UserError.abort(ex.getCause(), "Could not instantiate platform class " + platformClassName + ". Ensure the class is not abstract and has a no-argument constructor.");
            }

            if (!(result instanceof Platform)) {
                throw UserError.abort("Platform class " + platformClassName + " does not implement " + Platform.class.getTypeName());
            }
            return (Platform) result;
        }

        final Architecture hostedArchitecture = GraalAccess.getOriginalTarget().arch;
        final OS currentOs = OS.getCurrent();
        if (hostedArchitecture instanceof AMD64) {
            if (currentOs == OS.LINUX) {
                return new Platform.LINUX_AMD64();
            } else if (currentOs == OS.DARWIN) {
                return new Platform.DARWIN_AMD64();
            } else if (currentOs == OS.WINDOWS) {
                return new Platform.WINDOWS_AMD64();
            } else {
                throw VMError.shouldNotReachHere("Unsupported architecture/operating system: " + hostedArchitecture.getName() + "/" + currentOs.className);
            }
        } else if (hostedArchitecture instanceof AArch64) {
            if (OS.getCurrent() == OS.LINUX) {
                return new Platform.LINUX_AARCH64();
            } else {
                throw VMError.shouldNotReachHere("Unsupported architecture/operating system: " + hostedArchitecture.getName() + "/" + currentOs.className);
            }
        } else {
            throw VMError.shouldNotReachHere("Unsupported architecture: " + hostedArchitecture.getClass().getSimpleName());
        }
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

                features.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue(), AMD64.CPUFeature.values()));

                architecture = new AMD64(features, SubstrateTargetDescription.allAMD64Flags());
            }
            assert architecture instanceof AMD64 : "using AMD64 platform with a different architecture";
            boolean inlineObjects = SubstrateOptions.SpawnIsolates.getValue() && RuntimeCodeCache.Options.WriteableCodeCache.getValue();
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers: rax and xmm0
            return new SubstrateTargetDescription(architecture, true, 16, 0, inlineObjects, deoptScratchSpace);
        } else if (includedIn(platform, Platform.AARCH64.class)) {
            Architecture architecture;
            if (NativeImageOptions.NativeArchitecture.getValue()) {
                architecture = GraalAccess.getOriginalTarget().arch;
            } else {
                EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);
                features.addAll(parseCSVtoEnum(AArch64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue(), AArch64.CPUFeature.values()));
                architecture = new AArch64(features, EnumSet.noneOf(AArch64.Flag.class));
            }
            assert architecture instanceof AArch64 : "using AArch64 platform with a different architecture";
            boolean inlineObjects = SubstrateOptions.SpawnIsolates.getValue() && RuntimeCodeCache.Options.WriteableCodeCache.getValue();
            int deoptScratchSpace = 2 * 8; // Space for two 64-bit registers.
            return new SubstrateTargetDescription(architecture, true, 16, 0, inlineObjects, deoptScratchSpace);
        } else {
            throw UserError.abort("Architecture specified by platform is not supported: " + platform.getClass().getTypeName());
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

            int maxConcurrentThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(new OptionValues(optionProvider.getHostedValues()));
            this.imageBuildPool = createForkJoinPool(maxConcurrentThreads);
            imageBuildPool.submit(() -> {

                ImageSingletons.add(HostedOptionValues.class, new HostedOptionValues(optionProvider.getHostedValues()));
                ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));
                watchdog = new DeadlockWatchdog();
                try {
                    buildDirectoryProvider = new TemporaryBuildDirectoryProviderImpl();
                    ImageSingletons.add(TemporaryBuildDirectoryProvider.class, buildDirectoryProvider);
                    doRun(entryPoints, javaMainSupport, imageName, k, harnessSubstitutions, compilationExecutor, analysisExecutor);
                } catch (Throwable t) {
                    try {
                        cleanup();
                    } catch (Throwable ecleanup) {
                        t.addSuppressed(ecleanup);
                    }
                    throw t;
                } finally {
                    watchdog.close();
                }
                cleanup();
            }).get();
        } catch (InterruptedException | CancellationException e) {
            System.out.println("Interrupted!");
            throw new InterruptImageBuilding(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
        } finally {
            shutdownPoolSafe();
        }
    }

    private void cleanup() {
        if (buildDirectoryProvider != null) {
            buildDirectoryProvider.clean();
        }
        featureHandler.forEachFeature(Feature::cleanup);
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

    private ForkJoinPool createForkJoinPool(int maxConcurrentThreads) {
        ImageSingletonsSupportImpl.HostedManagement vmConfig = new ImageSingletonsSupportImpl.HostedManagement();
        ImageSingletonsSupportImpl.HostedManagement.installInThread(vmConfig);
        return new ForkJoinPool(
                        maxConcurrentThreads,
                        pool -> new ForkJoinWorkerThread(pool) {
                            @Override
                            protected void onStart() {
                                super.onStart();
                                ImageSingletonsSupportImpl.HostedManagement.installInThread(vmConfig);
                                assert loader.getClassLoader().equals(getContextClassLoader());
                            }

                            @Override
                            protected void onTermination(Throwable exception) {
                                ImageSingletonsSupportImpl.HostedManagement.clearInThread();
                                super.onTermination(exception);
                            }
                        },
                        Thread.getDefaultUncaughtExceptionHandler(),
                        false);
    }

    @SuppressWarnings("try")
    private void doRun(Map<Method, CEntryPointData> entryPoints,
                    JavaMainSupport javaMainSupport, String imageName, NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();

        OptionValues options = HostedOptionValues.singleton();
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        try (DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(originalSnippetReflection)).build()) {
            setupNativeImage(imageName, options, entryPoints, javaMainSupport, harnessSubstitutions, analysisExecutor, originalSnippetReflection, debug);

            boolean returnAfterAnalysis = runPointsToAnalysis(imageName, options, debug);
            if (returnAfterAnalysis) {
                return;
            }

            NativeImageHeap heap;
            HostedMetaAccess hMetaAccess;
            SharedRuntimeConfigurationBuilder runtime;
            try (StopTimer t = new Timer(imageName, "universe").start()) {
                hUniverse = new HostedUniverse(bigbang);
                hMetaAccess = new HostedMetaAccess(hUniverse, bigbang.getMetaAccess());

                new UniverseBuilder(aUniverse, bigbang.getMetaAccess(), hUniverse, hMetaAccess, HostedConfiguration.instance().createStaticAnalysisResultsBuilder(bigbang, hUniverse),
                                bigbang.getUnsupportedFeatures()).build(debug);

                runtime = new HostedRuntimeConfigurationBuilder(options, bigbang.getHostVM(), hUniverse, hMetaAccess, bigbang.getProviders(), nativeLibraries).build();
                registerGraphBuilderPlugins(featureHandler, runtime.getRuntimeConfig(), (HostedProviders) runtime.getRuntimeConfig().getProviders(), bigbang.getMetaAccess(), aUniverse,
                                hMetaAccess, hUniverse,
                                nativeLibraries, loader, false, true, bigbang.getAnnotationSubstitutionProcessor(), new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                                bigbang.getHostVM().getClassInitializationSupport(), ConfigurationValues.getTarget());

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
                    throw UserError.abort("Warning: no entry points found, i.e., no method annotated with @" + CEntryPoint.class.getSimpleName());
                }

                bigbang.getUnsupportedFeatures().report(bigbang);

                recordRestrictHeapAccessCallees(aUniverse.getMethods());

                /*
                 * After this point, all TypeFlow (and therefore also TypeState) objects are
                 * unreachable and can be garbage collected. This is important to keep the overall
                 * memory footprint low. However, this also means we no longer have complete call
                 * chain information. Only the summarized information stored in the
                 * StaticAnalysisResult objects is available after this point.
                 */
                bigbang.cleanupAfterAnalysis();
            } catch (UnsupportedFeatureException ufe) {
                throw FallbackFeature.reportAsFallback(ufe);
            }

            heap = new NativeImageHeap(aUniverse, hUniverse, hMetaAccess, ImageSingletons.lookup(ImageHeapLayouter.class));

            BeforeCompilationAccessImpl beforeCompilationConfig = new BeforeCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, hMetaAccess, heap, debug, runtime);
            featureHandler.forEachFeature(feature -> feature.beforeCompilation(beforeCompilationConfig));

            runtime.updateLazyState(hMetaAccess);

            NativeImageCodeCache codeCache;
            CompileQueue compileQueue;
            try (StopTimer t = new Timer(imageName, "compile").start()) {
                compileQueue = HostedConfiguration.instance().createCompileQueue(debug, featureHandler, hUniverse, runtime, DeoptTester.enabled(), bigbang.getProviders().getSnippetReflection(),
                                compilationExecutor);
                compileQueue.finish(debug);

                /* release memory taken by graphs for the image writing */
                hUniverse.getMethods().forEach(HostedMethod::clear);

                codeCache = NativeImageCodeCacheFactory.get().newCodeCache(compileQueue, heap, loader.platform,
                                ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                codeCache.layoutConstants();
                codeCache.layoutMethods(debug, imageName, bigbang, compilationExecutor);

                AfterCompilationAccessImpl config = new AfterCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, hMetaAccess, compileQueue.getCompilationTasks(), heap, debug);
                featureHandler.forEachFeature(feature -> feature.afterCompilation(config));
            }
            CodeCacheProvider codeCacheProvider = runtime.getRuntimeConfig().getBackendForNormalMethod().getProviders().getCodeCache();
            try (Indent indent = debug.logAndIndent("create native image")) {
                try (DebugContext.Scope buildScope = debug.scope("CreateBootImage", codeCacheProvider)) {
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

                        this.image = AbstractBootImage.create(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader.getClassLoader());
                        image.build(debug);
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

                AfterImageWriteAccessImpl afterConfig = new AfterImageWriteAccessImpl(featureHandler, loader, hUniverse, inv, tmpDir, image.getBootImageKind(), debug);
                featureHandler.forEachFeature(feature -> feature.afterImageWrite(afterConfig));
            }
        }
    }

    @SuppressWarnings("try")
    private boolean runPointsToAnalysis(String imageName, OptionValues options, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("run analysis")) {
            try (Indent ignored1 = debug.logAndIndent("process analysis initializers")) {
                BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bigbang, nativeLibraries, debug);
                featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                bigbang.getHostVM().getClassInitializationSupport().setConfigurationSealed(true);
            }

            try (StopTimer t = bigbang.analysisTimer.start()) {

                /*
                 * Iterate until analysis reaches a fixpoint.
                 */
                DuringAnalysisAccessImpl config = new DuringAnalysisAccessImpl(featureHandler, loader, bigbang, nativeLibraries, debug);
                int numIterations = 0;
                while (true) {
                    try (Indent indent2 = debug.logAndIndent("new analysis iteration")) {
                        /*
                         * Do the analysis (which itself is done in a similar iterative process)
                         */
                        boolean analysisChanged = bigbang.finish();

                        numIterations++;
                        if (numIterations > 1000) {
                            /*
                             * Usually there are < 10 iterations. If we have so many iterations, we
                             * probably have an endless loop (but at least we have a performance
                             * problem because we re-start the analysis so often).
                             */
                            throw UserError.abort("Static analysis did not reach a fix point after " + numIterations +
                                            " iterations because a Feature keeps requesting new analysis iterations. The analysis itself " +
                                            (analysisChanged ? "DID" : "DID NOT") + " find a change in type states in the last iteration.");
                        }

                        /*
                         * Allow features to change the universe.
                         */
                        try (StopTimer t2 = bigbang.processFeaturesTimer.start()) {
                            int numTypes = aUniverse.getTypes().size();
                            int numMethods = aUniverse.getMethods().size();
                            int numFields = aUniverse.getFields().size();

                            bigbang.getHostVM().notifyClassReachabilityListener(aUniverse, config);
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

                /*
                 * Libraries defined via @CLibrary annotations are added at the end of the list of
                 * libraries so that the written object file AND the static JDK libraries can depend
                 * on them.
                 */
                nativeLibraries.processAnnotated();

                AfterAnalysisAccessImpl postConfig = new AfterAnalysisAccessImpl(featureHandler, loader, bigbang, debug);
                featureHandler.forEachFeature(feature -> feature.afterAnalysis(postConfig));

                checkUniverse();

                bigbang.typeFlowTimer.print();
                bigbang.checkObjectsTimer.print();
                bigbang.processFeaturesTimer.print();

                /* report the unsupported features by throwing UnsupportedFeatureException */
                bigbang.getUnsupportedFeatures().report(bigbang);
                bigbang.checkUserLimitations();
            } catch (UnsupportedFeatureException ufe) {
                throw FallbackFeature.reportAsFallback(ufe);
            }
        } catch (InterruptedException ie) {
            throw new InterruptImageBuilding();
        } finally {
            OnAnalysisExitAccess onExitConfig = new OnAnalysisExitAccessImpl(featureHandler, loader, bigbang, debug);
            featureHandler.forEachFeature(feature -> feature.onAnalysisExit(onExitConfig));

            /*
             * Execute analysis reporting here. This code is executed even if unsupported features
             * are reported or the analysis fails due to any other reasons.
             */
            if (bigbang != null) {
                if (AnalysisReportsOptions.PrintAnalysisStatistics.getValue(options)) {
                    StatisticsPrinter.print(bigbang, SubstrateOptions.Path.getValue(), ReportUtils.extractImageName(imageName));
                }

                if (AnalysisReportsOptions.PrintAnalysisCallTree.getValue(options)) {
                    CallTreePrinter.print(bigbang, SubstrateOptions.Path.getValue(), ReportUtils.extractImageName(imageName));
                }

                if (AnalysisReportsOptions.PrintImageObjectTree.getValue(options)) {
                    ObjectTreePrinter.print(bigbang, SubstrateOptions.Path.getValue(), ReportUtils.extractImageName(imageName));
                    AnalysisHeapHistogramPrinter.print(bigbang, SubstrateOptions.Path.getValue(), ReportUtils.extractImageName(imageName));
                }

                if (PointstoOptions.PrintPointsToStatistics.getValue(options)) {
                    PointsToStats.report(bigbang, ReportUtils.extractImageName(imageName));
                }

                if (PointstoOptions.PrintSynchronizedAnalysis.getValue(options)) {
                    TypeState allSynchronizedTypeState = bigbang.getAllSynchronizedTypeState();
                    String typesString = allSynchronizedTypeState.closeToAllInstantiated(bigbang) ? "close to all instantiated" : //
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
    private void setupNativeImage(String imageName, OptionValues options, Map<Method, CEntryPointData> entryPoints, JavaMainSupport javaMainSupport, SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool analysisExecutor, SnippetReflectionProvider originalSnippetReflection, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("setup native-image builder")) {
            try (StopTimer ignored1 = new Timer(imageName, "setup").start()) {
                SubstrateTargetDescription target = createTarget(loader.platform);
                ImageSingletons.add(Platform.class, loader.platform);
                ImageSingletons.add(SubstrateTargetDescription.class, target);

                if (javaMainSupport != null) {
                    ImageSingletons.add(JavaMainSupport.class, javaMainSupport);
                }

                Providers originalProviders = GraalAccess.getOriginalProviders();
                MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();

                ClassInitializationSupport classInitializationSupport = new ConfigurableClassInitialization(originalMetaAccess, loader);
                ImageSingletons.add(RuntimeClassInitializationSupport.class, classInitializationSupport);
                ClassInitializationFeature.processClassInitializationOptions(classInitializationSupport);

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
                                classInitializationSupport, Collections.singletonList(harnessSubstitutions));

                AnalysisMetaAccess aMetaAccess = new SVMAnalysisMetaAccess(aUniverse, originalMetaAccess);
                AnalysisConstantReflectionProvider aConstantReflection = new AnalysisConstantReflectionProvider(aUniverse, originalProviders.getConstantReflection(), classInitializationSupport);
                WordTypes aWordTypes = new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind());
                HostedSnippetReflectionProvider aSnippetReflection = new HostedSnippetReflectionProvider((SVMHost) aUniverse.hostVM(), aWordTypes);

                if (!(NativeImageOptions.ExitAfterRelocatableImageWrite.getValue() && CAnnotationProcessorCache.Options.UseCAPCache.getValue())) {
                    CCompilerInvoker compilerInvoker = CCompilerInvoker.create(ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                    compilerInvoker.verifyCompiler();
                    ImageSingletons.add(CCompilerInvoker.class, compilerInvoker);
                }

                nativeLibraries = setupNativeLibraries(imageName, aConstantReflection, aMetaAccess, aSnippetReflection, cEnumProcessor, classInitializationSupport, debug);

                ForeignCallsProvider aForeignCalls = new SubstrateForeignCallsProvider();
                bigbang = createBigBang(options, target, aUniverse, nativeLibraries, analysisExecutor, watchdog::recordActivity, aMetaAccess, aConstantReflection, aWordTypes, aSnippetReflection,
                                annotationSubstitutions, aForeignCalls, classInitializationSupport);

                try (Indent ignored2 = debug.logAndIndent("process startup initializers")) {
                    FeatureImpl.DuringSetupAccessImpl config = new FeatureImpl.DuringSetupAccessImpl(featureHandler, loader, bigbang, debug);
                    featureHandler.forEachFeature(feature -> feature.duringSetup(config));
                }

                initializeBigBang(bigbang, options, featureHandler, nativeLibraries, debug, aMetaAccess, aUniverse.getSubstitutions(), loader, true,
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
                    ClassInitializationSupport classInitializationSupport, List<SubstitutionProcessor> additionalSubstitutions) {
        UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = createAutomaticUnsafeSubstitutions(originalSnippetReflection, annotationSubstitutions);
        SubstitutionProcessor aSubstitutions = createAnalysisSubstitutionProcessor(originalMetaAccess, originalSnippetReflection, cEnumProcessor, automaticSubstitutions,
                        annotationSubstitutions, additionalSubstitutions);

        SVMHost hostVM = new SVMHost(options, loader.getClassLoader(), classInitializationSupport, automaticSubstitutions);
        automaticSubstitutions.init(loader, originalMetaAccess, hostVM);
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);
        return new AnalysisUniverse(hostVM, target.wordJavaKind, loader.platform, analysisPolicy, aSubstitutions, originalMetaAccess, originalSnippetReflection,
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
    public static void initializeBigBang(Inflation bigbang, OptionValues options, FeatureHandler featureHandler, NativeLibraries nativeLibraries, DebugContext debug,
                    AnalysisMetaAccess aMetaAccess, SubstitutionProcessor substitutions, ImageClassLoader loader, boolean initForeignCalls, ClassInitializationPlugin classInitializationPlugin) {
        SubstrateReplacements aReplacements = bigbang.getReplacements();
        HostedProviders aProviders = bigbang.getProviders();
        AnalysisUniverse aUniverse = bigbang.getUniverse();

        /*
         * Eagerly register all target fields of recomputed value fields as unsafe accessed.
         */
        bigbang.getAnnotationSubstitutionProcessor().processComputedValueFields(bigbang);

        NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, null, null, nativeLibraries, loader, true, true,
                        bigbang.getAnnotationSubstitutionProcessor(), classInitializationPlugin, bigbang.getHostVM().getClassInitializationSupport(), ConfigurationValues.getTarget());
        registerReplacements(debug, featureHandler, null, aProviders, aProviders.getSnippetReflection(), true, initForeignCalls);

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
            bigbang.addSystemClass(Object.class, false, false).registerAsInHeap();
            bigbang.addSystemField(DynamicHub.class, "vtable");
            bigbang.addSystemClass(String.class, false, false).registerAsInHeap();
            bigbang.addSystemClass(String[].class, false, false).registerAsInHeap();
            bigbang.addSystemField(String.class, "value").registerAsInHeap();
            bigbang.addSystemClass(long[].class, false, false).registerAsInHeap();
            bigbang.addSystemClass(byte[].class, false, false).registerAsInHeap();
            bigbang.addSystemClass(byte[][].class, false, false).registerAsInHeap();
            bigbang.addSystemClass(Object[].class, false, false).registerAsInHeap();
            bigbang.addSystemClass(CFunctionPointer[].class, false, false).registerAsInHeap();
            bigbang.addSystemClass(PointerBase[].class, false, false).registerAsInHeap();

            try {
                bigbang.addRootMethod(ArraycopySnippets.class.getDeclaredMethod("doArraycopy", Object.class, int.class, Object.class, int.class, int.class));
                bigbang.addRootMethod(Object.class.getDeclaredMethod("getClass"));
            } catch (NoSuchMethodException ex) {
                throw VMError.shouldNotReachHere(ex);
            }

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bigbang.addSystemClass(kind.toJavaClass(), false, true);
                    bigbang.addSystemField(kind.toBoxedJavaClass(), "value");
                    bigbang.addSystemMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass());
                    bigbang.addSystemMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value");
                }
            }

            for (StructuredGraph graph : aReplacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options), options)) {
                new SVMMethodTypeFlowBuilder(bigbang, graph).registerUsedElements(false);
            }
        }
    }

    public static Inflation createBigBang(OptionValues options, TargetDescription target, AnalysisUniverse aUniverse, NativeLibraries nativeLibraries, ForkJoinPool analysisExecutor,
                    Runnable heartbeatCallback,
                    AnalysisMetaAccess aMetaAccess, AnalysisConstantReflectionProvider aConstantReflection, WordTypes aWordTypes, SnippetReflectionProvider aSnippetReflection,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ForeignCallsProvider aForeignCalls, ClassInitializationSupport classInitializationSupport) {
        assert aUniverse != null : "Analysis universe must be initialized.";
        assert nativeLibraries != null : "Native libraries must be set.";
        AnalysisConstantFieldProvider aConstantFieldProvider = new AnalysisConstantFieldProvider(aUniverse, aMetaAccess, aConstantReflection, classInitializationSupport);
        /*
         * Install all snippets so that the types, methods, and fields used in the snippets get
         * added to the universe.
         */
        BarrierSet barrierSet = ImageSingletons.lookup(Heap.class).createBarrierSet(aMetaAccess);
        SubstratePlatformConfigurationProvider platformConfig = new SubstratePlatformConfigurationProvider(barrierSet);
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider();
        LoweringProvider aLoweringProvider = SubstrateLoweringProvider.create(aMetaAccess, null, platformConfig, aMetaAccessExtensionProvider);
        StampProvider aStampProvider = new SubstrateStampProvider(aMetaAccess);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, null, aStampProvider, aSnippetReflection,
                        aWordTypes, platformConfig, aMetaAccessExtensionProvider);
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        SubstrateReplacements aReplacments = new SubstrateReplacements(aProviders, aSnippetReflection, bytecodeProvider, target, aWordTypes, new SubstrateGraphMakerFactory(aWordTypes));
        aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, aReplacments, aStampProvider,
                        aSnippetReflection, aWordTypes, platformConfig, aMetaAccessExtensionProvider);

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

            if (CAnnotationProcessorCache.Options.ExitAfterCAPCache.getValue()) {
                throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterCAPCache, "+"));
            }
            return nativeLibs;
        }
    }

    private void registerEntryPoints(Map<Method, CEntryPointData> entryPoints) {
        for (Method m : loader.findAnnotatedMethods(CEntryPoint.class)) {
            if (!Modifier.isStatic(m.getModifiers())) {
                throw UserError.abort("entry point method " + m.getDeclaringClass().getName() + "." + m.getName() + " is not static. Add a static modifier to the method.");
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

    public void interruptBuild() {
        shutdownPoolSafe();
    }

    private void shutdownPoolSafe() {
        if (imageBuildPool != null) {
            imageBuildPool.shutdownNow();
        }
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
                    AnalysisUniverse aUniverse, HostedMetaAccess hMetaAccess, HostedUniverse hUniverse, NativeLibraries nativeLibs, ImageClassLoader loader, boolean analysis, boolean hosted,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ClassInitializationPlugin classInitializationPlugin, ClassInitializationSupport classInitializationSupport,
                    TargetDescription target) {
        assert !analysis || hosted : "analysis must always be hosted";
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new SubstitutionInvocationPlugins(annotationSubstitutionProcessor));

        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(providers.getSnippetReflection(), providers.getWordTypes());

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        plugins.appendInlineInvokePlugin(replacements);

        plugins.appendNodePlugin(new IntrinsifyMethodHandlesInvocationPlugin(analysis, providers, aUniverse, hUniverse));
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(providers.getMetaAccess()));
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin(classInitializationSupport));
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), providers.getWordTypes(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        plugins.appendNodePlugin(wordOperationPlugin);

        plugins.setClassInitializationPlugin(classInitializationPlugin);

        featureHandler.forEachGraalFeature(feature -> feature.registerGraphBuilderPlugins(providers, plugins, analysis, hosted));

        HostedSnippetReflectionProvider hostedSnippetReflection = new HostedSnippetReflectionProvider((SVMHost) aUniverse.hostVM(), new SubstrateWordTypes(aMetaAccess, FrameAccess.getWordKind()));
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

        final boolean explicitUnsafeNullChecks = SubstrateOptions.SpawnIsolates.getValue();
        final boolean arrayEqualsSubstitution = !SubstrateOptions.useLLVMBackend();
        registerInvocationPlugins(providers.getMetaAccess(), providers.getSnippetReflection(), plugins.getInvocationPlugins(), replacements, !hosted, explicitUnsafeNullChecks,
                        arrayEqualsSubstitution);

        Architecture architecture = ConfigurationValues.getTarget().arch;
        ImageSingletons.lookup(TargetGraphBuilderPlugins.class).register(plugins, replacements, architecture,
                        explicitUnsafeNullChecks, /* registerForeignCallMath */false,
                        SubstrateOptions.EmitStringEncodingSubstitutions.getValue() && JavaVersionUtil.JAVA_SPEC >= 11,
                        JavaVersionUtil.JAVA_SPEC >= 11);

        /*
         * When the context is hosted, i.e., ahead-of-time compilation, and after the analysis we
         * need the hosted meta access.
         */
        MetaAccessProvider pluginsMetaAccess = hosted && !analysis ? hMetaAccess : aMetaAccess;
        assert pluginsMetaAccess != null;
        SubstrateGraphBuilderPlugins.registerInvocationPlugins(annotationSubstitutionProcessor, pluginsMetaAccess,
                        hostedSnippetReflection, plugins.getInvocationPlugins(), replacements, analysis);

        featureHandler.forEachGraalFeature(feature -> feature.registerInvocationPlugins(providers, hostedSnippetReflection, plugins.getInvocationPlugins(), analysis, hosted));

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
            for (SubstrateForeignCallDescriptor descriptor : SnippetRuntime.getRuntimeCalls()) {
                foreignCallsProvider.getForeignCalls().put(descriptor.getSignature(), new SubstrateForeignCallLinkage(runtimeCallProviders, descriptor));
            }
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

        ListIterator<BasePhase<? super LowTierContext>> pos = lowTier.findPhase(LoweringPhase.class);
        pos.next();
        pos.add(new StackValuePhase());

        lowTier.addBeforeLast(new OptimizeExceptionCallsPhase());

        Phase addressLoweringPhase = backend.newAddressLoweringPhase(runtimeCallProviders.getCodeCache());
        if (firstTier) {
            lowTier.findPhase(ExpandLogicPhase.class).add(addressLoweringPhase);
        } else {
            lowTier.findPhase(FixReadsPhase.class).add(addressLoweringPhase);
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
                TypeState parameterState = method.getTypeFlow().getParameterTypeState(bigbang, i);
                if (parameterState != null) {
                    AnalysisType declaredType = method.getTypeFlow().getOriginalMethodFlows().getParameter(i).getDeclaredType();
                    if (declaredType.isInterface()) {
                        TypeState declaredTypeState = declaredType.getTypeFlow(bigbang, true).getState();
                        parameterState = TypeState.forSubtraction(bigbang, parameterState, declaredTypeState);
                        if (!parameterState.isEmpty()) {
                            String methodKey = method.format("%H.%n(%p)");
                            bigbang.getUnsupportedFeatures().addMessage(methodKey, method,
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
                    state = TypeState.forSubtraction(bigbang, state, declaredType.getTypeFlow(bigbang, true).getState());
                    if (!state.isEmpty()) {
                        String fieldKey = field.format("%H.%n");
                        bigbang.getUnsupportedFeatures().addMessage(fieldKey, null,
                                        "Field " + fieldKey + " has declared type " + declaredType.toJavaName(true) + " which is incompatible with types in state: " + state);
                    }
                }
            }
        }

        if (SubstrateOptions.VerifyNamingConventions.getValue()) {
            for (AnalysisMethod method : aUniverse.getMethods()) {
                if ((method.isInvoked() || method.isImplementationInvoked()) && method.getAnnotation(Fold.class) == null) {
                    checkName(method.format("%H.%n(%p)"), method);
                }
            }
            for (AnalysisField field : aUniverse.getFields()) {
                if (field.isAccessed()) {
                    checkName(field.format("%H.%n"), null);
                }
            }
            for (AnalysisType type : aUniverse.getTypes()) {
                if ((type.isInstantiated() || type.isInTypeCheck())) {
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
                    bigbang.getUnsupportedFeatures().addMessage(name, method, msg.toString());
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
            bigbang.getUnsupportedFeatures().addMessage(name, method, "Hosted element used at run time: " + name);
        } else if (SubstrateUtil.isBuildingLibgraal() && (!name.startsWith("jdk.internal")) && (lname.contains("hotspot"))) {
            bigbang.getUnsupportedFeatures().addMessage(name, method, "HotSpot element used at run time: " + name);
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
        for (CLibrary library : loader.findAnnotations(CLibrary.class)) {
            nativeLibs.addAnnotated(library);
        }

        nativeLibs.finish();
        nativeLibs.reportErrors();
    }

    public AbstractBootImage getBuiltImage() {
        return image;
    }

    public BigBang getBigbang() {
        return bigbang;
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
            if (type.getWrapped().isInTypeCheck()) {
                System.out.print("inTypeCheck  ");
            }

            System.out.format("assignableFrom %s  ", matchesToString(type.getAssignableFromMatches()));
            System.out.format("instanceOf typeID %d, # %d  ", type.getInstanceOfFromTypeID(), type.getInstanceOfNumTypeIDs());
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
        String sep = "";
        for (HostedMethod impl : method.getImplementations()) {
            System.out.print(sep + impl.getDeclaringClass().toJavaName(false));
            sep = ", ";
        }
        System.out.println("]");
    }

    private static String matchesToString(int[] matches) {
        if (matches == null) {
            return "null";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < matches.length; i += 2) {
            result.append("[").append(matches[i]).append(", ").append(matches[i] + matches[i + 1] - 1).append("] ");
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

    private static <T extends Enum<T>> Set<T> parseCSVtoEnum(Class<T> enumType, String[] csvEnumValues, T[] availValues) {
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
