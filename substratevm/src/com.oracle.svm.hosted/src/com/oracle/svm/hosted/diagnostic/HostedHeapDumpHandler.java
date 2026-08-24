/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class HostedHeapDumpHandler {
    enum Phases {
        DuringAnalysis("during-analysis"),
        AfterAnalysis("after-analysis"),
        BeforeCompilation("before-compilation"),
        CompileQueueBeforeInlining("compile-queue-before-inlining"),
        CompileQueueAfterInlining("compile-queue-after-inlining"),
        CompileQueueAfterCompilation("compile-queue-after-compilation"),
        AfterImageWrite("after-image-write"),
        BuildEnd("build-end");

        final String name;

        Phases(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final List<String> phases;
    private final Path dumpLocation;
    private final String imageName;
    private final String timeStamp;
    private int iteration;

    HostedHeapDumpHandler(List<String> phases, String imageName) {
        this.phases = List.copyOf(phases);
        dumpLocation = getDumpLocation();
        this.imageName = ReportUtils.extractImageName(imageName);
        timeStamp = getTimeStamp();
    }

    public static HostedHeapDumpHandler singleton() {
        return ImageSingletons.lookup(HostedHeapDumpHandler.class);
    }

    public void dumpDuringAnalysis() {
        if (phases.contains(Phases.DuringAnalysis.getName())) {
            dumpHeap(Phases.DuringAnalysis.getName() + "-" + iteration++);
        }
    }

    public void dumpAfterAnalysis() {
        dumpHeap(Phases.AfterAnalysis);
    }

    public void dumpBeforeCompilation() {
        dumpHeap(Phases.BeforeCompilation);
    }

    public void dumpBeforeInlining() {
        dumpHeap(Phases.CompileQueueBeforeInlining);
    }

    public void dumpAfterInlining() {
        dumpHeap(Phases.CompileQueueAfterInlining);
    }

    public void dumpAfterCompilation() {
        dumpHeap(Phases.CompileQueueAfterCompilation);
    }

    public void dumpAfterImageWrite() {
        dumpHeap(Phases.AfterImageWrite);
    }

    public void dumpBuildEnd() {
        dumpHeap(Phases.BuildEnd);
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
