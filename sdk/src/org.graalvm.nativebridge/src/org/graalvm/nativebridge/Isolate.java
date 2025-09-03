/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a generic isolate abstraction used to execute code in a separate isolated heap.
 * <p>
 * This class defines the common API for interacting with isolate-based execution environments, such
 * as native image isolates ({@link NativeIsolate}), external process isolates
 * ({@link ProcessIsolate}), or HotSpot isolates ({@link HSIsolate}).
 * <p>
 * Threads must explicitly enter and leave an isolate to perform operations within it. Threads must
 * call {@link #enter()} or {@link #tryEnter()} before doing a foreign call to the isolate, and must
 * call {@link IsolateThread#leave()} when finished.
 * <p>
 * An isolate may be shut down via {@link #shutdown()}. If there is an isolate entered thread, the
 * isolate is shut down after its last attached thread leaves. After shutdown, all foreign resources
 * are released and the isolate is considered {@link #isDisposed() disposed}.
 * <p>
 * Each isolate is assigned a transiently unique identifier ({@link #getIsolateId()}) valid while
 * the isolate is alive. Once disposed, the ID may be reused.
 *
 * @see NativeIsolate
 * @see ProcessIsolate
 * @see HSIsolate
 */
public abstract sealed class Isolate<T extends IsolateThread> permits AbstractIsolate, HSIsolate {

    private static final AtomicInteger UUIDS = new AtomicInteger(0);

    final long uuid;
    final Set<ForeignObjectCleaner<?>> cleaners;

    Isolate() {
        this.uuid = UUIDS.incrementAndGet();
        this.cleaners = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    /**
     * Enters this {@link Isolate} on the current thread.
     *
     * @throws IllegalStateException when this isolate is already closed or being closed.
     * @throws IsolateDeathException if the isolate supports fatal error recovery and has
     *             encountered a fatal error.
     */
    public abstract T enter();

    /**
     * Tries to enter this {@link Isolate} on the current thread.
     *
     * @return {@link IsolateThread} on success or {@code null} when this {@link Isolate} is closed
     *         or being closed.
     * @see #enter()
     */
    public abstract T tryEnter();

    /**
     * Detaches the current thread from this isolate.
     */
    public abstract void detachCurrentThread();

    /**
     * Requests an isolate shutdown. If there is no host thread entered into this {@link Isolate}
     * the isolate is closed and the isolate heap is freed. If this {@link Isolate} has active
     * threads the isolate is freed by the last leaving thread.
     */
    public abstract boolean shutdown();

    /**
     * Returns true if the current thread is entered to this {@link Isolate}.
     */
    public abstract boolean isActive();

    /**
     * Returns true if the isolate shutdown process has already begun or is finished.
     */
    public abstract boolean isDisposed();

    /**
     * Returns the unique ID of the isolate. The ID remains unique as long as the isolate is
     * {@link #isActive() active}. Once the isolate is {@link #isDisposed() disposed}, the ID may be
     * reassigned to a newly created isolate.
     */
    public abstract long getIsolateId();

    /**
     * Returns all active (strongly reachable) {@link Peer} instances using this isolate.
     *
     * <p>
     * This method is useful for cleanup during isolate teardown. Active peers within the isolate
     * hold references to objects in the host, which must be freed to prevent memory leaks.
     * </p>
     *
     * <p>
     * <b>Typical usage:</b>
     * </p>
     *
     * <pre>{@code
     * for (Peer peer : isolate.getActivePeers()) {
     *     peer.release();
     * }
     * }</pre>
     *
     * <p>
     * <strong>Warning:</strong> This method should only be called within the isolate during isolate
     * teardown. It is intended strictly for use in final cleanup logic.
     * </p>
     */
    public Iterable<Peer> getActivePeers() {
        List<Peer> peers = new ArrayList<>();
        for (ForeignObjectCleaner<?> foreignObjectCleaner : cleaners) {
            Object value = foreignObjectCleaner.get();
            if (value instanceof Peer peer) {
                peers.add(peer);
            }
        }
        return peers;
    }
}
