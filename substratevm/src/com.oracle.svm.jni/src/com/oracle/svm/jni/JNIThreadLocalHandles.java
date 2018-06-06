/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni;

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

/**
 * Implementation of JNI local object handles, which are bound to a specific thread and can be
 * created and destroyed implicitly or explicitly. Local handles can be managed in frames and a
 * frame can be discarded in its entirety.
 */
public final class JNIThreadLocalHandles {
    /**
     * Minimum available local handles according to specification: "Before it enters a native
     * method, the VM automatically ensures that at least 16 local references can be created".
     */
    static final int NATIVE_CALL_MINIMUM_HANDLE_CAPACITY = 16;
    private static final int INITIAL_NUMBER_OF_HANDLES = NATIVE_CALL_MINIMUM_HANDLE_CAPACITY;
    private static final int INITIAL_NUMBER_OF_FRAMES = 4;

    private static final int MIN_VALUE = Math.toIntExact(1 + JNIObjectHandles.nullHandle().rawValue());
    private static final int MAX_VALUE = Integer.MAX_VALUE;

    private static final FastThreadLocalObject<JNIThreadLocalHandles> handles = FastThreadLocalFactory.createObject(JNIThreadLocalHandles.class);

    public static JNIThreadLocalHandles get() {
        if (handles.get() == null) {
            handles.set(new JNIThreadLocalHandles());
        }
        return handles.get();
    }

    public static boolean isInRange(JNIObjectHandle handle) {
        return handle.rawValue() >= MIN_VALUE && handle.rawValue() <= MAX_VALUE;
    }

    private Object[] objects = new Object[MIN_VALUE + INITIAL_NUMBER_OF_HANDLES];
    private int top = MIN_VALUE;

    private int[] frameStack = new int[INITIAL_NUMBER_OF_FRAMES];
    private int frameCount = 0;

    private JNIThreadLocalHandles() {
    }

    private static int toIndex(JNIObjectHandle handle) {
        return (int) handle.rawValue();
    }

    public int getHandleCount() {
        return top - MIN_VALUE;
    }

    public int pushFrame(int capacity) {
        if (frameCount == frameStack.length) {
            int[] oldArray = frameStack;
            frameStack = new int[oldArray.length * 2];
            System.arraycopy(oldArray, 0, frameStack, 0, oldArray.length);
        }
        frameStack[frameCount] = top;
        frameCount++;
        ensureCapacity(capacity);
        return frameCount;
    }

    public JNIObjectHandle create(Object obj) {
        if (obj == null) {
            return JNIObjectHandles.nullHandle();
        }
        ensureCapacity(1);
        int index = top;
        objects[index] = obj;
        top++;
        return (JNIObjectHandle) WordFactory.signed(index);
    }

    @SuppressWarnings("unchecked")
    public <T> T getObject(JNIObjectHandle handle) {
        return (T) objects[toIndex(handle)];
    }

    public boolean delete(JNIObjectHandle handle) {
        int index = toIndex(handle);
        Object previous = objects[index];
        objects[index] = null;
        return previous != null;
    }

    public void popFrame() {
        popFramesIncluding(frameCount);
    }

    public void popFramesIncluding(int frame) {
        assert frame > 0 && frame <= frameCount;
        int previousTop = top;
        frameCount = frame - 1;
        top = frameStack[frameCount];
        for (int i = top; i < previousTop; i++) {
            objects[i] = null; // so objects can be garbage collected
        }
    }

    public void ensureCapacity(int capacity) {
        if (top + capacity >= objects.length) {
            Object[] oldArray = objects;
            int newLength = oldArray.length * 2;
            assert newLength >= top + capacity;
            objects = new Object[newLength];
            System.arraycopy(oldArray, 0, objects, 0, oldArray.length);
        }
    }
}
