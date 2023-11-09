/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.nmt;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.headers.LibCSupport;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import com.oracle.svm.core.jdk.UninterruptibleUtils;

/**
 * This table stores addresses of NMT pre-init malloc blocks. These blocks will always contain
 * headers. It is important that this class only uses raw malloc/free to avoid recursive behaviour.
 * This table is read-only after NMT is initialized to mitigate the cost of synchronization.
 */
class PreInitTable extends AbstractUninterruptibleHashtable {

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UninterruptibleEntry[] createTable(int length) {
        return new UninterruptibleEntry[length];
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
        return a.getHash() == b.getHash();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
        UnsignedWord size = SizeOf.unsigned(UninterruptibleEntry.class);
        UninterruptibleEntry pointerOnHeap = ImageSingletons.lookup(LibCSupport.class).malloc(size);
        if (pointerOnHeap.isNonNull()) {
            NativeMemoryTracking.recordMallocWithoutHeader(size, NmtFlag.mtNMT.ordinal());
            UnmanagedMemoryUtil.copy((Pointer) valueOnStack, (Pointer) pointerOnHeap, SizeOf.unsigned(UninterruptibleEntry.class));
            return pointerOnHeap;
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void free(UninterruptibleEntry entry) {
        size--;
        ImageSingletons.lookup(LibCSupport.class).free(entry);
        NativeMemoryTracking.deaccountMallocWithoutHeader(SizeOf.unsigned(UninterruptibleEntry.class).rawValue(), NmtFlag.mtNMT.ordinal());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void remove(Pointer ptr) {
        UninterruptibleEntry entry = StackValue.get(UninterruptibleEntry.class);
        entry.setHash(UninterruptibleUtils.Long.hashCode(ptr.rawValue()));
        remove(entry);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean putIfAbsent(Pointer ptr) {
        UninterruptibleEntry entry = StackValue.get(UninterruptibleEntry.class);
        entry.setHash(UninterruptibleUtils.Long.hashCode(ptr.rawValue()));
        return putIfAbsent(entry);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UninterruptibleEntry get(Pointer ptr) {
        UninterruptibleEntry entry = StackValue.get(UninterruptibleEntry.class);
        entry.setHash(UninterruptibleUtils.Long.hashCode(ptr.rawValue()));
        return get(entry);
    }
}
