/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.graal.pointsto.reports.causality.CausalityExport;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonWriter;

@AutomaticallyRegisteredFeature
public class CausalityExporter implements InternalFeature {

    public final Path targetPath = NativeImageGenerator
                    .generatedFiles(HostedOptionValues.singleton())
                    .resolve(SubstrateOptions.Name.getValue() + ".cg.zip");

    private ZipOutputStream zip;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return AnalysisReportsOptions.PrintCausalityGraph.getValue(HostedOptionValues.singleton());
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
            System.err.println("Causality Export should be run until the compiling phase in order to get code size information!");
        }

        // The activation had to be done outside of this feature in order to be able to log feature
        // registrations themselves.
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        if (!(accessImpl.getBigBang() instanceof PointsToAnalysis)) {
            VMError.unsupportedFeature("CausalityExport only works with the PointsToAnalysis");
        }
        if (accessImpl.getBigBang().analysisPolicy().isContextSensitiveAnalysis()) {
            VMError.unsupportedFeature("CausalityExport only works with context insensitive analysis");
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        var accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;

        try {
            try {
                zip = new ZipOutputStream(new FileOutputStream(targetPath.toFile()));
                CausalityExport.dump((PointsToAnalysis) accessImpl.bb, zip, AnalysisReportsOptions.CausalityGraphVerbose.getValue(HostedOptionValues.singleton()));
            } catch (IOException ex) {
                if (zip != null) {
                    zip.close();
                    zip = null;
                    Files.deleteIfExists(targetPath);
                }
                throw ex;
            }
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere("Failed to create Causality Export", ex);
        }

        if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
            addReachabilityFileAndFinalize(new ReachabilityExport(accessImpl.getUniverse(), m -> 0));
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (zip != null) {
            FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;
            Map<AnalysisMethod, Integer> compilations = new HashMap<>();
            for (var pair : accessImpl.getCompilations().entrySet()) {
                compilations.compute(pair.getKey().getWrapped(), (m, size) -> {
                    if (size == null) {
                        size = 0;
                    }
                    size += pair.getValue().result.getTargetCodeSize();
                    return size;
                });
            }

            addReachabilityFileAndFinalize(new ReachabilityExport(accessImpl.aUniverse, compilations::get));
        }
    }

    private void addReachabilityFileAndFinalize(ReachabilityExport export) {
        try {
            zip.putNextEntry(new ZipEntry("reachability.json"));
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(zip))) {
                export.write(writer);
            }
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere("Failed to create Causality Export: reachability.json", ex);
        } finally {
            try {
                zip.close();
                zip = null;
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere("Failed to close zip file", ex);
            }
        }

        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, targetPath);
    }
}
