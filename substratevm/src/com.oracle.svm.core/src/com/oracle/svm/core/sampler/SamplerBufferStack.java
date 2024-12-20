/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;

/**
 * Holds a sequence of native memory buffers.
 *
 * The stack uses a spin-lock to protect itself from races with competing pop operations (ABA
 * problem).
 */
public class SamplerBufferStack {

    private SamplerBuffer head;
    private final SamplerSpinLock spinLock;

    @Platforms(Platform.HOSTED_ONLY.class)
    SamplerBufferStack() {
        this.spinLock = new SamplerSpinLock();
    }

    /**
     * Push the buffer into the linked-list.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void pushBuffer(SamplerBuffer buffer) {
        spinLock.lock();
        try {
            buffer.setNext(head);
            head = buffer;
        } finally {
            spinLock.unlock();
        }
    }

    /**
     * Pop the buffer from the linked-list. Returns {@code null} if the list is empty.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public SamplerBuffer popBuffer() {
        spinLock.lock();
        try {
            SamplerBuffer result = head;
            if (result.isNonNull()) {
                head = head.getNext();
                result.setNext(Word.nullPointer());
            }
            return result;
        } finally {
            spinLock.unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isLockedByCurrentThread() {
        return spinLock.isOwner();
    }
}
