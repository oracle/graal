/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core;

import jdk.graal.compiler.core.CompilationWrapper.ExceptionAction;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;

/**
 * Options related to {@link GraalCompiler}.
 */
public class GraalCompilerOptions {

    // @formatter:off
    @Option(help = "Print an informational line to the console for each completed compilation.", type = OptionType.Debug, stability = OptionStability.STABLE)
    public static final OptionKey<Boolean> PrintCompilation = new OptionKey<>(false);
    @Option(help = "Pattern for method(s) that will trigger an exception when compiled. " +
                   "This option exists to test handling compilation crashes gracefully. " +
                   "See the MethodFilter option for the pattern syntax. A ':Bailout' " +
                   "suffix will raise a bailout exception and a ':PermanentBailout' " +
                   "suffix will raise a permanent bailout exception.", type = OptionType.Debug)
    public static final OptionKey<String> CrashAt = new OptionKey<>(null);
    @Option(help = "Treats compilation bailouts as compilation failures.", type = OptionType.User, stability = OptionStability.STABLE)
    public static final OptionKey<Boolean> CompilationBailoutAsFailure = new OptionKey<>(false);
    @Option(help = "file:doc-files/CompilationFailureActionHelp.txt", type = OptionType.User, stability = OptionStability.STABLE)
    public static final EnumOptionKey<ExceptionAction> CompilationFailureAction = new EnumOptionKey<>(ExceptionAction.Silent);
    @Option(help = "Specifies the maximum number of compilation failures to handle with the action specified by " +
                   "CompilationFailureAction before changing to a less verbose action. " +
                   "This does not apply to the ExitVM action..", type = OptionType.User)
    public static final OptionKey<Integer> MaxCompilationProblemsPerAction = new OptionKey<>(2);
    @Option(help = "Specifies the compilation failure rate that indicates a systemic compilation problem (and a resulting warning). " +
                   "The value is made absolute and clamped to produce P, a value between 0 and 100. " +
                   "Systemic failure is detected if the percentage of failing compilations in a sliding time window >= P. " +
                   "A negative value will cause the VM to exit after issuing the warning. Set to 0 to disable systemic compilation problem detection.", type = OptionType.User)
    public static final OptionKey<Integer> SystemicCompilationFailureRate = new OptionKey<>(1);
    @Option(help = "The number of seconds by which to slow down each compilation. The compilations slowed down " +
                   "can be restricted with MethodFilter. This option exists to test the compilation watchdog.", type = OptionType.Debug)
    public static final OptionKey<Integer> InjectedCompilationDelay = new OptionKey<>(0);
    // @formatter:on
}
