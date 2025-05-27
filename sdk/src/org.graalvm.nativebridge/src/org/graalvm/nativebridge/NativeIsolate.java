/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * Represents an isolated heap backed by a native-image in-process isolate. Both the host and the
 * native-image isolate co-exist in the same operating system process but have separated heaps.
 * <p>
 * All foreign objects that have a {@link NativePeer} are associated with exactly one
 * {@code NativeIsolate}, which serves as the anchor for native execution and resource management.
 * </p>
 *
 * @see NativePeer
 * @see NativeIsolateThread
 * @see Isolate
 */
public final class NativeIsolate extends AbstractIsolate<NativeIsolateThread> {

    private static final long NULL = 0L;
    private static final Map<Long, NativeIsolate> isolates = new ConcurrentHashMap<>();

    private final long isolateId;
    private final LongUnaryOperator attachThread;
    private final LongUnaryOperator detachThread;
    private final LongBinaryOperator tearDown;
    final LongBinaryOperator releaseObjectHandle;
    private final Consumer<? super NativeIsolate> onIsolateTearDown;

    private NativeIsolate(long isolateId,
                    LongUnaryOperator attachThread,
                    LongUnaryOperator detachThread,
                    LongBinaryOperator tearDown,
                    LongBinaryOperator releaseObjectHandle,
                    ThreadLocal<NativeIsolateThread> threadLocal,
                    Consumer<? super NativeIsolate> onIsolateTearDown) {
        super(threadLocal);
        if (isolateId == NULL) {
            throw new IllegalArgumentException("Isolate address must be non NULL");
        }
        this.isolateId = isolateId;
        this.attachThread = Objects.requireNonNull(attachThread, "AttachThread must be non-null.");
        this.detachThread = Objects.requireNonNull(detachThread, "DetachThread must be non-null.");
        this.tearDown = Objects.requireNonNull(tearDown, "TearDown must be non-null.");
        this.releaseObjectHandle = Objects.requireNonNull(releaseObjectHandle, "ReleaseObjectHandle must be non-null.");
        this.onIsolateTearDown = onIsolateTearDown;
    }

    /**
     * Binds a native image thread to this isolate. When a thread created in the native image enters
     * for the first time to the host, it must be registered to the {@link NativeIsolate} as a
     * native thread.
     *
     * @param isolateThreadId the isolate thread to bind.
     */
    public void registerNativeThread(long isolateThreadId) {
        if (isAttached()) {
            throw new IllegalStateException(String.format("Native thread %s is already attached to isolate %s.", Thread.currentThread(), this));
        }
        registerForeignThread(new NativeIsolateThread(Thread.currentThread(), this, true, isolateThreadId));
    }

    /**
     * Returns the isolate address.
     */
    @Override
    public long getIsolateId() {
        return isolateId;
    }

    @Override
    public String toString() {
        return "NativeIsolate[" + uuid + " for 0x" + Long.toHexString(isolateId) + "]";
    }

    /**
     * Gets the NativeIsolate object for the entered isolate with the specified isolate address.
     * IMPORTANT: Must be used only when the isolate with the specified isolateId is entered.
     *
     * @param isolateId id of an entered isolate
     * @return NativeIsolate object for the entered isolate with the specified isolate address
     * @throws IllegalStateException when {@link NativeIsolate} does not exist for the
     *             {@code isolateId}
     */
    public static NativeIsolate get(long isolateId) {
        NativeIsolate res = isolates.get(isolateId);
        if (res == null) {
            throw new IllegalStateException("NativeIsolate for isolate 0x" + Long.toHexString(isolateId) + " does not exist.");
        }
        return res;
    }

    /**
     * Creates a {@link NativeIsolate} for the {@code isolateId} and {@link NativeIsolateConfig}.
     * This method can be called at most once, preferably right after creating the isolate. Use the
     * {@link #get(long)} method to get an existing {@link NativeIsolate} instance.
     *
     * @return the newly created {@link NativeIsolate} for the {@code isolateId}.
     * @throws IllegalStateException when {@link NativeIsolate} for the {@code isolateId} already
     *             exists.
     */
    public static NativeIsolate create(long isolateId, NativeIsolateConfig config,
                    LongUnaryOperator attachThread,
                    LongUnaryOperator detachThread,
                    LongBinaryOperator tearDown,
                    LongBinaryOperator releaseObjectHandle) {
        NativeIsolate res = new NativeIsolate(isolateId, attachThread, detachThread, tearDown, releaseObjectHandle,
                        config.getThreadLocalFactory().get(), config.getOnIsolateTearDownHook());
        NativeIsolate previous = isolates.put(isolateId, res);
        if (previous != null && !previous.isDisposed()) {
            throw new IllegalStateException("NativeIsolate for isolate 0x" + Long.toHexString(isolateId) + " already exists and is not disposed.");
        }
        return res;
    }

    @Override
    NativeIsolateThread attachCurrentThread() {
        long isolateThreadAddress = attachThread.applyAsLong(isolateId);
        return new NativeIsolateThread(Thread.currentThread(), this, false, isolateThreadAddress);
    }

    @Override
    void detachCurrentThread(NativeIsolateThread currentThread) {
        detachThread.applyAsLong(currentThread.isolateThread);
    }

    @Override
    void callTearDownHook() {

    }

    @Override
    boolean doIsolateShutdown(NativeIsolateThread shutdownThread) {
        if (onIsolateTearDown != null) {
            onIsolateTearDown.accept(this);
        }
        boolean success = tearDown.applyAsLong(isolateId, shutdownThread.isolateThread) == 0;
        if (success) {
            isolates.computeIfPresent(isolateId, (id, nativeIsolate) -> (nativeIsolate == NativeIsolate.this ? null : nativeIsolate));
        }
        return success;
    }

    static Collection<? extends NativeIsolate> getAllNativeIsolates() {
        return isolates.values();
    }
}
