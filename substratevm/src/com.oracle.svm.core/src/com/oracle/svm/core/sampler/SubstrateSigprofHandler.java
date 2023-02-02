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
package com.oracle.svm.core.sampler;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.IsolateListenerSupport.IsolateListener;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.nodes.WriteHeapBaseNode;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.sampler.AbstractJfrExecutionSampler;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

/**
 * This is the core class of the low overhead asynchronous execution sampler. It registers a SIGPROF
 * signal handler that is then triggered by the OS periodically. The asynchronous nature of the
 * signal means that the OS could invoke the signal handler at any time (when in native code, during
 * a GC, during a VM operation, or while executing uninterruptible code). Therefore, we need to be
 * very careful about the code that the signal handler executes, e.g., all called native methods
 * need to be async-signal-safe.
 *
 * The signal handler calls Native Image code to restore reserved registers such as the heap base
 * and the isolate-thread, before preparing everything that is needed for a stack walk.
 */
public abstract class SubstrateSigprofHandler extends AbstractJfrExecutionSampler implements IsolateListener, ThreadListener {
    private static final CGlobalData<Pointer> SIGNAL_HANDLER_ISOLATE = CGlobalDataFactory.createWord();
    private UnsignedWord keyForNativeThreadLocal;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SubstrateSigprofHandler() {
    }

    @Fold
    public static SubstrateSigprofHandler singleton() {
        return ImageSingletons.lookup(SubstrateSigprofHandler.class);
    }

    @Override
    @Uninterruptible(reason = "Thread state not set up yet.")
    public void afterCreateIsolate(Isolate isolate) {
        keyForNativeThreadLocal = createNativeThreadLocal();
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        UnsignedWord oldKey = keyForNativeThreadLocal;
        keyForNativeThreadLocal = WordFactory.nullPointer();
        deleteNativeThreadLocal(oldKey);
    }

    @Override
    protected void startSampling() {
        assert VMOperation.isInProgressAtSafepoint();
        assert getSignalHandlerIsolate().isNull();

        SubstrateJVM.getSamplerBufferPool().adjustBufferCount();

        setSignalHandlerIsolate(CurrentIsolate.getIsolate());
        installSignalHandler();

        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            install(thread);
        }
    }

    @Override
    protected abstract void updateInterval();

    @Override
    protected void stopSampling() {
        assert VMOperation.isInProgressAtSafepoint();
        assert getSignalHandlerIsolate().isNonNull();

        /* Uninstall the SIGPROF handler so that it won't be triggered anymore. */
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            uninstall(thread);
        }
        uninstallSignalHandler();

        /* Wait until all threads exited the signal handler and cleanup no longer needed data. */
        disallowThreadsInSamplerCode();
        try {
            setSignalHandlerIsolate(WordFactory.nullPointer());
        } finally {
            allowThreadsInSamplerCode();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Isolate getSignalHandlerIsolate() {
        return SIGNAL_HANDLER_ISOLATE.get().readWord(0);
    }

    private static void setSignalHandlerIsolate(Isolate isolate) {
        assert getSignalHandlerIsolate().isNull() || isolate.isNull();
        SIGNAL_HANDLER_ISOLATE.get().writeWord(0, isolate);
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify the global or thread-local execution sampler state.")
    public void beforeThreadRun() {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (isSampling()) {
            SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
            install(thread);
        }
        storeIsolateThreadInNativeThreadLocal(thread);
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    public void afterThreadRun() {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        uninstall(thread);
        ExecutionSamplerInstallation.disallow(thread);
    }

    protected abstract void installSignalHandler();

    protected abstract void uninstallSignalHandler();

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private static void install(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isAllowed(thread)) {
            ExecutionSamplerInstallation.installed(thread);
        }
    }

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private static void uninstall(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isInstalled(thread)) {
            ExecutionSamplerInstallation.uninstalled(thread);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract UnsignedWord createNativeThreadLocal();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void deleteNativeThreadLocal(UnsignedWord key);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract void setNativeThreadLocalValue(UnsignedWord key, IsolateThread value);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected abstract IsolateThread getNativeThreadLocalValue(UnsignedWord key);

    /**
     * Called from the platform dependent sigprof handler to enter isolate.
     */
    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    protected static boolean tryEnterIsolate() {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            Isolate isolate = getSignalHandlerIsolate();
            if (isolate.isNull()) {
                /* It may happen that the initial isolate exited. */
                return false;
            }

            /* Write isolate pointer (heap base) into register. */
            WriteHeapBaseNode.writeCurrentVMHeapBase(isolate);
        }

        /* We are keeping reference to isolate thread inside OS thread local area. */
        if (SubstrateOptions.MultiThreaded.getValue()) {
            UnsignedWord key = singleton().keyForNativeThreadLocal;
            IsolateThread thread = singleton().getNativeThreadLocalValue(key);
            if (thread.isNull()) {
                /* Thread is not yet initialized or already detached from isolate. */
                return false;
            }

            /* Write isolate thread pointer into register. */
            WriteCurrentVMThreadNode.writeCurrentVMThread(thread);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void storeIsolateThreadInNativeThreadLocal(IsolateThread isolateThread) {
        setNativeThreadLocalValue(keyForNativeThreadLocal, isolateThread);
    }
}
