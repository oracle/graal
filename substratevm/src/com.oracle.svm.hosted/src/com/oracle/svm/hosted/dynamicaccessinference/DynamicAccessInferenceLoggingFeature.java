/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dynamicaccessinference;

import java.io.IOException;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.graal.compiler.util.json.JsonWriter;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class DynamicAccessInferenceLoggingFeature implements InternalFeature {

    static class Options {
        @Option(help = "Log inferred dynamic access invocations.", stability = OptionStability.EXPERIMENTAL)//
        static final HostedOptionKey<Boolean> LogDynamicAccessInference = new HostedOptionKey<>(false);
    }

    private DynamicAccessInferenceLog log;

    private static boolean isEnabled() {
        return Options.LogDynamicAccessInference.getValue() || shouldWarnForNonStrictFolding();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        log = new DynamicAccessInferenceLog();
        ImageSingletons.add(DynamicAccessInferenceLog.class, log);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (Options.LogDynamicAccessInference.getValue()) {
            dumpLog();
        }
        if (shouldWarnForNonStrictFolding()) {
            warnForNonStrictFolding();
        }
        /* The log is no longer used after this, so we can clean it up. */
        log.seal();
    }

    private void dumpLog() {
        assert !log.isSealed() : "Attempt to access sealed log";
        String reportsPath = SubstrateOptions.reportsPath();
        ReportUtils.report("inferred dynamic access invocations", reportsPath, "dynamic_access_inference", "json", (writer) -> {
            try (JsonWriter out = new JsonPrettyWriter(writer);
                            JsonBuilder.ArrayBuilder arrayBuilder = out.arrayBuilder()) {
                for (DynamicAccessInferenceLog.LogEntry entry : log.getEntries()) {
                    try (JsonBuilder.ObjectBuilder objectBuilder = arrayBuilder.nextEntry().object()) {
                        entry.toJson(objectBuilder);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static boolean shouldWarnForNonStrictFolding() {
        return StrictDynamicAccessInferenceFeature.Options.StrictDynamicAccessInference.getValue() == StrictDynamicAccessInferenceFeature.Options.Mode.Warn;
    }

    private void warnForNonStrictFolding() {
        assert !log.isSealed() : "Attempt to access sealed log";
        ConstantExpressionRegistry registry = ConstantExpressionRegistry.singleton();
        List<DynamicAccessInferenceLog.LogEntry> unsafeFoldingEntries = log.getEntries().stream().filter(entry -> !registryContainsConstantOperands(registry, entry)).toList();
        if (!unsafeFoldingEntries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("The following method invocations have been inferred outside of the strict constant expression mode:").append(System.lineSeparator());
            for (int i = 0; i < unsafeFoldingEntries.size(); i++) {
                sb.append((i + 1)).append(". ").append(unsafeFoldingEntries.get(i)).append(System.lineSeparator());
            }
            sb.delete(sb.length() - System.lineSeparator().length(), sb.length());
            LogUtils.warning(sb.toString());
        }
    }

    private static boolean registryContainsConstantOperands(ConstantExpressionRegistry registry, DynamicAccessInferenceLog.LogEntry entry) {
        Pair<ResolvedJavaMethod, Integer> callLocation = entry.callLocation;
        if (entry.targetMethod.hasReceiver()) {
            Object receiver = registry.getReceiver(callLocation.getLeft(), callLocation.getRight(), entry.targetMethod);
            if (entry.targetReceiver != DynamicAccessInferenceLog.ignoreArgument() && receiver == null) {
                return false;
            }
        }
        for (int i = 0; i < entry.targetArguments.length; i++) {
            Object argument = registry.getArgument(callLocation.getLeft(), callLocation.getRight(), entry.targetMethod, i);
            if (entry.targetArguments[i] != DynamicAccessInferenceLog.ignoreArgument() && argument == null) {
                return false;
            }
        }
        return true;
    }
}
