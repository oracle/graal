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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.vm.PolyglotEngine.PolyglotRootNode;

abstract class SymbolInvokerImpl {

    @SuppressWarnings({"unchecked", "rawtypes"})
    static CallTarget createExecuteSymbol(TruffleLanguage<?> lang, PolyglotEngine engine, Object symbol) {
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
            symbolNode = new ForeignExecuteRoot(type, engine, (TruffleObject) symbol);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    @SuppressWarnings("rawtypes")
    public static RootNode createTemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
        return new TemporaryRoot(lang, foreignAccess, function);
    }

    static void unwrapArgs(PolyglotEngine engine, final Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof EngineTruffleObject) {
                final EngineTruffleObject engineObject = (EngineTruffleObject) args[i];
                engineObject.assertEngine(engine);
                args[i] = engineObject.getDelegate();
            }
            args[i] = JavaInterop.asTruffleValue(args[i]);
        }
    }

    static class TemporaryRoot extends RootNode {
        @Child private Node foreignAccess;
        @Child private ConvertNode convert;
        private final TruffleObject function;
        private final ValueProfile typeProfile = ValueProfile.createClassProfile();

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.convert = new ConvertNode();
            this.function = function;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            try {
                Object tmp = ForeignAccess.send(foreignAccess, function, args);
                return convert.convert(typeProfile.profile(tmp));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    static final class ForeignExecuteRoot extends PolyglotRootNode {
        private final TruffleObject function;

        @Child private ConvertNode convert;
        @Child private Node foreignAccess;

        @SuppressWarnings("rawtypes")
        ForeignExecuteRoot(Class<? extends TruffleLanguage> language, PolyglotEngine engine, TruffleObject function) {
            super(language, engine);
            this.function = function;
            this.convert = new ConvertNode();
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            unwrapArgs(engine, args);
            try {
                if (foreignAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    foreignAccess = insert(Message.createExecute(args.length).createNode());
                }
                Object tmp = ForeignAccess.send(foreignAccess, function, args);
                return convert.convert(tmp);
            } catch (ArityException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                foreignAccess = insert(Message.createExecute(args.length).createNode());
                return execute(frame);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }

    }

    private static final class ConvertNode extends Node {
        @Child private Node isNull;
        @Child private Node isBoxed;
        @Child private Node unbox;
        private final ConditionProfile isBoxedProfile = ConditionProfile.createBinaryProfile();

        ConvertNode() {
            this.isNull = Message.IS_NULL.createNode();
            this.isBoxed = Message.IS_BOXED.createNode();
            this.unbox = Message.UNBOX.createNode();
        }

        Object convert(Object obj) {
            if (obj instanceof TruffleObject) {
                return convert((TruffleObject) obj);
            } else {
                return obj;
            }
        }

        private Object convert(TruffleObject obj) {
            boolean isBoxedResult = ForeignAccess.sendIsBoxed(isBoxed, obj);
            if (isBoxedProfile.profile(isBoxedResult)) {
                try {
                    Object newValue = ForeignAccess.sendUnbox(unbox, obj);
                    return new ConvertedObject(obj, newValue);
                } catch (UnsupportedMessageException e) {
                    return null;
                }
            } else {
                boolean isNullResult = ForeignAccess.sendIsNull(isNull, obj);
                if (isNullResult) {
                    return new ConvertedObject(obj, null);
                }
            }
            return obj;
        }
    }
}
