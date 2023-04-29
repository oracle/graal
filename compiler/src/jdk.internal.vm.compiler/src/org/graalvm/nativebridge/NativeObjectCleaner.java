/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.nativebridge;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * A weak reference performing a given action when a referent becomes weakly reachable and is
 * enqueued into reference queue. The class is not active. The reference queue check is performed
 * anytime a new {@link NativeObjectCleaner#register()} is called, or it can be performed explicitly
 * using {@link NativeObjectCleaner#processPendingCleaners()}.
 */
public abstract class NativeObjectCleaner<T> extends WeakReference<T> {

    private static final Object INVALID_ISOLATE_THREAD = new Object();
    private static final ReferenceQueue<Object> cleanersQueue = new ReferenceQueue<>();

    final NativeIsolate isolate;

    /**
     * Creates a new {@link NativeObjectCleaner}.
     *
     * @param referent object the new weak reference will refer to
     * @param isolate the native object isolate
     */
    protected NativeObjectCleaner(T referent, NativeIsolate isolate) {
        super(referent, cleanersQueue);
        this.isolate = isolate;
    }

    /**
     * Registers {@link NativeObjectCleaner} for cleanup.
     */
    public final NativeObjectCleaner<T> register() {
        processPendingCleaners();
        if (!isolate.isDisposed()) {
            isolate.cleaners.add(this);
        }
        return this;
    }

    /**
     * At some point after a {@code referent} is garbage collected the {@link NativeIsolate} is
     * entered and the {@link #cleanUp(long)} is executed with the isolate thread address parameter.
     * This method should perform cleanup in the isolate heap.
     *
     * @param isolateThread the isolate thread address to call into isolate.
     */
    protected abstract void cleanUp(long isolateThread);

    /**
     * Performs an explicit clean up of enqueued {@link NativeObjectCleaner}s.
     */
    public static void processPendingCleaners() {
        Map<NativeIsolate, Object> enteredThreads = null;
        NativeObjectCleaner<?> cleaner;
        try {
            while ((cleaner = (NativeObjectCleaner<?>) cleanersQueue.poll()) != null) {
                NativeIsolate isolate = cleaner.isolate;
                if (isolate.cleaners.remove(cleaner)) {
                    Object enteredThread;
                    if (enteredThreads == null) {
                        enteredThreads = new HashMap<>();
                        enteredThread = null;
                    } else {
                        enteredThread = enteredThreads.get(isolate);
                    }
                    if (enteredThread == null) {
                        enteredThread = isolate.tryEnter();
                        if (enteredThread == null) {
                            enteredThread = INVALID_ISOLATE_THREAD;
                        }
                        enteredThreads.put(isolate, enteredThread);
                    }
                    if (enteredThread != INVALID_ISOLATE_THREAD) {
                        cleanImpl(((NativeIsolateThread) enteredThread).getIsolateThreadId(), cleaner);
                    }
                }
            }
        } finally {
            if (enteredThreads != null) {
                for (Object enteredThread : enteredThreads.values()) {
                    if (enteredThread != INVALID_ISOLATE_THREAD) {
                        ((NativeIsolateThread) enteredThread).leave();
                    }
                }
            }
        }
    }

    private static void cleanImpl(long isolateThread, NativeObjectCleaner<?> cleaner) {
        cleaner.cleanUp(isolateThread);
    }
}
