/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Disallowed.class)
@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.PlatformSupport.class)
final class DarwinStackOverflowSupport implements StackOverflowCheck.PlatformSupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean lookupStack(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        Pthread.pthread_t self = Pthread.pthread_self();
        UnsignedWord stackaddr = DarwinPthread.pthread_get_stackaddr_np(self);
        UnsignedWord stacksize = DarwinPthread.pthread_get_stacksize_np(self);
        stackBasePtr.write(stackaddr);

        UnsignedWord guardsize = vmComputeStackGuardSize(stackaddr.subtract(stacksize));
        VMError.guarantee(guardsize.belowThan(100 * 1024));
        VMError.guarantee(stacksize.aboveThan(guardsize));

        stacksize = stacksize.subtract(guardsize);
        stackEndPtr.write(stackaddr.subtract(stacksize));
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord vmComputeStackGuardSize(UnsignedWord stackend) {
        UnsignedWord guardsize = Word.zero();

        WordPointer address = StackValue.get(WordPointer.class);
        address.write(stackend);
        WordPointer size = StackValue.get(WordPointer.class);
        size.write(Word.zero());

        vm_region_basic_info_data_64_t info = StackValue.get(vm_region_basic_info_data_64_t.class);
        WordPointer task = mach_task_self();

        do {
            WordPointer dummyobject = StackValue.get(WordPointer.class);
            CIntPointer count = StackValue.get(CIntPointer.class);
            count.write(VM_REGION_SUBMAP_INFO_COUNT_64());

            int machVMRegion = mach_vm_region(task, address, size, VM_REGION_BASIC_INFO_64(), info, count, dummyobject);
            if (machVMRegion != 0) {
                throw VMError.shouldNotReachHereUnexpectedInput(machVMRegion); // ExcludeFromJacocoGeneratedReport
            }

            if (isProtected(info.protection())) {
                guardsize = guardsize.add(size.read());
            }

            UnsignedWord currentAddress = address.read();
            address.write(currentAddress.add(size.read()));
        } while (isProtected(info.protection()));

        return guardsize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isProtected(int prot) {
        return (prot & (VM_PROT_READ() | VM_PROT_WRITE())) == 0;
    }
}
