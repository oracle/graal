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
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObjectFactory.ForeignWriteNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.types.PointerType;

@ValueType
public final class LLVMTypedForeignObject implements LLVMObjectAccess, LLVMInternalTruffleObject {

    private final TruffleObject foreign;
    private final LLVMSourceType type;

    public static LLVMTypedForeignObject create(TruffleObject foreign, LLVMSourceType type) {
        return new LLVMTypedForeignObject(foreign, type);
    }

    public static LLVMTypedForeignObject createUnknown(TruffleObject foreign) {
        return new LLVMTypedForeignObject(foreign, null);
    }

    private LLVMTypedForeignObject(TruffleObject foreign, LLVMSourceType type) {
        this.foreign = foreign;
        this.type = type;
    }

    public TruffleObject getForeign() {
        return foreign;
    }

    public LLVMSourceType getType() {
        return type;
    }

    @Override
    public LLVMObjectReadNode createReadNode(ForeignToLLVMType toLLVMType) {
        return new ForeignReadNode(toLLVMType);
    }

    @Override
    public LLVMObjectWriteNode createWriteNode() {
        return ForeignWriteNodeGen.create();
    }

    static class ForeignReadNode extends LLVMObjectReadNode {

        @Child LLVMOffsetToNameNode offsetToName;
        @Child ForeignToLLVM toLLVM;
        @Child Node read = Message.READ.createNode();

        protected ForeignReadNode(ForeignToLLVMType type) {
            this.offsetToName = LLVMOffsetToNameNodeGen.create(type.getSizeInBytes());
            this.toLLVM = ForeignToLLVM.create(type);
        }

        @Override
        public Object executeRead(Object obj, long offset) throws InteropException {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            Object identifier = offsetToName.execute(object.getType(), offset);
            Object ret = ForeignAccess.sendRead(read, object.getForeign(), identifier);
            return toLLVM.executeWithTarget(ret);
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    abstract static class ForeignWriteNode extends LLVMObjectWriteNode {

        @Child LLVMDataEscapeNode dataEscape = LLVMDataEscapeNodeGen.create(PointerType.VOID);
        @Child Node write = Message.WRITE.createNode();

        private final ContextReference<LLVMContext> ctxRef = LLVMLanguage.getLLVMContextReference();

        @Specialization(guards = "valueClass.isInstance(value)")
        void doCachedType(Object obj, long offset, Object value,
                        @Cached("value.getClass()") @SuppressWarnings("unused") Class<?> valueClass,
                        @Cached("create(getSize(value))") LLVMOffsetToNameNode offsetToName) {
            doWrite(obj, offset, value, offsetToName);
        }

        @Specialization(limit = "4", replaces = "doCachedType", guards = "valueSize == getSize(value)")
        void doCachedSize(Object obj, long offset, Object value,
                        @Cached("getSize(value)") @SuppressWarnings("unused") int valueSize,
                        @Cached("create(valueSize)") LLVMOffsetToNameNode offsetToName) {
            doWrite(obj, offset, value, offsetToName);
        }

        private void doWrite(Object obj, long offset, Object value, LLVMOffsetToNameNode offsetToName) {
            LLVMTypedForeignObject object = (LLVMTypedForeignObject) obj;
            Object identifier = offsetToName.execute(object.getType(), offset);
            Object escapedValue = dataEscape.executeWithTarget(value, ctxRef.get());
            try {
                ForeignAccess.sendWrite(write, object.getForeign(), identifier, escapedValue);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }

        protected static int getSize(Object value) {
            if (value instanceof Byte || value instanceof Boolean) {
                return 1;
            } else if (value instanceof Short || value instanceof Character) {
                return 2;
            } else if (value instanceof Integer || value instanceof Float) {
                return 4;
            } else {
                return 8;
            }
        }

        @Override
        public boolean canAccess(Object obj) {
            return obj instanceof LLVMTypedForeignObject;
        }
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMTypedForeignObjectMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMTypedForeignObject;
    }
}
