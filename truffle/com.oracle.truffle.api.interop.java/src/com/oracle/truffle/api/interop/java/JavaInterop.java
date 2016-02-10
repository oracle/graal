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
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
 *
 * <h3>Java/Truffle Object Inter-op Semantics</h3>
 *
 * In case your language exposes a {@link TruffleObject} implementation, and somebody wraps your
 * object into a <em>JavaInterop</em> interface via
 * {@link #asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject)} method, this
 * is the set of {@link Message messages} you can expect:
 * <p>
 * Users can send you any message by annotating their interface method with {@link MethodMessage}
 * and it is up to them (and you) to negotiate the correct set of messages and their parameters to
 * help you understand each other. However there is a default set of {@link Message messages} (for
 * methods not annotated by {@link MethodMessage}) which consists of:
 * <ol>
 * <li>First of all {@link Message#createInvoke(int)} is constructed (with the number of parameters
 * of the interface method) and delivered to your object. The
 * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver} of the message is
 * your {@link TruffleObject}. The first
 * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) argument} is name of the
 * interface method, followed by the
 * {@link ForeignAccess#getArguments(com.oracle.truffle.api.frame.Frame) actual arguments} of the
 * interface method. Your language can either handle the message or throw
 * {@link UnsupportedMessageException} to signal additional processing is needed.</li>
 * <li>If the {@link Message#createInvoke(int) previous message} isn't handled, a
 * {@link Message#READ} is sent to your {@link TruffleObject object} (e.g.
 * {@link ForeignAccess#getReceiver(com.oracle.truffle.api.frame.Frame) receiver}) with a field name
 * equal to the name of the interface method. If the read returns a primitive type, it is returned.</li>
 * <li>If the read value is another {@link TruffleObject}, it is inspected whether it handles
 * {@link Message#IS_EXECUTABLE}. If it does, a message {@link Message#createExecute(int)} with name
 * of the interface method and its parameters is sent to the object. The result is returned to the
 * interface method caller.</li>
 * <li>In case the read value is neither primitive, neither {@link Message#IS_EXECUTABLE executable}
 * , and the interface method has no parameters, it is returned back.</li>
 * <li>All other cases yield an {@link InteropException}.</li>
 * </ol>
 * <p>
 * Object oriented languages are expected to handle the initial {@link Message#createInvoke(int)}
 * message. Non-OOP languages are expected to ignore it, yield {@link UnsupportedMessageException}
 * and handle the subsequent {@link Message#READ read} and {@link Message#createExecute(int)
 * execute} ones. The real semantic however depends on the actual language one is communicating
 * with.
 * <p>
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
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}, can be
     *            <code>null</code>, in such case the returned value will likely be
     *            <code>null</code> as well
     * @return instance of requested interface granting access to specified
     *         <code>foreignObject</code>, can be <code>null</code>, if the foreignObject parameter
     *         was <code>null</code>
     */
    public static <T> T asJavaObject(Class<T> type, TruffleObject foreignObject) {
        return asJavaObject(type, null, foreignObject);
    }

    private static <T> T asJavaObject(Class<T> clazz, Type type, TruffleObject foreignObject) {
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
                Class<?> elementType = Object.class;
                if (type instanceof ParameterizedType) {
                    ParameterizedType parametrizedType = (ParameterizedType) type;
                    final Type[] arr = parametrizedType.getActualTypeArguments();
                    if (arr.length == 1 && arr[0] instanceof Class) {
                        elementType = (Class<?>) arr[0];
                    }
                }
                obj = TruffleList.create(elementType, foreignObject);
            } else {
                obj = Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new TruffleHandler(foreignObject));
            }
        }
        return clazz.cast(obj);
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
        Class<?> retType = method.getReturnType();
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
                return asJavaObject(retType, method.getGenericReturnType(), truffleObject);
            }
        }
        return ret;
    }

    private static final class SingleHandler implements InvocationHandler {
        private final TruffleObject symbol;
        private CallTarget target;

        SingleHandler(TruffleObject obj) {
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

        TruffleHandler(TruffleObject obj) {
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

    static boolean isPrimitive(Object attr) {
        return toPrimitive(attr, null) != null;
    }

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
        if (attr instanceof String) {
            if (requestedType == char.class || requestedType == Character.class) {
                if (((String) attr).length() == 1) {
                    return ((String) attr).charAt(0);
                }
            }
            return attr;
        }
        if (attr instanceof Character) {
            return attr;
        }
        if (attr instanceof Boolean) {
            return attr;
        }
        return null;
    }

    @SuppressWarnings("unused")
    static Object message(final Message m, Object receiver, Object... arr) throws InteropException {
        Node n = m.createNode();
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(new TemporaryRoot(TruffleLanguage.class, n, (TruffleObject) receiver));
        return callTarget.call(arr);
    }

    static Object binaryMessage(final Message m, Object receiver, Object... arr) {
        try {
            return message(m, receiver, arr);
        } catch (InteropException e) {
            throw new AssertionError(e);
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
    } // end of TemporaryRoot

    static final class JavaFunctionObject implements TruffleObject {
        final Method method;
        final Object obj;

        JavaFunctionObject(Method method, Object obj) {
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

        JavaObject(Object obj, Class<?> clazz) {
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
