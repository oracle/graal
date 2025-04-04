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

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.phases.DynamicAccessDetectionPhase;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * This is a support class that keeps track of dynamic access calls requiring metadata usage
 * detected during {@link DynamicAccessDetectionPhase} and outputs them to the image-build output.
 */
@AutomaticallyRegisteredFeature
public final class DynamicAccessDetectionFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Output all method calls requiring metadata for dynamic access in the reached parts of the project, limited to the provided comma-separated list of class path or module path entries.")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> TrackDynamicAccess = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());
    }

    private record MethodsByAccessKind(Map<DynamicAccessDetectionPhase.DynamicAccessKind, CallLocationsByMethod> methodsByAccessKind) {
        MethodsByAccessKind() {
            this(new TreeMap<>());
        }

        public Set<DynamicAccessDetectionPhase.DynamicAccessKind> getAccessKinds() {
            return methodsByAccessKind.keySet();
        }

        public CallLocationsByMethod getCallLocationsByMethod(DynamicAccessDetectionPhase.DynamicAccessKind accessKind) {
            return methodsByAccessKind.getOrDefault(accessKind, new CallLocationsByMethod());
        }
    }

    private record CallLocationsByMethod(Map<String, List<String>> callLocationsByMethod) {
        CallLocationsByMethod() {
            this(new TreeMap<>());
        }

        public Set<String> getMethods() {
            return callLocationsByMethod.keySet();
        }

        public List<String> getMethodCallLocations(String methodName) {
            return callLocationsByMethod.getOrDefault(methodName, new ArrayList<>());
        }
    }

    private static final String OUTPUT_DIR_NAME = "dynamic-access";

    private final Set<String> pathEntries; // Class or module path entries
    private final Map<String, MethodsByAccessKind> callsByPathEntry;
    private final Set<FoldEntry> foldEntries = ConcurrentHashMap.newKeySet();

    public DynamicAccessDetectionFeature() {
        this.callsByPathEntry = new ConcurrentSkipListMap<>();
        this.pathEntries = Options.TrackDynamicAccess.getValue().values().stream()
                .map(entry -> {
                    if (entry.endsWith("/")) {
                        return entry.substring(0, entry.length() - 1);
                    }
                    return entry;
                })
                .collect(Collectors.toSet());
    }

    public static DynamicAccessDetectionFeature instance() {
        return ImageSingletons.lookup(DynamicAccessDetectionFeature.class);
    }

    public void addCall(String entry, DynamicAccessDetectionPhase.DynamicAccessKind accessKind, String call, String callLocation) {
        MethodsByAccessKind entryContent = this.callsByPathEntry.computeIfAbsent(entry, k -> new MethodsByAccessKind());
        CallLocationsByMethod methodCallLocations = entryContent.methodsByAccessKind().computeIfAbsent(accessKind, k -> new CallLocationsByMethod());
        List<String> callLocations = methodCallLocations.callLocationsByMethod().computeIfAbsent(call, k -> new ArrayList<>());
        callLocations.add(callLocation);
    }

    public MethodsByAccessKind getMethodsByAccessKind(String entry) {
        return this.callsByPathEntry.getOrDefault(entry, new MethodsByAccessKind());
    }

    public Set<String> getPathEntries() {
        return pathEntries;
    }

    public static String getEntryName(String path) {
        String fileName = path.substring(path.lastIndexOf("/") + 1);
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

    public static Path getOrCreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            if (!Files.isDirectory(directory)) {
                throw new NoSuchFileException(directory.toString(), null,
                                "Failed to retrieve directory: The path exists but is not a directory.");
            }
        } else {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                throw new IOException("Failed to create directory: " + directory, e);
            }
        }
        return directory;
    }

    private void dumpReportForEntry(String entry) {
        try {
            Path reportDirectory = getOrCreateDirectory(NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve(OUTPUT_DIR_NAME));
            MethodsByAccessKind methodsByAccessKind = getMethodsByAccessKind(entry);
            for (DynamicAccessDetectionPhase.DynamicAccessKind accessKind : methodsByAccessKind.getAccessKinds()) {
                Path entryDirectory = getOrCreateDirectory(reportDirectory.resolve(getEntryName(entry)));
                Path targetPath = entryDirectory.resolve(accessKind.fileName);
                try (var writer = new JsonPrettyWriter(targetPath)) {
                    try (JsonBuilder.ObjectBuilder dynamicAccessBuilder = writer.objectBuilder()) {
                        for (String methodName : methodsByAccessKind.getCallLocationsByMethod(accessKind).getMethods()) {
                            try (JsonBuilder.ArrayBuilder methodCallLocationBuilder = dynamicAccessBuilder.append(methodName).array()) {
                                for (String methodLocation : methodsByAccessKind.getCallLocationsByMethod(accessKind).getMethodCallLocations(methodName)) {
                                    methodCallLocationBuilder.append(methodLocation);
                                }
                            }
                        }
                    }
                    BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
                }
            }
        } catch (IOException e) {
            UserError.abort("Failed to dump report for entry %s:", entry);
        }
    }

    public void reportDynamicAccess() {
        for (String entry : pathEntries) {
            if (callsByPathEntry.containsKey(entry)) {
                printReportForEntry(entry);
                dumpReportForEntry(entry);
            }
        }
    }

    /*
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

    public void addFoldEntry(int bci, ResolvedJavaMethod method) {
        this.foldEntries.add(new FoldEntry(bci, method));
    }

    /*
     * If a fold entry exists for the given method, the method should be ignored by the analysis
     * phase.
     */
    public boolean containsFoldEntry(int bci, ResolvedJavaMethod method) {
        return this.foldEntries.contains(new FoldEntry(bci, method));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        DynamicAccessDetectionFeature.instance().reportDynamicAccess();
    }
}
