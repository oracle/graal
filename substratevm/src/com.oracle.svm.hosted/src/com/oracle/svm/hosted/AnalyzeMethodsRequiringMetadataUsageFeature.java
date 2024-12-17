/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.hosted.phases.AnalyzeMethodsRequiringMetadataUsagePhase;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is a support class that keeps track of calls requiring metadata usage detected during
 * {@link AnalyzeMethodsRequiringMetadataUsagePhase} and outputs them to the image-build output.
 */
@AutomaticallyRegisteredFeature
public final class AnalyzeMethodsRequiringMetadataUsageFeature implements InternalFeature {
    public static final String METHODTYPE_REFLECTION = "reflection";
    public static final String METHODTYPE_RESOURCE = "resource";
    public static final String METHODTYPE_SERIALIZATION = "serialization";
    public static final String METHODTYPE_PROXY = "proxy";
    private final Map<String, List<String>> reflectiveCalls;
    private final Map<String, List<String>> resourceCalls;
    private final Map<String, List<String>> serializationCalls;
    private final Map<String, List<String>> proxyCalls;
    private final Set<String> jarPaths;
    private final Set<FoldEntry> foldEntries = ConcurrentHashMap.newKeySet();

    public AnalyzeMethodsRequiringMetadataUsageFeature() {
        this.reflectiveCalls = new TreeMap<>();
        this.resourceCalls = new TreeMap<>();
        this.serializationCalls = new TreeMap<>();
        this.proxyCalls = new TreeMap<>();
        this.jarPaths = Collections.unmodifiableSet(new HashSet<>(AnalyzeMethodsRequiringMetadataUsageFeature.Options.TrackMethodsRequiringMetadata.getValue().values()));
    }

    public static AnalyzeMethodsRequiringMetadataUsageFeature instance() {
        return ImageSingletons.lookup(AnalyzeMethodsRequiringMetadataUsageFeature.class);
    }

    public void addCall(String methodType, String call, String callLocation) {
        switch (methodType) {
            case METHODTYPE_REFLECTION -> this.reflectiveCalls.computeIfAbsent(call, k -> new ArrayList<>()).add(callLocation);
            case METHODTYPE_RESOURCE -> this.resourceCalls.computeIfAbsent(call, k -> new ArrayList<>()).add(callLocation);
            case METHODTYPE_SERIALIZATION -> this.serializationCalls.computeIfAbsent(call, k -> new ArrayList<>()).add(callLocation);
            case METHODTYPE_PROXY -> this.proxyCalls.computeIfAbsent(call, k -> new ArrayList<>()).add(callLocation);
            default -> throw new IllegalArgumentException("Unknown method type: " + methodType);
        }
    }

    public void printReport(String methodType) {
        Map<String, List<String>> callMap = switch (methodType) {
            case METHODTYPE_REFLECTION -> this.reflectiveCalls;
            case METHODTYPE_RESOURCE -> this.resourceCalls;
            case METHODTYPE_SERIALIZATION -> this.serializationCalls;
            case METHODTYPE_PROXY -> this.proxyCalls;
            default -> throw new IllegalArgumentException("Unknown method type: " + methodType);
        };

        System.out.println(methodType.substring(0, 1).toUpperCase() + methodType.substring(1) + " calls detected:");
        for (String key : callMap.keySet()) {
            System.out.println("    " + key + ":");
            callMap.get(key).sort(Comparator.comparing(String::toString));
            for (String callLocation : callMap.get(key)) {
                System.out.println("        at " + callLocation);
            }
        }
    }

    public void dumpReport(String methodType) {
        Map<String, List<String>> calls = switch (methodType) {
            case METHODTYPE_REFLECTION -> this.reflectiveCalls;
            case METHODTYPE_RESOURCE -> this.resourceCalls;
            case METHODTYPE_SERIALIZATION -> this.serializationCalls;
            case METHODTYPE_PROXY -> this.proxyCalls;
            default -> throw new IllegalArgumentException("Unknown method type: " + methodType);
        };
        String fileName = methodType + "-usage.json";

        Path targetPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve(fileName);
        try (var writer = new JsonWriter(targetPath);
                        var builder = writer.objectBuilder()) {
            for (Map.Entry<String, List<String>> entry : calls.entrySet()) {
                try (JsonBuilder.ArrayBuilder array = builder.append(entry.getKey()).array()) {
                    for (String call : entry.getValue()) {
                        array.append(call);
                    }
                }
            }
            BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
        } catch (IOException e) {
            System.out.println("Failed to print JSON to " + targetPath + ":");
            e.printStackTrace(System.out);
        }
    }

    public void reportMethodUsage() {
        Map<String, Map<String, List<String>>> callMaps = Map.of(
                        METHODTYPE_REFLECTION, this.reflectiveCalls,
                        METHODTYPE_RESOURCE, this.resourceCalls,
                        METHODTYPE_SERIALIZATION, this.serializationCalls,
                        METHODTYPE_PROXY, this.proxyCalls);
        for (Map.Entry<String, Map<String, List<String>>> entry : callMaps.entrySet()) {
            String methodType = entry.getKey();
            Map<String, List<String>> calls = entry.getValue();
            if (!calls.isEmpty()) {
                printReport(methodType);
                dumpReport(methodType);
            }
        }
    }

    public Set<String> getJarPaths() {
        return jarPaths;
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
        AnalyzeMethodsRequiringMetadataUsageFeature.instance().reportMethodUsage();
    }

    public static class Options {
        @Option(help = "Output all metadata requiring call usages in the reached parts of the project, limited to the provided comma-separated list of JAR files.")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> TrackMethodsRequiringMetadata = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());
    }
}
