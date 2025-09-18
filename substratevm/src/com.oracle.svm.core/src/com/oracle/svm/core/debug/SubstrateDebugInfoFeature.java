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

package com.oracle.svm.core.debug;

import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.InstalledCodeObserverSupportFeature;
import com.oracle.svm.core.debug.gdb.GdbJitHandleAccessor;
import com.oracle.svm.core.debug.jitdump.JitdumpProvider;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class SubstrateDebugInfoFeature implements InternalFeature {

    public static final String DEBUG_INFO_OBJFILE_NAME = "objfile";
    public static final String DEBUG_INFO_PERFMAP_NAME = "perf-map";
    public static final String DEBUG_INFO_JITDUMP_NAME = "jitdump";

    public static class Options {

        private static final Set<String> DEBUG_INFO_ALL_FORMATS = Set.of(DEBUG_INFO_OBJFILE_NAME, DEBUG_INFO_PERFMAP_NAME, DEBUG_INFO_JITDUMP_NAME);
        private static final String DEBUG_INFO_ALLOWED_VALUES_TEXT = "'" + DEBUG_INFO_OBJFILE_NAME + "' (default): Generates and installs a full in-memory object file for each run-time compilation." +
                        ", '" + DEBUG_INFO_PERFMAP_NAME + "': Create and append to /tmp/perf-<pid>.map. Each run-time compilation adds one line to the map." +
                        ", '" + DEBUG_INFO_JITDUMP_NAME + "': Create and append to jit-<imageName>.dump. Each run-time compilation adds one or more records to the jitdump file.";

        @Option(help = "Specify formats for run-time debug info generation. Comma-separated list can contain " + DEBUG_INFO_ALLOWED_VALUES_TEXT + ". ")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> RuntimeDebugInfoFormat = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter(),
                        Options::validateRuntimeDebugInfoFormat);

        private static Set<String> getEnabledRuntimeDebugInfoFormats() {
            Set<String> values = RuntimeDebugInfoFormat.getValue().valuesAsSet();
            if (values.isEmpty()) {
                return Set.of(DEBUG_INFO_OBJFILE_NAME);
            }
            return values;
        }

        private static void validateRuntimeDebugInfoFormat(HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> optionKey) {
            UserError.guarantee(!optionKey.hasBeenSet() || SubstrateOptions.RuntimeDebugInfo.getValue(),
                            "Selecting a runtime debug info format is only possible if runtime debug info generation is enabled ('%s').",
                            SubstrateOptionsParser.commandArgument(SubstrateOptions.RuntimeDebugInfo, "+"));

            List<String> selectedFormats = optionKey.getValue().values();
            selectedFormats.removeAll(DEBUG_INFO_ALL_FORMATS);
            if (!selectedFormats.isEmpty()) {
                String d = optionKey.getValue().getDelimiter();
                throw UserError.invalidOptionValue(optionKey, String.join(d, selectedFormats), "Available formats are: " + String.join(d, DEBUG_INFO_ALL_FORMATS));
            }
        }

        @Fold
        public static boolean hasRuntimeDebugInfoFormatSupport(String debugInfoFormat) {
            return getEnabledRuntimeDebugInfoFormats().contains(debugInfoFormat);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return Platform.includedIn(Platform.LINUX.class) && SubstrateOptions.RuntimeDebugInfo.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(InstalledCodeObserverSupportFeature.class);
    }

    @Override
    public void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
        InstalledCodeObserverSupport installedCodeObserverSupport = ImageSingletons.lookup(InstalledCodeObserverSupport.class);

        installedCodeObserverSupport.addObserverFactory(new SubstrateDebugInfoInstaller.Factory(runtimeConfig));
        if (Options.hasRuntimeDebugInfoFormatSupport(DEBUG_INFO_OBJFILE_NAME)) {
            ImageSingletons.add(GdbJitHandleAccessor.class, new GdbJitHandleAccessor());
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (Options.hasRuntimeDebugInfoFormatSupport(DEBUG_INFO_JITDUMP_NAME)) {
            RuntimeSupport.getRuntimeSupport().addStartupHook(JitdumpProvider.startupHook());
            RuntimeSupport.getRuntimeSupport().addShutdownHook(JitdumpProvider.shutdownHook());
        }
    }
}
