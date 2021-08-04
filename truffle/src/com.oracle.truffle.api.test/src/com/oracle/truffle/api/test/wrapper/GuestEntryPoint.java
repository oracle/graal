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
package com.oracle.truffle.api.test.wrapper;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;

final class GuestEntryPoint {

    final HostEntryPoint hostToGuest;

    private final AtomicLong nextId = new AtomicLong();
    private final Map<Long, Object> idtoRemoteObject = new HashMap<>();
    private final Map<Object, Long> remoteObjectToId = new IdentityHashMap<>();
    private final Map<Long, HostContext> contexts = new HashMap<>();

    GuestEntryPoint(HostEntryPoint hostToGuest) {
        this.hostToGuest = hostToGuest;
    }

    <T> T unmarshall(Class<T> type, long id) {
        return type.cast(idtoRemoteObject.get(id));
    }

    public void registerHostContext(long guestContextId, HostContext context) {
        contexts.put(guestContextId, context);
    }

    public long registerHost(Object value) {
        Long id = remoteObjectToId.get(value);
        if (id == null) {
            id = nextId.getAndIncrement();
            remoteObjectToId.put(value, id);
            idtoRemoteObject.put(id, value);
        }
        return id;
    }

    public Object remoteMessage(long contextId, long hostObjectId, Message message, Object[] values) {
        Object receiver = unmarshall(Object.class, hostObjectId);
        Object[] localValues = unmarshallAtHost(contextId, values);
        ReflectionLibrary lib = ReflectionLibrary.getFactory().getUncached(receiver);
        Object result;
        try {
            result = lib.send(receiver, message, localValues);
        } catch (Exception e) {
            throw new UnsupportedOperationException("exceptions not implemented", e);
        }
        if (result instanceof HostGuestValue) {
            throw new UnsupportedOperationException();
        } else if (result instanceof TruffleObject) {
            return new GuestHostValue(null, contextId, hostObjectId);
        } else {
            // send primitives directly
            return result;
        }
    }

    private Object[] unmarshallAtHost(long contextId, Object[] args) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            unmarshall(contextId, newArgs, i, arg);
        }
        return newArgs;
    }

    private void unmarshall(long contextId, Object[] newArgs, int i, Object arg) {
        if (arg instanceof Object[]) {
            newArgs[i] = unmarshallAtHost(contextId, (Object[]) arg);
        } else if (arg instanceof Long) {
            newArgs[i] = unmarshall(Object.class, (long) arg);
        } else if (arg instanceof GuestValuePointer) {
            newArgs[i] = new HostGuestValue(hostToGuest, contextId, ((GuestValuePointer) arg).id);
        } else if (arg == null) { // null is currently reserved for interop library
            newArgs[i] = InteropLibrary.getUncached();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    static final class GuestValuePointer {

        final long id;

        GuestValuePointer(long id) {
            this.id = id;
        }
    }

}
