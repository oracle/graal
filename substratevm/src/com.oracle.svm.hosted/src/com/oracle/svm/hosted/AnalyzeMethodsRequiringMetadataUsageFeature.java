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
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * This is a support class that keeps track of calls requiring metadata usage detected during
 * {@link AnalyzeMethodsRequiringMetadataUsagePhase} and outputs them to the image-build output.
 */
@AutomaticallyRegisteredFeature
public final class AnalyzeMethodsRequiringMetadataUsageFeature implements InternalFeature {
    private final Set<String> jarPaths;
    private final Map<String, Map<String, Map<String, List<String>>>> callsByJar;
    private final Set<FoldEntry> foldEntries = ConcurrentHashMap.newKeySet();

    public AnalyzeMethodsRequiringMetadataUsageFeature() {
        this.callsByJar = new ConcurrentSkipListMap<>();
        this.jarPaths = Set.copyOf(Options.TrackMethodsRequiringMetadata.getValue().values());
    }

    public static AnalyzeMethodsRequiringMetadataUsageFeature instance() {
        return ImageSingletons.lookup(AnalyzeMethodsRequiringMetadataUsageFeature.class);
    }

    public void addCall(String jarPath, String methodType, String call, String callLocation) {
        this.callsByJar.computeIfAbsent(jarPath, k -> new TreeMap<>());
        this.callsByJar.get(jarPath).computeIfAbsent(methodType, k -> new TreeMap<>());
        this.callsByJar.get(jarPath).get(methodType).computeIfAbsent(call, k -> new ArrayList<>());
        this.callsByJar.get(jarPath).get(methodType).get(call).add(callLocation);
    }

    public void printReportForJar(String jarPath) {
        System.out.println("Dynamic method usage detected in " + jarPath + ":");
        for (String methodType : callsByJar.get(jarPath).keySet()) {
            System.out.println("    " + methodType.substring(0, 1).toUpperCase() + methodType.substring(1) + " calls detected:");
            for (String call : callsByJar.get(jarPath).get(methodType).keySet()) {
                System.out.println("        " + call + ":");
                for (String callLocation : callsByJar.get(jarPath).get(methodType).get(call)) {
                    System.out.println("            at " + callLocation);
                }
            }
        }
    }

    public void dumpReportForJar(String jarPath) {
        String fileName = extractLibraryName(jarPath) + "_method_calls.json";
        Map<String, Map<String, List<String>>> calls = callsByJar.get(jarPath);
        Path targetPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton()).resolve(fileName);
        try (var writer = new JsonPrettyWriter(targetPath)) {
            writer.print(calls);
            BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
        } catch (IOException e) {
            System.out.println("Failed to print JSON to " + targetPath + ":");
            e.printStackTrace(System.out);
        }
    }

    public void reportMethodUsage() {
        for (String jarPath : jarPaths) {
            if (callsByJar.containsKey(jarPath)) {
                printReportForJar(jarPath);
                dumpReportForJar(jarPath);
            }
        }
    }

    public Set<String> getJarPaths() {
        return jarPaths;
    }

    public String extractLibraryName(String path) {
        String fileName = path.substring(path.lastIndexOf("/") + 1);
        if (fileName.endsWith(".jar")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
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
