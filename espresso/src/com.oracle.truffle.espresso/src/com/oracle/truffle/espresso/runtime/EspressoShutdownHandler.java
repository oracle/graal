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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;

class EspressoShutdownHandler implements ContextAccess {

    // region context

    private final EspressoContext context;
    private final EspressoThreadManager threadManager;
    private final EspressoReferenceDrainer referenceDrainer;
    private final boolean softExit;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    // endregion context

    EspressoShutdownHandler(EspressoContext context,
                    EspressoThreadManager threadManager,
                    EspressoReferenceDrainer referenceDrainer, boolean softExit) {
        this.context = context;
        this.threadManager = threadManager;
        this.referenceDrainer = referenceDrainer;
        this.softExit = softExit;
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
    private final Object shutdownSynchronizer = new Object() {
    };

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
        assert Thread.holdsLock(getShutdownSynchronizer());
        isClosing = true;
        exitStatus = code;
        context.getLogger().finer(() -> "Starting close process with code=" + code);
    }

    Object getShutdownSynchronizer() {
        return shutdownSynchronizer;
    }

    /**
     * Starts the teardown process of the VM if it was not already started.
     *
     * Notify any thread waiting for teardown (through {@link #destroyVM(boolean)}) to immediately
     * return and let us do the job.
     */
    @TruffleBoundary
    void doExit(int code) {
        getContext().getLogger().fine(() -> {
            Meta meta = getMeta();
            StaticObject currentThread = getContext().getCurrentThread();
            String guestName = Target_java_lang_Thread.getThreadName(meta, currentThread);
            return "doExit(" + code + ") from " + guestName;
        });
        if (!isClosing()) {
            Object sync = getShutdownSynchronizer();
            synchronized (sync) {
                if (isClosing()) {
                    throw new EspressoExitException(getExitStatus());
                }
                beginClose(code);
                // Wake up spinning main thread.
                sync.notifyAll();
            }
            teardown(!context.ExitHost);
        }
        if (context.ExitHost) {
            System.exit(getExitStatus());
            throw EspressoError.shouldNotReachHere();
        } else {
            // At this point, the exit code given should have been registered. If not, this means
            // that
            // another closing was started before us, and we should use the previous' exit code.
            throw new EspressoExitException(getExitStatus());
        }
    }

    /**
     * Implements the {@code <DestroyJavaVM>} command of Espresso.
     * <ol>
     * <li>Waits for all other non-daemon thread to naturally terminate.
     * <li>If all threads have terminated, and no other thread called an exit method, then:
     * <li>This calls guest {@code java.lang.Shutdown#shutdown()}
     * <li>Proceeds to teardown leftover daemon threads.
     * </ol>
     * 
     * @param killThreads
     */
    @TruffleBoundary
    void destroyVM(boolean killThreads) {
        waitForClose();
        try {
            getMeta().java_lang_Shutdown_shutdown.invokeDirect(null);
        } catch (EspressoException | EspressoExitException e) {
            /* Suppress guest exception so as not to bypass teardown */
        }
        if (isClosing()) {
            // Skip if Shutdown.shutdown called an exit method.
            throw new EspressoExitException(getExitStatus());
        }
        Object s = getShutdownSynchronizer();
        synchronized (s) {
            if (isClosing()) {
                // If a daemon thread called an exit in-between
                throw new EspressoExitException(getExitStatus());
            }
            beginClose(0);
        }
        teardown(killThreads);
        throw new EspressoExitException(getExitStatus());
    }

    private void waitForClose() throws EspressoExitException {
        Object synchronizer = getShutdownSynchronizer();
        Thread initiating = Thread.currentThread();
        context.getLogger().fine("Waiting for non-daemon threads to finish or exit");
        synchronized (synchronizer) {
            while (true) {
                if (isClosing()) {
                    throw new EspressoExitException(getExitStatus());
                }
                if (hasActiveNonDaemon(initiating)) {
                    try {
                        synchronizer.wait();
                    } catch (InterruptedException e) {
                        /* loop back */
                    }
                } else {
                    return;
                }
            }
        }
    }

    private boolean hasActiveNonDaemon(Thread initiating) {
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread host = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (host != initiating && !host.isDaemon()) {
                if (host.isAlive()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void teardown(boolean killThreads) {
        assert isClosing();

        if (!getContext().isInitialized()) {
            return;
        }

        getVM().getJvmti().postVmDeath();

        getContext().prepareDispose();
        getContext().invalidateNoThreadStop("Killing the VM");
        Thread initiatingThread = Thread.currentThread();

        getContext().getLogger().finer("Teardown: Phase 0: wait");
        boolean nextPhase = !waitSpin(initiatingThread);

        if (softExit) {
            if (nextPhase) {
                getContext().getLogger().finer("Teardown: Phase 1: Interrupt threads, and stops daemons");
                teardownPhase1(initiatingThread);
                nextPhase = !waitSpin(initiatingThread);
            }

            if (nextPhase) {
                getContext().getLogger().finer("Teardown: Phase 2: Stop all threads");
                teardownPhase2(initiatingThread);
                nextPhase = !waitSpin(initiatingThread);
            }
        }

        if (killThreads) {
            if (nextPhase) {
                getContext().getLogger().finer("Teardown: Phase 3: Force kill with host EspressoExitExceptions");
                teardownPhase3(initiatingThread);
                nextPhase = !waitSpin(initiatingThread);
            }

            if (nextPhase) {
                getContext().getLogger().severe("Could not gracefully stop executing threads in context closing.");
                getContext().getLogger().finer("Teardown: Phase 4: Forcefully command the context to forget any leftover thread");
                teardownPhase4(initiatingThread);
            }
        }

        try {
            referenceDrainer.joinReferenceDrain();
        } catch (InterruptedException e) {
            // ignore
        }

        context.getTimers().report(context.getLogger());
    }

    /**
     * Triggers soft interruption of active threads. This sends an interrupt signal to all leftover
     * threads, gving them a chance to gracefully exit.
     */
    private void teardownPhase1(Thread initiatingThread) {
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (t.isAlive() && t != initiatingThread) {
                if (t.isDaemon()) {
                    Target_java_lang_Thread.killThread(guest);
                }
                Target_java_lang_Thread.interrupt0(guest);
            }
        }
    }

    /**
     * Slightly harder interruption of leftover threads. Equivalent of guest Thread.stop(). Gives
     * leftover threads a chance to terminate in guest code (running finaly blocks).
     */
    private void teardownPhase2(Thread initiatingThread) {
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (t.isAlive() && t != initiatingThread) {
                Target_java_lang_Thread.killThread(guest);
                Target_java_lang_Thread.interrupt0(guest);
            }
        }
    }

    /**
     * Threads still alive at this point gets sent an uncatchable host exception. This forces the
     * thread to never execute guest code again. In particular, this means that no finally blocks
     * will be executed. Still, monitors entered through the monitorenter bytecode will be unlocked.
     */
    private void teardownPhase3(Thread initiatingThread) {
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (t.isAlive() && t != initiatingThread) {
                /*
                 * Currently, threads in native can not be killed in Espresso. This translates into
                 * a polyglot-side java.lang.IllegalStateException: The language did not complete
                 * all polyglot threads but should have.
                 */
                Target_java_lang_Thread.forceKillThread(guest);
                Target_java_lang_Thread.interrupt0(guest);
            }
        }
    }

    /**
     * All threads still alive by that point are considered rogue, and we have no control over them.
     */
    private void teardownPhase4(Thread initiatingThread) {
        for (StaticObject guest : threadManager.activeThreads()) {
            Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
            if (t.isAlive() && t != initiatingThread) {
                // TODO(garcia): Tell truffle to forget about this thread
                // Or
                // TODO(garcia): Gracefully exit and allow stopping threads in native.
            }
        }
    }

    /**
     * Waits for some time for all executing threads to gracefully finish.
     *
     * @return true if all threads are completed, false otherwise.
     */
    private boolean waitSpin(Thread initiatingThread) {
        long tick = System.currentTimeMillis();
        spinLoop: //
        while (true) {
            long time = System.currentTimeMillis() - tick;
            if (time > MAX_KILL_PHASE_WAIT) {
                return false;
            }
            for (StaticObject guest : threadManager.activeThreads()) {
                Thread t = Target_java_lang_Thread.getHostFromGuestThread(guest);
                if (t != initiatingThread) {
                    if (t.isAlive()) {
                        continue spinLoop;
                    }
                }
            }
            return true;
        }
    }
}
