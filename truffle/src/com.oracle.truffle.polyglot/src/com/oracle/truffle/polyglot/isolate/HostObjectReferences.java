/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot.isolate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.HeapIsolationException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.polyglot.isolate.ReferenceUnavailableException.Kind;

/**
 * The {@link HostObjectReferences} lives on the host (JVM) side and accepts incoming requests to a
 * host object via the messageDispatch() methods that are invoked by the guest via JNI.
 *
 * Host objects are registered for dispatch via
 * {@link HostObjectReferences#registerHostObject(TruffleObject)} method. Every call to this method
 * (even for the same object) creates a new entry in the export reference table, reflecting a
 * corresponding, individual reference on the guest side. The references can be released by the
 * guest via the {@link HostObjectReferences#releaseReference(long,long)} method, which is again
 * invoked via JNI, typically when the guest no longer needs the reference.
 *
 */
final class HostObjectReferences implements ReflectionLibraryDispatch {

    private static final Message[] MESSAGES_BY_ID = InteropLibrary.getFactory().getMessages().toArray(new Message[0]);
    private static final Message MESSAGE_READ_BUFFER = Message.resolveExact(InteropLibrary.class, "readBuffer", Object.class, long.class, byte[].class, int.class, int.class);
    private static final Message MESSAGE_AS_HOST_OBJECT = Message.resolveExact(InteropLibrary.class, "asHostObject", Object.class);
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private final Map<Long, TruffleObject> exportById;
    private final AtomicLong currentReferenceId;
    @CompilationFinal ForeignContext foreignContext; // effectively final after initialization

    private HostObjectReferences() {
        this.exportById = new ConcurrentHashMap<>();
        this.currentReferenceId = new AtomicLong(1);
    }

    void setAPI(ForeignContext api) {
        assert foreignContext == null : "Context is already set.";
        this.foreignContext = api;
    }

    static HostObjectReferences create() {
        return new HostObjectReferences();
    }

    long registerHostObject(TruffleObject obj) {
        assert !InteropLibrary.getUncached().isNull(obj) : "Host null must be canonicalized";
        long id = currentReferenceId.getAndIncrement();
        exportById.put(id, obj);
        return id;
    }

    Object getHostObject(long objId) {
        return exportById.get(objId);
    }

    @Override
    public void releaseReference(long objectHandle) {
        exportById.remove(objectHandle);
    }

    @Override
    public Object dispatch(long objectId, int messageId, Object[] args) throws Exception {
        Message message = MESSAGES_BY_ID[messageId];

        // get the host object
        TruffleObject receiver = exportById.get(objectId);
        if (receiver == null) {
            throw new ReferenceUnavailableException(Kind.HOST, objectId);
        }

        if (messageId == MESSAGE_AS_HOST_OBJECT.getId() && !InteropLibrary.getUncached(receiver).isException(receiver)) {
            throw HeapIsolationException.create();
        }

        if (messageId == MESSAGE_READ_BUFFER.getId()) {
            int byteLength = (Integer) args[3];
            args[1] = new byte[Math.max(byteLength, 0)];
            args[2] = 0;
        }

        ReflectionLibrary reflectionLibrary = ReflectionLibrary.getFactory().getUncached(receiver);
        Object[] useArguments;
        if (args != null) {
            useArguments = args;
        } else {
            useArguments = EMPTY_OBJECT_ARRAY;
        }
        Object result = reflectionLibrary.send(receiver, message, useArguments);
        /*
         * Prevent repeated new object registration for nulls.
         */
        if (result instanceof TruffleObject && InteropLibrary.getUncached().isNull(result)) {
            result = null;
        }
        if (messageId == MESSAGE_READ_BUFFER.getId()) {
            result = args[1];
        }
        return result;
    }
}
