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
package com.oracle.truffle.llvm.runtime.debug.type;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = LLVMSourceType.class)
public class LLVMSourceTypeMessageResolution {

    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysResolution extends Node {

        public boolean access(LLVMSourceType receiver) {
            return receiver.getElementCount() != 0;
        }

    }

    @Resolve(message = "KEYS")
    abstract static class KeysResolution extends Node {

        @TruffleBoundary
        public Object access(LLVMSourceType receiver) {
            if (receiver.getElementCount() == 0) {
                return SubTypes.EMPTY;
            }

            final String[] keys = new String[receiver.getElementCount()];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = receiver.getElementName(i);
            }
            return new SubTypes(keys);
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoResolution extends Node {

        private static final int KEY_INFO = KeyInfo.READABLE;

        @TruffleBoundary
        public Object access(LLVMSourceType receiver, Object key) {
            if (key instanceof String) {
                LLVMSourceType element = receiver.getElementType((String) key);
                if (element != null) {
                    return KEY_INFO;
                }
            }
            return KeyInfo.NONE;
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadResolution extends Node {

        @TruffleBoundary
        public Object access(LLVMSourceType receiver, Object key) {
            if (key instanceof String) {
                LLVMSourceType element = receiver.getElementType((String) key);
                if (element != null) {
                    return element;
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(String.valueOf(key));
        }
    }

    static final class SubTypes implements TruffleObject {

        private static final SubTypes EMPTY = new SubTypes(new String[0]);

        public static boolean isInstance(TruffleObject object) {
            return object instanceof SubTypes;
        }

        private final String[] keys;

        SubTypes(String[] keys) {
            this.keys = keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return SubTypesMessageResolutionForeign.ACCESS;
        }

        @MessageResolution(receiverType = LLVMSourceTypeMessageResolution.SubTypes.class)
        static final class SubTypesMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class HasSizeResolution extends Node {

                public Object access(@SuppressWarnings("unused") SubTypes receiver) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class GetSizeResolution extends Node {

                public Object access(SubTypes receiver) {
                    return receiver.keys.length;
                }
            }

            @Resolve(message = "READ")
            abstract static class ReadResolution extends Node {

                public String access(SubTypes receiver, int index) {
                    if (index >= 0 && index < receiver.keys.length) {
                        return receiver.keys[index];
                    } else {
                        CompilerDirectives.transferToInterpreter();
                        throw UnknownIdentifierException.raise(String.valueOf(index));
                    }
                }
            }

        }
    }

}
