/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.options.OptionType.Debug;
import static org.graalvm.compiler.options.OptionType.User;

import java.nio.file.Paths;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;

public class NativeImageOptions {

    public static final int DEFAULT_MAX_ANALYSIS_SCALING = 16;

    @Option(help = "Comma separated list of CPU features that will be enabled while building the " +
                    "target executable, irrespective of whether they are supported by the hosted " +
                    "environment. Note that enabling features not present within the target environment " +
                    "may result in application crashes. The specific options available are target " +
                    "platform dependent. See --list-cpu-features for feature list.", type = User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> CPUFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.commaSeparated());

    @APIOption(name = "list-cpu-features")//
    @Option(help = "Show CPU features specific to the target platform and exit.", type = User)//
    public static final HostedOptionKey<Boolean> ListCPUFeatures = new HostedOptionKey<>(false);

    @Option(help = "Comma separated list of CPU features that will be enabled for runtime checks. The " +
                    "native image may check at run time if such features are supported by the target " +
                    "CPU, and can optimize certain operations based on this information. If a feature " +
                    "is not supported at run time, a less optimized variant will be executed. Because of " +
                    "the presence of multiple code variants, enabling runtime features can result in " +
                    "larger executables. To completely turn off runtime checked CPU features, set this " +
                    "option to the empty string. The specific options available are target platform " +
                    "dependent. See --list-cpu-features for feature list. The default values are: " +
                    "AMD64: 'AVX,AVX2'; AArch64: ''", type = User)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> RuntimeCheckedCPUFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.commaSeparated());

    @Option(help = "Overrides CPUFeatures and uses the native architecture, i.e., the architecture of a machine that builds an image. NativeArchitecture takes precedence over CPUFeatures", type = User)//
    public static final HostedOptionKey<Boolean> NativeArchitecture = new HostedOptionKey<>(false);

    @Option(help = "Print information about classes, methods, and fields that are present in the native image")//
    public static final HostedOptionKey<Boolean> PrintUniverse = new HostedOptionKey<>(false);

    @Option(help = "Print logging information during compilation")//
    public static final HostedOptionKey<Boolean> PrintAOTCompilation = new HostedOptionKey<>(false);

    @Option(help = "Print class statistics of native image heap")//
    public static final HostedOptionKey<Boolean> PrintHeapHistogram = new HostedOptionKey<>(false);

    @Option(help = "Print statistics of methods in native image heap")//
    public static final HostedOptionKey<Boolean> PrintMethodHistogram = new HostedOptionKey<>(false);

    @Option(help = "Print the sizes of the elements of the built image")//
    public static final HostedOptionKey<Boolean> PrintImageElementSizes = new HostedOptionKey<>(false);

    @Option(help = "Print the sizes of the native image heap as the image is built")//
    public static final HostedOptionKey<Boolean> PrintImageHeapPartitionSizes = new HostedOptionKey<>(false);

    @Option(help = "Print a list of active features")//
    public static final HostedOptionKey<Boolean> PrintFeatures = new HostedOptionKey<>(false);

    @Option(help = "Directory for temporary files generated during native image generation. If this option is specified, the temporary files are not deleted so that you can inspect them after native image generation")//
    public static final HostedOptionKey<String> TempDirectory = new HostedOptionKey<>("");

    @Option(help = "Suppress console error output for unittests")//
    public static final HostedOptionKey<Boolean> SuppressStderr = new HostedOptionKey<>(false);

    @Option(help = "Suppress console normal output for unittests")//
    public static final HostedOptionKey<Boolean> SuppressStdout = new HostedOptionKey<>(false);

    @Option(help = "Allow MethodTypeFlow to see @Fold methods")//
    public static final HostedOptionKey<Boolean> AllowFoldMethods = new HostedOptionKey<>(false);

    @APIOption(name = "report-unsupported-elements-at-runtime")//
    @Option(help = "Report usage of unsupported methods and fields at run time when they are accessed the first time, instead of as an error during image building", type = User)//
    public static final HostedOptionKey<Boolean> ReportUnsupportedElementsAtRuntime = new HostedOptionKey<>(false);

    @APIOption(name = "allow-incomplete-classpath", deprecated = "Allowing an incomplete classpath is now the default. Use --link-at-build-time to report linking errors at image build time for a class or package.")//
    @Option(help = "Deprecated", type = User)//
    static final HostedOptionKey<Boolean> AllowIncompleteClasspath = new HostedOptionKey<>(false);

    @SuppressWarnings("all")
    private static boolean areAssertionsEnabled() {
        boolean assertsEnabled = false;
        // Next assignment will be executed when asserts are enabled.
        assert assertsEnabled = true;
        return assertsEnabled;
    }

    /**
     * Enum with all C standards.
     *
     * When changing this enum, please change the CStandard option help message and keep the
     * standards in the chronological orders.
     */
    public enum CStandards {
        C89,
        C99,
        C11;

        public boolean compatibleWith(CStandards standard) {
            return this.compareTo(standard) >= 0;
        }
    }

    @Option(help = "C standard to use in header files. Possible values are: [C89, C99, C11]", type = User)//
    public static final HostedOptionKey<String> CStandard = new HostedOptionKey<>("C89");

    public static CStandards getCStandard() {
        try {
            return CStandards.valueOf(CStandard.getValue());
        } catch (IllegalArgumentException e) {
            throw UserError.abort("C standard %s is not supported. Supported standards are: %s", CStandard.getValue(), Arrays.toString(CStandards.values()));
        }
    }

    /**
     * Configures the number of threads used by the {@link CompletionExecutor}.
     */
    @Option(help = "The maximum number of threads to use concurrently during native image generation.")//
    public static final HostedOptionKey<Integer> NumberOfThreads = new HostedOptionKey<>(Math.min(Runtime.getRuntime().availableProcessors(), 32));

    /*
     * Analysis scales well up to 12 cores and gives slight improvements until 18 cores. We set the
     * default value to 16 to minimize wasted resources in large machines.
     */
    @Option(help = "The number of threads to use for analysis during native image generation. The number must be smaller than the NumberOfThreads.")//
    public static final HostedOptionKey<Integer> NumberOfAnalysisThreads = new HostedOptionKey<>(-1);

    @Option(help = "Return after analysis")//
    public static final HostedOptionKey<Boolean> ReturnAfterAnalysis = new HostedOptionKey<>(false);

    @Option(help = "Exit after analysis")//
    public static final HostedOptionKey<Boolean> ExitAfterAnalysis = new HostedOptionKey<>(false);

    @Option(help = "Exit after writing relocatable file")//
    public static final HostedOptionKey<Boolean> ExitAfterRelocatableImageWrite = new HostedOptionKey<>(false);

    @Option(help = "Throw unsafe operation offset errors.)")//
    public static final HostedOptionKey<Boolean> ThrowUnsafeOffsetErrors = new HostedOptionKey<>(true);

    @Option(help = "Print unsafe operation offset warnings.)")//
    public static final HostedOptionKey<Boolean> ReportUnsafeOffsetWarnings = new HostedOptionKey<>(false);

    @Option(help = "Print unsafe operation offset warnings.)")//
    public static final HostedOptionKey<Boolean> UnsafeOffsetWarningsAreFatal = new HostedOptionKey<>(false);

    @Option(help = "Show exception stack traces for exceptions during image building.)")//
    public static final HostedOptionKey<Boolean> ReportExceptionStackTraces = new HostedOptionKey<>(areAssertionsEnabled());

    @Option(help = "Maximum number of types allowed in the image. Used for tests where small number of types is necessary.", type = Debug)//
    public static final HostedOptionKey<Integer> MaxReachableTypes = new HostedOptionKey<>(-1);

    @Option(help = "Sets the dir where diagnostic information is dumped.")//
    public static final HostedOptionKey<String> DiagnosticsDir = new HostedOptionKey<>(
                    Paths.get("reports", ReportUtils.timeStampedFileName("diagnostics", "")).toString());

    @Option(help = "Enables the diagnostic mode.")//
    public static final HostedOptionKey<Boolean> DiagnosticsMode = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                ClassInitializationOptions.PrintClassInitialization.update(values, true);
                SubstitutionReportFeature.Options.ReportPerformedSubstitutions.update(values, true);
                SubstrateOptions.DumpTargetInfo.update(values, true);
                PrintFeatures.update(values, true);
            }
        }
    };

    public static int getMaximumNumberOfConcurrentThreads(OptionValues optionValues) {
        int maxNumberOfThreads = NativeImageOptions.NumberOfThreads.getValue(optionValues);
        if (maxNumberOfThreads < 0) {
            throw UserError.abort("Number of threads can't be negative. Set the NumberOfThreads flag to a positive value.");
        }
        return maxNumberOfThreads;
    }

    public static int getMaximumNumberOfAnalysisThreads(OptionValues optionValues) {
        int optionValue = NativeImageOptions.NumberOfAnalysisThreads.getValue(optionValues);
        int analysisThreads = NumberOfAnalysisThreads.hasBeenSet(optionValues) ? optionValue : Math.min(getMaximumNumberOfConcurrentThreads(optionValues), DEFAULT_MAX_ANALYSIS_SCALING);
        if (analysisThreads < 0) {
            throw UserError.abort("Number of analysis threads can't be negative. Set the NumberOfAnalysisThreads flag to a positive value.");
        }

        if (analysisThreads > NumberOfThreads.getValue(optionValues)) {
            throw UserError.abort("Number of analysis threads can't be larger than NumberOfThreads. Set the NumberOfAnalysisThreads flag to a positive value smaller than NumberOfThreads.");
        }
        return analysisThreads;
    }
}
