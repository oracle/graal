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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashSet;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInteropReflectFactory.MethodNodeGen;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

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
                throw UnknownIdentifierException.raise(name);
            }
            val = field.get(obj);
        } catch (NoSuchFieldException ex) {
            JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
            JavaMethodDesc method = onlyStatic ? classDesc.lookupStaticMethod(name) : classDesc.lookupMethod(name);
            if (method != null) {
                return new JavaFunctionObject(method, obj, object.languageContext);
            }

            int signature = name.indexOf("__");
            if (signature != -1) {
                for (Method m : object.clazz.getMethods()) {
                    final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
                    if (onlyStatic != isStatic) {
                        continue;
                    }
                    if (name.startsWith(m.getName())) {
                        final String fullName = jniName(m);
                        if (fullName.equals(name)) {
                            return new JavaFunctionObject(SingleMethodDesc.unreflect(m), obj);
                        }
                    }
                }
            }
            if (onlyStatic) {
                // no support for nonstatic type members now
                for (Class<?> t : object.clazz.getClasses()) {
                    final boolean isStatic = isStaticTypeOrInterface(t);
                    if (!isStatic) {
                        continue;
                    }
                    if (t.getSimpleName().equals(name)) {
                        return new JavaObject(null, t);
                    }
                }
            }
            throw UnknownIdentifierException.raise(name);
        }
        return JavaInterop.toGuestValue(val, object.languageContext);
    }

    static boolean isField(JavaObject object, String name) {
        Object obj = object.obj;
        final boolean onlyStatic = obj == null;
        try {
            final Field field = object.clazz.getField(name);
            final boolean isStatic = (field.getModifiers() & Modifier.STATIC) != 0;
            if (onlyStatic != isStatic) {
                return false;
            }
            return true;
        } catch (NoSuchFieldException | SecurityException ex) {
            return false;
        }
    }

    static boolean isMethod(JavaObject object, String name) {
        Object obj = object.obj;
        final boolean onlyStatic = obj == null;
        for (Method m : object.clazz.getMethods()) {
            final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
            if (onlyStatic != isStatic) {
                continue;
            }
            if (m.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean isMemberType(JavaObject object, String name) {
        Object obj = object.obj;
        final boolean onlyStatic = obj == null;
        if (!onlyStatic) {
            // no support for nonstatic members now
            return false;
        }
        for (Class<?> c : object.clazz.getClasses()) {
            if (isStaticTypeOrInterface(c) && name.equals(c.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    static boolean isJNIMethod(JavaObject object, String name) {
        Object obj = object.obj;
        final boolean onlyStatic = obj == null;
        for (Method m : object.clazz.getMethods()) {
            final boolean isStatic = (m.getModifiers() & Modifier.STATIC) != 0;
            if (onlyStatic != isStatic) {
                continue;
            }
            if (jniName(m).equals(name)) {
                return true;
            }
        }
        return false;
    }

    @CompilerDirectives.TruffleBoundary
    static JavaMethodDesc findMethod(JavaObject object, String name, Object[] args) {
        JavaMethodDesc method = findMethod(object, name);
        if (method != null) {
            if (!isApplicableByArity(method, args.length)) {
                return null;
            }
        }
        return method;
    }

    private static boolean isApplicableByArity(JavaMethodDesc method, int nArgs) {
        if (method instanceof SingleMethodDesc) {
            return nArgs == ((SingleMethodDesc) method).getParameterCount() ||
                            ((SingleMethodDesc) method).isVarArgs() && nArgs >= ((SingleMethodDesc) method).getParameterCount() - 1;
        } else {
            SingleMethodDesc[] overloads = ((OverloadedMethodDesc) method).getOverloads();
            for (SingleMethodDesc overload : overloads) {
                if (isApplicableByArity(overload, nArgs)) {
                    return true;
                }
            }
            return false;
        }
    }

    @CompilerDirectives.TruffleBoundary
    static JavaMethodDesc findMethod(JavaObject object, String name) {
        if (TruffleOptions.AOT) {
            return null;
        }

        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        return classDesc.lookupMethod(name);
    }

    private JavaInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Field findField(JavaObject receiver, String name) {
        try {
            return receiver.clazz.getField(name);
        } catch (NoSuchFieldException ex) {
            return null;
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
    static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function, Object languageContext) {
        assert JavaInterop.isJavaFunctionInterface(functionalType);
        final SingleHandler handler = new SingleHandler(function, languageContext);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    @CompilerDirectives.TruffleBoundary
    static TruffleObject asTruffleViaReflection(Object obj) {
        if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof TruffleHandler) {
                return ((TruffleHandler) h).obj;
            }
        }
        return new JavaObject(obj, obj.getClass());
    }

    @CompilerDirectives.TruffleBoundary
    static TruffleObject asTruffleViaReflection(Object obj, Object languageContext) {
        if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof TruffleHandler) {
                return ((TruffleHandler) h).obj;
            }
        }
        return new JavaObject(obj, obj.getClass(), languageContext);
    }

    static Object newProxyInstance(Class<?> clazz, TruffleObject obj) throws IllegalArgumentException {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new TruffleHandler(obj));
    }

    static boolean isStaticTypeOrInterface(Class<?> t) {
        // anonymous classes are private, they should be eliminated elsewhere
        return t.isInterface() || t.isEnum() || ((t.getModifiers() & Modifier.STATIC) > 0);
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findUniquePublicMemberNames(Class<?> c, boolean onlyInstance, boolean includeInternal) throws SecurityException {
        Class<?> clazz = c;
        while ((clazz.getModifiers() & Modifier.PUBLIC) == 0) {
            clazz = clazz.getSuperclass();
        }
        final Field[] fields = clazz.getFields();
        Collection<String> names = new LinkedHashSet<>();
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
            if (includeInternal) {
                names.add(jniName(method));
            }
        }
        if (!onlyInstance) {
            // no support for nonstatic member types now
            for (Class<?> t : clazz.getClasses()) {
                if (!isStaticTypeOrInterface(t)) {
                    continue;
                }
                names.add(t.getSimpleName());
            }
        }
        return names.toArray(new String[0]);
    }

    @CompilerDirectives.TruffleBoundary
    static RuntimeException reraise(InteropException ex) {
        CompilerDirectives.transferToInterpreter();
        throw ex.raise();
    }

    private static final class SingleHandler implements InvocationHandler {

        private final TruffleObject symbol;
        private CallTarget target;
        private final Object languageContext;

        SingleHandler(TruffleObject obj, Object languageContext) {
            this.symbol = obj;
            this.languageContext = languageContext;
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
                target = JavaInterop.lookupOrRegisterComputation(symbol, null, JavaInteropReflect.class);
                if (target == null) {
                    Node executeMain = Message.createExecute(args.length).createNode();
                    RootNode symbolNode = new ToJavaNode.TemporaryRoot(executeMain);
                    target = JavaInterop.lookupOrRegisterComputation(symbol, symbolNode, JavaInteropReflect.class);
                }
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof TruffleObject) {
                    continue;
                } else if (JavaInterop.isPrimitive(arg)) {
                    continue;
                } else {
                    arguments[i] = JavaInterop.toGuestValue(arg, languageContext);
                }
            }
            return target.call(symbol, TypeAndClass.forReturnType(method), args);
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

            Object[] args = arguments == null ? EMPTY : arguments;
            for (int i = 0; i < args.length; i++) {
                args[i] = JavaInterop.asTruffleValue(args[i]);
            }

            if (Object.class == method.getDeclaringClass()) {
                return method.invoke(obj, args);
            }

            CallTarget call = JavaInterop.lookupOrRegisterComputation(obj, null, method);
            if (call == null) {
                Message message = findMessage(method, method.getAnnotation(MethodMessage.class), args.length);
                TypeAndClass<?> convertTo = TypeAndClass.forReturnType(method);
                MethodNode methodNode = MethodNodeGen.create(method.getName(), message, convertTo);
                call = JavaInterop.lookupOrRegisterComputation(obj, methodNode, method);
            }

            return call.call(obj, args);
        }

    }

    abstract static class MethodNode extends RootNode {
        private final String name;
        private final TypeAndClass<?> returnType;
        @CompilerDirectives.CompilationFinal private Message message;
        @Child private ToJavaNode toJavaNode;
        @Child private Node node;

        MethodNode(String name, Message message, TypeAndClass<?> returnType) {
            super(null);
            this.name = name;
            this.toJavaNode = ToJavaNode.create();
            this.message = message;
            this.returnType = returnType;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                TruffleObject receiver = (TruffleObject) frame.getArguments()[0];
                Object[] params = (Object[]) frame.getArguments()[1];
                Object res = executeImpl(receiver, params);
                if (!returnType.clazz.isInterface()) {
                    res = JavaInterop.findOriginalObject(res);
                }
                return toJavaNode.execute(res, returnType);
            } catch (InteropException ex) {
                throw reraise(ex);
            }
        }

        private Object handleMessage(Node messageNode, TruffleObject obj, Object[] args) throws InteropException {
            if (message == Message.WRITE) {
                ForeignAccess.sendWrite(messageNode, obj, name, args[0]);
                return null;
            }
            if (message == Message.HAS_SIZE || message == Message.IS_BOXED || message == Message.IS_EXECUTABLE || message == Message.IS_NULL || message == Message.GET_SIZE) {
                return ForeignAccess.send(messageNode, obj);
            }

            if (message == Message.KEY_INFO) {
                return ForeignAccess.sendKeyInfo(messageNode, obj, name);
            }

            if (message == Message.READ) {
                return ForeignAccess.sendRead(messageNode, obj, name);
            }

            if (message == Message.UNBOX) {
                return ForeignAccess.sendUnbox(messageNode, obj);
            }

            if (Message.createExecute(0).equals(message)) {
                return ForeignAccess.sendExecute(messageNode, obj, args);
            }

            if (Message.createInvoke(0).equals(message)) {
                return ForeignAccess.sendInvoke(messageNode, obj, name, args);
            }

            if (Message.createNew(0).equals(message)) {
                return ForeignAccess.sendNew(messageNode, obj, args);
            }

            if (message == null) {
                return ((InvokeAndReadExecNode) messageNode).executeDispatch(obj, name, args);
            }
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.raise(message);
        }

        Node node(Object[] args) {
            if (node == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.node = insert(createNode(args));
            }
            return node;
        }

        Node createNode(Object[] args) {
            if (message == null) {
                return JavaInteropReflectFactory.InvokeAndReadExecNodeGen.create(returnType, args.length);
            }
            return message.createNode();
        }

        protected abstract Object executeImpl(TruffleObject receiver, Object[] arguments) throws InteropException;

        @SuppressWarnings("unused")
        @Specialization(guards = "acceptCached(receiver, foreignAccess, canHandleCall)", limit = "8")
        protected Object doCached(TruffleObject receiver, Object[] arguments,
                        @Cached("receiver.getForeignAccess()") ForeignAccess foreignAccess,
                        @Cached("createInlinedCallNode(createCanHandleTarget(foreignAccess))") DirectCallNode canHandleCall,
                        @Cached("createNode(arguments)") Node messageNode) {
            try {
                return handleMessage(messageNode, receiver, arguments);
            } catch (InteropException ex) {
                throw reraise(ex);
            }
        }

        protected static boolean acceptCached(TruffleObject receiver, ForeignAccess foreignAccess, DirectCallNode canHandleCall) {
            if (canHandleCall != null) {
                return (boolean) canHandleCall.call(new Object[]{receiver});
            } else if (foreignAccess != null) {
                return JavaInterop.ACCESSOR.interop().canHandle(foreignAccess, receiver);
            } else {
                return false;
            }
        }

        static DirectCallNode createInlinedCallNode(CallTarget target) {
            if (target == null) {
                return null;
            }
            DirectCallNode callNode = DirectCallNode.create(target);
            callNode.forceInlining();
            return callNode;
        }

        @Specialization
        Object doGeneric(TruffleObject receiver, Object[] arguments) {
            try {
                return handleMessage(node(arguments), receiver, arguments);
            } catch (InteropException ex) {
                throw reraise(ex);
            }
        }

        @CompilerDirectives.TruffleBoundary
        CallTarget createCanHandleTarget(ForeignAccess access) {
            return JavaInterop.ACCESSOR.interop().canHandleTarget(access);
        }

    }

    abstract static class InvokeAndReadExecNode extends Node {
        private final TypeAndClass<?> returnType;
        @Child private Node invokeNode;
        @Child private Node isExecNode;
        @Child private ToPrimitiveNode primitive;
        @Child private Node readNode;
        @Child private Node execNode;

        InvokeAndReadExecNode(TypeAndClass<?> returnType, int arity) {
            this.returnType = returnType;
            this.invokeNode = Message.createInvoke(arity).createNode();
        }

        abstract Object executeDispatch(TruffleObject obj, String name, Object[] args);

        @Specialization(rewriteOn = InteropException.class)
        Object doInvoke(TruffleObject obj, String name, Object[] args) throws InteropException {
            return ForeignAccess.sendInvoke(invokeNode, obj, name, args);
        }

        @Specialization(rewriteOn = UnsupportedMessageException.class)
        Object doReadExec(TruffleObject obj, String name, Object[] args) throws UnsupportedMessageException {
            try {
                if (readNode == null || primitive == null || isExecNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    readNode = insert(Message.READ.createNode());
                    primitive = insert(ToPrimitiveNode.create());
                    isExecNode = insert(Message.IS_EXECUTABLE.createNode());
                }
                Object val = ForeignAccess.sendRead(readNode, obj, name);
                Object primitiveVal = primitive.toPrimitive(val, returnType.clazz);
                if (primitiveVal != null) {
                    return primitiveVal;
                }
                TruffleObject attr = (TruffleObject) val;
                if (!ForeignAccess.sendIsExecutable(isExecNode, attr)) {
                    if (args.length == 0) {
                        return attr;
                    }
                    CompilerDirectives.transferToInterpreter();
                    throw ArityException.raise(0, args.length);
                }
                if (execNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    execNode = insert(Message.createExecute(args.length).createNode());
                }
                return ForeignAccess.sendExecute(execNode, attr, args);
            } catch (ArityException | UnsupportedTypeException | UnknownIdentifierException ex) {
                throw reraise(ex);
            }
        }

        @Specialization
        @CompilerDirectives.TruffleBoundary
        Object doBoth(TruffleObject obj, String name, Object[] args) {
            try {
                return doInvoke(obj, name, args);
            } catch (InteropException retry) {
                try {
                    return doReadExec(obj, name, args);
                } catch (UnsupportedMessageException error) {
                    throw reraise(error);
                }
            }
        }
    }

    private static Message findMessage(Method method, MethodMessage mm, int arity) {
        CompilerAsserts.neverPartOfCompilation();
        if (mm == null) {
            return null;
        }
        Message message = Message.valueOf(mm.message());
        if (message == Message.WRITE && arity != 1) {
            throw new IllegalStateException("Method needs to have a single argument to handle WRITE message " + method);
        }
        return message;
    }

    private static Object toJava(Object ret, Method method) {
        return ToJavaNode.toJava(ret, TypeAndClass.forReturnType(method));
    }

    private static String jniName(Method m) {
        StringBuilder sb = new StringBuilder();
        noUnderscore(sb, m.getName()).append("__");
        appendType(sb, m.getReturnType());
        Class<?>[] arr = m.getParameterTypes();
        for (int i = 0; i < arr.length; i++) {
            appendType(sb, arr[i]);
        }
        return sb.toString();
    }

    private static StringBuilder noUnderscore(StringBuilder sb, String name) {
        return sb.append(name.replace("_", "_1").replace('.', '_'));
    }

    private static void appendType(StringBuilder sb, Class<?> type) {
        if (type == Integer.TYPE) {
            sb.append('I');
            return;
        }
        if (type == Long.TYPE) {
            sb.append('J');
            return;
        }
        if (type == Double.TYPE) {
            sb.append('D');
            return;
        }
        if (type == Float.TYPE) {
            sb.append('F');
            return;
        }
        if (type == Byte.TYPE) {
            sb.append('B');
            return;
        }
        if (type == Boolean.TYPE) {
            sb.append('Z');
            return;
        }
        if (type == Short.TYPE) {
            sb.append('S');
            return;
        }
        if (type == Void.TYPE) {
            sb.append('V');
            return;
        }
        if (type == Character.TYPE) {
            sb.append('C');
            return;
        }
        if (type.isArray()) {
            sb.append("_3");
            appendType(sb, type.getComponentType());
            return;
        }
        noUnderscore(sb.append('L'), type.getName());
        sb.append("_2");
    }

    static String findFunctionalInterfaceMethodName(final Class<?> clazz) {
        if (TruffleOptions.AOT) {
            return null;
        }

        for (final Class<?> iface : clazz.getInterfaces()) {
            if (iface.isAnnotationPresent(FunctionalInterface.class)) {
                for (final Method m : iface.getMethods()) {
                    if (Modifier.isAbstract(m.getModifiers())) {
                        return m.getName();
                    }
                }
            }
        }

        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            return findFunctionalInterfaceMethodName(superclass);
        }
        return null;
    }
}
