/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.thread.RecurringCallbackSupport.RecurringCallbackTimer;

import jdk.graal.compiler.api.replacements.Fold;

@AutomaticallyRegisteredImageSingleton(ThreadingSupport.class)
public class ThreadingSupportImpl implements ThreadingSupport {
    private static final String ENABLE_SUPPORT_OPTION = SubstrateOptionsParser.commandArgument(RecurringCallbackSupport.ConcealedOptions.SupportRecurringCallback, "+");

    /**
     * Registers or removes a recurring callback for the current thread. Only one recurring callback
     * can be registered at a time. If there is already another recurring callback registered, it
     * will be overwritten.
     */
    @Override
    public void registerRecurringCallback(long interval, TimeUnit unit, Threading.RecurringCallback callback) {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (callback != null) {
            if (!RecurringCallbackSupport.isEnabled()) {
                throw new UnsupportedOperationException("Recurring callbacks must be enabled during image build with option " + ENABLE_SUPPORT_OPTION);
            }

            long intervalNanos = unit.toNanos(interval);
            if (intervalNanos < 1) {
                throw new IllegalArgumentException("The intervalNanos field is less than one.");
            }

            RecurringCallbackTimer callbackTimer = RecurringCallbackSupport.createCallbackTimer(intervalNanos, callback);
            RecurringCallbackSupport.installCallback(thread, callbackTimer, true);
        } else if (RecurringCallbackSupport.isEnabled()) {
            RecurringCallbackSupport.uninstallCallback(thread);
        }
    }

    // GR-63737 only called from legacy code
    @Fold
    public static boolean isRecurringCallbackSupported() {
        return RecurringCallbackSupport.isEnabled();
    }

    // GR-63737 only called from legacy code
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void pauseRecurringCallback(String reason) {
        RecurringCallbackSupport.suspendCallbackTimer(reason);
    }

    // GR-63737 only called from legacy code
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void resumeRecurringCallbackAtNextSafepoint() {
        RecurringCallbackSupport.resumeCallbackTimerAtNextSafepointCheck();
    }
}
