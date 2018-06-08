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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.BindSignatureNodeFactory.PointerBindSignatureNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversion.AsStringNode;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;

@MessageResolution(receiverType = NativePointer.class)
class NativePointerMessageResolution {

    @Resolve(message = "INVOKE")
    abstract static class BindNode extends Node {

        @Child protected BindSignatureNode bind = PointerBindSignatureNodeGen.create();

        public TruffleObject access(NativePointer receiver, String method, Object[] args) {
            if (!"bind".equals(method)) {
                throw UnknownIdentifierException.raise(method);
            }
            if (args.length != 1) {
                throw ArityException.raise(1, args.length);
            }

            return bind.execute(receiver, args[0]);
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsNativePointerNode extends Node {

        public boolean access(@SuppressWarnings("unused") NativePointer receiver) {
            return true;
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsNativePointerNode extends Node {

        public long access(NativePointer receiver) {
            return receiver.nativePointer;
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativePointerNode extends Node {

        public NativePointer access(NativePointer receiver) {
            return receiver;
        }
    }

    @Resolve(message = "UNBOX")
    abstract static class UnboxNativePointerNode extends Node {

        public long access(NativePointer receiver) {
            return receiver.nativePointer;
        }
    }

    @Resolve(message = "IS_BOXED")
    abstract static class IsBoxedNativePointerNode extends Node {

        @SuppressWarnings("unused")
        public boolean access(NativePointer receiver) {
            return true;
        }
    }

    @Resolve(message = "IS_NULL")
    abstract static class IsNullNativePointerNode extends Node {

        public boolean access(NativePointer receiver) {
            return receiver.nativePointer == 0;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class NativePointerKeysNode extends Node {

        private static final KeysArray KEYS = new KeysArray(new String[]{"bind"});

        @SuppressWarnings("unused")
        public TruffleObject access(NativePointer receiver) {
            return KEYS;
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class NativePointerKeyInfoNode extends Node {

        @Child private AsStringNode asString = AsStringNodeGen.create(true);

        @SuppressWarnings("unused")
        public int access(NativePointer receiver, Object arg) {
            String identifier = asString.execute(arg);
            if ("bind".equals(identifier)) {
                return KeyInfo.INVOCABLE;
            } else {
                return KeyInfo.NONE;
            }
        }
    }

    @CanResolve
    abstract static class CanResolveNativePointerNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof NativePointer;
        }
    }
}
