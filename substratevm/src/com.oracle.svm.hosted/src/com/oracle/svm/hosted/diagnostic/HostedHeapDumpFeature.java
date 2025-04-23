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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.util.StringUtil;

import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class HostedHeapDumpFeature implements InternalFeature {

    static class Options {
        @Option(help = "Dump the heap at a specific time during image building." +
                        "The option accepts a list of comma separated phases, any of: during-analysis, after-analysis, before-compilation.")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> DumpHeap = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());
    }

    enum Phases {
        DuringAnalysis("during-analysis"),
        AfterAnalysis("after-analysis"),
        BeforeCompilation("before-compilation"),
        CompileQueueBeforeInlining("compile-queue-before-inlining"),
        CompileQueueAfterInlining("compile-queue-after-inlining"),
        CompileQueueAfterCompilation("compile-queue-after-compilation");

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
        List<String> validPhases = Stream.of(Phases.values()).map(Phases::getName).collect(Collectors.toList());
        List<String> values = Options.DumpHeap.getValue().values();
        phases = new ArrayList<>();
        for (String value : values) {
            if (validPhases.contains(value)) {
                phases.add(value);
            } else {
                throw UserError.abort("Invalid value %s given for %s. Valid values are %s.",
                                value, SubstrateOptionsParser.commandArgument(Options.DumpHeap, ""), StringUtil.joinSingleQuoted(validPhases));
            }
        }
        return !phases.isEmpty();
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

    private int iteration;

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (phases.contains(Phases.DuringAnalysis.getName())) {
            dumpHeap(Phases.DuringAnalysis.getName() + "-" + iteration++);
        }
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        dumpHeap(Phases.AfterAnalysis);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        dumpHeap(Phases.BeforeCompilation);
    }

    public void beforeInlining() {
        dumpHeap(Phases.CompileQueueBeforeInlining);
    }

    public void afterInlining() {
        dumpHeap(Phases.CompileQueueAfterInlining);
    }

    public void compileQueueAfterCompilation() {
        dumpHeap(Phases.CompileQueueAfterCompilation);
    }

    private void dumpHeap(Phases phase) {
        if (phases.contains(phase.getName())) {
            dumpHeap(phase.getName());
        }
    }

    private void dumpHeap(String reason) {
        String outputFile = dumpLocation.resolve(imageName + '-' + reason + '-' + timeStamp + ".hprof").toString();
        System.out.println("Dumping heap " + reason.replace("-", " ") + " to " + outputFile);
        HostedHeapDump.take(outputFile);
    }

    private static Path getDumpLocation() {
        try {
            Path folder = SubstrateOptions.getImagePath().resolve("dumps").toAbsolutePath();
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
