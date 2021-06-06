/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.host;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.reflect.Type;
import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.HostLanguageAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.polyglot.AbstractHostLanguage;
import com.oracle.truffle.polyglot.host.HostAdapterFactory.AdapterResult;
import com.oracle.truffle.polyglot.host.HostMethodDesc.SingleMethod;
import com.oracle.truffle.polyglot.host.HostObject.GuestToHostCalls;

/*
 * Java host language implementation.
 */
final class HostLanguage extends AbstractHostLanguage<HostContext> {

    @CompilationFinal private GuestToHostCodeCache hostToGuestCodeCache;
    @CompilationFinal HostClassCache hostClassCache; // effectively final
    final HostLanguageAccess access;
    final AbstractPolyglotImpl polyglot;
    final APIAccess api;

    HostLanguage(AbstractPolyglotImpl polyglot, HostLanguageAccess hostAccess) {
        super(polyglot);
        this.polyglot = polyglot;
        this.access = hostAccess;
        this.api = polyglot.getAPIAccess();
    }

    @Override
    protected HostContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new HostContext(this);
    }

    @SuppressWarnings("serial")
    static class HostLanguageException extends AbstractTruffleException {

        HostLanguageException(String message) {
            super(message);
        }
    }

    GuestToHostCodeCache getGuestToHostCache() {
        GuestToHostCodeCache cache = this.hostToGuestCodeCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hostToGuestCodeCache = cache = new GuestToHostCodeCache(this);
        }
        return cache;
    }

    void initializeHostAccess(HostAccess policy, ClassLoader cl) {
        if (policy == null) {
            // should only happen during context preinitialization
            return;
        }

        HostClassCache cache = HostClassCache.findOrInitialize(this, policy, cl);
        if (this.hostClassCache != null) {
            if (this.hostClassCache.hostAccess.equals(cache.hostAccess)) {
                /*
                 * The cache can be effectively be reused if the same host access configuration
                 * applies.
                 */
            } else {
                throw new IllegalStateException("Found different host access configuration for a context with a shared engine. " +
                                "The host access configuration must be the same for all contexts of an engine. " +
                                "Provide the same host access configuration using the Context.Builder.allowHostAccess method when constructing the context.");
            }
        } else {
            this.hostClassCache = cache;
        }
    }

    @Override
    @TruffleBoundary
    protected Object getLanguageView(HostContext hostContext, Object value) {
        Object wrapped;
        if (value instanceof TruffleObject) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            try {
                assert !lib.hasLanguage(value) || lib.getLanguage(value) != HostLanguage.class;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
            wrapped = HostToTypeNode.convertToObject(hostContext, value, lib);
        } else {
            wrapped = value;
        }
        return HostObject.forObject(wrapped, hostContext);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        String sourceString = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new RootNode(this) {

            @CompilationFinal ContextReference<HostContext> contextRef;

            @Override
            public Object execute(VirtualFrame frame) {
                if (contextRef == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    contextRef = lookupContextReference(HostLanguage.class);
                }
                Object context = contextRef.get();
                return findDynamicClass(context, sourceString);
            }
        });
    }

    @Override
    protected void disposeContext(HostContext context) {
        context.disposeClassLoader();
    }

    @Override
    protected void initializeHostContext(Object internalContext, Object receiver, HostAccess hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        HostContext context = (HostContext) receiver;
        ClassLoader useCl = resolveClassLoader(cl);
        initializeHostAccess(hostAccess, useCl);
        context.initialize(internalContext, useCl, clFilter, hostCLAllowed, hostLookupAllowed);
    }

    @Override
    protected boolean patchContext(HostContext context, Env newEnv) {
        return true;
    }

    @Override
    protected void addToHostClassPath(Object receiver, TruffleFile truffleFile) {
        HostContext context = (HostContext) receiver;
        context.addToHostClasspath(truffleFile);
    }

    private static ClassLoader resolveClassLoader(ClassLoader cl) {
        ClassLoader useCl = cl;
        if (useCl == null) {
            useCl = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();
        }
        return useCl;
    }

    @Override
    protected Object getScope(HostContext context) {
        return context.topScope;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    @Override
    protected Object findDynamicClass(Object receiver, String classValue) {
        HostContext context = (HostContext) receiver;
        Class<?> found = context.findClass(classValue);
        if (found == null) {
            return null;
        }
        return HostObject.forClass(found, context);
    }

    @Override
    protected Object findStaticClass(Object receiver, String classValue) {
        HostContext context = (HostContext) receiver;
        Class<?> found = context.findClass(classValue);
        if (found == null) {
            return null;
        }
        return HostObject.forStaticClass(found, context);
    }

    @Override
    protected Node createToHostTypeNode() {
        return HostToTypeNodeGen.create();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T toHostType(Node hostNode, Object hostContext, Object value, Class<T> targetType, Type genericType) {
        HostContext context = (HostContext) hostContext;
        HostToTypeNode node = (HostToTypeNode) hostNode;
        if (node == null) {
            node = HostToTypeNodeGen.getUncached();
        }
        return (T) node.execute(context, value, targetType, genericType, true);
    }

    @Override
    protected Object asHostStaticClass(Object context, Class<?> value) {
        return HostObject.forStaticClass(value, (HostContext) context);
    }

    @Override
    protected Object toGuestValue(Object hostContext, Object hostValue) {
        HostContext context = (HostContext) hostContext;
        assert validHostValue(hostValue, context) : "polyglot unboxing should be a no-op at this point.";

        if (HostContext.isGuestPrimitive(hostValue)) {
            return hostValue;
        } else if (hostValue instanceof Proxy) {
            return HostProxy.toProxyGuestObject(context, (Proxy) hostValue);
        } else if (hostValue instanceof TruffleObject) {
            return hostValue;
        } else if (hostValue instanceof Class) {
            return HostObject.forClass((Class<?>) hostValue, context);
        } else if (hostValue == null) {
            return HostObject.NULL;
        } else if (hostValue.getClass().isArray()) {
            return HostObject.forObject(hostValue, context);
        } else {
            return HostInteropReflect.asTruffleViaReflection(hostValue, context);
        }
    }

    private boolean validHostValue(Object hostValue, HostContext context) {
        Object unboxed = access.toGuestValue(context.internalContext, null, hostValue);
        return unboxed == null || unboxed == hostValue;
    }

    @Override
    public boolean isHostValue(Object obj) {
        return (obj instanceof HostObject) ||
                        (obj instanceof HostFunction) ||
                        (obj instanceof HostException) ||
                        (obj instanceof HostProxy);
    }

    @Override
    protected Object unboxHostObject(Object hostValue) {
        return HostObject.valueOf(hostValue);
    }

    @Override
    protected Object unboxProxyObject(Object hostValue) {
        return HostProxy.toProxyHostObject(hostValue);
    }

    @Override
    protected Throwable unboxHostException(Throwable hostValue) {
        if (hostValue instanceof HostException) {
            return ((HostException) hostValue).getOriginal();
        }
        return null;
    }

    @Override
    protected Object toHostObject(Object hostContext, Object value) {
        HostContext context = (HostContext) hostContext;
        return HostObject.forObject(value, context);
    }

    @Override
    protected Object asHostDynamicClass(Object context, Class<?> value) {
        return null;
    }

    @Override
    protected boolean isHostException(Throwable exception) {
        return exception instanceof HostException;
    }

    @Override
    protected boolean isHostFunction(Object obj) {
        return HostFunction.isInstance(obj);
    }

    @Override
    protected boolean isHostObject(Object obj) {
        return HostObject.isInstance(obj);
    }

    @Override
    protected boolean isHostProxy(Object value) {
        return HostProxy.isProxyGuestObject(value);
    }

    @Override
    protected boolean isHostSymbol(Object obj) {
        if (HostObject.isHostObjectInstance(obj)) {
            return ((HostObject) obj).isStaticClass();
        }
        return false;
    }

    @Override
    protected Object createHostAdapter(Object context, Class<?>[] types, Object classOverrides) {
        HostContext hostContext = (HostContext) context;
        AdapterResult adapter = HostAdapterFactory.getAdapterClassFor(hostContext, types, classOverrides);
        if (!adapter.isSuccess()) {
            throw adapter.throwException();
        }
        return HostObject.forStaticClass(adapter.getAdapterClass(), hostContext);
    }

    @Override
    protected RuntimeException toHostException(Object context, Throwable exception) {
        HostContext hostContext = (HostContext) context;
        return new HostException(exception, hostContext);
    }

    @Override
    protected Object migrateHostObject(Object newContext, Object value) {
        return HostObject.withContext(value, (HostContext) newContext);
    }

    @Override
    protected Object migrateHostProxy(Object newContext, Object value) {
        return HostProxy.withContext(value, (HostContext) newContext);
    }

    @Override
    protected Error toHostResourceError(Throwable hostException) {
        Throwable t = unboxHostException(hostException);
        if (t instanceof StackOverflowError || t instanceof OutOfMemoryError) {
            return (Error) t;
        }
        return null;
    }

    @Override
    protected int findNextGuestToHostStackTraceElement(StackTraceElement firstElement, StackTraceElement[] hostStack, int nextElementIndex) {
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

    private static boolean isGuestToHostCallFromHostInterop(StackTraceElement element) {
        assert assertClassNameUnchanged(GuestToHostCalls.class, "com.oracle.truffle.polyglot.host.HostObject$GuestToHostCalls");
        assert assertClassNameUnchanged(GuestToHostCodeCache.class, "com.oracle.truffle.polyglot.host.GuestToHostCodeCache");
        assert assertClassNameUnchanged(SingleMethod.class, "com.oracle.truffle.polyglot.host.HostMethodDesc$SingleMethod");

        switch (element.getClassName()) {
            case "com.oracle.truffle.polyglot.host.HostMethodDesc$SingleMethod$MHBase":
                return element.getMethodName().equals("invokeHandle");
            case "com.oracle.truffle.polyglot.host.HostMethodDesc$SingleMethod$MethodReflectImpl":
                return element.getMethodName().equals("reflectInvoke");
            case "com.oracle.truffle.polyglot.host.HostObject$GuestToHostCalls":
                return true;
            default:
                return element.getClassName().startsWith("com.oracle.truffle.polyglot.host.GuestToHostCodeCache$") && element.getMethodName().equals("executeImpl");
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
