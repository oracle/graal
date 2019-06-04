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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMPointerVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

public final class LLVMFrameNullerUtil {
    private static final boolean USE_FRAME_NULLING = true;

    private LLVMFrameNullerUtil() {
    }

    public static void nullFrameSlot(VirtualFrame frame, FrameSlot frameSlot, boolean forceNulling) {
        if (!USE_FRAME_NULLING) {
            return;
        }

        CompilerAsserts.partialEvaluationConstant(frameSlot);
        FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(frameSlot);
        if (kind == FrameSlotKind.Object) {
            // object frame slots always need to be nulled (otherwise we would impact GC)
            nullObject(frame, frameSlot);
        } else if (CompilerDirectives.inCompiledCode() || forceNulling) {
            // Nulling primitive frame slots is only necessary in compiled code (otherwise, we would
            // compute values that are only used in framestates). This code must NOT be moved to a
            // separate method as it would cause endless deopts (the method or classes that are used
            // within the method might be unresolved because they were never executed). For the same
            // reason, we also must NOT use a switch statement.
            if (kind == FrameSlotKind.Boolean) {
                frame.setBoolean(frameSlot, false);
            } else if (kind == FrameSlotKind.Byte) {
                frame.setByte(frameSlot, (byte) 0);
            } else if (kind == FrameSlotKind.Int) {
                frame.setInt(frameSlot, 0);
            } else if (kind == FrameSlotKind.Long) {
                frame.setLong(frameSlot, 0L);
            } else if (kind == FrameSlotKind.Float) {
                frame.setFloat(frameSlot, 0f);
            } else if (kind == FrameSlotKind.Double) {
                frame.setDouble(frameSlot, 0d);
            } else {
                throw new UnsupportedOperationException("unexpected frameslot kind");
            }
        }
    }

    private static void nullAddress(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMNativePointer.createNull());
    }

    private static void nullIVarBit(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMIVarBit.createNull());
    }

    private static void null80BitFloat(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVM80BitFloat.createPositiveZero());
    }

    private static void nullObject(VirtualFrame frame, FrameSlot frameSlot) {
        if (frameSlot.getInfo() != null) {
            Type type = (Type) frameSlot.getInfo();
            if (type instanceof VectorType) {
                nullVector(frame, frameSlot, (VectorType) type);
                return;
            } else if (type instanceof VariableBitWidthType) {
                nullIVarBit(frame, frameSlot);
                return;
            } else if (type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.X86_FP80) {
                null80BitFloat(frame, frameSlot);
                return;
            }
        }

        // This is a best effort approach. It could still be that LLVMAddress clashes with some
        // other class.
        nullAddress(frame, frameSlot);
    }

    private static void nullVector(VirtualFrame frame, FrameSlot frameSlot, VectorType vectorType) {
        frame.setObject(frameSlot, getNullVector(vectorType));
    }

    private static LLVMVector getNullVector(VectorType vectorType) {
        CompilerAsserts.partialEvaluationConstant(vectorType);
        Type elementType = vectorType.getElementType();
        if (elementType instanceof PrimitiveType) {
            switch (((PrimitiveType) elementType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1Vector.create(new boolean[0]);
                case I8:
                    return LLVMI8Vector.create(new byte[0]);
                case I16:
                    return LLVMI16Vector.create(new short[0]);
                case I32:
                    return LLVMI32Vector.create(new int[0]);
                case I64:
                    return LLVMI64Vector.create(new long[0]);
                case DOUBLE:
                    return LLVMDoubleVector.create(new double[0]);
                case FLOAT:
                    return LLVMFloatVector.create(new float[0]);
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError();
            }
        } else if (elementType instanceof PointerType || elementType instanceof FunctionType) {
            return LLVMPointerVector.create(new LLVMPointer[0]);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Unknown vector element type: " + elementType);
        }
    }
}
