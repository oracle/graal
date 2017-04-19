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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMDoubleCompareNode extends LLVMExpressionNode {

    @ExplodeLoop
    private static boolean areOrdered(double... vals) {
        CompilerAsserts.compilationConstant(vals.length);
        for (double val : vals) {
            if (Double.isNaN(val)) {
                return false;
            }
        }
        return true;
    }

    public abstract static class LLVMDoubleOltNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 < val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOgtNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 > val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOgeNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 >= val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOleNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 <= val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOeqNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 == val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOneNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            if (val1 != val2) {
                return areOrdered(val1, val2);
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMDoubleOrdNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return areOrdered(val1, val2);
        }
    }

    public abstract static class LLVMDoubleUeqNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }
    }

    public abstract static class LLVMDoubleUgtNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !(val1 <= val2);
        }
    }

    public abstract static class LLVMDoubleUgeNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !(val1 < val2);
        }
    }

    public abstract static class LLVMDoubleUleNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !(val1 > val2);
        }
    }

    public abstract static class LLVMDoubleUltNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !(val1 >= val2);
        }
    }

    public abstract static class LLVMDoubleUneNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !(val1 == val2);
        }
    }

    public abstract static class LLVMDoubleUnoNode extends LLVMDoubleCompareNode {
        @Specialization
        public boolean executeI1(double val1, double val2) {
            return !areOrdered(val1, val2);
        }
    }

}
