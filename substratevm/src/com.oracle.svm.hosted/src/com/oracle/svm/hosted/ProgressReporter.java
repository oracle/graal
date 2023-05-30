/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.hosted.ProgressReporterJsonHelper.UNAVAILABLE_METRIC;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.jdk.resources.ResourceStorageEntry;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonWriter;
import com.oracle.svm.hosted.ProgressReporterFeature.UserRecommendation;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.AnalysisResults;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.GeneralInfo;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.ImageDetailKey;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.JsonMetric;
import com.oracle.svm.hosted.ProgressReporterJsonHelper.ResourceUsageKey;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.code.CompileQueue.CompileTask;
import com.oracle.svm.hosted.image.AbstractImage.NativeImageKind;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.hosted.util.CPUType;
import com.oracle.svm.hosted.util.VMErrorReporter;
import com.oracle.svm.util.ImageBuildStatistics;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

public class ProgressReporter {
    private static final boolean IS_CI = SubstrateUtil.isRunningInCI();
    private static final int CHARACTERS_PER_LINE;
    private static final String HEADLINE_SEPARATOR;
    private static final String LINE_SEPARATOR;
    private static final int MAX_NUM_BREAKDOWN = 10;
    public static final String STAGE_DOCS_URL = "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md";
    private static final double EXCESSIVE_GC_MIN_THRESHOLD_MILLIS = 15_000;
    private static final double EXCESSIVE_GC_RATIO = 0.5;
    private static final String BREAKDOWN_BYTE_ARRAY_PREFIX = "byte[] for ";

    private final NativeImageSystemIOWrappers builderIO;

    public final ProgressReporterJsonHelper jsonHelper;
    private final DirectPrinter linePrinter = new DirectPrinter();
    private final StringBuilder buildOutputLog = new StringBuilder();
    private final StagePrinter<?> stagePrinter;
    private final ColorStrategy colorStrategy;
    private final LinkStrategy linkStrategy;
    private final boolean usePrefix;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Long> codeBreakdown = new HashMap<>();
    private final Map<String, Long> heapBreakdown = new HashMap<>();

    private String outputPrefix = "";
    private long lastGCCheckTimeMillis = System.currentTimeMillis();
    private GCStats lastGCStats = GCStats.getCurrent();
    private long numRuntimeCompiledMethods = -1;
    private long graphEncodingByteLength = -1;
    private int numJNIClasses = -1;
    private int numJNIFields = -1;
    private int numJNIMethods = -1;
    private Timer debugInfoTimer;
    private boolean creationStageEndCompleted = false;
    private boolean reportStringBytes = true;

    /**
     * Build stages displayed as part of the Native Image build output. Changing this enum may
     * require updating the doc entries for each stage in the BuildOutput.md.
     */
    private enum BuildStage {
        INITIALIZING("Initializing"),
        ANALYSIS("Performing analysis", true, false),
        UNIVERSE("Building universe"),
        PARSING("Parsing methods", true, true),
        INLINING("Inlining methods", true, false),
        COMPILING("Compiling methods", true, true),
        LAYOUTING("Layouting methods", true, true),
        CREATING("Creating image", true, true);

        private static final int NUM_STAGES = values().length;

        private final String message;
        private final boolean hasProgressBar;
        private final boolean hasPeriodicProgress;

        BuildStage(String message) {
            this(message, false, false);
        }

        BuildStage(String message, boolean hasProgressBar, boolean hasPeriodicProgress) {
            this.message = message;
            this.hasProgressBar = hasProgressBar;
            this.hasPeriodicProgress = hasPeriodicProgress;
        }
    }

    static {
        CHARACTERS_PER_LINE = IS_CI ? ProgressReporterCHelper.MAX_CHARACTERS_PER_LINE : ProgressReporterCHelper.getTerminalWindowColumnsClamped();
        HEADLINE_SEPARATOR = Utils.stringFilledWith(CHARACTERS_PER_LINE, "=");
        LINE_SEPARATOR = Utils.stringFilledWith(CHARACTERS_PER_LINE, "-");
    }

    public static ProgressReporter singleton() {
        return ImageSingletons.lookup(ProgressReporter.class);
    }

    public ProgressReporter(OptionValues options) {
        if (SubstrateOptions.BuildOutputSilent.getValue(options)) {
            builderIO = NativeImageSystemIOWrappers.disabled();
        } else {
            builderIO = NativeImageSystemIOWrappers.singleton();
        }
        jsonHelper = new ProgressReporterJsonHelper();
        usePrefix = SubstrateOptions.BuildOutputPrefix.getValue(options);
        boolean enableColors = SubstrateOptions.BuildOutputColorful.getValue(options);
        colorStrategy = enableColors ? new ColorfulStrategy() : new ColorlessStrategy();
        stagePrinter = SubstrateOptions.BuildOutputProgress.getValue(options) ? new CharacterwiseStagePrinter() : new LinewiseStagePrinter();
        linkStrategy = SubstrateOptions.BuildOutputLinks.getValue(options) ? new LinkyStrategy() : new LinklessStrategy();
    }

    public void setNumRuntimeCompiledMethods(int value) {
        numRuntimeCompiledMethods = value;
    }

    public void setGraphEncodingByteLength(int value) {
        graphEncodingByteLength = value;
    }

    public void setJNIInfo(int numClasses, int numFields, int numMethods) {
        numJNIClasses = numClasses;
        numJNIFields = numFields;
        numJNIMethods = numMethods;
    }

    public void disableStringBytesReporting() {
        reportStringBytes = false;
    }

    public void printStart(String imageName, NativeImageKind imageKind) {
        if (usePrefix) {
            // Add the PID to further disambiguate concurrent builds of images with the same name
            outputPrefix = String.format("[%s:%s] ", imageName, GraalServices.getExecutionID());
            stagePrinter.progressBarStart += outputPrefix.length();
        }
        l().printHeadlineSeparator();
        recordJsonMetric(GeneralInfo.IMAGE_NAME, imageName);
        String imageKindName = imageKind.name().toLowerCase().replace('_', ' ');
        l().blueBold().link("GraalVM Native Image", "https://www.graalvm.org/native-image/").reset()
                        .a(": Generating '").bold().a(imageName).reset().a("' (").doclink(imageKindName, "#glossary-imagekind").a(")...").println();
        l().printHeadlineSeparator();
        stagePrinter.start(BuildStage.INITIALIZING);
    }

    public void printUnsuccessfulInitializeEnd() {
        if (stagePrinter.activeBuildStage != null) {
            stagePrinter.end(0);
        }
    }

    public void printInitializeEnd() {
        stagePrinter.end(getTimer(TimerCollection.Registry.CLASSLIST).getTotalTime() + getTimer(TimerCollection.Registry.SETUP).getTotalTime());
        VM vm = ImageSingletons.lookup(VM.class);
        recordJsonMetric(GeneralInfo.JAVA_VERSION, vm.version);
        recordJsonMetric(GeneralInfo.VENDOR_VERSION, vm.vendorVersion);
        recordJsonMetric(GeneralInfo.GRAALVM_VERSION, vm.vendorVersion); // deprecated
        l().a(" ").doclink("Java version", "#glossary-java-info").a(": ").a(vm.version).a(", ").doclink("vendor version", "#glossary-java-info").a(": ").a(vm.vendorVersion).println();
        String optimizationLevel = SubstrateOptions.Optimize.getValue();
        recordJsonMetric(GeneralInfo.GRAAL_COMPILER_OPTIMIZATION_LEVEL, optimizationLevel);
        String march = CPUType.getSelectedOrDefaultMArch();
        recordJsonMetric(GeneralInfo.GRAAL_COMPILER_MARCH, march);
        DirectPrinter graalLine = l().a(" ").doclink("Graal compiler", "#glossary-graal-compiler").a(": optimization level: %s, target machine: %s", optimizationLevel, march);
        ImageSingletons.lookup(ProgressReporterFeature.class).appendGraalSuffix(graalLine);
        graalLine.println();
        String cCompilerShort = null;
        if (ImageSingletons.contains(CCompilerInvoker.class)) {
            cCompilerShort = ImageSingletons.lookup(CCompilerInvoker.class).compilerInfo.getShortDescription();
            l().a(" ").doclink("C compiler", "#glossary-ccompiler").a(": ").a(cCompilerShort).println();
        }
        recordJsonMetric(GeneralInfo.CC, cCompilerShort);
        String gcName = Heap.getHeap().getGC().getName();
        recordJsonMetric(GeneralInfo.GC, gcName);
        long maxHeapSize = SubstrateGCOptions.MaxHeapSize.getValue();
        String maxHeapValue = maxHeapSize == 0 ? Heap.getHeap().getGC().getDefaultMaxHeapSize() : ByteFormattingUtil.bytesToHuman(maxHeapSize);
        l().a(" ").doclink("Garbage collector", "#glossary-gc").a(": ").a(gcName).a(" (").doclink("max heap size", "#glossary-gc-max-heap-size").a(": ").a(maxHeapValue).a(")").println();
    }

    public void printFeatures(List<Feature> features) {
        int numFeatures = features.size();
        if (numFeatures > 0) {
            l().a(" ").a(numFeatures).a(" ").doclink("user-specific feature(s)", "#glossary-user-specific-features").println();
            features.sort((a, b) -> a.getClass().getName().compareTo(b.getClass().getName()));
            for (Feature feature : features) {
                printFeature(l(), feature);
            }
        }
    }

    private static void printFeature(DirectPrinter printer, Feature feature) {
        printer.a(" - ");
        String name = feature.getClass().getName();
        String url = feature.getURL();
        if (url != null) {
            printer.link(name, url);
        } else {
            printer.a(name);
        }
        String description = feature.getDescription();
        if (description != null) {
            printer.a(": ").a(description);
        }
        printer.println();
    }

    public ReporterClosable printAnalysis(AnalysisUniverse universe, Collection<String> libraries) {
        return print(TimerCollection.Registry.ANALYSIS, BuildStage.ANALYSIS, () -> printAnalysisStatistics(universe, libraries));
    }

    private ReporterClosable print(TimerCollection.Registry registry, BuildStage buildStage) {
        return print(registry, buildStage, null);
    }

    private ReporterClosable print(TimerCollection.Registry registry, BuildStage buildStage, Runnable extraPrint) {
        Timer timer = getTimer(registry);
        timer.start();
        stagePrinter.start(buildStage);
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                stagePrinter.end(timer);
                if (extraPrint != null) {
                    extraPrint.run();
                }
            }
        };
    }

    private void printAnalysisStatistics(AnalysisUniverse universe, Collection<String> libraries) {
        String actualVsTotalFormat = "%,8d (%5.2f%%) of %,6d";
        long reachableTypes = universe.getTypes().stream().filter(t -> t.isReachable()).count();
        long totalTypes = universe.getTypes().size();
        recordJsonMetric(AnalysisResults.TYPES_TOTAL, totalTypes);
        recordJsonMetric(AnalysisResults.DEPRECATED_CLASS_TOTAL, totalTypes);
        recordJsonMetric(AnalysisResults.TYPES_REACHABLE, reachableTypes);
        recordJsonMetric(AnalysisResults.DEPRECATED_CLASS_REACHABLE, reachableTypes);
        l().a(actualVsTotalFormat, reachableTypes, Utils.toPercentage(reachableTypes, totalTypes), totalTypes)
                        .a(" types ").doclink("reachable", "#glossary-reachability").println();
        Collection<AnalysisField> fields = universe.getFields();
        long reachableFields = fields.stream().filter(f -> f.isAccessed()).count();
        int totalFields = fields.size();
        recordJsonMetric(AnalysisResults.FIELD_TOTAL, totalFields);
        recordJsonMetric(AnalysisResults.FIELD_REACHABLE, reachableFields);
        l().a(actualVsTotalFormat, reachableFields, Utils.toPercentage(reachableFields, totalFields), totalFields)
                        .a(" fields ").doclink("reachable", "#glossary-reachability").println();
        Collection<AnalysisMethod> methods = universe.getMethods();
        long reachableMethods = methods.stream().filter(m -> m.isReachable()).count();
        int totalMethods = methods.size();
        recordJsonMetric(AnalysisResults.METHOD_TOTAL, totalMethods);
        recordJsonMetric(AnalysisResults.METHOD_REACHABLE, reachableMethods);
        l().a(actualVsTotalFormat, reachableMethods, Utils.toPercentage(reachableMethods, totalMethods), totalMethods)
                        .a(" methods ").doclink("reachable", "#glossary-reachability").println();
        if (numRuntimeCompiledMethods >= 0) {
            recordJsonMetric(ImageDetailKey.RUNTIME_COMPILED_METHODS_COUNT, numRuntimeCompiledMethods);
            l().a(actualVsTotalFormat, numRuntimeCompiledMethods, Utils.toPercentage(numRuntimeCompiledMethods, totalMethods), totalMethods)
                            .a(" methods included for ").doclink("runtime compilation", "#glossary-runtime-methods").println();
        }
        String typesFieldsMethodFormat = "%,8d types, %,5d fields, and %,5d methods ";
        int reflectClassesCount = ClassForNameSupport.count();
        ReflectionHostedSupport rs = ImageSingletons.lookup(ReflectionHostedSupport.class);
        int reflectFieldsCount = rs.getReflectionFieldsCount();
        int reflectMethodsCount = rs.getReflectionMethodsCount();
        recordJsonMetric(AnalysisResults.METHOD_REFLECT, reflectMethodsCount);
        recordJsonMetric(AnalysisResults.TYPES_REFLECT, reflectClassesCount);
        recordJsonMetric(AnalysisResults.DEPRECATED_CLASS_REFLECT, reflectClassesCount);
        recordJsonMetric(AnalysisResults.FIELD_REFLECT, reflectFieldsCount);
        l().a(typesFieldsMethodFormat, reflectClassesCount, reflectFieldsCount, reflectMethodsCount)
                        .doclink("registered for reflection", "#glossary-reflection-registrations").println();
        recordJsonMetric(AnalysisResults.METHOD_JNI, (numJNIMethods >= 0 ? numJNIMethods : UNAVAILABLE_METRIC));
        recordJsonMetric(AnalysisResults.TYPES_JNI, (numJNIClasses >= 0 ? numJNIClasses : UNAVAILABLE_METRIC));
        recordJsonMetric(AnalysisResults.DEPRECATED_CLASS_JNI, (numJNIClasses >= 0 ? numJNIClasses : UNAVAILABLE_METRIC));
        recordJsonMetric(AnalysisResults.FIELD_JNI, (numJNIFields >= 0 ? numJNIFields : UNAVAILABLE_METRIC));
        if (numJNIClasses >= 0) {
            l().a(typesFieldsMethodFormat, numJNIClasses, numJNIFields, numJNIMethods)
                            .doclink("registered for JNI access", "#glossary-jni-access-registrations").println();
        }
        int numLibraries = libraries.size();
        if (numLibraries > 0) {
            TreeSet<String> sortedLibraries = new TreeSet<>(libraries);
            l().a("%,8d native %s: ", numLibraries, numLibraries == 1 ? "library" : "libraries").a(String.join(", ", sortedLibraries)).println();
        }
    }

    public ReporterClosable printUniverse() {
        return print(TimerCollection.Registry.UNIVERSE, BuildStage.UNIVERSE);
    }

    public ReporterClosable printParsing() {
        return print(TimerCollection.Registry.PARSE, BuildStage.PARSING);
    }

    public ReporterClosable printInlining() {
        return print(TimerCollection.Registry.INLINE, BuildStage.INLINING);
    }

    public ReporterClosable printCompiling() {
        return print(TimerCollection.Registry.COMPILE, BuildStage.COMPILING);
    }

    public ReporterClosable printLayouting() {
        return print(TimerCollection.Registry.LAYOUT, BuildStage.LAYOUTING);
    }

    // TODO: merge printCreationStart and printCreationEnd at some point (GR-35721).
    public void printCreationStart() {
        stagePrinter.start(BuildStage.CREATING);
    }

    public void setDebugInfoTimer(Timer timer) {
        this.debugInfoTimer = timer;
    }

    public void printCreationEnd(int imageFileSize, int numHeapObjects, long imageHeapSize, int codeAreaSize,
                    int numCompilations, int debugInfoSize) {
        recordJsonMetric(ImageDetailKey.IMAGE_HEAP_OBJECT_COUNT, numHeapObjects);
        Timer imageTimer = getTimer(TimerCollection.Registry.IMAGE);
        Timer writeTimer = getTimer(TimerCollection.Registry.WRITE);
        stagePrinter.end(imageTimer.getTotalTime() + writeTimer.getTotalTime());
        creationStageEndCompleted = true;
        String format = "%9s (%5.2f%%) for ";
        l().a(format, ByteFormattingUtil.bytesToHuman(codeAreaSize), Utils.toPercentage(codeAreaSize, imageFileSize))
                        .doclink("code area", "#glossary-code-area").a(":%,10d compilation units", numCompilations).println();
        int numResources = Resources.singleton().count();
        recordJsonMetric(ImageDetailKey.IMAGE_HEAP_RESOURCE_COUNT, numResources);
        l().a(format, ByteFormattingUtil.bytesToHuman(imageHeapSize), Utils.toPercentage(imageHeapSize, imageFileSize))
                        .doclink("image heap", "#glossary-image-heap").a(":%,9d objects and %,d resources", numHeapObjects, numResources).println();
        if (debugInfoSize > 0) {
            recordJsonMetric(ImageDetailKey.DEBUG_INFO_SIZE, debugInfoSize); // Optional metric
            DirectPrinter l = l().a(format, ByteFormattingUtil.bytesToHuman(debugInfoSize), Utils.toPercentage(debugInfoSize, imageFileSize))

                            .doclink("debug info", "#glossary-debug-info");
            if (debugInfoTimer != null) {
                l.a(" generated in %.1fs", Utils.millisToSeconds(debugInfoTimer.getTotalTime()));
            }
            l.println();
        }
        long otherBytes = imageFileSize - codeAreaSize - imageHeapSize - debugInfoSize;
        recordJsonMetric(ImageDetailKey.IMAGE_HEAP_SIZE, imageHeapSize);
        recordJsonMetric(ImageDetailKey.TOTAL_SIZE, imageFileSize);
        recordJsonMetric(ImageDetailKey.CODE_AREA_SIZE, codeAreaSize);
        recordJsonMetric(ImageDetailKey.NUM_COMP_UNITS, numCompilations);
        l().a(format, ByteFormattingUtil.bytesToHuman(otherBytes), Utils.toPercentage(otherBytes, imageFileSize))
                        .doclink("other data", "#glossary-other-data").println();
        l().a("%9s in total", ByteFormattingUtil.bytesToHuman(imageFileSize)).println();
        printBreakdowns();
        printRecommendations();
    }

    public void ensureCreationStageEndCompleted() {
        if (!creationStageEndCompleted) {
            println();
        }
    }

    public void createBreakdowns(HostedMetaAccess metaAccess, Collection<CompileTask> compilationTasks, Collection<ObjectInfo> heapObjects) {
        if (!SubstrateOptions.BuildOutputBreakdowns.getValue()) {
            return;
        }
        calculateCodeBreakdown(compilationTasks);
        calculateHeapBreakdown(metaAccess, heapObjects);
    }

    private void calculateCodeBreakdown(Collection<CompileTask> compilationTasks) {
        for (CompileTask task : compilationTasks) {
            String key = null;
            Class<?> javaClass = task.method.getDeclaringClass().getJavaClass();
            Module module = javaClass.getModule();
            if (module.isNamed()) {
                key = module.getName();
                if ("org.graalvm.nativeimage.builder".equals(key)) {
                    key = "svm.jar (Native Image)";
                }
            } else {
                key = findJARFile(javaClass);
                if (key == null) {
                    key = findPackageOrClassName(task.method);
                }
            }
            codeBreakdown.merge(key, (long) task.result.getTargetCodeSize(), Long::sum);
        }
    }

    private static String findJARFile(Class<?> javaClass) {
        CodeSource codeSource = javaClass.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            String path = codeSource.getLocation().getPath();
            if (path.endsWith(".jar")) {
                // Use String API to determine basename of path to handle both / and \.
                return path.substring(Math.max(path.lastIndexOf('/') + 1, path.lastIndexOf('\\') + 1));
            }
        }
        return null;
    }

    private static String findPackageOrClassName(HostedMethod method) {
        String qualifier = method.format("%H");
        int lastDotIndex = qualifier.lastIndexOf('.');
        if (lastDotIndex > 0) {
            qualifier = qualifier.substring(0, lastDotIndex);
        }
        return qualifier;
    }

    private void calculateHeapBreakdown(HostedMetaAccess metaAccess, Collection<ObjectInfo> heapObjects) {
        calculateHeapBreakdown(heapBreakdown, linkStrategy, metaAccess, heapObjects, reportStringBytes, graphEncodingByteLength, this::recordJsonMetric);
    }

    public void calculateHeapBreakdown(Map<String, Long> breakdown, LinkStrategy strategy, HostedMetaAccess metaAccess, Collection<ObjectInfo> heapObjects) {
        calculateHeapBreakdown(breakdown, strategy, metaAccess, heapObjects, reportStringBytes, graphEncodingByteLength, (k, v) -> {
        });
    }

    public static void calculateHeapBreakdown(Map<String, Long> heapBreakdown, LinkStrategy linkStrategy, HostedMetaAccess metaAccess, Collection<ObjectInfo> heapObjects,
                    boolean reportStringBytes, long graphEncodingByteLength, BiConsumer<JsonMetric, Object> jsonMetricAction) {
        long stringByteLength = 0;
        for (ObjectInfo o : heapObjects) {
            heapBreakdown.merge(o.getClazz().toJavaName(true), o.getSize(), Long::sum);
            JavaConstant javaObject = o.getConstant();
            if (reportStringBytes && metaAccess.isInstanceOf(javaObject, String.class)) {
                stringByteLength += Utils.getInternalByteArrayLength(metaAccess.getUniverse().getSnippetReflection().asObject(String.class, javaObject));
            }
        }

        Long byteArraySize = heapBreakdown.remove("byte[]");
        if (byteArraySize != null) {
            long remainingBytes = byteArraySize;
            if (stringByteLength > 0) {
                heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + "java.lang.String", stringByteLength);
                remainingBytes -= stringByteLength;
            }
            long codeInfoSize = CodeInfoTable.getImageCodeCache().getTotalByteArraySize();
            if (codeInfoSize > 0) {
                heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linkStrategy.asDocLink("code metadata", "#glossary-code-metadata"), codeInfoSize);
                remainingBytes -= codeInfoSize;
            }
            long metadataByteLength = ImageSingletons.lookup(ReflectionMetadataDecoder.class).getMetadataByteLength();
            if (metadataByteLength > 0) {
                heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linkStrategy.asDocLink("reflection metadata", "#glossary-reflection-metadata"), metadataByteLength);
                remainingBytes -= metadataByteLength;
            }
            long resourcesByteLength = 0;
            for (ResourceStorageEntry resourceList : Resources.singleton().resources()) {
                for (byte[] resource : resourceList.getData()) {
                    resourcesByteLength += resource.length;
                }
            }
            jsonMetricAction.accept(ImageDetailKey.RESOURCE_SIZE_BYTES, resourcesByteLength);
            if (resourcesByteLength > 0) {
                heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linkStrategy.asDocLink("embedded resources", "#glossary-embedded-resources"), resourcesByteLength);
                remainingBytes -= resourcesByteLength;
            }
            if (graphEncodingByteLength >= 0) {
                jsonMetricAction.accept(ImageDetailKey.GRAPH_ENCODING_SIZE, graphEncodingByteLength);
                heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linkStrategy.asDocLink("graph encodings", "#glossary-graph-encodings"), graphEncodingByteLength);
                remainingBytes -= graphEncodingByteLength;
            }
            assert remainingBytes >= 0;
            heapBreakdown.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linkStrategy.asDocLink("general heap data", "#glossary-general-heap-data"), remainingBytes);
        }
    }

    private void printBreakdowns() {
        if (!SubstrateOptions.BuildOutputBreakdowns.getValue()) {
            return;
        }
        l().printLineSeparator();
        Iterator<Entry<String, Long>> packagesBySize = codeBreakdown.entrySet().stream()
                        .sorted(Entry.comparingByValue(Comparator.reverseOrder())).iterator();
        Iterator<Entry<String, Long>> typesBySizeInHeap = heapBreakdown.entrySet().stream()
                        .sorted(Entry.comparingByValue(Comparator.reverseOrder())).iterator();

        final TwoColumnPrinter p = new TwoColumnPrinter();
        p.l().yellowBold().a(String.format("Top %d ", MAX_NUM_BREAKDOWN)).doclink("origins", "#glossary-code-area-origins").a(" of code area:")
                        .jumpToMiddle()
                        .a(String.format("Top %d object types in image heap:", MAX_NUM_BREAKDOWN)).reset().flushln();

        long printedCodeBytes = 0;
        long printedHeapBytes = 0;
        long printedCodeItems = 0;
        long printedHeapItems = 0;
        for (int i = 0; i < MAX_NUM_BREAKDOWN; i++) {
            String codeSizePart = "";
            if (packagesBySize.hasNext()) {
                Entry<String, Long> e = packagesBySize.next();
                String className = Utils.truncateClassOrPackageName(e.getKey());
                codeSizePart = String.format("%9s %s", ByteFormattingUtil.bytesToHuman(e.getValue()), className);
                printedCodeBytes += e.getValue();
                printedCodeItems++;
            }

            String heapSizePart = "";
            if (typesBySizeInHeap.hasNext()) {
                Entry<String, Long> e = typesBySizeInHeap.next();
                String className = e.getKey();
                // Do not truncate special breakdown items, they can contain links.
                if (!className.startsWith(BREAKDOWN_BYTE_ARRAY_PREFIX)) {
                    className = Utils.truncateClassOrPackageName(className);
                }
                heapSizePart = String.format("%9s %s", ByteFormattingUtil.bytesToHuman(e.getValue()), className);
                printedHeapBytes += e.getValue();
                printedHeapItems++;
            }
            if (codeSizePart.isEmpty() && heapSizePart.isEmpty()) {
                break;
            }
            p.l().a(codeSizePart).jumpToMiddle().a(heapSizePart).flushln();
        }

        int numCodeItems = codeBreakdown.size();
        int numHeapItems = heapBreakdown.size();
        long totalCodeBytes = codeBreakdown.values().stream().collect(Collectors.summingLong(Long::longValue));
        long totalHeapBytes = heapBreakdown.values().stream().collect(Collectors.summingLong(Long::longValue));

        p.l().a(String.format("%9s for %s more packages", ByteFormattingUtil.bytesToHuman(totalCodeBytes - printedCodeBytes), numCodeItems - printedCodeItems))
                        .jumpToMiddle()
                        .a(String.format("%9s for %s more object types", ByteFormattingUtil.bytesToHuman(totalHeapBytes - printedHeapBytes), numHeapItems - printedHeapItems)).flushln();
    }

    private void printRecommendations() {
        if (!SubstrateOptions.BuildOutputRecommendations.getValue()) {
            return;
        }
        List<UserRecommendation> recommendations = ImageSingletons.lookup(ProgressReporterFeature.class).getRecommendations();
        List<UserRecommendation> topApplicableRecommendations = recommendations.stream().filter(r -> r.isApplicable().get()).limit(5).toList();
        if (topApplicableRecommendations.isEmpty()) {
            return;
        }
        l().printLineSeparator();
        l().yellowBold().a("Recommendations:").reset().println();
        for (UserRecommendation r : topApplicableRecommendations) {
            String alignment = Utils.stringFilledWith(Math.max(1, 5 - r.id().length()), " ");
            l().a(" ").doclink(r.id(), "#recommendation-" + r.id().toLowerCase()).a(":").a(alignment).a(r.description()).println();
        }
    }

    public void printEpilog(Optional<String> optionalImageName, Optional<NativeImageGenerator> optionalGenerator, ImageClassLoader classLoader, Optional<Throwable> optionalError,
                    OptionValues parsedHostedOptions) {
        executor.shutdown();

        if (optionalError.isPresent()) {
            Path errorReportPath = NativeImageOptions.getErrorFilePath(parsedHostedOptions);
            Optional<FeatureHandler> featureHandler = optionalGenerator.isEmpty() ? Optional.empty() : Optional.ofNullable(optionalGenerator.get().featureHandler);
            ReportUtils.report("GraalVM Native Image Error Report", errorReportPath, p -> VMErrorReporter.generateErrorReport(p, buildOutputLog, classLoader, featureHandler, optionalError.get()),
                            false);
            if (ImageSingletonsSupport.isInstalled()) {
                BuildArtifacts.singleton().add(ArtifactType.BUILD_INFO, errorReportPath);
            }
        }

        if (optionalImageName.isEmpty() || optionalGenerator.isEmpty()) {
            printErrorMessage(optionalError, parsedHostedOptions);
            return;
        }
        String imageName = optionalImageName.get();
        NativeImageGenerator generator = optionalGenerator.get();

        l().printLineSeparator();
        printResourceStatistics();

        double totalSeconds = Utils.millisToSeconds(getTimer(TimerCollection.Registry.TOTAL).getTotalTime());
        recordJsonMetric(ResourceUsageKey.TOTAL_SECS, totalSeconds);

        createAdditionalArtifacts(imageName, generator, optionalError, parsedHostedOptions);
        printArtifacts(generator.getBuildArtifacts());

        l().printHeadlineSeparator();

        String timeStats;
        if (totalSeconds < 60) {
            timeStats = String.format("%.1fs", totalSeconds);
        } else {
            timeStats = String.format("%dm %ds", (int) totalSeconds / 60, (int) totalSeconds % 60);
        }
        l().a(optionalError.isEmpty() ? "Finished" : "Failed").a(" generating '").bold().a(imageName).reset().a("' ")
                        .a(optionalError.isEmpty() ? "in" : "after").a(" ").a(timeStats).a(".").println();

        printErrorMessage(optionalError, parsedHostedOptions);
    }

    private void printErrorMessage(Optional<Throwable> optionalError, OptionValues parsedHostedOptions) {
        if (optionalError.isEmpty()) {
            return;
        }
        Throwable error = optionalError.get();
        l().println();
        l().redBold().a("The build process encountered an unexpected error:").reset().println();
        if (NativeImageOptions.ReportExceptionStackTraces.getValue(parsedHostedOptions)) {
            l().dim().println();
            error.printStackTrace(builderIO.getOut());
            l().reset().println();
        } else {
            l().println();
            l().dim().a("> %s", error).reset().println();
            l().println();
            l().a("Please inspect the generated error report at:").println();
            l().link(NativeImageOptions.getErrorFilePath(parsedHostedOptions)).println();
            l().println();
            l().a("If you are unable to resolve this problem, please file an issue with the error report at:").println();
            var supportUrl = VM.getSupportUrl();
            l().link(supportUrl, supportUrl).println();
        }
    }

    private void createAdditionalArtifacts(String imageName, NativeImageGenerator generator, Optional<Throwable> error, OptionValues parsedHostedOptions) {
        BuildArtifacts artifacts = BuildArtifacts.singleton();
        Optional<Path> buildOutputJSONFile = SubstrateOptions.BuildOutputJSONFile.getValue(parsedHostedOptions).lastValue();
        if (error.isEmpty() && buildOutputJSONFile.isPresent()) {
            artifacts.add(ArtifactType.BUILD_INFO, reportBuildOutput(buildOutputJSONFile.get()));
        }
        if (generator.getBigbang() != null && ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(parsedHostedOptions)) {
            artifacts.add(ArtifactType.BUILD_INFO, reportImageBuildStatistics());
        }
        ImageSingletons.lookup(ProgressReporterFeature.class).createAdditionalArtifacts(artifacts);
        BuildArtifactsExporter.run(imageName, artifacts, generator.getBuildArtifacts());
    }

    private void printArtifacts(Map<ArtifactType, List<Path>> artifacts) {
        if (artifacts.isEmpty()) {
            return;
        }
        l().printLineSeparator();
        l().yellowBold().a("Produced artifacts:").reset().println();
        // Use TreeMap to sort paths alphabetically.
        Map<Path, List<String>> pathToTypes = new TreeMap<>();
        artifacts.forEach((artifactType, paths) -> {
            for (Path path : paths) {
                pathToTypes.computeIfAbsent(path, p -> new ArrayList<>()).add(artifactType.name().toLowerCase());
            }
        });
        pathToTypes.forEach((path, typeNames) -> {
            l().a(" ").link(path).dim().a(" (").a(String.join(", ", typeNames)).a(")").reset().println();
        });
    }

    private Path reportBuildOutput(Path jsonOutputFile) {
        String description = "image statistics in json";
        return ReportUtils.report(description, jsonOutputFile.toAbsolutePath(), out -> {
            try {
                jsonHelper.print(new JsonWriter(out));
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Failed to create " + jsonOutputFile, e);
            }
        }, false);
    }

    private static Path reportImageBuildStatistics() {
        Consumer<PrintWriter> statsReporter = ImageSingletons.lookup(ImageBuildStatistics.class).getReporter();
        Path reportsPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve("reports");
        return ReportUtils.report("image build statistics", reportsPath.resolve("image_build_statistics.json"), statsReporter, false);
    }

    private void printResourceStatistics() {
        double totalProcessTimeSeconds = Utils.millisToSeconds(System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
        GCStats gcStats = GCStats.getCurrent();
        double gcSeconds = Utils.millisToSeconds(gcStats.totalTimeMillis);
        CenteredTextPrinter p = new CenteredTextPrinter();
        recordJsonMetric(ResourceUsageKey.GC_COUNT, gcStats.totalCount);
        recordJsonMetric(ResourceUsageKey.GC_SECS, gcSeconds);
        p.a("%.1fs (%.1f%% of total time) in %d ", gcSeconds, gcSeconds / totalProcessTimeSeconds * 100, gcStats.totalCount)
                        .doclink("GCs", "#glossary-garbage-collections");
        long peakRSS = ProgressReporterCHelper.getPeakRSS();
        if (peakRSS >= 0) {
            p.a(" | ").doclink("Peak RSS", "#glossary-peak-rss").a(": ").a("%.2fGB", ByteFormattingUtil.bytesToGiB(peakRSS));
        }
        recordJsonMetric(ResourceUsageKey.PEAK_RSS, (peakRSS >= 0 ? peakRSS : UNAVAILABLE_METRIC));
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        long processCPUTime = ((com.sun.management.OperatingSystemMXBean) osMXBean).getProcessCpuTime();
        double cpuLoad = UNAVAILABLE_METRIC;
        if (processCPUTime > 0) {
            cpuLoad = Utils.nanosToSeconds(processCPUTime) / totalProcessTimeSeconds;
            p.a(" | ").doclink("CPU load", "#glossary-cpu-load").a(": ").a("%.2f", cpuLoad);
        }
        recordJsonMetric(ResourceUsageKey.CPU_LOAD, cpuLoad);
        p.flushln();
    }

    private void checkForExcessiveGarbageCollection() {
        long current = System.currentTimeMillis();
        long timeDeltaMillis = current - lastGCCheckTimeMillis;
        lastGCCheckTimeMillis = current;
        GCStats currentGCStats = GCStats.getCurrent();
        long gcTimeDeltaMillis = currentGCStats.totalTimeMillis - lastGCStats.totalTimeMillis;
        double ratio = gcTimeDeltaMillis / (double) timeDeltaMillis;
        if (gcTimeDeltaMillis > EXCESSIVE_GC_MIN_THRESHOLD_MILLIS && ratio > EXCESSIVE_GC_RATIO) {
            l().redBold().a("GC warning").reset()
                            .a(": %.1fs spent in %d GCs during the last stage, taking up %.2f%% of the time.",
                                            Utils.millisToSeconds(gcTimeDeltaMillis), currentGCStats.totalCount - lastGCStats.totalCount, ratio * 100)
                            .println();
            l().a("            Please ensure more than %.2fGB of memory is available for Native Image", ByteFormattingUtil.bytesToGiB(ProgressReporterCHelper.getPeakRSS())).println();
            l().a("            to reduce GC overhead and improve image build time.").println();
        }
        lastGCStats = currentGCStats;
    }

    public void recordJsonMetric(JsonMetric metric, Object value) {
        if (jsonHelper != null) {
            metric.record(jsonHelper, value);
        }
    }

    /*
     * HELPERS
     */

    private static Timer getTimer(TimerCollection.Registry type) {
        return TimerCollection.singleton().get(type);
    }

    private static class Utils {
        private static final double MILLIS_TO_SECONDS = 1000d;
        private static final double NANOS_TO_SECONDS = 1000d * 1000d * 1000d;
        private static final Field STRING_VALUE = ReflectionUtil.lookupField(String.class, "value");

        private static double millisToSeconds(double millis) {
            return millis / MILLIS_TO_SECONDS;
        }

        private static double nanosToSeconds(double nanos) {
            return nanos / NANOS_TO_SECONDS;
        }

        private static int getInternalByteArrayLength(String string) {
            if (string == null) {
                return 0;
            }
            try {
                return ((byte[]) STRING_VALUE.get(string)).length;
            } catch (ReflectiveOperationException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }

        private static double getUsedMemory() {
            return ByteFormattingUtil.bytesToGiB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        }

        private static String stringFilledWith(int size, String fill) {
            return new String(new char[size]).replace("\0", fill);
        }

        private static double toPercentage(long part, long total) {
            return part / (double) total * 100;
        }

        private static String truncateClassOrPackageName(String classOrPackageName) {
            int classNameLength = classOrPackageName.length();
            int maxLength = CHARACTERS_PER_LINE / 2 - 10;
            if (classNameLength <= maxLength) {
                return classOrPackageName;
            }
            StringBuilder sb = new StringBuilder();
            int currentDot = -1;
            while (true) {
                int nextDot = classOrPackageName.indexOf('.', currentDot + 1);
                if (nextDot < 0) { // Not more dots, handle the rest and return.
                    String rest = classOrPackageName.substring(currentDot + 1);
                    int sbLength = sb.length();
                    int restLength = rest.length();
                    if (sbLength + restLength <= maxLength) {
                        sb.append(rest);
                    } else {
                        int remainingSpaceDivBy2 = (maxLength - sbLength) / 2;
                        sb.append(rest.substring(0, remainingSpaceDivBy2 - 1) + "~" + rest.substring(restLength - remainingSpaceDivBy2, restLength));
                    }
                    break;
                }
                sb.append(classOrPackageName.charAt(currentDot + 1)).append('.');
                if (sb.length() + (classNameLength - nextDot) <= maxLength) {
                    // Rest fits maxLength, append and return.
                    sb.append(classOrPackageName.substring(nextDot + 1));
                    break;
                }
                currentDot = nextDot;
            }
            return sb.toString();
        }
    }

    private static class GCStats {
        private final long totalCount;
        private final long totalTimeMillis;

        private static GCStats getCurrent() {
            long totalCount = 0;
            long totalTime = 0;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                long collectionCount = bean.getCollectionCount();
                if (collectionCount > 0) {
                    totalCount += collectionCount;
                }
                long collectionTime = bean.getCollectionTime();
                if (collectionTime > 0) {
                    totalTime += collectionTime;
                }
            }
            return new GCStats(totalCount, totalTime);
        }

        GCStats(long totalCount, long totalTime) {
            this.totalCount = totalCount;
            this.totalTimeMillis = totalTime;
        }
    }

    public abstract static class ReporterClosable implements AutoCloseable {
        @Override
        public void close() {
            closeAction();
        }

        abstract void closeAction();
    }

    /*
     * CORE PRINTING
     */

    private void print(char text) {
        builderIO.getOut().print(text);
        buildOutputLog.append(text);
    }

    private void print(String text) {
        builderIO.getOut().print(text);
        buildOutputLog.append(text);
    }

    private void println() {
        builderIO.getOut().println();
        buildOutputLog.append(System.lineSeparator());
    }

    /*
     * PRINTERS
     */

    public abstract class AbstractPrinter<T extends AbstractPrinter<T>> {
        abstract T getThis();

        public abstract T a(String text);

        final T a(String text, Object... args) {
            return a(String.format(text, args));
        }

        final T a(int i) {
            return a(String.valueOf(i));
        }

        final T a(long i) {
            return a(String.valueOf(i));
        }

        final T bold() {
            colorStrategy.bold(this);
            return getThis();
        }

        final T blue() {
            colorStrategy.blue(this);
            return getThis();
        }

        final T blueBold() {
            colorStrategy.blueBold(this);
            return getThis();
        }

        final T magentaBold() {
            colorStrategy.magentaBold(this);
            return getThis();
        }

        final T redBold() {
            colorStrategy.redBold(this);
            return getThis();
        }

        final T yellowBold() {
            colorStrategy.yellowBold(this);
            return getThis();
        }

        final T dim() {
            colorStrategy.dim(this);
            return getThis();
        }

        final T reset() {
            colorStrategy.reset(this);
            return getThis();
        }

        final T link(String text, String url) {
            linkStrategy.link(this, text, url);
            return getThis();
        }

        final T link(Path path) {
            linkStrategy.link(this, path);
            return getThis();
        }

        public final T doclink(String text, String htmlAnchor) {
            linkStrategy.doclink(this, text, htmlAnchor);
            return getThis();
        }
    }

    /**
     * Start printing a new line.
     */
    private DirectPrinter l() {
        return linePrinter.a(outputPrefix);
    }

    public final class DirectPrinter extends AbstractPrinter<DirectPrinter> {
        @Override
        DirectPrinter getThis() {
            return this;
        }

        @Override
        public DirectPrinter a(String text) {
            print(text);
            return this;
        }

        void println() {
            ProgressReporter.this.println();
        }

        void printHeadlineSeparator() {
            dim().a(HEADLINE_SEPARATOR).reset().println();
        }

        void printLineSeparator() {
            dim().a(LINE_SEPARATOR).reset().println();
        }
    }

    abstract class LinePrinter<T extends LinePrinter<T>> extends AbstractPrinter<T> {
        protected final List<String> lineParts = new ArrayList<>();

        @Override
        public T a(String value) {
            lineParts.add(value);
            return getThis();
        }

        T l() {
            assert lineParts.isEmpty();
            return getThis();
        }

        final int getCurrentTextLength() {
            int textLength = 0;
            for (String text : lineParts) {
                if (!text.startsWith(ANSI.ESCAPE)) { // Ignore ANSI escape sequences.
                    textLength += text.length();
                }
            }
            return textLength;
        }

        final void printLineParts() {
            lineParts.forEach(ProgressReporter.this::print);
        }

        void flushln() {
            printLineParts();
            lineParts.clear();
            println();
        }
    }

    public void reportStageProgress() {
        stagePrinter.reportProgress();
    }

    public void beforeNextStdioWrite() {
        stagePrinter.beforeNextStdioWrite();
    }

    abstract class StagePrinter<T extends StagePrinter<T>> extends LinePrinter<T> {
        private int progressBarStart = 30;
        private BuildStage activeBuildStage = null;

        private ScheduledFuture<?> periodicPrintingTask;
        private AtomicBoolean isCancelled = new AtomicBoolean();

        T start(BuildStage stage) {
            assert activeBuildStage == null;
            activeBuildStage = stage;
            appendStageStart();
            if (activeBuildStage.hasProgressBar) {
                a(progressBarStartPadding()).dim().a("[");
            }
            if (activeBuildStage.hasPeriodicProgress) {
                startPeriodicProgress();
            }
            return getThis();
        }

        private void startPeriodicProgress() {
            isCancelled.set(false);
            periodicPrintingTask = executor.scheduleAtFixedRate(new Runnable() {
                int countdown;
                int numPrints;

                @Override
                public void run() {
                    if (isCancelled.get()) {
                        return;
                    }
                    if (--countdown < 0) {
                        reportProgress();
                        countdown = ++numPrints > 2 ? numPrints * 2 : numPrints;
                    }
                }
            }, 0, 1, TimeUnit.SECONDS);
        }

        private void appendStageStart() {
            a(outputPrefix).blue().a(String.format("[%s/%s] ", 1 + activeBuildStage.ordinal(), BuildStage.NUM_STAGES)).reset()
                            .blueBold().doclink(activeBuildStage.message, "#stage-" + activeBuildStage.name().toLowerCase()).a("...").reset();
        }

        final String progressBarStartPadding() {
            return Utils.stringFilledWith(progressBarStart - getCurrentTextLength(), " ");
        }

        void reportProgress() {
            a("*");
        }

        final void end(Timer timer) {
            end(timer.getTotalTime());
        }

        void end(double totalTime) {
            if (activeBuildStage.hasPeriodicProgress) {
                isCancelled.set(true);
                periodicPrintingTask.cancel(false);
            }
            if (activeBuildStage.hasProgressBar) {
                a("]").reset();
            }

            String suffix = String.format("(%.1fs @ %.2fGB)", Utils.millisToSeconds(totalTime), Utils.getUsedMemory());
            int textLength = getCurrentTextLength();
            // TODO: `assert textLength > 0;` should be used here but tests do not start stages
            // properly (GR-35721)
            String padding = Utils.stringFilledWith(Math.max(0, CHARACTERS_PER_LINE - textLength - suffix.length()), " ");
            a(padding).dim().a(suffix).reset().flushln();

            activeBuildStage = null;

            boolean optionsAvailable = ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(HostedOptionValues.class);
            if (optionsAvailable && SubstrateOptions.BuildOutputGCWarnings.getValue()) {
                checkForExcessiveGarbageCollection();
            }
        }

        abstract void beforeNextStdioWrite();
    }

    /**
     * A {@link StagePrinter} that prints full lines to stdout at the end of each stage. This
     * printer should be used when interactive progress bars are not desired, e.g., when logging is
     * enabled or in dumb terminals.
     */
    final class LinewiseStagePrinter extends StagePrinter<LinewiseStagePrinter> {
        @Override
        LinewiseStagePrinter getThis() {
            return this;
        }

        @Override
        void beforeNextStdioWrite() {
            throw VMError.shouldNotReachHere("LinewiseStagePrinter not allowed to set builderIO.listenForNextStdioWrite");
        }
    }

    /**
     * A {@link StagePrinter} that produces interactive progress bars on the command line. It should
     * only be used in rich terminals with cursor and ANSI support. It is also the only component
     * that interacts with {@link NativeImageSystemIOWrappers#progressReporter}.
     */
    final class CharacterwiseStagePrinter extends StagePrinter<CharacterwiseStagePrinter> {
        @Override
        CharacterwiseStagePrinter getThis() {
            return this;
        }

        /**
         * Print directly and only append to keep track of the current line in case it needs to be
         * re-printed.
         */
        @Override
        public CharacterwiseStagePrinter a(String value) {
            print(value);
            return super.a(value);
        }

        @Override
        CharacterwiseStagePrinter start(BuildStage stage) {
            super.start(stage);
            builderIO.progressReporter = ProgressReporter.this;
            return getThis();
        }

        @Override
        void reportProgress() {
            reprintLineIfNecessary();
            // Ensure builderIO is not listening for the next stdio write when printing progress
            // characters to stdout.
            builderIO.progressReporter = null;
            super.reportProgress();
            // Now that progress has been printed and has not been stopped, make sure builderIO
            // listens for the next stdio write again.
            builderIO.progressReporter = ProgressReporter.this;
        }

        @Override
        void end(double totalTime) {
            reprintLineIfNecessary();
            builderIO.progressReporter = null;
            super.end(totalTime);
        }

        void reprintLineIfNecessary() {
            if (builderIO.progressReporter == null) {
                printLineParts();
            }
        }

        @Override
        void flushln() {
            // No need to print lineParts because they are only needed for re-printing.
            lineParts.clear();
            println();
        }

        @Override
        void beforeNextStdioWrite() {
            colorStrategy.reset(); // Ensure color is reset.
            // Clear the current line.
            print('\r');
            int textLength = getCurrentTextLength();
            assert textLength > 0 : "linePrinter expected to hold current line content";
            for (int i = 0; i <= textLength; i++) {
                print(' ');
            }
            print('\r');
        }
    }

    final class TwoColumnPrinter extends LinePrinter<TwoColumnPrinter> {
        @Override
        TwoColumnPrinter getThis() {
            return this;
        }

        @Override
        public TwoColumnPrinter a(String value) {
            super.a(value);
            return this;
        }

        TwoColumnPrinter jumpToMiddle() {
            int remaining = (CHARACTERS_PER_LINE / 2) - getCurrentTextLength();
            assert remaining >= 0 : "Column text too wide";
            a(Utils.stringFilledWith(remaining, " "));
            assert getCurrentTextLength() == CHARACTERS_PER_LINE / 2;
            return this;
        }

        @Override
        void flushln() {
            print(outputPrefix);
            super.flushln();
        }
    }

    final class CenteredTextPrinter extends LinePrinter<CenteredTextPrinter> {
        @Override
        CenteredTextPrinter getThis() {
            return this;
        }

        @Override
        void flushln() {
            print(outputPrefix);
            String padding = Utils.stringFilledWith((Math.max(0, CHARACTERS_PER_LINE - getCurrentTextLength())) / 2, " ");
            print(padding);
            super.flushln();
        }
    }

    @SuppressWarnings("unused")
    private interface ColorStrategy {
        default void bold(AbstractPrinter<?> printer) {
        }

        default void blue(AbstractPrinter<?> printer) {
        }

        default void blueBold(AbstractPrinter<?> printer) {
        }

        default void magentaBold(AbstractPrinter<?> printer) {
        }

        default void redBold(AbstractPrinter<?> printer) {
        }

        default void yellowBold(AbstractPrinter<?> printer) {
        }

        default void dim(AbstractPrinter<?> printer) {
        }

        default void reset(AbstractPrinter<?> printer) {
        }

        default void reset() {
        }
    }

    final class ColorlessStrategy implements ColorStrategy {
    }

    final class ColorfulStrategy implements ColorStrategy {
        @Override
        public void bold(AbstractPrinter<?> printer) {
            printer.a(ANSI.BOLD);
        }

        @Override
        public void blue(AbstractPrinter<?> printer) {
            printer.a(ANSI.BLUE);
        }

        @Override
        public void blueBold(AbstractPrinter<?> printer) {
            printer.a(ANSI.BLUE_BOLD);
        }

        @Override
        public void magentaBold(AbstractPrinter<?> printer) {
            printer.a(ANSI.MAGENTA_BOLD);
        }

        @Override
        public void redBold(AbstractPrinter<?> printer) {
            printer.a(ANSI.RED_BOLD);
        }

        @Override
        public void yellowBold(AbstractPrinter<?> printer) {
            printer.a(ANSI.YELLOW_BOLD);
        }

        @Override
        public void dim(AbstractPrinter<?> printer) {
            printer.a(ANSI.DIM);
        }

        @Override
        public void reset(AbstractPrinter<?> printer) {
            printer.a(ANSI.RESET);
        }

        @Override
        public void reset() {
            print(ANSI.RESET);
        }
    }

    public interface LinkStrategy {
        void link(AbstractPrinter<?> printer, String text, String url);

        String asDocLink(String text, String htmlAnchor);

        default void link(AbstractPrinter<?> printer, Path path) {
            Path normalized = path.normalize();
            link(printer, normalized.toString(), normalized.toUri().toString());
        }

        default void doclink(AbstractPrinter<?> printer, String text, String htmlAnchor) {
            link(printer, text, STAGE_DOCS_URL + htmlAnchor);
        }
    }

    static final class LinklessStrategy implements LinkStrategy {
        @Override
        public void link(AbstractPrinter<?> printer, String text, String url) {
            printer.a(text);
        }

        @Override
        public String asDocLink(String text, String htmlAnchor) {
            return text;
        }
    }

    static final class LinkyStrategy implements LinkStrategy {
        /**
         * Adding link part individually for {@link LinePrinter#getCurrentTextLength()}.
         */
        @Override
        public void link(AbstractPrinter<?> printer, String text, String url) {
            printer.a(ANSI.LINK_START + url).a(ANSI.LINK_TEXT).a(text).a(ANSI.LINK_END);
        }

        @Override
        public String asDocLink(String text, String htmlAnchor) {
            return String.format(ANSI.LINK_FORMAT, STAGE_DOCS_URL + htmlAnchor, text);
        }
    }

    public static class ANSI {
        static final String ESCAPE = "\033";
        static final String RESET = ESCAPE + "[0m";
        static final String BOLD = ESCAPE + "[1m";
        static final String DIM = ESCAPE + "[2m";
        static final String STRIP_COLORS = "\033\\[[;\\d]*m";

        static final String LINK_START = ESCAPE + "]8;;";
        static final String LINK_TEXT = ESCAPE + "\\";
        static final String LINK_END = LINK_START + LINK_TEXT;
        static final String LINK_FORMAT = LINK_START + "%s" + LINK_TEXT + "%s" + LINK_END;
        static final String STRIP_LINKS = "\033]8;;https://\\S+\033\\\\([^\033]*)\033]8;;\033\\\\";

        static final String BLUE = ESCAPE + "[0;34m";

        static final String RED_BOLD = ESCAPE + "[1;31m";
        static final String YELLOW_BOLD = ESCAPE + "[1;33m";
        static final String BLUE_BOLD = ESCAPE + "[1;34m";
        static final String MAGENTA_BOLD = ESCAPE + "[1;35m";

        /* Strip all ANSI codes emitted by this class. */
        public static String strip(String string) {
            return string.replaceAll(STRIP_COLORS, "").replaceAll(STRIP_LINKS, "$1");
        }
    }
}
