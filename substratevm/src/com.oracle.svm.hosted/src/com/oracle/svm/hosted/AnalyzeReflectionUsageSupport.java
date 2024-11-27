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
import com.oracle.svm.core.option.HostedOptionValues;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonWriter;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;

public final class AnalyzeReflectionUsageSupport {
    private final Map<String, List<String>> reflectiveCalls;
    private final Set<String> jarPaths = new HashSet<>();
    private static final String ARTIFACT_FILE_NAME = "reflection-usage.json";

    public AnalyzeReflectionUsageSupport(String paths) {
        this.reflectiveCalls = new TreeMap<>();
        this.jarPaths.addAll(Arrays.asList(paths.split(":")));
    }

    public static AnalyzeReflectionUsageSupport instance() {
        AnalyzeReflectionUsageSupport trus = ImageSingletons.lookup(AnalyzeReflectionUsageSupport.class);
        GraalError.guarantee(trus != null, "Should never be null.");
        return trus;
    }

    public void addReflectiveCall(String reflectiveCall, String callLocation) {
        if (!this.reflectiveCalls.containsKey(reflectiveCall)) {
            List<String> callLocationList = new ArrayList<>();
            this.reflectiveCalls.put(reflectiveCall, callLocationList);
        }
        this.reflectiveCalls.get(reflectiveCall).add(callLocation);
    }

    public void printReflectionReport() {
        System.out.println("Reflective calls detected:");
        for (String key : this.reflectiveCalls.keySet()) {
            System.out.println("    " + key + ":");
            this.reflectiveCalls.get(key).sort(Comparator.comparing(String::toString));
            for (String callLocation : this.reflectiveCalls.get(key)) {
                System.out.println("        at " + callLocation);
            }
        }
    }

    public void dumpReflectionReport() {
        try (var writer = new JsonWriter(getTargetPath());
             var builder = writer.objectBuilder()) {
            for (Map.Entry<String, List<String>> entry : this.reflectiveCalls.entrySet()) {
                try (JsonBuilder.ArrayBuilder array = builder.append(entry.getKey()).array()) {
                    for (String call : entry.getValue()) {
                        array.append(call);
                    }
                }
            }
            BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, getTargetPath()); // Maybe shouldn't be BUILD_INFO
        } catch (IOException e) {
            System.out.println("Failed to print JSON:");
            e.printStackTrace(System.out);
        }
    }

    public void reportReflection() {
        if (!this.reflectiveCalls.isEmpty()) {
            printReflectionReport();
            dumpReflectionReport();
        }
    }

    private static Path getTargetPath() {
        Path buildPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());
        return buildPath.resolve(ARTIFACT_FILE_NAME);
    }

    public Set<String> getJarPaths() {
        return jarPaths;
    }
}

