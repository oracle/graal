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

import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JavaVM;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;

/**
 * An adapter for {@link JNIMethodScope} that integrates it with the {@link Isolate} interface.
 */
public final class HSIsolate extends Isolate<HSIsolateThread> {

    private static volatile HSIsolate instance;

    private final JavaVM hsJavaVm;

    /**
     * Creates a new {@link HSIsolate} instance for the given {@code jniEnv}. Since only a single
     * HotSpot VM can exist per process, this method may be called only once, ideally immediately
     * after the isolate is created. Subsequent access to the instance should be done via the
     * {@link #get()} method.
     *
     * @return the newly created {@link HSIsolate} instance.
     * @throws IllegalStateException if an {@link HSIsolate} has already been created.
     */
    public static synchronized HSIsolate create(JNIEnv jniEnv) {
        if (instance != null) {
            throw new IllegalStateException("HSIsolate already exists.");
        }
        HSIsolate result = new HSIsolate(JNIUtil.GetJavaVM(jniEnv));
        instance = result;
        return result;
    }

    /**
     * Gets the {@link HSIsolate} object.
     *
     * @throws IllegalStateException when {@link NativeIsolate} does not exist.
     */
    public static HSIsolate get() {
        HSIsolate result = instance;
        if (instance == null) {
            throw new IllegalStateException("HSIsolate was not initialized.");
        }
        return result;
    }

    /**
     * Gets the {@link HSIsolate} object if it was {@link #create(JNIEnv) created} or {@code null}.
     */
    static HSIsolate getOrNull() {
        return instance;
    }

    private HSIsolate(JavaVM hsJavaVm) {
        this.hsJavaVm = hsJavaVm;
    }

    @Override
    public HSIsolateThread enter() {
        return new HSIsolateThread(this, JNIMethodScope.env());
    }

    @Override
    public HSIsolateThread tryEnter() {
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        return scope != null ? new HSIsolateThread(this, scope.getEnv()) : null;
    }

    @Override
    public void detachCurrentThread() {
    }

    @Override
    public boolean shutdown() {
        return false;
    }

    @Override
    public boolean isActive() {
        return JNIMethodScope.scopeOrNull() != null;
    }

    @Override
    public boolean isDisposed() {
        return false;
    }

    @Override
    public long getIsolateId() {
        return hsJavaVm.rawValue();
    }

    @Override
    public String toString() {
        return "HSIsolate[" + uuid + " for 0x" + Long.toHexString(hsJavaVm.rawValue()) + "]";
    }
}
