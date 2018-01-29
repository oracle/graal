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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = NativeString.class)
class NativeStringMessageResolution {

    @Resolve(message = "UNBOX")
    abstract static class UnboxNativeStringNode extends Node {

        public Object access(NativeString receiver) {
            if (receiver.nativePointer == 0) {
                return receiver;
            } else {
                return receiver.toJavaString();
            }
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNativeStringNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(NativeString receiver) {
            return true;
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNativeStringNode extends Node {

        public long access(NativeString receiver) {
            return receiver.nativePointer;
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNativeStringNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(NativeString receiver) {
            return true;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class IsNullNativeStringNode extends Node {

        public boolean access(NativeString receiver) {
            return receiver.nativePointer == 0;
        }
    }

    @CanResolve
    abstract static class CanResolveNativeStringNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof NativeString;
        }
    }
}
