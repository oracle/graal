/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
