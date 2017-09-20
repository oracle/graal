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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.VMAccessor.JAVAINTEROP;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;

abstract class PolyglotRootNode extends RootNode {

    private static final CallTarget VOID_TARGET = Truffle.getRuntime().createCallTarget(new VoidRootNode());

    final PolyglotEngine engine;

    PolyglotRootNode(PolyglotEngine engine) {
        super(null);
        this.engine = engine;
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        Object prev = engine.enter();
        try {
            return executeImpl(frame);
        } finally {
            engine.leave(prev);
        }
    }

    protected abstract Object executeImpl(VirtualFrame frame);

    @SuppressWarnings("unchecked")
    static CallTarget createExecuteSymbol(PolyglotEngine engine, Class<?> type) {
        RootNode symbolNode;
        if (isPrimitiveType(type)) {
            // no conversion necessary just return value
            return VOID_TARGET;
        } else {
            symbolNode = new ForeignExecuteRootNode(engine, (Class<? extends TruffleObject>) type);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    @SuppressWarnings("unchecked")
    static CallTarget createAsJava(PolyglotEngine engine, Class<?> type) {
        RootNode symbolNode;
        if (isPrimitiveType(type)) {
            // no conversion necessary just return value
            return VOID_TARGET;
        } else {
            symbolNode = new AsJavaRootNode(engine, (Class<? extends TruffleObject>) type);
        }
        return Truffle.getRuntime().createCallTarget(symbolNode);
    }

    private static boolean isPrimitiveType(Class<?> type) {
        return type == String.class || type == Boolean.class || type == Byte.class || type == Short.class || type == Integer.class || type == Long.class || type == Character.class ||
                        type == Float.class || type == Double.class;
    }

    static void unwrapArgs(PolyglotEngine engine, final Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof EngineTruffleObject) {
                final EngineTruffleObject engineObject = (EngineTruffleObject) args[i];
                engineObject.assertEngine(engine);
                args[i] = engineObject.getDelegate();
            }
            if (args[i] != null && !isPrimitiveType(args[i].getClass())) {
                args[i] = JavaInterop.asTruffleObject(args[i]);
            }
        }
    }

    static CallTarget createSend(PolyglotEngine engine, Message message) {
        return Truffle.getRuntime().createCallTarget(new ForeignSendRootNode(engine, message));
    }

    static CallTarget createEval(PolyglotEngine engine, Language language, Source source) {
        return Truffle.getRuntime().createCallTarget(new EvalRootNode(engine, language, source));
    }

    private static final class ForeignSendRootNode extends PolyglotRootNode {
        @Child private ConvertNode returnConvertNode;
        @Child private Node messageNode;
        @Child private Node toJavaNode;

        ForeignSendRootNode(PolyglotEngine engine, Message message) {
            super(engine);
            this.returnConvertNode = new ConvertNode();
            this.messageNode = message.createNode();
            this.toJavaNode = JAVAINTEROP.createToJavaNode();
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Object executeImpl(VirtualFrame frame) {
            final TruffleObject receiver = ForeignAccess.getReceiver(frame);
            final Object[] args = ForeignAccess.getArguments(frame).toArray();
            unwrapArgs(engine, args);
            Object tmp = ForeignAccess.execute(messageNode, null, receiver, args);
            return returnConvertNode.convert(tmp);
        }
    }

    private static final class AsJavaRootNode extends PolyglotRootNode {
        @Child private Node toJavaNode;
        private final Class<? extends TruffleObject> receiverType;

        AsJavaRootNode(PolyglotEngine engine, Class<? extends TruffleObject> receiverType) {
            super(engine);
            this.receiverType = receiverType;
            this.toJavaNode = JAVAINTEROP.createToJavaNode();
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            Object[] args = frame.getArguments();
            final Class<?> targetType = (Class<?>) args[1];
            if (receiverType.isInstance(args[0])) {
                final TruffleObject value = receiverType.cast(args[0]);
                return JAVAINTEROP.toJava(toJavaNode, targetType, value);
            } else {
                throw new ClassCastException();
            }
        }

    }

    static final class ForeignExecuteRootNode extends PolyglotRootNode {
        @Child private ConvertNode returnConvertNode;
        @Child private Node executeNode;

        private final Class<? extends TruffleObject> receiverType;

        ForeignExecuteRootNode(PolyglotEngine engine, Class<? extends TruffleObject> receiverType) {
            super(engine);
            this.receiverType = receiverType;
            this.returnConvertNode = new ConvertNode();
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            Object[] callArgs = frame.getArguments();
            final TruffleObject function = receiverType.cast(callArgs[0]);
            final Object[] args = (Object[]) callArgs[1];
            unwrapArgs(engine, args);
            try {
                if (executeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    executeNode = insert(Message.createExecute(0).createNode());
                }
                Object tmp = ForeignAccess.sendExecute(executeNode, function, args);
                if (tmp == null) {
                    tmp = JavaInterop.asTruffleValue(null);
                }
                Object result = returnConvertNode.convert(tmp);
                // TODO we must check here that the language returns a valid value.
                return result;
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw e.raise();
            }
        }
    }

    static final class EvalRootNode extends PolyglotRootNode {

        private static final Object[] EMPTY_ARRAY = new Object[0];

        @Child private DirectCallNode call;
        private final Language language;
        private final Source source;

        private static final Object NULL_VALUE = JavaInterop.asTruffleValue(null);

        private EvalRootNode(PolyglotEngine engine, Language language, Source source) {
            super(engine);
            this.source = source;
            this.language = language;
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            if (call == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                initialize();
            }
            Object result = call.call(EMPTY_ARRAY);

            // TODO null is not valid value, wrap it using java interop
            // we should make this a strong check.
            if (result == null) {
                result = NULL_VALUE;
            }

            if (source.isInteractive()) {
                printResult(result);
            }

            return result;
        }

        @TruffleBoundary
        private void printResult(Object result) {
            String stringResult = LANGUAGE.toStringIfVisible(language.getEnv(false), result, true);
            if (stringResult != null) {
                try {
                    OutputStream out = language.engine().out;
                    out.write(stringResult.getBytes(StandardCharsets.UTF_8));
                    out.write(System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8));
                } catch (IOException ioex) {
                    // out stream has problems.
                    throw new IllegalStateException(ioex);
                }
            }
        }

        private void initialize() {
            CallTarget target = LANGUAGE.parse(language.getEnv(true), source, null);
            if (target == null) {
                throw new NullPointerException("Parsing has not produced a CallTarget for " + source);
            }
            this.call = insert(DirectCallNode.create(target));
        }

        PolyglotEngine getEngine() {
            return engine;
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
                    return new ConvertedObject(obj, null);
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

    private static final class VoidRootNode extends RootNode {

        VoidRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return frame.getArguments()[0];
        }
    }
}
