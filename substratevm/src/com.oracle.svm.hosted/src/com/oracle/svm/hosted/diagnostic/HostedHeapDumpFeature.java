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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.StringUtil;

import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class HostedHeapDumpFeature implements InternalFeature {

    static class Options {
        @Option(help = "Dump the heap at a specific time during image building. " +
                        "The option accepts a list of comma separated phases, any of: during-analysis, after-analysis, before-compilation, " +
                        "compile-queue-before-inlining, compile-queue-after-inlining, compile-queue-after-compilation, after-image-write, build-end.")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> DumpHeap = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        List<String> validPhases = Stream.of(HostedHeapDumpHandler.Phases.values()).map(HostedHeapDumpHandler.Phases::getName).toList();
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

    @Override
    public void duringSetup(DuringSetupAccess access) {
        DuringSetupAccessImpl config = (DuringSetupAccessImpl) access;
        ImageSingletons.add(HostedHeapDumpHandler.class, new HostedHeapDumpHandler(phases, config.getHostVM().getImageName()));
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        HostedHeapDumpHandler.singleton().dumpDuringAnalysis();
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        HostedHeapDumpHandler.singleton().dumpAfterAnalysis();
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        HostedHeapDumpHandler.singleton().dumpBeforeCompilation();
    }
}
