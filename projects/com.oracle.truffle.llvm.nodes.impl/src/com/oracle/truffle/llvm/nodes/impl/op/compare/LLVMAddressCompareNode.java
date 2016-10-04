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
package com.oracle.truffle.llvm.nodes.impl.op.compare;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.types.LLVMAddress;

@NodeChildren({@NodeChild(type = LLVMAddressNode.class), @NodeChild(type = LLVMAddressNode.class)})
public abstract class LLVMAddressCompareNode extends LLVMI1Node {

    public abstract static class LLVMAddressUltNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.unsignedLessThan(val2);
        }
    }

    public abstract static class LLVMAddressUgtNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.unsignedGreaterThan(val2);
        }
    }

    public abstract static class LLVMAddressUgeNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.unsignedGreaterEquals(val2);
        }
    }

    public abstract static class LLVMAddressUleNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.unsignedLessEquals(val2);
        }
    }

    public abstract static class LLVMAddressSleNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.signedLessEquals(val2);
        }
    }

    public abstract static class LLVMAddressSltNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.signedLessThan(val2);
        }
    }

    public abstract static class LLVMAddressSgtNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.signedGreaterThan(val2);
        }
    }

    public abstract static class LLVMAddressSgeNode extends LLVMAddressCompareNode {
        @Specialization
        public boolean executeI1(LLVMAddress val1, LLVMAddress val2) {
            return val1.signedGreaterEquals(val2);
        }
    }

}
