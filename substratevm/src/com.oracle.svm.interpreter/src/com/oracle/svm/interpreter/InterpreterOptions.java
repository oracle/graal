/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport;
import com.oracle.svm.core.option.RuntimeOptionValidationSupport.RuntimeOptionValidation;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.pltgot.PLTGOTOptions;
import com.oracle.svm.shared.option.SubstrateOptionsParser;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;

public class InterpreterOptions {
    @Option(help = "Adds support to divert execution from AOT compiled methods to the interpreter at run-time.", type = OptionType.Expert) //
    public static final HostedOptionKey<Boolean> DebuggerWithInterpreter = new HostedOptionKey<>(false) {
        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            super.onValueUpdate(values, oldValue, newValue);
            if (newValue) {
                PLTGOTOptions.EnablePLTGOT.update(values, true);
            }
        }
    };

    @Option(help = "Enables logging at build time related to the interpreter setup", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> InterpreterBuildTimeLogging = new HostedOptionKey<>(false);

    @Option(help = "Path to dump interpreter universe metadata as .class files", type = OptionType.Expert)//
    public static final HostedOptionKey<String> InterpreterDumpClassFiles = new HostedOptionKey<>("");

    // Defaults to false as this has roughly a 2x impact on interpreter dispatch performance.
    @Option(help = "Include interpreter tracing code in image")//
    public static final HostedOptionKey<Boolean> InterpreterTraceSupport = new HostedOptionKey<>(false);

    @Option(help = "Trace interpreter execution")//
    public static final RuntimeOptionKey<Boolean> InterpreterTrace = new RuntimeOptionKey<>(false, InterpreterOptions::validateTraceFlagBuildtime);

    @Option(help = "Enables backdoors in interpreter for testing", type = OptionType.Debug)//
    public static final HostedOptionKey<Boolean> InterpreterBackdoor = new HostedOptionKey<>(false);

    public static boolean interpreterEnabled() {
        return DebuggerWithInterpreter.getValue() || RuntimeClassLoading.isSupported();
    }

    @Platforms(HOSTED_ONLY.class)
    public static void registerInterpreterTraceOptionValidation() {
        RuntimeOptionValidationSupport.singleton().register(new RuntimeOptionValidation<>(InterpreterOptions::validateTraceFlagRuntime, InterpreterTrace));
    }

    @Platforms(HOSTED_ONLY.class)
    private static void validateTraceFlagBuildtime(RuntimeOptionKey<Boolean> optionKey) {
        if (optionKey.getValue() && !InterpreterTraceSupport.getValue()) {
            throw UserError.abort("Option '%s' requires trace support to be enabled via '%s'.", optionKey.getName(), Holder.TRACE_SUPPORT_OPTION);
        }
    }

    private static void validateTraceFlagRuntime(RuntimeOptionKey<Boolean> optionKey) {
        if (InterpreterTraceSupport.getValue()) {
            return;
        }
        if (optionKey.getValue()) {
            throw new IllegalArgumentException("Option '" + optionKey.getName() + "' requires '" + Holder.TRACE_SUPPORT_OPTION + "' to be enabled at build-time.");
        }
    }

    private static final class Holder {
        static final String TRACE_SUPPORT_OPTION = SubstrateOptionsParser.commandArgument(InterpreterTraceSupport, "+");
    }
}
