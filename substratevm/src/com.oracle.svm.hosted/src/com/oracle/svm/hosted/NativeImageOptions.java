/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.options.OptionType.Debug;
import static jdk.graal.compiler.options.OptionType.User;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ForkJoinPool;

import com.oracle.svm.util.LogUtils;
import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.BundleMember;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.classinitialization.ClassInitializationOptions;
import com.oracle.svm.hosted.util.CPUType;
import com.oracle.svm.util.StringUtil;

public class NativeImageOptions {

    public static final int DEFAULT_MAX_ANALYSIS_SCALING = 16;

    @Option(help = "Comma separated list of CPU features that will be enabled while building the " +
                    "target executable, irrespective of whether they are supported by the hosted " +
                    "environment. Note that enabling features not present within the target environment " +
                    "may result in application crashes. The specific options available are target " +
                    "platform dependent. See --list-cpu-features for feature list. These features " +
                    "are in addition to -march.", type = User, stability = OptionStability.STABLE)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> CPUFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

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
    public static final HostedOptionKey<LocatableMultiOptionValue.Strings> RuntimeCheckedCPUFeatures = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

    public static final String MICRO_ARCHITECTURE_NATIVE = "native";
    public static final String MICRO_ARCHITECTURE_COMPATIBILITY = "compatibility";
    public static final String MICRO_ARCHITECTURE_LIST = "list";

    @APIOption(name = "-march")//
    @Option(help = "Generate instructions for a specific machine type. Defaults to 'x86-64-v3' on AMD64 and 'armv8-a' on AArch64. " +
                    "Use -march=" + MICRO_ARCHITECTURE_COMPATIBILITY + " for best compatibility, or -march=" + MICRO_ARCHITECTURE_NATIVE +
                    " for best performance if the native executable is deployed on the same machine or on a machine with the same CPU features. " +
                    "To list all available machine types, use -march=" + MICRO_ARCHITECTURE_LIST + ".", type = User)//
    public static final HostedOptionKey<String> MicroArchitecture = new HostedOptionKey<>(null) {
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, String oldValue, String newValue) {
            if (MICRO_ARCHITECTURE_LIST.equals(newValue)) {
                CPUType.printList();
                throw new InterruptImageBuilding("");
            }
        }
    };

    @Option(help = "Uses the native architecture, i.e., the architecture of a machine that builds an image.", type = User, //
                    deprecated = true, deprecationMessage = "Please use '-march=native' instead. See '--help' for details.") //
    public static final HostedOptionKey<Boolean> NativeArchitecture = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            MicroArchitecture.update(values, newValue ? MICRO_ARCHITECTURE_NATIVE : null);
        }
    };

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
    @BundleMember(role = BundleMember.Role.Output)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Paths> TempDirectory = new HostedOptionKey<>(LocatableMultiOptionValue.Paths.build());

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

    @Option(help = "C standard to use in header files. Possible values are 'C89', 'C99', and 'C11'.", type = User)//
    public static final HostedOptionKey<String> CStandard = new HostedOptionKey<>("C89");

    public static CStandards getCStandard() {
        try {
            return CStandards.valueOf(CStandard.getValue());
        } catch (IllegalArgumentException e) {
            throw UserError.abort("C standard '%s' is not supported. Supported standards are %s.", CStandard.getValue(), StringUtil.joinSingleQuoted(CStandards.values()));
        }
    }

    /**
     * Configures the number of threads of the common pool (see driver).
     */
    private static final String PARALLELISM_OPTION_NAME = "parallelism";
    @APIOption(name = PARALLELISM_OPTION_NAME)//
    @Option(help = "The maximum number of threads to use concurrently during native image generation.")//
    public static final HostedOptionKey<Integer> NumberOfThreads = new HostedOptionKey<>(Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), 32)), key -> {
        int numberOfThreads = key.getValue();
        if (numberOfThreads < 1) {
            throw UserError.abort("The number of threads was set to %s. Please set the '--%s' option to at least 1.", numberOfThreads, PARALLELISM_OPTION_NAME);
        }
    });

    public static int getActualNumberOfThreads() {
        int commonThreadParallelism = ForkJoinPool.getCommonPoolParallelism();
        if (NumberOfThreads.getValue() == 1) {
            assert commonThreadParallelism == 1 : "Disabled common pool expected to report parallelism of 1";
            commonThreadParallelism = 0; /* A disabled common pool has no actual threads */
        }
        /*
         * Main thread plus common pool threads. setCommonPoolParallelism() asserts that this number
         * matches NumberOfThreads.
         */
        return 1 + commonThreadParallelism;
    }

    public static void setCommonPoolParallelism(OptionValues optionValues) {
        if (NativeImageOptions.NumberOfThreads.hasBeenSet(optionValues)) {
            /*
             * The main thread always helps to process tasks submitted to the common pool (e.g., see
             * ForkJoinPool#awaitTermination()), so subtract one from the number of threads. The
             * common pool can be disabled "by setting the parallelism property to zero" (see
             * ForkJoinPool's javadoc).
             */
            int numberOfCommonPoolThreads = NativeImageOptions.NumberOfThreads.getValue(optionValues) - 1;
            String commonPoolParallelismProperty = "java.util.concurrent.ForkJoinPool.common.parallelism";
            assert System.getProperty(commonPoolParallelismProperty) == null : commonPoolParallelismProperty + " already set";
            System.setProperty(commonPoolParallelismProperty, "" + numberOfCommonPoolThreads);
            int actualCommonPoolParallelism = ForkJoinPool.commonPool().getParallelism();
            /*
             * getParallelism() returns at least 1, even in single-threaded mode where common pool
             * is disabled.
             */
            boolean isSingleThreadedMode = numberOfCommonPoolThreads == 0 && actualCommonPoolParallelism == 1;
            if (!isSingleThreadedMode && actualCommonPoolParallelism != numberOfCommonPoolThreads) {
                String warning = "Failed to set parallelism of common pool (actual parallelism is %s).".formatted(actualCommonPoolParallelism);
                assert false : warning;
                LogUtils.warning(warning);
            }
        }
    }

    @Option(help = "Deprecated, option no longer has any effect", deprecated = true, deprecationMessage = "Please use '--parallelism' instead.")//
    public static final HostedOptionKey<Integer> NumberOfAnalysisThreads = new HostedOptionKey<>(-1);

    @Option(help = "Return after analysis")//
    public static final HostedOptionKey<Boolean> ReturnAfterAnalysis = new HostedOptionKey<>(false);

    @Option(help = "Exit after analysis")//
    public static final HostedOptionKey<Boolean> ExitAfterAnalysis = new HostedOptionKey<>(false);

    @Option(help = "Exit after writing relocatable file")//
    public static final HostedOptionKey<Boolean> ExitAfterRelocatableImageWrite = new HostedOptionKey<>(false);

    @Option(help = "Throw unsafe operation offset errors.")//
    public static final HostedOptionKey<Boolean> ThrowUnsafeOffsetErrors = new HostedOptionKey<>(true);

    @Option(help = "Print unsafe operation offset warnings.")//
    public static final HostedOptionKey<Boolean> ReportUnsafeOffsetWarnings = new HostedOptionKey<>(false);

    @Option(help = "Print unsafe operation offset warnings.")//
    public static final HostedOptionKey<Boolean> UnsafeOffsetWarningsAreFatal = new HostedOptionKey<>(false);

    /**
     * Inspired by HotSpot's hs_err_<pid>.log files and for build-time errors (err_b).
     *
     * Keep in sync with the {@code catch_files} array in {@code ci/common.jsonnet}.
     */
    private static final String DEFAULT_ERROR_FILE_NAME = "svm_err_b_%t_pid%p.md";

    public static final Path getErrorFilePath(OptionValues hostedOptionValues) {
        String errorFile = NativeImageOptions.ErrorFile.getValue(hostedOptionValues);
        Path expandedErrorFile = expandErrorFile(errorFile);
        if (expandedErrorFile.isAbsolute()) {
            throw UserError.abort("The error filename mask specified with " + SubstrateOptionsParser.commandArgument(NativeImageOptions.ErrorFile, errorFile) +
                            " is not allowed to be an absolute path.");
        }
        return NativeImageGenerator.generatedFiles(hostedOptionValues).resolve(expandedErrorFile);
    }

    private static Path expandErrorFile(String errorFile) {
        String timestamp = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS").format(new Date(GraalServices.getGlobalTimeStamp()));
        return Path.of(errorFile.replaceAll("%p", GraalServices.getExecutionID()).replaceAll("%t", timestamp));
    }

    @Option(help = "If an error occurs, save a build error report to this file [default: " + DEFAULT_ERROR_FILE_NAME + "] (%p replaced with pid, %t with timestamp).)")//
    public static final HostedOptionKey<String> ErrorFile = new HostedOptionKey<>(DEFAULT_ERROR_FILE_NAME);

    @Option(help = "Show exception stack traces for exceptions during image building.)", stability = OptionStability.STABLE)//
    public static final HostedOptionKey<Boolean> ReportExceptionStackTraces = new HostedOptionKey<>(areAssertionsEnabled());

    @Option(help = "Maximum number of types allowed in the image. Used for tests where small number of types is necessary.", type = Debug)//
    public static final HostedOptionKey<Integer> MaxReachableTypes = new HostedOptionKey<>(-1);

    @Option(help = "Sets the dir where diagnostic information is dumped.")//
    @BundleMember(role = BundleMember.Role.Output)//
    public static final HostedOptionKey<LocatableMultiOptionValue.Paths> DiagnosticsDir = new HostedOptionKey<>(
                    LocatableMultiOptionValue.Paths.buildWithDefaults(Paths.get("reports", ReportUtils.timeStampedFileName("diagnostics", ""))));

    @Option(help = "Enables the diagnostic mode.")//
    public static final HostedOptionKey<Boolean> DiagnosticsMode = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            if (newValue) {
                ClassInitializationOptions.PrintClassInitialization.update(values, true);
                SubstitutionReportFeature.Options.ReportPerformedSubstitutions.update(values, true);
                SubstrateOptions.DumpTargetInfo.update(values, true);
                PrintFeatures.update(values, true);
                ReportExceptionStackTraces.update(values, true);
            }
        }
    };
}
