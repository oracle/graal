/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.factories;

import java.util.HashMap;
import java.util.Map;

import com.oracle.graal.replacements.StandardGraphBuilderPlugins;
import com.oracle.graal.replacements.amd64.AMD64MathSubstitutions;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMAbortFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCallocFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMExitFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMMallocFactory;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;

public class LLVMRuntimeIntrinsicFactory {

    public static Map<String, NodeFactory<? extends LLVMNode>> getFunctionSubstitutionFactories(LLVMOptimizationConfiguration optConfig) {

        Map<String, NodeFactory<? extends LLVMNode>> intrinsics = new HashMap<>();
        // Fortran
        intrinsics.put("@_gfortran_abort", LLVMAbortFactory.getInstance());

        // C
        intrinsics.put("@abort", LLVMAbortFactory.getInstance());
        intrinsics.put("@exit", LLVMExitFactory.getInstance());

        if (optConfig.intrinsifyCLibraryFunctions()) {
            intrinsifyCFunctions(intrinsics);
        }
        return intrinsics;
    }

    /**
     * Intrinsifications of functions (e.g. the C function <code>sin</code>) especially make sense
     * when Graal intrinsifies the corresponding Java method calls, e.g.
     * {@link java.lang.Math#sin(double)}. Currently, the Graal intrinsifications for some
     * trigonometric functions in {@link AMD64MathSubstitutions} are still twice as slow as their C
     * counterparts. Hence, only the C standard library <code>sqrt</code> is intrinsified, since its
     * Graal intrinsification in {@link StandardGraphBuilderPlugins} is implemented as efficient as
     * the C function.
     */
    private static void intrinsifyCFunctions(Map<String, NodeFactory<? extends LLVMNode>> intrinsics) {
        intrinsics.put("@sqrt", LLVMSqrtFactory.getInstance());

        // other libraries
        intrinsics.put("@malloc", LLVMMallocFactory.getInstance());
        intrinsics.put("@free", LLVMFreeFactory.getInstance());
        intrinsics.put("@calloc", LLVMCallocFactory.getInstance());
    }

}
