/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.polyglot.PolyglotFunctionProxyHandlerFactory.FunctionProxyNodeGen;
import com.oracle.truffle.polyglot.PolyglotObjectProxyHandler.ProxyInvokeNode;
import org.graalvm.polyglot.Context;

final class PolyglotFunctionProxyHandler implements InvocationHandler, PolyglotWrapper {
    final Object functionObj;
    final PolyglotLanguageContext languageContext;
    /**
     * Strong reference to the creator {@link Context} to prevent it from being garbage collected
     * and closed while this function is still reachable.
     */
    final Context contextAnchor;
    private final Method functionMethod;
    private final CallTarget target;

    PolyglotFunctionProxyHandler(Object obj, Method functionMethod, Type genericType, PolyglotLanguageContext languageContext) {
        this.functionObj = obj;
        this.languageContext = languageContext;
        this.functionMethod = functionMethod;
        this.target = FunctionProxyNode.lookup(languageContext, obj.getClass(), functionMethod, genericType, false, null);
        this.contextAnchor = languageContext.context.getContextAPI();
    }

    @CompilerDirectives.TruffleBoundary
    static <T> T create(Class<T> functionalType, Type genericType, Object function, PolyglotLanguageContext languageContext) {
        assert isFunctionalInterface(functionalType);
        Method functionalInterfaceMethod = functionalInterfaceMethod(functionalType);
        if (PolyglotProxyClass.isOptimizationRequested(functionalType)) {
            return functionalType.cast(PolyglotProxyClass.get(functionalType).newInstance(languageContext, function, genericType, functionalInterfaceMethod));
        }
        PolyglotFunctionProxyHandler handler = new PolyglotFunctionProxyHandler(function, functionalInterfaceMethod, genericType, languageContext);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
    }

    static Method functionalInterfaceMethod(Class<?> functionalInterface) {
        if (!functionalInterface.isInterface()) {
            return null;
        }
        Method found = null;
        for (Method m : functionalInterface.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && !isObjectMethodOverride(m)) {
                if (found == null) {
                    found = m;
                } else if (!found.getName().equals(m.getName()) || !Arrays.equals(found.getParameterTypes(), m.getParameterTypes())) {
                    return null;
                } else if (found.getReturnType().isAssignableFrom(m.getReturnType())) {
                    found = m;
                } else if (!m.getReturnType().isAssignableFrom(found.getReturnType())) {
                    return null;
                }
            }
        }
        return found;
    }

    static boolean isObjectMethodOverride(Method m) {
        return ((m.getParameterCount() == 0 && (m.getName().equals("hashCode") || m.getName().equals("toString"))) ||
                        (m.getParameterCount() == 1 && m.getName().equals("equals") && m.getParameterTypes()[0] == Object.class));
    }

    @CompilerDirectives.TruffleBoundary
    static boolean isFunctionalInterface(Class<?> type) {
        if (!type.isInterface() || type == TruffleObject.class) {
            return false;
        }
        if (type.getAnnotation(FunctionalInterface.class) != null) {
            return true;
        } else if (functionalInterfaceMethod(type) != null) {
            return true;
        }
        return false;
    }

    @Override
    public Object getGuestObject() {
        return functionObj;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public int hashCode() {
        return PolyglotWrapper.hashCode(languageContext, functionObj);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PolyglotFunctionProxyHandler) {
            return PolyglotWrapper.equals(languageContext, functionObj, ((PolyglotFunctionProxyHandler) o).functionObj);
        } else {
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        Object[] resolvedArguments = arguments == null ? PolyglotObjectProxyHandler.EMPTY : arguments;
        if (Modifier.isAbstract(method.getModifiers()) && !isObjectMethodOverride(method)) {
            Object[] functionArguments = functionMethod.isVarArgs() ? spreadVarArgsArray(resolvedArguments, 0) : resolvedArguments;
            return target.call(null, languageContext, functionObj, functionArguments);
        } else {
            return invokeDefault(this, proxy, method, resolvedArguments);
        }
    }

    private static Object[] spreadVarArgsArray(Object[] arguments, int offset) {
        int argumentCount = arguments.length - offset;
        if (argumentCount == 1) {
            return (Object[]) arguments[offset];
        } else {
            final int allButOne = argumentCount - 1;
            Object[] last = (Object[]) arguments[offset + allButOne];
            Object[] merge = new Object[allButOne + last.length];
            System.arraycopy(arguments, offset, merge, 0, allButOne);
            System.arraycopy(last, 0, merge, allButOne, last.length);
            return merge;
        }
    }

    @CompilerDirectives.TruffleBoundary
    static Object invokeDefault(PolyglotWrapper host, Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class || isObjectMethodOverride(method)) {
            return invokeObjectMethod(host.getLanguageContext(), host.getGuestObject(), method, arguments);
        }

        if (TruffleOptions.AOT) {
            throw new UnsupportedOperationException("calling default method " + method.getName() + " is not yet supported on SubstrateVM");
        }
        return InvocationHandler.invokeDefault(proxy, method, arguments);
    }

    static Object invokeObjectMethod(Object languageContext, Object receiver, Method method, Object[] arguments) {
        switch (method.getName()) {
            case "equals":
                return PolyglotWrapper.equalsProxy(languageContext, receiver, arguments[0]);
            case "hashCode":
                return PolyglotWrapper.hashCode(languageContext, receiver);
            case "toString":
                return PolyglotWrapper.toString(languageContext, receiver);
            default:
                throw new UnsupportedOperationException(method.getName());
        }
    }

    @ImportStatic(ProxyInvokeNode.class)
    abstract static class FunctionProxyNode extends HostToGuestRootNode {

        final Class<?> receiverClass;
        final Method method;
        final Type genericType;
        final boolean flatArguments;

        FunctionProxyNode(PolyglotLanguageInstance languageInstance, Class<?> receiverType, Method method, Type genericType, boolean flatArguments) {
            super(languageInstance);
            this.receiverClass = Objects.requireNonNull(receiverType);
            this.method = Objects.requireNonNull(method);
            this.genericType = genericType;
            this.flatArguments = flatArguments;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<? extends TruffleObject> getReceiverType() {
            return (Class<? extends TruffleObject>) receiverClass;
        }

        @Override
        public final String getName() {
            return "FunctionalInterfaceProxy<" + receiverClass + ", " + method + ">";
        }

        @Specialization
        protected final Object doCached(PolyglotLanguageContext languageContext, TruffleObject function, Object[] args,
                        @Cached("getMethodGenericReturnType(method, genericType)") Type returnType,
                        @Cached("getMethodReturnType(method, returnType)") Class<?> returnClass,
                        @Cached PolyglotExecuteNode executeNode) {
            assert Objects.equals(ProxyInvokeNode.getMethodGenericReturnType(method, genericType), returnType) &&
                            Objects.equals(ProxyInvokeNode.getMethodReturnType(method, returnType), returnClass);
            assert Modifier.isAbstract(method.getModifiers()) && !isObjectMethodOverride(method) : method;
            if (flatArguments && !method.isVarArgs()) {
                return executeNode.executeImpl(languageContext, function, args, ARGUMENT_OFFSET, returnClass, returnType);
            }
            Object[] arguments = flatArguments ? spreadVarArgsArray(args, ARGUMENT_OFFSET) : (Object[]) args[ARGUMENT_OFFSET];
            return executeNode.execute(languageContext, function, arguments, returnClass, returnType, Object[].class, Object[].class);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(receiverClass);
            result = 31 * result + Objects.hashCode(method);
            result = 31 * result + Objects.hashCode(genericType);
            result = 31 * result + Boolean.hashCode(flatArguments);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FunctionProxyNode)) {
                return false;
            }
            FunctionProxyNode other = (FunctionProxyNode) obj;
            return receiverClass == other.receiverClass && method.equals(other.method) && Objects.equals(genericType, other.genericType) && flatArguments == other.flatArguments;
        }

        static CallTarget lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Method method, Type genericType, boolean flatArguments,
                        ExecutionSignature signature) {
            FunctionProxyNode node = FunctionProxyNodeGen.create(languageContext.getLanguageInstance(), receiverClass, method, genericType, flatArguments);
            CallTarget target = lookupHostCodeCache(languageContext, node, CallTarget.class);
            if (target == null) {
                CallTarget newTarget = signature == null ? node.getCallTarget() : EngineAccessor.NODES.initializeTarget(node, signature);
                target = installHostCodeCache(languageContext, node, newTarget, CallTarget.class);
            }
            return target;
        }
    }

}
