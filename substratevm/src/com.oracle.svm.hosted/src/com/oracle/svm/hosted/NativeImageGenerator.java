/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.svm.core.SubstrateOptions.LayerCreate;
import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsDir;
import static com.oracle.svm.hosted.NativeImageOptions.DiagnosticsMode;
import static jdk.graal.compiler.hotspot.JVMCIVersionCheck.OPEN_LABSJDK_RELEASE_URL_PATTERN;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
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
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;
import org.graalvm.nativeimage.impl.SizeOfSupport;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.AnalysisObjectScanningObserver;
import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
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
import com.oracle.graal.pointsto.reports.AnalysisReporter;
import com.oracle.graal.pointsto.typestate.DefaultAnalysisPolicy;
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
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.MissingRegistrationSupport;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
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
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.phases.CollectDeoptimizationSourcePositionsPhase;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionPathsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.SubstrateSafepointInsertionPhase;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.word.SubstrateWordOperationPlugins;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.ServiceCatalogSupport;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.meta.MethodOffset;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionClassFilter;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.LayeredHostedImageHeapMapCollector;
import com.oracle.svm.core.util.LayeredImageHeapMapStore;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.BuildArtifactsExporter.BuildArtifactsImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.AfterRegistrationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeHeapLayoutAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeUniverseBuildingAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.ConcurrentAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;
import com.oracle.svm.hosted.ProgressReporter.ReporterClosable;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;
import com.oracle.svm.hosted.ameta.SVMHostedValueProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.NativeImagePointsToAnalysis;
import com.oracle.svm.hosted.analysis.NativeImageReachabilityAnalysisEngine;
import com.oracle.svm.hosted.analysis.ReachabilityTracePrinter;
import com.oracle.svm.hosted.analysis.SVMAnalysisMetaAccess;
import com.oracle.svm.hosted.analysis.SubstrateUnsupportedFeatures;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.c.CConstantValueSupportImpl;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.OffsetOfSupportImpl;
import com.oracle.svm.hosted.c.SizeOfSupportImpl;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.libc.HostedLibCBase;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperSubstitutionProcessor;
import com.oracle.svm.hosted.classinitialization.ClassInitializationFeature;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.CFunctionSubstitutionProcessor;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.HostedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.NativeMethodSubstitutionProcessor;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl;
import com.oracle.svm.hosted.code.SubstrateGraphMakerFactory;
import com.oracle.svm.hosted.heap.ObservableImageHeapMapProviderImpl;
import com.oracle.svm.hosted.heap.SVMImageHeapScanner;
import com.oracle.svm.hosted.heap.SVMImageHeapVerifier;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageCodeCacheFactory;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.PreserveOptionsSupport;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.LoadImageSingletonFeature;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerLoader;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSnapshotUtil;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerWriter;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.UniverseBuilder;
import com.oracle.svm.hosted.methodhandles.SVMMethodHandleWithExceptionPlugin;
import com.oracle.svm.hosted.option.HostedOptionProvider;
import com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin;
import com.oracle.svm.hosted.phases.ConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin;
import com.oracle.svm.hosted.phases.ImageBuildStatisticsCounterPhase;
import com.oracle.svm.hosted.phases.InjectedAccessorsPlugin;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.phases.VerifyDeoptLIRFrameStatesPhase;
import com.oracle.svm.hosted.phases.VerifyNoGuardsPhase;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedFieldsPlugin;
import com.oracle.svm.hosted.substitute.DynamicHubPlugin;
import com.oracle.svm.hosted.substitute.SubstitutionInvocationPlugins;
import com.oracle.svm.hosted.util.CPUTypeAArch64;
import com.oracle.svm.hosted.util.CPUTypeAMD64;
import com.oracle.svm.hosted.util.CPUTypeRISCV64;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ImageBuildStatistics;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NativeImageSupport;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringProvider;
import jdk.graal.compiler.nodes.spi.StampProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.AddressLoweringPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeoptimizationGroupingPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.common.TransplantGraphsPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.NodeIntrinsificationProvider;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.word.WordOperationPlugin;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.riscv64.RISCV64;

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

    public NativeImageGenerator(ImageClassLoader loader, HostedOptionProvider optionProvider, Pair<Method, CEntryPointData> mainEntryPoint, ProgressReporter reporter) {
        this.loader = loader;
        this.mainEntryPoint = mainEntryPoint;
        this.featureHandler = new FeatureHandler();
        this.optionProvider = optionProvider;
        this.reporter = reporter;
        optionProvider.getHostedValues().put(CompilationAlarm.Options.CompilationNoProgressPeriod, 0D);

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
            os = OS.getCurrent().className.toLowerCase(Locale.ROOT);
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
            EnumSet<AMD64.CPUFeature> features = CPUTypeAMD64.getSelectedFeatures();
            features.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AMD64.CPUFeature.values()));
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
            AMD64 architecture = new AMD64(features);
            return new SubstrateTargetDescription(architecture, true, 16, 0, runtimeCheckedFeatures);
        } else if (includedIn(platform, Platform.AARCH64.class)) {
            EnumSet<AArch64.CPUFeature> features = CPUTypeAArch64.getSelectedFeatures();
            features.addAll(parseCSVtoEnum(AArch64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AArch64.CPUFeature.values()));
            AArch64 architecture = new AArch64(features);
            // runtime checked features are the same as static features on AArch64 for now
            EnumSet<AArch64.CPUFeature> runtimeCheckedFeatures = architecture.getFeatures().clone();
            return new SubstrateTargetDescription(architecture, true, 16, 0, runtimeCheckedFeatures);
        } else if (includedIn(platform, Platform.RISCV64.class)) {
            EnumSet<RISCV64.CPUFeature> features = CPUTypeRISCV64.getSelectedFeatures();
            features.addAll(parseCSVtoEnum(RISCV64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), RISCV64.CPUFeature.values()));
            RISCV64 architecture = new RISCV64(features);
            // runtime checked features are the same as static features on RISCV64 for now
            EnumSet<RISCV64.CPUFeature> runtimeCheckedFeatures = architecture.getFeatures().clone();
            return new SubstrateTargetDescription(architecture, true, 16, 0, runtimeCheckedFeatures);
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
                    EconomicSet<String> allOptionNames, TimerCollection timerCollection) {
        if (!buildStarted.compareAndSet(false, true)) {
            throw UserError.abort("An image build has already been performed with this generator.");
        }

        try {
            /*
             * JVMCI 20.2-b01 introduced new methods for linking and querying whether an interface
             * has default methods. Fail early if these methods are missing.
             */
            ResolvedJavaType.class.getDeclaredMethod("link");
        } catch (ReflectiveOperationException ex) {
            throw UserError.abort("JVMCI version provided %s is missing the 'ResolvedJavaType.link()' method added in jvmci-20.2-b01. " +
                            "Please use the latest JVMCI JDK from %s.", System.getProperty("java.home"), OPEN_LABSJDK_RELEASE_URL_PATTERN);
        }

        setSystemPropertiesForImageLate(k);

        var hostedOptionValues = new HostedOptionValues(optionProvider.getHostedValues());
        var tempDirectoryOptionValue = NativeImageOptions.TempDirectory.getValue(hostedOptionValues).lastValue().orElse(null);
        try (TemporaryBuildDirectoryProviderImpl tempDirectoryProvider = new TemporaryBuildDirectoryProviderImpl(tempDirectoryOptionValue)) {
            var builderTempDir = tempDirectoryProvider.getTemporaryBuildDirectory();
            HostedImageLayerBuildingSupport imageLayerSupport = HostedImageLayerBuildingSupport.initialize(hostedOptionValues, loader, builderTempDir);
            ImageSingletonsSupportImpl.HostedManagement.install(new ImageSingletonsSupportImpl.HostedManagement(imageLayerSupport, loader.classLoaderSupport.annotationExtractor), imageLayerSupport);

            ImageSingletons.add(LayeredImageSingletonSupport.class, (LayeredImageSingletonSupport) ImageSingletonsSupportImpl.get());
            ImageSingletons.add(ProgressReporter.class, reporter);
            ImageSingletons.add(DeadlockWatchdog.class, loader.watchdog);
            ImageSingletons.add(TimerCollection.class, timerCollection);
            ImageSingletons.add(ImageBuildStatistics.TimerCollectionPrinter.class, timerCollection);
            ImageSingletons.add(AnnotationExtractor.class, loader.classLoaderSupport.annotationExtractor);
            ImageSingletons.add(BuildArtifacts.class, new BuildArtifactsImpl());
            ImageSingletons.add(HostedOptionValues.class, hostedOptionValues);
            ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));
            ImageSingletons.add(TemporaryBuildDirectoryProvider.class, tempDirectoryProvider);

            doRun(entryPoints, javaMainSupport, imageName, k, harnessSubstitutions);
        } finally {
            reporter.ensureCreationStageEndCompleted();
        }
    }

    protected static void setSystemPropertiesForImageEarly() {
        VMError.guarantee(ImageInfo.inImageBuildtimeCode(), "Expected ImageInfo.inImageBuildtimeCode() to return true");
        VMError.guarantee(ImageInfo.inImageBuildtimeCode() == NativeImageSupport.inBuildtimeCode(),
                        "ImageInfo.inImageBuildtimeCode() and NativeImageSupport.inBuildtimeCode() are not in sync");
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
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY);
    }

    @SuppressWarnings("try")
    protected void doRun(Map<Method, CEntryPointData> entryPoints, JavaMainSupport javaMainSupport, String imageName, NativeImageKind k, SubstitutionProcessor harnessSubstitutions) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();

        OptionValues options = HostedOptionValues.singleton();

        try (DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();
                        DebugCloseable featureCleanup = () -> featureHandler.forEachFeature(Feature::cleanup)) {
            setupNativeImage(options, entryPoints, javaMainSupport, imageName, harnessSubstitutions, debug);

            boolean returnAfterAnalysis = runPointsToAnalysis(imageName, options, debug);
            if (returnAfterAnalysis) {
                return;
            }

            NativeImageHeap heap;
            HostedMetaAccess hMetaAccess;
            RuntimeConfiguration runtimeConfiguration;
            try (ReporterClosable c = reporter.printUniverse()) {
                loader.watchdog.recordActivity();

                hUniverse = new HostedUniverse(bb);
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    HostedImageLayerBuildingSupport.singleton().getWriter().setHostedUniverse(hUniverse);
                }
                if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                    HostedImageLayerBuildingSupport.singleton().getLoader().setHostedUniverse(hUniverse);
                }
                hMetaAccess = new HostedMetaAccess(hUniverse, bb.getMetaAccess());

                BeforeUniverseBuildingAccessImpl beforeUniverseBuildingConfig = new BeforeUniverseBuildingAccessImpl(featureHandler, loader, debug, hMetaAccess);
                featureHandler.forEachFeature(feature -> feature.beforeUniverseBuilding(beforeUniverseBuildingConfig));

                new UniverseBuilder(aUniverse, bb.getMetaAccess(), hUniverse, hMetaAccess, HostedConfiguration.instance().createStrengthenGraphs(bb, hUniverse),
                                bb.getUnsupportedFeatures()).build(debug);

                BuildPhaseProvider.markHostedUniverseBuilt();
                ClassInitializationSupport classInitializationSupport = bb.getHostVM().getClassInitializationSupport();
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(hMetaAccess);
                runtimeConfiguration = new HostedRuntimeConfigurationBuilder(options, bb.getHostVM(), hUniverse, hMetaAccess,
                                bb.getProviders(MultiMethod.ORIGINAL_METHOD), classInitializationSupport, platformConfig,
                                bb.getSnippetReflectionProvider()).build();

                registerGraphBuilderPlugins(featureHandler, runtimeConfiguration, (HostedProviders) runtimeConfiguration.getProviders(), bb.getMetaAccess(), aUniverse,
                                nativeLibraries, loader, ParsingReason.AOTCompilation, bb.getAnnotationSubstitutionProcessor(),
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                                ConfigurationValues.getTarget(), this.isStubBasedPluginsSupported());

                if (NativeImageOptions.PrintUniverse.getValue()) {
                    hUniverse.printTypes();
                }

                /* Find the entry point methods in the hosted world. */
                for (AnalysisMethod m : aUniverse.getMethods()) {
                    if (m.isNativeEntryPoint()) {
                        HostedMethod found = hUniverse.lookup(m);
                        assert found != null;
                        hostedEntryPoints.add(found);
                    }
                }
                if (hostedEntryPoints.size() == 0) {
                    throw UserError.abort("No entry points found, i.e., no method annotated with @%s", CEntryPoint.class.getSimpleName());
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

            var hConstantReflection = (HostedConstantReflectionProvider) runtimeConfiguration.getProviders().getConstantReflection();
            heap = new NativeImageHeap(aUniverse, hUniverse, hMetaAccess, hConstantReflection, ImageSingletons.lookup(ImageHeapLayouter.class));

            if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                HostedImageLayerBuildingSupport.singleton().getWriter().setNativeImageHeap(heap);
            }

            BeforeCompilationAccessImpl beforeCompilationConfig = new BeforeCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, heap, debug, runtimeConfiguration, nativeLibraries);
            featureHandler.forEachFeature(feature -> feature.beforeCompilation(beforeCompilationConfig));

            BuildPhaseProvider.markReadyForCompilation();

            NativeImageCodeCache codeCache;
            CompileQueue compileQueue;
            try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.COMPILE_TOTAL)) {
                compileQueue = HostedConfiguration.instance().createCompileQueue(debug, featureHandler, hUniverse, runtimeConfiguration, DeoptTester.enabled());
                if (ImageSingletons.contains(RuntimeCompilationCallbacks.class)) {
                    ImageSingletons.lookup(RuntimeCompilationCallbacks.class).onCompileQueueCreation(bb, hUniverse, compileQueue);
                }
                compileQueue.finish(debug);
                BuildPhaseProvider.markCompileQueueFinished();

                /* release memory taken by graphs for the image writing */
                hUniverse.getMethods().forEach(HostedMethod::clear);

                try (ProgressReporter.ReporterClosable ac = reporter.printLayouting()) {
                    codeCache = NativeImageCodeCacheFactory.get().newCodeCache(compileQueue, heap, loader.platform,
                                    ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                    codeCache.layoutConstants();
                    codeCache.layoutMethods(debug, bb);
                    codeCache.buildRuntimeMetadata(debug, bb.getSnippetReflectionProvider());
                }

                AfterCompilationAccessImpl config = new AfterCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, compileQueue.getCompilations(), codeCache, heap, debug,
                                runtimeConfiguration, nativeLibraries);
                featureHandler.forEachFeature(feature -> feature.afterCompilation(config));
                BuildPhaseProvider.markCompilationFinished();
            }

            /* Re-run shadow heap verification after compilation. */
            aUniverse.getHeapVerifier().checkHeapSnapshot(debug, hMetaAccess, "after compilation", bb.getUniverse().getEmbeddedRoots());
            bb.getUnsupportedFeatures().report(bb);

            CodeCacheProvider codeCacheProvider = runtimeConfiguration.getBackendForNormalMethod().getProviders().getCodeCache();
            reporter.printCreationStart();
            try (Indent indent = debug.logAndIndent("create native image")) {
                try (DebugContext.Scope buildScope = debug.scope("CreateImage", codeCacheProvider)) {
                    try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.IMAGE)) {
                        loader.watchdog.recordActivity();

                        BeforeHeapLayoutAccessImpl beforeLayoutConfig = new BeforeHeapLayoutAccessImpl(featureHandler, loader, aUniverse, hUniverse, heap, debug, runtimeConfiguration,
                                        nativeLibraries);
                        featureHandler.forEachFeature(feature -> feature.beforeHeapLayout(beforeLayoutConfig));

                        verifyAndSealShadowHeap(codeCache, debug, heap);

                        buildNativeImageHeap(heap, codeCache);

                        AfterHeapLayoutAccessImpl config = new AfterHeapLayoutAccessImpl(featureHandler, loader, heap, hMetaAccess, debug);
                        featureHandler.forEachFeature(feature -> feature.afterHeapLayout(config));

                        createAbstractImage(k, hostedEntryPoints, heap, hMetaAccess, codeCache);

                        FeatureImpl.AfterAbstractImageCreationAccessImpl access = new FeatureImpl.AfterAbstractImageCreationAccessImpl(featureHandler, loader, debug, image,
                                        runtimeConfiguration.getBackendForNormalMethod());
                        featureHandler.forEachGraalFeature(feature -> feature.afterAbstractImageCreation(access));

                        image.build(imageName, debug);

                        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                            HostedImageLayerBuildingSupport.singleton().getWriter().persistAnalysisInfo();
                        }

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
                    throw debug.handle(e);
                }
            }

            compileQueue.purge();

            int numCompilations = codeCache.getOrderedCompilations().size();
            int imageDiskFileSize = -1;

            try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.WRITE)) {
                loader.watchdog.recordActivity();
                BeforeImageWriteAccessImpl beforeConfig = new BeforeImageWriteAccessImpl(featureHandler, loader, imageName, image,
                                runtimeConfiguration, aUniverse, hUniverse, optionProvider, hMetaAccess, debug);
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
                try {
                    // size changes during linking and afterConfig phase
                    imageDiskFileSize = (int) inv.getOutputFile().toFile().length();
                } catch (Exception e) {
                    imageDiskFileSize = -1; // we can't read a disk file size
                }
            }
            try (StopTimer t = TimerCollection.createTimerAndStart(TimerCollection.Registry.ARCHIVE_LAYER)) {
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    ImageSingletonsSupportImpl.HostedManagement.persist();
                    HostedImageLayerBuildingSupport.singleton().archiveLayer();
                }
            }
            reporter.printCreationEnd(image.getImageFileSize(), heap.getLayerObjectCount(), image.getImageHeapSize(), codeCache.getCodeAreaSize(), numCompilations, image.getDebugInfoSize(),
                            imageDiskFileSize);
        }
    }

    /*
     * Re-run shadow heap verification before heap layout. This time the verification uses the
     * embedded roots discovered after compilation.
     */
    private void verifyAndSealShadowHeap(NativeImageCodeCache codeCache, DebugContext debug, NativeImageHeap heap) {
        /*
         * Get the embedded constants after compilation to include everything, e.g., interned string
         * from snippet lowering that would otherwise be missed.
         */
        Map<Constant, Object> embeddedConstants = codeCache.initAndGetEmbeddedConstants();
        bb.getUniverse().getHeapVerifier().checkHeapSnapshot(debug, heap.hMetaAccess, "before heap layout", embeddedConstants);
        bb.getUnsupportedFeatures().report(bb);
        /*
         * Seal shadow heap after final verification. Any modification to the shadow heap after this
         * point, i.e., registering a new ImageHeapConstant or materializing the hosted values
         * reader of an existing ImageHeapConstant, will result in an error.
         */
        bb.getUniverse().getHeapScanner().seal();
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
            /*
             * Set the ConcurrentAnalysisAccessImpl before Feature.beforeAnalysis is executed. Some
             * features may already execute some pre-analysis tasks, e.g., reading a hosted field
             * value, that can trigger reachability callbacks.
             */
            ConcurrentAnalysisAccessImpl concurrentConfig = new ConcurrentAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
            aUniverse.setConcurrentAnalysisAccess(concurrentConfig);
            try (Indent ignored1 = debug.logAndIndent("process analysis initializers")) {
                BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                ServiceCatalogSupport.singleton().enableServiceCatalogMapTransformer(config);
                featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                ServiceCatalogSupport.singleton().seal();
                bb.getHostVM().getClassInitializationSupport().sealConfiguration();
                if (ImageLayerBuildingSupport.buildingImageLayer()) {
                    ImageSingletons.lookup(LoadImageSingletonFeature.class).processRegisteredSingletons(aUniverse);
                }
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    HostedImageLayerBuildingSupport.singleton().getWriter().initializeExternalValues();
                }
            }

            try (ReporterClosable c = ProgressReporter.singleton().printAnalysis(bb.getUniverse(), nativeLibraries.getLibraries())) {
                DuringAnalysisAccessImpl config = new DuringAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                try {
                    if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                        /*
                         * All the field value transformers should be installed by this point.
                         * Accessing some fields can trigger reachability callbacks, meaning the
                         * concurrent analysis access needs to be set.
                         */
                        HostedImageLayerBuildingSupport.singleton().getLoader().relinkTransformedStaticFinalFieldValues();
                    }
                    /* All pre-analysis set-up is done and the fixed-point analysis can start. */
                    BuildPhaseProvider.markAnalysisStarted();
                    bb.runAnalysis(debug, (universe) -> {
                        try (StopTimer t2 = TimerCollection.createTimerAndStart(TimerCollection.Registry.FEATURES)) {
                            bb.getHostVM().notifyClassReachabilityListener(universe, config);
                            featureHandler.forEachFeature(feature -> {
                                feature.duringAnalysis(config);
                                loader.watchdog.recordActivity();
                            });
                        }
                        /* Analysis is finished if no additional iteration was requested. */
                        return !config.getAndResetRequireAnalysisIteration() && !concurrentConfig.getAndResetRequireAnalysisIteration();
                    });
                } catch (Throwable t) {
                    if (ImageSingletons.contains(RuntimeCompilationCallbacks.class)) {
                        ImageSingletons.lookup(RuntimeCompilationCallbacks.class).reportAnalysisError(aUniverse, t);
                    }

                    if (t instanceof AnalysisError e) {
                        throw UserError.abort(e, "Analysis step failed. Reason: %s.", e.getMessage());
                    } else {
                        throw t;
                    }
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

            String reportsPath = SubstrateOptions.reportsPath();
            /*
             * Execute analysis reporting here. This code is executed even if unsupported features
             * are reported or the analysis fails due to any other reasons.
             */
            AnalysisReporter.printAnalysisReports(imageName, options, reportsPath, bb);
            ReachabilityTracePrinter.report(imageName, options, reportsPath, bb);
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
                    String imageName, SubstitutionProcessor harnessSubstitutions, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("setup native-image builder")) {
            try (StopTimer ignored1 = TimerCollection.createTimerAndStart(TimerCollection.Registry.SETUP)) {
                installDefaultExceptionHandler(options, imageName);
                SubstrateTargetDescription target = createTarget();
                ImageSingletons.add(Platform.class, loader.platform);
                ImageSingletons.add(SubstrateTargetDescription.class, target);

                ImageSingletons.add(SubstrateOptions.ReportingSupport.class, new SubstrateOptions.ReportingSupport(
                                DiagnosticsMode.getValue() ? DiagnosticsDir.getValue().lastValue().get() : Path.of("reports")));
                FutureDefaultsOptions.parseAndVerifyOptions();
                if (javaMainSupport != null) {
                    ImageSingletons.add(JavaMainSupport.class, javaMainSupport);
                }

                Providers originalProviders = GraalAccess.getOriginalProviders();
                MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();

                ClassLoaderSupportImpl classLoaderSupport = new ClassLoaderSupportImpl(loader.classLoaderSupport);
                ImageSingletons.add(ClassLoaderSupport.class, classLoaderSupport);
                ImageSingletons.add(LinkAtBuildTimeSupport.class, new LinkAtBuildTimeSupport(loader, classLoaderSupport));
                ImageSingletons.add(ObservableImageHeapMapProvider.class, new ObservableImageHeapMapProviderImpl());

                ClassInitializationSupport classInitializationSupport = new ClassInitializationSupport(originalMetaAccess, loader);
                ImageSingletons.add(RuntimeClassInitializationSupport.class, classInitializationSupport);
                ClassInitializationFeature.processClassInitializationOptions(classInitializationSupport);

                OptionClassFilter missingRegistrationClassFilter = OptionClassFilterBuilder.createFilter(loader, SubstrateOptions.ThrowMissingRegistrationErrors,
                                SubstrateOptions.ThrowMissingRegistrationErrorsPaths);
                MissingRegistrationSupport missingRegistrationSupport = new MissingRegistrationSupport(missingRegistrationClassFilter);
                ImageSingletons.add(MissingRegistrationSupport.class, missingRegistrationSupport);

                if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(options)) {
                    ImageSingletons.add(ImageBuildStatistics.class, new ImageBuildStatistics());
                }

                if (SubstrateOptions.useEconomyCompilerConfig()) {
                    GraalConfiguration.setHostedInstanceIfEmpty(new EconomyGraalConfiguration());
                }

                if (ImageLayerBuildingSupport.buildingImageLayer()) {
                    /*
                     * Register those singletons early as it needs to exist before any ImageHeapMap
                     * is created.
                     */
                    ImageSingletons.add(LayeredImageHeapMapStore.class, new LayeredImageHeapMapStore());
                    if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                        ImageSingletons.add(LayeredHostedImageHeapMapCollector.class, new LayeredHostedImageHeapMapCollector());
                    }
                }

                /* Init the BuildPhaseProvider before any features need it. */
                BuildPhaseProvider.init();

                featureHandler.registerFeatures(loader, debug);
                BuildPhaseProvider.markFeatureRegistrationFinished();

                loader.initBuilderModules();

                AfterRegistrationAccessImpl access = new AfterRegistrationAccessImpl(featureHandler, loader, originalMetaAccess, mainEntryPoint, debug);
                featureHandler.forEachFeature(feature -> feature.afterRegistration(access));
                setDefaultLibCIfMissing();
                if (!Pair.<Method, CEntryPointData> empty().equals(access.getMainEntryPoint())) {
                    setAndVerifyMainEntryPoint(access, entryPoints);
                }
                registerEntryPoints(entryPoints);

                setDefaultConfiguration();

                SVMImageLayerSnapshotUtil imageLayerSnapshotUtil = null;
                if (ImageLayerBuildingSupport.buildingImageLayer()) {
                    imageLayerSnapshotUtil = HostedConfiguration.instance().createSVMImageLayerSnapshotUtil(loader);
                }

                Boolean useSharedLayerGraphs = SubstrateOptions.UseSharedLayerGraphs.getValue();
                Boolean useSharedLayerStrengthenedGraphs = SubstrateOptions.UseSharedLayerStrengthenedGraphs.getValue();
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    SVMImageLayerWriter imageLayerWriter = HostedConfiguration.instance().createSVMImageLayerWriter(imageLayerSnapshotUtil, useSharedLayerGraphs, useSharedLayerStrengthenedGraphs);
                    HostedImageLayerBuildingSupport.singleton().setWriter(imageLayerWriter);
                }

                if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                    HostedImageLayerBuildingSupport imageLayerBuildingSupport = HostedImageLayerBuildingSupport.singleton();
                    SVMImageLayerLoader imageLayerLoader = HostedConfiguration.instance().createSVMImageLayerLoader(imageLayerSnapshotUtil, imageLayerBuildingSupport, useSharedLayerGraphs);
                    imageLayerBuildingSupport.setLoader(imageLayerLoader);
                    CGlobalDataFeature.singleton().getAppLayerCGlobalTracking().initializePriorLayerCGlobals();
                }

                AnnotationSubstitutionProcessor annotationSubstitutions = createAnnotationSubstitutionProcessor(originalMetaAccess, loader, classInitializationSupport);
                CEnumCallWrapperSubstitutionProcessor cEnumProcessor = new CEnumCallWrapperSubstitutionProcessor();
                aUniverse = createAnalysisUniverse(options, target, loader, originalMetaAccess, annotationSubstitutions, cEnumProcessor,
                                classInitializationSupport, Collections.singletonList(harnessSubstitutions), missingRegistrationSupport);

                SVMImageLayerWriter imageLayerWriter = null;
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    imageLayerWriter = HostedImageLayerBuildingSupport.singleton().getWriter();
                    aUniverse.setImageLayerWriter(imageLayerWriter);
                    imageLayerWriter.setAnalysisUniverse(aUniverse);
                }

                SVMImageLayerLoader imageLayerLoader = null;
                if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                    imageLayerLoader = HostedImageLayerBuildingSupport.singleton().getLoader();
                    imageLayerLoader.setUniverse(aUniverse);
                    aUniverse.setImageLayerLoader(imageLayerLoader);
                    imageLayerLoader.loadLayerAnalysis();
                }

                AnalysisMetaAccess aMetaAccess = new SVMAnalysisMetaAccess(aUniverse, originalMetaAccess);
                if (imageLayerLoader != null) {
                    imageLayerLoader.setMetaAccess(aMetaAccess);
                }
                HostedValuesProvider hostedValuesProvider = new SVMHostedValueProvider(aMetaAccess, aUniverse);
                if (imageLayerLoader != null) {
                    imageLayerLoader.setHostedValuesProvider(hostedValuesProvider);
                }
                SVMHost hostVM = (SVMHost) aUniverse.hostVM();
                ((ConditionalConfigurationRegistry) ImageSingletons.lookup(RuntimeSerializationSupport.class)).setHostVM(hostVM);
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(aMetaAccess);
                HostedProviders aProviders = createHostedProviders(target, aUniverse, originalProviders, platformConfig, aMetaAccess, classInitializationSupport);
                aUniverse.hostVM().initializeProviders(aProviders);

                SimulateClassInitializerSupport simulateClassInitializerSupport = hostVM.createSimulateClassInitializerSupport(aMetaAccess);
                ImageSingletons.add(SimulateClassInitializerSupport.class, simulateClassInitializerSupport);
                if (imageLayerWriter != null) {
                    imageLayerWriter.setSimulateClassInitializerSupport(simulateClassInitializerSupport);
                }

                bb = createBigBang(debug, options, aUniverse, aMetaAccess, aProviders, annotationSubstitutions);
                aUniverse.setBigBang(bb);
                if (imageLayerLoader != null) {
                    imageLayerLoader.initNodeClassMap();
                }

                /* Create the HeapScanner and install it into the universe. */
                ImageHeap imageHeap = new ImageHeap();
                ObjectScanningObserver aScanningObserver;
                if (!UseExperimentalReachabilityAnalysis.getValue(options)) {
                    aScanningObserver = new AnalysisObjectScanningObserver(bb);
                } else {
                    aScanningObserver = new ReachabilityObjectScanner(aMetaAccess);
                }
                ImageHeapScanner heapScanner = new SVMImageHeapScanner(bb, imageHeap, loader, aMetaAccess, aProviders.getSnippetReflection(),
                                aProviders.getConstantReflection(), aScanningObserver, hostedValuesProvider);
                aUniverse.setHeapScanner(heapScanner);
                if (imageLayerWriter != null) {
                    imageLayerWriter.setImageHeap(imageHeap);
                }
                ((HostedSnippetReflectionProvider) aProviders.getSnippetReflection()).setHeapScanner(heapScanner);
                if (imageLayerLoader != null) {
                    imageLayerLoader.postFutureBigbangTasks();
                }
                HeapSnapshotVerifier heapVerifier = new SVMImageHeapVerifier(bb, imageHeap, heapScanner);
                aUniverse.setHeapVerifier(heapVerifier);

                /* Register already created types as assignable. */
                aUniverse.getTypes().forEach(t -> {
                    t.registerAsAssignable(bb);
                    if (t.isReachable()) {
                        bb.onTypeReachable(t);
                    }
                });

                boolean withoutCompilerInvoker = CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration.getValue() ||
                                (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue() && CAnnotationProcessorCache.Options.UseCAPCache.getValue());

                if (!withoutCompilerInvoker) {
                    CCompilerInvoker compilerInvoker;
                    if (ImageSingletons.contains(CCompilerInvoker.class)) {
                        compilerInvoker = ImageSingletons.lookup(CCompilerInvoker.class);
                    } else {
                        compilerInvoker = CCompilerInvoker.create(ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory());
                        ImageSingletons.add(CCompilerInvoker.class, compilerInvoker);
                    }
                    compilerInvoker.verifyCompiler();
                }

                nativeLibraries = setupNativeLibraries(aProviders, cEnumProcessor, classInitializationSupport, debug);
                ImageSingletons.add(NativeLibraries.class, nativeLibraries);

                try (Indent ignored2 = debug.logAndIndent("process startup initializers")) {
                    FeatureImpl.DuringSetupAccessImpl config = new FeatureImpl.DuringSetupAccessImpl(featureHandler, loader, bb, debug);
                    featureHandler.forEachFeature(feature -> feature.duringSetup(config));
                }
                BuildPhaseProvider.markSetupFinished();

                initializeBigBang(bb, options, featureHandler, nativeLibraries, debug, aMetaAccess, aUniverse.getSubstitutions(), loader, true,
                                new SubstrateClassInitializationPlugin(hostVM), this.isStubBasedPluginsSupported(), aProviders);

                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    HostedImageLayerBuildingSupport.singleton().registerBaseLayerTypes(bb, originalMetaAccess, loader.classLoaderSupport);
                }

                if (loader.classLoaderSupport.isPreserveMode()) {
                    PreserveOptionsSupport.registerPreservedClasses(bb, originalMetaAccess, loader.classLoaderSupport);
                }

                registerEntryPointStubs(entryPoints);
            }

            ProgressReporter.singleton().printInitializeEnd(featureHandler.getUserSpecificFeatures(), loader);
        }
    }

    /**
     * We install a default uncaught exception handler to make sure that the image build terminates
     * if an uncaught exception is encountered on <b>any</b> thread. As an unexpectedly failing
     * thread could theoretically cause a deadlock if another thread was waiting for its result, we
     * preventively terminate the build via {@link System#exit}.
     */
    private void installDefaultExceptionHandler(OptionValues options, String imageName) {
        /*
         * A flag to make sure we run the reporting only once even if multiple uncaught exceptions
         * are encountered.
         */
        var reportStarted = new AtomicBoolean();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (reportStarted.compareAndSet(false, true)) {
                /*
                 * Call into the ProgressReporter to provide error reporting as if the throwable was
                 * thrown on the main thread.
                 */
                reporter.printEpilog(Optional.of(imageName), Optional.of(NativeImageGenerator.this), loader, NativeImageGeneratorRunner.BuildOutcome.FAILED, Optional.of(throwable), options);
                System.exit(ExitStatus.BUILDER_ERROR.getValue());
            }
        });
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
                    AnnotationSubstitutionProcessor annotationSubstitutions, SubstitutionProcessor cEnumProcessor,
                    ClassInitializationSupport classInitializationSupport, List<SubstitutionProcessor> additionalSubstitutions, MissingRegistrationSupport missingRegistrationSupport) {
        SubstitutionProcessor aSubstitutions = createAnalysisSubstitutionProcessor(cEnumProcessor, annotationSubstitutions, additionalSubstitutions);

        SVMHost hostVM = HostedConfiguration.instance().createHostVM(options, loader, classInitializationSupport, annotationSubstitutions, missingRegistrationSupport);

        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);
        AnalysisFactory analysisFactory;
        if (UseExperimentalReachabilityAnalysis.getValue(options)) {
            analysisFactory = new ReachabilityAnalysisFactory();
        } else {
            analysisFactory = new PointsToAnalysisFactory();
        }
        SubstrateAnnotationExtractor annotationExtractor = (SubstrateAnnotationExtractor) loader.classLoaderSupport.annotationExtractor;
        return new AnalysisUniverse(hostVM, target.wordJavaKind, analysisPolicy, aSubstitutions, originalMetaAccess, analysisFactory, annotationExtractor);
    }

    public static AnnotationSubstitutionProcessor createAnnotationSubstitutionProcessor(MetaAccessProvider originalMetaAccess, ImageClassLoader loader,
                    ClassInitializationSupport classInitializationSupport) {
        AnnotationSubstitutionProcessor annotationSubstitutions = new AnnotationSubstitutionProcessor(loader, originalMetaAccess, classInitializationSupport);
        var fieldValueInterceptionSupport = new FieldValueInterceptionSupport(annotationSubstitutions);
        ImageSingletons.add(FieldValueInterceptionSupport.class, fieldValueInterceptionSupport);
        annotationSubstitutions.init(fieldValueInterceptionSupport);
        return annotationSubstitutions;
    }

    public static SubstitutionProcessor createAnalysisSubstitutionProcessor(
                    SubstitutionProcessor cEnumProcessor, SubstitutionProcessor annotationSubstitutions,
                    List<SubstitutionProcessor> additionalSubstitutionProcessors) {
        List<SubstitutionProcessor> allProcessors = new ArrayList<>();
        SubstitutionProcessor cFunctionSubstitutions = new CFunctionSubstitutionProcessor();
        SubstitutionProcessor proxySubstitutionProcessor = new ProxyRenamingSubstitutionProcessor();
        allProcessors.addAll(Arrays.asList(annotationSubstitutions, cFunctionSubstitutions, cEnumProcessor, proxySubstitutionProcessor));
        allProcessors.addAll(additionalSubstitutionProcessors);
        return SubstitutionProcessor.chainUpInOrder(allProcessors.toArray(new SubstitutionProcessor[0]));
    }

    @SuppressWarnings("try")
    public static void initializeBigBang(Inflation bb, OptionValues options, FeatureHandler featureHandler, NativeLibraries nativeLibraries, DebugContext debug,
                    AnalysisMetaAccess aMetaAccess, SubstitutionProcessor substitutions, ImageClassLoader loader, boolean initForeignCalls, ClassInitializationPlugin classInitializationPlugin,
                    boolean supportsStubBasedPlugins, HostedProviders aProviders) {
        SubstrateReplacements aReplacements = (SubstrateReplacements) bb.getProviders(MultiMethod.ORIGINAL_METHOD).getReplacements();
        AnalysisUniverse aUniverse = bb.getUniverse();

        /*
         * Eagerly register all target fields of recomputed value fields as unsafe accessed.
         */
        bb.getAnnotationSubstitutionProcessor().registerUnsafeAccessedFields(bb);

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

        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            /*
             * The substitution processor need to be installed as some substituted types can be
             * loaded at this point.
             */
            HostedImageLayerBuildingSupport.singleton().getLoader().relinkNonTransformedStaticFinalFieldValues();
        }

        try (Indent ignored = debug.logAndIndent("add initial classes/fields/methods")) {
            registerRootElements(bb);

            NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, nativeLibraries, loader, ParsingReason.PointsToAnalysis,
                            bb.getAnnotationSubstitutionProcessor(), classInitializationPlugin, ConfigurationValues.getTarget(), supportsStubBasedPlugins);
            registerReplacements(debug, featureHandler, null, aProviders, true, initForeignCalls, new GraphEncoder(ConfigurationValues.getTarget().arch));

            performSnippetGraphAnalysis(bb, aReplacements, options, Function.identity());
        }
    }

    /**
     * System classes and fields are necessary to tell the static analysis that certain things
     * really "exist". The most common reason for that is that there are no instances and
     * allocations of these classes seen during the static analysis. The heap chunks are one good
     * example.
     */
    private static void registerRootElements(Inflation bb) {
        String rootClassReason = "system class included unconditionally";
        String rootMethodReason = "system method included unconditionally";
        bb.addRootClass(Object.class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootField(DynamicHub.class, "vtable");
        bb.addRootClass(String.class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(String[].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootField(String.class, "value").registerAsInstantiated(rootClassReason);
        bb.addRootClass(long[].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(byte[].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(byte[][].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(Object[].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(CFunctionPointer[].class, false, false).registerAsInstantiated(rootClassReason);
        bb.addRootClass(PointerBase[].class, false, false).registerAsInstantiated(rootClassReason);

        /* MethodRef can conceal use of MethodPointer and MethodOffset until after analysis. */
        bb.addRootClass(MethodPointer.class, false, true);
        if (SubstrateOptions.useRelativeCodePointers()) {
            bb.addRootClass(MethodOffset.class, false, true);
        }

        bb.addRootMethod(ReflectionUtil.lookupMethod(SubstrateArraycopySnippets.class, "doArraycopy",
                        Object.class, int.class, Object.class, int.class, int.class), true, rootMethodReason);
        bb.addRootMethod(ReflectionUtil.lookupMethod(Object.class, "getClass"), true, rootMethodReason);

        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                bb.addRootClass(kind.toJavaClass(), false, true);
                bb.addRootClass(kind.toBoxedJavaClass(), false, true).registerAsInstantiated(rootClassReason);
                bb.addRootField(kind.toBoxedJavaClass(), "value");
                bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass()), true, rootMethodReason);
                bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value"), true, rootMethodReason);
                /*
                 * Register the cache location as reachable.
                 * BoxingSnippets$Templates#getCacheLocation accesses the cache field.
                 */
                Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
                if (innerClasses != null && innerClasses.length > 0) {
                    bb.getMetaAccess().lookupJavaType(innerClasses[0]).registerAsReachable("inner class of " + rootClassReason);
                }
            }
        }
        /* SubstrateTemplates#toLocationIdentity accesses the Counter.value field. */
        bb.getMetaAccess().lookupJavaType(JavaKind.Void.toJavaClass()).registerAsReachable(rootClassReason);
        bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.util.Counter.class).registerAsReachable(rootClassReason);
        bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.allocationprofile.AllocationCounter.class).registerAsReachable(rootClassReason);
        /*
         * SubstrateAllocationProfilingData is not actually present in the image since it is only
         * allocated at build time, is passed to snippets as a @ConstantParameter, and it only
         * contains final fields that are constant-folded. However, since the profiling object is
         * only allocated during lowering it is processed by the shadow heap after analysis, so its
         * type needs to be already marked reachable at this point.
         */
        bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.SubstrateAllocationProfilingData.class).registerAsReachable(rootClassReason);
        /*
         * Similarly to above, StackSlotIdentity only gets reachable during lowering, through build
         * time allocated constants. It doesn't actually end up in the image heap since all its
         * fields are final and are constant-folded, but the type becomes reachable, through the
         * shadow heap processing, after analysis.
         */
        bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity.class).registerAsReachable(rootClassReason);
    }

    public static void performSnippetGraphAnalysis(BigBang bb, SubstrateReplacements replacements, OptionValues options, Function<Object, Object> objectTransformer) {
        Collection<StructuredGraph> snippetGraphs = replacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options), options, objectTransformer);
        if (bb instanceof NativeImagePointsToAnalysis pointsToAnalysis) {
            for (StructuredGraph graph : snippetGraphs) {
                MethodTypeFlowBuilder.registerUsedElements(pointsToAnalysis, graph, false);
            }
        } else if (bb instanceof NativeImageReachabilityAnalysisEngine reachabilityAnalysis) {
            for (StructuredGraph graph : snippetGraphs) {
                reachabilityAnalysis.processGraph(graph);
            }
        } else {
            throw VMError.shouldNotReachHere("Unknown analysis type - please specify how to handle snippets");
        }
    }

    private static HostedProviders createHostedProviders(TargetDescription target, AnalysisUniverse aUniverse,
                    Providers originalProviders, SubstratePlatformConfigurationProvider platformConfig, AnalysisMetaAccess aMetaAccess, ClassInitializationSupport classInitializationSupport) {

        ForeignCallsProvider aForeignCalls = new SubstrateForeignCallsProvider(aMetaAccess, null);
        AnalysisConstantFieldProvider aConstantFieldProvider = new AnalysisConstantFieldProvider(aMetaAccess, (SVMHost) aUniverse.hostVM());

        AnalysisConstantReflectionProvider aConstantReflection = new AnalysisConstantReflectionProvider(aUniverse, aMetaAccess, classInitializationSupport);

        WordTypes aWordTypes = new SubstrateWordTypes(aMetaAccess, ConfigurationValues.getWordKind());

        HostedSnippetReflectionProvider aSnippetReflection = new HostedSnippetReflectionProvider(null, aWordTypes);

        MetaAccessExtensionProvider aMetaAccessExtensionProvider = HostedConfiguration.instance().createAnalysisMetaAccessExtensionProvider(aUniverse);

        LoweringProvider aLoweringProvider = SubstrateLoweringProvider.createForHosted(aMetaAccess, null, platformConfig, aMetaAccessExtensionProvider);

        StampProvider aStampProvider = new SubstrateStampProvider(aMetaAccess);

        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, null, aStampProvider, aSnippetReflection,
                        aWordTypes, platformConfig, aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider(),
                        aConstantReflection);

        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        SubstrateReplacements aReplacements = new SubstrateReplacements(aProviders, bytecodeProvider, target, new SubstrateGraphMakerFactory());
        aProviders = (HostedProviders) aReplacements.getProviders();
        assert aReplacements == aProviders.getReplacements();

        return aProviders;
    }

    private static Inflation createBigBang(DebugContext debug, OptionValues options, AnalysisUniverse aUniverse, AnalysisMetaAccess aMetaAccess, HostedProviders aProviders,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor) {
        SnippetReflectionProvider snippetReflectionProvider = aProviders.getSnippetReflection();
        ConstantReflectionProvider constantReflectionProvider = aProviders.getConstantReflection();
        WordTypes wordTypes = aProviders.getWordTypes();
        String reason = "included by " + SubstrateOptionsParser.commandArgument(LayerCreate, "");
        ClassInclusionPolicy classInclusionPolicy = ImageLayerBuildingSupport.buildingSharedLayer()
                        ? new ClassInclusionPolicy.SharedLayerImageInclusionPolicy(reason)
                        : new ClassInclusionPolicy.DefaultAllInclusionPolicy(reason);
        if (PointstoOptions.UseExperimentalReachabilityAnalysis.getValue(options)) {
            ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler;
            if (PointstoOptions.UseReachabilityMethodSummaries.getValue(options)) {
                MethodSummaryProvider methodSummaryProvider = new SimpleInMemoryMethodSummaryProvider();
                ImageSingletons.add(MethodSummaryProvider.class, methodSummaryProvider);
                reachabilityMethodProcessingHandler = new MethodSummaryBasedHandler(methodSummaryProvider, ImageSingletons.lookup(TimerCollection.class));
            } else {
                reachabilityMethodProcessingHandler = new DirectMethodProcessingHandler();
            }
            return new NativeImageReachabilityAnalysisEngine(options, aUniverse, aMetaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, annotationSubstitutionProcessor, debug,
                            ImageSingletons.lookup(TimerCollection.class), reachabilityMethodProcessingHandler, classInclusionPolicy);
        }
        return new NativeImagePointsToAnalysis(options, aUniverse, aMetaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, annotationSubstitutionProcessor,
                        new SubstrateUnsupportedFeatures(), debug, ImageSingletons.lookup(TimerCollection.class), classInclusionPolicy);
    }

    @SuppressWarnings("try")
    protected NativeLibraries setupNativeLibraries(HostedProviders providers, CEnumCallWrapperSubstitutionProcessor cEnumProcessor, ClassInitializationSupport classInitializationSupport,
                    DebugContext debug) {
        try (StopTimer ignored = TimerCollection.createTimerAndStart("(cap)")) {
            NativeLibraries nativeLibs = new NativeLibraries(providers, ConfigurationValues.getTarget(), classInitializationSupport,
                            ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory(), debug);
            cEnumProcessor.setNativeLibraries(nativeLibs);
            processNativeLibraryImports(nativeLibs, classInitializationSupport);

            ImageSingletons.add(SizeOfSupport.class, new SizeOfSupportImpl(nativeLibs));
            ImageSingletons.add(OffsetOf.Support.class, new OffsetOfSupportImpl(nativeLibs));
            ImageSingletons.add(CConstantValueSupport.class, new CConstantValueSupportImpl(nativeLibs));

            if (CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration.getValue()) {
                throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterQueryCodeGeneration, "+"));
            }

            if (CAnnotationProcessorCache.Options.ExitAfterCAPCache.getValue()) {
                throw new InterruptImageBuilding("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterCAPCache, "+"));
            }
            if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                HostedImageLayerBuildingSupport.setupSharedLayerLibrary(nativeLibs);
            }
            return nativeLibs;
        }
    }

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

    protected boolean isStubBasedPluginsSupported() {
        return !SubstrateOptions.useLLVMBackend();
    }

    public static void registerGraphBuilderPlugins(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, HostedProviders providers, AnalysisMetaAccess aMetaAccess,
                    AnalysisUniverse aUniverse, NativeLibraries nativeLibs, ImageClassLoader loader, ParsingReason reason,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ClassInitializationPlugin classInitializationPlugin,
                    TargetDescription target, boolean supportsStubBasedPlugins) {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new SubstitutionInvocationPlugins(annotationSubstitutionProcessor));

        HostedSnippetReflectionProvider hostedSnippetReflection = new HostedSnippetReflectionProvider(aUniverse.getHeapScanner(),
                        new SubstrateWordTypes(aMetaAccess, ConfigurationValues.getWordKind()));

        WordOperationPlugin wordOperationPlugin = new SubstrateWordOperationPlugins(hostedSnippetReflection, providers.getConstantReflection(), providers.getWordTypes(),
                        providers.getPlatformConfigurationProvider().getBarrierSet());

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        plugins.appendInlineInvokePlugin(replacements);

        if (reason.duringAnalysis()) {
            plugins.appendNodePlugin(new SVMMethodHandleWithExceptionPlugin(providers.getConstantReflection().getMethodHandleAccess(), false));
        }
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(providers.getMetaAccess()));
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin(reason));
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());
        plugins.appendNodePlugin(new DynamicHubPlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendNodePlugin(wordOperationPlugin);

        plugins.setClassInitializationPlugin(classInitializationPlugin);

        featureHandler.forEachGraalFeature(feature -> feature.registerGraphBuilderPlugins(providers, plugins, reason));

        NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(providers.getMetaAccess(),
                        hostedSnippetReflection, providers.getForeignCalls(), providers.getWordTypes(), target);
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
        registerInvocationPlugins(hostedSnippetReflection, plugins.getInvocationPlugins(), useExactMathPlugins, true, supportsStubBasedPlugins);

        OptionValues options = aUniverse.hostVM().options();
        ImageSingletons.lookup(TargetGraphBuilderPlugins.class).registerPlugins(plugins, options);

        SubstrateGraphBuilderPlugins.registerInvocationPlugins(annotationSubstitutionProcessor,
                        loader,
                        plugins.getInvocationPlugins(),
                        reason,
                        supportsStubBasedPlugins);

        featureHandler.forEachGraalFeature(feature -> feature.registerInvocationPlugins(providers, plugins, reason));

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
                    boolean hosted, boolean initForeignCalls, GraphEncoder encoder) {
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

            featureHandler.forEachGraalFeature(feature -> feature.registerLowerings(runtimeConfig, options, providers, lowerings, hosted));
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        assert checkInvocationPluginMethods(replacements);
        replacements.encodeSnippets(encoder);
    }

    private static boolean checkInvocationPluginMethods(SubstrateReplacements replacements) {
        for (ResolvedJavaMethod method : replacements.getDelayedInvocationPluginMethods()) {
            ResolvedJavaMethod unwrapped = method;
            while (unwrapped instanceof WrappedJavaMethod) {
                unwrapped = ((WrappedJavaMethod) unwrapped).getWrapped();
            }
            if (!method.equals(unwrapped)) {
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
            assert method.equals(unwrapped) || method.getSignature().toMethodDescriptor().equals(unwrapped.getSignature().toMethodDescriptor());
        }
        return true;
    }

    public static Suites createSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, boolean hosted, OptionValues optionsToUse) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        Suites suites;
        if (hosted) {
            suites = GraalConfiguration.hostedInstance().createSuites(optionsToUse == null ? HostedOptionValues.singleton() : optionsToUse, hosted, ConfigurationValues.getTarget().arch);
        } else {
            suites = GraalConfiguration.runtimeInstance().createSuites(optionsToUse == null ? RuntimeOptionValues.singleton() : optionsToUse, hosted, ConfigurationValues.getTarget().arch);
        }
        return modifySuites(backend, suites, featureHandler, hosted, false);
    }

    public static Suites createSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, boolean hosted) {
        return createSuites(featureHandler, runtimeConfig, hosted, null);
    }

    public static Suites createFirstTierSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, boolean hosted) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        Suites suites;
        if (hosted) {
            suites = GraalConfiguration.hostedInstance().createFirstTierSuites(HostedOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        } else {
            suites = GraalConfiguration.runtimeInstance().createFirstTierSuites(RuntimeOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        }
        return modifySuites(backend, suites, featureHandler, hosted, true);
    }

    private static Suites modifySuites(SubstrateBackend backend, Suites suites, FeatureHandler featureHandler,
                    boolean hosted, boolean firstTier) {
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
        lowTier.replacePlaceholder(TransplantGraphsPhase.class, new TransplantGraphsPhase(createSuitesForLateSnippetTemplate(suites)));

        if (SharedArenaSupport.isAvailable()) {
            var pos = midTier.findPhase(FrameStateAssignmentPhase.class, true);
            pos.add(SharedArenaSupport.singleton().createOptimizeSharedArenaAccessPhase(hosted));
        }

        /*
         * Graal inserts only loop safepoints. We want a SafepointNode also before every return. Our
         * safepoint insertion phase inserts both kinds of safepoints.
         */
        midTier.findPhase(LoopSafepointInsertionPhase.class).set(new SubstrateSafepointInsertionPhase());

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
                it.add(CanonicalizerPhase.createSingleShot());
            } else {
                ListIterator<BasePhase<? super MidTierContext>> it = midTier.findPhase(DeoptimizationGroupingPhase.class);
                it.previous();
                it.add(new CollectDeoptimizationSourcePositionsPhase());
            }
        }

        featureHandler.forEachGraalFeature(feature -> feature.registerGraalPhases(runtimeCallProviders, suites, hosted));

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

    private static Suites createSuitesForLateSnippetTemplate(Suites regularCompileSuites) {

        Suites s = regularCompileSuites.copy();

        removePhases(s, Speculative.class);
        removePhases(s, FloatingGuardPhase.class);

        s.getLowTier().removeAllPlaceHolderOfType(TransplantGraphsPhase.class);

        return s;
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
            lirSuites.getFinalCodeAnalysisStage().appendPhase(new VerifyDeoptLIRFrameStatesPhase());
        }
        return true;
    }

    private void checkUniverse() {
        if (SubstrateOptions.VerifyNamingConventions.getValue()) {
            for (AnalysisMethod method : aUniverse.getMethods()) {
                if ((method.isInvoked() || method.isReachable()) && method.getAnnotation(Fold.class) == null) {
                    checkName(bb, method);
                }
            }
            for (AnalysisField field : aUniverse.getFields()) {
                if (field.isAccessed()) {
                    checkName(bb, field);
                }
            }
            for (AnalysisType type : aUniverse.getTypes()) {
                if (type.isReachable()) {
                    checkName(bb, type);
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
            if (method.isNativeEntryPoint()) {
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

    public static void checkName(BigBang bb, AnalysisMethod method) {
        String format = method.format("%H.%n(%p)");
        checkName(bb, method, format);
    }

    public static void checkName(BigBang bb, ResolvedJavaField field) {
        String format = field.format("%H.%n");
        checkName(bb, null, format);
    }

    public static void checkName(BigBang bb, ResolvedJavaType type) {
        String format = type.toJavaName(true);
        checkName(bb, null, format);
    }

    /**
     * These are legit elements from the JDK that have hotspot in their name.
     */
    private static final Set<String> CHECK_NAMING_EXCEPTIONS = Set.of(
                    "java.awt.Cursor.DOT_HOTSPOT_SUFFIX",
                    "sun.lwawt.macosx.CCustomCursor.fHotspot",
                    "sun.lwawt.macosx.CCustomCursor.getHotSpot()",
                    "sun.awt.shell.Win32ShellFolder2.ATTRIB_GHOSTED");

    private static void checkName(BigBang bb, AnalysisMethod method, String name) {
        /*
         * We do not want any parts of the native image generator in the generated image. Therefore,
         * no element whose name contains "hosted" must be seen as reachable by the static analysis.
         * The same holds for "host VM" elements, which come from the hosting VM, unless they are
         * JDK internal types.
         */
        String lcName = name.toLowerCase(Locale.ROOT);
        if (!CHECK_NAMING_EXCEPTIONS.contains(name)) {
            if (lcName.contains("hosted")) {
                report(bb, name, method, "Hosted element used at run time: " + name + namingConventionsErrorMessageSuffix("hosted"));
            } else if (!lcName.startsWith("jdk.internal") && lcName.contains("hotspot")) {
                report(bb, name, method, "Element with HotSpot in its name used at run time: " + name + namingConventionsErrorMessageSuffix("HotSpot"));
            }
        }
    }

    private static String namingConventionsErrorMessageSuffix(String elementType) {
        return """

                        If this is a regular JDK value, and not a %s element that was accidentally included, you can add it to the NativeImageGenerator.CHECK_NAMING_EXCEPTIONS
                        If this is a %s element that was accidentally included, find a way to exclude it from the image.""".formatted(elementType, elementType);
    }

    private static void report(BigBang bb, String key, AnalysisMethod method, String message) {
        if (bb != null) {
            bb.getUnsupportedFeatures().addMessage(key, method, message);
        } else {
            throw new UnsupportedFeatureException(message);
        }
    }

    @SuppressWarnings("try")
    protected void processNativeLibraryImports(NativeLibraries nativeLibs, ClassInitializationSupport classInitializationSupport) {
        MetaAccessProvider metaAccess = nativeLibs.getMetaAccess();
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

    public static Path getOutputDirectory() {
        return NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());
    }

    public static Path generatedFiles(OptionValues optionValues) {
        Path path = SubstrateOptions.getImagePath(optionValues);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        if (!Files.isDirectory(path)) {
            throw VMError.shouldNotReachHere("Output path is not a directory: " + path);
        }
        return path.toAbsolutePath();
    }

    private static <T extends Enum<T>> Set<T> parseCSVtoEnum(Class<T> enumType, List<String> csvEnumValues, T[] availValues) {
        EnumSet<T> result = EnumSet.noneOf(enumType);
        for (String enumValue : csvEnumValues) {
            try {
                result.add(Enum.valueOf(enumType, enumValue));
            } catch (IllegalArgumentException iae) {
                throw VMError.shouldNotReachHere("Value '" + enumValue + "' does not exist. Available values are " + StringUtil.joinSingleQuoted(availValues) + ".");
            }
        }
        return result;
    }
}
