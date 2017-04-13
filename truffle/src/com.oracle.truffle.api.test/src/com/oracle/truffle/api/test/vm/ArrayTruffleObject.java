/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static org.junit.Assert.assertNotEquals;

import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;

final class ArrayTruffleObject implements TruffleObject, ForeignAccess.Factory18 {
    private final ForeignAccess access;
    private final Object[] values;
    private final Thread forbiddenDupl;

    ArrayTruffleObject(Object[] values) {
        this(values, null);
    }

    ArrayTruffleObject(Object[] values, Thread forbiddenDupl) {
        this.access = forbiddenDupl == null ? ForeignAccess.create(getClass(), this) : null;
        this.values = values;
        this.forbiddenDupl = forbiddenDupl;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return access != null ? access : ForeignAccess.create(getClass(), this);
    }

    @Override
    public CallTarget accessIsNull() {
        return target(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessIsExecutable() {
        return target(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessIsBoxed() {
        return target(RootNode.createConstantNode(Boolean.FALSE));
    }

    @Override
    public CallTarget accessHasSize() {
        return target(RootNode.createConstantNode(Boolean.TRUE));
    }

    @Override
    public CallTarget accessGetSize() {
        return target(RootNode.createConstantNode(values.length));
    }

    @Override
    public CallTarget accessUnbox() {
        return null;
    }

    @Override
    public CallTarget accessRead() {
        return target(new IndexNode());
    }

    @Override
    public CallTarget accessWrite() {
        return null;
    }

    @Override
    public CallTarget accessExecute(int argumentsLength) {
        return null;
    }

    @Override
    public CallTarget accessInvoke(int argumentsLength) {
        if (argumentsLength == 0) {
            return target(new DuplNode());
        }
        if (argumentsLength == 1) {
            return target(new InvokeNode());
        }
        return null;
    }

    @Override
    public CallTarget accessNew(int argumentsLength) {
        return null;
    }

    @Override
    public CallTarget accessKeys() {
        return null;
    }

    @Override
    public CallTarget accessMessage(Message unknown) {
        return null;
    }

    private static CallTarget target(RootNode node) {
        return Truffle.getRuntime().createCallTarget(node);
    }

    private final class IndexNode extends RootNode {
        IndexNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int index = ((Number) ForeignAccess.getArguments(frame).get(0)).intValue();
            if (values[index] instanceof Object[]) {
                return new ArrayTruffleObject((Object[]) values[index]);
            } else {
                return values[index];
            }
        }
    }

    private final class InvokeNode extends RootNode {
        InvokeNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final List<Object> args = ForeignAccess.getArguments(frame);
            if (!"get".equals(args.get(0))) {
                return null;
            }
            int index = ((Number) args.get(1)).intValue();
            if (values[index] instanceof Object[]) {
                return new ArrayTruffleObject((Object[]) values[index]);
            } else {
                return values[index];
            }
        }
    }

    private final class DuplNode extends RootNode {
        DuplNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final List<Object> args = ForeignAccess.getArguments(frame);
            if (!"dupl".equals(args.get(0))) {
                return null;
            }
            assertNotEquals("Cannot allocate duplicate on forbidden thread", forbiddenDupl, Thread.currentThread());
            return new ArrayTruffleObject(values);
        }
    }
}
