/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

public class BuildArtifactsExporter {
    private static final String ENV_VAR_REENABLE_DEPRECATED = "NATIVE_IMAGE_DEPRECATED_BUILD_ARTIFACTS_TXT";

    public static void run(String imageName, BuildArtifacts buildArtifacts, Map<ArtifactType, List<Path>> buildArtifactsMap) {
        run(buildArtifacts, buildArtifactsMap);
        if ("true".equalsIgnoreCase(System.getenv().get(ENV_VAR_REENABLE_DEPRECATED))) {
            reportDeprecatedBuildArtifacts(imageName, buildArtifacts, buildArtifactsMap);
        }
    }

    private static void run(BuildArtifacts buildArtifacts, Map<ArtifactType, List<Path>> buildArtifactsMap) {
        if (buildArtifactsMap.isEmpty() || !SubstrateOptions.GenerateBuildArtifactsFile.getValue()) {
            return; // nothing to do
        }
        Path buildPath = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());
        Path targetPath = buildPath.resolve(SubstrateOptions.BUILD_ARTIFACTS_FILE_NAME);
        try (JsonWriter writer = new JsonWriter(targetPath)) {
            writer.append('{');
            var iterator = buildArtifactsMap.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                writer.quote(entry.getKey().getJsonKey()).append(":");
                JsonPrinter.printCollection(writer, (Collection<Path>) entry.getValue(), Comparator.naturalOrder(), (p, w) -> w.quote(buildPath.relativize(p.toAbsolutePath()).toString()));
                if (iterator.hasNext()) {
                    writer.append(',');
                }
            }
            writer.append('}');
            buildArtifacts.add(ArtifactType.BUILD_INFO, targetPath);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Unable to create " + SubstrateOptions.BUILD_ARTIFACTS_FILE_NAME, e);
        }
    }

    private static void reportDeprecatedBuildArtifacts(String imageName, BuildArtifacts buildArtifacts, Map<ArtifactType, List<Path>> buildArtifactsMap) {
        Path buildDir = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());
        Consumer<PrintWriter> writerConsumer = writer -> buildArtifactsMap.forEach((artifactType, paths) -> {
            writer.println("[" + artifactType + "]");
            if (artifactType == BuildArtifacts.ArtifactType.JDK_LIBRARY_SHIM) {
                writer.println("# Note that shim JDK libraries depend on this");
                writer.println("# particular native image (including its name)");
                writer.println("# and therefore cannot be used with others.");
            }
            paths.stream().map(Path::toAbsolutePath).map(buildDir::relativize).forEach(writer::println);
            writer.println();
        });
        buildArtifacts.add(ArtifactType.BUILD_INFO, ReportUtils.report("build artifacts", buildDir.resolve(imageName + ".build_artifacts.txt"), writerConsumer, false));
    }
}
