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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Map;

abstract class ToJavaNode extends Node {
    private static final Object[] EMPTY = {};
    @Child private Node isExecutable = Message.IS_EXECUTABLE.createNode();

    public abstract Object execute(VirtualFrame frame, Object value, TypeAndClass<?> type);

    @Specialization(guards = "operand == null")
    @SuppressWarnings("unused")
    protected Object doNull(Object operand, TypeAndClass<?> type) {
        return null;
    }

    @Specialization(guards = {"operand != null", "operand.getClass() == cachedOperandType", "targetType == cachedTargetType"})
    protected Object doCached(VirtualFrame frame, Object operand, @SuppressWarnings("unused") TypeAndClass<?> targetType,
                    @Cached("operand.getClass()") Class<?> cachedOperandType,
                    @Cached("targetType") TypeAndClass<?> cachedTargetType) {
        return convertImpl(frame, cachedOperandType.cast(operand), cachedTargetType, cachedOperandType);
    }

    private Object convertImpl(VirtualFrame frame, Object value, TypeAndClass<?> targetType, Class<?> cachedOperandType) {
        Object convertedValue;
        if (isPrimitiveType(cachedOperandType)) {
            convertedValue = toPrimitive(value, targetType.clazz);
            assert convertedValue != null;
        } else if (value instanceof JavaObject && targetType.clazz.isInstance(((JavaObject) value).obj)) {
            convertedValue = ((JavaObject) value).obj;
        } else if (value instanceof TruffleObject && JavaInterop.isJavaFunctionInterface(targetType.clazz) && isExecutable(frame, (TruffleObject) value)) {
            convertedValue = asJavaFunction(targetType.clazz, (TruffleObject) value);
        } else if (value instanceof TruffleObject) {
            convertedValue = asJavaObject(targetType.clazz, targetType, (TruffleObject) value);
        } else {
            assert targetType.clazz.isAssignableFrom(value.getClass());
            convertedValue = value;
        }
        return convertedValue;
    }

    @Specialization(guards = "operand != null", contains = "doCached")
    protected Object doGeneric(VirtualFrame frame, Object operand, TypeAndClass<?> type) {
        // TODO this specialization should be a TruffleBoundary because it produces too much code.
        // It can't be because a frame is passed in. We need extract all uses of frame out of
        // convertImpl.
        return convertImpl(frame, operand, type, operand.getClass());
    }

    private static boolean isPrimitiveType(Class<?> clazz) {
        return clazz == int.class || clazz == Integer.class ||
                        clazz == boolean.class || clazz == Boolean.class ||
                        clazz == byte.class || clazz == Byte.class ||
                        clazz == short.class || clazz == Short.class ||
                        clazz == long.class || clazz == Long.class ||
                        clazz == float.class || clazz == Float.class ||
                        clazz == double.class || clazz == Double.class ||
                        clazz == char.class || clazz == Character.class ||
                        CharSequence.class.isAssignableFrom(clazz);
    }

    private boolean isExecutable(VirtualFrame frame, TruffleObject object) {
        return ForeignAccess.sendIsExecutable(isExecutable, frame, object);
    }

    @TruffleBoundary
    private static <T> T asJavaObject(Class<T> clazz, TypeAndClass<?> type, TruffleObject foreignObject) {
        Object obj;
        if (clazz.isInstance(foreignObject)) {
            obj = foreignObject;
        } else {
            if (!clazz.isInterface()) {
                throw new IllegalArgumentException();
            }
            if (foreignObject == null) {
                return null;
            }
            if (clazz == List.class && Boolean.TRUE.equals(binaryMessage(Message.HAS_SIZE, foreignObject))) {
                TypeAndClass<?> elementType = type.getParameterType(0);
                obj = TruffleList.create(elementType, foreignObject);
            } else if (clazz == Map.class) {
                TypeAndClass<?> keyType = type.getParameterType(0);
                TypeAndClass<?> valueType = type.getParameterType(1);
                obj = TruffleMap.create(keyType, valueType, foreignObject);
            } else {
                obj = Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new TruffleHandler(foreignObject));
            }
        }
        return clazz.cast(obj);
    }

    @TruffleBoundary
    private static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        final SingleHandler handler = new SingleHandler(function);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    private static final class SingleHandler implements InvocationHandler {
        private final TruffleObject symbol;
        private CallTarget target;

        SingleHandler(TruffleObject obj) {
            this.symbol = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object ret;
            if (method.isVarArgs()) {
                if (arguments.length == 1) {
                    ret = call((Object[]) arguments[0]);
                } else {
                    final int allButOne = arguments.length - 1;
                    Object[] last = (Object[]) arguments[allButOne];
                    Object[] merge = new Object[allButOne + last.length];
                    System.arraycopy(arguments, 0, merge, 0, allButOne);
                    System.arraycopy(last, 0, merge, allButOne, last.length);
                    ret = call(merge);
                }
            } else {
                ret = call(arguments);
            }
            return toJava(ret, method);
        }

        private Object call(Object[] arguments) {
            CompilerAsserts.neverPartOfCompilation();
            Object[] args = arguments == null ? EMPTY : arguments;
            if (target == null) {
                Node executeMain = Message.createExecute(args.length).createNode();
                RootNode symbolNode = new TemporaryRoot(TruffleLanguage.class, executeMain, symbol);
                target = Truffle.getRuntime().createCallTarget(symbolNode);
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof TruffleObject) {
                    continue;
                }
                if (isPrimitive(args[i])) {
                    continue;
                }
                arguments[i] = JavaInterop.asTruffleObject(args[i]);
            }
            return target.call(args);
        }
    }

    private static final class TruffleHandler implements InvocationHandler {
        private final TruffleObject obj;

        TruffleHandler(TruffleObject obj) {
            this.obj = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            CompilerAsserts.neverPartOfCompilation();
            Object[] args = arguments == null ? EMPTY : arguments;
            Object val;
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                if (Proxy.isProxyClass(args[i].getClass())) {
                    InvocationHandler h = Proxy.getInvocationHandler(args[i]);
                    if (h instanceof TruffleHandler) {
                        args[i] = ((TruffleHandler) h).obj;
                    }
                }
            }

            if (Object.class == method.getDeclaringClass()) {
                return method.invoke(obj, args);
            }

            String name = method.getName();
            Message message = findMessage(method.getAnnotation(MethodMessage.class));
            if (message == Message.WRITE) {
                if (args.length != 1) {
                    throw new IllegalStateException("Method needs to have a single argument to handle WRITE message " + method);
                }
                message(Message.WRITE, obj, name, args[0]);
                return null;
            }
            if (message == Message.HAS_SIZE || message == Message.IS_BOXED || message == Message.IS_EXECUTABLE || message == Message.IS_NULL || message == Message.GET_SIZE) {
                return message(message, obj);
            }

            if (message == Message.READ) {
                val = message(Message.READ, obj, name);
                return toJava(val, method);
            }

            if (message == Message.UNBOX) {
                val = message(Message.UNBOX, obj);
                return toJava(val, method);
            }

            if (Message.createExecute(0).equals(message)) {
                List<Object> copy = new ArrayList<>(args.length);
                copy.addAll(Arrays.asList(args));
                message = Message.createExecute(copy.size());
                val = message(message, obj, copy.toArray());
                return toJava(val, method);
            }

            if (Message.createInvoke(0).equals(message)) {
                List<Object> copy = new ArrayList<>(args.length + 1);
                copy.add(name);
                copy.addAll(Arrays.asList(args));
                message = Message.createInvoke(args.length);
                val = message(message, obj, copy.toArray());
                return toJava(val, method);
            }

            if (Message.createNew(0).equals(message)) {
                message = Message.createNew(args.length);
                val = message(message, obj, args);
                return toJava(val, method);
            }

            if (message == null) {
                Object ret;
                try {
                    List<Object> callArgs = new ArrayList<>(args.length);
                    callArgs.add(name);
                    callArgs.addAll(Arrays.asList(args));
                    ret = message(Message.createInvoke(args.length), obj, callArgs.toArray());
                } catch (InteropException ex) {
                    val = message(Message.READ, obj, name);
                    Object primitiveVal = toPrimitive(val, method.getReturnType());
                    if (primitiveVal != null) {
                        return primitiveVal;
                    }
                    TruffleObject attr = (TruffleObject) val;
                    if (Boolean.FALSE.equals(message(Message.IS_EXECUTABLE, attr))) {
                        if (args.length == 0) {
                            return toJava(attr, method);
                        }
                        throw new IllegalArgumentException(attr + " cannot be invoked with " + args.length + " parameters");
                    }
                    List<Object> callArgs = new ArrayList<>(args.length);
                    callArgs.addAll(Arrays.asList(args));
                    ret = message(Message.createExecute(callArgs.size()), attr, callArgs.toArray());
                }
                return toJava(ret, method);
            }
            throw new IllegalArgumentException("Unknown message: " + message);
        }

    }

    private static class TemporaryRoot extends RootNode {
        @Node.Child private Node foreignAccess;
        private final TruffleObject function;

        @SuppressWarnings("rawtypes")
        TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
        }

        @SuppressWarnings("deprecation")
        @Override
        public Object execute(VirtualFrame frame) {
            return ForeignAccess.execute(foreignAccess, frame, function, frame.getArguments());
        }
    }

    private static Message findMessage(MethodMessage mm) {
        CompilerAsserts.neverPartOfCompilation();
        if (mm == null) {
            return null;
        }
        return Message.valueOf(mm.message());
    }

    private static Object toJava(Object ret, Method method) {
        return toJava(ret, new TypeAndClass<>(method.getGenericReturnType(), method.getReturnType()));
    }

    static Object toJava(Object ret, TypeAndClass<?> type) {
        CompilerAsserts.neverPartOfCompilation();
        Class<?> retType = type.clazz;
        Object primitiveRet = toPrimitive(ret, retType);
        if (primitiveRet != null) {
            return primitiveRet;
        }
        if (ret instanceof TruffleObject) {
            if (Boolean.TRUE.equals(binaryMessage(Message.IS_NULL, ret))) {
                return null;
            }
        }
        if (retType.isInstance(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            final TruffleObject truffleObject = (TruffleObject) ret;
            if (retType.isInterface()) {
                return asJavaObject(retType, type, truffleObject);
            }
        }
        return ret;
    }

    static boolean isPrimitive(Object attr) {
        return toPrimitive(attr, null) != null;
    }

    @TruffleBoundary
    static Object toPrimitive(Object value, Class<?> requestedType) {
        Object attr;
        if (value instanceof TruffleObject) {
            if (!Boolean.TRUE.equals(binaryMessage(Message.IS_BOXED, value))) {
                return null;
            }
            try {
                attr = message(Message.UNBOX, value);
            } catch (InteropException e) {
                throw new IllegalStateException();
            }
        } else {
            attr = value;
        }
        if (attr instanceof Number) {
            if (requestedType == null) {
                return attr;
            }
            Number n = (Number) attr;
            if (requestedType == byte.class || requestedType == Byte.class) {
                return n.byteValue();
            }
            if (requestedType == short.class || requestedType == Short.class) {
                return n.shortValue();
            }
            if (requestedType == int.class || requestedType == Integer.class) {
                return n.intValue();
            }
            if (requestedType == long.class || requestedType == Long.class) {
                return n.longValue();
            }
            if (requestedType == float.class || requestedType == Float.class) {
                return n.floatValue();
            }
            if (requestedType == double.class || requestedType == Double.class) {
                return n.doubleValue();
            }
            if (requestedType == char.class || requestedType == Character.class) {
                return (char) n.intValue();
            }
            return n;
        }
        if (attr instanceof CharSequence) {
            if (requestedType == char.class || requestedType == Character.class) {
                if (((String) attr).length() == 1) {
                    return ((String) attr).charAt(0);
                }
            }
            return String.valueOf(attr);
        }
        if (attr instanceof Character) {
            return attr;
        }
        if (attr instanceof Boolean) {
            return attr;
        }
        return null;
    }

    @SuppressWarnings("all")
    @TruffleBoundary
    static Object message(final Message m, Object receiver, Object... arr) throws InteropException {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver));
        return callTarget.call(arr);
    }

    private static Object binaryMessage(final Message m, Object receiver, Object... arr) {
        try {
            return message(m, receiver, arr);
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }
}
