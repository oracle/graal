/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
