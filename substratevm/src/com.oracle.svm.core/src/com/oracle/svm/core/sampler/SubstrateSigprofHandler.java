/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.IsolateListenerSupport.IsolateListener;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.sampler.AbstractJfrExecutionSampler;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.PlatformThreads.ThreadLocalKey;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.word.Word;

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
public abstract class SubstrateSigprofHandler extends AbstractJfrExecutionSampler implements IsolateListener {
    private static final CGlobalData<Pointer> SIGNAL_HANDLER_ISOLATE = CGlobalDataFactory.createWord();
    private ThreadLocalKey keyForNativeThreadLocal;

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
        keyForNativeThreadLocal = PlatformThreads.singleton().createUnmanagedThreadLocal();
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        ThreadLocalKey oldKey = keyForNativeThreadLocal;
        keyForNativeThreadLocal = Word.nullPointer();
        PlatformThreads.singleton().deleteUnmanagedThreadLocal(oldKey);
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
            setSignalHandlerIsolate(Word.nullPointer());
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
    public void beforeThreadRun() {
        /* Workaround for GR-48636. */
        beforeThreadRun0();
    }

    @Uninterruptible(reason = "Prevent VM operations that modify the global or thread-local execution sampler state.")
    private void beforeThreadRun0() {
        IsolateThread thread = CurrentIsolate.getCurrentThread();
        if (isSampling()) {
            SubstrateJVM.getSamplerBufferPool().adjustBufferCount();
            install(thread);
        }
        storeIsolateThreadInNativeThreadLocal(thread);
    }

    protected abstract void installSignalHandler();

    protected void uninstallSignalHandler() {
        /*
         * Do not replace the signal handler with the default one because a signal might be pending
         * for some thread (the default signal handler would print "Profiling timer expired" to the
         * output).
         */
    }

    protected abstract void install0(IsolateThread thread);

    protected abstract void uninstall0(IsolateThread thread);

    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    private static void install(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isAllowed(thread)) {
            ExecutionSamplerInstallation.installed(thread);
            singleton().install0(thread);
        }
    }

    @Override
    @Uninterruptible(reason = "Prevent VM operations that modify thread-local execution sampler state.")
    protected void uninstall(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();

        if (ExecutionSamplerInstallation.isInstalled(thread)) {
            /*
             * Invalidate thread-local area. Once this value is set to null, the signal handler
             * can't interrupt this thread anymore.
             */
            storeIsolateThreadInNativeThreadLocal(Word.nullPointer());
            ExecutionSamplerInstallation.uninstalled(thread);
            uninstall0(thread);
        }
    }

    /**
     * Called from the platform dependent sigprof handler to enter isolate.
     */
    @Uninterruptible(reason = "The method executes during signal handling.", callerMustBe = true)
    protected static boolean tryEnterIsolate() {
        Isolate isolate = getSignalHandlerIsolate();
        if (isolate.isNull()) {
            /* It is not the initial isolate or the initial isolate already exited. */
            return false;
        }

        /* Write isolate pointer (heap base) into register. */
        CEntryPointSnippets.initBaseRegisters(Isolates.getHeapBase(isolate));

        /* We are keeping reference to isolate thread inside OS thread local area. */
        ThreadLocalKey key = singleton().keyForNativeThreadLocal;
        IsolateThread thread = PlatformThreads.singleton().getUnmanagedThreadLocalValue(key);
        if (thread.isNull()) {
            /* Thread is not yet initialized or already detached from isolate. */
            return false;
        }

        /* Write isolate thread pointer into register. */
        WriteCurrentVMThreadNode.writeCurrentVMThread(thread);
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void storeIsolateThreadInNativeThreadLocal(IsolateThread isolateThread) {
        PlatformThreads.singleton().setUnmanagedThreadLocalValue(keyForNativeThreadLocal, isolateThread);
    }

    public static class Options {
        @Option(help = "Print statistics collected during JFR-based execution sampler run.", type = OptionType.Expert)//
        public static final RuntimeOptionKey<Boolean> JfrBasedExecutionSamplerStatistics = new RuntimeOptionKey<>(false);
    }
}
