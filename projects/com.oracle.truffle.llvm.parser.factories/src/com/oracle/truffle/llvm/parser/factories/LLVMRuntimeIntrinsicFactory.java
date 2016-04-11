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

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMAbortFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMACosFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMASinFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATanFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCosFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanhFactory;
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
            // math.h
            intrinsics.put("@acos", LLVMACosFactory.getInstance());
            intrinsics.put("@asin", LLVMASinFactory.getInstance());
            intrinsics.put("@atan", LLVMATanFactory.getInstance());
            intrinsics.put("@cos", LLVMCosFactory.getInstance());
            intrinsics.put("@exp", LLVMExpFactory.getInstance());
            intrinsics.put("@log", LLVMLogFactory.getInstance());
            intrinsics.put("@sqrt", LLVMSqrtFactory.getInstance());
            intrinsics.put("@sin", LLVMSinFactory.getInstance());
            intrinsics.put("@tan", LLVMTanFactory.getInstance());
            intrinsics.put("@tanh", LLVMTanhFactory.getInstance());

            // other libraries
            intrinsics.put("@malloc", LLVMMallocFactory.getInstance());
            intrinsics.put("@free", LLVMFreeFactory.getInstance());
            intrinsics.put("@calloc", LLVMCallocFactory.getInstance());
        }
        return intrinsics;
    }

}
