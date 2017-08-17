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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.vector.LLVMAddressVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMDoubleVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMFloatVector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI16Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI1Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI32Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI64Vector;
import com.oracle.truffle.llvm.runtime.vector.LLVMI8Vector;

public final class LLVMFrameNullerUtil {
    private LLVMFrameNullerUtil() {
    }

    public static void nullI1(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setBoolean(frameSlot, false);
    }

    public static void nullI8(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setByte(frameSlot, (byte) 0);
    }

    public static void nullI16(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setInt(frameSlot, 0);
    }

    public static void nullI32(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setInt(frameSlot, 0);
    }

    public static void nullI64(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setLong(frameSlot, 0L);
    }

    public static void nullFloat(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setFloat(frameSlot, 0f);
    }

    public static void nullDouble(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setDouble(frameSlot, 0d);
    }

    public static void nullAddress(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMAddress.nullPointer());
    }

    public static void nullIVarBit(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMIVarBit.createNull());
    }

    public static void null80BitFloat(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, new LLVM80BitFloat(false, 0, 0));
    }

    public static void nullFunction(VirtualFrame frame, FrameSlot frameSlot) {
        frame.setObject(frameSlot, LLVMFunctionHandle.createHandle(0));
    }

    public static void nullFrameSlot(VirtualFrame frame, FrameSlot frameSlot) {
        CompilerAsserts.partialEvaluationConstant(frameSlot.getKind());
        switch (frameSlot.getKind()) {
            case Boolean:
                nullI1(frame, frameSlot);
                break;
            case Byte:
                nullI8(frame, frameSlot);
                break;
            case Int:
                nullI32(frame, frameSlot);
                break;
            case Long:
                nullI64(frame, frameSlot);
                break;
            case Float:
                nullFloat(frame, frameSlot);
                break;
            case Double:
                nullDouble(frame, frameSlot);
                break;
            case Object:
                CompilerAsserts.partialEvaluationConstant(frameSlot.getInfo());
                CompilerAsserts.partialEvaluationConstant(frameSlot.getInfo() == null);
                if (frameSlot.getInfo() == null) {
                    nullAddress(frame, frameSlot);
                    break;
                } else {
                    Type type = (Type) frameSlot.getInfo();
                    CompilerAsserts.partialEvaluationConstant(Type.isFunctionOrFunctionPointer(type));
                    CompilerAsserts.partialEvaluationConstant(type instanceof VectorType);
                    CompilerAsserts.partialEvaluationConstant(type instanceof VariableBitWidthType);
                    CompilerAsserts.partialEvaluationConstant(type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.X86_FP80);
                    if (Type.isFunctionOrFunctionPointer(type)) {
                        nullFunction(frame, frameSlot);
                    } else if (type instanceof VectorType && ((VectorType) type).getElementType() instanceof PrimitiveType) {
                        nullVector(frame, frameSlot, ((PrimitiveType) ((VectorType) type).getElementType()).getPrimitiveKind());
                    } else if (type instanceof VectorType && ((VectorType) type).getElementType() instanceof PointerType) {
                        frame.setObject(frameSlot, LLVMAddressVector.createNullVector());
                    } else if (type instanceof VariableBitWidthType) {
                        nullIVarBit(frame, frameSlot);
                    } else if (type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.X86_FP80) {
                        null80BitFloat(frame, frameSlot);
                    } else {
                        /*
                         * This is a best effort approach. It could still be that LLVMAddress and
                         * LLVMGlobalVariable clash.
                         */
                        nullAddress(frame, frameSlot);
                    }
                }
                break;
            case Illegal:
            default:
                throw new UnsupportedOperationException("unexpected frameslot kind");
        }
    }

    public static void nullVector(VirtualFrame frame, FrameSlot frameSlot, PrimitiveKind elementType) {
        CompilerAsserts.partialEvaluationConstant(elementType);
        switch (elementType) {
            case DOUBLE:
                frame.setObject(frameSlot, LLVMDoubleVector.create(null));
                break;
            case FLOAT:
                frame.setObject(frameSlot, LLVMFloatVector.create(null));
                break;
            case I1:
                frame.setObject(frameSlot, LLVMI1Vector.create(null));
                break;
            case I16:
                frame.setObject(frameSlot, LLVMI16Vector.create(null));
                break;
            case I32:
                frame.setObject(frameSlot, LLVMI32Vector.create(null));
                break;
            case I64:
                frame.setObject(frameSlot, LLVMI64Vector.create(null));
                break;
            case I8:
                frame.setObject(frameSlot, LLVMI8Vector.create(null));
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError();

        }
    }
}
