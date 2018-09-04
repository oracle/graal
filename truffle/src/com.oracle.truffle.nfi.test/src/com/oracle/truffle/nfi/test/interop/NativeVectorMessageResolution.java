/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * 
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * 
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi.test.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = NativeVector.class)
class NativeVectorMessageResolution {

    @Resolve(message = "GET_SIZE")
    abstract static class GetSizeNode extends Node {

        int access(NativeVector vector) {
            return vector.size();
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {

        double access(NativeVector vector, int idx) {
            return vector.get(idx);
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {

        double access(NativeVector vector, int idx, double value) {
            vector.set(idx, value);
            return value;
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNode extends Node {

        boolean access(NativeVector vector) {
            return vector.size() == 1;
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNode extends Node {

        double access(NativeVector vector) {
            if (vector.size() == 1) {
                return vector.get(0);
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.AS_POINTER);
            }
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {

        boolean access(NativeVector vector) {
            return vector.isPointer();
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {

        long access(NativeVector vector) {
            if (vector.isPointer()) {
                return vector.asPointer();
            } else {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedMessageException.raise(Message.AS_POINTER);
            }
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {

        NativeVector access(NativeVector vector) {
            vector.transitionToNative();
            return vector;
        }
    }

    @CanResolve
    abstract static class CanResolveNativeVector extends Node {

        boolean test(TruffleObject object) {
            return object instanceof NativeVector;
        }
    }
}
