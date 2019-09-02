/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMAMD64GetFlagNodes {
    private static final byte ZERO = 0;
    private static final byte ONE = 1;

    private LLVMAMD64GetFlagNodes() {
        // private constructor
    }

    @NodeChild("value")
    public abstract static class LLVMAMD64GetFlagNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value) {
            return value ? ONE : ZERO;
        }
    }

    @NodeChild("value")
    public abstract static class LLVMAMD64GetFlagNegNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value) {
            return value ? ZERO : ONE;
        }
    }

    @NodeChild("value1")
    @NodeChild("value2")
    public abstract static class LLVMAMD64GetFlagOrNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value1, boolean value2) {
            return value1 || value2 ? ONE : ZERO;
        }
    }

    @NodeChild("value1")
    @NodeChild("value2")
    public abstract static class LLVMAMD64GetFlagNorNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value1, boolean value2) {
            return !(value1 || value2) ? ONE : ZERO;
        }
    }

    @NodeChild("value1")
    @NodeChild("value2")
    public abstract static class LLVMAMD64GetFlagAndNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value1, boolean value2) {
            return value1 && value2 ? ONE : ZERO;
        }
    }

    @NodeChild("value1")
    @NodeChild("value2")
    public abstract static class LLVMAMD64GetFlagEqualNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value1, boolean value2) {
            return value1 == value2 ? ONE : ZERO;
        }
    }

    @NodeChild("value1")
    @NodeChild("value2")
    public abstract static class LLVMAMD64GetFlagXorNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean value1, boolean value2) {
            return value1 != value2 ? ONE : ZERO;
        }
    }

    /**
     * This is used to implement the {@code setg} and {@code setnle} instructions.
     */
    @NodeChild("zf")
    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64GetFlagGNode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf, boolean sf, boolean of) {
            return !zf && sf == of ? ONE : ZERO;
        }
    }

    /**
     * This is used to implement the {@code setng} and {@code setle} instructions.
     */
    @NodeChild("zf")
    @NodeChild("sf")
    @NodeChild("of")
    public abstract static class LLVMAMD64GetFlagLENode extends LLVMExpressionNode {
        @Specialization
        protected byte doI8(boolean zf, boolean sf, boolean of) {
            return zf || sf != of ? ONE : ZERO;
        }
    }
}
