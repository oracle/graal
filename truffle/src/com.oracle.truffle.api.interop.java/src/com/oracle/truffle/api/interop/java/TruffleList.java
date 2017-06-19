/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class TruffleList<T> extends AbstractList<T> {
    private final TruffleObject array;
    private final TypeAndClass<T> type;
    private final CallTarget callRead;
    private final CallTarget callGetSize;
    private final CallTarget callWrite;

    private TruffleList(TypeAndClass<T> elementType, TruffleObject array) {
        this.array = array;
        this.type = elementType;
        this.callRead = initializeListCall(array, Message.READ);
        this.callWrite = initializeListCall(array, Message.WRITE);
        this.callGetSize = initializeListCall(array, Message.GET_SIZE);
    }

    public static <T> List<T> create(TypeAndClass<T> elementType, TruffleObject array) {
        return new TruffleList<>(elementType, array);
    }

    @Override
    public T get(int index) {
        final Object item = callRead.call(type, array, index);
        return type.cast(item);
    }

    @Override
    public T set(int index, T element) {
        type.cast(element);
        T prev = get(index);
        callWrite.call(null, array, index, element);
        return prev;
    }

    @Override
    public int size() {
        return (Integer) callGetSize.call(null, array);
    }

    private static CallTarget initializeListCall(TruffleObject obj, Message msg) {
        CallTarget res = JavaInterop.lookupOrRegisterComputation(obj, null, TruffleList.class, msg);
        if (res == null) {
            res = JavaInterop.lookupOrRegisterComputation(obj, new ListNode(msg), TruffleList.class, msg);
        }
        return res;
    }

    private static final class ListNode extends RootNode {
        private final Message msg;
        @Child private Node node;
        @Child private ToJavaNode toJavaNode;

        ListNode(Message msg) {
            super(null);
            this.msg = msg;
            this.node = msg.createNode();
            this.toJavaNode = ToJavaNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            TypeAndClass<?> type = (TypeAndClass<?>) args[0];
            TruffleObject receiver = (TruffleObject) args[1];

            Object ret;
            try {
                if (msg == Message.GET_SIZE) {
                    ret = ForeignAccess.sendGetSize(node, receiver);
                } else if (msg == Message.READ) {
                    ret = ForeignAccess.sendRead(node, receiver, args[2]);
                } else if (msg == Message.WRITE) {
                    ret = ForeignAccess.sendWrite(node, receiver, args[2], JavaInterop.asTruffleValue(args[3]));
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(msg);
                }
            } catch (UnknownIdentifierException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IndexOutOfBoundsException(Objects.toString(args[2]));
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw ex.raise();
            }

            if (type != null) {
                return toJavaNode.execute(ret, type);
            } else {
                return ret;
            }
        }

    }

}
