/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.wrapper;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineDispatch;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;
import org.graalvm.polyglot.io.IOAccess;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;

/**
 * This class simulates a host to guest remote boundary. All parameters are designed to be easily
 * serializable.
 */
final class HostEntryPoint {

    private final AbstractPolyglotImpl guestPolyglot;

    private final AtomicLong nextId = new AtomicLong();
    private final Map<Long, Object> idToGuestObject = new HashMap<>();
    private final Map<Object, Long> guestObjectToId = new IdentityHashMap<>();

    private final APIAccess api;
    private final GuestEntryPoint guestEntry;

    HostEntryPoint(AbstractPolyglotImpl guestPolyglot) {
        this.guestPolyglot = guestPolyglot;
        this.api = guestPolyglot.getAPIAccess();
        this.guestEntry = new GuestEntryPoint(this);
    }

    public long remoteCreateEngine(SandboxPolicy sandboxPolicy) {
        // host access needs to be replaced
        GuestHostLanguage hostLanguage = new GuestHostLanguage(guestPolyglot, (AbstractHostAccess) guestPolyglot.createHostAccess());
        Object engine = guestPolyglot.buildEngine(new String[0], sandboxPolicy, OutputStream.nullOutputStream(), OutputStream.nullOutputStream(), null, new HashMap<>(), false, false, null, null,
                        hostLanguage, false, false, null);
        return guestToHost(engine);
    }

    private long guestToHost(Object value) {
        Long id = guestObjectToId.get(value);
        if (id == null) {
            id = nextId.getAndIncrement();
            guestObjectToId.put(value, id);
            idToGuestObject.put(id, value);
        }
        return id;
    }

    public long registerHost(Object value) {
        return guestEntry.registerHost(value);
    }

    static final class HostValuePointer {

        final long id;

        HostValuePointer(long id) {
            this.id = id;
        }
    }

    <T> T unmarshall(Class<T> type, long id) {
        return type.cast(idToGuestObject.get(id));
    }

    public long remoteCreateContext(long engineId, SandboxPolicy sandboxPolicy, String tmpDir) {
        Engine engine = unmarshall(Engine.class, engineId);
        Object receiver = api.getEngineReceiver(engine);
        AbstractEngineDispatch dispatch = api.getEngineDispatch(engine);
        Context remoteContext = dispatch.createContext(receiver, engine, sandboxPolicy, null, null, null, false, null, PolyglotAccess.NONE, false,
                        false, false, false, false, null, new HashMap<>(), new HashMap<>(),
                        new String[0], IOAccess.NONE, null,
                        false, null, EnvironmentAccess.NONE,
                        null, null, null, null, tmpDir, null, true, false, false);
        return guestToHost(remoteContext);
    }

    public long remoteEval(long contextId, String languageId, String characters) {
        Context context = unmarshall(Context.class, contextId);
        Value v = context.eval(languageId, characters);
        return guestToHost(api.getValueReceiver(v));
    }

    public long remoteGetBindings(long contextId, String languageId) {
        Context context = unmarshall(Context.class, contextId);
        Value v = context.getBindings(languageId);
        return guestToHost(api.getValueReceiver(v));
    }

    public Object remoteMessage(long contextId, long receiverId, Message message, Object[] args) {
        Context c = unmarshall(Context.class, contextId);
        c.enter();
        try {
            Object receiver = unmarshall(Object.class, receiverId);
            Object[] localValues = unmarshallAtGuest(contextId, args);
            ReflectionLibrary lib = ReflectionLibrary.getFactory().getUncached(receiver);
            Object result;
            try {
                result = lib.send(receiver, message, localValues);
            } catch (Exception e) {
                if (e instanceof AbstractTruffleException) {
                    // also send over stack traces and messages
                    return new GuestExceptionPointer(guestToHost(e), e.getMessage());
                } else {
                    throw new RuntimeException("Internal error thrown by remote message.", e);
                }
            }
            return marshallAtGuest(result);
        } finally {
            c.leave();
        }
    }

    static class GuestExceptionPointer {

        final long id;
        final String message;

        GuestExceptionPointer(long id, String message) {
            this.id = id;
            this.message = message;
        }

    }

    private Object marshallAtGuest(Object result) {
        if (result instanceof GuestHostValue) {
            return new HostValuePointer(((GuestHostValue) result).id);
        } else if (HostGuestValue.isGuestPrimitive(result)) {
            return result;
        } else if (result instanceof TruffleObject) {
            return guestToHost(result);
        } else {
            throw new UnsupportedOperationException(result.getClass().getName());
        }
    }

    private Object[] unmarshallAtGuest(long contextId, Object[] args) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Object[]) {
                newArgs[i] = unmarshallAtGuest(contextId, (Object[]) arg);
            } else if (arg instanceof Long) {
                newArgs[i] = unmarshall(Object.class, (long) arg);
            } else if (arg instanceof HostValuePointer) {
                newArgs[i] = new GuestHostValue(guestEntry, contextId, ((HostValuePointer) arg).id);
            } else if (arg == null) { // null is currently reserved for interop library
                newArgs[i] = InteropLibrary.getUncached();
            } else if (HostGuestValue.isGuestPrimitive(arg)) {
                newArgs[i] = arg;
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return newArgs;
    }

    public void registerHostContext(long guestContextId, HostContext context) {
        guestEntry.registerHostContext(guestContextId, context);
    }

    public Object unmarshallHost(Class<?> type, long id) {
        return guestEntry.unmarshall(type, id);
    }

    public void shutdown(long engineId) {
        Engine engine = unmarshall(Engine.class, engineId);
        Object receiver = api.getEngineReceiver(engine);
        AbstractEngineDispatch dispatch = api.getEngineDispatch(engine);
        dispatch.shutdown(receiver);
    }

}
