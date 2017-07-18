/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.convert;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public abstract class ForeignToLLVM extends LLVMNode {

    public abstract Object executeWithTarget(Object value);

    @Child protected Node isPointer = Message.IS_POINTER.createNode();
    @Child protected Node asPointer = Message.AS_POINTER.createNode();
    @Child protected Node isBoxed = Message.IS_BOXED.createNode();
    @Child protected Node unbox = Message.UNBOX.createNode();

    public Object fromForeign(TruffleObject value) {
        try {
            if (ForeignAccess.sendIsPointer(isPointer, value)) {
                return ForeignAccess.sendAsPointer(asPointer, value);
            } else if (ForeignAccess.sendIsBoxed(isBoxed, value)) {
                return ForeignAccess.sendUnbox(unbox, value);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(new Object[]{value});
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }

    protected static boolean notLLVM(TruffleObject value) {
        return LLVMExpressionNode.notLLVM(value);
    }

    protected boolean checkIsPointer(TruffleObject object) {
        return ForeignAccess.sendIsPointer(isPointer, object);
    }

    protected char getSingleStringCharacter(String value) {
        if (value.length() == 1) {
            return value.charAt(0);
        } else {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.raise(new Object[]{value});
        }
    }

    public enum ForeignToLLVMType {
        I1,
        I8,
        I16,
        I32,
        I64,
        FLOAT,
        DOUBLE,
        POINTER,
        ANY
    }

    private static ForeignToLLVMType convert(Type type) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return ForeignToLLVMType.I1;
                case I8:
                    return ForeignToLLVMType.I8;
                case I16:
                    return ForeignToLLVMType.I16;
                case I32:
                    return ForeignToLLVMType.I32;
                case I64:
                    return ForeignToLLVMType.I64;
                case FLOAT:
                    return ForeignToLLVMType.FLOAT;
                case DOUBLE:
                    return ForeignToLLVMType.DOUBLE;
                default:
                    throw UnsupportedTypeException.raise(new Object[]{type});
            }
        } else if (type instanceof PointerType) {
            return ForeignToLLVMType.POINTER;
        } else {
            throw UnsupportedTypeException.raise(new Object[]{type});
        }
    }

    public static SlowPathForeignToLLVM createSlowPathNode() {
        return new SlowPathForeignToLLVM();
    }

    public static ForeignToLLVM create(Type type) {
        return create(convert(type));
    }

    public static ForeignToLLVM create(ForeignToLLVMType type) {
        switch (type) {
            case ANY:
                return ToAnyLLVMNodeGen.create();
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointerNodeGen.create();
            default:
                throw new IllegalStateException(type.toString());

        }
    }

    public static final class SlowPathForeignToLLVM extends ForeignToLLVM {
        @TruffleBoundary
        public Object convert(Type type, Object value) {
            return convert(ForeignToLLVM.convert(type), value);
        }

        @TruffleBoundary
        public Object convert(ForeignToLLVMType type, Object value) {
            switch (type) {
                case ANY:
                    return ToAnyLLVM.slowPathPrimitiveConvert(this, value);
                case DOUBLE:
                    return ToDouble.slowPathPrimitiveConvert(this, value);
                case FLOAT:
                    return ToFloat.slowPathPrimitiveConvert(this, value);
                case I1:
                    return ToI1.slowPathPrimitiveConvert(this, value);
                case I16:
                    return ToI16.slowPathPrimitiveConvert(this, value);
                case I32:
                    return ToI32.slowPathPrimitiveConvert(this, value);
                case I64:
                    return ToI64.slowPathPrimitiveConvert(this, value);
                case I8:
                    return ToI8.slowPathPrimitiveConvert(this, value);
                case POINTER:
                    return ToPointer.slowPathPrimitiveConvert(this, value);
                default:
                    throw new IllegalStateException(type.toString());

            }
        }

        @Override
        public Object executeWithTarget(Object value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Use convert method.");
        }
    }
}
