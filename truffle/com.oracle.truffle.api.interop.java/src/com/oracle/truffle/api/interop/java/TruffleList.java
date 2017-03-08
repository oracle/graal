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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.AbstractList;
import java.util.List;

final class TruffleList<T> extends AbstractList<T> {
    private final TruffleObject array;
    private final TypeAndClass<T> type;
    private final CallTarget call;

    private TruffleList(TypeAndClass<T> elementType, TruffleObject array) {
        this.array = array;
        this.type = elementType;
        this.call = initializeListCall(array);
    }

    public static <T> List<T> create(TypeAndClass<T> elementType, TruffleObject array) {
        return new TruffleList<>(elementType, array);
    }

    @Override
    public T get(int index) {
        final Object item = call.call(type, Message.READ, array, index);
        return type.cast(item);
    }

    @Override
    public T set(int index, T element) {
        type.cast(element);
        T prev = get(index);
        call.call(null, Message.WRITE, array, index, element);
        return prev;
    }

    @Override
    public int size() {
        return (Integer) call.call(null, Message.GET_SIZE, array);
    }

    private static CallTarget initializeListCall(TruffleObject obj) {
        CallTarget res = JavaInterop.ACCESSOR.engine().registerInteropTarget(obj, null, TruffleList.class);
        if (res == null) {
            res = JavaInterop.ACCESSOR.engine().registerInteropTarget(obj, new ListNode(), TruffleList.class);
        }
        return res;
    }

    private static final class ListNode extends RootNode {
        @Child private Node readNode;
        @Child private Node writeNode;
        @Child private Node hasSizeNode;
        @Child private Node getSizeNode;
        @Child private ToJavaNode toJavaNode;

        ListNode() {
            super(TruffleLanguage.class, null, null);
            readNode = Message.READ.createNode();
            writeNode = Message.WRITE.createNode();
            hasSizeNode = Message.HAS_SIZE.createNode();
            getSizeNode = Message.GET_SIZE.createNode();
            toJavaNode = ToJavaNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            TypeAndClass<?> type = (TypeAndClass<?>) args[0];
            Message msg = (Message) args[1];
            TruffleObject receiver = (TruffleObject) args[2];

            Object ret;
            try {
                if (msg == Message.HAS_SIZE) {
                    ret = ForeignAccess.sendHasSize(hasSizeNode, receiver);
                } else if (msg == Message.GET_SIZE) {
                    ret = ForeignAccess.sendGetSize(getSizeNode, receiver);
                } else if (msg == Message.READ) {
                    ret = ForeignAccess.sendRead(readNode, receiver, args[3]);
                } else if (msg == Message.WRITE) {
                    ret = ForeignAccess.sendWrite(writeNode, receiver, args[3], args[4]);
                } else {
                    CompilerDirectives.transferToInterpreter();
                    throw UnsupportedMessageException.raise(msg);
                }
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
