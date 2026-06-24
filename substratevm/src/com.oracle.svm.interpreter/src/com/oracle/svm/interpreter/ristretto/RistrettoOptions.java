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

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidationSupport;
import com.oracle.svm.guest.staging.option.RuntimeOptionValidationSupport.RuntimeOptionValidation;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.option.HostedOptionKey;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RistrettoOptions {

    @Option(help = "Use the Graal JIT compiler at runtime to compile bytecodes.")//
    public static final RuntimeOptionKey<Boolean> JITEnableCompilation = new RuntimeOptionKey<>(true);

    @Option(help = "Number of invocations before compilation is triggered on a method.")//
    public static final RuntimeOptionKey<Integer> JITCompilerInvocationThreshold = new RuntimeOptionKey<>(1000);

    @Option(help = "Use on-stack replacement to enter runtime-compiled Ristretto code from interpreted loops.")//
    public static final RuntimeOptionKey<Boolean> JITUseOnStackReplacement = new RuntimeOptionKey<>(true);

    @Option(help = "Number of loop backedges before OSR compilation is triggered for a method and target BCI.")//
    public static final RuntimeOptionKey<Integer> JITCompilerOSRBackedgeThreshold = new RuntimeOptionKey<>(30000, RistrettoOptions::validateOSRBackedgeThreshold);

    @Option(help = "Disable invocation-entry Ristretto JIT compilations while leaving OSR compilations enabled.")//
    public static final RuntimeOptionKey<Boolean> JITDisableRootCompiles = new RuntimeOptionKey<>(false);

    @Option(help = "Comma-separated method-name filters that restrict which methods may be compiled by the Ristretto JIT.")//
    public static final RuntimeOptionKey<String> JITCompileOnly = new RuntimeOptionKey<>("");

    @Option(help = "Number of threads to use for Graal JIT compilation.")//
    public static final RuntimeOptionKey<Integer> JITCompilerThreadCount = new RuntimeOptionKey<>(1);

    @Option(help = "Trace decisions about when to compile what.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilationQueuing = new RuntimeOptionKey<>(false);

    @Option(help = "Trace counter values during profiling.")//
    public static final RuntimeOptionKey<Boolean> JITTraceProfilingIncrements = new RuntimeOptionKey<>(false);

    @Option(help = "Trace compilation events.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilation = new RuntimeOptionKey<>(false);

    @Option(help = "Print stack traces of compiler exceptions.")//
    public static final RuntimeOptionKey<Boolean> JITPrintExceptions = new RuntimeOptionKey<>(false);

    @Option(help = "Periodically dump Ristretto compiler statistics to stdout.")//
    public static final HostedOptionKey<Boolean> JITTraceCompilerStatistics = new HostedOptionKey<>(false);

    @Option(help = "Period, in seconds, between Ristretto compiler statistics dumps.")//
    public static final HostedOptionKey<Integer> JITTraceCompilerStatisticsPeriodSeconds = new HostedOptionKey<>(60, RistrettoOptions::validateCompilerStatisticsPeriod);

    private static void validateCompilerStatisticsPeriod(HostedOptionKey<Integer> option) {
        if (option.getValue() <= 0) {
            throw UserError.invalidOptionValue(option, option.getValue(), "The value must be positive.");
        }
    }

    public static int getJITCompilerOSRBackedgeThreshold() {
        return JITCompilerOSRBackedgeThreshold.getValue();
    }

    @Platforms(HOSTED_ONLY.class)
    public static void registerRuntimeOptionValidations() {
        RuntimeOptionValidationSupport.singleton().register(new RuntimeOptionValidation<>(RistrettoOptions::validateOSRBackedgeThreshold, JITCompilerOSRBackedgeThreshold));
    }

    private static void validateOSRBackedgeThreshold(RuntimeOptionKey<Integer> optionKey) {
        validateOSRBackedgeThresholdValue(optionKey.getValue());
    }

    private static void validateOSRBackedgeThresholdValue(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("Option '" + JITCompilerOSRBackedgeThreshold.getName() + "' must be greater than or equal to 0.");
        }
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

    @Fold
    public static boolean useDeoptimization() {
        return ConcealedOptions.JITUseDeoptimization.getValue();
    }
}
