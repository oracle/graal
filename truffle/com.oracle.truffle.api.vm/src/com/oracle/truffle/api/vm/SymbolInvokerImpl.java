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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

final class SymbolInvokerImpl {
    @SuppressWarnings({"unchecked", "rawtypes"})
    static CallTarget createCallTarget(TruffleLanguage<?> lang, PolyglotEngine engine, Object symbol) {
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
            symbolNode = new ExecuteRoot(type, engine, (TruffleObject) symbol);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    @SuppressWarnings("rawtypes")
    public static RootNode createTemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
        return new TemporaryRoot(lang, foreignAccess, function);
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
                Object tmp = ForeignAccess.send(foreignAccess, frame, function, args);
                return convert.convert(frame, typeProfile.profile(tmp));
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    static class ExecuteRoot extends RootNode {
        private final TruffleObject function;
        private final PolyglotEngine engine;

        @Child private ConvertNode convert;
        @Child private Node foreignAccess;

        private final Assumption debuggingDisabled = PolyglotEngine.Access.DEBUG.assumeNoDebugger();
        private final ContextStore store;

        @SuppressWarnings("rawtypes")
        ExecuteRoot(Class<? extends TruffleLanguage> lang, PolyglotEngine engine, TruffleObject function) {
            super(lang, null, null);
            this.function = function;
            this.engine = engine;
            this.convert = new ConvertNode();
            this.store = engine.context();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ContextStore prev = ExecutionImpl.executionStarted(store);
            try {
                if (!debuggingDisabled.isValid()) {
                    debugExecutionStarted();
                }
                return executeImpl(frame);
            } finally {
                ExecutionImpl.executionEnded(prev);
                if (!debuggingDisabled.isValid()) {
                    debugExecutionEnded();
                }
            }
        }

        @TruffleBoundary
        private void debugExecutionEnded() {
            PolyglotEngine.Access.DEBUG.executionEnded(engine, engine.debugger());
        }

        @TruffleBoundary
        private void debugExecutionStarted() {
            PolyglotEngine.Access.DEBUG.executionStarted(engine, -1, engine.debugger(), null);
        }

        private Object executeImpl(VirtualFrame frame) {
            final Object[] args = frame.getArguments();
            try {
                if (foreignAccess == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    foreignAccess = insert(Message.createExecute(args.length).createNode());
                }
                Object tmp = ForeignAccess.send(foreignAccess, frame, function, args);
                return convert.convert(frame, tmp);
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

        Object convert(VirtualFrame frame, Object obj) {
            if (obj instanceof TruffleObject) {
                return convert(frame, (TruffleObject) obj);
            } else {
                return obj;
            }
        }

        private Object convert(VirtualFrame frame, TruffleObject obj) {
            boolean isBoxedResult = ForeignAccess.sendIsBoxed(isBoxed, frame, obj);
            if (isBoxedProfile.profile(isBoxedResult)) {
                try {
                    return ForeignAccess.sendUnbox(unbox, frame, obj);
                } catch (UnsupportedMessageException e) {
                    return null;
                }
            } else {
                boolean isNullResult = ForeignAccess.sendIsNull(isNull, frame, obj);
                if (isNullResult) {
                    return null;
                }
            }
            return obj;
        }
    }
}
