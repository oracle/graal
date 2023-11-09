/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import jdk.graal.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;

import org.graalvm.word.PointerBase;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.headers.LibCSupport;
import com.oracle.svm.core.nmt.NmtFlag;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.ReturnAddress;

@AutomaticallyRegisteredImageSingleton(UnmanagedMemorySupport.class)
class UnmanagedMemorySupportImpl implements UnmanagedMemorySupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T malloc(UnsignedWord size) {
        return malloc(size, NmtFlag.mtNone.ordinal());
    }

    @Override
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T malloc(UnsignedWord size, int flag) {
        ReturnAddress ra = StackValue.get(ReturnAddress.class);
        if (NativeMemoryTracking.handlePreInitMallocs(size, flag, ra)) {
            return (T) ra.get();
        }
        Pointer outerPointer = libc().malloc(size.add(NativeMemoryTracking.getHeaderSize()));
        return (T) NativeMemoryTracking.recordMalloc(outerPointer, size, flag);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T calloc(UnsignedWord size) {
        return calloc(size, NmtFlag.mtNone.ordinal());
    }

    @Override
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T calloc(UnsignedWord size, int flag) {
        ReturnAddress ra = StackValue.get(ReturnAddress.class);
        if (NativeMemoryTracking.handlePreInitCallocs(size, flag, ra)) {
            return (T) ra.get();
        }
        Pointer outerPointer = libc().calloc(WordFactory.unsigned(1), size.add(NativeMemoryTracking.getHeaderSize()));
        return (T) NativeMemoryTracking.recordMalloc(outerPointer, size, flag);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        return realloc(ptr, size, NmtFlag.mtNone.ordinal());
    }

    @Override
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T extends PointerBase> T realloc(T ptr, UnsignedWord size, int flag) {

        ReturnAddress ra = StackValue.get(ReturnAddress.class);
        if (NativeMemoryTracking.handlePreInitReallocs((Pointer) ptr, size, flag, ra)) {
            return (T) ra.get();
        }

        // Retrieve necessary data from the old block
        Pointer oldOuterPointer = ((Pointer) ptr).subtract(NativeMemoryTracking.getHeaderSize());
        long oldSize = NativeMemoryTracking.getAllocationSize(oldOuterPointer);
        int oldCategory = NativeMemoryTracking.getAllocationCategory(oldOuterPointer);

        // Perform the realloc
        Pointer newOuterPointer = libc().realloc(oldOuterPointer, size.add(NativeMemoryTracking.getHeaderSize()));

        // Only deaccount the old block, if we were successful.
        if (newOuterPointer.isNonNull()) {
            NativeMemoryTracking.deaccountMalloc(oldSize, oldCategory);
        }

        // Account the new block and overwrite the header with the new tracking data
        return (T) NativeMemoryTracking.recordMalloc(newOuterPointer, size, flag);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void free(PointerBase ptr) {
        if (NativeMemoryTracking.handlePreInitFrees((Pointer) ptr)) {
            return;
        }
        NativeMemoryTracking.deaccountMalloc(ptr);
        libc().free(((Pointer) ptr).subtract(NativeMemoryTracking.getHeaderSize()));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void untrackedFree(PointerBase ptr) {
        libc().free(ptr);
    }

    @Fold
    static LibCSupport libc() {
        return ImageSingletons.lookup(LibCSupport.class);
    }
}
