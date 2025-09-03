/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pltgot;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.thread.JavaSpinLockUtils;

import jdk.internal.misc.Unsafe;

public class MethodAddressResolutionDispatcher {
    private static final MethodAddressResolutionDispatcher dispatcher = new MethodAddressResolutionDispatcher();
    private static final long LOCK_OFFSET = Unsafe.getUnsafe().objectFieldOffset(MethodAddressResolutionDispatcher.class, "lock");

    @SuppressWarnings("unused")//
    private volatile int lock;
    private int activeResolverInstances = 0;

    @Uninterruptible(reason = "PLT/GOT method address resolution doesn't support interruptible code paths.")
    protected static long resolveMethodAddress(long gotEntry) {
        try {
            JavaSpinLockUtils.lockNoTransition(dispatcher, LOCK_OFFSET);
            if (dispatcher.activeResolverInstances == 0) {
                GOTHeapSupport.get().makeGOTWritable();
            }
            dispatcher.activeResolverInstances++;
        } finally {
            JavaSpinLockUtils.unlock(dispatcher, LOCK_OFFSET);
        }

        long resolvedMethodAddress = PLTGOTConfiguration.singleton().getMethodAddressResolver().resolveMethodWithGotEntry(gotEntry);

        try {
            JavaSpinLockUtils.lockNoTransition(dispatcher, LOCK_OFFSET);
            if (dispatcher.activeResolverInstances == 1) {
                GOTHeapSupport.get().makeGOTReadOnly();
            }
            dispatcher.activeResolverInstances--;
        } finally {
            JavaSpinLockUtils.unlock(dispatcher, LOCK_OFFSET);
        }
        return resolvedMethodAddress;
    }
}
