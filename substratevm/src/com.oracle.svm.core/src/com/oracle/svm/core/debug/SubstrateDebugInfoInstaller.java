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

import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;

public final class SubstrateDebugInfoInstaller implements InstalledCodeObserver {
    public static final String DEBUG_INFO_OBJFILE_NAME = "objfile";
    public static final String DEBUG_INFO_JITDUMP_NAME = "jitdump";

    public static class Options {

        private static final Set<String> DEBUG_INFO_ALL_FORMATS = Set.of(DEBUG_INFO_OBJFILE_NAME, DEBUG_INFO_JITDUMP_NAME);

        @Option(help = """
                        Specify formats for run-time debug info generation as a comma-separated list.
                        Possible values are:
                          "objfile" (default): Generate and install a full in-memory object file for each run-time compilation.
                          "jitdump": Create <RuntimeJitdumpDir>/jit-<pid>.dump and append to it. Each run-time compilation adds one or more records to the file.""")//
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
            return SubstrateOptions.RuntimeDebugInfo.getValue() && getEnabledRuntimeDebugInfoFormats().contains(debugInfoFormat);
        }
    }

    public static final class Factory implements InstalledCodeObserver.Factory {

        private final RuntimeConfiguration runtimeConfig;
        private final SubstrateDebugInfoWriter writer;

        public Factory(RuntimeConfiguration runtimeConfig, SubstrateDebugInfoWriter writer) {
            this.runtimeConfig = runtimeConfig;
            this.writer = writer;
        }

        @Override
        public InstalledCodeObserver create(DebugContext debugContext, SharedMethod method, CompilationResult compilation, Pointer code, int codeSize) {
            try {
                return new SubstrateDebugInfoInstaller(debugContext, writer, method, compilation, runtimeConfig, code, codeSize);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }

    private final InstalledCodeObserverHandle handle;

    private SubstrateDebugInfoInstaller(DebugContext debugContext, SubstrateDebugInfoWriter writer, SharedMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfig,
                    Pointer code, int codeSize) {
        // Initialize the debug info generator and write the debug info.
        SubstrateDebugInfoProvider debugInfoProvider = new SubstrateDebugInfoProvider(debugContext, method, compilation, runtimeConfig, runtimeConfig.getProviders().getMetaAccess(), code.rawValue(),
                        codeSize);
        handle = writer.writeDebugInfo(debugContext, debugInfoProvider);
    }

    @Override
    public InstalledCodeObserverHandle install() {
        return handle;
    }
}
