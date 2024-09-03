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
package com.oracle.svm.hosted.image;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.FeatureImpl;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

@AutomaticallyRegisteredFeature
public class ImageHeapConnectedComponentsFeature implements InternalFeature {
    public static class Options {
        @Option(help = "file:doc-files/PrintImageHeapConnectedComponents.md", type = OptionType.Debug)//
        static final HostedOptionKey<Boolean> PrintImageHeapConnectedComponents = new HostedOptionKey<>(false);
    }

    private AbstractImage image;
    private NativeImageHeap heap;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Options.PrintImageHeapConnectedComponents.getValue();
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        FeatureImpl.AfterHeapLayoutAccessImpl access = (FeatureImpl.AfterHeapLayoutAccessImpl) a;
        this.heap = access.getHeap();
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        this.image = ((FeatureImpl.BeforeImageWriteAccessImpl) access).getImage();
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess a) {
        FeatureImpl.AfterImageWriteAccessImpl access = (FeatureImpl.AfterImageWriteAccessImpl) a;
        Path imagePath = access.getImagePath().getFileName();
        String imageName = imagePath != null ? imagePath.toString() : "native-image";
        ImageHeapConnectedComponentsPrinter printer = new ImageHeapConnectedComponentsPrinter(heap, access.getUniverse().getBigBang(), image, imageName);
        printReport("connected_components_" + imageName, "txt", printer::printConnectedComponentsObjectHistogramReport);
        printReport("summary_info_for_every_object_in_connected_components_" + imageName, "json", printer::printSummaryInfoForEveryObjectInConnectedComponents);
        printReport("access_points_for_connected_components_" + imageName, "json", printer::printAccessPointsForConnectedComponents);
        heap.objectReachabilityInfo.clear();
    }

    private static void printReport(String reportName, String extension, Consumer<PrintWriter> writer) {
        File file = ReportUtils.reportFile(SubstrateOptions.reportsPath(), reportName, extension);
        ReportUtils.report(reportName, file.toPath(), writer);
    }
}
