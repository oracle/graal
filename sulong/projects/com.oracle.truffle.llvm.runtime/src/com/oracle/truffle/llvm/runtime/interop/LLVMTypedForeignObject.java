/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObjectFactory.ForeignGetTypeNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObjectFactory.TypeCacheNodeGen;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropReadNode;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropWriteNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.spi.GetDynamicType;

@ValueType
public final class LLVMTypedForeignObject implements LLVMObjectAccess, LLVMInternalTruffleObject {

    private final TruffleObject foreign;
    private final LLVMInteropType.Structured type;

    public static LLVMTypedForeignObject create(TruffleObject foreign, LLVMInteropType.Structured type) {
        return new LLVMTypedForeignObject(foreign, type);
    }

    public static LLVMTypedForeignObject createUnknown(TruffleObject foreign) {
        return new LLVMTypedForeignObject(foreign, null);
    }

    private LLVMTypedForeignObject(TruffleObject foreign, LLVMInteropType.Structured type) {
        this.foreign = foreign;
        this.type = type;
    }

    public TruffleObject getForeign() {
        return foreign;
    }

    public LLVMInteropType.Structured getType() {
        return type;
    }

    @Override
    public LLVMObjectReadNode createReadNode() {
        return new ForeignReadNode();
    }

    @Override
    public LLVMObjectWriteNode createWriteNode() {
        return new ForeignWriteNode();
    }

    @Override
    public boolean equals(Object obj) {
        // ignores the type explicitly
        if (obj instanceof LLVMTypedForeignObject) {
            LLVMTypedForeignObject other = (LLVMTypedForeignObject) obj;
            return foreign.equals(other.foreign);
        }
        return false;
    }

    @Override
    public int hashCode() {
        // ignores the type explicitly
        return foreign.hashCode();
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMTypedForeignObjectMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMTypedForeignObject;
    }

    abstract static class TypeCacheNode extends LLVMNode {

        protected abstract LLVMInteropType.Structured execute(LLVMTypedForeignObject object);

        public static TypeCacheNode create() {
            return TypeCacheNodeGen.create();
        }

        static Node createGetDynamicType() {
            return GetDynamicType.INSTANCE.createNode();
        }

        @Specialization(rewriteOn = InteropException.class)
        LLVMInteropType.Structured doDynamic(LLVMTypedForeignObject object,
                        @Cached("createGetDynamicType()") Node getDynamicType) throws InteropException {
            return getDynamicType(object, getDynamicType);
        }

        static LLVMInteropType.Structured getDynamicType(LLVMTypedForeignObject object, Node getDynamicType) throws InteropException {
            Object type = ForeignAccess.send(getDynamicType, object.getForeign());
            if (type instanceof LLVMInteropType.Structured) {
                return (LLVMInteropType.Structured) type;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMPolyglotException(getDynamicType, "Invalid type %s returned from foreign object.", type);
            }
        }

        @Specialization
        LLVMInteropType.Structured doStatic(LLVMTypedForeignObject object) {
            return object.getType();
        }
    }

    public abstract static class ForeignGetTypeNode extends LLVMNode {

        public abstract LLVMInteropType.Structured execute(LLVMTypedForeignObject object);

        static Node createGetDynamicType() {
            return GetDynamicType.INSTANCE.createNode();
        }

        @Specialization(guards = "clazz.isInstance(object.getForeign())")
        public LLVMInteropType.Structured doCached(LLVMTypedForeignObject object,
                        @Cached("object.getForeign().getClass()") @SuppressWarnings("unused") Class<?> clazz,
                        @Cached("create()") TypeCacheNode typeCache) {
            return typeCache.execute(object);
        }

        @TruffleBoundary
        @Specialization(replaces = "doCached")
        public LLVMInteropType.Structured doFallback(LLVMTypedForeignObject object,
                        @Cached("createGetDynamicType()") Node getDynamicType) {
            try {
                return TypeCacheNode.getDynamicType(object, getDynamicType);
            } catch (InteropException ex) {
                return object.getType();
            }
        }
    }

    static class ForeignReadNode extends LLVMNode implements LLVMObjectReadNode {

        @Child LLVMInteropReadNode read = LLVMInteropReadNode.create();
        @Child ForeignGetTypeNode getType = ForeignGetTypeNodeGen.create();

        @Override
        public Object executeRead(Object obj, long offset, ForeignToLLVMType type) {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            return read.execute(getType.execute(object), object.getForeign(), offset, type);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    static class ForeignWriteNode extends LLVMNode implements LLVMObjectWriteNode {

        @Child LLVMInteropWriteNode write = LLVMInteropWriteNode.create();
        @Child LLVMDataEscapeNode dataEscape = LLVMDataEscapeNode.create();
        @Child ForeignGetTypeNode getType = ForeignGetTypeNodeGen.create();

        @Override
        public void executeWrite(Object obj, long offset, Object value, ForeignToLLVMType type) {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            Object escapedValue = dataEscape.executeWithTarget(value);
            write.execute(getType.execute(object), object.getForeign(), offset, escapedValue);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }
}
