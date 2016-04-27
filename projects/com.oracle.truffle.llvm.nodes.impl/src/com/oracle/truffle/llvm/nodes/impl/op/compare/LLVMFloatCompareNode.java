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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;

@NodeChildren({@NodeChild(type = LLVMFloatNode.class), @NodeChild(type = LLVMFloatNode.class)})
public abstract class LLVMFloatCompareNode extends LLVMI1Node {

    @ExplodeLoop
    private static boolean areOrdered(float... vals) {
        CompilerAsserts.compilationConstant(vals.length);
        for (float val : vals) {
            if (Float.isNaN(val)) {
                return false;
            }
        }
        return true;
    }

    public abstract static class LLVMFloatOltNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 < val2;
        }
    }

    public abstract static class LLVMFloatOgtNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 > val2;
        }
    }

    public abstract static class LLVMFloatOgeNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 >= val2;
        }
    }

    public abstract static class LLVMFloatOleNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 <= val2;
        }
    }

    public abstract static class LLVMFloatOeqNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 == val2;
        }
    }

    public abstract static class LLVMFloatOneNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2) && val1 != val2;
        }
    }

    public abstract static class LLVMFloatOrdNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return areOrdered(val1, val2);
        }
    }

    public abstract static class LLVMFloatUeqNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }
    }

    public abstract static class LLVMFloatUgtNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 > val2;
        }
    }

    public abstract static class LLVMFloatUgeNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 >= val2;
        }
    }

    public abstract static class LLVMFloatUleNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 <= val2;
        }
    }

    public abstract static class LLVMFloatUltNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 < val2;
        }
    }

    public abstract static class LLVMFloatUneNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 != val2;
        }
    }

    public abstract static class LLVMFloatUnoNode extends LLVMFloatCompareNode {
        @Specialization
        public boolean executeI1(float val1, float val2) {
            return !areOrdered(val1, val2);
        }
    }

}
