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
package com.oracle.truffle.llvm.test.interop;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

public final class MyBoxedInt implements TruffleObject {
    public int value = 42;

    @Override
    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(MyBoxedInt.class, new ForeignAccess.Factory26() {

            @Override
            public CallTarget accessWrite() {
                return null;
            }

            @Override
            public CallTarget accessUnbox() {
                return Truffle.getRuntime().createCallTarget(new RootNode(null) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return value;
                    }
                });
            }

            @Override
            public CallTarget accessRead() {
                return null;
            }

            @Override
            public CallTarget accessNew(int argumentsLength) {
                return null;
            }

            @Override
            public CallTarget accessKeys() {
                return null;
            }

            @Override
            public CallTarget accessMessage(Message unknown) {
                return null;
            }

            @Override
            public CallTarget accessIsNull() {
                return null;
            }

            @Override
            public CallTarget accessIsExecutable() {
                return null;
            }

            @Override
            public CallTarget accessIsBoxed() {
                return Truffle.getRuntime().createCallTarget(new RootNode(null) {

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return true;
                    }
                });
            }

            @Override
            public CallTarget accessInvoke(int argumentsLength) {
                return null;
            }

            @Override
            public CallTarget accessHasSize() {
                return null;
            }

            @Override
            public CallTarget accessGetSize() {
                return null;
            }

            @Override
            public CallTarget accessExecute(int argumentsLength) {
                return null;
            }

            @Override
            public CallTarget accessKeyInfo() {
                return null;
            }

            @Override
            public CallTarget accessIsPointer() {
                return null;
            }

            @Override
            public CallTarget accessAsPointer() {
                return null;
            }

            @Override
            public CallTarget accessToNative() {
                return null;
            }
        });
    }

}
