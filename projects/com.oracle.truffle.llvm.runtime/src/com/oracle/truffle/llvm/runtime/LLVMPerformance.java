/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

public final class LLVMPerformance {

    private static final int STACK_TRACE_SIZE = 3;

    public abstract static class LLVMPerformanceNode extends Node {
        public abstract void warn();
    }

    public static LLVMPerformanceNode getPerformanceNode(boolean countInvocations) {
        if (LLVMOptions.DEBUG.tracePerformanceWarnings()) {
            return countInvocations ? new LLVMInvocationCountPerformanceNode() : new LLVMPerformanceWarningNode();
        } else {
            return new LLVMNopPerformanceNode();
        }
    }

    private static final class LLVMNopPerformanceNode extends LLVMPerformanceNode {

        @Override
        public void warn() {
            // nop
        }
    }

    private static final class LLVMInvocationCountPerformanceNode extends LLVMPerformanceNode {

        private int counter = 0;

        @Override
        public void warn() {
            counter++;
            LLVMPerformance.warn(this.getParent(), null, counter);
        }
    }

    private static final class LLVMPerformanceWarningNode extends LLVMPerformanceNode {

        @Override
        public void warn() {
            LLVMPerformance.warn(this.getParent(), null, -1);
        }
    }

    public static void warn(Node node) {
        warn(node, null);
    }

    public static void warn(Node node, String info) {
        if (LLVMOptions.DEBUG.tracePerformanceWarnings() && CompilerDirectives.inCompiledCode()) {
            printWarning(node, info, -1);
        }
    }

    private static void warn(Node node, String info, int invocationCount) {
        if (LLVMOptions.DEBUG.tracePerformanceWarnings() && CompilerDirectives.inCompiledCode()) {
            printWarning(node, info, invocationCount);
        }
    }

    @TruffleBoundary
    private static void printWarning(Node node, String info, int invocationCount) {
        // Checkstyle: stop
        System.out.print("[perf] " + (node != null ? node.getClass() : "unwanted code") + " on hot path.");
        if (info != null) {
            System.out.println("  Info: " + info);
        } else {
            System.out.println("  Invocation count =" + invocationCount);
        }
        if (invocationCount > 0) {
            System.out.print("[perf] " + node.getClass() + " on hot path.");
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 0; i < STACK_TRACE_SIZE && i < stackTrace.length; i++) {
            System.out.println("  " + stackTrace[i].toString());
        }
        if (LLVMOptions.DEBUG.performanceWarningsAreFatal()) {
            throw new AssertionError("Fatal Performance Warning");
        }
        // Checkstyle: resume
    }

}
