/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

public final class ReferenceHandlerThread implements Runnable {
    private final Thread thread;
    private volatile IsolateThread isolateThread;

    @Platforms(Platform.HOSTED_ONLY.class)
    ReferenceHandlerThread() {
        thread = new Thread(this, "Reference Handler");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
    }

    public static void start() {
        if (isSupported()) {
            singleton().thread.start();
        }
    }

    public static boolean isReferenceHandlerThread() {
        if (isSupported()) {
            return CurrentIsolate.getCurrentThread() == singleton().isolateThread;
        }
        return false;
    }

    public static boolean isReferenceHandlerThread(Thread other) {
        if (isSupported()) {
            return other == singleton().thread;
        }
        return false;
    }

    @Override
    public void run() {
        ThreadingSupportImpl.pauseRecurringCallback("An exception in a recurring callback must not interrupt pending reference processing because it could result in a memory leak.");

        this.isolateThread = CurrentIsolate.getCurrentThread();
        try {
            while (true) {
                ReferenceInternals.waitForPendingReferences();
                ReferenceInternals.processPendingReferences();
                ReferenceHandler.processCleaners();
            }
        } catch (InterruptedException e) {
            VMError.guarantee(VMThreads.isTearingDown(), "Reference Handler should only be interrupted during tear-down");
        } catch (Throwable t) {
            if (t instanceof OutOfMemoryError && VMThreads.isTearingDown()) {
                // Likely failed to allocate the InterruptedException, ignore either way.
            } else {
                VMError.shouldNotReachHere("Reference processing and cleaners must handle all potential exceptions", t);
            }
        }
    }

    @Fold
    static ReferenceHandlerThread singleton() {
        return ImageSingletons.lookup(ReferenceHandlerThread.class);
    }

    @Fold
    static boolean isSupported() {
        return SubstrateOptions.MultiThreaded.getValue() && SubstrateOptions.AllowVMInternalThreads.getValue();
    }
}

@AutomaticallyRegisteredFeature
class ReferenceHandlerThreadFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ReferenceHandlerThread.isSupported();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(ReferenceHandlerThread.class, new ReferenceHandlerThread());
    }
}
