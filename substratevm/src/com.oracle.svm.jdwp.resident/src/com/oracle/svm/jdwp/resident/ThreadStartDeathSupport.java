/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.thread.VMOperationControl;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Support for Thread start/death events.
 */
public final class ThreadStartDeathSupport implements ThreadListener {

    /**
     * Debugger-related threads that should not be visible to clients.
     */
    private final Thread[] debuggerThreads = new Thread[2];
    private static final int DEBUGGER_THREAD_INDEX_SERVER = 0;
    private static final int DEBUGGER_THREAD_INDEX_OBJECTS_QUEUE = 1;

    private volatile boolean start;
    private volatile boolean death;
    private volatile Listener listener;

    @Platforms(Platform.HOSTED_ONLY.class)
    ThreadStartDeathSupport() {
        ThreadListenerSupport.get().register(this);
        RuntimeSupport.getRuntimeSupport().addShutdownHook(new RuntimeSupport.Hook() {
            @Override
            public void execute(boolean isFirstIsolate) {
                Listener l = listener;
                if (l != null) {
                    l.vmDied();
                }
            }
        });
    }

    @Fold
    public static ThreadStartDeathSupport get() {
        return ImageSingletons.lookup(ThreadStartDeathSupport.class);
    }

    void setDebuggerThreadServer(Thread serverThread) {
        debuggerThreads[DEBUGGER_THREAD_INDEX_SERVER] = serverThread;
    }

    void setDebuggerThreadObjectQueue(Thread serverThread) {
        debuggerThreads[DEBUGGER_THREAD_INDEX_OBJECTS_QUEUE] = serverThread;
    }

    /**
     * Filter application-related threads and convert IsolateThread to Thread.
     * 
     * @return a converted application Thread, or {@code null}.
     */
    public Thread filterAppThread(IsolateThread isolateThread) {
        if (VMOperationControl.isDedicatedVMOperationThread(isolateThread)) {
            return null;
        }
        Thread thread = PlatformThreads.fromVMThread(isolateThread);
        if (thread == null) {
            return null;
        }
        for (Thread t : debuggerThreads) {
            if (t == thread) {
                return null;
            }
        }
        // TODO(peterssen): GR-55071 Identify external threads that entered via JNI.
        if (thread.getName().startsWith("System-")) {
            return null;
        }
        return thread;
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setListeningOn(boolean startOrDeath, boolean enable) {
        if (startOrDeath) {
            start = enable;
        } else {
            death = enable;
        }
    }

    @Override
    public void beforeThreadRun() {
        Listener l = listener;
        if (l != null && start) {
            l.threadStarted();
        }
    }

    @Override
    @Uninterruptible(reason = "Only uninterruptible because we need to prevent stack overflow errors.")
    public void afterThreadRun() {
        Listener l = listener;
        if (l != null && death) {
            l.threadDied();
        }
    }

    interface Listener {

        void threadStarted();

        void threadDied();

        void vmDied();
    }
}
