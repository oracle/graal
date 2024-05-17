/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.hotspot.JVMCIVersionCheck.OPEN_LABSJDK_RELEASE_URL_PATTERN;
import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
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
import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.heap.ImageLayerSnapshotUtil;
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
import com.oracle.graal.pointsto.reports.ReportUtils;
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
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.MissingRegistrationSupport;
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
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.phases.CollectDeoptimizationSourcePositionsPhase;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionPathsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.SubstrateSafepointInsertionPhase;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldDispatchTableSnippets;
import com.oracle.svm.core.graal.snippets.OpenTypeWorldSnippets;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.word.SubstrateWordOperationPlugins;
import com.oracle.svm.core.graal.word.SubstrateWordTypes;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.ServiceCatalogSupport;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.OptionClassFilter;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.riscv64.RISCV64CPUFeatureAccess;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
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
import com.oracle.svm.hosted.code.ObjectFileTransformer;
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
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.jdk.localization.LocalizationFeature;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
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
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.phases.VerifyDeoptLIRFrameStatesPhase;
import com.oracle.svm.hosted.phases.VerifyNoGuardsPhase;
import com.oracle.svm.hosted.reflect.proxy.ProxyRenamingSubstitutionProcessor;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedFieldsPlugin;
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
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedPluginFactory;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
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
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.MethodHandleWithExceptionPlugin;
import jdk.graal.compiler.replacements.NodeIntrinsificationProvider;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;
import jdk.graal.compiler.word.WordOperationPlugin;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
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
        optionProvider.getHostedValues().put(CompilationAlarm.Options.CompilationNoProgressPeriod, 0D);
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
            AMD64 architecture = new AMD64(features, AMD64CPUFeatureAccess.allAMD64Flags());
            return new SubstrateTargetDescription(architecture, true, 16, 0, runtimeCheckedFeatures);
        } else if (includedIn(platform, Platform.AARCH64.class)) {
            EnumSet<AArch64.CPUFeature> features = CPUTypeAArch64.getSelectedFeatures();
            features.addAll(parseCSVtoEnum(AArch64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), AArch64.CPUFeature.values()));
            AArch64 architecture = new AArch64(features, AArch64CPUFeatureAccess.enabledAArch64Flags());
            // runtime checked features are the same as static features on AArch64 for now
            EnumSet<AArch64.CPUFeature> runtimeCheckedFeatures = architecture.getFeatures().clone();
            return new SubstrateTargetDescription(architecture, true, 16, 0, runtimeCheckedFeatures);
        } else if (includedIn(platform, Platform.RISCV64.class)) {
            EnumSet<RISCV64.CPUFeature> features = CPUTypeRISCV64.getSelectedFeatures();
            features.addAll(parseCSVtoEnum(RISCV64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue().values(), RISCV64.CPUFeature.values()));
            RISCV64 architecture = new RISCV64(features, RISCV64CPUFeatureAccess.enabledRISCV64Flags());
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
        HostedImageLayerBuildingSupport imageLayerSupport = HostedImageLayerBuildingSupport.initialize(hostedOptionValues);
        ImageSingletonsSupportImpl.HostedManagement.install(new ImageSingletonsSupportImpl.HostedManagement(imageLayerSupport.getLoader() != null || imageLayerSupport.getWriter() != null),
                        imageLayerSupport);

        ImageSingletons.add(LayeredImageSingletonSupport.class, (LayeredImageSingletonSupport) ImageSingletonsSupportImpl.get());
        ImageSingletons.add(ImageLayerBuildingSupport.class, imageLayerSupport);
        ImageSingletons.add(ProgressReporter.class, reporter);
        ImageSingletons.add(DeadlockWatchdog.class, loader.watchdog);
        ImageSingletons.add(TimerCollection.class, timerCollection);
        ImageSingletons.add(ImageBuildStatistics.TimerCollectionPrinter.class, timerCollection);
        ImageSingletons.add(AnnotationExtractor.class, loader.classLoaderSupport.annotationExtractor);
        ImageSingletons.add(BuildArtifacts.class, (type, artifact) -> buildArtifacts.computeIfAbsent(type, t -> new ArrayList<>()).add(artifact));
        ImageSingletons.add(HostedOptionValues.class, hostedOptionValues);
        ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));

        try (TemporaryBuildDirectoryProviderImpl tempDirectoryProvider = new TemporaryBuildDirectoryProviderImpl()) {
            ImageSingletons.add(TemporaryBuildDirectoryProvider.class, tempDirectoryProvider);
            if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                setupImageLayerArtifact(imageName);
            }
            doRun(entryPoints, javaMainSupport, imageName, k, harnessSubstitutions);
            if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                ImageSingletonsSupportImpl.HostedManagement.persist();
                HostedImageLayerBuildingSupport.singleton().getWriter().dumpFile();
            }
        } finally {
            reporter.ensureCreationStageEndCompleted();
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
    protected void doRun(Map<Method, CEntryPointData> entryPoints, JavaMainSupport javaMainSupport, String imageName, NativeImageKind k, SubstitutionProcessor harnessSubstitutions) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();

        OptionValues options = HostedOptionValues.singleton();

        try (DebugContext debug = new Builder(options, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).build();
                        DebugCloseable featureCleanup = () -> featureHandler.forEachFeature(Feature::cleanup)) {
            setupNativeImage(options, entryPoints, javaMainSupport, harnessSubstitutions, debug);

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
                hMetaAccess = new HostedMetaAccess(hUniverse, bb.getMetaAccess());

                BeforeUniverseBuildingAccessImpl beforeUniverseBuildingConfig = new BeforeUniverseBuildingAccessImpl(featureHandler, loader, debug, hMetaAccess);
                featureHandler.forEachFeature(feature -> feature.beforeUniverseBuilding(beforeUniverseBuildingConfig));

                new UniverseBuilder(aUniverse, bb.getMetaAccess(), hUniverse, hMetaAccess, HostedConfiguration.instance().createStrengthenGraphs(bb, hUniverse),
                                bb.getUnsupportedFeatures()).build(debug);

                BuildPhaseProvider.markHostedUniverseBuilt();
                ClassInitializationSupport classInitializationSupport = bb.getHostVM().getClassInitializationSupport();
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(hMetaAccess);
                runtimeConfiguration = new HostedRuntimeConfigurationBuilder(options, bb.getHostVM(), hUniverse, hMetaAccess,
                                bb.getProviders(MultiMethod.ORIGINAL_METHOD), classInitializationSupport, GraalAccess.getOriginalProviders().getLoopsDataProvider(), platformConfig,
                                bb.getSnippetReflectionProvider()).build();

                registerGraphBuilderPlugins(featureHandler, runtimeConfiguration, (HostedProviders) runtimeConfiguration.getProviders(), bb.getMetaAccess(), aUniverse,
                                nativeLibraries, loader, ParsingReason.AOTCompilation, bb.getAnnotationSubstitutionProcessor(),
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()),
                                ConfigurationValues.getTarget(), this.isStubBasedPluginsSupported());

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

                        if (ImageSingletons.contains(ObjectFileTransformer.class)) {
                            ImageSingletons.lookup(ObjectFileTransformer.class).afterAbstractImageCreation(image.getObjectFile());
                        }

                        image.build(imageName, debug);

                        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                            HostedImageLayerBuildingSupport.singleton().getWriter().persistAnalysisInfo(hUniverse, bb.getUniverse());
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
            }
            reporter.printCreationEnd(image.getImageFileSize(), heap.getObjectCount(), image.getImageHeapSize(), codeCache.getCodeAreaSize(), numCompilations, image.getDebugInfoSize());
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

    private static void setupImageLayerArtifact(String imageName) {
        int imageNameStart = imageName.lastIndexOf('/') + 1;
        String fileName = ImageLayerSnapshotUtil.FILE_NAME_PREFIX + imageName.substring(imageNameStart);
        String filePath = imageName.substring(0, imageNameStart) + fileName;
        Path layerSnapshotPath = generatedFiles(HostedOptionValues.singleton()).resolve(filePath + ImageLayerSnapshotUtil.FILE_EXTENSION);
        HostedImageLayerBuildingSupport.singleton().getWriter().setFileInfo(layerSnapshotPath, fileName, ImageLayerSnapshotUtil.FILE_EXTENSION);
        BuildArtifacts.singleton().add(ArtifactType.LAYER_SNAPSHOT, layerSnapshotPath);
    }

    protected void createAbstractImage(NativeImageKind k, List<HostedMethod> hostedEntryPoints, NativeImageHeap heap, HostedMetaAccess hMetaAccess, NativeImageCodeCache codeCache) {
        this.image = AbstractImage.create(k, hUniverse, hMetaAccess, nativeLibraries, heap, codeCache, hostedEntryPoints, loader.getClassLoader());
    }

    @SuppressWarnings("try")
    protected boolean runPointsToAnalysis(String imageName, OptionValues options, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("run analysis")) {
            try (Indent ignored1 = debug.logAndIndent("process analysis initializers")) {
                BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bb, nativeLibraries, debug);
                ServiceCatalogSupport.singleton().enableServiceCatalogMapTransformer(config);
                featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                ServiceCatalogSupport.singleton().seal();
                bb.getHostVM().getClassInitializationSupport().setConfigurationSealed(true);
            }

            if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                HostedImageLayerBuildingSupport.singleton().getLoader().loadLayerConstants();
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
                    SubstitutionProcessor harnessSubstitutions, DebugContext debug) {
        try (Indent ignored = debug.logAndIndent("setup native-image builder")) {
            try (StopTimer ignored1 = TimerCollection.createTimerAndStart(TimerCollection.Registry.SETUP)) {
                SubstrateTargetDescription target = createTarget();
                ImageSingletons.add(Platform.class, loader.platform);
                ImageSingletons.add(SubstrateTargetDescription.class, target);

                ImageSingletons.add(SubstrateOptions.ReportingSupport.class, new SubstrateOptions.ReportingSupport(
                                DiagnosticsMode.getValue() ? DiagnosticsDir.getValue().lastValue().get() : Path.of("reports")));

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
                ImageSingletons.add(MissingRegistrationSupport.class, new MissingRegistrationSupport(missingRegistrationClassFilter));

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
                aUniverse = createAnalysisUniverse(options, target, loader, originalMetaAccess, annotationSubstitutions, cEnumProcessor,
                                classInitializationSupport, Collections.singletonList(harnessSubstitutions));

                ImageLayerLoader imageLayerLoader = null;
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
                SubstratePlatformConfigurationProvider platformConfig = getPlatformConfig(aMetaAccess);
                HostedProviders aProviders = createHostedProviders(target, aUniverse, originalProviders, platformConfig, aMetaAccess, classInitializationSupport);
                aUniverse.hostVM().initializeProviders(aProviders);

                ImageSingletons.add(SimulateClassInitializerSupport.class, ((SVMHost) aUniverse.hostVM()).createSimulateClassInitializerSupport(aMetaAccess));

                bb = createBigBang(debug, options, aUniverse, aMetaAccess, aProviders, annotationSubstitutions);
                aUniverse.setBigBang(bb);

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
                if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                    HostedImageLayerBuildingSupport.singleton().getWriter().setImageHeap(imageHeap);
                }
                ((HostedSnippetReflectionProvider) aProviders.getSnippetReflection()).setHeapScanner(heapScanner);
                if (imageLayerLoader != null) {
                    imageLayerLoader.executeHeapScannerTasks();
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

                if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
                    Heap.getHeap().setStartOffset(HostedImageLayerBuildingSupport.singleton().getLoader().getImageHeapSize());
                }

                initializeBigBang(bb, options, featureHandler, nativeLibraries, debug, aMetaAccess, aUniverse.getSubstitutions(), loader, true,
                                new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()), this.isStubBasedPluginsSupported(), aProviders);

                loader.classLoaderSupport.getClassesToIncludeUnconditionally().forEach(cls -> bb.registerTypeForBaseImage(cls));

                registerEntryPointStubs(entryPoints);
            }

            ProgressReporter.singleton().printInitializeEnd(featureHandler.getUserSpecificFeatures(), loader);
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
                    AnnotationSubstitutionProcessor annotationSubstitutions, SubstitutionProcessor cEnumProcessor,
                    ClassInitializationSupport classInitializationSupport, List<SubstitutionProcessor> additionalSubstitutions) {
        SubstitutionProcessor aSubstitutions = createAnalysisSubstitutionProcessor(cEnumProcessor, annotationSubstitutions, additionalSubstitutions);

        SVMHost hostVM = HostedConfiguration.instance().createHostVM(options, loader, classInitializationSupport, annotationSubstitutions);

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
        annotationSubstitutions.init();
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
            bb.addRootClass(Object.class, false, false).registerAsInstantiated("root class");
            bb.addRootField(DynamicHub.class, "vtable");
            bb.addRootClass(String.class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(String[].class, false, false).registerAsInstantiated("root class");
            bb.addRootField(String.class, "value").registerAsInstantiated("root class");
            bb.addRootClass(long[].class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(byte[].class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(byte[][].class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(Object[].class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(CFunctionPointer[].class, false, false).registerAsInstantiated("root class");
            bb.addRootClass(PointerBase[].class, false, false).registerAsInstantiated("root class");

            bb.addRootMethod(ReflectionUtil.lookupMethod(SubstrateArraycopySnippets.class, "doArraycopy", Object.class, int.class, Object.class, int.class, int.class), true,
                            "Runtime support, registered in " + NativeImageGenerator.class);
            bb.addRootMethod(ReflectionUtil.lookupMethod(Object.class, "getClass"), true, "Runtime support, registered in " + NativeImageGenerator.class);

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bb.addRootClass(kind.toJavaClass(), false, true);
                    bb.addRootClass(kind.toBoxedJavaClass(), false, true).registerAsInstantiated("root class");
                    bb.addRootField(kind.toBoxedJavaClass(), "value");
                    bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass()), true, "Runtime support, registered in " + NativeImageGenerator.class);
                    bb.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value"), true, "Runtime support, registered in " + NativeImageGenerator.class);
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
            /*
             * SubstrateAllocationProfilingData is not actually present in the image since it is
             * only allocated at build time, is passed to snippets as a @ConstantParameter, and it
             * only contains final fields that are constant-folded. However, since the profiling
             * object is only allocated during lowering it is processed by the shadow heap after
             * analysis, so its type needs to be already marked reachable at this point.
             */
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets.SubstrateAllocationProfilingData.class).registerAsReachable("root class");
            /*
             * Similarly to above, StackSlotIdentity only gets reachable during lowering, through
             * build time allocated constants. It doesn't actually end up in the image heap since
             * all its fields are final and are constant-folded, but the type becomes reachable,
             * through the shadow heap processing, after analysis.
             */
            bb.getMetaAccess().lookupJavaType(com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity.class).registerAsReachable("root class");

            NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, nativeLibraries, loader, ParsingReason.PointsToAnalysis,
                            bb.getAnnotationSubstitutionProcessor(), classInitializationPlugin, ConfigurationValues.getTarget(), supportsStubBasedPlugins);
            registerReplacements(debug, featureHandler, null, aProviders, true, initForeignCalls, new GraphEncoder(ConfigurationValues.getTarget().arch));

            performSnippetGraphAnalysis(bb, aReplacements, options, Function.identity());
        }
    }

    public static void performSnippetGraphAnalysis(BigBang bb, SubstrateReplacements replacements, OptionValues options, Function<Object, Object> objectTransformer) {
        Collection<StructuredGraph> snippetGraphs = replacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options), options, objectTransformer);
        if (bb instanceof NativeImagePointsToAnalysis pointsToAnalysis) {
            for (StructuredGraph graph : snippetGraphs) {
                MethodTypeFlowBuilder.registerUsedElements(pointsToAnalysis, graph);
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

        MetaAccessExtensionProvider aMetaAccessExtensionProvider = HostedConfiguration.instance().createAnalysisMetaAccessExtensionProvider();

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
        String reason = "Included in the base image";
        ClassInclusionPolicy classInclusionPolicy = SubstrateOptions.LayeredBaseImageAnalysis.getValue(options) ? new ClassInclusionPolicy.LayeredBaseImageInclusionPolicy(reason)
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
                for (Path layerPath : HostedImageLayerBuildingSupport.singleton().getLoader().getLoadPaths()) {
                    Path snapshotFileName = layerPath.getFileName();
                    if (snapshotFileName != null) {
                        String layerName = snapshotFileName.toString().split(ImageLayerSnapshotUtil.FILE_NAME_PREFIX)[1].split(ImageLayerSnapshotUtil.FILE_EXTENSION)[0].trim();
                        /*
                         * This currently assumes lib{layer}.so is in the same dir as the layer
                         * snapshot. GR-53663 will create a proper bundle that contains both files.
                         */
                        Path layerPathDir = layerPath.getParent();
                        if (layerPathDir != null && layerName.startsWith("lib") && Files.exists(layerPathDir.resolve(layerName + ".so"))) {
                            nativeLibs.getLibraryPaths().add(layerPathDir.toString());
                            nativeLibs.addDynamicNonJniLibrary(layerName.split("lib")[1]);
                        } else {
                            throw VMError.shouldNotReachHere("Missing " + layerName + ".so. It must be placed in the same dir as the layer snapshot.");
                        }
                    }
                }
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

    protected boolean isStubBasedPluginsSupported() {
        return !SubstrateOptions.useLLVMBackend();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    static class SubstitutionInvocationPlugins extends InvocationPlugins {

        private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
        private EconomicMap<String, Integer> missingIntrinsicMetrics;

        SubstitutionInvocationPlugins(AnnotationSubstitutionProcessor annotationSubstitutionProcessor) {
            this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
            this.missingIntrinsicMetrics = null;
        }

        @Override
        protected void register(Type declaringClass, InvocationPlugin plugin, boolean allowOverwrite) {
            Type targetClass;
            if (declaringClass instanceof Class<?> annotatedClass) {
                targetClass = annotationSubstitutionProcessor.getTargetClass(annotatedClass);
                if (targetClass != declaringClass) {
                    /* Found a target class. Check if it is included. */
                    Executable annotatedMethod = plugin.name.equals("<init>") ? resolveConstructor(annotatedClass, plugin) : resolveMethod(annotatedClass, plugin);
                    String originalName = annotationSubstitutionProcessor.findOriginalElementName(annotatedMethod, (Class<?>) targetClass);
                    if (originalName == null) {
                        /*
                         * If the name is null, the element should not be substituted. Thus, we
                         * should also not register the invocation plugin.
                         */
                        return;
                    }
                    if (!originalName.equals(plugin.name)) {
                        throw VMError.unimplemented(String.format("""
                                        InvocationPlugins cannot yet deal with substitution methods that set the target name via the @TargetElement(name = ...) property.
                                        Annotated method "%s" vs target method "%s".""", plugin.name, originalName));
                    }
                }
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
                                            System.out.format("[Warning] Missing intrinsics found: %d%n", missingIntrinsicMetrics.size());
                                            List<Pair<String, Integer>> data = new ArrayList<>();
                                            final MapCursor<String, Integer> cursor = missingIntrinsicMetrics.getEntries();
                                            while (cursor.advance()) {
                                                data.add(Pair.create(cursor.getKey(), cursor.getValue()));
                                            }
                                            data.stream().sorted(Comparator.comparing(Pair::getRight, Comparator.reverseOrder())).forEach(
                                                            pair -> System.out.format("        - %d occurrences during parsing: %s%n", pair.getRight(), pair.getLeft()));
                                        }
                                    }));
                                } catch (IllegalStateException e) {
                                    // shutdown in progress, no need to register the hook
                                }
                            }
                            if (missingIntrinsicMetrics.containsKey(method)) {
                                missingIntrinsicMetrics.put(method, missingIntrinsicMetrics.get(method) + 1);
                            } else {
                                System.out.format("[Warning] Missing intrinsic %s found during parsing.%n", method);
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
            plugins.appendNodePlugin(new MethodHandleWithExceptionPlugin(providers.getConstantReflection().getMethodHandleAccess(), false));
        }
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        plugins.appendNodePlugin(new EarlyConstantFoldLoadFieldPlugin(providers.getMetaAccess()));
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin(reason));
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendNodePlugin(wordOperationPlugin);

        plugins.setClassInitializationPlugin(classInitializationPlugin);

        featureHandler.forEachGraalFeature(feature -> feature.registerGraphBuilderPlugins(providers, plugins, reason));

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
        registerInvocationPlugins(hostedSnippetReflection, plugins.getInvocationPlugins(), replacements,
                        useExactMathPlugins, true, supportsStubBasedPlugins, providers.getLowerer());

        Architecture architecture = ConfigurationValues.getTarget().arch;
        OptionValues options = aUniverse.hostVM().options();
        ImageSingletons.lookup(TargetGraphBuilderPlugins.class).register(plugins, replacements, architecture,
                        /* registerForeignCallMath */ false, options);

        SubstrateGraphBuilderPlugins.registerInvocationPlugins(annotationSubstitutionProcessor,
                        loader,
                        plugins.getInvocationPlugins(),
                        replacements,
                        reason,
                        architecture,
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
            if (SubstrateOptions.closedTypeWorld()) {
                TypeSnippets.registerLowerings(options, providers, lowerings);
            } else {
                OpenTypeWorldSnippets.registerLowerings(options, providers, lowerings);
                OpenTypeWorldDispatchTableSnippets.registerLowerings(options, providers, lowerings);
            }

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

    public static Suites createSuites(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, boolean hosted) {
        SubstrateBackend backend = runtimeConfig.getBackendForNormalMethod();
        Suites suites;
        if (hosted) {
            suites = GraalConfiguration.hostedInstance().createSuites(HostedOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        } else {
            suites = GraalConfiguration.runtimeInstance().createSuites(RuntimeOptionValues.singleton(), hosted, ConfigurationValues.getTarget().arch);
        }
        return modifySuites(backend, suites, featureHandler, hosted, false);
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
                it.add(CanonicalizerPhase.create());
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
        String message = checkName(name);
        if (message != null) {
            if (bb != null) {
                bb.getUnsupportedFeatures().addMessage(name, method, message);
            } else {
                throw new UnsupportedFeatureException(message);
            }
        }
    }

    public static String checkName(String name) {
        String lname = name.toLowerCase(Locale.ROOT);
        String message = null;
        if (lname.contains("hosted")) {
            message = "Hosted element used at run time: " + name;
        } else if (!name.startsWith("jdk.internal") && lname.contains("hotspot")) {
            message = "HotSpot element used at run time: " + name;
        }
        return message;
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

    public Map<ArtifactType, List<Path>> getBuildArtifacts() {
        return buildArtifacts;
    }

    private void printTypes() {
        String reportsPath = SubstrateOptions.reportsPath();
        ReportUtils.report("hosted universe", reportsPath, "universe_analysis", "txt",
                        writer -> printTypes(writer));
    }

    private void printTypes(PrintWriter writer) {

        for (HostedType type : hUniverse.getTypes()) {
            writer.format("%8d %s  ", type.getTypeID(), type.toJavaName(true));
            if (type.getSuperclass() != null) {
                writer.format("extends %d %s  ", type.getSuperclass().getTypeID(), type.getSuperclass().toJavaName(false));
            }
            if (type.getInterfaces().length > 0) {
                writer.print("implements ");
                String sep = "";
                for (HostedInterface interf : type.getInterfaces()) {
                    writer.format("%s%d %s", sep, interf.getTypeID(), interf.toJavaName(false));
                    sep = ", ";
                }
                writer.print("  ");
            }

            if (type.getWrapped().isInstantiated()) {
                writer.print("instantiated  ");
            }
            if (type.getWrapped().isReachable()) {
                writer.print("reachable  ");
            }

            if (SubstrateOptions.closedTypeWorld()) {
                writer.format("type check start %d range %d slot # %d ", type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot());
                writer.format("type check slots %s  ", slotsToString(type.getClosedTypeWorldTypeCheckSlots()));
            } else {
                writer.format("type id %s depth %s num class types %s num interface types %s ", type.getTypeID(), type.getTypeIDDepth(), type.getNumClassTypes(), type.getNumInterfaceTypes());
                writer.format("type check slots %s  ", String.join(" ", Arrays.stream(type.getOpenTypeWorldTypeCheckSlots()).mapToObj(Integer::toString).toArray(String[]::new)));
            }
            // if (type.findLeafConcreteSubtype() != null) {
            // writer.format("unique %d %s ", type.findLeafConcreteSubtype().getTypeID(),
            // type.findLeafConcreteSubtype().toJavaName(false));
            // }

            int le = type.getHub().getLayoutEncoding();
            if (LayoutEncoding.isPrimitive(le)) {
                writer.print("primitive  ");
            } else if (LayoutEncoding.isInterface(le)) {
                writer.print("interface  ");
            } else if (LayoutEncoding.isAbstract(le)) {
                writer.print("abstract  ");
            } else if (LayoutEncoding.isPureInstance(le)) {
                writer.format("instance size %d  ", LayoutEncoding.getPureInstanceAllocationSize(le).rawValue());
            } else if (LayoutEncoding.isArrayLike(le)) {
                String arrayType = LayoutEncoding.isHybrid(le) ? "hybrid" : "array";
                String elements = LayoutEncoding.isArrayLikeWithPrimitiveElements(le) ? "primitives" : "objects";
                writer.format("%s containing %s, base %d shift %d scale %d  ", arrayType, elements, LayoutEncoding.getArrayBaseOffset(le).rawValue(), LayoutEncoding.getArrayIndexShift(le),
                                LayoutEncoding.getArrayIndexScale(le));

            } else {
                throw VMError.shouldNotReachHereUnexpectedInput(le); // ExcludeFromJacocoGeneratedReport
            }

            writer.println();

            for (HostedType sub : type.getSubTypes()) {
                writer.format("               s %d %s%n", sub.getTypeID(), sub.toJavaName(false));
            }
            if (type.isInterface()) {
                for (HostedMethod method : hUniverse.getMethods()) {
                    if (method.getDeclaringClass().equals(type)) {
                        printMethod(writer, method, -1);
                    }
                }

            } else if (type.isInstanceClass()) {

                HostedField[] fields = type.getInstanceFields(false);
                fields = Arrays.copyOf(fields, fields.length);
                Arrays.sort(fields, Comparator.comparing(HostedField::toString));
                for (HostedField field : fields) {
                    writer.println("               f " + field.getLocation() + ": " + field.format("%T %n"));
                }
                HostedMethod[] vtable = type.getVTable();
                for (int i = 0; i < vtable.length; i++) {
                    if (vtable[i] != null) {
                        printMethod(writer, vtable[i], i);
                    }
                }
                for (HostedMethod method : hUniverse.getMethods()) {
                    if (method.getDeclaringClass().equals(type) && !method.hasVTableIndex()) {
                        printMethod(writer, method, -1);
                    }
                }
            }
        }

    }

    private static void printMethod(PrintWriter writer, HostedMethod method, int vtableIndex) {
        if (vtableIndex != -1) {
            writer.print("               v " + vtableIndex + " ");
        } else {
            writer.print("               m ");
        }
        if (method.hasVTableIndex()) {
            writer.print(method.getVTableIndex() + " ");
        }
        writer.print(method.format("%r %n(%p)") + ": " + method.getImplementations().length + " [");
        if (method.getImplementations().length <= 10) {
            String sep = "";
            for (HostedMethod impl : method.getImplementations()) {
                writer.print(sep + impl.getDeclaringClass().toJavaName(false));
                sep = ", ";
            }
        }
        writer.println("]");
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
                throw VMError.shouldNotReachHere("Value '" + enumValue + "' does not exist. Available values are " + StringUtil.joinSingleQuoted(availValues) + ".");
            }
        }
        return result;
    }
}
