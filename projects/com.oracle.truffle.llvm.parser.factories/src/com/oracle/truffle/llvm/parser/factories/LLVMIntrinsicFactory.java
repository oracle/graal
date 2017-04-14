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
package com.oracle.truffle.llvm.parser.factories;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMTypeIdForExceptionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI16Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI32Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI64Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI1NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI64NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMFrameAddressNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMI64ObjectSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeEndFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMLifetimeStartFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI32CopyFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemCopyFactory.LLVMMemI64CopyFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemMoveFactory.LLVMMemMoveI64Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemSetFactory.LLVMMemSetI32Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMMemSetFactory.LLVMMemSetI64Factory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMNoOpFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMPrefetchFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMReturnAddressFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackRestoreNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMStackSaveNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMTrapFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMFabsFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMPowFactory.LLVMPowDoubleFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMPowFactory.LLVMPowFloatFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMPowIFactory.LLVMPowIDoubleFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMPowIFactory.LLVMPowIFloatFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.arith.LLVMUAddWithOverflowFactory.LLVMUAddWithOverflowI32NodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI32NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI64NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI32NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI64NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeFactory;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVACopyNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAEnd;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAStart;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;

final class LLVMIntrinsicFactory {

    private static final Map<String, NodeFactory<? extends LLVMExpressionNode>> factories = new HashMap<>();

    static {
        // memset
        factories.put("@llvm.memset.p0i8.i32", LLVMMemSetI32Factory.getInstance());
        factories.put("@llvm.memset.p0i8.i64", LLVMMemSetI64Factory.getInstance());
        // no ops
        factories.put("@llvm.donothing", LLVMNoOpFactory.getInstance());
        factories.put("@llvm.prefetch", LLVMPrefetchFactory.getInstance());
        // ctlz
        factories.put("@llvm.ctlz.i32", CountLeadingZeroesI32NodeFactory.getInstance());
        factories.put("@llvm.ctlz.i64", CountLeadingZeroesI64NodeFactory.getInstance());
        // memcpy
        factories.put("@llvm.memcpy.p0i8.p0i8.i64", LLVMMemI64CopyFactory.getInstance());
        factories.put("@llvm.memcpy.p0i8.p0i8.i32", LLVMMemI32CopyFactory.getInstance());
        // ctpop
        factories.put("@llvm.ctpop.i32", CountSetBitsI32NodeFactory.getInstance());
        factories.put("@llvm.ctpop.i64", CountSetBitsI64NodeFactory.getInstance());
        // cttz
        factories.put("@llvm.cttz.i32", CountTrailingZeroesI32NodeFactory.getInstance());
        factories.put("@llvm.cttz.i64", CountTrailingZeroesI64NodeFactory.getInstance());
        // trap
        factories.put("@llvm.trap", LLVMTrapFactory.getInstance());
        // bswap
        factories.put("@llvm.bswap.i16", LLVMByteSwapI16Factory.getInstance());
        factories.put("@llvm.bswap.i32", LLVMByteSwapI32Factory.getInstance());
        factories.put("@llvm.bswap.i64", LLVMByteSwapI64Factory.getInstance());
        // memmove
        factories.put("@llvm.memmove.p0i8.p0i8.i64", LLVMMemMoveI64Factory.getInstance());
        // arith
        factories.put("@llvm.pow.f32", LLVMPowFloatFactory.getInstance());
        factories.put("@llvm.pow.f64", LLVMPowDoubleFactory.getInstance());

        factories.put("@llvm.powi.f32", LLVMPowIFloatFactory.getInstance());
        factories.put("@llvm.powi.f64", LLVMPowIDoubleFactory.getInstance());

        factories.put("@llvm.fabs.f64", LLVMFabsFactory.getInstance());

        // frameaddress, returnaddress (constantly returns a null pointer)
        factories.put("@llvm.returnaddress", LLVMReturnAddressFactory.getInstance());
        factories.put("@llvm.lifetime.start", LLVMLifetimeStartFactory.getInstance());
        factories.put("@llvm.lifetime.end", LLVMLifetimeEndFactory.getInstance());
    }

    private LLVMIntrinsicFactory() {
    }

    // The nodes are directly inserted in the current LLVM AST for the moment. To change this later
    // one,
    // reuse the same intrinsic node classes but pass arg read nodes as there arguments.
    static LLVMExpressionNode create(String name, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments, LLVMParserRuntime runtime) {
        return create(name, argNodes, numberOfExplicitArguments, runtime.getStackPointerSlot());
    }

    private static LLVMExpressionNode create(String functionName, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments, FrameSlot stack) {
        NodeFactory<? extends LLVMExpressionNode> factory = factories.get(functionName);
        if (factory == null) {
            LLVMExpressionNode readStackPointerNode = argNodes[0];
            if (functionName.equals("@llvm.uadd.with.overflow.i32")) {
                return LLVMUAddWithOverflowI32NodeGen.create(argNodes[2], argNodes[3], argNodes[1]);
            } else if (functionName.equals("@llvm.stacksave")) {
                return LLVMStackSaveNodeGen.create(readStackPointerNode);
            } else if (functionName.equals("@llvm.stackrestore")) {
                return LLVMStackRestoreNodeGen.create(argNodes[1], stack);
            } else if (functionName.equals("@llvm.frameaddress")) {
                return LLVMFrameAddressNodeGen.create(argNodes[1], stack);
            } else if (functionName.startsWith("@llvm.va_start")) {
                return new LLVMX86_64BitVAStart(numberOfExplicitArguments, argNodes[1], stack);
            } else if (functionName.startsWith("@llvm.va_end")) {
                return new LLVMX86_64BitVAEnd(argNodes[1]);
            } else if (functionName.startsWith("@llvm.va_copy")) {
                return LLVMX86_64BitVACopyNodeGen.create(argNodes[1], argNodes[2], numberOfExplicitArguments);
            } else if (functionName.equals("@llvm.eh.sjlj.longjmp") || functionName.equals("@llvm.eh.sjlj.setjmp")) {
                throw new LLVMUnsupportedException(UnsupportedReason.SET_JMP_LONG_JMP);
            } else if (functionName.startsWith("@llvm.objectsize.i64")) {
                return LLVMI64ObjectSizeNodeGen.create(argNodes[1], argNodes[2]);
            } else if (functionName.startsWith("@llvm.expect")) {
                return getExpect(argNodes, functionName);
            } else if (functionName.startsWith("@llvm.dbg.declare")) {
                return new LLVMExpressionNode() {

                    @Override
                    public Object executeGeneric(VirtualFrame frame) {
                        // TODO: implement debugging support
                        return null;
                    }

                };
            } else if (functionName.startsWith("@llvm.dbg.value")) {
                return new LLVMExpressionNode() {

                    @Override
                    public Object executeGeneric(VirtualFrame frame) {
                        /*
                         * TODO: implement
                         *
                         * it seems like this call is used when the project was build with higher
                         * optimization levels.
                         *
                         * $ opt -O3 mycode.ll -S -o mycode.opt.ll
                         */
                        return null;
                    }

                };
            } else if (functionName.startsWith("@llvm.eh.typeid.for")) {
                return new LLVMTypeIdForExceptionNode(argNodes[1]);
            } else {
                throw new IllegalStateException("llvm intrinsic " + functionName + " not yet supported!");
            }
        } else {
            Object[] realArgs = new Object[argNodes.length - LLVMCallNode.ARG_START_INDEX];
            System.arraycopy(argNodes, LLVMCallNode.ARG_START_INDEX, realArgs, 0, realArgs.length);
            return factory.createNode(realArgs);
        }

    }

    private static LLVMExpressionNode getExpect(LLVMExpressionNode[] argNodes, String functionName) {
        if (functionName.startsWith("@llvm.expect.i1")) {
            boolean expectedValue = argNodes[2].executeI1(null);
            LLVMExpressionNode actualValueNode = argNodes[1];
            return LLVMExpectI1NodeGen.create(expectedValue, actualValueNode);
        } else if (functionName.startsWith("@llvm.expect.i32")) {
            int expectedValue = argNodes[2].executeI32(null);
            LLVMExpressionNode actualValueNode = argNodes[1];
            return LLVMExpectI32NodeGen.create(expectedValue, actualValueNode);
        } else if (functionName.startsWith("@llvm.expect.i64")) {
            long expectedValue = argNodes[2].executeI64(null);
            LLVMExpressionNode actualValueNode = argNodes[1];
            return LLVMExpectI64NodeGen.create(expectedValue, actualValueNode);
        } else {
            throw new IllegalStateException(functionName);
        }
    }

}
