/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.TrackDynamicAccessEnabled;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.oracle.svm.hosted.driver.IncludeOptionsSupport.parseIncludeSelector;

import static java.lang.System.lineSeparator;

/**
 * This is a support class that keeps track of dynamic access calls requiring metadata usage
 * detected during {@link DynamicAccessDetectionPhase} and outputs them to the image-build output.
 */
@AutomaticallyRegisteredFeature
public final class DynamicAccessDetectionFeature implements InternalFeature {

    // We use a ConcurrentSkipListMap, as opposed to a ConcurrentHashMap, to maintain
    // order of methods by access kind.
    public record MethodsByAccessKind(Map<DynamicAccessDetectionPhase.DynamicAccessKind, CallLocationsByMethod> methodsByAccessKind) {
        MethodsByAccessKind() {
            this(new ConcurrentSkipListMap<>());
        }

        public Set<DynamicAccessDetectionPhase.DynamicAccessKind> getAccessKinds() {
            return methodsByAccessKind.keySet();
        }

        public CallLocationsByMethod getCallLocationsByMethod(DynamicAccessDetectionPhase.DynamicAccessKind accessKind) {
            return methodsByAccessKind.getOrDefault(accessKind, new CallLocationsByMethod());
        }
    }

    // We use a ConcurrentSkipListSet, as opposed to a wrapped ConcurrentHashMap, to maintain
    // order of call locations by method.
    public record CallLocationsByMethod(Map<String, ConcurrentSkipListSet<String>> callLocationsByMethod) {
        CallLocationsByMethod() {
            this(new ConcurrentSkipListMap<>());
        }

        public Set<String> getMethods() {
            return callLocationsByMethod.keySet();
        }

        public ConcurrentSkipListSet<String> getMethodCallLocations(String methodName) {
            return callLocationsByMethod.getOrDefault(methodName, new ConcurrentSkipListSet<>());
        }
    }

    private static final Set<String> neverInlineTrivialMethods = Set.of(
                    "java.lang.invoke.MethodHandles$Lookup.unreflectGetter",
                    "java.lang.invoke.MethodHandles$Lookup.unreflectSetter",
                    "java.io.ObjectInputStream.readObject",
                    "java.io.ObjectStreamClass.lookup",
                    "java.lang.reflect.Array.newInstance",
                    "java.lang.ClassLoader.loadClass",
                    "java.lang.foreign.Linker.nativeLinker");

    public static final String TRACK_ALL = "all";

    private static final String OUTPUT_DIR_NAME = "dynamic-access";
    private static final String TRACK_NONE = "none";
    private static final String TO_CONSOLE = "to-console";
    private static final String NO_DUMP = "no-dump";

    private EconomicSet<String> sourceEntries; // Class path entries and module or
    // package names
    private final Map<String, MethodsByAccessKind> callsBySourceEntry;
    private final Set<FoldEntry> foldEntries = ConcurrentHashMap.newKeySet();
    private final BuildArtifacts buildArtifacts = BuildArtifacts.singleton();
    private final OptionValues hostedOptionValues = HostedOptionValues.singleton();

    private boolean printToConsole;
    private boolean dumpJsonFiles = true;

    public DynamicAccessDetectionFeature() {
        callsBySourceEntry = new ConcurrentSkipListMap<>();
    }

    public static DynamicAccessDetectionFeature instance() {
        return ImageSingletons.lookup(DynamicAccessDetectionFeature.class);
    }

    public void addCall(String entry, DynamicAccessDetectionPhase.DynamicAccessKind accessKind, String call, String callLocation) {
        MethodsByAccessKind entryContent = callsBySourceEntry.computeIfAbsent(entry, k -> new MethodsByAccessKind());
        CallLocationsByMethod methodCallLocations = entryContent.methodsByAccessKind().computeIfAbsent(accessKind, k -> new CallLocationsByMethod());
        ConcurrentSkipListSet<String> callLocations = methodCallLocations.callLocationsByMethod().computeIfAbsent(call, k -> new ConcurrentSkipListSet<>());
        callLocations.add(callLocation);
    }

    public MethodsByAccessKind getMethodsByAccessKind(String entry) {
        return callsBySourceEntry.computeIfAbsent(entry, k -> new MethodsByAccessKind());
    }

    public EconomicSet<String> getSourceEntries() {
        return sourceEntries;
    }

    public static String getEntryName(String path) {
        String fileName = path.substring(path.lastIndexOf(File.separator) + 1);
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    private void printReportForEntry(String entry) {
        System.out.println("Dynamic method usage detected in " + entry + ":");
        MethodsByAccessKind methodsByAccessKind = getMethodsByAccessKind(entry);
        for (DynamicAccessDetectionPhase.DynamicAccessKind accessKind : methodsByAccessKind.getAccessKinds()) {
            System.out.println("    " + accessKind + " calls detected:");
            CallLocationsByMethod methodCallLocations = methodsByAccessKind.getCallLocationsByMethod(accessKind);
            for (String call : methodCallLocations.getMethods()) {
                System.out.println("        " + call + ":");
                for (String callLocation : methodCallLocations.getMethodCallLocations(call)) {
                    System.out.println("            at " + callLocation);
                }
            }
        }
    }

    private static Path getOrCreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new NoSuchFileException(directory.toString(), null,
                                "Failed to retrieve directory: The path exists but is not a directory.");
            }
        } else {
            try {
                Files.createDirectories(directory);
            } catch (IOException e) {
                throw new IOException("Failed to create directory: " + directory, e);
            }
        }
        return directory;
    }

    private void dumpReportForEntry(String entry) {
        try {
            MethodsByAccessKind methodsByAccessKind = getMethodsByAccessKind(entry);
            Path reportDirectory = NativeImageGenerator.generatedFiles(hostedOptionValues)
                            .resolve(OUTPUT_DIR_NAME);
            for (DynamicAccessDetectionPhase.DynamicAccessKind accessKind : methodsByAccessKind.getAccessKinds()) {
                Path entryDirectory = getOrCreateDirectory(reportDirectory.resolve(getEntryName(entry)));
                Path targetPath = entryDirectory.resolve(accessKind.fileName);
                ReportUtils.report("Dynamic Access Detection Report", targetPath,
                                writer -> generateDynamicAccessReport(writer, accessKind, methodsByAccessKind),
                                false);
                buildArtifacts.add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
            }
        } catch (IOException e) {
            throw UserError.abort("Failed to dump report for entry %s: %s", entry, e.getMessage());
        }
    }

    private static void generateDynamicAccessReport(PrintWriter writer, DynamicAccessDetectionPhase.DynamicAccessKind accessKind, MethodsByAccessKind methodsByAccessKind) {
        writer.println("{");
        String methodsJson = methodsByAccessKind.getCallLocationsByMethod(accessKind).getMethods().stream()
                        .map(methodName -> toMethodJson(accessKind, methodName, methodsByAccessKind))
                        .collect(Collectors.joining("," + lineSeparator()));
        writer.println(methodsJson);
        writer.println("}");
    }

    private static String toMethodJson(DynamicAccessDetectionPhase.DynamicAccessKind accessKind, String methodName, MethodsByAccessKind methodsByAccessKind) {
        String locationsJson = methodsByAccessKind.getCallLocationsByMethod(accessKind)
                        .getMethodCallLocations(methodName).stream()
                        .map(location -> "    \"" + location + "\"")
                        .collect(Collectors.joining("," + lineSeparator()));
        return "  \"" + methodName + "\": [" + lineSeparator() +
                        locationsJson + lineSeparator() +
                        "  ]";
    }

    public void reportDynamicAccess() {
        for (String entry : sourceEntries) {
            if (callsBySourceEntry.containsKey(entry)) {
                if (dumpJsonFiles) {
                    dumpReportForEntry(entry);
                }
                if (printToConsole) {
                    printReportForEntry(entry);
                }
            }
        }
    }

    /**
     * Support data structure used to keep track of calls which don't require metadata, but can't be
     * folded.
     */
    public static class FoldEntry {
        private final int bci;
        private final ResolvedJavaMethod method;

        public FoldEntry(int bci, ResolvedJavaMethod method) {
            this.bci = bci;
            this.method = method;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            FoldEntry other = (FoldEntry) obj;
            return bci == other.bci && Objects.equals(method, other.method);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bci, method);
        }
    }

    /**
     * We only add fold entries for methods registered by
     * {@link com.oracle.svm.hosted.snippets.ReflectionPlugins#registerBulkInvocationPlugin}, as
     * these represent methods that cannot be folded but also do not require metadata.
     */
    public void addFoldEntry(int bci, ResolvedJavaMethod method) {
        foldEntries.add(new FoldEntry(bci, method));
    }

    /**
     * If a fold entry exists for the given method, the method should be ignored by the analysis
     * phase.
     */
    public boolean containsFoldEntry(int bci, ResolvedJavaMethod method) {
        return foldEntries.contains(new FoldEntry(bci, method));
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageClassLoader imageClassLoader = ((FeatureImpl.AfterRegistrationAccessImpl) access).getImageClassLoader();
        NativeImageClassLoaderSupport support = imageClassLoader.classLoaderSupport;
        NativeImageClassLoaderSupport.IncludeSelectors dynamicAccessSelectors = support.getDynamicAccessSelectors();

        EconomicSet<String> tmpSet = EconomicSet.create();
        tmpSet.addAll(dynamicAccessSelectors.classpathEntries().stream()
                        .map(path -> path.toAbsolutePath().toString())
                        .collect(Collectors.toSet()));
        tmpSet.addAll(dynamicAccessSelectors.moduleNames());
        tmpSet.addAll(dynamicAccessSelectors.packages().stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet()));
        sourceEntries = EconomicSet.create(tmpSet);

        AccumulatingLocatableMultiOptionValue.Strings options = SubstrateOptions.TrackDynamicAccess.getValue();
        for (String optionValue : options.values()) {
            switch (optionValue) {
                case TO_CONSOLE -> printToConsole = true;
                case NO_DUMP -> dumpJsonFiles = false;
                case TRACK_NONE -> {
                    printToConsole = false;
                    dumpJsonFiles = true;
                }
            }
        }

        ImageSingletons.add(TrackDynamicAccessEnabled.TrackDynamicAccessEnabledSingleton.class, new TrackDynamicAccessEnabled.TrackDynamicAccessEnabledSingleton() {
        });
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        DynamicAccessDetectionFeature.instance().reportDynamicAccess();
        DynamicAccessDetectionPhase.clearMethodSignatures();
        foldEntries.clear();
    }

    @Override
    public void beforeHeapLayout(BeforeHeapLayoutAccess access) {
        callsBySourceEntry.clear();
        sourceEntries.clear();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        ImageClassLoader imageClassLoader = ((FeatureImpl.IsInConfigurationAccessImpl) access).getImageClassLoader();
        NativeImageClassLoaderSupport support = imageClassLoader.classLoaderSupport;
        return !support.dynamicAccessSelectorsEmpty();
    }

    private static String dynamicAccessPossibleOptions() {
        return String.format("[%s, %s, %s, %s, %s]",
                        TRACK_ALL, TRACK_NONE, TO_CONSOLE, NO_DUMP, IncludeOptionsSupport.possibleExtendedOptions());
    }

    public static void parseDynamicAccessOptions(EconomicMap<OptionKey<?>, Object> hostedValues, NativeImageClassLoaderSupport classLoaderSupport) {
        AccumulatingLocatableMultiOptionValue.Strings trackDynamicAccess = SubstrateOptions.TrackDynamicAccess.getValue(new OptionValues(hostedValues));
        Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> valuesWithOrigins = trackDynamicAccess.getValuesWithOrigins();
        valuesWithOrigins.forEach(valueWithOrigin -> {
            String optionArgument = SubstrateOptionsParser.commandArgument(SubstrateOptions.TrackDynamicAccess, valueWithOrigin.value(), true, false);
            var options = Arrays.stream(valueWithOrigin.value().split(",")).toList();
            for (String option : options) {
                UserError.guarantee(!option.isEmpty(), "Option %s from %s cannot be passed an empty string",
                                optionArgument, valueWithOrigin.origin());
                switch (option) {
                    case TRACK_ALL -> classLoaderSupport.setTrackAllDynamicAccess(valueWithOrigin);
                    case TRACK_NONE -> classLoaderSupport.clearDynamicAccessSelectors();
                    case TO_CONSOLE, NO_DUMP -> {
                        // These options are parsed later in the afterRegistration hook
                    }
                    default -> parseIncludeSelector(optionArgument, valueWithOrigin, classLoaderSupport.getDynamicAccessSelectors(), IncludeOptionsSupport.ExtendedOption.parse(option),
                                    dynamicAccessPossibleOptions());
                }
            }
        });
        if (!classLoaderSupport.dynamicAccessSelectorsEmpty()) {
            for (String method : neverInlineTrivialMethods) {
                SubstrateOptions.NeverInlineTrivial.update(hostedValues, method);
            }
        }
    }
}
