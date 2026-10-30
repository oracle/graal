/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExecutionSignature;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;
import com.oracle.truffle.polyglot.PolyglotObjectProxyHandlerFactory.ObjectProxyNodeGen;
import com.oracle.truffle.polyglot.PolyglotGeneratedProxy.DefaultMethodFallbackException;
import org.graalvm.polyglot.Context;

final class PolyglotObjectProxyHandler implements InvocationHandler, PolyglotWrapper {

    static final Object[] EMPTY = {};

    final Object obj;
    final PolyglotLanguageContext languageContext;
    final CallTarget invoke;
    /**
     * Strong reference to the creator {@link Context} to prevent it from being garbage collected
     * and closed while this object is still reachable.
     */
    final Context contextAnchor;

    PolyglotObjectProxyHandler(Object obj, PolyglotLanguageContext languageContext, Class<?> interfaceClass, Type genericType) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.invoke = ObjectProxyNode.lookup(languageContext, obj.getClass(), interfaceClass, genericType, null, null);
        this.contextAnchor = languageContext.context.getContextAPI();
    }

    @Override
    public Object getGuestObject() {
        return obj;
    }

    @Override
    public PolyglotLanguageContext getLanguageContext() {
        return languageContext;
    }

    @Override
    public PolyglotContextImpl getContext() {
        return languageContext.context;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        Object[] resolvedArguments = arguments == null ? EMPTY : arguments;
        try {
            return invoke.call(null, languageContext, obj, method, resolvedArguments);
        } catch (DefaultMethodFallbackException | UnsupportedOperationException e) {
            try {
                return PolyglotFunctionProxyHandler.invokeDefault(this, proxy, method, resolvedArguments);
            } catch (Exception inner) {
                throw PolyglotGeneratedProxy.fallbackFailure(e, method.getName(), inner);
            }
        }
    }

    @TruffleBoundary
    static Object newProxyInstance(Class<?> clazz, Type genericType, Object obj, PolyglotLanguageContext languageContext) throws IllegalArgumentException {
        if (PolyglotProxyClass.isOptimizationRequested(clazz)) {
            return PolyglotProxyClass.get(clazz).newInstance(languageContext, obj, genericType, null);
        }
        PolyglotObjectProxyHandler handler = new PolyglotObjectProxyHandler(obj, languageContext, clazz, genericType);
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, handler);
    }

    abstract static class ObjectProxyNode extends HostToGuestRootNode {

        final Class<?> receiverClass;
        final Class<?> interfaceType;
        final Type genericType;
        final Method fixedMethod;

        ObjectProxyNode(PolyglotLanguageInstance languageInstance, Class<?> receiverType, Class<?> interfaceType, Type genericType, Method fixedMethod) {
            super(languageInstance);
            this.receiverClass = Objects.requireNonNull(receiverType);
            this.interfaceType = Objects.requireNonNull(interfaceType);
            this.genericType = genericType;
            this.fixedMethod = fixedMethod;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Class<? extends TruffleObject> getReceiverType() {
            return (Class<? extends TruffleObject>) receiverClass;
        }

        @Override
        public String getName() {
            return "InterfaceProxy<" + receiverClass + ">";
        }

        @Specialization
        final Object doDefault(PolyglotLanguageContext languageContext, Object receiver, Object[] args,
                        @Bind Node node,
                        @Cached ProxyInvokeNode proxyInvoke,
                        @Cached ToGuestValuesNode toGuests) {
            Method method;
            Object[] arguments;
            if (fixedMethod == null) {
                method = (Method) args[ARGUMENT_OFFSET];
                arguments = toGuests.execute(node, (Object[]) args[ARGUMENT_OFFSET + 1], 0);
            } else {
                method = fixedMethod;
                arguments = toGuests.execute(node, args, ARGUMENT_OFFSET);
            }
            return proxyInvoke.execute(languageContext, receiver, method, genericType, arguments);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Objects.hashCode(receiverClass);
            result = 31 * result + Objects.hashCode(interfaceType);
            result = 31 * result + Objects.hashCode(genericType);
            result = 31 * result + Objects.hashCode(fixedMethod);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ObjectProxyNode)) {
                return false;
            }
            ObjectProxyNode other = (ObjectProxyNode) obj;
            return receiverClass == other.receiverClass && interfaceType == other.interfaceType && Objects.equals(genericType, other.genericType) &&
                            Objects.equals(fixedMethod, other.fixedMethod);
        }

        static CallTarget lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> interfaceClass, Type genericType, Method fixedMethod,
                        ExecutionSignature signature) {
            ObjectProxyNode node = ObjectProxyNodeGen.create(languageContext.getLanguageInstance(), receiverClass, interfaceClass, genericType, fixedMethod);
            CallTarget target = lookupHostCodeCache(languageContext, node, CallTarget.class);
            if (target == null) {
                CallTarget newTarget = signature == null ? node.getCallTarget() : EngineAccessor.NODES.initializeTarget(node, signature);
                target = installHostCodeCache(languageContext, node, newTarget, CallTarget.class);
            }
            return target;
        }
    }

    @GenerateInline(false)
    abstract static class ProxyInvokeNode extends Node {

        public abstract Object execute(PolyglotLanguageContext languageContext, Object receiver, Method method, Type genericType, Object[] arguments);

        /*
         * The limit of the proxy node is unbounded. There are only so many methods a Java interface
         * can have. So we always want to specialize.
         */
        protected static final int LIMIT = Integer.MAX_VALUE;

        @Specialization(guards = {"cachedMethod == method"}, limit = "LIMIT")
        @SuppressWarnings("unused")
        protected static Object doCachedMethod(PolyglotLanguageContext languageContext, Object receiver, Method method, Type genericType, Object[] arguments,
                        @Cached("method") Method cachedMethod,
                        @Cached("method.getName()") String name,
                        @Cached("method.isDefault()") boolean defaultMethod,
                        @Cached("getMethodGenericReturnType(method, genericType)") Type returnType,
                        @Cached("getMethodReturnType(method, returnType)") Class<?> returnClass,
                        @Cached.Shared("invoke") @Cached MemberInvokeNode invoke) {
            assert Objects.equals(ProxyInvokeNode.getMethodGenericReturnType(method, genericType), returnType) &&
                            Objects.equals(ProxyInvokeNode.getMethodReturnType(method, returnType), returnClass);
            return invoke.execute(languageContext, receiver, name, defaultMethod, returnClass, returnType, arguments);
        }

        @NeverDefault
        static Class<?> getMethodReturnType(Method method, Type genericReturnType) {
            if (method == null || method.getReturnType() == void.class) {
                return Object.class;
            }
            return EngineAccessor.HOST.getRawTypeFromGenericType(genericReturnType, method.getReturnType());
        }

        @NeverDefault
        static Type getMethodGenericReturnType(Method method, Type genericTargetType) {
            if (method == null || method.getReturnType() == void.class) {
                return Object.class;
            }
            return EngineAccessor.HOST.findActualTypeArgument(method.getGenericReturnType(), genericTargetType);
        }

    }

    /** Member invocation shared by reflective interfaces and fixed generated Object methods. */
    abstract static class MemberInvokeNode extends Node {

        abstract Object execute(PolyglotLanguageContext languageContext, Object receiver, String name, boolean defaultMethod, Class<?> returnClass, Type returnType, Object[] arguments);

        static final int LIMIT = Integer.MAX_VALUE;
        @CompilationFinal private boolean invokeFailed;

        /* Invocation failure state belongs to this node, not the inlining target. */
        @SuppressWarnings("truffle-static-method")
        @Specialization(limit = "LIMIT")
        final Object doInvoke(PolyglotLanguageContext languageContext, Object receiver, String name, boolean defaultMethod, Class<?> returnClass, Type returnType, Object[] arguments,
                        @Bind Node node,
                        @CachedLibrary("receiver") InteropLibrary receivers,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary members,
                        @Cached InlinedConditionProfile branchProfile,
                        @Cached PolyglotToHostNode toHost,
                        @Cached InlinedBranchProfile error) {
            Object result = invokeOrExecute(node, languageContext, receiver, arguments, name, defaultMethod, receivers, members, branchProfile, error);
            return toHost.execute(node, languageContext, result, returnClass, returnType);
        }

        private Object invokeOrExecute(Node node, PolyglotLanguageContext polyglotContext, Object receiver, Object[] arguments, String member, boolean defaultMethod, InteropLibrary receivers,
                        InteropLibrary members,
                        InlinedConditionProfile invokeProfile, InlinedBranchProfile error) {
            try {
                boolean localInvokeFailed = this.invokeFailed;
                if (!localInvokeFailed) {
                    try {
                        return receivers.invokeMember(receiver, member, arguments);
                    } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        // fallthrough to unsupported
                        invokeFailed = localInvokeFailed = true;
                    }
                }
                if (localInvokeFailed) {
                    if (invokeProfile.profile(node, receivers.isMemberInvocable(receiver, member))) {
                        return receivers.invokeMember(receiver, member, arguments);
                    } else if (receivers.isMemberReadable(receiver, member)) {
                        Object readMember = receivers.readMember(receiver, member);
                        if (members.isExecutable(readMember)) {
                            return members.execute(readMember, arguments);
                        } else if (arguments.length == 0) {
                            return readMember;
                        }
                    }
                }
                error.enter(node);
                throw invokeUnsupported(polyglotContext, receiver, member, defaultMethod);
            } catch (UnknownIdentifierException e) {
                error.enter(node);
                throw invokeUnsupported(polyglotContext, receiver, member, defaultMethod);
            } catch (UnsupportedTypeException e) {
                error.enter(node);
                throw PolyglotInteropErrors.invalidExecuteArgumentType(polyglotContext, receiver, e.getSuppliedValues());
            } catch (ArityException e) {
                error.enter(node);
                throw PolyglotInteropErrors.invalidExecuteArity(polyglotContext, receiver, arguments, e.getExpectedMinArity(), e.getExpectedMaxArity(), e.getActualArity());
            } catch (UnsupportedMessageException e) {
                error.enter(node);
                throw invokeUnsupported(polyglotContext, receiver, member, defaultMethod);
            }
        }

        private static RuntimeException invokeUnsupported(PolyglotLanguageContext context, Object receiver, String member, boolean defaultMethod) {
            return defaultMethod ? PolyglotEngineException.DEFAULT_METHOD_FALLBACK : PolyglotInteropErrors.invokeUnsupported(context, receiver, member);
        }

    }

}
