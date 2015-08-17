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
package com.oracle.truffle.api.interop.impl;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.*;

public final class SymbolInvokerImpl extends SymbolInvoker {
    static final FrameDescriptor UNUSED_FRAMEDESCRIPTOR = new FrameDescriptor();

    @SuppressWarnings("unchecked")
    @Override
    protected Object invoke(TruffleLanguage<?> lang, Object symbol, Object... arr) throws IOException {
        if (symbol instanceof String) {
            return symbol;
        }
        if (symbol instanceof Number) {
            return symbol;
        }
        if (symbol instanceof Boolean) {
            return symbol;
        }
        Class<? extends TruffleLanguage<?>> type = (Class<? extends TruffleLanguage<?>>) lang.getClass();
        Node executeMain = Message.createExecute(arr.length).createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(type, executeMain, (TruffleObject) symbol, arr));
        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(arr, UNUSED_FRAMEDESCRIPTOR);
        return callTarget.call(frame);
    }

    private static class TemporaryRoot extends RootNode {
        @Child private Node foreignAccess;
        @Child private ConvertNode convert;
        private final TruffleObject function;
        private final Object[] args;

        public TemporaryRoot(Class<? extends TruffleLanguage<?>> lang, Node foreignAccess, TruffleObject function, Object... args) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.convert = new ConvertNode();
            this.function = function;
            this.args = args;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object tmp = ForeignAccess.execute(foreignAccess, frame, function, args);
            return convert.convert(frame, tmp);
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
            try {
                isBoxedResult = ForeignAccess.execute(isBoxed, frame, obj);
            } catch (IllegalArgumentException ex) {
                isBoxedResult = false;
            }
            if (Boolean.TRUE.equals(isBoxedResult)) {
                return ForeignAccess.execute(unbox, frame, obj);
            } else {
                try {
                    Object isNullResult = ForeignAccess.execute(isNull, frame, obj);
                    if (Boolean.TRUE.equals(isNullResult)) {
                        return null;
                    }
                } catch (IllegalArgumentException ex) {
                    // fallthrough
                }
            }
            return obj;
        }
    }
}
