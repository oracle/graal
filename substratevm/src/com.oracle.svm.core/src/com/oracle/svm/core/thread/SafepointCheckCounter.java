/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.VMThreadLocalOffsetProvider;

/**
 * Per-thread counter for safepoint checks. If the counter reaches a value less or equal 0, the
 * {@link SafepointSlowpath} is entered.
 * <p>
 * Be careful when calling any of the methods that manipulate or set the counter value directly as
 * they have the potential to destroy or skew the data that is needed for scheduling the execution
 * of recurring callbacks (i.e., the number of executed safepoints in a period of time). See class
 * {@link RecurringCallbackSupport} for more details about recurring callbacks.
 * <p>
 * If the recurring callback support is disabled, the safepoint check counter can only be 0
 * (safepoint requested) or {@link #MAX_VALUE} (normal execution).
 * <p>
 * If the recurring callback support is enabled, the safepoint check counter can have one of the
 * following values:
 * <ul>
 * <li>value > 0: remaining number of safepoint checks before the safepoint slowpath code is
 * executed.</li>
 * <li>value == 0: the safepoint slowpath code will be executed. If the counter is 0, we know that
 * the thread that owns the counter decremented it to 0 in the safepoint fast path (i.e., there is
 * no other way that the counter can reach 0).</li>
 * <li>value < 0: another thread requested a safepoint by doing an arithmetic negation on the
 * value.</li>
 * </ul>
 */
public class SafepointCheckCounter {
    private static final FastThreadLocalInt valueTL = FastThreadLocalFactory.createInt("SafepointCheckCounter.value").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);
    /**
     * Can be used to reset the thread-local value after a safepoint. We explicitly keep some
     * distance to {@link Integer#MAX_VALUE} to avoid corner cases.
     */
    static final int MAX_VALUE = Integer.MAX_VALUE >>> 1;

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static boolean compareAndSet(IsolateThread thread, int oldValue, int newValue) {
        return valueTL.compareAndSet(thread, oldValue, newValue);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void setVolatile(IsolateThread thread, int value) {
        assert CurrentIsolate.getCurrentThread() == thread || VMThreads.StatusSupport.isStatusCreated(thread) || VMOperationControl.mayExecuteVmOperations();
        assert RecurringCallbackSupport.isEnabled() && value > 0 || !RecurringCallbackSupport.isEnabled() && (value == 0 || value == MAX_VALUE);
        valueTL.setVolatile(thread, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void setVolatile(int value) {
        assert value >= 0;
        valueTL.setVolatile(value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static int getVolatile(IsolateThread thread) {
        return valueTL.getVolatile(thread);
    }

    public static LocationIdentity getLocationIdentity() {
        return valueTL.getLocationIdentity();
    }

    public static int getThreadLocalOffset() {
        return VMThreadLocalOffsetProvider.getOffset(valueTL);
    }

    public static class TestingBackdoor {
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static int getValue() {
            return getVolatile(CurrentIsolate.getCurrentThread());
        }
    }
}
