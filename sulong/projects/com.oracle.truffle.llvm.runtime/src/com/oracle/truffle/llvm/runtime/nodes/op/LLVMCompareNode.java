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
package com.oracle.truffle.llvm.runtime.nodes.op;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesLongPointer;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAddressEqualsNode.LLVMPointerEqualsNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
@TypeSystemReference(LLVMTypesLongPointer.class)
public abstract class LLVMCompareNode extends LLVMAbstractCompareNode {

    public abstract static class LLVMEqNode extends LLVMCompareNode {
        @Specialization
        protected boolean eq(boolean val1, boolean val2) {
            return val1 == val2;
        }

        @Specialization
        protected boolean eq(byte val1, byte val2) {
            return val1 == val2;
        }

        @Specialization
        protected boolean eq(short val1, short val2) {
            return val1 == val2;
        }

        @Specialization
        protected boolean eq(int val1, int val2) {
            return val1 == val2;
        }

        @Specialization
        protected boolean eqLong(long val1, long val2) {
            return val1 == val2;
        }

        @Specialization(replaces = "eqLong")
        protected boolean eqPointer(LLVMPointer val1, LLVMPointer val2,
                        @Cached LLVMPointerEqualsNode equals) {
            return equals.execute(val1, val2);
        }

        @Specialization
        protected boolean eq(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.isEqual(val2);
        }
    }

    public abstract static class LLVMNeNode extends LLVMCompareNode {
        @Specialization
        protected boolean nq(boolean val1, boolean val2) {
            return val1 != val2;
        }

        @Specialization
        protected boolean nq(byte val1, byte val2) {
            return val1 != val2;
        }

        @Specialization
        protected boolean nq(short val1, short val2) {
            return val1 != val2;
        }

        @Specialization
        protected boolean nq(int val1, int val2) {
            return val1 != val2;
        }

        @Specialization
        protected boolean nqLong(long val1, long val2) {
            return val1 != val2;
        }

        @Specialization(replaces = "nqLong")
        protected boolean nqPointer(LLVMPointer val1, LLVMPointer val2,
                        @Cached LLVMPointerEqualsNode equals) {
            return !equals.execute(val1, val2);
        }

        @Specialization
        protected boolean nq(LLVMIVarBit val1, LLVMIVarBit val2) {
            return !val1.isEqual(val2);
        }
    }

    public abstract static class LLVMSignedLtNode extends LLVMCompareNode {
        @Specialization
        protected boolean slt(short val1, short val2) {
            return val1 < val2;
        }

        @Specialization
        protected boolean slt(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) < 0;
        }

        @Specialization
        protected boolean slt(int val1, int val2) {
            return val1 < val2;
        }

        @Specialization
        protected boolean slt(long val1, long val2) {
            return val1 < val2;
        }

        @Specialization
        protected boolean slt(byte val1, byte val2) {
            return val1 < val2;
        }
    }

    public abstract static class LLVMSignedLeNode extends LLVMCompareNode {
        @Specialization
        protected boolean sle(short val1, short val2) {
            return val1 <= val2;
        }

        @Specialization
        protected boolean sle(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) <= 0;
        }

        @Specialization
        protected boolean sle(int val1, int val2) {
            return val1 <= val2;
        }

        @Specialization
        protected boolean sle(long val1, long val2) {
            return val1 <= val2;
        }

        @Specialization
        protected boolean sle(byte val1, byte val2) {
            return val1 <= val2;
        }
    }

    public abstract static class LLVMUnsignedLtNode extends LLVMCompareNode {
        @Specialization
        protected boolean ult(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        protected boolean ult(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) < 0;
        }

        @Specialization
        protected boolean ult(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        protected boolean ult(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        protected boolean ult(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }
    }

    public abstract static class LLVMUnsignedLeNode extends LLVMCompareNode {
        @Specialization
        protected boolean ule(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        protected boolean ule(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) <= 0;
        }

        @Specialization
        protected boolean ule(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        protected boolean ule(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        protected boolean ule(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }
    }

    public abstract static class LLVMOrderedLtNode extends LLVMCompareNode {
        @Specialization
        protected boolean olt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) < 0;
        }

        @Specialization
        protected boolean olt(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return val1 < val2;
        }

        @Specialization
        protected boolean olt(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            assert !(val1 < val2) || areOrdered(val1, val2);
            return val1 < val2;
        }
    }

    public abstract static class LLVMOrderedGtNode extends LLVMCompareNode {
        @Specialization
        protected boolean ogt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) > 0;
        }

        @Specialization
        protected boolean ogt(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return val1 > val2;
        }

        @Specialization
        protected boolean ogt(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            assert !(val1 > val2) || areOrdered(val1, val2);
            return val1 > val2;
        }
    }

    public abstract static class LLVMOrderedGeNode extends LLVMCompareNode {
        @Specialization
        protected boolean oge(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) >= 0;
        }

        @Specialization
        protected boolean oge(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return val1 >= val2;
        }

        @Specialization
        protected boolean oge(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            assert !(val1 >= val2) || areOrdered(val1, val2);
            return val1 >= val2;
        }
    }

    public abstract static class LLVMOrderedLeNode extends LLVMCompareNode {
        @Specialization
        protected boolean ole(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) <= 0;
        }

        @Specialization
        protected boolean oleDouble(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return val1 <= val2;
        }

        @Specialization
        protected boolean oleFloat(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            assert !(val1 <= val2) || areOrdered(val1, val2);
            return val1 <= val2;
        }
    }

    public abstract static class LLVMOrderedEqNode extends LLVMCompareNode {
        @Specialization
        protected boolean oeq(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) == 0;
        }

        @Specialization
        protected boolean oeq(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return val1 == val2;
        }

        @Specialization
        protected boolean oeq(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            assert !(val1 == val2) || areOrdered(val1, val2);
            return val1 == val2;
        }
    }

    public abstract static class LLVMOrderedNeNode extends LLVMCompareNode {
        @Specialization
        protected boolean one(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) != 0;
        }

        @Specialization
        protected boolean one(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            if (val1 != val2) {
                return areOrdered(val1, val2);
            } else {
                return false;
            }
        }

        @Specialization
        protected boolean one(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            if (val1 != val2) {
                return areOrdered(val1, val2);
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOrderedNode extends LLVMCompareNode {
        @Specialization
        protected boolean ord(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2);
        }

        @Specialization
        protected boolean ord(double val1, double val2) {
            return areOrdered(val1, val2);
        }

        @Specialization
        protected boolean ord(float val1, float val2) {
            return areOrdered(val1, val2);
        }
    }

    public abstract static class LLVMUnorderedLtNode extends LLVMCompareNode {
        @Specialization
        protected boolean ult(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) < 0;
        }

        @Specialization
        protected boolean ult(double val1, double val2) {
            return !(val1 >= val2);
        }

        @Specialization
        protected boolean ult(float val1, float val2) {
            return !(val1 >= val2);
        }
    }

    public abstract static class LLVMUnorderedLeNode extends LLVMCompareNode {
        @Specialization
        protected boolean ule(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) <= 0;
        }

        @Specialization
        protected boolean ule(double val1, double val2) {
            return !(val1 > val2);
        }

        @Specialization
        protected boolean ule(float val1, float val2) {
            return !(val1 > val2);
        }
    }

    public abstract static class LLVMUnorderedGtNode extends LLVMCompareNode {
        @Specialization
        protected boolean ugt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) > 0;
        }

        @Specialization
        protected boolean ugt(double val1, double val2) {
            return !(val1 <= val2);
        }

        @Specialization
        protected boolean ugt(float val1, float val2) {
            return !(val1 <= val2);
        }
    }

    public abstract static class LLVMUnorderedGeNode extends LLVMCompareNode {
        @Specialization
        protected boolean uge(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) >= 0;
        }

        @Specialization
        protected boolean uge(double val1, double val2) {
            return !(val1 < val2);
        }

        @Specialization
        protected boolean uge(float val1, float val2) {
            return !(val1 < val2);
        }
    }

    public abstract static class LLVMUnorderedEqNode extends LLVMCompareNode {
        @Specialization
        protected boolean ueq(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) == 0;
        }

        @Specialization
        protected boolean ueq(double val1, double val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }

        @Specialization
        protected boolean ueq(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }
    }

    public abstract static class LLVMUnorderedNeNode extends LLVMCompareNode {
        @Specialization
        protected boolean une(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) != 0;
        }

        @Specialization
        protected boolean une(double val1, double val2) {
            return doubleCompare(val1, val2);
        }

        private static boolean doubleCompare(double val1, double val2) {
            return !(val1 == val2);
        }

        @Specialization
        protected boolean une(float val1, float val2) {
            return floatCompare(val1, val2);
        }

        private static boolean floatCompare(float val1, float val2) {
            return !(val1 == val2);
        }
    }

    public abstract static class LLVMUnorderedNode extends LLVMCompareNode {
        @Specialization
        protected boolean uno(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2);
        }

        @Specialization
        protected boolean uno(double val1, double val2) {
            return !areOrdered(val1, val2);
        }

        @Specialization
        protected boolean uno(float val1, float val2) {
            return !areOrdered(val1, val2);
        }
    }

    public abstract static class LLVMTrueCmpNode extends LLVMCompareNode {
        @Specialization
        @SuppressWarnings("unused")
        protected boolean op(Object val1, Object val2) {
            return true;
        }
    }

    public abstract static class LLVMFalseCmpNode extends LLVMCompareNode {
        @Specialization
        @SuppressWarnings("unused")
        protected boolean op(Object val1, Object val2) {
            return false;
        }
    }

    private static boolean areOrdered(double v1, double v2) {
        return !Double.isNaN(v1) && !Double.isNaN(v2);
    }

    private static boolean areOrdered(float v1, float v2) {
        return !Float.isNaN(v1) && !Float.isNaN(v2);
    }
}
