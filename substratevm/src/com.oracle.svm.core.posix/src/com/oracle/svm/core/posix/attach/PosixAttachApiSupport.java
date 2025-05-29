/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.posix.attach;

import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

import com.oracle.svm.core.attach.AttachApiFeature;
import com.oracle.svm.core.attach.AttachApiSupport;
import com.oracle.svm.core.attach.AttachListenerThread;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.jdk.management.Target_jdk_internal_vm_VMSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/** The attach mechanism on Linux and macOS uses a UNIX domain socket for communication. */
public class PosixAttachApiSupport implements AttachApiSupport {
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private State state;
    private PosixAttachListenerThread attachListenerThread;
    private String cachedSocketFilePath;
    private int listener;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PosixAttachApiSupport() {
        state = State.Uninitialized;
        listener = -1;
    }

    @Fold
    public static PosixAttachApiSupport singleton() {
        return ImageSingletons.lookup(PosixAttachApiSupport.class);
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L344-L360")
    public void startup() {
        String path = getSocketFilePath();
        try (CCharPointerHolder f = CTypeConversion.toCString(path)) {
            AttachHelper.startup(f.get());
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L409-L440")
    public boolean isInitTrigger() {
        String filename = ".attach_pid" + ProcessHandle.current().pid();
        if (isInitTrigger0(filename)) {
            return true;
        }

        String fallbackPath = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory() + "/" + filename;
        return isInitTrigger0(fallbackPath);
    }

    private static boolean isInitTrigger0(String path) {
        try (CCharPointerHolder f = CTypeConversion.toCString(path)) {
            return AttachHelper.isInitTrigger(f.get());
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L373-L395")
    public void initialize() {
        lock.lock();
        try {
            if (state == State.Destroyed) {
                /* Attaching isn't possible anymore. */
                return;
            }

            if (state == State.Initialized) {
                if (isSocketFileValid()) {
                    /* Nothing to do, already fully initialized. */
                    return;
                }
                shutdown(false);
            }

            assert state == State.Uninitialized;
            if (createListener()) {
                attachListenerThread = new PosixAttachListenerThread(listener);
                attachListenerThread.start();
                state = State.Initialized;
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean isSocketFileValid() {
        assert lock.isHeldByCurrentThread();
        try (CCharPointerHolder f = CTypeConversion.toCString(getSocketFilePath())) {
            return AttachHelper.checkSocketFile(f.get());
        }
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L169-L180")
    public void shutdown(boolean inTeardownHook) {
        if (!shutdownRequested.compareAndSet(false, true) && Thread.currentThread() instanceof AttachListenerThread) {
            /*
             * Another thread already does the shutdown, so return right away (the attach listener
             * thread must not try to acquire the lock in that case because the thread that does the
             * shutdown will block until the attach listener thread exited).
             */
            return;
        }

        shutdown0(inTeardownHook);
    }

    private void shutdown0(boolean inTeardownHook) {
        lock.lock();
        try {
            if (state != State.Initialized) {
                assert attachListenerThread == null;
                shutdownRequested.set(false);
                return;
            }

            /* Shutdown the listener. This will also wake up the attach listener thread. */
            try (CCharPointerHolder f = CTypeConversion.toCString(getSocketFilePath())) {
                AttachHelper.listenerCleanup(listener, f.get());
                listener = -1;
            }

            if (attachListenerThread != Thread.currentThread()) {
                /* Wait until the attach listener thread exits. */
                try {
                    attachListenerThread.join();
                } catch (InterruptedException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }

            attachListenerThread = null;
            state = inTeardownHook ? State.Destroyed : State.Uninitialized;
            shutdownRequested.set(false);
        } finally {
            lock.unlock();
        }
    }

    private String getSocketFilePath() {
        /* No need for synchronization - all threads will compute the same result. */
        if (cachedSocketFilePath == null) {
            long pid = ProcessHandle.current().pid();
            String tempDir = Target_jdk_internal_vm_VMSupport.getVMTemporaryDirectory();
            cachedSocketFilePath = Paths.get(tempDir, ".java_pid" + pid).toString();
        }
        return cachedSocketFilePath;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-25+18/src/hotspot/os/posix/attachListener_posix.cpp#L185-L249")
    private boolean createListener() {
        assert lock.isHeldByCurrentThread();

        String path = getSocketFilePath();
        try (CCharPointerHolder p = CTypeConversion.toCString(path)) {
            listener = AttachHelper.createListener(p.get());
            return listener != -1;
        }
    }

    private enum State {
        Uninitialized,
        Initialized,
        Destroyed
    }
}

@AutomaticallyRegisteredFeature
class PosixAttachApiFeature extends AttachApiFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        PosixAttachApiSupport support = new PosixAttachApiSupport();
        ImageSingletons.add(AttachApiSupport.class, support);
        ImageSingletons.add(PosixAttachApiSupport.class, support);
    }
}
