/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = ErrnoMirror.class)
public final class ErrnoMirrorMessageResolution {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteErrnoMirror extends Node {

        @SuppressWarnings("unused")
        Object access(ErrnoMirror receiver, Object[] args) {
            return new Target_com_oracle_truffle_nfi_impl_NativePointer(ErrnoMirror.getErrnoMirrorLocation().rawValue());
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeErrnoMirror extends Node {

        @SuppressWarnings("unused")
        public ErrnoMirror access(ErrnoMirror receiver, String method, Object[] args) {
            if (!"bind".equals(method)) {
                throw UnknownIdentifierException.raise(method);
            }
            return receiver;
        }
    }

    @CanResolve
    abstract static class IsErrnoMirror extends Node {

        boolean test(TruffleObject object) {
            return object instanceof ErrnoMirror;
        }
    }
}
