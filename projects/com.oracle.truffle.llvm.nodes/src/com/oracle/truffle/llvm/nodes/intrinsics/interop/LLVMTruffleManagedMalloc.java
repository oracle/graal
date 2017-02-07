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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMTruffleManagedMalloc extends LLVMIntrinsic {

    @MessageResolution(receiverType = ManagedMallocObject.class, language = LLVMLanguage.class)
    public static class ManagedMallocForeignAccess {

        @CanResolve
        public abstract static class Check extends Node {

            protected static boolean test(TruffleObject receiver) {
                return receiver instanceof ManagedMallocObject;
            }

        }

        @Resolve(message = "HAS_SIZE")
        public abstract static class ForeignHasSizeNode extends Node {

            protected Object access(@SuppressWarnings("unused") ManagedMallocObject malloc) {
                return true;
            }

        }

        @Resolve(message = "GET_SIZE")
        public abstract static class ForeignGetSizeNode extends Node {

            protected Object access(ManagedMallocObject malloc) {
                return malloc.getSize();
            }

        }

        @Resolve(message = "READ")
        public abstract static class ForeignReadNode extends Node {

            protected Object access(ManagedMallocObject malloc, int index) {
                return malloc.get(index);
            }

        }

        @Resolve(message = "WRITE")
        public abstract static class ForeignWriteNode extends Node {

            protected Object access(ManagedMallocObject malloc, int index, Object value) {
                malloc.set(index, value);
                return value;
            }

        }

    }

    public static class ManagedMallocObject implements TruffleObject {

        private final Object[] contents;

        public ManagedMallocObject(int entries) {
            contents = new Object[entries];
        }

        public Object get(int index) {
            return contents[index];
        }

        public void set(int index, Object value) {
            contents[index] = value;
        }

        public int getSize() {
            return contents.length;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ManagedMallocForeignAccessForeign.ACCESS;
        }

    }

    @Specialization
    public Object executeIntrinsic(long size) {
        if (size < 0) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Can't truffle_managed_malloc less than zero bytes");
        }

        long roundedSize = size + ((LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES - size) % LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES);

        if (roundedSize / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException("Can't truffle_managed_malloc for more than 2^31 objects");
        }

        return new ManagedMallocObject((int) (roundedSize / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES));
    }

}
