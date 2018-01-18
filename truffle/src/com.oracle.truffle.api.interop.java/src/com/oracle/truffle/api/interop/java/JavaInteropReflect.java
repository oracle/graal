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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Function;

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
import com.oracle.truffle.api.interop.java.ObjectProxyHandlerFactory.InvokeAndReadExecNodeGen;
import com.oracle.truffle.api.interop.java.ObjectProxyHandlerFactory.MethodNodeGen;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class JavaInteropReflect {
    static final Object[] EMPTY = {};

    private JavaInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Class<?> findInnerClass(Class<?> clazz, String name) {
        if (Modifier.isPublic(clazz.getModifiers())) {
            for (Class<?> t : clazz.getClasses()) {
                // no support for non-static type members now
                if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                    return t;
                }
            }
        }
        return null;
    }

    static boolean isField(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        return classDesc.lookupField(name, onlyStatic) != null;
    }

    static boolean isMethod(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        return classDesc.lookupMethod(name, onlyStatic) != null;
    }

    static boolean isInternalMethod(JavaObject object, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(object.clazz);
        final boolean onlyStatic = object.isClass();
        JavaMethodDesc method = classDesc.lookupMethod(name, onlyStatic);
        return method != null && method.isInternal();
    }

    static boolean isMemberType(JavaObject object, String name) {
        final boolean onlyStatic = object.isClass();
        if (!onlyStatic) {
            // no support for non-static members now
            return false;
        }
        Class<?> clazz = object.clazz;
        if (Modifier.isPublic(clazz.getModifiers())) {
            for (Class<?> t : clazz.getClasses()) {
                if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isJNIName(String name) {
        return name.contains("__");
    }

    @CompilerDirectives.TruffleBoundary
    static boolean isApplicableByArity(JavaMethodDesc method, int nArgs) {
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
        JavaMethodDesc foundMethod = classDesc.lookupMethod(name, object.isClass());
        if (foundMethod == null && isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, object.isClass());
        }
        return foundMethod;
    }

    @CompilerDirectives.TruffleBoundary
    static JavaFieldDesc findField(JavaObject receiver, String name) {
        JavaClassDesc classDesc = JavaClassDesc.forClass(receiver.clazz);
        final boolean onlyStatic = receiver.isClass();
        return classDesc.lookupField(name, onlyStatic);
    }

    @CompilerDirectives.TruffleBoundary
    static <T> T asJavaFunction(Class<T> functionalType, TruffleObject function, Object languageContext) {
        assert JavaInterop.isJavaFunctionInterface(functionalType);
        Method functionalInterfaceMethod = JavaInterop.functionalInterfaceMethod(functionalType);
        final FunctionProxyHandler handler = new FunctionProxyHandler(function, functionalInterfaceMethod, languageContext);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    @CompilerDirectives.TruffleBoundary
    static Function<Object[], Object> asDefaultJavaFunction(TruffleObject function, Object languageContext) {
        return new JavaFunction(function, languageContext);
    }

    @CompilerDirectives.TruffleBoundary
    static TruffleObject asTruffleViaReflection(Object obj, Object languageContext) {
        if (obj instanceof JavaFunction) {
            return ((JavaFunction) obj).functionObj;
        } else if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof FunctionProxyHandler) {
                return ((FunctionProxyHandler) h).functionObj;
            } else if (h instanceof ObjectProxyHandler) {
                return ((ObjectProxyHandler) h).obj;
            }
        }
        return new JavaObject(obj, obj.getClass(), languageContext);
    }

    static Object newProxyInstance(Class<?> clazz, TruffleObject obj, Object languageContext) throws IllegalArgumentException {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new ObjectProxyHandler(obj, languageContext));
    }

    static boolean isStaticTypeOrInterface(Class<?> t) {
        // anonymous classes are private, they should be eliminated elsewhere
        return Modifier.isPublic(t.getModifiers()) && (t.isInterface() || t.isEnum() || Modifier.isStatic(t.getModifiers()));
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findUniquePublicMemberNames(Class<?> clazz, boolean onlyInstance, boolean includeInternal) throws SecurityException {
        JavaClassDesc classDesc = JavaClassDesc.forClass(clazz);
        Collection<String> names = new LinkedHashSet<>();
        names.addAll(classDesc.getFieldNames(!onlyInstance));
        names.addAll(classDesc.getMethodNames(!onlyInstance, includeInternal));
        if (!onlyInstance) {
            if (Modifier.isPublic(clazz.getModifiers())) {
                // no support for non-static member types now
                for (Class<?> t : clazz.getClasses()) {
                    if (!isStaticTypeOrInterface(t)) {
                        continue;
                    }
                    names.add(t.getSimpleName());
                }
            }
        }
        return names.toArray(new String[0]);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    static Class<?> getMethodReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return Object.class;
        }
        return method.getReturnType();
    }

    static Type getMethodGenericReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return Object.class;
        }
        return method.getGenericReturnType();
    }

    static String jniName(Method m) {
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
}

abstract class AbstractFunctionFromJava extends RootNode {

    @Child private Node interopActionNode;
    @Child private ToJavaNode toJava;

    AbstractFunctionFromJava(Node interopActionNode) {
        super(null);
        this.interopActionNode = interopActionNode;
        this.toJava = ToJavaNode.create();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        TruffleObject function = (TruffleObject) frame.getArguments()[0];
        Object[] args = (Object[]) frame.getArguments()[1];
        Class<?> type = (Class<?>) frame.getArguments()[2];
        Type genericType = (Type) frame.getArguments()[3];
        Object languageContext = frame.getArguments()[4];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof TruffleObject) {
                continue;
            } else if (JavaInterop.isPrimitive(arg)) {
                continue;
            } else {
                args[i] = JavaInterop.toGuestValue(arg, languageContext);
            }
        }

        Object raw;
        try {
            raw = ForeignAccess.send(interopActionNode, function, args);
        } catch (InteropException ex) {
            CompilerDirectives.transferToInterpreter();
            throw JavaInteropReflect.rethrow(JavaInterop.wrapHostException(new UnsupportedOperationException(ex.getMessage())));
        }
        if (type == null) {
            return raw;
        }
        Object real = JavaInterop.findOriginalObject(raw);
        return toJava.execute(real, type, genericType, languageContext);
    }
}

final class InstantiateFromJava extends AbstractFunctionFromJava {

    InstantiateFromJava() {
        super(Message.createNew(0).createNode());
    }

}

final class ExecuteFunctionFromJava extends AbstractFunctionFromJava {

    ExecuteFunctionFromJava() {
        super(Message.createExecute(0).createNode());
    }

}

final class JavaFunction implements Function<Object[], Object> {
    final TruffleObject functionObj;
    final Object languageContext;
    private CallTarget target;

    JavaFunction(TruffleObject executable, Object languageContext) {
        this.functionObj = executable;
        this.languageContext = languageContext;
    }

    public Object apply(Object[] arguments) {
        Object[] args = arguments == null ? JavaInteropReflect.EMPTY : arguments;
        if (target == null) {
            target = JavaInterop.lookupOrRegisterComputation(functionObj, null, ExecuteFunctionFromJava.class);
            if (target == null) {
                RootNode symbolNode = new ExecuteFunctionFromJava();
                target = JavaInterop.lookupOrRegisterComputation(functionObj, symbolNode, ExecuteFunctionFromJava.class);
            }
        }
        return target.call(functionObj, args, Object.class, null, languageContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof JavaFunction) {
            JavaFunction other = (JavaFunction) obj;
            return this.languageContext == other.languageContext && this.functionObj.equals(other.functionObj);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return functionObj.hashCode();
    }
}

final class FunctionProxyHandler implements InvocationHandler {
    final TruffleObject functionObj;
    final Object languageContext;
    private final Method functionMethod;
    private CallTarget target;

    FunctionProxyHandler(TruffleObject obj, Method functionMethod, Object languageContext) {
        this.functionObj = obj;
        this.functionMethod = functionMethod;
        this.languageContext = languageContext;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        if (method.equals(functionMethod)) {
            return call(arguments);
        } else {
            return invokeDefault(proxy, method, arguments);
        }
    }

    private Object call(Object[] arguments) {
        Object[] args = arguments == null ? JavaInteropReflect.EMPTY : arguments;
        if (functionMethod.isVarArgs()) {
            args = spreadVarArgsArray(args);
        }
        if (target == null) {
            target = JavaInterop.lookupOrRegisterComputation(functionObj, null, ExecuteFunctionFromJava.class);
            if (target == null) {
                RootNode symbolNode = new ExecuteFunctionFromJava();
                target = JavaInterop.lookupOrRegisterComputation(functionObj, symbolNode, ExecuteFunctionFromJava.class);
            }
        }
        return target.call(functionObj, args, JavaInteropReflect.getMethodReturnType(functionMethod), JavaInteropReflect.getMethodGenericReturnType(functionMethod), languageContext);
    }

    private static Object[] spreadVarArgsArray(Object[] arguments) {
        if (arguments.length == 1) {
            return (Object[]) arguments[0];
        } else {
            final int allButOne = arguments.length - 1;
            Object[] last = (Object[]) arguments[allButOne];
            Object[] merge = new Object[allButOne + last.length];
            System.arraycopy(arguments, 0, merge, 0, allButOne);
            System.arraycopy(last, 0, merge, allButOne, last.length);
            return merge;
        }
    }

    private static Object invokeDefault(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
        // default method; requires Java 9 (JEP 274)
        Class<?> declaringClass = method.getDeclaringClass();
        assert declaringClass.isInterface() : declaringClass;
        MethodHandle mh;
        try {
            mh = MethodHandles.lookup().findSpecial(declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
        } catch (IllegalAccessException e) {
            throw new UnsupportedOperationException(method.getName(), e);
        }
        return mh.bindTo(proxy).invokeWithArguments(arguments);
    }
}

final class ObjectProxyHandler implements InvocationHandler {
    final TruffleObject obj;
    final Object languageContext;

    ObjectProxyHandler(TruffleObject obj, Object languageContext) {
        this.obj = obj;
        this.languageContext = languageContext;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();

        Object[] args = arguments == null ? JavaInteropReflect.EMPTY : arguments;
        for (int i = 0; i < args.length; i++) {
            args[i] = JavaInterop.asTruffleValue(args[i]);
        }

        if (Object.class == method.getDeclaringClass()) {
            return method.invoke(obj, args);
        }

        CallTarget call = JavaInterop.lookupOrRegisterComputation(obj, null, method);
        if (call == null) {
            Message message = findMessage(method, method.getAnnotation(MethodMessage.class), args.length);
            MethodNode methodNode = MethodNodeGen.create(method.getName(), message, JavaInteropReflect.getMethodReturnType(method), JavaInteropReflect.getMethodGenericReturnType(method));
            call = JavaInterop.lookupOrRegisterComputation(obj, methodNode, method);
        }

        return call.call(obj, args, languageContext);
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

    abstract static class MethodNode extends RootNode {
        private final String name;
        private final Message message;
        private final Class<?> returnType;
        private final Type genericReturnType;
        @Child private ToJavaNode toJavaNode;
        @Child private Node node;

        MethodNode(String name, Message message, Class<?> returnType, Type genericReturnType) {
            super(null);
            this.name = name;
            this.message = message;
            this.returnType = returnType;
            this.genericReturnType = genericReturnType;
            this.toJavaNode = ToJavaNode.create();
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                TruffleObject receiver = (TruffleObject) frame.getArguments()[0];
                Object[] params = (Object[]) frame.getArguments()[1];
                Object languageContext = frame.getArguments()[2];
                Object res = executeImpl(receiver, params);
                if (!returnType.isInterface()) {
                    res = JavaInterop.findOriginalObject(res);
                }
                return toJavaNode.execute(res, returnType, genericReturnType, languageContext);
            } catch (UnknownIdentifierException ex) {
                throw new UnsupportedOperationException(ex.getMessage());
            } catch (UnsupportedTypeException ex) {
                throw new IllegalArgumentException(ex.getMessage());
            } catch (ArityException ex) {
                throw new IllegalArgumentException(ex.getMessage());
            } catch (InteropException ex) {
                throw new UnsupportedOperationException(ex.getMessage());
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
                return InvokeAndReadExecNodeGen.create(returnType, args.length);
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
                throw ex.raise();
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
                throw ex.raise();
            }
        }

        @CompilerDirectives.TruffleBoundary
        CallTarget createCanHandleTarget(ForeignAccess access) {
            return JavaInterop.ACCESSOR.interop().canHandleTarget(access);
        }

    }

    abstract static class InvokeAndReadExecNode extends Node {
        private final Class<?> returnType;
        @Child private Node invokeNode;
        @Child private Node isExecNode;
        @Child private ToPrimitiveNode primitive;
        @Child private Node readNode;
        @Child private Node execNode;

        InvokeAndReadExecNode(Class<?> returnType, int arity) {
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
                Object primitiveVal = primitive.toPrimitive(val, returnType);
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
                throw ex.raise();
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
                } catch (UnsupportedMessageException ex) {
                    throw ex.raise();
                }
            }
        }
    }
}
