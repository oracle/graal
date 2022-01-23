/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.VM;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.MethodMetadataDecoder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CompileQueue.CompileTask;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.util.ImageBuildStatistics;
import com.oracle.svm.util.ReflectionUtil;

public class ProgressReporter {
    private static final int CHARACTERS_PER_LINE;
    private static final int PROGRESS_BAR_START = 30;
    private static final boolean IS_CI = System.console() == null || System.getenv("CI") != null;
    private static final boolean IS_DUMB_TERM = isDumbTerm();
    private static final int MAX_NUM_FEATURES = 50;
    private static final int MAX_NUM_BREAKDOWN = 10;
    private static final String CODE_BREAKDOWN_TITLE = String.format("Top %d packages in code area:", MAX_NUM_BREAKDOWN);
    private static final String HEAP_BREAKDOWN_TITLE = String.format("Top %d object types in image heap:", MAX_NUM_BREAKDOWN);
    private static final String STAGE_DOCS_URL = "https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/BuildOutput.md";
    private static final double EXCESSIVE_GC_MIN_THRESHOLD_MILLIS = 15_000;
    private static final double EXCESSIVE_GC_RATIO = 0.5;
    private static final String BREAKDOWN_BYTE_ARRAY_PREFIX = "byte[] for ";

    private static final double MILLIS_TO_SECONDS = 1000d;
    private static final double NANOS_TO_SECONDS = 1000d * 1000d * 1000d;
    private static final double BYTES_TO_KiB = 1024d;
    private static final double BYTES_TO_MiB = 1024d * 1024d;
    private static final double BYTES_TO_GiB = 1024d * 1024d * 1024d;

    private final NativeImageSystemIOWrappers builderIO;

    private final boolean isEnabled;
    private final LinePrinter linePrinter;
    private final boolean usePrefix;
    private final boolean showLinks;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> periodicPrintingTask;

    private int numStageChars = 0;
    private long lastGCCheckTimeMillis = System.currentTimeMillis();
    private GCStats lastGCStats = getCurrentGCStats();
    private long numRuntimeCompiledMethods = -1;
    private long graphEncodingByteLength = 0;
    private int numJNIClasses = -1;
    private int numJNIFields = -1;
    private int numJNIMethods = -1;
    private Timer debugInfoTimer;
    private boolean initializeStageEndCompleted = false;
    private boolean creationStageEndCompleted = false;

    private enum BuildStage {
        INITIALIZING("Initializing"),
        ANALYSIS("Performing analysis"),
        UNIVERSE("Building universe"),
        PARSING("Parsing methods"),
        INLINING("Inlining methods"),
        COMPILING("Compiling methods"),
        CREATING("Creating image");

        private static final int NUM_STAGES = values().length;

        private final String message;

        BuildStage(String message) {
            this.message = message;
        }
    }

    static {
        CHARACTERS_PER_LINE = IS_CI ? ProgressReporterCHelper.MAX_CHARACTERS_PER_LINE : ProgressReporterCHelper.getTerminalWindowColumnsClamped();
    }

    private static boolean isDumbTerm() {
        String term = System.getenv("TERM");
        return (term == null || term.equals("") || term.equals("dumb") || term.equals("unknown"));
    }

    public static ProgressReporter singleton() {
        return ImageSingletons.lookup(ProgressReporter.class);
    }

    public ProgressReporter(OptionValues options) {
        builderIO = NativeImageSystemIOWrappers.singleton();

        isEnabled = SubstrateOptions.BuildOutputUseNewStyle.getValue(options);
        if (isEnabled) {
            Timer.disablePrinting();
        }
        usePrefix = SubstrateOptions.BuildOutputPrefix.getValue(options);
        boolean enableColors = !IS_DUMB_TERM && !IS_CI && OS.getCurrent() != OS.WINDOWS;
        if (SubstrateOptions.BuildOutputColorful.hasBeenSet(options)) {
            enableColors = SubstrateOptions.BuildOutputColorful.getValue(options);
        }
        boolean loggingDisabled = DebugOptions.Log.getValue(options) == null;
        boolean enableProgress = !IS_DUMB_TERM && !IS_CI && loggingDisabled;
        if (SubstrateOptions.BuildOutputProgress.hasBeenSet(options)) {
            enableProgress = SubstrateOptions.BuildOutputProgress.getValue(options);
        }
        if (enableColors) {
            linePrinter = new ColorfulLinePrinter(enableProgress);
            /* Add a shutdown hook to reset the ANSI mode. */
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(ProgressReporter::resetANSIMode));
            } catch (IllegalStateException e) {
                /* If the VM is already shutting down, we do not need to register shutdownHook. */
            }
        } else {
            linePrinter = new ColorlessLinePrinter(enableProgress);
        }
        showLinks = SubstrateOptions.BuildOutputColorful.getValue(options);
    }

    private LinePrinter l() {
        assert linePrinter.isEmpty() : "Line printer should be empty before printing a new line";
        return linePrinter;
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

    public void printStart(String imageName) {
        if (usePrefix) {
            // Add the PID to further disambiguate concurrent builds of images with the same name
            linePrinter.outputPrefix = String.format("[%s:%s] ", imageName, GraalServices.getExecutionID());
        }
        l().printHeadlineSeparator();
        l().blueBold().link("GraalVM Native Image", "https://www.graalvm.org/native-image/").reset()
                        .a(": Generating '").bold().a(imageName).reset().a("'...").flushln();
        l().printHeadlineSeparator();
        printStageStart(BuildStage.INITIALIZING);
    }

    public void printInitializeEnd(Timer classlistTimer, Timer setupTimer) {
        if (initializeStageEndCompleted) {
            return;
        }
        printStageEnd(classlistTimer.getTotalTime() + setupTimer.getTotalTime());
        initializeStageEndCompleted = true;
    }

    public void printInitializeEnd(Timer classlistTimer, Timer setupTimer, Collection<String> libraries) {
        printInitializeEnd(classlistTimer, setupTimer);
        l().a(" ").doclink("Version info", "#glossary-version-info").a(": '").a(ImageSingletons.lookup(VM.class).version).a("'").flushln();
        printNativeLibraries(libraries);
    }

    private void printNativeLibraries(Collection<String> libraries) {
        int numLibraries = libraries.size();
        if (numLibraries > 0) {
            if (numLibraries == 1) {
                l().a(" 1 native library: ").a(libraries.iterator().next()).flushln();
            } else {
                l().a(" ").a(numLibraries).a(" native libraries: ").a(String.join(", ", libraries)).flushln();
            }
        }
    }

    public void printFeatures(List<String> list) {
        int numUserFeatures = list.size();
        if (numUserFeatures > 0) {
            l().a(" ").a(numUserFeatures).a(" ").doclink("user-provided feature(s)", "#glossary-user-provided-features").flushln();
            if (numUserFeatures <= MAX_NUM_FEATURES) {
                for (String name : list) {
                    l().a("  - ").a(name).flushln();
                }
            } else {
                for (int i = 0; i < MAX_NUM_FEATURES; i++) {
                    l().a("  - ").a(list.get(i)).flushln();
                }
                l().a("  ... ").a(numUserFeatures - MAX_NUM_FEATURES).a(" more").flushln();
            }
        }
    }

    public ReporterClosable printAnalysis(BigBang bb) {
        Timer timer = bb.getAnalysisTimer();
        timer.start();
        printStageStart(BuildStage.ANALYSIS);
        printProgressStart();
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                printProgressEnd();
                printStageEnd(bb.getAnalysisTimer());
                String actualVsTotalFormat = "%,8d (%5.2f%%) of %,6d";
                long reachableClasses = bb.getUniverse().getTypes().stream().filter(t -> t.isReachable()).count();
                long totalClasses = bb.getUniverse().getTypes().size();
                l().a(actualVsTotalFormat, reachableClasses, reachableClasses / (double) totalClasses * 100, totalClasses)
                                .a(" classes ").doclink("reachable", "#glossary-reachability").flushln();
                Collection<AnalysisField> fields = bb.getUniverse().getFields();
                long reachableFields = fields.stream().filter(f -> f.isAccessed()).count();
                int totalFields = fields.size();
                l().a(actualVsTotalFormat, reachableFields, reachableFields / (double) totalFields * 100, totalFields)
                                .a(" fields ").doclink("reachable", "#glossary-reachability").flushln();
                Collection<AnalysisMethod> methods = bb.getUniverse().getMethods();
                long reachableMethods = methods.stream().filter(m -> m.isReachable()).count();
                int totalMethods = methods.size();
                l().a(actualVsTotalFormat, reachableMethods, reachableMethods / (double) totalMethods * 100, totalMethods)
                                .a(" methods ").doclink("reachable", "#glossary-reachability").flushln();
                if (numRuntimeCompiledMethods >= 0) {
                    l().a(actualVsTotalFormat, numRuntimeCompiledMethods, numRuntimeCompiledMethods / (double) totalMethods * 100, totalMethods)
                                    .a(" methods included for ").doclink("runtime compilation", "#glossary-runtime-methods").flushln();
                }
                String classesFieldsMethodFormat = "%,8d classes, %,5d fields, and %,5d methods ";
                RuntimeReflectionSupport rs = ImageSingletons.lookup(RuntimeReflectionSupport.class);
                l().a(classesFieldsMethodFormat, rs.getReflectionClassesCount(), rs.getReflectionFieldsCount(), rs.getReflectionMethodsCount())
                                .doclink("registered for reflection", "#glossary-reflection-registrations").flushln();
                if (numJNIClasses > 0) {
                    l().a(classesFieldsMethodFormat, numJNIClasses, numJNIFields, numJNIMethods)
                                    .doclink("registered for JNI access", "#glossary-jni-access-registrations").flushln();
                }
            }
        };

    }

    public ReporterClosable printUniverse(Timer timer) {
        timer.start();
        printStageStart(BuildStage.UNIVERSE);
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                printStageEnd(timer);
            }
        };
    }

    public ReporterClosable printParsing(Timer timer) {
        timer.start();
        printStageStart(BuildStage.PARSING);
        printProgressStart();
        startPeriodicPrinting();
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                stopPeriodicPrinting();
                printProgressEnd();
                printStageEnd(timer);
            }
        };
    }

    public ReporterClosable printInlining(Timer timer) {
        timer.start();
        printStageStart(BuildStage.INLINING);
        printProgressStart();
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                printProgressEnd();
                printStageEnd(timer);
            }
        };
    }

    public void printInliningSkipped() {
        printStageStart(BuildStage.INLINING);
        linePrinter.a(progressBarStartPadding()).dim().a("(skipped)").reset().flushln(false);
        numStageChars = 0;
    }

    public ReporterClosable printCompiling(Timer timer) {
        timer.start();
        printStageStart(BuildStage.COMPILING);
        printProgressStart();
        startPeriodicPrinting();
        return new ReporterClosable() {
            @Override
            public void closeAction() {
                timer.stop();
                stopPeriodicPrinting();
                printProgressEnd();
                printStageEnd(timer);
            }
        };
    }

    // TODO: merge printCreationStart and printCreationEnd at some point (GR-35238).
    public void printCreationStart() {
        printStageStart(BuildStage.CREATING);
    }

    public void setDebugInfoTimer(Timer timer) {
        this.debugInfoTimer = timer;
    }

    public void printCreationEnd(Timer creationTimer, Timer writeTimer, int imageSize, AnalysisUniverse universe, int numHeapObjects, long imageHeapSize, int codeCacheSize,
                    int numCompilations, int debugInfoSize) {
        printStageEnd(creationTimer.getTotalTime() + writeTimer.getTotalTime());
        creationStageEndCompleted = true;
        String format = "%9s (%5.2f%%) for ";
        l().a(format, bytesToHuman(codeCacheSize), codeCacheSize / (double) imageSize * 100)
                        .doclink("code area", "#glossary-code-area").a(":%,9d compilation units", numCompilations).flushln();
        long numInstantiatedClasses = universe.getTypes().stream().filter(t -> t.isInstantiated()).count();
        l().a(format, bytesToHuman(imageHeapSize), imageHeapSize / (double) imageSize * 100)
                        .doclink("image heap", "#glossary-image-heap").a(":%,8d classes and %,d objects", numInstantiatedClasses, numHeapObjects).flushln();
        if (debugInfoSize > 0) {
            LinePrinter l = l().a(format, bytesToHuman(debugInfoSize), debugInfoSize / (double) imageSize * 100)
                            .doclink("debug info", "#glossary-debug-info");
            if (debugInfoTimer != null) {
                l.a(" generated in %.1fs", millisToSeconds(debugInfoTimer.getTotalTime()));
            }
            l.flushln();
        }
        long otherBytes = imageSize - codeCacheSize - imageHeapSize - debugInfoSize;
        l().a(format, bytesToHuman(otherBytes), otherBytes / (double) imageSize * 100)
                        .doclink("other data", "#glossary-other-data").flushln();
        l().a("%9s in total", bytesToHuman(imageSize)).flushln();
    }

    public void ensureCreationStageEndCompleted() {
        if (!creationStageEndCompleted) {
            linePrinter.flushln();
            restoreBuilderIO();
        }
    }

    private void restoreBuilderIO() {
        builderIO.useCapturing = false;
        builderIO.flushCapturedContent();
    }

    public void printBreakdowns(Collection<CompileTask> compilationTasks, Collection<ObjectInfo> heapObjects) {
        Map<String, Long> codeBreakdown = calculateCodeBreakdown(compilationTasks);
        Map<String, Long> heapBreakdown = calculateHeapBreakdown(heapObjects);
        l().printLineSeparator();
        int numCodeBreakdownItems = codeBreakdown.size();
        int numHeapBreakdownItems = heapBreakdown.size();
        Iterator<Entry<String, Long>> packagesBySize = codeBreakdown.entrySet().stream()
                        .sorted(Entry.comparingByValue(Comparator.reverseOrder())).iterator();
        Iterator<Entry<String, Long>> typesBySizeInHeap = heapBreakdown.entrySet().stream()
                        .sorted(Entry.comparingByValue(Comparator.reverseOrder())).iterator();

        l().yellowBold().a(CODE_BREAKDOWN_TITLE).jumpToMiddle().a(HEAP_BREAKDOWN_TITLE).reset().flushln();

        List<Entry<String, Long>> printedCodeSizeEntries = new ArrayList<>();
        List<Entry<String, Long>> printedHeapSizeEntries = new ArrayList<>();
        for (int i = 0; i < MAX_NUM_BREAKDOWN; i++) {
            String codeSizePart = "";
            if (packagesBySize.hasNext()) {
                Entry<String, Long> e = packagesBySize.next();
                String className = truncateClassOrPackageName(e.getKey());
                codeSizePart = String.format("%9s %s", bytesToHuman(e.getValue()), className);
                printedCodeSizeEntries.add(e);
            }

            String heapSizePart = "";
            if (typesBySizeInHeap.hasNext()) {
                Entry<String, Long> e = typesBySizeInHeap.next();
                String className = e.getKey();
                // Do not truncate special breakdown items, they can contain links.
                if (!className.startsWith(BREAKDOWN_BYTE_ARRAY_PREFIX)) {
                    className = truncateClassOrPackageName(className);
                }
                heapSizePart = String.format("%9s %s", bytesToHuman(e.getValue()), className);
                printedHeapSizeEntries.add(e);
            }
            if (codeSizePart.isEmpty() && heapSizePart.isEmpty()) {
                break;
            }
            l().a(codeSizePart).jumpToMiddle().a(heapSizePart).flushln();
        }

        l().a("      ... ").a(numCodeBreakdownItems - printedHeapSizeEntries.size()).a(" additional packages")
                        .jumpToMiddle()
                        .a("      ... ").a(numHeapBreakdownItems - printedCodeSizeEntries.size()).a(" additional object types").flushln();

        l().dim().a("(use ").link("GraalVM Dashboard", "https://www.graalvm.org/dashboard/?ojr=help%3Btopic%3Dgetting-started.md").a(" to see all)").reset().flushCenteredln();
    }

    private static Map<String, Long> calculateCodeBreakdown(Collection<CompileTask> compilationTasks) {
        Map<String, Long> classNameToCodeSize = new HashMap<>();
        for (CompileTask task : compilationTasks) {
            String classOrPackageName = task.method.format("%H");
            int lastDotIndex = classOrPackageName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                classOrPackageName = classOrPackageName.substring(0, lastDotIndex);
            }
            classNameToCodeSize.merge(classOrPackageName, (long) task.result.getTargetCodeSize(), Long::sum);
        }
        return classNameToCodeSize;
    }

    private static final Field STRING_VALUE = ReflectionUtil.lookupField(String.class, "value");

    private static int getInternalByteArrayLength(String string) {
        try {
            return ((byte[]) STRING_VALUE.get(string)).length;
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private Map<String, Long> calculateHeapBreakdown(Collection<ObjectInfo> heapObjects) {
        Map<String, Long> classNameToSize = new HashMap<>();
        long stringByteLength = 0;
        for (ObjectInfo o : heapObjects) {
            classNameToSize.merge(o.getClazz().toJavaName(true), o.getSize(), Long::sum);
            Object javaObject = o.getObject();
            if (javaObject instanceof String) {
                stringByteLength += getInternalByteArrayLength((String) javaObject);
            }
        }

        Long byteArraySize = classNameToSize.remove("byte[]");
        if (byteArraySize != null) {
            long remainingBytes = byteArraySize;
            classNameToSize.put(BREAKDOWN_BYTE_ARRAY_PREFIX + "java.lang.String", stringByteLength);
            remainingBytes -= stringByteLength;
            long metadataByteLength = ImageSingletons.lookup(MethodMetadataDecoder.class).getMetadataByteLength();
            if (metadataByteLength > 0) {
                classNameToSize.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linePrinter.asDocLink("method metadata", "#glossary-method-metadata"), metadataByteLength);
                remainingBytes -= metadataByteLength;
            }
            if (graphEncodingByteLength > 0) {
                classNameToSize.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linePrinter.asDocLink("graph encodings", "#glossary-graph-encodings"), graphEncodingByteLength);
                remainingBytes -= graphEncodingByteLength;
            }
            assert remainingBytes >= 0;
            classNameToSize.put(BREAKDOWN_BYTE_ARRAY_PREFIX + linePrinter.asDocLink("general heap data", "#glossary-general-heap-data"), remainingBytes);
        }
        return classNameToSize;
    }

    public void printEpilog(String imageName, NativeImageGenerator generator, boolean wasSuccessfulBuild, Timer totalTimer, OptionValues parsedHostedOptions) {
        l().printLineSeparator();
        printResourceStats();
        l().printLineSeparator();

        l().yellowBold().a("Produced artifacts:").reset().flushln();
        generator.getBuildArtifacts().forEach((artifactType, paths) -> {
            for (Path p : paths) {
                l().a(" ").link(p).dim().a(" (").a(artifactType.name().toLowerCase()).a(")").reset().flushln();
            }
        });
        if (ImageBuildStatistics.Options.CollectImageBuildStatistics.getValue(parsedHostedOptions)) {
            l().a(" ").link(reportImageBuildStatistics(imageName, generator.getBigbang())).flushln();
        }
        l().a(" ").link(reportBuildArtifacts(imageName, generator.getBuildArtifacts())).flushln();

        l().printHeadlineSeparator();

        double totalSeconds = millisToSeconds(totalTimer.getTotalTime());
        String timeStats;
        if (totalSeconds < 60) {
            timeStats = String.format("%.1fs", totalSeconds);
        } else {
            timeStats = String.format("%dm %ds", (int) totalSeconds / 60, (int) totalSeconds % 60);
        }
        l().a(wasSuccessfulBuild ? "Finished" : "Failed").a(" generating '").bold().a(imageName).reset().a("' ")
                        .a(wasSuccessfulBuild ? "in" : "after").a(" ").a(timeStats).a(".").flushln();
        executor.shutdown();
    }

    private Path reportImageBuildStatistics(String imageName, BigBang bb) {
        Consumer<PrintWriter> statsReporter = ImageSingletons.lookup(ImageBuildStatistics.class).getReporter();
        String description = "image build statistics";
        if (ImageBuildStatistics.Options.ImageBuildStatisticsFile.hasBeenSet(bb.getOptions())) {
            final File file = new File(ImageBuildStatistics.Options.ImageBuildStatisticsFile.getValue(bb.getOptions()));
            return ReportUtils.report(description, file.getAbsoluteFile().toPath(), statsReporter, !isEnabled);
        } else {
            String name = "image_build_statistics_" + ReportUtils.extractImageName(imageName);
            String path = SubstrateOptions.Path.getValue() + File.separatorChar + "reports";
            return ReportUtils.report(description, path, name, "json", statsReporter, !isEnabled);
        }
    }

    private Path reportBuildArtifacts(String imageName, Map<ArtifactType, List<Path>> buildArtifacts) {
        Path buildDir = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());

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
        return ReportUtils.report("build artifacts", buildDir.resolve(imageName + ".build_artifacts.txt"), writerConsumer, !isEnabled);
    }

    private void printResourceStats() {
        double totalProcessTimeSeconds = millisToSeconds(System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime());
        GCStats gcStats = getCurrentGCStats();
        double gcSeconds = millisToSeconds(gcStats.totalTimeMillis);
        LinePrinter l = l().a("%.1fs (%.1f%% of total time) in %d ", gcSeconds, gcSeconds / totalProcessTimeSeconds * 100, gcStats.totalCount)
                        .doclink("GCs", "#glossary-garbage-collections");
        long peakRSS = ProgressReporterCHelper.getPeakRSS();
        if (peakRSS >= 0) {
            l.a(" | ").doclink("Peak RSS", "#glossary-peak-rss").a(": ").a("%.2fGB", bytesToGiB(peakRSS));
        }
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        long processCPUTime = ((com.sun.management.OperatingSystemMXBean) osMXBean).getProcessCpuTime();
        if (processCPUTime > 0) {
            l.a(" | ").doclink("CPU load", "#glossary-cpu-load").a(": ").a("%.2f", nanosToSeconds(processCPUTime) / totalProcessTimeSeconds);
        }
        l.flushCenteredln();
    }

    private void printStageStart(BuildStage stage) {
        assert numStageChars == 0;
        l().appendPrefix().flush();
        linePrinter.blue()
                        .a("[").a(1 + stage.ordinal()).a("/").a(BuildStage.NUM_STAGES).a("] ").reset().blueBold()
                        .doclink(stage.message, "#stage-" + stage.name().toLowerCase()).a("...").reset();
        numStageChars = linePrinter.getCurrentTextLength();
        linePrinter.flush();
        builderIO.useCapturing = true;
    }

    private void printProgressStart() {
        linePrinter.a(progressBarStartPadding()).dim().a("[");
        numStageChars = PROGRESS_BAR_START + 1; /* +1 for [ */
        linePrinter.flush();
    }

    private String progressBarStartPadding() {
        return stringFilledWith(PROGRESS_BAR_START - numStageChars, " ");
    }

    private void printProgressEnd() {
        linePrinter.printRaw(']');
        numStageChars++; // for ]
    }

    public void printStageProgress() {
        linePrinter.printRaw('*');
        numStageChars++; // for *
    }

    private void startPeriodicPrinting() {
        periodicPrintingTask = executor.scheduleAtFixedRate(new Runnable() {
            int countdown;
            int numPrints;

            @Override
            public void run() {
                if (--countdown < 0) {
                    printStageProgress();
                    countdown = ++numPrints > 2 ? numPrints * 2 : numPrints;
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void stopPeriodicPrinting() {
        periodicPrintingTask.cancel(false);
    }

    private void printStageEnd(Timer timer) {
        printStageEnd(timer.getTotalTime());
    }

    private void printStageEnd(double totalTime) {
        assert numStageChars >= 0;
        String suffix = String.format("(%.1fs @ %.2fGB)", millisToSeconds(totalTime), getUsedMemory());
        String padding = stringFilledWith(Math.max(0, CHARACTERS_PER_LINE - numStageChars - suffix.length()), " ");
        linePrinter.a(padding).dim().a(suffix).reset().flushln(false);
        numStageChars = 0;
        boolean optionsAvailable = ImageSingletonsSupport.isInstalled() && ImageSingletons.contains(HostedOptionValues.class);
        if (optionsAvailable && SubstrateOptions.BuildOutputGCWarnings.getValue()) {
            checkForExcessiveGarbageCollection();
        }
        restoreBuilderIO();
    }

    private void checkForExcessiveGarbageCollection() {
        long current = System.currentTimeMillis();
        long timeDeltaMillis = current - lastGCCheckTimeMillis;
        lastGCCheckTimeMillis = current;
        GCStats currentGCStats = getCurrentGCStats();
        long gcTimeDeltaMillis = currentGCStats.totalTimeMillis - lastGCStats.totalTimeMillis;
        double ratio = gcTimeDeltaMillis / (double) timeDeltaMillis;
        if (gcTimeDeltaMillis > EXCESSIVE_GC_MIN_THRESHOLD_MILLIS && ratio > EXCESSIVE_GC_RATIO) {
            l().redBold().a("GC warning").reset()
                            .a(": %.1fs spent in %d GCs during the last stage, taking up %.2f%% of the time.",
                                            millisToSeconds(gcTimeDeltaMillis), currentGCStats.totalCount - lastGCStats.totalCount, ratio * 100)
                            .flushln();
            l().a("            Please ensure more than %.2fGB of memory is available for Native Image", bytesToGiB(ProgressReporterCHelper.getPeakRSS())).flushln();
            l().a("            to reduce GC overhead and improve image build time.").flushln();
        }
        lastGCStats = currentGCStats;
    }

    /*
     * HELPERS
     */

    private static void resetANSIMode() {
        NativeImageSystemIOWrappers.singleton().getOut().print(ANSIColors.RESET);
    }

    private static String stringFilledWith(int size, String fill) {
        return new String(new char[size]).replace("\0", fill);
    }

    protected static String truncateClassOrPackageName(String classOrPackageName) {
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

    private static double getUsedMemory() {
        return bytesToGiB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
    }

    private static String bytesToHuman(long bytes) {
        return bytesToHuman("%4.2f", bytes);
    }

    private static String bytesToHuman(String format, long bytes) {
        if (bytes < BYTES_TO_KiB) {
            return String.format(format, (double) bytes) + "B";
        } else if (bytes < BYTES_TO_MiB) {
            return String.format(format, bytesToKiB(bytes)) + "KB";
        } else if (bytes < BYTES_TO_GiB) {
            return String.format(format, bytesToMiB(bytes)) + "MB";
        } else {
            return String.format(format, bytesToGiB(bytes)) + "GB";
        }
    }

    private static double bytesToKiB(long bytes) {
        return bytes / BYTES_TO_KiB;
    }

    private static double bytesToGiB(long bytes) {
        return bytes / BYTES_TO_GiB;
    }

    private static double bytesToMiB(long bytes) {
        return bytes / BYTES_TO_MiB;
    }

    private static double millisToSeconds(double millis) {
        return millis / MILLIS_TO_SECONDS;
    }

    private static double nanosToSeconds(double nanos) {
        return nanos / NANOS_TO_SECONDS;
    }

    private static GCStats getCurrentGCStats() {
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

    private static class GCStats {
        private final long totalCount;
        private final long totalTimeMillis;

        GCStats(long totalCount, long totalTime) {
            this.totalCount = totalCount;
            this.totalTimeMillis = totalTime;
        }
    }

    @AutomaticFeature
    public static class ProgressReporterFeature implements Feature {
        private final ProgressReporter reporter = ProgressReporter.singleton();

        @Override
        public void duringAnalysis(DuringAnalysisAccess access) {
            reporter.printStageProgress();
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
     * COLORFUL OUTPUT
     */

    abstract class LinePrinter {
        private final List<String> textBuffer = new ArrayList<>();
        private final StringBuilder printBuffer;
        protected final String headlineSeparator = stringFilledWith(CHARACTERS_PER_LINE, "=");
        protected final String lineSeparator = stringFilledWith(CHARACTERS_PER_LINE, "-");
        private String outputPrefix = "";

        LinePrinter(boolean enableProgress) {
            printBuffer = enableProgress ? null : new StringBuilder();
        }

        protected LinePrinter a(String text) {
            if (isEnabled) {
                textBuffer.add(text);
            }
            return this;
        }

        protected LinePrinter a(String text, Object... args) {
            return a(String.format(text, args));
        }

        protected LinePrinter a(int i) {
            return a(String.valueOf(i));
        }

        protected LinePrinter a(long i) {
            return a(String.valueOf(i));
        }

        protected LinePrinter appendPrefix() {
            return a(outputPrefix);
        }

        protected LinePrinter jumpToMiddle() {
            int remaining = (CHARACTERS_PER_LINE / 2) - getCurrentTextLength();
            assert remaining >= 0 : "Column text too wide";
            a(stringFilledWith(remaining, " "));
            assert !isEnabled || getCurrentTextLength() == CHARACTERS_PER_LINE / 2;
            return this;
        }

        protected abstract LinePrinter bold();

        protected abstract LinePrinter blue();

        protected abstract LinePrinter blueBold();

        protected abstract LinePrinter redBold();

        protected abstract LinePrinter yellowBold();

        protected abstract LinePrinter dim();

        protected abstract LinePrinter reset();

        protected abstract LinePrinter link(String text, String url);

        protected abstract LinePrinter link(Path path);

        protected abstract LinePrinter doclink(String text, String htmlAnchor);

        protected abstract String asDocLink(String text, String htmlAnchor);

        private void flush() {
            if (!isEnabled) {
                return;
            }
            if (printBuffer != null) {
                textBuffer.forEach(printBuffer::append);
            } else {
                textBuffer.forEach(builderIO.getOut()::print);
            }
            textBuffer.clear();
        }

        private void printRaw(char value) {
            if (!isEnabled) {
                return;
            }
            if (printBuffer != null) {
                printBuffer.append(value);
            } else {
                builderIO.getOut().print(value);
            }
        }

        private void flushln() {
            flushln(true);
        }

        private void flushln(boolean useOutputPrefix) {
            if (!isEnabled) {
                return;
            }
            if (useOutputPrefix) {
                builderIO.getOut().print(outputPrefix);
            }
            if (printBuffer != null) {
                builderIO.getOut().print(printBuffer);
                printBuffer.setLength(0); // Clear buffer.
            }
            textBuffer.forEach(builderIO.getOut()::print);
            textBuffer.clear();
            builderIO.getOut().println();
        }

        private void flushCenteredln() {
            if (!isEnabled) {
                return;
            }
            String padding = stringFilledWith((Math.max(0, CHARACTERS_PER_LINE - getCurrentTextLength())) / 2, " ");
            textBuffer.add(0, padding);
            flushln();
        }

        // Ignores ansi escape sequences
        private int getCurrentTextLength() {
            int textLength = 0;
            for (String text : textBuffer) {
                if (!text.startsWith(ANSIColors.ESCAPE)) {
                    textLength += text.length();
                }
            }
            return textLength;
        }

        private boolean isEmpty() {
            return textBuffer.isEmpty();
        }

        private void printHeadlineSeparator() {
            dim().a(headlineSeparator).reset().flushln();
        }

        private void printLineSeparator() {
            dim().a(lineSeparator).reset().flushln();
        }
    }

    private final class ColorfulLinePrinter extends LinePrinter {

        ColorfulLinePrinter(boolean enableProgress) {
            super(enableProgress);
        }

        @Override
        protected LinePrinter bold() {
            return a(ANSIColors.BOLD);
        }

        @Override
        protected LinePrinter blue() {
            return a(ANSIColors.BLUE);
        }

        @Override
        protected LinePrinter blueBold() {
            return a(ANSIColors.BLUE_BOLD);
        }

        @Override
        protected LinePrinter redBold() {
            return a(ANSIColors.RED_BOLD);
        }

        @Override
        protected LinePrinter yellowBold() {
            return a(ANSIColors.YELLOW_BOLD);
        }

        @Override
        protected LinePrinter dim() {
            return a(ANSIColors.DIM);
        }

        @Override
        protected LinePrinter link(String text, String url) {
            if (showLinks) {
                /* links added as a single item so that jumpToMiddle can ignore url text. */
                a(ANSIColors.LINK_START + url).a(ANSIColors.LINK_TEXT).a(text).a(ANSIColors.LINK_END);
            } else {
                a(text);
            }
            return this;
        }

        @Override
        protected LinePrinter link(Path path) {
            Path normalized = path.normalize();
            return link(normalized.toString(), normalized.toUri().toString());
        }

        @Override
        protected LinePrinter doclink(String text, String htmlAnchor) {
            return link(text, STAGE_DOCS_URL + htmlAnchor);
        }

        @Override
        protected LinePrinter reset() {
            return a(ANSIColors.RESET);
        }

        @Override
        protected String asDocLink(String text, String htmlAnchor) {
            if (showLinks) {
                return String.format(ANSIColors.LINK_FORMAT, STAGE_DOCS_URL + htmlAnchor, text);
            } else {
                return text;
            }
        }
    }

    private final class ColorlessLinePrinter extends LinePrinter {

        ColorlessLinePrinter(boolean enableProgress) {
            super(enableProgress);
        }

        @Override
        protected LinePrinter bold() {
            return this;
        }

        @Override
        protected LinePrinter blue() {
            return this;
        }

        @Override
        protected LinePrinter blueBold() {
            return this;
        }

        @Override
        protected LinePrinter redBold() {
            return this;
        }

        @Override
        protected LinePrinter yellowBold() {
            return this;
        }

        @Override
        protected LinePrinter dim() {
            return this;
        }

        @Override
        protected LinePrinter link(String text, String url) {
            return a(text);
        }

        @Override
        protected LinePrinter link(Path path) {
            return a(path.normalize().toString());
        }

        @Override
        protected LinePrinter doclink(String text, String htmlAnchor) {
            return a(text);
        }

        @Override
        protected LinePrinter reset() {
            return this;
        }

        @Override
        protected String asDocLink(String text, String htmlAnchor) {
            return text;
        }
    }

    private static class ANSIColors {
        static final String ESCAPE = "\033";
        static final String RESET = ESCAPE + "[0m";
        static final String BOLD = ESCAPE + "[1m";
        static final String DIM = ESCAPE + "[2m";

        static final String LINK_START = ESCAPE + "]8;;";
        static final String LINK_TEXT = ESCAPE + "\\";
        static final String LINK_END = LINK_START + LINK_TEXT;
        static final String LINK_FORMAT = LINK_START + "%s" + LINK_TEXT + "%s" + LINK_END;

        static final String BLUE = ESCAPE + "[0;34m";

        static final String RED_BOLD = ESCAPE + "[1;31m";
        static final String YELLOW_BOLD = ESCAPE + "[1;33m";
        static final String BLUE_BOLD = ESCAPE + "[1;34m";
    }
}
