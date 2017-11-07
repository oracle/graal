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
package com.oracle.truffle.llvm.nodes.op;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMCompareNode extends LLVMExpressionNode {

    public abstract static class LLVMEqNode extends LLVMCompareNode {
        @Specialization
        public boolean eq(boolean val1, boolean val2) {
            return val1 == val2;
        }

        @Specialization
        public boolean eq(byte val1, byte val2) {
            return val1 == val2;
        }

        @Specialization
        public boolean eq(short val1, short val2) {
            return val1 == val2;
        }

        @Specialization
        public boolean eq(int val1, int val2) {
            return val1 == val2;
        }

        @Specialization
        public boolean eq(long val1, long val2) {
            return val1 == val2;
        }

        @Specialization
        public boolean eq(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.compare(val2) == 0;
        }

        @Specialization
        public LLVMI1Vector eq(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a.longValue() == b.longValue());
        }

        @Specialization
        public LLVMI1Vector eq(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a.longValue() == b.longValue());
        }

        @Specialization
        public LLVMI1Vector eq(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a.intValue() == b.intValue());
        }

        @Specialization
        public LLVMI1Vector eq(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> a == b);
        }

        @Specialization
        public LLVMI1Vector eq(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a.shortValue() == b.shortValue());
        }

        @Specialization
        public LLVMI1Vector eq(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a.byteValue() == b.byteValue());
        }
    }

    public abstract static class LLVMNqNode extends LLVMCompareNode {
        @Specialization
        public boolean nq(boolean val1, boolean val2) {
            return val1 != val2;
        }

        @Specialization
        public boolean nq(byte val1, byte val2) {
            return val1 != val2;
        }

        @Specialization
        public boolean nq(short val1, short val2) {
            return val1 != val2;
        }

        @Specialization
        public boolean nq(int val1, int val2) {
            return val1 != val2;
        }

        @Specialization
        public boolean nq(long val1, long val2) {
            return val1 != val2;
        }

        @Specialization
        public boolean nq(boolean val1, LLVMAddress val2) {
            return (val1 ? 1 : 0) != val2.getVal();
        }

        @Specialization
        public boolean nq(long val1, LLVMAddress val2) {
            return val1 != val2.getVal();
        }

        @Specialization
        public boolean nq(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.compare(val2) != 0;
        }

        @Specialization
        public LLVMI1Vector nq(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a.longValue() != b.longValue());
        }

        @Specialization
        public LLVMI1Vector nq(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a.longValue() != b.longValue());
        }

        @Specialization
        public LLVMI1Vector nq(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a.intValue() != b.intValue());
        }

        @Specialization
        public LLVMI1Vector nq(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> a != b);
        }

        @Specialization
        public LLVMI1Vector nq(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a.shortValue() != b.shortValue());
        }

        @Specialization
        public LLVMI1Vector nq(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a.byteValue() != b.byteValue());
        }
    }

    public abstract static class LLVMSltNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector slt(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a < b);
        }

        @Specialization
        public LLVMI1Vector slt(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a < b);
        }

        @Specialization
        public LLVMI1Vector slt(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a < b);
        }

        @Specialization
        public LLVMI1Vector slt(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a < b);
        }

        @Specialization
        public LLVMI1Vector slt(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a < b);
        }

        @Specialization
        public boolean slt(short val1, short val2) {
            return val1 < val2;
        }

        @Specialization
        public boolean slt(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) < 0;
        }

        @Specialization
        public boolean slt(int val1, int val2) {
            return val1 < val2;
        }

        @Specialization
        public boolean slt(long val1, long val2) {
            return val1 < val2;
        }

        @Specialization
        public boolean slt(byte val1, byte val2) {
            return val1 < val2;
        }
    }

    public abstract static class LLVMSleNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector sle(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a <= b);
        }

        @Specialization
        public LLVMI1Vector sle(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a <= b);
        }

        @Specialization
        public LLVMI1Vector sle(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a <= b);
        }

        @Specialization
        public LLVMI1Vector sle(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a <= b);
        }

        @Specialization
        public LLVMI1Vector sle(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a <= b);
        }

        @Specialization
        public boolean sle(short val1, short val2) {
            return val1 <= val2;
        }

        @Specialization
        public boolean sle(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) <= 0;
        }

        @Specialization
        public boolean sle(int val1, int val2) {
            return val1 <= val2;
        }

        @Specialization
        public boolean sle(long val1, long val2) {
            return val1 <= val2;
        }

        @Specialization
        public boolean sle(byte val1, byte val2) {
            return val1 <= val2;
        }
    }

    public abstract static class LLVMSgtNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector sgt(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a > b);
        }

        @Specialization
        public LLVMI1Vector sgt(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a > b);
        }

        @Specialization
        public LLVMI1Vector sgt(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a > b);
        }

        @Specialization
        public LLVMI1Vector sgt(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a > b);
        }

        @Specialization
        public LLVMI1Vector sgt(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a > b);
        }

        @Specialization
        public boolean sgt(short val1, short val2) {
            return val1 > val2;
        }

        @Specialization
        public boolean sgt(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) > 0;
        }

        @Specialization
        public boolean sgt(int val1, int val2) {
            return val1 > val2;
        }

        @Specialization
        public boolean sgt(long val1, long val2) {
            return val1 > val2;
        }

        @Specialization
        public boolean sgt(byte val1, byte val2) {
            return val1 > val2;
        }
    }

    public abstract static class LLVMSgeNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector sge(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> a >= b);
        }

        @Specialization
        public LLVMI1Vector sge(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> a >= b);
        }

        @Specialization
        public LLVMI1Vector sge(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> a >= b);
        }

        @Specialization
        public LLVMI1Vector sge(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> a >= b);
        }

        @Specialization
        public LLVMI1Vector sge(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> a >= b);
        }

        @Specialization
        public boolean sge(short val1, short val2) {
            return val1 >= val2;
        }

        @Specialization
        public boolean sge(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.signedCompare(val2) >= 0;
        }

        @Specialization
        public boolean sge(int val1, int val2) {
            return val1 >= val2;
        }

        @Specialization
        public boolean sge(long val1, long val2) {
            return val1 >= val2;
        }

        @Specialization
        public boolean sge(byte val1, byte val2) {
            return val1 > val2;
        }

    }

    public abstract static class LLVMUgtNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector ugt(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) > 0);
        }

        @Specialization
        public LLVMI1Vector ugt(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) > 0);
        }

        @Specialization
        public LLVMI1Vector ugt(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) > 0);
        }

        @Specialization
        public LLVMI1Vector ugt(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) > 0);
        }

        @Specialization
        public LLVMI1Vector ugt(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) > 0);
        }

        @Specialization
        public LLVMI1Vector ugt(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> a && !b);
        }

        @Specialization
        public boolean ugt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) > 0;
        }

        @Specialization
        public boolean ugt(double val1, double val2) {
            return !(val1 <= val2);
        }

        @Specialization
        public boolean ugt(float val1, float val2) {
            return !(val1 <= val2);
        }

        @Specialization
        public boolean ugt(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) > 0;

        }

        @Specialization
        public boolean ugt(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) > 0;
        }

        @Specialization
        public boolean ugt(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) > 0;

        }

        @Specialization
        public boolean ugt(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) > 0;

        }

        @Specialization
        public boolean ugt(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) > 0;

        }
    }

    public abstract static class LLVMUgeNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector uge(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) >= 0);
        }

        @Specialization
        public LLVMI1Vector uge(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) >= 0);
        }

        @Specialization
        public LLVMI1Vector uge(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) >= 0);
        }

        @Specialization
        public LLVMI1Vector uge(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) >= 0);
        }

        @Specialization
        public LLVMI1Vector uge(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) >= 0);
        }

        @Specialization
        public LLVMI1Vector uge(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> a || a == b);
        }

        @Specialization
        public boolean uge(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) >= 0;
        }

        @Specialization
        public boolean uge(double val1, double val2) {
            return !(val1 < val2);
        }

        @Specialization
        public boolean uge(float val1, float val2) {
            return !(val1 < val2);
        }

        @Specialization
        public boolean uge(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) >= 0;
        }

        @Specialization
        public boolean uge(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) >= 0;
        }

        @Specialization
        public boolean uge(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) >= 0;
        }

        @Specialization
        public boolean uge(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) >= 0;
        }

        @Specialization
        public boolean uge(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) >= 0;
        }
    }

    public abstract static class LLVMUltNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector ult(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) < 0);
        }

        @Specialization
        public LLVMI1Vector ult(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) < 0);
        }

        @Specialization
        public LLVMI1Vector ult(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) < 0);
        }

        @Specialization
        public LLVMI1Vector ult(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) < 0);
        }

        @Specialization
        public LLVMI1Vector ult(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) < 0);
        }

        @Specialization
        public LLVMI1Vector ult(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> !a && b);
        }

        @Specialization
        public boolean ult(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) < 0;
        }

        @Specialization
        public boolean ult(double val1, double val2) {
            return !(val1 >= val2);
        }

        @Specialization
        public boolean ult(float val1, float val2) {
            return !(val1 >= val2);
        }

        @Specialization
        public boolean ult(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        public boolean ult(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) < 0;
        }

        @Specialization
        public boolean ult(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        public boolean ult(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) < 0;
        }

        @Specialization
        public boolean ult(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) < 0;
        }
    }

    public abstract static class LLVMUleNode extends LLVMCompareNode {
        @Specialization
        public LLVMI1Vector ule(LLVMAddressVector left, LLVMAddressVector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) <= 0);
        }

        @Specialization
        public LLVMI1Vector ule(LLVMI64Vector left, LLVMI64Vector right) {
            return left.doCompare(right, (a, b) -> Long.compareUnsigned(a, b) <= 0);
        }

        @Specialization
        public LLVMI1Vector ule(LLVMI16Vector left, LLVMI16Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) <= 0);
        }

        @Specialization
        public LLVMI1Vector ule(LLVMI32Vector left, LLVMI32Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) <= 0);
        }

        @Specialization
        public LLVMI1Vector ule(LLVMI8Vector left, LLVMI8Vector right) {
            return left.doCompare(right, (a, b) -> Integer.compareUnsigned(a, b) <= 0);
        }

        @Specialization
        public LLVMI1Vector ule(LLVMI1Vector left, LLVMI1Vector right) {
            return left.doCompare(right, (a, b) -> !a || a == b);
        }

        @Specialization
        public boolean ule(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) <= 0;
        }

        @Specialization
        public boolean ule(double val1, double val2) {
            return !(val1 > val2);
        }

        @Specialization
        public boolean ule(float val1, float val2) {
            return !(val1 > val2);
        }

        @Specialization
        public boolean ule(short val1, short val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        public boolean ule(LLVMIVarBit val1, LLVMIVarBit val2) {
            return val1.unsignedCompare(val2) <= 0;
        }

        @Specialization
        public boolean ule(int val1, int val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        public boolean ule(long val1, long val2) {
            return Long.compareUnsigned(val1, val2) <= 0;
        }

        @Specialization
        public boolean ule(byte val1, byte val2) {
            return Integer.compareUnsigned(val1, val2) <= 0;
        }
    }

    public abstract static class LLVMOltNode extends LLVMCompareNode {
        @Specialization
        public boolean olt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) < 0;
        }

        @Specialization
        public boolean olt(double val1, double val2) {
            if (val1 < val2) {
                return true;
            } else {
                return false;
            }
        }

        @Specialization
        public boolean olt(float val1, float val2) {
            if (val1 < val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOgtNode extends LLVMCompareNode {
        @Specialization
        public boolean ogt(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) > 0;
        }

        @Specialization
        public boolean ogt(double val1, double val2) {
            if (val1 > val2) {
                return true;
            } else {
                return false;
            }
        }

        @Specialization
        public boolean ogt(float val1, float val2) {
            if (val1 > val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOgeNode extends LLVMCompareNode {
        @Specialization
        public boolean oge(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) >= 0;
        }

        @Specialization
        public boolean oge(double val1, double val2) {
            if (val1 >= val2) {
                return true;
            } else {
                return false;
            }
        }

        @Specialization
        public boolean oge(float val1, float val2) {
            if (val1 >= val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOleNode extends LLVMCompareNode {
        @Specialization
        public boolean ole(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) <= 0;
        }

        @Specialization
        public boolean ole(double val1, double val2) {
            if (val1 <= val2) {
                return true;
            } else {
                return false;
            }
        }

        @Specialization
        public boolean executeI1(float val1, float val2) {
            if (val1 <= val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOeqNode extends LLVMCompareNode {
        @Specialization
        public boolean oeq(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) == 0;
        }

        @Specialization
        public boolean oeq(double val1, double val2) {
            if (val1 == val2) {
                return true;
            } else {
                return false;
            }
        }

        @Specialization
        public boolean oeq(float val1, float val2) {
            if (val1 == val2) {
                assert areOrdered(val1, val2);
                return true;
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOneNode extends LLVMCompareNode {
        @Specialization
        public boolean one(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2) && LLVM80BitFloat.compare(val1, val2) != 0;
        }

        @Specialization
        public boolean one(double val1, double val2) {
            if (val1 != val2) {
                return areOrdered(val1, val2);
            } else {
                return false;
            }
        }

        @Specialization
        public boolean one(float val1, float val2) {
            if (val1 != val2) {
                return areOrdered(val1, val2);
            } else {
                return false;
            }
        }
    }

    public abstract static class LLVMOrdNode extends LLVMCompareNode {
        @Specialization
        public boolean ord(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return LLVM80BitFloat.areOrdered(val1, val2);
        }

        @Specialization
        public boolean ord(double val1, double val2) {
            return areOrdered(val1, val2);
        }

        @Specialization
        public boolean ord(float val1, float val2) {
            return areOrdered(val1, val2);
        }
    }

    public abstract static class LLVMUeqNode extends LLVMCompareNode {
        @Specialization
        public boolean ueq(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) == 0;
        }

        @Specialization
        public boolean ueq(double val1, double val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }

        @Specialization
        public boolean ueq(float val1, float val2) {
            return !areOrdered(val1, val2) || val1 == val2;
        }
    }

    public abstract static class LLVMUneNode extends LLVMCompareNode {
        @Specialization
        public boolean une(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2) || LLVM80BitFloat.compare(val1, val2) != 0;
        }

        @Specialization
        public boolean une(double val1, double val2) {
            return !(val1 == val2);
        }

        @Specialization
        public boolean une(float val1, float val2) {
            return !(val1 == val2);
        }
    }

    public abstract static class LLVMUnoNode extends LLVMCompareNode {
        @Specialization
        public boolean uno(LLVM80BitFloat val1, LLVM80BitFloat val2) {
            return !LLVM80BitFloat.areOrdered(val1, val2);
        }

        @Specialization
        public boolean uno(double val1, double val2) {
            return !areOrdered(val1, val2);
        }

        @Specialization
        public boolean uno(float val1, float val2) {
            return !areOrdered(val1, val2);
        }
    }

    private static boolean areOrdered(double v1, double v2) {
        return !Double.isNaN(v1) && !Double.isNaN(v2);
    }

    private static boolean areOrdered(float v1, float v2) {
        return !Float.isNaN(v1) && !Float.isNaN(v2);
    }

}
