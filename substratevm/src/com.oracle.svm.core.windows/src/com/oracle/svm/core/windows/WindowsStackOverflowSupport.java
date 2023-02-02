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
package com.oracle.svm.core.windows;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.windows.headers.MemoryAPI;

@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.OSSupport.class)
@Platforms({Platform.WINDOWS.class})
final class WindowsStackOverflowSupport implements StackOverflowCheck.OSSupport {

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void getStackInformation(WordPointer stackEndPtr, WordPointer stackSizePtr) {
        int sizeOfMInfo = SizeOf.get(MemoryAPI.MEMORY_BASIC_INFORMATION.class);
        MemoryAPI.MEMORY_BASIC_INFORMATION minfo = StackValue.get(sizeOfMInfo);
        MemoryAPI.VirtualQuery(minfo, minfo, WordFactory.unsigned(sizeOfMInfo));
        Pointer stackBottom = (Pointer) minfo.AllocationBase();
        stackEndPtr.write(stackBottom);
        UnsignedWord stackSize = minfo.RegionSize();

        // Add up the sizes of all the regions with the same AllocationBase.
        while (true) {
            MemoryAPI.VirtualQuery(stackBottom.add(stackSize), minfo, WordFactory.unsigned(sizeOfMInfo));
            if (stackBottom.equal(minfo.AllocationBase())) {
                stackSize = stackSize.add(minfo.RegionSize());
            } else {
                break;
            }
        }
        stackSizePtr.write(stackSize);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public UnsignedWord lookupStackBase() {
        WordPointer stackBasePtr = StackValue.get(WordPointer.class);
        WordPointer stackEndPtr = StackValue.get(WordPointer.class);
        lookupStack(stackBasePtr, stackEndPtr, WordFactory.zero());
        return stackBasePtr.read();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public UnsignedWord lookupStackEnd() {
        return lookupStackEnd(WordFactory.zero());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public UnsignedWord lookupStackEnd(UnsignedWord requestedStackSize) {
        WordPointer stackBasePtr = StackValue.get(WordPointer.class);
        WordPointer stackEndPtr = StackValue.get(WordPointer.class);
        lookupStack(stackBasePtr, stackEndPtr, requestedStackSize);
        return stackEndPtr.read();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void lookupStack(WordPointer stackBasePtr, WordPointer stackEndPtr, UnsignedWord requestedStackSize) {
        WordPointer stackSizePtr = StackValue.get(WordPointer.class);
        getStackInformation(stackEndPtr, stackSizePtr);
        UnsignedWord stackEnd = stackEndPtr.read();
        UnsignedWord stackSize = stackSizePtr.read();
        stackBasePtr.write(stackEnd.add(stackSize));

        if (requestedStackSize.notEqual(WordFactory.zero())) {
            /*
             * if stackSize > requestedStackSize, then artificially limit stack end to match
             * requested stack size.
             */
            if (stackSize.aboveThan(requestedStackSize)) {
                UnsignedWord stackAdjustment = stackSize.subtract(requestedStackSize);
                stackEndPtr.write(stackEnd.add(stackAdjustment));
            }
        }
    }
}
