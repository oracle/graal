/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.op.arith.floating;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMArithmetic.LLVMArithmeticOpNode;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactoryFactory.CachedAddNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactoryFactory.CachedDivNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactoryFactory.CachedMulNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactoryFactory.CachedRemNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.arith.floating.LLVMArithmeticFactoryFactory.CachedSubNodeGen;

public abstract class LLVMArithmeticFactory {
    public static LLVMArithmeticOpNode createAddNode() {
        return CachedAddNodeGen.create();
    }

    public static LLVMArithmeticOpNode createSubNode() {
        return CachedSubNodeGen.create();
    }

    public static LLVMArithmeticOpNode createMulNode() {
        return CachedMulNodeGen.create();
    }

    public static LLVMArithmeticOpNode createDivNode() {
        return CachedDivNodeGen.create();
    }

    public static LLVMArithmeticOpNode createRemNode() {
        return CachedRemNodeGen.create();
    }

    abstract static class CachedAddNode extends LLVMArithmeticOpNode {
        @Override
        public boolean canCompute(Object x, Object y) {
            return x instanceof LLVMArithmetic && y instanceof LLVMArithmetic;
        }

        @Specialization(guards = "impl.canCompute(x, y)")
        public LLVMArithmetic execute(LLVMArithmetic x, LLVMArithmetic y,
                        @Cached("createNode(x, y)") LLVMArithmeticOpNode impl) {
            return impl.execute(x, y);
        }

        LLVMArithmeticOpNode createNode(Object x, Object y) {
            if (x instanceof LLVMArithmetic && x.getClass() == y.getClass()) {
                return ((LLVMArithmetic) x).createAddNode();
            } else {
                throw new AssertionError("unsupported operand types: " + x.getClass() + ", " + y.getClass());
            }
        }
    }

    abstract static class CachedSubNode extends LLVMArithmeticOpNode {
        @Override
        public boolean canCompute(Object x, Object y) {
            return x instanceof LLVMArithmetic && y instanceof LLVMArithmetic;
        }

        @Specialization(guards = "impl.canCompute(x, y)")
        public LLVMArithmetic execute(LLVMArithmetic x, LLVMArithmetic y,
                        @Cached("createNode(x, y)") LLVMArithmeticOpNode impl) {
            return impl.execute(x, y);
        }

        LLVMArithmeticOpNode createNode(Object x, Object y) {
            if (x instanceof LLVMArithmetic && x.getClass() == y.getClass()) {
                return ((LLVMArithmetic) x).createSubNode();
            } else {
                throw new AssertionError("unsupported operand types: " + x.getClass() + ", " + y.getClass());
            }
        }
    }

    abstract static class CachedMulNode extends LLVMArithmeticOpNode {
        @Override
        public boolean canCompute(Object x, Object y) {
            return x instanceof LLVMArithmetic && y instanceof LLVMArithmetic;
        }

        @Specialization(guards = "impl.canCompute(x, y)")
        public LLVMArithmetic execute(LLVMArithmetic x, LLVMArithmetic y,
                        @Cached("createNode(x, y)") LLVMArithmeticOpNode impl) {
            return impl.execute(x, y);
        }

        LLVMArithmeticOpNode createNode(Object x, Object y) {
            if (x instanceof LLVMArithmetic && x.getClass() == y.getClass()) {
                return ((LLVMArithmetic) x).createMulNode();
            } else {
                throw new AssertionError("unsupported operand types: " + x.getClass() + ", " + y.getClass());
            }
        }
    }

    abstract static class CachedDivNode extends LLVMArithmeticOpNode {
        @Override
        public boolean canCompute(Object x, Object y) {
            return x instanceof LLVMArithmetic && y instanceof LLVMArithmetic;
        }

        @Specialization(guards = "impl.canCompute(x, y)")
        public LLVMArithmetic execute(LLVMArithmetic x, LLVMArithmetic y,
                        @Cached("createNode(x, y)") LLVMArithmeticOpNode impl) {
            return impl.execute(x, y);
        }

        LLVMArithmeticOpNode createNode(Object x, Object y) {
            if (x instanceof LLVMArithmetic && x.getClass() == y.getClass()) {
                return ((LLVMArithmetic) x).createDivNode();
            } else {
                throw new AssertionError("unsupported operand types: " + x.getClass() + ", " + y.getClass());
            }
        }
    }

    abstract static class CachedRemNode extends LLVMArithmeticOpNode {
        @Override
        public boolean canCompute(Object x, Object y) {
            return x instanceof LLVMArithmetic && y instanceof LLVMArithmetic;
        }

        @Specialization(guards = "impl.canCompute(x, y)")
        public LLVMArithmetic execute(LLVMArithmetic x, LLVMArithmetic y,
                        @Cached("createNode(x, y)") LLVMArithmeticOpNode impl) {
            return impl.execute(x, y);
        }

        LLVMArithmeticOpNode createNode(Object x, Object y) {
            if (x instanceof LLVMArithmetic && x.getClass() == y.getClass()) {
                return ((LLVMArithmetic) x).createRemNode();
            } else {
                throw new AssertionError("unsupported operand types: " + x.getClass() + ", " + y.getClass());
            }
        }
    }
}
