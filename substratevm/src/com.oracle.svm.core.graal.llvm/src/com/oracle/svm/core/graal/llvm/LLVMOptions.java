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
package com.oracle.svm.core.graal.llvm;

import org.graalvm.compiler.options.Option;
import com.oracle.svm.core.option.HostedOptionKey;
import org.graalvm.compiler.options.OptionType;

public class LLVMOptions {

    @Option(help = "Include debugging info in the generated image (for LLVM backend).")//
    public static final HostedOptionKey<Integer> IncludeLLVMDebugInfo = new HostedOptionKey<>(0);

    @Option(help = "Dump contents of the generated stackmap to the specified file")//
    public static final HostedOptionKey<String> DumpLLVMStackMap = new HostedOptionKey<>(null);

    @Option(help = "How many batches per thread should be used for LLVM compilation. 0 means a single batch, -1 means all functions separately", type = OptionType.Expert)//
    public static final HostedOptionKey<Integer> LLVMBatchesPerThread = new HostedOptionKey<>(1);
}
