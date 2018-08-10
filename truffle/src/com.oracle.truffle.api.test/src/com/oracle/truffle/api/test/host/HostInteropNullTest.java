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
package com.oracle.truffle.api.test.host;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class HostInteropNullTest {

    @FunctionalInterface
    public interface StringCallback {

        void call(String obj);
    }

    class TestRootNode extends RootNode {

        private final TruffleObject receiver;
        @Child Node execute = Message.EXECUTE.createNode();

        TestRootNode(TruffleObject receiver) {
            super(null);
            this.receiver = receiver;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return ForeignAccess.sendExecute(execute, receiver, frame.getArguments());
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new RuntimeException(ex);
            }
        }
    }

    static class TestNull implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return TestNullMessageResolutionForeign.ACCESS;
        }
    }

    @MessageResolution(receiverType = TestNull.class)
    static class TestNullMessageResolution {

        @Resolve(message = "IS_NULL")
        abstract static class TestNullIsNull extends Node {
            boolean access(TestNull str) {
                return str != null;
            }
        }

        @CanResolve
        abstract static class CanResolveTestNull extends Node {

            boolean test(TruffleObject object) {
                return object instanceof TestNull;
            }
        }
    }

}
