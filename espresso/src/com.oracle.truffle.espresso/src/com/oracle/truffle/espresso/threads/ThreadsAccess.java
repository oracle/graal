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
import static com.oracle.truffle.espresso.threads.KillStatus.KILLED;
import static com.oracle.truffle.espresso.threads.KillStatus.NORMAL;
import static com.oracle.truffle.espresso.threads.KillStatus.STOP;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class ThreadsAccess implements ContextAccess {

    private final Meta meta;
    private final EspressoContext context;

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

    @Override
    public EspressoContext getContext() {
        return context;
    }

    public ThreadsAccess(Meta meta) {
        this.meta = meta;
        this.context = meta.getContext();
    }

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

    public Thread getHost(StaticObject guest) {
        return (Thread) meta.HIDDEN_HOST_THREAD.getHiddenObject(guest);
    }

    /**
     * Implements support for thread deprecated methods ({@link Thread#stop()},
     * {@link Thread#suspend()}, {@link Thread#resume()}).
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

    public boolean isInterrupted(StaticObject guest, boolean clear) {
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

    public void interrupt(StaticObject guest) {
        if (context.getJavaVersion().java13OrEarlier()) {
            meta.HIDDEN_INTERRUPTED.setBoolean(guest, true, true);
        }
        Thread host = getHost(guest);
        if (host != null) {
            host.interrupt();
        }
    }

    public void clearInterruptStatus() {
        StaticObject guest = context.getCurrentThread();
        if (context.getJavaVersion().java13OrEarlier()) {
            clearInterruptStatus(guest);
        }
        Thread.interrupted();
    }

    public boolean isAlive(StaticObject guest) {
        int state = meta.java_lang_Thread_threadStatus.getInt(guest);
        return state != State.NEW.value && state != State.TERMINATED.value;
    }

    /**
     * Suspends a thread. On return, guarantees that the given thread has seen the suspend request.
     */
    public void suspend(StaticObject guest) {
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
        interrupt(guest);
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
        // Prepare
        meta.HIDDEN_HOST_THREAD.setHiddenObject(guest, host);
        host.setDaemon(meta.java_lang_Thread_daemon.getBoolean(guest));
        meta.java_lang_Thread_threadStatus.setInt(guest, State.RUNNABLE.value);
        host.setPriority(meta.java_lang_Thread_priority.getInt(guest));
        if (isInterrupted(guest, false)) {
            host.interrupt();
        }
        context.registerThread(host, guest);
        String guestName = context.getThreadAccess().getThreadName(guest);
        host.setName(guestName);
        return host;
    }

    /**
     * Termination of a threads works as follows:
     * <ul>
     * <li>Prevent other threads from {@linkplain #stop(StaticObject, StaticObject)} stopping} the
     * given thread</li>
     * <li>Invoke guest {@link Thread#exit()}</li>
     * <li>Sets the status of this thread to {@link State.TERMINATED} and notifies other threads
     * waiting on this thread's monitor</li>
     * <li>Unregisters the thread</li>
     * </ul>
     */
    public void terminate(StaticObject thread) {
        terminate(thread, null);
    }

    public void terminate(StaticObject thread, DirectCallNode exit) {
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
        terminateAndNotify(thread);
        context.unregisterThread(thread);
    }

    public void terminateAndNotify(StaticObject guest) {
        guest.getLock().lock();
        try {
            meta.java_lang_Thread_threadStatus.setInt(guest, State.TERMINATED.value);
            // Notify waiting threads you are done working
            guest.getLock().signalAll();
        } finally {
            guest.getLock().unlock();
        }
    }

    /**
     * returns true if this thread has been stopped before starting, or if the context is in
     * closing.
     */
    public boolean isStillborn(StaticObject guest) {
        if (context.isClosing()) {
            return true;
        }
        DeprecationSupport support = getDeprecationSupport(guest, false);
        if (support != null) {
            return support.status != NORMAL;
        }
        return false;
    }

    public void clearInterruptStatus(StaticObject guest) {
        meta.HIDDEN_INTERRUPTED.setBoolean(guest, false, true);
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
                    lock = new SuspendLock();
                    this.suspendLock = lock;
                }
            }
            lock.suspend(threads, thread);
        }

        void resume() {
            SuspendLock lock = this.suspendLock;
            if (lock == null) {
                return;
            }
            lock.resume();
        }

        void stop(StaticObject death) {
            // Writing the throwable must be done before the kill status can be observed
            throwable = death;
            status = STOP;
        }

        void kill() {
            status = KILL;
        }

        void exit() {
            status = EXITING;
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
                case KILLED:
                    break;
                case STOP:
                    if (meta.getContext().isClosing()) {
                        // Give some leeway during closing.
                        status = KILLED;
                    } else {
                        status = NORMAL;
                    }
                    // check if death cause throwable is set, if not throw ThreadDeath
                    StaticObject deathThrowable = throwable;
                    throw deathThrowable != null ? meta.throwException(deathThrowable) : meta.throwException(meta.java_lang_ThreadDeath);
                case KILL:
                    // This thread refuses to stop. Send a host exception.
                    assert meta.getContext().isClosing();
                    throw new EspressoExitException(meta.getContext().getExitStatus());
            }
        }
    }
}
