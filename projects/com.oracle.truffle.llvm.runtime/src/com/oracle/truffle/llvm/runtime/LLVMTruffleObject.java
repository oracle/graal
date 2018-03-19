/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObjectFactory.BaseToPointerNodeGen;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;
import com.oracle.truffle.llvm.runtime.types.Type;

@ValueType
public final class LLVMTruffleObject implements LLVMObjectNativeLibrary.Provider {
    private final TruffleObject object;
    private final long offset;
    private final LLVMSourceType baseType;

    private static LLVMSourceType overrideBaseType(LLVMTruffleObject obj, Type newType) {
        if (obj.getOffset() == 0) {
            if (newType.getSourceType() != null) {
                return newType.getSourceType();
            } else {
                return obj.getBaseType();
            }
        } else {
            return obj.getBaseType();
        }
    }

    public static LLVMTruffleObject createNullPointer() {
        return createPointer(0L);
    }

    public static LLVMTruffleObject createPointer(long ptr) {
        return new LLVMTruffleObject(null, ptr, null);
    }

    public LLVMTruffleObject(LLVMTruffleObject orig, Type type) {
        this(orig.getObject(), orig.getOffset(), overrideBaseType(orig, type));
    }

    public LLVMTruffleObject(TruffleObject object, Type type) {
        this(object, 0, type.getSourceType());
    }

    private LLVMTruffleObject(TruffleObject object, long offset, LLVMSourceType baseType) {
        this.object = object;
        this.offset = offset;
        this.baseType = baseType;
    }

    public long getOffset() {
        return offset;
    }

    public TruffleObject getObject() {
        return object;
    }

    public LLVMTruffleObject increment(long incr) {
        return new LLVMTruffleObject(object, offset + incr, baseType);
    }

    public LLVMSourceType getBaseType() {
        return baseType;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (object == null ? "null" : object.getClass().getSimpleName()) + ":" + getOffset() + ")";
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return wrapLibrary(LLVMObjectNativeLibrary.createCached(object));
    }

    private static LLVMObjectNativeLibrary wrapLibrary(final LLVMObjectNativeLibrary lib) {
        return new LLVMObjectNativeLibrary() {

            @Child private Node isNull;
            @Child private BaseToPointerNode baseToPointer;

            @Override
            public boolean guard(Object obj) {
                if (obj instanceof LLVMTruffleObject) {
                    return lib.guard(((LLVMTruffleObject) obj).getObject());
                } else {
                    return false;
                }
            }

            @Override
            public boolean isPointer(VirtualFrame frame, Object obj) {
                LLVMTruffleObject object = (LLVMTruffleObject) obj;
                if (lib.isPointer(frame, object.getObject())) {
                    return true;
                } else {
                    if (isNull == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        isNull = insert(Message.IS_NULL.createNode());
                    }
                    return object.getObject() == null || ForeignAccess.sendIsNull(isNull, object.getObject());
                }
            }

            @Override
            public long asPointer(VirtualFrame frame, Object obj) throws InteropException {
                if (baseToPointer == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    baseToPointer = insert(BaseToPointerNodeGen.create());
                }
                LLVMTruffleObject object = (LLVMTruffleObject) obj;
                long base = baseToPointer.executeToPointer(frame, object.getObject(), lib);
                return base + object.getOffset();
            }

            @Override
            public Object toNative(VirtualFrame frame, Object obj) throws InteropException {
                LLVMTruffleObject object = (LLVMTruffleObject) obj;
                if (object.getObject() == null) {
                    return object;
                } else {
                    Object nativeBase = lib.toNative(frame, object.getObject());
                    return new LLVMTruffleObject((TruffleObject) nativeBase, object.offset, object.baseType);
                }
            }
        };
    }

    abstract static class BaseToPointerNode extends Node {

        protected abstract long executeToPointer(VirtualFrame frame, Object object, LLVMObjectNativeLibrary lib);

        @Specialization(guards = "object == null")
        @SuppressWarnings("unused")
        protected long doNull(Object object, LLVMObjectNativeLibrary lib) {
            return 0;
        }

        @Specialization(guards = {"object != null", "checkNull(isNull, object)"})
        @SuppressWarnings("unused")
        protected long doNull(TruffleObject object, LLVMObjectNativeLibrary lib,
                        @Cached("createIsNull()") Node isNull) {
            return 0;
        }

        @Specialization(guards = {"object != null", "lib.isPointer(frame, object)"})
        protected long doPointer(VirtualFrame frame, Object object, LLVMObjectNativeLibrary lib) {
            try {
                return lib.asPointer(frame, object);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }

        static Node createIsNull() {
            return Message.IS_NULL.createNode();
        }

        static boolean checkNull(Node isNull, TruffleObject object) {
            return ForeignAccess.sendIsNull(isNull, object);
        }
    }
}
