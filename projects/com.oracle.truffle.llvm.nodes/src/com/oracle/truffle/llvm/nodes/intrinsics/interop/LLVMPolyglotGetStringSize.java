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
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.interop.LLVMAsForeignNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMPolyglotGetStringSizeNodeGen.BoxedGetStringSizeNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class)})
public abstract class LLVMPolyglotGetStringSize extends LLVMIntrinsic {

    @Specialization
    long getForeignStringSize(LLVMManagedPointer object,
                    @Cached("create()") LLVMAsForeignNode asForeign,
                    @Cached("create()") BoxedGetStringSize getSize) {
        return getSize.execute(asForeign.execute(object));
    }

    @Specialization
    long getStringSize(String str) {
        return str.length();
    }

    abstract static class BoxedGetStringSize extends LLVMNode {

        @Child Node isBoxed = Message.IS_BOXED.createNode();
        @Child Node unbox = Message.UNBOX.createNode();

        abstract long execute(TruffleObject object);

        boolean checkBoxed(TruffleObject object) {
            return ForeignAccess.sendIsBoxed(isBoxed, object);
        }

        @Specialization(guards = "checkBoxed(object)")
        long doBoxed(TruffleObject object) {
            try {
                String unboxed = (String) ForeignAccess.sendUnbox(unbox, object);
                return unboxed.length();
            } catch (UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Fallback
        @TruffleBoundary
        @SuppressWarnings("unused")
        long doFail(TruffleObject object) {
            throw new LLVMPolyglotException(this, "Polyglot value is not a string.");
        }

        public static BoxedGetStringSize create() {
            return BoxedGetStringSizeNodeGen.create();
        }
    }
}
