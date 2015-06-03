/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.interop;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.interop.messages.*;
import com.oracle.truffle.interop.node.*;

public final class SymbolInvokerImpl extends SymbolInvoker {
    static final FrameDescriptor UNUSED_FRAMEDESCRIPTOR = new FrameDescriptor();

    @Override
    protected Object invoke(Object symbol, Object... arr) throws IOException {
        ForeignObjectAccessNode executeMain = ForeignObjectAccessNode.getAccess(Execute.create(Receiver.create(), arr.length));
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(executeMain, (TruffleObject) symbol, arr));
        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(arr, UNUSED_FRAMEDESCRIPTOR);
        return callTarget.call(frame);
    }

    private static class TemporaryRoot extends RootNode {
        @Child private ForeignObjectAccessNode foreignAccess;
        private final TruffleObject function;
        private final Object[] args;

        public TemporaryRoot(ForeignObjectAccessNode foreignAccess, TruffleObject function, Object... args) {
            this.foreignAccess = foreignAccess;
            this.function = function;
            this.args = args;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return foreignAccess.executeForeign(frame, function, args);
        }
    }

}
