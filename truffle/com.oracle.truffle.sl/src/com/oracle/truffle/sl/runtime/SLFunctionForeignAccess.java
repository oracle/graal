/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.sl.nodes.call.SLDispatchNode;
import com.oracle.truffle.sl.nodes.call.SLDispatchNodeGen;
import java.math.BigInteger;
import java.util.List;

/**
 * Implementation of foreign access for {@link SLFunction}.
 */
final class SLFunctionForeignAccess implements ForeignAccess.Factory {
    public static final ForeignAccess INSTANCE = ForeignAccess.create(new SLFunctionForeignAccess());

    private SLFunctionForeignAccess() {
    }

    @Override
    public boolean canHandle(TruffleObject o) {
        return o instanceof SLFunction;
    }

    @Override
    public CallTarget accessMessage(Message tree) {
        if (Message.createExecute(0).equals(tree)) {
            return Truffle.getRuntime().createCallTarget(new SLForeignCallerRootNode());
        } else if (Message.IS_NULL.equals(tree)) {
            return Truffle.getRuntime().createCallTarget(new SLForeignNullCheckNode());
        } else {
            throw new IllegalArgumentException(tree.toString() + " not supported");
        }
    }

    private static class SLForeignCallerRootNode extends RootNode {
        @Child private SLDispatchNode dispatch = SLDispatchNodeGen.create();

        @Override
        public Object execute(VirtualFrame frame) {
            SLFunction function = (SLFunction) ForeignAccess.getReceiver(frame);
            // the calling convention of interop passes the receiver of a
            // function call (the this object)
            // as an implicit 1st argument; we need to ignore this argument for SL
            List<Object> args = ForeignAccess.getArguments(frame);
            Object[] arr = args.subList(1, args.size()).toArray();
            for (int i = 0; i < arr.length; i++) {
                Object a = arr[i];
                if (a instanceof Long) {
                    continue;
                }
                if (a instanceof BigInteger) {
                    continue;
                }
                if (a instanceof Number) {
                    arr[i] = ((Number) a).longValue();
                }
            }
            return dispatch.executeDispatch(frame, function, arr);
        }

    }

    private static class SLForeignNullCheckNode extends RootNode {
        @Override
        public Object execute(VirtualFrame frame) {
            Object receiver = ForeignAccess.getReceiver(frame);
            return SLNull.SINGLETON == receiver;
        }
    }
}
