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

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.collections.Pair;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.Peer;
import org.graalvm.polyglot.Context;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

final class ForeignContext implements ForeignObject {

    private final Peer peer;
    private final Context localContext;
    final ReflectionLibraryDispatch guestObjectReflection;
    private final HostObjectReferences guestToHostReceiver;
    private final PolyglotIsolateServices polyglotIsolateServices;
    private final IsolateSourceCache sourceCache;
    private final WeakReference<ForeignContext> weakThis;
    int registrationRequired;
    private volatile Reference<Context> weakAPI;
    private boolean disposed;

    ForeignContext(Peer peer,
                    Context localContext,
                    ReflectionLibraryDispatch guestObjectReflection,
                    HostObjectReferences guestToHostReceiver,
                    PolyglotIsolateServices polyglotIsolateServices,
                    IsolateSourceCache sourceCache) {
        this.peer = peer;
        this.localContext = localContext;
        this.guestObjectReflection = ForeignReflectionLibraryDispatch.optimized(this, (ForeignReflectionLibraryDispatch) guestObjectReflection);
        this.guestToHostReceiver = guestToHostReceiver;
        this.polyglotIsolateServices = polyglotIsolateServices;
        this.sourceCache = sourceCache;
        this.weakThis = new WeakReference<>(this);
        guestToHostReceiver.setAPI(this);
    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public void setPeer(Peer newPeer) {
        throw new UnsupportedOperationException("SetPeer is not supported.");
    }

    void setContextAPIReference(Reference<Context> contextAPIReference) {
        this.weakAPI = contextAPIReference;
    }

    Reference<Context> getContextAPIReference() {
        return weakAPI;
    }

    Context getContextAPI() {
        Context result = weakAPI.get();
        if (result == null) {
            throw CompilerDirectives.shouldNotReachHere("API object must not be garbage collected when context implementation is in use.");
        }
        return result;
    }

    Context getContextAPIOrNull() {
        return weakAPI.get();
    }

    Context getLocalContext() {
        return localContext;
    }

    HostObjectReferences getGuestToHostReceiver() {
        return guestToHostReceiver;
    }

    PolyglotIsolateServices getPolyglotIsolateServices() {
        return polyglotIsolateServices;
    }

    IsolateSourceCache getSourceCache() {
        return sourceCache;
    }

    Pair<Isolate<?>, Long> getKey() {
        return Pair.create(peer.getIsolate(), peer.getHandle());
    }

    WeakReference<ForeignContext> asWeakReference() {
        return weakThis;
    }

    /**
     * Eagerly releases foreign references associated with this {@link ForeignContext}, allowing
     * related objects in the isolated heap to be freed promptly.
     */
    synchronized void dispose() {
        if (!disposed) {
            disposed = true;
            getPeer().release();
            ((ForeignObject) guestObjectReflection).getPeer().release();
        }
    }

    synchronized boolean isDisposed() {
        return disposed;
    }

    synchronized void releaseReference(long handle) {
        if (!disposed) {
            guestObjectReflection.releaseReference(handle);
        }
    }
}
