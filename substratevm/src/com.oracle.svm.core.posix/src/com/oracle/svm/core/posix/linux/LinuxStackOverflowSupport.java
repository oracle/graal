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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.stack.StackOverflowCheck;

@AutomaticallyRegisteredImageSingleton(StackOverflowCheck.PlatformSupport.class)
final class LinuxStackOverflowSupport implements StackOverflowCheck.PlatformSupport {
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean lookupStack(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        boolean result = lookupStack0(stackBasePtr, stackEndPtr);
        if (!result) {
            stackBasePtr.write(WordFactory.zero());
            stackEndPtr.write(WordFactory.zero());
        }
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean lookupStack0(WordPointer stackBasePtr, WordPointer stackEndPtr) {
        WordPointer guardSizePtr = StackValue.get(WordPointer.class);
        Pthread.pthread_attr_t attr = StackValue.get(Pthread.pthread_attr_t.class);
        if (Pthread.pthread_getattr_np(Pthread.pthread_self(), attr) != 0) {
            /*
             * pthread_getattr_np can fail for various reasons, e.g., because /proc/self/maps can't
             * be opened
             */
            return false;
        }

        if (Pthread.pthread_attr_getstack(attr, stackBasePtr, stackEndPtr) != 0) {
            return false;
        }

        /*
         * The block of memory returned by pthread_attr_getstack() includes guard pages where
         * present. We need to retrieve the size of the guard pages in order to trim them off. Note
         * that these guard pages are not the yellow and red zones of the stack that we designate.
         */
        if (Pthread.pthread_attr_getguardsize(attr, guardSizePtr) != 0) {
            return false;
        }
        UnsignedWord stackAddr = stackBasePtr.read();
        UnsignedWord stackSize = stackEndPtr.read();
        UnsignedWord guardSize = guardSizePtr.read();

        UnsignedWord stackBase = stackAddr.add(stackSize);
        UnsignedWord stackEnd = stackAddr.add(guardSize);
        stackBasePtr.write(stackBase);
        stackEndPtr.write(stackEnd);

        PosixUtils.checkStatusIs0(Pthread.pthread_attr_destroy(attr), "LinuxStackOverflowSupport: pthread_attr_destroy");
        return true;
    }
}
