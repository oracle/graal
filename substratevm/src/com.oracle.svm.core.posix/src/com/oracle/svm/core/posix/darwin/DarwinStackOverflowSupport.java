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
package com.oracle.svm.core.posix.darwin;

import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.VM_PROT_READ;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.VM_PROT_WRITE;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.VM_REGION_BASIC_INFO_64;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.VM_REGION_SUBMAP_INFO_COUNT_64;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.mach_task_self;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.mach_vm_region;
import static com.oracle.svm.core.posix.headers.darwin.DarwinVirtualMemory.vm_region_basic_info_data_64_t;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.OSSupport.class)
final class DarwinStackOverflowSupport implements StackOverflowCheck.OSSupport {
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

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static boolean isProtected(int prot) {
        return (prot & (VM_PROT_READ() | VM_PROT_WRITE())) == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static int vmComputeStackGuard(UnsignedWord stackend) {
        int guardsize = 0;

        WordPointer address = StackValue.get(WordPointer.class);
        address.write(stackend);
        WordPointer size = StackValue.get(WordPointer.class);
        size.write(WordFactory.zero());

        vm_region_basic_info_data_64_t info = StackValue.get(vm_region_basic_info_data_64_t.class);
        WordPointer task = mach_task_self();

        do {
            WordPointer dummyobject = StackValue.get(WordPointer.class);
            CIntPointer count = StackValue.get(CIntPointer.class);
            count.write(VM_REGION_SUBMAP_INFO_COUNT_64());

            if (mach_vm_region(task, address, size, VM_REGION_BASIC_INFO_64(), info, count, dummyobject) != 0) {
                return -1;
            }

            if (isProtected(info.protection())) {
                guardsize += size.read().rawValue();
            }

            UnsignedWord currentAddress = address.read();
            address.write(currentAddress.add(size.read()));
        } while (isProtected(info.protection()));

        return guardsize;
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
        Pthread.pthread_t self = Pthread.pthread_self();
        UnsignedWord stackaddr = DarwinPthread.pthread_get_stackaddr_np(self);
        UnsignedWord stacksize = DarwinPthread.pthread_get_stacksize_np(self);
        stackBasePtr.write(stackaddr);

        int guardsize = vmComputeStackGuard(stackaddr.subtract(stacksize));
        VMError.guarantee(guardsize >= 0 && guardsize < 100 * 1024);
        VMError.guarantee(stacksize.aboveThan(guardsize));

        stacksize = stacksize.subtract(guardsize);
        stackEndPtr.write(stackaddr.subtract(stacksize));

        if (requestedStackSize.notEqual(WordFactory.zero())) {
            /*
             * if stackSize > requestedStackSize, then artificially limit stack end to match
             * requested stack size.
             */
            if (stacksize.aboveThan(requestedStackSize)) {
                stackEndPtr.write(stackaddr.subtract(requestedStackSize));
            }
        }
    }
}
