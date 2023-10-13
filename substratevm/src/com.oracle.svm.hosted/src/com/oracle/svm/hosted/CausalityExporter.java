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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.reports.AnalysisReportsOptions;
import com.oracle.graal.pointsto.reports.causality.CausalityExport;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@AutomaticallyRegisteredFeature
public class CausalityExporter implements InternalFeature {

    public final Path targetPath = NativeImageGenerator
            .generatedFiles(HostedOptionValues.singleton())
            .resolve(SubstrateOptions.Name.getValue() + ".cg.zip");

    private ZipOutputStream zip;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        ArrayList<Class<? extends Feature>> a = new ArrayList<>();
        a.add(ReachabilityExporter.class);
        return a;
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return AnalysisReportsOptions.PrintCausalityGraph.getValue(HostedOptionValues.singleton());
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (NativeImageOptions.ExitAfterAnalysis.getValue()) {
            System.err.println("Causality Export should be run until the compiling phase in order to get code size information!");
        }

        // The activation had to be done outside of this feature in order to be able to log feature registrations themselves.
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
        BigBang bb = ((FeatureImpl.AfterAnalysisAccessImpl) access).bb;

        try {
            try {
                zip = new ZipOutputStream(new FileOutputStream(targetPath.toFile()));
                CausalityExport.dump((PointsToAnalysis) bb, zip, AnalysisReportsOptions.CausalityGraphVerbose.getValue(HostedOptionValues.singleton()));
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
            addReachabilityFileAndFinalize();
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        if (zip != null) {
            addReachabilityFileAndFinalize();
        }
    }

    private void addReachabilityFileAndFinalize() {
        ReachabilityExporter reachabilityExporter = ImageSingletons.lookup(ReachabilityExporter.class);

        try {
            zip.putNextEntry(new ZipEntry("reachability.json"));
            Files.copy(reachabilityExporter.reachabilityJsonPath, zip);
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
