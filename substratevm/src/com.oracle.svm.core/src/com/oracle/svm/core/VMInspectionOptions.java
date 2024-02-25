/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.WINDOWS;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.heap.dump.HeapDumping;
import com.oracle.svm.core.jdk.management.ManagementAgentModule;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public final class VMInspectionOptions {
    private static final String ENABLE_MONITORING_OPTION = "enable-monitoring";
    private static final String MONITORING_DEFAULT_NAME = "<deprecated-default>";
    private static final String MONITORING_ALL_NAME = "all";
    private static final String MONITORING_HEAPDUMP_NAME = "heapdump";
    private static final String MONITORING_JFR_NAME = "jfr";
    private static final String MONITORING_JVMSTAT_NAME = "jvmstat";
    private static final String MONITORING_JMXCLIENT_NAME = "jmxclient";
    private static final String MONITORING_JMXSERVER_NAME = "jmxserver";
    private static final String MONITORING_THREADDUMP_NAME = "threaddump";
    private static final String MONITORING_NMT_NAME = "nmt";

    private static final List<String> MONITORING_ALL_VALUES = List.of(MONITORING_HEAPDUMP_NAME, MONITORING_JFR_NAME, MONITORING_JVMSTAT_NAME, MONITORING_JMXCLIENT_NAME, MONITORING_JMXSERVER_NAME,
                    MONITORING_THREADDUMP_NAME, MONITORING_NMT_NAME, MONITORING_ALL_NAME, MONITORING_DEFAULT_NAME);
    private static final String MONITORING_ALLOWED_VALUES_TEXT = "'" + MONITORING_HEAPDUMP_NAME + "', '" + MONITORING_JFR_NAME + "', '" + MONITORING_JVMSTAT_NAME + "', '" + MONITORING_JMXSERVER_NAME +
                    "' (experimental), '" + MONITORING_JMXCLIENT_NAME + "' (experimental), '" + MONITORING_THREADDUMP_NAME + "', '" + MONITORING_NMT_NAME + "' (experimental), or '" +
                    MONITORING_ALL_NAME + "' (deprecated behavior: defaults to '" + MONITORING_ALL_NAME + "' if no argument is provided)";

    static {
        assert MONITORING_ALL_VALUES.stream().allMatch(v -> MONITORING_DEFAULT_NAME.equals(v) || MONITORING_ALLOWED_VALUES_TEXT.contains(v)) : "A value is missing in the user-facing help text";
    }

    @APIOption(name = ENABLE_MONITORING_OPTION, defaultValue = MONITORING_DEFAULT_NAME) //
    @Option(help = "Enable monitoring features that allow the VM to be inspected at run time. Comma-separated list can contain " + MONITORING_ALLOWED_VALUES_TEXT + ". " +
                    "For example: '--" + ENABLE_MONITORING_OPTION + "=" + MONITORING_HEAPDUMP_NAME + "," + MONITORING_JFR_NAME + "'.", type = OptionType.User) //
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> EnableMonitoringFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter(),
                    VMInspectionOptions::validateEnableMonitoringFeatures);

    @Option(help = "Dumps all runtime compiled methods on SIGUSR2.", type = OptionType.User) //
    public static final HostedOptionKey<Boolean> DumpRuntimeCompilationOnSignal = new HostedOptionKey<>(false);

    @Option(help = "Print native memory tracking statistics on shutdown if native memory tracking is enabled.", type = OptionType.User) //
    public static final RuntimeOptionKey<Boolean> PrintNMTStatistics = new RuntimeOptionKey<>(false);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void validateEnableMonitoringFeatures(@SuppressWarnings("unused") OptionKey<?> optionKey) {
        Set<String> enabledFeatures = getEnabledMonitoringFeatures();
        if (enabledFeatures.contains(MONITORING_DEFAULT_NAME)) {
            LogUtils.warning(
                            "'%s' without an argument is deprecated. Please always explicitly specify the list of monitoring features to be enabled (for example, '%s').",
                            getDefaultMonitoringCommandArgument(),
                            SubstrateOptionsParser.commandArgument(EnableMonitoringFeatures, String.join(",", List.of(MONITORING_HEAPDUMP_NAME, MONITORING_JFR_NAME))));
        }
        enabledFeatures.removeAll(MONITORING_ALL_VALUES);
        if (!enabledFeatures.isEmpty()) {
            throw UserError.abort("The '%s' option contains invalid value(s): %s. It can only contain %s.", getDefaultMonitoringCommandArgument(), String.join(", ", enabledFeatures),
                            MONITORING_ALLOWED_VALUES_TEXT);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String getDefaultMonitoringCommandArgument() {
        return SubstrateOptionsParser.commandArgument(EnableMonitoringFeatures, MONITORING_DEFAULT_NAME);
    }

    @Fold
    public static String getHeapDumpCommandArgument() {
        return SubstrateOptionsParser.commandArgument(EnableMonitoringFeatures, MONITORING_HEAPDUMP_NAME);
    }

    private static Set<String> getEnabledMonitoringFeatures() {
        return new HashSet<>(EnableMonitoringFeatures.getValue().values());
    }

    private static boolean hasAllOrKeywordMonitoringSupport(String keyword) {
        Set<String> enabledFeatures = getEnabledMonitoringFeatures();
        return enabledFeatures.contains(MONITORING_ALL_NAME) || enabledFeatures.contains(MONITORING_DEFAULT_NAME) || enabledFeatures.contains(keyword);
    }

    @Fold
    public static boolean hasHeapDumpSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_HEAPDUMP_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    public static boolean dumpImageHeap() {
        if (hasHeapDumpSupport()) {
            String absoluteHeapDumpPath = HeapDumping.getHeapDumpPath(SubstrateOptions.Name.getValue() + ".hprof");
            try {
                HeapDumping.singleton().dumpHeap(absoluteHeapDumpPath, true);
            } catch (IOException e) {
                System.err.println("Failed to create heap dump:");
                e.printStackTrace();
                return false;
            }
            System.out.println("Heap dump created at '" + absoluteHeapDumpPath + "'.");
            return true;
        } else {
            System.out.println("Unable to dump heap. Heap dumping is only supported on Linux and MacOS for native executables built with '" +
                            VMInspectionOptions.getHeapDumpCommandArgument() + "'.");
            return false;
        }
    }

    /**
     * Use {@link com.oracle.svm.core.jfr.HasJfrSupport#get()} instead and don't call this method
     * directly because the VM inspection options are only one of multiple ways to enable the JFR
     * support.
     */
    @Fold
    public static boolean hasJfrSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JFR_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Fold
    public static boolean hasJvmstatSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JVMSTAT_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Fold
    public static boolean hasJmxServerSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JMXSERVER_NAME) && !Platform.includedIn(WINDOWS.class) && ManagementAgentModule.isPresent();
    }

    @Fold
    public static boolean hasJmxClientSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_JMXCLIENT_NAME) && !Platform.includedIn(WINDOWS.class);
    }

    @Fold
    public static boolean hasThreadDumpSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_THREADDUMP_NAME) || DeprecatedOptions.DumpThreadStacksOnSignal.getValue();
    }

    @Fold
    public static boolean hasNativeMemoryTrackingSupport() {
        return hasAllOrKeywordMonitoringSupport(MONITORING_NMT_NAME);
    }

    static class DeprecatedOptions {
        @Option(help = "Enables features that allow the VM to be inspected during run time.", type = OptionType.User, //
                        deprecated = true, deprecationMessage = "Please use '--" + ENABLE_MONITORING_OPTION + "'") //
        static final HostedOptionKey<Boolean> AllowVMInspection = new HostedOptionKey<>(false) {
            @Override
            protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
                EnableMonitoringFeatures.update(values, newValue ? "all" : "");
                DumpRuntimeCompilationOnSignal.update(values, true);
                super.onValueUpdate(values, oldValue, newValue);
            }
        };

        @Option(help = "Dumps all thread stacktraces on SIGQUIT/SIGBREAK.", type = OptionType.User, //
                        deprecated = true, deprecationMessage = "Please use '--" + ENABLE_MONITORING_OPTION + "=" + MONITORING_THREADDUMP_NAME + "'") //
        public static final HostedOptionKey<Boolean> DumpThreadStacksOnSignal = new HostedOptionKey<>(false);
    }

    private VMInspectionOptions() {
    }
}
