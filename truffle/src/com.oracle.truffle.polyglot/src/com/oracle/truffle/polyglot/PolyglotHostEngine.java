/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.util.function.Predicate;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.HostEngine;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.polyglot.PolyglotValue.InteropValue;

final class PolyglotHostEngine extends HostEngine {

    @CompilationFinal private GuestToHostCodeCache hostToGuestCodeCache;
    @CompilationFinal HostClassCache hostClassCache; // effectively final
    final AbstractPolyglotImpl polyglot;

    protected PolyglotHostEngine(AbstractPolyglotImpl polyglot) {
        super(polyglot);
        this.polyglot = polyglot;
    }

    @Override
    public void patchHostContext(Object receiver, HostAccess hostAccess, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        ClassLoader useCl = resolveClassLoader(cl);
        initializeHostAccess(hostAccess, useCl);
        HostContext context = (HostContext) receiver;
        context.patch(useCl, clFilter, hostCLAllowed, hostLookupAllowed);
    }

    @Override
    public AbstractValueDispatch lookupValueDispatch(Object guestValue) {
        return new InteropValue(polyglot, null, guestValue, guestValue.getClass());
    }

    @Override
    public void addToHostClassPath(Object receiver, Object truffleFile) {
        HostContext context = (HostContext) receiver;
        context.addToHostClasspath((TruffleFile) truffleFile);
    }

    @Override
    public Object toGuestValue(Object receiver, Object hostValue) {
        HostContext context = (HostContext) receiver;
        assert !(hostValue instanceof Value);
        assert !HostWrapper.isInstance(hostValue);

        if (PolyglotImpl.isGuestPrimitive(hostValue)) {
            return hostValue;
        } else if (hostValue instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(context, (Proxy) hostValue);
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

    @Override
    public Object createHostContext(HostAccess access, ClassLoader cl, Predicate<String> clFilter, boolean hostCLAllowed, boolean hostLookupAllowed) {
        ClassLoader useCl = resolveClassLoader(cl);
        initializeHostAccess(access, useCl);
        return new HostContext(this, useCl, clFilter, hostCLAllowed, hostLookupAllowed);
    }

    private static ClassLoader resolveClassLoader(ClassLoader cl) {
        ClassLoader useCl = cl;
        if (useCl == null) {
            useCl = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();
        }
        return useCl;
    }

    GuestToHostCodeCache getGuestToHostCache() {
        GuestToHostCodeCache cache = this.hostToGuestCodeCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hostToGuestCodeCache = cache = new GuestToHostCodeCache();
        }
        return cache;
    }

    @Override
    public Object getTopScope(Object context) {
        return ((HostContext) context).topScope;
    }

    void initializeHostAccess(HostAccess policy, ClassLoader cl) {
        if (policy == null) {
            // should only happen during context preinitialization
            return;
        }

        HostClassCache cache = HostClassCache.findOrInitialize(PolyglotImpl.getInstance().getAPIAccess(), policy, cl);
        if (this.hostClassCache != null) {
            if (this.hostClassCache.hostAccess.equals(cache.hostAccess)) {
                /*
                 * The cache can be effectively be reused if the same host access configuration
                 * applies.
                 */
            } else {
                throw PolyglotEngineException.illegalState("Found different host access configuration for a context with a shared engine. " +
                                "The host access configuration must be the same for all contexts of an engine. " +
                                "Provide the same host access configuration using the Context.Builder.allowHostAccess method when constructing the context.");
            }
        } else {
            this.hostClassCache = cache;
        }
    }

    @Override
    public Object asHostValue(Object context, Object value) {
        return null;
    }

    @Override
    public Object getLanguageView(Object receiver, Object value) {
        HostContext context = (HostContext) receiver;

        Object wrapped;
        if (value instanceof TruffleObject) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            try {
                assert !lib.hasLanguage(value) || lib.getLanguage(value) != HostLanguage.class;
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
            wrapped = ToHostNode.convertToObject(value, context.internalContext.getHostContext(), lib);
        } else {
            wrapped = value;
        }
        return HostObject.forObject(wrapped, context);
    }

    @Override
    public Object asHostDynamicClass(Object config, Class<?> value) {
        return null;
    }

    @Override
    public Object asHostStaticClass(Object config, Class<?> value) {
        return null;
    }

    @Override
    public boolean isHostValue(Object value) {
        return false;
    }

    @Override
    public Object asHostValue(Object value) {
        return null;
    }

    @Override
    public boolean isHostException(Object value) {
        return false;
    }

    @Override
    public Throwable asHostException(Object value) {
        return null;
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
    public void disposeContext(Object receiver) {
        HostContext context = (HostContext) receiver;
        context.disposeClassLoader();
    }

}
