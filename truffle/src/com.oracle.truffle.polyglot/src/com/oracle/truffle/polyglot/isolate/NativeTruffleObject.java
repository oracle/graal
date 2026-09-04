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

import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.IsolateThread;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.Message;
import com.oracle.truffle.api.library.ReflectionLibrary;
import org.graalvm.nativebridge.ForeignObjectCleaner;

import java.lang.ref.WeakReference;

/**
 * The guest object reference lives on the host JVM. It accepts all Truffle messages via the
 * ReflectionLibrary and passes them on to the guest isolate.
 */
@ExportLibrary(ReflectionLibrary.class)
final class NativeTruffleObject implements TruffleObject {

    private static final Message HAS_LANGUAGE = Message.resolveExact(InteropLibrary.class, "hasLanguage", Object.class);
    private static final Message GET_LANGUAGE = Message.resolveExact(InteropLibrary.class, "getLanguage", Object.class);
    private static final Message IS_SCOPE = Message.resolveExact(InteropLibrary.class, "isScope", Object.class);
    private static final Message MESSAGE_READ_BUFFER = Message.resolveExact(InteropLibrary.class, "readBuffer", Object.class, long.class, byte[].class, int.class, int.class);

    private final long guestReferenceId;
    final ForeignContext context;
    NativeTruffleException exception;

    private NativeTruffleObject(long guestReferenceId, ForeignContext context) {
        this.guestReferenceId = guestReferenceId;
        this.context = context;
    }

    long getGuestReferenceId() {
        return guestReferenceId;
    }

    /**
     * This runnable will be invoked when the guest object reference is collected.
     */
    private static final class CleanupReference extends ForeignObjectCleaner<NativeTruffleObject> {

        private final WeakReference<ForeignContext> contextRef;
        private final long guestObjectId;

        CleanupReference(NativeTruffleObject referent, Isolate<?> isolate, ForeignContext context, long guestObjectId) {
            super(referent, isolate);
            this.contextRef = context.asWeakReference();
            this.guestObjectId = guestObjectId;
        }

        @Override
        protected void cleanUp(IsolateThread isolateThread) {
            ForeignContext context = contextRef.get();
            if (context != null) {
                context.releaseReference(guestObjectId);
            }
        }

        @Override
        public String toString() {
            return "NativeTruffleObject " + guestObjectId;
        }
    }

    /**
     * Creates a new {@code HostToGuestObject}.
     *
     * @param guestReferenceId of the guest object that is referenced
     * @param context handle of the (local) {@code NativeContext} this object is associated with.
     * @return a new {@code HostToGuestObject}
     */
    static NativeTruffleObject createReference(long guestReferenceId, ForeignContext context) {
        NativeTruffleObject ref = new NativeTruffleObject(guestReferenceId, context);
        new CleanupReference(ref, context.getPeer().getIsolate(), context, guestReferenceId).register();
        return ref;
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH", justification = "Bug in findbugs")
    Object send(Message message, Object[] args) throws Exception {
        // We cannot safely pass Class<? extends TruffleLanguage> over the isolate boundary as it
        // requires classloading. We need to disable delegation of the `InteropLibrary#getLanguage`
        // message. To keep the interop protocol consistent we also need to disable delegation of
        // `InteropLibrary#hasLanguage` and `InteropLibrary#isScope` messages.
        if (HAS_LANGUAGE == message || IS_SCOPE == message) {
            return false;
        } else if (GET_LANGUAGE == message) {
            throw UnsupportedMessageException.create();
        }
        int messageId = message.getId();
        SymbolTable.Symbol symbol = null;
        try {
            if (SymbolTable.isMemberNameMessage(messageId)) {
                SymbolTable.Symbol s = context.hostSymbols.acquireSymbol((String) args[0]);
                if (s != null) {
                    args[0] = symbol = s;
                }
            }
            byte[] messageReadBufferIntoOutByteArray = null;
            int messageReadBufferIntoOutByteArrayOffset = -1;
            if (message == MESSAGE_READ_BUFFER) {
                messageReadBufferIntoOutByteArray = (byte[]) args[1];
                messageReadBufferIntoOutByteArrayOffset = (Integer) args[2];
                args[1] = null;
                args[2] = 0;
            }
            // depending on message type, enable scoping here for function invocations. also for
            // instantiate?
            Object result = context.guestObjectReflection.dispatch(guestReferenceId, messageId, args);
            if (symbol != null) {
                symbol.finishRegistration();
            }
            if (result == null) {
                return null;
            }
            if (message == MESSAGE_READ_BUFFER) {
                byte[] outByteArray = (byte[]) result;
                System.arraycopy(outByteArray, 0, messageReadBufferIntoOutByteArray, messageReadBufferIntoOutByteArrayOffset, outByteArray.length);
                result = null;
            }
            return result;
        } finally {
            if (symbol != null) {
                symbol.release();
            }
        }
    }
}
