/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.VMOperation;

/**
 * @see GCAccounting
 * @see ChunksAccounting
 */
public final class HeapAccounting {
    private final UninterruptibleUtils.AtomicUnsigned edenUsedBytes = new UninterruptibleUtils.AtomicUnsigned();
    private final UninterruptibleUtils.AtomicUnsigned youngUsedBytes = new UninterruptibleUtils.AtomicUnsigned();

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapAccounting() {
    }

    public void setEdenAndYoungGenBytes(UnsignedWord edenBytes, UnsignedWord youngBytes) {
        assert VMOperation.isGCInProgress() : "would cause races otherwise";
        youngUsedBytes.set(youngBytes);
        edenUsedBytes.set(edenBytes);
    }

    public void increaseEdenUsedBytes(UnsignedWord value) {
        youngUsedBytes.addAndGet(value);
        edenUsedBytes.addAndGet(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getYoungUsedBytes() {
        assert !VMOperation.isGCInProgress() : "value is incorrect during a GC";
        return youngUsedBytes.get();
    }

    public UnsignedWord getEdenUsedBytes() {
        assert !VMOperation.isGCInProgress() : "value is incorrect during a GC";
        return edenUsedBytes.get();
    }

    @SuppressWarnings("static-method")
    public UnsignedWord getSurvivorSpaceAfterChunkBytes(int survivorIndex) {
        return HeapImpl.getHeapImpl().getYoungGeneration().getSurvivorFromSpaceAt(survivorIndex).getChunkBytes();
    }
}
