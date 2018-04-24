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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = LLVMTypedForeignObject.class)
public class LLVMTypedForeignObjectMessageResolution {

    @Resolve(message = "IS_POINTER")
    public abstract static class ForeignIsPointer extends Node {

        @Child Node isPointer = Message.IS_POINTER.createNode();

        protected boolean access(LLVMTypedForeignObject receiver) {
            return ForeignAccess.sendIsPointer(isPointer, receiver.getForeign());
        }
    }

    @Resolve(message = "AS_POINTER")
    public abstract static class ForeignAsPointer extends Node {

        @Child Node asPointer = Message.AS_POINTER.createNode();

        protected long access(LLVMTypedForeignObject receiver) {
            try {
                return ForeignAccess.sendAsPointer(asPointer, receiver.getForeign());
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    public abstract static class ForeignToNative extends Node {

        @Child Node toNative = Message.TO_NATIVE.createNode();

        protected Object access(LLVMTypedForeignObject receiver) {
            try {
                Object nativized = ForeignAccess.sendToNative(toNative, receiver.getForeign());
                if (nativized != receiver.getForeign()) {
                    return LLVMTypedForeignObject.create((TruffleObject) nativized, receiver.getType());
                }
                return receiver;
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }
        }
    }

    @Resolve(message = "IS_NULL")
    public abstract static class ForeignIsNull extends Node {

        @Child Node isNull = Message.IS_NULL.createNode();

        protected boolean access(LLVMTypedForeignObject receiver) {
            return ForeignAccess.sendIsNull(isNull, receiver.getForeign());
        }
    }
}
