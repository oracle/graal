/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import java.lang.reflect.Method;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.Modifier;

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
 * equal to the name of the interface method. If the read returns a primitive type, it is returned.
 * </li>
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
 * <h3>Setting the Java Interop Up</h3> When configuring your
 * {@link com.oracle.truffle.api.vm.PolyglotEngine} you can use
 * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#globalSymbol} method to create references
 * to classes with static methods and fields or instance methods or fields as shown in following
 * example:
 *
 * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop}
 *
 * After that objects <b>mul</b> and <b>compose</b> are available for import from any
 * {@link TruffleLanguage Truffe language}.
 *
 * @since 0.9
 */
public final class JavaInterop {

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
     * @since 0.9
     */
    public static <T> T asJavaObject(Class<T> type, TruffleObject foreignObject) {
        if (foreignObject instanceof JavaObject) {
            JavaObject javaObject = (JavaObject) foreignObject;
            if (type.isInstance(javaObject.obj)) {
                return type.cast(javaObject.obj);
            }
        }
        return convertToJavaObject(type, foreignObject);
    }

    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("unchecked")
    private static <T> T convertToJavaObject(Class<T> type, TruffleObject foreignObject) {
        RootNode root = new TemporaryConvertRoot(TruffleLanguage.class, ToJavaNodeGen.create(), foreignObject, type);
        Object convertedValue = Truffle.getRuntime().createCallTarget(root).call();
        return (T) convertedValue;
    }

    /**
     * Checks whether an {@link TruffleObject object} is a Java object of a given type.
     * <p>
     * Given some Java object <code>x</code> of type <code>X</code>, the following assertion will
     * always pass.
     *
     * <pre>
     * X x = ...;
     * TruffleObject obj = JavaInterop.asTruffleObject(x);
     * assert JavaInterop.isJavaObject(X.class, obj);
     * </pre>
     *
     * @param type Java class that the object is tested for
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}
     * @return {@code true} if the {@code foreignObject} was created from an object of class
     *         {@code type} using {@link #asTruffleObject(Object)}, {@code false} otherwise
     * @since 0.24
     */
    public static boolean isJavaObject(Class<?> type, TruffleObject foreignObject) {
        if (foreignObject instanceof JavaObject) {
            JavaObject javaObject = (JavaObject) foreignObject;
            return type.isInstance(javaObject.obj);
        }
        return false;
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
     *
     * obj.x;
     * obj.y;
     * obj.name();
     * </pre>
     *
     * When the <code>obj</code> represents a {@link Class}, then the created {@link TruffleObject}
     * will allow access to <b>public</b> and <b>static</b> fields and methods from the class.
     * <p>
     * Do not convert primitive types (instances of {@link Number}, {@link Boolean},
     * {@link Character} or {@link String}) to {@link TruffleObject}, all {@link TruffleLanguage}s
     * are supposed to handle primitives. Use directly the the primitive types instead. To convert
     * generic objects to {@link TruffleObject} while retaining primitive values unwrapped, use
     * {@link #asTruffleValue(java.lang.Object)} instead.
     *
     * @param obj a Java object to convert into one suitable for <em>Truffle</em> languages
     * @return converted object
     * @since 0.9
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
        if (TruffleOptions.AOT) {
            throw new IllegalArgumentException();
        }
        return JavaInteropReflect.asTruffleViaReflection(obj);
    }

    /**
     * Prepares a Java object for use in any {@link TruffleLanguage}. If the object is one of
     * {@link #isPrimitive primitive} values, it is just returned, as all {@link TruffleLanguage}s
     * are supposed to handle such object. If it is a non-primitive type of Java object, the method
     * does exactly the same thing as {@link #asTruffleObject}.
     *
     * @param obj a Java object to convert into one suitable for <em>Truffle</em> languages
     * @return converted object, or primitive
     * @since 0.18
     */
    public static Object asTruffleValue(Object obj) {
        return isPrimitive(obj) ? obj : asTruffleObject(obj);
    }

    /**
     * Test whether the object is a primitive, which all {@link TruffleLanguage}s are supposed to
     * handle. Primitives are instances of {@link Number}, {@link Boolean}, {@link Character} or
     * {@link String}.
     *
     * @param obj a Java object to test
     * @return <code>true</code> when the object is a primitive from {@link TruffleLanguage}s point
     *         of view, <code>false</code> otherwise.
     * @since 0.18
     */
    public static boolean isPrimitive(Object obj) {
        if (obj instanceof TruffleObject) {
            // Someone tried to pass a TruffleObject in
            return false;
        }
        if (obj == null) {
            return false;
        }
        return ToPrimitiveNode.shared().isPrimitive(obj);
    }

    /**
     * Takes executable object from a {@link TruffleLanguage} and converts it into an instance of a
     * <b>Java</b> <em>functional interface</em>. If the <code>functionalType</code> method is using
     * {@link Method#isVarArgs() variable arguments}, then the arguments are unwrapped and passed
     * into the <code>function</code> as indivual arguments.
     *
     * @param <T> requested and returned type
     * @param functionalType interface with a single defined method - so called <em>functional
     *            interface</em>
     * @param function <em>Truffle</em> that responds to {@link Message#IS_EXECUTABLE} and can be
     *            invoked
     * @return instance of interface that wraps the provided <code>function</code>
     * @since 0.9
     */
    public static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        RootNode root = new TemporaryConvertRoot(TruffleLanguage.class, ToJavaNodeGen.create(), function, functionalType);
        return functionalType.cast(Truffle.getRuntime().createCallTarget(root).call());
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
     * @param functionalType interface with a single defined method - so called <em>functional
     *            interface</em>
     * @param implementation implementation of the interface, or directly a lambda expression
     *            defining the required behavior
     * @return an {@link Message#IS_EXECUTABLE executable} {@link TruffleObject} ready to be used in
     *         any <em>Truffle</em> language
     * @since 0.9
     */
    public static <T> TruffleObject asTruffleFunction(Class<T> functionalType, T implementation) {
        final Method method = functionalInterfaceMethod(functionalType);
        if (method == null) {
            throw new IllegalArgumentException();
        }
        return new JavaFunctionObject(method, implementation);
    }

    /**
     * Test whether the object represents a <code>null</code> value.
     *
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}, can be
     *            <code>null</code>.
     * @return <code>true</code> when the object represents a <code>null</code> value,
     *         <code>false</code> otherwise.
     * @see Message#IS_NULL
     * @since 0.18
     */
    public static boolean isNull(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return true;
        }
        return ToPrimitiveNode.shared().isNull(foreignObject);
    }

    /**
     * Test whether the object represents an array. When an object has a size, it represents an
     * array and can be converted to a list of array elements by:
     *
     * <pre>
     * {@link #asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject) asJavaObject}(java.util.List.<b>class</b>, foreignObject);
     * </pre>
     *
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}, can be
     *            <code>null</code>.
     * @return <code>true</code> when the object represents an array, <code>false</code> otherwise.
     * @see Message#HAS_SIZE
     * @since 0.18
     */
    public static boolean isArray(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return false;
        }
        return ToPrimitiveNode.shared().hasSize(foreignObject);
    }

    /**
     * Test whether the object represents a boxed primitive type.
     *
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}, can be
     *            <code>null</code>.
     * @return <code>true</code> when the object represents a boxed primitive type,
     *         <code>false</code> otherwise.
     * @see Message#IS_BOXED
     * @since 0.18
     */
    public static boolean isBoxed(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return false;
        }
        return ToPrimitiveNode.shared().isBoxed(foreignObject);
    }

    /**
     * Convert the object into a Java primitive type. Primitive types are subclasses of Number,
     * Boolean, Character and String.
     *
     * @param foreignObject object coming from a {@link TruffleObject Truffle language}, which is
     *            known (check {@link #isBoxed(com.oracle.truffle.api.interop.TruffleObject)}) to be
     *            a boxed Java primitive type.
     * @return An object representation of Java primitive type, or <code>null<code> when the
     *         unboxing was not possible.
     * @see Message#UNBOX
     * @since 0.18
     */
    public static Object unbox(TruffleObject foreignObject) {
        if (foreignObject == null) {
            return null;
        }
        try {
            return ToPrimitiveNode.shared().unbox(foreignObject);
        } catch (InteropException iex) {
            return null;
        }
    }

    private static <T> Method functionalInterfaceMethod(Class<T> functionalType) {
        if (!functionalType.isInterface()) {
            return null;
        }
        final Method[] arr = functionalType.getMethods();
        if (arr.length == 1) {
            return arr[0];
        }
        Method found = null;
        for (Method m : arr) {
            if ((m.getModifiers() & Modifier.ABSTRACT) == 0) {
                continue;
            }
            try {
                Object.class.getMethod(m.getName(), m.getParameterTypes());
                continue;
            } catch (NoSuchMethodException ex) {
                // OK, not an object method
            }
            if (found != null) {
                return null;
            }
            found = m;
        }
        return found;
    }

    private static class TemporaryConvertRoot extends RootNode {
        @Child private ToJavaNode node;
        private final Object value;
        private final Class<?> type;

        @SuppressWarnings("rawtypes")
        TemporaryConvertRoot(Class<? extends TruffleLanguage> lang, ToJavaNode node, Object value, Class<?> type) {
            super(lang, null, null);
            this.node = node;
            this.value = value;
            this.type = type;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.execute(value, new TypeAndClass<>(null, type));
        }
    }

    @CompilerDirectives.TruffleBoundary
    static boolean isJavaFunctionInterface(Class<?> type) {
        if (!type.isInterface() || type == TruffleObject.class) {
            return false;
        }
        if (type.getAnnotation(FunctionalInterface.class) != null) {
            return true;
        }
        return type.getMethods().length == 1;
    }

    static final JavaInteropAccessor ACCESSOR = new JavaInteropAccessor();
}
