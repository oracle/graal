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

import java.lang.ref.Reference;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.collections.Pair;
import org.graalvm.nativebridge.ForeignObject;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.Peer;
import org.graalvm.polyglot.Engine;

final class ForeignEngine implements ForeignObject {

    private final Peer peer;
    private final Engine localEngine;
    private final boolean bound;
    private final long hostStackHeadRoom;
    private final ForeignPolyglotIsolateServices isolateServices;
    private final IsolateSourceCache sourceCache;
    private final Collection<ForeignContext> contexts;
    private boolean closed;
    private volatile Reference<Engine> weakAPI;

    ForeignEngine(Peer peer, Engine localEngine, boolean bound,
                    long hostStackHeadRoom, ForeignPolyglotIsolateServices isolateServices) {
        this.peer = peer;
        this.localEngine = localEngine;
        this.bound = bound;
        this.hostStackHeadRoom = hostStackHeadRoom;
        this.isolateServices = isolateServices;
        this.sourceCache = isolateServices.getSourceCache();
        this.contexts = Collections.newSetFromMap(new WeakHashMap<>());
    }

    @Override
    public Peer getPeer() {
        return peer;
    }

    @Override
    public void setPeer(Peer newPeer) {
        throw new UnsupportedOperationException("SetPeer is not supported.");
    }

    Engine getLocalEngine() {
        return localEngine;
    }

    void setEngineAPIReference(Reference<Engine> api) {
        this.weakAPI = api;
    }

    Reference<Engine> getEngineAPIReference() {
        return weakAPI;
    }

    Engine getEngineAPI() {
        Engine result = weakAPI.get();
        if (result == null) {
            throw CompilerDirectives.shouldNotReachHere("API object must not be garbage collected when engine implementation is in use.");
        }
        return result;
    }

    boolean isBound() {
        return bound;
    }

    long getHostStackHeadRoom() {
        return hostStackHeadRoom;
    }

    IsolateSourceCache getSourceCache() {
        return sourceCache;
    }

    ForeignPolyglotIsolateServices getPolyglotIsolateServices() {
        return isolateServices;
    }

    Pair<Isolate<?>, Long> getKey() {
        return Pair.create(peer.getIsolate(), peer.getHandle());
    }

    synchronized void onContextCreated(ForeignContext foreignContext) {
        if (closed) {
            throw new IllegalStateException("Engine is already closed.");
        }
        PolyglotIsolateHostSupport.registerContext(foreignContext);
        contexts.add(foreignContext);
    }

    synchronized boolean isClosed() {
        return closed;
    }

    synchronized void close() {
        closed = true;
        if (!contexts.isEmpty()) {
            for (Iterator<ForeignContext> iterator = contexts.iterator(); iterator.hasNext();) {
                ForeignContext foreignContext = iterator.next();
                iterator.remove();
                /*
                 * NativeContextDispatch#close does not unregister the NativeContext if the close
                 * method is called when the isolate is still active.
                 */
                if (PolyglotIsolateHostSupport.requireContextRegistered(foreignContext)) {
                    PolyglotIsolateHostSupport.releaseContextRegisteredRequirement(foreignContext, true);
                }
            }
        }
    }
}
