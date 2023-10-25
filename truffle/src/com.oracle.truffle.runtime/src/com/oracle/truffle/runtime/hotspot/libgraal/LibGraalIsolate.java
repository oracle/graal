/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.hotspot.libgraal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Represents a single libgraal isolate. All {@link LibGraalObject}s have a {@link LibGraalIsolate}
 * context.
 */
public final class LibGraalIsolate {

    private final long id;
    private final long address;

    private static final Map<Long, LibGraalIsolate> isolates = new HashMap<>();

    static synchronized LibGraalIsolate forIsolateId(long isolateId, long address) {
        return isolates.computeIfAbsent(isolateId, id -> new LibGraalIsolate(id, address));
    }

    private LibGraalIsolate(long isolateId, long address) {
        this.id = isolateId;
        this.address = address;
    }

    public long getId() {
        return id;
    }

    /**
     * Gets the isolate associated with the current thread. The current thread must be in an
     * {@linkplain LibGraalScope opened} scope.
     *
     * @throws IllegalStateException if the current thread is not attached to libgraal
     */
    public static LibGraalIsolate current() {
        return LibGraalScope.current().getIsolate();
    }

    /**
     * Gets the value corresponding to {@code key} from a key-value store of singleton objects. If
     * no value corresponding to {@code key} currently exists, then it is computed with
     * {@code supplier} and inserted in the store.
     *
     * This is used to obtain objects whose lifetime is bound to the isolate represented by this
     * object.
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> T getSingleton(Class<T> key, Supplier<T> supplier) {
        // Cannot use HashMap.computeIfAbsent as it will throw a ConcurrentModificationException
        // if supplier recurses here to compute another singleton.
        if (!singletons.containsKey(key)) {
            singletons.put(key, supplier.get());
        }
        return (T) singletons.get(key);
    }

    private final Map<Class<?>, Object> singletons = new HashMap<>();

    /**
     * Strong references to the {@link WeakReference} objects.
     */
    private final Set<LibGraalIsolate.Cleaner> cleaners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    void register(LibGraalObject obj, long handle) {
        cleanHandles();
        Cleaner cref = new Cleaner(cleanersQueue, obj, handle);
        cleaners.add(cref);
    }

    /**
     * Processes {@link #cleanersQueue} to release any handles whose {@link LibGraalObject} objects
     * are now unreachable.
     */
    private void cleanHandles() {
        Cleaner cleaner;
        while ((cleaner = (Cleaner) cleanersQueue.poll()) != null) {
            cleaners.remove(cleaner);
            if (!cleaner.clean()) {
                new Exception(String.format("Error releasing handle %d in isolate %d (0x%x)", cleaner.handle, id, address)).printStackTrace(System.out);
            }
        }
    }

    /**
     * Queue into which a {@link Cleaner} is enqueued when its {@link LibGraalObject} referent
     * becomes unreachable.
     */
    private final ReferenceQueue<LibGraalObject> cleanersQueue = new ReferenceQueue<>();

    private static final class Cleaner extends WeakReference<LibGraalObject> {
        private final long handle;

        Cleaner(ReferenceQueue<LibGraalObject> cleanersQueue, LibGraalObject referent, long handle) {
            super(referent, cleanersQueue);
            this.handle = handle;
        }

        boolean clean() {
            return LibGraalObject.releaseHandle(LibGraalScope.getIsolateThread(), handle);
        }
    }

    /**
     * Notifies that the {@code JavaVM} associated with {@code isolate} has been destroyed. All
     * subsequent accesses to objects in the isolate will throw an
     * {@link DestroyedIsolateException}. Called by {@code LibGraalFeature#shutdownLibGraal} using
     * JNI.
     */
    public static synchronized void unregister(long isolateId) {
        LibGraalIsolate isolate = isolates.remove(isolateId);
        // The isolates.remove(isolateId) may return null when no LibGraalScope was created for the
        // given isolateId.
        if (isolate != null) {
            isolate.destroyed = true;
        }
    }

    @Override
    public String toString() {
        return String.format("%s[%d (0x%x)]", getClass().getSimpleName(), id, address);
    }

    private boolean destroyed;

    public boolean isValid() {
        return !destroyed;
    }
}
