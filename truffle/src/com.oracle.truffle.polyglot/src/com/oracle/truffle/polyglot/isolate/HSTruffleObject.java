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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;

import java.lang.ref.WeakReference;

/**
 * The host object reference lives in the guest. It accepts all Truffle messages via the reflection
 * library and relays them via JNI to the HostObjectDispatch in the host JVM.
 *
 * If a host object reference is no longer referenced and thus collected, the associated cleaner
 * will call to the host to release the reference.
 */
@ExportLibrary(ReflectionLibrary.class)
final class HSTruffleObject implements TruffleObject {

    private static final Message MESSAGE_READ_BUFFER = Message.resolveExact(InteropLibrary.class, "readBuffer", Object.class, long.class, byte[].class, int.class, int.class);
    private static final Message MESSAGE_IS_NULL = Message.resolveExact(InteropLibrary.class, "isNull", Object.class);

    final GuestContext context;
    private final long hostReferenceId;

    private HSTruffleObject(GuestContext context, long hostReferenceId) {
        this.context = context;
        this.hostReferenceId = hostReferenceId;
    }

    long getHostReferenceId() {
        return hostReferenceId;
    }

    /**
     * This runnable will be invoked when the host object reference is collected.
     */
    private static class CleanupReference extends CleanableWeakReference<HSTruffleObject> {

        private final WeakReference<GuestContext> contextRef;
        private final long hostObjectId;

        CleanupReference(HSTruffleObject reference, GuestContext context, long hostObjectId) {
            super(reference);
            this.contextRef = context.asWeakReference();
            this.hostObjectId = hostObjectId;
        }

        @Override
        public void run() {
            GuestContext context = contextRef.get();
            if (context != null) {
                context.releaseReference(hostObjectId);
            }
        }
    }

    @SuppressWarnings("unused")
    public static HSTruffleObject createHostObjectReference(long hostReferenceId, GuestContext context) {
        HSTruffleObject ref = new HSTruffleObject(context, hostReferenceId);
        new CleanupReference(ref, context, hostReferenceId);
        return ref;
    }

    @ExportMessage
    @TruffleBoundary
    Object send(Message message, Object[] args) throws Exception {
        /*
         * A host object that represents null never crosses the boundary as a HOST_OBJECT
         * reference: BinaryProtocol.writeHostTypedValue diverts every isNull() host value to the
         * HOST_NULL tag, which materializes as getHostNull() rather than an HSTruffleObject.
         * Therefore an HSTruffleObject is guaranteed to wrap a non-null host object and can answer
         * isNull() locally, avoiding a round trip to the host on every guest-side null check.
         */
        if (MESSAGE_IS_NULL == message) {
            return false;
        }
        byte[] messageReadBufferIntoOutByteArray = null;
        int messageReadBufferIntoOutByteArrayOffset = -1;
        if (message == MESSAGE_READ_BUFFER) {
            messageReadBufferIntoOutByteArray = (byte[]) args[1];
            messageReadBufferIntoOutByteArrayOffset = (Integer) args[2];
            args[1] = null;
            args[2] = 0;
        }
        Object result;
        try {
            result = context.guestToHostDispatch.dispatch(hostReferenceId, message.getId(), args);
        } catch (AbstractTruffleException t) {
            /*
             * Capture the Java stack within the isolate to enable proper Guest Frame merging, which
             * depends on CallTarget.execute frames.
             */
            TruffleStackTrace.fillIn(t);
            throw t;
        }
        if (result == null) {
            return PolyglotIsolateAccessor.ENGINE.getHostNull();
        }
        if (message == MESSAGE_READ_BUFFER) {
            byte[] outByteArray = (byte[]) result;
            System.arraycopy(outByteArray, 0, messageReadBufferIntoOutByteArray, messageReadBufferIntoOutByteArrayOffset, outByteArray.length);
            result = null;
        }
        return result;
    }
}
