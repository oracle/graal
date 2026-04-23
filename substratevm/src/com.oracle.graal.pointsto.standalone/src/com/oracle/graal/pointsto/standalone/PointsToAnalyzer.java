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

import java.io.File;
import java.lang.reflect.Executable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

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
import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.plugins.PointstoGraphBuilderPlugins;
import com.oracle.graal.pointsto.reports.AnalysisReporter;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureManager;
import com.oracle.graal.pointsto.standalone.heap.StandaloneHeapSnapshotVerifier;
import com.oracle.graal.pointsto.standalone.heap.StandaloneImageHeapScanner;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantFieldProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
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

    /**
     * Name of the {@code VMAccess.Builder} service that is used to create the {@link VMAccess}
     * based on espresso guest context.
     */
    private static final String DEFAULT_VM_ACCESS_NAME = "espresso";
    private static final String VM_ACCESS_MODULE_PATH_PROPERTY = "com.oracle.graal.pointsto.standalone.vmaccess.modulepath";
    private static final String VM_ACCESS_UPGRADE_MODULE_PATH_PROPERTY = "com.oracle.graal.pointsto.standalone.vmaccess.upgrade.modulepath";
    private static final List<String> BASE_VM_ACCESS_MODULES = List.of("jdk.graal.compiler", "java.scripting");
    private static final String ESPRESSO_LOG_LEVEL_PROPERTY = "espresso.test.log.level";

    static {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.loader");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.text.spi");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.reflect.annotation");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.security.jca");
    }

    /**
     * Due to GR-73534, we cannot create multiple {@link VMAccess} instances backed by espresso in
     * the same process, so we reuse the first one in that case.
     *
     * This cache is process-global, so reusing it across analyzers is only correct as long as the
     * effective standalone VMAccess configuration stays the same, especially the target application
     * class path and the selected VMAccess mode.
     */
    private static ClassLoaderAccess cachedAccess;
    private static String cachedClasspath;

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
        SnippetReflectionProvider snippetReflection = originalProviders.getSnippetReflection();
        MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();
        debugContext = new DebugContext.Builder(options, new GraalDebugHandlersFactory(snippetReflection)).build();
        analysisName = getAnalysisName(mainEntryClass);
        StandaloneHost standaloneHost = new StandaloneHost(options, analysisName, false, true);
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);

        JavaKind wordKind = originalProviders.getWordTypes().getWordKind();
        AnalysisUniverse aUniverse = new AnalysisUniverse(standaloneHost, wordKind,
                        analysisPolicy, SubstitutionProcessor.IDENTITY, originalMetaAccess, new PointsToAnalysisFactory(), new StandaloneAnnotationExtractor());
        AnalysisMetaAccess aMetaAccess = new StandaloneAnalysisMetaAccess(aUniverse, originalMetaAccess);
        StandaloneConstantReflectionProvider aConstantReflection = new StandaloneConstantReflectionProvider(aMetaAccess, aUniverse, originalProviders.getConstantReflection(),
                        originalProviders.getSnippetReflection(), classLoaderAccess.isFullyIsolated());
        StandaloneConstantFieldProvider aConstantFieldProvider = new StandaloneConstantFieldProvider(aMetaAccess, originalProviders.getConstantFieldProvider(), false);
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider(aUniverse);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider,
                        originalProviders.getForeignCalls(), originalProviders.getLowerer(), null,
                        originalProviders.getStampProvider(), snippetReflection, new WordTypes(aMetaAccess, wordKind),
                        originalProviders.getPlatformConfigurationProvider(), aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider());
        Replacements replacements = new StandaloneReplacementsImpl(aProviders, new ResolvedJavaMethodBytecodeProvider(), originalProviders.getCodeCache().getTarget());
        aProviders = aProviders.copyWith(replacements);
        standaloneHost.initializeProviders(aProviders);
        ClassInclusionPolicy classInclusionPolicy = new ClassInclusionPolicy.DefaultAllInclusionPolicy("Included in the base image");
        bigbang = new StandalonePointsToAnalysis(options, aUniverse, standaloneHost, aMetaAccess, snippetReflection, aConstantReflection, aProviders.getWordTypes(),
                        debugContext, new TimerCollection(), classInclusionPolicy);
        aUniverse.setBigBang(bigbang);
        ImageHeap heap = new ImageHeap();
        HostedValuesProvider hostedValuesProvider = new HostedValuesProvider(aMetaAccess, aUniverse);
        StandaloneImageHeapScanner heapScanner = new StandaloneImageHeapScanner(bigbang, heap, aMetaAccess,
                        snippetReflection, aConstantReflection, new AnalysisObjectScanningObserver(bigbang), classLoaderAccess, hostedValuesProvider);
        aUniverse.setHeapScanner(heapScanner);
        HeapSnapshotVerifier heapVerifier = new StandaloneHeapSnapshotVerifier(bigbang, heap, heapScanner);
        aUniverse.setHeapVerifier(heapVerifier);
        /* Register already created types as assignable. */
        aUniverse.getTypes().forEach(t -> {
            t.registerAsAssignable(bigbang);
            if (t.isReachable()) {
                bigbang.onTypeReachable(t);
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
            NoClassInitializationPlugin classInitializationPlugin = new NoClassInitializationPlugin();
            plugins.setClassInitializationPlugin(classInitializationPlugin);
            aProviders.setGraphBuilderPlugins(plugins);
            PointstoGraphBuilderPlugins.registerArrayPlugins(plugins.getInvocationPlugins());
            PointstoGraphBuilderPlugins.registerSystemPlugins(plugins.getInvocationPlugins());
            PointstoGraphBuilderPlugins.registerObjectPlugins(plugins.getInvocationPlugins());
        }
        bigbang.markInitializationFinished();
    }

    private String getAnalysisName(String entryClass) {
        String entryPointsFile = StandaloneOptions.AnalysisEntryPointsFile.getValue(options);
        String entryPointsFileOptionName = StandaloneOptions.AnalysisEntryPointsFile.getName();
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
        String classpath = StandaloneOptions.AnalysisTargetAppCP.getValue(options);
        AnalysisError.guarantee(classpath != null, "Must specify analysis target application's classpath with -H:%s", StandaloneOptions.AnalysisTargetAppCP.getName());
        if (cachedAccess == null) {
            cachedAccess = initializeCachedAccess(classpath);
            cachedClasspath = classpath;
        } else {
            AnalysisError.guarantee(cachedClasspath.equals(classpath),
                            "Standalone analysis reuses a process-global VMAccess cache and cannot switch classpath from '%s' to '%s' within the same process.",
                            cachedClasspath, classpath);
        }
        return new PointsToAnalyzer(mainEntryClass, options, cachedAccess, Arrays.asList(entryMethods));
    }

    /**
     * Creates the process-global {@link ClassLoaderAccess} used by standalone analysis.
     */
    private static ClassLoaderAccess initializeCachedAccess(String classpath) {
        VMAccess access = buildVmAccess(classpath);
        GuestAccess.plantConfiguration(access);
        return new ClassLoaderAccess(access);
    }

    /**
     * Builds the {@link VMAccess} used to resolve guest application types for the requested target
     * class path.
     */
    private static VMAccess buildVmAccess(String classpath) {
        VMAccess.Builder builder = getVmAccessBuilder();
        builder.classPath(Arrays.asList(classpath.split(File.pathSeparator)));
        configureVmAccessBuilder(builder);
        return builder.build();
    }

    /**
     * Applies the standalone analysis defaults shared by host-backed and fully isolated
     * {@link VMAccess} configurations.
     */
    private static void configureVmAccessBuilder(VMAccess.Builder builder) {
        String modulePath = GraalServices.getSavedProperty(VM_ACCESS_MODULE_PATH_PROPERTY);
        if (modulePath != null) {
            builder.modulePath(Arrays.asList(modulePath.split(File.pathSeparator)));
        }
        String upgradeModulePath = GraalServices.getSavedProperty(VM_ACCESS_UPGRADE_MODULE_PATH_PROPERTY);
        if (upgradeModulePath != null) {
            builder.systemProperty("jdk.module.upgrade.path", String.join(File.pathSeparator, upgradeModulePath.split(File.pathSeparator)));
        }
        builder.addModules(BASE_VM_ACCESS_MODULES);
        builder.enableAssertions(true);
        builder.enableSystemAssertions(true);
        String logLevel = GraalServices.getSavedProperty(ESPRESSO_LOG_LEVEL_PROPERTY);
        if (logLevel != null) {
            builder.vmOption("--log.level=" + logLevel);
        }
        if (builder.isFullyIsolated()) {
            /*
             * Make sure we use the modules prepared for GraalVM.
             */
            String javaHome = GraalServices.getSavedProperty("java.home");
            AnalysisError.guarantee(javaHome != null, "Missing required property java.home.");
            builder.vmOption("JavaHome=" + javaHome);
            /*
             * This is needed for Word types.
             */
            builder.addModule("org.graalvm.word");
            builder.addModule("org.graalvm.nativeimage.guest.staging");
        }
    }

    /**
     * Looks up the requested {@code VMAccess.Builder} service and defaults to the espresso-backed
     * builder when no explicit selection was saved.
     */
    private static VMAccess.Builder getVmAccessBuilder() {
        String requestedAccessName = GraalServices.getSavedProperty("com.oracle.graal.pointsto.standalone.vmaccess.name", DEFAULT_VM_ACCESS_NAME);
        ServiceLoader<VMAccess.Builder> loader = ServiceLoader.load(VMAccess.Builder.class);
        VMAccess.Builder selected = null;
        for (VMAccess.Builder builder : loader) {
            if (requestedAccessName.equals(builder.getVMAccessName())) {
                selected = builder;
                break;
            }
        }
        if (selected == null) {
            AnalysisError.shouldNotReachHere("No VMAccess.Builder service found with name " +
                            requestedAccessName + ". Found: " +
                            loader.stream().map(p -> p.get().getVMAccessName()).collect(Collectors.joining(", ")));
        }
        return selected;
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

        public boolean isClassAllowed(AnalysisType type) {
            /*
             * GR-70770 - Analysis of classes loaded by the boot class loader should eventually be
             * supported as well.
             */
            return vmAccess.lookupBootClassLoaderType(type.toJavaName()) == null;
        }
    }

    @SuppressWarnings("try")
    public int run() {
        registerEntryMethods();
        registerFeatures();
        int exitCode = 0;
        Feature.BeforeAnalysisAccess beforeAnalysisAccess = new StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl(bigbang);
        standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.beforeAnalysis(beforeAnalysisAccess));
        try (Timer t = new Timer("analysis", analysisName)) {
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
            String entryPointsFile = StandaloneOptions.AnalysisEntryPointsFile.getValue(options);
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
