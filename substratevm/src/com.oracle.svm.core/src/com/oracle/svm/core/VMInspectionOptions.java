/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.WINDOWS;

import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

public final class VMInspectionOptions {
    private static final String ENABLE_MONITORING_OPTION = "enable-monitoring";
    private static final String MONITORING_ALL_NAME = "all";
    private static final String MONITORING_HEAPDUMP_NAME = "heapdump";
    private static final String MONITORING_JFR_NAME = "jfr";
    private static final String MONITORING_JVMSTAT_NAME = "jvmstat";
    private static final String MONITORING_ALLOWED_VALUES = "'" + MONITORING_HEAPDUMP_NAME + "', '" + MONITORING_JFR_NAME + "', '" + MONITORING_JVMSTAT_NAME + "', or '" + MONITORING_ALL_NAME +
                    "' (defaults to '" + MONITORING_ALL_NAME + "' if no argument is provided)";

    @APIOption(name = ENABLE_MONITORING_OPTION, defaultValue = MONITORING_ALL_NAME) //
    @Option(help = "Enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain " + MONITORING_ALLOWED_VALUES + ". " +
                    "For example: `--" + ENABLE_MONITORING_OPTION + "=" + MONITORING_HEAPDUMP_NAME + "," + MONITORING_JVMSTAT_NAME + "`.", type = OptionType.User) //
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> EnableMonitoringFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.commaSeparated(),
                    VMInspectionOptions::validateEnableMonitoringFeatures);

    public static void validateEnableMonitoringFeatures(OptionKey<?> optionKey) {
        Set<String> enabledFeatures = getEnabledMonitoringFeatures();
        enabledFeatures.removeAll(List.of(MONITORING_HEAPDUMP_NAME, MONITORING_JFR_NAME, MONITORING_JVMSTAT_NAME, MONITORING_ALL_NAME));
        if (!enabledFeatures.isEmpty()) {
            throw UserError.abort("The option %s contains invalid value(s): %s. It can only contain %s.", optionKey.getName(), String.join(", ", enabledFeatures), MONITORING_ALLOWED_VALUES);
        }
    }

    @Fold
    public static String getHeapdumpsCommandArgument() {
        return SubstrateOptionsParser.commandArgument(EnableMonitoringFeatures, MONITORING_HEAPDUMP_NAME);
    }

    public static Set<String> getEnabledMonitoringFeatures() {
        return new HashSet<>(EnableMonitoringFeatures.getValue().values());
    }

    private static boolean hasAllOrKeywordMonitoringSupport(String keyword) {
        Set<String> enabledFeatures = getEnabledMonitoringFeatures();
        return enabledFeatures.contains(MONITORING_ALL_NAME) || enabledFeatures.contains(keyword);
    }

    @Fold
    public static boolean hasHeapDumpSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_HEAPDUMP_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Fold
    public static boolean hasJfrSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JFR_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Fold
    public static boolean hasJvmstatSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JVMSTAT_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Option(help = "Dumps all runtime compiled methods on SIGUSR2.", type = OptionType.User) //
    public static final HostedOptionKey<Boolean> DumpRuntimeCompilationOnSignal = new HostedOptionKey<>(false, VMInspectionOptions::validateOnSignalOption);

    @Option(help = "Dumps all thread stacktraces on SIGQUIT/SIGBREAK.", type = OptionType.User) //
    public static final HostedOptionKey<Boolean> DumpThreadStacksOnSignal = new HostedOptionKey<>(false, VMInspectionOptions::validateOnSignalOption);

    private static void validateOnSignalOption(HostedOptionKey<Boolean> optionKey) {
        if (optionKey.getValue() && !SubstrateOptions.EnableSignalAPI.getValue()) {
            throw UserError.abort("The option %s requires the Signal API, but the Signal API is disabled. Please enable with `-H:+%s`.",
                            optionKey.getName(), SubstrateOptions.EnableSignalAPI.getName());
        }
    }

    static class DeprecatedOptions {
        @Option(help = "Enables features that allow the VM to be inspected during run time.", type = OptionType.User, //
                        deprecated = true, deprecationMessage = "Please use --" + ENABLE_MONITORING_OPTION) //
        static final HostedOptionKey<Boolean> AllowVMInspection = new HostedOptionKey<>(false) {
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                EnableMonitoringFeatures.update(values, newValue ? "all" : "");
                DumpRuntimeCompilationOnSignal.update(values, true);
                super.onValueUpdate(values, oldValue, newValue);
            }
        };
    }

    private VMInspectionOptions() {
    }
}
