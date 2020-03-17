/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.sulong;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIntrinsicRootNode.LLVMIntrinsicExpressionNode;
import com.oracle.truffle.llvm.runtime.SulongStackTrace;
import com.oracle.truffle.llvm.runtime.SulongStackTrace.Element;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public abstract class LLVMPrintStackTrace extends LLVMIntrinsic {
    @TruffleBoundary
    @Specialization
    protected Object doOp() {
        SulongStackTrace trace = getStackTrace(this, "__sulong_print_stacktrace", true);
        List<Element> elements = trace.getTrace();
        System.err.println("C stack trace:");
        for (Element element : elements) {
            System.err.print(element);
        }
        return LLVMNativePointer.createNull();
    }

    // method can be used for debugging
    public static SulongStackTrace getStackTrace(LLVMNode node) {
        return getStackTrace(node, "", false);
    }

    private static SulongStackTrace getStackTrace(LLVMNode node, String message, boolean filterCurrentLocation) {
        Throwable t = new CThrowable(node, message);
        List<TruffleStackTraceElement> ctrace = TruffleStackTrace.getStackTrace(t);

        SulongStackTrace trace = new SulongStackTrace(message);
        for (int i = 0; i < ctrace.size(); i++) {
            TruffleStackTraceElement element = ctrace.get(i);
            if (filterCurrentLocation && element.getLocation() == node) {
                assert i == 0;
                continue;
            }
            fillStackTrace(trace, element.getLocation());
        }
        return trace;
    }

    private static void fillStackTrace(SulongStackTrace stackTrace, Node node) {
        LLVMBasicBlockNode block = NodeUtil.findParent(node, LLVMBasicBlockNode.class);
        LLVMFunctionStartNode f = NodeUtil.findParent(node, LLVMFunctionStartNode.class);

        if (block == null || f == null) {
            LLVMIntrinsicExpressionNode intrinsic = NodeUtil.findParent(node, LLVMIntrinsicExpressionNode.class);
            if (intrinsic != null) {
                stackTrace.addStackTraceElement(intrinsic.toString(), null, null);
            }
            return;
        }

        LLVMSourceLocation location = null;
        if (node instanceof LLVMInstrumentableNode) {
            location = ((LLVMInstrumentableNode) node).getSourceLocation();
        }
        if (location == null) {
            location = block.getSourceLocation();
        }
        if (location != null) {
            stackTrace.addStackTraceElement(f.getOriginalName(), location, f.getBcName(), f.getBcSource().getName(), blockName(block));
            return;
        }

        SourceSection s = node.getSourceSection();
        if (s == null) {
            s = f.getSourceSection();
        }

        if (s == null) {
            stackTrace.addStackTraceElement(f.getBcName(), f.getBcSource().getName(), blockName(block));
        } else {
            location = LLVMSourceLocation.createUnknown(s);
            stackTrace.addStackTraceElement(f.getOriginalName(), location, f.getBcName(), f.getBcSource().getName(), blockName(block));
        }
    }

    private static String blockName(LLVMBasicBlockNode block) {
        CompilerAsserts.neverPartOfCompilation();
        int blockId = block.getBlockId();
        String blockName = block.getBlockName();
        return String.format("id: %d name: %s", blockId, blockName == null ? "N/A" : blockName);
    }

    @SuppressWarnings("serial")
    private static class CThrowable extends Throwable implements TruffleException {
        private Node node;

        CThrowable(Node node, String message) {
            super(message);
            this.node = node;
        }

        @Override
        public Node getLocation() {
            return node;
        }
    }
}
