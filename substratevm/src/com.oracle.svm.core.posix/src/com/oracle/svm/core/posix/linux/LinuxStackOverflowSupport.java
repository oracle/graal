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
package com.oracle.svm.core.posix.linux;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.thread.PosixPlatformThreads;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.PlatformSupport.class)
final class LinuxStackOverflowSupport implements StackOverflowCheck.PlatformSupport {
    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean lookupStack(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        boolean result = lookupStack0(stackBasePtr, stackEndPtr);
        if (!result) {
            stackBasePtr.write(Word.zero());
            stackEndPtr.write(Word.zero());
        }
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean lookupStack0(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        Pthread.pthread_attr_t attr = StackValue.get(Pthread.pthread_attr_t.class);
        if (Pthread.pthread_getattr_np(Pthread.pthread_self(), attr) != 0) {
            /*
             * pthread_getattr_np can fail for various reasons, e.g., because /proc/self/maps can't
             * be opened
             */
            return false;
        }

        WordPointer stackAddrPtr = StackValue.get(WordPointer.class);
        WordPointer stackSizePtr = StackValue.get(WordPointer.class);
        if (Pthread.pthread_attr_getstack(attr, stackAddrPtr, stackSizePtr) != 0) {
            return false;
        }

        UnsignedWord includedGuardSize = PosixPlatformThreads.computeGuardSizeIncludedInStackSize(attr);
        if (includedGuardSize == UnsignedUtils.MAX_VALUE) {
            return false;
        }

        UnsignedWord stackAddr = stackAddrPtr.read();
        UnsignedWord stackBase = stackAddr.add(stackSizePtr.read());
        UnsignedWord stackEnd = stackAddr.add(includedGuardSize);

        /* Publish the data. */
        stackBasePtr.write(stackBase);
        stackEndPtr.write(stackEnd);
        PosixUtils.checkStatusIs0(Pthread.pthread_attr_destroy(attr), "LinuxStackOverflowSupport: pthread_attr_destroy");
        return true;
    }
}
