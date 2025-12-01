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
package com.oracle.svm.interpreter.ristretto;

import com.oracle.svm.core.option.RuntimeOptionKey;

import jdk.graal.compiler.options.Option;

public class RistrettoRuntimeOptions {
    @Option(help = "Use the Graal JIT compiler at runtime to compile bytecodes.")//
    public static final RuntimeOptionKey<Boolean> JITEnableCompilation = new RuntimeOptionKey<>(true);

    @Option(help = "Number of invocations before compilation is triggered on a method.")//
    public static final RuntimeOptionKey<Integer> JITCompilerInvocationThreshold = new RuntimeOptionKey<>(1000);

    @Option(help = "Number of threads to use for Graal JIT compilation.")//
    public static final RuntimeOptionKey<Integer> JITCompilerThreadCount = new RuntimeOptionKey<>(1);

    @Option(help = "Trace decisions about when to compile what.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilationQueuing = new RuntimeOptionKey<>(false);

    @Option(help = "Trace counter values during profiling.")//
    public static final RuntimeOptionKey<Boolean> JITTraceProfilingIncrements = new RuntimeOptionKey<>(false);

    @Option(help = "Trace compilation events.")//
    public static final RuntimeOptionKey<Boolean> JITTraceCompilation = new RuntimeOptionKey<>(false);
}
