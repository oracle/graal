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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionParser;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class DumpIsolateCreationOnlyOptionsFeature implements InternalFeature, FeatureSingleton, UnsavedSingleton {
    public static final class Options {
        @Option(help = "Dump options that must be passed during isolate creation and not set later.")//
        public static final HostedOptionKey<Boolean> DumpIsolateCreationOnlyOptions = new HostedOptionKey<>(false);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (Options.DumpIsolateCreationOnlyOptions.getValue()) {
            dumpIsolateOnlyOptions();
        }
    }

    private static void dumpIsolateOnlyOptions() {
        Path dumpPath = NativeImageGenerator.getOutputDirectory().resolve("isolate-creation-only-options.txt");
        try (OutputStream out = Files.newOutputStream(dumpPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            RuntimeOptionParser.singleton().getDescriptors().forEach(descriptor -> {
                if (!(descriptor.getOptionKey() instanceof RuntimeOptionKey<?> runtimeOptionKey)) {
                    return;
                }
                if (!runtimeOptionKey.isIsolateCreationOnly()) {
                    return;
                }
                try {
                    writer.append(descriptor.getOptionValueType().getCanonicalName());
                    writer.append(' ');
                    writer.append(descriptor.getName());
                    writer.newLine();
                } catch (IOException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            });
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.BUILD_INFO, dumpPath);
    }
}
