/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.classfile.perf.DebugCloseable;
import com.oracle.truffle.espresso.classfile.perf.DebugTimer;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.EspressoThreadRegistry;
import com.oracle.truffle.espresso.threads.ThreadAccess;

final class EspressoShutdownHandler extends ContextAccessImpl {

    private static final DebugTimer SHUTDOWN_TIMER = DebugTimer.create("shutdown");

    // region context

    private final EspressoThreadRegistry threadManager;
    private final EspressoReferenceDrainer referenceDrainer;
    private final boolean softExit;

    // endregion context

    EspressoShutdownHandler(EspressoContext context,
                    EspressoThreadRegistry threadManager,
                    EspressoReferenceDrainer referenceDrainer, boolean softExit) {
        super(context);
        this.threadManager = threadManager;
        this.referenceDrainer = referenceDrainer;
        this.softExit = softExit;
        this.shutdownSynchronizer = EspressoLock.create(context.getBlockingSupport());
    }

    /**
     * The amount of time the teardown process should wait (in total) for threads to complete before
     * moving on to the next phase.
     */
    private static final long MAX_KILL_PHASE_WAIT = 100;

    /**
     * Controls behavior of context closing. Until an exit method has been called, context closing
     * waits for all non-daemon threads to finish.
     */
    private volatile boolean isClosing = false;
    private volatile int exitStatus = -1;

    /**
     * On return of the main method, host main thread waits on this synchronizer. Once a thread
     * terminates, or an exit method is called, it is notified
     */
    private final EspressoLock shutdownSynchronizer;

    boolean isClosing() {
        return isClosing;
    }

    int getExitStatus() {
        assert isClosing();
        if (isExitStatusUninit()) {
            return 0;
        }
        return exitStatus;
    }

    private boolean isExitStatusUninit() {
        return !isClosing;
    }

    private void beginClose(int code) {
        assert getShutdownSynchronizer().isHeldByCurrentThread();
        isClosing = true;
        exitStatus = code;
        getContext().getLogger().finer(() -> "Starting close process with code=" + code);
    }

    EspressoLock getShutdownSynchronizer() {
        return shutdownSynchronizer;
    }

    /**
     * Starts the teardown process of the VM if it was not already started.
     *
     * Notify any thread waiting for teardown (through {@link #destroyVM()}) to immediately return
     * and let us do the job.
     */
    @TruffleBoundary
    void doExit(int code) {
        if (!getContext().isInitialized()) {
            return;
        }
        getContext().getLogger().fine(() -> {
            StaticObject currentThread = getContext().getCurrentPlatformThread();
            String guestName = getThreadAccess().getThreadName(currentThread);
            return "doExit(" + code + ") from " + guestName;
        });
        closeAndTeardown(code, true);
    }

    /**
     * Implements the {@code <DestroyJavaVM>} command of Espresso.
     * <ol>
     * <li>Waits for all other non-daemon thread to naturally terminate.
     * <li>If all threads have terminated, and no other thread called an exit method, then:
     * <li>This calls guest {@code java.lang.Shutdown#shutdown()}
     * <li>Proceeds to teardown leftover daemon threads.
     * <li>Softly exits the context by throwing an {@link EspressoExitException}.
     * </ol>
     * 
     * Note that this espresso context will be unable to run guest code once this method returns.
     *
     */
    @TruffleBoundary
    void destroyVM() {
        waitForClose();
        try {
            getMeta().java_lang_Shutdown_shutdown.invokeDirectStatic();
        } catch (AbstractTruffleException e) {
            /* Suppress guest exception so as not to bypass teardown */
        }
        closeAndTeardown(0, false);
    }

    private void closeAndTeardown(int code, boolean notifyNaturalExit) {
        if (isClosing()) {
            return;
        }
        EspressoLock sync = getShutdownSynchronizer();
        sync.lock();
        try {
            if (isClosing()) {
                return;
            }
            beginClose(code);
            if (notifyNaturalExit) {
                // Wake up spinning natural exiting thread if needed.
                sync.signalAll();
            }
        } finally {
            sync.unlock();
        }
        teardown();
    }

    private void waitForClose() throws EspressoExitException {
        EspressoLock synchronizer = getShutdownSynchronizer();
        Thread initiating = Thread.currentThread();
        getContext().getLogger().fine("Waiting for non-daemon threads to finish or exit");
        synchronizer.lock();
        try {
            while (true) {
                if (isClosing()) {
                    return;
                }
                if (hasActiveNonDaemon(initiating)) {
                    try {
                        synchronizer.await(0L);
                    } catch (GuestInterruptedException e) {
                        /* loop back */
                    }
                } else {
                    return;
                }
            }
        } finally {
            synchronizer.unlock();
        }
    }

    private boolean hasActiveNonDaemon(Thread initiating) {
        for (StaticObject guest : getManagedThreads()) {
            Thread host = getThreadAccess().getHost(guest);
            if (host != initiating && !host.isDaemon()) {
                if (host.isAlive()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void ensureThreadsJoined() {
        // wait indefinitely
        ensureThreadsJoined(Thread.currentThread(), 0L, Level.WARNING);
    }

    private boolean ensureThreadsJoined(Thread initiatingThread, long maxWaitMillis, Level failureLogLevel) {
        // Unconditionally kill
        getContext().getLogger().finer("Teardown: Phase 3: Force kill with host EspressoExitExceptions");
        teardownPhase3(initiatingThread);
        boolean success = joinThreads(initiatingThread, true, maxWaitMillis);

        // Special handling of the reference drainer
        referenceDrainer.shutdownAndWaitReferenceDrain();

        if (!success) {
            getContext().getLogger().log(failureLogLevel, "Could not gracefully stop executing threads in context closing.");
            getContext().getLogger().log(failureLogLevel, () -> {
                StringBuilder str = new StringBuilder("Threads still alive: ");
                for (StaticObject guest : getManagedThreads()) {
                    str.append(getThreadAccess().getHost(guest));
                }
                return str.toString();
            });
        }

        return success;
    }

    @SuppressWarnings("try")
    private void teardown() {
        assert isClosing();

        try (DebugCloseable shutdown = SHUTDOWN_TIMER.scope(getContext().getTimers())) {
            getVM().getJvmti().postVmDeath();

            getContext().prepareDispose();
            Thread initiatingThread = Thread.currentThread();

            getContext().getLogger().finer("Teardown: Phase 0: wait");
            boolean nextPhase = !joinNonDaemonThreads(initiatingThread);

            if (softExit) {
                // These phases give to running java thread a small window in which they can
                // gracefully exit in guest code, before abruptly cancelling them.
                if (nextPhase) {
                    // Send guest interruptions
                    getContext().getLogger().finer("Teardown: Phase 1: Interrupt threads.");
                    teardownPhase1(initiatingThread);
                    nextPhase = !joinNonDaemonThreads(initiatingThread);
                }

                if (nextPhase) {
                    // Send guest ThreadDeaths
                    getContext().getLogger().finer("Teardown: Phase 2: Stop all threads.");
                    teardownPhase2(initiatingThread);
                    nextPhase = !joinNonDaemonThreads(initiatingThread);
                }
            }

            boolean allowHostExit = getContext().getEspressoEnv().AllowHostExit;
            // wait indefinitely if we can't kill the host
            if (!ensureThreadsJoined(initiatingThread, allowHostExit ? MAX_KILL_PHASE_WAIT : 0L, allowHostExit ? Level.FINE : Level.WARNING)) {
                if (allowHostExit) {
                    // Needed until we can release rogue threads from Truffle (GR-28701).
                    getContext().getLogger().fine("Calling Host System.exit()...");
                    System.exit(getExitStatus());
                }
            }
        }

        getContext().getTimers().report(getContext().getLogger()::info);
    }

    /**
     * Triggers soft interruption of active threads. This sends an interrupt signal to all leftover
     * threads, giving them a chance to gracefully exit.
     */
    private void teardownPhase1(Thread initiatingThread) {
        teardownLoop(getThreadAccess()::callInterrupt, initiatingThread);
    }

    /**
     * Slightly harder interruption of leftover threads. Equivalent of guest Thread.stop(). Gives
     * leftover threads a chance to terminate in guest code (running finally blocks).
     */
    private void teardownPhase2(Thread initiatingThread) {
        teardownLoop(getThreadAccess()::stop, initiatingThread);
    }

    /**
     * Threads still alive at this point gets sent an uncatchable host exception. This forces the
     * thread to never execute guest code again. In particular, this means that no finally blocks
     * will be executed. Still, monitors entered through the monitorenter bytecode will be unlocked.
     */
    private void teardownPhase3(Thread initiatingThread) {
        teardownLoop(getThreadAccess()::kill, initiatingThread);
    }

    /**
     * Teardown phase happens for all thread registered from espresso to truffle, for which
     * {@code TruffleLanguage#dispose()} has no yet been called.
     */
    private void teardownLoop(Consumer<StaticObject> action, Thread initiatingThread) {
        for (StaticObject guest : getManagedThreads()) {
            Thread t = getThreadAccess().getHost(guest);
            if (t.isAlive() && t != initiatingThread) {
                action.accept(guest);
            }
        }
    }

    private boolean joinNonDaemonThreads(Thread initiatingThread) {
        return joinThreads(initiatingThread, false, MAX_KILL_PHASE_WAIT);
    }

    /**
     * Waits for some time for all non-disposed threads to terminate.
     *
     * @param maxWaitMillis the timeout for join operations, 0 for unlimited
     *
     * @return true if all threads are completed, false otherwise.
     */
    private boolean joinThreads(Thread initiatingThread, boolean waitForDaemon, long maxWaitMillis) {
        for (StaticObject guest : getManagedThreads()) {
            Thread t = getThreadAccess().getHost(guest);
            if (waitForDaemon || !t.isDaemon()) {
                if (t != initiatingThread && t != referenceDrainer.drainHostThread() /*- drain thread gets a custom shutdown */) {
                    if (t.isAlive()) {
                        TruffleSafepoint.setBlockedThreadInterruptible(null, o -> t.join(maxWaitMillis), null);
                        if (t.isAlive()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private Iterable<StaticObject> getManagedThreads() {
        return new ManagedThreadsIterable(threadManager.activeThreads(), getThreadAccess());
    }

    private static class ManagedThreadsIterable implements Iterable<StaticObject> {
        private final StaticObject[] thrds;
        private final ThreadAccess access;

        ManagedThreadsIterable(StaticObject[] thrds, ThreadAccess access) {
            this.thrds = thrds;
            this.access = access;
        }

        @Override
        public Iterator<StaticObject> iterator() {
            return new ManagedThreadsIterator(thrds, access);
        }
    }

    private static class ManagedThreadsIterator implements Iterator<StaticObject> {
        private final ThreadAccess access;
        private final StaticObject[] thrds;
        int pos;

        ManagedThreadsIterator(StaticObject[] thrds, ThreadAccess access) {
            this.thrds = thrds;
            this.access = access;
            this.pos = -1;
            consume();
        }

        @Override
        public boolean hasNext() {
            return hasElement() && currentIsManaged();
        }

        @Override
        public StaticObject next() {
            StaticObject res = thrds[pos];
            consume();
            return res;
        }

        // skip to next managed thread
        private void consume() {
            pos++;
            while (hasElement() && !currentIsManaged()) {
                pos++;
            }
        }

        private boolean currentIsManaged() {
            if (!hasElement()) {
                return false;
            }
            return access.isManaged(thrds[pos]);
        }

        private boolean hasElement() {
            return pos >= 0 && pos < thrds.length;
        }
    }
}
