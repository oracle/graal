/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNodeGen.ForeignReadStringNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNodeGen.PointerReadStringNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMReadStringNode extends LLVMNode {

    @Child PointerReadStringNode readOther;

    public abstract String executeWithTarget(Object address);

    @Specialization
    String readString(String address) {
        return address;
    }

    @Specialization(guards = "isString(foreign)")
    String readString(LLVMManagedPointer foreign) {
        return (String) foreign.getObject();
    }

    @Specialization(guards = "foreigns.isForeign(pointer)")
    String readForeign(LLVMManagedPointer pointer,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") LLVMAsForeignLibrary foreigns,
                    @Cached("create()") ForeignReadStringNode read) {
        return read.execute(pointer, foreigns.asForeign(pointer));
    }

    @Fallback
    String readOther(Object address) {
        if (readOther == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            readOther = insert(PointerReadStringNode.create());
        }
        return readOther.execute(address);
    }

    protected static boolean isString(LLVMManagedPointer pointer) {
        return pointer.getOffset() == 0 && pointer.getObject() instanceof String;
    }

    abstract static class Dummy extends LLVMNode {

        protected abstract LLVMManagedPointer execute();
    }

    abstract static class ForeignReadStringNode extends LLVMNode {

        protected abstract String execute(LLVMManagedPointer pointer, Object foreign);

        @Specialization(limit = "3")
        String doDefault(@SuppressWarnings("unused") LLVMManagedPointer object, Object foreign,
                        @CachedLibrary("foreign") InteropLibrary interop,
                        @Cached PointerReadStringNode read) {
            if (interop.isString(foreign)) {
                try {
                    return interop.asString(foreign);
                } catch (UnsupportedMessageException e) {
                }
            }
            return read.execute(object);
        }

        public static ForeignReadStringNode create() {
            return ForeignReadStringNodeGen.create();
        }
    }

    abstract static class PointerReadStringNode extends LLVMNode {

        @Child private LLVMLoadNode read = LLVMI8LoadNode.create();

        protected abstract String execute(Object address);

        boolean isReadOnlyMemory(LLVMPointer address) {
            CompilerAsserts.neverPartOfCompilation();
            LLVMGlobal global = lookupContextReference(LLVMLanguage.class).get().findGlobal(address);
            if (global != null) {
                return global.isReadOnly();
            } else {
                return false;
            }
        }

        @Specialization(guards = {"cachedAddress.isSame(address)", "isReadOnlyMemory(cachedAddress)"})
        String doCachedPointer(@SuppressWarnings("unused") LLVMPointer address,
                        @Cached("address") @SuppressWarnings("unused") LLVMPointer cachedAddress,
                        @Cached("doReadString(cachedAddress)") String result) {
            return result;
        }

        @Specialization(replaces = "doCachedPointer")
        String doReadString(LLVMPointer address) {
            LLVMPointer ptr = address;
            int length = 0;
            while ((byte) read.executeWithTarget(ptr) != 0) {
                length++;
                ptr = ptr.increment(Byte.BYTES);
            }

            char[] string = new char[length];

            ptr = address;
            for (int i = 0; i < length; i++) {
                string[i] = (char) Byte.toUnsignedInt((byte) read.executeWithTarget(ptr));
                ptr = ptr.increment(Byte.BYTES);
            }

            return toString(string);
        }

        @TruffleBoundary
        private static String toString(char[] string) {
            return new String(string);
        }

        public static PointerReadStringNode create() {
            return PointerReadStringNodeGen.create();
        }
    }
}
