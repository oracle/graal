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

import java.lang.reflect.Method;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;

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
        RootNode root = new TemporaryConvertRoot(TruffleLanguage.class, new ToJavaNode(), foreignObject, type);
        Object convertedValue = Truffle.getRuntime().createCallTarget(root).call();
        return type.cast(convertedValue);
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
     * @since 0.9
     */
    public static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function) {
        RootNode root = new TemporaryConvertRoot(TruffleLanguage.class, new ToJavaNode(), function, functionalType);
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
     * @param functionalType interface with a single defined method - so called
     *            <em>functional interface</em>
     * @param implementation implementation of the interface, or directly a lambda expression
     *            defining the required behavior
     * @return an {@link Message#IS_EXECUTABLE executable} {@link TruffleObject} ready to be used in
     *         any <em>Truffle</em> language
     * @since 0.9
     */
    public static <T> TruffleObject asTruffleFunction(Class<T> functionalType, T implementation) {
        final Method[] arr = functionalType.getDeclaredMethods();
        if (!functionalType.isInterface() || arr.length != 1) {
            throw new IllegalArgumentException();
        }
        return new JavaFunctionObject(arr[0], implementation);
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
            return node.convert(frame, value, type);
        }
    }
}
