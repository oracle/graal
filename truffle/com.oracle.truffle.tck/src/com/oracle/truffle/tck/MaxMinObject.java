/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tck;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class MaxMinObject implements TruffleObject {
    private final boolean max;

    public MaxMinObject(boolean max) {
        this.max = max;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return ForeignAccess.create(MaxMinObject.class, new AF(max));
    }

    static final class AF implements ForeignAccess.Factory10 {
        private final boolean max;

        public AF(boolean max) {
            this.max = max;
        }

        @Override
        public CallTarget accessIsNull() {
            return null;
        }

        @Override
        public CallTarget accessIsExecutable() {
            return null;
        }

        @Override
        public CallTarget accessIsBoxed() {
            return null;
        }

        @Override
        public CallTarget accessHasSize() {
            return null;
        }

        @Override
        public CallTarget accessGetSize() {
            return null;
        }

        @Override
        public CallTarget accessUnbox() {
            return null;
        }

        @Override
        public CallTarget accessRead() {
            return null;
        }

        @Override
        public CallTarget accessWrite() {
            return null;
        }

        @Override
        public CallTarget accessExecute(int argumentsLength) {
            if (argumentsLength == 2) {
                MaxMinNode maxNode = MaxMinObjectFactory.MaxMinNodeGen.create(max, MaxMinObjectFactory.UnboxNodeGen.create(new ReadArgNode(0)),
                                MaxMinObjectFactory.UnboxNodeGen.create(new ReadArgNode(1)));
                return Truffle.getRuntime().createCallTarget(maxNode);
            }
            return null;
        }

        @Override
        public CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        @Override
        public CallTarget accessMessage(Message unknown) {
            return null;
        }
    }

    static class ReadArgNode extends Node {
        private final int argIndex;

        public ReadArgNode(int argIndex) {
            this.argIndex = argIndex;
        }

        public Object execute(VirtualFrame frame) {
            return ForeignAccess.getArguments(frame).get(argIndex);
        }
    }

    @NodeChildren({@NodeChild(value = "valueNode", type = ReadArgNode.class)})
    abstract static class UnboxNode extends Node {
        @Child private Node unbox;
        @Child private Node isBoxed;

        public abstract Object executeUnbox(VirtualFrame frame);

        @Specialization
        public int executeUnbox(int value) {
            return value;
        }

        @Specialization
        public long executeUnbox(long value) {
            return value;
        }

        @Specialization
        public String executeUnbox(String value) {
            return value;
        }

        @Specialization(guards = "isBoxedPrimitive(frame, foreignValue)")
        public Object executeUnbox(VirtualFrame frame, TruffleObject foreignValue) {
            if (unbox == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                unbox = insert(Message.UNBOX.createNode());
            }
            return ForeignAccess.execute(unbox, frame, foreignValue);
        }

        protected final boolean isBoxedPrimitive(VirtualFrame frame, TruffleObject object) {
            if (isBoxed == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBoxed = insert(Message.IS_BOXED.createNode());
            }
            return (boolean) ForeignAccess.execute(isBoxed, frame, object);
        }

    }

    @NodeChildren({@NodeChild(value = "firstNode", type = UnboxNode.class), @NodeChild(value = "secondNode", type = UnboxNode.class)})
    abstract static class MaxMinNode extends RootNode {
        private final boolean max;

        MaxMinNode(boolean max) {
            super(MMLanguage.class, null, null);
            this.max = max;
        }

        @Specialization
        public int execute(int first, int second) {
            return max ? Math.max(first, second) : Math.min(first, second);
        }

        @Specialization
        public long execute(long first, long second) {
            return max ? Math.max(first, second) : Math.min(first, second);
        }

        @Specialization
        public double execute(double first, double second) {
            return max ? Math.max(first, second) : Math.min(first, second);
        }
    }

    private abstract class MMLanguage extends TruffleLanguage {
        public MMLanguage(Env env) {
            super(env);
        }
    }
}
