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
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper methods to simplify access to objects of {@link TruffleLanguage Truffle languages} from
 * Java and the other way around. The <b>Java</b>/<em>Truffle</em> interop builds on
 * {@link ForeignAccess mutual interoperability} between individual <em>Truffle</em> languages - it
 * just encapsulates it into <b>Java</b> facade to make it as natural to access foreign
 * {@link TruffleObject Truffle objects} as <b>Java</b> programmers are used to when accessing
 * <b>Java</b> objects and interfaces directly.
 */
public final class JavaInterop {
    static final Object[] EMPTY = {};

    private JavaInterop() {
    }

    /**
     * Wraps a {@link TruffleObject foreign object} into easy to use interface. Imagine one wants to
     * access a <em>JavaScript</em> object like:
     *
     * <pre>
     * var obj = {
     *   'x' : 10,
     *   'y' : 3.3,
     *   'name' : 'Truffle'
     * };
     * </pre>
     *
     * from <b>Java</b>. One can do it by defining an interface:
     *
     * <pre>
     * <b>interface</b> ObjAccess {
     *   int x();
     *   {@link MethodMessage @MethodMessage}(message = "WRITE")
     *   void x(int newValue);
     *   double y();
     *   String name();
     * }
     * </pre>
     *
     * and obtaining its instance by calling this conversion method:
     *
     * <pre>
     * ObjAccess access = JavaInterop.{@link #asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject) asJavaObject}(ObjAccess.<b>class</b>, obj);
     * <b>assert</b> access.x() == 10 : "Still the default";
     * access.x(5);
     * <b>assert</b> access.x() == 5 : "Changed to five";
     * </pre>
     *
     * @param <T> type of requested and returned value
     * @param type interface modeling structure of <code>foreignObject</code> in <b>Java</b>
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}
     * @return instance of requested interface granting access to specified
     *         <code>foreignObject</code>
     */
    public static <T> T asJavaObject(Class<T> type, TruffleObject foreignObject) {
        if (type.isInstance(foreignObject)) {
            return type.cast(foreignObject);
        } else {
            if (!type.isInterface()) {
                throw new IllegalArgumentException();
            }
            Object obj = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, new TruffleHandler(foreignObject));
            return type.cast(obj);
        }
    }

    /**
     * Exports a Java object for use in any {@link TruffleLanguage}. The system scans structure of
     * provided object and exposes all <b>public</b> fields and methods to any <em>Truffle</em>
     * language. An instance of class
     *
     * <pre>
     * <b>class</b> JavaRecord {
     *   <b>public int</b> x;
     *   <b>public double</b> y;
     *   <b>public</b> String name() {
     *     <b>return</b> "Truffle";
     *   }
     * }
     * {@link TruffleObject} obj = JavaInterop.asTruffleObject(new JavaRecord());
     * </pre>
     *
     * can then be access from <em>JavaScript</em> or any other <em>Truffle</em> based language as
     *
     * <pre>
     * obj.x;
     * obj.y;
     * obj.name();
     * </pre>
     *
     * When the <code>obj</code> represents a {@link Class}, then the created {@link TruffleObject}
     * will allow access to <b>public</b> and <b>static</b> fields and methods from the class.
     *
     * @param obj a Java object to convert into one suitable for <em>Truffle</em> languages
     * @return converted object
     */
    public static TruffleObject asTruffleObject(Object obj) {
        if (obj instanceof TruffleObject) {
            return ((TruffleObject) obj);
        }
        if (obj instanceof Class) {
            return new JavaObject(null, (Class<?>) obj);
        }
        if (obj == null) {
            return JavaObject.NULL;
        }
        return new JavaObject(obj, obj.getClass());
    }

    /**
     * Takes executable object from a {@link TruffleLanguage} and converts it into an instance of a
     * <b>Java</b> <em>functional interface</em>.
     *
     * @param <T> requested and returned type
     * @param functionalType interface with a single defined method - so called
     *            <em>functional interface</em>
     * @param function <em>Truffle</em> that responds to {@link Message#IS_EXECUTABLE} and can be
     *            invoked
     * @return instance of interface that wraps the provided <code>function</code>
     */
    public static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        final Method[] arr = functionalType.getDeclaredMethods();
        if (!functionalType.isInterface() || arr.length != 1) {
            throw new IllegalArgumentException();
        }
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, new SingleHandler(function));
        return functionalType.cast(obj);
    }

    /**
     * Takes a functional interface and its implementation (for example lambda function) and
     * converts it into object executable by <em>Truffle</em> languages. Here is a definition of
     * function returning the meaning of life as lambda expression, converting it back to
     * <b>Java</b> and using it:
     *
     * <pre>
     * TruffleObject to = JavaInterop.asTruffleFunction(Callable.<b>class</b>, () -> 42);
     * Callable c = JavaInterop.{@link #asJavaFunction(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject) asJavaFunction}(Callable.<b>class</b>, to);
     * <b>assert</b> c.call() == 42;
     * </pre>
     *
     * @param <T> requested interface and implementation
     * @param functionalType interface with a single defined method - so called
     *            <em>functional interface</em>
     * @param implementation implementation of the interface, or directly a lambda expression
     *            defining the required behavior
     * @return an {@link Message#IS_EXECUTABLE executable} {@link TruffleObject} ready to be used in
     *         any <em>Truffle</em> language
     */
    public static <T> TruffleObject asTruffleFunction(Class<T> functionalType, T implementation) {
        final Method[] arr = functionalType.getDeclaredMethods();
        if (!functionalType.isInterface() || arr.length != 1) {
            throw new IllegalArgumentException();
        }
        return new JavaFunctionObject(arr[0], implementation);
    }

    static Message findMessage(MethodMessage mm) {
        if (mm == null) {
            return null;
        }
        return Message.valueOf(mm.message());
    }

    static Object toJava(Object ret, Method method) {
        if (isPrimitive(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            if (Boolean.TRUE.equals(message(Message.IS_NULL, ret))) {
                return null;
            }
        }
        Class<?> retType = method.getReturnType();
        if (retType.isInstance(ret)) {
            return ret;
        }
        if (ret instanceof TruffleObject) {
            final TruffleObject truffleObject = (TruffleObject) ret;
            if (retType.isInterface()) {
                if (method.getReturnType() == List.class && Boolean.TRUE.equals(message(Message.HAS_SIZE, truffleObject))) {
                    Class<?> elementType = Object.class;
                    Type type = method.getGenericReturnType();
                    if (type instanceof ParameterizedType) {
                        ParameterizedType parametrizedType = (ParameterizedType) type;
                        final Type[] arr = parametrizedType.getActualTypeArguments();
                        if (arr.length == 1 && arr[0] instanceof Class) {
                            elementType = (Class<?>) arr[0];
                        }
                    }
                    return TruffleList.create(elementType, truffleObject);
                }
                return asJavaObject(retType, truffleObject);
            }
        }
        return ret;
    }

    private static final class SingleHandler implements InvocationHandler {
        private final TruffleObject symbol;
        private CallTarget target;

        public SingleHandler(TruffleObject obj) {
            this.symbol = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object[] args = arguments == null ? EMPTY : arguments;
            if (target == null) {
                Node executeMain = Message.createExecute(args.length).createNode();
                RootNode symbolNode = new TemporaryRoot(TruffleLanguage.class, executeMain, symbol);
                target = Truffle.getRuntime().createCallTarget(symbolNode);
            }
            Object ret = target.call(args);
            return toJava(ret, method);
        }
    }

    private static final class TruffleHandler implements InvocationHandler {
        private final TruffleObject obj;

        public TruffleHandler(TruffleObject obj) {
            this.obj = obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
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
                List<Object> copy = new ArrayList<>(args.length + 1);
                // copy.add(obj);
                copy.addAll(Arrays.asList(args));
                message = Message.createExecute(copy.size());
                val = message(message, obj, copy.toArray());
                return toJava(val, method);
            }

            if (Message.createInvoke(0).equals(message)) {
                List<Object> copy = new ArrayList<>(args.length + 1);
                copy.add(obj);
                copy.addAll(Arrays.asList(args));
                message = Message.createInvoke(copy.size());
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
                    ret = message(Message.createInvoke(callArgs.size()), obj, callArgs.toArray());
                } catch (IllegalArgumentException ex) {
                    val = message(Message.READ, obj, name);
                    if (isPrimitive(val)) {
                        return val;
                    }
                    TruffleObject attr = (TruffleObject) val;
                    if (Boolean.FALSE.equals(message(Message.IS_EXECUTABLE, attr))) {
                        if (args.length == 0) {
                            return toJava(attr, method);
                        }
                        throw new IllegalStateException(attr + " cannot be invoked with " + args.length + " parameters");
                    }
                    List<Object> callArgs = new ArrayList<>(args.length + 1);
                    // callArgs.add(attr);
                    callArgs.addAll(Arrays.asList(args));
                    ret = message(Message.createExecute(callArgs.size()), attr, callArgs.toArray());
                }
                return toJava(ret, method);
            }
            throw new IllegalStateException("Unknown message: " + message);
        }

    }

    static boolean isPrimitive(Object attr) {
        if (attr instanceof TruffleObject) {
            return false;
        }
        if (attr instanceof Number) {
            return true;
        }
        if (attr instanceof String) {
            return true;
        }
        if (attr instanceof Character) {
            return true;
        }
        if (attr instanceof Boolean) {
            return true;
        }
        return false;
    }

    static Object message(final Message m, Object receiver, Object... arr) {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver));
        return callTarget.call(arr);
    }

    private static class TemporaryRoot extends RootNode {
        @Node.Child private Node foreignAccess;
        private final TruffleObject function;

        @SuppressWarnings("rawtypes")
        public TemporaryRoot(Class<? extends TruffleLanguage> lang, Node foreignAccess, TruffleObject function) {
            super(lang, null, null);
            this.foreignAccess = foreignAccess;
            this.function = function;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return ForeignAccess.execute(foreignAccess, frame, function, frame.getArguments());
        }
    } // end of TemporaryRoot

    static final class JavaFunctionObject implements TruffleObject {
        final Method method;
        final Object obj;

        public JavaFunctionObject(Method method, Object obj) {
            this.method = method;
            this.obj = obj;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(JavaFunctionObject.class, new JavaFunctionForeignAccess());
        }

    } // end of JavaFunctionObject

    static final class JavaObject implements TruffleObject {
        static final JavaObject NULL = new JavaObject(null, Object.class);

        final Object obj;
        final Class<?> clazz;

        public JavaObject(Object obj, Class<?> clazz) {
            this.obj = obj;
            this.clazz = clazz;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(JavaObject.class, new JavaObjectForeignAccess());
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(obj);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof JavaObject) {
                return obj == ((JavaObject) other).obj && clazz == ((JavaObject) other).clazz;
            }
            return false;
        }
    } // end of JavaObject

}
