/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import static com.oracle.svm.guest.staging.option.RuntimeOptionKey.RuntimeOptionKeyFlag.Immutable;
import static com.oracle.svm.guest.staging.option.RuntimeOptionValidators.NON_NEGATIVE;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidation;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RistrettoOptions {

    @Option(help = "Preserve AOT bytecodes for inlining and deoptimization in Ristretto runtime compilations. " +
                    "Example: com.foo.Bar.baz registers baz as an analysis root, while com.foo.Bar.* and negative patterns only match methods that " +
                    "analysis already reaches. Class initializers and bodies loading unresolved runtime-deferred constant dynamics, including through " +
                    "invokedynamic bootstrap arguments, cannot be preserved. Unsupported exact selections fail; wildcard selections are normally skipped, " +
                    "but unsupported field, invocation, or constant semantics newly discovered for a retained body at final analysis are fatal for all selections.")//
    public static final HostedOptionKey<String> JITPreserveBytecodesForRuntime = new HostedOptionKey<>(null, RistrettoOptions::validatePreservedBytecodes);

    @Option(help = "Fail when preserving AOT bytecodes requires additional method, type, field or image-heap reachability. " +
                    "By default, retain these dependencies and log them only when -H:Log=PreserveBytecodes is enabled. " +
                    "Reachability is checked before each selected method is registered during analysis.")//
    public static final HostedOptionKey<Boolean> JITPreserveBytecodesFailOnAdditionalReachability = new HostedOptionKey<>(false);

    @Option(help = "Fail if a wildcard-selected AOT method loses its preserved bytecodes after analysis retained them. " +
                    "By default, skip the body and log the rejection only when -H:Log=PreserveBytecodes is enabled. " +
                    "Dependencies registered during preservation analysis may remain in the image. " +
                    "Early wildcard exclusions are unaffected; exact preservation requests still fail if their bodies cannot be kept. This option does not " +
                    "control final-analysis safety rejection of newly resolved unsupported field, invocation, or constant semantics, which always fails.")//
    public static final HostedOptionKey<Boolean> JITPreserveBytecodesFailOnLateRejection = new HostedOptionKey<>(false);

    @Option(help = "Use the Graal JIT compiler at runtime to compile bytecodes. Can only be configured during startup.")//
    public static final RuntimeOptionKey<Boolean> JITEnableCompilation = new RuntimeOptionKey<>(true, Immutable);

    @Option(help = "Number of invocations before compilation is triggered on a method.")//
    public static final RuntimeOptionKey<Integer> JITCompilerInvocationThreshold = new RuntimeOptionKey<>(1000);

    @Option(help = "Use on-stack replacement to enter runtime-compiled Ristretto code from interpreted loops. Can only be configured during startup.")//
    public static final RuntimeOptionKey<Boolean> JITUseOnStackReplacement = new RuntimeOptionKey<>(true, Immutable);

    /**
     * Compilation mode, initialized by the startup hook before application execution and unchanged
     * afterwards.
     */
    private static boolean compilationEnabled;
    private static boolean useOSR;

    @Option(help = "Number of loop backedges before OSR compilation is triggered for a method and target BCI.")//
    public static final RuntimeOptionKey<Integer> JITCompilerOSRBackedgeThreshold = new RuntimeOptionKey<>(30000, NON_NEGATIVE, null);

    @Option(help = "Disable invocation-entry Ristretto JIT compilations while leaving OSR compilations enabled.")//
    public static final RuntimeOptionKey<Boolean> JITDisableRootCompiles = new RuntimeOptionKey<>(false);

    @Option(help = "Comma-separated method-name filters that restrict which methods may be compiled by the Ristretto JIT.")//
    public static final RuntimeOptionKey<String> JITCompileOnly = new RuntimeOptionKey<>("");

    @Option(help = "Compile eligible Ristretto methods before their first invocation and wait for background compilation.")//
    public static final RuntimeOptionKey<Boolean> JITXComp = new RuntimeOptionKey<>(false);

    @Option(help = "Wait for submitted background Ristretto compilation requests to finish.")//
    public static final RuntimeOptionKey<Boolean> JITXBatch = new RuntimeOptionKey<>(false);

    @Option(help = "Number of threads to use for Graal JIT compilation.")//
    public static final RuntimeOptionKey<Integer> JITCompilerThreadCount = new RuntimeOptionKey<>(1, RistrettoOptions::positive, null);

    @Option(help = "Report a diagnostic message for a Ristretto compilation that runs longer than this many seconds and prevent this watcher from exiting the VM (0 leaves the generic CompilationWatchDog configuration unchanged).")//
    public static final RuntimeOptionKey<Integer> JITCompilationWatchdogTimeoutSeconds = new RuntimeOptionKey<>(0, NON_NEGATIVE, null);

    @Option(help = "Trace decisions about when to compile what.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilationQueuing = new RuntimeOptionKey<>(false);

    @Option(help = "Trace counter values during profiling.")//
    public static final RuntimeOptionKey<Boolean> JITTraceProfilingIncrements = new RuntimeOptionKey<>(false);

    @Option(help = "Trace compilation events.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilation = new RuntimeOptionKey<>(false);

    @Option(help = "Trace installed Ristretto JIT code address ranges.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCodeInstallations = new RuntimeOptionKey<>(false);

    @Option(help = "Print stack traces of compiler exceptions.")//
    public static final RuntimeOptionKey<Boolean> JITPrintExceptions = new RuntimeOptionKey<>(false);

    @Option(help = "Periodically dump Ristretto compiler statistics to stdout.")//
    public static final HostedOptionKey<Boolean> JITTraceCompilerStatistics = new HostedOptionKey<>(false);

    @Option(help = "Period, in seconds, between Ristretto compiler statistics dumps.")//
    public static final HostedOptionKey<Integer> JITTraceCompilerStatisticsPeriodSeconds = new HostedOptionKey<>(60, RistrettoOptions::validateCompilerStatisticsPeriod);

    private static void positive(RuntimeOptionKey<Integer> option, int value) {
        if (value <= 0) {
            throw RuntimeOptionValidation.invalidOptionValue(option, value, "The value must be greater than 0");
        }
    }

    private static void validatePreservedBytecodes(HostedOptionKey<String> option) {
        String value = option.getValue();
        if (value != null && !value.isBlank() && !SubstrateOptions.useRistretto()) {
            throw UserError.invalidOptionValue(option, value, "Preserving bytecodes requires Ristretto.");
        }
    }

    private static void validateCompilerStatisticsPeriod(HostedOptionKey<Integer> option) {
        if (option.getValue() <= 0) {
            throw UserError.invalidOptionValue(option, option.getValue(), "The value must be positive.");
        }
    }

    public static int getJITCompilerOSRBackedgeThreshold() {
        return JITCompilerOSRBackedgeThreshold.getValue();
    }

    /** Returns whether compilation was enabled at startup. */
    public static boolean isCompilationEnabled() {
        return compilationEnabled;
    }

    /** Returns whether both compilation and OSR were enabled at startup. */
    public static boolean useOSR() {
        return useOSR;
    }

    static void initializeRuntimeOptionCache() {
        compilationEnabled = JITEnableCompilation.getValue();
        useOSR = compilationEnabled && JITUseOnStackReplacement.getValue();
    }

    public static final class ConcealedOptions {
        @Option(help = "Use deoptimization for runtime compiled code optimizations.")//
        public static final HostedOptionKey<Boolean> JITUseDeoptimization = new HostedOptionKey<>(true);
    }

    public static boolean matchesJITCompileOnly(Object method) {
        String compileOnly = JITCompileOnly.getValue();
        if (compileOnly.isBlank()) {
            return true;
        }

        String methodName = method instanceof ResolvedJavaMethod resolvedMethod ? resolvedMethod.format("%H.%n(%p)") : String.valueOf(method);
        for (String filter : compileOnly.split(",")) {
            String trimmedFilter = filter.trim();
            if (!trimmedFilter.isEmpty() && methodName.contains(trimmedFilter)) {
                return true;
            }
        }
        return false;
    }

    public static boolean useBlockingCompilation() {
        return JITXComp.getValue() || JITXBatch.getValue();
    }

    @Fold
    public static boolean useDeoptimization() {
        return ConcealedOptions.JITUseDeoptimization.getValue();
    }
}

/**
 * Initializes the effective Ristretto compilation mode during runtime initialization, after option
 * parsing and validation. The options are immutable once runtime initialization begins.
 */
final class RistrettoOptionsStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        RistrettoOptions.initializeRuntimeOptionCache();
    }
}
