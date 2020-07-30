/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Objects;

import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ToGuestValuesNode;

final class HostInteropReflect {
    static final Object[] EMPTY = {};
    static final String STATIC_TO_CLASS = "class";
    static final String CLASS_TO_STATIC = "static";

    private HostInteropReflect() {
    }

    @CompilerDirectives.TruffleBoundary
    static Class<?> findInnerClass(Class<?> clazz, String name) {
        if (!TruffleOptions.AOT) { // GR-13208: SVM does not support Class.getClasses() yet
            if (Modifier.isPublic(clazz.getModifiers())) {
                for (Class<?> t : clazz.getClasses()) {
                    // no support for non-static type members now
                    if (isStaticTypeOrInterface(t) && t.getSimpleName().equals(name)) {
                        return t;
                    }
                }
            }
        }
        return null;
    }

    static boolean isJNIName(String name) {
        return name.contains("__");
    }

    @CompilerDirectives.TruffleBoundary
    static HostMethodDesc findMethod(PolyglotEngineImpl impl, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(impl, clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod == null && isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
        }
        return foundMethod;
    }

    @CompilerDirectives.TruffleBoundary
    static HostFieldDesc findField(PolyglotEngineImpl impl, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(impl, clazz);
        return classDesc.lookupField(name, onlyStatic);
    }

    @TruffleBoundary
    static boolean isReadable(HostObject object, Class<?> clazz, String name, boolean onlyStatic, boolean isClass) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.getEngine(), clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod != null) {
            return true;
        } else if (isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }

        HostFieldDesc foundField = classDesc.lookupField(name, onlyStatic);
        if (foundField != null) {
            return true;
        }

        if (onlyStatic) {
            if (STATIC_TO_CLASS.equals(name)) {
                return true;
            }
            Class<?> innerClass = findInnerClass(clazz, name);
            if (innerClass != null) {
                return true;
            }
        }
        if (isClass) {
            if (CLASS_TO_STATIC.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static boolean isModifiable(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.getEngine(), clazz);
        HostFieldDesc foundField = classDesc.lookupField(name, onlyStatic);
        if (foundField != null && !foundField.isFinal()) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    static boolean isInvokable(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.getEngine(), clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod != null) {
            return true;
        } else if (isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    static boolean isInternal(HostObject object, Class<?> clazz, String name, boolean onlyStatic) {
        HostClassDesc classDesc = HostClassDesc.forClass(object.getEngine(), clazz);
        HostMethodDesc foundMethod = classDesc.lookupMethod(name, onlyStatic);
        if (foundMethod == null && isJNIName(name)) {
            foundMethod = classDesc.lookupMethodByJNIName(name, onlyStatic);
            if (foundMethod != null) {
                return true;
            }
        }
        return false;
    }

    @CompilerDirectives.TruffleBoundary
    static <T> T asJavaFunction(Class<T> functionalType, Object function, PolyglotLanguageContext languageContext) {
        assert isFunctionalInterface(functionalType);
        Method functionalInterfaceMethod = functionalInterfaceMethod(functionalType);
        final FunctionProxyHandler handler = new FunctionProxyHandler(function, functionalInterfaceMethod, languageContext);
        Object obj = Proxy.newProxyInstance(functionalType.getClassLoader(), new Class<?>[]{functionalType}, handler);
        return functionalType.cast(obj);
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

    static Method functionalInterfaceMethod(Class<?> functionalInterface) {
        if (!functionalInterface.isInterface()) {
            return null;
        }
        Method found = null;
        for (Method m : functionalInterface.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && !HostClassDesc.isObjectMethodOverride(m)) {
                if (found != null) {
                    return null;
                }
                found = m;
            }
        }
        return found;
    }

    static Object asTruffleViaReflection(Object obj, PolyglotLanguageContext languageContext) {
        if (obj instanceof Proxy) {
            return asTruffleObjectProxy(obj, languageContext);
        }
        return HostObject.forObject(obj, languageContext);
    }

    @CompilerDirectives.TruffleBoundary
    private static Object asTruffleObjectProxy(Object obj, PolyglotLanguageContext languageContext) {
        if (Proxy.isProxyClass(obj.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(obj);
            if (h instanceof FunctionProxyHandler) {
                return ((FunctionProxyHandler) h).functionObj;
            } else if (h instanceof ObjectProxyHandler) {
                return ((ObjectProxyHandler) h).obj;
            }
        }
        return HostObject.forObject(obj, languageContext);
    }

    static Object newProxyInstance(Class<?> clazz, Object obj, PolyglotLanguageContext languageContext) throws IllegalArgumentException {
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new ObjectProxyHandler(obj, languageContext, clazz));
    }

    static boolean isStaticTypeOrInterface(Class<?> t) {
        // anonymous classes are private, they should be eliminated elsewhere
        return Modifier.isPublic(t.getModifiers()) && (t.isInterface() || t.isEnum() || Modifier.isStatic(t.getModifiers()));
    }

    @CompilerDirectives.TruffleBoundary
    static String[] findUniquePublicMemberNames(PolyglotEngineImpl engine, Class<?> clazz, boolean isStatic, boolean isClass, boolean includeInternal) throws SecurityException {
        HostClassDesc classDesc = HostClassDesc.forClass(engine, clazz);
        EconomicSet<String> names = EconomicSet.create();
        names.addAll(classDesc.getFieldNames(isStatic));
        names.addAll(classDesc.getMethodNames(isStatic, includeInternal));
        if (isStatic) {
            names.add(STATIC_TO_CLASS);
            if (!TruffleOptions.AOT) { // GR-13208: SVM does not support Class.getClasses() yet
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
        } else if (isClass) {
            names.add(CLASS_TO_STATIC);
        }
        return names.toArray(new String[names.size()]);
    }

    @SuppressWarnings({"unchecked"})
    static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    public static Class<?> getMethodReturnType(Method method) {
        if (method == null || method.getReturnType() == void.class) {
            return Object.class;
        }
        return method.getReturnType();
    }

    public static Type getMethodGenericReturnType(Method method) {
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

@ImportStatic(HostInteropReflect.class)
abstract class FunctionProxyNode extends HostToGuestRootNode {

    final Class<?> receiverClass;
    final Method method;

    FunctionProxyNode(Class<?> receiverType, Method method) {
        this.receiverClass = receiverType;
        this.method = method;
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
    protected Object doCached(PolyglotLanguageContext languageContext, TruffleObject function, Object[] args,
                    @Cached("getMethodReturnType(method)") Class<?> returnClass,
                    @Cached("getMethodGenericReturnType(method)") Type returnType,
                    @Cached PolyglotExecuteNode executeNode) {
        return executeNode.execute(languageContext, function, args[ARGUMENT_OFFSET], returnClass, returnType);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(receiverClass);
        result = 31 * result + Objects.hashCode(method);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FunctionProxyNode)) {
            return false;
        }
        FunctionProxyNode other = (FunctionProxyNode) obj;
        return receiverClass == other.receiverClass && method.equals(other.method);
    }

    static CallTarget lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Method method) {
        FunctionProxyNode node = FunctionProxyNodeGen.create(receiverClass, method);
        CallTarget target = lookupHostCodeCache(languageContext, node, CallTarget.class);
        if (target == null) {
            target = installHostCodeCache(languageContext, node, createTarget(node), CallTarget.class);
        }
        return target;
    }
}

final class FunctionProxyHandler implements InvocationHandler, HostWrapper {
    final Object functionObj;
    final PolyglotLanguageContext languageContext;
    private final Method functionMethod;
    private final CallTarget target;

    FunctionProxyHandler(Object obj, Method functionMethod, PolyglotLanguageContext languageContext) {
        this.functionObj = obj;
        this.languageContext = languageContext;
        this.functionMethod = functionMethod;
        this.target = FunctionProxyNode.lookup(languageContext, obj.getClass(), functionMethod);
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
        return HostWrapper.hashCode(languageContext, functionObj);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof FunctionProxyHandler) {
            return HostWrapper.equals(languageContext, functionObj, ((FunctionProxyHandler) o).functionObj);
        } else {
            return false;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        CompilerAsserts.neverPartOfCompilation();
        Object[] resolvedArguments = arguments == null ? HostInteropReflect.EMPTY : arguments;
        if (method.equals(functionMethod)) {
            return target.call(languageContext, functionObj, spreadVarArgsArray(resolvedArguments));
        } else {
            return invokeDefault(this, proxy, method, resolvedArguments);
        }
    }

    private Object[] spreadVarArgsArray(Object[] arguments) {
        if (!functionMethod.isVarArgs()) {
            return arguments;
        }
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

    static Object invokeDefault(HostWrapper host, Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals":
                    return HostWrapper.equalsProxy(host, arguments[0]);
                case "hashCode":
                    return HostWrapper.hashCode(host.getLanguageContext(), host.getGuestObject());
                case "toString":
                    return HostWrapper.toString(host);
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }

        if (TruffleOptions.AOT) {
            throw new UnsupportedOperationException("calling default method " + method.getName() + " is not yet supported on SubstrateVM");
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

class ObjectProxyNode extends HostToGuestRootNode {

    final Class<?> receiverClass;
    final Class<?> interfaceType;

    @Child private ProxyInvokeNode proxyInvoke = ProxyInvokeNodeGen.create();
    @CompilationFinal private ToGuestValuesNode toGuests = ToGuestValuesNode.create();

    ObjectProxyNode(Class<?> receiverType, Class<?> interfaceType) {
        this.receiverClass = receiverType;
        this.interfaceType = interfaceType;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<? extends TruffleObject> getReceiverType() {
        return (Class<? extends TruffleObject>) receiverClass;
    }

    @Override
    public final String getName() {
        return "InterfaceProxy<" + receiverClass + ">";
    }

    @Override
    protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
        Method method = (Method) args[ARGUMENT_OFFSET];
        Object[] arguments = toGuests.apply(languageContext, (Object[]) args[ARGUMENT_OFFSET + 1]);
        return proxyInvoke.execute(languageContext, receiver, method, arguments);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(receiverClass);
        result = 31 * result + Objects.hashCode(interfaceType);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ObjectProxyNode)) {
            return false;
        }
        ObjectProxyNode other = (ObjectProxyNode) obj;
        return receiverClass == other.receiverClass && interfaceType == other.interfaceType;
    }

    static CallTarget lookup(PolyglotLanguageContext languageContext, Class<?> receiverClass, Class<?> interfaceClass) {
        ObjectProxyNode node = new ObjectProxyNode(receiverClass, interfaceClass);
        CallTarget target = lookupHostCodeCache(languageContext, node, CallTarget.class);
        if (target == null) {
            target = installHostCodeCache(languageContext, node, createTarget(node), CallTarget.class);
        }
        return target;
    }
}

@ImportStatic({HostInteropReflect.class})
abstract class ProxyInvokeNode extends Node {

    public abstract Object execute(PolyglotLanguageContext languageContext, Object receiver, Method method, Object[] arguments);

    /*
     * The limit of the proxy node is unbounded. There are only so many methods a Java interface can
     * have. So we always want to specialize.
     */
    protected static final int LIMIT = Integer.MAX_VALUE;

    @CompilationFinal private boolean invokeFailed;

    /*
     * It is supposed to be safe to compare method names with == only as they are always interned.
     */
    @Specialization(guards = {"cachedMethod == method"}, limit = "LIMIT")
    @SuppressWarnings("unused")
    protected Object doCachedMethod(PolyglotLanguageContext languageContext, Object receiver, Method method, Object[] arguments,
                    @Cached("method") Method cachedMethod,
                    @Cached("method.getName()") String name,
                    @Cached("getMethodReturnType(method)") Class<?> returnClass,
                    @Cached("getMethodGenericReturnType(method)") Type returnType,
                    @CachedLibrary("receiver") InteropLibrary receivers,
                    @CachedLibrary(limit = "LIMIT") InteropLibrary members,
                    @Cached ConditionProfile branchProfile,
                    @Cached("create()") ToHostNode toHost,
                    @Cached BranchProfile error) {
        Object result = invokeOrExecute(languageContext, receiver, arguments, name, receivers, members, branchProfile, error);
        return toHost.execute(result, returnClass, returnType, languageContext, true);
    }

    @TruffleBoundary
    private static boolean guardReturnType(Method method, Type returnType) {
        return method.getGenericReturnType().equals(returnType);
    }

    private Object invokeOrExecute(PolyglotLanguageContext polyglotContext, Object receiver, Object[] arguments, String member, InteropLibrary receivers,
                    InteropLibrary members,
                    ConditionProfile invokeProfile, BranchProfile error) {
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
                if (invokeProfile.profile(receivers.isMemberInvocable(receiver, member))) {
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
            error.enter();
            throw HostInteropErrors.invokeUnsupported(polyglotContext, receiver, member);
        } catch (UnknownIdentifierException e) {
            error.enter();
            throw HostInteropErrors.invokeUnsupported(polyglotContext, receiver, member);
        } catch (UnsupportedTypeException e) {
            error.enter();
            throw HostInteropErrors.invalidExecuteArgumentType(polyglotContext, receiver, e.getSuppliedValues());
        } catch (ArityException e) {
            error.enter();
            throw HostInteropErrors.invalidExecuteArity(polyglotContext, receiver, arguments, e.getExpectedArity(), e.getActualArity());
        } catch (UnsupportedMessageException e) {
            error.enter();
            throw HostInteropErrors.invokeUnsupported(polyglotContext, receiver, member);
        }
    }

}

final class ObjectProxyHandler implements InvocationHandler, HostWrapper {

    final Object obj;
    final PolyglotLanguageContext languageContext;
    final CallTarget invoke;

    ObjectProxyHandler(Object obj, PolyglotLanguageContext languageContext, Class<?> interfaceClass) {
        this.obj = obj;
        this.languageContext = languageContext;
        this.invoke = ObjectProxyNode.lookup(languageContext, obj.getClass(), interfaceClass);
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
        Object[] resolvedArguments = arguments == null ? HostInteropReflect.EMPTY : arguments;
        try {
            return invoke.call(languageContext, obj, method, resolvedArguments);
        } catch (UnsupportedOperationException e) {
            try {
                return FunctionProxyHandler.invokeDefault(this, proxy, method, resolvedArguments);
            } catch (Exception innerE) {
                e.addSuppressed(innerE);
                throw e;
            }
        }
    }

}
