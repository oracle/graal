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
import com.oracle.truffle.api.TruffleLanguage;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
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
                if (ToPrimitiveNode.temporary().isPrimitive(ret)) {
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

    @CompilerDirectives.TruffleBoundary
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

    @CompilerDirectives.TruffleBoundary
    static RuntimeException reraise(InteropException ex) {
        CompilerDirectives.transferToInterpreter();
        throw ex.raise();
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
                target = JavaInterop.ACCESSOR.engine().lookupOrRegisterComputation(symbol, null, JavaInteropReflect.class);
                if (target == null) {
                    Node executeMain = Message.createExecute(args.length).createNode();
                    RootNode symbolNode = new ToJavaNode.TemporaryRoot(TruffleLanguage.class, executeMain);
                    target = JavaInterop.ACCESSOR.engine().lookupOrRegisterComputation(symbol, symbolNode, JavaInteropReflect.class);
                }
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof TruffleObject) {
                    continue;
                }
                if (ToPrimitiveNode.temporary().isPrimitive(args[i])) {
                    continue;
                }
                arguments[i] = JavaInterop.asTruffleObject(args[i]);
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

            CallTarget call = JavaInterop.ACCESSOR.engine().lookupOrRegisterComputation(obj, null, method);
            if (call == null) {
                Message message = findMessage(method, method.getAnnotation(MethodMessage.class), args.length);
                TypeAndClass<?> convertTo = TypeAndClass.forReturnType(method);
                MethodNode methodNode = MethodNodeGen.create(method.getName(), message, convertTo);
                call = JavaInterop.ACCESSOR.engine().lookupOrRegisterComputation(obj, methodNode, method);
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
            super(TruffleLanguage.class, null, null);
            this.name = name;
            this.toJavaNode = ToJavaNodeGen.create();
            this.message = message;
            this.returnType = returnType;
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                TruffleObject receiver = (TruffleObject) frame.getArguments()[0];
                Object[] params = (Object[]) frame.getArguments()[1];
                Object res = executeImpl(receiver, params);
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
                if (readNode == null) {
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
}
