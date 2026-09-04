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

import java.lang.ref.WeakReference;
import java.util.Objects;

import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.polyglot.Context;

final class GuestContext {

    private static final long UNSET_HANDLE = 0L;

    final Context context;
    final Object polyglotContextReceiver;
    final ReflectionLibraryDispatch guestToHostDispatch;
    final GuestObjectReferences hostToGuestObjectReferences;
    final SymbolTable.Sink hostSymbols;
    final SymbolTable.Source guestSymbols;
    private final WeakReference<GuestContext> weakThis;
    private volatile long contextHandle = UNSET_HANDLE;
    private boolean disposed;

    GuestContext(Context context, Object polyglotContextReceiver, ReflectionLibraryDispatch guestToHostDispatch, long hostStackSpaceHeadroom) {
        this.context = Objects.requireNonNull(context, "Context must be non-null");
        this.polyglotContextReceiver = Objects.requireNonNull(polyglotContextReceiver, "PolyglotContextReceiver must be non-null");
        this.guestToHostDispatch = ForeignReflectionLibraryDispatch.optimized(this, (ForeignReflectionLibraryDispatch) guestToHostDispatch, hostStackSpaceHeadroom);
        this.hostToGuestObjectReferences = new GuestObjectReferences(this);
        this.hostSymbols = SymbolTable.createSinkTable();
        this.guestSymbols = SymbolTable.createSourceTable();
        this.weakThis = new WeakReference<>(this);
    }

    void setHandle(long handle) {
        if (contextHandle != UNSET_HANDLE) {
            throw new IllegalStateException("Context handle is already set");
        }
        contextHandle = handle;
    }

    long getHandle() {
        long result = contextHandle;
        if (result == UNSET_HANDLE) {
            throw new IllegalStateException("Context handle is not set");
        }
        return result;
    }

    WeakReference<GuestContext> asWeakReference() {
        return weakThis;
    }

    synchronized void dispose() {
        if (!disposed) {
            disposed = true;
            // We need an eager cleanup to break host-isolate reference cycle.
            ((ForeignObject) guestToHostDispatch).getPeer().release();
            hostToGuestObjectReferences.releaseAllReferences();
        }
    }

    synchronized void releaseReference(long handle) {
        if (!disposed) {
            guestToHostDispatch.releaseReference(handle);
        }
    }
}
