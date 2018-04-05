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
package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;

@MessageResolution(receiverType = LLVMTruffleObject.class)
public class LLVMTruffleObjectMessageResolution {

    @Resolve(message = "IS_NULL")
    public abstract static class IsNull extends Node {

        protected boolean access(LLVMTruffleObject receiver) {
            return receiver.getObject() == null && receiver.getOffset() == 0;
        }
    }

    @Resolve(message = "IS_POINTER")
    public abstract static class IsPointer extends Node {

        protected boolean access(LLVMTruffleObject receiver) {
            return receiver.getObject() == null;
        }
    }

    @Resolve(message = "AS_POINTER")
    public abstract static class AsPointer extends Node {

        protected long access(LLVMTruffleObject receiver) {
            if (receiver.getObject() == null) {
                return receiver.getOffset();
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.AS_POINTER);
            }
        }
    }

    @Resolve(message = "HAS_SIZE")
    public abstract static class HasSize extends Node {

        protected boolean access(LLVMTruffleObject receiver) {
            return receiver.getExportType() instanceof LLVMInteropType.Array;
        }
    }

    @Resolve(message = "GET_SIZE")
    public abstract static class GetSize extends Node {

        protected long access(LLVMTruffleObject receiver) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Array)) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.GET_SIZE);
            }

            LLVMInteropType.Array array = (LLVMInteropType.Array) receiver.getExportType();
            return array.getLength();
        }
    }

    @Resolve(message = "HAS_KEYS")
    public abstract static class HasKeys extends Node {

        protected boolean access(LLVMTruffleObject receiver) {
            return receiver.getExportType() instanceof LLVMInteropType.Struct;
        }
    }

    @Resolve(message = "KEYS")
    public abstract static class GetKeys extends Node {

        protected TruffleObject access(LLVMTruffleObject receiver) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.KEYS);
            }

            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            return new Keys(struct);
        }
    }

    @Resolve(message = "KEY_INFO")
    public abstract static class GetKeyInfo extends Node {

        protected int access(LLVMTruffleObject receiver, String ident) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Struct)) {
                return KeyInfo.NONE;
            }

            LLVMInteropType.Struct struct = (LLVMInteropType.Struct) receiver.getExportType();
            LLVMInteropType.StructMember member = struct.findMember(ident);
            if (member == null) {
                // does not exist
                return KeyInfo.NONE;
            } else if (member.getType() instanceof LLVMInteropType.Value) {
                // primitive or pointer, can be read or written
                return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
            } else {
                assert member.getType() instanceof LLVMInteropType.Structured;
                // inline struct or array, can be read but not overwritten
                return KeyInfo.READABLE;
            }
        }

        protected int access(LLVMTruffleObject receiver, Number key) {
            if (!(receiver.getExportType() instanceof LLVMInteropType.Array)) {
                return KeyInfo.NONE;
            }

            LLVMInteropType.Array array = (LLVMInteropType.Array) receiver.getExportType();
            long idx = key.longValue();
            if (Long.compareUnsigned(idx, array.getLength()) >= 0) {
                // out of bounds
                return KeyInfo.NONE;
            } else if (array.getElementType() instanceof LLVMInteropType.Value) {
                // primitive or pointer, can be read or written
                return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
            } else {
                assert array.getElementType() instanceof LLVMInteropType.Structured;
                // array of structs or multi-dimensional array, can be read but not overwritten
                return KeyInfo.READABLE;
            }
        }
    }

    @Resolve(message = "READ")
    public abstract static class Read extends Node {

        @Child LLVMForeignGetElementPointerNode getElementPointer = LLVMForeignGetElementPointerNodeGen.create();
        @Child LLVMForeignAccessNode.Read read = LLVMForeignAccessNode.createRead();

        protected Object access(LLVMTruffleObject receiver, String ident) {
            LLVMTruffleObject ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
            return read.execute(ptr, ptr.getExportType());
        }

        protected Object access(LLVMTruffleObject receiver, Number idx) {
            LLVMTruffleObject ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx.longValue());
            return read.execute(ptr, ptr.getExportType());
        }
    }

    @Resolve(message = "WRITE")
    public abstract static class Write extends Node {

        @Child LLVMForeignGetElementPointerNode getElementPointer = LLVMForeignGetElementPointerNodeGen.create();
        @Child LLVMForeignAccessNode.Write write = LLVMForeignAccessNode.createWrite();

        protected Object access(LLVMTruffleObject receiver, String ident, Object value) {
            LLVMTruffleObject ptr = getElementPointer.execute(receiver.getExportType(), receiver, ident);
            doWrite(ptr, value);
            return value;
        }

        protected Object access(LLVMTruffleObject receiver, Number idx, Object value) {
            LLVMTruffleObject ptr = getElementPointer.execute(receiver.getExportType(), receiver, idx.longValue());
            doWrite(ptr, value);
            return value;
        }

        private void doWrite(LLVMTruffleObject ptr, Object value) {
            LLVMInteropType type = ptr.getExportType();
            if (!(type instanceof LLVMInteropType.Value)) {
                // embedded structured type, write not possible
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.WRITE);
            }

            write.execute(ptr, (LLVMInteropType.Value) type, value);
        }
    }

    @MessageResolution(receiverType = Keys.class)
    public static final class Keys implements TruffleObject {

        private final LLVMInteropType.Struct type;

        private Keys(LLVMInteropType.Struct type) {
            this.type = type;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysForeign.ACCESS;
        }

        static boolean isInstance(TruffleObject object) {
            return object instanceof Keys;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            int access(Keys receiver) {
                return receiver.type.getMemberCount();
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                try {
                    return receiver.type.getMember(index).getName();
                } catch (IndexOutOfBoundsException ex) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
            }
        }
    }
}
