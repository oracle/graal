/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.phases.CommunityCompilerConfiguration;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DeoptimizationGroupingPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.LoopSafepointInsertionPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.NodeIntrinsificationProvider;
import org.graalvm.compiler.replacements.amd64.AMD64GraphBuilderPlugins;
import org.graalvm.compiler.word.WordOperationPlugin;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.Feature.OnAnalysisExitAccess;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;

import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.graal.pointsto.reports.ObjectTreePrinter;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.svm.core.JavaMainWrapper.JavaMainSupport;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.allocationprofile.AllocationCounter;
import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.DeoptTester;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64AddressLowering;
import com.oracle.svm.core.graal.code.amd64.SubstrateAMD64RegisterConfig;
import com.oracle.svm.core.graal.jdk.ArraycopySnippets;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.graal.meta.SubstrateSnippetReflectionProvider;
import com.oracle.svm.core.graal.meta.SubstrateStampProvider;
import com.oracle.svm.core.graal.meta.SubstrateTargetDescription;
import com.oracle.svm.core.graal.phases.CollectDeoptimizationSourcePositionsPhase;
import com.oracle.svm.core.graal.phases.DeadStoreRemovalPhase;
import com.oracle.svm.core.graal.phases.MethodSafepointInsertionPhase;
import com.oracle.svm.core.graal.phases.OptimizeExceptionCallsPhase;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.phases.TrustedInterfaceTypePlugin;
import com.oracle.svm.core.graal.snippets.ArithmeticSnippets;
import com.oracle.svm.core.graal.snippets.DeoptRuntimeSnippets;
import com.oracle.svm.core.graal.snippets.DeoptTestSnippets;
import com.oracle.svm.core.graal.snippets.ExceptionSnippets;
import com.oracle.svm.core.graal.snippets.MonitorSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.NonSnippetLowerings;
import com.oracle.svm.core.graal.snippets.TypeSnippets;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValuePhase;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.LocalizationFeature;
import com.oracle.svm.core.option.HostedOptionValues;
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
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.OnAnalysisExitAccessImpl;
import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.SVMBytecodeAnalysisPolicy;
import com.oracle.svm.hosted.analysis.SVMDefaultAnalysisPolicy;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.annotation.AnnotationSupport;
import com.oracle.svm.hosted.c.CAnnotationProcessorCache;
import com.oracle.svm.hosted.c.GraalAccess;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.cenum.CEnumCallWrapperSubstitutionProcessor;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.code.CFunctionSubstitutionProcessor;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.HostedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.NativeMethodSubstitutionProcessor;
import com.oracle.svm.hosted.code.RestrictHeapAccessCallees;
import com.oracle.svm.hosted.code.SharedRuntimeConfigurationBuilder;
import com.oracle.svm.hosted.code.SubstrateGraphMakerFactory;
import com.oracle.svm.hosted.image.AbstractBootImage;
import com.oracle.svm.hosted.image.AbstractBootImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
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
import com.oracle.svm.hosted.phases.ImplicitExceptionsPlugin;
import com.oracle.svm.hosted.phases.InjectedAccessorsPlugin;
import com.oracle.svm.hosted.phases.IntrinsifyMethodHandlesInvocationPlugin;
import com.oracle.svm.hosted.phases.SubstrateClassInitializationPlugin;
import com.oracle.svm.hosted.phases.VerifyDeoptFrameStatesLIRPhase;
import com.oracle.svm.hosted.phases.VerifyNoGuardsPhase;
import com.oracle.svm.hosted.snippets.AssertSnippets;
import com.oracle.svm.hosted.snippets.DeoptHostedSnippets;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeclarativeSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedFieldsPlugin;
import com.oracle.svm.hosted.substitute.UnsafeAutomaticSubstitutionProcessor;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NativeImageGenerator {

    private final FeatureHandler featureHandler;
    private final ImageClassLoader loader;
    private final HostedOptionProvider optionProvider;

    private ForkJoinPool imageBuildPool;
    private AnalysisUniverse aUniverse;
    private HostedUniverse hUniverse;
    private BigBang bigbang;
    private AbstractBootImage image;
    private AtomicBoolean buildStarted = new AtomicBoolean();

    public NativeImageGenerator(ImageClassLoader loader, HostedOptionProvider optionProvider) {
        this.loader = loader;
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
                Constructor<?> constructor = platformClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                result = constructor.newInstance();
            } catch (ReflectiveOperationException ex) {
                throw UserError.abort("Could not instantiated platform class " + platformClassName + ". Ensure the class is not abstract and has a no-argument constructor.");
            }

            if (!(result instanceof Platform)) {
                throw UserError.abort("Platform class " + platformClassName + " does not implement " + Platform.class.getTypeName());
            }
            return (Platform) result;
        }

        Architecture hostedArchitecture = GraalAccess.getOriginalTarget().arch;
        if (hostedArchitecture instanceof AMD64) {
            final String osName = System.getProperty("os.name");
            if (OS.getCurrent() == OS.LINUX) {
                return new Platform.LINUX_AMD64();
            } else if (OS.getCurrent() == OS.DARWIN) {
                return new Platform.DARWIN_AMD64();
            } else if (OS.getCurrent() == OS.WINDOWS) {
                return new Platform.WINDOWS_AMD64();
            } else {
                throw VMError.shouldNotReachHere("Unsupported operating system: " + osName);
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

    public static TargetDescription createTarget(Platform platform) {
        if (includedIn(platform, Platform.AMD64.class)) {
            Architecture architecture;
            if (NativeImageOptions.NativeArchitecture.getValue()) {
                architecture = GraalAccess.getOriginalTarget().arch;
            } else {
                EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);
                // SSE and SSE2 are added by defaults as they are required by Graal
                features.add(AMD64.CPUFeature.SSE);
                features.add(AMD64.CPUFeature.SSE2);

                features.addAll(parseCSVtoEnum(AMD64.CPUFeature.class, NativeImageOptions.CPUFeatures.getValue()));

                architecture = new AMD64(features, SubstrateTargetDescription.allFlags());
            }
            assert architecture instanceof AMD64 : "SVM supports only AMD64 architectures.";
            boolean inlineObjects = SubstrateOptions.UseHeapBaseRegister.getValue();
            return new SubstrateTargetDescription(architecture, true, 16, 0, inlineObjects);
        } else {
            throw UserError.abort("Architecture specified by platform is not supported: " + platform.getClass().getTypeName());
        }
    }

    /**
     * Executes the image build. Only one image can be built with this generator.
     */
    public void run(Map<Method, CEntryPointData> entryPoints, Method mainEntryPoint,
                    JavaMainSupport javaMainSupport, String imageName,
                    AbstractBootImage.NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor,
                    EconomicSet<String> allOptionNames) {
        try {
            if (!buildStarted.compareAndSet(false, true)) {
                throw UserError.abort("An image build has already been performed with this generator.");
            }

            setSystemPropertiesForImage(k);

            int maxConcurrentThreads = NativeImageOptions.getMaximumNumberOfConcurrentThreads(new OptionValues(optionProvider.getHostedValues()));
            this.imageBuildPool = createForkJoinPool(maxConcurrentThreads);
            imageBuildPool.submit(() -> {
                try {
                    ImageSingletons.add(HostedOptionValues.class, new HostedOptionValues(optionProvider.getHostedValues()));
                    ImageSingletons.add(RuntimeOptionValues.class, new RuntimeOptionValues(optionProvider.getRuntimeValues(), allOptionNames));

                    doRun(entryPoints, mainEntryPoint, javaMainSupport, imageName, k, harnessSubstitutions, compilationExecutor, analysisExecutor);
                } finally {
                    try {
                        /*
                         * Make sure we clean up after ourselves even in the case of an exception.
                         */
                        if (deleteTempDirectory) {
                            deleteAll(tempDirectory());
                        }
                        featureHandler.forEachFeature(Feature::cleanup);
                    } catch (Throwable e) {
                        /*
                         * Suppress subsequent errors so that we unwind the original error brought
                         * us here.
                         */
                    }
                }
            }).get();
        } catch (InterruptedException | CancellationException e) {
            System.out.println("Interrupted!");
            throw new InterruptImageBuilding();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
        } finally {
            shutdownPoolSafe();
            clearSystemPropertiesForImage();
        }
    }

    private static void setSystemPropertiesForImage(NativeImageKind imageKind) {
        System.setProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME);
        if (imageKind.executable) {
            System.setProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, ImageInfo.PROPERTY_IMAGE_KIND_VALUE_EXECUTABLE);
        } else {
            System.setProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY, ImageInfo.PROPERTY_IMAGE_KIND_VALUE_SHARED_LIBRARY);
        }
    }

    private static void clearSystemPropertiesForImage() {
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_CODE_KEY);
        System.clearProperty(ImageInfo.PROPERTY_IMAGE_KIND_KEY);
    }

    private ForkJoinPool createForkJoinPool(int maxConcurrentThreads) {
        ImageSingletonsSupportImpl.HostedManagement vmConfig = new ImageSingletonsSupportImpl.HostedManagement();
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
    private void doRun(Map<Method, CEntryPointData> entryPoints, Method mainEntryPoint,
                    JavaMainSupport javaMainSupport, String imageName, AbstractBootImage.NativeImageKind k,
                    SubstitutionProcessor harnessSubstitutions,
                    ForkJoinPool compilationExecutor, ForkJoinPool analysisExecutor) {
        List<HostedMethod> hostedEntryPoints = new ArrayList<>();
        NativeLibraries nativeLibs;

        SVMHost svmHost;
        AnalysisMetaAccess aMetaAccess;
        SnippetReflectionProvider aSnippetReflection;
        HostedProviders aProviders;
        Throwable error = null;
        OptionValues options = HostedOptionValues.singleton();
        SnippetReflectionProvider originalSnippetReflection = GraalAccess.getOriginalSnippetReflection();
        try (DebugContext debug = DebugContext.create(options, new GraalDebugHandlersFactory(originalSnippetReflection))) {
            try (Indent indent = debug.logAndIndent("start analysis pass")) {
                try (StopTimer t = new Timer(imageName, "setup").start()) {
                    // TODO Make customizable via command line parameter.
                    Platform platform = defaultPlatform(loader.getClassLoader());

                    TargetDescription target = createTarget(platform);
                    ImageSingletons.add(Platform.class, platform);
                    ImageSingletons.add(TargetDescription.class, target);
                    if (javaMainSupport != null) {
                        ImageSingletons.add(JavaMainSupport.class, javaMainSupport);
                    }

                    Providers originalProviders = GraalAccess.getOriginalProviders();
                    MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();

                    AnalysisPolicy analysisPolicy;
                    if (PointstoOptions.AllocationSiteSensitiveHeap.getValue(options)) {
                        // context sensitive analysis
                        analysisPolicy = new SVMBytecodeAnalysisPolicy(options);
                    } else {
                        // context insensitive analysis
                        analysisPolicy = new SVMDefaultAnalysisPolicy(options);
                    }

                    svmHost = new SVMHost(options, platform, analysisPolicy, loader.getClassLoader());

                    featureHandler.registerFeatures(loader);

                    AfterRegistrationAccessImpl access = new AfterRegistrationAccessImpl(featureHandler, loader, originalMetaAccess);
                    featureHandler.forEachFeature(feature -> feature.afterRegistration(access));

                    registerEntryPoints(entryPoints);

                    /*
                     * Check if any configuration factory class was registered. If not, register the
                     * basic one.
                     */
                    HostedConfiguration.setDefaultIfEmpty();
                    GraalConfiguration.setDefaultIfEmpty();

                    registerEntryPoints(entryPoints);

                    CFunctionSubstitutionProcessor cfunctionSubstitutions = new CFunctionSubstitutionProcessor();

                    AnnotationSubstitutionProcessor annotationSubstitutions = new DeclarativeSubstitutionProcessor(loader, originalMetaAccess);
                    ImageSingletons.add(AnnotationSubstitutionProcessor.class, annotationSubstitutions);
                    annotationSubstitutions.init();

                    UnsafeAutomaticSubstitutionProcessor automaticSubstitutions = new UnsafeAutomaticSubstitutionProcessor(annotationSubstitutions, originalSnippetReflection);
                    ImageSingletons.add(UnsafeAutomaticSubstitutionProcessor.class, automaticSubstitutions);
                    automaticSubstitutions.init(loader, originalMetaAccess);

                    CEnumCallWrapperSubstitutionProcessor cEnumProcessor = new CEnumCallWrapperSubstitutionProcessor();

                    SubstitutionProcessor substitutions = SubstitutionProcessor.chainUpInOrder(harnessSubstitutions, new AnnotationSupport(originalMetaAccess, originalSnippetReflection),
                                    annotationSubstitutions, cfunctionSubstitutions, automaticSubstitutions, cEnumProcessor);
                    aUniverse = new AnalysisUniverse(svmHost, target, substitutions, originalMetaAccess, originalSnippetReflection, new SubstrateSnippetReflectionProvider());
                    aMetaAccess = new AnalysisMetaAccess(aUniverse, originalMetaAccess);

                    // native libraries
                    AnalysisConstantReflectionProvider aConstantReflection = new AnalysisConstantReflectionProvider(svmHost, aUniverse, originalProviders.getConstantReflection());
                    AnalysisConstantFieldProvider aConstantFieldProvider = new AnalysisConstantFieldProvider(aUniverse, aMetaAccess, aConstantReflection);
                    aSnippetReflection = new HostedSnippetReflectionProvider(svmHost);
                    nativeLibs = processNativeLibraryImports(options, aMetaAccess, aConstantReflection, aSnippetReflection);

                    ImageSingletons.add(NativeLibraries.class, nativeLibs);
                    if (CAnnotationProcessorCache.Options.ExitAfterCAPCache.getValue()) {
                        System.out.println("Exiting image generation because of " + SubstrateOptionsParser.commandArgument(CAnnotationProcessorCache.Options.ExitAfterCAPCache, "+"));
                        return;
                    }

                    /*
                     * Install all snippets so that the types, methods, and fields used in the
                     * snippets get added to the universe.
                     */
                    ForeignCallsProvider aForeignCalls = new SubstrateForeignCallsProvider();
                    LoweringProvider aLoweringProvider = SubstrateLoweringProvider.create(aMetaAccess, null);
                    StampProvider aStampProvider = new SubstrateStampProvider(aMetaAccess);
                    WordTypes aWordTypes = new WordTypes(aMetaAccess, FrameAccess.getWordKind());
                    aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, null, aStampProvider, aSnippetReflection,
                                    aWordTypes);
                    BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
                    SubstrateReplacements aReplacements = new SubstrateReplacements(options, aProviders, aSnippetReflection, bytecodeProvider, target, new SubstrateGraphMakerFactory(aWordTypes));
                    aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider, aForeignCalls, aLoweringProvider, aReplacements, aStampProvider,
                                    aSnippetReflection,
                                    aWordTypes);

                    bigbang = new Inflation(options, aUniverse, aProviders, svmHost, analysisExecutor);

                    /*
                     * Eagerly register all target fields of recomputed value fields as unsafe
                     * accessed.
                     */
                    annotationSubstitutions.processComputedValueFields(bigbang);

                    try (Indent indent2 = debug.logAndIndent("process startup initializers")) {
                        DuringSetupAccessImpl config = new DuringSetupAccessImpl(featureHandler, loader, bigbang, svmHost);
                        featureHandler.forEachFeature(feature -> feature.duringSetup(config));
                    }

                    NativeImageGenerator.registerGraphBuilderPlugins(featureHandler, null, aProviders, aMetaAccess, aUniverse, null, null, nativeLibs, loader, true, true);
                    registerReplacements(debug, featureHandler, null, aProviders, aProviders.getSnippetReflection(), true);

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
                     * System classes and fields are necessary to tell the static analysis that
                     * certain things really "exist". The most common reason for that is that there
                     * are no instances and allocations of these classes seen during the static
                     * analysis. The heap chunks are one good example.
                     */
                    try (Indent indent2 = debug.logAndIndent("add initial classes/fields/methods")) {
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

                        // Fields of BootImageInfo that get patched via relocation to addresses
                        // to the partitions of the native image heap.
                        bigbang.addSystemStaticField(NativeImageInfo.class, "firstReadOnlyPrimitiveObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "lastReadOnlyPrimitiveObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "firstReadOnlyReferenceObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "lastReadOnlyReferenceObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "firstWritablePrimitiveObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "lastWritablePrimitiveObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "firstWritableReferenceObject").registerAsInHeap();
                        bigbang.addSystemStaticField(NativeImageInfo.class, "lastWritableReferenceObject").registerAsInHeap();

                        // Graal uses it for type checks in the partial escape analysis phase.
                        bigbang.addSystemClass(Reference.class, false, false);

                        bigbang.addSystemClass(AllocationSite.class, false, false).registerAsInHeap();
                        bigbang.addSystemClass(AllocationCounter.class, false, false).registerAsInHeap();
                        bigbang.addSystemClass(AtomicReference.class, false, false).registerAsInHeap();

                        try {
                            /*
                             * TODO we want to get rid of these explicit registrations. All
                             * registered foreign calls should automatically be included in the
                             * static analysis.
                             */
                            bigbang.addRootMethod(ArraycopySnippets.class.getDeclaredMethod("doArraycopy", Object.class, int.class, Object.class, int.class, int.class));

                            bigbang.addRootMethod(Object.class.getDeclaredMethod("getClass"));

                            if (NativeImageOptions.DeoptimizeAll.getValue()) {
                                bigbang.addRootMethod(DeoptTester.class.getMethod("deoptTest"));
                            }
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

                        entryPoints.forEach((method, entryPointData) -> CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> entryPointData));

                        for (StructuredGraph graph : aReplacements.getSnippetGraphs(GraalOptions.TrackNodeSourcePosition.getValue(options))) {
                            new SVMMethodTypeFlowBuilder(bigbang, graph).registerUsedElements();
                        }
                    }

                    try (Indent indent2 = debug.logAndIndent("process analysis initializers")) {
                        BeforeAnalysisAccessImpl config = new BeforeAnalysisAccessImpl(featureHandler, loader, bigbang, svmHost, nativeLibs);
                        featureHandler.forEachFeature(feature -> feature.beforeAnalysis(config));
                    }
                }

                try (StopTimer t = new Timer(imageName, "analysis").start()) {

                    Timer processFeaturesTimer = new Timer(imageName, "(features)", false);

                    /*
                     * Iterate until analysis reaches a fixpoint
                     */
                    DuringAnalysisAccessImpl config = new DuringAnalysisAccessImpl(featureHandler, loader, bigbang, svmHost, nativeLibs);
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
                                 * Usually there are < 10 iterations. If we have so many iterations,
                                 * we probably have an endless loop (but at least we have a
                                 * performance problem because we re-start the analysis so often).
                                 */
                                throw VMError.shouldNotReachHere("Static analysis did not reach a fix point after " + numIterations +
                                                " iterations because a Feature keeps requesting new analysis iterations. The analysis itself " +
                                                (analysisChanged ? "DID" : "DID NOT") + " find a change in type states in the last iteration.");
                            }

                            /*
                             * Allow features to change the universe
                             */
                            try (StopTimer t2 = processFeaturesTimer.start()) {
                                int numTypes = aUniverse.getTypes().size();
                                int numMethods = aUniverse.getMethods().size();
                                int numFields = aUniverse.getFields().size();

                                featureHandler.forEachFeature(feature -> feature.duringAnalysis(config));

                                if (!config.getAndResetRequireAnalysisIteration()) {
                                    if (numTypes != aUniverse.getTypes().size() || numMethods != aUniverse.getMethods().size() || numFields != aUniverse.getFields().size()) {
                                        throw UserError.abort(
                                                        "When a feature makes more types, methods, of fields reachable, it must require another analysis iteration via DuringAnalysisAccess.requireAnalysisIteration()");
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    AfterAnalysisAccessImpl postConfig = new AfterAnalysisAccessImpl(featureHandler, loader, bigbang);
                    featureHandler.forEachFeature(feature -> feature.afterAnalysis(postConfig));

                    checkUniverse();

                    bigbang.typeFlowTimer.print();
                    bigbang.checkObjectsTimer.print();
                    processFeaturesTimer.print();

                    /* report the unsupported features by throwing UnsupportedFeatureException */
                    bigbang.getUnsupportedFeatures().report(bigbang);
                    bigbang.checkUserLimitations();
                } catch (UnsupportedFeatureException ufe) {
                    if (NativeImageOptions.ReportUnsupportedFeaturesCause.getValue() && ufe.getCause() != null) {
                        System.err.println("Original exception: ");
                        ufe.getCause().printStackTrace();
                    }
                    throw UserError.abort(ufe.getMessage());
                }

            } catch (InterruptedException ie) {
                throw new InterruptImageBuilding();
            } catch (RuntimeException | Error e) {
                // Prevents swallowing exceptions when ReturnAfterAnalysis is true
                error = e;
                throw e;
            } finally {
                OnAnalysisExitAccess onExitConfig = new OnAnalysisExitAccessImpl(featureHandler, loader, bigbang);
                featureHandler.forEachFeature(feature -> {
                    try {
                        feature.onAnalysisExit(onExitConfig);
                    } catch (Exception ex) {
                        System.err.println("Exception during " + feature.getClass().getName() + ".onAnalysisExit()");
                    }
                });

                /*
                 * Execute analysis reporting here. This code is executed even if unsupported
                 * features are reported or the analysis fails due to any other reasons.
                 */

                if (AnalysisReportsOptions.PrintAnalysisCallTree.getValue(options)) {
                    String reportName = imageName.substring(imageName.lastIndexOf("/") + 1);
                    CallTreePrinter.print(bigbang, SubstrateOptions.Path.getValue(), reportName);
                }

                if (AnalysisReportsOptions.PrintImageObjectTree.getValue(options)) {
                    String reportName = imageName.substring(imageName.lastIndexOf("/") + 1);
                    ObjectTreePrinter.print(bigbang, SubstrateOptions.Path.getValue(), reportName);
                }

                if (PointstoOptions.ReportAnalysisStatistics.getValue(options)) {
                    PointsToStats.report(bigbang, imageName.replace("images/", ""));
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
            if (error == null && NativeImageOptions.ReturnAfterAnalysis.getValue()) {
                return;
            }
            if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
                throw new InterruptImageBuilding("interrupted image construction as ExitAfterAnalysis is set");
            }

            NativeImageHeap heap;
            HostedMethod mainEntryPointHostedStub;
            HostedMetaAccess hMetaAccess;
            SharedRuntimeConfigurationBuilder runtime;
            try (StopTimer t = new Timer(imageName, "universe").start()) {
                hUniverse = new HostedUniverse(bigbang, svmHost);
                hMetaAccess = new HostedMetaAccess(hUniverse, aMetaAccess);

                new UniverseBuilder(aUniverse, aMetaAccess, hUniverse, hMetaAccess, HostedConfiguration.instance().createStaticAnalysisResultsBuilder(bigbang, hUniverse),
                                bigbang.getUnsupportedFeatures()).build(debug);

                runtime = new HostedRuntimeConfigurationBuilder(options, svmHost, hUniverse, hMetaAccess, aProviders).build();
                registerGraphBuilderPlugins(featureHandler, runtime.getRuntimeConfig(), (HostedProviders) runtime.getRuntimeConfig().getProviders(), aMetaAccess, aUniverse,
                                hMetaAccess, hUniverse,
                                nativeLibs, loader, false, true);

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
                /* Find main entry point */
                if (mainEntryPoint != null) {
                    AnalysisMethod analysisStub = CEntryPointCallStubSupport.singleton().getStubForMethod(mainEntryPoint);
                    mainEntryPointHostedStub = (HostedMethod) hMetaAccess.getUniverse().lookup(analysisStub);
                    assert hostedEntryPoints.contains(mainEntryPointHostedStub);
                } else {
                    mainEntryPointHostedStub = null;
                }
                if (hostedEntryPoints.size() == 0) {
                    throw UserError.abort("Warning: no entry points found, i.e., no method annotated with @" + CEntryPoint.class.getSimpleName());
                }

                heap = new NativeImageHeap(aUniverse, hUniverse, hMetaAccess);

                BeforeCompilationAccessImpl config = new BeforeCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, hMetaAccess, heap);
                featureHandler.forEachFeature(feature -> feature.beforeCompilation(config));

                bigbang.getUnsupportedFeatures().report(bigbang);
            } catch (UnsupportedFeatureException ufe) {
                throw UserError.abort(ufe.getMessage());
            }

            recordMethodsWithStackValues();
            recordRestrictHeapAccessCallees(aUniverse.getMethods());

            /*
             * After this point, all TypeFlow (and therefore also TypeState) objects are unreachable
             * and can be garbage collected. This is important to keep the overall memory footprint
             * low. However, this also means we no longer have complete call chain information. Only
             * the summarized information stored in the StaticAnalysisResult objects is available
             * after this point.
             */
            bigbang.cleanupAfterAnalysis();

            NativeImageCodeCache codeCache;
            CompileQueue compileQueue;
            try (StopTimer t = new Timer(imageName, "compile").start()) {
                compileQueue = HostedConfiguration.instance().createCompileQueue(debug, featureHandler, hUniverse, runtime, NativeImageOptions.DeoptimizeAll.getValue(), aSnippetReflection,
                                compilationExecutor);
                compileQueue.finish(debug);

                /* release memory taken by graphs for the image writing */
                hUniverse.getMethods().forEach(HostedMethod::clear);

                codeCache = new NativeImageCodeCache(compileQueue.getCompilations(), heap);
                codeCache.layoutMethods(debug);
                codeCache.layoutConstants();

                AfterCompilationAccessImpl config = new AfterCompilationAccessImpl(featureHandler, loader, aUniverse, hUniverse, hMetaAccess, heap);
                featureHandler.forEachFeature(feature -> feature.afterCompilation(config));
            }

            try (Indent indent = debug.logAndIndent("create native image")) {
                try (DebugContext.Scope buildScope = debug.scope("CreateBootImage")) {
                    try (StopTimer t = new Timer(imageName, "image").start()) {

                        // Start building the model of the native image heap.
                        heap.addInitialObjects(debug);
                        // Then build the model of the code cache, which can
                        // add objects to the native image heap.
                        codeCache.addConstantsToHeap(debug);
                        // Finish building the model of the native image heap.
                        heap.addTrailingObjects(debug);

                        AfterHeapLayoutAccessImpl config = new AfterHeapLayoutAccessImpl(featureHandler, loader, hMetaAccess);
                        featureHandler.forEachFeature(feature -> feature.afterHeapLayout(config));

                        this.image = AbstractBootImage.create(k, hUniverse, hMetaAccess, nativeLibs, heap, codeCache, hostedEntryPoints, mainEntryPointHostedStub, loader.getClassLoader());
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
                }
            }

            BeforeImageWriteAccessImpl beforeConfig = new BeforeImageWriteAccessImpl(featureHandler, loader, imageName, image,
                            runtime.getRuntimeConfig(), aUniverse, hUniverse, optionProvider, hMetaAccess);
            featureHandler.forEachFeature(feature -> feature.beforeImageWrite(beforeConfig));

            try (StopTimer t = new Timer(imageName, "write").start()) {
                /*
                 * This will write the debug info too -- i.e. we may be writing more than one file,
                 * if the debug info is in a separate file. We need to push writing the file to the
                 * image implementation, because whether the debug info and image share a file or
                 * not is an implementation detail of the image.
                 */
                Path tmpDir = tempDirectory();
                Path imagePath = image.write(debug, generatedFiles(HostedOptionValues.singleton()), tmpDir, imageName, beforeConfig);

                AfterImageWriteAccessImpl afterConfig = new AfterImageWriteAccessImpl(featureHandler, loader, hUniverse, imagePath, tmpDir, image.getBootImageKind());
                featureHandler.forEachFeature(feature -> feature.afterImageWrite(afterConfig));
            }
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
                BooleanSupplier instance;
                try {
                    Constructor<? extends BooleanSupplier> constructor = options.include().getDeclaredConstructor();
                    constructor.setAccessible(true);
                    instance = constructor.newInstance();
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                    throw VMError.shouldNotReachHere(ex);
                }
                include = instance.getAsBoolean();
            }
            if (include) {
                entryPoints.put(m, CEntryPointData.create(m));
            }
        }
    }

    /**
     * Track methods that have a stack values. This is later used for deoptimization testing during
     * compilation.
     */
    private void recordMethodsWithStackValues() {
        bigbang.getUniverse().getMethods().parallelStream().forEach(analysisMethod -> {
            if (analysisMethod.getTypeFlow() != null && analysisMethod.getTypeFlow().getGraph() != null && analysisMethod.getTypeFlow().getGraph().getNodes(StackValueNode.TYPE).isNotEmpty()) {
                hUniverse.recordMethodWithStackValues(analysisMethod);
            }
        });
    }

    /**
     * Record callees of methods that must not allocate.
     */
    private static void recordRestrictHeapAccessCallees(Collection<AnalysisMethod> methods) {
        ImageSingletons.lookup(RestrictHeapAccessCallees.class).aggregateMethods(methods);
    }

    public void interruptBuild() {
        shutdownPoolSafe();
    }

    private void shutdownPoolSafe() {
        if (imageBuildPool != null) {
            imageBuildPool.shutdownNow();
        }
    }

    static class SubstitutionInvocationPlugins extends InvocationPlugins {

        @Override
        protected void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Type declaringClass, String name, Type... argumentTypes) {
            Type targetClass;
            if (declaringClass instanceof Class) {
                targetClass = ImageSingletons.lookup(AnnotationSubstitutionProcessor.class).getTargetClass((Class<?>) declaringClass);
            } else {
                targetClass = declaringClass;
            }
            super.register(plugin, isOptional, allowOverwrite, targetClass, name, argumentTypes);
        }
    }

    public static void registerGraphBuilderPlugins(FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, HostedProviders providers, AnalysisMetaAccess aMetaAccess,
                    AnalysisUniverse aUniverse, HostedMetaAccess hMetaAccess, HostedUniverse hUniverse, NativeLibraries nativeLibs, ImageClassLoader loader, boolean analysis, boolean hosted) {
        assert !analysis || hosted : "analysis must always be hosted";
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new SubstitutionInvocationPlugins());

        WordOperationPlugin wordOperationPlugin = new WordOperationPlugin(providers.getSnippetReflection(), providers.getWordTypes());

        SubstrateReplacements replacements = (SubstrateReplacements) providers.getReplacements();
        plugins.appendInlineInvokePlugin(replacements);

        plugins.appendNodePlugin(new IntrinsifyMethodHandlesInvocationPlugin(providers, aUniverse, hUniverse));
        plugins.appendNodePlugin(new DeletedFieldsPlugin());
        plugins.appendNodePlugin(new InjectedAccessorsPlugin());
        plugins.appendNodePlugin(new ConstantFoldLoadFieldPlugin());
        plugins.appendNodePlugin(new CInterfaceInvocationPlugin(providers.getMetaAccess(), providers.getWordTypes(), nativeLibs));
        plugins.appendNodePlugin(new LocalizationFeature.CharsetNodePlugin());

        plugins.appendInlineInvokePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(wordOperationPlugin);
        plugins.appendTypePlugin(new TrustedInterfaceTypePlugin());
        plugins.appendNodePlugin(wordOperationPlugin);
        plugins.appendNodePlugin(new ImplicitExceptionsPlugin(providers.getMetaAccess(), providers.getForeignCalls()));

        plugins.setClassInitializationPlugin(new SubstrateClassInitializationPlugin((SVMHost) aUniverse.hostVM()));

        featureHandler.forEachGraalFeature(feature -> feature.registerNodePlugins(analysis ? aMetaAccess : hMetaAccess, plugins, analysis, hosted));

        HostedSnippetReflectionProvider hostedSnippetReflection = new HostedSnippetReflectionProvider((SVMHost) aUniverse.getHostVM());
        NodeIntrinsificationProvider nodeIntrinsificationProvider = new NodeIntrinsificationProvider(providers.getMetaAccess(), hostedSnippetReflection,
                        providers.getForeignCalls(), providers.getLowerer(), providers.getWordTypes());
        for (Class<? extends NodeIntrinsicPluginFactory> factoryClass : loader.findSubclasses(NodeIntrinsicPluginFactory.class)) {
            if (!Modifier.isAbstract(factoryClass.getModifiers()) && !factoryClass.getName().contains("hotspot")) {
                NodeIntrinsicPluginFactory factory;
                try {
                    factory = factoryClass.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw VMError.shouldNotReachHere(ex);
                }
                factory.registerPlugins(plugins.getInvocationPlugins(), nodeIntrinsificationProvider);
            }
        }

        BytecodeProvider replacementBytecodeProvider = replacements.getDefaultReplacementBytecodeProvider();
        final boolean explicitUnsafeNullChecks = SubstrateOptions.UseHeapBaseRegister.getValue() && SubstrateOptions.UseLinearPointerCompression.getValue();
        registerInvocationPlugins(providers.getMetaAccess(), providers.getSnippetReflection(), plugins.getInvocationPlugins(), replacementBytecodeProvider, !hosted, explicitUnsafeNullChecks);
        AMD64GraphBuilderPlugins.register(plugins, replacementBytecodeProvider, (AMD64) ConfigurationValues.getTarget().arch, true, explicitUnsafeNullChecks);

        /*
         * When the context is hosted, i.e., ahead-of-time compilation, and after the analysis we
         * need the hosted meta access.
         */
        MetaAccessProvider pluginsMetaAccess = hosted && !analysis ? hMetaAccess : aMetaAccess;
        assert pluginsMetaAccess != null;
        SubstrateGraphBuilderPlugins.registerInvocationPlugins(pluginsMetaAccess, providers.getConstantReflection(), hostedSnippetReflection, plugins.getInvocationPlugins(),
                        replacementBytecodeProvider, analysis);

        featureHandler.forEachGraalFeature(feature -> feature.registerInvocationPlugins(providers, hostedSnippetReflection, plugins.getInvocationPlugins(), analysis, hosted));

        providers.setGraphBuilderPlugins(plugins);
        replacements.setGraphBuilderPlugins(plugins);
        if (runtimeConfig != null && runtimeConfig.getProviders() instanceof HostedProviders) {
            ((HostedProviders) runtimeConfig.getProviders()).setGraphBuilderPlugins(plugins);
            for (Backend backend : runtimeConfig.getBackends()) {
                ((HostedProviders) backend.getProviders()).setGraphBuilderPlugins(plugins);
            }
        }
    }

    @SuppressWarnings("try")
    public static void registerReplacements(DebugContext debug, FeatureHandler featureHandler, RuntimeConfiguration runtimeConfig, Providers providers,
                    SnippetReflectionProvider snippetReflection, boolean hosted) {
        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();

        Providers runtimeCallProviders = runtimeConfig != null ? runtimeConfig.getBackendForNormalMethod().getProviders() : providers;
        SubstrateForeignCallsProvider foreignCallsProvider = (SubstrateForeignCallsProvider) providers.getForeignCalls();
        for (SubstrateForeignCallDescriptor descriptor : SnippetRuntime.getRuntimeCalls()) {
            foreignCallsProvider.getForeignCalls().put(descriptor, new SubstrateForeignCallLinkage(runtimeCallProviders, descriptor));
        }
        featureHandler.forEachGraalFeature(feature -> feature.registerForeignCalls(runtimeConfig, runtimeCallProviders, snippetReflection, foreignCallsProvider.getForeignCalls(), hosted));

        try (DebugContext.Scope s = debug.scope("RegisterLowerings", new DebugDumpScope("RegisterLowerings"))) {
            SubstrateLoweringProvider lowerer = (SubstrateLoweringProvider) providers.getLowerer();
            Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings = lowerer.getLowerings();

            Predicate<ResolvedJavaMethod> mustNotAllocatePredicate = null;
            if (hosted) {
                mustNotAllocatePredicate = method -> ImageSingletons.lookup(RestrictHeapAccessCallees.class).mustNotAllocate(method);
            }

            Iterable<DebugHandlersFactory> factories = runtimeConfig != null ? runtimeConfig.getDebugHandlersFactories() : Collections.singletonList(new GraalDebugHandlersFactory(snippetReflection));
            lowerer.setConfiguration(runtimeConfig, options, factories, providers, snippetReflection);
            NonSnippetLowerings.registerLowerings(runtimeConfig, mustNotAllocatePredicate, options, factories, providers, snippetReflection, lowerings);
            ArithmeticSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            MonitorSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            TypeSnippets.registerLowerings(runtimeConfig, options, factories, providers, snippetReflection, lowerings);
            ExceptionSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);

            if (hosted) {
                AssertSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
                DeoptHostedSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            } else {
                DeoptRuntimeSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
            }

            if (NativeImageOptions.DeoptimizeAll.getValue()) {
                DeoptTestSnippets.registerLowerings(options, factories, providers, snippetReflection, lowerings);
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
        Providers runtimeCallProviders = runtimeConfig.getBackendForNormalMethod().getProviders();

        OptionValues options = hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton();

        Suites suites = GraalConfiguration.instance().createSuites(options, hosted);

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

        highTier.appendPhase(new StackValuePhase());

        lowTier.addBeforeLast(new OptimizeExceptionCallsPhase());

        CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
        SubstrateAMD64RegisterConfig registerConfig = (SubstrateAMD64RegisterConfig) runtimeCallProviders.getCodeCache().getRegisterConfig();
        SubstrateAMD64AddressLowering addressLowering = new SubstrateAMD64AddressLowering(compressEncoding, registerConfig);
        lowTier.findPhase(FixReadsPhase.class).add(new AddressLoweringPhase(addressLowering));

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
            ListIterator<BasePhase<? super MidTierContext>> it = midTier.findPhase(DeoptimizationGroupingPhase.class);
            it.previous();
            it.add(new CollectDeoptimizationSourcePositionsPhase());
        }

        featureHandler.forEachGraalFeature(feature -> feature.registerGraalPhases(runtimeCallProviders, snippetReflection, suites, hosted));

        return suites;
    }

    @SuppressWarnings("unused")
    public static LIRSuites createLIRSuites(FeatureHandler featureHandler, Providers providers, boolean hosted) {
        LIRSuites lirSuites = Suites.createLIRSuites(new CommunityCompilerConfiguration(), hosted ? HostedOptionValues.singleton() : RuntimeOptionValues.singleton());
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
                TypeState state = method.getTypeFlow().getParameterTypeState(bigbang, i);
                if (state != null) {
                    AnalysisType declaredType = method.getTypeFlow().getOriginalMethodFlows().getParameter(i).getDeclaredType();
                    if (declaredType.isInterface()) {
                        state = TypeState.forSubtraction(bigbang, state, declaredType.getTypeFlow(bigbang, true).getState());
                        if (!state.isEmpty()) {
                            String methodKey = method.format("%H.%n(%p)");
                            bigbang.getUnsupportedFeatures().addMessage(methodKey, method,
                                            "Parameter " + i + " of " + methodKey + " has declared type " + declaredType.toJavaName(true) + " which is incompatible with types in state: " + state);
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
                List<AnalysisMethod> invocations = method.getJavaInvocations();
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
        } else if ((!name.startsWith("jdk.internal")) && (lname.contains("hotspot"))) {
            bigbang.getUnsupportedFeatures().addMessage(name, method, "HotSpot element used at run time: " + name);
        }
    }

    @SuppressWarnings("try")
    private NativeLibraries processNativeLibraryImports(OptionValues options, MetaAccessProvider metaAccess, AnalysisConstantReflectionProvider aConstantReflection,
                    SnippetReflectionProvider snippetReflection) {
        String imageName = NativeImageOptions.Name.getValue(options);
        try (StopTimer t = new Timer(imageName, "(cap)").start()) {

            NativeLibraries nativeLibs = new NativeLibraries(aConstantReflection, metaAccess, snippetReflection, ConfigurationValues.getTarget());

            for (Method method : loader.findAnnotatedMethods(CConstant.class)) {
                nativeLibs.loadJavaMethod(metaAccess.lookupJavaMethod(method));
            }
            for (Class<?> clazz : loader.findAnnotatedClasses(CStruct.class)) {
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
            for (Class<?> clazz : loader.findAnnotatedClasses(RawStructure.class)) {
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
            for (Class<?> clazz : loader.findAnnotatedClasses(CPointerTo.class)) {
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }
            for (Class<?> clazz : loader.findAnnotatedClasses(CEnum.class)) {
                nativeLibs.loadJavaType(metaAccess.lookupJavaType(clazz));
            }

            for (CLibrary library : loader.findAnnotations(CLibrary.class)) {
                nativeLibs.addLibrary(library.value());
            }

            nativeLibs.finish(tempDirectory());
            nativeLibs.reportErrors();
            return nativeLibs;
        }
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

    private Path tempDirectory;
    private boolean deleteTempDirectory;

    public synchronized Path tempDirectory() {
        if (tempDirectory == null) {
            try {
                String tempName = NativeImageOptions.TempDirectory.getValue();
                if (tempName == null || tempName.isEmpty()) {
                    tempDirectory = Files.createTempDirectory("SVM-");
                    deleteTempDirectory = true;
                } else {
                    tempDirectory = FileSystems.getDefault().getPath(tempName).resolve("SVM-" + System.currentTimeMillis());
                    assert !Files.exists(tempDirectory);
                    Files.createDirectories(tempDirectory);
                }
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return tempDirectory.toAbsolutePath();
    }

    private static void deleteAll(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static <T extends Enum<T>> Set<T> parseCSVtoEnum(Class<T> enumType, String csvEnumValues) {
        return Arrays.stream(csvEnumValues.split(",")).map(String::trim).filter(v -> !Objects.equals(v, "")).map(enumValue -> {
            try {
                return Enum.valueOf(enumType, enumValue);
            } catch (IllegalArgumentException iae) {
                throw VMError.shouldNotReachHere("Value '" + enumValue + "' does not exist. Available values are:\n" + Arrays.toString(AMD64.CPUFeature.values()));
            }
        }).collect(Collectors.toSet());
    }
}
