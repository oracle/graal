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
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAbortFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMAtExitFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMAbsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCeilFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFloorFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLAbsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLog10Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMRintFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMExitFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMSignalFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrCmpFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.c.LLVMTruffleOnlyIntrinsicsFactory.LLVMStrlenFactory;

/**
 * This class creates intrinsic functions and is designed to be inherited.
 *
 */
public class LLVMRuntimeIntrinsicFactory {

    static Map<String, NodeFactory<? extends LLVMExpressionNode>> getFunctionSubstitutionFactories() {
        return new LLVMRuntimeIntrinsicFactory().getFactories();
    }

    protected final Map<String, NodeFactory<? extends LLVMExpressionNode>> intrinsics;

    protected LLVMRuntimeIntrinsicFactory() {
        intrinsics = new HashMap<>();
    }

    protected Map<String, NodeFactory<? extends LLVMExpressionNode>> getFactories() {
        intrinsifyAbortIntrinsics();
        intrinsifyMathFunctions();
        intrinsifyTruffleOnlyIntrinsics();
        return intrinsics;
    }

    /**
     *
     * This method intrinsifies functions that exit the process such as <code>abort</code> or
     * <code>exit</code> in C or <code>_gfortran_abort</code> in Fortran. Not intrinsifying these
     * functions and directly executing the JVM process upon their invocation would not desirable,
     * since cleanups or exit functions (such as shared library destructors or functions registered
     * by <code>atexit</code>) could not be executed. Additionally, a failing JUnit test would then
     * also exit the process instead of executing the remaining test cases.
     *
     */
    protected void intrinsifyAbortIntrinsics() {
        // Fortran
        intrinsics.put("@_gfortran_abort", LLVMAbortFactory.getInstance());
        // C
        intrinsics.put("@abort", LLVMAbortFactory.getInstance());
        intrinsics.put("@exit", LLVMExitFactory.getInstance());
        intrinsics.put("@atexit", LLVMAtExitFactory.getInstance());
        intrinsics.put("@signal", LLVMSignalFactory.getInstance());
    }

    /**
     * Intrinsifies functions that provide an implementation for <code>TruffleObject</code>s but use
     * the Graal NFI if the arguments are not <code>TruffleObject</code>s.
     */
    protected void intrinsifyTruffleOnlyIntrinsics() {
        intrinsics.put("@strlen", LLVMStrlenFactory.getInstance());
        intrinsics.put("@strcmp", LLVMStrCmpFactory.getInstance());
    }

    /**
     * This method intrinsifies functions from the <code>math.h</code> header file of the C standard
     * library. Intrinsifications of these functions (e.g. the C function <code>exp</code>)
     * especially make sense when Graal intrinsifies the corresponding Java method calls, e.g.
     * {@link java.lang.Math#exp(double)}.
     */
    protected void intrinsifyMathFunctions() {
        intrinsics.put("@sqrt", LLVMSqrtFactory.getInstance());
        intrinsics.put("@log", LLVMLogFactory.getInstance());
        intrinsics.put("@log10", LLVMLog10Factory.getInstance());
        intrinsics.put("@rint", LLVMRintFactory.getInstance());
        intrinsics.put("@ceil", LLVMCeilFactory.getInstance());
        intrinsics.put("@floor", LLVMFloorFactory.getInstance());
        intrinsics.put("@abs", LLVMAbsFactory.getInstance());
        intrinsics.put("@labs", LLVMLAbsFactory.getInstance());
        intrinsics.put("@fabs", LLVMFAbsFactory.getInstance());
        intrinsics.put("@pow", LLVMPowFactory.getInstance());
        intrinsics.put("@exp", LLVMExpFactory.getInstance());
    }

}
