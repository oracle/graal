/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle;

import com.oracle.graal.options.*;

/**
 * Options for the Truffle compiler.
 */
public class TruffleCompilerOptions {

    // @formatter:off
    // configuration
    /**
     * Instructs the Truffle Compiler to compile call targets only if their name contains at least one element of a comma-separated list of includes.
     * Excludes are prefixed with a tilde (~).
     *
     * The format in EBNF:
     * <pre>
     * CompileOnly = Element, { ',', Element } ;
     * Element = Include | '~' Exclude ;
     * </pre>
     */
    @Option(help = "")
    public static final OptionValue<String> TruffleCompileOnly = new OptionValue<>(null);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleCompilationThreshold = new OptionValue<>(1000);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleMinInvokeThreshold = new OptionValue<>(3);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInvalidationReprofileCount = new OptionValue<>(3);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleReplaceReprofileCount = new OptionValue<>(10);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInliningReprofileCount = new OptionValue<>(100);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleFunctionInlining = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleGraphMaxNodes = new OptionValue<>(30000);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInliningMaxRecursiveDepth = new OptionValue<>(2);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInliningMaxCallerSize = new OptionValue<>(2250);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInliningMaxCalleeSize = new OptionValue<>(250);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleInliningTrivialSize = new OptionValue<>(10);
    @Option(help = "")
    public static final OptionValue<Double> TruffleInliningMinFrequency = new OptionValue<>(0.3);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleSplittingEnabled = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleSplittingMaxCalleeSize = new OptionValue<>(50);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleUseTimeForCompilationDecision = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleCompilationDecisionTime = new OptionValue<>(100);
    @Option(help = "")
    public static final OptionValue<Integer> TruffleMaxCompilationCacheSize = new OptionValue<>(512);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCompilationDecisionTimePrintFail = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleBackgroundCompilation = new OptionValue<>(true);

    // tracing
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilation = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilationDetails = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleExpansion = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleExpansionSource = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCacheDetails = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleCompilationExceptions = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCompilationExceptionsAreFatal = new OptionValue<>(true);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleInlining = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleInliningTree = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TraceTruffleInliningDetails = new OptionValue<>(false);
    @Option(help = "")
    public static final OptionValue<Boolean> TruffleCallTargetProfiling = new StableOptionValue<>(false);
    // @formatter:on
}
