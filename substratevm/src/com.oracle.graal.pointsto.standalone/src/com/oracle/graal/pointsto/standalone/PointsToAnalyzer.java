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

import static jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.registerInvocationPlugins;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.PointsToAnalysisFactory;
import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.reports.AnalysisReporter;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureManager;
import com.oracle.graal.pointsto.standalone.heap.StandaloneHeapSnapshotVerifier;
import com.oracle.graal.pointsto.standalone.heap.StandaloneImageHeapScanner;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantFieldProvider;
import com.oracle.graal.pointsto.standalone.meta.StandaloneConstantReflectionProvider;
import com.oracle.graal.pointsto.standalone.util.Timer;
import com.oracle.graal.pointsto.typestate.DefaultAnalysisPolicy;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.graal.pointsto.util.PointsToOptionParser;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

public final class PointsToAnalyzer {

    static {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.internal.vm.ci");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.graal.compiler.management");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.loader");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.misc");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.text.spi");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.reflect.annotation");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "sun.security.jca");
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "jdk.jdeps", "com.sun.tools.classfile");
    }

    private final OptionValues options;
    private final StandalonePointsToAnalysis bigbang;
    private final StandaloneAnalysisFeatureManager standaloneAnalysisFeatureManager;
    private final ClassLoader analysisClassLoader;
    private final DebugContext debugContext;
    private StandaloneAnalysisFeatureImpl.OnAnalysisExitAccessImpl onAnalysisExitAccess;
    private final String analysisName;
    private boolean entrypointsAreSet;
    private boolean mainEntryIsSet;

    @SuppressWarnings({"try", "unchecked"})
    private PointsToAnalyzer(String mainEntryClass, OptionValues options) {
        this.options = options;
        standaloneAnalysisFeatureManager = new StandaloneAnalysisFeatureManager(options);
        String appCP = StandaloneOptions.AnalysisTargetAppCP.getValue(options);
        if (appCP == null) {
            AnalysisError.shouldNotReachHere("Must specify analysis target application's classpath with -H:" + StandaloneOptions.AnalysisTargetAppCP.getName());
        }
        List<URL> urls = new ArrayList<>();
        for (String cp : appCP.split(File.pathSeparator)) {
            try {
                File file = new File(cp);
                if (file.exists()) {
                    urls.add(file.toURI().toURL());
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        analysisClassLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
        Providers originalProviders = GraalAccess.getOriginalProviders();
        SnippetReflectionProvider snippetReflection = originalProviders.getSnippetReflection();
        MetaAccessProvider originalMetaAccess = originalProviders.getMetaAccess();
        debugContext = new DebugContext.Builder(options, new GraalDebugHandlersFactory(snippetReflection)).build();
        StandaloneHost standaloneHost = new StandaloneHost(options, analysisClassLoader);
        int wordSize = getWordSize();
        AnalysisPolicy analysisPolicy = PointstoOptions.AllocationSiteSensitiveHeap.getValue(options) ? new BytecodeSensitiveAnalysisPolicy(options)
                        : new DefaultAnalysisPolicy(options);

        JavaKind wordKind = JavaKind.fromWordSize(wordSize);
        AnalysisUniverse aUniverse = new AnalysisUniverse(standaloneHost, wordKind,
                        analysisPolicy, SubstitutionProcessor.IDENTITY, originalMetaAccess, new PointsToAnalysisFactory(), new StandaloneAnnotationExtractor());
        AnalysisMetaAccess aMetaAccess = new StandaloneAnalysisMetaAccess(aUniverse, originalMetaAccess);
        StandaloneConstantReflectionProvider aConstantReflection = new StandaloneConstantReflectionProvider(aUniverse, HotSpotJVMCIRuntime.runtime());
        StandaloneConstantFieldProvider aConstantFieldProvider = new StandaloneConstantFieldProvider(aMetaAccess);
        AnalysisMetaAccessExtensionProvider aMetaAccessExtensionProvider = new AnalysisMetaAccessExtensionProvider(aUniverse);
        HostedProviders aProviders = new HostedProviders(aMetaAccess, null, aConstantReflection, aConstantFieldProvider,
                        originalProviders.getForeignCalls(), originalProviders.getLowerer(), originalProviders.getReplacements(),
                        originalProviders.getStampProvider(), snippetReflection, new WordTypes(aMetaAccess, wordKind),
                        originalProviders.getPlatformConfigurationProvider(), aMetaAccessExtensionProvider, originalProviders.getLoopsDataProvider(), originalProviders.getIdentityHashCodeProvider());
        standaloneHost.initializeProviders(aProviders);
        analysisName = getAnalysisName(mainEntryClass);
        ClassInclusionPolicy classInclusionPolicy = new ClassInclusionPolicy.DefaultAllInclusionPolicy("Included in the base image");
        bigbang = new StandalonePointsToAnalysis(options, aUniverse, standaloneHost, aMetaAccess, snippetReflection, aConstantReflection, aProviders.getWordTypes(), debugContext,
                        new TimerCollection(), classInclusionPolicy);
        standaloneHost.setImageName(analysisName);
        aUniverse.setBigBang(bigbang);
        ImageHeap heap = new ImageHeap();
        HostedValuesProvider hostedValuesProvider = new HostedValuesProvider(aMetaAccess, aUniverse);
        StandaloneImageHeapScanner heapScanner = new StandaloneImageHeapScanner(bigbang, heap, aMetaAccess,
                        snippetReflection, aConstantReflection, new AnalysisObjectScanningObserver(bigbang), analysisClassLoader, hostedValuesProvider);
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
            registerInvocationPlugins(originalProviders.getSnippetReflection(), plugins.getInvocationPlugins(), false, true, false);
        }
    }

    private String getAnalysisName(String entryClass) {
        String entryPointsFile = StandaloneOptions.AnalysisEntryPointsFile.getValue(options);
        String entryPointsFileOptionName = StandaloneOptions.AnalysisEntryPointsFile.getName();
        mainEntryIsSet = entryClass != null && !entryClass.isBlank();
        entrypointsAreSet = entryPointsFile != null && entryPointsFile.length() != 0;
        if (!mainEntryIsSet && !entrypointsAreSet) {
            AnalysisError.shouldNotReachHere(
                            "No analysis entry are specified. Must set entry class or -H:" + entryPointsFileOptionName + " to specify the analysis entries.");
        }
        if (mainEntryIsSet) {
            return entryClass;
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

    private static int getWordSize() {
        int wordSize;
        String archModel = System.getProperty("sun.arch.data.model");
        switch (archModel) {
            case "64":
                wordSize = AMD64Kind.QWORD.getSizeInBytes();
                break;
            case "32":
                wordSize = AMD64Kind.DWORD.getSizeInBytes();
                break;
            default:
                throw new RuntimeException("Property sun.arch.data.model should only be 64 or 32, but is " + archModel);

        }
        return wordSize;
    }

    /**
     * Create a PointsToAnalyzer instance with given arguments. The arguments should specify one
     * analysis entry class, and additional analysis options in Substrate VM's hosted option style.
     *
     * @param args entry class name and additional analysis options
     * @return PointsToAnalyzer instance
     */
    public static PointsToAnalyzer createAnalyzer(String[] args) {
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
        return new PointsToAnalyzer(mainEntryClass, options);
    }

    @SuppressWarnings("try")
    public int run() {
        registerEntryMethods();
        registerFeatures();
        int exitCode = 0;
        Feature.BeforeAnalysisAccess beforeAnalysisAccess = new StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl(standaloneAnalysisFeatureManager, analysisClassLoader, bigbang, debugContext);
        standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.beforeAnalysis(beforeAnalysisAccess));
        try (Timer t = new Timer("analysis", analysisName)) {
            StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl config = new StandaloneAnalysisFeatureImpl.DuringAnalysisAccessImpl(standaloneAnalysisFeatureManager, analysisClassLoader, bigbang,
                            debugContext);
            bigbang.runAnalysis(debugContext, (analysisUniverse) -> {
                bigbang.getHostVM().notifyClassReachabilityListener(analysisUniverse, config);
                standaloneAnalysisFeatureManager.forEachFeature(feature -> feature.duringAnalysis(config));
                return !config.getAndResetRequireAnalysisIteration();
            });
        } catch (Throwable e) {
            reportException(e);
            exitCode = 1;
        }
        onAnalysisExitAccess = new StandaloneAnalysisFeatureImpl.OnAnalysisExitAccessImpl(standaloneAnalysisFeatureManager, analysisClassLoader, bigbang, debugContext);
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
            try {
                Class<?> analysisMainClass = Class.forName(entryClass, false, analysisClassLoader);
                Method main = analysisMainClass.getDeclaredMethod("main", String[].class);
                // main method is static, whatever the invokeSpecial is it is ignored.
                bigbang.addRootMethod(main, true, "Single entry point, registered in " + PointsToAnalyzer.class);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Can't find the specified analysis main class " + entryClass, e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Can't find the main method in the analysis main class " + analysisName, e);
            }
        }

        if (entrypointsAreSet) {
            String entryPointsFile = StandaloneOptions.AnalysisEntryPointsFile.getValue(options);
            MethodConfigReader.readMethodFromFile(entryPointsFile, bigbang, analysisClassLoader, m -> {
                // We need to start analyzing from any method given by user, even it is a virtual
                // method.
                boolean isInvokeSpecial = m.isConstructor() || m.isFinal();
                AnalysisType t = m.getDeclaringClass();
                if (!t.isAbstract()) {
                    t.registerAsInstantiated("Root class.");
                }
                bigbang.addRootMethod(m, isInvokeSpecial, "Entry point from file, registered in " + PointsToAnalyzer.class);
            });
        }
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
        System.err.print("Exception:");
        e.printStackTrace();
    }

    public ClassLoader getClassLoader() {
        return analysisClassLoader;
    }
}
