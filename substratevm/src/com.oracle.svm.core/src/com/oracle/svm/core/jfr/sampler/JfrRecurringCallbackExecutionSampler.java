/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr.sampler;

import static com.oracle.svm.core.snippets.KnownIntrinsics.readCallerStackPointer;
import static com.oracle.svm.core.snippets.KnownIntrinsics.readReturnAddress;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.JfrExecutionSamplerSupported;
import com.oracle.svm.core.jfr.JfrFeature;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.ThreadingSupportImpl.RecurringCallbackTimer;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;

public final class JfrRecurringCallbackExecutionSampler extends AbstractJfrExecutionSampler {
    private static final ExecutionSampleCallback CALLBACK = new ExecutionSampleCallback();

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrRecurringCallbackExecutionSampler() {
    }

    @Override
    protected void startSampling() {
        assert VMOperation.isInProgressAtSafepoint();

        SubstrateJVM.getSamplerBufferPool().adjustBufferCount();

        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            install(thread, createRecurringCallbackTimer());
        }
    }

    @Override
    protected void updateInterval() {
        assert VMOperation.isInProgressAtSafepoint();
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            uninstall(thread);
            install(thread, createRecurringCallbackTimer());
        }
    }

    @Override
    protected void stopSampling() {
        assert VMOperation.isInProgressAtSafepoint();
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            uninstall(thread);
        }
    }

    private RecurringCallbackTimer createRecurringCallbackTimer() {
        return ThreadingSupportImpl.createRecurringCallbackTimer(TimeUtils.millisToNanos(newIntervalMillis), CALLBACK);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    private static void install(IsolateThread thread, RecurringCallbackTimer callbackTimer) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isAllowed(thread)) {
            Threading.RecurringCallback currentCallback = ThreadingSupportImpl.getRecurringCallback(thread);
            if (currentCallback == null) {
                ExecutionSamplerInstallation.installed(thread);
                ThreadingSupportImpl.setRecurringCallback(thread, callbackTimer);
            }
        }
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify the recurring callbacks.")
    protected void uninstall(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isInstalled(thread)) {
            Threading.RecurringCallback currentCallback = ThreadingSupportImpl.getRecurringCallback(thread);
            if (currentCallback == CALLBACK) {
                ThreadingSupportImpl.removeRecurringCallback(thread);
            }
            ExecutionSamplerInstallation.uninstalled(thread);
        }
    }

    @Override
    public void beforeThreadRun() {
        RecurringCallbackTimer callbackTimer = createRecurringCallbackTimer();
        beforeThreadRun0(callbackTimer);
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the execution sampler or the recurring callbacks.")
    private void beforeThreadRun0(RecurringCallbackTimer callbackTimer) {
        if (isSampling()) {
            SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
            install(CurrentIsolate.getCurrentThread(), callbackTimer);
        }
    }

    private static final class ExecutionSampleCallback implements Threading.RecurringCallback {
        @Override
        @NeverInline("Starting a stack walk in the caller frame")
        @Uninterruptible(reason = "Avoid interference with the application.")
        public void run(Threading.RecurringCallbackAccess access) {
            Pointer sp = readCallerStackPointer();
            CodePointer ip = readReturnAddress();
            tryUninterruptibleStackWalk(ip, sp, false);
        }
    }
}

@AutomaticallyRegisteredFeature
class JfrRecurringCallbackExecutionSamplerFeature implements InternalFeature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(JfrFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (JfrExecutionSamplerSupported.isSupported() && !ImageSingletons.contains(JfrExecutionSampler.class)) {
            JfrRecurringCallbackExecutionSampler sampler = new JfrRecurringCallbackExecutionSampler();
            ImageSingletons.add(JfrExecutionSampler.class, sampler);

            ThreadListenerSupport.get().register(sampler);
        }
    }
}
