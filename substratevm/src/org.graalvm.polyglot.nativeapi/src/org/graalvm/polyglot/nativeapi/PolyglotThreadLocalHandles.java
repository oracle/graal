/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

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
final class PolyglotThreadLocalHandles<T extends ObjectHandle> {
    private static final int INITIAL_NUMBER_OF_FRAMES = 4;

    public static final int MIN_VALUE = Math.toIntExact(1 + nullHandle().rawValue());
    public static final int MAX_VALUE = Integer.MAX_VALUE;

    public static <U extends SignedWord> U nullHandle() {
        return WordFactory.signed(0);
    }

    public static <U extends ObjectHandle> boolean isInRange(U handle) {
        return handle.rawValue() >= MIN_VALUE && handle.rawValue() <= MAX_VALUE;
    }

    private Object[] objects;
    private int top = MIN_VALUE;

    private int[] frameStack = new int[INITIAL_NUMBER_OF_FRAMES];
    private int frameCount = 0;
    private boolean frozen;

    PolyglotThreadLocalHandles(int initialNumberOfHandles) {
        objects = new Object[MIN_VALUE + initialNumberOfHandles];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends ObjectHandle> int toIndex(T handle) {
        return (int) handle.rawValue();
    }

    public int pushFrame(int capacity) {
        if (frozen) {
            throw new IllegalStateException("Cannot push frame while handle creation is frozen");
        }
        if (frameCount == frameStack.length) {
            growFrameStack();
        }
        frameStack[frameCount] = top;
        frameCount++;
        ensureCapacity(capacity);
        return frameCount;
    }

    @NeverInline("Decrease code size of entry points by not inlining allocations")
    private void growFrameStack() {
        frameStack = Arrays.copyOf(frameStack, frameStack.length * 2);
    }

    @SuppressWarnings("unchecked")
    public T create(Object obj) {
        if (frozen) {
            throw new IllegalStateException("Cannot create handle while handle creation is frozen");
        }
        if (frameCount == 0) {
            throw new IllegalStateException("Cannot create handle with empty frame stack");
        }
        if (obj == null) {
            return nullHandle();
        }
        T handle = createNonNull(obj);
        assert !handle.equal(nullHandle());
        return handle;
    }

    @SuppressWarnings("unchecked")
    private T createNonNull(Object obj) {
        assert obj != null;

        ensureCapacity(1);
        assert top < objects.length;

        int index = top;
        objects[index] = obj;
        top++;
        return WordFactory.signed(index);
    }

    @SuppressWarnings("unchecked")
    public <U> U getObject(T handle) {
        if (frameCount == 0) {
            throw new IllegalStateException("Cannot get object with empty frame stack");
        }
        int index = toIndex(handle);
        if (index >= top) {
            throw new IllegalStateException("Cannot get object from beyond the live object range.");
        }
        return (U) objects[index];
    }

    public void popFrame() {
        popFramesIncluding(frameCount);
    }

    public void popFramesIncluding(int frame) {
        if (frame <= 0 || frame > frameCount) {
            String message = "Invalid frame pop request. Requested frame to pop: " +
                            frame +
                            ", Frame count: " +
                            frameCount;
            throw new IllegalStateException(message);
        }
        int previousTop = top;
        frameCount = frame - 1;
        top = frameStack[frameCount];
        for (int i = top; i < previousTop; i++) {
            objects[i] = null; // so objects can be garbage collected
        }
    }

    private void ensureCapacity(int capacity) {
        int minLength = top + capacity;
        if (minLength >= objects.length) {
            growCapacity(minLength);
        }
    }

    @NeverInline("Decrease code size of entry points by not inlining allocations")
    private void growCapacity(int minLength) {
        objects = Arrays.copyOf(objects, minLength * 2);
    }

    public void freezeHandleCreation() {
        frozen = true;
    }

    public void unfreezeHandleCreation() {
        frozen = false;
    }
}
