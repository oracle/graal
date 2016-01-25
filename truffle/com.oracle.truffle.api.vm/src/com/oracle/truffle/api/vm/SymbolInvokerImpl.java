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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class SymbolInvokerImpl {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CallTarget createCallTarget(TruffleLanguage<?> lang, Object symbol, Object... arr) {
        Class<? extends TruffleLanguage<?>> type;
        if (lang != null) {
            type = (Class) lang.getClass();
        } else {
            type = (Class) TruffleLanguage.class;
        }
        RootNode symbolNode;
        if ((symbol instanceof String) || (symbol instanceof Number) || (symbol instanceof Boolean) || (symbol instanceof Character)) {
            symbolNode = RootNode.createConstantNode(symbol);
        } else {
            Node executeMain = Message.createExecute(arr.length).createNode();
            symbolNode = createTemporaryRoot(type, executeMain, (TruffleObject) symbol, arr.length);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    @SuppressWarnings("rawtypes")
    public static RootNode createTemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function, int argumentLength) {
        return new TemporaryRoot(lang, foreignAccess, function, argumentLength);
    }

    static class TemporaryRoot extends RootNode {
        @Child private Node foreignAccess;
        @Child private ConvertNode convert;
        private final int argumentLength;
        private final TruffleObject function;

        @SuppressWarnings("rawtypes")
        public TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function, int argumentLength) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.convert = new ConvertNode();
            this.function = function;
            this.argumentLength = argumentLength;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            if (args.length != argumentLength) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new ArgumentsMishmashException();
            }
            try {
                Object tmp = ForeignAccess.send(foreignAccess, frame, function, args);
                return convert.convert(frame, tmp);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static final class ConvertNode extends Node {
        @Child private Node isNull;
        @Child private Node isBoxed;
        @Child private Node unbox;

        public ConvertNode() {
            this.isNull = Message.IS_NULL.createNode();
            this.isBoxed = Message.IS_BOXED.createNode();
            this.unbox = Message.UNBOX.createNode();
        }

        Object convert(VirtualFrame frame, Object obj) {
            if (obj instanceof TruffleObject) {
                return convert(frame, (TruffleObject) obj);
            } else {
                return obj;
            }
        }

        private Object convert(VirtualFrame frame, TruffleObject obj) {
            Object isBoxedResult;
            isBoxedResult = ForeignAccess.sendIsBoxed(isBoxed, frame, obj);
            if (Boolean.TRUE.equals(isBoxedResult)) {
                try {
                    return ForeignAccess.sendUnbox(unbox, frame, obj);
                } catch (UnsupportedMessageException e) {
                    return null;
                }
            } else {
                Object isNullResult = ForeignAccess.sendIsNull(isNull, frame, obj);
                if (Boolean.TRUE.equals(isNullResult)) {
                    return null;
                }
            }
            return obj;
        }
    }
}
