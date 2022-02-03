/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.lang.reflect.Type;
import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostService;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.host.HostAdapterFactory.AdapterResult;
import com.oracle.truffle.host.HostMethodDesc.SingleMethod;
import com.oracle.truffle.host.HostMethodScope.ScopedObject;
import com.oracle.truffle.host.HostObject.GuestToHostCalls;

public class HostLanguageService extends AbstractHostService {

    final HostLanguage language;

    HostLanguageService(AbstractPolyglotImpl polyglot, HostLanguage language) {
        super(polyglot);
        this.language = language;
    }

    @Override
    public void initializeHostContext(Object internalContext, Object receiver, HostAccess hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        HostContext context = (HostContext) receiver;
        ClassLoader useCl = cl;
        if (useCl == null) {
            useCl = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();
        }
        language.initializeHostAccess(hostAccess, useCl);
        context.initialize(internalContext, useCl, clFilter, hostCLAllowed, hostLookupAllowed);
    }

    @Override
    public void addToHostClassPath(Object receiver, Object truffleFile) {
        HostContext context = (HostContext) receiver;
        context.addToHostClasspath((TruffleFile) truffleFile);
    }

    @Override
    public Object findDynamicClass(Object receiver, String classValue) {
        HostContext context = (HostContext) receiver;
        Class<?> found = context.findClass(classValue);
        if (found == null) {
            return null;
        }
        return HostObject.forClass(found, context);
    }

    @Override
    public Object findStaticClass(Object receiver, String classValue) {
        HostContext context = (HostContext) receiver;
        Class<?> found = context.findClass(classValue);
        if (found == null) {
            return null;
        }
        return HostObject.forStaticClass(found, context);
    }

    @Override
    public Object createToHostTypeNode() {
        return HostToTypeNodeGen.create();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T toHostType(Object hostNode, Object hostContext, Object value, Class<T> targetType, Type genericType) {
        HostContext context = (HostContext) hostContext;
        HostToTypeNode node = (HostToTypeNode) hostNode;
        if (node == null) {
            node = HostToTypeNodeGen.getUncached();
        }
        return (T) node.execute(context, value, targetType, genericType, true);
    }

    @Override
    public Object asHostStaticClass(Object context, Class<?> value) {
        return HostObject.forStaticClass(value, (HostContext) context);
    }

    @Override
    public Object toGuestValue(Object hostContext, Object hostValue, boolean asValue) {
        HostContext context = (HostContext) hostContext;
        assert validHostValue(hostValue, context) : "polyglot unboxing should be a no-op at this point.";
        if (HostContext.isGuestPrimitive(hostValue)) {
            return hostValue;
        } else if (hostValue instanceof Proxy) {
            return HostProxy.toProxyGuestObject(context, (Proxy) hostValue);
        } else if (!asValue && hostValue instanceof ScopedObject) {
            return ((ScopedObject) hostValue).unwrapForGuest();
        } else if (hostValue instanceof TruffleObject) {
            return hostValue;
        } else if (hostValue instanceof Class) {
            return HostObject.forClass((Class<?>) hostValue, context);
        } else if (hostValue == null) {
            return HostObject.NULL;
        } else {
            return HostObject.forObject(hostValue, context);
        }
    }

    private boolean validHostValue(Object hostValue, HostContext context) {
        Object unboxed = language.access.toGuestValue(context.internalContext, hostValue);
        return unboxed == hostValue;
    }

    @Override
    public boolean isHostValue(Object value) {
        Object obj = HostLanguage.unwrapIfScoped(language, value);
        return (obj instanceof HostObject) ||
                        (obj instanceof HostFunction) ||
                        (obj instanceof HostException) ||
                        (obj instanceof HostProxy);
    }

    @Override
    public Object unboxHostObject(Object hostValue) {
        return HostObject.valueOf(language, hostValue);
    }

    @Override
    public Object unboxProxyObject(Object hostValue) {
        return HostProxy.toProxyHostObject(language, hostValue);
    }

    @Override
    public Throwable unboxHostException(Throwable hostValue) {
        if (hostValue instanceof HostException) {
            return ((HostException) hostValue).getOriginal();
        }
        return null;
    }

    @Override
    public Object toHostObject(Object hostContext, Object value) {
        HostContext context = (HostContext) hostContext;
        return HostObject.forObject(value, context);
    }

    @Override
    public Object asHostDynamicClass(Object context, Class<?> value) {
        return null;
    }

    @Override
    public boolean isHostException(Object exception) {
        return exception instanceof HostException;
    }

    @Override
    public boolean isHostFunction(Object value) {
        return HostFunction.isInstance(language, value);
    }

    @Override
    public boolean isHostObject(Object value) {
        return HostObject.isInstance(language, value);
    }

    @Override
    public boolean isHostProxy(Object value) {
        return HostProxy.isProxyGuestObject(language, value);
    }

    @Override
    public boolean isHostSymbol(Object obj) {
        Object o = HostLanguage.unwrapIfScoped(language, obj);
        if (o instanceof HostObject) {
            return ((HostObject) o).isStaticClass();
        }
        return false;
    }

    @Override
    public Object createHostAdapter(Object context, Class<?>[] types, Object classOverrides) {
        HostContext hostContext = (HostContext) context;
        AdapterResult adapter = HostAdapterFactory.getAdapterClassFor(hostContext, types, classOverrides);
        if (!adapter.isSuccess()) {
            throw adapter.throwException();
        }
        return HostObject.forStaticClass(adapter.getAdapterClass(), hostContext);
    }

    @Override
    public RuntimeException toHostException(Object context, Throwable exception) {
        HostContext hostContext = (HostContext) context;
        return new HostException(exception, hostContext);
    }

    @Override
    public Object migrateValue(Object targetContext, Object value, Object valueContext) {
        assert targetContext != valueContext;
        if (value instanceof TruffleObject) {
            assert value instanceof TruffleObject;
            if (HostObject.isInstance(language, value)) {
                return HostObject.withContext(language, value, (HostContext) HostAccessor.ENGINE.getHostContext(targetContext));
            } else if (value instanceof HostProxy) {
                return HostProxy.withContext(value, (HostContext) HostAccessor.ENGINE.getHostContext(targetContext));
            } else if (valueContext == null) {
                /*
                 * The only way this can happen is with Value.asValue(TruffleObject). If it happens
                 * otherwise, its wrong.
                 */
                assert value instanceof TruffleObject;
                return value;
            } else {
                // cannot migrate
                return null;
            }
        } else {
            assert InteropLibrary.isValidValue(value);
            return value;
        }
    }

    @Override
    public Error toHostResourceError(Throwable hostException) {
        Throwable t = unboxHostException(hostException);
        if (t instanceof StackOverflowError || t instanceof OutOfMemoryError) {
            return (Error) t;
        }
        return null;
    }

    @Override
    public int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
        StackTraceElement element = firstElement;
        int index = nextElementIndex;
        while (isGuestToHostReflectiveCall(element) && index < hostStack.length) {
            element = hostStack[index++];
        }
        if (isGuestToHostCallFromHostInterop(element)) {
            return index - nextElementIndex;
        } else {
            return -1;
        }
    }

    @Override
    public void pin(Object receiver) {
        HostMethodScope.pin(receiver);
    }

    private static boolean isGuestToHostCallFromHostInterop(StackTraceElement element) {
        assert assertClassNameUnchanged(GuestToHostCalls.class, "com.oracle.truffle.host.HostObject$GuestToHostCalls");
        assert assertClassNameUnchanged(GuestToHostCodeCache.class, "com.oracle.truffle.host.GuestToHostCodeCache");
        assert assertClassNameUnchanged(SingleMethod.class, "com.oracle.truffle.host.HostMethodDesc$SingleMethod");

        switch (element.getClassName()) {
            case "com.oracle.truffle.host.HostMethodDesc$SingleMethod$MHBase":
                return element.getMethodName().equals("invokeHandle");
            case "com.oracle.truffle.host.HostMethodDesc$SingleMethod$MethodReflectImpl":
                return element.getMethodName().equals("reflectInvoke");
            case "com.oracle.truffle.host.HostObject$GuestToHostCalls":
                return true;
            default:
                return element.getClassName().startsWith("com.oracle.truffle.host.GuestToHostCodeCache$") && element.getMethodName().equals("executeImpl");
        }
    }

    private static boolean assertClassNameUnchanged(Class<?> c, String name) {
        if (c.getName().equals(name)) {
            return true;
        }
        throw new AssertionError("Class name is outdated. Expected " + name + " but got " + c.getName());
    }

    private static boolean isGuestToHostReflectiveCall(StackTraceElement element) {
        switch (element.getClassName()) {
            case "sun.reflect.NativeMethodAccessorImpl":
            case "sun.reflect.DelegatingMethodAccessorImpl":
            case "jdk.internal.reflect.NativeMethodAccessorImpl":
            case "jdk.internal.reflect.DelegatingMethodAccessorImpl":
            case "java.lang.reflect.Method":
                return element.getMethodName().startsWith("invoke");
            default:
                return false;
        }
    }

}
