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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;

public abstract class LLVMMainFunctionReturnValueRootNode extends RootNode {

    final RootCallTarget rootCallTarget;

    protected LLVMMainFunctionReturnValueRootNode(RootCallTarget rootCallTarget) {
        super(LLVMLanguage.class, null, new FrameDescriptor());
        this.rootCallTarget = rootCallTarget;
    }

    public static class LLVMMainFunctionReturnI1RootNode extends LLVMMainFunctionReturnValueRootNode {

        public LLVMMainFunctionReturnI1RootNode(RootCallTarget rootCallTarget) {
            super(rootCallTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return ((Boolean) rootCallTarget.call()).booleanValue() ? 1 : 0;
        }

    }

    public static class LLVMMainFunctionReturnIVarBitRootNode extends LLVMMainFunctionReturnValueRootNode {

        public LLVMMainFunctionReturnIVarBitRootNode(RootCallTarget rootCallTarget) {
            super(rootCallTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return ((LLVMIVarBit) rootCallTarget.call()).getIntValue();
        }

    }

    public static class LLVMMainFunctionReturnNumberRootNode extends LLVMMainFunctionReturnValueRootNode {

        public LLVMMainFunctionReturnNumberRootNode(RootCallTarget rootCallTarget) {
            super(rootCallTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return ((Number) rootCallTarget.call()).intValue();
        }

    }

    public static class LLVMMainFunctionReturnVoidRootNode extends LLVMMainFunctionReturnValueRootNode {

        private static final int VOID_RET_VALUE = 0;

        public LLVMMainFunctionReturnVoidRootNode(RootCallTarget rootCallTarget) {
            super(rootCallTarget);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            rootCallTarget.call();
            return VOID_RET_VALUE;
        }

    }

}
