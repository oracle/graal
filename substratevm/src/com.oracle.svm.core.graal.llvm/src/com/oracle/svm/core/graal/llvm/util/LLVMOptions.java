/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.util;

import java.util.Arrays;
import java.util.List;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

import com.oracle.svm.core.option.HostedOptionKey;

public class LLVMOptions {

    @Option(help = "Include debugging info in the generated image (for LLVM backend).", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> IncludeLLVMDebugInfo = new HostedOptionKey<>(0);

    @Option(help = "Dump contents of the generated stackmap to the specified file", type = OptionType.Debug)//
    public static final HostedOptionKey<String> DumpLLVMStackMap = new HostedOptionKey<>(null);

    @Option(help = "Maximum size of batches used for LLVM compilation. 0 means a single batch, 1 means all functions separately", type = OptionType.Debug)//
    public static final HostedOptionKey<Integer> LLVMMaxFunctionsPerBatch = new HostedOptionKey<>(1000);

    @Option(help = "Path to a custom ld binary for LLVM linking")//
    public static final HostedOptionKey<String> CustomLD = new HostedOptionKey<>("");

    @Option(help = "Enable LLVM bitcode optimizations")//
    public static final HostedOptionKey<Boolean> BitcodeOptimizations = new HostedOptionKey<>(false);

    @Option(help = "Return special registers from functions in LLVM bitcode. This may decrease performance if the target doesn't support returning multiple values from a function.")//
    public static final HostedOptionKey<Boolean> ReturnSpecialRegs = new HostedOptionKey<>(true);

    public static final List<HostedOptionKey<?>> allOptions = Arrays.asList(IncludeLLVMDebugInfo, DumpLLVMStackMap, LLVMMaxFunctionsPerBatch, CustomLD, BitcodeOptimizations, ReturnSpecialRegs);
}
