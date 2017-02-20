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
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import static com.oracle.truffle.api.interop.java.ToJavaNode.message;
import static com.oracle.truffle.api.interop.java.ToJavaNode.toPrimitive;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class JavaInteropReflect {
    private static final Object[] EMPTY = {};

    @CompilerDirectives.TruffleBoundary
    static Object readField(JavaObject object, String name) throws NoSuchFieldError, SecurityException, IllegalArgumentException, IllegalAccessException {
        Object obj = object.obj;
        final boolean onlyStatic = obj == null;
        Object val;
        try {
            final Field field = object.clazz.getField(name);
            final boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
            if (onlyStatic != isStatic) {
                throw new NoSuchFieldException();
            }
            val = field.get(obj);
        } catch (NoSuchFieldException ex) {
            for (Method m : object.clazz.getMethods()) {
                final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                if (onlyStatic != isStatic) {
                    continue;
                }
                if (m.getName().equals(name)) {
                    return new JavaFunctionObject(m, obj);
                }
            }
            throw (NoSuchFieldError) new NoSuchFieldError(ex.getMessage()).initCause(ex);
        }
        return JavaInterop.asTruffleObject(val);
    }

    @CompilerDirectives.TruffleBoundary
    static Method findMethod(JavaObject object, String name, Object[] args) {
        for (Method m : object.clazz.getMethods()) {
            if (m.getName().equals(name)) {
                if (m.getParameterTypes().length == args.length || m.isVarArgs()) {
                    return m;
                }
            }
        }
        return null;
    }

    private JavaInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Object newConstructor(final Class<?> clazz, Object[] args) throws IllegalStateException, SecurityException {
        IllegalStateException ex = new IllegalStateException("No suitable constructor found for " + clazz);
        for (Constructor<?> constructor : clazz.getConstructors()) {
            try {
                Object ret = constructor.newInstance(args);
                if (ToJavaNode.isPrimitive(ret)) {
                    return ret;
                }
                return JavaInterop.asTruffleObject(ret);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException instEx) {
                ex = new IllegalStateException(instEx);
            }
        }
        throw ex;
    }

    @CompilerDirectives.TruffleBoundary
    static Field findField(JavaObject receiver, String name) {
        try {
            return receiver.clazz.getField(name);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException(ex);
        }
    }

    @CompilerDirectives.TruffleBoundary
    static void setField(Object obj, Field f, Object convertedValue) {
        try {
            f.set(obj, convertedValue);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    @CompilerDirectives.TruffleBoundary
    static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        final SingleHandler handler = new SingleHandler(function);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    static TruffleObject asTruffleViaReflection(Object obj) throws IllegalArgumentException {
        if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof TruffleHandler) {
                return ((TruffleHandler) h).obj;
            }
        }
        return new JavaObject(obj, obj.getClass());
    }

    static Object newProxyInstance(Class<?> clazz, TruffleObject obj) throws IllegalArgumentException {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new TruffleHandler(obj));
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findPublicMemberNames(Class<?> c, boolean onlyInstance) throws SecurityException {
        Class<?> clazz = c;
        while ((clazz.getModifiers() & Modifier.PUBLIC) == 0) {
            clazz = clazz.getSuperclass();
        }
        final Field[] fields = clazz.getFields();
        List<String> names = new ArrayList<>();
        for (Field field : fields) {
            if (((field.getModifiers() & Modifier.STATIC) == 0) != onlyInstance) {
                continue;
            }
            names.add(field.getName());
        }
        final Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (((method.getModifiers() & Modifier.STATIC) == 0) != onlyInstance) {
                continue;
            }
            names.add(method.getName());
        }
        return names.toArray(new String[0]);
    }

    private static final class SingleHandler implements InvocationHandler {

        private final TruffleObject symbol;
        private CallTarget target;

        SingleHandler(TruffleObject obj) {
            super();
            this.symbol = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object ret;
            if (method.isVarArgs()) {
                if (arguments.length == 1) {
                    ret = call((Object[]) arguments[0], method);
                } else {
                    final int allButOne = arguments.length - 1;
                    Object[] last = (Object[]) arguments[allButOne];
                    Object[] merge = new Object[allButOne + last.length];
                    System.arraycopy(arguments, 0, merge, 0, allButOne);
                    System.arraycopy(last, 0, merge, allButOne, last.length);
                    ret = call(merge, method);
                }
            } else {
                ret = call(arguments, method);
            }
            return toJava(ret, method);
        }

        private Object call(Object[] arguments, Method method) {
            CompilerAsserts.neverPartOfCompilation();
            Object[] args = arguments == null ? EMPTY : arguments;
            if (target == null) {
                Node executeMain = Message.createExecute(args.length).createNode();
                RootNode symbolNode = new ToJavaNode.TemporaryRoot(TruffleLanguage.class, executeMain, symbol, TypeAndClass.forReturnType(method));
                target = Truffle.getRuntime().createCallTarget(symbolNode);
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof TruffleObject) {
                    continue;
                }
                if (ToJavaNode.isPrimitive(args[i])) {
                    continue;
                }
                arguments[i] = JavaInterop.asTruffleObject(args[i]);
            }
            return target.call(args);
        }
    }

    private static final class TruffleHandler implements InvocationHandler {

        final TruffleObject obj;

        TruffleHandler(TruffleObject obj) {
            this.obj = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            CompilerAsserts.neverPartOfCompilation();
            TypeAndClass<?> convertTo = TypeAndClass.forReturnType(method);
            Object[] args = arguments == null ? EMPTY : arguments;
            Object val;
            for (int i = 0; i < args.length; i++) {
                args[i] = JavaInterop.asTruffleValue(args[i]);
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
                message(null, Message.WRITE, obj, name, args[0]);
                return null;
            }
            if (message == Message.HAS_SIZE || message == Message.IS_BOXED || message == Message.IS_EXECUTABLE || message == Message.IS_NULL || message == Message.GET_SIZE) {
                return message(null, message, obj);
            }

            if (message == Message.READ) {
                val = message(convertTo, Message.READ, obj, name);
                return toJava(val, method);
            }

            if (message == Message.UNBOX) {
                val = message(null, Message.UNBOX, obj);
                return toJava(val, method);
            }

            if (Message.createExecute(0).equals(message)) {
                List<Object> copy = new ArrayList<>(args.length);
                copy.addAll(Arrays.asList(args));
                message = Message.createExecute(copy.size());
                val = message(convertTo, message, obj, copy.toArray());
                return toJava(val, method);
            }

            if (Message.createInvoke(0).equals(message)) {
                List<Object> copy = new ArrayList<>(args.length + 1);
                copy.add(name);
                copy.addAll(Arrays.asList(args));
                message = Message.createInvoke(args.length);
                val = message(convertTo, message, obj, copy.toArray());
                return toJava(val, method);
            }

            if (Message.createNew(0).equals(message)) {
                message = Message.createNew(args.length);
                val = message(convertTo, message, obj, args);
                return toJava(val, method);
            }

            if (message == null) {
                Object ret;
                try {
                    List<Object> callArgs = new ArrayList<>(args.length);
                    callArgs.add(name);
                    callArgs.addAll(Arrays.asList(args));
                    ret = message(convertTo, Message.createInvoke(args.length), obj, callArgs.toArray());
                } catch (InteropException ex) {
                    val = message(null, Message.READ, obj, name);
                    Object primitiveVal = toPrimitive(val, method.getReturnType());
                    if (primitiveVal != null) {
                        return primitiveVal;
                    }
                    TruffleObject attr = (TruffleObject) val;
                    if (Boolean.FALSE.equals(message(null, Message.IS_EXECUTABLE, attr))) {
                        if (args.length == 0) {
                            return toJava(attr, method);
                        }
                        throw new IllegalArgumentException(attr + " cannot be invoked with " + args.length + " parameters");
                    }
                    List<Object> callArgs = new ArrayList<>(args.length);
                    callArgs.addAll(Arrays.asList(args));
                    ret = message(convertTo, Message.createExecute(callArgs.size()), attr, callArgs.toArray());
                }
                return toJava(ret, method);
            }
            throw new IllegalArgumentException("Unknown message: " + message);
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
        return ToJavaNode.toJava(ret, TypeAndClass.forReturnType(method));
    }
}
