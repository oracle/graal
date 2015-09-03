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

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.*;
import java.io.*;

final class SymbolInvokerImpl {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CallTarget createCallTarget(TruffleLanguage<?> lang, Object symbol, Object... arr) throws IOException {
        Class<? extends TruffleLanguage<?>> type;
        if (lang != null) {
            type = (Class) lang.getClass();
        } else {
            type = (Class) TruffleLanguage.class;
        }
        RootNode symbolNode;
        if ((symbol instanceof String) || (symbol instanceof Number) || (symbol instanceof Boolean) || (symbol instanceof Character)) {
            symbolNode = new ConstantRootNode(type, symbol);
        } else {
            Node executeMain = Message.createExecute(arr.length).createNode();
            symbolNode = new TemporaryRoot(type, executeMain, (TruffleObject) symbol, arr.length);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    private static final class ConstantRootNode extends RootNode {

        private final Object value;

        public ConstantRootNode(Class<? extends TruffleLanguage<?>> lang, Object value) {
            super(lang, null, null);
            this.value = value;
        }

        @Override
        public Object execute(VirtualFrame vf) {
            return value;
        }
    }

    private static class TemporaryRoot extends RootNode {
        @Child private Node foreignAccess;
        @Child private ConvertNode convert;
        private final int argumentLength;
        private final TruffleObject function;

        public TemporaryRoot(Class<? extends TruffleLanguage<?>> lang, Node foreignAccess, TruffleObject function, int argumentLength) {
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
                throw new ArgumentsMishmashException();
            }
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
