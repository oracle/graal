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
package com.oracle.truffle.llvm.nodes.op.compare;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVM80BitFloatCompareNode extends LLVMExpressionNode {

    public abstract static class LLVM80BitFloatOltNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) < 0;
        }
    }

    public abstract static class LLVM80BitFloatOgtNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) > 0;
        }
    }

    public abstract static class LLVM80BitFloatOgeNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) >= 0;
        }
    }

    public abstract static class LLVM80BitFloatOleNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) <= 0;
        }
    }

    public abstract static class LLVM80BitFloatOeqNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) == 0;
        }
    }

    public abstract static class LLVM80BitFloatOneNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) != 0;
        }
    }

    public abstract static class LLVM80BitFloatOrdNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2);
        }
    }

    public abstract static class LLVM80BitFloatUeqNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) == 0;
        }
    }

    public abstract static class LLVM80BitFloatUgtNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) > 0;
        }
    }

    public abstract static class LLVM80BitFloatUgeNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) >= 0;
        }
    }

    public abstract static class LLVM80BitFloatUleNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) <= 0;
        }
    }

    public abstract static class LLVM80BitFloatUltNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) < 0;
        }
    }

    public abstract static class LLVM80BitFloatUneNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) != 0;
        }
    }

    public abstract static class LLVM80BitFloatUnoNode extends LLVM80BitFloatCompareNode {
        @Specialization
        public boolean executeI1(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2);
        }
    }

}
