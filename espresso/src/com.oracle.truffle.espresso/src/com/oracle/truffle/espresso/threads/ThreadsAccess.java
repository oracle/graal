/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.threads;

import static com.oracle.truffle.espresso.threads.KillStatus.EXITING;
import static com.oracle.truffle.espresso.threads.KillStatus.KILL;
import static com.oracle.truffle.espresso.threads.KillStatus.NORMAL;
import static com.oracle.truffle.espresso.threads.KillStatus.STOP;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Provides bridges to guest world thread implementation.
 */
public final class ThreadsAccess implements ContextAccess {

    private final Meta meta;
    private final EspressoContext context;

    public ThreadsAccess(Meta meta) {
        this.meta = meta;
        this.context = meta.getContext();
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public String getThreadName(StaticObject thread) {
        if (thread == null) {
            return "<unknown>";
        } else {
            return meta.toHostString(meta.java_lang_Thread_name.getObject(thread));
        }
    }

    public long getThreadId(StaticObject thread) {
        if (thread == null) {
            return -1;
        } else {
            return (long) meta.java_lang_Thread_tid.get(thread);
        }
    }

    /**
     * Returns the host thread associated with this guest thread, or null if the guest thread has
     * not yet been registered.
     */
    public Thread getHost(StaticObject guest) {
        return (Thread) meta.HIDDEN_HOST_THREAD.getHiddenObject(guest);
    }

    // region thread state transition

    public void fromRunnable(StaticObject self, State state) {
        assert meta.java_lang_Thread_threadStatus.getInt(self) == State.RUNNABLE.value;
        setState(self, state);
        checkDeprecation();
    }

    public void toRunnable(StaticObject self) {
        try {
            checkDeprecation();
        } finally {
            setState(self, State.RUNNABLE);
        }
    }

    private void setState(StaticObject self, State state) {
        meta.java_lang_Thread_threadStatus.setInt(self, state.value);
    }

    // endregion thread state transition

    // region thread control

    /**
     * Implements support for thread deprecated methods ({@code Thread#stop()},
     * {@code Thread#suspend()}, {@code Thread#resume()}).
     * <p>
     * The following performance concerns are to be considered:
     * <ul>
     * <li>If these deprecated methods are never called, this method should entirely fold.</li>
     * <li>When called for the first time, a very large portion of the compiled code will be
     * invalidated.</li>
     * <li>After being called for the first time, in most cases, this method should amount to
     * getting the current thread and reading a field</li>
     * </ul>
     */
    public void checkDeprecation() {
        // TODO: safepoints
        if (!context.shouldCheckDeprecationStatus()) {
            return;
        }
        StaticObject current = context.getCurrentThread();
        DeprecationSupport support = getDeprecationSupport(current, false);
        if (support == null) {
            return;
        }
        if (context.shouldCheckStop()) {
            support.handleStop(meta);
        }
        if (context.shouldCheckSuspend()) {
            support.handleSuspend();
        }
    }

    /**
     * Lookups and calls the guest Thread.interrupt() method.
     */
    public void interruptThread(StaticObject guestThread) {
        assert guestThread != null && getMeta().java_lang_Thread.isAssignableFrom(guestThread.getKlass());
        // Thread.interrupt is non-final.
        Method interruptMethod = guestThread.getKlass().vtableLookup(getMeta().java_lang_Thread_interrupt.getVTableIndex());
        assert interruptMethod != null;
        interruptMethod.invokeDirect(guestThread);
    }

    /**
     * Implementation of {@link Thread#isInterrupted()}.
     */
    public boolean isInterrupted(StaticObject guest, boolean clear) {
        if (context.getJavaVersion().java13OrEarlier() && !isAlive(guest)) {
            return false;
        }
        boolean isInterrupted = meta.HIDDEN_INTERRUPTED.getBoolean(guest, true);
        if (clear) {
            Thread host = getHost(guest);
            EspressoError.guarantee(host == Thread.currentThread(), "Thread#isInterrupted(true) is only supported for the current thread.");
            if (host != null && host.isInterrupted()) {
                Thread.interrupted();
            }
            clearInterruptStatus(guest);
        }
        return isInterrupted;
    }

    /**
     * Implementation of {@link Thread#interrupt()}.
     */
    public void interrupt(StaticObject guest) {
        if (context.getJavaVersion().java13OrEarlier() && isAlive(guest)) {
            // In JDK 13+, the interrupted status is set in java code.
            meta.HIDDEN_INTERRUPTED.setBoolean(guest, true, true);
        }
        Thread host = getHost(guest);
        if (host != null) {
            host.interrupt();
        }
    }

    /**
     * Implementation of {@code Thread.clearInterruptEvent} (JDK 13+).
     */
    public void clearInterruptEvent() {
        assert !context.getJavaVersion().java13OrEarlier();
        Thread.interrupted();
    }

    /**
     * Sets the interrupted field of the given thread to {@code false}.
     */
    public void clearInterruptStatus(StaticObject guest) {
        meta.HIDDEN_INTERRUPTED.setBoolean(guest, false, true);
    }

    /**
     * Implementation of {@link Thread#isAlive()}.
     */
    public boolean isAlive(StaticObject guest) {
        int state = meta.java_lang_Thread_threadStatus.getInt(guest);
        return state != State.NEW.value && state != State.TERMINATED.value;
    }

    // endregion thread control

    // region deprecated methods support

    /**
     * Suspends a thread. On return, guarantees that the given thread has seen the suspend request.
     */
    public void suspend(StaticObject guest) {
        if (!isAlive(guest)) {
            return;
        }
        DeprecationSupport support = getDeprecationSupport(guest, true);
        assert support != null;
        support.suspend(this);
    }

    /**
     * Resumes a thread. Does nothing if the thread was not previously suspended.
     */
    public void resume(StaticObject guest) {
        DeprecationSupport support = getDeprecationSupport(guest, false);
        if (support == null) {
            return;
        }
        support.resume();
    }

    /**
     * Notifies a thread to throw an asynchronous guest throwable whenever possible.
     */
    public void stop(StaticObject guest, StaticObject throwable) {
        DeprecationSupport support = getDeprecationSupport(guest, true);
        assert support != null;
        support.stop(throwable);
        Thread host = getHost(guest);
        if (host != null) {
            host.interrupt();
        }
    }

    /**
     * Notifies a thread to throw a host {@link EspressoExitException} whenever possible.
     */
    public void kill(StaticObject guest) {
        DeprecationSupport support = getDeprecationSupport(guest, true);
        support.kill();
    }

    /**
     * Creates a thread for the given guest thread. This thread will be ready to be started.
     */
    public Thread createJavaThread(StaticObject guest, DirectCallNode exit, DirectCallNode dispatch) {
        Thread host = context.getEnv().createThread(new GuestRunnable(context, guest, exit, dispatch));
        // Link guest to host
        meta.HIDDEN_HOST_THREAD.setHiddenObject(guest, host);
        // Prepare host thread
        host.setDaemon(meta.java_lang_Thread_daemon.getBoolean(guest));
        meta.java_lang_Thread_threadStatus.setInt(guest, State.RUNNABLE.value);
        host.setPriority(meta.java_lang_Thread_priority.getInt(guest));
        if (isInterrupted(guest, false)) {
            host.interrupt();
        }
        String guestName = context.getThreadAccess().getThreadName(guest);
        host.setName(guestName);
        // Make the thread known to the context
        context.registerThread(host, guest);
        return host;
    }

    /**
     * Terminates a given thread.
     * <p>
     * The following procedure is applied for termination:
     * <ul>
     * <li>Prevent other threads from {@linkplain #stop(StaticObject, StaticObject)} stopping} the
     * given thread.</li>
     * <li>Invoke guest {@code Thread.exit()}.</li>
     * <li>Sets the status of this thread to {@link State#TERMINATED} and notifies other threads
     * waiting on this thread's monitor.</li>
     * <li>Unregisters the thread from the context.</li>
     * </ul>
     */
    public void terminate(StaticObject thread) {
        terminate(thread, null);
    }

    void terminate(StaticObject thread, DirectCallNode exit) {
        DeprecationSupport support = getDeprecationSupport(thread, true);
        support.exit();
        try {
            if (exit == null) {
                meta.java_lang_Thread_exit.invokeDirect(thread);
            } else {
                exit.call(thread);
            }
        } catch (EspressoException | EspressoExitException e) {
            // just drop it
        }
        setTerminateStatusAndNotify(thread);
        context.unregisterThread(thread);
    }

    /**
     * returns true if this thread has been stopped before starting, or if the context is in
     * closing.
     * <p>
     * If this method returns true, the thread will have been terminated.
     */
    public boolean terminateIfStillborn(StaticObject guest) {
        if (isStillborn(guest)) {
            setTerminateStatusAndNotify(guest);
            return true;
        }
        return false;
    }

    private void setTerminateStatusAndNotify(StaticObject guest) {
        guest.getLock().lock();
        try {
            meta.java_lang_Thread_threadStatus.setInt(guest, State.TERMINATED.value);
            // Notify waiting threads you are done working
            guest.getLock().signalAll();
        } finally {
            guest.getLock().unlock();
        }
    }

    private boolean isStillborn(StaticObject guest) {
        if (context.isClosing()) {
            return true;
        }
        /*
         * A bit of a special case. We want to make sure we observe the stillborn status
         * synchronously.
         */
        synchronized (guest) {
            DeprecationSupport support = (DeprecationSupport) meta.HIDDEN_DEPRECATION_SUPPORT.getHiddenObject(guest, true);
            if (support != null) {
                return support.status != NORMAL;
            }
            return false;
        }
    }

    private DeprecationSupport getDeprecationSupport(StaticObject guest, boolean initIfNull) {
        DeprecationSupport support = (DeprecationSupport) meta.HIDDEN_DEPRECATION_SUPPORT.getHiddenObject(guest);
        if (initIfNull && support == null) {
            synchronized (guest) {
                support = (DeprecationSupport) meta.HIDDEN_DEPRECATION_SUPPORT.getHiddenObject(guest, true);
                if (support == null) {
                    support = new DeprecationSupport(guest);
                    meta.HIDDEN_DEPRECATION_SUPPORT.setHiddenObject(guest, support, true);
                }
            }
        }
        return support;
    }

    private static final class DeprecationSupport {

        private final StaticObject thread;

        private volatile StaticObject throwable = null;
        /*
         * Non-volatile for general performance purposes. The cost is that the target thread might
         * take longer to observe requests.;
         */
        private KillStatus status = NORMAL;
        private SuspendLock suspendLock = null;

        DeprecationSupport(StaticObject thread) {
            this.thread = thread;
        }

        void suspend(ThreadsAccess threads) {
            SuspendLock lock = this.suspendLock;
            if (lock == null) {
                synchronized (this) {
                    lock = new SuspendLock(threads, thread);
                    this.suspendLock = lock;
                }
            }
            lock.suspend();
        }

        void resume() {
            SuspendLock lock = this.suspendLock;
            if (lock == null) {
                return;
            }
            lock.resume();
        }

        synchronized void stop(StaticObject death) {
            KillStatus s = status;
            if (s == NORMAL || s == STOP) {
                // Writing the throwable must be done before the kill status can be observed
                throwable = death;
                updateKillState(STOP);
            }
        }

        synchronized void kill() {
            updateKillState(KILL);
        }

        synchronized void exit() {
            updateKillState(EXITING);
        }

        private void updateKillState(KillStatus state) {
            assert Thread.holdsLock(this);
            status = state;
        }

        @TruffleBoundary
        void handleSuspend() {
            SuspendLock lock = this.suspendLock;
            if (lock == null) {
                return;
            }
            lock.selfSuspend();
        }

        @TruffleBoundary
        void handleStop(Meta meta) {
            switch (status) {
                case NORMAL:
                case EXITING:
                    return;
                case STOP:
                    synchronized (this) {
                        // synchronize to make sure we are still stopped.
                        KillStatus s = status;
                        if (s == STOP) {
                            updateKillState(NORMAL);
                            // check if death cause throwable is set, if not throw ThreadDeath
                            StaticObject deathThrowable = throwable;
                            throw deathThrowable != null ? meta.throwException(deathThrowable) : meta.throwException(meta.java_lang_ThreadDeath);
                        } else if (s == KILL) {
                            throw new EspressoExitException(meta.getContext().getExitStatus());
                        }
                        // Stop status has been cleared somewhere else.
                        assert s == NORMAL || s == EXITING;
                        return;
                    }
                case KILL:
                    // This thread refuses to stop. Send a host exception.
                    throw new EspressoExitException(meta.getContext().getExitStatus());
            }
            throw EspressoError.shouldNotReachHere();
        }
    }

    // region deprecated methods support
}
