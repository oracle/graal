/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.handles;

import java.util.Arrays;

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;

/**
 * Implementation of local object handles, which are bound to a specific thread and can be created
 * and destroyed implicitly or explicitly. Local handles can be managed in frames and a frame can be
 * discarded in its entirety.
 */
public final class ThreadLocalHandles<T extends ObjectHandle> {
    private static final int INITIAL_NUMBER_OF_FRAMES = 4;

    public static final int MIN_VALUE = Math.toIntExact(1 + nullHandle().rawValue());
    public static final int MAX_VALUE = Integer.MAX_VALUE;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <U extends SignedWord> U nullHandle() {
        return WordFactory.signed(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <U extends ObjectHandle> boolean isInRange(U handle) {
        return handle.rawValue() >= MIN_VALUE && handle.rawValue() <= MAX_VALUE;
    }

    private Object[] objects;
    private int top = MIN_VALUE;

    private int[] frameStack = new int[INITIAL_NUMBER_OF_FRAMES];
    private int frameCount = 0;

    public ThreadLocalHandles(int initialNumberOfHandles) {
        objects = new Object[MIN_VALUE + initialNumberOfHandles];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends ObjectHandle> int toIndex(T handle) {
        return (int) handle.rawValue();
    }

    public int getHandleCount() {
        return top - MIN_VALUE;
    }

    public int pushFrame(int capacity) {
        if (frameCount == frameStack.length) {
            growFrameStack();
        }
        frameStack[frameCount] = top;
        frameCount++;
        ensureCapacity(capacity);
        return frameCount;
    }

    @NeverInline("Decrease code size of JNI entry points by not inlining allocations")
    private void growFrameStack() {
        frameStack = Arrays.copyOf(frameStack, frameStack.length * 2);
    }

    @SuppressWarnings("unchecked")
    public T create(Object obj) {
        if (obj == null) {
            return nullHandle();
        }
        ensureCapacity(1);
        T handle = tryCreateNonNull(obj);
        assert !handle.equal(nullHandle());
        return handle;
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public T tryCreateNonNull(Object obj) {
        assert obj != null;
        if (top >= objects.length) {
            return nullHandle();
        }
        int index = top;
        objects[index] = obj;
        top++;
        return WordFactory.signed(index);
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <U> U getObject(T handle) {
        return (U) objects[toIndex(handle)];
    }

    public boolean delete(T handle) {
        int index = toIndex(handle);
        Object previous = objects[index];
        objects[index] = null;
        return previous != null;
    }

    public void popFrame() {
        popFramesIncluding(frameCount);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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
        int minLength = top + capacity;
        if (minLength >= objects.length) {
            growCapacity(minLength);
        }
    }

    @NeverInline("Decrease code size of JNI entry points by not inlining allocations")
    private void growCapacity(int minLength) {
        objects = Arrays.copyOf(objects, minLength * 2);
    }
}
