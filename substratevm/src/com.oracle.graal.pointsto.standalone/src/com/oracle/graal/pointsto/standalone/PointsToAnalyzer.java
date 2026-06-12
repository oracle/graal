/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import java.lang.reflect.Executable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.AnalysisObjectScanningObserver;
import com.oracle.graal.pointsto.AnalysisPolicy;
import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.bytecode.BytecodeSensitiveAnalysisPolicy;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccessExtensionProvider;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisFactory;
import com.oracle.graal.pointsto.plugins.PointstoGraphBuilderPlugins;
import com.oracle.graal.pointsto.reports.AnalysisReporter;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureManager;
import com.oracle.graal.pointsto.standalone.heap.StandaloneHeapSnapshotVerifier;
import com.oracle.graal.pointsto.standalone.heap.StandaloneFieldValueAvailabilitySupport;
import com.oracle.graal.pointsto.standalone.heap.StandaloneHostedValuesProvider;
import com.oracle.graal.pointsto.standalone.heap.StandaloneImageHeapScanner;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantFieldProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneSnippetReflectionProvider;
import com.oracle.graal.pointsto.standalone.plugins.StandaloneClassInitializationPlugin;
import com.oracle.graal.pointsto.standalone.plugins.StandaloneReplacementsImpl;
import com.oracle.graal.pointsto.standalone.util.Timer;
import com.oracle.graal.pointsto.typestate.DefaultAnalysisPolicy;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.PointsToOptionParser;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.shared.util.ModuleSupport;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.vmaccess.VMAccess;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public final class PointsToAnalyzer {

    private static final String COMMON_POOL_THREAD_FACTORY_PROPERTY = "java.util.concurrent.ForkJoinPool.common.threadFactory";

    static {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.loader");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.text.spi");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.reflect.annotation");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.security.jca");
        configureCommonPoolWorkerThreadFactory();
    }

    /**
     * The default JDK common-pool workers clear ordinary {@link ThreadLocal} state between
     * top-level tasks. Standalone Espresso VMAccess can attach guest state lazily to a worker
     * thread, so preserve that state unless the embedding JVM already configured a different common
     * pool factory explicitly.
     *
     * This is a process-wide setting for {@link ForkJoinPool#commonPool()}, not a standalone-local
     * knob. If it is applied before the common pool is initialized then unrelated application work
     * using the common pool in the same JVM will also observe
     * {@link StandaloneCommonPoolWorkerThreadFactory}. That broader behavior change is currently an
     * accepted tradeoff for standalone's embedding use case.
     */
    private static void configureCommonPoolWorkerThreadFactory() {
        if (GraalServices.getSavedProperty(COMMON_POOL_THREAD_FACTORY_PROPERTY) == null) {
            System.setProperty(COMMON_POOL_THREAD_FACTORY_PROPERTY, StandaloneCommonPoolWorkerThreadFactory.class.getName());
        }
    }

    /**
     * Espresso-backed standalone analysis relies on a common-pool worker factory that preserves
     * worker-local guest state across top-level tasks. If the process initialized the common pool
     * too early with a different factory, fail fast before analysis starts instead of running with
     * a silently ineffective safepoint workaround.
     */
    private static void validateCommonPoolWorkerThreadFactory() {
        if (!StandaloneVMAccessSupport.requiresStandaloneCommonPoolWorkerFactory()) {
            return;
        }
        ForkJoinPool.ForkJoinWorkerThreadFactory actualFactory = ForkJoinPool.commonPool().getFactory();
        AnalysisError.guarantee(actualFactory instanceof StandaloneCommonPoolWorkerThreadFactory,
                        "Standalone Espresso analysis requires ForkJoinPool.commonPool() to use %s, but found %s. " +
                                        "Set -D%s=%s at JVM startup before the common pool is initialized.",
                        StandaloneCommonPoolWorkerThreadFactory.class.getName(),
                        actualFactory.getClass().getName(),
                        COMMON_POOL_THREAD_FACTORY_PROPERTY,
                        StandaloneCommonPoolWorkerThreadFactory.class.getName());
    }

    private final OptionValues options;
    private final StandalonePointsToAnalysis bigbang;
    private final StandaloneAnalysisFeatureManager standaloneAnalysisFeatureManager;
    private final ClassLoaderAccess classLoaderAccess;
    private final List<Executable> directEntryMethods;
    private final DebugContext debugContext;
    private StandaloneAnalysisFeatureImpl.OnAnalysisExitAccessImpl onAnalysisExitAccess;
    private final String analysisName;
    private boolean entrypointsAreSet;
    private boolean mainEntryIsSet;
    private boolean directEntryMethodsAreSet;

    @SuppressWarnings({"try"})
    private PointsToAnalyzer(String mainEntryClass, OptionValues options, ClassLoaderAccess classLoaderAccess, List<Executable> directEntryMethods) {
        this.options = options;
        standaloneAnalysisFeatureManager = new StandaloneAnalysisFeatureManager(options);
        this.classLoaderAccess = classLoaderAccess;
        this.directEntryMethods = List.copyOf(directEntryMethods);
        Providers originalProviders = GuestAccess.get().getProviders();
        SnippetReflectionProvider originalSnippetReflection = originalProviders.getSnippetReflection();
        MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();
        debugContext = new DebugContext.Builder(options, new GraalDebugHandlersFactory(originalSnippetReflection)).build();
        analysisName = getAnalysisName(mainEntryClass);
        StandaloneHost standaloneHost = new StandaloneHost(options, analysisName, createClassInitializationStrategy(), StandaloneOptions.StandaloneClosedTypeWorld.getValue(options));
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);

        JavaKind wordKind = originalProviders.getWordTypes().getWordKind();
        AnalysisUniverse aUniverse = new AnalysisUniverse(standaloneHost, wordKind,
                        analysisPolicy, SubstitutionProcessor.IDENTITY, originalMetaAccess, new PointsToAnalysisFactory(), new StandaloneAnnotationExtractor());
        AnalysisMetaAccess aMetaAccess = new StandaloneAnalysisMetaAccess(aUniverse, originalMetaAccess);
        WordTypes aWordTypes = new WordTypes(aMetaAccess, wordKind);
        StandaloneSnippetReflectionProvider snippetReflection = new StandaloneSnippetReflectionProvider(null, originalSnippetReflection, aWordTypes);
        StandaloneFieldValueAvailabilitySupport fieldValueAvailabilitySupport = new StandaloneFieldValueAvailabilitySupport(standaloneHost);
        StandaloneConstantReflectionProvider aConstantReflection = new StandaloneConstantReflectionProvider(aMetaAccess, aUniverse, originalProviders.getConstantReflection(),
                        originalSnippetReflection, classLoaderAccess.isFullyIsolated(), fieldValueAvailabilitySupport);
        StandaloneConstantFieldProvider aConstantFieldProvider = new StandaloneConstantFieldProvider(aMetaAccess, originalProviders.getConstantFieldProvider());
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider(aUniverse);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider,
                        originalProviders.getForeignCalls(), originalProviders.getLowerer(), null,
                        originalProviders.getStampProvider(), snippetReflection, aWordTypes,
                        originalProviders.getPlatformConfigurationProvider(), aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());
        Replacements replacements = new StandaloneReplacementsImpl(aProviders, new ResolvedJavaMethodBytecodeProvider(), originalProviders.getCodeCache().getTarget());
        aProviders = aProviders.copyWith(replacements);
        standaloneHost.initializeProviders(aProviders);
        ClassInclusionPolicy classInclusionPolicy = new ClassInclusionPolicy.DefaultAllInclusionPolicy("Included in the base image");
        bigbang = new StandalonePointsToAnalysis(options, aUniverse, standaloneHost, aMetaAccess, snippetReflection, aConstantReflection, aProviders.getWordTypes(),
                        debugContext, new TimerCollection(), classInclusionPolicy);
        aUniverse.setBigBang(bigbang);
        ImageHeap heap = new ImageHeap();
        HostedValuesProvider hostedValuesProvider = new StandaloneHostedValuesProvider(aMetaAccess, aUniverse, fieldValueAvailabilitySupport);
        StandaloneImageHeapScanner heapScanner = new StandaloneImageHeapScanner(bigbang, heap, aMetaAccess,
                        snippetReflection, aConstantReflection, new AnalysisObjectScanningObserver(bigbang), hostedValuesProvider);
        aUniverse.setHeapScanner(heapScanner);
        snippetReflection.setHeapScanner(heapScanner);
        HeapSnapshotVerifier heapVerifier = new StandaloneHeapSnapshotVerifier(bigbang, heap, heapScanner);
        aUniverse.setHeapVerifier(heapVerifier);
        /* Register already created types as assignable and replay full reachability handling. */
        aUniverse.getTypes().forEach(t -> {
            t.registerAsAssignable(bigbang);
            if (t.isReachable()) {
                aUniverse.onTypeReachable(t);
            }
        });
        /*
         * System classes and fields are necessary to tell the static analysis that certain things
         * really "exist". The most common reason for that is that there are no instances and
         * allocations of these classes seen during the static analysis. The heap chunks are one
         * good example.
         */
        try (Indent ignored = debugContext.logAndIndent("add initial classes/fields/methods")) {
            bigbang.addRootClass(Object.class, false, false).registerAsInstantiated("root class");
            bigbang.addRootClass(String.class, false, false).registerAsInstantiated("root class");
            bigbang.addRootClass(String[].class, false, false).registerAsInstantiated("root class");
            bigbang.addRootField(String.class, "value").registerAsInstantiated("root class");
            bigbang.addRootClass(long[].class, false, false).registerAsInstantiated("root class");
            bigbang.addRootClass(byte[].class, false, false).registerAsInstantiated("root class");
            bigbang.addRootClass(byte[][].class, false, false).registerAsInstantiated("root class");
            bigbang.addRootClass(Object[].class, false, false).registerAsInstantiated("root class");

            var rootReason = "Registered in " + PointsToAnalyzer.class;
            bigbang.addRootMethod(ReflectionUtil.lookupMethod(Object.class, "getClass"), true, rootReason);

            for (JavaKind kind : JavaKind.values()) {
                if (kind.isPrimitive() && kind != JavaKind.Void) {
                    bigbang.addRootClass(kind.toJavaClass(), false, true);
                    bigbang.addRootField(kind.toBoxedJavaClass(), "value");
                    bigbang.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), "valueOf", kind.toJavaClass()), true, rootReason);
                    bigbang.addRootMethod(ReflectionUtil.lookupMethod(kind.toBoxedJavaClass(), kind.getJavaName() + "Value"), true, rootReason);
                    /*
                     * Register the cache location as reachable.
                     * BoxingSnippets$Templates#getCacheLocation accesses the cache field.
                     */
                    Class<?>[] innerClasses = kind.toBoxedJavaClass().getDeclaredClasses();
                    if (innerClasses != null && innerClasses.length > 0) {
                        bigbang.getMetaAccess().lookupJavaType(innerClasses[0]).registerAsReachable("root class");
                    }
                }
            }
            bigbang.getMetaAccess().lookupJavaType(JavaKind.Void.toJavaClass()).registerAsReachable("root class");

            GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
            plugins.setClassInitializationPlugin(new StandaloneClassInitializationPlugin(standaloneHost));
            aProviders.setGraphBuilderPlugins(plugins);
            PointstoGraphBuilderPlugins.registerArrayPlugins(plugins.getInvocationPlugins());
            PointstoGraphBuilderPlugins.registerSystemPlugins(plugins.getInvocationPlugins());
            PointstoGraphBuilderPlugins.registerObjectPlugins(plugins.getInvocationPlugins());
        }
        bigbang.markInitializationFinished();
    }

    private StandaloneClassInitializationStrategy createClassInitializationStrategy() {
        return new BuildTimeHeapClassInitializationStrategy(options);
    }

    private String getAnalysisName(String entryClass) {
        String entryPointsFile = StandaloneOptions.StandaloneAnalysisEntryPointsFile.getValue(options);
        String entryPointsFileOptionName = StandaloneOptions.StandaloneAnalysisEntryPointsFile.getName();
        mainEntryIsSet = entryClass != null && !entryClass.isBlank();
        entrypointsAreSet = entryPointsFile != null && entryPointsFile.length() != 0;
        directEntryMethodsAreSet = !directEntryMethods.isEmpty();
        if (!mainEntryIsSet && !entrypointsAreSet && !directEntryMethodsAreSet) {
            AnalysisError.shouldNotReachHere(
                            "No analysis entry are specified. Must set entry class, direct entry method, or -H:" + entryPointsFileOptionName + " to specify the analysis entries.");
        }
        if (mainEntryIsSet) {
            return entryClass;
        } else if (directEntryMethodsAreSet) {
            return directEntryMethods.get(0).getDeclaringClass().getName();
        } else {
            Path entryFilePath = Paths.get(entryPointsFile);
            Path fileName = entryFilePath.getFileName();
            if (fileName == null) {
                return "Null";
            } else {
                return fileName.toString();
            }
        }
    }

    /**
     * Create a PointsToAnalyzer instance with given arguments. The arguments should specify one
     * analysis entry class, and additional analysis options in Substrate VM's hosted option style.
     * Reuses the process-global {@link ClassLoaderAccess} cache when it has already been
     * initialized.
     *
     * @param args entry class name and additional analysis options
     * @return PointsToAnalyzer instance
     */
    public static PointsToAnalyzer createAnalyzer(String[] args) {
        return createAnalyzer(args, new Executable[0]);
    }

    /**
     * Create a {@link PointsToAnalyzer} instance with given arguments and reflective entry methods.
     * The arguments should specify hosted-style analysis options and may also specify one analysis
     * entry class. Reuses the process-global {@link ClassLoaderAccess} cache when it has already
     * been initialized.
     *
     * GR-74882 intentionally keeps this first direct-root API reflection-based so standalone tests
     * can register roots without temporary entry-points files. Follow-up issue GR-74896 tracks the
     * richer API that should accept resolved JVMCI elements directly instead of requiring
     * {@link Executable} handles.
     *
     * @param args entry class name and additional analysis options
     * @param entryMethods reflective entry methods to register as analysis roots
     * @return PointsToAnalyzer instance
     */
    public static PointsToAnalyzer createAnalyzer(String[] args, Executable... entryMethods) {
        String mainEntryClass = null;
        List<String> optionArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                optionArgs.add(arg);
            } else {
                mainEntryClass = arg;
            }
        }
        OptionValues options = PointsToOptionParser.getInstance().parse(optionArgs.toArray(new String[0]));
        String classpath = StandaloneOptions.StandaloneAnalysisTargetAppCP.getValue(options);
        AnalysisError.guarantee(classpath != null, "Must specify analysis target application's classpath with -H:%s", StandaloneOptions.StandaloneAnalysisTargetAppCP.getName());
        VMAccess access = StandaloneVMAccessSupport.getOrCreateVMAccess(classpath);
        validateCommonPoolWorkerThreadFactory();
        return new PointsToAnalyzer(mainEntryClass, options, new ClassLoaderAccess(access), Arrays.asList(entryMethods));
    }

    /**
     * Encapsulates the access to class loaders to decouple the standalone analysis from hotspot.
     */
    public static class ClassLoaderAccess {
        private final VMAccess vmAccess;

        public ClassLoaderAccess(VMAccess vmAccess) {
            this.vmAccess = vmAccess;
        }

        public ResolvedJavaType forName(String name) {
            return vmAccess.lookupAppClassLoaderType(name);
        }

        /**
         * Returns whether the wrapped {@link VMAccess} resolves application classes in a fully
         * isolated guest VM.
         */
        public boolean isFullyIsolated() {
            return vmAccess.isFullyIsolated();
        }

    }

    @SuppressWarnings("try")
    public int run() {
        registerEntryMethods();
        registerFeatures();
        int exitCode = 0;
        Feature.BeforeAnalysisAccess beforeAnalysisAccess = new StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl(bigbang);
        standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.beforeAnalysis(beforeAnalysisAccess));
        try (Timer t = new Timer("analysis", analysisName, bigbang.getUniverse(), (StandaloneHost) bigbang.getHostVM())) {
            StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl config = new StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl(bigbang);
            bigbang.getUniverse().setConcurrentAnalysisAccess(config);
            bigbang.runAnalysis(debugContext, (analysisUniverse) -> {
                bigbang.getHostVM().notifyClassReachabilityListener(analysisUniverse, config);
                standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.duringAnalysis(config));
                return !config.getAndResetRequireAnalysisIteration();
            });
        } catch (Throwable e) {
            reportException(e);
            exitCode = 1;
        }
        onAnalysisExitAccess = new StandaloneAnalysisFeatureImpl.OnAnalysisExitAccessImpl(bigbang);
        standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.onAnalysisExit(onAnalysisExitAccess));
        AnalysisReporter.printAnalysisReports("pointsto_" + analysisName, options, StandaloneOptions.reportsPath(options, "reports").toString(), bigbang);
        bigbang.getUnsupportedFeatures().report(bigbang);
        return exitCode;
    }

    /**
     * Clean up all analysis data. This method is called by user, not by the analysis framework,
     * because user may still use the analysis results after the pointsto analysis.
     */
    public void cleanUp() {
        bigbang.cleanupAfterAnalysis();
    }

    public AnalysisUniverse getResultUniverse() {
        return bigbang.getUniverse();
    }

    /**
     * Returns the completed standalone analysis instance from the most recent {@link #run()} call.
     */
    public StandalonePointsToAnalysis getResultAnalysis() {
        return bigbang;
    }

    public Object getResultFromFeature(Class<? extends Feature> feature) {
        return onAnalysisExitAccess.getResult(feature);
    }

    private void registerFeatures() {
        standaloneAnalysisFeatureManager.registerFeaturesFromOptions();
    }

    /**
     * Register analysis entry points.
     */
    public void registerEntryMethods() {
        if (mainEntryIsSet) {
            String entryClass = analysisName;
            ResolvedJavaType mainType = classLoaderAccess.forName(entryClass);
            if (mainType == null) {
                throw new RuntimeException("Can't find the specified analysis main class " + entryClass);
            }
            Signature signature = GuestAccess.get().getProviders().getMetaAccess().parseMethodDescriptor("([Ljava/lang/String;)V");
            ResolvedJavaMethod mainMethod = mainType.findMethod("main", signature);
            if (mainMethod == null) {
                throw new RuntimeException("Can't find the main method in the analysis main class " + analysisName);
            }
            bigbang.addRootMethod(bigbang.getUniverse().lookup(mainMethod), true, "Single entry point, registered in " + PointsToAnalyzer.class);
        }

        if (entrypointsAreSet) {
            String entryPointsFile = StandaloneOptions.StandaloneAnalysisEntryPointsFile.getValue(options);
            MethodConfigReader.readMethodFromFile(entryPointsFile, bigbang, classLoaderAccess, m -> {
                registerUserEntryMethod(m, "Entry point from file, registered in " + PointsToAnalyzer.class);
            });
        }

        for (Executable entryMethod : directEntryMethods) {
            registerUserEntryMethod(bigbang.getMetaAccess().lookupJavaMethod(entryMethod), "Direct entry point, registered in " + PointsToAnalyzer.class);
        }
    }

    /**
     * Registers a user-specified entry method and preserves the same virtual-entry semantics used
     * for file-driven roots.
     */
    private void registerUserEntryMethod(AnalysisMethod method, String reason) {
        /*
         * We need to start analyzing from any method given by user, even if it is a virtual method.
         */
        boolean isInvokeSpecial = method.isConstructor() || method.isFinal();
        AnalysisType declaringType = method.getDeclaringClass();
        if (!declaringType.isAbstract()) {
            declaringType.registerAsInstantiated("Root class.");
        }
        bigbang.addRootMethod(method, isInvokeSpecial, reason);
    }

    /**
     * Need --add-exports=java.base/jdk.internal.module=ALL-UNNAMED in command line.
     *
     * @param args options to run the analyzing
     */
    public static void main(String[] args) {
        PointsToAnalyzer analyzer = createAnalyzer(args);
        analyzer.run();
    }

    protected static void reportException(Throwable e) {
        System.out.print("Exception:");
        e.printStackTrace(System.out);
    }
}
