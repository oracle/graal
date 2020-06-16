/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

@AutomaticFeature
public class HostedHeapDumpFeature implements Feature {

    static class Options {
        @Option(help = "Dump the heap at a specific time during image building." +
                        "The option accepts a list of comma separated phases, any of: after-analysis, before-compilation.")//
        public static final HostedOptionKey<String[]> DumpHeap = new HostedOptionKey<>(null);
    }

    enum Phases {
        AfterAnalysis("after-analysis"),
        BeforeCompilation("before-compilation");

        final String name;

        Phases(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        if (Options.DumpHeap.getValue() != null) {
            List<String> validPhases = Stream.of(Phases.values()).map(p -> p.getName()).collect(Collectors.toList());
            List<String> values = OptionUtils.flatten(",", Options.DumpHeap.getValue());
            phases = new ArrayList<>();
            for (String value : values) {
                if (validPhases.contains(value)) {
                    phases.add(value);
                } else {
                    throw UserError.abort("Invalid value " + value + " given for " +
                                    SubstrateOptionsParser.commandArgument(Options.DumpHeap, "") + '.' +
                                    " Valid values are: " + String.join(", ", validPhases) + '.');
                }
            }
            return !phases.isEmpty();
        }

        return false;
    }

    private List<String> phases;
    private Path dumpLocation;
    private String imageName;
    private String timeStamp;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        DuringSetupAccessImpl config = (DuringSetupAccessImpl) access;
        dumpLocation = getDumpLocation();
        imageName = ReportUtils.extractImageName(config.getHostVM().getImageName());
        timeStamp = getTimeStamp();
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        if (phases.contains(Phases.AfterAnalysis.getName())) {
            dumpHeap(Phases.AfterAnalysis.getName());
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (phases.contains(Phases.BeforeCompilation.getName())) {
            dumpHeap(Phases.BeforeCompilation.getName());
        }
    }

    private void dumpHeap(String reason) {
        String outputFile = dumpLocation.resolve(imageName + '-' + reason + '-' + timeStamp + ".hprof").toString();
        System.out.println("Dumping heap " + reason.replace("-", " ") + " to " + outputFile);
        HostedHeapDump.take(outputFile);
    }

    private static Path getDumpLocation() {
        try {
            Path folder = Paths.get(Paths.get(SubstrateOptions.Path.getValue()).toString(), "dumps").toAbsolutePath();
            return Files.createDirectories(folder);
        } catch (IOException e) {
            throw new Error("Cannot create heap dumps directory.", e);
        }
    }

    private static String getTimeStamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
        return LocalDateTime.now().format(formatter);
    }
}
