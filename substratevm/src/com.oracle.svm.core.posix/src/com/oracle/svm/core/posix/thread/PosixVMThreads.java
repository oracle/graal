/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.thread;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.posix.pthread.PthreadThreadLocal;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

public final class PosixVMThreads extends VMThreads {

    public static final PthreadThreadLocal<IsolateThread> VMThreadTL = new PthreadThreadLocal<>();
    public static final FastThreadLocalWord<Isolate> IsolateTL = FastThreadLocalFactory.createWord();
    private static final int STATE_UNINITIALIZED = 1;
    private static final int STATE_INITIALIZING = 2;
    private static final int STATE_INITIALIZED = 3;
    private static final int STATE_TEARING_DOWN = 4;
    private static final UninterruptibleUtils.AtomicInteger initializationState = new UninterruptibleUtils.AtomicInteger(STATE_UNINITIALIZED);

    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static boolean isInitialized() {
        return initializationState.get() >= STATE_INITIALIZED;
    }

    /** Is threading being torn down? */
    @Uninterruptible(reason = "Called from uninterruptible code during tear down.")
    public static boolean isTearingDown() {
        return initializationState.get() >= STATE_TEARING_DOWN;
    }

    @Override
    /** Note that threading is being torn down. */
    protected void setTearingDown() {
        initializationState.set(STATE_TEARING_DOWN);
    }

    /**
     * Make sure the runtime is initialized for threading.
     */
    @Uninterruptible(reason = "Called from uninterruptible code. Too early for safepoints.")
    public static void ensureInitialized() {
        if (initializationState.compareAndSet(STATE_UNINITIALIZED, STATE_INITIALIZING)) {
            /*
             * We claimed the initialization lock, so we are now responsible for doing all the
             * initialization.
             */
            VMThreadTL.initialize();
            PthreadVMLockSupport.initialize();

            initializationState.set(STATE_INITIALIZED);

        } else {
            /* Already initialized, or some other thread claimed the initialization lock. */
            while (initializationState.get() < STATE_INITIALIZED) {
                /* Busy wait until the other thread finishes the initialization. */
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code. Too late for safepoints.")
    public static void finishTearDown() {
        VMThreadTL.destroy();
    }
}

@AutomaticFeature
@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
class PosixVMThreadsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMThreads.class, new PosixVMThreads());
    }
}
