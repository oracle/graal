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
import com.oracle.truffle.api.interop.ForeignAccessFactory;
import com.oracle.truffle.api.interop.InteropPredicate;
import com.oracle.truffle.api.interop.exception.UnsupportedMessageException;
import com.oracle.truffle.api.interop.messages.Message;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.interop.ForeignAccessArguments;
import com.oracle.truffle.interop.messages.Execute;
import com.oracle.truffle.interop.messages.Receiver;
import com.oracle.truffle.sl.nodes.call.SLDispatchNode;
import com.oracle.truffle.sl.nodes.call.SLDispatchNodeGen;
import java.math.BigInteger;

/**
 * Implementation of foreing access for {@link SLFunction}.
 */
final class SLFunctionForeignAccess implements ForeignAccessFactory {
    public static final ForeignAccessFactory INSTANCE = new SLFunctionForeignAccess();

    private SLFunctionForeignAccess() {
    }

    @Override
    public InteropPredicate getLanguageCheck() {
        return (com.oracle.truffle.api.interop.TruffleObject o) -> o instanceof SLFunction;
    }

    @Override
    public CallTarget getAccess(Message tree) {
        if (Execute.create(Receiver.create(), 0).matchStructure(tree)) {
            return Truffle.getRuntime().createCallTarget(new SLForeignCallerRootNode());
        } else {
            throw new UnsupportedMessageException(tree.toString() + " not supported");
        }
    }

    private static class SLForeignCallerRootNode extends RootNode {
        @Child private SLDispatchNode dispatch = SLDispatchNodeGen.create();

        @Override
        public Object execute(VirtualFrame frame) {
            SLFunction function = (SLFunction) ForeignAccessArguments.getReceiver(frame.getArguments());
            // the calling convention of interop passes the receiver of a
            // function call (the this object)
            // as an implicit 1st argument; we need to ignore this argument for SL
            Object[] arguments = ForeignAccessArguments.extractUserArguments(1, frame.getArguments());
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof Long) {
                    continue;
                }
                if (arguments[i] instanceof BigInteger) {
                    continue;
                }
                if (arguments[i] instanceof Number) {
                    arguments[i] = ((Number) arguments[i]).longValue();
                }
            }

            return dispatch.executeDispatch(frame, function, arguments);
        }

    }

}
