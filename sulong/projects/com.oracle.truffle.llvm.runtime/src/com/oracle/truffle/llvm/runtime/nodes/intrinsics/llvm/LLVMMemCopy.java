/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.move.LLVMPrimitiveMoveNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class, value = "destination")
@NodeChild(type = LLVMExpressionNode.class, value = "source")
@NodeChild(type = LLVMExpressionNode.class, value = "length")
@NodeChild(type = LLVMExpressionNode.class, value = "isVolatile")
public abstract class LLVMMemCopy extends LLVMBuiltin {

    @Child private LLVMMemMoveNode memMove;

    public LLVMMemCopy(LLVMMemMoveNode memMove) {
        this.memMove = memMove;
    }

    public static LLVMExpressionNode createIntrinsic(LLVMExpressionNode[] args, LLVMMemMoveNode memMove, NodeFactory nodeFactory) {
        LLVMExpressionNode serialMovesReplacement = LLVMPrimitiveMoveNode.createSerialMoves(args, nodeFactory, memMove);
        if (serialMovesReplacement != null) {
            return serialMovesReplacement;
        }

        if (args.length == 6) {
            return LLVMMemCopyNodeGen.create(memMove, args[1], args[2], args[3], args[5]);
        } else if (args.length == 5) {
            // LLVM 7 drops the alignment argument
            return LLVMMemCopyNodeGen.create(memMove, args[1], args[2], args[3], args[4]);
        } else {
            throw new LLVMParserException("Illegal number of arguments to @llvm.memcpy.*: " + args.length);
        }
    }

    @Specialization
    protected Object doVoid(VirtualFrame frame, LLVMPointer target, LLVMPointer source, int length, boolean isVolatile) {
        return doVoid(frame, target, source, (long) length, isVolatile);
    }

    @Specialization
    protected Object doVoid(VirtualFrame frame, LLVMPointer target, LLVMPointer source, long length, @SuppressWarnings("unused") boolean isVolatile) {
        memMove.executeWithTarget(frame, target, source, length);
        return null;
    }

    @Specialization
    protected Object doVoid(VirtualFrame frame, LLVMPointer target, LLVMPointer source, LLVMPointer length, boolean isVolatile,
                    @Cached LLVMToNativeNode toNative) {
        return doVoid(frame, target, source, toNative.executeWithTarget(length).asNative(), isVolatile);
    }
}
